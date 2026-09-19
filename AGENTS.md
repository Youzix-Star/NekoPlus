# AGENTS.md —— 给下一个接手的人（和 agent）

这份文件是**经验**，不是文档：里面每一条都是这次真的摔过一跤才写下来的。
先读完 §1 和 §2 再动手，能省掉几个小时。

---

## 0. 项目现状（先看清你在哪个世界）

- **仓库**：`Youzix-Star/NekoPlus`，包名 `love.miao.yun`，主干 `main`。
- **两套 UI 引擎**：miuix（`top.yukonga.miuix.kmp.*`）和 Material 3（`ui/material3/*`）。
  同一套功能两套页面树，**互不共享 Composable**，切换在 `设置 → 外观`。
- **默认无本地构建**：全部靠 GitHub Actions 验证（见 §2）。这台设备是 Android/Termux，没有 Android SDK。
- **`main` 的状态**：CI 绿、`testDebugUnitTest` 38 个测试、release APK 可出；版本 `2.0.2 Beta 2 (105)`。
- **新手引导**：移植版在分支 **`feat/provision-port`**（草稿 PR #1），相对 main 有 100+ 文件。
  它把 HyperCeiler 的 `library/provision` 原样搬了进来（布局/动画/drawable/Java/状态机/发光引擎）。
  **main 上还是旧的 Compose 版引导**（`ui/onboarding/*`），**用户还没批准删除**，别自作主张删。
- 用户手上装的是分支构建的 **preview 包**（`v2.0.3-onboarding-previewN`），最新 **Preview 10（115）**，
  他对这一版的评价是「基本上就很好了」。
- **`main` 的版本号是 `2.0.2 Beta 2 (105)`，分支是 `2.0.3 Onboarding Preview 10 (115)`** ——
  分支还没合并，别把两者的版本号搞混。

---

## 1. 铁律（违反其中任何一条，这次都付过学费）

1. **"照抄"的任务就照抄，不要重写。**
   用户说"用它的代码/UI"时，意思是复制它的文件与布局，不是"照着做一个像的"。
   我第一版用 Compose 重写了一遍引导，被否掉，白烧约两小时。**先找它的源码，再决定动不动手。**
2. **整份抄 keep 规则，不要挑着抄。**
   两次致命崩溃（打开引导就崩、AI 页一点就崩）都是同一原因：
   `HyperCeiler/library/core/src/main/keepRules/rules.keep` 我只抄了 `-dontwarn` 那几条，
   漏了 `-keep class fan.** { *; }` 和 `-keep class androidx.preference.** { *; }`。
   那个仓库的规则分散在 `library/*/src/main/keepRules/*.keep` 和 `app/src/main/keepRules/rules.keep`——**全都读一遍**。
3. **不要凭记忆写 API。** 签名从 AAR/字节码里读（§4 有方法）。猜错一次就是一轮 5 分钟 CI。
4. **不要宣称你没验证过的事。** 本文件里两处"我说 0 处、其实是 121 处 / 我说拿不到包、其实公开可下"，
   都被用户当场抓住。**没验证就写"未验证"**。
5. **一个 tag 发出去就不再动它。** 重指会让用户手里的 sha 失效（这次发生过一次，造成中途更正）。
   宁可多一个版本号。
6. **一次构建改一批事，不要"改一行打一个包"。** 用户要装 APK、要走真机流程，
   每版只带一个改动会把人磨没。
7. **不要在别人正在改的同一个工作区里切分支/合并。** 两个 agent 同用一个 `~/NekoPlus` 时，
   另开 `git worktree`（这次我是这么避开撞车的）。
8. **合并失败不要被 `| tail` 吃掉。** 我用 `git merge … | tail -3 && …` 让冲突标记被提交进去过一次；
   合并后**必须** `grep -rn "^<<<<<<<"`。

---

## 2. CI 是唯一验证手段（以及它的真实代价）

**测过了：一次 CI 3～6 分钟。这次移植烧掉 64 次构建、纯等待 ≈4.2 小时。**
所以：**能在本地读代码/字节码解决的问题，别丢给 CI。**

```bash
TOK=<用户的 GitHub PAT，见会话/PR>   # 需要 repo + read:packages
SHA=$(git rev-parse HEAD)
curl -s -H "Authorization: token $TOK" \
  "https://api.github.com/repos/Youzix-Star/NekoPlus/actions/runs?head_sha=$SHA&per_page=1"
# 取 job id 后读日志：
JID=$(curl -s -H "Authorization: token $TOK" \
  "https://api.github.com/repos/Youzix-Star/NekoPlus/actions/runs/$RID/jobs" \
  | python3 -c "import json,sys; print(json.load(sys.stdin)['jobs'][0]['id'])")
curl -sL -H "Authorization: token $TOK" \
  "https://api.github.com/repos/Youzix-Star/NekoPlus/actions/jobs/$JID/logs" -o ~/ci.log
grep -oE "(e|error): [^ ]*(java|kt):[0-9]+:[0-9]+ .{0,90}" ~/ci.log | head -30
```

- 工作流：`.github/workflows/android.yml`，`main`/`master` push 与 PR 触发；`v*` tag 触发 release job。
- 给用户发版本：`versionCode/versionName` 升一格 → `git tag -f vX.Y.Z-name <sha>` → push tag →
  release job 自动构建并发布到
  `https://github.com/Youzix-Star/NekoPlus/releases/download/<tag>/MiaoAssistant-<tag>.apk`。
  **下回来自己核 sha256 再交给用户**（他会核对）。
- R8 mapping 会作为 artifact `r8-mapping` 上传：混淆过的堆栈靠它翻译回真实类名（§4）。
- **本地构建这条路是开的、也值得开**（用户问过，我只给出了建议没动手）：
  在这台设备上装 JDK + Android cmdline-tools 后，编译错误 20 秒出结果而不是 5 分钟。
  一次性成本约 2～3 GB 磁盘。**这是剩下唯一的大杠杆**，但要先跟用户确认（它和"不在本地构建"的旧约定冲突）。

---

## 3. 这次真正花掉时间的四件事（别重复）

| 教训 | 代价 |
|---|---|
| 用 Compose 重写而不是照抄它的 provision | ≈2 小时，全废 |
| 断言 `fan.miuix:*` 是"拿不到的私有库"（其实 GitHub Packages 公开可下，只是匿名 401） | ≈0.5 小时 + 信任 |
| 两次 keep 规则漏抄 → 真机崩溃 → 用户装、复现、导日志、我再修 | ≈1 小时 + 用户两次安装 |
| CI 每轮 3～6 分钟 × 64 轮 | ≈4.2 小时 |

---

## 4. 本机已有的工具（用它们，不要重新发明）

**已经随仓库提供**：`scripts/dexmethods.py`、`scripts/checkbrand.py`、`scripts/kcheck.py`
（`scripts/README.md` 有用法）。下面的路径是同一批工具在设备上的家目录副本：

- **`dexmethods.py <dex文件> "<类描述符>"`** —— 最小 DEX 解析器，列某个类的方法表。
  用途：问"R8 到底有没有把某个方法裁掉"。**别用 grep 在 dex 里找方法签名 —— dex 不这么存**（它按类型池拆）。
- **`checkbrand.py <apk> <字符串…>`** —— 在 `resources.arsc`/manifest/dex 里**两种编码**（UTF-8 与 UTF-16）分别计数。
  **资源池和 manifest 池是 UTF-16**，只搜 ASCII 会给出假阴性 —— 我因此得出过两个相反的错误结论。
- **`kcheck.py <文件>`** —— 花括号/括号平衡检查（会跳过注释与字符串），改完大文件先跑它。
- **AAR/依赖侦察**（不需要本地 Gradle）：
  ```bash
  # Maven Central 或 Google Maven 直接下 aar，看类名与常量池字符串
  curl -sL "https://repo1.maven.org/maven2/<group>/<artifact>/<ver>/<artifact>-<ver>.aar" -o a.aar
  unzip -o -q a.aar classes.jar && unzip -l classes.jar | grep -i 关键词
  # 真实方法签名：解出 class 文件后 strings 看常量池，或解 sources jar
  ```
  注意 **AAR 里可能嵌 `libs/*.jar`**（`fan.miuix:animation` 的真身就在 `libs/miuix-animation.jar`）。
- **R8 mapping 反查被改名的库**：库没有 consumer keep 规则时，类会被改名/合并，按名字在 dex 里搜不到。
  用 CI 的 `r8-mapping` artifact：`grep "PredictiveBackHandlerKt.PredictiveBackHandler" mapping.txt` 找到落地类名，
  再去 dex 里确认方法形状。

---

## 5. 已知的坑（按领域）

**R8 / 混淆**
- XML 里按名字 inflate 的类、反射实例化的类、被 `Class.forName` 加载的类：**必须 keep 全名**，
  否则不是"缺类"就是"缺方法"（`NoSuchMethodError`），而且只在真机崩。
- AGP 的 `proguard-android-optimize` 默认 `-repackageclasses`：混淆后的类落进**无名包**，
  于是 `Class.getPackage()` 返回 **null**。`androidx.preference.PreferenceInflater.init()` 正好用它推默认包前缀 ⇒
  `setPreferencesFromResource()` 直接 NPE。修法就是 keep `androidx.preference.**`。
- `-keepattributes SourceFile,LineNumberTable` 必须有：否则崩溃堆栈是 `r8-map-id-<hash>:29`（无文件名、数字不是行号），
  每次定位都只能靠反汇编猜。已加。

**miuix（小米闭源库，`fan.miuix:*`）**
- 来源：`https://maven.pkg.github.com/ReChronoRain/HyperCeiler`，**包是 public，但匿名请求 401**，
  必须带 token：本地 `GIT_ACTOR`/`GIT_TOKEN` 或 `local.properties` 的 `gpr.user`/`gpr.key`；
  CI 用 `secrets.PACKAGES_TOKEN || secrets.GITHUB_TOKEN`（已在 workflow 里接好）。
- 版本必须**整族一致**（`1.0.13.0`），它的 POM 互相依赖。
- **预测性返回在 `miuix-nav`**（`PredictiveBackHandler`、`NavBackEvent`、`NavSwipeEdge`），
  **不在 `miuix-ui`**。签名：`(enabled, onProgress: suspend (Flow<NavBackEvent>) -> Unit, onCommit, onCancel)`。
- 触感反馈会异步走 `miuix.util.HapticFeedbackUtil` → `Vibrator.vibrate`：
  **Manifest 没有 `android.permission.VIBRATE` 就在后台线程抛 `SecurityException` 把应用带走**（已加）。
- 模糊（`MiuiBlurUtils`）在非 MIUI 上不生效，属上游行为，不要"修"。
- miuix 的 `Colors` 没有 `tertiary`，只有 `tertiaryContainer` 之类；写之前查 AAR。

**HyperCeiler（上游，AGPL-3.0-only）**
- 本地全量快照：`~/hcp-work/HyperCeiler-HEAD`；勘察笔记：`~/hc-provision-notes.md`（中文，含大量 file:line）。
- 引导模块是 `library/provision`；它自己的 keep 规则在 `library/*/src/main/keepRules/` 与 `app/src/main/keepRules/`。
- 未搬的页面：协议与声明、基础设置（原内容：语言/图标/作用域）、条款 Web/Markdown 弹层。

**其他**
- Android 的正则是 **ICU，不是 JVM `java.util.regex`**：裸 `}` 会抛异常，而 JVM 单元测试抓不到。
  本项目所有静态正则都已改成手写扫描（`text/` 那套）。
- 写了 `AGPL-3.0-only` 的文件头与许可表：**搬别人的 AGPL 代码必须保留它的版权头**，
  应用内「开源许可」页有指向 HyperCeiler 的署名链接，**不能删**。

---

## 6. 引导（移植版）的结构

- 流程：`Startup（开场）→ Permission（权限）→ BasicSettings（改造为 AI 配置）→ Congratulation（完成）`，
  上游 `state/StateMachine.java` 的四步链，result code `-1` 前进 / `0` 后退。
- 入口：`love.miao.yun.ui.provision.ProvisionGuide`（`isDone` / `markDone` / `launch`）接到 `OnboardingPrefs`，
  首次启动出现一次，「关于 → 新手引导」可重进。
- 权限页：两行 = 无障碍服务 / 悬浮窗权限（实时状态，跳系统设置），
  骨架与行样式是 `PermissionItemView`（**未改**）；网络/root/LSP/installed_apps 全去。
- AI 页：三行 = 接口地址 / API Key / **模型（`DropDownPreference`，进页面拉 `GET {base}/models` 后可选）**。
  **「连接测试」行已删**（拉列表本身就是连通性验证 —— 用户的原话"那个测试有点多余了"）。
  读写只走 `love.miao.yun.ai.AiManager` 的 `ai_config`（键 `base_url`/`api_key`/`model`），**不许有第二份真相**。
- 品牌：标记 = `love.miao.yun.ui.AppIconText` 文本（不是图片），名字 = `NekoPlus`，
  副标题 = `Ciallo～(∠・ω c)⌒★`（深饱和紫 + 浅光晕，压在彩色极光上）。
- 尺寸刻度（用户要求跨页一致，**只剩三个字号**）：32sp 标题/品牌名、17sp 行标题与按钮、14sp 所有次级行。
  图标：标记 104dp、页面预览 70dp、权限勾 24dp。行高：权限页 56dp（上游值）、AI 页约 65dp（上游库的推导值）。
- 崩溃报告页：`ui/crash/CrashReportActivity.kt`，**独立进程 `:crash`**（主进程正在死，它得活下来），
  纯代码搭 View、复制/分享/重启/关闭；`CrashHandler` 写完文件 → 起页面 → 杀进程，**不转交平台默认 handler**
  （否则系统"应用已停止"弹窗会盖住它）。**调试模式里有「模拟崩溃」**，用来验证这条路径。
- 报告落在 `Android/data/love.miao.yun/files/crash/latest.txt`；用户会通过报告页「复制」把内容贴出来。

---

## 7. 状态与待办（截至 2026-09-19 · Preview 10）

### 7.1 已经做完的（别再当待办、也别重复问）

| 事 | 落地 |
|---|---|
| **撤回"内容垂直居中"** | Preview 10（115，commit `8cc93dc`）：权限页去掉 `layout_gravity="center_vertical"`、AI 页去掉 `getListViewPaddingTop()` 覆盖（96dp dimen 一并删除），两页恢复**顶对齐**。用户原话：「有点太靠低了，还不如上个版本的高度」。**行下方的空白保持原样，不要用 padding/gravity/offset 去"修"** |
| 引导四步链 | 开场 → 权限 → AI 配置 → 完成，入口 `ProvisionGuide` 接 `OnboardingPrefs` |
| 品牌 | 标记 = `AppIconText` 文本、名字 = `NekoPlus`、副标题 = `Ciallo～(∠・ω c)⌒★`（深饱和紫 + 浅光晕） |
| AI 页三行 | 接口地址 / API Key / **模型（`DropDownPreference`，进页面拉 `GET {base}/models` 后可选）**；「连接测试」按用户要求**已删** |
| 预测返回 | 改用 miuix 官方 `PredictiveBackHandler`（`miuix-nav`），`Aosp`/`None` 与外观里的选择项**已删** |
| AI 模板 | 3 → **11 套**（翻译腔、成吉思鸡、阴阳怪气、发疯文学、鲁迅体、浅近文言、机器人客服、猫娘 + 原有三套） |
| 崩溃报告页 | 独立进程 `:crash` + `CrashHandler`，调试模式里有「模拟崩溃」可主动验证 |
| 崩溃堆栈可读 | `-keepattributes SourceFile,LineNumberTable` + CI 上传 `r8-mapping` artifact |
| 跨页尺寸 | 三个字号刻度 32 / 17 / 14sp；图标 104 / 70 / 24dp（见 §6） |

### 7.2 真正还悬着、要用户点头的

1. **把 `feat/provision-port` 合进 `main`**（草稿 PR #1）。用户评价已是「基本上就很好了」但**没说过 merge**；
   合并前别改 `main` 的 `2.0.2 Beta 2 (105)`。
2. **删掉旧 Compose 版引导**：`ui/onboarding/*`（含 `GlowPalette`/`GlowPainter`/`GlowBackground`/`GuideWizard`/`OnboardingPages`）
   及其测试、`MiuixApp`/`MaterialApp` 两个调用点 —— **必须等用户明确认可移植版之后再删**。
3. **收窄 `-keep class fan.** { *; }`**：APK 因此从 5.2 MB 涨到 8.1 MB。验收后只保留 `fan.animation.**`
   与被 XML 按名 inflate 的 widget，把体积要回来（改完必须用 `scripts/dexmethods.py` 复核 `spring(FF)` 等仍在）。
4. **完成页「设置完毕」字号**：现为 14sp（为跨页一致压下来的），备选 **17sp**（行标题/按钮那一档）。
   用户还没对这一版表态。
5. **许可**：`ai/AiManager.kt`、`ai/TokenStats.kt`、`util/UpdateChecker.kt`、`service/MiaoAccessibilityService.kt`
   头里写"移植自 NekoNeko，GPL-3.0-only"。**若 NekoNeko 是用户自己的项目**，这 4 个可一并升 AGPL；
   问过一次，他还没答。
6. **本地构建**：要不要在这台设备上装 JDK + Android cmdline-tools（编译从 3~6 分钟压到 20 秒）。
   我建议装；它与"不在本地构建"的旧约定冲突，属于用户的决定。
7. 还没被真机验证过的观感（问用户才知道）：PredictiveBack 的跟手度、11 套模板的味道、
   深紫副标题 + 浅光晕在极光暗谷的可读性、开场圆钮的放大转场（非 MIUI 可能是 no-op，属上游自带分支）。

### 7.3 边界（用户明确说过，别越界）

- **只改新手引导**。设置页、Compose 版 AI 配置页、文本替换页一律不动（原话：「我仅仅是让改新手引导」）。
- 文案一行一句、不放营销话：「我们是以逻辑主导设计的」。多出来的入口会被骂（「删除表单编辑那个按钮，神经病！」）。

## 8. 工作区布局与卫生（用户明确要求过：别把仓库根目录搞乱）

**原则：仓库里只放仓库的东西。日志、APK、AAR、临时解包目录一律放在家目录。**

```
~/NekoPlus/              唯一的 svn 检出（分支 feat/provision-port 通常在这里）
~/scratch/               所有临时物：logs/（CI 日志）、aar/（下下来的依赖包）、下载的 APK、解包目录
~/crash-reports/         用户给的崩溃日志副本（按日期+版本命名，便于回溯）
~/hcp-work/HyperCeiler-HEAD/  上游全量快照（只读参考）
~/hc-provision-notes.md       上游 provision 模块的勘察笔记（中文，含 file:line）
scripts/                 三个验证小工具（见 §4）
docs/provision-port.md   移植工作的完整记录（真因、决定、尺寸表、验收清单）
```

- **不要 `git worktree add` 一堆副本然后忘了**。多个 agent 并行时用一个 worktree 是对的，
  但收工后要 `git worktree remove`，只留一个检出 —— 用户被"根目录一堆文件 + 多个副本"困过一次。
- 任何 `*.log` / `*.apk` / `*.jks` 都已在 `.gitignore` 里（`miao-release.jks` 是本地签名密钥，
  **永远不要提交**），但它们仍然会躺在工作目录里碍眼 —— 直接往 `~/scratch/` 写，不要写进仓库。
- 崩溃日志（`Android/data/love.miao.yun/files/crash/latest.txt`）拷进 `~/crash-reports/`，
  文件名带上版本与现象，下一个 agent 才看得出先后。

## 9. 怎么跟这位用户协作（很重要）

- **他要结果，不要流程。** 反复问"要不要我这样做"会挨骂；**能自己决定的小事就决定**，
  把"我做了什么、为什么、代价是什么"讲清楚即可。真正需要他拍的只有产品口味（配色、文案、位置）。
- **他会核实你说的话。** 编造或含糊的结论会被当场拆穿（他就抓到过我一次）。
  **给结论必须带证据**：sha256、dex 里查到什么、mapping 里的哪一行。
- **他讨厌冗余入口与啰嗦文案**（"删除表单编辑那个按钮，神经病！"），
  界面文案一行一句、不放营销话。
- **他看不到代码，只看 APK。** 涉及观感的事必须给他一个可安装的 release 链接，并说清"要你看哪几点"。
- **他会有耐心等诚实，不会等借口。** 出错就直说"这是我的判断错误"+ 立刻修，别解释环境。
