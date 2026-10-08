# Toolkit 0.4.1: phone feedback fixes

The user reported rectangular backgrounds on icons in dark mode, input focus remaining after outside taps, and previews requiring navigation buttons. These corrections preserve the selected tools and palettes.

## Changes

- Removed the decorative 20% fill path from all 29 shared Phosphor vectors. Foreground shapes, semantic tinting, button hit areas and selected-navigation indicators remain.
- Routed all app text fields through a shared component that registers their focus and visible bounds. The root observes completed taps without consuming them. An outside tap clears focus and hides the keyboard; switching inputs, tapping password visibility controls, dragging, selecting text and pressing other buttons remain interactive. The PDF search/page-number bottom sheet has its own focus area because it occupies a separate window.
- Added left/right swipes to generated and opened PDF previews, exported-image previews and long-text parts. One deliberate swipe advances one page; first/last-page bounds are retained. At enlarged zoom, dragging pans instead. Pinch zoom and navigation buttons continue to work.
- Added section swipes to spreadsheet sheets, presentation slides and EPUB chapters through the shared document viewer. Native WebView horizontal scrolling takes priority for wide content, vertical scrolling stays native, and scripts/network/file access remain disabled.
- The initial regression run found that the short last part of a text document left an insufficient swipe target. The handler now covers the whole text preview pane.
- Version is 0.4.1/code 5, with the existing application ID and debug signing identity. The phone artifact uses ARM64 only.

The zoom/pan interaction uses the installed Compose `transformable` overload with `canPan`, following the [AndroidX implementation contract](https://android.googlesource.com/platform/frameworks/support/+/4d0ff4691c6b051084acf4451a795a06a0c16a13/compose/foundation/foundation/src/commonMain/kotlin/androidx/compose/foundation/gestures/Transformable.kt).

## Validation

| Check | Result |
|---|---|
| Final emulator build | assembleDebug, assembleDebugAndroidTest, testDebugUnitTest and lintDebug succeeded. |
| JVM tests | 12 passed; no failures or skips. |
| Lint | Zero errors, 56 warnings. |
| New interaction regressions | All 8 passed in the final rerun, including all three dark palettes, normal and bottom-sheet input focus, password controls, PDF/image swipes, pinch zoom, zoomed panning, real XLSX sheet switching, wide native HTML scrolling and long-text chunks. |
| Existing viewer/navigation regressions | All 9 passed in the preceding combined run. This covers document safety/rendering, selected tool routes, forms, settings and preview flows. |
| Manual keyboard check | The real Home search field reports focused=false after tapping Your tools; Android's input method reports mInputShown=false after dismissal settles. |
| Visual review | Native Platinum dark Home inspected after the vector changes. |
| Upgrade | Installed over the prior emulator app without uninstalling. |

There are 17 distinct passing UI cases across the final eight-case rerun and the nine existing cases. The initial combined 16-case run had one failure (the short-text swipe area); its output is retained alongside the successful rerun. The document/download engine suite was not repeated for these interaction-only changes. The [earlier report](validation-2026-10-08.md) records that separate coverage.

Evidence is under `docs/validation/2026-10-08-interactions/`. Physical-phone behavior still needs the user's retest; this pass used the isolated API 36.1 x86_64 emulator. Real-account Telegram and camera/OEM coverage remain as previously deferred.

## Repository publication

At initial publication preparation, `main` was unborn and the session restricted `.git` to read-only access. Shell connections to github.com failed, and the configured GitHub connector returned 404 for `AmaanKhan63/toolkit`. The user subsequently enabled full access and requested commits and push on 2026-10-08; shell GitHub access and source validation then succeeded. Git history records the resulting publication state.

A local publication script and SHA-256 manifest in ignored `.local/` prepare 25 focused commits. The [publication plan](publication-plan.md) lists the feature imports, the three separate phone-feedback fixes and the final release evidence. Related tests accompany their feature where possible. These are review groups for an initial import, not a reconstruction of historical commits; intermediate import commits have not each been built.

The script verifies reviewed source bytes, stages saved baseline content through Git blobs without replacing working files, requires `main` and the expected origin, and refuses an existing history or staged changes. A normal non-forced push is available with `-Push`. The publication sequence reconstructs all 392 reviewed final files, including the documentation; APKs, local configuration and signing keys are excluded.
