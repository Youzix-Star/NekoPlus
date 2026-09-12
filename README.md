# 喵喵助手 / NekoPlus

一个基于 Jetpack Compose 的 Android 应用骨架，**双 UI 引擎**：同一套功能可以用
[miuix](https://github.com/compose-miuix-ui/miuix) 或 Material Design 渲染，随时切换。

> 当前进度：**空壳 UI + 一个可用的悬浮窗**。业务逻辑尚未接入，界面上的统计数字与开关
> 大多为占位实现。

## 双 UI 引擎

两套引擎各自拥有完整的页面树，互不共享 Composable；切换后立即重建整个界面，
选择保存在 `SharedPreferences`（`ui_prefs` / `ui_engine`）。

| | miuix 引擎 | Material Design 引擎 |
|---|---|---|
| 设计语言 | miuix KMP，圆角卡片 + 液态玻璃悬浮底栏 | Material 3 Expressive，取自 [InstallerX-Revived](https://github.com/wxxsfxyzm/InstallerX-Revived) |
| 底栏 | `FloatingBottomBar` + `rememberLayerBackdrop` | `NavigationBar` |
| 顶栏 | `TopAppBar(largeTitle = …)` 大标题下推 | `LargeFlexibleTopAppBar`，随滚动折叠 |
| 设置项 | `ArrowPreference` / `SwitchPreference` / `WindowSpinnerPreference` | `SegmentedColumn` + `NavigationItemWidget` / `SwitchWidget` / `DropDownMenuWidget` / `IntNumberPickerWidget` |
| 毛玻璃 | 底栏液态玻璃 | 顶栏实时模糊 |
| 取色 | 动态取色（Monet）为默认 | 动态取色（Monet）为默认 |

两套引擎共用同一个「毛玻璃」开关（设置 → 外观），状态存在 `MiaoState.useBlur` 并持久化。
关闭后 miuix 底栏变为不透明悬浮样式、MD 顶栏变回 `surfaceContainer` 实色，
同时也省掉 `RenderEffect` 的开销。

### Material Design 引擎移植自参考项目的组件

`ui/material3/widgets/` 下的组件都是从 InstallerX-Revived 逐行搬过来的（只改包名），
不是自己拼的近似实现：`SegmentedColumn`（拼接式圆角分组，含展开动画与圆角过渡）、
`BaseWidget`、`SwitchWidget`、`NavigationItemWidget`、`NumberPickerWidget`、
`DropDownMenuWidget` + `GroupedDropdownMenuPopup`、`SwipeableSnackbarHost`。

模糊同样复用 miuix 的 backdrop 引擎（`ui/material3/Backdrop.kt`），
所以两套引擎的玻璃观感同源，而不是各写一套。

## 导航模型

界面严格分成两层，**预见式返回（predictive back）只存在于第二层**：

- **一级页面**：首页 / 悬浮窗 / 设置 / 关于 四个页签。点底栏或左右翻页切换，
  **不挂** `PredictiveBackHandler`，因此页签之间不会因为边缘返回手势而误触发返回动画
  ——这正是早期版本的 bug。

  切换动画用的是从参考项目移植的 `ui/MainPagerState.kt`：`selectedPage` 在点击瞬间更新，
  所以底栏高亮与大标题立刻跟上，而 pager 用 `EaseInOut` tween 滑过去
  （时长 `100ms × 跨越页数`，最少按两页算），由 `PagerState.scroll(MutatePriority.UserInput)`
  手动驱动，避免默认的吸附行为；用户中途手滑会中断动画并以实际落点为准。

  一级页面的返回键在非首个页签时，用同一个动画滑回首页，全程没有预见式视觉。
- **二级页面**：目前只有「开源许可」。这一层是唯一挂载 `PredictiveBackHandler` 的
  位置，返回时按 AOSP 的形体变换做预见式缩放与跟手位移，参数统一在
  `ui/PredictiveBack.kt`（`aospPredictiveBack`）里定义：
  最小缩放 0.9、缓动 `cubic-bezier(0.1, 0.1, 0, 1)`、边缘判定 8dp、
  垂直方向按 `1-(1-r)²` 阻尼跟手并朝手势方向靠拢。

## 悬浮窗

`service/FloatingWindowService.kt` 用普通 `View`（非 Compose）实现一个圆角可拖拽药丸，
点 ✕ 关闭，通过 `TYPE_APPLICATION_OVERLAY` 叠加。前台服务类型为 `specialUse`，
并带一条常驻通知。悬浮窗开关会同步到首页的大卡片：关闭时显示「未在工作」，
开启时整块变成「正在作为悬浮窗」并进入工作态配色。

## 构建

本项目**不在本地构建**，全部依赖 GitHub Actions：

- `.github/workflows/android.yml`：JDK 21 → 安装 `platforms;android-37.0` /
  `build-tools;37.0.0` → 从 Secrets 还原签名库 → `assembleRelease` → 上传
  APK 产物；推送 `v*` 标签时额外创建 Release。
- 需要的 Secrets：`KEYSTORE_BASE64`、`KEYSTORE_PASSWORD`、`KEY_ALIAS`、`KEY_PASSWORD`。
  本地构建则读取根目录的 `keystore.properties`。

工具链版本见 `gradle/libs.versions.toml`：AGP 9.4.0（自带 Kotlin 支持，因此
**不应用** `org.jetbrains.kotlin.android`）、Kotlin/Compose 插件 2.4.20、
Gradle 9.7.1、Compose BOM 2026.08.00、compileSdk 37 / minSdk 33 / targetSdk 35。
`material3` 显式固定为 `1.5.0-alpha27`——`LargeFlexibleTopAppBar` 与公开的
`*Emphasized` 字体只从该版本开始存在，BOM 里钉的 1.4.0 没有。

> `minSdk 33` 是被 miuix 的 `miuix-blur` 拉高的：液态玻璃底栏依赖真实的
> `RenderEffect` 模糊，只有 Android 13+ 提供。

## 目录结构

```
app/src/main/java/love/miao/yun/
├── MainActivity.kt              35 行分发器：读引擎选择 → 挂对应引擎的根 Composable
├── MiaoState.kt                 跨引擎共享的少量状态（悬浮窗运行中、今日计数、规则数）
├── ui/
│   ├── AppIcons.kt              统一图标入口
│   ├── PredictiveBack.kt        两套引擎共用的 AOSP 预见式返回变换
│   ├── MainPagerState.kt        一级页签切换动画（移植自参考项目）
│   ├── UiEngine.kt              引擎枚举、毛玻璃开关与持久化
│   ├── miuix/                   miuix 引擎：MiuixApp + home/floating/settings/about/licenses
│   │   ├── liquid/              液态玻璃底栏与镜片效果（Apache-2.0，来自 NekoEdit）
│   │   └── animation/           DampedDragAnimation、InteractiveHighlight
│   └── material3/               Material Design 引擎：MaterialApp + 同名页面树
│       ├── Backdrop.kt          MD 顶栏的毛玻璃（复用 miuix 的 backdrop 引擎）
│       └── widgets/             SegmentedColumn、BaseWidget、SwitchWidget、
│                                NavigationItemWidget、NumberPickerWidget、
│                                DropDownMenuWidget、SwipeableSnackbarHost
│                                （GPL-3.0，来自 InstallerX-Revived）
└── service/FloatingWindowService.kt
```

## 许可

本项目以 **AGPL-3.0** 发布。

其中 `ui/material3/widgets/` 移植自 [InstallerX-Revived](https://github.com/wxxsfxyzm/InstallerX-Revived)
（GPL-3.0），`ui/miuix/liquid/` 来自 NekoEdit（Apache-2.0），
两者的原始版权声明与 SPDX 标识均保留在各文件头部。
