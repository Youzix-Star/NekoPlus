# 把 HyperCeiler 的新手引导直接搬进来（进行中）

这份文件记录 `feat/provision-port` 分支在做什么，以及**卡在哪里、为什么**。
源头：`https://github.com/ReChronoRain/HyperCeiler` 的 `library/provision` 模块，AGPL-3.0-only，
版权归 HyperCeiler Contributions。

## 已经搬进来的（原样，未改写）

| 内容 | 数量 | 位置 |
|---|---|---|
| 布局 XML | 16 个 | `app/src/main/res/layout/provision_*.xml` |
| 动画 XML | 9 个 | `app/src/main/res/anim/` |
| drawable | 17 个 | `app/src/main/res/drawable/provision_*` |
| 颜色选择器 | 1 个 | `app/src/main/res/color/` |
| 值资源 | 7 个 | `app/src/main/res/values/provision_{colors,dimens,styles,attrs,drawables,arrays,strings}.xml` |
| 夜间值 | 2 个 | `app/src/main/res/values-night/provision_*.xml` |
| AGSL 着色器 | 1 个 | `app/src/main/res/raw/glow.glsl` |
| Java 源码 | 28 个 | `app/src/main/java/com/sevtinge/hyperceiler/provision/`、`.../fan/provision/` |

Java 里**没有**搬的三页（HyperCeiler 自己的内容）：协议与声明页、基础设置页（语言/图标/作用域）、
条款 Web 弹层与 Markdown 渲染器。NekoPlus 的引导只需要三步：开场 / 权限 / 完成。

## 唯一的硬墙：它依赖小米闭源库

`library/provision` 的代码 import 了 8 组 **`fan.miuix:*` 1.0.13.0** 的类，这些是小米的 MIUI/Miuix
闭源库（模块自己的 `gradle/libs.versions.toml` 里列了 15 个 artifact，
从私有 GitHub Packages 取，需要凭证）。**这部分不是 HyperCeiler 的代码，谁都搬不走**：

| 闭源 API | 它用来干什么 | 我们的替身 |
|---|---|---|
| `fan.appcompat.app.AppCompatActivity` / `AlertDialog` | MIUI 版 AppCompat | `androidx.appcompat`（需加依赖） |
| `fan.appcompat.app.GroupButtonsConfig` | 底部按钮组的构建器 | 自己写一个：同样的两个按钮 + 它自带的 `provision_next_btn_background.xml` / `provision_skip_background.xml` |
| `fan.animation.*`（Folme） | 开场页的属性动画 | `utils/AnimHelper.java` 改成 `ViewPropertyAnimator`，**时长与缓动照抄它的数** |
| `fan.transition.ActivityOptionsHelper.makeScaleUpAnim` | 圆钮撑开那一下 | 平台公开 API `ActivityOptions.makeThumbnailScaleUpAnimation` + 前景色合成 |
| `fan.core.utils.MiuiBlurUtils` | MIUI 背景模糊 | 空实现（它自己在非 MIUI 上也不生效） |
| `fan.core.utils.HyperMaterialUtils` / `EnvStateManager` | 特性开关 / 屏幕短边 | 分别返回 false / 用 `Resources` 算 |
| `fan.internal.utils.LiteUtils` / `ViewUtils` | 低端机判定、padding 工具 | LiteUtils 恒 false（上游源码里也是硬编码 false）、ViewUtils 照实现 |
| `fan.preference.*`、`fan.bottomsheet.*` | 只在没搬的那两页用 | 不需要 |

主题同样：它的 `ProvisionTheme` 继承 `Theme.AppCompat`，并且引用一堆 `miuix_*` 尺寸/颜色、
`groupButtons*ButtonStyle`、`BottomSheetModalStyle`。这些要我们自己写一份等价主题。
布局只引用了 5 个闭源资源，已按 MIUI 列表项的常规数值补在
`values/provision_miuix_stubs.xml`（16 dp 圆角 / 16 dp 内容内边距 / 12 dp 外边距 / 白色低表面）。

## 接下来的顺序

1. 加 `androidx.appcompat` 依赖；写 `fan/appcompat/app/AppCompatActivity`、`GroupButtonsConfig`、
   `fan/transition/ActivityOptionsHelper`、`fan/core/utils/*`、`fan/internal/utils/*` 这几个替身；
2. `utils/AnimHelper.java` 与两个 Fragment 里的 Folme 调用改成 `ViewPropertyAnimator`（数值照抄）；
3. `state/StateMachine.java` 剪成三步（开场 / 权限 / 完成），`PermissionSettingsFragment` +
   `PermissionItemView` 改成我们的两个开关（无障碍、悬浮窗）；
4. 主题、`AndroidManifest.xml` 里声明三个 Activity，从 `MainActivity` 进引导；
5. 走 CI 把编译错误磨平，再发 APK 让眼睛验收。

在这一切完成之前，`main` 上是**已经能用**的那版（Compose 重写版，含它的极光着色器、状态机流程、
350 ms 整页平移与开场撑开），这个分支不动它。
