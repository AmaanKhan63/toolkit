# Native development — October 7, 2026

Implementation resumed at the user's direction: “Then what are you waiting for?” There is no further design-approval gate. This is a working native increment, not completion of the entire selected catalogue.

## Implemented in Android 0.2.0

- Six tool categories on Home, search, secondary Saved tools, and recent tools in Files. Full-screen Settings, Themes and a locally stored user profile.
- Platinum default, Champagne and Jade; each supports independent System/Light/Dark appearance. The existing appearance preference is preserved. New palette/profile keys extend DataStore without clearing existing preferences.
- Selected Folded T launcher with adaptive and monochrome resources; bundled Phosphor duotone icons and Inter font. No runtime asset downloads.
- Images to PDF: bounded image summary, separate grid organizer with move-to-position/remove, full-width option sheets, numeric linked or separate margins (0–160 reference px, 96 px/inch), actual staged-PDF preview, page navigation and zoom before Save as.
- Local PDF operations below, each with actual output preview before export. Passwords remain transient and are not stored in preferences/history/templates.
- Basic in-app PDF viewing (page navigation, zoom/pan) and bounded text previews. Files history opens supported results internally and offers external Open/Share for ZIP exports.
- Room migration 1 → 2 preserves existing PDF history and adds MIME types for text/ZIP outputs. Application ID and debug signing configuration remain unchanged.

| Catalogue ID | Working native scope | Still outside this increment |
|---|---|---|
| D01 | Image ordering/removal, A4/Letter, orientation, numeric margins, generation, preview and Save as | Selectable image quality |
| D02 | Merge up to 20 PDFs in file order, preserving inputs | Selecting different page subsets per input |
| D03 | AES-256 file-open protection; unlock using current password; password/confirmation validation | Separate printing/editing restriction controls |
| D12 | Render selected PDF pages to PNG/JPEG, longest edge 1200/1800/2400 px, large navigable preview; save batch as ZIP (up to 100 pages) | Extracting embedded image objects; saving individual files directly to a folder |
| P01 | Extract selected ranges into a PDF | Multiple split outputs in one run |
| P02 | Reorder, duplicate, omit and rotate pages | Inserting pages from another document in the same editor; thumbnail page picker |
| P03 | Crop all or selected pages by a numeric inset on each edge | Independent crop edges and visual crop handles |
| P14 | Existing-text-layer extraction to TXT; explicit no-text-layer error | Structured Markdown reconstruction |
| P16 | Clear PDF document information and catalog XMP metadata | Metadata inspection/diff and other document formats; this is not content sanitization |
| V01 | Open PDFs, page navigation, zoom and pan | Search, thumbnails and bookmarks; in-viewer password prompt |

## Remaining selected work

Office/Excel protection and conversion (including the requested Excel → PDF workflow), compression, watermark/page numbers, scanning/perspective correction, signatures, HTML/Markdown generation, full saved workflow execution, yt-dlp/aria2/Telegram/torrent integrations and remaining viewers are still in development. Native unavailable entries say **In development**. No simulated results or queues are presented as working features.

Document operations currently run with the app process, not a persistent background worker. Inputs and jobs survive Activity recreation, but not process death. Password form fields are intentionally not saved across recreation. Abandoned private PDF-tool staging is removed on the next process start. Successful Save as closes the output before recording history; failures attempt partial-file deletion and retain the prepared result for retry.

The PDF viewer renders the actual staged file. Protected-output previews are a private decrypted copy reopened from the newly encrypted output; saved output remains encrypted. Text preview is limited to the first 256K characters; the exported text is complete. PDF-to-images shows actual image files and preserves source-page numbers. PDFs are limited to 2,000 source/output pages per processing run; memory-intensive inputs can still exceed device capacity and report an error.

## Verification

[Recorded results](validation/2026-10-07/checks.json): debug APK and instrumentation build passed; **12 JVM tests passed**, lint reports **0 errors / 28 warnings**, and **14 unique Android tests passed across the suite and a focused corrected-test rerun**. The suite first passed 13/14; its rotated-page text-order assertion was replaced with independent rotation and exact per-page content assertions, then that test passed. The application engine did not change for that correction. [Initial run](validation/2026-10-07/instrumentation-initial.log) and [focused rerun](validation/2026-10-07/page-order-retest.log) preserve the evidence.

Native cases cover actual PDF generation, input preservation, EXIF transforms, corrupted images, cancellation cleanup, page order/duplicates/rotation, wrong and correct passwords, encrypted-output validation, image format/dimensions/source-page ZIP entries, cropping, metadata removal, text extraction, Room history migration, category navigation, themes/profile destinations, and the 20-image organizer to generated-PDF preview flow. Browser prototype checks remain separate. The final picker-only correction was rebuilt with lint, installed as an update without clearing data, and checked manually: choosing one synthetic PDF immediately returned to the tool with one input and visible password fields. Merge retains multiple selection. [Final build log](validation/2026-10-07/final-picker-build.log) and [password form](validation/2026-10-07/native-pdf-password-form.png) record this check; the full engine suite was not repeated for that picker-only change.

Native screenshots were inspected for [Platinum dark Home](validation/2026-10-07/native-home-platinum.png), [Platinum light Home](validation/2026-10-07/native-final-home.png), [Jade](validation/2026-10-07/native-jade.png) and the [light theme selector](validation/2026-10-07/native-platinum-light.png). A [375 dp Home check](validation/2026-10-07/native-375-home.png) retained the two-column layout and scrollable categories. A force-stop/relaunch retained Jade/Dark in Settings, then the palette was returned to Platinum. A landscape Home snapshot at 200% text retained the header/navigation and a scrollable body, but this is not a complete accessibility pass. Physical-device coverage, minimum-API coverage, complete TalkBack testing and the full large-text/tablet/landscape matrix remain outstanding. Emulator display/text overrides were restored afterward.

The first emulator attempt encountered process-start ANRs across Android system services and Toolkit while the host was low on free memory. It did not count as a passing test. Build and emulator runs were separated; only this task's Gradle/Kotlin processes were stopped, and the isolated ToolkitTest emulator was restarted with 3 GB RAM. The second software-rendered attempt also failed during Android startup. Switching the isolated AVD to host graphics (Vulkan disabled), 4 cores, and ahead-of-time package compilation allowed the native suite to run. A System UI ANR still appeared after the suite; emulator stability is a material limitation of this environment. No user device or unrelated emulator was reset.

## Engine and asset sources

PDF processing uses [PDFBox Android 2.0.27.0](https://github.com/TomRoush/PdfBox-Android) with [PDFBox password protection](https://pdfbox.apache.org/2.0/cookbook/encryption.html). Page rasterization uses Android's PdfRenderer. Icons are converted from the official [Phosphor core repository](https://github.com/phosphor-icons/core); the pinned source commit and licenses are under `third-party/` and bundled notices are available in Settings.

The application still requests no network or broad storage permission. Android document providers can independently sync selected cloud-backed files. PDFBox's transitive Bouncy Castle package contains certificate/network-related code flagged by lint; Toolkit does not use those paths or configure any network trust manager. Review/trim those dependencies before the network downloader integration.
