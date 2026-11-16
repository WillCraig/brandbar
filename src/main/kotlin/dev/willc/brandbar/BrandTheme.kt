package dev.willc.brandbar

import java.awt.Color
import java.awt.Graphics2D
import java.awt.image.BufferedImage
import java.nio.file.Path

class BrandThemeException(message: String, cause: Throwable? = null) : IllegalArgumentException(message, cause)

enum class IndeterminateMode {
    BOUNCE,
    TRAVEL,
}

enum class TrailStyle {
    STRIPES,
    GRADIENT,
}

enum class TravelDirection {
    RIGHT,
    LEFT,
}

interface BrandSprite {
    val logicalWidth: Int
    val logicalHeight: Int

    fun paint(
        g: Graphics2D,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        flipHorizontal: Boolean = false,
    )

    fun toBufferedImage(
        width: Int = logicalWidth,
        height: Int = logicalHeight,
        scale: Double = 1.0,
        flipHorizontal: Boolean = false,
    ): BufferedImage
}

data class BrandTheme(
    val name: String,
    val runner: BrandSprite,
    val reverseRunner: BrandSprite?,
    val flipRunnerOnReverse: Boolean,
    val trailTile: BrandSprite?,
    val trailColors: List<Color>,
    val trailStyle: TrailStyle,
    val trackColor: Color,
    val borderColor: Color,
    val barHeightPx: Int,
    val trailHeightPx: Int,
    val cornerRadiusPx: Int,
    val indeterminateMode: IndeterminateMode,
    val frameIntervalMs: Int,
    val stepPx: Int,
    val indeterminateSegmentRatio: Double,
) {
    fun paintRunner(
        g: Graphics2D,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        direction: TravelDirection,
    ) {
        throw NotImplementedError("BrandTheme.paintRunner is not implemented yet")
    }
}

object BrandThemeLoader {
    fun loadBundled(
        resourceRoot: String = "/brand",
        classLoader: ClassLoader = BrandThemeLoader::class.java.classLoader,
    ): BrandTheme {
        throw NotImplementedError("BrandThemeLoader.loadBundled is not implemented yet")
    }

    fun loadFromDirectory(directory: Path): BrandTheme {
        throw NotImplementedError("BrandThemeLoader.loadFromDirectory is not implemented yet")
    }

    fun parseHexColor(raw: String, fieldName: String): Color {
        throw NotImplementedError("BrandThemeLoader.parseHexColor is not implemented yet")
    }
}
