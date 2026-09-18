/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package love.miao.yun.text

/**
 * The form editor's vocabulary: the shapes a rule can take, and the translation both ways.
 *
 * A form is the half of the feature that does not require reading the language, and the two UI
 * engines each draw their own widgets for it — so what a dropdown *means* lives here instead, in
 * one place without a screen attached: which kinds exist, what a half-filled form becomes, and how
 * a rule written by hand turns back into something the form can show.
 */
object TextRuleForm {

    /** The condition shapes the dropdown offers, in the order it offers them. */
    enum class Condition(
        val label: String,
        /** Whether the condition takes text. */
        val wantsText: Boolean = false,
        /** Whether it takes a number instead. */
        val wantsNumber: Boolean = false,
    ) {
        Always("总是"),
        Contains("含", wantsText = true),
        NotContains("不含", wantsText = true),
        StartsWith("以…开头", wantsText = true),
        EndsWith("以…结尾", wantsText = true),
        Equals("是", wantsText = true),
        Matches("匹配正则", wantsText = true),
        Longer("长度大于", wantsNumber = true),
        Shorter("长度小于", wantsNumber = true),
        Chance("随机百分比", wantsNumber = true),
        EveryNth("每 N 次", wantsNumber = true),
        ;

        /** Every kind but 总是 can be negated; 总是 has nothing to negate. */
        val canNegate: Boolean get() = this != Always
    }

    /** The action shapes the dropdown offers. */
    enum class Action(
        val label: String,
        /** How many text values the action takes. */
        val values: Int,
        /** Whether the action offers the 空格不加 option. */
        val hasSkipSpaced: Boolean = false,
    ) {
        Replace("替换", 2),
        Delete("删除", 1),
        Prefix("开头加", 1),
        Suffix("末尾加", 1),
        PerSentence("每句末尾加", 1, hasSkipSpaced = true),
        Wrap("首尾包裹", 1),
        TrimPunctuation("去掉末尾标点", 0),
        Emoticon("加颜文字", 0),
        Regex("正则替换", 2),
    }

    /** One rule as a form sees it. */
    data class Form(
        val condition: Condition = Condition.Always,
        val conditionText: String = "",
        val conditionNumber: Int = DEFAULT_NUMBER,
        val negated: Boolean = false,
        val action: Action = Action.Suffix,
        val first: String = "",
        val second: String = "",
        val skipSpaced: Boolean = false,
        val firstOnly: Boolean = false,
    ) {
        val conditionWantsText: Boolean get() = condition.wantsText
        val conditionWantsNumber: Boolean get() = condition.wantsNumber
    }

    const val DEFAULT_NUMBER = 30

    // ------------------------------------------------------------------ reading

    /** How [condition] looks in a form; a negation that has no kind of its own keeps the flag. */
    fun form(condition: TextCondition): Form = when (condition) {
        TextCondition.Always -> Form(condition = Condition.Always)

        is TextCondition.Contains -> Form(condition = Condition.Contains, conditionText = condition.text)
        is TextCondition.StartsWith -> Form(condition = Condition.StartsWith, conditionText = condition.text)
        is TextCondition.EndsWith -> Form(condition = Condition.EndsWith, conditionText = condition.text)
        is TextCondition.Equals -> Form(condition = Condition.Equals, conditionText = condition.text)
        is TextCondition.Matches -> Form(condition = Condition.Matches, conditionText = condition.pattern)

        is TextCondition.LongerThan -> Form(condition = Condition.Longer, conditionNumber = condition.characters)
        is TextCondition.ShorterThan -> Form(condition = Condition.Shorter, conditionNumber = condition.characters)
        is TextCondition.Chance -> Form(condition = Condition.Chance, conditionNumber = condition.percent)
        is TextCondition.EveryNth -> Form(condition = Condition.EveryNth, conditionNumber = condition.count)

        is TextCondition.Not -> when (val inner = condition.condition) {
            // 不含 gets its own kind, because that is how a person thinks about it.
            is TextCondition.Contains -> Form(condition = Condition.NotContains, conditionText = inner.text)
            else -> form(inner).copy(negated = true)
        }
    }

    fun form(action: TextAction): Form = when (action) {
        is TextAction.Replace ->
            if (action.to.isEmpty()) {
                Form(action = Action.Delete, first = action.from)
            } else {
                Form(
                    action = Action.Replace,
                    first = action.from,
                    second = action.to,
                    firstOnly = action.firstOnly,
                )
            }

        is TextAction.ReplaceRegex ->
            Form(action = Action.Regex, first = action.pattern, second = action.replacement)

        is TextAction.Prefix -> Form(action = Action.Prefix, first = action.text)
        is TextAction.Suffix -> Form(action = Action.Suffix, first = action.text)
        is TextAction.Wrap -> Form(action = Action.Wrap, first = action.text)
        is TextAction.SuffixPerSentence ->
            Form(action = Action.PerSentence, first = action.text, skipSpaced = action.skipSpaced)

        TextAction.TrimTrailingPunctuation -> Form(action = Action.TrimPunctuation)
        TextAction.Emoticon -> Form(action = Action.Emoticon)
    }

    // ------------------------------------------------------------------ writing

    /**
     * The condition a form describes.
     *
     * An empty text is left as an empty text: the engine already treats a rule with nothing to look
     * for as doing nothing, and inventing a default would mean a rule that quietly matches
     * everything. What a user typed is what the rule says.
     */
    fun condition(form: Form): TextCondition {
        val base = when (form.condition) {
            Condition.Always -> TextCondition.Always
            Condition.Contains, Condition.NotContains -> TextCondition.Contains(form.conditionText)
            Condition.StartsWith -> TextCondition.StartsWith(form.conditionText)
            Condition.EndsWith -> TextCondition.EndsWith(form.conditionText)
            Condition.Equals -> TextCondition.Equals(form.conditionText)
            Condition.Matches -> TextCondition.Matches(form.conditionText)
            Condition.Longer -> TextCondition.LongerThan(form.conditionNumber)
            Condition.Shorter -> TextCondition.ShorterThan(form.conditionNumber)
            Condition.Chance -> TextCondition.Chance(form.conditionNumber.coerceIn(0, 100))
            Condition.EveryNth -> TextCondition.EveryNth(form.conditionNumber.coerceAtLeast(1))
        }
        val negated = form.condition == Condition.NotContains || form.negated
        return if (negated && base != TextCondition.Always) TextCondition.Not(base) else base
    }

    fun action(form: Form): TextAction = when (form.action) {
        Action.Replace -> TextAction.Replace(form.first, form.second, form.firstOnly)
        Action.Delete -> TextAction.Replace(form.first, "")
        Action.Prefix -> TextAction.Prefix(form.first)
        Action.Suffix -> TextAction.Suffix(form.first)
        Action.PerSentence -> TextAction.SuffixPerSentence(form.first, form.skipSpaced)
        Action.Wrap -> TextAction.Wrap(form.first)
        Action.TrimPunctuation -> TextAction.TrimTrailingPunctuation
        Action.Emoticon -> TextAction.Emoticon
        Action.Regex -> TextAction.ReplaceRegex(form.first, form.second)
    }

    /** The rule a form describes, keeping the parts a form does not touch. */
    fun rule(form: Form, existing: TextRule): TextRule = existing.copy(
        condition = condition(form),
        action = action(form),
    )

    /** The form for a whole rule, both halves at once. */
    fun form(rule: TextRule): Form {
        val conditionForm = form(rule.condition)
        return form(rule.action).copy(
            condition = conditionForm.condition,
            conditionText = conditionForm.conditionText,
            conditionNumber = conditionForm.conditionNumber,
            negated = conditionForm.negated,
        )
    }
}
