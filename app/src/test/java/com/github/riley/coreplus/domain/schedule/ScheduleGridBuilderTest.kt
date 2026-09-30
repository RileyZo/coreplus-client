package com.github.riley.coreplus.domain.schedule

import com.github.riley.coreplus.domain.model.BigSection
import com.github.riley.coreplus.domain.model.Course
import com.github.riley.coreplus.domain.model.Section
import com.github.riley.coreplus.domain.model.TimeTable
import com.github.riley.coreplus.domain.model.TimeTableDefaults
import com.github.riley.coreplus.domain.model.TimeTableSeason
import com.github.riley.coreplus.domain.model.Weekday
import com.github.riley.coreplus.domain.model.WeeklySchedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

/**
 * [ScheduleGridBuilder.build] 的单元测试。
 *
 * 聚焦于连课合并算法与异常输入的处理，不依赖任何 Android 组件。
 *
 * 所有测试用例共享夏季默认作息表（5 个大节、每大节 2 小节），以贴近真实使用场景。
 */
class ScheduleGridBuilderTest {

    /** 夏季默认作息表：5 个大节、每节 2 小节。 */
    private val summer = TimeTableDefaults.summer

    /** 构造一条 Course 的快捷方法，默认所有三元组相同以便测试合并逻辑。 */
    private fun course(
        weekday: Weekday,
        bigSection: Int,
        name: String = "高等数学",
        location: String = "主楼301",
        teacher: String = "赵六",
        week: Int = 1,
        id: String = "c-${weekday.value}-${bigSection}-${name.hashCode()}",
    ) = Course(
        id = id,
        name = name,
        credit = 3.0,
        attribute = "必修",
        location = location,
        teacher = teacher,
        week = week,
        weekday = weekday,
        bigSection = bigSection,
    )

    @Test
    fun `should merge two consecutive big sections of same course into one block with span 2`() {
        // 周一第 1、2 大节为同一门课 → 合并成一个 span=2 的块
        val week = WeeklySchedule(
            weekNumber = 1,
            courses = listOf(
                course(Weekday.MONDAY, 1),
                course(Weekday.MONDAY, 2),
            ),
        )

        val grid = ScheduleGridBuilder.build(week, summer)

        // 只有一个块（合并后），span=2
        assertEquals("应只有一个合并块", 1, grid.blocks.size)
        val block = grid.blocks[0]
        assertEquals(1, block.startBigSection)
        assertEquals(2, block.span)
        // 起止时间：起始大节首小节开始 → 末尾大节末小节结束
        assertEquals(summer.bigSection(1)!!.startTime, block.startTime)
        assertEquals(summer.bigSection(2)!!.endTime, block.endTime)
        // 小节编号：4 个（每大节 2 小节 × 2 大节）
        assertEquals(4, block.sectionLabels.size)
        assertEquals("1-1", block.sectionLabels[0])
        assertEquals("2-2", block.sectionLabels[3])
        // 该块应出现在 grid 中
        assertFalse("grid 不应为空", grid.isEmpty)
    }

    @Test
    fun `should merge four consecutive big sections into one block with span 4`() {
        // 周二第 1、2、3、4 大节为同一门课（四小节连课）→ 合并成一个 span=4 的块
        val week = WeeklySchedule(
            weekNumber = 1,
            courses = listOf(
                course(Weekday.TUESDAY, 1),
                course(Weekday.TUESDAY, 2),
                course(Weekday.TUESDAY, 3),
                course(Weekday.TUESDAY, 4),
            ),
        )

        val grid = ScheduleGridBuilder.build(week, summer)

        assertEquals(1, grid.blocks.size)
        val block = grid.blocks[0]
        assertEquals(1, block.startBigSection)
        assertEquals(4, block.span)
        assertEquals(summer.bigSection(1)!!.startTime, block.startTime)
        assertEquals(summer.bigSection(4)!!.endTime, block.endTime)
        // 4 大节 × 2 小节 = 8 个小节标签
        assertEquals(8, block.sectionLabels.size)
    }

    @Test
    fun `should not merge when teacher is different`() {
        // 同一天相邻大节、同课程名、同教室，但教师不同 → 不合并
        val week = WeeklySchedule(
            weekNumber = 1,
            courses = listOf(
                course(Weekday.WEDNESDAY, 1, teacher = "赵六"),
                course(Weekday.WEDNESDAY, 2, teacher = "钱七", id = "c-2-different-teacher"),
            ),
        )

        val grid = ScheduleGridBuilder.build(week, summer)

        assertEquals("三元组中教师不同时不应合并", 2, grid.blocks.size)
        assertTrue("两个块 span 均为 1", grid.blocks.all { it.span == 1 })
    }

    @Test
    fun `should not merge when location is different`() {
        // 同一天相邻大节、同课程名、同教师，但教室不同 → 不合并
        val week = WeeklySchedule(
            weekNumber = 1,
            courses = listOf(
                course(Weekday.THURSDAY, 1, location = "致远楼605"),
                course(Weekday.THURSDAY, 2, location = "致远楼603", id = "c-2-diff-location"),
            ),
        )

        val grid = ScheduleGridBuilder.build(week, summer)

        assertEquals(2, grid.blocks.size)
        assertTrue(grid.blocks.all { it.span == 1 })
    }

    @Test
    fun `should not merge when course name is different`() {
        val week = WeeklySchedule(
            weekNumber = 1,
            courses = listOf(
                course(Weekday.FRIDAY, 1, name = "高等数学"),
                course(Weekday.FRIDAY, 2, name = "线性代数", id = "c-2-diff-name"),
            ),
        )

        val grid = ScheduleGridBuilder.build(week, summer)

        assertEquals(2, grid.blocks.size)
        assertTrue(grid.blocks.all { it.span == 1 })
    }

    @Test
    fun `should not merge non-consecutive big sections`() {
        // 周一第 1 大节和第 3 大节为同一门课，但中间隔了第 2 大节（空或不同课）→ 不合并
        val week = WeeklySchedule(
            weekNumber = 1,
            courses = listOf(
                course(Weekday.MONDAY, 1),
                course(Weekday.MONDAY, 3, id = "c-3-same-course"),
            ),
        )

        val grid = ScheduleGridBuilder.build(week, summer)

        assertEquals("大节号不连续时不应合并", 2, grid.blocks.size)
        assertTrue(grid.blocks.all { it.span == 1 })
    }

    @Test
    fun `should not merge across different weekdays`() {
        // 周一第 5 大节和周二第 1 大节为同一门课 → 不合并（跨天不合并）
        val week = WeeklySchedule(
            weekNumber = 1,
            courses = listOf(
                course(Weekday.MONDAY, 5),
                course(Weekday.TUESDAY, 1, id = "c-tue-1-same-course"),
            ),
        )

        val grid = ScheduleGridBuilder.build(week, summer)

        assertEquals("跨天的同课不应合并", 2, grid.blocks.size)
        assertTrue(grid.blocks.all { it.span == 1 })
    }

    @Test
    fun `should return empty blocks but keep rows when week is null`() {
        // week 为 null → blocks 为空、rows 仍等于作息表大节数
        val grid = ScheduleGridBuilder.build(week = null, timeTable = summer)

        assertTrue("week 为 null 时 blocks 应为空", grid.blocks.isEmpty())
        assertEquals(
            "rows 应等于作息表大节数",
            summer.bigSections.size,
            grid.rows.size,
        )
        assertTrue("grid.isEmpty 应为 true", grid.isEmpty)
    }

    @Test
    fun `should return empty blocks when courses list is empty`() {
        val week = WeeklySchedule(weekNumber = 1, courses = emptyList())

        val grid = ScheduleGridBuilder.build(week, summer)

        assertTrue(grid.blocks.isEmpty())
        assertEquals(summer.bigSections.size, grid.rows.size)
    }

    @Test
    fun `should ignore courses whose bigSection exceeds timetable range`() {
        // 夏季作息表只有 5 个大节，第 6 大节的课程应被忽略
        val week = WeeklySchedule(
            weekNumber = 1,
            courses = listOf(
                course(Weekday.MONDAY, 1),
                course(Weekday.MONDAY, 6, id = "c-beyond-range"),  // 超出范围
            ),
        )

        val grid = ScheduleGridBuilder.build(week, summer)

        assertEquals("超出作息表大节范围的课程应被忽略", 1, grid.blocks.size)
        assertEquals(1, grid.blocks[0].startBigSection)
    }

    @Test
    fun `should deduplicate when same weekday and bigSection appear multiple times`() {
        // 同一 (weekday, bigSection) 出现两条记录 → 只保留一条
        // 注意：Kotlin 的 associateBy 对重复键保留最后一条。
        // API.md §4.3 规则 2 声明「同一格子只应有一条记录」，后端不应下发重复记录，
        // 因此此处仅验证去重行为，不强制要求保留第几条。
        val first = course(Weekday.MONDAY, 1, name = "第一门课", id = "first")
        val duplicate = course(Weekday.MONDAY, 1, name = "重复课", id = "duplicate")
        val week = WeeklySchedule(
            weekNumber = 1,
            courses = listOf(first, duplicate),
        )

        val grid = ScheduleGridBuilder.build(week, summer)

        assertEquals("同一格应只保留一条记录（去重）", 1, grid.blocks.size)
    }

    @Test
    fun `should return empty grid when timetable has no big sections`() {
        // 空作息表（没有大节）→ rows 和 blocks 都为空
        val emptyTimeTable = TimeTable(
            season = TimeTableSeason.SUMMER,
            bigSections = emptyList(),
        )
        val week = WeeklySchedule(
            weekNumber = 1,
            courses = listOf(course(Weekday.MONDAY, 1)),
        )

        val grid = ScheduleGridBuilder.build(week, emptyTimeTable)

        assertTrue("空作息表时 rows 应为空", grid.rows.isEmpty())
        assertTrue("空作息表时 blocks 应为空", grid.blocks.isEmpty())
    }

    @Test
    fun `should correctly compute sectionLabels for span 2 block`() {
        // 合并块的 sectionLabels 应包含所有小节编号
        val week = WeeklySchedule(
            weekNumber = 1,
            courses = listOf(
                course(Weekday.MONDAY, 1),
                course(Weekday.MONDAY, 2),
            ),
        )

        val grid = ScheduleGridBuilder.build(week, summer)
        val block = grid.blocks[0]

        // 大节 1 有 1-1、1-2，大节 2 有 2-1、2-2
        assertEquals(listOf("1-1", "1-2", "2-1", "2-2"), block.sectionLabels)
    }

    @Test
    fun `should correctly compute time range for merged block`() {
        // 合并块的 startTime 应等于起始大节首小节的 startTime，
        // endTime 应等于末尾大节末小节的 endTime
        val week = WeeklySchedule(
            weekNumber = 1,
            courses = listOf(
                course(Weekday.TUESDAY, 3),
                course(Weekday.TUESDAY, 4),
            ),
        )

        val grid = ScheduleGridBuilder.build(week, summer)
        val block = grid.blocks[0]

        assertEquals(
            "startTime 应为第 3 大节首小节开始时间",
            summer.bigSection(3)!!.startTime,
            block.startTime,
        )
        assertEquals(
            "endTime 应为第 4 大节末小节结束时间",
            summer.bigSection(4)!!.endTime,
            block.endTime,
        )
    }
}
