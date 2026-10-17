package com.infopt

import android.os.ParcelFileDescriptor
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

object Shell {

    fun isShizukuAvailable(): Boolean = try {
        Shizuku.pingBinder() && Shizuku.checkSelfPermission() == 0
    } catch (e: Throwable) { false }

    /**
     * Выполняет shell-команду через Shizuku (root-эквивалент без root).
     * Возвращает stdout + stderr.
     */
    fun exec(cmd: String): String {
        return try {
            // newProcess привилегированный, требует api Shizuku 13
            val process = Shizuku.newProcess(arrayOf("sh", "-c", cmd), null, null)
            val stdout = BufferedReader(InputStreamReader(process.inputStream)).readText()
            val stderr = BufferedReader(InputStreamReader(process.errorStream)).readText()
            process.waitFor()
            if (stderr.isNotBlank()) "$stdout\n[stderr] $stderr" else stdout
        } catch (e: Throwable) {
            "ERROR: ${e.message}"
        }
    }

    // ---- Высокоуровневые операции ----

    fun compilePackage(pkg: String, mode: String = "speed"): String =
        exec("cmd package compile -m $mode -f $pkg")

    fun trimCaches(mb: Int = 4096): String =
        exec("pm trim-caches ${mb}M")

    fun killBackground(): String =
        exec("am kill-all")

    fun setAnimationScale(scale: Float): String {
        val s = scale.toString()
        exec("settings put global window_animation_scale $s")
        exec("settings put global transition_animation_scale $s")
        exec("settings put global animator_duration_scale $s")
        return "Анимации установлены на $s"
    }

    fun setBackgroundLimit(limit: Int): String {
        val mode = when (limit) {
            0 -> "0"
            1 -> "1"
            2 -> "2"
            3 -> "3"
            4 -> "4"
            else -> "-1"
        }
        return exec("settings put global cached_apps_freezer enabled")
            .also { exec("settings put global activity_manager_constants max_cached_processes=$limit") }
    }

    fun dropCaches(): String =
        exec("sync; echo 3 > /proc/sys/vm/drop_caches")
}
