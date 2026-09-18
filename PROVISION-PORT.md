# 把 HyperCeiler 的新手引导直接搬进来

状态：**已搬完，CI 绿**（2026-09-18，`feat/provision-port`，PR #1）。
源头：`https://github.com/ReChronoRain/HyperCeiler` 的 `library/provision` 模块，AGPL-3.0-only，
版权归 HyperCeiler Contributions。本地对照源码：`/data/data/com.termux/files/home/hcp-work/HyperCeiler-HEAD`
（纯解包，未初始化 git、未构建）。所有 `file:line` 引用见 `/data/data/com.termux/files/home/hc-provision-notes.md`。

## 0. 一句话

引导本体 = 它自己的 XML 布局、drawable、anim、`glow.glsl`（AGSL）和 Java
（`com.sevtinge.hyperceiler.provision.**` + `fan.provision.**`），一行行搬过来；
小米那批 `fan.miuix:*` 1.0.13.0 jar 由 HyperCeiler 自己发在 GitHub Packages 上，已作为依赖接进来，
**不再是"搬不动"的墙**（上一版这份文档说拿不到这些包，那是错的）。

流程剪成它状态机的前三步：**开场（极光）→ 权限 → 完成**。
首次启动交给它，「关于 → 新手引导」走同一个开关；Compose 版引导仍在树里，未删未改，
等真机看过后再决定去留。

## 1. 原样搬进来的（未改写）

| 内容 | 数量 | 位置 |
|---|---|---|
| 布局 XML | 16 | `app/src/main/res/layout/provision_*.xml` |
| 动画 XML | 9 | `app/src/main/res/anim/` |
| drawable | 17 | `app/src/main/res/drawable/provision_*` |
| 颜色选择器 | 1 | `app/src/main/res/color/` |
| 值资源 | 8 | `res/values/provision_{colors,dimens,styles,attrs,drawables,arrays,strings,themes}.xml` |
| 夜间值 | 2 | `res/values-night/provision_{colors,themes}.xml` |
| AGSL 着色器 | 1 | `res/raw/glow.glsl` |
| 中文字符串 | 1 文件 | `res/values-zh-rCN/strings.xml`（本轮补搬，之前只有英文默认） |
| Java | 33 | `.../provision/**`、`.../fan/provision/**` |
| AIDL | 2 | `app/src/main/aidl/com/sevtinge/hyperceiler/provision/*.aidl` |

本轮**新补搬**的 Java（本轮之前缺失、导致 Java 编译失败的就是这些）：

| 文件 | 作用 |
|---|---|
| `utils/IKeyEvent.java`、`utils/IOnFocusListener.java` | 它的两个回调接口，`DefaultActivity`/`StartupState`/两个 Fragment 都在实现 |
| `utils/BlurUtils.java` | 包裹 `fan.core.utils.MiuiBlurUtils` 的模糊工具 |
| `utils/PageIntercepHelper.java` | 页间跳转拦截（`BaseActivity.finish()` 与 `StateMachine.run()` 都调用它） |
| `utils/LifecycleHandler.java` | 上者的依赖：Activity 栈 |
| `service/ProvisionAnimService.java` | AIDL 桩服务，"下一步/返回"动画回调链的落点 |
| `aidl/**/IProvisionAnim.aidl`、`IAnimCallback.aidl` | 上者与 `fan.provision.ProvisionAnimHelper` 的接口 |

`BuildConfig` 之外唯一改过的地方：`PageIntercepHelper` 里 `import ...provision.R` → `love.miao.yun.R`
（整个移植都是这一条替换，别的没动）。

补搬它的 keep 规则：`app/proguard-rules.pro` 里
`-dontwarn miui.**`、`-dontwarn com.android.internal.view.menu.MenuBuilder` 照抄自它
`library/core/src/main/keepRules/rules.keep:26-28`；`-keep ...provision.activity.**` /
`-keep ...provision.fragment.**` 照抄自 `library/provision/src/main/keepRules/rules.keep`。
没有前两条，release 的 R8 直接失败：`fan.miuix:*` 引用着 `miui.util.HapticFeedbackUtil` 和
`com.android.internal.view.menu.MenuBuilder` 这两个不在 `android.jar` 里的类。

## 2. 只有三处按"内容必须变成我们的"改写过

1. **`state/StateMachine.java`** —— 它的五步链剪成三步：`Startup → Permission → Congratulation`。
   删掉 `mTermsAndStatementState` / `mBasicState` 两个字段、两条 `addState`、两条 `setNextState`
   和 `getStateInfo()` 里两个分支，以及这两个 Activity 的 import。
   `TermsAndStatementState` / `BasicState` / 两个 Activity / 协议页的布局与 Java 一律没搬。
2. **权限页**（它自己的四行 network/root/installed_apps/lsp → 我们的两个开关）：
   - `fragment/PermissionSettingsFragment.java`：只留 `getLayoutId()` / `onViewCreated()` /
     `onResume()` 这套骨架，两个 `PermissionItemView` 的标题换成
     `R.string.provision_permission_accessibility` / `provision_permission_overlay`，
     点击分别 `Settings.ACTION_ACCESSIBILITY_SETTINGS` 与
     `Settings.ACTION_MANAGE_OVERLAY_PERMISSION`，状态实时读
     `MiaoAccessibilityService.Companion.isEnabled()` 与 `Settings.canDrawOverlays()`。
     网络探测、root 检测、LSP 检测、`PermissionUtils` 全部去掉（临时桩
     `common/utils/PermissionUtils.java` 已删除）。
   - `res/layout/provision_permission_layout.xml`：容器、内边距、行样式与说明文字都是它的，
     行换成 `@+id/accessibility`、`@+id/overlay`；说明文字（"您可以稍后设置下列必要权限"）
     移到列表最前面——上游它排在第一行 `network` 之后，因为那一行是"继续"按钮的前置条件，
     我们两行都不是，放在前面才是它的原意。
   - `widget/PermissionItemView.java`：**一字未改**。它的行外观（56dp 高、16dp 圆角、
     `miuix_theme_content_padding_horizontal_common` 内边距、勾选用的
     `provision_picker_btn_radio` 图标）就是我们要的样子，只是由我们的两个开关驱动。
3. **硬编码的包名/类名**（不改跑不通）：
   - `fan/provision/ProvisionAnimHelper.java:164`：`setPackage("com.sevtinge.hyperceiler")`
     → `mContext.getPackageName()`（它绑的服务现在是本应用声明的）。
   - `fragment/CongratulationFragment.java:360`：`getHomeIntent()` 的两处
     `com.sevtinge.hyperceiler.ui.SplashActivity` / `HomePageActivity` → `love.miao.yun.MainActivity`。

另外两处**新增**（不是改写它的代码）：

- `MainActivity.kt` / `ui/miuix/MiuixApp.kt` / `ui/material3/MaterialApp.kt`：入口改接到引导 Activity。
- `love/miao/yun/ui/provision/ProvisionGuide.kt`（新文件，`SPDX-License-Identifier: AGPL-3.0-only`）：
  只做三件事——`isDone()` 转问 `OnboardingPrefs`、`markDone()` 写它、`launch()` 调
  `OobeUtils.resetOobeState()` 后起 `DefaultActivity`。它不给引导加任何东西。

## 3. 主题：占位值删了，真值来自 `fan.miuix:*`

之前 `res/values/provision_miuix_stubs.xml` 里五个手猜的 stand-in 已**整个删除**。
`fan.miuix:*` 的 AAR 带 `res/`，资源会并进 app，布局里那些 `@dimen/miuix_*` / `@color/miuix_*`
直接由包自己提供，取到的就是真值（下面是从 AAR 里读出来的）：

| 资源 | 真值 | 出处 |
|---|---|---|
| `miuix_theme_radius_common` | `16dp` | `theme/res/values/values.xml` |
| `miuix_theme_padding_horizontal_common` | `12dp` | 同上 |
| `miuix_theme_content_padding_horizontal_common` | `16dp` | 同上 |
| `miuix_appcompat_secondary_text_size` | `14sp` | `appcompat/res/values/values.xml` |
| `miuix_color_black_solid_10` | `#f7f7f7` | `theme/...`（`miuix_default_color_surface_low_light` 指向它） |
| `miuix_color_white_solid_10` | `#101010` | 同上 |
| `miuix_color_blue_light_primary_default` | `#3482ff` | 同上 |
| `miuix_color_blue_dark_primary_default` | `#277af7` | 同上 |
| `miuix_design_default_color_surface_variant` | `#f7f7f7` | 同上（浅色 `windowBackground`） |
| `miuix_appcompat_black` | `#000000` | 同上（夜间 `windowBackground`） |
| `miuix_default_color_container_list_light` / `_dark` | `@color/miuix_color_white_solid_100` / `miuix_color_white_level8` | 同上 |
| `miuix_default_color_on_surface_light` / `_tertiary_light` | `@color/miuix_color_black_level1` / `_level4` | 同上 |
| `miuix_preference_item_padding_start` / `_end` | `@dimen/miuix_theme_content_padding_horizontal_common`（=16dp） | `preference/res/values/values.xml` |

（手猜那版尺寸恰好对：16/16/12dp；颜色是错的——占位写的白色 `#FFFFFFFF`，真值是 `#f7f7f7`。）

**新增** `res/values/provision_themes.xml`：从它 `res/values/themes.xml` 原样搬来的浅色
`ProvisionTheme`（parent `@style/Theme.AppCompat.DayNight`，来自 miuix 的 appcompat 包）
与 `TransparentActionBarTheme`。夜间版 `res/values-night/provision_themes.xml` 之前就在。
没有覆盖我们的 `values/themes.xml`（`Theme.MiaoAssistant` 一字未动）。
布局里用到的 `@style/Widget.Button(.Primary)`、`@style/TextAppearance.PreferenceList`、
`@style/ThemeOverlay.Preference.{Light,Dark}.OS2`、`@style/Widget.ProgressBar.DayNight`
都在 `fan.miuix:*` 里（已逐个核对 `R.txt` 与 `res/values`）。

## 4. Manifest

三个引导 Activity + 动画服务都声明了，主题 `@style/ProvisionTheme`：

- `...provision.activity.DefaultActivity`（开场，`StartupState` 把自己的 `StartupFragment` 塞进它）
- `...provision.activity.PermissionSettingsActivity`
- `...provision.activity.CongratulationActivity`
- `...provision.service.ProvisionAnimService`（`fan.intent.action.OOBSERVICE`）

与上游的两点不同：上游全部 `exported="true"` 并给 `DefaultActivity` 挂了个 `action.MAIN`
（那是给 MIUI 引导框架从外部拉起的），我们只由自己启动，所以 `exported="false"` 且不带 filter。
`MiaoApp` / `MainActivity` / 两个 service 的声明一行没动。

## 5. 入口

打开条件仍然是 `OnboardingPrefs.isDone(context)`：

- `MainActivity.onCreate`：`!ProvisionGuide.isDone(this)` → `ProvisionGuide.launch(this)` + `finish()`，
  不再渲染 Compose 引导（就是 Compose 版当初弹出来的那一刻）。
- 「关于 → 新手引导」：那两个页面照旧把 `MiaoState.showOnboarding` 置 true，
  `MiuixApp` / `MaterialApp` 里原来的 `MiuixOnboarding(...)` / `MaterialOnboarding(...)` 调用点
  换成 `LaunchedEffect` → 清旗标 → `ProvisionGuide.launch(context)`。
  Compose 引导本体（`love/miao/yun/ui/onboarding/*` 与其测试）**没有改、没有删**。
- `ProvisionGuide.launch()` 先调 `OobeUtils.resetOobeState()`（它自己的复位：清
  `pref_oobe_state` 里的 `is_provisioned` 与已持久化的状态链），否则从"关于"再进会直接落在完成页。
- 完成：`CongratulationFragment.startHome()` 里在它原本的 `OobeUtils.setProvisioned(true)` 旁边
  调 `ProvisionGuide.markDone()`（= `OnboardingPrefs.setDone`），然后带
  `NEW_TASK|CLEAR_TASK` 回到 `MainActivity`。
  用户从开场页按返回键退出也算"看过"：`DefaultActivity.onDestroy()` 里 `isFinishing()` 时同样 markDone
  （配置变更不会 finish，所以转屏不算）。

## 6. AIDL / 动画链

它 `ProvisionBaseActivity` 的"继续/返回"是走 AIDL 的：`fan.provision.ProvisionAnimHelper` 绑
`fan.intent.action.OOBSERVICE` → `ProvisionAnimService` → 回调 `onNextAminStart()`（上游拼写如此）。
所以 `app/build.gradle.kts` 打开了 `buildFeatures { aidl = true }`（上游 `library/provision/build.gradle.kts:13-15` 也是这么开的），
两个 `.aidl` 原样放在 `app/src/main/aidl/`。它那套 `mProvisionAnimHelper == null` 的直连分支
（`ProvisionBaseActivity.java:102-104`）因此没被用到——链路是通的。

## 7. 顺手修的一处，**不在移植范围内**

`.github/workflows/android.yml` 的 `Name the APK after the ref`：pull_request 事件下
`GITHUB_REF_NAME` 是 `1/merge`，`dist/MiaoAssistant-${GITHUB_REF_NAME}.apk` 于是变成
`dist/MiaoAssistant-1/merge.apk`，目标目录不存在 ⇒ **每个 PR 的 run 都在最后一步失败**。
把 `/` 换成 `-` 就好。这一步在移植卡在编译时从没跑到过，所以是这次才暴露出来的既有 bug。

## 8. 验证到什么程度

- **本地没有 Android SDK，一次都没构建过**；唯一的构建/验证路径是 GitHub Actions。
- CI：`./gradlew testDebugUnitTest`（规则引擎的 JVM 测试）+ `assembleRelease`（R8 + 资源压缩）
  全部通过；run URL 与 APK 见下。
- 对 APK 做过静态核对（`MiaoAssistant-1-merge.apk`）：三个引导 Activity 与动画服务都在
  AndroidManifest 里；`resources.arsc` 里有 `provision_*` 布局/drawable/`glow`/`ProvisionTheme`
  以及"无障碍服务 / 悬浮窗权限 / 权限设置 / 设置完毕"（中文来自 `values-zh-rCN`）；
  dex 里能找到极光的 uniform 名（`uTime`/`uColorBlack`/`uCircleFinalRadius`）、状态机字符串
  （`com.android.provision.STATE_`、`pref_oobe_state`）和 `ProvisionAnimService`。

## 9. 只有真机才能验的部分（请重点看这些）

1. **极光背景本身**（`glow.glsl` 的 AGSL + 20% 尺寸渲染再放大 5×）：开场圆形揭示、环带、
   噪声流动、2s↔120s 的 ping-pong。minSdk 33 所以 `RuntimeShader` 一定可用，但画面对不对只能眼睛看。
2. **miuix 的按钮组**（`GroupButtonsConfig` 生成的"继续 / 跳过"）：尺寸、圆角、间距在包里，
   非 MIUI 设备上长什么样没验过。
3. **开场圆钮的放大转场**（`fan.transition.ActivityOptionsHelper.makeScaleUpAnim`）：
   它在非 MIUI 上可能返回 null——`StartupFragment` 本来就有 null 分支，会无动画直接进权限页。
4. **模糊**（`MiuiBlurUtils`）：非 MIUI 不生效，本来就这样。
5. **品牌归属**：极光页/完成页显示的是**它自己的 Logo 与字标**（`provision_logo_image*`、
   `provision_text_logo_image*` 是 "HyperCeiler" 的 vector 路径），文案是
   "欢迎来到 HyperCeiler / 开始使用"。这一版是"照搬优先"，**没有换成我们的品牌**——
   要不要换、换成什么，需要人来定（换的话是替换 `res/drawable/provision_*logo*` 与
   `provision_congratulation_label` / `provision_complete_text` 两条字符串）。
6. **权限页的两行是否真好用**：点"无障碍服务"跳系统设置、点"悬浮窗权限"弹授权框，
   回来（`onResume`）勾是否亮。
7. **返回键路径**：开场页返回 = 退出引导且不再出现；权限页返回 = 回开场；完成页返回 =
   回权限页且不带结果。
8. **深色/浅色**：`ProvisionTheme` 浅色走 `DayNight`、夜间走 `Dark`，`values-night` 的颜色是它原本的。

## 10. 还剩什么

- 上面第 9 条里 5、6 两项是人来决定的事（品牌、文案），代码上已经没有卡点。
- 真机验收通过后，才可以删掉 Compose 版引导（`love/miao/yun/ui/onboarding/*` + `MiaoState.showOnboarding`
  那两个调用点），以及考虑把 `Implementation(libs.miuix.legacy.*)` 里实际没被引导用到的 artifact 去掉。
- 没搬的（故意没搬）：协议与声明页、基础设置页（语言/图标/作用域）、条款 Web 弹层与 Markdown 渲染器、
  `NoticeProvider` 协议版本同步、`AppLanguageHelper` 跨语言包装、`ProvisionManager`。
  依赖它们而失去作用的文件也一并没搬：`BasicState`/`TermsAndStatementState`/两个 Activity、
  `BaseListFragment`/`WebFragment`/`MarkdownView`/`TermsAndStatementBottomSheet`/`ClickSpan`/`TermsTitleSpan`、
  `NetworkManager`（权限页不再探网）。仓库里仍有它们的死布局
  （`provision_page_layout.xml`、`provision_list_page_layout.xml`、`fragment_bottom_sheet_web.xml`、
  `provision_terms_and_statement_layout.xml`、`edit_verification_code_dialog.xml`、
  `provision_item_list_*.xml`）——那是原样搬进来的资源，留着不影响，删不删随意。

## 11. 怎么复现这一轮

```bash
git push origin feat/provision-port                     # PR #1 的 run 会带上这个分支
SHA=$(git rev-parse HEAD)
TOK=<token>
curl -s -H "Authorization: token $TOK" \
  "https://api.github.com/repos/Youzix-Star/NekoPlus/actions/runs?head_sha=$SHA&per_page=1"
# 产物
curl -sL -H "Authorization: token $TOK" \
  "https://api.github.com/repos/Youzix-Star/NekoPlus/actions/artifacts/<AID>/zip" -o apk.zip
```

`main` 全程没动；Compose 版引导与它的测试也没动。
