# 喵喵助手 / NekoPlus

一个基于 Jetpack Compose 的 Android 应用骨架，**双 UI 引擎**：同一套功能可以用
[miuix](https://github.com/compose-miuix-ui/miuix) 或 Material Design 渲染，随时切换。

> 当前进度：双引擎界面骨架 + 可用的悬浮窗 + **AI 修改文本**。
> 界面上的部分统计数字与开关仍是占位实现。

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
- **二级页面**：AI 配置、开源许可。这一层是唯一挂载 `PredictiveBackHandler` 的位置，
  返回时的跟手动画可选三种风格（见下）。

  二级页面自己就是一个完整页面，**自己带顶栏**（MD 用小的 pinned `TopAppBar`，miuix 用
  带大标题的 `TopAppBar`）。之前 MD 那侧是外壳先画一条 `LargeFlexibleTopAppBar`、页面里再画
  一条，两条叠起来把第一个控件压到了屏幕三分之一以下——现在外壳只负责两个层叠的层与动画。

  顺带一个坑：miuix 的 `TopAppBar` 必须和页面拿到**同一个** `ScrollBehavior`。只把
  `MiuixScrollBehavior()` 给列表、不给顶栏时，`TopAppBarState.heightOffsetLimit` 会停在
  `-Float.MAX_VALUE`，于是那个 nested-scroll 连接会把每一次向上滚动全部吃掉——
  AI 配置页因此完全滑不动。

### 预见式返回动画

**整套自己实现，只读 androidx 的 `PredictiveBackHandler`**，不再依赖 `miuix-nav`。
设置里三选一：**AOSP**（默认）、**Miuix**、**无动画**。

之前的版本把参考项目的变换代码原样搬过来，再手工拼一个 `NavTransitionScope` 喂给它，
bug 就出在这一层：手势取消时，`androidx` 会立刻取消回调所在的协程，而从那个协程里启动的
回弹动画会被一起取消——页面就那样卡在半路。现在：

- `ui/predictiveback/PredictiveBackHost.kt` 是所有状态与几何的唯一实现，两个引擎都挂它。
- **一个驱动值**：`progress` 从 `0`（二级页面完全展开）到 `1`（已经消失）。手指按下时
  `snapTo` 直接跟手，松手后从手指离开的位置继续。
- **释放动画跑在独立的 scope 上**（`rememberCoroutineScope`），手势协程被取消也杀不掉它。
- **每个样式只是一组数字**（`BackMotionConfig`）：AOSP 是「缩小 + 跟手横移 + 随手势上下
  漂移」，Miuix 是「整屏 1:1 跟手滑出 + 下层视差 1/4 屏」，无动画则完全不跟手。
- 几何在 `graphicsLayer { }` 里**按绘制期求值**，整个手势期间零重组；不滑动时这个 modifier
  根本不挂，所以一级页面（含液态玻璃底栏的 backdrop）在静止时与以前完全一致。
- 关闭按钮与返回手势走同一条路径：页面先走完剩下的位移，再从组合里摘掉，不会出现「半路消失」。

## 两套引擎的排版差异

同一批设置页在两个引擎里的内边距来源不同，改布局时容易多塞一层：

- **miuix**：`SegmentedColumn` 没有对应物，卡片自己不带外边距，所以页面要给
  `innerPadding + 12.dp`。
- **Material Design**：`SegmentedColumn` **自带** `PADDING_HORIZONTAL = 16`，
  所以页面再补一层 16 dp 就会让卡片比参考项目窄一圈 —— 二级页面只补
  `PaddingValues(bottom = 24.dp)`，横向交给 `SegmentedColumn`。

miuix 输入框的颜色也是在这一节定的：默认 `secondaryContainer` 在动态取色下偏深，
`ui/miuix/MiuixFields.kt` 里的 `miaoTextFieldColors()` 改用参考项目那套
`surfaceContainer`（浅、中性，仍然是输入框），标签退到次要文字色，焦点描边保留主色。

## AI 修改文本

第一个真正的功能，从原 NekoNeko 项目移植（`ai/AiManager.kt`、`ai/TokenStats.kt`，
以及 `service/MiaoAccessibilityService.kt`）。链路是：**抓取当前输入框 → 调 AI 改写 → 写回**。

- **只在点击时动作**：`MiaoAccessibilityService.onAccessibilityEvent` 是空的，
  不会自动读取任何内容。抓取顺序是先 `findFocus(FOCUS_INPUT)`，
  拿不到焦点信息再遍历节点树找「聚焦且可编辑」的节点；写回走 `ACTION_SET_TEXT`。
- **接口**：OpenAI 兼容的 `POST {base}/chat/completions`，默认 `https://api.deepseek.com`
  与 `deepseek-v4-flash`。`GET {base}/models` 用来拉模型列表并顺带验证连通性。
- **提示词**：用户提示词里含 `{text}` 时整体替换为捕获文本，否则作为人设拼在正文前；
  系统提示词单独发送。内置「微软式翻译」「微软式中文」「Emoji」三个预设。
- **配置**：两套引擎各有一个「AI 配置」二级页面（接口 / 连接测试 / 提示词 / 预设 /
  用量统计）。改动即时落盘，没有保存按钮。SharedPreferences 的文件名与键名与原项目
  逐字一致，可以直接沿用原来的配置。
- **入口**：任何一个动作为「AI 修改」的悬浮窗按钮（默认那一个就是）。走完整条链路，
  结果与失败原因用 Toast 告知。

需要授予无障碍权限（设置 → AI 修改文本 → 无障碍服务），
`AndroidManifest.xml` 里的服务由 `BIND_ACCESSIBILITY_SERVICE` 保护。

## 首页

首页是状态面板，两张卡片是让这个应用真正能做事的两个开关，都可直接点按
（启动/收起悬浮窗、跳转无障碍设置），配色随状态在 `primaryContainer` 与
`errorContainer` 之间切换。

两张不是等重的：**悬浮窗**是主操作，占一张大卡片；**无障碍服务**改成一行高的紧凑卡片
（`CompactStatusCard`），两条大色块叠在一起只会读成一堵墙。MD 引擎那侧保留两张等重卡片，
但列表加了 `Arrangement.spacedBy(12.dp)`——它们原本是两个相邻 item，中间一条缝都没有。

无障碍状态由 `MainActivity.onResume` 刷新：这个开关只能在系统设置里改，
回到前台正是它可能变化的时刻。

## 悬浮窗

`service/FloatingWindowService.kt` 用普通 `View`（非 Compose）实现**一组可拖拽按钮**，
通过 `TYPE_APPLICATION_OVERLAY` 叠加。前台服务类型为 `specialUse`，并带一条常驻通知。
悬浮窗开关会同步到首页的大卡片：关闭时显示「未在工作」，开启时整块变成「正在作为悬浮窗」。

### 可自定义，而且可以有多个

`floating/FloatingWindowPrefs.kt` 把按钮列表存成 JSON（`floating_window` 这个
SharedPreferences），**每个按钮各自独立**：

| 项 | 说明 | 范围 |
| --- | --- | --- |
| 开关 | 每个按钮能单独收起来，不用删掉，设置全都留着 | 开 / 关 |
| 图标 | Material Icons（Rounded），15 个内置图标 | 向量 drawable |
| 显示图标 | **由用户决定**显示与否，不自动隐藏 | 开 / 关 |
| 文字 | 或者不用图标，自己写 1–3 个字 | 1–3 字 |
| 点击 | **一条动作链**，最多三步，按顺序执行 | 4 选 1 × 最多 3 步 |
| 长按 | 同样是一条链，或留空 | 5 选 1 × 最多 3 步 |
| 宽度 | 与高度各自独立 | 24–160 dp |
| 高度 | 与宽度各自独立，两个一起就能做成侧边悬浮条 | 24–160 dp |
| 形状 | 圆形 / 方形 / 横条 / 竖条，一次到位 | 预设 |
| 圆角 | 半径，给到短边一半就是胶囊 | 0–短边/2 dp |
| 不透明度 | 整个按钮的透明度 | 30–100% |
| 位置 | 拖动即移动，松手后写入偏好 | 像素 |

**动作链**是为了能串起有先后的操作：先用 AI 改写，再对结果做一次替换。每一步都在上一步
真的完成之后才开始 —— 按钮上的进度圈会一直转到整条链跑完，而不是第一步就熄掉。
「收起悬浮窗」是终点：它执行完就没有按钮可以继续了，所以链到此为止。

**两个边分开设**是为了能做「侧边悬浮条」：`40×132 dp` 的竖条贴在屏幕边上，`132×40 dp`
的横条当工具条用。代价是图标 —— 图标是方的，硬塞进细长条里不是被挤扁就是溢出，所以
图标是方的，硬塞进细长条里会被挤扁。所以**显示图标这件事交给用户自己决定**（编辑器里一个
开关：关掉就是一条干净的色块）；只在短边不足 30 dp 或长短边之比超过 2.2 时，编辑器会提示
一句「当前尺寸下图标会偏小或被压扁」，但**不替用户做决定**。想给悬浮条加字就切文字模式：
文字是按长边读的，在条上反而更合适。

图标是**向量 drawable**（`res/drawable/ic_ball_*.xml`，取自
[google/material-design-icons](https://github.com/google/material-design-icons)，Apache-2.0）
而不是字形或 emoji：drawable 是 Compose（`painterResource`）和普通 View 悬浮层
（`setImageResource`）都能画的那一种形式，所以设置里选到的图和屏幕上出现的图是同一份资源，
不用为两套渲染各画一遍。选图用 `FlowRow` 平铺，一眼看全。

"拖动"分组里还有两个作用于整个悬浮层的开关：**贴边吸附**（松手后吸到最近的一侧）
与**拖动反馈**（开始拖动时轻微震动）。

服务对这个偏好注册了 `OnSharedPreferenceChangeListener`：在任何一处改了按钮，屏幕上的
按钮**当场**增删或改样子，不用重启。拖动位置只在拖动结束时落盘，所以单击永远不会写状态；
长按是自己用 `postDelayed` 检测的，因为消费掉手势的 touch listener 会让
`View.onTouchEvent` 不再执行，框架自带的长按检测也就不会触发了。

执行结果用 Toast 反馈——几个按钮没有可以写状态行的面板。**AI 修改运行期间按钮上转的是
系统那个不确定进度圈**（`ProgressBar`，跟着取色来源着色，并且固定为正方形所以贴在悬浮条上
也不会被拉成椭圆），结束后恢复原来的图标或文字。

### 取色

悬浮窗位于任何 Compose 主题之外，颜色不能靠继承，所以在**悬浮窗页签的外观分组**里三选一
（`ui/FloatingColorSource.kt`）：**动态取色**（默认）、**跟随 Miuix**、**跟随 Material Design**。

`ui/FloatingPalette.kt` 负责解析。关键在于 material3 与 miuix 的颜色方案工厂函数都是
非 `@Composable` 的（`dynamicLight/DarkColorScheme(context)`、`light/darkColorScheme()`），
所以一个没有任何组合的 `Service` 也能直接拿到调色板。明暗按系统夜间模式决定。

服务注册了 `OnSharedPreferenceChangeListener`：改设置时悬浮窗当场换色，不用重启。

## 新手引导

首次启动会先走一遍四页引导（`ui/onboarding/`），两套引擎各有一份实现，盖在整个界面上：

1. 这个应用做什么；
2. 要开的两个开关 —— 无障碍服务与悬浮窗权限，**带实时状态**，点一下直接跳系统设置；
3. 按钮怎么用（点按 / 长按 / 拖动 / 贴边）；
4. 去哪里填 API Key。

「跳过」和「开始使用」等价，都会把 `onboarding` 这个 SharedPreferences 标记为已看过，
所以引导只在自己会出来的时候出现一次；想再看就去 **关于 → 新手引导**，那只是把
`MiaoState.showOnboarding` 重新置为 true，不会动那个标记。返回键等于「跳过」。

## 图标

应用图标是**文字标记** `ᯠ ͟͟    ̫  ͟͟ ᯄ ੭`，不是图片素材：

- 启动图由 `~/icons/render_icon.py` 离线渲染 —— 逐字符挑字体（`ᯠ`/`ᯄ` 只有
  `NotoSansBatak` 有、`੭` 在 `MiSansGurmukhi`、组合符 `͟`/`̫` 在 Roboto），
  **共用同一条基线**绘制，才能在画布里正确居中；输出五档 `mipmap-*/ic_launcher(.round).png`
  与自适应图标前景 `drawable/app_icon.png`。
- 关于页不加载那张位图，而是把同一个字符串（`AppIconText`）当**居中文字**画 —— 既跟随主题
  墨色，也避开了一个坑：启动器图标是 **adaptive icon**，而 Compose 的 `painterResource`
  只认位图与矢量图，拿它去画会直接抛异常。
- 常驻通知仍是矢量猫脸剪影（`drawable/ic_notification.xml`）：通知小图标只用 alpha 通道，
  一串文字在 24dp 上只会是一团糊。

## 配置备份

`util/PrefsBackup.kt` 把本应用的几个 SharedPreferences 文件整体导成一个 JSON：

- **不做手写清单**，而是遍历 `ui_prefs` / `floating_window` / `ai_config` / `token_stats` /
  `onboarding` 这些文件的所有键 —— 手写清单会在下一个功能加进来时悄悄漏掉它；
- 每个值都带类型标签（`b`/`i`/`l`/`f`/`s`/`set`）：JSON 里的 `1` 分不出是 Int、Long 还是
  Float，导入时必须知道；
- 导入只覆盖文件里存在的键，不做清空。

导出/导入走系统文件选择器（`CreateDocument` / `OpenDocument`），所以不需要存储权限，落在哪儿
由用户决定；两个引擎共用 `ui/BackupActions.kt`，避免一边能备份一边不能。导入之后会把
`MiaoState` 里的引擎、毛玻璃、悬浮窗取色、返回动画重新读一遍，不用重启。

**导出的文件包含 API Key**，界面上也写明了这一点。

## 更新检测

`util/UpdateChecker.kt`（移植自 NekoNeko）走 **GitHub Releases**：

1. 先打 API `releases/latest`，取 `tag_name`、`body`（更新说明）与 APK 资源链接；
2. API 报错（多半是被限流）就**回退到 releases 页面**，从 302 的 `Location` 里读 tag ——
   这一步不需要 token 也没有配额，差别就是「检查更新」到底能不能用。

版本比较在数字段之后还会比预发布后缀（`2.0.0` > `2.0.0-alpha.2` > `2.0.0-alpha.1`），
否则 alpha 用户会永远收不到正式版。关于页的「检查更新」会给出更新说明与下载按钮。

**按渠道查**：预发布版本（版本号里带 `-`）走 `/releases` 列表，正式版走 `/releases/latest`。
两个接口不能混用 —— `/releases/latest` 按设计**忽略预发布**，alpha 用它就永远看不到下一个
alpha；反过来正式版看全表，就会被推 alpha。另外发布时如果勾了 GitHub 的
「Set as a pre-release」，只有 alpha 渠道的版本能看见它，这正是想要的行为。

> 刚发布完立刻检查可能仍显示「已是最新版本」：GitHub 的 releases 接口有几十秒缓存。

## 无障碍服务的组件名伪装

`com/google/android/accessibility/selecttospeak/SelectToSpeakService.kt` 继承真正的实现
（`service/MiaoAccessibilityService.kt`，它因此是 `open class`），清单里**只声明这个壳**。

原因是微信 8.0.52+ 对不认识的无障碍服务会给一棵**空节点树**：窗口还在、`title` 还是「微信」，
但 `root` 回来只有一个 `class=?`、`bounds=[0,0][0,0]`、没有子节点的占位节点 ——
这就是「微信里抓不到输入框」从内部看的样子，再怎么遍历也没有可遍历的东西。
把组件名换成系统自带的 select-to-speak 服务名就能过这一关（原版 1.1.8 用的就是这一招）。

代价写在明处：系统无障碍设置里显示的组件名不再是我们自己的；而且**已经开过旧组件名的用户
必须重新开一次**，因为平台列表里留的是旧名字，它已经解析不到了。

**已在微信上验证通过**（小米 2510DRK44C / Android 17，微信 8.0.x）：伪装前的 dump 里微信窗口
`rootChildren=0`、只剩一个 `class=?` 的占位节点；伪装后 `rootChildren=3`、整棵树可读，前台候选
节点就是微信自己的输入框 `com.tencent.mm:id/bkk`（`focused,editable,visible`，且同时支持
`SET_TEXT` 与 `PASTE`）。调试 dump 的第一行「本服务组件名」就是判断伪装有没有生效的开关。

## 调试模式

有些应用抓不到输入框（微信就是），因为它的聊天框是自绘的、上报 `isEditable=false`，
而且经常不在前台窗口的节点树里。为了能离线定位这种问题，设置 → 调试 里有一个**调试模式**：

- 打开后可以查看**最近一次抓取**（可复制、可清空）；
- 抓取动作本身是一个悬浮窗动作 —— **导出界面元素**，把它设成某个按钮的点击动作，
  在出问题的那应用里点一下，它会把当时的界面写进
  `Android/data/love.miao.yun/files/debug/ui-dump.txt`。

文件内容分两段：先是一段**诊断**（前台包名、窗口数、按分数排好序的候选输入节点，以及
「当前会选中哪一个」），后面是**完整树** —— 每个窗口、每个节点一行，含 class、viewId、
text/desc/hint、屏幕坐标、全部状态位与 action 列表。

顺带把抓取逻辑本身放宽了：不再只看「前台窗口里获得焦点的可编辑节点」，而是遍历**所有窗口**
收集所有可能是文本框的节点（`isEditable`、类名像 EditText、带 `ACTION_SET_TEXT`/`ACTION_PASTE`），
再按「获得焦点 > 可编辑 > 类名像输入框 > 支持写入」打分取最高；写入失败时
`ACTION_SET_TEXT` 会退到「写剪贴板 + `ACTION_PASTE`」，微信这类只吃粘贴的输入框就有着落了。

## 崩溃日志

`util/CrashHandler.kt`（同样来自 1.1.8 的功能清单）在 `MiaoApp` 里安装，崩溃时把
时间、版本、机型、线程、**最近经过的界面**与完整堆栈写到
`Android/data/love.miao.yun/files/crash/latest.txt`；两个外壳在每个页签与二级页面上都留了
面包屑。关于页的「崩溃日志」可以查看、复制、清空。

## 文案

关于页版本号下方那句是 `Ciallo～(∠・ω c)⌒★`。开发者一栏有两位：**Xiao-youyu**
（原作者，喵喵助手 1.1.8 的作者）与 **Youzix-Star**（本仓库的重构与维护）。

界面文案写在各屏的 Composable 里，`res/values/strings.xml` 只留平台按名字取的几条：
应用名、无障碍服务条目、常驻通知，以及悬浮窗用 Toast 报的结果。之前那份脚手架留下的
上百条 `R.string` 常量没有任何引用，已经删掉。

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
├── floating/                    悬浮窗按钮的模型与持久化
│   └── FloatingWindowPrefs.kt   图标 / 文字 / 点击与长按动作 / 大小 / 圆角 / 透明度 / 位置
├── ai/                          AI 层（移植自原 NekoNeko）
│   ├── AiManager.kt             OpenAI 兼容接口、配置、预设
│   └── TokenStats.kt            用量统计
├── ui/
│   ├── AppIcons.kt              统一图标入口
│   ├── onboarding/              新手引导（两套引擎各一份 + 偏好）
│   ├── predictiveback/          预见式返回：自成一体的实现，只依赖 androidx
│   │   ├── PredictiveBackHost.kt   两个层叠的层 + 手势驱动 + 每帧几何
│   │   └── PredictiveBackStyle.kt  AOSP / Miuix / 无动画，每个只是一组数字
│   ├── MainPagerState.kt        一级页签切换动画（移植自参考项目）
│   ├── FloatingColorSource.kt   悬浮窗取色来源（动态取色 / Miuix / MD）
│   ├── FloatingPalette.kt       把取色来源解析成 ARGB 调色板
│   ├── UiEngine.kt              引擎枚举、毛玻璃开关、悬浮窗取色与持久化
│   ├── miuix/                   miuix 引擎：MiuixApp + home/floating/settings/about/licenses
│   │   ├── ai/                  AI 配置页（miuix 组件）
│   │   ├── liquid/              液态玻璃底栏与镜片效果（Apache-2.0，来自 NekoEdit）
│   │   └── animation/           DampedDragAnimation、InteractiveHighlight
│   └── material3/               Material Design 引擎：MaterialApp + 同名页面树
│       ├── ai/                  AI 配置页（MD 组件）
│       ├── Backdrop.kt          MD 顶栏的毛玻璃（复用 miuix 的 backdrop 引擎）
│       └── widgets/             SegmentedColumn、BaseWidget、SwitchWidget、
│                                NavigationItemWidget、NumberPickerWidget、
│                                DropDownMenuWidget、SwipeableSnackbarHost
│                                （GPL-3.0，来自 InstallerX-Revived）
└── service/
    ├── FloatingWindowService.kt 悬浮窗：一组可拖拽按钮（普通 View）
    └── MiaoAccessibilityService.kt  读取/写回当前输入框
```

## 许可

本项目以 **GPL-3.0** 发布。

其中 `ui/material3/widgets/` 移植自 [InstallerX-Revived](https://github.com/wxxsfxyzm/InstallerX-Revived)
（GPL-3.0），`ui/miuix/liquid/` 来自 NekoEdit（Apache-2.0），
两者的原始版权声明与 SPDX 标识均保留在各文件头部。
