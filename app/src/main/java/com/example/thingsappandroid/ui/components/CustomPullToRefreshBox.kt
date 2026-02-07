package com.example.thingsappandroid.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Configuration for the custom [CustomPullToRefreshBox] behavior.
 */
data class CustomPullToRefreshConfig(
    /** Pull distance (in dp) required to trigger refresh on release. */
    val pullThreshold: Dp = 80.dp,
    /** Max pull distance (in dp) for the indicator before resistance. */
    val maxPullDistance: Dp = 120.dp,
    /** Drag multiplier for pull (e.g. 0.5f = half the finger movement). */
    val pullDragMultiplier: Float = 0.5f,
)

/**
 * Fully custom Pull-to-Refresh container. Replaces Material3 [androidx.compose.material3.pulltorefresh.PullToRefreshBox]
 * with your own pull detection, threshold, and indicator.
 *
 * Uses [NestedScrollConnection] to intercept pull-down when content is at top, then triggers [onRefresh] on release
 * when pull exceeds [CustomPullToRefreshConfig.pullThreshold]. The [indicator] slot receives pull progress (0f..1f+)
 * and current pull offset in pixels for positioning.
 *
 * @param isRefreshing True while the refresh action is in progress (e.g. loading).
 * @param onRefresh Called when the user releases after pulling past the threshold.
 * @param modifier Modifier for the root [Box].
 * @param config Optional [CustomPullToRefreshConfig] to tune pull behavior.
 * @param indicator Composable for the refresh indicator; receives progress and offset (use [Modifier.offset] for position).
 * @param content The scrollable content (typically a [Column] with [Modifier.verticalScroll]; the box provides the scroll).
 */
@Composable
fun CustomPullToRefreshBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    config: CustomPullToRefreshConfig = CustomPullToRefreshConfig(),
    indicator: @Composable (pullProgress: Float, pullOffsetPx: Float, isRefreshing: Boolean) -> Unit,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val thresholdPx = with(density) { config.pullThreshold.toPx() }
    val maxPullPx = with(density) { config.maxPullDistance.toPx() }
    val scope = rememberCoroutineScope()

    var pullOffsetPx by remember { mutableStateOf(0f) }
    val animatable = remember { Animatable(0f) }
    val scrollState = rememberScrollState()
    var wasRefreshing by remember { mutableStateOf(false) }

    val connection = remember(thresholdPx, maxPullPx, config.pullDragMultiplier) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val dy = available.y
                if (dy <= 0f) return Offset.Zero
                if (scrollState.value > 0) return Offset.Zero
                val consume = (dy * config.pullDragMultiplier).coerceIn(0f, maxPullPx - pullOffsetPx)
                if (consume > 0f) {
                    pullOffsetPx += consume
                }
                return Offset(0f, consume)
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                val dy = available.y
                if (dy >= 0f) return Offset.Zero
                if (pullOffsetPx <= 0f) return Offset.Zero
                val release = (-dy).coerceIn(0f, pullOffsetPx)
                pullOffsetPx -= release
                return Offset(0f, -release)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                return when {
                    pullOffsetPx >= thresholdPx -> {
                        onRefresh()
                        scope.launch {
                            animatable.snapTo(thresholdPx)
                            pullOffsetPx = 0f
                        }
                        available
                    }
                    pullOffsetPx > 0f -> {
                        scope.launch {
                            pullOffsetPx = 0f
                            animatable.snapTo(0f)
                        }
                        available
                    }
                    else -> Velocity.Zero
                }
            }
        }
    }

    LaunchedEffect(isRefreshing) {
        if (wasRefreshing && !isRefreshing) {
            animatable.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
        }
        wasRefreshing = isRefreshing
    }

    val displayOffsetPx = when {
        isRefreshing -> thresholdPx.coerceAtLeast(animatable.value)
        else -> pullOffsetPx + animatable.value
    }
    val progress = (displayOffsetPx / thresholdPx).coerceIn(0f, 2f)
    val showIndicator = progress > 0.02f || isRefreshing

    Box(
        modifier = modifier.nestedScroll(connection),
    ) {
        Box(Modifier.verticalScroll(scrollState)) {
            content()
        }
        if (showIndicator) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset { IntOffset(0, displayOffsetPx.roundToInt()) },
                contentAlignment = Alignment.TopCenter,
            ) {
                indicator(progress, displayOffsetPx, isRefreshing)
            }
        }
    }
}
