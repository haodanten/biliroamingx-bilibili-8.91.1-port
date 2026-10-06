# 改动清单（相对上游 BiliRoamingX 1.23.5）

本仓库是**叠加层（overlay）**，不是完整工程。
请先取得上游源码，再把 `src/` 覆盖上去。

```
# 1. 取上游
git clone https://github.com/BiliRoamingX/BiliRoamingX
cd BiliRoamingX
git checkout <对应 1.23.5 的提交>

# 2. 覆盖本仓库的 src/
cp -r /path/to/this-repo/src/. .

# 3. 构建
./gradlew dist
```

> 上游的 `integrations/libs/Dobby` 是 **git 子模块**（https://github.com/LSPosed/Dobby.git），
> 本仓库**不含**它，克隆上游后需要 `git submodule update --init --recursive`。

---

## 修改的文件（39）

### 字节码补丁（`patches/`）

| 文件 | 改动 |
|---|---|
| `misc/protobuf/patch/MossPatch.kt` | **核心修复**：`blockingUnaryCall` 从"方法末尾追加 after hook"改为 **wrapper 模式** —— 原方法整体改名保留，新方法负责 before → 调用原方法(try/catch) → after。修复 38 个响应侧 hook 全部失效 |
| `misc/json/patch/PegasusPatch.kt` | 新增 `CardClickProcessor$e.onMenuList` / `handleClick` 两个 hook（热门 ⋮ → 屏蔽面板） |
| `misc/protobuf/fingerprints/MossOkHttpInterceptorFingerprint.kt` | 适配 8.91.1 的新锚点 |
| `misc/other/patch/*.kt`（AutoLike / BiliLayoutAdjustResources / BLRRoute / CacheRedirect / ForbidLiveRoomAutoFloat / ForceCommentNavigable / TrialQuality） | 8.91.1 指纹重新锚定 |
| `misc/notification/patch/MusicNotificationPatch.kt` | AB 开关被移除，改为无条件启用 |
| `video/player/*`、`video/quality/*`、`video/subtitle/*` | 指纹重新锚定；`OverridePlaybackSpeedPatch` 修复 `goto` 跳过 `const/high16 v4` 导致的 `VerifyError` |
| `resources/bilibili/host/values/strings.xml` | 新增「分享替换为屏蔽」文案 |
| `resources/bilibili/xml/biliroaming_setting_filter_popular.xml` | 热门设置页新增开关项 |

### 集成（`integrations/`）

| 文件 | 改动 |
|---|---|
| `patches/json/PegasusPatch.java` | **核心**：热门卡片注入屏蔽项；补齐热门过滤路径缺失的分区/分类维度；热门 ⋮ 面板构建与点击处理；调用宿主原版面板的完整实现（未接通）；`@Keep` 标注 |
| `patches/json/JSONPatch.java` | `BiliSpace.ad` 等被移除字段改为反射写入；`customizeSpace` 调用点加兜底 |
| `patches/protobuf/MossPatch.kt` | Moss hook 分发点 |
| `patches/drawer/DrawerPatch.java` | 8.91.1 空指针修复 |
| `patches/RemoveVipSectionPatch.kt` | `HomeUserCenterFragment.onSkinChange` 空指针修复 |
| `patches/PlaybackSpeedPatch.kt`、`SubtitleImportSavePatch.kt`、`okhttp/hooks/FeedIndex.kt` | 适配 |
| `settings/Settings.kt` | 新增 `PopularShareToBlock` 设置项 |
| `settings/Setting.kt` | 批量写设置的 `BatchEditor` |
| `settings/fragments/Filter*Fragment.kt` | 适配 |
| `dummy/.../pegasus/api/model/BasicIndexItem.java` | 补 `threePoint` 字段 |
| `dummy/.../adcommon/data/model/FeedAdInfo.java` | 因宿主类移动而**新建**（旧路径 `adcommon/basic/model/FeedAdInfo.java` 已删除） |

### 构建与其它

| 文件 | 改动 |
|---|---|
| `gradle.properties`、`gradle/libs.versions.toml`、`integrations/app/build.gradle.kts`、`settings.gradle.kts` | 构建配置；新增 `manager` 模块 |
| `.github/workflows/ci.yml`、`pr.yml` | CI 调整 |
| `README.md` | 上游 README 微调 |

---

## 新增的文件

| 文件 | 说明 |
|---|---|
| `integrations/dummy/.../comm/supermenu/core/IMenuItem.java`<br>`IMenu.java`<br>`MenuImpl.java`<br>`MenuItemImpl.java` | **编译桩** —— 宿主 supermenu 的签名，用于让集成能编译；签名逐个从宿主 smali 对齐 |
| `integrations/app/.../settings/BatchEditor.kt` | 批量写设置 |
| `integrations/app/src/test/.../BatchEditorTest.kt` | 单测 |
| `integrations/dummy/.../okhttp3/Headers.java`<br>`HttpUrl.java`<br>`Request.java` | okhttp 编译桩 |
| `manager/`（整个模块） | 补丁管理器 App |
| `patches/patches.json` | 补丁清单 |
| `.github/workflows/release.yml` | 发布流水线 |
| `FINGERPRINTS.md` | 指纹记录 |

---

## 删除的文件

| 文件 | 原因 |
|---|---|
| `integrations/dummy/.../adcommon/basic/model/FeedAdInfo.java` | 宿主类移动到 `adcommon.data.model`，旧桩随之迁移 |

---

## 不包含的内容

- ❌ **修改后的哔哩哔哩 APK**
- ❌ **哔哩哔哩原始 APK**
- ❌ 任何从哔哩哔哩客户端提取的 DEX / 资源 / native 库

理由见 [README.md](README.md#-本仓库不包含什么重要)。
