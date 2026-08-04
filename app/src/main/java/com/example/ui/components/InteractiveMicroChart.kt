package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.NeonIndigo
import com.example.ui.theme.NeonRose

@Composable
fun InteractiveMicroChart(
    priceHistory: List<Double>,
    modifier: Modifier = Modifier,
    isPositive: Boolean = true
) {
    val strokeColor = if (isPositive) NeonEmerald else NeonRose
    val gradientColors = if (isPositive) {
        listOf(NeonEmerald.copy(alpha = 0.35f), Color.Transparent)
    } else {
        listOf(NeonRose.copy(alpha = 0.35f), Color.Transparent)
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        if (priceHistory.size < 2) return@Canvas

        val minPrice = priceHistory.minOrNull() ?: 1.0
        val maxPrice = priceHistory.maxOrNull() ?: 1.0
        val priceRange = (maxPrice - minPrice).coerceAtLeast(0.0001)

        val width = size.width
        val height = size.height

        val stepX = width / (priceHistory.size - 1)

        val strokePath = Path()
        val fillPath = Path()

        priceHistory.forEachIndexed { index, price ->
            val x = index * stepX
            val normalizedY = ((price - minPrice) / priceRange).toFloat()
            val y = height - (normalizedY * (height - 20f)) - 10f

            if (index == 0) {
                strokePath.moveTo(x, y)
                fillPath.moveTo(x, height)
                fillPath.lineTo(x, y)
            } else {
                val prevX = (index - 1) * stepX
                val prevPrice = priceHistory[index - 1]
                val prevNormalizedY = ((prevPrice - minPrice) / priceRange).toFloat()
                val prevY = height - (prevNormalizedY * (height - 20f)) - 10f

                val controlX1 = prevX + (stepX / 2f)
                val controlY1 = prevY
                val controlX2 = prevX + (stepX / 2f)
                val controlY2 = y

                strokePath.cubicTo(controlX1, controlY1, controlX2, controlY2, x, y)
                fillPath.cubicTo(controlX1, controlY1, controlX2, controlY2, x, y)
            }
        }

        fillPath.lineTo(width, height)
        fillPath.close()

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(gradientColors)
        )

        drawPath(
            path = strokePath,
            color = strokeColor,
            style = Stroke(width = 4f)
        )
    }
}
