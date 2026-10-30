# Build and run Toolkit

## Android Studio

1. Select **File > Open** and choose the project root containing `settings.gradle.kts`.
2. Let Gradle sync. Use Android Studio's embedded JDK when prompted.
3. Choose **app** and an emulator in the device dropdown. **Tools > Device Manager** manages emulators; a connected USB-debugging phone also works.
4. Press **Run**. Android Studio builds, installs and launches Toolkit. Run again after changes; supported Compose edits can use Live Edit.

No APK download or manual installation is needed for each test. Keep the application installed during updates to preserve settings, history and download staging. See the [capability report](docs/native-toolkit-2026-10-07.md) for rendering limits and the [0.4.2 repair report](docs/toolkit-repairs-2026-10-30.md) for changes, validation and the phone artifact.

## Tooling and command-line builds

- Android Gradle Plugin 9.1.1; Gradle wrapper 9.3.1; Kotlin/Compose compiler 2.2.10.
- Compile SDK 36.1, target SDK 36, minimum SDK 26; Build Tools 36.0.0.
- The installed Android Studio JBR works. Ignored `local.properties` supplies the SDK path.
- Gradle bundles the native engines and fonts. The phone needs no standalone Python, yt-dlp, aria2 or Telegram installation.

PowerShell from this directory:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

Universal debug APK: `app/build/outputs/apk/debug/app-debug.apk`. It includes four native architectures and is large because Python, FFmpeg, aria2 and TDLib are bundled. For an ARM64 phone-only build:

```powershell
.\gradlew.bat :app:assembleDebug -PtoolkitAbi=arm64-v8a
```

Other accepted values: `armeabi-v7a`, `x86_64`, `x86`. Omit the property for the universal APK. ARM-only builds do not run on the x86_64 emulator. The output filename is reused; check the build configuration before installing.

Optional command-line installation:

```powershell
adb devices -l
adb -s YOUR_DEVICE_SERIAL install -r .\app\build\outputs\apk\debug\app-debug.apk
```

Identity remains `com.amaankhan.toolkit`, version 0.4.2 / code 6. Keep the same local debug signing key for in-place updates. A production release needs a separately managed release key; no key is committed. Do not uninstall during upgrade tests.

## Tests

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
adb -s emulator-5556 install -r .\app\build\outputs\apk\debug\app-debug.apk
adb -s emulator-5556 install -r .\app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk
adb -s emulator-5556 shell am instrument -w com.amaankhan.toolkit.test/androidx.test.runner.AndroidJUnitRunner
```

The optional `scripts/start-test-emulator.ps1` starts isolated **ToolkitTest** on port 5556 using the installed API 36.1 x86_64 image. AVD data stays under ignored `.local/`; unrelated AVDs are untouched. Wait for `adb -s emulator-5556 shell getprop sys.boot_completed` to report `1`. On a memory-constrained laptop use one Gradle worker and finish compiling before emulator tests.

Inspect instrumentation output for OK / FAILURES; ADB's exit code alone is insufficient. Regular tests use synthetic local fixtures, including actual aria2/yt-dlp/FFmpeg execution. They do not sign into Telegram. The optional live-site check is enabled explicitly:

```powershell
adb -s emulator-5556 shell am instrument -w -e liveNetwork true -e class 'com.amaankhan.toolkit.downloads.DownloadEngineTest#optionalLiveSiteInspectionUsesTheActualAppNetworkContext' com.amaankhan.toolkit.test/androidx.test.runner.AndroidJUnitRunner
```

This queries a public YouTube test video and can fail if the external site/network changes. **Transfers > Transfer settings > Update yt-dlp** updates the media engine; pause video jobs first.

If media fails, use **Save error report** on the failed transfer or inspection error. Attach the exported text file with a screenshot. **Transfer settings > Recent error reports** retains the latest 20 reports after retrying; they stay private until exported. Reports redact credentials, signed URLs and private paths, and include versions, stage, HTTP/process status and recent diagnostic events. Review before sharing.

Debug builds contain a same-UID document-provider fixture for save failure/recovery tests. It requires Android's signature-level MANAGE_DOCUMENTS permission for other UIDs, has no picker entry, and is absent from release builds.

## Native walkthrough

- **Settings > Themes:** Platinum, Champagne, Jade; independent System/Light/Dark. **User profile** is a local name/workspace.
- **Create PDF > Images to PDF:** pick up to 500 images, use Manage to reorder or move to a page number, expand Page layout for margins, preview real pages, then Save as. Images are decoded individually; PDF streams use disk scratch storage. JPEG photos are encoded at quality 90 after bounded decoding; PNG uses lossless RGB with transparency flattened on white.
- **Convert / Organize / Protect / Optimize:** choose inputs/ranges, enter passwords where needed, preview generated content, save a copy. PDF-to-images exports a ZIP or separate images to a selected folder.
- **Word / Excel / PowerPoint to PDF:** inspect actual generated layout and selected output pages. Local rendering limits appear before conversion.
- **Workflows:** choose a starting tool or the Excel-to-protected-PDF example, add compatible steps, save a template, enter runtime passwords, preview/save. Batch mode processes separate documents into a ZIP.
- **Tools > Download tools:** Video & audio, Direct link, Telegram or Torrent / magnet. Track active/completed jobs in **Transfers**, and open saved results in **Files**. Choose Documents or a subfolder as the destination; Android blocks the storage root and main Download folder. Torrents expose file selection before transfer.
- **Previews:** swipe left/right between PDF pages, exported images, document sections or long-text parts. Zoomed PDF/image drags pan the current page; Fit/100% restores page swiping. Wide tables retain horizontal scrolling.
- **Forms:** tap outside an input to clear focus and dismiss the keyboard. Input taps, password visibility controls and selection remain interactive.
- **Files > Open a file:** PDF or Office/Markdown/text/EPUB/local HTML, with automatic PDF routing. PDF has pages, text search and bookmarks; document readers have sections, text sizing/search and fullscreen. Access failures offer Retry and Choose another file; reselecting the same document renews the reader session.
- **Scan:** system camera or existing image, four-corner correction, add/reorder pages and preview PDF.

## Storage and privacy

Android creates app-private storage. DataStore holds profile, palettes, appearance and saved/recent tools. Room holds file history and the download queue; workflow JSON saves options without passwords. PDF bookmarks are local preferences. Main database schema 1 migrates to schema 2 without discarding history; the download database is separate.

Document intermediates use private cache, cleaned at the next app-process start. ViewModel document operations survive Activity recreation; process death requires restarting them. Finished outputs go to user-selected destinations. Clearing history leaves saved files intact.

Download staging uses private, non-backed-up storage for resumption. Two transfer slots share the configured cap; direct transfers use bounded segmentation. Network/service limits still apply. Interrupted active jobs become paused; no automatic boot restart. Save retries retain staging and track already exported files. SAF providers cannot universally promise atomic writes; cleanup failures are reported.

Telegram API configuration and its TDLib database key are sealed using Android Keystore. Its session/files stay private and excluded from backup. The Telegram screen explains API ID/hash setup at https://my.telegram.org/apps, then phone/code/account-password login. A bot token does not replace account login for browsing accessible chat history. No supplied credential screenshot is embedded in source or fixtures. Sign out clears the local connection/session after active Telegram transfers stop.

The app now has Internet, network-state, foreground-transfer, notification and wake-lock permissions. Document readers block scripts and remote resources. Camera capture uses the system camera; storage uses scoped file/folder access without broad storage permission. Backup/transfer exclusions are configured. OEM background policies, camera hardware, physical-device upgrades and minimum-API native compatibility need device coverage beyond the API 36.1 emulator.
