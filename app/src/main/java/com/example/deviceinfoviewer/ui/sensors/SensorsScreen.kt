package com.example.deviceinfoviewer.ui.sensors

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.deviceinfoviewer.data.model.SensorItemInfo
import com.example.deviceinfoviewer.ui.theme.NeonPurple
import com.example.deviceinfoviewer.ui.theme.NeonPurpleBright
import org.koin.androidx.compose.koinViewModel

@Composable
fun SensorsScreen(viewModel: SensorsViewModel = koinViewModel()) {
    val sensors by viewModel.sensors.observeAsState(emptyList())

    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("\u4f20\u621f\u5668\u4fe1\u606f", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        Text("${sensors.size} \u4e2a\u4f20\u621f\u5668", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

        sensors.forEach { sensor ->
            SensorItemCard(sensor)
        }
    }
}

@Composable
private fun SensorItemCard(sensor: SensorItemInfo) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(sensor.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                Text(sensorTypeName(sensor.type), fontSize = 12.sp, color = NeonPurple)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(sensor.vendor.ifEmpty { "\u672a\u77e5\u5382\u5546" }, fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Power: %.2f mA".format(sensor.powerMa), fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("\u8303\u56f4: 0 \u2013 %.2f".format(sensor.maxRange),
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
        }
    }
}

private fun sensorTypeName(type: Int): String = when (type) {
    1 -> "加速度计"; 2 -> "磁力计"; 3 -> "方向"; 4 -> "陀螺仪"
    5 -> "光线"; 6 -> "压力"; 7 -> "温度"; 8 -> "接近"
    9 -> "重力"; 10 -> "线性加速度"; 11 -> "旋转矢量"
    12 -> "湿度"; 13 -> "环境温度"; 14 -> "磁场(未校准)"
    15 -> "游戏旋转矢量"; 16 -> "陀螺仪(未校准)"
    else -> "传感器 $type"
}
