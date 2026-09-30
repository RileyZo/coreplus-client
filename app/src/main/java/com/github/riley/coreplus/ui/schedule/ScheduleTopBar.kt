package com.github.riley.coreplus.ui.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.github.riley.coreplus.R
import com.github.riley.coreplus.domain.model.ScheduleLayout

/**
 * 课表页顶部区域：标题栏 + 周次选择器 + 提示条。
 *
 * 技术设计文档 §6.3 要求「顶部提供周次选择器（左右箭头 + 当前周数）」并「显示今天是第几周」，
 * §6.4 要求「作息表切换入口放在顶部」，因此三部分都挤在这里：
 * - 第一行 [TopAppBar]：标题、当前作息表季节（点开即编辑）、溢出菜单
 * - 第二行：`◀ 第 N / M 周 ▶`，中间同时显示「今天是第几周」与「回到本周」
 * - 第三行（按需）：未缓存周的回退提示
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleTopBar(
    state: ScheduleUiState,
    showSampleEntry: Boolean,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onGoToCurrentWeek: () -> Unit,
    onRefresh: () -> Unit,
    onOpenTimeTable: () -> Unit,
    onSelectLayout: (ScheduleLayout) -> Unit,
    onOpenTermStartPicker: () -> Unit,
    onLoadSample: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        TopAppBar(
            title = { Text(stringResource(R.string.schedule_title)) },
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

        WeekSelectorRow(
            state = state,
            onPreviousWeek = onPreviousWeek,
            onNextWeek = onNextWeek,
            onGoToCurrentWeek = onGoToCurrentWeek,
            onOpenTermStartPicker = onOpenTermStartPicker,
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
}

/**
 * 周次选择器一行。
 *
 * 中间两行文本：第一行是「第 N / M 周」，第二行在能确定本周时显示
 * 「今天是第 X 周 · 回到本周」（可点），否则说明为什么确定不了。
 */
@Composable
private fun WeekSelectorRow(
    state: ScheduleUiState,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onGoToCurrentWeek: () -> Unit,
    onOpenTermStartPicker: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        IconButton(onClick = onPreviousWeek, enabled = state.canGoPrevious) {
            Icon(
                painter = painterResource(R.drawable.ic_chevron_left),
                contentDescription = stringResource(R.string.schedule_previous_week),
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = if (state.totalWeeks > 0) {
                    stringResource(R.string.schedule_week_label_with_total, state.selectedWeek, state.totalWeeks)
                } else {
                    stringResource(R.string.schedule_week_label, state.selectedWeek)
                },
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )

            val currentWeek = state.currentWeek
            if (currentWeek != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.notice_current_week_today, currentWeek),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (state.canGoToCurrentWeek) {
                        TextButton(
                            onClick = onGoToCurrentWeek,
                            contentPadding = PaddingValues(horizontal = 8.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.schedule_go_to_current_week),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
            } else {
                // 还没开学 / 已放假 / 学期起始日不对：说明原因并给出入口
                TextButton(
                    onClick = onOpenTermStartPicker,
                    contentPadding = PaddingValues(horizontal = 8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.notice_current_week_unknown),
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        IconButton(onClick = onNextWeek, enabled = state.canGoNext) {
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = stringResource(R.string.schedule_next_week),
            )
        }
    }
}
