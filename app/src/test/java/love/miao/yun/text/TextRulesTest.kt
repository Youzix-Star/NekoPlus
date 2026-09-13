/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: GPL-3.0-only
 */

package love.miao.yun.text

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the rule language promises, checked without a phone.
 *
 * The parser and the engine are pure Kotlin on purpose, so the parts of this feature that can be
 * wrong quietly — a rule that splits a word in half, a suffix that stacks on the second tap, a round
 * trip that loses a rule — are checked on every build instead of by installing an APK and squinting.
 */
class TextRulesTest {

    private fun rules(text: String): TextRules = TextRuleText.parse(text).config

    private fun apply(text: String, source: String, packageName: String? = null): String =
        TextEngine(Random(1)).apply(text, rules(source), packageName)

    private fun first(source: String): TextRule = rules(source).rules.first()

    // ------------------------------------------------------------------ the language

    @Test
    fun `a plain assignment is a replacement, 1_1_8 style`() {
        val rule = first("你好=您好")
        assertEquals(TextAction.Replace("你好", "您好"), rule.action)
        assertEquals(TextCondition.Always, rule.condition)
        assertEquals("您好，您好", apply("你好，你好", "你好=您好"))
    }

    @Test
    fun `arrows and full width equals signs work too`() {
        assertEquals(TextAction.Replace("a", "b"), first("a→b").action)
        assertEquals(TextAction.Replace("a", "b"), first("a＝b").action)
        assertEquals(TextAction.Replace("a", "b"), first("a -> b").action)
    }

    @Test
    fun `when-then and if-then are the same sentence`() {
        val expected = TextRule(
            condition = TextCondition.Contains("？"),
            action = TextAction.Suffix("喵"),
        )
        assertEquals(expected, first("当 含\"？\" 则 末尾加\"喵\""))
        assertEquals(expected, first("如果 含\"？\" 那么 末尾加\"喵\""))
        assertEquals(expected, first("如果 含\"？\" 就 末尾加\"喵\""))
    }

    @Test
    fun `a keyword inside a quoted value does not split the rule`() {
        // 因为 contains 为, and 就 is the action keyword: the two ways this language can be fooled.
        assertEquals(TextAction.Replace("因为", "所以"), first("替换 \"因为\" 为 \"所以\"").action)
        assertEquals("所以", apply("因为", "替换 \"因为\" 为 \"所以\""))
        assertEquals(TextCondition.Contains("就"), first("当 含\"就\" 则 末尾加\"喵\"").condition)
        assertEquals("我就要喵", apply("我就要", "当 含\"就\" 则 末尾加\"喵\""))
    }

    @Test
    fun `the app scope trails the rule`() {
        val rule = first("末尾加\"喵\" @com.tencent.mm")
        assertEquals(setOf("com.tencent.mm"), rule.apps)
        assertEquals("你好喵", apply("你好", "末尾加\"喵\" @com.tencent.mm", "com.tencent.mm"))
        assertEquals("你好", apply("你好", "末尾加\"喵\" @com.tencent.mm", "com.tencent.mobileqq"))
        // A rule limited to one app never runs where there is no app to compare against.
        assertEquals("你好", apply("你好", "末尾加\"喵\" @com.tencent.mm"))
    }

    @Test
    fun `a switched-off rule is written down and read back`() {
        val rule = first("末尾加\"喵\"  # 已停用")
        assertFalse(rule.enabled)
        assertEquals("你好", apply("你好", "末尾加\"喵\"  # 已停用"))
    }

    @Test
    fun `variables are values with names`() {
        val source = """
            {后缀} = 呀
            末尾加"{后缀}"
        """.trimIndent()
        assertEquals("你好呀", apply("你好", source))
        assertEquals("呀", rules(source).variable(TextDefaults.SUFFIX_NAME))
    }

    @Test
    fun `a broken line costs that line and nothing else`() {
        val parsed = TextRuleText.parse(
            """
            你好 = 您好
            当 什么东西 则 末尾加"喵"
            末尾加"汪"
            """.trimIndent(),
        )
        assertEquals(2, parsed.config.rules.size)
        assertEquals(1, parsed.problems.size)
        assertEquals(2, parsed.problems.first().line)
    }

    @Test
    fun `rendering and parsing are exact inverses`() {
        val source = """
            {后缀} = 喵
            {猫爪} = ฅ

            你好 = 您好
            当 含"？" 则 末尾加"{后缀}"
            如果 长度 > 40 那么 每句末尾加"{后缀}"（空格不加）
            当 匹配/\\d+/ 则 正则替换/\\d+/ 为 "数"
            首尾包裹"{猫爪}" @com.tencent.mobileqq
            去掉末尾标点
            加颜文字
            替换第一个 "a" 为 "b"
            删除"的"
        """.trimIndent()

        val once = rules(source)
        val rendered = TextRuleText.render(once)
        val twice = TextRuleText.parse(rendered).config
        assertEquals(once.rules, twice.rules)
        assertEquals(once.variables, twice.variables)
        assertEquals(rendered, TextRuleText.render(twice))
    }

    // ------------------------------------------------------------------ the engine

    @Test
    fun `a suffix is not added twice`() {
        val source = "末尾加\"喵\""
        assertEquals("你好喵", apply("你好", source))
        assertEquals("你好喵", apply("你好喵", source))
    }

    @Test
    fun `a wrap is not added twice either`() {
        val source = "首尾包裹\"ฅ\""
        assertEquals("ฅ你好ฅ", apply("你好", source))
        assertEquals("ฅ你好ฅ", apply("ฅ你好ฅ", source))
    }

    @Test
    fun `every sentence, and only the sentences with words in them`() {
        val source = "每句末尾加\"喵\""
        assertEquals("你好喵，你准备好了吗喵？", apply("你好，你准备好了吗？", source))
        // 1.1.8's rule, kept: a suffix inside an emoticon is a suffix in the wrong place.
        assertEquals("ฅ^•ﻌ•^ฅ 你好喵", apply("ฅ^•ﻌ•^ฅ 你好", source))
        // A run of separators is one boundary, and comes back exactly as it went in.
        assertEquals("你好喵， 你好喵", apply("你好， 你好", source))
    }

    @Test
    fun `space-no-suffix treats the whole text as one sentence`() {
        val source = "每句末尾加\"喵\"（空格不加）"
        assertEquals("你好 我是开发者喵", apply("你好 我是开发者", source))
    }

    @Test
    fun `punctuation is cleared before the suffix lands on it`() {
        val source = """
            去掉末尾标点
            末尾加"喵"
        """.trimIndent()
        assertEquals("你好喵", apply("你好。", source))
    }

    @Test
    fun `random is random, and every-n times counts across taps`() {
        assertEquals("你好", apply("你好", "当 随机 0% 则 末尾加\"喵\""))
        assertEquals("你好喵", apply("你好", "当 随机 100% 则 末尾加\"喵\""))

        // One engine, three taps: 每 2 次 fires on the second and the fourth.
        val engine = TextEngine(Random(1))
        val source = rules("当 每 2 次 则 末尾加\"喵\"")
        assertEquals("a", engine.apply("a", source))
        assertEquals("a喵", engine.apply("a", source))
        assertEquals("a", engine.apply("a", source))
        assertEquals("a喵", engine.apply("a", source))
    }

    @Test
    fun `conditions read the text the way they say they do`() {
        assertEquals("你好喵", apply("你好", "当 以\"你\"开头 则 末尾加\"喵\""))
        assertEquals("你好", apply("你好", "当 以\"好\"开头 则 末尾加\"喵\""))
        assertEquals("你好喵", apply("你好", "当 不以\"好\"开头 则 末尾加\"喵\""))
        assertEquals("你好喵", apply("你好", "当 长度 > 1 则 末尾加\"喵\""))
        assertEquals("你好", apply("你好", "当 长度 > 5 则 末尾加\"喵\""))
        assertEquals("你好喵", apply("你好", "当 匹配/^你/ 则 末尾加\"喵\""))
        assertEquals("你好", apply("你好", "当 不含\"你\" 则 末尾加\"喵\""))
    }

    @Test
    fun `rules run in order`() {
        val source = """
            你好 = 您好
            末尾加"喵"
        """.trimIndent()
        assertEquals("您好喵", apply("你好", source))
    }

    @Test
    fun `an emoticon comes out of the list it was given`() {
        val source = """
            {颜文字} = ฅ ｜ ^•ﻌ•^
            加颜文字
        """.trimIndent()
        val result = apply("你好", source)
        assertTrue(result == "你好 ฅ" || result == "你好 ^•ﻌ•^")
    }

    @Test
    fun `variables are substituted, and anything unknown is left alone`() {
        val rules = TextRules(rules = emptyList(), variables = mapOf("后缀" to "喵"))
        assertEquals("你好喵", rules.expand("你好{后缀}"))
        assertEquals("喵喵", rules.expand("{后缀}{后缀}"))
        // Anything the scan cannot make sense of is copied out as typed, so a typo is visible
        // rather than silently swallowing the rest of the text.
        assertEquals("你好{没有这个}", rules.expand("你好{没有这个}"))
        assertEquals("你好{}", rules.expand("你好{}"))
        assertEquals("你好{未闭合", rules.expand("你好{未闭合"))
        assertEquals("没有变量", rules.expand("没有变量"))
    }

    @Test
    fun `the form can show every rule and hand it back unchanged`() {
        val conditions = listOf(
            TextCondition.Always,
            TextCondition.Contains("x"),
            TextCondition.StartsWith("y"),
            TextCondition.EndsWith("z"),
            TextCondition.Equals("q"),
            TextCondition.Matches("\\d+"),
            TextCondition.LongerThan(3),
            TextCondition.ShorterThan(9),
            TextCondition.Chance(40),
            TextCondition.EveryNth(2),
            TextCondition.Not(TextCondition.Contains("no")),
            TextCondition.Not(TextCondition.StartsWith("a")),
        )
        conditions.forEach { condition ->
            assertEquals(condition, TextRuleForm.condition(TextRuleForm.form(condition)))
        }

        val actions = listOf(
            TextAction.Replace("a", "b"),
            TextAction.Replace("a", ""),
            TextAction.Replace("a", "b", firstOnly = true),
            TextAction.ReplaceRegex("\\d+", "n"),
            TextAction.Prefix("["),
            TextAction.Suffix("喵"),
            TextAction.Wrap("ฅ"),
            TextAction.SuffixPerSentence("喵", skipSpaced = true),
            TextAction.TrimTrailingPunctuation,
            TextAction.Emoticon,
        )
        actions.forEach { action ->
            assertEquals(action, TextRuleForm.action(TextRuleForm.form(action)))
        }
    }

    @Test
    fun `a rule written as text survives a trip through the form`() {
        val rule = first("当 含\"？\" 则 每句末尾加\"{后缀}\"（空格不加） @com.tencent.mm")
        val form = TextRuleForm.form(rule)
        assertEquals(TextRuleForm.Condition.Contains, form.condition)
        assertEquals("？", form.conditionText)
        assertEquals(TextRuleForm.Action.PerSentence, form.action)
        assertEquals("{后缀}", form.first)
        assertTrue(form.skipSpaced)
        // The form does not touch the app scope, so the rule comes back whole.
        assertEquals(rule, TextRuleForm.rule(form, rule))
    }

    @Test
    fun `the three switches add and disable rules, and the list only shows replacements`() {
        var rules = TextDefaults.starter()
        TextSwitches.Toggle.entries.forEach { toggle ->
            assertFalse(toggle.name, TextSwitches.isOn(rules, toggle))
            rules = TextSwitches.set(rules, toggle, true)
            assertTrue(toggle.name, TextSwitches.isOn(rules, toggle))
        }
        // Switching off disables rather than deletes, so switching back on is free.
        rules = TextSwitches.set(rules, TextSwitches.Toggle.Suffix, false)
        assertFalse(TextSwitches.isOn(rules, TextSwitches.Toggle.Suffix))
        assertEquals(3, rules.rules.size)
        rules = TextSwitches.set(rules, TextSwitches.Toggle.Suffix, true)
        assertTrue(TextSwitches.isOn(rules, TextSwitches.Toggle.Suffix))
        assertEquals(3, rules.rules.size)

        // The paw is 1.1.8's: QQ only.
        val paw = rules.rules.first { it.action is TextAction.Wrap }
        assertEquals(setOf("com.tencent.mobileqq"), paw.apps)

        // The page's list is the plain replacements, and writing it back leaves the switches alone.
        assertTrue(TextSwitches.replacements(rules).isEmpty())
        val withReplacements = TextSwitches.setReplacements(
            rules,
            listOf(TextSwitches.replacement("你好", "您好")),
        )
        assertEquals(1, TextSwitches.replacements(withReplacements).size)
        TextSwitches.Toggle.entries.forEach { toggle ->
            assertTrue(toggle.name, TextSwitches.isOn(withReplacements, toggle))
        }
    }

    @Test
    fun `custom emoticons replace the built-in list, and empty goes back to it`() {
        val rules = TextDefaults.starter()
        val custom = TextSwitches.setEmoticons(rules, "ฅ\n(=^･ω･^=)")
        assertEquals("ฅ\n(=^･ω･^=)", TextSwitches.emoticons(custom))
        val result = TextEngine(Random(1)).apply("你好", TextSwitches.set(custom, TextSwitches.Toggle.Emoticon, true))
        assertTrue(result == "你好 ฅ" || result == "你好 (=^･ω･^=)")

        val back = TextSwitches.setEmoticons(custom, "  ")
        assertEquals("", TextSwitches.emoticons(back))
    }

    @Test
    fun `a fresh install changes nothing at all`() {
        val starter = TextDefaults.starter()
        assertTrue(starter.rules.isEmpty())
        assertEquals("你好", TextEngine(Random(7)).apply("你好", starter))
        // The variables are still there, so the first rule the user writes can already refer to them.
        assertEquals("喵", starter.variable(TextDefaults.SUFFIX_NAME))
    }

    @Test
    fun `1_1_8's behaviour is a pack, and the cat paw is a value`() {
        val meow = TextDefaults.meow()
        assertEquals(2, meow.rules.size)
        assertTrue(TextEngine(Random(7)).apply("你好", meow).startsWith("你好喵"))

        val paw = TextDefaults.catPaw()
        assertEquals("ฅ你好ฅ", TextEngine(Random(1)).apply("你好", paw))
        // The whole point: the mark is a variable, so changing it changes every rule that draws one.
        val custom = paw.copy(variables = paw.variables + (TextDefaults.CAT_PAW_NAME to "🐾"))
        assertEquals("🐾你好🐾", TextEngine(Random(1)).apply("你好", custom))
    }
}
