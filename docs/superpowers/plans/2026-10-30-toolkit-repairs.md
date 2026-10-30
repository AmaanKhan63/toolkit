# Toolkit usability and reliability repairs

**Goal:** Resolve the ten reported problems in the existing native Android app.

**Architecture:** Keep Kotlin/Compose, private local processing, existing databases and package identity. Separate document input, configuration and recovery states; keep downloader engines and readers behind their existing models.

**Global constraints:** Preserve com.amaankhan.toolkit, signing and installed data. No broad storage permission. No credentials or personal screenshots in fixtures. Keep the existing theme palettes and supported document formats. No publishing or installation on a personal device without a request.

## Design decisions

- Images to PDF: up to 500 images, using disk-backed PDF assembly and bounded image decoding. Show import progress and preserve selected order. Full-width reorder rows; layout options collapsed behind a summary, with accessible controls.
- Input screens: a prominent file-selection area, formats and useful three-step guidance; replace the empty instruction card. Use existing native styles.
- PDF protection: explicit Protect/Unlock choices; only ask for an existing password when the source needs it. Keep reader permissions under advanced options.
- Navigation: download tools belong in Tools, Transfers tracks current/completed jobs, Files has one Open file action and visible supported reader formats.
- File access: retain document grants, graceful reselect/retry paths for expired access, no raw Android exceptions. A shared folder picker starts in Documents and explains Android's restricted roots.
- Media: diagnose actual subprocess errors, bounded retries/timeouts, useful safe error categories, accurate progress/size display, conservative default quality. Do not claim a live-site fix from synthetic tests alone.

## Tasks and checks

- [x] Image engine: add a regression exceeding the former 20 image cap, implement disk-backed assembly, test page count/rendering/order/cancellation. Files: PdfLayout.kt, ImagePdfEngine.kt, ToolkitViewModel.kt, ImagePdfEngineTest.kt.
- [x] Media engine: trace runMedia, request options, progress and update behavior; add regression coverage for identified faults and implement repairs. Files: downloads/*.kt and engine tests; no UI edits in this task. Live full-transfer reliability remains unconfirmed; see repair report.
- [x] Native UI: shared selection panel and responsive choice fields, image organization/layout, conditional protection fields, unified file opening and recovery, Transfers navigation and clear destination controls. Files: ui/*.kt, PdfReaderModel.kt and document-access helpers.
- [x] Validate JVM tests, lint, native engine/UI tests on an isolated emulator; inspect compact/large-text layouts and successful/failure states. Build ARM64 update, verify package/version/signing against 0.4.1. Record exact boundaries and any externally blocked checks. See `docs/toolkit-repairs-2026-10-30.md` for results and live-site limitations.

## Review focus

- Hundreds of high-resolution inputs must not retain hundreds of decoded bitmaps.
- Revoked URI grants must offer reselect without showing provider internals.
- 320dp and enlarged fonts must keep labels/actions usable.
- Media retries must preserve cancellation and never silently omit failed fragments.
- Protected inputs must retain a path to enter the old password without confusing unprotected inputs.

## Progress

- Initial inspection: clean main at 33e6a46; branch fix/toolkit-usability-reliability created. No connected Android device. Root-folder prohibition confirmed in Android documentation. Current PDF opening does not persist grants and renders raw exception messages.

## Follow-up: reproducible failure evidence

- [x] Stop further live YouTube investigation at the user's request; do not infer a file-size cause from a test deadline.
- [x] Add bounded private media reports, sanitized engine context, screenshot codes/IDs, user-chosen export and recent-report access after retry.
- [x] Verify local HTTP failures, inspection/save capture, real engine version, retention/export, redaction and recovery guidance; complete safety review.
- [x] Refresh the compact diagnostics screenshot and prepare the repository review gallery. User approval of the UI remains pending; branch publication is separately authorized.
