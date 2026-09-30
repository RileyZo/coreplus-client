package com.github.riley.coreplus.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.github.riley.coreplus.domain.model.Course
import com.github.riley.coreplus.domain.model.Schedule
import com.github.riley.coreplus.domain.model.Weekday
import com.github.riley.coreplus.domain.model.WeeklySchedule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * [ScheduleCacheDataSource] 的单元测试，使用 Robolectric 模拟 Android Context。
 *
 * 覆盖：
 * - 写后读往返相等
 * - 写入后 filesDir 下不应残留 .tmp 文件
 * - 损坏降级：手动写入非法 JSON 后 read() 返回 null，且出现 .corrupt 文件
 * - clear() 后 read() 返回 null
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ScheduleCacheDataSourceTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
        explicitNulls = false
        encodeDefaults = true
    }

    private lateinit var dataSource: ScheduleCacheDataSource
    private lateinit var filesDir: File

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        dataSource = ScheduleCacheDataSource(context, json, Dispatchers.IO)
        filesDir = context.filesDir

        // 清理前一次测试残留
        File(filesDir, "schedule_cache.json").delete()
        File(filesDir, "schedule_cache.json.tmp").delete()
        File(filesDir, "schedule_cache.json.corrupt").delete()
    }

    @After
    fun tearDown() {
        File(filesDir, "schedule_cache.json").delete()
        File(filesDir, "schedule_cache.json.tmp").delete()
        File(filesDir, "schedule_cache.json.corrupt").delete()
    }

    /** 构造一份测试用课表。 */
    private fun sampleSchedule() = Schedule(
        totalWeeks = 3,
        weeks = listOf(
            WeeklySchedule(
                weekNumber = 1,
                courses = listOf(
                    Course("id-1", "高等数学", 5.0, "必修", "主楼301", "赵六", 1, Weekday.MONDAY, 1),
                    Course("id-2", "线性代数", 3.0, "必修", "主楼205", "周八", 1, Weekday.WEDNESDAY, 2),
                ),
            ),
            WeeklySchedule(
                weekNumber = 2,
                courses = listOf(
                    Course("id-3", "大学英语", 4.0, "必修", "外语楼110", "吴九", 2, Weekday.TUESDAY, 1),
                ),
            ),
        ),
    )

    @Test
    fun `write then read should return equal schedule`() = runTest {
        val original = sampleSchedule()

        val writeError = dataSource.write(original)
        assertNull("写入不应有错误", writeError)

        val readBack = dataSource.read()
        assertNotNull("读取不应为 null", readBack)
        assertEquals("读取结果应与写入值相等", original, readBack)
    }

    @Test
    fun `write should not leave tmp files behind`() = runTest {
        val schedule = sampleSchedule()

        dataSource.write(schedule)

        val tmpFile = File(filesDir, "schedule_cache.json.tmp")
        assertFalse("写入后不应残留 .tmp 文件", tmpFile.exists())
    }

    @Test
    fun `read should return null when cache file does not exist`() = runTest {
        // 未写入过任何数据
        val result = dataSource.read()

        assertNull("文件不存在时 read() 应返回 null", result)
    }

    @Test
    fun `read should return null and create corrupt file when JSON is invalid`() = runTest {
        // 手动写入非法 JSON
        val cacheFile = File(filesDir, "schedule_cache.json")
        cacheFile.writeText("{ this is not valid json !!! }", Charsets.UTF_8)

        val result = dataSource.read()

        assertNull("非法 JSON 时 read() 应返回 null", result)
        val corruptFile = File(filesDir, "schedule_cache.json.corrupt")
        assertTrue("损坏文件应被备份为 .corrupt", corruptFile.exists())
    }

    @Test
    fun `clear should make read return null`() = runTest {
        // 先写入数据
        dataSource.write(sampleSchedule())
        assertNotNull(dataSource.read())

        // 清除
        dataSource.clear()

        // 验证读取返回 null
        assertNull("clear() 后 read() 应返回 null", dataSource.read())
    }

    @Test
    fun `write multiple times should overwrite correctly`() = runTest {
        val first = Schedule(totalWeeks = 2, weeks = listOf(WeeklySchedule(1)))
        val second = Schedule(totalWeeks = 5, weeks = listOf(WeeklySchedule(1), WeeklySchedule(5)))

        dataSource.write(first)
        dataSource.write(second)

        val readBack = dataSource.read()
        assertEquals("多次写入后应为最后一次的值", second, readBack)
    }

    @Test
    fun `read should return null when file is empty`() = runTest {
        val cacheFile = File(filesDir, "schedule_cache.json")
        cacheFile.writeText("", Charsets.UTF_8)

        val result = dataSource.read()

        assertNull("空文件时 read() 应返回 null", result)
    }

    @Test
    fun `write and read should preserve all fields correctly`() = runTest {
        val original = Schedule(
            totalWeeks = 20,
            weeks = listOf(
                WeeklySchedule(
                    weekNumber = 1,
                    courses = listOf(
                        Course(
                            id = "complex-id-123",
                            name = "计算机组成原理与体系结构",
                            credit = 4.5,
                            attribute = "限选",
                            location = "致远楼605实验室",
                            teacher = "张三丰教授",
                            week = 1,
                            weekday = Weekday.FRIDAY,
                            bigSection = 3,
                        ),
                    ),
                ),
            ),
        )

        dataSource.write(original)
        val readBack = dataSource.read()

        assertEquals(original, readBack)
        val course = readBack!!.weeks[0].courses[0]
        assertEquals("complex-id-123", course.id)
        assertEquals("计算机组成原理与体系结构", course.name)
        assertEquals(4.5, course.credit, 0.001)
        assertEquals("限选", course.attribute)
        assertEquals(Weekday.FRIDAY, course.weekday)
        assertEquals(3, course.bigSection)
    }
}
