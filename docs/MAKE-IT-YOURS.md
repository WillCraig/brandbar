# Make It Yours

Brand Bar is built to be rebranded. Forking needs no Kotlin changes: change
the files under `src/main/resources/brand/`, rebuild, and install.

## The brand folder

```text
src/main/resources/brand/
├── theme.json        # colors, dimensions, indeterminate animation
├── runner.svg        # runner graphic (.svg or .png; PNG may add runner@2x.png)
├── runner-left.svg   # optional dedicated reverse runner for BOUNCE mode
└── trail.png         # optional tiled trail pattern (optional trail@2x.png)
```

### Image rules

- Runner: SVG preferred (crisp at any scale), or PNG with a `@2x` HiDPI twin.
  Keep it small (tens of KB) and roughly 32×24 px logical size; it is scaled
  to the bar height at paint time.
- Trail image: horizontally tileable PNG/SVG, or omit it and use colors.
- File names must stay inside the brand folder (relative names only). At
  least one of `trailImage` / non-empty `trailColors` is required.

### `theme.json` fields

| Field | Meaning |
| --- | --- |
| `name` | Display name used in screenshot sheets and diagnostics. |
| `runnerImage` | Runner file (required). |
| `reverseRunnerImage` | Optional left-moving runner; if null and `flipRunnerOnReverse` is true, the runner is mirrored instead. |
| `flipRunnerOnReverse` | Mirror the runner when it bounces back left. Set false for text logos. |
| `trailImage` / `trailColors` / `trailStyle` | Trail tile and/or `#RRGGBB`/`#AARRGGBB` colors, as `STRIPES` or `GRADIENT`. |
| `trackColor` / `borderColor` | Unfilled track and 1px border colors. |
| `barHeightPx` (8–128) / `trailHeightPx` (2–barHeight) / `cornerRadiusPx` (0–64) | Geometry, in logical px before IDE scaling. |
| `indeterminateMode` | `BOUNCE` (reverses end-to-end) or `TRAVEL` (wraps left-to-right). |
| `frameIntervalMs` (8–1000) / `stepPx` (1–128) / `indeterminateSegmentRatio` (0.05–0.95) | Animation timing, step, and moving-segment length. |

## Preview without opening an IDE

```bash
./gradlew renderScreenshots                              # bundled theme
./gradlew renderScreenshots -Pbrandbar.theme=examples/themes/netflix
./gradlew renderExampleScreenshots                       # all examples + gallery
```

Rendered PNGs land in `build/reports/screenshots/` and
`build/reports/example-screenshots/` (0, 25, 50, 75, 100%, plus indeterminate
frames and an overview sheet).

## Path 1: a company rebranding it

1. Fork this repo (or use it as a template).
2. Swap the art and colors in `src/main/resources/brand/` (see above); start
   from any folder in `examples/themes/` as a reference for the format.
3. Change the plugin identity: `<id>`, `<name>`, and `<vendor>` in
   `src/main/resources/META-INF/plugin.xml`, plus `id`/`name` in the
   `intellijPlatform.pluginConfiguration` block of `build.gradle.kts`.
4. Keep the `NOTICE` file and add a visible
   **"Based on Brand Bar by Will Craig"** line to your plugin description —
   Apache-2.0 section 4(d) requires redistributions to keep `NOTICE`.
5. Run `./gradlew test renderScreenshots` and inspect the screenshots.
6. Build with `./gradlew buildPlugin` (zip in `build/distributions/`), then
   either install from disk on each machine or publish the zip to a private /
   custom plugin repository for your company's IDEs.

## Path 2: an individual making their own

1. Fork the repo, or just clone it.
2. Copy an example theme (for example `examples/themes/will-craig-personal/`,
   `examples/themes/peter-griffin/`, `examples/themes/sailboat/`, or
   `examples/themes/beer/`) into `src/main/resources/brand/`.
3. Edit `theme.json`, drop in your own images, preview headlessly (above).
4. Run `./gradlew buildPlugin`.
5. In your IDE: **Settings → Plugins → ⚙ → Install Plugin from Disk…** and
   pick the zip from `build/distributions/`.

That's it — one person, one folder, no Kotlin required.

## License and attribution

Brand Bar is licensed under Apache-2.0 (see `LICENSE`). Under section 4(d),
anyone who redistributes it — including forks and rebranded builds — must
keep the attribution in `NOTICE`. Please also keep a visible
"Based on Brand Bar by Will Craig" line in your plugin description.
