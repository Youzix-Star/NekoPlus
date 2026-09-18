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

流程就是它状态机本身，只去掉协议页那一步：**开场（极光）→ 权限 → AI 配置 → 完成**。
第三步用的是它自己的"应用设置"那一页（`BasicSettingsActivity/Fragment` +
`res/xml/provision_basic_settings.xml`），页面家什与行样式/图标都留着，行换成这个应用首次
运行必须设的东西——AI 的接口地址 / API Key / 模型名 + 连接测试，读写走应用自己的 `AiManager`。
首次启动交给它，「关于 → 新手引导」走同一个开关；Compose 版引导仍在树里，未删未改，
等真机看过后再决定去留。**品牌已经是我们的**：开场页/完成页的标记、应用名与副标题都是本应用的
（上游的 logo/字标 vector 仍留在 `res/drawable` 里，只是不再被引用），见 §2.2。

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
| Java | 36 | `.../provision/**`、`.../fan/provision/**` |
| 偏好设置 XML | 1 | `app/src/main/res/xml/provision_basic_settings.xml`（上游"基础设置"那一页，本应用用作 AI 配置） |
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
| `state/BasicState.java`、`activity/BasicSettingsActivity.java` | 引导第三步：它自己的"应用设置"页（内容见 §2.1） |

`BuildConfig` 之外唯一改过的地方：`PageIntercepHelper` 里 `import ...provision.R` → `love.miao.yun.R`
（整个移植都是这一条替换，别的没动）。

补搬它的 keep 规则：`app/proguard-rules.pro` 里
`-dontwarn miui.**`、`-dontwarn com.android.internal.view.menu.MenuBuilder` 照抄自它
`library/core/src/main/keepRules/rules.keep:26-28`；`-keep ...provision.activity.**` /
`-keep ...provision.fragment.**` 照抄自 `library/provision/src/main/keepRules/rules.keep`，
另外自己加了一条 `-keep ...provision.state.**`：状态机把引导进度按 `getSimpleName()` 持久化，
不保名的话，跨版本更新后旧状态链可能指到别的页。
没有前两条，release 的 R8 直接失败：`fan.miuix:*` 引用着 `miui.util.HapticFeedbackUtil` 和
`com.android.internal.view.menu.MenuBuilder` 这两个不在 `android.jar` 里的类。

## 2. 只有这几处按"内容必须变成我们的"改写过

1. **`state/StateMachine.java`** —— 它的五步链去掉协议页那一步，剩下
   `Startup → Permission → Basic → Congratulation`，与上游同一条链、同一套 result code
   （`-1` 前进 / `0` 后退）。`StartupState`/`PermissionState`/`BasicState`/`CongratulationState`
   的接线与上游逐字一致；只少了 `mTermsAndStatementState` 一个字段、一条 `addState`、两条
   `setNextState`、`getStateInfo()` 一个分支和两个 Activity 的 import。
   `TermsAndStatementState` / `TermsAndStatementActivity` / 协议页的布局与 Java 一律没搬。
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
3. **第三页：上游的"基础设置" → 我们的 AI 配置**（详见 §2.1）。
   读写走应用自己的 `AiManager`：存储文件是 `ai_config`（不是 `ai_prefs`），
   键是 `base_url` / `api_key` / `model`（+ 未动的 `prompt` / `system_prompt`）——
   代码里就是这样，所以口径以 `AiManager` 为准，引导页不自己拼键名。
4. **硬编码的包名/类名**（不改跑不通）：
   - `fan/provision/ProvisionAnimHelper.java:164`：`setPackage("com.sevtinge.hyperceiler")`
     → `mContext.getPackageName()`（它绑的服务现在是本应用声明的）。
   - `fragment/CongratulationFragment.java:360`：`getHomeIntent()` 的两处
     `com.sevtinge.hyperceiler.ui.SplashActivity` / `HomePageActivity` → `love.miao.yun.MainActivity`。

另外两处**新增**（不是改写它的代码）：

- `MainActivity.kt` / `ui/miuix/MiuixApp.kt` / `ui/material3/MaterialApp.kt`：入口改接到引导 Activity。
  应用自己的设置页（`ui/miuix/settings/`、`ui/material3/settings/`、AI 配置页、文本规则页）一行未动。
- `love/miao/yun/ui/provision/ProvisionGuide.kt`（新文件，`SPDX-License-Identifier: AGPL-3.0-only`）：
  只做三件事——`isDone()` 转问 `OnboardingPrefs`、`markDone()` 写它、`launch()` 调
  `OobeUtils.resetOobeState()` 后起 `DefaultActivity`。它不给引导加任何东西。

### 2.1 第三页（AI 配置）怎么来的

上游把这一页留给"这个应用自己要设置的东西"（语言 / 桌面图标 / 作用域）。**页面家什全部是它的**：

| 保留 | 说明 |
|---|---|
| `activity/BasicSettingsActivity.java` | 原样搬（只有 `getTitleStringId()` 换成我们的标题串，见下） |
| `state/BasicState.java` | 原样搬（上游这个文件自己没有 license 头，补了它标准的那个头） |
| `res/xml/provision_basic_settings.xml` | 它的 `PreferenceScreen` + `PreferenceCategory` 结构 |
| `fragment/BasicSettingsFragment.java` | 它的类形状：`extends fan.preference.PreferenceFragment`、`onCreatePreferences()` + `onViewCreated()` 两段式、行字段 + `findPreference()` + `setPersistent(false)` + `setOnPreferenceChangeListener` |
| 页面图标 | `getPreviewDrawable()` 仍是它的 `R.drawable.provision_basic_settings` |
| 行外观与尺寸 | miuix preference 行（它的布局/字号/内边距），每行都有 `android:icon` |
| 底部按钮 | `ProvisionBaseActivity` 的"继续 / 跳过"，与其它页同一套 |

**只有"行是什么"换了**，四行、一行一句、没有段落：

| 行 | key | 图标（都来自它搬进来的 drawable，没有新画） | 行为 |
|---|---|---|---|
| API 接口地址 | `base_url` | `provision_service_state` | 点开 miuix 编辑框（`TYPE_TEXT_VARIATION_URI`），摘要显示当前值 |
| API Key | `api_key` | `provision_terms` | 点开编辑框（密码输入类型），摘要只显示末 4 位 / "未设置" |
| 模型名 | `model` | `provision_basic_settings` | 点开编辑框，摘要显示当前值 |
| 连接测试 | `connection_test` | `provision_picker_btn_radio` | 调 `AiManager.listModels()`（就是应用"获取模型列表"那次请求），摘要变成"连通，N 个模型"/"失败：…" |

分类标题两个：「接口」「连接」，与应用里 Compose 版 AI 页的小标题一致；页面标题「AI 配置」
（`R.string.provision_ai_settings_title`，是本轮新加的串，不是改它原来的
`provision_basic_settings_title`——那条在 `values-zh-rCN` 里另有一份，改默认值会被中文包盖掉）。

**没有第二份真相**：四行都 `setPersistent(false)`（上游对它自己的应用设置行也是这么干的：
`mLanguagePreference.setPersistent(false)` + `AppSettingsStore`），读写一律走
`love.miao.yun.ai.AiManager`（`AiManager.INSTANCE.load/save/listModels`，即应用 AI 页用的同一个
`ai_config` 存储与同一组键 `base_url`/`api_key`/`model`）。在引导里填的值，就是应用用的值；
`prompt`/`system_prompt` 原样保留（回写时先读整份配置、只改一个字段）。

图标是"复用已搬进来的它自己的 drawable"，不是为这一页新画的图：上游这页的行本来没有行图标，
真机验收时若要换成正式美术资源，只需改 `provision_basic_settings.xml` 里四个 `android:icon`。
第三页的四个 key 与 `AiManager` 一致（`base_url` / `api_key` / `model` / 我们自己的
`connection_test`），写回时先 `load()` 整份配置、只改一个字段，所以应用 AI 页的提示词不会被清掉。


### 2.2 品牌（上游的 logo / 字标）

上游开场页与完成页显示的是 HyperCeiler 的 logo 与 "HyperCeiler" 字标 vector，文案是
"Welcome to HyperCeiler / Enter HyperCeiler"。这些换成了本应用的，**布局、尺寸、位置与入场
动画一字未改**：

| 槽位 | 上游 | 现在 |
|---|---|---|
| 90dp 方块 `logo_image` | `provision_logo_image(_lite).xml`（白色/黑色 vector） | `@drawable/app_mark`（本应用 launcher 前景图按墨迹边界裁出来的那一块，像素未改；开场页 tint 白，完成页 tint `provision_complete_title_color`） |
| 字标槽 `text_logo_image`（320×45.28dp，它的 dimens） | `provision_text_logo_image(_lite).xml` | 应用名文字 `@string/app_name`（「喵喵助手」），字号用它自己的 `provision_title_text_size` |
| 开场页字标下面（新增一行） | — | 副标题「把输入框里的字改好」，`provision_subtitle_margin_top` / `provision_subtitle_text_size`，与 Compose 版引导首屏那句一样 |
| 完成页按钮文案 | `provision_complete_text` = "Enter HyperCeiler" | 「开始使用」 |
| 完成页外的欢迎串 | `provision_congratulation_label` = "Welcome to HyperCeiler" | 「欢迎使用喵喵助手」（含 `values-zh-rCN` 那一份；两条串实际没被布局引用，release 里会被资源压缩删掉） |

两点说明：

1. **标记为什么是图而不是 `AppIconText` 那句话**：`AppIconText` 是本应用的文字标记
   （About 页也是它），launcher 图标里烤的就是同一个字串。但它是**窄长**的
   （`app_icon.png` 432×432 里墨迹只有 217×43，宽高比约 5:1），直接塞进上游这个 90dp 方块
   只有一半宽，所以把同一张图按墨迹边界裁成 `app_mark.png`（`app/icon` 原图保留，像素未动），
   放进方块时正好铺满宽度。要改成直接显示那句话，改 `provision_startup_layout.xml` /
   `provision_congratulation_layout.xml` 里那一个 view 即可。
2. **两页的颜色不同是照它的设计**：开场页用白（黑底极光 + 圆形揭示），完成页用深色
   （它自己的 `provision_complete_title_color`，与那一页 `system_state_text` 同色系）——
   上游这两页本来也是"开场白字标 / 完成页 `_lite` 深色稿"，开了 MIUI 模糊时才换成白的；
   现在两个平台同一套。
3. 开场页的字标槽位与新增的副标题放在同一个 `logo_image_wrapper` 里（容器由 `FrameLayout`
   改成纵向 `LinearLayout` 才叠得下两行文字，尺寸与居中不变），所以上游对 wrapper 整体做的
   入场动画原样作用在两行上；`mTextLogoImage` 在 Java 里由 `ImageView` 变成 `TextView`，
   对应的 `setImageResource(R.drawable.provision_logo_image*)` 调用删掉，两个 vector 仍留在树里。

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

四个引导 Activity + 动画服务都声明了，主题 `@style/ProvisionTheme`：

- `...provision.activity.DefaultActivity`（开场，`StartupState` 把自己的 `StartupFragment` 塞进它）
- `...provision.activity.PermissionSettingsActivity`
- `...provision.activity.BasicSettingsActivity`（第三步，AI 配置）
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
  全部通过。
  - 当前（四步 + 我们的品牌）：https://github.com/Youzix-Star/NekoPlus/actions/runs/35376896117
  - 产物：artifact `MiaoAssistant-release-apk`，解出来是 `MiaoAssistant-1-merge.apk`，
    5 198 691 字节，`sha256 ae78de28850ec620ff17592e16b77e021bd73e6a8ce43f8b18cf2938b6289ede`
    （artifact id 10560995452；本地在 `~/apk-branded/MiaoAssistant-1-merge.apk`）。
    这个 PR 的 APK 是**有签名**的（CI 恢复了 keystore）。
  - 四步版（未换品牌的那次）：run 35375307539 / artifact 10560201005 /
    `sha256 fe5bc171…`；三步版：run 35373086630。
- 对 APK 做过静态核对（`MiaoAssistant-1-merge.apk`）：四个引导 Activity
  （Default / PermissionSettings / **BasicSettings** / Congratulation）与动画服务都在
  AndroidManifest 里；`resources.arsc` 里有 `provision_*` 布局/drawable/`glow`/`ProvisionTheme`
  与 `provision_ai_*`，以及"无障碍服务 / 悬浮窗权限 / 权限设置 / 设置完毕 / AI 配置 /
  API 接口地址 / 模型名 / 连接测试"；dex 里能找到极光的 uniform 名
  （`uTime`/`uColorBlack`/`uCircleFinalRadius`）、状态机字符串（`com.android.provision.STATE_`、
  `pref_oobe_state`）、`ProvisionAnimService`、`BasicSettingsFragment`、`BasicState`/`StartupState`
  类名与 `connection_test`/`base_url`/`api_key` 三个 key。
  （release 的资源文件名被 AGP 缩短成 `res/0F.xml` 这种，所以只能从 `resources.arsc` 里认名字；
  引导的 activity/fragment/state 类名被 keep 规则保住了，别的类名会被 R8 改。）
- 品牌那一轮另外核对过：`resources.arsc` 里有「喵喵助手」「把输入框里的字改好」「开始使用」，
  整个 arsc 里再也找不到 "HyperCeiler"；`res/Ef.png` 是裁好的 217×43 标记（1734 字节），
  `res/aH.png` 是 launcher 用的 432×432 原图；四个引导 Activity 与三个 AI key 都在。
  未被引用的 `provision_logo_image*.xml` / `provision_text_logo_image*.xml` 会被资源压缩从
  release APK 里删掉 —— 它们在**源码树里**保留着（用户要求保留图标）。

## 9. 只有真机才能验的部分（请重点看这些）

1. **极光背景本身**（`glow.glsl` 的 AGSL + 20% 尺寸渲染再放大 5×）：开场圆形揭示、环带、
   噪声流动、2s↔120s 的 ping-pong。minSdk 33 所以 `RuntimeShader` 一定可用，但画面对不对只能眼睛看。
2. **miuix 的按钮组**（`GroupButtonsConfig` 生成的"继续 / 跳过"）：尺寸、圆角、间距在包里，
   非 MIUI 设备上长什么样没验过。
3. **开场圆钮的放大转场**（`fan.transition.ActivityOptionsHelper.makeScaleUpAnim`）：
   它在非 MIUI 上可能返回 null——`StartupFragment` 本来就有 null 分支，会无动画直接进权限页。
4. **模糊**（`MiuiBlurUtils`）：非 MIUI 不生效，本来就这样。
5. **第三页（AI 配置）这一页本身**：miuix 的 `fan.preference.PreferenceFragment` 只在 MIUI 上
   被上游用过，非 MIUI 设备上它长什么样、`EditTextPreference` 的编辑弹层（miuix 自己的
   `miuix_preference_dialog_edittext.xml`）能不能弹出来、四行的图标对齐——都要眼睛看。
   四个行图标是复用已搬进来的它自己的 drawable（上游这页本来没有行图标），不是正式美术。
6. **连接测试真的连一次**：填上 Key 点一下，看摘要是否变"连通，N 个模型"；故意填错 Key 看是否
   变"失败：HTTP 401 …"。它与应用里 AI 页的"获取模型列表"是同一次请求。
7. **引导里填的值应用有没有吃到**：在第三页把模型名改成别的，进应用「AI 配置」页看是不是同一个值
   （两边都走 `AiManager` / `ai_config`，理论上必然一致，但仍值得看一眼）。
8. **我们自己的品牌在极光上好不好看**：开场页是"标记（白，裁到 90dp 满宽）+ 应用名 32sp
   + 一行副标题"，完成页同款但用深色。标记本身窄长（约 5:1），所以在 90dp 方块里是"宽而扁"
   的一条 —— 亮不亮、够不够醒目，要眼睛看。
9. **完成页 `system_state_text`（「系统准备中 / 设置完毕」）的颜色是上游写死的 `#BF000000`**，
   而这一页的极光没有开场遮罩、是亮的、有时偏白：如果真机上这行字读不清，那是上游的配色
   （我们没有改），修的话把它换成与标记同色系即可。
10. **权限页的两行是否真好用**：点"无障碍服务"跳系统设置、点"悬浮窗权限"弹授权框，
    回来（`onResume`）勾是否亮。
11. **返回键路径（现在是四页）**：开场返回 = 退出引导且不再出现；权限页返回 = 回开场；
    第三页返回 = 回权限页；完成页返回 = 回第三页。
12. **深色/浅色**：`ProvisionTheme` 浅色走 `DayNight`、夜间走 `Dark`，`values-night` 的颜色是它原本的。

## 10. 还剩什么

- 上面第 9 条里 5、6、8、9 四项是人来决定的事（第三页的真机观感、行图标美术、
  极光上的品牌观感、完成页那行字的配色），代码上已经没有卡点。
- 仍然是上游美术、但我们没动的：`provision_logo_image_bg.webp`（"低端机走静态背景"那条路的
  背景图，`OobeUtils.isLiteOrLowDevice()` 上游硬编码 false ⇒ 这条分支在真机上不会触发）、
  权限页/第三页的预览图标与行图标（`provision_service_state` / `provision_terms` /
  `provision_basic_settings` / `provision_picker_btn_radio`，都属于它搬进来的图标，
  用户要求保留）。开场页/完成页的两个 logo/字标 vector 仍留在 `res/drawable`，只是不再被引用。
- 真机验收通过后，才可以删掉 Compose 版引导（`love/miao/yun/ui/onboarding/*` + `MiaoState.showOnboarding`
  那两个调用点），以及考虑把 `Implementation(libs.miuix.legacy.*)` 里实际没被引导用到的 artifact 去掉。
- 没搬的（故意没搬）：协议与声明页（含条款 Web 弹层与 Markdown 渲染器）、
  `NoticeProvider` 协议版本同步、`AppLanguageHelper` 跨语言包装、`ProvisionManager`。
  依赖它们而失去作用的文件也一并没搬：`TermsAndStatementState`/`TermsAndStatementActivity`、
  `BaseListFragment`/`WebFragment`/`MarkdownView`/`TermsAndStatementBottomSheet`/`ClickSpan`/`TermsTitleSpan`、
  `NetworkManager`（权限页不再探网）。上游"基础设置"页原有的内容（语言 / 图标 / 作用域）没有搬过来，
  因为这一页已经改用 AI 配置；`AppLanguageHelper`/`AppSettingsStore`/`PrefsConfigurator` 都没有。
  仓库里仍有它们的死布局
  （`provision_page_layout.xml`、`provision_list_page_layout.xml`、`fragment_bottom_sheet_web.xml`、
  `provision_terms_and_statement_layout.xml`、`edit_verification_code_dialog.xml`、
  `provision_item_list_*.xml`）与死数组
  （`res/values/provision_arrays.xml` 里图标/语言那几组）——那是原样搬进来的资源，留着不影响，删不删随意。

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
