/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0
 */

package love.miao.yun.rule

/**
 * Ready-made rules that replace the pile of switches the app used to have.
 *
 * MiaoAssistant exposed "append after every sentence", "keep spaces alone", "strip the punctuation
 * before the suffix", "sprinkle emoticons with this probability" and "wrap in cat paws" as separate
 * booleans. Here they are ordinary entries in the pipeline, so a user can read them, reorder them,
 * disable them or copy one and edit it.
 *
 * Nothing is applied automatically: these are templates the user adds.
 */
object BuiltinRules {

    /** The kaomoji library that used to live in `MiaoConfig.BUILTIN_EMOTICONS`. */
    val emoticons: List<String> = listOf(
        "^⌯𖥦⌯^ ੭ ^", "⌯'ㅅ'⌯", "=^𖥦^=", "⌯•ㅅ•⌯", "ฅ•̀∀•́ฅ",
        "ฅ ̳͒•ˑ̫• ̳͒ฅ♡", "ฅ(̳•·̫•̳ฅ)♡", "ฅ^••^ฅ", "=^•ω•^=",
        "₍^ >ヮ<^₎", "/ᐠ - ˕ -マ Ⳋ", "ฅ^•ﻌ•^ฅ", "ฅ՞•ﻌ•՞ฅ",
        "(ฅ´ω`ฅ)", "ฅ(*`ω´*)ฅ", "ฅ꒰ ⸝˶• •˶⸝꒱ฅ", "₍˄·͈༝·͈˄*₎◞ ̑̑",
        "!!^⌯𖥦⌯^ ੭!!", "₍^⸝⸝> ·̫ <⸝⸝ ^₎", "ฅ^._.^ฅ",
        "₍🎀˄•͈༝•͈˄₎ฅ˒˒", "^•͈༝•^ฅ", "꒰ఎ(^ . ֑ .^)໒꒱", "ฅ●ω●ฅ",
        "₍⸍⸌·͈༝·͈⸍⸌₎◞", "(>^ω^<)", "ฅ^-﹃-^ฅ", "^ ̳ට ̫ ට ̳^",
        "୧₍˄·͈༝·͈˄₎୨", "^ ̳ᴗ  ̫ ᴗ ̳^", "˓˓ก(⸍⸌̣ʷ̣̫⸍̣⸌₎ค˒˒",
        "ヽ(ฅ≧へ≦)ฅ", "(`･ω･´)ฅ", "(=^･ᴥ･^=)", "(^ω^ฅ)",
        "ฅ(≧▽≦)ฅ", "ฅ(=´▽`=)ฅ", "ヾ((๑˘ㅂ˘๑)ฅ", "(ฅ◑ω◑ฅ)",
        "(๑•̀ω•́ฅ)", "(ฅ>ω<*ฅ)", "(=^.^=)", "(=´ᴥ`)",
        "(=ↀωↀ=)", "(=^-ω-^=)", "ฅ(*°ω°*ฅ)", "ヽ(=^･ω･^=)丿",
        "(^•ᴥ•^)", "( Φ ω Φ )", "(=^x^=)", "ฅ( ̳• ◡ • ̳)ฅ",
        "o( =•ω•= )m", "~o( =∩ω∩= )m", "≡ω≡",
    )

    /** Suffix used by the classic "喵" rules. */
    const val MIAO = "喵"

    /**
     * The template library, in the order it is offered in the UI.
     *
     * Templates carry distinct names so the list stays readable; the caller assigns fresh ids when
     * the user adds one.
     */
    val templates: List<Rule> = listOf(
        Rule(
            name = "每句结尾加「喵」",
            kind = RuleKind.SUFFIX,
            text = MIAO,
            perSentence = true,
            cjkOnly = true,
        ),
        Rule(
            name = "只在句末加「喵」",
            kind = RuleKind.SUFFIX,
            text = MIAO,
            perSentence = false,
        ),
        Rule(
            name = "去掉「喵」前的标点",
            kind = RuleKind.REPLACE,
            pattern = "[，,。！!？?](?=$MIAO)",
            replacement = "",
            useRegex = true,
        ),
        Rule(
            name = "五成概率加颜文字",
            kind = RuleKind.RANDOM_SUFFIX,
            candidates = emoticons,
            randomMode = RandomMode.PROBABILITY,
            probability = 0.5f,
        ),
        Rule(
            name = "每三句加一次颜文字",
            kind = RuleKind.RANDOM_SUFFIX,
            candidates = emoticons,
            randomMode = RandomMode.INTERVAL,
            interval = 3,
        ),
        Rule(
            name = "猫爪 · 句首",
            kind = RuleKind.PREFIX,
            text = "🐾 ",
        ),
        Rule(
            name = "猫爪 · 句尾",
            kind = RuleKind.SUFFIX,
            text = " 🐾",
        ),
        Rule(
            name = "微软式中文（AI）",
            kind = RuleKind.AI,
            preset = "微软式中文",
        ),
        Rule(
            name = "口头禅替换（示例）",
            kind = RuleKind.REPLACE,
            pattern = "我",
            replacement = "本喵",
            useRegex = false,
        ),
    )

    /** A fresh, disabled-by-default copy of a template, ready to be edited by the user. */
    fun instantiate(template: Rule): Rule = template.copy(
        id = java.util.UUID.randomUUID().toString(),
        enabled = false,
    )
}
