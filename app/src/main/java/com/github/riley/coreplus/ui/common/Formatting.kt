package com.github.riley.coreplus.ui.common

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * 界面文案用的日期时间格式。
 *
 * 集中在一处，避免各个 Composable 里散落 `DateTimeFormatter.ofPattern(...)`
 * ——那个调用不便宜，且格式容易写得不一致。
 */
object Formatting {

    /** 完整日期，如 `2026年8月31日`。用于学期开始日期。 */
    val FULL_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日")

    /** 省略年份的日期，如 `8月31日`。用于空间紧张处。 */
    val SHORT_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("M月d日")

    /** 时刻，如 `08:00`。作息表编辑与课程块时间线都用它。 */
    val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
}

/** 格式化为 `2026年8月31日`。 */
fun LocalDate.display(): String = format(Formatting.FULL_DATE)

/** 格式化为 `8月31日`。 */
fun LocalDate.displayShort(): String = format(Formatting.SHORT_DATE)

/** 格式化为 `08:00`。 */
fun LocalTime.display(): String = format(Formatting.TIME)

/**
 * 把学分显示成尽量短的形式：整数省略小数点（`3`），否则保留一位（`3.5`、`0.5`）。
 *
 * 后端下发的 `credit` 是浮点数，`3.0` 直接显示成 "3.0" 会显得啰嗦。
 */
fun Double.displayCredit(): String =
    if (this % 1.0 == 0.0) toInt().toString() else toString()
