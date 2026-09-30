package com.github.riley.coreplus.ui.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.riley.coreplus.R
import com.github.riley.coreplus.domain.model.Course
import com.github.riley.coreplus.domain.schedule.CourseBlock
import com.github.riley.coreplus.ui.common.displayCredit

/**
 * 课程详情对话框（技术设计文档 §6.2「点击课程块可查看 / 编辑 / 删除」）。
 *
 * 本轮实现**查看**与**删除**；编辑表单留待后续。
 *
 * 删除按钮下面固定挂一句提醒：本地删除只改缓存，下次刷新会被学校数据覆盖回来，
 * 而且临时调课本来就不会同步到学校系统——这是技术设计文档 §3 明确接受的取舍，
 * 但用户得知道，否则会把这里当成「请假/调课」的正式入口。
 */
@Composable
fun CourseDetailDialog(
    block: CourseBlock,
    onDismissRequest: () -> Unit,
    onDelete: (Course) -> Unit,
) {
    val course = block.course

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Column {
                Text(text = course.name, style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(4.dp))
                // 节次与时间是「实时算出来」的，随作息表切换而变（技术设计文档 §4）
                Text(
                    text = listOf(
                        course.weekday.label,
                        block.bigSectionText,
                        block.timeRangeText,
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (block.isMerged) {
                    Text(
                        text = stringResource(R.string.course_detail_merged_hint, block.span),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(12.dp))
                }

                DetailRow(R.string.course_detail_teacher, course.teacher)
                DetailRow(R.string.course_detail_location, course.location)
                DetailRow(R.string.course_detail_weekday, course.weekday.label)
                DetailRow(R.string.course_detail_section, block.sectionLabels.joinToString(" "))
                DetailRow(R.string.course_detail_time, block.timeRangeText)
                DetailRow(R.string.course_detail_credit, course.credit.displayCredit())
                DetailRow(R.string.course_detail_attribute, course.attribute)
                DetailRow(R.string.course_detail_week, stringResource(R.string.schedule_week_label, course.week))

                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.course_detail_delete_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onDelete(course) }) {
                Text(
                    text = stringResource(R.string.action_delete),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.action_close))
            }
        },
    )
}

/** 一行「标签 + 值」。值为空时显示占位文案，避免出现空行让用户以为数据丢了。 */
@Composable
private fun DetailRow(labelRes: Int, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(56.dp),
        )
        Text(
            text = value.ifBlank { stringResource(R.string.course_detail_unknown) },
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
    }
}
