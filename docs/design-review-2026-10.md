> Current native status: implementation resumed on 2026-10-07. See [development progress](native-development-2026-10-07.md). The review below is a historical design record.

# Toolkit whole-app design review

**Current follow-up:** [October 7 theme and interaction review](design-review-2026-10-07.md). Launcher A and all three themes are selected, with Platinum as the default; the remaining interaction review is separate. The v7 descriptions and snapshots below are historical, while the shared hosted draft now contains the October 7 corrections.

Date: 2026-10-06. Status: proposed revision, awaiting user review. Supersedes the placement of Recent/Favorites in the previously approved A v5. Feature implementation is paused for this review; selected features have not been dropped.

## Review artifacts

- [Interactive whole-app prototype, A revision v7](https://p.superdesign.dev/draft/41479730-3489-47d2-b335-cf4b829f3717). Use **Review screens** for state navigation and **Icon concepts** for the three launcher choices. Start state has no fabricated history or downloads.
- [Superdesign canvas](https://superdesign.dev/teams/17c539cf-4d05-433f-a594-93ba26fecbc0/projects/67a07e9d-597f-4bbc-8416-567520d5b34b).
- [Launcher and icon-family comparison](prototypes/review-2026-10/icons-comparison.png).
- [Home dark](prototypes/review-2026-10/home-dark.png), [home light](prototypes/review-2026-10/home-light.png), [category](prototypes/review-2026-10/category-dark.png), [configure](prototypes/review-2026-10/configure.png), [result preview](prototypes/review-2026-10/preview.png), [downloads](prototypes/review-2026-10/downloads.png), [Files](prototypes/review-2026-10/files.png), [workflows](prototypes/review-2026-10/workflow.png), [Settings](prototypes/review-2026-10/settings.png).
- [Local interactive HTML](prototypes/review-2026-10/index.html). Uses remote font, Tailwind and icon resources for this browser design review; this is not the native application's dependency model.

Authored directly after the design-generation command failed twice in the host shell before generation began. Imported to the existing Superdesign draft with reversible history; v5 remains the historical approved baseline. No new Android build or source changes were made in this review.

The prototype covers screen families, not final behavior for every control of every tool. Input selection, rendering, downloads and authentication are fictional. Some specialized controls explain intended behavior; page operations, file previews and reader engines are not implemented by this HTML. The per-screen contracts below remain authoritative for the subsequent detailed native design and implementation.

## Browser verification

[Check results](prototypes/review-2026-10/browser-checks.json): main conversion ordering/options/preview/page navigation, cancel Save as, edit options retained, sample save into Files; JPEG search alias and no-match state; pin into secondary Saved; queue pause/resume/retry; Telegram and torrent selection; workflow compatibility/encryption-last order. All 22 document entries open a setup and result concept, and all eight reader families render.

Checked 25 screen routes at 320px and 390px in light and dark: no horizontal document/main overflow, invalid inline handlers or uncaught JavaScript errors. Also checked a landscape configuration and a coarse enlarged-text browser proxy for document overflow. These are not native large-font, TalkBack, keyboard or process-state tests. Screenshots were visually inspected. The nine visible Phosphor category/family samples loaded successfully. Native launcher masks remain untested.

## Evidence and diagnosis

Reviewed the user's current dashboard screenshot, all current Compose UI source (`ToolkitApp.kt`, `ImagesToPdfScreen.kt`, `ToolkitTheme.kt`, `Glyph.kt`, `ToolCatalogue.kt`), launcher vector, and existing emulator screenshots. This is a source and visual design review, not a new native accessibility or runtime test.

The initial prototype overrepresented populated, successful states. Native implementation then exposed empty personal sections and an entire future catalogue. Approval of that prototype did not validate these first-use flows. The resulting first viewport spends most of its space explaining missing content. Repeated bordered cards, detached star buttons and identical document icons obscure hierarchy. This needs information architecture and interaction work, not just a palette change.

Only D01 Images to PDF currently processes files. Settings, theme persistence and saved-file history exist. D02–D13, selected P tools including workflows, N01–N04 and all in-app viewers remain pending. “Coming soon” was accurate but made the unfinished catalogue dominate the product. The previous build was a functional first slice; it was not the agreed complete app.

## Proposed structure

Keep three stable destinations: **Tools · Downloads · Files**. Settings is a full screen opened from the app bar. Theme selection lives there; the prototype may expose a separate review-only theme switch.

- Tools opens six compact categories: Create PDF, Convert, Organize pages, Protect, Optimize, Workflows. Show a short subtitle explaining each category. Search covers the selected catalogue and uses type aliases such as JPG/JPEG. No flat catalogue on the landing screen.
- Remove Recent and Favorites from the home layout. Files has a History filter for actual completed activity. An optional Saved view, reached from Tools, holds pinned tools and workflow templates. It takes no home space when unused. No star appended to every tool row; pinning belongs in the tool's overflow menu.
- A category screen groups concise rows within a shared surface. Tool icon, name, one-line outcome and chevron; hierarchy comes from spacing and typography, not a border around every row.
- Downloads is a queue with Add download. Its source picker offers Video/audio link, Direct file, Telegram and Torrent/magnet. It does not masquerade as a list of tools.
- Files is a library of accessible outputs and explicitly opened documents, with Open file, type filters, search and History. It is not a promise to index the entire phone.
- For interim builds, default discovery shows working tools. A clearly separate Development status view can expose planned scope. No fake active queues or repeated Coming soon cards. Prototypes explicitly label future behavior as simulated.

## Screen-by-screen findings and replacement contracts

| Screen or state | Current issue / delivery status | Proposed interaction and review requirement |
|---|---|---|
| Launcher | Four generic squares; static vector only | Three identity candidates with normal, circular mask, small-size and monochrome samples. Select before adaptive-icon implementation. |
| Tools, first use | Empty Recent/Favorites dominate; long ungrouped list | Six categories, search and a compact useful entry. No fabricated history, greetings or statistics. |
| Tools, returning user | Recents duplicate catalogue; stars consume row width | Same stable home. Saved is user-requested secondary navigation; History is in Files. |
| Search | Three independently filtered lists; ambiguous empty copy | One categorized result list, clear query, no-match recovery, name and format aliases. Back restores prior category/scroll position. |
| Category | Missing | Short grouped rows with distinct semantic icons and plain-language descriptions. PDF/A and excluded tools never appear. |
| Tool input, empty | Explanatory cards repeat instructions | One task explanation, supported inputs, primary Choose files. No settings until relevant. System picker remains Android-owned. |
| Images to PDF, populated | Tall file cards; options occupy long scrolling form | Reorderable thumbnails, page count, add/remove, accessible move buttons. Compact options in grouped rows. Actual page preview reflects margins/orientation. |
| Document configuration | Most tools unbuilt | Reuse input list, options, output summary and sticky Preview result. Specialized controls only where the operation needs them. |
| Processing | Inline progress and blocked navigation | Named operation, honest page/step progress, Cancel; background/resume wording must match actual engine capability. No invented completion estimate. |
| Result ready | Text says ready; actual PDF cannot be inspected | Render the staged output before export. Page thumbnails, next/previous, zoom, page count, size, Back to edit and Save as. Preview changes must invalidate stale output. |
| Save destination | System picker; cancel retains staged PDF | Keep Android Save as. Returning after cancellation restores the preview. Saving failure retains recoverable staged output and offers retry/different destination. |
| Saved result | Basic text card and external Open/Share | Real output thumbnail, name, actual location/size, View, Share and Done. Do not state Saved until stream close succeeds. |
| Files, empty | Passive explanation | Open a file plus Create a PDF; explain what appears here in one sentence. |
| Files, populated | Each file has many permanent buttons; destructive history action prominent | Thumbnail/type, filename, size/date; tap previews. Overflow contains Share, Show location where supported, Remove from history. Deletion is a separate explicit operation. |
| Missing/revoked file | Generic toast | Inline unavailable row, Locate file/Grant access or Remove from history; do not discard metadata automatically. |
| PDF viewer | External intent only | In-app page renderer, thumbnail strip, zoom, text search when a text layer is supported, bookmarks. No annotation/OCR features. |
| Word viewer | Unbuilt | Document canvas, page/fit controls, read-only indication; explain unsupported layout elements instead of implying Microsoft fidelity. |
| Excel viewer | Unbuilt | Sheet tabs, scrollable grid, frozen headings where supported, cell/formula details, zoom. Avoid squeezing a full sheet into unreadable text. |
| Slides viewer | Unbuilt | Slide thumbnails, current/total count, fullscreen. No promise of animations. |
| Markdown and text | Unbuilt | Rendered/source toggle for Markdown; readable monospace/source modes for text/JSON/XML/logs, table mode for CSV/TSV. Large-file progress; remote Markdown images off by default. |
| EPUB | Unbuilt | Reading view, chapters, type size/theme, remembered location. |
| Local HTML | Unbuilt | Render/source modes, scripts and external resources disabled by default; no embedded remote web browser. |
| Downloads, empty | Four buttons lead to placeholder dialogs | One Add download CTA with short supported-source guidance; queue tabs need meaningful empty states. |
| Video/audio setup | Unbuilt | Paste one or several links → inspect → select playlist items, format/quality, audio/video, destination → queue. Source-reported maximum quality; no guaranteed maximum network speed. |
| Direct download setup | Unbuilt | URLs, destination, file naming, server-dependent connections and speed limits in expandable advanced settings. |
| Telegram onboarding | Unbuilt | Explain developer app credentials versus account login, link to my.telegram.org, then API setup → phone/code → optional 2FA → accessible chats. Prototype never collects real credentials. |
| Telegram browser | Unbuilt | Chat/channel/group search, file filters, multi-select, destination, selected count and Add to queue. Access errors explain that membership/permissions are required. |
| Torrent/magnet setup | Unbuilt | Metadata loading → file selection with sizes → destination and visible seeding policy. Distinguish download complete from active seeding. |
| Download queue | Unbuilt | Active/completed grouping; source/name, progress/size, speed; pause only where supported, cancel confirmation, retry error. No excluded scheduling/cookie/subtitle options. |
| Workflow library | Placeholder card/dialog | Templates in Saved; Create workflow in Tools. First-use example is clearly a template, not history. |
| Workflow editor | Unbuilt | Vertical compatible steps, per-step options, input/output types, reorder/remove, validate before run; encryption last. No node canvas, branches or scripts. |
| Workflow result | Unbuilt | Step-level progress/failure, safe cancellation, final actual preview before saving. Never persist passwords in templates. |
| Settings | Long prose in a bottom sheet; multiple unrelated sections | Full-screen grouped Appearance, Files/storage, Privacy/connections, About. Progressive disclosure. Clear cache/history descriptions and separate confirmations. |
| Licenses/clear history | Basic dialogs exist | Readable scrollable licenses; clear-history dialog states exported files remain. Consistent dismiss/back/focus behavior. |
| Errors and permissions | Mostly notice/toast | In-context problem + actionable recovery: wrong password, corrupt/unsupported input, low storage, revoked access, network loss and partial export. Keep user input on recoverable failure. |

## Complete selected-tool mapping

| Proposed category | Selected IDs |
|---|---|
| Create PDF | D01 Images, P05 Scan, D06 Word, D07 Excel, D08 Presentations, D13 HTML, P15 Text/Markdown |
| Convert | D09 PDF to Word, D10 PDF to Excel, D11 PDF to presentation, D12 PDF to images/extract images, P14 PDF to text/Markdown |
| Organize pages | D02 Merge, P01 Split/extract, P02 Reorder/remove/rotate/duplicate/insert, P03 Crop, P04 Watermark/page numbers, P09 Draw/place signature |
| Protect | D03 PDF lock/unlock, D04 Excel protection |
| Optimize | D05 Compress, P16 Document metadata |
| Workflows | P17 Compatible saved document pipelines |
| Downloads | N01 yt-dlp, N02 aria2 direct, N03 Telegram, N04 torrents/magnets |
| Files/viewers | V01–V06, V08 EPUB, V12 local HTML |

For Excel protection, show Editing protection and File-open password as distinct modes. For conversion to editable Office, disclose reconstruction limitations at the relevant option. Image-only PDF to text reports no text layer; it must not offer excluded OCR. A drawn signature is not a certificate signature. Crop is not redaction.

## Shared preview contract

Preview means rendering the actual staged result, not displaying input thumbnails or a success label. Every operation that generates a renderable file must offer preview before save. PDF uses pages; Office uses the appropriate viewer; image export uses a result gallery; TXT/Markdown uses text/rendered mode. Multi-output batches show an output list. Password operations show protection state and allow temporary in-memory unlock when necessary. Metadata changes show before/after fields. A format that cannot be rendered gets an explicit limited-preview explanation; never silently substitute input content. Download completion opens a supported viewer, or offers Open with for excluded media/archive formats.

## Identity and visual system

Keep the user's neutral light/dark and restrained blue choice. Reduce repeated borders; use grouped surfaces, meaningful spacing, one primary CTA, and a visible selected-tab pill. Inter remains the typeface. Titles 24–28, row titles 15–16, body 14–16, metadata 12–13. Android touch areas at least 48dp; text must wrap instead of colliding with actions.

Propose Phosphor: duotone for category/large tool icons, regular for secondary actions, filled for selected navigation. This is one family with deliberate roles, not mixed icon packs. Source: [Phosphor](https://phosphoricons.com/), [official SVG repository](https://github.com/phosphor-icons/core). Bundle selected vectors in the native app; no network icon dependency. Preview the real family before selection.

Launcher concepts: **A Folded T**, a distinctive typographic fold; **B Toolcase**, a simple everyday toolbox; **C Connected modules**, three interlocking components. Compare at 24/48/96px and inside circle/squircle masks. Include themed monochrome. Proposed marks are concepts, not selected brand assets. Native implementation must follow [Android adaptive-icon guidance](https://developer.android.com/develop/ui/views/launch/icon_design_adaptive).

## Review gate and validation boundaries

Review the new home, category, tool configuration, actual-result-preview concept, downloads, Files and Settings together. Choose the launcher concept and icon treatment. Only then revise the native shell and implement the preview foundation before expanding the engines. Prior A v5 approval does not automatically approve this revision.

Browser interaction/screenshot checks cover prototype navigation and layout only. Native TalkBack, system font scaling, permission dialogs, keyboard/IME, process death, rendering fidelity and Android adaptive masks require separate native verification after implementation. New screens in the prototype demonstrate intended behavior with fictional data, not working processing or authenticated services.
