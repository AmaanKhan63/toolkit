# Toolkit feasibility and data design

Research date: 2026-09-21; selection clarifications: 2026-10-05. This document preserves discovery proposals. The [first-slice implementation plan](implementation-plan.md) and [build/runtime notes](../BUILDING.md) describe the implemented Android foundation and D01; do not interpret the broader research below as delivered functionality.

## Current decisions (2026-10-05)

The user selected prototype A, replacing its prominent Favorites grid with Recent and adding a separate Favorites section. The chosen refinement is a neutral light theme with a matching dark theme, restrained blue accents and sharper typography. The user approved refined A version 5 on 2026-10-05 ("revised A feel great"); it is the visual baseline for Android implementation. The former paper/forest appearance is superseded; B and C are historical alternatives.

The [feature catalogue](feature-catalogue.md#confirmed-selection) is the current inclusion/exclusion authority. N04 torrents/magnets and P17 workflows are included. N05-N10 and all X tools are excluded. Optional viewers are limited to V06, V08 and V12. OCR, annotations, forms, certificate signing, redaction, comparison, repair, summarization and translation are excluded by their P IDs. Earlier discovery descriptions of these capabilities are background research, not selected features.

### D04: Excel protection is possible

The earlier blanket Advanced label overstated the difficulty of the narrow operation. Worksheet/workbook editing protection can be toggled with short scripts. File-open encryption is different, but decrypting a supported file with its password is also available through libraries. [openpyxl documents editing protection](https://openpyxl.readthedocs.io/en/stable/protection.html); [msoffcrypto-tool documents Office decryption](https://msoffcrypto-tool.readthedocs.io/en/latest/). The latter's encryption feature is marked experimental, so it is evidence of feasibility, not an automatic engine choice.

The user's prior Python script was not located in this workspace or the memory index. Do not claim which operation it performed. A short script may delegate substantial work to libraries. Proposed scope: XLSX sheet/workbook protection and password-based file encryption/decryption, reclassified as moderate integration work. Preserve the original and test that formulas, formatting, charts and other package parts survive. XLS compatibility is separate. Unknown encryption-password recovery is not implied.

### D14: PDF/A recommendation

[PDF/A (ISO 19005)](https://pdfa.org/resource/iso-19005-pdfa/) is designed for long-term preservation and reproducible rendering; depending on its conformance profile it embeds necessary resources and restricts features that undermine preservation. It is not ordinary compression or an upgrade every PDF needs. Encryption is incompatible with PDF/A conformance, so PDF/A and the final password-lock step must not be presented as simultaneously preserved properties.

Recommendation was to omit D14 unless a receiving organization or archive specifically asks for it. The user accepted this recommendation on 2026-10-05; D14 is excluded.

### P17: proposed first workflow version

Moderate complexity when built on reusable tool operations; substantially larger if it becomes an arbitrary visual programming system. Design operation contracts now rather than bolt a workflow runner onto UI-only tools later.

- A workflow is a named, ordered list of compatible document-processing steps, each with its own options. Use a vertical step list on mobile, with Add step, edit, remove and accessible move-up/down controls. Dragging can be a convenience, never the only reorder method.
- Each operation declares accepted MIME types, whether it consumes one file or a collection, and the possible output types/counts. Offer only valid next steps; revalidate after changes. Merge requires a compatible PDF collection; image-to-PDF consumes images. Split produces a collection: subsequent operations must explicitly support per-file mapping or collecting that set. Reject unsupported combinations before running.
- Examples: selected PDFs -> Merge -> Compress -> Lock -> Save; selected images -> Images to PDF -> Watermark -> Compress -> Lock -> Save.
- Saving/export is an explicit final destination action. A lock operation is normally terminal because later transforms cannot read encrypted output without authorization. Store passwords only for the current run, never in reusable workflow templates or history.
- The runner writes to per-run private staging, preserves original inputs, reports current step plus queued/running/completed/failed/cancelled states, checks disk space and cleans temporary output. Do not publish partial results as successful. On failure stop downstream steps and show the failed step; restarting must not duplicate exported files. Surviving process death requires persisted progress and file-grant reconciliation; secrets may need to be requested again.
- Version workflow templates as data (operation ID/version and non-secret options) so future tool updates can detect incompatible recipes. Reuse the app's job queue and cancellation controls.
- Initial scope: local document tools with implemented operation adapters. Viewers, Telegram login, torrents and download-engine control are not transformation steps. Completed downloads may be selected as workflow inputs. Branches, loops, conditions, custom scripts, schedules and automatic watched folders are outside this first version.

These are proposed behavioral boundaries for the selected P17 feature, not a tested implementation or a fixed delivery estimate.

### Telegram setup status

The user has created a Telegram application identity. The supplied screenshot is credential-bearing, not a UI design reference. Do not upload it to Superdesign, copy it into the repository, transcribe its values into prompts/logs, or embed them in examples. App registration does not mean Toolkit has an authenticated user session; phone/login-code and optional two-step verification still belong in the future on-device authorization flow.

## Intent and assumptions

The requested outcome is one mobile application replacing separate document websites, command-line download tools, and file viewers. The main motivation is keeping personal documents under the user's control. The user wants a researched feature list and multiple UI prototypes before selecting the final scope and UI.

Confirmed platform: Android only for now. Provisional assumptions: personal use first; no Toolkit backend, mandatory account, advertising SDK, or analytics SDK. These are proposals and can be corrected. Optional feature families are not automatically accepted.

Local processing reduces exposure to document-conversion services. It does not make downloads offline or eliminate all security risk. Telegram and download websites still receive the requests needed to serve content. Selecting a cloud-backed folder in Android's file picker may also involve that provider.

## Architecture options

| Approach | Advantages | Costs / tradeoffs |
|---|---|---|
| Native Android: Kotlin + Jetpack Compose | Recommended for direct file access integration, Android background APIs, native engines, accessibility, and system sharing | Android implementation first; an iPhone client would need additional work |
| Flutter with native plugins | Shared visual layer if iPhone is an immediate requirement | Downloader runtimes, Office rendering, filesystem, and background behavior still need platform-specific integration |
| Mobile UI plus a self-hosted server | Large desktop conversion engines are easier to run | Requires a server and transfers documents off the phone; conflicts with the default local-processing goal |

Recommendation is an engineering judgment, supported by [Android's Compose guidance](https://developer.android.com/compose) and the native/background requirements below. Final minimum Android version follows the user's device and chosen engine validation, not a guess at dependency support.

Proposed modules: app shell, file access, preferences/database, job queue, document engines, download adapters, viewers. PDF/Office engines and downloader runtimes are adapters, so one engine can change without rewriting navigation and storage. Reuse well-tested engines; do not recreate encryption, Office layout, or media codecs.

## Processing feasibility and limits

PDF structural work has existing local engines. [qpdf](https://qpdf.readthedocs.io/en/stable/cli.html) documents page operations, encryption and transformations; [PDFBox](https://pdfbox.apache.org/2.0/commandline.html) documents merging, splitting, image/text extraction and rendering. They are candidates, not selected Android dependencies. A short device test must establish a maintained packaging route, supported Android versions, memory use, and license compatibility.

Compression needs two clearly named modes: lossless cleanup and optional image downsampling/recompression. Already optimized files may not shrink. Preserve text, links, and searchable content; never silently flatten every page to a bitmap. Write to a new output and validate it before exposing a completed job.

Office viewers and Office-to-PDF need a genuine layout engine. A parser such as Apache POI does not by itself provide Microsoft-quality page/slide rendering. Investigate an embeddable offline renderer or SDK, assessing cost, licensing, app size, fonts, and supported formats. [Collabora's mobile Office work](https://www.collaboraonline.com/es/collabora-office-android-ios/) shows that local mobile Office is possible; it is not proof of a small, drop-in reusable SDK. Opening another installed app is only an explicit fallback, not fulfillment of the in-app viewer requirement.

PDF-to-editable-Office is reconstruction. Tables, columns, formulas, charts and scanned pages need different treatments. A PDF cannot generally supply the spreadsheet formulas or presentation objects that were discarded when it was created. Retain these requested tools as advanced scope, with tested and visibly stated output limitations.

Excel has three different protections: file-open encryption, workbook structure, worksheet editing. [Microsoft explains this distinction](https://support.microsoft.com/en-us/excel/protect-a-workbook). [POI documents encryption/decryption support](https://poi.apache.org/encryption.html), but Android compatibility and preservation of macros, formulas, charts and formatting require round-trip tests. XLSX is the proposed starting format; XLS is separate. Unlock encrypted files using the supplied password; do not promise recovery of an unknown strong password.

On-device OCR is feasible with language-specific models, for example [ML Kit](https://developers.google.com/ml-kit/vision/text-recognition/v2/android). Bundled and downloaded model paths differ. Searchable PDF creation additionally requires positioning a text layer; recognizing text alone does not implement it.

## Downloader approach

Embed Android-compatible runtimes for yt-dlp, FFmpeg, and a supported JavaScript engine. Current [yt-dlp documentation](https://github.com/yt-dlp/yt-dlp) identifies FFmpeg for separate video/audio merging, and yt-dlp-ejs plus a JS engine for full YouTube support. An [Android wrapper](https://github.com/yausername/youtubedl-android) demonstrates a packaging route, but its bundled runtime, current extractor compatibility, native-library support and licensing need verification before selection. Do not reuse old example storage permissions uncritically.

Offer multiple links/playlists, available quality choices, output container, audio-only, subtitles, per-job folders and bounded parallel jobs. MP4 is a container, not a guarantee of a particular codec or best available quality. Prefer compatible remuxing when possible; transcoding costs time, battery, and quality. Site changes require a reviewed engine update path.

[aria2](https://aria2.github.io/) supports segmented/multi-source transfers, HTTP(S), FTP, SFTP, BitTorrent and Metalink. Native binaries do not automatically understand Android document-provider URIs. Use private staging files followed by an explicit export into the granted output location where necessary; account for extra space. Restrict any aria2 control interface to the app/local endpoint with authentication.

Optimize throughput using bounded concurrent jobs, fragment concurrency where supported, direct multi-connection transfers where the server supports them, and retries with backoff. Measure speed, battery use, storage throughput, and completion success on the actual device. More connections are not always faster. Network conditions and service limits prevent any honest maximum-speed guarantee.

Persist job metadata before starting work. Implement visible cancellation and restart reconciliation; mark unrecoverable jobs honestly. Android can restrict background activity. On supported versions use user-initiated data-transfer jobs for long user-requested transfers, with version-appropriate foreground execution and notification fallback; WorkManager is appropriate for deferrable work. [Android's documentation](https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/long-running) explicitly warns about long-running worker quotas on Android 16.

## Telegram: user account mode recommended

Use [TDLib](https://core.telegram.org/tdlib) to browse chats accessible to the signed-in account. Its [chat-history](https://core.telegram.org/tdlib/docs/classtd_1_1td__api_1_1get_chat_history.html) and [message-search](https://core.telegram.org/tdlib/docs/classtd_1_1td__api_1_1search_chat_messages.html) interfaces support the proposed browsing model. Respect membership, visible history, protected-content restrictions, rate limits and unavailable/deleted messages. Do not silently join chats or auto-download everything.

Personal-build setup, shown step by step in the app:

1. Sign in at [my.telegram.org](https://my.telegram.org) using an existing Telegram account.
2. Open **API development tools**, register an application, and obtain `api_id` and `api_hash`.
3. Enter those values in Toolkit on the phone. Do not paste them into repository files or chat.
4. Sign in through Telegram's supported flow with phone number/login code and two-step-verification password if requested. The implementation must follow TDLib's actual authorization states.
5. Choose a channel/group available to that account, filter files, select items and an output folder, then queue the selection.

Source: [Telegram's application setup instructions](https://core.telegram.org/api/obtaining_api_id). For a distributed application, the developer normally registers an application identity and users sign in; requiring every user to create an API application is a personal-build option, not necessarily the final onboarding design.

A bot is an optional, narrower mode: create it with official BotFather (`/newbot`), save the token locally, and add it to a chat with the appropriate permissions. The hosted [Bot API](https://core.telegram.org/bots/api#getfile) currently limits `getFile` downloads to 20 MB and is not a general user-history browser. A local Bot API server changes file limits but adds infrastructure; it is not the default for this phone-only app.

## Where app data lives

Android creates an app-specific sandbox and provides storage APIs. We must still decide what to store, implement database/preferences writes, encrypt sensitive values, manage temporary files, and define backup/restore. There is no need for a remote database for the requested features.

| Data | Proposed storage |
|---|---|
| Theme, default quality, preferences | Jetpack DataStore in app-private storage |
| Jobs, history, favorites, file URI metadata | Room database in app-private storage |
| Telegram session database | Private TDLib database with encryption configuration; encryption key protected by Android Keystore |
| Optional cookies/tokens/API credentials | Encrypted private files; encryption keys in Keystore; never plain logs or source files |
| PDF/Excel passwords | In memory for the operation by default; no password history |
| In-progress outputs, previews | Private staging/cache with cleanup and disk-space checks; resumable partial files need durable staging rather than disposable cache |
| Finished PDFs/downloads | User-selected location via Storage Access Framework or appropriate MediaStore collection |

Sources: [Android storage](https://developer.android.com/training/data-storage), [DataStore](https://developer.android.com/topic/libraries/architecture/datastore), [Keystore](https://developer.android.com/privacy-and-security/keystore), [document picker](https://developer.android.com/training/data-storage/shared/documents-files).

Android normally removes app-private data when the app is uninstalled or its storage is cleared. Outputs exported to shared/user-selected storage normally remain. In-place app updates preserve data if we keep the app identity/signing and provide database migrations. Backups and migrations are application design work, not something to assume is fully handled by the phone.

[Auto Backup](https://developer.android.com/identity/data/autobackup) can include app data unless configured. The privacy proposal is to exclude credentials, sessions, documents and work files from automatic cloud backup/device transfer, with an explicit user-controlled settings/history export if selected. Verify the actual OS/OEM behavior; do not promise that a single manifest flag covers every transfer path.

Default local-tool behavior: no document uploads, no telemetry SDKs, no secret/file-content logging, originals preserved, narrow file grants, external resources disabled in local HTML/Markdown viewers, and clear history/cache controls. Biometric app lock is optional and separate from file encryption. File parsers and engines still require maintenance and bounded resource use.

## Proposed phases and validation

1. **Discovery and visual selection (complete):** Android-only scope, feature IDs and refined dashboard A version 5 are approved. Carry its visual system into the connected Android flows. Device/distribution details remain implementation inputs.
2. **Focused feasibility checks:** PDF manipulation/compression; Excel round trips and Office rendering; yt-dlp/FFmpeg/JS/native packaging; background transfers and folder access on the user's device. Each check produces evidence before committing to its engine.
3. **Foundation and document slice:** approved navigation, themes, file access, settings, Room/DataStore, queue; images-to-PDF, merge, split/reorder, password operations, defined compression, PDF and Markdown viewing.
4. **Download slice:** yt-dlp and aria2 adapters, shared queue and destination selection; then Telegram account browsing and selected downloads.
5. **Office and chosen extras:** only the selected additions, with explicit fidelity/format support and performance budgets.

Before implementation, turn the selected slice into a written spec and implementation plan. UI prototypes contain dummy state; they do not prove processing, privacy, accessibility or Android compatibility.

Development loop: Android Studio Run builds and installs a debug app on the emulator or connected phone. Compose Preview/Live Edit can shorten supported UI iterations; runtime/native changes still need rebuilding. Automated checks validate processing/data behavior, emulator tests validate Android flows, and physical-device tests validate storage, memory, speed, thermal and background behavior. A signed release APK is for distribution and upgrade testing, not every visual tweak. Preserve the signing key and package identity from the first installed release.
