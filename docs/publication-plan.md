# Initial repository publication plan

Target: `main` at `AmaanKhan63/toolkit`. This document records the 25-group initial import; Git history is the source of truth for publication status.

The initial app is split by feature for review. These groups were prepared from the existing source; they do not claim that commits were made during development. Intermediate import commits are not individually build-validated. The completed app and latest fixes have the validation recorded in the linked reports.

| # | Commit |
|---|---|
| 01 | `docs: import product scope and approved UI prototypes` |
| 02 | `build: configure Android app Gradle wrapper and privacy boundaries` |
| 03 | `build: bundle Office compatibility runtime and third-party notices` |
| 04 | `feat: add shared controls palettes typography and launcher identity` |
| 05 | `feat: persist preferences profile history and generated files` |
| 06 | `feat: convert images to PDF with page layout and range selection` |
| 07 | `feat: add PDF merge protection compression and page operations` |
| 08 | `feat: add PDF annotations signatures metadata and extra tools` |
| 09 | `feat: parse local Office Markdown HTML and EPUB documents` |
| 10 | `feat: protect Excel workbooks and convert spreadsheets to PDF` |
| 11 | `feat: convert Word presentations and rich text to PDF` |
| 12 | `feat: export PDF content to Office formats` |
| 13 | `feat: capture and process document scans` |
| 14 | `feat: compose compatible document workflow templates` |
| 15 | `feat: persist download jobs and run foreground transfers` |
| 16 | `feat: integrate yt-dlp aria2 transfers and download security checks` |
| 17 | `feat: connect Telegram accounts and browse downloadable files` |
| 18 | `feat: expose download sources quality controls and queue management` |
| 19 | `feat: preview documents images and text before export` |
| 20 | `feat: integrate categorized tools navigation settings and profiles` |
| 21 | `test: retain emulator tooling and baseline validation evidence` |
| 22 | `fix: remove decorative icon backplates in dark themes` |
| 23 | `fix: dismiss input focus and keyboard after outside taps` |
| 24 | `fix: navigate preview pages with horizontal drag gestures` |
| 25 | `release: record 0.4.1 interaction regressions and verified artifacts` |

The three fixes use saved pre-feedback source for their baseline. Focus and swipe edits to the same viewer file are staged in sequence, so each fix has its own diff. Working files stay at their final version throughout publication.

The ignored `.local/publication-plan.json` locks every staged source and final working file by SHA-256. `.local/publish-toolkit.ps1` checks the branch, remote, empty history/index, paths and hashes before writing Git. It does not force-push or alter the sandbox. If interrupted, it refuses to restart over an existing history; inspect the partial history before continuing.

The initial publication attempt was blocked by read-only `.git` access and restricted networking. On 2026-10-08 the user enabled full access and requested publication; source validation and the connection to the empty GitHub repository then succeeded. Publication uses a normal push to `main`, without replacing any existing remote history.

Validation: [0.4.1 interaction fixes](interaction-fixes-2026-10-08.md) and [baseline engine/UI evidence](validation-2026-10-08.md).
