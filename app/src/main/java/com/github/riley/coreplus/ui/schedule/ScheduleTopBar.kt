package com.github.riley.coreplus.ui.schedule

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.github.riley.coreplus.R
import com.github.riley.coreplus.domain.model.ScheduleLayout
import com.github.riley.coreplus.ui.common.display

/**
 * 课表页顶部区域：标题栏 + 回退提示条。
 *
 * 技术设计文档 §6.4 要求「作息表切换入口放在顶部」，因此结构中仍保留作息表入口：
 * - [TopAppBar]：标题（含可点开查看所有周次的 [TextButton]）、当前作息表季节（点开即编辑）、溢出菜单
 * - 第二行（按需）：未缓存周的回退提示
 *
 * 周次切换（上一周 / 下一周）通过课表主体上的左右滑动手势完成，不在此处显示箭头按钮。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ScheduleTopBar(
    state: ScheduleUiState,
    showSampleEntry: Boolean,
    onRefresh: () -> Unit,
    onOpenTimeTable: () -> Unit,
    onSelectLayout: (ScheduleLayout) -> Unit,
    onToggleShowWeekend: () -> Unit,
    onSelectWeek: (Int) -> Unit,
    onGoToCurrentWeek: () -> Unit,
    onOpenTermStartPicker: () -> Unit,
    onLoadSample: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var weekDialogVisible by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.schedule_title))
                    TextButton(onClick = { weekDialogVisible = true }) {
                        Text(stringResource(R.string.schedule_week_label, state.selectedWeek))
                    }
                }
            },
            actions = {
                // 作息表入口：直接显示当前季节，点一下就能改（§6.4）
                TextButton(onClick = onOpenTimeTable) {
                    Text(state.season.label)
                }
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_more_vert),
                        contentDescription = stringResource(R.string.action_more),
                    )
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.schedule_refresh)) },
                        leadingIcon = {
                            Icon(painterResource(R.drawable.ic_refresh), contentDescription = null)
                        },
                        onClick = {
                            menuExpanded = false
                            onRefresh()
                        },
                    )
                    HorizontalDivider()

                    // 布局切换（§6.1）。列表尚未实现，选中后主区显示占位提示
                    ScheduleLayout.entries.forEach { layout ->
                        DropdownMenuItem(
                            text = { Text(layout.label) },
                            trailingIcon = {
                                if (state.layout == layout) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_check),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            },
                            onClick = {
                                menuExpanded = false
                                onSelectLayout(layout)
                            },
                        )
                    }
                    HorizontalDivider()

                    // 显示周末开关：即时生效，菜单保持打开以便看到勾选变化
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.schedule_show_weekend)) },
                        leadingIcon = {
                            Checkbox(
                                checked = state.showWeekend,
                                onCheckedChange = null,
                            )
                        },
                        onClick = onToggleShowWeekend,
                    )

                    HorizontalDivider()

                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.schedule_term_start_date)) },
                        onClick = {
                            menuExpanded = false
                            onOpenTermStartPicker()
                        },
                    )

                    // 开发期后门：后端未就绪时用它验证表格渲染。release 构建不出现
                    if (showSampleEntry) {
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.schedule_load_sample)) },
                            onClick = {
                                menuExpanded = false
                                onLoadSample()
                            },
                        )
                    }
                }
            },
        )

        // 未缓存周的回退提示（§6.3「切换到未缓存的周时，显示上周课程并提示用户」）
        state.fallbackWeek?.let { fallback ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.tertiaryContainer,
            ) {
                Text(
                    text = stringResource(R.string.notice_week_fallback, state.selectedWeek, fallback),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
        }
    }

    // 周次选择对话框（独立于 Column 避免受 Scaffold 布局约束）
    if (weekDialogVisible) {
        WeekSelectionDialog(
            state = state,
            onDismiss = { weekDialogVisible = false },
            onSelectWeek = { week ->
                weekDialogVisible = false
                onSelectWeek(week)
            },
            onGoToCurrentWeek = {
                weekDialogVisible = false
                onGoToCurrentWeek()
            },
            onOpenTermStartPicker = {
                weekDialogVisible = false
                onOpenTermStartPicker()
            },
        )
    }
}

/**
 * 周次选择对话框。
 *
 * 垂直布局，从上到下依次为：
 * - 标题栏：「选择周次」+ 关闭按钮
 * - 「今天是第 X 周」提示（如能确定）
 * - 周次选择列表：以 [FlowRow] 列出所有周次，每项显示「第 * 周」
 * - 首周开始日期栏：显示当前学期起始日及推断/手动标记，点击可打开选择面板
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WeekSelectionDialog(
    state: ScheduleUiState,
    onDismiss: () -> Unit,
    onSelectWeek: (Int) -> Unit,
    onGoToCurrentWeek: () -> Unit,
    onOpenTermStartPicker: () -> Unit,
) {
    val currentWeek = state.currentWeek

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 20.dp),
            ) {
                // 标题栏
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.schedule_select_week),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = onGoToCurrentWeek,
                        enabled = state.canGoToCurrentWeek,
                        contentPadding = PaddingValues(horizontal = 8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.schedule_go_to_current_week),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            painter = painterResource(R.drawable.ic_close),
                            contentDescription = stringResource(R.string.action_close),
                        )
                    }
                }

                // 当前周信息
                if (currentWeek != null) {
                    Text(
                        text = stringResource(R.string.notice_current_week_today, currentWeek),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                } else {
                    Text(
                        text = stringResource(R.string.notice_current_week_unknown),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                }

                Spacer(Modifier.height(12.dp))
                HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))

                // 周次选择列表
                val weeks = remember(state.weekUpperBound) {
                    (1..state.weekUpperBound).toList()
                }

                FlowRow(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    weeks.forEach { week ->
                        val isSelected = week == state.selectedWeek
                        val isCurrent = currentWeek != null && week == currentWeek

                        Surface(
                            onClick = { onSelectWeek(week) },
                            shape = RoundedCornerShape(10.dp),
                            color = when {
                                isSelected -> MaterialTheme.colorScheme.primary
                                isCurrent -> MaterialTheme.colorScheme.primaryContainer
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            },
                            contentColor = when {
                                isSelected -> MaterialTheme.colorScheme.onPrimary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        ) {
                            Text(
                                text = stringResource(R.string.schedule_week_label, week),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))

                // 首周开始日期栏
                val termStartDate = state.termStartDate
                if (termStartDate != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenTermStartPicker() }
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.schedule_term_start_date),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = if (state.termStartIsInferred) {
                                    stringResource(R.string.term_start_current_inferred, termStartDate.display())
                                } else {
                                    stringResource(R.string.term_start_current_manual, termStartDate.display())
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        Icon(
                            painter = painterResource(R.drawable.ic_chevron_right),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}