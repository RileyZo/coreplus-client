package com.github.riley.coreplus.domain.model

/**
 * 星期枚举，用于替代 [Course] 中以字符串保存的星期值。
 *
 * 枚举内部同时保存了序号与中文名称，并重写了 [toString]，
 * 因此在需要字符串的场景下可以直接使用 `weekday.toString()`（或字符串模板 `"$weekday"`）得到 "星期一"。
 *
 * @property value 星期序号，1 表示星期一，7 表示星期日
 * @property label 完整中文名称，如 "星期一"
 * @property shortLabel 简写中文名称，如 "周一"
 */
enum class Weekday(val value: Int, val label: String, val shortLabel: String) {
    MONDAY(1, "星期一", "周一"),
    TUESDAY(2, "星期二", "周二"),
    WEDNESDAY(3, "星期三", "周三"),
    THURSDAY(4, "星期四", "周四"),
    FRIDAY(5, "星期五", "周五"),
    SATURDAY(6, "星期六", "周六"),
    SUNDAY(7, "星期日", "周日");

    /**
     * 返回完整中文名称，例如 `Weekday.MONDAY.toString()` 得到 "星期一"。
     */
    override fun toString(): String = label

    companion object {
        /** 中文数字与序号的映射，用于解析 "星期三"、"周三"、"礼拜三" 等写法。 */
        private val CHINESE_NUMBERS = mapOf(
            "一" to 1, "二" to 2, "三" to 3, "四" to 4,
            "五" to 5, "六" to 6, "日" to 7, "天" to 7, "七" to 7,
        )

        /**
         * 按序号查找星期，序号超出 1..7 时返回 null。
         */
        fun fromValue(value: Int): Weekday? = entries.firstOrNull { it.value == value }

        /**
         * 按字符串查找星期，兼容多种常见写法：
         * "星期一"、"周一"、"礼拜一"、"星期天"、"Monday"、"MON"、"1"。
         *
         * 无法识别时返回 null。
         */
        fun fromString(text: String): Weekday? {
            val raw = text.trim()
            if (raw.isEmpty()) return null

            // 直接匹配枚举常量名，如 "Monday"、"MONDAY"
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }?.let { return it }

            // 匹配中文全称 / 简写，如 "星期一"、"周一"
            entries.firstOrNull { it.label == raw || it.shortLabel == raw }?.let { return it }

            // 匹配纯数字，如 "1"
            raw.toIntOrNull()?.let { fromValue(it) }?.let { return it }

            // 匹配 "星期X" / "周X" / "礼拜X" 形式，如 "星期天"、"礼拜三"
            val suffix = raw.removePrefix("星期")
                .removePrefix("礼拜")
                .removePrefix("周")
            if (suffix.length == 1) {
                CHINESE_NUMBERS[suffix]?.let { return fromValue(it) }
            }
            return null
        }
    }
}
