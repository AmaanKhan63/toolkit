# Toolkit 0.4.2 repairs

Branch: `fix/toolkit-usability-reliability`. Native Kotlin/Compose implementation; package and signing identity preserved.

**UI approval is pending the user's review.** The [native screenshot gallery](validation/2026-10-30-ui/index.html) contains the proposed screens at standard and compact sizes. Pushing the repair branch is not design approval.

## Changes corresponding to the reported issues

1. **Images to PDF:** increased the cap from 20 to 500. Imports report progress and preserve order. PDF assembly uses disk scratch storage and decodes one image at a time. JPEG photos use bounded decoding and quality-90 compression; PNG transparency is flattened on white. Large batches still require sufficient free storage.
2. **File selection:** PDF, Office and rich-document tools share an inline selection action, supported formats and a short workflow explanation.
3. **Image layout:** full-width arrangement rows, accessible up/down/remove actions and a direct move-to-position dialog. Page layout is expandable; labels and values no longer compete for half a narrow row. Thumbnails fit without cropping.
4. **PDF protection:** explicit protect/remove-password actions. Current password appears only when the selected input requires one; protection uses new password and confirmation. Reader permissions are expandable.
5. **Media reliability:** bounded connection/retry waits, cancellation of the Python/FFmpeg process group, specific sanitized failure messages, refreshed sources on retry, serial fragments and failure on missing fragments instead of exporting incomplete media. Default video choice is MP4 at up to 1080p. The engine remains independently updatable.
6. **Navigation:** Tools contains Download tools; Transfers lists active/completed jobs; Files collects saved outputs and the reader.
7. **Folders:** the shared picker starts in Documents and explains choosing a subfolder. Android restricts the storage root and main Download directory; this is an OS rule, not a missing broad-storage permission. See [Android storage restrictions](https://developer.android.com/about/versions/11/privacy/storage#document-access-restrictions).
8. **Transfer presentation:** queue-first screen, active/completed/all filters, actual media titles, readable byte units and remaining time, wrapping recovery actions, and an explicit unknown-size state instead of an unreliable fragment estimate.
9. **Files and readers:** one Open a file action routes to PDF or the existing local document reader. Supported formats are visible: PDF, Word, Excel, PowerPoint, EPUB, Markdown, text/CSV and HTML. Existing Office rendering limits still apply.
10. **Reader recovery:** preserve the exact document URI and read grant. Missing/revoked files and damaged documents show safe messages with Retry and Choose another file. Rendering failures and evicted preview cache recover through the same flow. No raw provider/process exception is displayed.

## Validation

Validation uses the isolated API 36.1 x86_64 emulator, installed with `adb install -r` over Toolkit 0.4.1. No phone was connected or modified. Logs and synthetic screenshots are under ignored `.local/validation-2026-10-30/`.

- JVM tests: 30 tests, zero failures, including six report/redaction regressions.
- Android lint: zero errors, 68 warnings.
- Image engine regression coverage includes a 500-page synthetic batch, page order/rendering, cancellation, corrupt input cleanup, PNG alpha, asymmetric margins, JPEG orientation/compression and unchanged originals.
- Native media fixtures exercise an actual 403 response, failed HLS fragments, cancellation and transfer-state preservation.
- Reader tests exercise real cross-package URI grants, revoked PDF/text access, damaged input, cache eviction and retry.
- Final broad native run: `OK (83 tests)`, 359.97 seconds, zero failures. This comprises 80 executed tests and three opt-in live-network cases gated by assumptions. The independently executed live cases are documented below.
- The broad run includes existing navigation, document-reader, interaction, native transfer and storage-migration coverage. It also passes all five real reader-access recovery tests and four new screen-flow checks.

### Live YouTube check

The supplied `lh4JdZTJe7k` link is a long video. The installed Android app successfully inspected it and downloaded/remuxed a playable ten-second section at up to 360p using the bundled engine. This does **not** establish successful completion of the full twelve-hour transfer or reproduce the original phone/network failure.

The real in-app updater also passed: yt-dlp `2025.11.12` to `2026.10.26`, status `DONE`. New installs still use the library's bundled engine until **Transfers > Transfer settings > Update yt-dlp** is used. Pause media jobs before updating.

The repeat ten-second-section check after that update successfully inspected the link but exceeded the 180-second transfer timeout. That is a failed live validation; it does not establish whether the engine version or network/site behavior caused the difference. The section check uses FFmpeg's network section-download path, whereas regular transfers use yt-dlp's normal download path. The reported intermittent full-download problem therefore remains unconfirmed after these reliability repairs.

A separate opt-in check then exercised the actual `DownloadEngines.video` path at the new 1080p/MP4 default, with a 2 MiB/s cap. Its first attempt reached the 180-second test deadline while receiving data. A final attempt allowed 300 seconds to reach a 104 MiB checkpoint, then planned to pause/resume for another 4 MiB. It received **51,856,554 bytes** before the test deadline; no classified engine failure was raised before cancellation. This is a failed validation, not a completed download. Neither crossing the original roughly 98 MB failure point nor live pause/resume was established. The test cleans up its partial downloads. The final regular suite has an additional gated live test available; it was added after the recorded 83-case broad run.

**Issue 5 remains partially resolved:** local retry, cancellation, incomplete-fragment handling and progress/error reporting are repaired and tested. Reliable completion of the user's long YouTube video still needs confirmation on the actual phone/network. The update is not presented as a verified cure for that intermittent failure.

The evidence does not identify file size as the cause. Toolkit's media request sets no maximum file-size limit, and its production path has no five-minute overall transfer deadline. That deadline belongs to the opt-in test. The previous engine wrapper replaced every nonzero process exit with the same generic error, so the original screenshot cannot distinguish a network, site, format, storage or engine problem. A long video makes complete verification slower; it does not establish a size-related defect. A future investigation must capture a real classified failure or complete the transfer and exercise resume, rather than treating a test deadline as the original bug.

### Reports for the next failure

At the user's request, further live-site investigation stopped. Media download, inspection and save errors now carry a safe code and report ID. **Save error report** exports a text file through Android's document picker; **Transfers > Transfer settings > Recent error reports** also provides reports after retrying. The latest 20 reports remain in private non-backed-up app storage, with bounded text and orphan temporary-file cleanup. Nothing uploads automatically.

Reports include app and actual yt-dlp versions, Android/device/ABI, network transport, available storage, requested/resolved formats, stage, elapsed time, process exit, HTTP status codes, last stream progress, retry-event count, app stack and sanitized recent engine errors. Source metadata is limited to the host and a YouTube video ID when applicable. Request credentials, signed URLs and private paths are redacted. Review the file before sharing. A code/ID screenshot helps identify the failure; sending the text report supplies substantially more context. No report can guarantee a root cause for every external or OS failure.

The [example report](validation/2026-10-30-ui/sample-error-report.txt) was produced by the real Android downloader against a synthetic local 403 response. Tests verify credential/header/escaped-value/path redaction, bounded history, reload/export, actual engine version, download/inspection capture, preserved folder-access recovery and compact UI accessibility. The safety review's findings were corrected and re-reviewed. New diagnostic checks use local fixtures; no additional YouTube transfer was attempted. The final focused native run reported `OK (13 tests)` in 77.496 seconds: ten executed checks and three gated live cases, with zero failures.

## Phone artifact

- `build/distributions/Toolkit-0.4.2-arm64-debug.apk` — 104,120,073 bytes.
- Package `com.amaankhan.toolkit`, version `0.4.2`, version code `6`, ABI `arm64-v8a`; min SDK 26, target SDK 36.
- APK signature verified; SHA-256 signing-certificate digest matches the existing 0.4.1 ARM64 APK.
- APK SHA-256: `9f0dd8f0c195b7a2e876638893757cae498f5f8bd0491df5f460fba092b71585`.
- Install over the existing app without uninstalling. The matching debug signing identity supports updates from the earlier delivered debug build; this is not a production-signed release.

### Compact-screen review

All four revised-flow tests passed at 320dp width (840px at density 420) with system font scale 1.3: image organization/layout, conditional protection, transfer progress/recovery, and Transfers/Files navigation. A test was corrected to scroll the home catalogue before selecting Protect. No production adjustment was needed after this run. After diagnostics were added, the transfer flow was rerun at the same compact settings: `OK (1 test)`, 11.476 seconds, including the report action and recovery controls. The isolated emulator had to be restarted after becoming unresponsive; the rerun passed without an app change. The emulator's size, density and font setting were restored.

Standard and compact native screenshots were visually inspected and are available in the [repository review gallery](validation/2026-10-30-ui/index.html). This inspection is distinct from the user's pending approval. Content scrolls where necessary and actions wrap; screenshots use synthetic inputs.

## Limits

Physical-device/OEM background restrictions, minimum-API behavior and a complete twelve-hour media transfer have not been validated. The 500-image stress fixture proves batch count/order/cleanup, not a 500-photo high-resolution device benchmark. No Telegram account login was performed. Generated UI prototypes are design references; native screenshots and emulator tests are the implementation evidence.
