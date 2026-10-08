# Bundled assets and processing libraries

- Inter variable font: Google Fonts, https://github.com/google/fonts/tree/main/ofl/inter. License: SIL Open Font License 1.1, copied in Inter-OFL.txt. Bundled font SHA-256: 29160A80FF49DDCAB2C97711247E08B1FAB27A484A329CE8B813D820DC559031.
- Current icons: Phosphor duotone, pinned source and license in Phosphor-SOURCE.txt and Phosphor-LICENSE.txt. Lucide-LICENSE.txt records the historical foundation icons.
- PDFBox Android: license and notices in PdfBox-LICENSE.txt and PdfBox-NOTICE.txt.
- Apache POI and Android compatibility adapters: `office-runtime/build.gradle.kts`, poi-on-android-SOURCE.json and Office-runtime-NOTICES.txt. The build regenerates dependency notices from the pinned runtime artifacts and includes them in the app's assets. Color, Dimension and Dimension2D are Toolkit value adapters; other compatibility sources retain their upstream provenance.

Notices are bundled in the APK and accessible through Settings → Open-source licenses. Assets and document libraries are local resources; the installed app does not download them at runtime. This is a development dependency inventory, not a completed production distribution audit.
