package com.github.riley.coreplus.domain.term

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/**
 * [WeekCalculator.currentWeek] 的单元测试。
 *
 * 覆盖开学首日、跨月、还没开学、超出总周数、totalWeeks <= 0 时的上界检查豁免等场景。
 */
class WeekCalculatorTest {

    /** 学期起始日：2026-08-31（星期一）。 */
    private val termStart = LocalDate.of(2026, 8, 31)

    @Test
    fun `should return 1 on first day of term`() {
        // 开学首日 → 第 1 周
        val today = termStart

        val result = WeekCalculator.currentWeek(termStart, today, totalWeeks = 20)

        assertEquals(1, result)
    }

    @Test
    fun `should return 2 seven days after term start`() {
        // termStart + 7 天 → 第 2 周
        val today = termStart.plusDays(7)

        val result = WeekCalculator.currentWeek(termStart, today, totalWeeks = 20)

        assertEquals(2, result)
    }

    @Test
    fun `should return correct week across month boundary`() {
        // termStart=2026-08-31, today=2026-10-05
        // 间隔天数 = (10月5日 - 8月31日) = 35 天 → 35/7 + 1 = 6
        val today = LocalDate.of(2026, 10, 5)

        val result = WeekCalculator.currentWeek(termStart, today, totalWeeks = 20)

        assertEquals(6, result)
    }

    @Test
    fun `should return null when today is before term start`() {
        // 还没开学 → null
        val today = termStart.minusDays(1)

        val result = WeekCalculator.currentWeek(termStart, today, totalWeeks = 20)

        assertNull("还没开学时应返回 null", result)
    }

    @Test
    fun `should return null when week exceeds totalWeeks`() {
        // 算出第 21 周但 totalWeeks=20 → null（已进入假期）
        // termStart + 20*7 天 = 第 21 周的第一天
        val today = termStart.plusDays(20 * 7)

        val result = WeekCalculator.currentWeek(termStart, today, totalWeeks = 20)

        assertNull("超出 totalWeeks 时应返回 null", result)
    }

    @Test
    fun `should return week when totalWeeks is zero or negative`() {
        // totalWeeks <= 0 时不做上界检查
        // 算出第 30 周也应返回 30
        val today = termStart.plusDays(29 * 7) // 第 30 周

        val resultWithZero = WeekCalculator.currentWeek(termStart, today, totalWeeks = 0)
        val resultWithNegative = WeekCalculator.currentWeek(termStart, today, totalWeeks = -1)

        assertEquals("totalWeeks=0 时不做上界检查", 30, resultWithZero)
        assertEquals("totalWeeks=-1 时不做上界检查", 30, resultWithNegative)
    }

    @Test
    fun `should return correct week on last day of last week`() {
        // 第 20 周的最后一天 = termStart + 20*7 - 1 天
        val today = termStart.plusDays(20 * 7 - 1)

        val result = WeekCalculator.currentWeek(termStart, today, totalWeeks = 20)

        assertEquals("第 20 周最后一天应返回 20", 20, result)
    }

    @Test
    fun `should return 1 when today equals termStart exactly`() {
        // 边界：today 与 termStart 同一天
        val result = WeekCalculator.currentWeek(termStart, termStart, totalWeeks = 20)

        assertEquals(1, result)
    }

    @Test
    fun `should handle very long term`() {
        // 一个 52 周的学期（一整年），验证大数值不会溢出或出错
        val longTermStart = LocalDate.of(2026, 1, 5) // 星期一
        val today = longTermStart.plusDays(51 * 7)     // 第 52 周

        val result = WeekCalculator.currentWeek(longTermStart, today, totalWeeks = 52)

        assertEquals(52, result)
    }
}
