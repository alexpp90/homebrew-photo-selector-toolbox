package com.photoselectortoolbox.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.photoselectortoolbox.domain.model.CompositionGuideType
import com.photoselectortoolbox.domain.model.CompositionResult
import com.photoselectortoolbox.domain.model.DetectedLine
import com.photoselectortoolbox.domain.model.SpiralOrientation
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.tan

/**
 * Pure 2D vector geometry definitions and mathematical solvers for photographic composition guides.
 *
 * Implements:
 * - Aspect ratio bounding box solver preventing letterbox/pillarbox drift.
 * - Rule of Thirds grid lines and power point nodes.
 * - Golden Ratio Phi grid (0.382 and 0.618).
 * - Fibonacci / Golden spiral geometry with 4-way focal eye orientation matrix reflection.
 * - Baroque and Sinister diagonals with strictly orthogonal reciprocal harmonic vectors.
 * - Center crosshair and horizon leveling reference lines.
 */
object CompositionGeometry {

    data class DisplayBounds(
        val left: Float,
        val top: Float,
        val width: Float,
        val height: Float
    )

    data class Point2D(val x: Float, val y: Float)
    data class Line2D(val startX: Float, val startY: Float, val endX: Float, val endY: Float)
    data class Rect2D(val left: Float, val top: Float, val right: Float, val bottom: Float)

    const val PHI = 1.6180339887f
    const val PHI_INV = 0.6180339887f
    const val PHI_INV_SQ = 0.3819660113f

    /**
     * Solves the exact displayed bounding box for an image within a container,
     * maintaining [aspectRatio] (width / height) and centering the image.
     *
     * Eliminates letterbox (horizontal bars) and pillarbox (vertical bars) alignment drift.
     */
    fun calculateDisplayedBounds(
        containerWidth: Float,
        containerHeight: Float,
        aspectRatio: Float
    ): DisplayBounds {
        require(containerWidth > 0f && containerHeight > 0f && aspectRatio > 0f) {
            "Container dimensions and aspect ratio must be strictly positive"
        }
        val displayedWidth = minOf(containerWidth, containerHeight * aspectRatio)
        val displayedHeight = displayedWidth / aspectRatio
        val left = (containerWidth - displayedWidth) / 2f
        val top = (containerHeight - displayedHeight) / 2f
        return DisplayBounds(
            left = left,
            top = top,
            width = displayedWidth,
            height = displayedHeight
        )
    }

    /**
     * Computes the 4 grid lines for Rule of Thirds (vertical at 1/3, 2/3 and horizontal at 1/3, 2/3).
     */
    fun getRuleOfThirdsLines(width: Float, height: Float): List<Line2D> {
        val w3 = width / 3f
        val w23 = 2f * width / 3f
        val h3 = height / 3f
        val h23 = 2f * height / 3f
        return listOf(
            Line2D(w3, 0f, w3, height),
            Line2D(w23, 0f, w23, height),
            Line2D(0f, h3, width, h3),
            Line2D(0f, h23, width, h23)
        )
    }

    /**
     * Computes the 4 intersection "Power Points" for Rule of Thirds.
     */
    fun getRuleOfThirdsPowerPoints(width: Float, height: Float): List<Point2D> {
        val w3 = width / 3f
        val w23 = 2f * width / 3f
        val h3 = height / 3f
        val h23 = 2f * height / 3f
        return listOf(
            Point2D(w3, h3),
            Point2D(w23, h3),
            Point2D(w3, h23),
            Point2D(w23, h23)
        )
    }

    /**
     * Computes the 4 Golden Ratio (Phi) grid lines at ~0.382 and ~0.618.
     */
    fun getGoldenRatioLines(width: Float, height: Float): List<Line2D> {
        val x1 = width * PHI_INV_SQ
        val x2 = width * PHI_INV
        val y1 = height * PHI_INV_SQ
        val y2 = height * PHI_INV
        return listOf(
            Line2D(x1, 0f, x1, height),
            Line2D(x2, 0f, x2, height),
            Line2D(0f, y1, width, y1),
            Line2D(0f, y2, width, y2)
        )
    }

    /**
     * Computes the 4 Golden Ratio intersections.
     */
    fun getGoldenRatioIntersections(width: Float, height: Float): List<Point2D> {
        val x1 = width * PHI_INV_SQ
        val x2 = width * PHI_INV
        val y1 = height * PHI_INV_SQ
        val y2 = height * PHI_INV
        return listOf(
            Point2D(x1, y1),
            Point2D(x2, y1),
            Point2D(x1, y2),
            Point2D(x2, y2)
        )
    }

    /**
     * Computes Baroque diagonal (bottom-left to top-right) and its two perpendicular reciprocal vectors.
     */
    fun getBaroqueDiagonals(width: Float, height: Float): List<Line2D> {
        val denom = width * width + height * height
        // Main Baroque diagonal: (0, H) -> (W, 0)
        val main = Line2D(0f, height, width, 0f)
        // Reciprocal from Top-Left (0, 0)
        val rTL = Line2D(0f, 0f, width * height * height / denom, width * width * height / denom)
        // Reciprocal from Bottom-Right (W, H)
        val rBR = Line2D(width, height, width * width * width / denom, height * height * height / denom)
        return listOf(main, rTL, rBR)
    }

    /**
     * Computes Sinister diagonal (top-left to bottom-right) and its two perpendicular reciprocal vectors.
     */
    fun getSinisterDiagonals(width: Float, height: Float): List<Line2D> {
        val denom = width * width + height * height
        // Main Sinister diagonal: (0, 0) -> (W, H)
        val main = Line2D(0f, 0f, width, height)
        // Reciprocal from Top-Right (W, 0)
        val rTR = Line2D(width, 0f, width * width * width / denom, width * width * height / denom)
        // Reciprocal from Bottom-Left (0, H)
        val rBL = Line2D(0f, height, width * height * height / denom, height * height * height / denom)
        return listOf(main, rTR, rBL)
    }

    /**
     * Tests whether two 2D line segments are strictly orthogonal (perpendicular).
     */
    fun isOrthogonal(line1: Line2D, line2: Line2D, tolerance: Float = 1e-4f): Boolean {
        val dx1 = line1.endX - line1.startX
        val dy1 = line1.endY - line1.startY
        val dx2 = line2.endX - line2.startX
        val dy2 = line2.endY - line2.startY
        val dot = dx1 * dx2 + dy1 * dy2
        val mag1 = hypot(dx1.toDouble(), dy1.toDouble()).toFloat()
        val mag2 = hypot(dx2.toDouble(), dy2.toDouble()).toFloat()
        if (mag1 == 0f || mag2 == 0f) return false
        val normalizedDot = abs(dot) / (mag1 * mag2)
        return normalizedDot < tolerance
    }

    /**
     * Computes points along the continuous logarithmic Fibonacci spiral,
     * applying the 4-way orientation flip matrix around the center.
     */
    fun getFibonacciSpiralPoints(
        orientation: SpiralOrientation,
        width: Float = 1f,
        height: Float = 1f,
        steps: Int = 120
    ): List<Point2D> {
        val b = 0.3063489 // ln(phi) / (pi / 2)
        val thetaStart = 2.5 * PI
        val thetaEnd = -2.0 * PI
        val eyeX = 0.7236068f
        val eyeY = 0.618034f
        val scaleRadius = 0.95f

        val points = ArrayList<Point2D>(steps)
        for (i in 0 until steps) {
            val t = i.toDouble() / (steps - 1)
            val theta = thetaStart + t * (thetaEnd - thetaStart)
            val r = (scaleRadius * exp(b * theta) / exp(b * thetaStart)).toFloat()

            val normX = (eyeX + r * cos(theta).toFloat()).coerceIn(0f, 1f)
            val normY = (eyeY + r * sin(theta).toFloat()).coerceIn(0f, 1f)

            val flipX = orientation == SpiralOrientation.TOP_LEFT || orientation == SpiralOrientation.BOTTOM_LEFT
            val flipY = orientation == SpiralOrientation.TOP_LEFT || orientation == SpiralOrientation.TOP_RIGHT

            val x = if (flipX) (1f - normX) * width else normX * width
            val y = if (flipY) (1f - normY) * height else normY * height

            points.add(Point2D(x, y))
        }
        return points
    }

    /**
     * Computes the recursive golden subdivision rectangles for [orientation].
     */
    fun getFibonacciSubdivisionRects(
        orientation: SpiralOrientation,
        width: Float = 1f,
        height: Float = 1f
    ): List<Rect2D> {
        // Base canonical rectangles for BOTTOM_RIGHT
        val rawRects = listOf(
            Rect2D(0f, 0f, PHI_INV, 1f),
            Rect2D(PHI_INV, 0f, 1f, PHI_INV),
            Rect2D(PHI_INV + PHI_INV_SQ * PHI_INV, PHI_INV, 1f, 1f),
            Rect2D(PHI_INV, PHI_INV + PHI_INV_SQ * PHI_INV, PHI_INV + PHI_INV_SQ * PHI_INV, 1f)
        )

        val flipX = orientation == SpiralOrientation.TOP_LEFT || orientation == SpiralOrientation.BOTTOM_LEFT
        val flipY = orientation == SpiralOrientation.TOP_LEFT || orientation == SpiralOrientation.TOP_RIGHT

        return rawRects.map { r ->
            val l = if (flipX) (1f - r.right) * width else r.left * width
            val right = if (flipX) (1f - r.left) * width else r.right * width
            val top = if (flipY) (1f - r.bottom) * height else r.top * height
            val bottom = if (flipY) (1f - r.top) * height else r.bottom * height
            Rect2D(minOf(l, right), minOf(top, bottom), maxOf(l, right), maxOf(top, bottom))
        }
    }

    /**
     * Computes Center Crosshair lines and calibration ticks.
     */
    fun getCenterCrosshairLines(width: Float, height: Float): List<Line2D> {
        val cx = width / 2f
        val cy = height / 2f
        return listOf(
            Line2D(0f, cy, width, cy),
            Line2D(cx, 0f, cx, height)
        )
    }

    /**
     * Computes calibration ticks along the horizontal and vertical center axes at 10%, 20%, 30%, 40% offsets.
     */
    fun getCrosshairCalibrationTicks(width: Float, height: Float, tickLength: Float = 10f): List<Line2D> {
        val cx = width / 2f
        val cy = height / 2f
        val ticks = mutableListOf<Line2D>()
        val offsets = listOf(0.1f, 0.2f, 0.3f, 0.4f)

        for (offset in offsets) {
            // Horizontal axis vertical ticks
            val xPos = cx + offset * width
            val xNeg = cx - offset * width
            ticks.add(Line2D(xPos, cy - tickLength / 2f, xPos, cy + tickLength / 2f))
            ticks.add(Line2D(xNeg, cy - tickLength / 2f, xNeg, cy + tickLength / 2f))

            // Vertical axis horizontal ticks
            val yPos = cy + offset * height
            val yNeg = cy - offset * height
            ticks.add(Line2D(cx - tickLength / 2f, yPos, cx + tickLength / 2f, yPos))
            ticks.add(Line2D(cx - tickLength / 2f, yNeg, cx + tickLength / 2f, yNeg))
        }
        return ticks
    }
}

/**
 * Compose canvas overlay rendering photographic vector guides and OpenCV analysis detections.
 *
 * Automatically centers and bounds guides to the [imageAspectRatio], eliminating any
 * letterbox or pillarbox misalignment.
 */
@Composable
fun CompositionOverlay(
    modifier: Modifier = Modifier,
    imageAspectRatio: Float = 3f / 2f,
    activeGuides: Set<CompositionGuideType> = emptySet(),
    compositionResult: CompositionResult? = null,
    showLeadingLines: Boolean = true,
    showMassDistribution: Boolean = true
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .testTag("composition_overlay_container")
    ) {
        val containerWidth = maxWidth.value
        val containerHeight = maxHeight.value

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .testTag("composition_overlay_canvas")
        ) {
            if (size.width <= 0f || size.height <= 0f || imageAspectRatio <= 0f) return@Canvas

            val bounds = CompositionGeometry.calculateDisplayedBounds(
                containerWidth = size.width,
                containerHeight = size.height,
                aspectRatio = imageAspectRatio
            )

            withTransform({
                translate(left = bounds.left, top = bounds.top)
                clipRect(left = 0f, top = 0f, right = bounds.width, bottom = bounds.height)
            }) {
                val w = bounds.width
                val h = bounds.height

                // 1. Draw Active Vector Composition Guides
                for (guide in activeGuides) {
                    when (guide) {
                        is CompositionGuideType.RuleOfThirds -> {
                            drawRuleOfThirds(w, h)
                        }
                        is CompositionGuideType.GoldenRatio -> {
                            drawGoldenRatio(w, h)
                        }
                        is CompositionGuideType.FibonacciSpiral -> {
                            drawFibonacciSpiral(w, h, guide.orientation)
                        }
                        is CompositionGuideType.BaroqueSinister -> {
                            drawBaroqueSinister(w, h)
                        }
                        is CompositionGuideType.CrosshairHorizon -> {
                            drawCrosshairHorizon(w, h)
                        }
                    }
                }

                // 2. Draw OpenCV Computer Vision Analysis Overlays
                compositionResult?.let { result ->
                    if (showMassDistribution) {
                        drawMassDistribution(w, h, result)
                    }
                    if (showLeadingLines) {
                        drawLeadingLines(w, h, result.leadingLines)
                    }
                }
            }
        }
    }
}

// ── Private Canvas Renderers ──────────────────────────────────────────────────

private fun DrawScope.drawRuleOfThirds(width: Float, height: Float) {
    val guideColor = Color(0xDDE0E0E0)
    val strokeWidth = 1.5.dp.toPx()

    val lines = CompositionGeometry.getRuleOfThirdsLines(width, height)
    for (line in lines) {
        drawLine(
            color = guideColor,
            start = Offset(line.startX, line.startY),
            end = Offset(line.endX, line.endY),
            strokeWidth = strokeWidth
        )
    }

    // Power points
    val powerPoints = CompositionGeometry.getRuleOfThirdsPowerPoints(width, height)
    val outerRadius = 7.dp.toPx()
    val innerRadius = 3.dp.toPx()
    for (pt in powerPoints) {
        drawCircle(
            color = Color(0x66000000),
            radius = outerRadius + 1.dp.toPx(),
            center = Offset(pt.x, pt.y)
        )
        drawCircle(
            color = Color(0xFF00E5FF),
            radius = outerRadius,
            center = Offset(pt.x, pt.y),
            style = Stroke(width = 2.dp.toPx())
        )
        drawCircle(
            color = Color.White,
            radius = innerRadius,
            center = Offset(pt.x, pt.y)
        )
    }
}

private fun DrawScope.drawGoldenRatio(width: Float, height: Float) {
    val guideColor = Color(0xCCFBBF24) // Golden Amber
    val strokeWidth = 1.5.dp.toPx()

    val lines = CompositionGeometry.getGoldenRatioLines(width, height)
    for (line in lines) {
        drawLine(
            color = guideColor,
            start = Offset(line.startX, line.startY),
            end = Offset(line.endX, line.endY),
            strokeWidth = strokeWidth
        )
    }

    val intersections = CompositionGeometry.getGoldenRatioIntersections(width, height)
    val radius = 5.dp.toPx()
    for (pt in intersections) {
        drawCircle(
            color = Color(0xFFF59E0B),
            radius = radius,
            center = Offset(pt.x, pt.y),
            style = Stroke(width = 2.dp.toPx())
        )
        drawCircle(
            color = Color.White,
            radius = 2.5.dp.toPx(),
            center = Offset(pt.x, pt.y)
        )
    }
}

private fun DrawScope.drawFibonacciSpiral(
    width: Float,
    height: Float,
    orientation: SpiralOrientation
) {
    val spiralColor = Color(0xFFFBBF24)
    val rectColor = Color(0x44FBBF24)

    // Golden rectangles
    val rects = CompositionGeometry.getFibonacciSubdivisionRects(orientation, width, height)
    for (r in rects) {
        drawRect(
            color = rectColor,
            topLeft = Offset(r.left, r.top),
            size = Size(r.right - r.left, r.bottom - r.top),
            style = Stroke(width = 1.dp.toPx())
        )
    }

    // Smooth spiral curve
    val points = CompositionGeometry.getFibonacciSpiralPoints(orientation, width, height)
    if (points.size >= 2) {
        val path = Path()
        path.moveTo(points[0].x, points[0].y)
        for (i in 1 until points.size) {
            path.lineTo(points[i].x, points[i].y)
        }
        drawPath(
            path = path,
            color = spiralColor,
            style = Stroke(width = 2.5.dp.toPx())
        )
    }

    // Focal eye node
    val eye = points.lastOrNull()
    if (eye != null) {
        drawCircle(
            color = Color(0xFFFFD54F),
            radius = 6.dp.toPx(),
            center = Offset(eye.x, eye.y),
            style = Stroke(width = 2.dp.toPx())
        )
        drawCircle(
            color = Color.White,
            radius = 3.dp.toPx(),
            center = Offset(eye.x, eye.y)
        )
    }
}

private fun DrawScope.drawBaroqueSinister(width: Float, height: Float) {
    val baroqueColor = Color(0xCCE040FB) // Magenta
    val sinisterColor = Color(0xFF818CF8) // Indigo
    val strokeWidth = 1.5.dp.toPx()

    // Baroque
    val baroqueLines = CompositionGeometry.getBaroqueDiagonals(width, height)
    for ((index, line) in baroqueLines.withIndex()) {
        drawLine(
            color = baroqueColor,
            start = Offset(line.startX, line.startY),
            end = Offset(line.endX, line.endY),
            strokeWidth = if (index == 0) strokeWidth * 1.5f else strokeWidth
        )
    }

    // Sinister
    val sinisterLines = CompositionGeometry.getSinisterDiagonals(width, height)
    for ((index, line) in sinisterLines.withIndex()) {
        drawLine(
            color = sinisterColor,
            start = Offset(line.startX, line.startY),
            end = Offset(line.endX, line.endY),
            strokeWidth = if (index == 0) strokeWidth * 1.5f else strokeWidth
        )
    }
}

private fun DrawScope.drawCrosshairHorizon(width: Float, height: Float) {
    val guideColor = Color(0xCC00E676) // Bright Green
    val cx = width / 2f
    val cy = height / 2f

    // Center Crosshairs
    val lines = CompositionGeometry.getCenterCrosshairLines(width, height)
    for (line in lines) {
        drawLine(
            color = guideColor,
            start = Offset(line.startX, line.startY),
            end = Offset(line.endX, line.endY),
            strokeWidth = 1.5.dp.toPx()
        )
    }

    // Reticle circle
    drawCircle(
        color = guideColor,
        radius = 24.dp.toPx(),
        center = Offset(cx, cy),
        style = Stroke(width = 1.5.dp.toPx())
    )
    drawCircle(
        color = Color.White,
        radius = 3.dp.toPx(),
        center = Offset(cx, cy)
    )

    // Calibration ticks
    val ticks = CompositionGeometry.getCrosshairCalibrationTicks(width, height, 12.dp.toPx())
    for (tick in ticks) {
        drawLine(
            color = guideColor,
            start = Offset(tick.startX, tick.startY),
            end = Offset(tick.endX, tick.endY),
            strokeWidth = 1.5.dp.toPx()
        )
    }

    // Horizon leveling guide (angular pitch marks at center)
    val horizonLength = 80.dp.toPx()
    for (deg in listOf(-5.0, -2.0, -1.0, 1.0, 2.0, 5.0)) {
        val rad = deg * PI / 180.0
        val markLength = 8.dp.toPx()
        val tickX = cx + (deg.toFloat() * 12.dp.toPx())
        val tickY = cy - (tan(rad).toFloat() * horizonLength * 0.2f)
        drawLine(
            color = guideColor.copy(alpha = 0.7f),
            start = Offset(tickX, cy - markLength / 2f),
            end = Offset(tickX, cy + markLength / 2f),
            strokeWidth = 1.dp.toPx()
        )
    }
}

private fun DrawScope.drawLeadingLines(width: Float, height: Float, lines: List<DetectedLine>) {
    for (line in lines) {
        val start = Offset(line.startX * width, line.startY * height)
        val end = Offset(line.endX * width, line.endY * height)
        val alpha = (0.4f + line.strength * 0.6f).coerceIn(0.4f, 1f)

        val brush = Brush.linearGradient(
            colors = listOf(
                Color(0xFF00E5FF).copy(alpha = alpha),
                Color(0xFFFF9100).copy(alpha = alpha)
            ),
            start = start,
            end = end
        )

        // Line segment
        drawLine(
            brush = brush,
            start = start,
            end = end,
            strokeWidth = (2.5f + line.strength * 2f).dp.toPx()
        )

        // Endpoint caps
        drawCircle(
            color = Color(0xFF00E5FF).copy(alpha = alpha),
            radius = 4.dp.toPx(),
            center = start
        )
        drawCircle(
            color = Color(0xFFFF9100).copy(alpha = alpha),
            radius = 4.dp.toPx(),
            center = end
        )
    }
}

private fun DrawScope.drawMassDistribution(
    width: Float,
    height: Float,
    result: CompositionResult
) {
    // 3x3 Grid distribution density tint
    if (result.gridMassDistribution.size >= 9) {
        for (row in 0..2) {
            val yStart = (row * height) / 3f
            val cellH = height / 3f
            for (col in 0..2) {
                val xStart = (col * width) / 3f
                val cellW = width / 3f
                val idx = row * 3 + col
                val mass = result.gridMassDistribution[idx]

                // Visual density heat map
                val alpha = (mass * 0.45f).coerceIn(0.02f, 0.35f)
                drawRect(
                    color = Color(0xFFFF5252).copy(alpha = alpha),
                    topLeft = Offset(xStart, yStart),
                    size = Size(cellW, cellH)
                )

                // Grid cell borders
                drawRect(
                    color = Color(0x33FFFFFF),
                    topLeft = Offset(xStart, yStart),
                    size = Size(cellW, cellH),
                    style = Stroke(width = 1.dp.toPx())
                )
            }
        }
    }

    // Centroid indicator
    val cx = result.massCentroidX * width
    val cy = result.massCentroidY * height
    val radius = 16.dp.toPx()

    // Outer ring
    drawCircle(
        color = Color(0xFFFF5252),
        radius = radius,
        center = Offset(cx, cy),
        style = Stroke(width = 2.dp.toPx())
    )

    // Center crosshair
    val crosshairLen = 22.dp.toPx()
    drawLine(
        color = Color(0xFFFF5252),
        start = Offset(cx - crosshairLen / 2f, cy),
        end = Offset(cx + crosshairLen / 2f, cy),
        strokeWidth = 2.dp.toPx()
    )
    drawLine(
        color = Color(0xFFFF5252),
        start = Offset(cx, cy - crosshairLen / 2f),
        end = Offset(cx, cy + crosshairLen / 2f),
        strokeWidth = 2.dp.toPx()
    )

    // Center dot
    drawCircle(
        color = Color.White,
        radius = 4.dp.toPx(),
        center = Offset(cx, cy)
    )
}
