package com.example.thingsappandroid.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import com.example.thingsappandroid.ui.theme.PrimaryGreen
import androidx.compose.runtime.remember
import com.example.thingsappandroid.ui.theme.GreenFiGradientStart
import com.example.thingsappandroid.ui.theme.GreenFiGradientEnd
import com.example.thingsappandroid.ui.theme.Gray400
import com.example.thingsappandroid.ui.theme.Gray100

/**
 * Configuration for the custom refresh indicator appearance and behavior.
 */
data class CustomRefreshIndicatorConfig(
    val indicatorSize: Dp = 56.dp,
    val strokeWidth: Dp = 4.dp,
    val containerColor: Color = Gray100,
    val progressColor: Color = PrimaryGreen,
    val useGradient: Boolean = true,
    val gradientStart: Color = GreenFiGradientStart,
    val gradientEnd: Color = GreenFiGradientEnd,
    val iconTint: Color = PrimaryGreen,
    val showPullHint: Boolean = true,
    val pullHintText: String = "Pull to refresh",
    val refreshingText: String = "Refreshing...",
    val hintTextColor: Color = Gray400,
    val hintTextSizeSp: Float = 12f,
    val icon: ImageVector = Icons.Rounded.Refresh,
    val minPullProgressToShow: Float = 0.08f,
    val scaleWhenRefreshing: Float = 1f,
)

/**
 * Standalone refresh indicator that takes raw [pullProgress] (0f..1f+).
 * Use with [CustomPullToRefreshBox] in its [indicator] slot.
 */
@Composable
fun CustomRefreshIndicatorWithProgress(
    pullProgress: Float,
    isRefreshing: Boolean,
    modifier: Modifier = Modifier,
    config: CustomRefreshIndicatorConfig = CustomRefreshIndicatorConfig(),
) {
    val progress = pullProgress.coerceIn(0f, 1f)
    val showIndicator = progress >= config.minPullProgressToShow || isRefreshing
    if (!showIndicator) return
    CustomRefreshIndicatorContent(
        progress = progress,
        isRefreshing = isRefreshing,
        modifier = modifier,
        config = config,
    )
}

/**
 * Fully custom pull-to-refresh indicator component.
 * Use with Material3 [PullToRefreshBox] via the [indicator] parameter.
 *
 * @param modifier Modifier for the indicator container (e.g. [Modifier.align][Alignment.TopCenter]).
 * @param isRefreshing Whether a refresh is currently in progress.
 * @param pullToRefreshState The [PullToRefreshState] from [rememberPullToRefreshState].
 * @param config Optional [CustomRefreshIndicatorConfig] to customize appearance.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoxScope.CustomRefreshIndicator(
    modifier: Modifier = Modifier,
    isRefreshing: Boolean,
    pullToRefreshState: PullToRefreshState,
    config: CustomRefreshIndicatorConfig = CustomRefreshIndicatorConfig(),
) {
    val progress = pullToRefreshState.distanceFraction
    val showIndicator = progress >= config.minPullProgressToShow || isRefreshing
    if (!showIndicator) return
    CustomRefreshIndicatorContent(
        progress = progress,
        isRefreshing = isRefreshing,
        modifier = modifier,
        config = config,
    )
}

@Composable
private fun CustomRefreshIndicatorContent(
    progress: Float,
    isRefreshing: Boolean,
    modifier: Modifier,
    config: CustomRefreshIndicatorConfig,
) {
    val density = LocalDensity.current
    val infiniteTransition = rememberInfiniteTransition(label = "refresh_spinner")
    val rotation = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    val scale = remember { Animatable(0.8f) }
    val visible = progress >= config.minPullProgressToShow || isRefreshing
    LaunchedEffect(visible) {
        scale.animateTo(if (visible) 1f else 0.8f, animationSpec = tween(200))
    }

    Column(
        modifier = modifier
            .scale(scale.value)
            .padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val strokeWidthPx = with(density) { config.strokeWidth.toPx() }

        // Container circle (background)
        Box(
            modifier = Modifier
                .size(config.indicatorSize + config.strokeWidth)
                .clip(CircleShape)
                .background(config.containerColor),
            contentAlignment = Alignment.Center
        ) {
            // Progress arc (or spinning arc when refreshing)
            Canvas(
                modifier = Modifier.size(config.indicatorSize + config.strokeWidth),
                onDraw = {
                    val arcRadius = (size.minDimension - strokeWidthPx) / 2f
                    val center = Offset(size.minDimension / 2f, size.minDimension / 2f)
                    val sweepAngle = if (isRefreshing) {
                        270f
                    } else {
                        (progress * 360f).coerceIn(0f, 360f)
                    }
                    val startAngle = if (isRefreshing) rotation.value else 150f
                    val topLeft = Offset(strokeWidthPx / 2f, strokeWidthPx / 2f)
                    val arcSize = Size(arcRadius * 2f, arcRadius * 2f)
                    val strokeStyle = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)

                    rotate(degrees = startAngle, pivot = center) {
                        if (config.useGradient) {
                            drawArc(
                                brush = Brush.sweepGradient(
                                    center = center,
                                    colors = listOf(config.gradientStart, config.gradientEnd)
                                ),
                                startAngle = 0f,
                                sweepAngle = sweepAngle,
                                useCenter = false,
                                topLeft = topLeft,
                                size = arcSize,
                                style = strokeStyle
                            )
                        } else {
                            drawArc(
                                color = config.progressColor,
                                startAngle = 0f,
                                sweepAngle = sweepAngle,
                                useCenter = false,
                                topLeft = topLeft,
                                size = arcSize,
                                style = strokeStyle
                            )
                        }
                    }
                }
            )

            // Center icon
            Icon(
                imageVector = config.icon,
                contentDescription = null,
                modifier = Modifier.size(config.indicatorSize * 0.4f),
                tint = config.iconTint
            )
        }

        if (config.showPullHint) {
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                text = if (isRefreshing) config.refreshingText else config.pullHintText,
                style = MaterialTheme.typography.labelMedium.copy(fontSize = config.hintTextSizeSp.sp),
                color = config.hintTextColor
            )
        }
    }
}
