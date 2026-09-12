/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0
 */

package love.miao.yun.rule

import kotlin.random.Random

/**
 * Everything the engine needs that does not belong to a single rule.
 *
 * @param packageName foreground app, used to filter rules by their scope.
 * @param random source of randomness for [RuleKind.RANDOM_SUFFIX]; injectable for tests.
 * @param sentenceCount how many sentences a rule has already seen, used by
 *   [RandomMode.INTERVAL]. Callers keep the counter; the engine stays stateless.
 */
class RuleContext(
    val packageName: String? = null,
    val random: Random = Random.Default,
    val sentenceCount: (ruleId: String) -> Int = { 0 },
)

/**
 * Runs the ordered rule pipeline.
 *
 * Each rule receives the output of the previous one, so order is significant: a replacing rule
 * followed by a suffix rule reads as "rewrite, then decorate". Rules whose [Rule.scope] does not
 * contain the foreground app, and rules that are disabled, are skipped.
 *
 * [RuleKind.AI] cannot run here because it needs the network; [apply] leaves the text untouched for
 * those and reports them through [Result.deferredAi], and callers use [findAiRule] to pick one.
 */
object RuleEngine {
    private val SENTENCE_SPLIT = Regex("([，,。！!？?\\s]+)")

    data class Result(
        val text: String,
        /** Names of the rules that changed the text, in the order they ran. */
        val applied: List<String> = emptyList(),
        /** Names of the rules that matched the app but produced no change. */
        val unchanged: List<String> = emptyList(),
        /** AI rules that the caller still has to run outside this pipeline. */
        val deferredAi: List<Rule> = emptyList(),
        /** Rules skipped because of a bad pattern; a message per rule. */
        val errors: List<String> = emptyList(),
    )

    fun apply(text: String?, rules: List<Rule>, context: RuleContext = RuleContext()): Result {
        if (text.isNullOrEmpty()) return Result(text.orEmpty())
        if (rules.isEmpty()) return Result(text)

        var current = text
        val applied = mutableListOf<String>()
        val unchanged = mutableListOf<String>()
        val deferred = mutableListOf<Rule>()
        val errors = mutableListOf<String>()

        for (rule in rules) {
            if (!rule.enabled || !rule.matchesApp(context.packageName)) continue

            val next = when (rule.kind) {
                RuleKind.REPLACE -> {
                    if (rule.pattern.isEmpty()) {
                        current
                    } else {
                        try {
                            replace(current, rule)
                        } catch (e: Exception) {
                            errors += "规则「${rule.displayName}」正则语法错误: ${e.message}"
                            current
                        }
                    }
                }

                RuleKind.SUFFIX -> suffix(current, rule, context)
                RuleKind.PREFIX -> prefix(current, rule)
                RuleKind.RANDOM_SUFFIX -> randomSuffix(current, rule, context)
                RuleKind.AI -> {
                    deferred += rule
                    current
                }
            }

            if (next != current) {
                applied += rule.displayName
                current = next
            } else {
                unchanged += rule.displayName
            }
        }

        return Result(current, applied, unchanged, deferred, errors)
    }

    /** The first enabled AI rule that applies to the current app, if any. */
    fun findAiRule(rules: List<Rule>, context: RuleContext = RuleContext()): Rule? =
        rules.firstOrNull { it.enabled && it.kind == RuleKind.AI && it.matchesApp(context.packageName) }

    // ------------------------------------------------------------------ helpers

    private fun replace(text: String, rule: Rule): String =
        if (rule.useRegex) {
            Regex(rule.pattern).replace(text, rule.replacement)
        } else {
            text.replace(rule.pattern, rule.replacement)
        }

    private fun prefix(text: String, rule: Rule): String {
        if (rule.text.isEmpty() || text.startsWith(rule.text)) return text
        return rule.text + text
    }

    private fun suffix(text: String, rule: Rule, context: RuleContext): String {
        if (rule.text.isEmpty() || text.endsWith(rule.text)) return text
        return if (rule.perSentence) appendPerSentence(text, rule, context) else text + rule.text
    }

    /**
     * Appends [Rule.text] after every sentence, keeping the original separators.
     *
     * Ported from MiaoAssistant: only segments that contain CJK characters are decorated, so a
     * trailing kaomoji does not get a "喵" stuffed into the middle of it.
     */
    private fun appendPerSentence(text: String, rule: Rule, context: RuleContext): String {
        val matcher = SENTENCE_SPLIT.toPattern().matcher(text)
        val parts = mutableListOf<String>()
        val separators = mutableListOf<String>()
        var lastEnd = 0
        while (matcher.find()) {
            parts.add(text.substring(lastEnd, matcher.start()))
            separators.add(matcher.group(1).orEmpty())
            lastEnd = matcher.end()
        }
        if (lastEnd < text.length) {
            parts.add(text.substring(lastEnd))
        } else if (parts.isNotEmpty()) {
            parts.add("")
        }
        if (parts.isEmpty()) parts.add(text)

        val result = StringBuilder()
        parts.forEachIndexed { index, rawPart ->
            val part = rawPart.trim()
            if (part.isNotEmpty()) {
                result.append(part)
                val eligible = !rule.cjkOnly || containsCjk(part)
                if (eligible && !part.endsWith(rule.text)) result.append(rule.text)
            }
            if (index < separators.size) result.append(separators[index])
        }

        val output = result.toString().trim()
        context.sentenceCount(rule.id)
        return output.ifEmpty { text + rule.text }
    }

    private fun randomSuffix(text: String, rule: Rule, context: RuleContext): String {
        if (rule.candidates.isEmpty() || text.endsWith(rule.candidates.firstOrNull().orEmpty())) return text
        val fire = when (rule.randomMode) {
            RandomMode.PROBABILITY -> context.random.nextFloat() < rule.probability
            RandomMode.HALF -> context.random.nextFloat() < 0.5f
            RandomMode.INTERVAL -> {
                val interval = rule.interval.coerceAtLeast(1)
                context.sentenceCount(rule.id) % interval == 0
            }
        }
        if (!fire) return text
        val picked = rule.candidates[context.random.nextInt(rule.candidates.size)]
        return "$text $picked"
    }

    /** True when [text] contains a CJK ideograph, kana or Hangul syllable. */
    fun containsCjk(text: String): Boolean = text.any { char ->
        val code = char.code
        code in 0x4E00..0x9FFF ||   // CJK Unified Ideographs
            code in 0x3400..0x4DBF || // CJK Extension A
            code in 0x3040..0x309F || // Hiragana
            code in 0x30A0..0x30FF || // Katakana
            code in 0xAC00..0xD7AF   // Hangul syllables
    }

    private val Rule.displayName: String get() = name.ifBlank { kind.id }
}
