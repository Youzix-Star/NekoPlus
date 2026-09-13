/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: GPL-3.0-only
 */

package love.miao.yun.text

/**
 * The three switches the simple page offers, and the rules behind them.
 *
 * 1.1.8 exposed exactly this much: a suffix, an emoticon, and a cat paw for QQ — each one a switch,
 * none of them editable. The values behind these switches are 1.1.8's own data, and the editable
 * part of the page is the plain find-and-replace list. Everything else the engine can do (conditions,
 * regex, variables, packs, per-app rules) still exists and is simply not on screen yet.
 */
object TextSwitches {

    enum class Toggle(val label: String, val summary: String) {
        Suffix("末尾加后缀", "「喵」写在每一句后面"),
        Emoticon("句末颜文字", "从颜文字表里随机挑一个接在最后"),
        Paw("QQ 猫爪", "首尾各加一个印记，只在 QQ 生效"),
    }

    /** Whether this switch is on, i.e. whether a rule of its shape is enabled. */
    fun isOn(rules: TextRules, toggle: Toggle): Boolean =
        rules.rules.any { it.enabled && matches(it, toggle) }

    /**
     * Turns one on or off.
     *
     * Switching off disables the rule instead of deleting it, so switching back on gives the user
     * back exactly what they had — including a rule they had edited by hand in the text form.
     */
    fun set(rules: TextRules, toggle: Toggle, on: Boolean): TextRules {
        if (rules.rules.none { matches(it, toggle) }) {
            return if (on) rules.copy(rules = rules.rules + rule(toggle)) else rules
        }
        return rules.copy(
            rules = rules.rules.map { if (matches(it, toggle)) it.copy(enabled = on) else it },
        )
    }

    /**
     * The rules the page's list shows and edits: plain find-and-replace, no conditions.
     *
     * A rule with a condition in it was written by hand in the text form, and a two-field editor
     * would quietly drop the condition — so those are left out of the list until the advanced
     * editor comes back.
     */
    fun replacements(rules: TextRules): List<TextRule> =
        rules.rules.filter { it.condition == TextCondition.Always && it.action is TextAction.Replace }

    /** Replaces the list of plain replacements, keeping every other rule where it was. */
    fun setReplacements(rules: TextRules, replacements: List<TextRule>): TextRules =
        rules.copy(rules = rules.rules.filterNot { isReplacement(it) } + replacements)

    /**
     * The emoticon list, one per line — **the whole list, defaults included**.
     *
     * The box opens showing the table the app ships with, so nobody has to guess what is in there:
     * adding a line adds an emoticon, deleting a line takes one away, and it is all visible. An
     * older version of this feature showed an empty box and made typing anything replace the whole
     * table, which lost fifty-odd emoticons to one careless edit.
     */
    fun emoticons(rules: TextRules): String {
        val stored = rules.variable(TextDefaults.EMOTICON_NAME) ?: return builtInEmoticons()
        return stored.split(TextDefaults.EMOTICON_SEPARATOR)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
            .ifEmpty { builtInEmoticons() }
    }

    /**
     * Stores the list as typed.
     *
     * Empty is not a list: a blank box means "I deleted everything" only in the sense of a slip, and
     * the one thing worse than a long list is an emoticon switch that silently stops working, so it
     * falls back to the built-in table.
     */
    fun setEmoticons(rules: TextRules, text: String): TextRules {
        val list = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val value = if (list.isEmpty()) builtInEmoticons() else list.joinToString(TextDefaults.EMOTICON_SEPARATOR)
        return rules.copy(variables = rules.variables + (TextDefaults.EMOTICON_NAME to value))
    }

    private fun builtInEmoticons(): String =
        TextDefaults.EMOTICONS.joinToString(TextDefaults.EMOTICON_SEPARATOR)

    /** The `A = B` line a replacement rule is written as, and back again. */
    fun replacement(from: String, to: String): TextRule =
        TextRule(action = TextAction.Replace(from, to))

    private fun isReplacement(rule: TextRule): Boolean =
        rule.condition == TextCondition.Always && rule.action is TextAction.Replace

    private fun matches(rule: TextRule, toggle: Toggle): Boolean = when (toggle) {
        Toggle.Suffix -> rule.action is TextAction.Suffix
        Toggle.Emoticon -> rule.action is TextAction.Emoticon
        Toggle.Paw -> rule.action is TextAction.Wrap
    }

    private fun rule(toggle: Toggle): TextRule = when (toggle) {
        Toggle.Suffix -> TextRule(
            action = TextAction.Suffix("{" + TextDefaults.SUFFIX_NAME + "}"),
        )

        Toggle.Emoticon -> TextRule(action = TextAction.Emoticon)

        Toggle.Paw -> TextRule(
            action = TextAction.Wrap("{" + TextDefaults.CAT_PAW_NAME + "}"),
            apps = setOf(QQ),
        )
    }

    /** The one app 1.1.8 ever marked with the paw. */
    private const val QQ = "com.tencent.mobileqq"
}
