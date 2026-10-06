# 引用与来源

## 上游项目

本仓库是 **[BiliRoamingX](https://github.com/BiliRoamingX/BiliRoamingX)** 的衍生作品，
未经上游作者审阅或认可。

| 项目 | 地址 | 许可 |
|---|---|---|
| BiliRoamingX | https://github.com/BiliRoamingX/BiliRoamingX | **GPL-3.0** |
| BiliRoamingX 预构建 | https://github.com/BiliRoamingX/BiliRoamingX-PreBuilds | — |
| 本仓库的基线来源 | https://github.com/nafumao/BiliRoamingX | GPL-3.0 |

- 上游基线版本：**1.23.5**
- 上游版权：**Copyright (C) BiliRoamingX contributors**
- 本仓库对其修改后，**同样以 GPL-3.0 发布**（见 [LICENSE](LICENSE)），
  并提供**完整的对应源码**（`src/`）。

### 对上游的修改

所有修改的清单、根因与验证记录见 [`docs/移植进度.md`](docs/移植进度.md)。
修改范围包括 `patches/`、`integrations/` 两个 Gradle 子模块，
以及若干资源文件（`strings.xml`、`biliroaming_setting_filter_popular.xml`）。

---

## 第三方组件

本仓库的构建依赖以下第三方项目，**其版权归各自作者所有**：

| 组件 | 地址 | 用途 | 许可 |
|---|---|---|---|
| ReVanced Patcher | https://github.com/ReVanced/revanced-patcher | 字节码补丁框架 | GPL-3.0 |
| ReVanced CLI | https://github.com/ReVanced/revanced-cli | 补丁命令行 | GPL-3.0 |
| AndroidX | https://developer.android.com/jetpack/androidx | Android 支持库 | Apache-2.0 |
| Kotlin | https://kotlinlang.org/ | 语言与标准库 | Apache-2.0 |
| protobuf-lite | https://protobuf.dev/ | 序列化 | BSD-3-Clause |
| fastjson | https://github.com/alibaba/fastjson | JSON | Apache-2.0 |

---

## 引用的技术资料

本项目的逆向工作参考了以下公开资料：

- Android 官方文档 —— `Parcelable`、`Resources`、`BottomSheetDialogFragment`
  https://developer.android.com/reference
- smali / dexlib2 指令集参考（ReVanced Patcher 依赖）
  https://github.com/JesusFreke/smali
- Kotlin `Function1` 等函数式接口定义
  https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.jvm.functions/
- R8 混淆器对字符串常量与反射名的处理行为
  https://r8.googlesource.com/r8

---

## 不构成引用的内容

以下内容**属于哔哩哔哩（上海幻电信息科技有限公司 / bilibili Inc.）的财产**，
本项目仅出于互操作性目的在文档中**提及其类名与方法名**，不包含、不分发其任何代码或资源：

- 哔哩哔哩 Android 客户端（`tv.danmaku.bili`）
- 其 DEX、资源、native 库、协议定义
- `com.bilibili.*`、`com.bapis.*`、`tv.danmaku.*` 等包名下的任何实现

文档中出现的类名/方法名/字段名，是为说明适配目标而作的**事实性描述**，
属于与专有软件互操作所必需的信息。

---

## 本仓库自主产出的内容

以下内容为本次 AI 工作的原创产出，同样以 GPL-3.0 发布：

| 内容 | 位置 |
|---|---|
| `DexSearch` —— 免 apktool 的 DEX 检索/反汇编小工具 | `tools/dexsearch/` |
| 全部审计与移植文档 | `docs/` |
| `src/` 下的全部修改 | `src/` |
