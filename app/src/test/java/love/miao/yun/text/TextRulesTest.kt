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

    private fun rules(text: String): TextRules = TextRuleText.parse(text).rules

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
        assertEquals(2, parsed.rules.size)
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
        val twice = TextRuleText.parse(rendered).rules
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
    fun `the starter rules are 1_1_8's behaviour, written down`() {
        val starter = TextDefaults.starter()
        // The suffix, always; the emoticon is a 30% chance, so it is checked both ways round on a
        // seeded engine rather than hoped for.
        assertTrue(TextEngine(Random(7)).apply("你好", starter).startsWith("你好喵"))
        assertEquals(2, starter.rules.size)
        assertTrue(TextRuleText.render(starter).contains("末尾加"))
    }
}
