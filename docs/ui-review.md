> Current native status: implementation resumed on 2026-10-07. See [development progress](native-development-2026-10-07.md). The review below is a historical design record.

# Toolkit UI review

**Superseded for current decisions:** [October 7 theme and interaction review](design-review-2026-10-07.md), following the [whole-app review](design-review-2026-10.md). Launcher A is selected; all three themes are approved with Platinum as the default. The user reopened the design after native testing on 2026-10-06. The A v5 approval and screenshots below are historical.

Updated 2026-10-05. Refined A (version 5) is approved: the user confirmed "revised A feel great". The approved baseline has light and matching dark themes, neutral surfaces, restrained blue, sharper typography, prominent Recent and separate Favorites. The first native Android foundation and Images-to-PDF slice now use this baseline; see [build/runtime notes](../BUILDING.md). The behavior described below is specific to the historical web prototype.

[Open refined A](https://p.superdesign.dev/draft/41479730-3489-47d2-b335-cf4b829f3717) · [Open the design canvas](https://superdesign.dev/teams/17c539cf-4d05-433f-a594-93ba26fecbc0/projects/67a07e9d-597f-4bbc-8416-567520d5b34b).

Refined with the Superdesign skill and Gemini 3.1 Pro, then corrected directly for sizing, accessibility and prototype interactions. Version 5 retains Tools / Downloads / Files navigation and app-bar Settings. The hosted HTML was fetched and compared with the browser-checked local source.

## Current preview

| View | Snapshot |
|---|---|
| Light dashboard | [Image](prototypes/a-dashboard.png) |
| Dark dashboard | [Image](prototypes/a-dashboard-dark.png) |
| Workflow editor, after sample edits | [Image](prototypes/a-workflow-dark.png) |
| Interactive source | [HTML](prototypes/a-dashboard.html) |

Recent contains four compact tool cards. Favorites is separate, with demo pin controls. Workflows opens an ordered step builder. Browse all tools shows selected document categories; Downloads and Files explain their selected scope in preview dialogs.

## What works in the prototype

- Light/dark toggle and Light/System/Dark settings; overlays follow the theme.
- Search filters sample Recent and Favorites entries, including an empty state.
- Favorite controls change pin state in place so both states can be reviewed; sample rows remain visible.
- Workflow options, compatible additions, removal and move up/down controls. Images to PDF remains first; Lock PDF stays last. The demo allows one instance of each offered operation.
- Save demo keeps a template in memory for the current page session, without persistence after reload. Preview run describes a simulated sequence; it never converts files or reports a real output.
- Sheets and dialogs close using close buttons or Escape. Sheets contain keyboard focus and return it to the opener.
- Tool, folder and navigation controls explain their preview behavior instead of silently doing nothing.

All data is fictional. No real files, passwords, Telegram credentials, account sessions or downloads are processed. Web previews load hosted styling, icons and fonts; they are not the offline Android implementation. The supplied Telegram configuration screenshot was not embedded in the prototype or design context.

## Agreed scope and next design review

The [feature catalogue](feature-catalogue.md) records every accepted and excluded ID. PDF/A is excluded. Workflows (P17) are included as a proposed linear sequence of compatible document operations; no branching, scripts or scheduling in the first version.

The visual-refinement review is complete. Use version 5 as the Android design baseline; do not reopen the A/B/C or theme selection. Build connected flows using this visual system: file selection, tool options, destination, progress/cancel/failure, download setup and queue, Telegram connection/chat/file selection, and file viewing. Large text, empty/history states, revoked folder access and insufficient storage still need coverage during implementation. Prototype approval does not imply working processing engines or production validation.

## Validation boundary

The local HTML passed Chrome checks at 320, 375 and 390px widths in both themes: no document-level horizontal overflow or main-content overlap with navigation, expected theme backgrounds and no page JavaScript errors. Search, empty state, favorite toggles, settings themes, Escape/focus return, workflow add/remove/reorder, session-only save, simulated preview and preview navigation dialogs were exercised. Workflow controls passed 48px target and focus-containment checks at 320 × 568. Light, dark and workflow screenshots were visually inspected.

Results: [refinement browser checks](prototypes/a-refinement-browser-check.json). These browser checks do not establish full accessibility compliance, Android behavior, engine correctness, background downloads, performance, or privacy enforcement. Android validation is recorded separately with the implementation.

## Original exploration

Original A is preserved as [HTML](prototypes/a-dashboard-original.html) and [image](prototypes/a-dashboard-original.png). Unselected alternatives: [B — Task launcher](https://p.superdesign.dev/draft/1332cdf7-a844-42bc-8a2c-e00a7cdf1f26) and [C — File workspace](https://p.superdesign.dev/draft/d15bcb30-c7a2-4c76-90b9-fa8ba5180b5e). Their paper/forest styling and old sample tools are historical exploration, not the current feature scope. [Original v3 browser checks](prototypes/browser-check.json) apply only to those earlier drafts.
