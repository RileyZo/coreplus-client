package com.github.riley.coreplus.ui.schedule

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.riley.coreplus.R
import com.github.riley.coreplus.domain.term.TermStartInference
import com.github.riley.coreplus.ui.common.display
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * 学期开始日期选择器。
 *
 * **只有星期一可选**：学期第一周必然从星期一开始，放开其他日期只会让用户选出一个
 * 导致「今天是第几周」算错几天的值。限制通过 Material3 的 [SelectableDates] 实现，
 * 非星期一在日历上直接置灰。
 *
 * 用户不设置时使用 [TermStartInference] 的自动推断值（3 月 1 日 / 9 月 1 日所在周的星期一），
 * 对话框里会把它显示出来，让用户知道当前依据的是什么。
 *
 * 手动设置过的情况下额外提供「恢复自动推断」，用于跨学期时一键回到默认——
 * 否则上学期的日期会一直挂着，导致周次算出负数或超出范围。
 *
 * @param inferredDate 自动推断出的日期，仅用于展示
 * @param manualDate 用户手动设置的日期；null 表示当前走自动推断
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TermStartDatePickerDialog(
    inferredDate: LocalDate,
    manualDate: LocalDate?,
    onConfirm: (LocalDate) -> Unit,
    onUseInferred: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    val datePickerState = rememberDatePickerState(
        // 自动推断时不预选，逼用户做一次明确选择；已手动设置时回显当前值
        initialSelectedDateMillis = manualDate?.toUtcMillis(),
        selectableDates = remember { MondayOnlyDates },
    )

    DatePickerDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                onClick = { datePickerState.selectedDateMillis?.let { onConfirm(it.toUtcLocalDate()) } },
                enabled = datePickerState.selectedDateMillis != null,
            ) {
                Text(stringResource(R.string.action_ok))
            }
        },
        dismissButton = {
            Row {
                manualDate?.let {
                    TextButton(onClick = onUseInferred) {
                        Text(stringResource(R.string.term_start_use_inferred))
                    }
                }
                TextButton(onClick = onDismissRequest) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        },
    ) {
        Column(modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp)) {
            Text(
                text = stringResource(R.string.term_start_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(
                    if (manualDate != null) R.string.term_start_current_manual
                    else R.string.term_start_current_inferred,
                    (manualDate ?: inferredDate).display(),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        DatePicker(state = datePickerState, showModeToggle = false)
    }
}

/**
 * 只放行星期一的日期选择规则。
 *
 * Material3 的 [DatePicker] 内部一律用 **UTC 毫秒**表示日期（避免时区导致选中日偏移一天），
 * 因此判断前必须先按 UTC 还原成 [LocalDate]，不能用系统默认时区。
 */
@OptIn(ExperimentalMaterial3Api::class)
private object MondayOnlyDates : SelectableDates {

    override fun isSelectableDate(utcTimeMillis: Long): Boolean =
        TermStartInference.isMonday(utcTimeMillis.toUtcLocalDate())

    /** 只放开去年到明年，避免用户翻到几十年外的日历。 */
    override fun isSelectableYear(year: Int): Boolean =
        year in (LocalDate.now().year - 1)..(LocalDate.now().year + 1)
}

/** [LocalDate] → UTC 当天零点的毫秒数。 */
private fun LocalDate.toUtcMillis(): Long =
    atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

/** UTC 毫秒数 → [LocalDate]。必须按 UTC 还原，否则会差一天。 */
private fun Long.toUtcLocalDate(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
