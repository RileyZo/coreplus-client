package com.github.riley.coreplus.data.local

import com.github.riley.coreplus.domain.model.Course
import com.github.riley.coreplus.domain.model.Schedule
import com.github.riley.coreplus.domain.model.Weekday
import com.github.riley.coreplus.domain.model.WeeklySchedule
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 示例课表数据。
 *
 * 后端尚未就绪时，用于验证表格 UI 是否正确：连课合并、时间轴对齐、周切换、未缓存周的回退提示。
 * 入口在顶部溢出菜单，**仅 debug 构建可见**（由 UI 层用 `BuildConfig.DEBUG` 门控）。
 *
 * 数据与 `docs/API.md` 附录 A 的 mock 响应**完全一致**，
 * 因此后端接上之后，两边渲染出的表格应当一模一样，可直接对照排查。
 *
 * 覆盖的场景：
 * - 周一第 1、2 大节：两节连课（`计算机组成原理`）
 * - 周二第 3、4 大节：两节连课（`数据结构`）
 * - 周四第 1、5 大节：同一天但不相邻，**不应**合并
 * - 周五第 3 大节：同一位教师（李四）的另一门课，**不应**与周一的课合并
 * - 第 2 周比第 1 周少两门课，用于观察周切换后的差异
 * - 周六、周日无课
 * - `totalWeeks = 2`，因此切到第 3 周会触发「未缓存周显示上一周课程并提示」
 */
@Singleton
class SampleScheduleProvider @Inject constructor() {

    /** 生成示例课表。 */
    fun schedule(): Schedule = Schedule(
        totalWeeks = TOTAL_WEEKS,
        weeks = listOf(week1(), week2()),
    )

    private fun week1() = WeeklySchedule(
        weekNumber = 1,
        courses = listOf(
            course("mock-1001", 1, "计算机组成原理", 3.0, "必修", "致远楼605", "李四", Weekday.MONDAY, 1),
            course("mock-1002", 1, "计算机组成原理", 3.0, "必修", "致远楼605", "李四", Weekday.MONDAY, 2),
            course("mock-1003", 1, "大学体育", 1.0, "必修", "体育馆", "王五", Weekday.MONDAY, 4),
            course("mock-1004", 1, "高等数学（上）", 5.0, "必修", "主楼301", "赵六", Weekday.TUESDAY, 1),
            course("mock-1005", 1, "数据结构", 4.0, "必修", "致远楼402", "孙七", Weekday.TUESDAY, 3),
            course("mock-1006", 1, "数据结构", 4.0, "必修", "致远楼402", "孙七", Weekday.TUESDAY, 4),
            course("mock-1007", 1, "线性代数", 3.0, "必修", "主楼205", "周八", Weekday.WEDNESDAY, 2),
            course("mock-1008", 1, "大学英语（三）", 4.0, "必修", "外语楼110", "吴九", Weekday.THURSDAY, 1),
            course("mock-1009", 1, "形势与政策", 0.5, "必修", "报告厅", "郑十", Weekday.THURSDAY, 5),
            course("mock-1010", 1, "操作系统", 3.5, "限选", "致远楼603", "李四", Weekday.FRIDAY, 3),
        ),
    )

    private fun week2() = WeeklySchedule(
        weekNumber = 2,
        courses = listOf(
            course("mock-2001", 2, "计算机组成原理", 3.0, "必修", "致远楼605", "李四", Weekday.MONDAY, 1),
            course("mock-2002", 2, "计算机组成原理", 3.0, "必修", "致远楼605", "李四", Weekday.MONDAY, 2),
            course("mock-2003", 2, "高等数学（上）", 5.0, "必修", "主楼301", "赵六", Weekday.TUESDAY, 1),
            course("mock-2004", 2, "数据结构", 4.0, "必修", "致远楼402", "孙七", Weekday.TUESDAY, 3),
            course("mock-2005", 2, "数据结构", 4.0, "必修", "致远楼402", "孙七", Weekday.TUESDAY, 4),
            course("mock-2006", 2, "线性代数", 3.0, "必修", "主楼205", "周八", Weekday.WEDNESDAY, 2),
            course("mock-2007", 2, "大学英语（三）", 4.0, "必修", "外语楼110", "吴九", Weekday.THURSDAY, 1),
            course("mock-2008", 2, "计算机网络", 3.0, "限选", "致远楼601", "钱十一", Weekday.FRIDAY, 3),
        ),
    )

    @Suppress("LongParameterList") // 示例数据构造器，参数即字段，不对外暴露
    private fun course(
        id: String,
        week: Int,
        name: String,
        credit: Double,
        attribute: String,
        location: String,
        teacher: String,
        weekday: Weekday,
        bigSection: Int,
    ) = Course(
        id = id,
        name = name,
        credit = credit,
        attribute = attribute,
        location = location,
        teacher = teacher,
        week = week,
        weekday = weekday,
        bigSection = bigSection,
    )

    private companion object {
        const val TOTAL_WEEKS = 2
    }
}
