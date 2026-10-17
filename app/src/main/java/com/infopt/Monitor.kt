package com.infopt

import android.app.ActivityManager
import android.content.Context
import java.io.File
import java.io.RandomAccessFile

object Monitor {

    fun ramInfo(ctx: Context): String {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        val usedMb = (mi.totalMem - mi.availMem) / (1024 * 1024)
        val totalMb = mi.totalMem / (1024 * 1024)
        return "RAM: $usedMb / $totalMb МБ"
    }

    fun cpuLoad(): String {
        return try {
            val reader = RandomAccessFile("/proc/stat", "r")
            val line = reader.readLine()
            reader.close()
            val parts = line.split("\\s+".toRegex()).drop(1).mapNotNull { it.toLongOrNull() }
            val idle = parts.getOrElse(3) { 0L }
            val total = parts.sum()
            "CPU: загружен (тик idle=$idle, total=$total)"
        } catch (e: Exception) { "CPU: н/д" }
    }

    fun batteryTemp(ctx: Context): String {
        val intent = ctx.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
        val t = intent?.getIntExtra(android.os.BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        return "Батарея: ${"%.1f".format(t / 10.0)} °C"
    }

    fun cpuTemp(): String {
        val zones = File("/sys/class/thermal").listFiles { f -> f.name.startsWith("thermal_zone") } ?: return "CPU: н/д"
        val parts = mutableListOf<String>()
        for (z in zones.take(6)) {
            val type = File(z, "type").readTextOrNull() ?: continue
            val temp = File(z, "temp").readTextOrNull()?.trim()?.toIntOrNull() ?: continue
            parts += "$type=${temp / 1000}°C"
        }
        return if (parts.isEmpty()) "CPU: н/д" else "Температуры: ${parts.joinToString(", ")}"
    }

    private fun File.readTextOrNull() = try { readText() } catch (e: Exception) { null }
}
