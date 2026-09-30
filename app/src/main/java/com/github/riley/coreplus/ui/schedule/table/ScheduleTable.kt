package com.github.riley.coreplus.ui.schedule.table

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.riley.coreplus.domain.model.Weekday
import com.github.riley.coreplus.domain.schedule.CourseBlock
import com.github.riley.coreplus.domain.schedule.ScheduleGrid
import com.github.riley.coreplus.ui.common.display
import com.github.riley.coreplus.ui.theme.gridLineColor
import com.github.riley.coreplus.ui.theme.todayHighlightColor
import java.time.LocalDate

/**
 * 课表的**表格布局**（技术设计文档 §6.2）。
 *
 * 二维网格：列是星期几，行是大节。三个部分：
 * - 顶部星期表头：固定不随内容纵向滚动，但随内容**横向**同步滚动
 * - [TimeRuler]：左侧行头，「关键时间线处显示时间；两根时间线中间显示节次编号（1-1、1-2…）」，
 *   横向固定、纵向随内容滚动
 * - [GridBody]：课程块区域，跨行的连课块由 [ScheduleGrid] 里的 [CourseBlock.span] 决定
 *
 * ## 为什么用自定义 `Layout` 而不是 `Column` 套 `Row`
 *
 * 连课合并块要跨越多行。用嵌套的 Row/Column 无法让一个子元素跨越兄弟行，
 * 只能靠 `Modifier.height(span * rowHeight)` + 绝对偏移硬凑，代码反而更难懂。
 * 自定义 `Layout` 直接决定每个块的 `(x, y, width, height)`，跨行是天然结果。
 *
 * ## 为什么行高固定
 *
 * 固定 [RowHeight] 让时间轴与网格**不需要互相测量**就能对齐：
 * 两者都用 `rowHeight * rowCount` 算总高，第 n 行的 y 都是 `rowHeight * (n - 1)`。
 * 若行高自适应内容，就得把测量结果在两个组件间来回传递，复杂度陡增且没有收益——
 * 课表本来就是等高的表格。
 *
 * ## 窄屏横向滚动
 *
 * 7 列 + 时间轴在 360dp 宽的手机上每列只剩约 45dp，中文课程名会被截到一两个字。
 * 因此当可用宽度不足以给每列 [MinColumnWidth] 时，网格改为横向滚动
 * （表头与网格共用同一个横向 `ScrollState`，保持同步）；
 * 宽屏（平板、横屏）则正好铺满，不出现滚动。
 *
 * ## 网格线用 `drawBehind` 画
 *
 * 而不是给每个格子加 `border`：N 行 × 7 列会产生大量重复边框（相邻格子各画一条，
 * 视觉上变成 2dp 粗线），且每个块都多一层布局节点。一次 `drawBehind` 画完全部线，零额外节点。
 *
 * @param grid 已算好时间与连课合并的表格数据
 * @param weekStartDate 所显示那一周的星期一，用于表头标注日期与高亮今天；null 时不标注
 * @param today 今天的日期，用于高亮当天所在的列
 */
@Composable
fun ScheduleTable(
    grid: ScheduleGrid,
    modifier: Modifier = Modifier,
    weekStartDate: LocalDate? = null,
    today: LocalDate? = null,
    onBlockClick: (CourseBlock) -> Unit = {},
) {
    val days = remember { Weekday.entries.toList() }
    val verticalScrollState = rememberScrollState()
    val horizontalScrollState = rememberScrollState()

    // 只有「今天确实落在正在显示的这一周里」才高亮，否则回退显示上一周时会高亮错列
    val todayIndex = remember(weekStartDate, today) {
        when {
            weekStartDate == null || today == null -> null
            today.isBefore(weekStartDate) -> null
            today.isAfter(weekStartDate.plusDays(DAYS_PER_WEEK - 1L)) -> null
            else -> days.indexOfFirst { it.value == today.dayOfWeek.value }.takeIf { it >= 0 }
        }
    }

    BoxWithConstraints(modifier) {
        // 去掉时间轴后剩下的宽度，才是 7 列能分到的
        val availableForColumns = (maxWidth - RulerWidth).coerceAtLeast(0.dp)
        val columnWidth = maxOf(availableForColumns / days.size, MinColumnWidth)
        val gridWidth = columnWidth * days.size

        Column(Modifier.fillMaxSize()) {
            // 表头：左侧留白不滚动，七天表头与网格同步横向滚动
            Row(Modifier.fillMaxWidth()) {
                Spacer(Modifier.width(RulerWidth))
                Row(Modifier.horizontalScroll(horizontalScrollState)) {
                    days.forEach { day ->
                        WeekdayHeaderCell(
                            day = day,
                            date = weekStartDate?.plusDays((day.value - 1).toLong()),
                            today = today,
                            modifier = Modifier.width(columnWidth),
                        )
                    }
                }
            }
            HorizontalDivider()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(verticalScrollState)
                    // 上下留白，让首尾两条时间线上的文字不被滚动容器裁掉
                    .padding(vertical = RulerVerticalPadding),
            ) {
                TimeRuler(grid = grid, rowHeight = RowHeight, modifier = Modifier.width(RulerWidth))
                Row(Modifier.horizontalScroll(horizontalScrollState)) {
                    GridBody(
                        grid = grid,
                        days = days,
                        todayIndex = todayIndex,
                        rowHeight = RowHeight,
                        modifier = Modifier.width(gridWidth),
                        onBlockClick = onBlockClick,
                    )
                }
            }
        }
    }
}

/**
 * 单个星期表头单元格。
 *
 * 有日期时在星期下面标出几号，并高亮今天；今天用主色加粗，周末用弱化色。
 * 宽度由调用方给定（[width]），保证与下面的网格列严格等宽。
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
private data class Placement(val dayIndex: Int, val rowIndex: Int, val span: Int)

/** 时间轴上的元素：[Time] 是画在网格线上的时刻，[Label] 是画在两线中间的小节编号。 */
private sealed interface RulerItem {
    data class Time(val index: Int) : RulerItem
    data class Label(val index: Int) : RulerItem
}

/**
 * 网格主体。
 *
 * 每个块按 [Placement] 摆放：`x = 列宽 × dayIndex`，`y = 行高 × rowIndex`，
 * 高度为 `行高 × span`（连课合并块因此自然跨越多行）。
 * 被合并覆盖的格子在 [ScheduleGrid] 里本就不持有块，所以不会画出重叠矩形。
 *
 * 总宽度由调用方通过 `Modifier.width` 给定（窄屏时为 `最小列宽 × 7`），
 * 因此这里直接用 `constraints.maxWidth` 反推列宽即可。
 */
@Composable
private fun GridBody(
    grid: ScheduleGrid,
    days: List<Weekday>,
    todayIndex: Int?,
    rowHeight: Dp,
    modifier: Modifier = Modifier,
    onBlockClick: (CourseBlock) -> Unit,
) {
    val lineColor = gridLineColor()
    val highlightColor = todayHighlightColor()
    val lineWidth = 1.dp

    // 大节序号 → 行下标。作息表的大节序号未必从 1 连续编号，不能直接用序号当行号
    val rowIndexOf = remember(grid) {
        grid.rows.mapIndexed { index, row -> row.bigSection.index to index }.toMap()
    }

    Layout(
        modifier = modifier.drawBehind {
            val rowHeightPx = rowHeight.toPx()
            if (days.isEmpty() || size.width <= 0f) return@drawBehind
            val columnWidth = size.width / days.size
            val strokeWidth = lineWidth.toPx()

            // 今天所在列的淡色底
            if (todayIndex != null) {
                drawRect(
                    color = highlightColor,
                    topLeft = Offset(columnWidth * todayIndex, 0f),
                    size = Size(columnWidth, size.height),
                )
            }
            // 横线：rowCount + 1 条，首尾各一条
            for (i in 0..grid.rowCount) {
                val y = (i * rowHeightPx).coerceIn(0f, size.height)
                drawLine(lineColor, Offset(0f, y), Offset(size.width, y), strokeWidth)
            }
            // 竖线：days.size + 1 条
            for (j in 0..days.size) {
                val x = (j * columnWidth).coerceIn(0f, size.width)
                drawLine(lineColor, Offset(x, 0f), Offset(x, size.height), strokeWidth)
            }
        },
        content = {
            grid.blocks.forEach { block ->
                val rowIndex = rowIndexOf[block.startBigSection] ?: return@forEach
                val dayIndex = days.indexOf(block.course.weekday)
                if (dayIndex < 0) return@forEach

                CourseBlockCell(
                    block = block,
                    modifier = Modifier.layoutId(Placement(dayIndex, rowIndex, block.span)),
                    onClick = { onBlockClick(block) },
                )
            }
        },
    ) { measurables, constraints ->
        val rowHeightPx = rowHeight.roundToPx()
        val totalHeight = rowHeightPx * grid.rowCount
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else 0
        val columnWidth = if (days.isEmpty()) 0 else width / days.size

        val placed = ArrayList<Pair<Placement, Placeable>>(measurables.size)
        measurables.forEach { measurable ->
            val placement = measurable.layoutId as? Placement ?: return@forEach
            placed += placement to measurable.measure(
                Constraints.fixed(columnWidth, rowHeightPx * placement.span),
            )
        }

        layout(width, totalHeight) {
            placed.forEach { (placement, placeable) ->
                placeable.placeRelative(columnWidth * placement.dayIndex, rowHeightPx * placement.rowIndex)
            }
        }
    }
}

/**
 * 左侧时间轴。
 *
 * 技术设计文档 §6.2：「行头：关键时间线处显示时间；两根时间线中间显示节次编号（1-1、1-2、2-1…）」。
 * 因此每行有两个元素——画在该行**上边界线上**的起始时刻，和画在**行中间**的小节编号列表；
 * 另外在整个轴的最下方补一个末大节的结束时间。
 *
 * 时刻按「跨在网格线上」摆放（`y = 行号 × 行高 − 半个文字高`），
 * 这样它视觉上正好压在 [GridBody] 画出的那条横线上。
 */
@Composable
private fun TimeRuler(
    grid: ScheduleGrid,
    rowHeight: Dp,
    modifier: Modifier = Modifier,
) {
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    val timeStyle = MaterialTheme.typography.labelSmall
    val labelStyle = MaterialTheme.typography.labelSmall

    Layout(
        modifier = modifier,
        content = {
            grid.rows.forEachIndexed { rowIndex, row ->
                Text(
                    text = row.bigSection.startTime.display(),
                    modifier = Modifier.layoutId(RulerItem.Time(rowIndex)),
                    style = timeStyle,
                    color = textColor,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
                // 小节编号竖排，如 "1-1" 换行 "1-2"
                Text(
                    text = row.bigSection.sections.joinToString("\n") { row.bigSection.sectionNumber(it) },
                    modifier = Modifier.layoutId(RulerItem.Label(rowIndex)),
                    style = labelStyle,
                    color = textColor,
                    textAlign = TextAlign.Center,
                )
            }
            grid.rows.lastOrNull()?.let { lastRow ->
                Text(
                    text = lastRow.bigSection.endTime.display(),
                    modifier = Modifier.layoutId(RulerItem.Time(grid.rowCount)),
                    style = timeStyle,
                    color = textColor,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        },
    ) { measurables, constraints ->
        val rowHeightPx = rowHeight.roundToPx()
        val totalHeight = rowHeightPx * grid.rowCount
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else 0

        // Placeable 不暴露 layoutId，因此测量时就把它与 Measurable 的 layoutId 配对保存
        val loose = Constraints(minWidth = 0, maxWidth = width, minHeight = 0, maxHeight = Constraints.Infinity)
        val placed = ArrayList<Pair<Any?, Placeable>>(measurables.size)
        measurables.forEach { measurable ->
            placed += measurable.layoutId to measurable.measure(loose)
        }

        layout(width, totalHeight) {
            placed.forEach { (layoutId, placeable) ->
                val x = (width - placeable.width) / 2
                when (val item = layoutId) {
                    // 压在网格线上：向上偏移半个文字高
                    is RulerItem.Time ->
                        placeable.placeRelative(x, item.index * rowHeightPx - placeable.height / 2)

                    // 落在两线中间：在该行内垂直居中
                    is RulerItem.Label ->
                        placeable.placeRelative(x, item.index * rowHeightPx + (rowHeightPx - placeable.height) / 2)

                    null -> Unit
                }
            }
        }
    }
}

/** 一个大节行的高度。固定值，让时间轴与网格无需互相测量即可对齐。 */
private val RowHeight = 96.dp

/**
 * 左侧时间轴的宽度，需能容纳 "08:00" 这样的时刻文本。
 * 刻意压到最小，把宽度让给课程列。
 */
private val RulerWidth = 44.dp

/**
 * 窄屏下每列的最小宽度。
 *
 * 低于这个宽度中文课程名会被截到不可读，因此宁可横向滚动也不继续压缩。
 * 68dp 在默认字号下约能放 5 个汉字，放大字号下约 3~4 个。
 */
private val MinColumnWidth = 68.dp

/** 滚动内容的上下留白，避免首尾时间线上的文字被裁切。 */
private val RulerVerticalPadding = 10.dp

private const val DAYS_PER_WEEK = 7L
