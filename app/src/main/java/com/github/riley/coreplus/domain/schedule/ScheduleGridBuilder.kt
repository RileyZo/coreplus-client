package com.github.riley.coreplus.domain.schedule

import com.github.riley.coreplus.domain.model.BigSection
import com.github.riley.coreplus.domain.model.Course
import com.github.riley.coreplus.domain.model.TimeTable
import com.github.riley.coreplus.domain.model.Weekday
import com.github.riley.coreplus.domain.model.WeeklySchedule

/**
 * 把「某一周的课程列表 + 当前作息表」构建成 [ScheduleGrid]。
 *
 * 无状态纯函数，不依赖 Android，可单元测试。承担两件技术设计文档要求的事：
 *
 * 1. **实时时间计算**（§4）：块的起止时间取自 [TimeTable] 中大节的首末小节，不做缓存。
 *    作息表一改，重新 [build] 即可。
 * 2. **连课视觉合并**（§2.4）：数据层保持「一条记录 = 一个大节」，
 *    仅在此处把同一天相邻、且 [Course.isSameCourseAs] 成立的大节合并为一个跨行块。
 *
 * 大节序号超出作息表范围的课程会被忽略（见 `docs/API.md` §4.3 对 `bigSection` 上限的说明）。
 */
object ScheduleGridBuilder {

    /**
     * 构建表格数据。
     *
     * @param week 目标周；为 null（未缓存）或该周无课时，返回只有空行的表格
     * @param timeTable 当前生效的作息表
     */
    fun build(week: WeeklySchedule?, timeTable: TimeTable): ScheduleGrid {
        val bigSections = timeTable.bigSections.sortedBy { it.index }
        if (bigSections.isEmpty()) return ScheduleGrid(emptyList(), emptyList())

        // 大节序号 → 行下标
        val rowIndexOf = bigSections.withIndex().associate { (rowIndex, section) -> section.index to rowIndex }
        // 每行每天待填入的块
        val cells = List(bigSections.size) { mutableMapOf<Weekday, CourseBlock>() }
        val blocks = mutableListOf<CourseBlock>()

        for (weekday in Weekday.entries) {
            val byBigSection = week?.coursesOn(weekday)?.associateBy { it.bigSection }.orEmpty()
            // 只保留作息表里真实存在的大节，并按序号升序，便于向后贪婪扫描
            val sections = byBigSection.keys.filter { it in rowIndexOf }.sorted()

            var cursor = 0
            while (cursor < sections.size) {
                val startSection = sections[cursor]
                val course = byBigSection.getValue(startSection)
                val span = measureSpan(course, startSection, sections, byBigSection)

                buildBlock(course, startSection, span, timeTable)?.let { block ->
                    blocks += block
                    cells[rowIndexOf.getValue(startSection)][weekday] = block
                }
                cursor += span
            }
        }

        val rows = bigSections.mapIndexed { rowIndex, bigSection ->
            GridRow(bigSection, Weekday.entries.associateWith { day -> cells[rowIndex][day] })
        }
        return ScheduleGrid(rows = rows, blocks = blocks)
    }

    /**
     * 从 [startSection] 起，向后贪婪地数出可以合并的大节数。
     *
     * 合并条件（两者都必须满足）：
     * - 大节序号**连续**（`startSection + 已合并数`）
     * - 与起始课程 [Course.isSameCourseAs] 成立，即课程名、教室、教师三者全等
     *
     * @return 跨越的大节数，至少为 1
     */
    private fun measureSpan(
        course: Course,
        startSection: Int,
        sections: List<Int>,
        byBigSection: Map<Int, Course>,
    ): Int {
        var span = 1
        while (true) {
            val nextSection = startSection + span
            // 大节号必须在「今天实际有课的序号」里，保证连续性而非仅仅数字相邻
            if (sections.binarySearch(nextSection) < 0) break
            val next = byBigSection[nextSection] ?: break
            if (!next.isSameCourseAs(course)) break
            span++
        }
        return span
    }

    /**
     * 生成一个块；起始大节不在作息表中时返回 null（该课程被忽略）。
     *
     * 起止时间 = 起始大节首小节开始 → 末尾大节末小节结束，
     * 即技术设计文档 §4 要求的「整个大节的起止时间，不区分课间」。
     */
    private fun buildBlock(
        course: Course,
        startSection: Int,
        span: Int,
        timeTable: TimeTable,
    ): CourseBlock? {
        val start: BigSection = timeTable.bigSection(startSection) ?: return null
        val end: BigSection = timeTable.bigSection(startSection + span - 1) ?: start

        val labels = (0 until span).flatMap { offset ->
            timeTable.bigSection(startSection + offset)?.let { big ->
                big.sections.map { big.sectionNumber(it) }
            }.orEmpty()
        }

        return CourseBlock(
            course = course,
            startBigSection = startSection,
            span = span,
            startTime = start.startTime,
            endTime = end.endTime,
            sectionLabels = labels,
        )
    }
}
