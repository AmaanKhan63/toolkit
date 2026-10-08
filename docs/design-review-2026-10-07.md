# Toolkit theme and interaction review

Date: 2026-10-07. Launcher A (Folded T) is selected. The user approved all three themes with Platinum as the default: "All 3, platinum default". Implementation has now resumed at the user?s direction (?Then what are you waiting for??). No further design approval is blocking work. See [native implementation progress](native-development-2026-10-07.md).

## Compare the themes

Keep Platinum, Champagne and Jade in Settings → Themes. Platinum is the default palette for new users and when no valid palette preference is stored. Each palette supports light, dark and system appearance; palette selection is independent of appearance mode. Persist both choices locally and preserve an existing user's appearance preference. All three prototypes contain the same revised flows; their individual starting palettes remain useful for comparison.

| Option | Direction | Interactive prototype |
|---|---|---|
| 1. Graphite / Platinum | Neutral graphite, porcelain and silver | [Open Platinum](https://p.superdesign.dev/draft/9b07a303-234d-485d-af29-c3ef8d97420b) |
| 2. Obsidian / Champagne | Warm charcoal, ivory and restrained gold | [Open Champagne](https://p.superdesign.dev/draft/982a4794-fadb-4c09-b012-b3ff7cf90695) |
| 3. Midnight / Jade | Mineral green and soft jade | [Open Jade](https://p.superdesign.dev/draft/d1968392-721a-4143-b8c7-0196e53744e4) |

[Side-by-side comparison](prototypes/review-2026-10-07/theme-comparison.png) · [Interactive local comparison](prototypes/review-2026-10-07/theme-comparison.html) · [Superdesign canvas](https://superdesign.dev/teams/17c539cf-4d05-433f-a594-93ba26fecbc0/projects/67a07e9d-597f-4bbc-8416-567520d5b34b).

The original [whole-app draft](https://p.superdesign.dev/draft/41479730-3489-47d2-b335-cf4b829f3717) also contains these corrections and starts with the selected default, Platinum. Use **Screens** for review shortcuts and **Themes** to compare appearance. Both controls belong to the prototype, not the production app bar. Published snapshots retain their comparison copy; this recorded user decision is authoritative for implementation.

The selected launcher artwork is preserved as [launcher-a.svg](prototypes/review-2026-10-07/launcher-a.svg). The interface retains the consistent Phosphor icon family. Native adaptive and monochrome launcher resources are now implemented in the subsequent native pass.

## Feedback addressed in the prototype

| Feedback | Revised interaction |
|---|---|
| Long Images-to-PDF list | A bounded thumbnail summary and image count keep layout options close. **Manage** opens a separate grid with remove and move-to-position controls. **Done** returns to layout. The review shortcut loads 20 images. |
| Chopped option dropdowns | Shared full-width choice sheets replace browser-native dropdown presentation throughout the prototype, including workflows. They show the current value, wrap long labels, support keyboard navigation and contain focus. |
| Exact margins | Numeric px values, linked for all sides or separate top/right/bottom/left, with validation and a corresponding page inset. The prototype accepts 0–160 px per side. |
| Selected pages ignored | Choosing **Selected pages** reveals a range field and individual page checkboxes. Invalid or empty ranges block preview. The preview uses the selected source pages and retains the choice when returning to edit. |
| PDF-to-images preview too small | One large image with its filename, source-page number, previous/next and zoom. Navigation sits above the image so it is immediately reachable. |
| Missing PDF/Excel password entry | Protect shows new password and confirmation; unlock shows current password. Show/hide and validation are visible. Excel uses an XLSX input and spreadsheet preview. File-open encryption and editing protection remain separate choices. |
| Forced workflow start | Input type is selectable, every first step is removable, and additions are filtered by the preceding output. The initial example is **Unlock Excel → Excel to PDF → Lock PDF**. Invalid order produces a specific explanation. Passwords are requested at run time and excluded from templates. |
| Missing Settings destinations | **Themes** and **User profile** are first-class Settings destinations. Profile has display name and workspace label; the proposed native profile is local to the device and needs no sign-in. |

Numeric px margins use a fixed reference of 96 px per inch, rather than physical display pixels. Conversion to Android PDF geometry is `points = px × 72 / 96`. This keeps margins stable across phones. See [CSS absolute lengths](https://www.w3.org/TR/css-values-4/#absolute-lengths) and [Android PDF page units](https://developer.android.com/reference/android/graphics/pdf/PdfDocument.PageInfo). Production validation must also ensure opposing margins leave usable page space for the chosen page size.

The six-page Word example demonstrates page selection after layout. Real Word/Excel/presentation pagination depends on the selected document renderer; matching Microsoft output is not validated here.

## Review sequence

1. Open a theme and switch light/dark using **Themes**.
2. Open **Screens → 20-image layout**. Check Manage, page size, orientation and numeric margins.
3. Open **Select Word pages**, choose Selected pages, enter `2, 4-5`, and preview. The three resulting pages should show source pages 2, 4 and 5.
4. Open **Large image output** and inspect the preview navigation.
5. Open PDF or Excel protection. Use made-up passwords. Unlock's sample current password is `demo`.
6. Open **Excel → PDF workflow**, run the example, then remove steps or change the input type. Try an incompatible order to inspect the error.
7. Open Settings and inspect Themes and User profile.

## Validation and limits

[Browser checks](prototypes/review-2026-10-07/checks.json) passed for the 20-image organizer, choice sheets, margin validation, selected-page subsets, image navigation, both protection forms, the Excel workflow, invalid reordering, replacing the first step, profile and theme selection. A layout sweep covered nine screen families × three palettes × two appearance modes × 320/390 px widths, with no horizontal overflow or uncaught JavaScript errors. [Theme checks](prototypes/review-2026-10-07/theme-checks.json) verify each branch's default palette and shared interaction functions.

[Thirty text-token contrast checks](prototypes/review-2026-10-07/contrast.json) had a minimum ratio of 5.06:1 for the tested foreground/background pairs. This is not a complete accessibility audit. Final screenshots were inspected for the shared selector, margins, large preview and theme comparison.

All conversions, authentication and workflow execution are simulated. No real documents or credentials belong in this hosted review. Password fields clear on navigation or successful submission and are not copied into prototype state or templates. Profile edits last for the current page session. Hosted prototypes load remote fonts, styles, icons and the selected brand asset; that is separate from the native app's on-device processing model.

Theme branches were created with Superdesign and Gemini 3.1 Pro, then synchronized with the same directly authored interaction corrections and validated in Chromium. Local HTML exports are in [the review artifact directory](prototypes/review-2026-10-07/). No Android source changes, build, emulator run or physical-device validation were performed in this review.

The [October 6 review](design-review-2026-10.md) remains the screen inventory and selected-feature mapping. The corrections above supersede its affected controls and its still-open launcher choice. This review does not mark the planned Android tool engines as implemented.
