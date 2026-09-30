package com.github.riley.coreplus.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [Schedule.merge] 与部分成功语义的单元测试。
 *
 * 技术设计文档 §3 规定：
 * - 响应中出现的周覆盖本地同周数据
 * - 响应中未出现的周保留本地缓存
 * - `totalWeeks` 一律取新值
 * - 周次超出新 `totalWeeks` 的本地数据被丢弃
 */
class ScheduleMergeTest {

    /** 构造一条测试用课程。 */
    private fun course(name: String, weekday: Weekday, bigSection: Int, week: Int) = Course(
        id = "test-$week-${weekday.value}-$bigSection",
        name = name,
        credit = 3.0,
        attribute = "必修",
        location = "主楼101",
        teacher = "老师",
        week = week,
        weekday = weekday,
        bigSection = bigSection,
    )

    /** 构造一个有 [count] 门课的周。 */
    private fun week(weekNumber: Int, count: Int = 2) = WeeklySchedule(
        weekNumber = weekNumber,
        courses = (1..count).map { i ->
            course("课程$i", Weekday.MONDAY, i, weekNumber)
        },
    )

    @Test
    fun `merge should add new weeks from response while keeping existing ones`() {
        // 本地有周 1、3；other 只含周 2（totalWeeks=4）
        // → 合并后有周 1、2、3，缺失周 4
        val local = Schedule(
            totalWeeks = 4,
            weeks = listOf(week(1), week(3)),
        )
        val fetched = Schedule(
            totalWeeks = 4,
            weeks = listOf(week(2)),
        )

        val merged = local.merge(fetched)

        assertEquals("totalWeeks 应取 other 的值", 4, merged.totalWeeks)
        assertEquals("合并后应有 3 周", 3, merged.weeks.size)
        assertNotNull("周 1 应保留", merged.week(1))
        assertNotNull("周 2 应新增", merged.week(2))
        assertNotNull("周 3 应保留", merged.week(3))
        assertNull("周 4 应缺失", merged.week(4))
        assertEquals("缺失周次应为 [4]", listOf(4), merged.missingWeekNumbers)
    }

    @Test
    fun `merge should overwrite existing week when it appears in response`() {
        // 本地周 1 有 2 门课；other 的周 1 有 3 门课
        // → 合并后周 1 应为 other 的内容（3 门课）
        val local = Schedule(
            totalWeeks = 2,
            weeks = listOf(week(1, count = 2)),
        )
        val fetched = Schedule(
            totalWeeks = 2,
            weeks = listOf(week(1, count = 3)),
        )

        val merged = local.merge(fetched)

        val week1 = merged.week(1)!!
        assertEquals("周 1 应被覆盖为 other 的内容", 3, week1.courses.size)
    }

    @Test
    fun `merge with empty weeks should keep all local data and only update totalWeeks`() {
        // other.weeks 为空 → 本地缓存完整保留，只更新 totalWeeks
        val local = Schedule(
            totalWeeks = 5,
            weeks = listOf(week(1), week(3), week(5)),
        )
        val fetched = Schedule(totalWeeks = 10, weeks = emptyList())

        val merged = local.merge(fetched)

        assertEquals("totalWeeks 应更新为 other 的值", 10, merged.totalWeeks)
        assertEquals("本地 3 周数据应完整保留", 3, merged.weeks.size)
        assertNotNull(merged.week(1))
        assertNotNull(merged.week(3))
        assertNotNull(merged.week(5))
    }

    @Test
    fun `merge should trim local weeks exceeding new totalWeeks`() {
        // 本地有周 25（旧学期），other.totalWeeks = 20
        // → 周 25 应被裁剪掉
        val local = Schedule(
            totalWeeks = 25,
            weeks = listOf(week(1), week(25)),
        )
        val fetched = Schedule(
            totalWeeks = 20,
            weeks = listOf(week(2)),
        )

        val merged = local.merge(fetched)

        assertEquals(20, merged.totalWeeks)
        assertNotNull("周 1 应保留", merged.week(1))
        assertNotNull("周 2 应新增", merged.week(2))
        assertNull("超出 new totalWeeks 的周 25 应被裁剪", merged.week(25))
    }

    @Test
    fun `totalWeeks should always be taken from other`() {
        // 无论 local 的 totalWeeks 是多少，合并后一律用 other 的
        val local = Schedule(totalWeeks = 99, weeks = emptyList())
        val fetched = Schedule(totalWeeks = 18, weeks = listOf(week(1)))

        val merged = local.merge(fetched)

        assertEquals("totalWeeks 一律取 other 的值", 18, merged.totalWeeks)
    }

    @Test
    fun `missingWeekNumbers should list all gaps from 1 to totalWeeks`() {
        val schedule = Schedule(
            totalWeeks = 5,
            weeks = listOf(week(2), week(4)),
        )

        val missing = schedule.missingWeekNumbers

        assertEquals("缺失周次应为 [1, 3, 5]", listOf(1, 3, 5), missing)
    }

    @Test
    fun `missingWeekNumbers should be empty when all weeks are cached`() {
        val schedule = Schedule(
            totalWeeks = 3,
            weeks = listOf(week(1), week(2), week(3)),
        )

        val missing = schedule.missingWeekNumbers

        assertTrue("全部周已缓存时 missingWeekNumbers 应为空", missing.isEmpty())
    }

    @Test
    fun `knownWeekNumbers should return set of cached week numbers`() {
        val schedule = Schedule(
            totalWeeks = 10,
            weeks = listOf(week(1), week(5), week(10)),
        )

        val known = schedule.knownWeekNumbers

        assertEquals(setOf(1, 5, 10), known)
    }

    @Test
    fun `week should return null for uncached week`() {
        val schedule = Schedule(
            totalWeeks = 5,
            weeks = listOf(week(1), week(3)),
        )

        assertNull("未缓存的周 2 应返回 null", schedule.week(2))
        assertNull("未缓存的周 4 应返回 null", schedule.week(4))
        assertNotNull("已缓存的周 1 应非 null", schedule.week(1))
    }

    @Test
    fun `WeeklySchedule withoutCourse should remove matching course by id`() {
        val c1 = course("课1", Weekday.MONDAY, 1, week = 1)
        val c2 = course("课2", Weekday.MONDAY, 2, week = 1)
        val original = WeeklySchedule(weekNumber = 1, courses = listOf(c1, c2))

        val result = original.withoutCourse(c1)

        assertEquals(1, result.courses.size)
        assertEquals("课2", result.courses[0].name)
    }

    @Test
    fun `merge with partial success should correctly compute missingWeekNumbers`() {
        // 完整的部分成功场景：
        // 本地有周 1、2（旧数据）
        // 本次拉取到周 1（覆盖）、周 4（新增）
        // totalWeeks = 6
        val local = Schedule(
            totalWeeks = 5,
            weeks = listOf(week(1), week(2)),
        )
        val fetched = Schedule(
            totalWeeks = 6,
            weeks = listOf(week(1), week(4)),
        )

        val merged = local.merge(fetched)

        assertEquals(6, merged.totalWeeks)
        // 合并后应有周 1（覆盖）、周 2（保留）、周 4（新增）
        assertEquals(3, merged.weeks.size)
        assertNotNull(merged.week(1))
        assertNotNull(merged.week(2))
        assertNotNull(merged.week(4))
        // 缺失周次：3、5、6
        assertEquals(listOf(3, 5, 6), merged.missingWeekNumbers)
    }
}
