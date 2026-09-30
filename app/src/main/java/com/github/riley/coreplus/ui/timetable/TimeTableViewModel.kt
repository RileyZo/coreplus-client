package com.github.riley.coreplus.ui.timetable

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.riley.coreplus.R
import com.github.riley.coreplus.domain.model.TimeTable
import com.github.riley.coreplus.domain.model.TimeTableSeason
import com.github.riley.coreplus.domain.repository.TimeTableRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalTime
import javax.inject.Inject

/**
 * 作息时间表窗口的状态。
 *
 * @property editing 非 null 时显示时间选择器，指明正在改哪个小节的哪一端
 * @property errorText 内联错误文案。这里用内联文本而不是 Snackbar：
 *   错误只与刚编辑的那一行有关，显示在窗口内部更贴近上下文
 */
data class TimeTableUiState(
    val initializing: Boolean = true,
    val season: TimeTableSeason = TimeTableSeason.SUMMER,
    val timeTables: Map<TimeTableSeason, TimeTable> = emptyMap(),
    val editing: EditingSection? = null,
    val errorText: String? = null,
) {
    /** 当前季节对应的作息表；数据尚未到达时为 null。 */
    val current: TimeTable? get() = timeTables[season]
}

/**
 * 正在编辑的小节。
 *
 * 记录的是**定位信息**而非 `Section` 实例：用户在时间选择器上操作期间，
 * 仓库里的作息表可能已被别处改动，用下标重新查一次才拿得到最新值。
 *
 * @property editingStart true 表示正在改开始时间，false 表示改结束时间
 */
data class EditingSection(
    val bigSectionIndex: Int,
    val sectionIndex: Int,
    val sectionLabel: String,
    val editingStart: Boolean,
    val initialHour: Int,
    val initialMinute: Int,
)

/**
 * 作息时间表窗口的 ViewModel。
 *
 * 技术设计文档 §2.3：结构固定，用户只能修改小节的起止时间；大节的起止时间由首末小节推导。
 * §6.4：入口在顶部，两套作息表对应夏/冬，可修改起止时间，提供「恢复默认」。
 *
 * 切换季节会立即持久化（§7 要求「当前作息表的选择需持久化存储，重启后恢复」），
 * 并且课表上的时间会**立刻重算**——`ScheduleViewModel` 与本 ViewModel 共享同一个
 * 单例 `TimeTableRepository`，季节一变，两边的 Flow 同时收到新值（§4「切换作息表后立即重算」）。
 */
@HiltViewModel
class TimeTableViewModel @Inject constructor(
    private val timeTableRepository: TimeTableRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private data class Interaction(
        val editing: EditingSection? = null,
        val errorText: String? = null,
    )

    private val interaction = MutableStateFlow(Interaction())

    val uiState: StateFlow<TimeTableUiState> = combine(
        timeTableRepository.timeTables,
        timeTableRepository.selectedSeason,
        interaction,
    ) { tables, season, ui ->
        TimeTableUiState(
            initializing = false,
            season = season,
            timeTables = tables,
            editing = ui.editing,
            errorText = ui.errorText,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), TimeTableUiState())

    /** 切换夏/冬。同时收起时间选择器：它的下标指向的是切换前那套表。 */
    fun selectSeason(season: TimeTableSeason) {
        interaction.update { it.copy(editing = null) }
        viewModelScope.launch { timeTableRepository.select(season) }
    }

    /** 打开时间选择器，修改某个小节的开始或结束时间。 */
    fun beginEdit(bigSectionIndex: Int, sectionIndex: Int, sectionLabel: String, editingStart: Boolean, initial: LocalTime) {
        interaction.update {
            it.copy(
                editing = EditingSection(
                    bigSectionIndex = bigSectionIndex,
                    sectionIndex = sectionIndex,
                    sectionLabel = sectionLabel,
                    editingStart = editingStart,
                    initialHour = initial.hour,
                    initialMinute = initial.minute,
                ),
                errorText = null,
            )
        }
    }

    fun cancelEdit() {
        interaction.update { it.copy(editing = null) }
    }

    /**
     * 提交时间选择器的结果。
     *
     * 只改被点的那一端，另一端保留原值。若 resulting 区间非法（开始不早于结束），
     * `TimeTableRepository.updateSection` 会返回 false 且**不写入任何数据**，
     * 这里把它翻译成内联错误提示。
     */
    fun confirmEdit(hour: Int, minute: Int) {
        val target = interaction.value.editing ?: return
        val state = uiState.value
        val table = state.current ?: return
        val section = table.bigSection(target.bigSectionIndex)
            ?.sections
            ?.firstOrNull { it.index == target.sectionIndex }
            ?: return

        val picked = LocalTime.of(hour, minute)
        val newStart = if (target.editingStart) picked else section.startTime
        val newEnd = if (target.editingStart) section.endTime else picked

        viewModelScope.launch {
            val saved = timeTableRepository.updateSection(
                season = state.season,
                bigSectionIndex = target.bigSectionIndex,
                sectionIndex = target.sectionIndex,
                start = newStart,
                end = newEnd,
            )
            interaction.update {
                it.copy(
                    editing = null,
                    errorText = if (saved) null else context.getString(R.string.time_table_invalid_range),
                )
            }
        }
    }

    /** 恢复当前季节的内置默认作息（技术设计文档 §2.3）。 */
    fun restoreDefaults() {
        val season = uiState.value.season
        viewModelScope.launch {
            val saved = timeTableRepository.restoreDefaults(season)
            interaction.update {
                it.copy(editing = null, errorText = if (saved) null else context.getString(R.string.time_table_save_failed))
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
