package com.example.thingsappandroid.features.activity.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

// =================================================================================================
// SECTION: CONFIGURATION (COLORS)
// =================================================================================================
private val ColorBackgroundStroke = Color(0xFFE5E5E5) // The static gray pipe
private val ColorCapFill = Color(0xFFD4D4D4)        // The static gray caps
private val ColorFlow = Color(0xFF7CCF00)           // The moving green energy
private val ColorCarbonFlow = Color(0xFF333333)     // The moving dark carbon energy

@Composable
fun LinkedCardConnector(
    topContent: @Composable () -> Unit,
    bottomContentLeft: @Composable () -> Unit,
    bottomContentRight: @Composable () -> Unit,
    isGreen: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 1. Top Widget
        Box(modifier = Modifier.zIndex(1f)) {
            topContent()
        }

        // 2. The Connector Graphic (Canvas) - sits below top content; when green, right energy flow is disabled
        ConnectorGraphic(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp) // Fixed height to match geometry
                .zIndex(0f),
            isGreen = isGreen
        )
        Row(
            modifier = Modifier
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(15.dp)
        ) {
            Box(modifier = Modifier.weight(1f)) {
                bottomContentLeft()
            }
            Box(modifier = Modifier.weight(1f)) {
                bottomContentRight()
            }
        }
    }
}

@Composable
private fun ConnectorGraphic(
    modifier: Modifier = Modifier,
    isGreen: Boolean = false
) {
    val density = LocalDensity.current

    // =============================================================================================
    // SECTION: ANIMATION ENGINE
    // Drives the progress from 0f to 1f continuously
    // =============================================================================================
    val infiniteTransition = rememberInfiniteTransition(label = "flow_animation")
    val progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing), // Speed: 2 seconds
            repeatMode = RepeatMode.Restart
        ),
        label = "progress"
    )

    Spacer(
        modifier = modifier.drawWithCache {
            // =====================================================================================
            // SECTION: DIMENSIONS & COORDINATES
            // =====================================================================================

            // Visual Settings
            val strokeWidth = 6.dp.toPx()  // Pipe Thickness
            val capWidth = 16.dp.toPx()    // Cap Width
            val capHeight = 8.dp.toPx()    // Cap Height
            val cornerRadius = 8.dp.toPx() // Pipe Bend Radius

            // Layout Landmarks (Y-Axis)
            val yTop = 0f
            val ySplit = 14.dp.toPx()
            val yHorizontal = 22.dp.toPx()
            val yLegStart = 30.dp.toPx()
            val yBottom = size.height
            
            // Offset for energy flow to stop under topContent (not at the very top)
            // Use ySplit as the minimum stop point to ensure flow ends well below topContent
            val flowStopOffset = ySplit + capHeight + 2.dp.toPx() // Stop flow well below top

            // Layout Landmarks (X-Axis)
            val gapPx = 15.dp.toPx()
            val cardWidth = (size.width - gapPx) / 2
            val centerX = size.width / 2
            val leftLegX = cardWidth / 2
            val rightLegX = size.width - (cardWidth / 2)

            // =====================================================================================
            // SECTION: MODULE - THE PIPE (PATHS)
            // =====================================================================================

            // Left Branch Path
            val leftPath = Path().apply {
                moveTo(centerX, yTop)
                lineTo(centerX, ySplit)
                // Curve 1: Top -> Left
                cubicTo(
                    centerX, ySplit + (cornerRadius * 0.55f),
                    centerX - (cornerRadius * 0.45f), yHorizontal,
                    centerX - cornerRadius, yHorizontal
                )
                lineTo(leftLegX + cornerRadius, yHorizontal)
                // Curve 2: Left -> Down
                cubicTo(
                    leftLegX + (cornerRadius * 0.45f), yHorizontal,
                    leftLegX, yHorizontal + (cornerRadius * 0.55f),
                    leftLegX, yLegStart
                )
                lineTo(leftLegX, yBottom)
            }

            // Right Branch Path
            val rightPath = Path().apply {
                moveTo(centerX, yTop)
                lineTo(centerX, ySplit)
                // Curve 1: Top -> Right
                cubicTo(
                    centerX, ySplit + (cornerRadius * 0.55f),
                    centerX + (cornerRadius * 0.45f), yHorizontal,
                    centerX + cornerRadius, yHorizontal
                )
                lineTo(rightLegX - cornerRadius, yHorizontal)
                // Curve 2: Right -> Down
                cubicTo(
                    rightLegX - (cornerRadius * 0.45f), yHorizontal,
                    rightLegX, yHorizontal + (cornerRadius * 0.55f),
                    rightLegX, yLegStart
                )
                lineTo(rightLegX, yBottom)
            }

            // =====================================================================================
            // SECTION: MODULE - THE CAPS (CUP & DOMES)
            // =====================================================================================

            // Top Cap: "The Cup" (Hanging Down)
            val topCap = Path().apply {
                moveTo(centerX - capWidth / 2, yTop)
                lineTo(centerX + capWidth / 2, yTop)
                // Curve Down
                cubicTo(
                    centerX + capWidth / 2, yTop + capHeight * 0.55f,
                    centerX + capWidth * 0.25f, yTop + capHeight,
                    centerX, yTop + capHeight
                )
                cubicTo(
                    centerX - capWidth * 0.25f, yTop + capHeight,
                    centerX - capWidth / 2, yTop + capHeight * 0.55f,
                    centerX - capWidth / 2, yTop
                )
                close()
            }

            // Bottom Caps: "The Domes" (Standing Up)
            val bottomLeftCap = Path().apply {
                moveTo(leftLegX - capWidth / 2, yBottom)
                lineTo(leftLegX + capWidth / 2, yBottom)
                // Curve Up
                cubicTo(
                    leftLegX + capWidth / 2, yBottom - capHeight * 0.55f,
                    leftLegX + capWidth * 0.25f, yBottom - capHeight,
                    leftLegX, yBottom - capHeight
                )
                cubicTo(
                    leftLegX - capWidth * 0.25f, yBottom - capHeight,
                    leftLegX - capWidth / 2, yBottom - capHeight * 0.55f,
                    leftLegX - capWidth / 2, yBottom
                )
                close()
            }

            val bottomRightCap = Path().apply {
                moveTo(rightLegX - capWidth / 2, yBottom)
                lineTo(rightLegX + capWidth / 2, yBottom)
                // Curve Up
                cubicTo(
                    rightLegX + capWidth / 2, yBottom - capHeight * 0.55f,
                    rightLegX + capWidth * 0.25f, yBottom - capHeight,
                    rightLegX, yBottom - capHeight
                )
                cubicTo(
                    rightLegX - capWidth * 0.25f, yBottom - capHeight,
                    rightLegX - capWidth / 2, yBottom - capHeight * 0.55f,
                    rightLegX - capWidth / 2, yBottom
                )
                close()
            }

            // =====================================================================================
            // SECTION: DRAWING & ANIMATION LOGIC
            // =====================================================================================
            val leftMeasure = PathMeasure()
            leftMeasure.setPath(leftPath, false)
            val rightMeasure = PathMeasure()
            rightMeasure.setPath(rightPath, false)

            val leftLength = leftMeasure.length
            val rightLength = rightMeasure.length

            onDrawBehind {
                // 1. Draw The Pipe (Static) - always show both pipes
                drawPath(leftPath, ColorBackgroundStroke, style = Stroke(width = strokeWidth))
                drawPath(rightPath, ColorBackgroundStroke, style = Stroke(width = strokeWidth))

                // 2. Draw The Caps (Static)
                drawPath(topCap, ColorCapFill)
                drawPath(bottomLeftCap, ColorCapFill)
                drawPath(bottomRightCap, ColorCapFill)

                // 3. Draw The Energy (Animation) - when green, only disable right energy flow (pipe stays visible)
                val segmentLength = 20f

                fun drawFlow(measure: PathMeasure, length: Float, flowColor: Color = ColorFlow) {
                    // Calculate the maximum distance the flow can reach (stop well before top)
                    // Ensure flow stops below topContent by using the full flowStopOffset
                    val effectiveLength = (length - flowStopOffset).coerceAtLeast(0f)
                    
                    val distance = (1f - progress) * (effectiveLength + segmentLength) // Reversed flow: bottom to top
                    val start = (distance - segmentLength).coerceAtLeast(0f)
                    val end = distance.coerceAtMost(effectiveLength)

                    if (start < effectiveLength && end > 0f) {
                        val segmentPath = Path()
                        measure.getSegment(start, end, segmentPath, true)

                        val startPos = measure.getPosition(start)
                        val endPos = measure.getPosition(end)

                        drawPath(
                            segmentPath,
                            brush = Brush.linearGradient(
                                0f to flowColor,
                                1f to flowColor.copy(alpha = 0f),
                                start = startPos,
                                end = endPos
                            ),
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                    }
                }

                drawFlow(leftMeasure, leftLength, ColorFlow) // Green energy flow for battery
                if (!isGreen) {
                    drawFlow(rightMeasure, rightLength, ColorCarbonFlow) // Right energy flow: only disabled when green; pipe and cap stay visible
                }

                // 4. Draw The Pulse (Cap Fill Effect) - Reversed flow: bottom to top
                // Flow stops before reaching top, so cap animation should also stop earlier
                // Since flow stops at flowStopOffset from top, cap should never animate
                // (cap is at the very top, flow never reaches it)
                // Therefore, we disable the top cap animation entirely
                // The static gray cap will remain visible, but no animated fill
            }
        }
    )
}

@Preview(showBackground = true, widthDp = 360)
@Composable
fun LinkedCardConnectorPreview() {
    LinkedCardConnector(
        topContent = {
            ClimateStatusCard()
        },
        bottomContentLeft = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                BatteryCard(
                    batteryLevel = 85f,
                    isCharging = true
                )
                // Animated Connection Line
                ChargingConnectionLine(
                    isCharging = true,
                    height = 40.dp
                )
                // New Extension Component
                GreenConnectorComponent()
            }
        },
        bottomContentRight = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CarbonCard(currentUsage = 102f)
                CarbonConnectionLine(height = 54.dp, isCharging = true,)
                LowCarbonComponent()

            }
        }
    )
}