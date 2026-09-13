/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: GPL-3.0-only
 */

package love.miao.yun.text

/**
 * The rule language, both ways round.
 *
 * One line is one rule. The shapes are:
 *
 * ```
 * 你好 = 您好                        一条替换规则，和 1.1.8 写的完全一样
 * 你好 → 您好                        箭头、全角等号都认
 * 末尾加"喵"                          没有条件，总是执行
 * 当 含"？" 则 末尾加"喵"              有条件
 * 如果 长度 > 40 那么 每句末尾加"喵"     如果…那么… 是同一件事
 * 首尾加"{猫爪}" @com.tencent.mobileqq  只在这个应用里生效
 * {后缀} = 喵                        变量：可自定义的配置项
 * # 井号开头是注释
 * ```
 *
 * Rendering is the exact inverse of parsing, so the text box can be opened, edited and closed
 * without anything being quietly rewritten, and the same text can be pasted into a chat to share.
 *
 * The one trap this language has, and the reason keywords are only looked for **outside quotes**, is
 * Chinese itself: `替换 "因为" 为 "所以"` contains a 为 inside the value, and a parser that splits on
 * the first 为 it sees splits the word in half.
 */
object TextRuleText {

    /** A line the parser could not make sense of. */
    data class Problem(val line: Int, val message: String)

    data class Parsed(
        val rules: TextRules,
        val problems: List<Problem> = emptyList(),
    ) {
        val ok: Boolean get() = problems.isEmpty()
    }

    // ------------------------------------------------------------------ parsing

    /**
     * Reads the whole configuration: variables first, then rules.
     *
     * Errors never abort the parse. A half-written line costs the user that line, not the other
     * twenty, and the message says which line and what was wrong with it.
     */
    fun parse(text: String, fallbackVariables: Map<String, String> = TextDefaults.variables): Parsed {
        val rules = mutableListOf<TextRule>()
        val variables = LinkedHashMap(fallbackVariables)
        val problems = mutableListOf<Problem>()

        text.lines().forEachIndexed { index, raw ->
            val line = raw.trim()
            val number = index + 1
            if (line.isEmpty() || line.startsWith("#")) return@forEachIndexed

            parseVariable(line)?.let { (name, value) ->
                variables[name] = value
                return@forEachIndexed
            }

            when (val result = parseRule(line)) {
                is RuleResult.Ok -> rules += result.rule
                is RuleResult.Failed -> problems += Problem(number, result.message)
            }
        }

        return Parsed(TextRules(rules, variables), problems)
    }

    /** `{名字} = 值`, or null when the line is not a variable definition. */
    private fun parseVariable(line: String): Pair<String, String>? {
        if (!line.startsWith("{")) return null
        val close = line.indexOf('}')
        if (close <= 1) return null
        val name = line.substring(1, close).trim()
        if (name.isEmpty()) return null
        var rest = line.substring(close + 1).trim()
        if (rest.isEmpty()) return null
        rest = rest.removePrefix("=").removePrefix("＝").removePrefix(":").removePrefix("：").trim()
        return name to unquote(rest)
    }

    private sealed interface RuleResult {
        data class Ok(val rule: TextRule) : RuleResult
        data class Failed(val message: String) : RuleResult
    }

    private fun parseRule(line: String): RuleResult {
        var enabled = true
        var body = line

        // A trailing comment: `# 已停用` is how the renderer writes a rule that is switched off,
        // and anything else after a `#` is dropped the way a comment should be.
        findOutsideQuotes(body, listOf(" # "))?.let { (_, at) ->
            val comment = body.substring(at)
            enabled = !comment.contains("停用")
            body = body.substring(0, at).trim()
        }

        // The app scope trails the rule: `... @com.tencent.mm @com.tencent.mobileqq`.
        val apps = mutableSetOf<String>()
        while (true) {
            // From the right: `@a @b` has to lose @b first, or the name would be "@a @b".
            val at = findOutsideQuotes(body, listOf(" @"), last = true)?.second ?: break
            val name = body.substring(at + 1).trim()
            if (name.isEmpty() || name.any { it.isWhitespace() }) break
            apps += name.removePrefix("@").trim()
            body = body.substring(0, at).trim()
        }

        // "当 … 则 …" and "如果 … 那么 …" are the same sentence with different words.
        splitConditionAction(body)?.let { (conditionText, actionText) ->
            val condition = parseCondition(conditionText)
                ?: return RuleResult.Failed("看不懂条件「$conditionText」")
            val action = parseAction(actionText)
                ?: return RuleResult.Failed("看不懂动作「$actionText」")
            return RuleResult.Ok(
                TextRule(condition = condition, action = action, apps = apps, enabled = enabled),
            )
        }

        // A bare assignment is a replacement: the one shape 1.1.8 had, kept exactly as it was.
        assignment(body)?.let { (from, to) ->
            return RuleResult.Ok(
                TextRule(
                    condition = TextCondition.Always,
                    action = TextAction.Replace(from, to),
                    apps = apps,
                    enabled = enabled,
                ),
            )
        }

        // Otherwise the line has to be an action on its own.
        val action = parseAction(body)
            ?: return RuleResult.Failed("这一行既不是替换也不是动作：「$body」")
        return RuleResult.Ok(
            TextRule(condition = TextCondition.Always, action = action, apps = apps, enabled = enabled),
        )
    }

    /** Splits `当 <条件> 则 <动作>` into its two halves. */
    private fun splitConditionAction(line: String): Pair<String, String>? {
        val lead = CONDITION_LEADS.firstOrNull { line.startsWith(it) } ?: return null
        val rest = line.substring(lead.length)
        val (keyword, at) = findOutsideQuotes(rest, ACTION_LEADS) ?: return null
        val condition = rest.substring(0, at).trim()
        val action = rest.substring(at + keyword.length).trim()
        if (condition.isEmpty() || action.isEmpty()) return null
        return condition to action
    }

    /** `A = B`, with `=`, `＝`, `→` or `->` between the halves. */
    private fun assignment(line: String): Pair<String, String>? {
        val (keyword, at) = findOutsideQuotes(line, listOf("=", "＝", "→", "->")) ?: return null
        val from = unquote(line.substring(0, at).trim())
        if (from.isEmpty()) return null
        val after = line.substring(at + keyword.length).trim().removePrefix(">")
        return from to unquote(after)
    }

    private fun parseCondition(text: String): TextCondition? {
        var body = text.trim()
        var negated = false
        if (body.length > 1 && (body.startsWith("不") || body.startsWith("非"))) {
            negated = true
            body = body.substring(1).trim()
        }

        val condition = when {
            body.startsWith("包含") -> TextCondition.Contains(valueOf(body, "包含"))
            body.startsWith("含") -> TextCondition.Contains(valueOf(body, "含"))

            body.startsWith("以") && body.contains("开头") ->
                TextCondition.StartsWith(unquote(body.removePrefix("以").substringBefore("开头").trim()))

            body.startsWith("以") && body.contains("结尾") ->
                TextCondition.EndsWith(unquote(body.removePrefix("以").substringBefore("结尾").trim()))

            body.startsWith("是") -> TextCondition.Equals(valueOf(body, "是"))

            body.startsWith("匹配") -> regexLiteral(body)?.let { TextCondition.Matches(it) }

            body.startsWith("长度") -> {
                val rest = body.removePrefix("长度").trim().normalizeSymbols()
                val characters = NUMBER.find(rest)?.value?.toIntOrNull() ?: return null
                when {
                    rest.startsWith(">") -> TextCondition.LongerThan(characters)
                    rest.startsWith("<") -> TextCondition.ShorterThan(characters)
                    else -> null
                }
            }

            body.startsWith("随机") || body.startsWith("概率") -> {
                val percent = NUMBER.find(body)?.value?.toIntOrNull() ?: return null
                TextCondition.Chance(percent.coerceIn(0, 100))
            }

            body.startsWith("每") -> {
                val count = NUMBER.find(body)?.value?.toIntOrNull() ?: return null
                TextCondition.EveryNth(count.coerceAtLeast(1))
            }

            body.startsWith("任何") || body.startsWith("总是") -> TextCondition.Always

            else -> null
        } ?: return null

        return if (negated && condition != TextCondition.Always) {
            TextCondition.Not(condition)
        } else {
            condition
        }
    }

    private fun parseAction(text: String): TextAction? {
        val body = text.trim()

        if (body.startsWith("正则替换")) {
            val pattern = regexLiteral(body) ?: return null
            val rest = body.substringAfterLast('/').trim()
            return TextAction.ReplaceRegex(pattern, unquote(rest.removePrefix("为").removePrefix("替换为").trim()))
        }

        if (body.startsWith("替换") || body.startsWith("把")) {
            val rest = body.removePrefix("把").removePrefix("替换").trim()
            val firstOnly = rest.startsWith("第一个") || rest.startsWith("首个")
            val cleaned = rest.removePrefix("第一个").removePrefix("首个").trim()
            val (keyword, at) = findOutsideQuotes(cleaned, REPLACE_SEPARATORS) ?: return null
            val from = unquote(cleaned.substring(0, at).trim())
            val to = unquote(cleaned.substring(at + keyword.length).trim())
            if (from.isEmpty()) return null
            return TextAction.Replace(from, to, firstOnly)
        }

        if (body.startsWith("删除")) {
            // The value is taken as written: swallowing a trailing 掉 here would eat a rule that
            // deletes the character 掉, which is a word this feature is used on.
            val from = valueOf(body, "删除")
            if (from.isEmpty()) return null
            return TextAction.Replace(from, "")
        }

        if (body.startsWith("每句末尾加") || body.startsWith("每句加")) {
            val rest = body.substringAfter("加").trim()
            return TextAction.SuffixPerSentence(
                text = unquote(rest.substringBefore("（").substringBefore("(").trim()),
                skipSpaced = rest.contains("空格不加"),
            )
        }

        if (body.startsWith("首尾包裹") || body.startsWith("首尾加") || body.startsWith("包裹")) {
            val keyword = listOf("首尾包裹", "首尾加", "包裹").first { body.startsWith(it) }
            return TextAction.Wrap(unquote(body.removePrefix(keyword).trim()))
        }

        // Checked before 末尾加, or "末尾加颜文字" would be read as a suffix made of the word 颜文字.
        if (body.startsWith("加颜文字") || body.startsWith("末尾加颜文字")) {
            return TextAction.Emoticon
        }

        if (body.startsWith("去掉末尾标点") || body.startsWith("删除末尾标点")) {
            return TextAction.TrimTrailingPunctuation
        }

        if (body.startsWith("开头加") || body.startsWith("开头插入")) {
            return TextAction.Prefix(unquote(body.substringAfter("加").substringAfter("插入").trim()))
        }

        if (body.startsWith("末尾加") || body.startsWith("结尾加")) {
            return TextAction.Suffix(unquote(body.substringAfter("加").trim()))
        }

        return null
    }

    /** The value after a leading keyword: the quoted part when there is one, the rest otherwise. */
    private fun valueOf(body: String, keyword: String): String {
        val rest = body.removePrefix(keyword).trim()
        return unquote(firstQuoted(rest) ?: rest)
    }

    // ------------------------------------------------------------------ rendering

    fun render(rules: TextRules): String = buildString {
        rules.variables.forEach { (name, value) ->
            appendLine("{$name} = $value")
        }
        if (rules.variables.isNotEmpty() && rules.rules.isNotEmpty()) appendLine()
        rules.rules.forEach { appendLine(render(it)) }
    }.trimEnd()

    fun render(rule: TextRule): String = buildString {
        val condition = renderCondition(rule.condition)
        val action = renderAction(rule.action)
        if (condition == null) append(action) else append("当 ").append(condition).append(" 则 ").append(action)
        rule.apps.sorted().forEach { append(" @").append(it) }
        if (!rule.enabled) append("  # 已停用")
    }

    private fun renderCondition(condition: TextCondition): String? = when (condition) {
        TextCondition.Always -> null
        is TextCondition.Contains -> "含${quote(condition.text)}"
        is TextCondition.StartsWith -> "以${quote(condition.text)}开头"
        is TextCondition.EndsWith -> "以${quote(condition.text)}结尾"
        is TextCondition.Equals -> "是${quote(condition.text)}"
        is TextCondition.Matches -> "匹配/${condition.pattern}/"
        is TextCondition.LongerThan -> "长度 > ${condition.characters}"
        is TextCondition.ShorterThan -> "长度 < ${condition.characters}"
        is TextCondition.Chance -> "随机 ${condition.percent}%"
        is TextCondition.EveryNth -> "每 ${condition.count} 次"
        is TextCondition.Not -> renderCondition(condition.condition)?.let { "不$it" }
    }

    private fun renderAction(action: TextAction): String = when (action) {
        is TextAction.Replace -> when {
            action.to.isEmpty() -> "删除${quote(action.from)}"
            action.firstOnly -> "替换第一个 ${quote(action.from)} 为 ${quote(action.to)}"
            // The bare form on purpose: this is the line 1.1.8 users already have in their notes.
            else -> "${quote(action.from)} = ${quote(action.to)}"
        }

        is TextAction.ReplaceRegex -> "正则替换/${action.pattern}/ 为 ${quote(action.replacement)}"
        is TextAction.Prefix -> "开头加${quote(action.text)}"
        is TextAction.Suffix -> "末尾加${quote(action.text)}"
        is TextAction.Wrap -> "首尾包裹${quote(action.text)}"
        is TextAction.SuffixPerSentence ->
            "每句末尾加${quote(action.text)}" + if (action.skipSpaced) "（空格不加）" else ""

        TextAction.TrimTrailingPunctuation -> "去掉末尾标点"
        TextAction.Emoticon -> "加颜文字"
    }

    // ------------------------------------------------------------------ quotes and keywords

    /**
     * Wraps a value in quotes, but only when it needs them.
     *
     * `你好 = 您好` is a rule a person reads at a glance; `"你好" = "您好"` is a data format. So a
     * value that is a plain run of characters stays bare, and everything else — spaces, punctuation,
     * anything that could be mistaken for the language itself — gets quoted.
     */
    private fun quote(value: String): String = if (isPlain(value)) value else "\"$value\""

    private fun isPlain(value: String): Boolean =
        value.isNotEmpty() && value.none { it.isWhitespace() || it in RESERVED }

    /**
     * Takes the quotes off a value, or leaves it bare.
     *
     * Quotes are optional, which is what keeps `你好 = 您好` looking like the rule a person would
     * write; they exist for values with spaces in them, or for the ones that are only punctuation.
     */
    fun unquote(raw: String): String {
        val value = raw.trim()
        VALID_QUOTES.forEach { (open, close) ->
            if (value.length >= 2 && value.first() == open && value.last() == close) {
                return value.substring(1, value.length - 1)
            }
        }
        return value
    }

    /** The first quoted run in [text], with its quotes taken off. */
    private fun firstQuoted(text: String): String? {
        var closing: Char? = null
        var start = -1
        text.forEachIndexed { index, character ->
            val close = VALID_QUOTES[character]
            when {
                closing == null && close != null -> {
                    closing = close
                    start = index
                }

                closing != null && character == closing -> {
                    return text.substring(start + 1, index)
                }
            }
        }
        return null
    }

    /**
     * The first of [keywords] that appears at all, ignoring anything inside quotes.
     *
     * This is the whole trick of the parser: `替换 "因为" 为 "所以"` has a 为 in its own value, and
     * looking inside quotes is how a rule about a word gets its word split in half.
     */
    private fun findOutsideQuotes(
        text: String,
        keywords: List<String>,
        last: Boolean = false,
    ): Pair<String, Int>? {
        var closing: Char? = null
        var found: Pair<String, Int>? = null
        var index = 0
        while (index < text.length) {
            val character = text[index]
            val close = VALID_QUOTES[character]
            if (closing != null) {
                if (character == closing) closing = null
                index++
                continue
            }
            if (close != null) {
                closing = close
                index++
                continue
            }
            keywords.forEach { keyword ->
                if (text.startsWith(keyword, index)) {
                    if (!last) return keyword to index
                    found = keyword to index
                }
            }
            index++
        }
        return found
    }

    /** The body of `/…/`, for a regex written the way a regex is written. */
    private fun regexLiteral(text: String): String? {
        val start = text.indexOf('/')
        if (start < 0) return null
        val end = text.indexOf('/', start + 1)
        if (end <= start) return null
        return text.substring(start + 1, end)
    }

    /** Full-width symbols people actually type, folded onto the ASCII the parser reads. */
    private fun String.normalizeSymbols(): String = this
        .replace('≥', '>')
        .replace('≤', '<')
        .replace('＞', '>')
        .replace('＜', '<')
        .replace('％', '%')

    private val NUMBER = Regex("\\d+")

    private val REPLACE_SEPARATORS = listOf("替换为", "换成", "改为", "为", "→")

    /** The words that start a condition, and the words that start its action. */
    private val CONDITION_LEADS = listOf("当", "如果", "若")

    private val ACTION_LEADS = listOf("那么", "则", "就")

    private val VALID_QUOTES = mapOf(
        '"' to '"',
        '\'' to '\'',
        '“' to '”',
        '「' to '」',
        '『' to '』',
        '【' to '】',
    )

    /** Characters that would make a bare value ambiguous with the language around it. */
    private val RESERVED = setOf(
        '=', '＝', '→', '\'', '"', '“', '”', '「', '」', '『', '』', '【', '】',
        '#', '@', '/', '{', '}', '\n', '\t',
    )
}
