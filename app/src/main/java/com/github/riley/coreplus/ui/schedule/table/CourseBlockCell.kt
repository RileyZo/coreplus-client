package com.github.riley.coreplus.ui.schedule.table

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.riley.coreplus.R
import com.github.riley.coreplus.domain.schedule.CourseBlock
import com.github.riley.coreplus.ui.theme.courseColor

/**
 * 表格里的一个课程块。
 *
 * 技术设计文档 §6.2 要求「课程块显示：课程名称、教师、上课地点。空间不够时按**从后往前**顺序隐藏」。
 * 因此内容按 `[名称, 教师, 地点]` 的优先级排列，用 [BoxWithConstraints] 量出可用高度、
 * 除以单行高度得到能放几行，再从尾部截断——块越矮（大节越少的小节），显示的信息越少。
 *
 * 连课合并块的 [CourseBlock.span] 更大、高度更高，因此自然能显示全部三项。
 */
@Composable
fun CourseBlockCell(
    block: CourseBlock,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    val color = courseColor(block.course.name)
    val detailLabel = stringResource(R.string.course_detail_title)
    // 首行是课程名，加粗以区别于下面的教师 / 地点
    val nameStyle = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold)
    val detailStyle = MaterialTheme.typography.labelSmall

    Box(
        modifier = modifier
            .padding(CellPadding)
            .clip(RoundedCornerShape(CellCorner))
            .background(color.background)
            .clickable(
                onClickLabel = detailLabel,
                onClick = onClick,
            )
            .padding(CellInnerPadding),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val capacity = (maxHeight / LineHeight).toInt().coerceAtLeast(1)

            // 先去掉空字段再截断：否则「教师为空」会白白占掉一行名额，
            // 导致明明放得下的地点也被隐藏
            val lines = listOf(block.course.name, block.course.teacher, block.course.location)
                .filter { it.isNotBlank() }
                .take(capacity)

            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Top,
            ) {
                lines.forEachIndexed { index, text ->
                    Text(
                        text = text,
                        style = if (index == 0) nameStyle else detailStyle,
                        color = color.content,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = LineHeightText,
                    )
                }
            }
        }
    }
}

/** 块与相邻块之间的外边距，留出网格线的视觉空隙。 */
private val CellPadding = 1.5.dp

/** 圆角半径。 */
private val CellCorner = 7.dp

/** 块内文字与边缘的距离。 */
private val CellInnerPadding = 5.dp

/**
 * 单行占用的估算高度。
 *
 * 与 [LineHeightText] 对应并留出少量余量。字体缩放放大时估算会偏乐观，
 * 最坏情况是最后一行被裁掉一半——比让文字溢出块外要好。
 */
private val LineHeight = 15.dp

private val LineHeightText = 14.sp
