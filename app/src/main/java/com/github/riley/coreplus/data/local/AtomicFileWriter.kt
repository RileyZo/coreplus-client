package com.github.riley.coreplus.data.local

import java.io.File
import java.io.FileOutputStream

/**
 * 原子文件写入工具。
 *
 * 技术设计文档 §9 把「缓存文件写入的原子性」列为待办。课表缓存与作息表都需要它，
 * 因此抽成一个共用对象。
 *
 * 步骤：写入 `<name>.tmp` → `flush` → `fd.sync()` → `renameTo` 覆盖目标。
 *
 * - POSIX 的 `rename` 是原子的，且会替换已存在的目标文件，因此**不存在「新旧文件都没有」的窗口**：
 *   进程在任意时刻被杀，磁盘上留下的要么是完整的旧文件，要么是完整的新文件。
 * - `fd.sync()` 不可省略。只 rename 不刷盘时，掉电可能得到一个 0 字节的目标文件——
 *   对课表缓存而言就是「一学期的数据没了」。
 *
 * 本工具**不加锁**。并发写入同一目标时需要调用方自行用 `Mutex` 串行化，
 * 否则两个协程会互相覆盖同一个 `.tmp` 文件。
 */
internal object AtomicFileWriter {

    private const val TMP_SUFFIX = ".tmp"

    /**
     * 原子地把 [content] 写入 [target]。
     *
     * @throws java.io.IOException 写入或重命名失败时抛出；失败时临时文件会被清理，目标文件保持原样
     */
    fun write(target: File, content: String) {
        val tmp = File(target.parentFile, target.name + TMP_SUFFIX)
        try {
            FileOutputStream(tmp).use { output ->
                output.write(content.toByteArray(Charsets.UTF_8))
                output.flush()
                output.fd.sync()
            }
        } catch (e: Exception) {
            runCatching { tmp.delete() }
            throw e
        }

        if (!tmp.renameTo(target)) {
            runCatching { tmp.delete() }
            error("无法将 ${tmp.name} 重命名为 ${target.name}")
        }
    }
}
