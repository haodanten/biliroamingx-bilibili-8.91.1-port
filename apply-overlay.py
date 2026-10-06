#!/usr/bin/env python3
"""
把本仓库平铺的源码文件放回它们在 BiliRoamingX 源码树里的正确位置。

仓库是 GitHub 网页端上传的，网页上传不带相对路径，所有文件都被拍平到根目录。
这个脚本负责还原结构。

用法:
    python apply-overlay.py /path/to/BiliRoamingX        # 就地覆盖
    python apply-overlay.py /path/to/BiliRoamingX --dry  # 只看会做什么
"""
import argparse
import os
import shutil
import sys

# 本仓库里属于源码叠加层的文件名（平铺在根目录）
FILES = [
    ".github\\workflows\\ci.yml",
    ".github\\workflows\\pr.yml",
    ".github\\workflows\\release.yml",
    "FINGERPRINTS.md",
    "gradle.properties",
    "gradle\\libs.versions.toml",
    "integrations\\app\\build.gradle.kts",
    "integrations\\app\\src\\main\\java\\app\\revanced\\bilibili\\patches\\drawer\\DrawerPatch.java",
    "integrations\\app\\src\\main\\java\\app\\revanced\\bilibili\\patches\\json\\JSONPatch.java",
    "integrations\\app\\src\\main\\java\\app\\revanced\\bilibili\\patches\\json\\PegasusPatch.java",
    "integrations\\app\\src\\main\\java\\app\\revanced\\bilibili\\patches\\okhttp\\hooks\\FeedIndex.kt",
    "integrations\\app\\src\\main\\java\\app\\revanced\\bilibili\\patches\\PlaybackSpeedPatch.kt",
    "integrations\\app\\src\\main\\java\\app\\revanced\\bilibili\\patches\\protobuf\\MossPatch.kt",
    "integrations\\app\\src\\main\\java\\app\\revanced\\bilibili\\patches\\RemoveVipSectionPatch.kt",
    "integrations\\app\\src\\main\\java\\app\\revanced\\bilibili\\patches\\SubtitleImportSavePatch.kt",
    "integrations\\app\\src\\main\\java\\app\\revanced\\bilibili\\settings\\BatchEditor.kt",
    "integrations\\app\\src\\main\\java\\app\\revanced\\bilibili\\settings\\fragments\\FilterCommentByKeywordFragment.kt",
    "integrations\\app\\src\\main\\java\\app\\revanced\\bilibili\\settings\\fragments\\FilterDynamicByKeywordFragment.kt",
    "integrations\\app\\src\\main\\java\\app\\revanced\\bilibili\\settings\\fragments\\FilterHomeRcmdByKeywordFragment.kt",
    "integrations\\app\\src\\main\\java\\app\\revanced\\bilibili\\settings\\fragments\\FilterSearchKeywordsFragment.kt",
    "integrations\\app\\src\\main\\java\\app\\revanced\\bilibili\\settings\\Setting.kt",
    "integrations\\app\\src\\main\\java\\app\\revanced\\bilibili\\settings\\Settings.kt",
    "integrations\\app\\src\\test\\java\\app\\revanced\\bilibili\\settings\\BatchEditorTest.kt",
    "integrations\\dummy\\src\\main\\java\\com\\bilibili\\adcommon\\data\\model\\FeedAdInfo.java",
    "integrations\\dummy\\src\\main\\java\\com\\bilibili\\app\\comm\\supermenu\\core\\IMenu.java",
    "integrations\\dummy\\src\\main\\java\\com\\bilibili\\app\\comm\\supermenu\\core\\IMenuItem.java",
    "integrations\\dummy\\src\\main\\java\\com\\bilibili\\app\\comm\\supermenu\\core\\MenuImpl.java",
    "integrations\\dummy\\src\\main\\java\\com\\bilibili\\app\\comm\\supermenu\\core\\MenuItemImpl.java",
    "integrations\\dummy\\src\\main\\java\\com\\bilibili\\pegasus\\api\\model\\BasicIndexItem.java",
    "integrations\\dummy\\src\\main\\java\\okhttp3\\Headers.java",
    "integrations\\dummy\\src\\main\\java\\okhttp3\\HttpUrl.java",
    "integrations\\dummy\\src\\main\\java\\okhttp3\\Request.java",
    "manager\\build.gradle.kts",
    "manager\\proguard-rules.pro",
    "manager\\src\\main\\AndroidManifest.xml",
    "manager\\src\\main\\java\\app\\revanced\\biliroaming\\manager\\MainActivity.kt",
    "manager\\src\\main\\res\\drawable\\ic_launcher_foreground.xml",
    "manager\\src\\main\\res\\layout\\activity_main.xml",
    "manager\\src\\main\\res\\mipmap-anydpi-v26\\ic_launcher.xml",
    "manager\\src\\main\\res\\mipmap-anydpi-v26\\ic_launcher_round.xml",
    "manager\\src\\main\\res\\mipmap-hdpi\\ic_launcher.xml",
    "manager\\src\\main\\res\\values\\colors.xml",
    "manager\\src\\main\\res\\values\\strings.xml",
    "manager\\src\\main\\res\\values\\styles.xml",
    "patches\\patches.json",
    "patches\\src\\main\\kotlin\\app\\revanced\\patches\\bilibili\\misc\\json\\patch\\PegasusPatch.kt",
    "patches\\src\\main\\kotlin\\app\\revanced\\patches\\bilibili\\misc\\notification\\patch\\MusicNotificationPatch.kt",
    "patches\\src\\main\\kotlin\\app\\revanced\\patches\\bilibili\\misc\\other\\patch\\AutoLikePatch.kt",
    "patches\\src\\main\\kotlin\\app\\revanced\\patches\\bilibili\\misc\\other\\patch\\BiliLayoutAdjustResourcesPatch.kt",
    "patches\\src\\main\\kotlin\\app\\revanced\\patches\\bilibili\\misc\\other\\patch\\BLRoutePatch.kt",
    "patches\\src\\main\\kotlin\\app\\revanced\\patches\\bilibili\\misc\\other\\patch\\CacheRedirectPatch.kt",
    "patches\\src\\main\\kotlin\\app\\revanced\\patches\\bilibili\\misc\\other\\patch\\ForbidLiveRoomAutoFloatPatch.kt",
    "patches\\src\\main\\kotlin\\app\\revanced\\patches\\bilibili\\misc\\other\\patch\\ForceCommentNavigablePatch.kt",
    "patches\\src\\main\\kotlin\\app\\revanced\\patches\\bilibili\\misc\\other\\patch\\TrialQualityPatch.kt",
    "patches\\src\\main\\kotlin\\app\\revanced\\patches\\bilibili\\misc\\protobuf\\fingerprints\\MossOkHttpInterceptorFingerprint.kt",
    "patches\\src\\main\\kotlin\\app\\revanced\\patches\\bilibili\\misc\\protobuf\\patch\\MossPatch.kt",
    "patches\\src\\main\\kotlin\\app\\revanced\\patches\\bilibili\\video\\player\\fingerprints\\IjkMediaPlayerOptionsFingerprint.kt",
    "patches\\src\\main\\kotlin\\app\\revanced\\patches\\bilibili\\video\\player\\patch\\OverridePlaybackSpeedPatch.kt",
    "patches\\src\\main\\kotlin\\app\\revanced\\patches\\bilibili\\video\\player\\patch\\RememberPlaybackSpeedPatch.kt",
    "patches\\src\\main\\kotlin\\app\\revanced\\patches\\bilibili\\video\\quality\\fingerprints\\PlayerSettingHelperFingerprint.kt",
    "patches\\src\\main\\kotlin\\app\\revanced\\patches\\bilibili\\video\\subtitle\\fingerprints\\RecordSelectedSubtitleFingerprint.kt",
    "patches\\src\\main\\resources\\bilibili\\host\\values\\strings.xml",
    "patches\\src\\main\\resources\\bilibili\\xml\\biliroaming_setting_filter_popular.xml",
    "README.md",
    "settings.gradle.kts",
]

SKIP = {".gitignore", "README.md", "AI-DISCLOSURE.md", "ATTRIBUTION.md",
        "CHANGES.md", "FILES.md", "LICENSE", "apply-overlay.py"}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("target", help="BiliRoamingX 源码树根目录")
    ap.add_argument("--dry", action="store_true", help="只打印，不写文件")
    a = ap.parse_args()

    here = os.path.dirname(os.path.abspath(__file__))
    if not os.path.isdir(a.target):
        sys.exit(f"目标目录不存在: {a.target}")

    n = miss = 0
    for rel in FILES:
        base = os.path.basename(rel)
        src = os.path.join(here, base)
        if not os.path.isfile(src):
            print(f"  ! 缺少 {base}")
            miss += 1
            continue
        dst = os.path.join(a.target, rel)
        print(f"  -> {rel}")
        if not a.dry:
            os.makedirs(os.path.dirname(dst), exist_ok=True)
            shutil.copy2(src, dst)
        n += 1

    print()
    print(f"{'将' if a.dry else '已'}放置 {n} 个文件" + (f"，{miss} 个缺失" if miss else ""))
    if a.dry:
        print("（--dry 模式，未实际写入）")


if __name__ == "__main__":
    main()