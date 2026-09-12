# 喵喵助手 · MiaoAssistant

一个基于**无障碍服务**的输入框文本改写工具：在你选定的应用里，把发出去的文字自动加上「喵」和颜文字。

| | |
| --- | --- |
| 包名 | `love.miao.yun` |
| 版本 | `1.1.8`（versionCode 9） |
| minSdk / targetSdk | 26 / 35 |
| UI | Jetpack Compose + Material 3（Compose BOM `2024.02.00`） |
| 构建 | AGP `8.1.4` / Kotlin `1.9.22` / Gradle `8.4` / JDK 17 |

## 功能

- **实时改写**：无障碍服务读取输入框内容，按规则追加「喵」或颜文字
- **三种处理模式**：标点触发（`punctuation`）、实时（`realtime`）、悬浮窗（`floating_window`）
- **颜文字**：支持概率 / 随机 / 按句间隔三种触发方式，可自定义颜文字列表
- **替换规则**：自定义 `原文=替换` 规则
- **应用选择**：只勾选的应用里生效
- **个性化**：空格不加喵、标点优化、删除优化、QQ 猫爪等
- **保活**：前台服务，另有悬浮按钮 / 悬浮窗 / 日志悬浮窗三种悬浮形态
- **崩溃记录**：内置 `CrashHandler` 与 `DebugLog`

## 目录结构

```
app/src/main/java/
├── com/google/android/accessibility/selecttospeak/
│   └── SelectToSpeakService.kt      # 无障碍服务（服务类名伪装成系统组件）
└── love/miao/yun/
    ├── MainActivity.kt              # 入口 + 导航
    ├── MiaoApp.kt                   # Application
    ├── service/
    │   ├── MiaoAccessibilityService.kt
    │   ├── FloatingButtonService.kt
    │   ├── FloatingWindowService.kt
    │   └── LogFloatingService.kt
    ├── ui/
    │   ├── MainScreen.kt / SettingsScreen.kt
    │   ├── PersonalizationScreen.kt / AppSelectScreen.kt
    │   ├── KeepAliveScreen.kt / AboutScreen.kt
    │   └── theme/Theme.kt
    └── util/
        ├── MiaoConfig.kt            # 配置模型与持久化
        ├── TextProcessor.kt         # 文本改写核心
        ├── CrashHandler.kt / DebugLog.kt
```

## 构建与发布

构建全部在 GitHub Actions 上完成，`.github/workflows/android.yml` 会在每次 push / PR 时
构建**已签名的 release APK** 并上传为 Artifact；推送 `v*` 标签时额外创建 GitHub Release 并附上 APK。

签名材料通过仓库 Secrets 注入，仓库里没有任何私钥或口令：

| Secret | 说明 |
| --- | --- |
| `KEYSTORE_BASE64` | keystore 文件的 base64 内容 |
| `KEYSTORE_PASSWORD` | keystore 口令 |
| `KEY_ALIAS` | 密钥别名（`miao`） |
| `KEY_PASSWORD` | 密钥口令 |

`app/build.gradle.kts` 会先读项目根目录的 `keystore.properties`（已被 git 忽略，CI 上不存在），
读不到再读环境变量。两者都没有时构建依然成功，只是产出未签名的 APK。

如果需要在本地签名构建，把 `keystore.properties.example` 复制成 `keystore.properties` 并填好即可。

## 许可证

尚未添加，待项目所有者确定。
