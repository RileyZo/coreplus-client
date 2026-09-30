package com.github.riley.coreplus.domain.term

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 周次计算：由学期起始日与今天推算「今天是第几周」。
 *
 * 计算结果**不缓存**，每次都实时算，因此用户改动学期起始日后立即生效。
 */
object WeekCalculator {

    /**
     * 计算 [today] 是第几周。
     *
     * @param termStart 学期第一周的星期一
     * @param totalWeeks 学期总周数；小于等于 0 表示未知，此时不做上界检查
     * @return 周次（从 1 开始）；以下情况返回 null，UI 据此隐藏「本周」按钮：
     *   - [today] 早于 [termStart]，即还没开学
     *   - 算出的周次超过 [totalWeeks]，即已进入假期（或学期起始日设置得不对）
     */
    fun currentWeek(termStart: LocalDate, today: LocalDate, totalWeeks: Int): Int? {
        val days = ChronoUnit.DAYS.between(termStart, today)
        if (days < 0) return null

        val week = (days / DAYS_PER_WEEK + 1).toInt()
        if (totalWeeks > 0 && week > totalWeeks) return null
        return week
    }

    /** 每周天数。 */
    private const val DAYS_PER_WEEK = 7L
}
