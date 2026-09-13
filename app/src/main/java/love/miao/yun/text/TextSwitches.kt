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
     * What the user added, one per line — the built-in ones are not shown back to them.
     *
     * The list the engine draws from is always the built-in table plus whatever is here, so the
     * emoticons work out of the box for everybody and typing a few of your own **adds** to them
     * rather than replacing them. Replacing is what 1.1.8 did, and it meant one careless edit left
     * a user with a single emoticon and no way to get the other fifty-three back.
     */
    fun emoticons(rules: TextRules): String {
        val stored = rules.variable(TextDefaults.EMOTICON_NAME) ?: return ""
        return stored.split(TextDefaults.EMOTICON_SEPARATOR)
            .map { it.trim() }
            .filter { it.isNotEmpty() && it !in TextDefaults.EMOTICONS }
            .joinToString("\n")
    }

    /** Sets the additions; an empty list leaves exactly the built-in table. */
    fun setEmoticons(rules: TextRules, text: String): TextRules {
        val custom = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val value = (TextDefaults.EMOTICONS + custom)
            .distinct()
            .joinToString(TextDefaults.EMOTICON_SEPARATOR)
        return rules.copy(variables = rules.variables + (TextDefaults.EMOTICON_NAME to value))
    }

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
