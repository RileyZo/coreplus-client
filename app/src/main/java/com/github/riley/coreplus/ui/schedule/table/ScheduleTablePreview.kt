package com.github.riley.coreplus.ui.schedule.table

import android.content.res.Configuration
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.github.riley.coreplus.data.local.SampleScheduleProvider
import com.github.riley.coreplus.domain.model.TimeTableDefaults
import com.github.riley.coreplus.domain.schedule.ScheduleGrid
import com.github.riley.coreplus.domain.schedule.ScheduleGridBuilder
import com.github.riley.coreplus.ui.theme.CoreTheme
import java.time.LocalDate

/**
 * [ScheduleTable] 的预览。
 *
 * 数据取自 [SampleScheduleProvider]，与 `docs/API.md` 附录 A 的 mock 响应一致，
 * 因此预览里看到的效果就是后端接上之后的效果。
 *
 * 这几个预览重点验证三件事：
 * 1. **连课合并**——周一第 1、2 大节与周二第 3、4 大节各应渲染成一个跨两行的高块
 * 2. **时间轴对齐**——左侧时刻应压在横向网格线上，小节编号落在两线中间
 * 3. **从后往前隐藏**——块高度不足时先隐地点、再隐教师，课程名始终保留
 */
private fun sampleGrid(week: Int): ScheduleGrid {
    val schedule = SampleScheduleProvider().schedule()
    return ScheduleGridBuilder.build(schedule.week(week), TimeTableDefaults.summer)
}

/** 预览固定用的「今天」，避免每次渲染结果都不一样。 */
private val PreviewToday: LocalDate = LocalDate.of(2026, 9, 2)

/** 预览固定用的学期第一周星期一。 */
private val PreviewWeekStart: LocalDate = LocalDate.of(2026, 8, 31)

@Preview(name = "表格·第1周（含连课合并）", showBackground = true, widthDp = 400, heightDp = 780)
@Composable
private fun ScheduleTableWeek1Preview() {
    CoreTheme(dynamicColor = false) {
        Surface(modifier = Modifier.fillMaxSize()) {
            ScheduleTable(
                grid = sampleGrid(1),
                modifier = Modifier.fillMaxSize(),
                weekStartDate = PreviewWeekStart,
                today = PreviewToday,
            )
        }
    }
}

@Preview(name = "表格·第1周（不显示周末）", showBackground = true, widthDp = 400, heightDp = 780)
@Composable
private fun ScheduleTableWeek1NoWeekendPreview() {
    CoreTheme(dynamicColor = false) {
        Surface(modifier = Modifier.fillMaxSize()) {
            ScheduleTable(
                grid = sampleGrid(1),
                modifier = Modifier.fillMaxSize(),
                weekStartDate = PreviewWeekStart,
                today = PreviewToday,
                showWeekend = false,
            )
        }
    }
}

@Preview(name = "表格·第2周（课程较少）", showBackground = true, widthDp = 400, heightDp = 780)
@Composable
private fun ScheduleTableWeek2Preview() {
    CoreTheme(dynamicColor = false) {
        Surface(modifier = Modifier.fillMaxSize()) {
            ScheduleTable(
                grid = sampleGrid(2),
                modifier = Modifier.fillMaxSize(),
                // 今天不在这一周内，因此不应有任何列被高亮
                weekStartDate = PreviewWeekStart.plusDays(7),
                today = PreviewToday,
            )
        }
    }
}

@Preview(name = "表格·未缓存周（空网格）", showBackground = true, widthDp = 400, heightDp = 780)
@Composable
private fun ScheduleTableEmptyPreview() {
    CoreTheme(dynamicColor = false) {
        Surface(modifier = Modifier.fillMaxSize()) {
            ScheduleTable(
                // 第 3 周不在示例数据里，week() 返回 null → 空网格
                grid = sampleGrid(3),
                modifier = Modifier.fillMaxSize(),
                weekStartDate = null,
                today = PreviewToday,
            )
        }
    }
}

@Preview(name = "表格·深色主题", showBackground = true, widthDp = 400, heightDp = 780, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ScheduleTableDarkPreview() {
    CoreTheme(dynamicColor = false, darkTheme = true) {
        Surface(modifier = Modifier.fillMaxSize()) {
            ScheduleTable(
                grid = sampleGrid(1),
                modifier = Modifier.fillMaxSize(),
                weekStartDate = PreviewWeekStart,
                today = PreviewToday,
            )
        }
    }
}
