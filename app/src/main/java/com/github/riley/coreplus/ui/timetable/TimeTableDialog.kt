package com.github.riley.coreplus.ui.timetable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.github.riley.coreplus.R
import com.github.riley.coreplus.domain.model.BigSection
import com.github.riley.coreplus.domain.model.TimeTableSeason
import com.github.riley.coreplus.ui.common.display
import java.time.LocalTime

/**
 * 作息时间表窗口（技术设计文档 §6.4）。
 *
 * 内容：夏/冬切换 + 每个小节的起止时间编辑 + 恢复默认。
 *
 * 时间选择器用**第二层对话框**而不是内嵌展开：`TimePicker` 本身就有 ~300dp 高，
 * 内嵌会把已经装了 5 个大节的窗口撑到需要滚动两屏。两层对话框各自滚动，交互更清楚。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeTableDialog(
    onDismissRequest: () -> Unit,
    viewModel: TimeTableViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val seasons = TimeTableSeason.entries

    Dialog(onDismissRequest = onDismissRequest) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 620.dp),
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
            ) {
                Text(
                    text = stringResource(R.string.time_table_title),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.time_table_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(18.dp))

                // 夏/冬切换。选中即持久化，课表上的时间随之立即重算
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    seasons.forEachIndexed { index, season ->
                        SegmentedButton(
                            selected = state.season == season,
                            onClick = { viewModel.selectSeason(season) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = seasons.size),
                        ) {
                            Text(season.label)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))

                val table = state.current
                if (table == null) {
                    Box(modifier = Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    table.bigSections.forEachIndexed { index, bigSection ->
                        BigSectionEditor(
                            bigSection = bigSection,
                            onPickTime = { sectionIndex, editingStart, initial ->
                                viewModel.beginEdit(
                                    bigSectionIndex = bigSection.index,
                                    sectionIndex = sectionIndex,
                                    sectionLabel = bigSection.sections
                                        .firstOrNull { it.index == sectionIndex }
                                        ?.let { bigSection.sectionNumber(it) }
                                        .orEmpty(),
                                    editingStart = editingStart,
                                    initial = initial,
                                )
                            },
                        )
                        if (index != table.bigSections.lastIndex) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        }
                    }
                }

                state.errorText?.let { message ->
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                Spacer(Modifier.height(18.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TextButton(onClick = viewModel::restoreDefaults, enabled = state.current != null) {
                        Text(stringResource(R.string.time_table_restore_defaults))
                    }
                    TextButton(onClick = onDismissRequest) {
                        Text(stringResource(R.string.action_close))
                    }
                }
            }
        }
    }

    // 第二层：时间选择器。与上面的窗口是同级 Dialog，因此会叠在其上方
    state.editing?.let { target ->
        TimePickerDialog(
            title = target.sectionLabel,
            initialHour = target.initialHour,
            initialMinute = target.initialMinute,
            onConfirm = viewModel::confirmEdit,
            onDismiss = viewModel::cancelEdit,
        )
    }
}

/**
 * 一个大节的编辑区：标题行显示大节号与推导出的起止时间，
 * 下面每个小节一行 `1-1  [08:00] → [08:45]`，两端都可点开时间选择器。
 *
 * 大节的起止时间**只读**——技术设计文档 §2.3 规定它等于「首小节开始 → 末小节结束」，
 * 是推导值而非独立字段，因此不提供直接编辑入口。
 */
@Composable
private fun BigSectionEditor(
    bigSection: BigSection,
    onPickTime: (sectionIndex: Int, editingStart: Boolean, initial: LocalTime) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.time_table_big_section_label, bigSection.index),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(
                    R.string.time_table_big_section_range,
                    bigSection.startTime.display(),
                    bigSection.endTime.display(),
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(4.dp))

        bigSection.sections.forEach { section ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = bigSection.sectionNumber(section),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(40.dp),
                    textAlign = TextAlign.Start,
                )
                TimeButton(
                    text = section.startTime.display(),
                    onClick = { onPickTime(section.index, true, section.startTime) },
                )
                Text(
                    text = "→",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
                TimeButton(
                    text = section.endTime.display(),
                    onClick = { onPickTime(section.index, false, section.endTime) },
                )
            }
        }
    }
}

/** 可点击的时刻按钮。用 [TextButton] 而非自绘，天然带上点击态与无障碍语义。 */
@Composable
private fun TimeButton(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 12.dp)) {
        Text(text = text, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * 时间选择器弹层。
 *
 * Material3 只提供 [TimePicker] 组件、不提供配套的对话框，
 * 因此按官方推荐用 [AlertDialog] 承载。`is24Hour = true`：课表时间没有上下午歧义需求。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    title: String,
    initialHour: Int,
    initialMinute: Int,
    onConfirm: (hour: Int, minute: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val pickerState = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = true,
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (title.isBlank()) {
                    stringResource(R.string.time_table_pick_time)
                } else {
                    "$title · ${stringResource(R.string.time_table_pick_time)}"
                },
            )
        },
        text = {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TimePicker(state = pickerState)
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(pickerState.hour, pickerState.minute) }) {
                Text(stringResource(R.string.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}
