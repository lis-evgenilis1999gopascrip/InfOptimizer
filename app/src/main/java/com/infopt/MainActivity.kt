package com.infopt

import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.*
import rikka.shizuku.Shizuku
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val io = Executors.newSingleThreadExecutor()

    private val REQ_SHIZUKU = 1001

    private lateinit var tvStatus: TextView
    private lateinit var tvTelemetry: TextView
    private lateinit var tvLog: TextView

    private val permListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == REQ_SHIZUKU) {
            log("Shizuku permission: ${if (grantResult == PackageManager.PERMISSION_GRANTED) "GRANTED" else "DENIED"}")
            refreshStatus()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        tvTelemetry = findViewById(R.id.tvTelemetry)
        tvLog = findViewById(R.id.tvLog)

        Shizuku.addRequestPermissionResultListener(permListener)

        findViewById<MaterialButton>(R.id.btnShizuku).setOnClickListener { requestShizuku() }
        findViewById<MaterialButton>(R.id.btnCompile).setOnClickListener { runTask("AOT-компиляция") { compileAll() } }
        findViewById<MaterialButton>(R.id.btnTrim).setOnClickListener { runTask("Очистка кэша") { Shell.trimCaches() } }
        findViewById<MaterialButton>(R.id.btnKill).setOnClickListener { runTask("Убийство фоновых") { Shell.killBackground() } }
        findViewById<MaterialButton>(R.id.btnAnim).setOnClickListener { runTask("Отключение анимаций") { Shell.setAnimationScale(0f) } }
        findViewById<MaterialButton>(R.id.btnLimit).setOnClickListener { runTask("Ограничение фоновых") { Shell.setBackgroundLimit(2) } }

        refreshStatus()
        startTelemetryLoop()
    }

    private fun requestShizuku() {
        if (!Shizuku.pingBinder()) {
            log("Shizuku не запущен. Установите Shizuku и запустите через ADB/беспроводную отладку.")
            return
        }
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            log("Разрешение уже выдано.")
            refreshStatus()
            return
        }
        Shizuku.requestPermission(REQ_SHIZUKU)
    }

    private fun refreshStatus() {
        val available = try { Shizuku.pingBinder() } catch (e: Throwable) { false }
        val granted = try { Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED } catch (e: Throwable) { false }
        tvStatus.text = when {
            !available -> "Shizuku: НЕ ЗАПУЩЕН"
            !granted   -> "Shizuku: запущен, нет разрешения"
            else       -> "Shizuku: ГОТОВ ✔"
        }
    }

    private fun runTask(name: String, block: () -> String) {
        if (!Shell.isShizukuAvailable()) {
            log("$name: нет Shizuku-доступа.")
            return
        }
        log("→ $name...")
        io.execute {
            val res = try { block() } catch (e: Throwable) { "ERROR: ${e.message}" }
            runOnUiThread { log("[OK] $name\n$res\n") }
        }
    }

    private fun compileAll(): String {
        val pkgs = packageManager.getInstalledPackages(0)
            .filter { it.applicationInfo?.flags?.and(android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0 }
            .mapNotNull { it.packageName }
            .take(15)
        val sb = StringBuilder()
        for (p in pkgs) {
            val r = Shell.compilePackage(p, "speed")
            sb.appendLine("$p → ${if (r.contains("Success")) "OK" else r.trim().take(60)}")
        }
        return sb.toString()
    }

    private fun startTelemetryLoop() {
        scope.launch {
            while (isActive) {
                val text = withContext(Dispatchers.IO) {
                    buildString {
                        appendLine(Monitor.ramInfo(this@MainActivity))
                        appendLine(Monitor.batteryTemp(this@MainActivity))
                        appendLine(Monitor.cpuTemp())
                    }
                }
                tvTelemetry.text = text
                delay(3000)
            }
        }
    }

    private fun log(s: String) {
        tvLog.append("\n$s")
    }

    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeRequestPermissionResultListener(permListener)
        scope.cancel()
        io.shutdown()
    }
}
