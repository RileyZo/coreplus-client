package com.github.riley.coreplus.ui.schedule.table

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.riley.coreplus.R
import com.github.riley.coreplus.domain.model.Section
import com.github.riley.coreplus.domain.model.TimeTable
import com.github.riley.coreplus.domain.model.Weekday
import com.github.riley.coreplus.domain.schedule.CourseBlock
import com.github.riley.coreplus.domain.schedule.ScheduleGrid
import com.github.riley.coreplus.ui.common.display
import com.github.riley.coreplus.ui.theme.courseColor
import java.time.LocalDate
import java.time.LocalTime

/**
 * 课表的**卡片式布局**。
 *
 * 布局结构：
 * - 左上角：作息表切换按钮
 * - 列头：星期几 + 日期（仅显示日）
 * - 行头：小节号 + 开始时间 + 结束时间
 * - 表体：每个小节渲染一个占位卡片（灰色无课，有色有课）
 * - 午休/晚休提示：插入在时间间隙处
 *
 * @param grid 已算好时间与连课合并的表格数据
 * @param timeTable 当前作息表，用于获取所有小节的时间信息
 * @param weekStartDate 所显示那一周的星期一，用于表头标注日期与高亮今天；null 时不标注
 * @param today 今天的日期，用于高亮当天所在的列
 * @param showWeekend 是否显示周六、周日两列；false 时只显示周一至周五
 * @param seasonLabel 当前作息表季节标签，用于左上角切换按钮显示
 * @param onOpenTimeTable 点击作息表切换按钮的回调
 * @param onBlockClick 课程块点击回调
 */
@Composable
fun ScheduleTable(
    grid: ScheduleGrid,
    timeTable: TimeTable,
    modifier: Modifier = Modifier,
    weekStartDate: LocalDate? = null,
    today: LocalDate? = null,
    showWeekend: Boolean = true,
    seasonLabel: String = "",
    onOpenTimeTable: () -> Unit = {},
    onBlockClick: (CourseBlock) -> Unit = {},
) {
    val days = remember(showWeekend) {
        Weekday.entries.filter { showWeekend || it.value <= Weekday.FRIDAY.value }
    }
    val verticalScrollState = rememberScrollState()

    // 只有「今天确实落在正在显示那一周里」才高亮，否则回退显示上一周时会高亮错列
    val todayIndex = remember(weekStartDate, today) {
        when {
            weekStartDate == null || today == null -> null
            today.isBefore(weekStartDate) -> null
            today.isAfter(weekStartDate.plusDays(DAYS_PER_WEEK - 1L)) -> null
            else -> days.indexOfFirst { it.value == today.dayOfWeek.value }.takeIf { it >= 0 }
        }
    }

    // 获取所有小节（展开大节）
    val allSections = remember(timeTable) {
        timeTable.bigSections.flatMap { bigSection ->
            bigSection.sections.map { section ->
                SectionInfo(
                    bigSectionIndex = bigSection.index,
                    sectionIndex = section.index,
                    startTime = section.startTime,
                    endTime = section.endTime,
                )
            }
        }
    }

    // 固定的午休和晚休位置：午休在2-2和3-1之间，晚休在4-2和5-1之间
    val breakItems = remember(allSections) {
        fixedBreaks(allSections)
    }

    Column(modifier = modifier.fillMaxSize()) {
        // 表头区域：使用与表体相同的 Row 结构保证列对齐
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // 行头区域（对应 TimeRuler 的宽度）：放置作息表切换文字
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(RulerWidth)
                    .clickable(
                        enabled = true,
                        onClick = onOpenTimeTable
                    )
            ) {
                Text(
                    text = seasonLabel,
                    style = MaterialTheme.typography.labelMedium
                )
                Icon(
                    painter = painterResource(R.drawable.ic_caretdown),
                    contentDescription = stringResource(R.string.schedule_time_table),
                    modifier = Modifier.size(16.dp)
                )
            }

            // 列头：星期 + 日期
            Row(Modifier.weight(1f)) {
                days.forEach { day ->
                    WeekdayHeaderCell(
                        day = day,
                        date = weekStartDate?.plusDays((day.value - 1).toLong()),
                        today = today,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        // 表格主体（可滚动）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(verticalScrollState)
                .padding(vertical = RulerVerticalPadding),
        ) {
            // 行头：小节号 + 时间
            TimeRuler(
                sections = allSections,
                breakItems = breakItems,
                rowHeight = RowHeight,
                breakHeight = BreakHeight,
                modifier = Modifier.width(RulerWidth),
            )
            // 表格体：卡片式布局
            GridBody(
                grid = grid,
                days = days,
                sections = allSections,
                breakItems = breakItems,
                todayIndex = todayIndex,
                rowHeight = RowHeight,
                breakHeight = BreakHeight,
                modifier = Modifier.weight(1f),
                onBlockClick = onBlockClick,
            )
        }
    }
}

/**
 * 小节信息，用于渲染行头和表格体。
 */
private data class SectionInfo(
    val bigSectionIndex: Int,
    val sectionIndex: Int,
    val startTime: LocalTime,
    val endTime: LocalTime,
) {
    /** 小节编号，形如 "1-1"、"1-2"。 */
    val label: String get() = "$bigSectionIndex-$sectionIndex"
}

/**
 * 休息时间项。
 */
private data class BreakItem(
    val index: Int, // 在哪个小节之后插入
    val label: String, // 显示文本，如"午休"
)

/**
 * 固定的午休和晚休位置。
 *
 * 午休位于第2-2小节之后（即第2大节和第3大节之间）
 * 晚休位于第4-2小节之后（即第4大节和第5大节之间）
 */
private fun fixedBreaks(sections: List<SectionInfo>): List<BreakItem> {
    val breaks = mutableListOf<BreakItem>()
    
    // 查找2-2小节的位置
    sections.forEachIndexed { index, section ->
        if (section.bigSectionIndex == 2 && section.sectionIndex == 2) {
            breaks.add(BreakItem(index, "午休"))
        }
        if (section.bigSectionIndex == 4 && section.sectionIndex == 2) {
            breaks.add(BreakItem(index, "晚休"))
        }
    }
    
    return breaks
}

/**
 * 单个星期表头单元格。
 *
 * 有日期时在星期下面标出几号，并高亮今天；今天用主色加粗，周末用弱化色。
 * 宽度由调用方通过 `Modifier.weight(1f)` 给定，保证与下面的网格列严格等宽。
 */
@Composable
private fun WeekdayHeaderCell(
    day: Weekday,
    date: LocalDate?,
    today: LocalDate?,
    modifier: Modifier = Modifier,
) {
    val isToday = date != null && date == today
    val isWeekend = day.value >= Weekday.SATURDAY.value

    val color = when {
        isToday -> MaterialTheme.colorScheme.primary
        isWeekend -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurface
    }

    Column(
        modifier = modifier.padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = day.shortLabel,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
            color = color,
            maxLines = 1,
        )
        // 只显示「几号」：窄屏下列宽有限，完整日期放不下
        date?.let {
            Text(
                text = it.dayOfMonth.toString(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                color = color,
                maxLines = 1,
            )
        }
    }
}

/** 课程块在网格中的摆放信息，作为 `Modifier.layoutId` 传递。 */
private data class Placement(val dayIndex: Int, val sectionIndex: Int, val span: Int)

/** 休息项的摆放信息，作为 `Modifier.layoutId` 传递。 */
private data class BreakPlacement(val afterSectionIndex: Int)

/** 时间轴上的元素：[SectionLabel] 是小节编号和时间，[Break] 是休息提示。 */
private sealed interface RulerItem {
    data class SectionLabel(val index: Int) : RulerItem
    data class Break(val index: Int, val label: String) : RulerItem
}

/**
 * 左侧时间轴。
 *
 * 显示每个小节的编号和起止时间，以及在休息位置显示午休/晚休提示。
 */
@Composable
private fun TimeRuler(
    sections: List<SectionInfo>,
    breakItems: List<BreakItem>,
    rowHeight: Dp,
    breakHeight: Dp,
    modifier: Modifier = Modifier,
) {
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    val timeStyle = MaterialTheme.typography.labelSmall
    val labelStyle = MaterialTheme.typography.labelSmall
    val breakStyle = MaterialTheme.typography.labelSmall

    // 计算每个小节在 Y 轴上的位置（考虑休息项的高度）
    val density = LocalDensity.current
    val sectionYPositions = remember(sections, breakItems, rowHeight, breakHeight) {
        with(density) {
            val positions = IntArray(sections.size)
            var currentY = 0
            val breakIndices = breakItems.map { it.index }.toSet()

            for (i in sections.indices) {
                positions[i] = currentY
                currentY += rowHeight.toPx().toInt()
                // 如果当前位置后有休息项，加上休息项高度
                if (i in breakIndices) {
                    currentY += breakHeight.toPx().toInt()
                }
            }
            positions
        }
    }

    Layout(
        modifier = modifier,
        content = {
            sections.forEachIndexed { index, section ->
                Column(
                    modifier = Modifier.layoutId(RulerItem.SectionLabel(index)),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = section.label,
                        style = labelStyle,
                        color = textColor,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = "${section.startTime.display()}\n${section.endTime.display()}",
                        style = timeStyle,
                        color = textColor,
                        textAlign = TextAlign.Center,
                        lineHeight = timeStyle.fontSize,
                    )
                }
            }
            breakItems.forEach { breakItem ->
                Box(
                    modifier = Modifier
                        .layoutId(RulerItem.Break(breakItem.index, breakItem.label))
                        .fillMaxWidth()
                        .height(breakHeight),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = breakItem.label,
                        style = breakStyle,
                        color = textColor,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        },
    ) { measurables, constraints ->
        val rowHeightPx = rowHeight.roundToPx()
        val breakHeightPx = breakHeight.roundToPx()
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else 0

        val loose = Constraints(minWidth = 0, maxWidth = width, minHeight = 0, maxHeight = Constraints.Infinity)
        val placed = ArrayList<Pair<Any?, Placeable>>(measurables.size)
        measurables.forEach { measurable ->
            placed += measurable.layoutId to measurable.measure(loose)
        }

        // 计算总高度
        var calculatedHeight = 0
        val breakIndices = breakItems.map { it.index }.toSet()
        for (i in sections.indices) {
            calculatedHeight += rowHeightPx
            if (i in breakIndices) {
                calculatedHeight += breakHeightPx
            }
        }

        layout(width, calculatedHeight) {
            placed.forEach { (layoutId, placeable) ->
                val x = (width - placeable.width) / 2
                when (val item = layoutId) {
                    is RulerItem.SectionLabel -> {
                        val y = sectionYPositions[item.index]
                        placeable.placeRelative(x, y + (rowHeightPx - placeable.height) / 2)
                    }
                    is RulerItem.Break -> {
                        // 计算休息项的 Y 位置
                        var y = 0
                        for (i in 0..item.index) {
                            y += rowHeightPx
                            if (i in breakIndices && i < item.index) {
                                y += breakHeightPx
                            }
                        }
                        placeable.placeRelative(x, y)
                    }
                    null -> Unit
                }
            }
        }
    }
}

/**
 * 表格主体。
 *
 * 渲染策略：
 * - 课程起始小节：渲染一个跨多行的大卡片（高度 = span × 行高），无接缝
 * - 被课程覆盖的后续小节：不渲染（被起始卡片覆盖）
 * - 无课的小节：渲染灰色占位卡片
 */
@Composable
private fun GridBody(
    grid: ScheduleGrid,
    days: List<Weekday>,
    sections: List<SectionInfo>,
    breakItems: List<BreakItem>,
    todayIndex: Int?,
    rowHeight: Dp,
    breakHeight: Dp,
    modifier: Modifier = Modifier,
    onBlockClick: (CourseBlock) -> Unit,
) {
    val breakIndices = remember(breakItems) { breakItems.map { it.index }.toSet() }

    // 计算每个小节在 Y 轴上的位置（考虑休息项的高度）
    val density = LocalDensity.current
    val sectionYPositions = remember(sections, breakItems, rowHeight, breakHeight) {
        with(density) {
            val positions = IntArray(sections.size)
            var currentY = 0

            for (i in sections.indices) {
                positions[i] = currentY
                currentY += rowHeight.toPx().toInt()
                if (i in breakIndices) {
                    currentY += breakHeight.toPx().toInt()
                }
            }
            positions
        }
    }

    // 预计算每个 (dayIndex, sectionIndex) 的渲染信息
    val renderInfo = remember(grid, days, sections, breakIndices) {
        // courseStarts: (dayIndex, sectionIndex) -> CourseBlock，表示该位置是课程起始点
        val courseStarts = mutableMapOf<Pair<Int, Int>, CourseBlock>()
        // coveredSections: 被课程覆盖的 (dayIndex, sectionIndex) 集合
        val coveredSections = mutableSetOf<Pair<Int, Int>>()

        days.forEachIndexed { dayIndex, day ->
            sections.forEachIndexed { sectionIndex, _ ->
                val block = findCourseBlock(grid, day, sectionIndex, sections) ?: return@forEachIndexed

                // 找到该课程块起始小节在 sections 列表中的索引
                val startSectionIndex = findBigSectionStartIndex(
                    sections,
                    block.startBigSection,
                )
                if (startSectionIndex < 0) return@forEachIndexed

                // 只在起始位置处理，避免非起始小节重复标记导致覆盖范围超出课程实际跨度
                if (sectionIndex != startSectionIndex) return@forEachIndexed

                // 计算小节跨度：每个大节有 2 个小节
                val smallSectionSpan = block.span * SECTIONS_PER_BIG_SECTION

                courseStarts[dayIndex to sectionIndex] = block

                // 标记所有被覆盖的小节
                for (i in 0 until smallSectionSpan) {
                    coveredSections.add(dayIndex to (sectionIndex + i))
                }
            }
        }

        RenderInfo(courseStarts, coveredSections)
    }

    Layout(
        modifier = modifier,
        content = {
            sections.forEachIndexed { sectionIndex, _ ->
                days.forEachIndexed { dayIndex, _ ->
                    val key = dayIndex to sectionIndex

                    when {
                        key in renderInfo.courseStarts -> {
                            // 课程起始位置：渲染完整课程卡片
                            val block = renderInfo.courseStarts[key]!!
                            val smallSectionSpan = block.span * SECTIONS_PER_BIG_SECTION
                            CourseBlockCell(
                                block = block,
                                modifier = Modifier.layoutId(
                                    Placement(dayIndex, sectionIndex, smallSectionSpan),
                                ),
                                onClick = { onBlockClick(block) },
                            )
                        }
                        key !in renderInfo.coveredSections -> {
                            // 未被课程覆盖：渲染灰色占位卡片
                            EmptySlotCard(
                                modifier = Modifier.layoutId(
                                    Placement(dayIndex, sectionIndex, 1),
                                ),
                            )
                        }
                        // 被课程覆盖但非起始位置：不渲染（由起始卡片覆盖）
                    }
                }
            }

            // 休息项：单个横着的大卡片，跨越所有列
            breakItems.forEach { breakItem ->
                BreakCard(
                    label = breakItem.label,
                    modifier = Modifier.layoutId(BreakPlacement(breakItem.index)),
                )
            }
        },
    ) { measurables, constraints ->
        val rowHeightPx = rowHeight.roundToPx()
        val breakHeightPx = breakHeight.roundToPx()
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else 0
        val columnWidth = if (days.isEmpty()) 0 else width / days.size

        val placed = ArrayList<Pair<Any?, Placeable>>(measurables.size)
        measurables.forEach { measurable ->
            val layoutId = measurable.layoutId
            when (layoutId) {
                is Placement -> {
                    // 计算卡片高度：跨度 × 行高 + 中间休息项高度
                    val breakCount = countBreaksInRange(
                        layoutId.sectionIndex,
                        layoutId.span,
                        breakIndices,
                    )
                    val height = layoutId.span * rowHeightPx + breakCount * breakHeightPx
                    placed += layoutId to measurable.measure(
                        Constraints.fixed(columnWidth, height),
                    )
                }
                is BreakPlacement -> {
                    placed += layoutId to measurable.measure(
                        Constraints.fixed(width, breakHeightPx),
                    )
                }
                null -> Unit
            }
        }

        // 计算总高度
        var calculatedHeight = 0
        for (i in sections.indices) {
            calculatedHeight += rowHeightPx
            if (i in breakIndices) {
                calculatedHeight += breakHeightPx
            }
        }

        layout(width, calculatedHeight) {
            placed.forEach { (layoutId, placeable) ->
                when (layoutId) {
                    is Placement -> {
                        val y = sectionYPositions.getOrElse(layoutId.sectionIndex) { 0 }
                        placeable.placeRelative(columnWidth * layoutId.dayIndex, y)
                    }
                    is BreakPlacement -> {
                        var y = 0
                        for (i in 0..layoutId.afterSectionIndex) {
                            y += rowHeightPx
                            if (i in breakIndices && i < layoutId.afterSectionIndex) {
                                y += breakHeightPx
                            }
                        }
                        placeable.placeRelative(0, y)
                    }
                    null -> Unit
                }
            }
        }
    }
}

/** 每个大节包含的小节数。 */
private const val SECTIONS_PER_BIG_SECTION = 2

/** 渲染信息：课程起始位置和覆盖区域。 */
private data class RenderInfo(
    val courseStarts: Map<Pair<Int, Int>, CourseBlock>,
    val coveredSections: Set<Pair<Int, Int>>,
)

/**
 * 查找指定大节的首个小节在 sections 列表中的索引。
 *
 * 课程总是从大节的第 1 个小节开始，因此固定查找 sectionIndex == 1。
 *
 * @return 索引，未找到返回 -1
 */
private fun findBigSectionStartIndex(
    sections: List<SectionInfo>,
    bigSectionIndex: Int,
): Int {
    return sections.indexOfFirst {
        it.bigSectionIndex == bigSectionIndex && it.sectionIndex == 1
    }
}

/**
 * 计算从 startIndex 开始、跨度为 span 的范围内包含的休息项数量。
 */
private fun countBreaksInRange(
    startIndex: Int,
    span: Int,
    breakIndices: Set<Int>,
): Int {
    var count = 0
    for (i in startIndex until startIndex + span - 1) {
        if (i in breakIndices) count++
    }
    return count
}

/**
 * 查找指定位置的课程块。
 *
 * @param grid 表格数据
 * @param day 星期几
 * @param sectionIndex 小节索引（在所有小节列表中的索引）
 * @param sections 所有小节列表
 * @return 课程块，如果该位置没有课程则返回 null
 */
private fun findCourseBlock(
    grid: ScheduleGrid,
    day: Weekday,
    sectionIndex: Int,
    sections: List<SectionInfo>,
): CourseBlock? {
    val sectionInfo = sections.getOrNull(sectionIndex) ?: return null

    for (block in grid.blocks) {
        if (block.course.weekday != day) continue
        if (sectionInfo.bigSectionIndex in block.startBigSection..block.endBigSection) {
            return block
        }
    }
    return null
}

/**
 * 空的小节占位卡片（无课时显示灰色）。
 */
@Composable
private fun EmptySlotCard(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .padding(CellPadding)
            .clip(RoundedCornerShape(CellCorner))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
    )
}

/**
 * 休息项卡片（午休/晚休）。
 *
 * 横着的大卡片，跨越所有列，显示休息提示文字。
 */
@Composable
private fun BreakCard(
    label: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .padding(horizontal = CellPadding)
            .clip(RoundedCornerShape(CellCorner))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 块与相邻块之间的外边距，留出网格线的视觉空隙。 */
private val CellPadding = 1.5.dp

/** 圆角半径。 */
private val CellCorner = 4.dp

/** 一个大节行的高度。固定值，让时间轴与网格无需互相测量即可对齐。 */
private val RowHeight = 64.dp

/** 休息项的高度。 */
private val BreakHeight = 16.dp

/**
 * 左侧时间轴的宽度，需能容纳 "08:00" 这样的时刻文本。
 * 刻意压到最小，把宽度让给课程列。
 */
private val RulerWidth = 44.dp

/** 滚动内容的上下留白，避免首尾时间线上的文字被裁切。 */
private val RulerVerticalPadding = 10.dp

private const val DAYS_PER_WEEK = 7L
