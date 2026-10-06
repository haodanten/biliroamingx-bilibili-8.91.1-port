# nafumao/BiliRoamingX（BiliRoamingX-AI）代码审计报告

- 审计对象：https://github.com/nafumao/BiliRoamingX ，分支 `main`，HEAD 提交 `fix: CI use dist instead of build to avoid test compilation failures`（2026-06-15T17:00:06Z）
- 审计方式：下载 `main` tarball 与上游 https://github.com/BiliRoamingX/BiliRoamingX `main` tarball，做**逐文件 MD5 全量比对**，再对差异文件做人工审计
- 审计深度：**高风险**（构建/发布链路、跨进程设置持久化、CI 密钥）
- 结论：**有阻断风险**

---

## 0. 影响面先定边界（关键前提）

全量比对结果：fork 与上游的**差异面极小**，这是本次结论的基础。

| 分类 | 数量 | 文件 |
|---|---|---|
| 仅 fork 新增 | 15 | `.github/workflows/release.yml`、`FINGERPRINTS.md`、`manager/**`（12 个文件）、`integrations/app/src/test/**/SettingTest.kt` |
| 仅上游有 | 0 | — |
| 内容不同 | 13 | `.github/workflows/ci.yml`、`.github/workflows/pr.yml`、`gradle.properties`、`gradle/libs.versions.toml`、`integrations/app/build.gradle.kts`、`settings.gradle.kts`、`README.md`、`Setting.kt`、`PlaybackSpeedPatch.kt`、`FeedIndex.kt`、4 个 `Filter*ByKeywordFragment.kt` |

**其余 400+ 个 Kotlin/Java 文件、全部 `patches/**` 补丁逻辑、全部 JNI/CMake、全部资源文件与上游逐字节一致（MD5 相同）。**

- 未发现后门、C2、遥测回传、硬编码 IP、base64 载荷、动态下载执行。
- `Runtime.getRuntime().exec` 仅一处，用于 `logcat` 落盘（`integrations/app/src/main/java/app/revanced/bilibili/patches/main/ApplicationDelegate.java:329`）；`System.loadLibrary("biliroamingx")` 仅加载模块自带 so。
- 全部外联 URL 均为 B 站域名、GitHub API、微软/谷歌翻译接口。

> 所以"代码有问题"的答案不是"投毒"，而是：**fork 自己新加的那几处改动里，有一个会静默丢用户设置的阻断级缺陷、一条把 PAT 交给构建的 CI 高危链路、以及一个装不上的发布产物。**

---

## 1. 阻断风险

### R1 — `Setting.saveBatch` 无异常保护，一次抛错后全进程设置永久写入死 Editor

证据：`_audit/brx/integrations/app/src/main/java/app/revanced/bilibili/settings/Setting.kt:135-141`

```kotlin
fun saveBatch(block: () -> Unit) {
    val editor = prefs.edit()
    _batchEditor = editor          // 全局可变状态
    block()                        // ← 无 try/finally
    _batchEditor = null
    editor.apply()
}
```

配合 `Setting.kt:156-164`（以及 `184/211/238/265/292` 五处同构代码）：

```kotlin
override fun saveInternal(newValue: Boolean) {
    val editor = _batchEditor
    if (editor != null) {
        saveToEditor(newValue, editor)   // 写进那个 Editor
    } else { ... }
}
```

失败模式：`block()` 内任何一次异常（Fragment 里读控件状态、`getKeywords()` 返回异常、OOM）都会让 `_batchEditor` **永久保持非空**。此后该进程内**每一次** `Settings.xxx.save()` 都写进这个已经被抛弃、永远不会 `apply()` 的 Editor —— 用户在设置页做的所有修改全部静默丢弃，且没有任何报错，直到进程重启。

影响面：`Setting.saveBatch` 被 4 个过滤设置页调用（`FilterHomeRcmdByKeywordFragment.kt:129`、`FilterCommentByKeywordFragment.kt:69`、`FilterSearchKeywordsFragment.kt:57`、`FilterDynamicByKeywordFragment.kt:54`），而 `Setting.save` 的调用点遍布 `settings/fragments/**`、`patches/protobuf/hooks/**`、`patches/okhttp/hooks/**`、`patches/main/MainActivityDelegate.kt`、`account/Accounts.kt`。

最小修复：
```kotlin
fun saveBatch(block: () -> Unit) {
    val previous = _batchEditor
    val editor = prefs.edit()
    _batchEditor = editor
    try {
        block()
    } finally {
        _batchEditor = previous
        editor.apply()
    }
}
```
验收：单测注入一个必然抛异常的 `saveBatch` block，断言异常之后 `BooleanSetting("x", false).save(true)` 仍能落到 prefs。

### R2 — 同一改动把线程不安全的 `SharedPreferences.Editor` 变成跨线程全局共享对象

证据：`Setting.kt:127-129` 用 `@JvmField @Volatile var _batchEditor` 做全局槽位，`saveBatch` 没有 `@Synchronized`，也没有任何锁。

`android.content.SharedPreferences.Editor` 官方约定**不是线程安全的**（`SharedPreferencesImpl.EditorImpl` 内部 `mModified` 是普通 `HashMap`）。而设置保存确实来自多线程：

- UI 线程：`settings/fragments/FilterHomeRcmdByKeywordFragment.kt:129` 等 4 处 `saveBatch`
- OkHttp 回调线程：`patches/okhttp/hooks/GarbSuitDetail.kt:49`
- protobuf/gRPC 回调线程：`patches/protobuf/hooks/PlayURLPlayConfEdit.kt:19` 与 `:21`

即"UI 线程正持有一个 Editor 在跑 `block()`"期间，hook 线程调用 `Settings.LosslessEnabled.save(...)` 会直接写进同一个 Editor。两条后果：Editor 内部 map 并发写入（数据竞争/脏状态）；以及这些写入被推迟到 UI 线程 `block()` 结束时才统一 apply，时序不再可控。

对照上游 `Setting.kt`：每次 `save()` 都是 `prefs.edit { ... }`，即独立 Editor + 立即 `apply()`，天然规避上述两个问题。**这是 fork 新引入的回退。**

### R3 — 该改动声称修复的竞态在原实现下不成立

README `:122` 与 `Setting.kt:131-134` 声称修复"#754 单次保存多个设置时的竞态数据丢失"。但：

- `prefs`（`Setting.kt:75`，`CrossProcessPreferences.get(Constants.PREFS_SETTING)`）是**单例缓存**（`CrossProcessPreferences.kt:76-82` 用 `prefsCache` 复用），所有 Setting 共享同一实例。
- 主进程路径 `prefs.edit{}` → `EditorImpl` 直接委托真实 `SharedPreferencesImpl.EditorImpl`，`apply()` 提交的是**整张内存 map 快照**（`CrossProcessPreferences.kt:282-288, 364-370`），后一次 apply 不会丢掉前一次的键。
- 非主进程路径 `EditorImpl` 按 key 累积到 `editPairs`，`apply()` 走一次广播全量下发（`CrossProcessPreferences.kt:285-287, 364-370`），同样不会丢。

结论：这是一个**修了不存在的 bug、引入了 R1/R2 两个真 bug** 的改动。上游代码不存在该数据丢失。

### R4 — 新增的单测永不执行，且本身跑不起来

证据链：

1. `integrations/app/build.gradle.kts:142` 新加 `testImplementation("junit:junit:4.13.2")`，并提交了 `integrations/app/src/test/java/app/revanced/bilibili/settings/SettingTest.kt`（238 行、14 个用例）。
2. 但 `.github/workflows/ci.yml:44` 与 `release.yml:52` 跑的是 `./gradlew ... dist :manager:assembleRelease` —— `dist` 只依赖 `buildDexJar` + `generatePatchesFiles`（`_audit/brx/patches/build.gradle.kts:80-84`），**不含 `test`/`check`**。
3. 仓库自己的提交信息写得很直白：`fix: CI use dist instead of build to avoid test compilation failures` —— 上游 `ci.yml:50` 原本就是 `dist`，fork 曾有版本用 `build`，因为**测试编译失败**才退回 `dist`。

测试本身也有硬伤：`SettingTest.kt:26-28` 用
```kotlin
val modifiersField = java.lang.reflect.Field::class.java.getDeclaredField("modifiers")
modifiersField.setInt(prefsField, prefsField.modifiers and Modifier.FINAL.inv())
```
来去掉 `final`。JDK 12 起 `Field.class` 的 `modifiers` 字段被 `Reflection.registerFieldsToFilter` 过滤，`getDeclaredField("modifiers")` 直接抛 `NoSuchFieldException`；CI 用的是 temurin **JDK 17**（`ci.yml:26`）。`@Before` 必然抛异常 → 14 个用例全部 error。

另外用例本身是自证式的：名为 `saveBatch prevents race condition by single apply` 的用例只调了一次 `saveBatch` 再断言值相等，**没有任何并发**，无法覆盖 R1/R2。

结论：**该仓库当前处于"零有效测试覆盖"状态**，且这是被 CI 配置主动规避的结果。

---

## 2. 高风险

### R5 — CI 把个人访问令牌（PAT）交给 Gradle 构建

证据：

- `.github/workflows/ci.yml:38-43`
  ```yaml
  env:
    GITHUB_TOKEN: ${{ secrets.GH_PAT }}
  run: ./gradlew --no-daemon -Dorg.gradle.jvmargs=-Xmx2g \
        -Pgpr.user=min09577 \
        -Pgpr.key=${{ secrets.GH_PAT }} \
        dist :manager:assembleRelease
  ```
- `.github/workflows/pr.yml:43-45` 同样写法
- `.github/workflows/release.yml:45-52` 同样写法，且该 workflow 权限为 `contents: write`（`release.yml:8-10`）

对照上游 `.github/workflows/ci.yml:47-50`：`GITHUB_TOKEN: ${{ github.token }}`，**不传 PAT，也不把凭据放进 Gradle 属性**。

风险：

1. `-Pgpr.key=...` 会出现在 Gradle 进程命令行与 Gradle 日志/构建扫描中；`env GITHUB_TOKEN` 则被子进程全量继承。
2. Gradle 构建期会执行 `build-logic/**`、全部 `build.gradle.kts`、依赖 artifacts 携带的 build script。任一处被投毒即可读走 `GITHUB_TOKEN` 环境变量 → **以该 PAT 的权限接管账号**（`release.yml` 已声明 `contents: write`，说明该 token 具备写权限）。
3. `ci.yml` 由 `pull_request` 触发（`ci.yml:6-7`）。fork 来源的 PR 拿不到 secret，但**同仓库分支发起的 PR 会拿到** `secrets.GH_PAT`。`pr.yml` 同样。
4. 该 PAT 同时用于发布（`softprops/action-gh-release@v2`），一旦泄露等于仓库写权限外泄。

最小修复：所有 workflow 改回 `${{ github.token }}`，并显式声明最小 `permissions`；确需读私有 GitHub Packages 时改用 `${{ secrets.GITHUB_TOKEN }}`（GitHub Packages 的 read 权限可由 workflow token 提供）；不要在命令行参数里传密钥，改写 `~/.gradle/gradle.properties` 或 `GRADLE_OPTS` 之外的注入方式亦不能解决问题——关键是别把长期凭据交给构建。

### R6 — 发布的 Manager APK 未签名，装不上

证据：

- `_audit/brx/manager/build.gradle.kts`（全文 27 行）只有 `android { namespace / compileSdk / defaultConfig / buildFeatures }`，**没有 `buildTypes`、没有 `signingConfig`**。AGP 的 `release` build type 在未指定 `signingConfig` 时为 `null`，产物是 `manager-release-unsigned.apk`。
- `.github/workflows/release.yml` 全文没有 `keytool` / `jarsigner` / `apksigner` 步骤，却在 `:54-62` 收集 `manager/build/outputs/apk/release/*.apk`，并在 `:89-91` 作为 Release 资产上传。
- 该仓库自己的提交历史记录过同一个坑并修过：`v1.23.7: Generate debug keystore in CI for APK signing — CRITICAL: The CI runner had no Android debug keystore, so the APK was produced completely unsigned`、`v1.23.6: Fix packaging excludes stripping signing files ... making the APK unsigned and uninstallable`。
- 而倒数第二个提交 `fix: Remove custom signing config from Manager (use AGP default debug key)` 认为"AGP 会给 release 用 debug key"——**这是错的**，AGP 只会给 `debug` build type 自动挂 debug signingConfig，`release` 不会。等于把已修好的问题退了回去。

影响：Release 页面上挂着"Manager APK（操作指引）"，用户下载安装会得到 `INSTALL_PARSE_FAILED_NO_CERTIFICATES`。

最小修复：给 `manager/build.gradle.kts` 补
```kotlin
android {
    signingConfigs { create("manager") { storeFile = file("../debug.keystore"); storePassword = "android"; keyAlias = "androiddebugkey"; keyPassword = "android" } }
    buildTypes { release { signingConfig = signingConfigs.getByName("manager") } }
}
```
并在 workflow 里先 `keytool -genkeypair` 生成该 keystore；或直接把 `:manager:assembleDebug` 的产物作为发布资产。

---

## 3. 中危 / 文档与实际严重不符

### R7 — README 指向的仓库不存在

`README.md` 中 `min09577/BiliRoamingX` 出现 4 次：

| 位置 | 内容 | 实测 |
|---|---|---|
| `README.md:7` | CI badge 指向 `min09577/BiliRoamingX/actions` | 死链 |
| `README.md:63` | `git clone --recurse-submodules https://github.com/min09577/BiliRoamingX.git` | **GitHub API 返回 `Not Found`** |
| `README.md:79` | "去 Releases 下载这三个文件" → `min09577/BiliRoamingX/releases/latest` | 死链 |
| `README.md:20` | AI 维护者 `@min09577` | 仅作者信息，无碍 |

实测：`api.github.com/repos/min09577/BiliRoamingX` → `{"message": "Not Found"}`；`nafumao/BiliRoamingX` 创建于 2026-08-02（晚于内容提交时间 2026-06-15），2 star、0 release。也就是说 README 是从别处整体搬过来的，**没有随仓库改名同步**，用户照着 README 一步都走不通。

### R8 — 打包命令与上游不一致，且丢掉了签名级别参数

- 上游 `README.md:64`：`java -jar revanced-cli.jar patch --merge integrations.apk --patch-bundle patches.jar --signing-levels 1,2,3 bilibili.apk`
- fork `README.md:96`：`java -jar revanced-cli.jar patch -b patches.jar -m integrations.apk bilibili.apk`

差异：长选项改成短选项（`--patch-bundle` → `-b`）、**去掉了 `--signing-levels 1,2,3`**。签名级别是否影响安装取决于所用 revanced-cli 版本的默认值，本次审计**未能实测**（需要真实 APK 与 CLI），列为需验证项而不是阻断项——但既然上游明确带这个参数，fork 单方面省略且无任何验证证据，属于不可接受的回退。

另外命令里的文件名对不上实际产物：`patches/build.gradle.kts:22` 设 `archiveBaseName = "${rootProject.name}-${project.name}"` → 产物是 `BiliRoamingX-AI-patches-1.23.5.jar`；`integrations/app/build.gradle.kts:94` → 产物是 `BiliRoamingX-AI-app-1.23.5.apk`。README 却让用户直接 `-b patches.jar -m integrations.apk`，不重命名就会 `file not found`。

### R9 — `FINGERPRINTS.md` 自称"自动生成"，统计数字与实际不符

用可复现的脚本重新统计（脚本已附在修正后的 `FINGERPRINTS.md` 文末）：

| 指标 | 文档原值 | 实测值 |
|---|---|---|
| `fingerprints/` 下的 .kt 文件 | 87 | **102** |
| Fingerprint 声明（含 `open`/`abstract` 基类） | 103 | **119** |
| 活跃引用 | ~82 | 109（119 − 3 个基类同文件被继承 − 7 个基类） |
| 孤儿 | ~5 | **3**（精确） |
| 引用指纹的 Patch | ~35 | **49**（Patch 文件共 87 个） |

内容错误：漏收 `TheseusOnOgvDownloadFingerprint`（被 `CacheRedirectPatch.kt:30` 使用）；
`OgvSearchResultFingerprint (×3)` 一行掩盖了 `OgvSearchResultFingerprint` /
`OgvSearchResultV2Fingerprint` / `BangumiSearchResultFingerprint` 三个不同对象；
分节标题的计数也有三处对不上表体（theme 8→9、drawer 3→4、other 22→25、layout 文件数 2→3）。

另：文档声称"自动生成"，但仓库中不存在任何生成脚本，该说法无法复现。

> **纠正**：本节初版曾写"`OnOgvDownloadFingerprint` 实际叫 `TheseusOnOgvDownloadFingerprint`"——
> 这是初版脚本的误报。`patches/.../fingerprints/OnOgvDownloadFingerprint.kt` 里
> **两个对象都真实存在**（第 6 行 `OnOgvDownloadFingerprint`、第 16 行
> `TheseusOnOgvDownloadFingerprint`），文档缺的是后者。特此更正。

值得肯定的是：文档列出的 3 个"孤儿 Fingerprint"（`PlayerFullStoryWidgetFingerprint`、
`GeminiPlayerFullStoryWidgetFingerprint`、`PlayerContainerFingerprint`）经全仓库引用扫描
**核实完全正确**——这 3 个是唯二核实为真的部分。

结论：这是一份**人工包装成"自动生成"的文档**，统计口径不可信，不能作为维护该项目的决策依据。

### R10 — `PlaybackSpeedPatch` 引入死代码与无开关日志

- `_audit/brx/.../patches/PlaybackSpeedPatch.kt:74` 声明 `@Volatile private var lastAppliedSpeed: Float = 0f`，`:87` 赋值，**全仓库无任何读取点** → 死代码，且它被声明为"修复浮点精度问题"的一部分，实际与该问题无关。
- `:88 / :100 / :103 / :107 / :128 / :132` 新增 6 处 `Log.d(TAG, ...)`，**未受 `Settings.Debug` 保护**（对比 `ApplicationDelegate.java:317` 的 logcat 落盘是有 `Settings.Debug` 开关的）。生产环境每次应用播放速度都会写 logcat。
- 该文件真正的行为变化只有一处：`:124` 把上游 `speed == 2.0f || speed == 3.0f` 改成 `(speed > 1.99f && speed < 2.01f) || (speed > 2.99f && speed < 3.01f)`。这是**合理的小改进**（避开浮点相等比较），但被包装成了"修复视频切换后浮点精度失效"，属于描述夸大。

### R11 — `FeedIndex` URL 匹配放宽，属未验证的启发式改动

- 上游：`url.contains("/x/v2/feed/index?")`
- fork（`_audit/brx/.../patches/okhttp/hooks/FeedIndex.kt`）：`url.contains("/x/v2/feed/index")`

去掉 `?` 会把 `/x/v2/feed/indexXXX` 这类同前缀路径一并纳入。改动的动机（返回手势刷新的 URL 可能不带 query）合理，但没有任何测试或抓包证据证明该端点上不存在其他同前缀接口。列为需验证项。

---

## 3.5 构建阶段新发现（在真实执行"修复→构建→打补丁"全流程时暴露）

这两条在纯静态审计里看不出来，是把项目真正跑一遍才撞上的，严重度高于前面多数条目。

### R12 — `BiliLayoutAdjustResourcesPatch` 写死不存在的资源名，导致**整个补丁流程在最后一步崩掉**

证据：`_audit/brx/patches/src/main/kotlin/app/revanced/patches/bilibili/misc/other/patch/BiliLayoutAdjustResourcesPatch.kt:222-229` 与 `:355-362`

这个 Patch 会把宿主布局 `res/layout/theseus_playlist_title.xml` **整个覆盖**成硬编码布局，其中写死了：

```xml
android:text="@string/theseus_playlist_default_order"
```

而 B站 8.91.1 的资源表里**没有**这个字符串（该槽位真名是 `string/video_unite_global_string_22`，ID `0x7f117943`）。于是在 `aapt2 link` 阶段：

```
警告: .../apk/res/layout/theseus_playlist_title.xml:96: error:
      resource string/theseus_playlist_default_order not found.
警告: error: failed linking file resources.
brut.androlib.exceptions.AndrolibException: ... could not exec (exit code = 1)
```

**后果**：不是"某个功能没生效"，而是**整个补丁进程失败、一个 APK 都产不出来**。
我实测：69 个 Patch 全部执行成功后，卡在 `Compiling modified resources` 直接退出。

实证过程：
1. 全量扫描解码后布局的 4071 个 `@string/` 引用，与 `res/values/**/public.xml` + `strings.xml` 比对 → **恰好 1 个悬空引用**，就是它。
2. `aapt2 dump xmltree --file res/mJk.xml bilibili-orig.apk` 显示原始布局该属性是 `@0x7f117943`（合法 ID）。
3. `aapt2 dump resources` 解析 `0x7f117943` → `string/video_unite_global_string_22`，资源表完好。
4. 结论：悬空引用是**补丁自己造出来的**，不是宿主的锅，也不是 apktool 的锅。

修复（已落地）：改为在替换布局里使用字面量，并保留原字符串名作为可读注释；该 `TextView` 所在的 `order_layout` 在两个替换布局里都是 `android:visibility="gone"`，不影响显示与多语言。

### R13 — 构建完全无法复现：依赖落在私有 GitHub Packages 后面，且没有任何预编译产物

我实测的硬阻断：

| 依赖 | 仓库 | 结果 |
|---|---|---|
| `kofua.app.revanced:revanced-patcher:19.3.1.2` | `maven.pkg.github.com/zjns/registry` | **401** |
| `kofua.app.revanced:apktool-lib:2.12.0.1` | 同上 | **401** |
| `app.revanced:multidexlib2:3.0.3.r3` | `maven.pkg.github.com/revanced/registry` | **401** |
| Maven Central / JitPack 上的同名/近似坐标 | — | 404 / API 不兼容 |

且：
- `BiliRoamingX/BiliRoamingX` releases = **0**，tags 到 `v1.23.3`
- `BiliRoamingX/BiliRoamingX-PreBuilds`（README 里引用的下载源）= **404 Not Found**
- `nafumao/BiliRoamingX` releases = **0**，`actions/artifacts` = **0**，`actions/runs` = **0**

也就是说：**照着仓库文档，任何人都无法拿到或构建出可用的 `patches.jar`**。`README.md:64` 说 `./gradlew dist` 即可，实际会因 401 直接失败，而 README 从没提过需要 `read:packages` 的 PAT。

（本次审计为绕过该阻断，从公开源码重建了整条链：`zjns/Apktool@9f052712` → `kofua.app.revanced:apktool-lib:2.12.0.1` → `zjns/revanced-patcher@0767b2e6` → `kofua.app.revanced:revanced-patcher:19.3.1.2`，全部安装到 `mavenLocal` 后 `./gradlew dist` 才跑通。**未修改任何构建脚本**。）

最小修复建议：把 `revanced-patcher` / `apktool-lib` / `multidexlib2` 发布到 Maven Central 或改为 JitPack 坐标；至少要在 README 与 CI 里明确写出 GitHub Packages 鉴权要求。

---

## 3.6 真机运行阶段发现（在 Redmi 23078RKD5C / Android 16 上实测崩溃）

出包装机后应用必崩，从设备 dropbox + logcat 抓到两个真实崩溃点。

### R14 — `DrawerPatch` 空 View NPE，开侧边栏后**每次启动必崩**

设备 dropbox 原文：

```
java.lang.RuntimeException: Unable to start activity ComponentInfo{tv.danmaku.bili/tv.danmaku.bili.MainActivityV2}:
  java.lang.NullPointerException: Attempt to invoke virtual method
  'android.view.ViewGroup$LayoutParams android.view.View.getLayoutParams()' on a null object reference
    at app.revanced.bilibili.patches.main.MainActivityDelegate.onCreate(:53)
    at tv.danmaku.bili.MainActivityV2.onCreate(BL:238)
```

根因：`integrations/.../patches/drawer/DrawerPatch.java:28`

```java
ViewGroup contentView = activity.findViewById(Window.ID_ANDROID_CONTENT);
View view = contentView.getChildAt(0);   // ← 8.91.1 此处返回 null
...
drawerLayout.addView(view, 0, view.getLayoutParams());  // ← 第 32 行 NPE
```

宿主在注入点被调用时 ContentView 还没有子 View，`getChildAt(0)` 返回 null，第 32 行立刻 NPE，
`MainActivityV2` 启动失败。因为该设置被持久化，**之后每次启动都会崩** —— 与用户描述
"改完某项设置、返回或重启后闪退" 完全一致（`Settings.Drawer` 默认 false，开启后才触发）。

修复：拆出 `installDrawer(activity, attempt)`，先判空，子 View 未就绪时 `postDelayed` 重试
（最多约 1 秒），期间用户关掉侧边栏会自然退出。

### R15 — `JSONPatch` 硬引用宿主已移除的类，`NoClassDefFoundError` 杀进程

设备 dropbox 原文：

```
java.lang.NoClassDefFoundError: Failed resolution of:
  Lcom/bilibili/ad/adview/videodetail/danmakuv2/model/DmAdvert;
    at app.revanced.bilibili.patches.json.JSONPatch.b(:128)
    at app.revanced.bilibili.patches.json.JSONPatch.parseObjectHook(:1)
    at com.alibaba.fastjson.JSON.parseObject(BL:39)
    at com.mall.common.context.u.u(BL:38)
    ...
Caused by: java.lang.ClassNotFoundException: ...DmAdvert
```

`JSONPatch.java:9` 直接 `import` 了该宿主类，`instanceof DmAdvert` 在执行时触发类解析；
8.91.1 里 `DmAdvert` 与 `Dm` **都已不存在**。异常从 `JSON.parseObject` 钩子逃出后被 kotlinx
协程判为致命错误（`CoroutinesInternalError`）→ 直接杀进程。商城 bootstrap 每次启动都会走
JSON 解析，因此这是**启动级**崩溃。

修复：删掉两个 import，改用 `Class.forName` + `Lazy` 反射（`dmAdvertClass` / `dmAdvertGetAds`），
并提供 `isDmAdvert()` 守卫 —— 宿主没有这个类时整段逻辑静默跳过，不再让钩子崩掉。

### R16 — 根因：这个 fork 复活的是**两年前的上游快照**，与 B站 8.91.1 严重错代

- 上游 `BiliRoamingX/BiliRoamingX` 的 `main` 最后一次真实提交是 **2024-09-24**
- 其版本门限最高只到 `ge8_9_0()`（B站 **8.9.0**，versionCode 8090000）
- 实测宿主是 **8.91.1**（versionCode 8911100），比它新 1.5 年

由此产生三类必然故障：

| 现象 | 数量 | 说明 |
|---|---|---|
| 指纹（Fingerprint）失效 | 13 个补丁 | 目标方法签名已变，功能直接不生效 |
| 宿主类引用过期 | 静态扫出 18 处 | 类被移除/换包名（如 `adcommon.basic.model` → `adcommon.data.model`）→ `NoClassDefFoundError` / `NoSuchFieldError` |
| 注入时机变化 | R14 | 宿主生命周期时序改变 → NPE |

**结论：这套补丁集并非"B站 8.91.1 可用"，它只对 ~8.9.0 那一代宿主有效。**

本次已修掉两个必崩点，使 8.91.1 上可以正常启动与使用大部分功能；但 13 个失效补丁对应的功能
仍然缺失，且 18 处过期类引用中，凡处于"用户启用了相关功能才会走到"的路径（如推荐流卡片类型
过滤、视频详情、OGv 搜索），打开后仍可能崩。彻底的解法是把宿主换回 ~8.9.0 版本。

---

## 4. 上游遗留问题（非本 fork 引入，供参考，不计入本次结论）

以下问题在上游 `BiliRoamingX/BiliRoamingX` 中完全一致，属于 fork 之前的既有状态：

1. `settings.gradle.kts:24-31` 依赖第三方个人 GitHub Packages 源 `https://maven.pkg.github.com/zjns/registry`（与上游逐字相同），且 `patches` 的 `revanced-patcher` 版本 `19.3.1.2` 是 `kofua.app.revanced:revanced-patcher`（BiliRoamingX 自己的 patcher 分支），不是官方 ReVanced 版本。GitHub Packages 强制鉴权 → **fork PR 无 secret 时构建必然失败**。
2. README 引导用户下载第三方定制 CLI `zjns/revanced-cli.jar`（上游 `README.md:62` 同样如此）。
3. 所有 `uses:` 均未 pin commit SHA（`actions/checkout@v4` 等），存在 tag 漂移风险。
4. `integrations/app/build.gradle.kts:84` release 使用 debug 签名配置 —— 上游同样如此，对"merges 进宿主 App"的模块 APK 是设计如此，不算缺陷。

---

## 5. 验证证据清单（实际执行过的动作）

| 动作 | 结果 |
|---|---|
| 下载 fork `main` tarball（26.5 MB）并解包 | 成功 |
| 下载上游 `main` tarball（26.5 MB）并解包 | 成功 |
| 全仓库逐文件 MD5 比对 | 仅 15 新增 + 13 修改，见第 0 节 |
| `Get-Content`/UTF-8 字节校验差异文件编码 | 证实差异文件为合法 UTF-8（控制台 GBK 显示乱码已排除，避免误报） |
| 全仓 grep `Runtime.exec` / `System.load*` / `DexClassLoader` / 硬编码 URL | 无可疑外联与加载 |
| GitHub API 查询 `min09577/BiliRoamingX` | `Not Found`（R7） |
| GitHub API 查询官方 `ReVanced/revanced-patcher` releases | 仅 19.3.1 / 20.0.0 / 21 / 22，无 `19.3.1.2`；因此确认本项目用的是 `kofua.app.revanced` 私有坐标（避免误报） |
| 解析 `FINGERPRINTS.md` 表格名 vs 源码 `object *Fingerprint*` | 见 R9 |
| 全局搜索 `lastAppliedSpeed` / `saveBatch` / `_batchEditor` 引用点 | 见 R1/R2/R10 |
| **未执行**：`./gradlew dist`、`./gradlew test`、APK 实机安装、revanced-cli 实际打补丁 | 本机无 Git/JDK/Android SDK，且需真实 B 站 APK 与签名环境 |

未覆盖范围：`assets/images/*` 截图、`.idea/**`（IDE 工程文件）、`patches/src/main/resources/**` 资源内容（因与上游逐字节一致故未逐字审阅）、release 产物的实际 APK 字节（本次未下载 Release 资产）。

---

## 6. 修复优先级

| 顺序 | 项 | 归属 |
|---|---|---|
| P0 | R1 `saveBatch` 补 `try/finally` + 回滚 `previous` | Kotlin/Android |
| P0 | R5 CI 换回 `${{ github.token }}`，收敛 `permissions` | DevSecOps |
| P0 | R6 Manager 补签名配置或改发 debug 产物 | Android 构建 |
| P1 | R2 `_batchEditor` 加锁或改为传参（去掉全局可变状态） | Kotlin/Android |
| P1 | R4 修好测试并让 CI 跑 `test`（不要用 `dist` 掩盖） | 测试工程 |
| P1 | R3 回退这次"修竞态"改动，改为用证据复现原问题 | 代码审计 |
| P2 | R7/R8 README 重写：仓库 URL、产物名、签名级别参数 | 文档 |
| P2 | R9 修正或删除 `FINGERPRINTS.md` 的统计口径 | 文档 |
| P3 | R10 删除 `lastAppliedSpeed`，日志加 `Settings.Debug` 开关 | 代码清理 |
| P3 | R11 补抓包/测试证据再决定是否保留放宽 | 测试工程 |

---

## 7. 最终结论

**有阻断风险。**

安全侧是好消息：这个 fork **没有**注入后门、没有回传、没有供应链投毒，99% 的源码与上游 `BiliRoamingX` 逐字节相同。

问题全部集中在 AI 自行"迭代"的那 13 个文件里，而且特征高度一致：**修不存在的 bug、引入真 bug、把已知问题退回、用配置回避失败、把文档写得比代码漂亮**。

最严重的是 R1/R2（用户设置静默丢失）与 R5（长期 PAT 交给构建）、R6（发布产物装不上）——这三条都直接影响真实用户和仓库安全，建议在合并任何新功能之前先修。
