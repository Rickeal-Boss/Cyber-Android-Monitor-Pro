package com.example.deviceinfoviewer.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.deviceinfoviewer.ui.theme.NeonPurple
import org.koin.androidx.compose.koinViewModel

private data class IntervalOption(val label: String, val ms: Long)

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = koinViewModel()) {
    val options = listOf(
        IntervalOption("0.5 \u79d2", 500L),
        IntervalOption("1 \u79d2", 1000L),
        IntervalOption("2 \u79d2", 2000L)
    )
    var selected by remember { mutableStateOf(viewModel.getIntervalMs()) }

    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("\u8bbe\u7f6e", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)

        Text("\u5237\u65b0\u9891\u7387", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

        options.forEach { option ->
            val isSelected = selected == option.ms
            Card(
                Modifier.fillMaxWidth().clickable {
                    selected = option.ms
                    viewModel.setIntervalMs(option.ms)
                },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) NeonPurple.copy(alpha = 0.15f)
                    else MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = isSelected,
                        onClick = {
                            selected = option.ms
                            viewModel.setIntervalMs(option.ms)
                        },
                        colors = RadioButtonDefaults.colors(selectedColor = NeonPurple)
                    )
                    Text(option.label, fontSize = 16.sp,
                        color = if (isSelected) NeonPurple else MaterialTheme.colorScheme.onSurface,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }

        // App info
        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Cyber Android Monitor Pro (Device Info Viewer)", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("v2.0.202.0", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Kotlin 2.1.0 · Compose · Batman Theme",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                Text("by Rickeal-Boss",
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    color = NeonPurple)
            }
        }
    }
}
