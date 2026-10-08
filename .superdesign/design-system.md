# Toolkit: whole-app A revision under review

## October 7 feedback override (newest)

Launcher A Folded T is selected. Use the exact selected mark at https://vgbujcuwptvheqijyjbe.supabase.co/storage/v1/object/public/hmac-uploads/projects/67a07e9d-597f-4bbc-8416-567520d5b34b/brand-assets/toolkit-folded-t/launcher-a.svg in all logo positions. Its shape is not up for exploration. The user selected all three palettes: Graphite / Platinum (default), Obsidian / Champagne and Midnight / Jade. Use the explicit tokens in the latest prototype's `palettes` object. Offer all three in Settings → Themes, with independent System/Light/Dark appearance and locally persisted preferences. Default a missing palette to Platinum and retain an existing appearance choice. These tokens override the older blue-only rule below. Retain Inter and Phosphor, with quiet grouped surfaces, crisp hierarchy and at least 48px touch targets.

Functional corrections are mandatory in every theme: compact image summary + separate full-page organizer; top-layer choice sheets for every selector; numeric px margins, linked or per-side, fixed 96px/inch conversion; selected-page range + grid with validation and real subset in the simulated preview; large navigable PDF-to-image output; visible current/new/confirm password fields with mismatch and wrong-current-password states; correct XLSX input/output for Excel; workflows based on compatible input/output types, including encrypted Excel -> unlock Excel -> Excel to PDF -> lock PDF with run-time passwords; Themes and a local on-device User Profile in Settings. Preserve all corrected JavaScript interactions. Passwords must never enter preferences, profile, saved templates, URLs, logs or network calls.

Current review: docs/design-review-2026-10-07.md. Source draft v9 and three theme branches share the corrected interactions. Launcher A and the three themes are approved; Platinum is the default. Theme approval does not record approval of the remaining interactions. Local prototype exports in docs/prototypes/review-2026-10-07/ are the current source context.

Historical baseline, 2026-10-06 (color and launcher rules below are superseded by the October 7 override): Android only. A v5 was approved, but feedback on the native app reopened the whole-app design. The user dislikes Recent/Favorites at the top and the ungrouped catalogue. The category structure remains proposed. See docs/design-review-2026-10.md for all-screen findings and selected-tool mapping.

## Product and selected scope

Personal document tools, downloads, and file viewing. Local document processing; downloads contact source services. No mandatory Toolkit account/backend. Prototypes use invented data and simulated actions, not real file access or processing. No supplied brand image assets; Toolkit text is the app title.

Keep: images to PDF, merge, PDF lock/unlock, Excel protection, compression, Office/PDF conversions, PDF images and HTML to PDF. Additional PDF tools: split/extract, organize, crop, watermark/page numbers, camera scan, drawn signature, PDF to TXT/Markdown without OCR, Markdown/TXT to PDF, document metadata and saved workflows. PDF/A is excluded by the user and must not appear.

Downloads: Video link, Direct file, Telegram, Torrent/magnet. Core queue, progress, pause/resume where available, cancel, destination and quality selection. Exclude scheduling, Wi-Fi-only controls, configurable retry policies, URL-list import, site-cookie login, subtitles, thumbnails and chapter selection.

Viewers: PDF, Markdown, Word, Excel, presentations, TXT/CSV/TSV/JSON/XML/logs, EPUB and local HTML. No standalone image/audio/video/archive/OpenDocument/RTF viewers. Document image previews remain allowed.

Exclude OCR, annotations, PDF forms, certificate signatures, redaction, comparison/repair, AI summary/translation and ALL extra image/media/archive/QR/general utility tool families. Draw/place signature remains selected. Do not confuse document converters with rejected standalone utilities.

## Art direction

Refined utility interface: disciplined hierarchy, crisp type, aligned icons, comfortable whitespace, subtle elevation and a small blue accent. A real mobile app, never a marketing page or desktop dashboard. No forest/cream/candy palette, decorative charts, gradients, glass, giant cards, unnecessary all-caps, fake security guarantees, avatars, reward counters or artwork. Quality comes from typography, density, alignment and states.

## Semantic tokens

| Token | Light | Dark |
|---|---|---|
| canvas | #F5F6F8 | #0F1115 |
| surface | #FFFFFF | #171A20 |
| elevated | #FFFFFF | #1F242C |
| text | #141922 | #F2F4F8 |
| secondary | #5F6978 | #ADB6C5 |
| border | #E2E6EE | #353D4A |
| accent / links | #245BDD | #8CAEFF |
| accent tint | #EAF0FF | #202E4C |
| action fill | #245BDD | #245BDD |
| action text | #FFFFFF | #FFFFFF |
| success text | #217047 | #88D4AC |
| error text | #B42332 | #FF9EA8 |

Use CSS custom properties on the prototype root and a working light/dark theme control. Theme overlays, inputs, text, icons, borders and navigation consistently. A border alone must not be the sole indication of interaction. No white text on pale-blue fills.

- Typography: Inter with system-ui fallback throughout. No Space Grotesk/serif. Titles 28/34 at 650-700; section headings 18/24 at 600; tool labels 15-16/22 at 600; body 16/24; metadata 12-13/18. Tab labels at least 12px. Monospace only for technical values.
- Spacing: 4/8/12/16/20/24/32. Side gutters 20px at 390px, at least 16px at 320px. Compact 56-64px app bar plus safe area; 48-52px search. No giant greeting.
- Cards 16-20px radius; buttons/inputs 12-14px; small tags may be pills. Very subtle light shadow (0 3px 14px rgba(20,25,34,.04)); dark separation through layers/borders.
- Proposed Phosphor family: foreground vectors without decorative fill plates; selected navigation uses its container indicator. Consistent 22-24px icons; 28px category icons in 48px tinted wells. No Lucide. No emoji. Interactive targets at least 48x48 CSS px; icons may be smaller inside. Decorative icons are aria-hidden.
- Semantic buttons, visible labels and keyboard focus; allow pinch zoom. Modal focus contained, Escape closes, focus returns to opener. Reduced motion supported. Content scrolls without controls obscuring it.
- Do not override Tailwind numeric spacing: old drafts accidentally made h-12 equal 12px. Use explicit sizes or semantic CSS variables.

## Dashboard composition

Keep Tools / Downloads / Files bottom navigation and Settings in the app bar. Settings is a full screen. Theme comparison belongs in Settings/review controls. No fourth workflow tab.

1. Compact Toolkit app bar and search. Grouped surfaces, fewer borders, clear hierarchy.
2. Six categories in two columns: Create PDF, Convert, Organize pages, Protect, Optimize, Workflows. Meaningful transparent icons and short descriptions. Open category lists; never dump the catalogue on home.
3. No Recent or Favorites home sections. Files has History. Optional Saved tools is secondary navigation from Tools; pin in tool overflow, never stars on each row.
4. Category screens use concise grouped rows. Search returns one categorized result list with format aliases.
5. Useful first-use state without fabricated history/queues. Fictional populated states only in labeled review scenarios. No Coming soon cards in this future-state prototype. Interim native builds should show working tools by default.

Use a discreet Prototype / sample data label. No activity banners. Natural scrolling and useful first viewport.

## Workflow concept

Ordered vertical step list, not a branching graph. Show a mock sheet illustrating Images to PDF -> Watermark -> Compress -> Lock, compatible Add step choices, options, removal and accessible move up/down controls. Lock is last. Save demo stores mock template state; Preview run is explicitly simulated. No real passwords, file uploads, OCR, AI, scripting, loops, schedules or download-engine steps.

## Prototype behavior and data boundary

One coherent whole-app review prototype with working light/dark switch and full-screen Settings. Navigable fictional states for Tools/category/search, Images-to-PDF setup, actual-output preview concept, saved result, download sources/setup/queue, Telegram onboarding/browser, torrent selection, Files/viewers and workflow editor. Review-only screen index may expose supporting states. All buttons produce meaningful preview feedback. No alert replacing a major screen.

Configure -> preview staged result -> Save as -> saved. Preview contains readable fictional document pages with page/zoom controls, not only input thumbnails. A review-only Identity screen compares three new inline-vector launcher concepts A Folded T, B Toolcase, C Connected modules: large/small, circle/squircle and monochrome examples. The user explicitly requests replacing the existing logo; no logo is currently approved. These candidates are not final brand assets.

All documents/session states are fictional. No API IDs/hashes/tokens/phone numbers/passwords/account screenshots/real filesystem paths/credential fields. Telegram shows a connection explanation only. No uploads or network processing from handlers. Hosted fonts/style/icon assets are browser-preview infrastructure, not the final Android app.



## Native implementation authorization ? 2026-10-07

The user directed implementation to proceed ("Then what are you waiting for?"). No further design approval gate applies. Use the October 7 reviewed direction and selected palettes as the native baseline. Native progress and validation are tracked in `docs/native-development-2026-10-07.md`; the hosted prototypes remain design artifacts.
