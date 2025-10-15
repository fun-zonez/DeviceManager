package com.mystrox.devicemanager

import android.content.BroadcastReceiver
import kotlinx.coroutines.flow.asStateFlow

import android.app.ActivityManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.RandomAccessFile

// Data class combining elements from Response 1 and 4
data class DeviceHealthState(
    val batteryPct: Float = 0f,
    val isCharging: Boolean = false,
    val batteryTemp: Float = 0f,
    val batteryHealth: String = "Unknown",
    val cpuUsage: Float = 0f, // Added from Response 2/4
    val thermalState: String = "Unknown", // Added from Response 2
    val totalRam: Long = 0L,
    val usedRam: Long = 0L,
    val totalStorage: Long = 0L,
    val usedStorage: Long = 0L,
    val uptime: String = "0h 0m",
    val healthScore: Int = 100, // Added from Response 4
    val recommendations: List<String> = emptyList() // Added from Response 2 concept
)

class DeviceHealthViewModel(private val context: Context) : ViewModel() {

    private val _state = MutableStateFlow(DeviceHealthState())
    val state: StateFlow<DeviceHealthState> = _state.asStateFlow()

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent != null) {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                val batteryPct = if (level >= 0 && scale > 0) level * 100 / scale.toFloat() else 0f
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL
                val temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f
                val health = when (intent.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)) {
                    BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
                    BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
                    BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
                    BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over Voltage"
                    BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Unspecified Failure"
                    BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
                    else -> "Unknown"
                }
                _state.value = _state.value.copy(
                    batteryPct = batteryPct,
                    isCharging = isCharging,
                    batteryTemp = temp,
                    batteryHealth = health
                )
            }
        }
    }

    init {
        context.registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        collectSystemStatsPeriodically()
    }

    private fun collectSystemStatsPeriodically() {
        viewModelScope.launch {
            while (true) {
                val newState = updateStateWithSystemStats()
                _state.value = newState
                delay(2000) // Update every 2 seconds
            }
        }
    }

    private fun updateStateWithSystemStats(): DeviceHealthState {
        val currentState = _state.value
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)

        val storagePath = Environment.getDataDirectory()
        val statFs = StatFs(storagePath.path)
        val blockSize = statFs.blockSizeLong
        val totalBlocks = statFs.blockCountLong
        val availableBlocks = statFs.availableBlocksLong
        val totalStorage = totalBlocks * blockSize
        val usedStorage = totalStorage - (availableBlocks * blockSize)

        val totalRam = memoryInfo.totalMem
        val usedRam = totalRam - memoryInfo.availMem

        val uptimeMillis = SystemClock.elapsedRealtime()
        val hours = (uptimeMillis / (1000 * 60 * 60)) % 24
        val minutes = (uptimeMillis / (1000 * 60)) % 60

        val cpuUsage = getCpuUsage()
        val thermalState = getThermalState()

        var healthScore = 100
        // Simple health calculation based on key metrics
        if (currentState.batteryTemp > 45) healthScore -= 20
        if (currentState.batteryPct < 20) healthScore -= 10
        if (usedRam.toFloat() / totalRam > 0.90f) healthScore -= 15
        if (usedStorage.toFloat() / totalStorage > 0.95f) healthScore -= 10
        if (cpuUsage > 85) healthScore -= 15
        if (thermalState != "Normal" && thermalState != "Unknown") healthScore -= 10

        healthScore = healthScore.coerceIn(0, 100)

        val recommendations = mutableListOf<String>()
        if (currentState.batteryTemp > 40) recommendations.add("Battery temperature is high. Close heavy apps.")
        if (currentState.batteryPct < 20) recommendations.add("Battery is low. Consider charging.")
        if (usedRam.toFloat() / totalRam > 0.85f) recommendations.add("RAM usage is high. Close unused apps.")
        if (usedStorage.toFloat() / totalStorage > 0.90f) recommendations.add("Storage is almost full. Clean up space.")
        if (cpuUsage > 80) recommendations.add("CPU usage is high. Check running apps.")
        if (recommendations.isEmpty()) recommendations.add("Device is running well!")

        return currentState.copy(
            totalRam = totalRam,
            usedRam = usedRam,
            totalStorage = totalStorage,
            usedStorage = usedStorage,
            uptime = "${hours}h ${minutes}m",
            cpuUsage = cpuUsage,
            thermalState = thermalState,
            healthScore = healthScore,
            recommendations = recommendations
        )
    }

    private fun getCpuUsage(): Float {
        return try {
            val reader = RandomAccessFile("/proc/stat", "r")
            val load = reader.readLine()
            reader.close()
            val toks = load.split(" ").filter { it.isNotBlank() }
            if (toks.size < 5) return 0.0f
            val idle1 = toks[4].toLongOrNull() ?: 0L
            val cpu1 = toks.drop(1).take(4).sumOf { it.toLongOrNull() ?: 0L } // user, nice, system, idle

            Thread.sleep(360) // Wait for change

            val reader2 = RandomAccessFile("/proc/stat", "r")
            val load2 = reader2.readLine()
            reader2.close()
            val toks2 = load2.split(" ").filter { it.isNotBlank() }
            if (toks2.size < 5) return 0.0f
            val idle2 = toks2[4].toLongOrNull() ?: 0L
            val cpu2 = toks2.drop(1).take(4).sumOf { it.toLongOrNull() ?: 0L }

            val cpuDelta = cpu2 - cpu1
            val idleDelta = idle2 - idle1
            val totalDelta = cpuDelta + idleDelta

            if (totalDelta > 0) {
                (cpuDelta.toFloat() / totalDelta.toFloat()) * 100
            } else 0.0f
        } catch (e: Exception) {
            0.0f
        }
    }

    private fun getThermalState(): String {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                when (powerManager.currentThermalStatus) {
                    PowerManager.THERMAL_STATUS_NONE -> "Normal"
                    PowerManager.THERMAL_STATUS_LIGHT -> "Light Throttling"
                    PowerManager.THERMAL_STATUS_MODERATE -> "Moderate Throttling"
                    PowerManager.THERMAL_STATUS_SEVERE -> "Severe Throttling"
                    PowerManager.THERMAL_STATUS_CRITICAL -> "Critical"
                    PowerManager.THERMAL_STATUS_EMERGENCY -> "Emergency"
                    PowerManager.THERMAL_STATUS_SHUTDOWN -> "Shutdown"
                    else -> "Unknown"
                }
            } else {
                // Fallback for older versions, might try reading thermal zones
                // This is less reliable and often requires root on older devices
                "Not Available (API < 29)"
            }
        } catch (e: Exception) {
            "Unknown"
        }
    }

    override fun onCleared() {
        context.unregisterReceiver(batteryReceiver)
        super.onCleared()
    }
}