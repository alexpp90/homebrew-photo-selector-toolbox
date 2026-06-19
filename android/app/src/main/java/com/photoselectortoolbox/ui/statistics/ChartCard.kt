package com.photoselectortoolbox.ui.statistics

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrykandpatrick.vico.core.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottomAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStartAxis
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.core.cartesian.axis.AxisItemPlacer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.columnSeries
import com.photoselectortoolbox.ui.theme.Indigo500
import com.photoselectortoolbox.ui.theme.Zinc400
import com.photoselectortoolbox.ui.theme.Zinc700
import com.photoselectortoolbox.ui.theme.Zinc800

@Composable
fun ChartCard(
    title: String,
    data: Map<String, Int>,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Zinc800,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 12.dp),
            )

            if (data.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No data",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                }
            } else {
                VicoColumnChart(
                    data = data,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                )
            }
        }
    }
}

@Composable
private fun VicoColumnChart(
    data: Map<String, Int>,
    modifier: Modifier = Modifier,
) {
    val keys = remember(data) { data.keys.toList() }
    val values = remember(data) { data.values.map { it.toFloat() } }

    val modelProducer = remember { CartesianChartModelProducer.build() }

    LaunchedEffect(data) {
        modelProducer.tryRunTransaction {
            columnSeries {
                series(values)
            }
        }
    }

    val bottomAxisValueFormatter = CartesianValueFormatter { x, _, _ ->
        keys.getOrElse(x.toInt()) { "" }
    }

    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberColumnCartesianLayer(
                columnProvider = ColumnCartesianLayer.ColumnProvider.series(
                    rememberLineComponent(
                        color = Indigo500,
                        thickness = 16.dp,
                        shape = remember {
                            com.patrykandpatrick.vico.core.common.shape.Shape.rounded(
                                topLeftPercent = 40,
                                topRightPercent = 40,
                            )
                        },
                    ),
                ),
            ),
            startAxis = rememberStartAxis(
                label = rememberTextComponent(
                    color = Zinc400,
                    textSize = 10.sp,
                ),
                axis = rememberLineComponent(color = Zinc700),
                tick = rememberLineComponent(color = Zinc700),
                guideline = rememberLineComponent(color = Zinc700.copy(alpha = 0.3f)),
            ),
            bottomAxis = rememberBottomAxis(
                label = rememberTextComponent(
                    color = Zinc400,
                    textSize = 9.sp,
                ),
                axis = rememberLineComponent(color = Zinc700),
                tick = rememberLineComponent(color = Zinc700),
                guideline = null,
                valueFormatter = bottomAxisValueFormatter,
                itemPlacer = remember {
                    AxisItemPlacer.Horizontal.default(
                        spacing = if (keys.size > 10) 2 else 1,
                        addExtremeLabelPadding = true,
                    )
                },
            ),
        ),
        modelProducer = modelProducer,
        modifier = modifier,
        scrollState = rememberVicoScrollState(scrollEnabled = keys.size > 8),
        zoomState = rememberVicoZoomState(zoomEnabled = false),
    )
}
