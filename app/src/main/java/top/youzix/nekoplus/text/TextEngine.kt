/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package top.youzix.nekoplus.text

import kotlin.random.Random

/**
 * Runs a list of rules over a piece of text.
 *
 * Deliberately a class with state rather than an object: the `每 N 次` condition counts across calls,
 * and a count that resets every time the user taps a button is a count that never fires. One
 * instance belongs to whatever is running the rules; the settings preview makes a throwaway one, so
 * trying a rule out never advances the real counter.
 */
class TextEngine(private val random: Random = Random.Default) {

    /** How many times each `每 N 次` rule has been reached, keyed by its index in the list. */
    private val counters = HashMap<Int, Int>()

    /**
     * Applies every rule, in order, to [text].
     *
     * @param packageName the app the text came from, for rules limited to one; null runs only the
     *   rules that are not limited.
     */
    fun apply(text: String, rules: TextRules, packageName: String? = null): String {
        var result = text
        rules.rules.forEachIndexed { index, rule ->
            if (!rule.enabled) return@forEachIndexed
            if (!rule.appliesTo(packageName)) return@forEachIndexed
            if (!matches(rule.condition, result, index)) return@forEachIndexed
            result = perform(rule.action, result, rules)
        }
        return result
    }

    // ------------------------------------------------------------------ conditions

    private fun TextRule.appliesTo(packageName: String?): Boolean =
        apps.isEmpty() || (packageName != null && packageName in apps)

    private fun matches(condition: TextCondition, text: String, index: Int): Boolean =
        when (condition) {
            TextCondition.Always -> true
            is TextCondition.Contains -> text.contains(condition.text)
            is TextCondition.StartsWith -> text.startsWith(condition.text)
            is TextCondition.EndsWith -> text.endsWith(condition.text)
            is TextCondition.Equals -> text == condition.text
            is TextCondition.Matches -> runCatching { Regex(condition.pattern).containsMatchIn(text) }
                .getOrDefault(false)

            is TextCondition.LongerThan -> text.length > condition.characters
            is TextCondition.ShorterThan -> text.length < condition.characters
            is TextCondition.Chance -> random.nextInt(100) < condition.percent.coerceIn(0, 100)
            is TextCondition.EveryNth -> count(index) % condition.count.coerceAtLeast(1) == 0
            is TextCondition.Not -> !matches(condition.condition, text, index)
        }

    /**
     * How many times the rule at [index] has been reached, counting this time.
     *
     * Counting the *reaching*, not the firing, is what makes `每 2 次` mean "every other tap" rather
     * than "every other tap that also happened to pass some other condition".
     */
    private fun count(index: Int): Int {
        val next = (counters[index] ?: 0) + 1
        counters[index] = next
        return next
    }

    // ------------------------------------------------------------------ actions

    private fun perform(action: TextAction, text: String, rules: TextRules): String = when (action) {
        is TextAction.Replace -> {
            val from = rules.expand(action.from)
            val to = rules.expand(action.to)
            if (from.isEmpty()) {
                text
            } else if (action.firstOnly) {
                replaceFirst(text, from, to)
            } else {
                text.replace(from, to)
            }
        }

        is TextAction.ReplaceRegex -> runCatching {
            val regex = Regex(action.pattern)
            regex.replace(text, rules.expand(action.replacement))
        }.getOrDefault(text)

        is TextAction.Prefix -> {
            val prefix = rules.expand(action.text)
            if (prefix.isEmpty() || text.startsWith(prefix)) text else prefix + text
        }

        is TextAction.Suffix -> {
            val suffix = rules.expand(action.text)
            if (suffix.isEmpty() || text.endsWith(suffix)) text else text + suffix
        }

        // Idempotent from both ends, so a second tap cannot turn a wrapped sentence into a
        // double-wrapped one.
        is TextAction.Wrap -> {
            val mark = rules.expand(action.text)
            when {
                mark.isEmpty() -> text
                text.startsWith(mark) && text.endsWith(mark) && text.length > mark.length * 2 -> text
                else -> mark + text + mark
            }
        }

        is TextAction.SuffixPerSentence ->
            suffixPerSentence(text, rules.expand(action.text), action.skipSpaced)

        TextAction.TrimTrailingPunctuation -> trimTrailingPunctuation(text)

        TextAction.Emoticon -> pickEmoticon(rules)?.let { text + " " + it } ?: text
    }

    private fun replaceFirst(text: String, from: String, to: String): String {
        val at = text.indexOf(from)
        if (at < 0) return text
        return text.substring(0, at) + to + text.substring(at + from.length)
    }

    /**
     * Puts a suffix after every sentence.
     *
     * Two rules are inherited from 1.1.8 because they were learned the hard way: only segments with
     * CJK characters in them get the suffix — otherwise it lands inside an emoticon, which is how
     * "喵" ends up in the middle of a cat face — and with [skipSpaced] the whole text is treated as
     * one sentence, which is what 空格不加喵 did for people who use spaces instead of punctuation.
     */
    private fun suffixPerSentence(text: String, suffix: String, skipSpaced: Boolean): String {
        if (suffix.isEmpty()) return text
        // 空格不加喵: the whole text counts as one sentence, so the suffix goes on the end once.
        if (skipSpaced) return if (text.endsWith(suffix)) text else text + suffix

        val result = StringBuilder()
        var start = 0
        var index = 0
        while (index < text.length) {
            if (!isSentenceEnd(text[index])) {
                index++
                continue
            }
            appendSentence(result, text.substring(start, index), suffix)
            // The separators are kept exactly as they were, run or single: they are the user's text.
            var end = index
            while (end < text.length && isSentenceEnd(text[end])) end++
            result.append(text, index, end)
            index = end
            start = index
        }
        if (start < text.length) appendSentence(result, text.substring(start), suffix)

        val trimmed = result.toString().trim()
        return if (trimmed.isEmpty()) text + suffix else trimmed
    }

    private fun appendSentence(into: StringBuilder, sentence: String, suffix: String) {
        val part = sentence.trim()
        if (part.isEmpty()) return
        into.append(part)
        if (containsCjk(part) && !part.endsWith(suffix)) into.append(suffix)
    }

    /**
     * Whether a character ends a sentence.
     *
     * The same set 1.1.8 split on — CJK and ASCII punctuation, plus any whitespace — written as a
     * scan rather than a pattern. Not for style: a `Regex` in a `companion object` is compiled
     * during class initialisation, and Android's engine is ICU, which is not the one the unit tests
     * run against. That difference is exactly what crashed the settings page once already.
     */
    private fun isSentenceEnd(character: Char): Boolean = when (character) {
        '，', ',', '。', '！', '!', '？', '?' -> true
        else -> character.isWhitespace()
    }

    /** Removes one punctuation mark from the end: "你好。喵" has no business keeping the 喵 apart. */
    private fun trimTrailingPunctuation(text: String): String {
        val trimmed = text.trimEnd()
        if (trimmed.isEmpty()) return text
        return if (trimmed.last() in PUNCTUATION) trimmed.dropLast(1) else text
    }

    private fun pickEmoticon(rules: TextRules): String? {
        val list = rules.variable(TextDefaults.EMOTICON_NAME)
            ?.split(TextDefaults.EMOTICON_SEPARATOR)
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()
        return if (list.isEmpty()) null else list[random.nextInt(list.size)]
    }

    private companion object {
        val PUNCTUATION = setOf(
            '。', '！', '？', '，', '、', '；', '：',
            '.', '!', '?', ',', ';', ':',
            '~', '～', '…', '—',
            '）', '】', '》', '』', '〉', ')', ']', '}',
        )

        /** CJK, kana and Hangul: the characters a suffix belongs after, and an emoticon does not. */
        fun containsCjk(text: String): Boolean = text.any { character ->
            val code = character.code
            code in 0x4E00..0x9FFF ||
                code in 0x3400..0x4DBF ||
                code in 0x3040..0x309F ||
                code in 0x30A0..0x30FF ||
                code in 0xAC00..0xD7AF
        }
    }
}
