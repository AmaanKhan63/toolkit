# Android implementation status

The user authorized completion using the reviewed design. No design or command approval is pending. Launcher A, Platinum default, Champagne/Jade, categorized Home, secondary Saved and recents in Files remain the baseline.

Version 0.4.0 implements document tools D01-D13 and P01-P05/P09/P14-P17, downloaders N01-N04, and viewers V01-V06/V08/V12. Excluded IDs remain excluded. Every selected tool has an operational native route.

See [capabilities and verification](native-toolkit-2026-10-07.md) for supported conversion layouts, account-dependent validation and device limits. Source coverage does not establish full Office rendering fidelity or physical-device certification.

Integrated areas:

1. Reviewed Compose navigation, local profile and three palettes; independent appearance, shared bottom-sheet selectors and actual output previews.
2. PDF operations, numeric margins, selected pages, password forms, compression/stamping/signatures, camera/manual perspective correction and metadata.
3. XLSX/legacy XLS protection/table conversion; Word/PPT readers/converters; PDF text-to-Office and visual slide output.
4. Compatible saved workflows starting with Excel, images, PDF, Word, presentations, HTML or text; independent-document batches; runtime passwords.
5. Persistent foreground queue using native yt-dlp/FFmpeg, aria2 direct/torrent and TDLib Telegram. Save/retry/cancel use scoped storage and preserve finished user files.
6. PDF search/bookmarks/thumbnails; Markdown, Office, structured text, EPUB and safe HTML readers.

Validation combines JVM rules, actual Android engines, native navigation, independent Office-file readers and emulator visual review. Telegram account flows and physical-device/OEM behavior require their real environment; synthetic tests do not cover them. Historical first-slice reports remain for provenance; their unfinished-feature lists are superseded.

October 8 resume: the user explicitly deferred Telegram account and physical-device checks. HTML verification, presentation fit, CSV rendering boundaries, accessible control wrapping, narrow-screen Home, Settings navigation and theme contrast were addressed. See [resumed validation](validation-2026-10-08.md) and the [download TLS review](tls-review-2026-10-08.md).
