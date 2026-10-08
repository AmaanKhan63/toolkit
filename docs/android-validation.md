# Android 0.1.0 validation

Historical report. For the current 0.2.0 build, see [native capabilities and verification](native-development-2026-10-07.md) and [recorded results](validation/2026-10-07/checks.json). The artifact path below is reused by builds; its historical size and hash do not describe the current APK.

Date: 2026-10-05. This report applies to the first native foundation and Images-to-PDF slice, not the full feature catalogue.

## Artifact

- APK: `app/build/outputs/apk/debug/app-debug.apk`
- Application ID: `com.amaankhan.toolkit`
- Version: 0.1.0 (1), debug signing, min API 26, target API 36.
- Size: 11,614,766 bytes.
- SHA-256: `121E3DEBE8160FF0727479B60C853F8A4FECFAEF650CC838858D71BF9BB5E1C1`.

## Completed checks

| Check | Result |
|---|---|
| Debug APK and instrumentation APK build | Pass |
| JVM page-fitting, orientation, invalid-input and memory-budget tests | 8 passed |
| Android PDF engine tests on isolated ToolkitTest AVD | 5 passed |
| Native navigation, settings themes, modal Back and Images-to-PDF entry test | 1 passed |
| Android lint | 0 errors, 16 warnings |
| Merged application manifest | No Internet or broad storage permission; only app-scoped dynamic-receiver permission |
| Actual image picker → two images → reorder → create PDF | Pass |
| Cancel Save as → retain prepared PDF → retry save | Pass |
| Saved PDF parsed/rendered independently with PyMuPDF | 2 A4 pages, expected reordered colors, valid 13,483-byte PDF |
| Force-stop/relaunch after export and preference changes | Dark theme, pinned Images to PDF, Recent and saved-file history retained |
| Native light/dark and composer/success screenshots | Visually inspected |

The test emulator uses the installed Android 36.1 Google APIs Play Store x86_64 image and a Pixel 6 profile. It has its own AVD under ignored `.local/`; no existing app/emulator data was modified. Inputs were synthetic blue/green PNGs and instrumentation-generated fixtures, not personal documents.

PDF tests verify readable PDF output, page order and dimensions, rendered center colors/white margins, unchanged source hashes, rotated image orientation, all eight EXIF transforms, rejection of corrupt input without staged output, and cancellation cleanup. The UI test uses Espresso 3.7.0 for compatibility with the emulator's input APIs.

Lint warnings concern intentionally pinned dependency/tool versions, target SDK freshness, conservative free-space checking, and optional Kotlin URI helper usage. No lint baseline or suppression was added to hide them. The environment also prints an SDK metadata-version warning; this did not prevent builds or tests. One KSP daemon emitted a nonfatal shutdown exception while rebuilding instrumentation; the task completed successfully and the resulting test APK passed all six tests.

## Evidence

- [Native light dashboard](android/home-light.png)
- [Native dark dashboard](android/home-dark.png)
- [Reordered images and page options](android/images-to-pdf.png)
- [Successful export](android/pdf-saved.png)
- [Independent exported-PDF check](android/export-check.json)
- Generated JVM report: `app/build/reports/tests/testDebugUnitTest/index.html`.
- Generated lint report: `app/build/reports/lint-results-debug.html`.
- Instrumentation command: `adb -s emulator-5556 shell am instrument -w com.amaankhan.toolkit.test/androidx.test.runner.AndroidJUnitRunner`, final result `OK (6 tests)`.

## Remaining boundaries

No physical phone testing, minimum-API emulator testing, complete TalkBack/large-text audit, OEM backup/transfer validation, low-storage provider fault injection, or long-running background/process-death recovery validation. The first run is process-bound and capped at 20 input images. Downsampling is automatic; user-selectable image quality is still future work. Provider-dependent export cleanup is best effort and exposes an explicit warning on failure.

Other document tools, actual workflow execution, downloaders and in-app viewers are not implemented. Open PDF hands off to an installed viewer and is labelled accordingly. No production signing or repository push occurred. See [the build and first phone check](../BUILDING.md) for installation and the next manual test.
