package com.mystrox.devicemanager

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mystrox.devicemanager.ui.theme.DeviceManagerTheme
import java.text.CharacterIterator
import java.text.StringCharacterIterator
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DeviceManagerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val viewModel: DeviceHealthViewModel = viewModel { DeviceHealthViewModel(this@MainActivity) }
                    DeviceHealthDashboard(viewModel)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceHealthDashboard(viewModel: DeviceHealthViewModel) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Device Health Dashboard", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            contentPadding = innerPadding,
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                HealthMetricCard(
                    title = "Battery",
                    valueText = "${state.batteryPct.roundToInt()}%",
                    secondaryText = "${state.isCharging} | ${state.batteryHealth}",
                    icon = if (state.isCharging) Icons.Rounded.BatteryChargingFull else Icons.Rounded.BatteryStd,
                    progress = state.batteryPct / 100f,
                    progressColor = when {
                        state.batteryPct > 60 -> Color(0xFF388E3C) // Green
                        state.batteryPct > 20 -> Color(0xFFFBC02D) // Yellow
                        else -> Color(0xFFD32F2F) // Red
                    }
                )
            }
            item {
                HealthMetricCard(
                    title = "Temperature",
                    valueText = "${state.batteryTemp}°C",
                    secondaryText = state.thermalState, // Added thermal state
                    icon = Icons.Rounded.Thermostat,
                    progress = (state.batteryTemp / 50f).coerceIn(0f, 1f), // Assuming 50C is high
                    progressColor = when {
                        state.batteryTemp > 45 -> Color(0xFFD32F2F) // Red
                        state.batteryTemp > 35 -> Color(0xFFFBC02D) // Yellow
                        else -> Color(0xFF1976D2) // Blue
                    }
                )
            }
            item {
                val usedRamPercent = if (state.totalRam > 0) (state.usedRam.toFloat() / state.totalRam.toFloat()) else 0f
                HealthMetricCard(
                    title = "Memory (RAM)",
                    valueText = "${formatBytes(state.usedRam)} / ${formatBytes(state.totalRam)}",
                    secondaryText = "Used | CPU: ${state.cpuUsage.roundToInt()}%", // Added CPU usage
                    icon = Icons.Rounded.Memory,
                    progress = usedRamPercent,
                    progressColor = when {
                        usedRamPercent > 0.85f -> Color(0xFFD32F2F)
                        usedRamPercent > 0.65f -> Color(0xFFFBC02D)
                        else -> Color(0xFF388E3C)
                    }
                )
            }
            item {
                val usedStoragePercent = if (state.totalStorage > 0) (state.usedStorage.toFloat() / state.totalStorage.toFloat()) else 0f
                HealthMetricCard(
                    title = "Storage",
                    valueText = "${formatBytes(state.usedStorage)} / ${formatBytes(state.totalStorage)}",
                    secondaryText = "Full",
                    icon = Icons.Rounded.Storage,
                    progress = usedStoragePercent,
                    progressColor = when {
                        usedStoragePercent > 0.90f -> Color(0xFFD32F2F)
                        usedStoragePercent > 0.75f -> Color(0xFFFBC02D)
                        else -> Color(0xFF388E3C)
                    }
                )
            }
            item {
                HealthMetricCard(
                    title = "Device Uptime",
                    valueText = state.uptime,
                    secondaryText = "Health Score: ${state.healthScore}/100", // Added health score
                    icon = Icons.Rounded.Timer,
                    progress = -1f // Negative progress hides the indicator
                )
            }
            item {
                // Recommendations Card
                Card(
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(text = "Recommendations", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(modifier = Modifier.height(8.dp))
                        state.recommendations.forEach { recommendation ->
                            Text(
                                text = "• $recommendation",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HealthMetricCard(
    title: String,
    valueText: String,
    secondaryText: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    progress: Float,
    progressColor: Color = MaterialTheme.colorScheme.primary
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Column {
                    Text(text = title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        text = valueText,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(text = secondaryText, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
            }
            if (progress >= 0) {
                Spacer(modifier = Modifier.height(16.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    color = progressColor,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }
        }
    }
}

@SuppressLint("DefaultLocale")
fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val ci: CharacterIterator = StringCharacterIterator("kMGTPE")
    var tempBytes = bytes
    while (tempBytes >= 999_950) {
        tempBytes /= 1024
        ci.next()
    }
    return String.format("%.1f %cB", tempBytes / 1024.0, ci.current())
}