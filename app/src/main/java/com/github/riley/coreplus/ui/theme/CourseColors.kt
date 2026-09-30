package com.github.riley.coreplus.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * 课程块的配色：背景色 + 前景色成对给出。
 *
 * 成对而不是「背景 + 自动算前景」，是因为自动反色在中等明度的颜色上很容易翻车
 * （既不够黑也不够白），成对定义能逐色手工校验对比度。
 */
data class CourseColor(val background: Color, val content: Color)

/** 浅色主题调色板。背景是低饱和度的浅色，文字用同色系的深色。 */
private val LightPalette = listOf(
    CourseColor(Color(0xFFDCEBFA), Color(0xFF0D3C6E)),   // 蓝
    CourseColor(Color(0xFFDCEEDD), Color(0xFF1B5E20)),   // 绿
    CourseColor(Color(0xFFFBE3CE), Color(0xFF8C3B00)),   // 橙
    CourseColor(Color(0xFFEBDCF2), Color(0xFF4A148C)),   // 紫
    CourseColor(Color(0xFFD2EEF3), Color(0xFF00565E)),   // 青
    CourseColor(Color(0xFFF8D9E4), Color(0xFF7D0F3F)),   // 粉
    CourseColor(Color(0xFFE7EBCB), Color(0xFF4C5210)),   // 橄榄
    CourseColor(Color(0xFFDCDDF5), Color(0xFF24289C)),   // 靛
    CourseColor(Color(0xFFEADCD2), Color(0xFF5C3521)),   // 棕
    CourseColor(Color(0xFFD6E9E2), Color(0xFF11503C)),   // 墨绿
)

/** 深色主题调色板。背景压暗、文字提亮，避免夜间刺眼。 */
private val DarkPalette = listOf(
    CourseColor(Color(0xFF1E3A5F), Color(0xFFBBD7F5)),
    CourseColor(Color(0xFF1E4026), Color(0xFFBEE0C4)),
    CourseColor(Color(0xFF4E2E12), Color(0xFFF2CDA6)),
    CourseColor(Color(0xFF3A1D4A), Color(0xFFD9BEEA)),
    CourseColor(Color(0xFF113E45), Color(0xFFA9DCE4)),
    CourseColor(Color(0xFF4A1A30), Color(0xFFEFBACB)),
    CourseColor(Color(0xFF3B3F1C), Color(0xFFDCE0AE)),
    CourseColor(Color(0xFF262B5E), Color(0xFFBCC1EC)),
    CourseColor(Color(0xFF43301F), Color(0xFFDFC6AE)),
    CourseColor(Color(0xFF164134), Color(0xFFA9D8C8)),
)

/**
 * 按课程名取一个稳定的配色。
 *
 * **稳定**是关键：同一门课在第 1 周和第 15 周、这次启动和下次启动都是同一个颜色，
 * 用户才能靠颜色快速扫出「这门课在哪几天」。
 *
 * 用 [Math.floorMod] 而不是 `hashCode() % size`：后者在 hashCode 为负时会返回负下标。
 * 也不用 `abs(hashCode)`：`abs(Int.MIN_VALUE)` 仍是负数。
 *
 * 刻意**不**用教师或教室参与哈希——同一门课换教室（调课）时颜色不该变。
 */
@Composable
fun courseColor(courseName: String): CourseColor {
    val palette = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) DarkPalette else LightPalette
    val index = Math.floorMod(courseName.hashCode(), palette.size)
    return palette[index]
}

/** 今天所在列的高亮底色。 */
@Composable
fun todayHighlightColor(): Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)

/** 表格分隔线颜色。 */
@Composable
fun gridLineColor(): Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
