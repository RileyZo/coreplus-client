package com.github.riley.coreplus

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.github.riley.coreplus.ui.schedule.ScheduleScreen
import com.github.riley.coreplus.ui.theme.CoreTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * 应用唯一的 Activity。
 *
 * 刻意不使用导航库：整个应用只有「课表」一个页面，登录、作息表、课程详情、
 * 学期起始日选择全部以对话框承载。用条件渲染代替导航图，
 * 少一层抽象也少一个依赖，等真的出现第二个页面再引入也不迟。
 *
 * [enableEdgeToEdge] 让内容延伸到状态栏与导航栏下方；
 * `Scaffold` 与 `TopAppBar` 会自行消费窗口 insets，因此不需要在这里额外加 padding。
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CoreTheme {
                ScheduleScreen()
            }
        }
    }
}
