# Toolkit: exploratory mobile design system

Status: design exploration, not an approved application specification.

## Purpose

A personal mobile toolbox for processing documents on the device, downloading selected files from remote services, and viewing files. No account required for local tools. Telegram connection is optional. Android only for now, confirmed by the user.

The user explicitly requests multiple prototypes before UI implementation. Produce a real mobile app surface, never a marketing website, landing page, or desktop dashboard. All file names, progress, and history are invented demo data. No real credentials or user documents.

## Style source and adaptation

Primary inspiration: Superdesign library prompt `mosaic-grid-architecture-style` (Technical Minimalist). Adopt its paper/forest palette, precise hairlines, flat surfaces, and clear modular organization. Adapt it for an accessible touch interface: use larger body labels and softer mobile corners rather than tiny uppercase text and sharp desktop panels. No background mosaic decoration, hero imagery, stock photos, gradients, glass effects, decorative charts, or marketing statistics.

## Tokens

- Light: canvas #F7F7F5; surface #FFFFFF; text #18251E; secondary text #58635C; border #D9DFD9; primary #1A3C2B; on-primary #FFFFFF; primary-container #E4EEE6.
- Dark: canvas #111914; surface #1B261F; text #EFF4EF; secondary text #B6C4B9; border #35483A; primary #B6E8C3; on-primary #173020; primary-container #283F30.
- Small category fills only: documents #E4EEE6; downloads #F7EDC8; files #F9E2D8. Use dark text #18251E on these fills. Category colors supplement text and icons.
- Typography: Space Grotesk for titles only, system sans-serif for body/buttons, system monospace for file extensions/sizes only. Title 28/34, section 18/24, body 16/24, metadata 13/18. No tiny labels. No uppercase paragraphs.
- Spacing: 4, 8, 12, 16, 20, 24, 32. Horizontal mobile padding 20. Card radius 16, button radius 12, input radius 12, pill radius 999.
- Touch targets at least 48x48. One consistent outlined icon family. Visible focus indicators, semantic labels. No emoji as icons. Text must wrap at narrow widths. No hover-only affordances.
- Motion: subtle 150-220ms transitions; respect reduced motion. No loading animation that implies a real download.

## Shared information architecture

Bottom navigation: Tools, Downloads, Files. Settings is an accessible gear button in the top app bar. Preserve this shell across directions. Keep an active job visible without interrupting other tasks. Clear titles and back navigation for each tool.

Tools: search by task, favorites, groups (PDF & documents; images; media; utilities), recent tools. Useful starter shortcuts are Images to PDF, Merge PDF, Compress PDF, Lock / unlock. One understated local-processing label on relevant tools; never claim the entire app is offline because downloading requires the network.

Downloads: add link, select source (Video link, Direct file, Telegram); queue with pause, resume, cancel and error retry; per-job destination/format/quality. Put engine names in advanced settings, not the main action labels. Show sample jobs as sample data; no maximum-speed or security guarantees.

Files: recent outputs and user-selected folders, file-type filters, viewer, multi-select actions, open/share/convert. Do not imply that all phone files are automatically accessible.

Settings: system/light/dark theme, default output folder, Wi-Fi-only downloads, concurrency controls, history retention, temporary-file cleanup, Telegram connection/sign out, optional biometric lock, export/import settings, about and licenses. Do not put sign-in on the home page.

## Interaction model

Document tool: select files -> reorder / options -> choose output -> run -> preview / share / another tool. Preserve original files by default. PDF compression shows quality tradeoff and measured before/after sizes only after a real operation; any figures in this prototype are explicitly examples.

Downloader: paste link -> inspect formats -> select quality/container and location -> queue -> open result. Telegram: connect -> select a chat -> filter files -> select items -> queue.

Provide lightweight click-through navigation and sheets where supported, with dummy data. Never request or accept real passwords, real Telegram tokens, or file uploads in these design drafts. Show a discreet prototype/demo indication in a footer or information sheet, not an intrusive banner.

## Three directions to compare

A. Tool dashboard: balanced home with search, 2x2 favorite tools, clear category rows, and a compact ongoing-task strip. Recommended for mixed use.
B. Task launcher: same palette, type and shell; a quieter action-first layout with three large stacked actions (Convert a file, Download a link, Open a file), followed by a compact recent list. More whitespace, fewer decisions.
C. File workspace: same palette, type and shell; file list with filters and multi-select contextual actions leads the layout; tools are offered after file selection. Best for repetitive document work.

These are alternative entry layouts, not final feature commitments. Every direction must retain discoverable Tools, Downloads, Files, and Settings.
