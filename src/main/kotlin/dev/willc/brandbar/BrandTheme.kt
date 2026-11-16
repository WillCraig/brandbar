package dev.willc.brandbar

import com.github.weisj.jsvg.SVGDocument
import com.github.weisj.jsvg.parser.LoaderContext
import com.github.weisj.jsvg.parser.SVGLoader
import com.github.weisj.jsvg.view.ViewBox
import java.awt.Color
import java.awt.Component
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.math.hypot
import kotlin.math.roundToInt

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

internal class RasterBrandSprite(
    private val image1x: BufferedImage,
    private val image2x: BufferedImage?,
) : BrandSprite {
    override val logicalWidth: Int = image1x.width
    override val logicalHeight: Int = image1x.height

    override fun paint(
        g: Graphics2D,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        flipHorizontal: Boolean,
    ) {
        if (width <= 0 || height <= 0) return
        val effectiveScale = hypot(g.transform.scaleX, g.transform.shearY)
        val source = if (effectiveScale > 1.25 && image2x != null) image2x else image1x
        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR,
            )
            if (flipHorizontal) {
                g2.drawImage(source, x + width, y, -width, height, null)
            } else {
                g2.drawImage(source, x, y, width, height, null)
            }
        } finally {
            g2.dispose()
        }
    }

    override fun toBufferedImage(
        width: Int,
        height: Int,
        scale: Double,
        flipHorizontal: Boolean,
    ): BufferedImage {
        val safeScale = scale.coerceAtLeast(0.1)
        val pixelW = (width * safeScale).roundToInt().coerceAtLeast(1)
        val pixelH = (height * safeScale).roundToInt().coerceAtLeast(1)
        val out = BufferedImage(pixelW, pixelH, BufferedImage.TYPE_INT_ARGB)
        val g = out.createGraphics()
        try {
            g.scale(safeScale, safeScale)
            paint(g, 0, 0, width, height, flipHorizontal)
        } finally {
            g.dispose()
        }
        return out
    }
}

internal class SvgBrandSprite(
    private val document: SVGDocument,
    override val logicalWidth: Int,
    override val logicalHeight: Int,
) : BrandSprite {
    override fun paint(
        g: Graphics2D,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        flipHorizontal: Boolean,
    ) {
        if (width <= 0 || height <= 0) return
        val g2 = g.create(x, y, width, height) as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
            if (flipHorizontal) {
                g2.translate(width.toDouble(), 0.0)
                g2.scale(-1.0, 1.0)
            }
            document.render(null as Component?, g2, ViewBox(0f, 0f, width.toFloat(), height.toFloat()))
        } finally {
            g2.dispose()
        }
    }

    override fun toBufferedImage(
        width: Int,
        height: Int,
        scale: Double,
        flipHorizontal: Boolean,
    ): BufferedImage {
        val safeScale = scale.coerceAtLeast(0.1)
        val pixelW = (width * safeScale).roundToInt().coerceAtLeast(1)
        val pixelH = (height * safeScale).roundToInt().coerceAtLeast(1)
        val out = BufferedImage(pixelW, pixelH, BufferedImage.TYPE_INT_ARGB)
        val g = out.createGraphics()
        try {
            g.scale(safeScale, safeScale)
            paint(g, 0, 0, width, height, flipHorizontal)
        } finally {
            g.dispose()
        }
        return out
    }
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
        when (direction) {
            TravelDirection.RIGHT -> runner.paint(g, x, y, width, height, flipHorizontal = false)
            TravelDirection.LEFT -> {
                if (reverseRunner != null) {
                    reverseRunner.paint(g, x, y, width, height, flipHorizontal = false)
                } else {
                    runner.paint(g, x, y, width, height, flipHorizontal = flipRunnerOnReverse)
                }
            }
        }
    }
}

private interface BrandAssetSource {
    val description: String
    fun readBytesOrNull(relativeName: String): ByteArray?
}

private class ClasspathBrandSource(
    rawRoot: String,
    private val classLoader: ClassLoader,
) : BrandAssetSource {
    private val normalizedRoot = rawRoot.trim('/').let { if (it.isEmpty()) "" else "$it/" }
    override val description: String = "classpath:$normalizedRoot"

    override fun readBytesOrNull(relativeName: String): ByteArray? {
        val safeName = sanitizeRelativeAssetName(relativeName)
        val resourcePath = "$normalizedRoot$safeName"
        return classLoader.getResourceAsStream(resourcePath)?.use { it.readAllBytes() }
    }
}

private class DirectoryBrandSource(
    private val directory: Path,
) : BrandAssetSource {
    override val description: String = directory.toAbsolutePath().toString()

    override fun readBytesOrNull(relativeName: String): ByteArray? {
        val safeName = sanitizeRelativeAssetName(relativeName)
        val resolved = directory.resolve(safeName).normalize()
        if (!resolved.startsWith(directory.normalize())) {
            throw BrandThemeException("Brand asset '$relativeName' escapes brand directory $directory")
        }
        if (!Files.isRegularFile(resolved)) {
            return null
        }
        return Files.readAllBytes(resolved)
    }
}

private fun sanitizeRelativeAssetName(name: String): String {
    val trimmed = name.trim()
    if (trimmed.isEmpty() || trimmed.startsWith("/") || trimmed.contains("..")) {
        throw BrandThemeException("Invalid brand asset file name: '$name'")
    }
    return trimmed
}

object BrandThemeLoader {
    private val HEX_COLOR_REGEX = Regex("^#([0-9a-fA-F]{6}|[0-9a-fA-F]{8})$")

    fun loadBundled(
        resourceRoot: String = "/brand",
        classLoader: ClassLoader = BrandThemeLoader::class.java.classLoader,
    ): BrandTheme = loadFromSource(ClasspathBrandSource(resourceRoot, classLoader))

    fun loadFromDirectory(directory: Path): BrandTheme {
        if (!Files.isDirectory(directory)) {
            throw BrandThemeException("Brand directory does not exist: $directory")
        }
        return loadFromSource(DirectoryBrandSource(directory))
    }

    fun parseHexColor(raw: String, fieldName: String): Color {
        val trimmed = raw.trim()
        val match = HEX_COLOR_REGEX.matchEntire(trimmed)
            ?: throw BrandThemeException("Invalid hex color '$raw' for '$fieldName'; expected #RRGGBB or #AARRGGBB")
        val hex = match.groupValues[1]
        return if (hex.length == 6) {
            val rgb = hex.toInt(16)
            Color((rgb shr 16) and 0xFF, (rgb shr 8) and 0xFF, rgb and 0xFF)
        } else {
            val argb = hex.toLong(16)
            Color(
                ((argb shr 16) and 0xFF).toInt(),
                ((argb shr 8) and 0xFF).toInt(),
                (argb and 0xFF).toInt(),
                ((argb shr 24) and 0xFF).toInt(),
            )
        }
    }

    private fun loadFromSource(source: BrandAssetSource): BrandTheme {
        val jsonBytes = source.readBytesOrNull("theme.json")
            ?: throw BrandThemeException("Missing theme.json in ${source.description}")
        val root = BrandJson.parseObject(String(jsonBytes, StandardCharsets.UTF_8))
        val entries = root.entries

        val name = entries.optionalString("name") ?: "Brand Bar"
        if (name.isBlank()) {
            throw BrandThemeException("Field 'name' in theme.json must not be blank")
        }

        val runnerFile = entries.optionalString("runnerImage")
            ?: throw BrandThemeException("Field 'runnerImage' is required in theme.json")
        val runner = loadSprite(source, runnerFile, "runnerImage")

        val reverseRunnerFile = entries.optionalNullableString("reverseRunnerImage")
        val reverseRunner = reverseRunnerFile?.let { loadSprite(source, it, "reverseRunnerImage") }

        val flipRunnerOnReverse = entries.optionalBoolean("flipRunnerOnReverse") ?: true

        val trailImageFile = entries.optionalNullableString("trailImage")
        val trailTile = trailImageFile?.let { loadSprite(source, it, "trailImage") }

        val trailColors = entries.optionalStringList("trailColors")
            ?.mapIndexed { index, raw -> parseHexColor(raw, "trailColors[$index]") }
            ?: emptyList()

        if (trailTile == null && trailColors.isEmpty()) {
            throw BrandThemeException(
                "theme.json in ${source.description} must specify 'trailImage', non-empty 'trailColors', or both",
            )
        }

        val trailStyle = entries.optionalEnum("trailStyle", TrailStyle.STRIPES)
        val trackColor = parseHexColor(entries.optionalString("trackColor") ?: "#1E293B", "trackColor")
        val borderColor = parseHexColor(entries.optionalString("borderColor") ?: "#334155", "borderColor")

        val barHeightPx = entries.optionalInt("barHeightPx") ?: 20
        if (barHeightPx !in 8..128) {
            throw BrandThemeException("Field 'barHeightPx' ($barHeightPx) must be between 8 and 128")
        }

        val trailHeightPx = entries.optionalInt("trailHeightPx") ?: (barHeightPx / 2).coerceAtLeast(4)
        if (trailHeightPx !in 2..barHeightPx) {
            throw BrandThemeException(
                "Field 'trailHeightPx' ($trailHeightPx) must be between 2 and barHeightPx ($barHeightPx)",
            )
        }

        val cornerRadiusPx = entries.optionalInt("cornerRadiusPx") ?: 6
        if (cornerRadiusPx !in 0..64) {
            throw BrandThemeException("Field 'cornerRadiusPx' ($cornerRadiusPx) must be between 0 and 64")
        }

        val indeterminateMode = entries.optionalEnum("indeterminateMode", IndeterminateMode.BOUNCE)

        val frameIntervalMs = entries.optionalInt("frameIntervalMs") ?: 32
        if (frameIntervalMs !in 8..1000) {
            throw BrandThemeException("Field 'frameIntervalMs' ($frameIntervalMs) must be between 8 and 1000")
        }

        val stepPx = entries.optionalInt("stepPx") ?: 4
        if (stepPx !in 1..128) {
            throw BrandThemeException("Field 'stepPx' ($stepPx) must be between 1 and 128")
        }

        val indeterminateSegmentRatio = entries.optionalDouble("indeterminateSegmentRatio") ?: 0.35
        if (indeterminateSegmentRatio !in 0.05..0.95) {
            throw BrandThemeException(
                "Field 'indeterminateSegmentRatio' ($indeterminateSegmentRatio) must be between 0.05 and 0.95",
            )
        }

        return BrandTheme(
            name = name,
            runner = runner,
            reverseRunner = reverseRunner,
            flipRunnerOnReverse = flipRunnerOnReverse,
            trailTile = trailTile,
            trailColors = trailColors,
            trailStyle = trailStyle,
            trackColor = trackColor,
            borderColor = borderColor,
            barHeightPx = barHeightPx,
            trailHeightPx = trailHeightPx,
            cornerRadiusPx = cornerRadiusPx,
            indeterminateMode = indeterminateMode,
            frameIntervalMs = frameIntervalMs,
            stepPx = stepPx,
            indeterminateSegmentRatio = indeterminateSegmentRatio,
        )
    }

    private fun loadSprite(source: BrandAssetSource, fileName: String, fieldName: String): BrandSprite {
        val bytes = source.readBytesOrNull(fileName)
            ?: throw BrandThemeException("Missing asset '$fileName' referenced by '$fieldName' in ${source.description}")

        if (fileName.lowercase().endsWith(".svg")) {
            val doc = ByteArrayInputStream(bytes).use { stream ->
                SVGLoader().load(stream, null, LoaderContext.createDefault())
            } ?: throw BrandThemeException("Failed to parse SVG asset '$fileName' for '$fieldName'")
            val size = doc.size()
            val w = size.width.roundToInt().coerceAtLeast(1)
            val h = size.height.roundToInt().coerceAtLeast(1)
            return SvgBrandSprite(doc, w, h)
        }

        val image1x = decodeRaster(bytes, fileName, fieldName)
        val retinaName = deriveRetinaFileName(fileName)
        val image2x = retinaName?.let { candidate ->
            source.readBytesOrNull(candidate)?.let { decodeRaster(it, candidate, "$fieldName@2x") }
        }
        return RasterBrandSprite(image1x, image2x)
    }

    private fun decodeRaster(bytes: ByteArray, fileName: String, fieldName: String): BufferedImage {
        val decoded = try {
            ByteArrayInputStream(bytes).use { ImageIO.read(it) }
        } catch (err: Exception) {
            throw BrandThemeException("Failed to decode image '$fileName' for '$fieldName'", err)
        } ?: throw BrandThemeException("Unsupported or corrupt image '$fileName' for '$fieldName'")

        if (decoded.width <= 0 || decoded.height <= 0) {
            throw BrandThemeException("Image '$fileName' for '$fieldName' has non-positive dimensions")
        }
        return decoded
    }

    private fun deriveRetinaFileName(fileName: String): String? {
        val dot = fileName.lastIndexOf('.')
        if (dot <= 0) return null
        val stem = fileName.substring(0, dot)
        if (stem.endsWith("@2x")) return null
        val ext = fileName.substring(dot)
        return "$stem@2x$ext"
    }

    private fun Map<String, JsonValue>.optionalString(key: String): String? {
        val raw = this[key] ?: return null
        return (raw as? JsonValue.JsonStr)?.value
            ?: throw BrandThemeException("Field '$key' in theme.json must be a string")
    }

    private fun Map<String, JsonValue>.optionalNullableString(key: String): String? {
        return when (val raw = this[key]) {
            null, JsonValue.JsonNull -> null
            is JsonValue.JsonStr -> {
                if (raw.value.isBlank()) {
                    throw BrandThemeException("Field '$key' in theme.json must not be blank when present")
                }
                raw.value
            }
            else -> throw BrandThemeException("Field '$key' in theme.json must be a string or null")
        }
    }

    private fun Map<String, JsonValue>.optionalBoolean(key: String): Boolean? {
        return when (val raw = this[key]) {
            null, JsonValue.JsonNull -> null
            is JsonValue.JsonBool -> raw.value
            else -> throw BrandThemeException("Field '$key' in theme.json must be a boolean")
        }
    }

    private fun Map<String, JsonValue>.optionalInt(key: String): Int? {
        return when (val raw = this[key]) {
            null, JsonValue.JsonNull -> null
            is JsonValue.JsonNum -> {
                val intVal = raw.value.toInt()
                if (raw.value != intVal.toDouble()) {
                    throw BrandThemeException("Field '$key' in theme.json must be an integer, got ${raw.value}")
                }
                intVal
            }
            else -> throw BrandThemeException("Field '$key' in theme.json must be an integer")
        }
    }

    private fun Map<String, JsonValue>.optionalDouble(key: String): Double? {
        return when (val raw = this[key]) {
            null, JsonValue.JsonNull -> null
            is JsonValue.JsonNum -> raw.value
            else -> throw BrandThemeException("Field '$key' in theme.json must be a number")
        }
    }

    private fun Map<String, JsonValue>.optionalStringList(key: String): List<String>? {
        return when (val raw = this[key]) {
            null, JsonValue.JsonNull -> null
            is JsonValue.JsonArr -> raw.items.mapIndexed { idx, item ->
                (item as? JsonValue.JsonStr)?.value
                    ?: throw BrandThemeException("Element $idx of '$key' in theme.json must be a string")
            }
            else -> throw BrandThemeException("Field '$key' in theme.json must be an array of strings")
        }
    }

    private inline fun <reified E : Enum<E>> Map<String, JsonValue>.optionalEnum(key: String, default: E): E {
        val raw = optionalNullableString(key) ?: return default
        return enumValues<E>().firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) }
            ?: throw BrandThemeException(
                "Invalid value '$raw' for '$key' in theme.json; expected one of ${enumValues<E>().joinToString { it.name }}",
            )
    }
}
