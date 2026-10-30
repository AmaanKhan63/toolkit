# Toolkit

An Android toolbox for on-device document processing, file viewing and downloads. Kotlin + Jetpack Compose; Android 8.0 (API 26) and newer.

Version **0.4.2** improves document selection, image arrangement, PDF password flows and viewer recovery. Images to PDF accepts up to 500 images with disk-backed assembly. Download tools live under Tools; Transfers tracks jobs; Files opens all supported document formats. It retains PDF tools, Office conversions/protection, camera scanning, saved compatible workflows, eight viewer categories, and yt-dlp / aria2 / Telegram / torrent downloads. Platinum is the default palette; Champagne and Jade have matching light and dark appearances.

The 0.4.2 UI is awaiting user approval: [native screenshot gallery](docs/validation/2026-10-30-ui/index.html). See the [repair report](docs/toolkit-repairs-2026-10-30.md) for verification results and the still-unconfirmed intermittent YouTube failure.

Development resumed on October 8. The HTML verification issue is resolved, document viewers and small-screen layouts have been corrected, and the download TLS checks pass. Telegram account and physical-device checks are deferred at the user's request. See the [engine and layout validation report](docs/validation-2026-10-08.md). The 0.4.1 update removes icon backplates, dismisses inputs on outside taps, and adds horizontal swipe navigation to previews; see the [interaction report](docs/interaction-fixes-2026-10-08.md).

Open this directory in **Android Studio**, select **app** and an emulator, then press **Run**. Android Studio builds and installs updates; there is no need to download an APK for development.

- [Build and run](BUILDING.md)
- [Current capabilities, limits and validation](docs/native-toolkit-2026-10-07.md)
- [Selected feature catalogue](docs/feature-catalogue.md)
- [Implementation status](docs/implementation-plan.md)
- [Design decisions](docs/design-review-2026-10-07.md)

Document processing stays on the device. Downloaders connect to the selected source. There is no Toolkit backend, account service, analytics or cloud upload. Settings/history are private Android data; completed files use Android's file/folder picker. Telegram requires the user's own login and stores its session locally.

Office conversion uses the documented local layout subset: Word reading layout, Excel tables/cached values, and PowerPoint slide geometry/basic shapes/images/text. Complex charts, SmartArt, exact Word pagination and advanced typography are not reproduced with Microsoft Office fidelity. PDF-to-editable formats use existing text; OCR and PDF/A remain excluded.

The configured remote is https://github.com/AmaanKhan63/toolkit.git. The 0.4.2 repairs are on `fix/toolkit-usability-reliability`, with UI approval tracked separately in the review gallery. Media failures offer a private, exportable error report for troubleshooting. Historical reports remain under docs; the current capability report supersedes their unfinished-feature lists.
