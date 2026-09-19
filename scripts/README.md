# scripts/ —— 在没有 Android SDK 的机器上验证 APK 的小工具

这台设备（Android/Termux）**不能构建**，只能跑 CI。于是"我改了的东西到底进没进包里"
就成了每轮都要回答的问题 —— 这三个小工具就是为了让它 20 秒内可答，而不是再等 5 分钟 CI。

```bash
# 1) 某个类在 dex 里到底有哪些方法（问"R8 有没有把它裁掉"）
python3 scripts/dexmethods.py <classes.dex> "Lfan/animation/FolmeEase;"
#    注意：dex 不把方法签名存成字符串，所以 grep 是问不出答案的 —— 这个脚本真的解析方法表。

# 2) 某个字符串在包里出现几次（两种编码都数）
python3 scripts/checkbrand.py <xxx.apk> HyperCeiler 喵喵助手
#    注意：资源池与 manifest 池是 UTF-16，只搜 ASCII 会给出假阴性（这个坑踩过两次）。

# 3) Kotlin/Java 改完先看括号平不平衡
python3 scripts/kcheck.py <文件>
```

取 dex：`unzip -o -q app.apk 'classes*.dex'`。
取 CI 日志与产物见仓库根目录 `AGENTS.md` §2。
