# Store preparation (Live App Testing draft)

This directory holds the draft material for a later Amazon Live App Testing (LAT) / Appstore submission (`docs/PLAN-F3b.md` section 5). **Nothing here has been submitted.**

| File | Content |
|---|---|
| `metadata-en.md`, `metadata-de.md` | Listing text, category, content rating, export compliance, privacy answers |
| `privacy.md` | Privacy policy draft (en + de), to be hosted later |
| `test-criteria-checklist.md` | Amazon Test Criteria status with evidence, tester notes, open technical points |
| `icon-512.png` | Store icon 512x512 PNG, rounded corners transparent |
| `icon-1280x720.png` | Banner 1280x720 PNG |
| `screenshots/` | Screenshots 1920x1080 from the Fire TV (not there yet; to be chosen with the maintainer, 3 to 10) |

Unfilled data is marked `[to be filled]` or `<to be filled by the maintainer>`.

## Regenerate the icons

The two PNGs are rendered from the app drawables (`drawable/ic_launcher.xml`, `drawable/banner.xml`) by a Robolectric test that overwrites them on every run:

```
scripts/dock ./gradlew :app:testDebugUnitTest --tests '*StoreAssetsTest*'
```

(`app/src/test/kotlin/io/github/knoellp/roomscreen/StoreAssetsTest.kt`.) The launcher drawable has an opaque background; the 512 icon is masked to rounded corners so that it has real transparency.
