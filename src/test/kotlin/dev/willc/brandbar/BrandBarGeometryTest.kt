package dev.willc.brandbar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrandBarGeometryTest {
    private val inner = BarBounds(x = 4, y = 2, width = 300, height = 20)

    @Test
    fun determinateProgressMovesRunnerAndGrowsTrailWithinBounds() {
        val percentages = listOf(0.0, 0.25, 0.50, 0.75, 1.0)
        val layouts = percentages.map { fraction ->
            BrandBarGeometry.computeDeterminate(
                innerBounds = inner,
                fraction = fraction,
                runnerWidthPx = 24,
                runnerHeightPx = 20,
                trailHeightPx = 10,
            )
        }

        assertEquals(0, layouts.first().trail.width)
        assertEquals(inner.width, layouts.last().trail.width)

        for (layout in layouts) {
            assertTrue(layout.trail.x >= inner.x)
            assertTrue(layout.trail.right <= inner.right)
            assertEquals(10, layout.trail.height)
            assertEquals(inner.y + (inner.height - 10) / 2, layout.trail.y)
            assertTrue(layout.runner.x >= inner.x)
            assertTrue(layout.runner.right <= inner.right)
        }

        for (i in 1 until layouts.size) {
            assertTrue(
                "Trail width must grow monotonically at step $i",
                layouts[i].trail.width > layouts[i - 1].trail.width,
            )
            assertTrue(
                "Runner X must advance monotonically at step $i",
                layouts[i].runner.x > layouts[i - 1].runner.x,
            )
        }
    }

    @Test
    fun indeterminateBounceReversesCleanlyAtBothEnds() {
        var state = IndeterminateState(offsetPx = 0, direction = TravelDirection.RIGHT)
        var sawLeftBounce = false
        var sawReturnRightBounce = false

        repeat(220) {
            val frame = BrandBarGeometry.computeIndeterminate(
                innerBounds = inner,
                state = state,
                mode = IndeterminateMode.BOUNCE,
                segmentRatio = 0.35,
                runnerWidthPx = 24,
                runnerHeightPx = 20,
                trailHeightPx = 10,
            )
            assertTrue(frame.trail.x >= inner.x)
            assertTrue(frame.trail.right <= inner.right)
            assertTrue(frame.runner.x >= inner.x)
            assertTrue(frame.runner.right <= inner.right)

            val next = BrandBarGeometry.advanceIndeterminate(
                innerWidthPx = inner.width,
                state = state,
                mode = IndeterminateMode.BOUNCE,
                segmentRatio = 0.35,
                runnerWidthPx = 24,
                stepPx = 6,
            )
            if (state.direction == TravelDirection.RIGHT && next.direction == TravelDirection.LEFT) {
                sawLeftBounce = true
            }
            if (sawLeftBounce && state.direction == TravelDirection.LEFT && next.direction == TravelDirection.RIGHT) {
                sawReturnRightBounce = true
            }
            state = next
        }

        assertTrue("Expected runner to bounce off the right boundary", sawLeftBounce)
        assertTrue("Expected runner to bounce back off the left boundary", sawReturnRightBounce)
    }

    @Test
    fun indeterminateTravelMovesForwardAndWrapsWithinTrack() {
        var state = IndeterminateState(offsetPx = 0, direction = TravelDirection.RIGHT)
        var sawWrap = false
        var previousOffset = state.offsetPx

        repeat(150) {
            val frame = BrandBarGeometry.computeIndeterminate(
                innerBounds = inner,
                state = state,
                mode = IndeterminateMode.TRAVEL,
                segmentRatio = 0.30,
                runnerWidthPx = 24,
                runnerHeightPx = 20,
                trailHeightPx = 10,
            )
            assertEquals(TravelDirection.RIGHT, frame.direction)
            assertTrue(frame.trail.x >= inner.x)
            assertTrue(frame.trail.right <= inner.right)
            assertTrue(frame.runner.x >= inner.x)
            assertTrue(frame.runner.right <= inner.right)

            state = BrandBarGeometry.advanceIndeterminate(
                innerWidthPx = inner.width,
                state = state,
                mode = IndeterminateMode.TRAVEL,
                segmentRatio = 0.30,
                runnerWidthPx = 24,
                stepPx = 8,
            )
            if (state.offsetPx < previousOffset) {
                sawWrap = true
            }
            previousOffset = state.offsetPx
        }

        assertTrue("Expected TRAVEL mode to wrap back to start after crossing the track", sawWrap)
    }
}
