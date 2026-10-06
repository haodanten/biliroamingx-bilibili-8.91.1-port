# 文件清单（平铺名 → 源码树路径）

> **为什么是平铺的？**
> 本仓库是经 GitHub 网页端上传的。网页上传**不携带相对路径**，
> 所以所有文件都被拍平到了根目录。
>
> 想还原成可覆盖上游的目录结构，直接跑：
>
> ``bash
> python apply-overlay.py /path/to/BiliRoamingX
> ``
>
> 它会按下面的映射把文件放回正确位置。

共 **65** 个源码文件。

| 根目录文件名 | 在 BiliRoamingX 源码树中的路径 |
|---|---|
| `ci.yml` | `.github/workflows/ci.yml` |
| `pr.yml` | `.github/workflows/pr.yml` |
| `release.yml` | `.github/workflows/release.yml` |
| `FINGERPRINTS.md` | `FINGERPRINTS.md` |
| `gradle.properties` | `gradle.properties` |
| `libs.versions.toml` | `gradle/libs.versions.toml` |
| `build.gradle.kts` | `integrations/app/build.gradle.kts` |
| `DrawerPatch.java` | `integrations/app/src/main/java/app/revanced/bilibili/patches/drawer/DrawerPatch.java` |
| `JSONPatch.java` | `integrations/app/src/main/java/app/revanced/bilibili/patches/json/JSONPatch.java` |
| `PegasusPatch.java` | `integrations/app/src/main/java/app/revanced/bilibili/patches/json/PegasusPatch.java` |
| `FeedIndex.kt` | `integrations/app/src/main/java/app/revanced/bilibili/patches/okhttp/hooks/FeedIndex.kt` |
| `PlaybackSpeedPatch.kt` | `integrations/app/src/main/java/app/revanced/bilibili/patches/PlaybackSpeedPatch.kt` |
| `MossPatch.kt` | `integrations/app/src/main/java/app/revanced/bilibili/patches/protobuf/MossPatch.kt` |
| `RemoveVipSectionPatch.kt` | `integrations/app/src/main/java/app/revanced/bilibili/patches/RemoveVipSectionPatch.kt` |
| `SubtitleImportSavePatch.kt` | `integrations/app/src/main/java/app/revanced/bilibili/patches/SubtitleImportSavePatch.kt` |
| `BatchEditor.kt` | `integrations/app/src/main/java/app/revanced/bilibili/settings/BatchEditor.kt` |
| `FilterCommentByKeywordFragment.kt` | `integrations/app/src/main/java/app/revanced/bilibili/settings/fragments/FilterCommentByKeywordFragment.kt` |
| `FilterDynamicByKeywordFragment.kt` | `integrations/app/src/main/java/app/revanced/bilibili/settings/fragments/FilterDynamicByKeywordFragment.kt` |
| `FilterHomeRcmdByKeywordFragment.kt` | `integrations/app/src/main/java/app/revanced/bilibili/settings/fragments/FilterHomeRcmdByKeywordFragment.kt` |
| `FilterSearchKeywordsFragment.kt` | `integrations/app/src/main/java/app/revanced/bilibili/settings/fragments/FilterSearchKeywordsFragment.kt` |
| `Setting.kt` | `integrations/app/src/main/java/app/revanced/bilibili/settings/Setting.kt` |
| `Settings.kt` | `integrations/app/src/main/java/app/revanced/bilibili/settings/Settings.kt` |
| `BatchEditorTest.kt` | `integrations/app/src/test/java/app/revanced/bilibili/settings/BatchEditorTest.kt` |
| `FeedAdInfo.java` | `integrations/dummy/src/main/java/com/bilibili/adcommon/data/model/FeedAdInfo.java` |
| `IMenu.java` | `integrations/dummy/src/main/java/com/bilibili/app/comm/supermenu/core/IMenu.java` |
| `IMenuItem.java` | `integrations/dummy/src/main/java/com/bilibili/app/comm/supermenu/core/IMenuItem.java` |
| `MenuImpl.java` | `integrations/dummy/src/main/java/com/bilibili/app/comm/supermenu/core/MenuImpl.java` |
| `MenuItemImpl.java` | `integrations/dummy/src/main/java/com/bilibili/app/comm/supermenu/core/MenuItemImpl.java` |
| `BasicIndexItem.java` | `integrations/dummy/src/main/java/com/bilibili/pegasus/api/model/BasicIndexItem.java` |
| `Headers.java` | `integrations/dummy/src/main/java/okhttp3/Headers.java` |
| `HttpUrl.java` | `integrations/dummy/src/main/java/okhttp3/HttpUrl.java` |
| `Request.java` | `integrations/dummy/src/main/java/okhttp3/Request.java` |
| `build.gradle.kts` | `manager/build.gradle.kts` |
| `proguard-rules.pro` | `manager/proguard-rules.pro` |
| `AndroidManifest.xml` | `manager/src/main/AndroidManifest.xml` |
| `MainActivity.kt` | `manager/src/main/java/app/revanced/biliroaming/manager/MainActivity.kt` |
| `ic_launcher_foreground.xml` | `manager/src/main/res/drawable/ic_launcher_foreground.xml` |
| `activity_main.xml` | `manager/src/main/res/layout/activity_main.xml` |
| `ic_launcher.xml` | `manager/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` |
| `ic_launcher_round.xml` | `manager/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml` |
| `ic_launcher.xml` | `manager/src/main/res/mipmap-hdpi/ic_launcher.xml` |
| `colors.xml` | `manager/src/main/res/values/colors.xml` |
| `strings.xml` | `manager/src/main/res/values/strings.xml` |
| `styles.xml` | `manager/src/main/res/values/styles.xml` |
| `patches.json` | `patches/patches.json` |
| `PegasusPatch.kt` | `patches/src/main/kotlin/app/revanced/patches/bilibili/misc/json/patch/PegasusPatch.kt` |
| `MusicNotificationPatch.kt` | `patches/src/main/kotlin/app/revanced/patches/bilibili/misc/notification/patch/MusicNotificationPatch.kt` |
| `AutoLikePatch.kt` | `patches/src/main/kotlin/app/revanced/patches/bilibili/misc/other/patch/AutoLikePatch.kt` |
| `BiliLayoutAdjustResourcesPatch.kt` | `patches/src/main/kotlin/app/revanced/patches/bilibili/misc/other/patch/BiliLayoutAdjustResourcesPatch.kt` |
| `BLRoutePatch.kt` | `patches/src/main/kotlin/app/revanced/patches/bilibili/misc/other/patch/BLRoutePatch.kt` |
| `CacheRedirectPatch.kt` | `patches/src/main/kotlin/app/revanced/patches/bilibili/misc/other/patch/CacheRedirectPatch.kt` |
| `ForbidLiveRoomAutoFloatPatch.kt` | `patches/src/main/kotlin/app/revanced/patches/bilibili/misc/other/patch/ForbidLiveRoomAutoFloatPatch.kt` |
| `ForceCommentNavigablePatch.kt` | `patches/src/main/kotlin/app/revanced/patches/bilibili/misc/other/patch/ForceCommentNavigablePatch.kt` |
| `TrialQualityPatch.kt` | `patches/src/main/kotlin/app/revanced/patches/bilibili/misc/other/patch/TrialQualityPatch.kt` |
| `MossOkHttpInterceptorFingerprint.kt` | `patches/src/main/kotlin/app/revanced/patches/bilibili/misc/protobuf/fingerprints/MossOkHttpInterceptorFingerprint.kt` |
| `MossPatch.kt` | `patches/src/main/kotlin/app/revanced/patches/bilibili/misc/protobuf/patch/MossPatch.kt` |
| `IjkMediaPlayerOptionsFingerprint.kt` | `patches/src/main/kotlin/app/revanced/patches/bilibili/video/player/fingerprints/IjkMediaPlayerOptionsFingerprint.kt` |
| `OverridePlaybackSpeedPatch.kt` | `patches/src/main/kotlin/app/revanced/patches/bilibili/video/player/patch/OverridePlaybackSpeedPatch.kt` |
| `RememberPlaybackSpeedPatch.kt` | `patches/src/main/kotlin/app/revanced/patches/bilibili/video/player/patch/RememberPlaybackSpeedPatch.kt` |
| `PlayerSettingHelperFingerprint.kt` | `patches/src/main/kotlin/app/revanced/patches/bilibili/video/quality/fingerprints/PlayerSettingHelperFingerprint.kt` |
| `RecordSelectedSubtitleFingerprint.kt` | `patches/src/main/kotlin/app/revanced/patches/bilibili/video/subtitle/fingerprints/RecordSelectedSubtitleFingerprint.kt` |
| `strings.xml` | `patches/src/main/resources/bilibili/host/values/strings.xml` |
| `biliroaming_setting_filter_popular.xml` | `patches/src/main/resources/bilibili/xml/biliroaming_setting_filter_popular.xml` |
| `README.md` | `README.md` |
| `settings.gradle.kts` | `settings.gradle.kts` |

---

## 非源码文件（本来就在根目录，不需要移动）

| 文件 | 说明 |
|---|---|
| `README.md` | 项目说明 |
| `AI-DISCLOSURE.md` | **AI 作者声明（必读）** |
| `ATTRIBUTION.md` | 引用与来源 |
| `CHANGES.md` | 逐文件改动说明 |
| `FILES.md` | 本文件 |
| `LICENSE` | GPL-3.0 |
| `.gitignore` | — |
| `apply-overlay.py` | 还原目录结构 |
| `移植进度.md` | 移植记录（中文） |
| `BiliRoamingX-AI-代码审计报告.md` | 代码审计报告（中文） |
| `修复与构建报告.md` | 修复与构建报告（中文） |
| `DexSearch.java` | 自研 DEX 检索工具 |
| `DSH-文件卡片按钮改为打开资源管理器.ps1` | 附带产出（与本移植无关） |