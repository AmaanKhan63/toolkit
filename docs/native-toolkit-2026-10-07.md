# Toolkit 0.4.0: native capabilities and verification

This report supersedes earlier unfinished-feature lists. All selected IDs have native implementations and operational screens. This is a development build; implementation coverage, tested fixtures and real-account/device validation are separate facts. Launcher A and all three approved palettes are retained, with Platinum the default.

## Documents

| IDs | Implemented behavior | Practical boundary |
|---|---|---|
| D01 | Images to PDF, compact selection summary, separate arrangement screen, numeric linked/per-side margins, actual page preview and Save as | Up to 20 images; A4/Letter; EXIF correction and bounded downsampling. Margin px use a fixed reference conversion, not device pixels. |
| D02, P01, P02 | Merge with per-input ranges; extract/split selected pages; reorder/remove/rotate/duplicate/insert pages | Page ranges validate against actual inputs. Split batches export ZIP. Originals remain unchanged. |
| D03 | PDF AES-256 protection, runtime current/new password inputs, unlock, print/edit/copy permissions and real preview | Opening encrypted input requires its password. Restrictions are different from encryption. |
| D04 | XLSX Agile AES-256 file protection/removal; XLS legacy file protection; sheet/workbook editing protection/removal | XLSX encryption preserves original package bytes. XLS uses weaker legacy RC4, labelled in the form. No recovery of unknown file-open passwords. |
| D05 | Lossless stream optimization or image downsampling/recompression | Retains text/vector content. Savings depend on input; compare actual output size. |
| D06 | DOCX/DOC to PDF; actual output page selection; searchable local flow layout | DOCX headings/runs/tables/images/basic page breaks; legacy DOC paragraphs. Complex page positioning, fields, drawings and exact Word pagination differ. |
| D07 | XLSX/XLS to PDF with sheet/output-page selection and paginated tables | Cached values; no formula execution, macro execution, chart rendering or exact Excel print layout. |
| D08 | PPTX/PPT to PDF, selected slides/output pages | Original slide size, positioned text/images/basic shapes. PPTX includes basic theme/master/layout inheritance. Charts, SmartArt, complex groups/effects and advanced typography may differ; no animation playback. |
| D09, D10 | PDF text layer to editable DOCX paragraphs or XLSX inferred cells, with preview of generated output | Layout reconstruction is approximate; formulas are not recoverable. Image-only inputs report the missing text layer. No OCR. |
| D11 | PDF to PPTX, one visual page image per slide | Explicitly visual slides, not reconstructed editable text/shapes. |
| D12 | PDF pages to PNG/JPEG at selectable resolution; embedded-image extraction; large previous/next preview | Save ZIP or separate images to a folder. Embedded-image extraction is bounded and does not reconstruct an arbitrary rendered page. |
| D13, P15 | Self-contained HTML, TXT and Markdown to PDF | Local flow layout; no scripts or remote resources. Markdown tables/headings/code are supported. |
| P03, P04, P09 | Per-side crop; page numbers, text/image watermarks; draw/place visible signatures | Crop is not redaction. Drawn signatures are not certificate-based signatures. |
| P05 | System camera/import, manual four-corner perspective correction, grayscale, page reorder/remove and PDF preview | Synthetic geometry tested; real camera hardware/OEM behavior remains untested. No OCR or automatic document-edge model. |
| P14, P16 | PDF text to TXT/Markdown; inspect/remove PDF document metadata | Uses existing text. Metadata cleanup is not a promise to remove every possible identifying mark embedded in document content. |
| P17 | Save/reuse ordered compatible workflows, per-step options, runtime passwords, progress/cancel; independent-file batches return ZIP | Starts with images, Excel, PDF, Word, presentations, HTML or text. Excel unlock > PDF > PDF lock works. Password protection stays last; ZIP outputs are terminal. No branching/scripts. |

Every converter previews actual generated content before saving. Office previews show the supported local renderer's interpretation. A visually correct preview does not establish identical rendering in every external Office application. Passwords are not serialized into templates.

## Downloaders

| ID | Implemented behavior | Verification/boundary |
|---|---|---|
| N01 | Bundled Python/yt-dlp/FFmpeg; multiple links, playlist selection, formats/quality, audio extraction, destination, progress/cancel/resume, manual engine update | Real local media-to-MP3 native run passed; live public YouTube inspection passed with nine formats. Site availability, authentication, DRM and upstream changes still apply. Cookies/subtitles/chapters are excluded. |
| N02 | Native aria2 segmented HTTP(S), persistent queue, pause/resume and range requests, per-job/combined speed limits | Exact-byte multi-megabyte transfer and interrupted range resumption tested against local HTTP fixtures. |
| N03 | TDLib account setup, phone/code/2FA/email states, accessible chats/groups/channels, search/type filters, paging/multi-select, queued downloads and sign out | Native library and encrypted-secret persistence tested. Real login, chat browsing and account downloads require the user's login and are not claimed as verified. No supplied API secret is embedded. |
| N04 | Magnet/local torrent metadata, explicit file selection, aria2 transfer, seed duration/upload cap and destination | Synthetic torrent with a local web seed verified: no payload before selection; only the selected file completed. Public-peer swarm throughput is untested. |

The foreground service uses two transfer slots and bounded native connections. It persists progress, private staging, pending output URIs and completed exports. A destination-write failure keeps the completed download for retry; retries skip already saved outputs and preserve existing destination files. Cancel removes private staging/owned partial files, not completed user outputs. Process interruption leaves active jobs paused. Android/OEM time limits can pause long-running transfers. No maximum-speed guarantee is made.

## Viewers

| IDs | Native behavior |
|---|---|
| V01 | PDF pages/zoom, thumbnails/go-to, existing-text search, local bookmarks and document outline; password prompt for encrypted files. |
| V02 | Markdown rendered/source, tables/strikethrough, search/text sizing; remote resources blocked. |
| V03 | DOCX/DOC reading layout with the Word limits above. |
| V04 | XLSX/XLS sheets, scroll/zoom/search and cached values; at most 100 sheets, 20,000 rows and 256 columns per displayed sheet. |
| V05 | PPTX/PPT sections with positioned slide layout and fullscreen; same supported shapes/images/text subset as conversion. |
| V06 | TXT/CSV/TSV/JSON/XML/log source reading in 64K-character chunks; quoted CSV/TSV tables and JSON formatting. Structured preview is limited to 512K characters; Source reaches the full text. |
| V08 | Reflowable EPUB chapters in package spine order, embedded images, chapter selector/search/text sizing; no DRM decryption. Obfuscated bundled fonts use fallback fonts. |
| V12 | Local HTML rendered/source with scripts, external resources, content/file access and navigation blocked. |

Document WebViews have JavaScript disabled, network loads blocked and restrictive content policy. The reader never executes Office macros or external links. Rich zipped document input/expansion limits prevent unbounded archives; large/unsupported files report errors. Unselected image/media/archive viewers are delegated to installed external applications when opening their exports.

## Storage and upgrade behavior

No backend is needed. Android owns private app directories; Toolkit explicitly manages DataStore preferences, Room history/download databases, workflow JSON, cache and Keystore-protected Telegram configuration. Completed files use user-granted content URIs. Main database migration 1 > 2 is tested; downloads use a separate version-1 database. Application ID/signing are preserved for install-with-replacement updates.

Document work is temporary and must restart after process death. Downloads retain private staging for resume. Backups/transfers are excluded in configuration; OEM/physical behavior has not been certified. See [BUILDING](../BUILDING.md) for the Android Studio workflow and detailed storage boundaries.

## Validation

Development resumed on October 8. The previous HTML verification failure is resolved using native rendered-text search with document scripts still disabled. The resumed work adds whole-slide fit, WebView clipping, adaptive controls, Settings scroll isolation, narrow-screen Home and a native negative TLS test. See the [October 8 validation report](validation-2026-10-08.md) and its archived results for the current build and rerun status. The [October 7 checkpoint](checkpoint-2026-10-07.md) is historical evidence. Telegram account and physical-device checks are explicitly deferred at the user's request.

Coverage includes real Android PDF rendering/encryption, input preservation/cancellation, Office packages and legacy files, perspective geometry, workflow compatibility/batch cleanup, native engines, scoped-storage recovery and Compose navigation. Generated DOCX/XLSX/PPTX were opened by independent Python libraries; generated legacy XLS was independently decrypted with msoffcrypto and read with xlrd. Tests use synthetic documents and never the user's credentials.

Not established by emulator checks: Telegram real-account behavior, full Microsoft Office fidelity, every downloader site, all third-party storage providers, public torrent swarms, physical camera behavior, Android 8/ARM native execution, OEM power management or a signed production release. These are stated boundaries rather than hidden Coming Soon tools.

## Dependencies

Pinned engine dependencies and notices are in Gradle, `office-runtime/` and `third-party/`. Download/reader provenance is in `third-party/Download-reader-SOURCES.json`; runtime FFmpeg reports GPL-3.0-or-later with GPL/version3 enabled. The source and license obligations of bundled components must accompany any later distribution. This run does not publish or relicense the project.

Excluded scope remains excluded: D14, P06/P07/P08/P10/P11/P12/P13/P18, N05-N10, V07/V09-V11 and all X tools.
