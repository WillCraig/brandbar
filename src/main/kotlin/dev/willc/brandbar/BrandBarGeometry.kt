package dev.willc.brandbar

import kotlin.math.roundToInt

data class BarBounds(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
) {
    val right: Int
        get() = x + width
    val bottom: Int
        get() = y + height
}

data class DeterminateFrameLayout(
    val track: BarBounds,
    val trail: BarBounds,
    val runner: BarBounds,
)

data class IndeterminateState(
    val offsetPx: Int = 0,
    val direction: TravelDirection = TravelDirection.RIGHT,
)

data class IndeterminateFrameLayout(
    val track: BarBounds,
    val trail: BarBounds,
    val runner: BarBounds,
    val direction: TravelDirection,
)

object BrandBarGeometry {
    fun computeDeterminate(
        innerBounds: BarBounds,
        fraction: Double,
        runnerWidthPx: Int,
        runnerHeightPx: Int,
        trailHeightPx: Int,
    ): DeterminateFrameLayout {
        val safeW = innerBounds.width.coerceAtLeast(1)
        val safeH = innerBounds.height.coerceAtLeast(1)
        val clampedFraction = fraction.coerceIn(0.0, 1.0)
        val runnerW = runnerWidthPx.coerceIn(1, safeW)
        val runnerH = runnerHeightPx.coerceIn(1, safeH)
        val trailH = trailHeightPx.coerceIn(1, safeH)

        val trailWidth = (innerBounds.width * clampedFraction).roundToInt().coerceIn(0, innerBounds.width)
        val trailY = innerBounds.y + (innerBounds.height - trailH) / 2
        val runnerY = innerBounds.y + (innerBounds.height - runnerH) / 2

        val maxRunnerOffset = (innerBounds.width - runnerW).coerceAtLeast(0)
        val runnerX = innerBounds.x + (maxRunnerOffset * clampedFraction).roundToInt()

        return DeterminateFrameLayout(
            track = innerBounds,
            trail = BarBounds(
                x = innerBounds.x,
                y = trailY,
                width = trailWidth,
                height = trailH,
            ),
            runner = BarBounds(
                x = runnerX,
                y = runnerY,
                width = runnerW,
                height = runnerH,
            ),
        )
    }

    fun computeIndeterminate(
        innerBounds: BarBounds,
        state: IndeterminateState,
        mode: IndeterminateMode,
        segmentRatio: Double,
        runnerWidthPx: Int,
        runnerHeightPx: Int,
        trailHeightPx: Int,
    ): IndeterminateFrameLayout {
        val safeW = innerBounds.width.coerceAtLeast(1)
        val safeH = innerBounds.height.coerceAtLeast(1)
        val runnerW = runnerWidthPx.coerceIn(1, safeW)
        val runnerH = runnerHeightPx.coerceIn(1, safeH)
        val trailH = trailHeightPx.coerceIn(1, safeH)

        val maxRunnerOffset = (innerBounds.width - runnerW).coerceAtLeast(0)
        val clampedOffset = state.offsetPx.coerceIn(0, maxRunnerOffset)
        val runnerX = innerBounds.x + clampedOffset
        val runnerY = innerBounds.y + (innerBounds.height - runnerH) / 2
        val trailY = innerBounds.y + (innerBounds.height - trailH) / 2

        val effectiveDirection = when (mode) {
            IndeterminateMode.BOUNCE -> state.direction
            IndeterminateMode.TRAVEL -> TravelDirection.RIGHT
        }

        val maxSegmentW = (innerBounds.width * segmentRatio.coerceIn(0.05, 0.95))
            .roundToInt()
            .coerceIn(runnerW, safeW)
        val runnerCenter = runnerX + runnerW / 2

        val (trailStart, trailEnd) = when (effectiveDirection) {
            TravelDirection.RIGHT -> {
                val start = (runnerCenter - maxSegmentW).coerceAtLeast(innerBounds.x)
                val end = runnerCenter.coerceIn(start, innerBounds.right)
                start to end
            }
            TravelDirection.LEFT -> {
                val start = runnerCenter.coerceIn(innerBounds.x, innerBounds.right)
                val end = (runnerCenter + maxSegmentW).coerceIn(start, innerBounds.right)
                start to end
            }
        }

        return IndeterminateFrameLayout(
            track = innerBounds,
            trail = BarBounds(
                x = trailStart,
                y = trailY,
                width = (trailEnd - trailStart).coerceAtLeast(0),
                height = trailH,
            ),
            runner = BarBounds(
                x = runnerX,
                y = runnerY,
                width = runnerW,
                height = runnerH,
            ),
            direction = effectiveDirection,
        )
    }

    @Suppress("UNUSED_PARAMETER")
    fun advanceIndeterminate(
        innerWidthPx: Int,
        state: IndeterminateState,
        mode: IndeterminateMode,
        segmentRatio: Double,
        runnerWidthPx: Int,
        stepPx: Int,
    ): IndeterminateState {
        val safeW = innerWidthPx.coerceAtLeast(1)
        val runnerW = runnerWidthPx.coerceIn(1, safeW)
        val maxOffset = (innerWidthPx - runnerW).coerceAtLeast(0)
        if (maxOffset == 0) {
            return IndeterminateState(offsetPx = 0, direction = TravelDirection.RIGHT)
        }
        val step = stepPx.coerceAtLeast(1)

        return when (mode) {
            IndeterminateMode.BOUNCE -> when (state.direction) {
                TravelDirection.RIGHT -> {
                    val next = state.offsetPx + step
                    if (next >= maxOffset) {
                        IndeterminateState(offsetPx = maxOffset, direction = TravelDirection.LEFT)
                    } else {
                        IndeterminateState(offsetPx = next, direction = TravelDirection.RIGHT)
                    }
                }
                TravelDirection.LEFT -> {
                    val next = state.offsetPx - step
                    if (next <= 0) {
                        IndeterminateState(offsetPx = 0, direction = TravelDirection.RIGHT)
                    } else {
                        IndeterminateState(offsetPx = next, direction = TravelDirection.LEFT)
                    }
                }
            }
            IndeterminateMode.TRAVEL -> {
                val wrapped = (state.offsetPx.coerceAtLeast(0) + step) % (maxOffset + 1)
                IndeterminateState(offsetPx = wrapped, direction = TravelDirection.RIGHT)
            }
        }
    }
}
