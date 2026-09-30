package com.github.riley.coreplus.domain.schedule

import com.github.riley.coreplus.domain.model.BigSection
import com.github.riley.coreplus.domain.model.Course
import com.github.riley.coreplus.domain.model.Weekday
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * 表格布局渲染所需的**已计算好**的中间模型。
 *
 * 由 [ScheduleGridBuilder] 从「某一周的课程列表 + 当前作息表」实时推导，**不落盘**——
 * 这实现了技术设计文档 §4「不缓存计算结果，根据当前作息表实时计算」：
 * 用户切换夏/冬作息表后，重新 build 一次即可得到新的时间。
 */

/**
 * 一个课程块：表格里的一个可点击矩形。
 *
 * 连课（同一天相邻大节且属于同一门课）会被合并成一个块，此时 [span] 大于 1。
 *
 * @property course 该块对应的课程。合并块取**起始大节**的那条记录
 * @property startBigSection 起始大节序号，决定块的纵向位置
 * @property span 纵向跨越的大节数，≥ 1
 * @property startTime 起始大节的首小节开始时间
 * @property endTime 末尾大节的末小节结束时间
 * @property sectionLabels 覆盖到的小节编号，形如 `["1-1", "1-2", "2-1", "2-2"]`
 */
data class CourseBlock(
    val course: Course,
    val startBigSection: Int,
    val span: Int,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val sectionLabels: List<String>,
) {
    /** 结束大节序号。 */
    val endBigSection: Int get() = startBigSection + span - 1

    /** 是否为连课合并块。 */
    val isMerged: Boolean get() = span > 1

    /** 起止时间文本，如 `"08:00–09:40"`。 */
    val timeRangeText: String get() = "${format(startTime)}–${format(endTime)}"

    /** 大节范围文本，如 `"1-2节"` 或 `"3节"`。 */
    val bigSectionText: String
        get() = if (isMerged) "${startBigSection}-${endBigSection}节" else "${startBigSection}节"

    companion object {
        /** 时间显示格式，只到分钟。 */
        val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

        /** 把 [LocalTime] 格式化为 `HH:mm`。 */
        fun format(time: LocalTime): String = time.format(TIME_FORMAT)
    }
}

/**
 * 表格的一行，对应一个大节。
 *
 * @property bigSection 该行对应的大节，提供起止时间与小节划分
 * @property cells 七天各自的块。为 null 表示该格无课，**或**该格被上方跨行块覆盖
 *   （被覆盖的格子不会重复持有块，因此渲染时不会画出重叠矩形）
 */
data class GridRow(
    val bigSection: BigSection,
    val cells: Map<Weekday, CourseBlock?>,
) {
    /** 该行是否完全没有内容，用于决定是否隐藏（例如空白的第 5 大节）。 */
    val isEmpty: Boolean get() = cells.values.all { it == null }
}

/**
 * 一整张表格。
 *
 * @property rows 所有行，按大节序号升序
 * @property blocks 所有块的扁平列表，与 [rows] 中的内容等价。
 *   自定义 `Layout` 直接遍历本列表来测量与摆放，比逐行扫描更方便
 */
data class ScheduleGrid(
    val rows: List<GridRow>,
    val blocks: List<CourseBlock>,
) {
    /** 本周是否没有任何课程。 */
    val isEmpty: Boolean get() = blocks.isEmpty()

    /** 行数，等于作息表中的大节数。 */
    val rowCount: Int get() = rows.size

    companion object {
        /** 空表格：有行但没有任何块，用于「该周无课」或「该周未缓存」。 */
        fun empty(bigSections: List<BigSection>): ScheduleGrid = ScheduleGrid(
            rows = bigSections.map { GridRow(it, Weekday.entries.associateWith { null }) },
            blocks = emptyList(),
        )
    }
}
