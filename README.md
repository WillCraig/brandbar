# Brand Bar

A brandable progress-bar plugin for IntelliJ-based JetBrains IDEs (`since-build` 243 / 2024.3+). Fork this repository, swap your artwork and colors into `src/main/resources/brand/`, and build a company-skinned progress bar without editing Kotlin.

## Clean-room & artwork notice

Brand Bar is an original, clean-room project licensed under the [MIT License](LICENSE).

- It is inspired only by the general idea of custom progress-bar plugins (such as [Nyan Progress Bar](https://plugins.jetbrains.com/plugin/8575-nyan-progress-bar)) and is implemented from scratch in Kotlin using public IntelliJ Platform APIs (`LafManagerListener`, `ApplicationActivationListener`, `JBUI`) and Swing `BasicProgressBarUI`.
- No code, comments, class names, or assets were copied from `batya239/NyanProgressBar` or `TreeFireMen/intellij-pokemon-progress-ikun`.
- This repository ships **no** Nyan Cat artwork (Nyan Cat is copyrighted by Chris Torres / PRguitarman), no Nyan rainbow clone, and no IKun or celebrity basketball artwork. All bundled placeholder graphics under `src/main/resources/brand/` are simple, original geometric assets.

## Fork and brand it

A fork only needs to edit files in **`src/main/resources/brand/`** to reskin the progress bar:

```text
src/main/resources/brand/
├── theme.json        # Colors, dimensions, and indeterminate animation settings
├── runner.svg        # Forward runner graphic (.svg or .png)
├── runner.png        # Optional raster alternative (automatically paired with runner@2x.png)
├── runner@2x.png     # Optional 2x HiDPI companion when using raster PNGs
├── trail.png         # Optional horizontally tiled trail overlay/pattern
└── trail@2x.png      # Optional 2x HiDPI companion for trail.png
```

### Step-by-step rebranding

1. **Replace the runner graphic:**
   - Drop your logo or mascot into `src/main/resources/brand/` as an `.svg` (for example `runner.svg`) or `.png` (`runner.png`, plus `runner@2x.png` for HiDPI displays) and point `"runnerImage"` in `theme.json` at that filename.
2. **Configure reverse motion (optional):**
   - In `"BOUNCE"` indeterminate mode, the runner reverses direction from right to left.
   - By default (`"flipRunnerOnReverse": true` and `"reverseRunnerImage": null`), Brand Bar horizontally mirrors `"runnerImage"` when moving left.
   - If your logo contains text or asymmetric details that should not be mirrored, either set `"flipRunnerOnReverse": false` or provide a dedicated `"reverseRunnerImage"` file (for example `"runner-left.svg"` or `"runner-left.png"`).
3. **Customize the trail and track:**
   - Set `"trailColors"` to one or more `#RRGGBB` / `#AARRGGBB` hex colors rendered as horizontal `"STRIPES"` or a horizontal `"GRADIENT"`.
   - Optionally set `"trailImage"` to a tileable `.png` (with optional `@2x.png`) or `.svg` that tiles across the filled trail, or set `"trailImage": null` for pure color fills. At least one of `"trailImage"` or non-empty `"trailColors"` is required.
4. **Preview without opening an IDE:**
   - Run `./gradlew test` or `./gradlew renderScreenshots` and open `build/reports/screenshots/brandbar-overview.png` to inspect 0%, 25%, 50%, 75%, 100%, bounce, and travel frames immediately.
5. **(Optional) Change plugin coordinates for internal distribution:**
   - Update `<id>`, `<name>`, and `<vendor>` in `src/main/resources/META-INF/plugin.xml` and `id` / `name` in `build.gradle.kts`, then run `./gradlew buildPlugin` to produce the distributable ZIP under `build/distributions/`.

### `theme.json` reference

| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `name` | `string` | `"Brand Bar"` | Display name shown in headless review sheets and diagnostics (non-blank). |
| `runnerImage` | `string` | *(required)* | Relative filename inside `src/main/resources/brand/` (`.svg` or `.png`). |
| `reverseRunnerImage` | `string \| null` | `null` | Optional left-moving runner asset used during `"BOUNCE"` return travel. |
| `flipRunnerOnReverse` | `boolean` | `true` | Mirror `runnerImage` horizontally when moving left and `reverseRunnerImage` is `null`. |
| `trailImage` | `string \| null` | `null` | Optional tile graphic drawn across the filled progress trail. |
| `trailColors` | `string[]` | `[]` | Hex colors (`#RRGGBB` or `#AARRGGBB`) painted along the trail. |
| `trailStyle` | `"STRIPES" \| "GRADIENT"` | `"STRIPES"` | Horizontal color bands (`STRIPES`) or left-to-right linear gradient (`GRADIENT`). |
| `trackColor` | `string` | `"#1E293B"` | Unfilled progress-bar track background (`#RRGGBB` or `#AARRGGBB`). |
| `borderColor` | `string` | `"#334155"` | Rounded 1px track border color (`#RRGGBB` or `#AARRGGBB`). |
| `barHeightPx` | `int` | `20` | Logical bar height in px before `JBUI.scale` (`8..128`). |
| `trailHeightPx` | `int` | `barHeightPx / 2` | Logical filled trail height in px (`2..barHeightPx`). |
| `cornerRadiusPx` | `int` | `6` | Logical rounded corner radius in px (`0..64`). |
| `indeterminateMode` | `"BOUNCE" \| "TRAVEL"` | `"BOUNCE"` | `"BOUNCE"` reverses end-to-end; `"TRAVEL"` moves left-to-right and wraps. |
| `frameIntervalMs` | `int` | `32` | Indeterminate animation timer interval in milliseconds (`8..1000`). |
| `stepPx` | `int` | `4` | Logical px advanced per animation frame before `JBUI.scale` (`1..128`). |
| `indeterminateSegmentRatio` | `number` | `0.35` | Active trail segment length as a fraction of bar width (`0.05..0.95`). |

## Running tests & verification

Requirements: **JDK 21** (Temurin 21 recommended). Gradle 9.8.1 is invoked via `./gradlew`.

```bash
# Compile, build plugin archive, run unit tests, and render headless Java2D screenshots
./gradlew build test

# Render headless Java2D screenshots only
./gradlew renderScreenshots

# Run JetBrains Plugin Verifier against sinceBuild 243 and latest stable IDEs
./gradlew verifyPlugin
# (or check only the baseline 243 IDE locally: ./gradlew verifyPlugin -Pbrandbar.verifierChannel=current)
# (or check latest EAP builds: ./gradlew verifyPlugin -Pbrandbar.verifierChannel=eap)

# Boot a real IntelliJ IDEA Community instance headlessly under Xvfb (Starter + Driver),
# assert BrandProgressBarUI is registered in UIManager, and capture an in-IDE screenshot
./gradlew integrationTest
```

> On headless Linux hosts without an X display (`ubuntu-latest`), install `xvfb` (`sudo apt-get install -y xvfb`) before running `./gradlew integrationTest`; JetBrains IDE Starter automatically invokes `xvfb-run` when `DISPLAY` is unset.

## Where screenshots and CI artifacts land

Every CI run executes on GitHub-hosted `ubuntu-latest` runners (`.github/workflows/build.yml`) and uploads reviewable artifacts so changes can be inspected without opening an IDE:

| Output | Local path | GitHub Actions artifact |
| --- | --- | --- |
| Headless Java2D renders (0%, 25%, 50%, 75%, 100%, indeterminate bounce & travel frames, plus composite `brandbar-overview.png`) | `build/reports/screenshots/*.png` | `brandbar-screenshots` |
| Live booted IntelliJ IDEA screenshot (`BrandIdeSmokeTest` under Xvfb) | `build/reports/ide-screenshots/in-ide-brandbar.png` | `brandbar-ide-screenshot` |
| Plugin Verifier compatibility reports (stable & EAP) | `build/reports/pluginVerifier/**` | `plugin-verifier-stable`, `plugin-verifier-eap` |

JetBrains and IntelliJ are trademarks of JetBrains s.r.o. This project is not affiliated with JetBrains.
