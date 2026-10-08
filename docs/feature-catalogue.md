# Toolkit feature catalogue

Research date: 2026-09-21. User selections updated through 2026-10-07. This catalogue records feature decisions and the original feasibility assessment. Android 0.4.0 implements the selected document, downloader and viewer routes. Use [the current capability and verification matrix](native-toolkit-2026-10-07.md) for actual behavior, supported layout subsets and validation boundaries; the feasibility descriptions below are historical planning, not full-fidelity guarantees. See [build and runtime instructions](../BUILDING.md).

User selections below override the original recommendations. Feasibility labels describe implementation work, not whether a feature is possible or a result from a working mobile prototype.

## Confirmed selection

- Keep requested document tools D01-D13, including D04. D14 (PDF/A) is excluded, confirmed by the user.
- Keep PDF additions P01-P05, P09, P14-P17.
- Exclude P06, P07, P08, P10, P11, P12, P13, P18.
- Keep N01-N04. Exclude optional N05-N10. Basic progress, cancellation, resume where supported and ordinary error recovery remain part of the core downloader; scheduling, Wi-Fi-only controls, configurable retry policies, URL-list imports, authenticated site cookies, subtitles, thumbnails and chapter downloads are not selected.
- Keep requested viewers V01-V05 and optional V06, V08, V12. Exclude V07, V09-V11.
- Exclude every X-family tool. Images-to-PDF and camera scanning remain selected document tools; excluding the X family does not remove these.
- P17 covers the requested workflow/pipeline feature. Start with ordered compatible document operations; branching and scripting are outside the proposed first version.
- UI: October 7 reviewed design is the current baseline. Six tool categories on Home, secondary Saved tools, recent tools in Files, Launcher A, Platinum default with Champagne/Jade and independent light/dark/system appearance. Implementation resumed at the user's direction.

Excluding OCR means P14 and PDF-to-editable-document tools must not silently reintroduce text recognition: they work from existing text, report unsupported scans clearly, and may offer explicitly labelled image-based output only where applicable. An image inside a document preview is not a standalone image viewer.

**Local**: a practical candidate for on-device processing; still needs integration and testing. **Advanced**: needs a dedicated engine, compatibility work, or fidelity validation. **Online**: communicates with the selected source service. Local models/engines may need an initial download if not bundled.

## Requested document tools

| ID | Decision | Feature | Feasibility and scope |
|---|---|---|---|
| D01 | Keep | PNG / JPEG / JPG images to PDF | Implemented in 0.1.0: up to 20 images, reorder/remove, A4/Letter, margins, automatic/portrait/landscape orientation, EXIF correction, bounded downsampling and Save as. A user-selectable quality control remains future work. JPEG and JPG are the same format. |
| D02 | Keep | Merge PDFs | Local; choose file/page order; preserve originals. |
| D03 | Keep | Lock / unlock PDFs | Local; password encryption and removal with the required password. Editing/printing restrictions are distinct from encryption. No promise of recovering unknown encryption passwords. |
| D04 | Keep | Lock / unlock Excel files | Feasible locally; moderate for a defined XLSX scope. Separate sheet/workbook editing protection from file-open encryption. Existing libraries handle both categories; Android integration and round-trip preservation need testing. Legacy XLS is a separate compatibility task. |
| D05 | Keep | Compress PDFs | Local / advanced; lossless cleanup plus optional image downsampling. Savings depend on contents; preserve searchable text and show quality tradeoffs. |
| D06 | Keep | Word to PDF | Advanced; DOCX first, legacy DOC separately. Fonts, pagination, tables, and drawings need an Office rendering engine. |
| D07 | Keep | Excel to PDF | Advanced; XLSX first; print areas, page breaks, formulas, charts, and fonts need validation. |
| D08 | Keep | PowerPoint to PDF | Advanced; PPTX first; fonts, charts, shapes and slide rendering need validation. |
| D09 | Keep | PDF to Word | Advanced; rebuild editable document structure; accuracy varies, especially with scans and complex layouts. |
| D10 | Keep | PDF to Excel | Advanced; extract/reconstruct tables; cannot recover original spreadsheet formulas from a PDF. |
| D11 | Keep | PDF to PowerPoint | Advanced; distinguish image-per-slide output from editable reconstruction. |
| D12 | Keep | PDF to PNG / JPEG; extract embedded images | Local; selectable pages, resolution, batch export. |
| D13 | Keep | HTML to PDF | Local for self-contained HTML; remote URLs/resources require network access. |
| D14 | Excluded | PDF to PDF/A | Excluded by the user. PDF/A is an archival PDF standard for long-term reproducibility, not a smaller or generally higher-quality PDF. Revisit only if an archive or receiving organization requires it. |

The common conversion and PDF categories above and below were checked against [iLovePDF's current tool catalogue](https://www.ilovepdf.com/). These are feature references, not a proposal to upload files to that service.

## Optional PDF additions

| ID | Decision | Feature | Feasibility |
|---|---|---|---|
| P01 | Keep | Split PDF; extract selected pages | Local |
| P02 | Keep | Reorder, remove, rotate, duplicate, and insert pages | Local |
| P03 | Keep | Crop page margins | Local; cropping is not secure redaction |
| P04 | Keep | Add page numbers and text/image watermarks | Local |
| P05 | Keep | Camera scan to PDF; perspective correction | Local; delegates capture to the system camera, with manual four-corner correction |
| P06 | Excluded | OCR: images/scans to text and searchable PDF | Advanced; on-device recognition plus text-layer generation, language choices |
| P07 | Excluded | Highlight, draw, add notes/text/images | Local; annotations are separate from editing existing text |
| P08 | Excluded | Fill PDF forms; flatten filled forms | Advanced; AcroForm scope first; XFA separately |
| P09 | Keep | Draw/place a signature | Local; not a certificate-based digital signature |
| P10 | Excluded | Certificate-based signing and verification | Advanced; certificate, trust, and timestamp handling |
| P11 | Excluded | Permanent redaction | Advanced; must remove underlying content, not just draw black rectangles |
| P12 | Excluded | Compare PDFs | Advanced; text comparison and visual comparison are separate modes |
| P13 | Excluded | Repair damaged PDFs | Advanced / best effort; cannot guarantee recovery |
| P14 | Keep | PDF to TXT / Markdown | Selected without OCR; use an existing text layer. Reading order/tables affect quality. Image-only pages cannot be transcribed automatically. |
| P15 | Keep | Markdown / TXT to PDF | Local; fonts, code blocks, tables, pagination |
| P16 | Keep | Inspect/remove document metadata | Local / format-dependent |
| P17 | Keep | Saved batch workflows | Selected; moderate. Ordered steps, compatible input/output types, per-step options, save/reuse, progress/cancel and safe cleanup. E.g. merge -> compress -> lock. No branching or scripts initially. |
| P18 | Excluded | Summarize / translate documents | Advanced; only consider on-device models under the current privacy goal; cloud processing would be a separate explicit choice |

## Requested downloaders

| ID | Decision | Feature | Proposed scope |
|---|---|---|---|
| N01 | Keep | yt-dlp video/audio downloads | Online; multiple URLs, playlist selection, format/quality, audio-only, destination, progress, basic error recovery, cancellation, resumable transfers where supported. Subtitles/thumbnails/chapters and site-cookie login are excluded. |
| N02 | Keep | aria2 direct downloads | Online; multiple jobs, segmented transfers when supported, selected destination, pause/resume, per-file and total speed controls |
| N03 | Keep | Telegram file browser/downloader | Online; account connection, accessible chat/channel/group browser, file-type/search filters, multi-select, queue, destination, sign out |

All three share one persistent queue. Do not equate concurrent jobs with unlimited parallelism. Auto-tune bounded concurrency and show speed/remaining-time estimates. No fixed or maximum speed guarantee.

## Optional download additions

| ID | Decision | Feature | Feasibility |
|---|---|---|---|
| N04 | Keep | Torrents / magnets with selective files | aria2 supports these; requires explicit seeding, bandwidth, and storage controls |
| N05 | Excluded | FTP / SFTP / Metalink sources | aria2 capabilities; credentials and server compatibility need integration |
| N06 | Excluded | Wi-Fi-only, scheduled jobs, retry policy | Android background execution constraints apply |
| N07 | Excluded | Import URL lists; duplicate detection | Local queue preparation; source inspection uses network |
| N08 | Excluded | Download checksums / integrity verification | Local after download |
| N09 | Excluded | Per-site cookies / authenticated downloads | Advanced; sensitive account session storage and source-specific support |
| N10 | Excluded | Download chapters, subtitles, thumbnails | yt-dlp feature-dependent; selection shown after inspection |

Sources: [yt-dlp documentation](https://github.com/yt-dlp/yt-dlp), [aria2 capabilities](https://aria2.github.io/), [TDLib](https://core.telegram.org/tdlib).

## Requested and optional viewers

| ID | Decision | File types | Status and scope |
|---|---|---|---|
| V01 | Keep | PDF | Requested; zoom, thumbnails, search where text is available, bookmarks |
| V02 | Keep | Markdown | Requested; rendered/source modes; remote images disabled by default |
| V03 | Keep | Word: DOCX, DOC | Requested; advanced layout engine; legacy formats separately tested |
| V04 | Keep | Excel: XLSX, XLS | Requested; sheets, zoom, row/column navigation; charts/formula fidelity depends on engine |
| V05 | Keep | Presentations: PPTX, PPT | Requested; slide navigation/fullscreen; animation support is separate |
| V06 | Keep | TXT, CSV, TSV, JSON, XML, log files | Optional; text/table/structured modes; stream large files |
| V07 | Excluded | ODT, ODS, ODP, RTF | Optional; shares the Office-engine decision |
| V08 | Keep | EPUB | Optional; reflowable ebook viewer |
| V09 | Excluded | PNG, JPEG, WebP, GIF, HEIC | Optional; device/decoder-dependent formats |
| V10 | Excluded | Audio and video | Optional; playback depends on codec/device support |
| V11 | Excluded | ZIP / 7z / TAR archive contents | Optional; preview and selected extraction; format support varies by engine |
| V12 | Keep | Local HTML | Optional; scripts and external resources disabled by default |

Format discovery references: [Pandoc format list](https://pandoc.org/), [libarchive](https://www.libarchive.org/), [FFmpeg](https://ffmpeg.org/ffmpeg.html). These lists establish possible categories, not proof that every format has been ported to Android or renders losslessly.

## Other useful tool families to choose from

| ID | Decision | Tools | Assessment |
|---|---|---|---|
| X01 | Excluded | Image resize, crop, rotate, compression, format conversion | Good local addition |
| X02 | Excluded | Remove photo metadata, including location | Good local privacy addition; verify exported metadata |
| X03 | Excluded | Video/audio conversion, trim, audio extraction, compression | Advanced but shares FFmpeg infrastructure |
| X04 | Excluded | ZIP creation/extraction; encrypted archives | Local; explicit format/encryption support |
| X05 | Excluded | Batch file rename; duplicate finder | Local within user-granted folders |
| X06 | Excluded | QR/barcode scanner and generator | Local; camera permission when scanning |
| X07 | Excluded | Text tools: counts, case changes, JSON formatting, Base64, hashes | Local; small optional utility family |
| X08 | Excluded | Unit converter, date calculations, calculator | Local; low complexity, lower relation to file workflows |

## Suggested shared app features

Platinum (default), Champagne and Jade themes, each with independent system/light/dark appearance and locally saved preferences (selected 2026-10-07); accessible large text, search, secondary Saved tools and file History (proposed in the 2026-10-06 review; no empty home sections), share-sheet integration, persistent job history with retention controls, notifications with pause/cancel, default output folder, storage usage/temporary-file cleanup, optional biometric app lock, language settings, export/import preferences, connection management, and About/open-source licenses. Do not add excluded optional downloader settings through this shared-feature list.

The shared-features paragraph is a suggestion list, not a selection of every optional setting. Implemented shared features include themes/profile, search, Saved, file/transfer history, outbound sharing, notifications, download folder/settings, connection management and licenses. Biometrics, localization and preferences import/export were not selected. Current implementation and validation are tracked in the capability report linked above; no design-approval gate is pending.
