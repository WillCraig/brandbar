# Example themes — testing and screenshots only

These themes exist **only** to test `BrandThemeLoader` and to make README
screenshots. They are never packaged in the plugin zip (the
`verifyDistributionExcludesExamples` Gradle check, wired into `check`, fails
the build if any `examples/` asset — by path, slug, or content hash — ends up
in the distribution).

## Trademark / fan-art disclaimer

All company names, logos, and characters referenced here belong to their
respective owners. The runners in this folder are simple original SVG
approximations using brand colors only; no copyrighted logo artwork,
screenshots, or official character art is included. The Peter Griffin theme is
original fan-style geometric art — no Fox screenshots or official art.

**These examples are not affiliated with, sponsored, or endorsed by** PNC,
Strix Works, Meta, Google, Netflix, Cursor, Anthropic, Medical Mutual, Union
Home Mortgage, Sherwin-Williams, Apple, Fox, or any other brand owner. Per-theme
asset notes and palette source URLs are in each theme's `SOURCES.md`.

## Layout

Each `themes/<slug>/` folder has the same format as
`src/main/resources/brand/`: `theme.json`, `runner.svg`, and `SOURCES.md`.

## Preview any example without an IDE

```bash
./gradlew renderScreenshots -Pbrandbar.theme=examples/themes/netflix
./gradlew renderExampleScreenshots   # every theme + gallery-overview.png
```
