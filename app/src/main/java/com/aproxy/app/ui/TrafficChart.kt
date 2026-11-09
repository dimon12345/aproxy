package com.aproxy.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.aproxy.app.proxy.TrafficMeter
import com.aproxy.app.proxy.TrafficPoint
import com.aproxy.app.ui.theme.AProxyPalette
import kotlin.math.max

@Composable
fun TrafficChart(
    history: List<TrafficPoint>,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val width = size.width
        val height = size.height
        if (width <= 0f || height <= 0f) return@Canvas

        val gridColor = Color.White.copy(alpha = 0.055f)
        for (index in 0..3) {
            val y = height * index / 3f
            drawLine(
                color = gridColor,
                start = Offset(0f, y),
                end = Offset(width, y),
                strokeWidth = 1.dp.toPx(),
            )
        }

        val data = history.takeLast(TrafficMeter.HISTORY_SIZE)
        if (data.isEmpty()) return@Canvas

        val maxValue = max(
            data.maxOf { max(it.download, it.upload) },
            1024L,
        ).toFloat()

        val slot = width / (TrafficMeter.HISTORY_SIZE - 1)
        val startX = width - (data.size - 1) * slot
        val topPadding = height * 0.08f
        val usableHeight = (height - topPadding - 2.dp.toPx()).coerceAtLeast(1f)

        fun pointsFor(selector: (TrafficPoint) -> Long): List<Offset> =
            data.mapIndexed { index, point ->
                val x = startX + index * slot
                val normalized = (selector(point) / maxValue).coerceIn(0f, 1f)
                Offset(x, topPadding + (1f - normalized) * usableHeight)
            }

        fun smoothPath(points: List<Offset>): Path {
            val path = Path()
            if (points.isEmpty()) return path
            path.moveTo(points.first().x, points.first().y)
            for (index in 1 until points.size) {
                val previous = points[index - 1]
                val current = points[index]
                val middleX = (previous.x + current.x) / 2f
                path.cubicTo(
                    middleX,
                    previous.y,
                    middleX,
                    current.y,
                    current.x,
                    current.y,
                )
            }
            return path
        }

        fun drawSeries(selector: (TrafficPoint) -> Long, color: Color) {
            val points = pointsFor(selector)
            val line = smoothPath(points)
            val area = Path().apply {
                addPath(line)
                lineTo(points.last().x, height)
                lineTo(points.first().x, height)
                close()
            }

            drawPath(
                path = area,
                brush = Brush.verticalGradient(
                    colors = listOf(color.copy(alpha = 0.28f), Color.Transparent),
                    startY = 0f,
                    endY = height,
                ),
            )
            drawPath(
                path = line,
                color = color.copy(alpha = 0.18f),
                style = Stroke(
                    width = 7.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )
            drawPath(
                path = line,
                color = color,
                style = Stroke(
                    width = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )
            drawCircle(
                color = color,
                radius = 3.dp.toPx(),
                center = points.last(),
            )
        }

        drawSeries({ it.download }, AProxyPalette.Cyan)
        drawSeries({ it.upload }, AProxyPalette.Violet)
    }
}
