package com.rb.cybermonitorpro.ui.sensors

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rb.cybermonitorpro.R
import com.rb.cybermonitorpro.data.model.SensorItemInfo
import com.rb.cybermonitorpro.data.model.SensorTypeMeta
import com.rb.cybermonitorpro.ui.theme.NeonPurple
import com.rb.cybermonitorpro.ui.theme.NeonPurpleBright
import com.rb.cybermonitorpro.ui.theme.SuccessNeon
import com.rb.cybermonitorpro.ui.theme.WarningNeon
import org.koin.androidx.compose.koinViewModel
import com.rb.cybermonitorpro.ui.effects.staggeredSwipe
import com.rb.cybermonitorpro.ui.effects.cardEnlargeBevel

/**
 * 传感器列表页 — 现在通过回调将传感器选择上抛给 MainActivity 以全屏覆盖层展示
 */
@Composable
fun SensorsScreen(
    viewModel: SensorsViewModel = koinViewModel(),
    onNavigateToSensor: (SensorItemInfo) -> Unit = {}
) {
    val sensors by viewModel.sensors.observeAsState(emptyList())

    SensorListContent(
        sensors = sensors,
        onSensorClick = { onNavigateToSensor(it) }
    )
}

@Composable
private fun SensorListContent(
    sensors: List<SensorItemInfo>,
    onSensorClick: (SensorItemInfo) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        var cardIdx = 0
        Text(
            stringResource(R.string.sensor_list_title),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            stringResource(R.string.sensor_list_count, sensors.size),
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        sensors.forEachIndexed { idx, sensor ->
            SensorItemCard(sensor = sensor, onClick = { onSensorClick(sensor) }, modifier = Modifier.staggeredSwipe(cardIdx + idx))
        }
        cardIdx += sensors.size
    }
}

@Composable
private fun SensorItemCard(sensor: SensorItemInfo, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val meta = SensorTypeMeta.fromTypeId(sensor.type)
    val ctx = LocalContext.current

    Card(
        modifier
            .fillMaxWidth()
            .cardEnlargeBevel()

            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    SensorTypeMeta.getDisplayName(sensor.type, ctx),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                // 可监控标识
                if (meta != null) {
                    Text(
                        "\u25B6",
                        fontSize = 14.sp,
                        color = NeonPurpleBright
                    )
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    sensor.vendor.ifEmpty { sensor.name.split(" ").firstOrNull() ?: stringResource(R.string.sensor_unknown_vendor) },
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (sensor.isWakeUp) {
                        Text(
                            stringResource(R.string.sensor_tag_wakeup),
                            fontSize = 11.sp,
                            color = WarningNeon
                        )
                    }
                    if (sensor.isDynamic) {
                        Text(
                            stringResource(R.string.sensor_tag_dynamic),
                            fontSize = 11.sp,
                            color = SuccessNeon
                        )
                    }
                }
            }
            Text(
                stringResource(R.string.sensor_range_label, sensor.maxRange, meta?.unit?.takeIf { it.isNotEmpty() } ?: ""),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}
