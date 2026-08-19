package com.rb.cybermonitorpro.ui.sensors

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import com.rb.cybermonitorpro.ui.nightlight.rememberHdrScrollState
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rb.cybermonitorpro.R
import com.rb.cybermonitorpro.data.model.SensorItemInfo
import com.rb.cybermonitorpro.data.model.SensorTypeMeta
import com.rb.cybermonitorpro.ui.effects.LocalSharedTransitionScope
import com.rb.cybermonitorpro.ui.effects.cardGradientBorder
import com.rb.cybermonitorpro.ui.effects.cardRipple
import com.rb.cybermonitorpro.ui.theme.NeonPurple
import com.rb.cybermonitorpro.ui.theme.NeonPurpleBright
import com.rb.cybermonitorpro.ui.theme.SuccessNeon
import com.rb.cybermonitorpro.ui.theme.WarningNeon
import org.koin.androidx.compose.koinViewModel
import com.rb.cybermonitorpro.ui.effects.staggeredSwipe

/**
 * 传感器列表页 — 现在通过回调将传感器选择上抛给 MainActivity 以全屏覆盖层展示
 * F3: 回调携带卡片中心触点坐标（boundsInRoot），覆盖层从卡片中心圆形展开
 */
@Composable
fun SensorsScreen(
    viewModel: SensorsViewModel = koinViewModel(),
    onNavigateToSensor: (SensorItemInfo, Offset) -> Unit = { _, _ -> }
) {
    val sensors by viewModel.sensors.observeAsState(emptyList())

    SensorListContent(
        sensors = sensors,
        onSensorClick = { sensor, origin -> onNavigateToSensor(sensor, origin) }
    )
}

@Composable
private fun SensorListContent(
    sensors: List<SensorItemInfo>,
    onSensorClick: (SensorItemInfo, Offset) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberHdrScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
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
            SensorItemCard(
                sensor = sensor,
                onClick = { origin -> onSensorClick(sensor, origin) },
                modifier = Modifier.staggeredSwipe(cardIdx + idx)
            )
        }
        cardIdx += sensors.size
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun SensorItemCard(sensor: SensorItemInfo, onClick: (Offset) -> Unit, modifier: Modifier = Modifier) {
    val meta = SensorTypeMeta.fromTypeId(sensor.type)
    val ctx = LocalContext.current
    val sharedScope = LocalSharedTransitionScope.current
    // F3: 卡片中心触点（boundsInRoot; RIPPLE-04: 偏移异常时降级 positionInWindow 换算）
    var cardCenter by remember { mutableStateOf(Offset.Zero) }

    Card(
        // ★ F5-2: sharedElement 插在修饰链头 — 后接 staggeredSwipe→cardGradientBorder→cardRipple 链序不动;
        //   key 与 SensorDetailContent 标题容器一致 ("sensor_"+sensorId); scope 为 null 时优雅降级
        Modifier
            .then(
                if (sharedScope != null) with(sharedScope) {
                    Modifier.sharedElement(
                        rememberSharedContentState(key = "sensor_${sensor.sensorId}"),
                        boundsTransform = { _, _ ->
                            spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)
                        }
                    )
                } else Modifier
            )
            .then(modifier)
            .fillMaxWidth()
            .onGloballyPositioned { cardCenter = it.boundsInRoot().center }
            .cardGradientBorder(20.dp, hdrHighlight = true)
            .cardRipple(onClick = { onClick(cardCenter) }),
        shape = RoundedCornerShape(20.dp),
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
