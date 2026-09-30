package com.github.riley.coreplus.domain.term

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * [TermStartInference] 的单元测试。
 *
 * 覆盖自动推断的边界：秋/春季学期切换、寒假容差、开学前提前识别、3 月 1 日恰逢星期日等。
 */
class TermStartInferenceTest {

    @Test
    fun `infer on 2026-09-29 should return 2026-08-31`() {
        // 2026-09-01 是星期二 → 所在周星期一是 2026-08-31
        val today = LocalDate.of(2026, 9, 29)

        val result = TermStartInference.infer(today)

        assertEquals(LocalDate.of(2026, 8, 31), result)
    }

    @Test
    fun `infer on 2027-04-15 should return spring term start`() {
        // 2027-03-01 是星期一 → 所在周星期一就是 2027-03-01
        val today = LocalDate.of(2027, 4, 15)

        val result = TermStartInference.infer(today)

        assertEquals(LocalDate.of(2027, 3, 1), result)
    }

    @Test
    fun `infer during winter break should return previous autumn term`() {
        // 2027-01-15 在寒假期间 → 仍归上学期（2026-08-31 起始的秋季学期）
        val today = LocalDate.of(2027, 1, 15)

        val result = TermStartInference.infer(today)

        assertEquals(LocalDate.of(2026, 8, 31), result)
    }

    @Test
    fun `infer on 2026-08-27 should identify new semester within tolerance`() {
        // 2026-08-27 + 7 天容差 = 2026-09-03，覆盖 2026-09-01 所在周的星期一（2026-08-31）
        // → 提前识别新学期
        val today = LocalDate.of(2026, 8, 27)

        val result = TermStartInference.infer(today)

        assertEquals("开学前容差内应提前识别新学期", LocalDate.of(2026, 8, 31), result)
    }

    @Test
    fun `infer on 2026-08-20 should not identify new semester beyond tolerance`() {
        // 2026-08-20 + 7 天容差 = 2026-08-27，不覆盖 2026-08-31
        // → 仍归春季学期（2026-02-23 起始）
        val today = LocalDate.of(2026, 8, 20)

        val result = TermStartInference.infer(today)

        // 2026-02-23 是 2026-03-01 所在周的星期一（2026-03-01 是星期日）
        // 验证：2026-03-01 是星期日 → 所在周星期一 = 2026-02-23
        assertEquals("容差外不应提前识别新学期", LocalDate.of(2026, 2, 23), result)
    }

    @Test
    fun `mondayOf on Sunday should return previous Monday`() {
        // 2026-03-01 是星期日 → 所在周星期一是 2026-02-23
        val sunday = LocalDate.of(2026, 3, 1)

        val result = TermStartInference.mondayOf(sunday)

        assertEquals(LocalDate.of(2026, 2, 23), result)
    }

    @Test
    fun `mondayOf on Monday should return same date`() {
        // 星期一本身 → 返回原值
        val monday = LocalDate.of(2026, 8, 31)

        val result = TermStartInference.mondayOf(monday)

        assertEquals(monday, result)
    }

    @Test
    fun `isMonday on Monday should return true`() {
        assertTrue(TermStartInference.isMonday(LocalDate.of(2026, 8, 31)))
    }

    @Test
    fun `isMonday on non-Monday should return false`() {
        // 星期二到星期日均应返回 false
        assertFalse(TermStartInference.isMonday(LocalDate.of(2026, 9, 1)))   // 星期二
        assertFalse(TermStartInference.isMonday(LocalDate.of(2026, 9, 2)))   // 星期三
        assertFalse(TermStartInference.isMonday(LocalDate.of(2026, 9, 3)))   // 星期四
        assertFalse(TermStartInference.isMonday(LocalDate.of(2026, 9, 4)))   // 星期五
        assertFalse(TermStartInference.isMonday(LocalDate.of(2026, 9, 5)))   // 星期六
        assertFalse(TermStartInference.isMonday(LocalDate.of(2026, 9, 6)))   // 星期日
    }

    @Test
    fun `infer should always return a Monday`() {
        // 测试连续 365 天，验证 infer 始终返回星期一
        val start = LocalDate.of(2026, 1, 1)
        for (dayOffset in 0..364) {
            val today = start.plusDays(dayOffset.toLong())
            val result = TermStartInference.infer(today)
            assertTrue(
                "infer($today) 返回 $result，不是星期一",
                TermStartInference.isMonday(result),
            )
        }
    }
}
