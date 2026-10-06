package app.revanced.patches.bilibili.video.subtitle.fingerprints

import app.revanced.patcher.fingerprint.MethodFingerprint

object RecordSelectedSubtitleFingerprint : MethodFingerprint(
    // 8.91.1 适配:
    //  - 原来 4 个字符串集中在同一方法, 现在 "danmaku_subtitle_lan_vice" 与
    //    "danmaku_user_changed_bilingual" 被拆到了 SubtitleKeyConfig 等别的类,
    //    目标方法里只剩这两个。
    //  - 参数从 (Z)V 变成 (ZZZ)V。
    // 目标: Ltv/danmaku/biliplayerv2/service/interact/biz/InteractLayerService;->recordSelectedSubtitle(ZZZ)V
    strings = listOf(
        "danmaku_subtitle_multi",
        "danmaku_subtitle_lan_main",
    ),
    parameters = listOf("Z", "Z", "Z"),
    returnType = "V",
)
