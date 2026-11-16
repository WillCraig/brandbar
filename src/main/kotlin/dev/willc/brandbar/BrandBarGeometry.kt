package dev.willc.brandbar

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
        throw NotImplementedError("BrandBarGeometry.computeDeterminate is not implemented yet")
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
        throw NotImplementedError("BrandBarGeometry.computeIndeterminate is not implemented yet")
    }

    fun advanceIndeterminate(
        innerWidthPx: Int,
        state: IndeterminateState,
        mode: IndeterminateMode,
        segmentRatio: Double,
        runnerWidthPx: Int,
        stepPx: Int,
    ): IndeterminateState {
        throw NotImplementedError("BrandBarGeometry.advanceIndeterminate is not implemented yet")
    }
}
