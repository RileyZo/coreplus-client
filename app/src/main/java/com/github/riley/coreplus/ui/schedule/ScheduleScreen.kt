package com.github.riley.coreplus.ui.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.github.riley.coreplus.BuildConfig
import com.github.riley.coreplus.R
import com.github.riley.coreplus.domain.model.ScheduleLayout
import com.github.riley.coreplus.domain.schedule.CourseBlock
import com.github.riley.coreplus.ui.login.LoginDialog
import com.github.riley.coreplus.ui.schedule.table.ScheduleTable
import com.github.riley.coreplus.ui.timetable.TimeTableDialog
import java.time.LocalDate

/**
 * 课表主界面。
 *
 * 只负责三件事：把 [ScheduleUiState] 分派给对应的渲染分支、承载 Snackbar、挂载对话框。
 * 所有计算都在 [ScheduleViewModel] 里完成，因此本文件里没有任何业务判断。
 *
 * ## 布局分支（技术设计文档 §6.1）
 *
 * 表格与列表**共享同一个 UiState**，靠 [ScheduleUiState.layout] 选择渲染分支。
 * 列表尚未实现，走 [ListPlaceholder]；这就是文档要求「先实现表格，列表预留抽象」的落地方式。
 *
 * ## 提示走 Channel 而非状态
 *
 * [ScheduleViewModel.messages] 是 `Channel`，`showSnackbar` 挂起直到用户看到，
 * 因此多条提示会依次播放而不会互相覆盖；旋转屏幕也不会重放（不是状态的一部分）。
 */
@Composable
fun ScheduleScreen(
    modifier: Modifier = Modifier,
    viewModel: ScheduleViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel, snackbarHostState) {
        viewModel.messages.collect { message ->
            val result = snackbarHostState.showSnackbar(
                message = message.text,
                actionLabel = message.actionLabel,
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) message.onAction?.invoke()
        }
    }

    // 今天与「正在显示那一周的星期一」。用于表头标注日期、高亮今天所在的列
    val today = remember { LocalDate.now() }
    val weekStartDate = remember(state.termStartDate, state.displayedWeek) {
        val start = state.termStartDate
        val week = state.displayedWeek
        if (start == null || week == null || week < 1) {
            null
        } else {
            start.plusDays((week - 1) * DAYS_PER_WEEK)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            ScheduleTopBar(
                state = state,
                showSampleEntry = BuildConfig.DEBUG,
                onPreviousWeek = viewModel::previousWeek,
                onNextWeek = viewModel::nextWeek,
                onGoToCurrentWeek = viewModel::goToCurrentWeek,
                onRefresh = viewModel::requestRefresh,
                onOpenTimeTable = viewModel::showTimeTableEditor,
                onSelectLayout = viewModel::setLayout,
                onOpenTermStartPicker = viewModel::showTermStartPicker,
                onLoadSample = viewModel::loadSample,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
        ) {
            when {
                state.initializing -> CenteredProgress()
                !state.hasCache -> NoCacheState(onLogin = viewModel::requestRefresh)
                state.layout == ScheduleLayout.LIST -> ListPlaceholder()
                else -> TableContent(
                    state = state,
                    weekStartDate = weekStartDate,
                    today = today,
                    onBlockClick = viewModel::showCourseDetail,
                )
            }

            // 顶部细进度条：不遮挡表格，也不像模态框那样打断操作
            if (state.refreshing) {
                LinearProgressIndicator(modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth())
            }
        }
    }

    // region 对话框

    if (state.loginVisible) {
        LoginDialog(
            onDismissRequest = viewModel::dismissLogin,
            onLoginSucceeded = viewModel::onLoginSucceeded,
        )
    }

    if (state.timeTableEditorVisible) {
        TimeTableDialog(onDismissRequest = viewModel::dismissTimeTableEditor)
    }

    if (state.termStartPickerVisible) {
        val inferred = state.termStartDate ?: today
        TermStartDatePickerDialog(
            inferredDate = inferred,
            manualDate = if (state.termStartIsInferred) null else state.termStartDate,
            onConfirm = { date ->
                viewModel.setTermStartDate(date)
                viewModel.dismissTermStartPicker()
            },
            onUseInferred = {
                viewModel.setTermStartDate(null)
                viewModel.dismissTermStartPicker()
            },
            onDismissRequest = viewModel::dismissTermStartPicker,
        )
    }

    state.detailBlock?.let { block ->
        CourseDetailDialog(
            block = block,
            onDismissRequest = viewModel::dismissCourseDetail,
            onDelete = viewModel::deleteCourse,
        )
    }

    // endregion
}

/**
 * 表格分支。
 *
 * 即使这一周没有课也**照常画出网格与时间轴**，只在中间叠一条说明：
 * 空表格比一片空白更能说明「数据没问题，这周真的没课」。
 */
@Composable
private fun TableContent(
    state: ScheduleUiState,
    weekStartDate: LocalDate?,
    today: LocalDate,
    onBlockClick: (CourseBlock) -> Unit,
) {
    val grid = state.grid ?: run {
        CenteredProgress()
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        ScheduleTable(
            grid = grid,
            modifier = Modifier.fillMaxSize(),
            weekStartDate = weekStartDate,
            today = today,
            onBlockClick = onBlockClick,
        )

        if (grid.isEmpty) {
            val notCached = state.schedule?.week(state.selectedWeek) == null
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 32.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
                tonalElevation = 2.dp,
            ) {
                Text(
                    text = if (notCached) {
                        stringResource(R.string.notice_week_not_cached, state.selectedWeek)
                    } else {
                        stringResource(R.string.empty_week_title)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                )
            }
        }
    }
}

/** 无缓存时的引导态（技术设计文档 §3：缓存存在性决定首屏）。 */
@Composable
private fun NoCacheState(onLogin: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.empty_no_cache_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.empty_no_cache_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onLogin) {
            Text(stringResource(R.string.empty_login))
        }
    }
}

/** 列表布局的占位（技术设计文档 §6.1：先实现表格，列表预留抽象）。 */
@Composable
private fun ListPlaceholder() {
    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.schedule_list_placeholder),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun CenteredProgress() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

private const val DAYS_PER_WEEK = 7L
