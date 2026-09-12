/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0
 */

package love.miao.yun.rule

/**
 * The plain-text rule format users paste in and copy out.
 *
 * Unchanged from the previous implementation so existing snippets keep working:
 *
 * ```
 * 查找=替换                 （纯文本替换）
 * # [正则] 查找=替换        （正则替换）
 * # [已禁用] 查找=替换      （停用）
 * ```
 *
 * Lines without `=` are a find-only rule (replace with nothing). Rules of other kinds cannot be
 * expressed in this format, so [export] writes them as comments that [import] skips.
 */
object RuleTextCodec {

    private const val MARK_DISABLED = "[已禁用]"
    private const val MARK_DISABLED_ALT = "[禁用]"
    private const val MARK_REGEX = "[正则]"

    fun import(text: String?): List<Rule> {
        if (text.isNullOrBlank()) return emptyList()
        val rules = mutableListOf<Rule>()

        for (rawLine in text.split("\n")) {
            var line = rawLine.replace("\r", "").trim()
            if (line.isEmpty()) continue

            var enabled = true
            if (line.startsWith("#")) line = line.substring(1).trim()

            while (line.startsWith("[")) {
                val end = line.indexOf(']')
                if (end < 0) break
                val marker = line.substring(0, end + 1)
                when (marker) {
                    MARK_DISABLED, MARK_DISABLED_ALT -> enabled = false
                    MARK_REGEX -> Unit
                    else -> break
                }
                line = line.substring(end + 1).trim()
            }

            // Re-read the regex marker after the disabled marker, in whatever order they appear.
            val useRegex = line.startsWith(MARK_REGEX)
            if (useRegex) line = line.substring(MARK_REGEX.length).trim()
            if (line.isEmpty()) continue

            val separator = line.indexOf('=')
            val find = if (separator >= 0) line.substring(0, separator).trim() else line
            val replace = if (separator >= 0) line.substring(separator + 1).trim() else ""
            if (find.isEmpty()) continue

            rules += Rule(
                name = find,
                kind = RuleKind.REPLACE,
                enabled = enabled,
                pattern = find,
                replacement = replace,
                useRegex = useRegex,
            )
        }
        return rules
    }

    fun export(rules: List<Rule>): String = buildString {
        for (rule in rules) {
            if (rule.kind != RuleKind.REPLACE) {
                append("# ").append(rule.summary).append('\n')
                continue
            }
            if (!rule.enabled) append("# ").append(MARK_DISABLED).append(' ')
            if (rule.useRegex) append("# ").append(MARK_REGEX).append(' ')
            append(rule.pattern).append('=').append(rule.replacement).append('\n')
        }
    }
}
