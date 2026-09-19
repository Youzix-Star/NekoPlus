/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package top.youzix.nekoplus.text

/**
 * What a rule looks at before it does anything.
 *
 * A small closed set on purpose. The point of the language is that a rule fits on one line and can
 * be read back a year later; every condition here answers a question a person would actually ask
 * about a sentence — does it say this, does it start with this, how long is it.
 */
sealed interface TextCondition {

    /** No condition at all: the rule always runs. */
    data object Always : TextCondition

    /** The text contains [text] anywhere. */
    data class Contains(val text: String) : TextCondition

    data class StartsWith(val text: String) : TextCondition

    data class EndsWith(val text: String) : TextCondition

    /** The whole text is exactly [text]. */
    data class Equals(val text: String) : TextCondition

    /** The text matches the regular expression [pattern]. */
    data class Matches(val pattern: String) : TextCondition

    data class LongerThan(val characters: Int) : TextCondition

    data class ShorterThan(val characters: Int) : TextCondition

    /** True [percent] times out of a hundred. */
    data class Chance(val percent: Int) : TextCondition

    /** True on every [count]th time the rule is reached. */
    data class EveryNth(val count: Int) : TextCondition

    /** The opposite of [condition], which is how "不含" and "不以…开头" are stored. */
    data class Not(val condition: TextCondition) : TextCondition
}

/**
 * What a rule does to the text.
 *
 * Every action is **idempotent** where that is meaningful: adding a suffix the text already ends
 * with does nothing. Rules run on every tap, on text that may already have been through them, and a
 * rule that stacks "喵喵喵" on the third tap is a rule nobody keeps.
 */
sealed interface TextAction {

    /** Replaces [from] with [to]; all of them, or only the first when [firstOnly]. */
    data class Replace(
        val from: String,
        val to: String,
        val firstOnly: Boolean = false,
    ) : TextAction

    /** Replaces everything matching [pattern] with [replacement], `$1` style groups included. */
    data class ReplaceRegex(val pattern: String, val replacement: String) : TextAction

    /** Puts [text] in front of everything else. */
    data class Prefix(val text: String) : TextAction

    /** Puts [text] after everything else. */
    data class Suffix(val text: String) : TextAction

    /** Puts [text] on both ends — the shape a "cat paw" mark takes. */
    data class Wrap(val text: String) : TextAction

    /**
     * Puts [text] after every sentence.
     *
     * Only sentences containing CJK get it, which is how a suffix avoids landing inside an emoticon,
     * and [skipSpaced] narrows it further: with a space in the text, only the last sentence is
     * treated, which is what 1.1.8 called 空格不加喵.
     */
    data class SuffixPerSentence(val text: String, val skipSpaced: Boolean = false) : TextAction

    /** Removes one trailing punctuation mark, for text that is about to gain a suffix. */
    data object TrimTrailingPunctuation : TextAction

    /** Appends one emoticon, picked at random out of the `{颜文字}` variable. */
    data object Emoticon : TextAction
}

/**
 * One line of the language.
 *
 * @param condition what has to hold for the rule to run.
 * @param action what it does when it does.
 * @param apps the package names the rule is limited to; empty means every app. A rule that only
 *   makes sense in one chat app says so instead of the whole feature being switched per app.
 */
data class TextRule(
    val enabled: Boolean = true,
    val condition: TextCondition = TextCondition.Always,
    val action: TextAction,
    val apps: Set<String> = emptySet(),
)

/**
 * The whole configuration: the rules, and the named values they refer to.
 *
 * [variables] is the part 1.1.8 had no room for. Its "cat paw" was a byte sequence in the service,
 * its suffix was a fixed field, and its emoticon list was a constant — three things you could not
 * change. Here they are values with names, and a rule mentions them as `{猫爪}`, so changing what
 * the cat paw *is* changes every rule that draws one.
 */
data class TextRules(
    val rules: List<TextRule> = emptyList(),
    val variables: Map<String, String> = TextDefaults.variables,
) {
    /** The value of `{name}`, or null when nothing defines it. */
    fun variable(name: String): String? = variables[name]

    /**
     * Substitutes every `{name}` in [text]; an unknown name is left alone, visibly.
     *
     * Scanned by hand rather than with a regular expression, and not because of style: the first
     * version of this compiled `\{([^{}\n]+)}` into a static field, and Android's regex engine
     * (ICU, not the Java one this was tested against on the JVM) rejects a bare `}` — so the class
     * failed to initialise and **opening the settings page crashed instantly**. A loop has no
     * dialect to be incompatible with.
     */
    fun expand(text: String): String {
        if (!text.contains('{')) return text
        val result = StringBuilder(text.length)
        var index = 0
        while (index < text.length) {
            val open = text.indexOf('{', index)
            val close = if (open < 0) -1 else text.indexOf('}', open + 1)
            // Nothing left to substitute, or an unclosed brace: the rest is copied as written.
            if (open < 0 || close < 0) {
                result.append(text, index, text.length)
                break
            }
            val name = text.substring(open + 1, close)
            val value = variables[name]
            if (value != null && name.isNotEmpty()) {
                result.append(text, index, open).append(value)
            } else {
                // Unknown name, or an empty one: left alone, so the user can see what they typed.
                result.append(text, index, close + 1)
            }
            index = close + 1
        }
        return result.toString()
    }
}

/** One named set of rules, kept around so the user can switch between them. */
data class TextPack(
    val name: String,
    val rules: TextRules,
)

/**
 * What a fresh install has, and the word lists the feature ships with.
 *
 * The starter rules are the ones 1.1.8 shipped hard-wired — a suffix and an emoticon — expressed as
 * two lines that can be edited or deleted, rather than as behaviour buried in the service.
 */
object TextDefaults {

    /** The emoticon list 1.1.8 shipped, unchanged: it was good, and it is now editable. */
    val EMOTICONS: List<String> = listOf(
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

    /** The separator between emoticons in the `{颜文字}` value; a character nobody types by luck. */
    const val EMOTICON_SEPARATOR = "｜"

    const val SUFFIX_NAME = "后缀"
    const val CAT_PAW_NAME = "猫爪"
    const val EMOTICON_NAME = "颜文字"

    val variables: Map<String, String> = mapOf(
        SUFFIX_NAME to "喵",
        // 1.1.8's cat paw was an invisible byte sequence only QQ could draw, hard-wired for QQ.
        // A paw that renders everywhere is a better default for a value the user can now change.
        CAT_PAW_NAME to "ฅ",
        EMOTICON_NAME to EMOTICONS.joinToString(EMOTICON_SEPARATOR),
    )

    /**
     * What a fresh install starts with: the variables, and **no rules**.
     *
     * 1.1.8 shipped with 加喵 switched on, but this app's AI button has never appended anything, and
     * a feature that silently starts editing text nobody asked it to edit is a feature that gets
     * uninstalled. The behaviour is here instead, as [meow], one tap away in the editor.
     */
    fun starter(): TextRules = TextRules(rules = emptyList(), variables = variables)

    /** 1.1.8's own behaviour, as a pack: a suffix on the end, and an emoticon 30% of the time. */
    fun meow(): TextRules = TextRules(
        rules = listOf(
            TextRule(
                condition = TextCondition.Always,
                action = TextAction.Suffix("{" + SUFFIX_NAME + "}"),
            ),
            TextRule(
                condition = TextCondition.Chance(30),
                action = TextAction.Emoticon,
            ),
        ),
        variables = variables,
    )

    /** 1.1.8's 猫爪, generalized: the same mark on both ends, and the mark is the user's to change. */
    fun catPaw(): TextRules = TextRules(
        rules = listOf(
            TextRule(
                condition = TextCondition.Always,
                action = TextAction.Wrap("{" + CAT_PAW_NAME + "}"),
            ),
        ),
        variables = variables,
    )
}
