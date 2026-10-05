package com.github.riley.coreplus.ui.schedule

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.riley.coreplus.R
import com.github.riley.coreplus.di.DefaultDispatcher
import com.github.riley.coreplus.domain.error.AppError
import com.github.riley.coreplus.domain.model.Course
import com.github.riley.coreplus.domain.model.Schedule
import com.github.riley.coreplus.domain.model.ScheduleLayout
import com.github.riley.coreplus.domain.model.TimeTable
import com.github.riley.coreplus.domain.model.TimeTableDefaults
import com.github.riley.coreplus.domain.model.TimeTableSeason
import com.github.riley.coreplus.domain.model.WeeklySchedule
import com.github.riley.coreplus.domain.repository.RefreshResult
import com.github.riley.coreplus.domain.repository.AuthRepository
import com.github.riley.coreplus.domain.repository.ScheduleRepository
import com.github.riley.coreplus.domain.repository.SettingsRepository
import com.github.riley.coreplus.domain.repository.TimeTableRepository
import com.github.riley.coreplus.domain.schedule.CourseBlock
import com.github.riley.coreplus.domain.schedule.ScheduleGridBuilder
import com.github.riley.coreplus.domain.term.TermStartInference
import com.github.riley.coreplus.domain.term.WeekCalculator
import com.github.riley.coreplus.ui.common.UiMessage
import com.github.riley.coreplus.ui.common.display
import com.github.riley.coreplus.ui.common.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/**
 * 课表主界面的 ViewModel。
 *
 * 技术设计文档 §5：「持有当前周次、作息表选择，暴露 UI 状态；周次切换、作息表切换、刷新等交互在此协调」。
 *
 * ## 状态来源分成两半
 *
 * - **[repositorySnapshot]**：来自四个仓库的持久化数据，用 `combine` 汇总。
 *   任何一处变化（刷新、删除课程、切换作息表、改学期起始日）都会自动重新派生 UI 状态。
 * - **[interaction]**：只由用户交互驱动的瞬时状态（选中周次、对话框可见性等）。
 *
 * 分开的好处是「点一下下一周」不必重新读任何仓库，只更新 [interaction]；
 * 而派生逻辑集中在 [buildState] 一处，两条来源怎么变都走同一条路径。
 *
 * ## 派生而非存储
 *
 * [ScheduleUiState.grid]（含连课合并与实时时间）、[ScheduleUiState.currentWeek]、
 * 回退显示的周次，全部在 [buildState] 里**每次重新计算**，不做缓存。
 * 这是技术设计文档 §4「不缓存计算结果，根据当前作息表实时计算」的直接体现：
 * 用户切换夏/冬作息表后，`combine` 重新发射，时间自动重算。
 *
 * ## 一次性提示走 Channel
 *
 * Snackbar 文案通过 [messages]（`Channel`）发出而不是塞进状态，
 * 避免旋转屏幕后重复弹出；`Channel` 会缓冲，因此不会像 `SharedFlow` 那样丢掉无人接收的消息。
 */
@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val scheduleRepository: ScheduleRepository,
    private val timeTableRepository: TimeTableRepository,
    private val settingsRepository: SettingsRepository,
    private val authRepository: AuthRepository,
    @ApplicationContext private val context: Context,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) : ViewModel() {

    /** 仅由用户交互驱动的状态。 */
    private data class Interaction(
        /** null 表示「跟随今天」，即自动定位到本周（技术设计文档 §6.3）。 */
        val selectedWeek: Int? = null,
        val refreshing: Boolean = false,
        val loginVisible: Boolean = false,
        val timeTableEditorVisible: Boolean = false,
        val termStartPickerVisible: Boolean = false,
        val detailBlock: CourseBlock? = null,
    )

    /** 四个仓库汇总后的快照。 */
    private data class RepositorySnapshot(
        val schedule: Schedule?,
        val timeTables: Map<TimeTableSeason, TimeTable>,
        val season: TimeTableSeason,
        val layout: ScheduleLayout,
        val showWeekend: Boolean,
        val userTermStart: LocalDate?,
    )

    /** [RepositorySnapshot] 中来自除「显示周末」之外的五个流，分开组合以避免超过 5 元的 `combine` 重载。 */
    private data class SnapshotBase(
        val schedule: Schedule?,
        val timeTables: Map<TimeTableSeason, TimeTable>,
        val season: TimeTableSeason,
        val layout: ScheduleLayout,
        val userTermStart: LocalDate?,
    )

    private val interaction = MutableStateFlow(Interaction())

    private val repositorySnapshot: Flow<RepositorySnapshot> = combine(
        combine(
            scheduleRepository.schedule,
            timeTableRepository.timeTables,
            timeTableRepository.selectedSeason,
            settingsRepository.layout,
            settingsRepository.termStartDate,
        ) { schedule, timeTables, season, layout, userTermStart ->
            SnapshotBase(schedule, timeTables, season, layout, userTermStart)
        },
        settingsRepository.showWeekend,
    ) { base, showWeekend ->
        RepositorySnapshot(
            schedule = base.schedule,
            timeTables = base.timeTables,
            season = base.season,
            layout = base.layout,
            showWeekend = showWeekend,
            userTermStart = base.userTermStart,
        )
    }.flowOn(defaultDispatcher)   // buildState 里要跑连课合并，放到后台线程

    val uiState: StateFlow<ScheduleUiState> =
        combine(repositorySnapshot, interaction, ::buildState)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ScheduleUiState())

    private val _messages = Channel<UiMessage>(Channel.BUFFERED)
    val messages: Flow<UiMessage> = _messages.receiveAsFlow()

    /** 「是否已提示过设置学期开始日期」，用于避免每次启动都提示。 */
    private val termStartPrompted = settingsRepository.termStartPrompted
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** 用户手动设置的学期起始日；null 表示走自动推断。 */
    private val userTermStart = settingsRepository.termStartDate
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        // 首屏判定（技术设计文档 §5）：无缓存则直接引导登录，不等用户点按钮
        viewModelScope.launch {
            if (repositorySnapshot.first().schedule == null) {
                interaction.update { it.copy(loginVisible = true) }
            }
        }
    }

    // region 周次切换

    /** 选中某一周。 */
    fun selectWeek(week: Int) {
        interaction.update { it.copy(selectedWeek = week) }
    }

    fun previousWeek() {
        val target = uiState.value.selectedWeek - 1
        if (target >= 1) selectWeek(target)
    }

    fun nextWeek() {
        val state = uiState.value
        val target = state.selectedWeek + 1
        if (target <= state.weekUpperBound) selectWeek(target)
    }

    /** 回到「跟随今天」，并立即跳到本周。 */
    fun goToCurrentWeek() {
        interaction.update { it.copy(selectedWeek = null) }
    }

    // endregion

    // region 登录与刷新

    /**
     * 用户点了「刷新」。
     *
     * 技术设计文档 §1 要求「刷新需重新登录」，因此这里**先丢弃旧 token**再弹登录框，
     * 哪怕上一个 token 理论上还在有效期内。这样就不存在「用旧 token 直接拉数据」的路径。
     */
    fun requestRefresh() {
        authRepository.logout()
        interaction.update { it.copy(loginVisible = true) }
    }

    fun dismissLogin() {
        interaction.update { it.copy(loginVisible = false) }
    }

    /** 登录对话框提交成功后回调，用刚拿到的 token 拉取课表。 */
    fun onLoginSucceeded() {
        interaction.update { it.copy(loginVisible = false) }
        fetchSchedule()
    }

    private fun fetchSchedule() {
        if (interaction.value.refreshing) return
        viewModelScope.launch {
            interaction.update { it.copy(refreshing = true) }
            try {
                when (val result = scheduleRepository.refresh()) {
                    is RefreshResult.Failure -> onRefreshFailed(result.error)
                    is RefreshResult.Success -> onRefreshSucceeded(result)
                }
            } finally {
                interaction.update { it.copy(refreshing = false) }
            }
        }
    }

    private suspend fun onRefreshFailed(error: AppError) {
        _messages.send(UiMessage(error.toUserMessage(context)))
        // token 或学校会话已失效：清掉并重新引导登录
        if (error is AppError.LoginRequired) {
            authRepository.logout()
            interaction.update { it.copy(loginVisible = true) }
        }
    }

    /**
     * 拉取成功。
     *
     * 合并语义由 `ScheduleRepositoryImpl` 负责，这里只把结果翻译成人话：
     * 「部分成功」时（技术设计文档 §3）明确告诉用户有几周没拉到、已保留旧数据。
     */
    private suspend fun onRefreshSucceeded(result: RefreshResult.Success) {
        val text = buildString {
            append(context.getString(R.string.notice_refresh_success, result.fetchedWeeks.size))
            if (result.missingWeeks.isNotEmpty()) {
                append('，')
                append(context.getString(R.string.notice_partial_success, result.missingWeeks.size))
            }
        }
        _messages.send(UiMessage(text))
        maybePromptTermStart()
    }

    /**
     * 首次拿到课表、且用户从未设置过学期起始日时，提示一次。
     *
     * 用带按钮的 Snackbar 而不是对话框：此时登录框可能刚关闭，
     * 再叠一个对话框体验很差，而且自动推断的值大多数情况下本来就是对的。
     */
    private suspend fun maybePromptTermStart() {
        if (termStartPrompted.value || userTermStart.value != null) return

        settingsRepository.markTermStartPrompted()
        _messages.send(
            UiMessage(
                text = context.getString(
                    R.string.notice_term_start_hint,
                    TermStartInference.infer(LocalDate.now()).display(),
                ),
                actionLabel = context.getString(R.string.notice_term_start_action),
                onAction = { showTermStartPicker() },
            ),
        )
    }

    // endregion

    // region 课程详情与删除

    fun showCourseDetail(block: CourseBlock) {
        interaction.update { it.copy(detailBlock = block) }
    }

    fun dismissCourseDetail() {
        interaction.update { it.copy(detailBlock = null) }
    }

    /** 删除课程并直接覆盖缓存（技术设计文档 §3：刷新后会丢失，这是已接受的取舍）。 */
    fun deleteCourse(course: Course) {
        viewModelScope.launch {
            val error = scheduleRepository.deleteCourse(course)
            interaction.update { it.copy(detailBlock = null) }
            if (error != null) {
                _messages.send(UiMessage(error.toUserMessage(context)))
            } else {
                _messages.send(UiMessage(context.getString(R.string.notice_course_deleted, course.name)))
            }
        }
    }

    // endregion

    // region 作息表 / 布局 / 学期起始日

    fun selectSeason(season: TimeTableSeason) {
        viewModelScope.launch { timeTableRepository.select(season) }
    }

    fun showTimeTableEditor() {
        interaction.update { it.copy(timeTableEditorVisible = true) }
    }

    fun dismissTimeTableEditor() {
        interaction.update { it.copy(timeTableEditorVisible = false) }
    }

    fun setLayout(layout: ScheduleLayout) {
        viewModelScope.launch { settingsRepository.setLayout(layout) }
    }

    /** 切换是否在课表中显示周末两列。 */
    fun toggleShowWeekend() {
        viewModelScope.launch {
            settingsRepository.setShowWeekend(!uiState.value.showWeekend)
        }
    }

    fun showTermStartPicker() {
        interaction.update { it.copy(termStartPickerVisible = true) }
    }

    fun dismissTermStartPicker() {
        interaction.update { it.copy(termStartPickerVisible = false) }
    }

    /** 传 null 表示清除手动设置、恢复自动推断。 */
    fun setTermStartDate(date: LocalDate?) {
        viewModelScope.launch {
            settingsRepository.setTermStartDate(date)
            val text = if (date == null) {
                context.getString(R.string.notice_term_start_reset)
            } else {
                context.getString(R.string.notice_term_start_saved, date.display())
            }
            _messages.send(UiMessage(text))
        }
    }

    // endregion

    // region 开发期辅助

    /** 载入示例课表，仅 debug 构建有入口。数据与 `docs/API.md` 附录 A 一致。 */
    fun loadSample() {
        viewModelScope.launch {
            val error = scheduleRepository.loadSample()
            if (error != null) {
                _messages.send(UiMessage(error.toUserMessage(context)))
                return@launch
            }
            interaction.update { it.copy(loginVisible = false, selectedWeek = null) }
            _messages.send(UiMessage(context.getString(R.string.notice_sample_loaded)))
            maybePromptTermStart()
        }
    }

    // endregion

    /**
     * 把仓库快照与交互状态合成 UI 状态。**所有派生计算都在这里。**
     */
    private fun buildState(snapshot: RepositorySnapshot, ui: Interaction): ScheduleUiState {
        val schedule = snapshot.schedule
        val totalWeeks = schedule?.totalWeeks ?: 0
        val season = snapshot.season
        val timeTable = snapshot.timeTables[season] ?: TimeTableDefaults.of(season)

        val today = LocalDate.now()
        val termStart = snapshot.userTermStart ?: TermStartInference.infer(today)
        val currentWeek = WeekCalculator.currentWeek(termStart, today, totalWeeks)

        val upperBound = if (totalWeeks > 0) totalWeeks else Schedule.FALLBACK_TOTAL_WEEKS
        val selectedWeek = (ui.selectedWeek ?: currentWeek ?: 1).coerceIn(1, upperBound)

        // 技术设计文档 §6.3：切到未缓存的周时，显示最近一个已缓存的更早周并提示用户
        val cachedWeek = schedule?.week(selectedWeek)
        val fallbackWeek =
            if (cachedWeek == null) nearestCachedWeekBefore(schedule, selectedWeek) else null
        val displayed = cachedWeek ?: fallbackWeek

        return ScheduleUiState(
            initializing = false,
            schedule = schedule,
            totalWeeks = totalWeeks,
            selectedWeek = selectedWeek,
            displayedWeek = displayed?.weekNumber,
            fallbackWeek = fallbackWeek?.weekNumber,
            currentWeek = currentWeek,
            termStartDate = termStart,
            termStartIsInferred = snapshot.userTermStart == null,
            timeTable = timeTable,
            season = season,
            layout = snapshot.layout,
            showWeekend = snapshot.showWeekend,
            grid = ScheduleGridBuilder.build(displayed, timeTable),
            refreshing = ui.refreshing,
            loginVisible = ui.loginVisible,
            timeTableEditorVisible = ui.timeTableEditorVisible,
            termStartPickerVisible = ui.termStartPickerVisible,
            detailBlock = ui.detailBlock,
        )
    }

    /** 从 [week] 往前找最近一个已缓存的周；都没有则返回 null。 */
    private fun nearestCachedWeekBefore(schedule: Schedule?, week: Int): WeeklySchedule? {
        if (schedule == null) return null
        for (candidate in (week - 1) downTo 1) {
            schedule.week(candidate)?.let { return it }
        }
        return null
    }

    private companion object {
        /** 退到后台后保持上游订阅 5 秒，避免旋屏时重新读盘。 */
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
