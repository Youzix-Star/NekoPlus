package love.miao.yun.util

import kotlin.random.Random

object TextProcessor {
    private val SENTENCE_SPLIT_PATTERN = Regex("([，,。！!？?\\s]+)")

    /** 检查文本是否包含 CJK 字符（中日韩），用于区分实际句子和颜文字 */
    private fun containsCJK(text: String): Boolean {
        return text.any {
            val c = it.code
            c in 0x4E00..0x9FFF ||  // CJK Unified Ideographs
            c in 0x3400..0x4DBF ||  // CJK Extension A
            c in 0x3040..0x309F ||  // Hiragana
            c in 0x30A0..0x30FF ||  // Katakana
            c in 0xAC00..0xD7AF    // Hangul
        }
    }

    fun process(original: String?, config: MiaoConfig, punctuationMode: Boolean = false, protectSuffix: Boolean = false): String {
        if (original.isNullOrBlank()) return original ?: ""
        var text = original.trim()

        // 1. 应用替换规则（按原词长度降序，长词优先匹配）
        for (rule in config.rules.sortedByDescending { it.from.length }) {
            if (rule.from.isNotEmpty()) {
                text = text.replace(rule.from, rule.to)
            }
        }

        // 判断是否应该触发颜文字
        val shouldEmoticon = config.shouldTriggerEmoticon()

        if (punctuationMode || protectSuffix) {
            // 标点触发/原文编辑模式：在文本末尾追加后缀和颜文字
            if (config.enableAppend && config.appendText.isNotEmpty() && !text.endsWith(config.appendText)) {
                // 空格不加喵：如果开启，检查空格后是否加喵
                if (config.spaceNoMiao) {
                    text = appendWithSpaceNoMiao(text, config.appendText)
                } else {
                    text = text + config.appendText
                }
            }
            // 标点模式优化：删除喵前面的标点符号
            if (punctuationMode && config.punctuationOptimize && config.enableAppend && config.appendText.isNotEmpty()) {
                text = removePunctuationBeforeSuffix(text, config.appendText)
            }
            if (shouldEmoticon) {
                val emoticons = config.getActiveEmoticons()
                if (emoticons.isNotEmpty()) {
                    text = text + " " + emoticons[Random.nextInt(emoticons.size)]
                }
            }
        } else {
            // 实时处理模式：每句话后追加后缀，末尾追加颜文字
            if (config.enableAppend) {
                text = if (config.spaceNoMiao) {
                    appendPerSentenceSpaceNoMiao(text, config.appendText)
                } else {
                    appendPerSentence(text, config.appendText)
                }
            }
            if (shouldEmoticon) {
                val emoticons = config.getActiveEmoticons()
                if (emoticons.isNotEmpty()) {
                    text = text + " " + emoticons[Random.nextInt(emoticons.size)]
                }
            }
        }

        return text
    }

    private fun appendPerSentence(text: String, suffix: String): String {
        if (suffix.isEmpty()) return text
        val parts = mutableListOf<String>()
        val separators = mutableListOf<String>()
        val matcher = SENTENCE_SPLIT_PATTERN.toPattern().matcher(text)
        var lastEnd = 0
        while (matcher.find()) {
            parts.add(text.substring(lastEnd, matcher.start()))
            separators.add(matcher.group(1)!!)
            lastEnd = matcher.end()
        }
        if (lastEnd < text.length) {
            parts.add(text.substring(lastEnd))
        } else if (parts.isNotEmpty() && lastEnd == text.length) {
            parts.add("")
        }
        if (parts.isEmpty()) parts.add(text)

        val result = StringBuilder()
        for (i in parts.indices) {
            val part = parts[i].trim()
            if (part.isNotEmpty()) {
                result.append(part)
                // 只在包含 CJK 字符的段落后加后缀，避免在颜文字内部加"喵"
                if (containsCJK(part) && !part.endsWith(suffix)) {
                    result.append(suffix)
                }
            }
            if (i < separators.size) {
                result.append(separators[i])
            }
        }
        val resultStr = result.toString().trim()
        return if (resultStr.isEmpty()) text + suffix else resultStr
    }

    /**
     * 标点模式优化：删除后缀前面的标点符号
     * 例如："测试，你好。喵" -> "测试，你好 喵"
     */
    private fun removePunctuationBeforeSuffix(text: String, suffix: String): String {
        val suffixIdx = text.lastIndexOf(suffix)
        if (suffixIdx <= 0) return text
        val charBefore = text[suffixIdx - 1]
        // 中英文标点符号
        val punctuation = setOf(
            '。', '！', '？', '，', '、', '；', '：',
            '.', '!', '?', ',', ';', ':',
            '~', '～', '…', '—',
            '）', '】', '》', '』', '〉',
            ')', ']', '}'
        )
        return if (charBefore in punctuation) {
            text.substring(0, suffixIdx - 1) + text.substring(suffixIdx)
        } else {
            text
        }
    }

    /**
     * 空格不加喵：空格后面的句子不加喵，只有最后一句加喵
     * 例如："你好 我是开发者" -> "你好 我是开发者喵"
     */
    private fun appendWithSpaceNoMiao(text: String, suffix: String): String {
        if (suffix.isEmpty()) return text
        // 找到最后一个非空格分隔的句子，在末尾加喵
        val lastSpaceIdx = text.lastIndexOf(' ')
        if (lastSpaceIdx >= 0) {
            val before = text.substring(0, lastSpaceIdx + 1)
            val lastSentence = text.substring(lastSpaceIdx + 1).trim()
            if (lastSentence.isNotEmpty() && !lastSentence.endsWith(suffix)) {
                return before + lastSentence + suffix
            }
            return text
        }
        // 没有空格，直接在末尾加喵
        if (!text.endsWith(suffix)) {
            return text + suffix
        }
        return text
    }

    /**
     * 实时处理模式下的空格不加喵：每句话后追加后缀，但空格分隔的部分不加
     * 例如："你好 我是开发者" -> "你好 我是开发者喵"
     */
    private fun appendPerSentenceSpaceNoMiao(text: String, suffix: String): String {
        if (suffix.isEmpty()) return text
        // 直接在整段文本末尾加喵，不在每句话后加
        if (!text.endsWith(suffix)) {
            return text + suffix
        }
        return text
    }
}
