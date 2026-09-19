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

| 行 | key | 行为 |
|---|---|---|
| API 接口地址 | `base_url` | 点开 miuix 编辑框（`TYPE_TEXT_VARIATION_URI`），摘要显示当前值 |
| API Key | `api_key` | 点开编辑框（密码输入类型），摘要只显示末 4 位 / 「未设置」 |
| 模型 | `model` | 它的 `DropDownPreference`（与它语言/图标两行同一个类）：进页面就用应用自己的 `GET {base}/models` 拉一次列表，拿到的 id 同时当 entries 与 entryValues；摘要「拉取中…」→ 当前模型（或「当前 xxx 不在列表里」）/「拉取失败：HTTP 401 …」 |

（行图标：最后一轮按用户要求去掉了，和我们的设置页一致。）

**「拉列表」就是连接测试**：没有单独的测试行——`AiManager.listModels` 是应用 AI 页"获取模型列表"
用的同一次请求、同一份解析，失败信息就是连接诊断（HTTP 状态码、超时、空列表）。

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


### 2.2 品牌（上游的 logo / 字标 / 文案）

上游开场页与完成页显示的是 HyperCeiler 的 logo 与 "HyperCeiler" 字标 vector，文案是
"Welcome to HyperCeiler / Enter HyperCeiler"。这些换成了本应用的，**布局、尺寸、位置、
间距与入场动画一字未改**（是替换，不是重新设计）：

| 槽位 | 上游 | 现在 |
|---|---|---|
| 90dp 标记位 `logo_image` | `provision_logo_image(_lite).xml` —— 它的 logo vector（ImageView） | `TextView`，文字取应用的常量 `love.miao.yun.ui.AppIconText`（About 页同一个；launcher 图标里烤的也是这一串），在 Fragment 里设置；`autoSizeTextType="uniform"` 只是让这个窄长（约 5:1）的标记始终填满这 90dp 宽而不被省略号截断。开场页白色，完成页用它自己的 `provision_complete_title_color` |
| 字标槽 `text_logo_image`（320×45.28dp，它的 dimens） | `provision_text_logo_image(_lite).xml` —— "HyperCeiler" 字标 vector | 应用名文字 `@string/app_name`（「喵喵助手」），字号用它自己的 `provision_title_text_size`，同为白色/深色 |
| 开场页字标下面（新增一行） | — | 副标题「把输入框里的字改好」，用它自己的 `provision_subtitle_margin_top` / `provision_subtitle_text_size`，与 Compose 版引导首屏那句一样 |

尺寸/位置/动画没动的三点：这两个槽位仍在原来的父容器与同一组 dimens 里；新增的一行放在同一个
`logo_image_wrapper` 里（容器由 `FrameLayout` 改成纵向 `LinearLayout` 才叠得下，宽高都是
`wrap_content` + 居中，与原来等价），所以上游对 wrapper 整体做的入场动画原样作用在两行上；
Java 里 `mLogoImage`/`mTextLogoImage` 由 `ImageView` 变成 `TextView`，对应的
`setImageResource(R.drawable.provision_logo_image*)` 调用删掉，**两个 vector 仍留在 `res/drawable`**。

文案前后对照（用户能看到的都在这张表里）：

| 资源 | 上游 | 现在 | 谁能看到 |
|---|---|---|---|
| `provision_complete_text` | "Enter HyperCeiler" | 「开始使用」 | 完成页按钮（zh-rCN 那份本来就是「开始使用」） |
| `provision_congratulation_label` | "Welcome to HyperCeiler" / 「欢迎来到 HyperCeiler」 | 「喵喵助手 · 把输入框里的字改好」（两份都改） | **实际上看不到**：上游自己的完成页布局也没引用它（我们的同样没有），release 里被资源压缩删掉；改成我们的名字是为了它不再指向别的产品 |
| `provision_terms_of_use_label_use_network_china` | "…before you can continue using HyperCeiler" / 「…才可继续使用 HyperCeiler」 | 换成「喵喵助手」/「喵喵助手」 | 协议页没搬，这条串没有任何布局/代码引用（死资源）；按"别让它意外露出来"处理 |
| `provision_guide_subtitle` | — | 「把输入框里的字改好」（新增串） | 开场页副标题 |

**别的产品名剩下的地方（都不可见，已逐条核对）**：

- 构建产物里 `resources.arsc` **一次都没有** HyperCeiler / 迅雷 / sevtinge（见 §8 的核对），
  也就是说没有任何 label、标题、按钮或提示能把它显示出来；
- dex / 布局 XML 里剩下的是**内部标识符**：`com.sevtinge.hyperceiler.provision.*` 包名与类名
  （原样搬它的代码就必然带着）、`OobeUtils` 写的 SharedPreferences 文件名 `hyperceiler_prefs`；
- dex 里那条 `https://github.com/ReChronoRain/HyperCeiler` 来自应用自己的开源许可页
  （`ui/miuix/licenses/LicensesScreen.kt` / `ui/material3/licenses/`）——那是搬它 AGPL 代码
  **必须**给的署名，故意留着；
- 死资源里还带别人名字的：`provision_service_policy_download`（迅雷 / Thunder，它自己的下载服务行）、
  `provision_get_md_failed`（Markdown 文本里的 `hyperceiler://refresh` 协议名）。两条都没有任何
  布局/代码引用，release 里会被资源压缩删掉，留着只为保持"原样搬进来"的完整性。

### 2.3 AI 页那次真机崩溃（Preview 3 → Preview 4）

现象（用户真机，Xiaomi 2510DRK44C / Android 17 SDK 37）：

```
BasicSettingsFragment.onCreatePreferences
  → PreferenceFragmentCompat.setPreferencesFromResource
  → NPE: Attempt to invoke virtual method 'java.lang.String java.lang.Package.getName()' on a null object reference
```

**真因（两层都验过）**：

1. `androidx.preference.PreferenceInflater.init()` 用类自己的包名当"默认包前缀"：
   ```java
   setDefaultPackages(new String[]{
       Preference.class.getPackage().getName() + ".",
       SwitchPreference.class.getPackage().getName() + "."});
   ```
   这是 **androidx 上游 1.2.1 的原文**（已取 `preference-1.2.1-sources.jar` 核对，不是 miuix 改的）；
   `fan.miuix:preference` 里 `PreferenceInflater` 的 dex 也正好是
   `Class.getPackage()` → `Package.getName()` 这条链（反汇编 `init` 看到调用点就在
   `setDefaultPackages` 之前）。它拿到前缀后再按名字去加载 XML 里写的 preference 类。
2. 我们的 release 走 AGP 的 `proguard-android-optimize` 默认，R8 会把混淆后的类**重打包进无名包**
   ——上一个 APK 里 4257 个类有 4118 个是 `La0;` 这种没有包的短名（`gu1`/`pu1` 就是栈里那两个），
   `androidx.preference.Preference` 也在其中 ⇒ `Class.getPackage()` 返回 **null** ⇒ `getName()` 崩。

**修法：照抄 HyperCeiler 自己的规则**（它 app 的 keepRules 里就有 `-repackageclasses`，
同样会把类丢进无名包，所以它必须保这个包）：

```
-keep class androidx.preference.** { *; }        # library/core/src/main/keepRules/rules.keep:14
```

我当初只抄了同一个文件里的两条 `-dontwarn`，漏了这条。**整包保名而不是只保 `Preference`**：
它推出来的前缀还要用来按名字加载其它 preference 类（我们的 XML 里就是全限定的
`androidx.preference.EditTextPreference`）。
Preview 4 的 dex 里 `Landroidx/preference/` 有 80 个类（改之前只有 1 个），
`Preference` / `PreferenceInflater.init` / `PreferenceFragmentCompat` / `EditTextPreference` 都在，
`Preference.getPackage()` 因此非 null。页面本身**一行代码没动**，外观仍是它的 preference 行。

### 2.4 开场页那行副标题的颜色

`#FFE8E4FF` 在真机上就是白（用户反馈"还是白色"），换成 **中深板岩紫 `#FF3E3670`**，
阴影由深改浅：`#99FFFFFF`、半径 10px、无偏移。理由：

- 这行压在极光中段（亮粉彩）上：`#FF3E3670` 相对亮度 ≈0.045，对亮粉彩（≈0.72）约 **8:1**，
  对极光中调（≈0.42）约 **5:1** —— 明显不是白，且清楚；
- 中深色唯一吃亏的地方是极光的**暗饱和谷**（≈0.13，约 1.9:1），那里靠浅色光晕把字缘托起来，
  所以阴影用浅色、无偏移（不是投影，是光晕）；深色阴影只会把中深色字糊成一团；
- 名字那行（`NekoPlus`，白）没动。

### 2.5 四个步骤的大小对齐（用户已定，本轮全部落地）

用户反馈「各个界面感觉图标大小、文字大小都有点不一样」。把四页并排量了一遍：同一个元素在不同页用了
不同的数，多数不是上游的锅，是我们的内容替换后没回头统一。**结论：四页现在只有三个字号刻度。**

#### 文字

| 元素 | 出现在 | 数字 | 来源 | 本轮/上轮的动作 |
|---|---|---|---|---|
| 页面标题（权限设置 / AI 配置） | ②③ | **32sp** | `provision_title_text_size`（它的 dimen）+ `ProvisionPageTitleTextStyle`（它的样式） | 本来就一致 |
| 品牌名 | ①④ | **32sp** | 同一个 dimen | 本来就一致 |
| 品牌名下面那行（① Ciallo ／ ④ 设置完毕） | ①④ | **14sp** | `provision_subtitle_text_size`（它的 dimen） | ④ 由 24sp（`provision_congratulation_title_text_size_complete`）改成 14sp |
| 列表前导行（②「您可以稍后设置…」／③ 分类标题「接口」） | ②③ | **14sp** | ② `miuix_appcompat_secondary_text_size`、③ `miuix_preference_category_text_size`（都是它的库 dimen，都是 14sp） | ② 去掉布局里写死的 13sp 覆盖 |
| 行标题 | ②③ | **17sp** | `miuix_preference_normal_text_size`（它的库 dimen，② 走 `TextAppearance.PreferenceList`） | ② 补上显式 17sp，写明出处 |
| 行摘要（③ 当前值 / 拉取结果） | ③ | **14sp** | `miuix_preference_secondary_text_size`（它的库 dimen） | 本来就一致 |
| 底部按钮（继续 / 跳过） | ②③ | **17sp** | `miuix_appcompat_button_text_size`（它的 `Widget.Button` 样式） | 本来就一致 |
| 完成页按钮 | ④ | **17sp** | 它的布局写的是 **17dp** | 改成 17sp（数字不变，跟随系统字体缩放），于是与底部按钮、行标题同号 |
| 字体族 | ②③④ | 它的 MIUI 专有族（`TextAppearance.PreferenceList` 的 misans-medium、④ 的 `mipro-regular`） | 它的样式 | 未动：非 MIUI 上回退系统字体，实际同一套字形 |

#### 图标 / 标记

| 元素 | 出现在 | 数字 | 来源 | 动作 |
|---|---|---|---|---|
| 品牌标记（文字标记 `AppIconText`） | ①④ | 盒子 **104dp**、autoSize ≤ **40sp** | 104dp 是用户前几轮放大的；40sp 上限本来两页不同 | ④ 由 34sp 上限改成 40sp，两页完全一致 |
| 页面预览图标（`provision_service_state` / `provision_basic_settings`） | ②③ | **70dp** | `provision_preview_image_size`（它的 dimen）；两个 drawable 自己声明 100dp，由 ImageView 缩到 70dp | 本来就一致 |
| 行内勾（`provision_picker_btn_radio`） | ② | **24dp** | 上游 drawable 自称 `android:width="64px"`（**px**：密集屏≈24dp、mdpi≈64dp，没有 dp 数可抄） | `wrap_content` → 24dp（`provision_guide_row_icon_size`，**我们的数**，MIUI 常规行图标尺寸） |
| actionbar 返回箭头 | ②③ | **40dp** | `provision_actionbar_icon_size`（它的 dimen） | 本来就一致 |
| 开场圆钮与其箭头 | ① | 圆钮 **70dp**、箭头 29×20dp | `provision_next_button_size`（它的 dimen）+ 它布局里的写死值 | 本来就一致 |
| 行图标 | ③ | **无** | 用户决定（与我们的设置页一致） | 未动 |

> 两类"锚点"尺寸不同是**有意的**：品牌标记 104dp（用户要求放大过的）与页面预览图标 70dp（它的 dimen）。
> 同类之间已经一致（两个极光页的标记、两个内容页的预览图标、所有按钮 17sp）。

#### 间距 / 节奏（用户这一轮拍板的三件）

| 项 | 决定 | 落地 |
|---|---|---|
| **M1** 两页首行同高 | 采纳 | 先纠正我上一轮的描述错误：权限页并不是"首行从 0 开始"——它上面有那行说明（我两轮前把它移到最前面），首行本来就在 ≈43dp；AI 页是 `miuix_preference_rv_padding_top` 7.27dp + 分类头 35dp ≈ 42.3dp。所以按字面加 42dp 反而会错位 42dp。**按意图落地**：给权限页也加上同样的列表上边距（`provision_guide_list_top_inset` 7dp，= 它 7.27dp 取整）并把说明行的上下边距改成 8dp/8dp（= 它分类头的 8/8dp 配方）⇒ 两页首行都在 **≈42dp**（42.0 vs 42.3） |
| **M3** 开场页副标题间距 | 4dp → **16dp** | `provision_guide_subtitle_margin_top` = 16dp，两个极光页共用（④ 原来是写死的 30dp）。16dp 是用户选的：它给的两个数（4dp 是"页副标题"、30dp 是完成页）的中间值，因为这两页是品牌块不是页标题 |
| **空白** 内容居中 | 两页都居中，actionbar/底部按钮原位 | ② `provision_permission_layout.xml` 的内层 `LinearLayout` 加 `android:layout_gravity="center_vertical"`（ScrollView 仍 match_parent：内容比视口小时 FrameLayout 才应用 gravity，变高时照样能滚）；③ 覆盖它自己的钩子 `getListViewPaddingTop()`（**protected、子类可覆盖**，已用 AAR 方法表确认 `onCreateRecyclerView` 会调它）返回 `super + provision_guide_list_extra_top`(96dp，**我们的数**，按 ~558dp 内容区/约 230dp 内容、余量约 330dp 算，居中一半是 ≈165dp，96dp 是保守起点，旋钮就这一个值) |
| **M2** 行高 | 保持上游：② 56dp vs ③ ≈70dp | 回退了我上一轮擅自统一的 65dp：② 重新用它的 `provision_list_item_height`(56dp)。③ 按它库里的数算：14dp + 17sp 行 + 14sp 行 + `miuix_preference_summary_margin_top` 0dp + 14dp ≈ 65dp（用户记作 ≈70dp）。③ 的行有摘要，值这份高度，采纳用户判断 |

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
  - **当前（Preview 4 = 四步 + 品牌 + AI 页 NPE 修复）**：
    分支 run https://github.com/Youzix-Star/NekoPlus/actions/runs/35384135520（绿）
    · tag run（发 preview APK）https://github.com/Youzix-Star/NekoPlus/actions/runs/35384541185（绿）
    · 用户可装的 Release 产物：
    `https://github.com/Youzix-Star/NekoPlus/releases/download/v2.0.3-onboarding-preview4/MiaoAssistant-v2.0.3-onboarding-preview4.apk`
    8 155 556 字节，`sha256 f0d6f516056c8b49faad46e6d913f5cead0c58fbde034160939e781a188ae9bd`
    （v2/v3 签名；本地 `~/rel-p4/`）。CI artifact 是另一次构建：`~/apk-p4/MiaoAssistant-1-merge.apk`，
    同一份源码，`sha256 9fa0d93635f29b77315a2abb73319255fa6b6063136b33ab23dfe020e316e704`
    —— 两个 hash 不同只是因为重新构建（zip 条目时间戳/签名块），内容逐项核对一致。
  - **Preview 8（四页大小/字号/图标对齐 + 用户拍板的 M1/M2/M3/居中）**：
    tag 与产物在最后一轮被**重指到最终 commit**（同一版本号 113 / Preview 8；上一轮那个
    `sha256 28207f7e…` 的构建已作废）。最终：
    分支 run https://github.com/Youzix-Star/NekoPlus/actions/runs/35390454728（绿）
    · tag run https://github.com/Youzix-Star/NekoPlus/actions/runs/35390998138（绿）
    · Release 产物 `https://github.com/Youzix-Star/NekoPlus/releases/download/v2.0.3-onboarding-preview8/MiaoAssistant-v2.0.3-onboarding-preview8.apk`
    8 104 672 字节，`sha256 28207f7ee82555e8ff14ee1b008332eab58f745b69592659cbdb0af7a5c9cba1`（本地 `~/rel-p8/`）
  - 更早的：Preview 7 run 35388714409（`~/rel-p7/`）、四步 + 品牌 run 35378199008（`~/apk-brand/`）
  - 产物：artifact `MiaoAssistant-release-apk`，解出来是 `MiaoAssistant-1-merge.apk`，
    5 196 963 字节，`sha256 4e898517ca852be8ff3b4868778e754591bc291a7c3221437c7fcd413d100d30`
    （artifact id 10561376128；本地在 `~/apk-brand/MiaoAssistant-1-merge.apk`）。
    这个 PR 的 APK 是**有签名**的（CI 恢复了 keystore）。
  - 更早的绿色 run：四步 + 裁图版品牌 run 35376896117（`~/apk-branded/`）、四步版 run 35375307539
    （`~/apk-final4/`，`sha256 fe5bc171…`）、三步版 run 35373086630（`~/apk-final/`）。
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
- Preview 4 的核对（用 `~/dexclasses.py` / `~/dexmethods.py` / `~/checkbrand.py` 跑在**发布的那份 APK**上）：
  manifest 里有 versionName「2.0.3 Onboarding Preview 4」；`resources.arsc` 里 HyperCeiler 0 次、
  我们的文案都在；`Landroidx/preference/` 有 **80** 个类（修复前只有 1 个），
  `Preference` / `PreferenceInflater.init` / `PreferenceFragmentCompat` / `EditTextPreference` /
  `PreferenceCategory`（androidx 与 fan 两个包都有）全在、名字带包；
  `BasicSettingsActivity` / `BasicSettingsFragment` 在；APK 有 v2/v3 签名块。
- 品牌核对（对 `MiaoAssistant-1-merge.apk` 全量扫）：`resources.arsc` 里有「喵喵助手」
  「把输入框里的字改好」「开始使用」，而 HyperCeiler / 迅雷 / sevtinge **在整个 arsc 里是 0 次**
  （label、标题、按钮、提示都没有）；dex 里有 `AppIconText` 那个字串（标记是文字，不是图）。
  仍然出现产品名的地方只有内部标识符与许可署名，逐条列在 §2.2 末尾。
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
8. **我们自己的品牌在极光上好不好看**：开场页是"标记（AppIconText 文字，autoSize 填满 90dp）
   + 应用名 32sp + 一行副标题"，完成页同款但用深色。这个标记本身窄长（约 5:1），在这个
   90dp 方块里是一条"宽而扁"的字 —— 大小、字重、以及 ᯠ / ੭ 这类字符在系统字体回退下渲染得
   对不对，都要眼睛看（About 页同一个常量渲染是正常的，但那里是 52sp 宽屏）。
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
- 内部标识符里仍带 `hyperceiler`：包名 `com.sevtinge.hyperceiler.provision.*`（原样搬代码的必然结果）
  与 `OobeUtils` 的 SharedPreferences 文件名 `hyperceiler_prefs`。都不可见；要改就等于改它的代码，
  目前按"原样"留着。
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
