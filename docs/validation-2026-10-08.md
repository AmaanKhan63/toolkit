# Toolkit: resumed validation and UI corrections

The user resumed the 0.4.0 checkpoint on October 8, then explicitly chose: **Leave account and physical-device checks for later**. Telegram login, real-camera/OEM behavior and physical ARM-device testing remain deferred. No credentials were requested, embedded or used.

## Corrections

- Presentation previews now scale the complete slide to the phone width, including positioned text and images. The old fixed-width layout clipped the right side. The viewer keeps JavaScript disabled and text remains searchable.
- Local document controls wrap instead of hiding behind horizontal scrolling. Fullscreen section navigation reserves room for long chapter/sheet names.
- WebViews are clipped to their content area. Switching CSV from Source to Table previously painted white over the dark navigation header. Quoted cell line breaks are preserved.
- Home switches from two columns to a readable list below 360dp or above 120% font scale. PDF page/zoom controls and the bookmark toolbar wrap as needed.
- Returning from Themes to Settings starts at the top instead of inheriting the Themes page's scroll offset.
- Champagne light secondary text was slightly darkened: its contrast on tinted panels increased from 4.48:1 to 4.55:1. All six palette/appearance combinations meet 4.5:1 for the checked body, secondary and button-label color pairs.
- The HTML test now inspects rendered text through native WebView search. It no longer attempts a JavaScript DOM probe in a JavaScript-disabled viewer.

## Review scope

Native review used the isolated ToolkitTest emulator (API 36.1 x86_64), at 1080 x 2400 with 420dpi/100% text and 540dpi/130% text (320dp width). All three palettes were reviewed in light and dark. Synthetic DOCX, XLSX, PPTX, EPUB, Markdown, HTML and quoted/multiline CSV files are archived with screenshots. Sheet/chapter navigation, full-slide fit, fullscreen and actual generated PDF preview were exercised.

The seven fixtures contain no user documents. They broaden sample coverage without establishing Microsoft Office fidelity for charts, SmartArt, exact print layouts or advanced typography. The supported local-rendering subset remains documented in the [capability matrix](native-toolkit-2026-10-07.md).

## Security review

The actual aria2 and yt-dlp engines, plus Android's default HTTPS client, rejected a fresh untrusted certificate served over emulator loopback. The dependency trust-manager findings were traced to unused Bouncy Castle EST helpers; no lint suppression or permissive trust manager was introduced. See [TLS review](tls-review-2026-10-08.md).

## Evidence

| Check | Result |
|---|---|
| Final Gradle build | assembleDebug, assembleDebugAndroidTest, testDebugUnitTest and lintDebug succeeded. |
| JVM tests | 12 passed. |
| Native engine/integration cases | 45 passed; one optional live-site case skipped. Includes real download engines, TLS rejection, Office/PDF processing, storage recovery and migrations. |
| Final viewer/navigation rerun | All 9 passed at 320dp width and 130% text. Covers encrypted PDF/search/bookmarks, slide fit, CSV clipping, safe HTML, all selected tool routes, settings/profile, 20-image preview, workflow saving and downloader forms. |
| Lint | Zero errors; 64 warnings retained. Dependency TLS warnings are reviewed separately, not suppressed. |
| Theme contrast | All 24 checked text/background pairs meet 4.5:1. |
| Installation | Replaced the installed app without uninstalling; existing debug signer retained. |

The native total is **54 passing cases and one optional skip across the engine run and final UI rerun**, not a claim that the first combined run was green. The initial 55-case run exposed six UI/test failures; the intermediate UI rerun reduced these to two. The fixes include the real bookmark/Settings navigation issues plus screenshot capture and lazy-list selectors in the test harness. Initial failures are archived alongside the successful final rerun. The separately enabled live YouTube inspection from October 7 remains historical evidence; it was not repeated here.

Results and synthetic review assets are under [validation/2026-10-08-toolkit](validation/2026-10-08-toolkit/), including [final UI results](validation/2026-10-08-toolkit/ui-final.txt), [build log](validation/2026-10-08-toolkit/build.log) and [theme contrast](validation/2026-10-08-toolkit/theme-contrast.json). The original October 7 checkpoint remains historical evidence; it is no longer a pause instruction.

Final universal debug APK SHA-256: `517a494e209d711f4d3e7b12332ed60cc50bda4163991ed7098bfe2e3b4013f7`. Package `com.amaankhan.toolkit`, version 0.4.0/code 4. Signing certificate SHA-256: `4b77fbe0a667261fcae9d34a9dcd78d2b8ef625553f0de7c61f9653314e61bb7`.

The app remains a development build with the same package and debug signer. Updates use installation with replacement and preserve app data. No production release or repository push was performed.
