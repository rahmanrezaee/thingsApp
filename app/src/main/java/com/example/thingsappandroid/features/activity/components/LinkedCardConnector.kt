package com.example.thingsappandroid.features.activity.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 1. Top Widget
        topContent()

        // 2. The Connector Graphic (Canvas)
        ConnectorGraphic(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp) // Fixed height to match geometry
        )

        // 3. Bottom Widgets (Split Row)
        Row(
            modifier = Modifier.fillMaxWidth(),
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
    modifier: Modifier = Modifier
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
                // 1. Draw The Pipe (Static)
                drawPath(leftPath, ColorBackgroundStroke, style = Stroke(width = strokeWidth))
                drawPath(rightPath, ColorBackgroundStroke, style = Stroke(width = strokeWidth))

                // 2. Draw The Caps (Static)
                drawPath(topCap, ColorCapFill)
                drawPath(bottomLeftCap, ColorCapFill)
                drawPath(bottomRightCap, ColorCapFill)

                // 3. Draw The Energy (Animation)
                val segmentLength = 20f

                fun drawFlow(measure: PathMeasure, length: Float, flowColor: Color = ColorFlow) {
                    val distance = (1f - progress) * (length + segmentLength) // Reversed flow: bottom to top
                    val start = (distance - segmentLength).coerceAtLeast(0f)
                    val end = distance.coerceAtMost(length)

                    if (start < length) {
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
                drawFlow(rightMeasure, rightLength, ColorCarbonFlow) // Dark carbon energy flow for carbon card

                // 4. Draw The Pulse (Cap Fill Effect) - Reversed flow: bottom to top
                val capInfluenceLength = 8f

                // Calculate cap alpha for left branch (green flow)
                val leftHeadPos = (1f - progress) * (leftLength + segmentLength)
                val leftTailPos = leftHeadPos - segmentLength
                val leftFillFactor = (leftHeadPos / capInfluenceLength).coerceIn(0f, 1f)
                val leftDrainFactor = (leftTailPos / capInfluenceLength).coerceIn(0f, 1f)
                val leftCapAlpha = (leftFillFactor - leftDrainFactor).coerceIn(0f, 1f)

                // Calculate cap alpha for right branch (carbon flow)
                val rightHeadPos = (1f - progress) * (rightLength + segmentLength)
                val rightTailPos = rightHeadPos - segmentLength
                val rightFillFactor = (rightHeadPos / capInfluenceLength).coerceIn(0f, 1f)
                val rightDrainFactor = (rightTailPos / capInfluenceLength).coerceIn(0f, 1f)
                val rightCapAlpha = (rightFillFactor - rightDrainFactor).coerceIn(0f, 1f)

                // Combine cap effects - use the maximum alpha and choose dominant color
                val combinedCapAlpha = maxOf(leftCapAlpha, rightCapAlpha)

                if (combinedCapAlpha > 0f) {
                    val capColor = if (leftCapAlpha >= rightCapAlpha) {
                        ColorFlow.copy(alpha = combinedCapAlpha)
                    } else {
                        ColorCarbonFlow.copy(alpha = combinedCapAlpha)
                    }
                    drawPath(topCap, capColor)
                }
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
                GreenConnectorComponent(isConnected = true)
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