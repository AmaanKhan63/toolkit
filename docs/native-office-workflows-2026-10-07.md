# Native Office and workflows — October 7, 2026

Current native version: 0.3.0 (3). Development is authorized; no further design approval is required. This update extends the [0.2.0 PDF foundation](native-development-2026-10-07.md). It does not complete the entire selected feature catalogue.

## Working scope

| Feature | Native behavior | Limits still to address |
|---|---|---|
| D04 Excel protection | XLSX file-open encryption with AES-256 agile encryption; current-password decryption; add/remove all worksheet and workbook-structure editing protection; actual workbook table preview before Save as | Legacy XLS, macros and signed workbooks are rejected. Editing protection is not file encryption. Editing changes on an encrypted input produce an unencrypted XLSX, explicitly explained in the UI. |
| D07 Excel to PDF | Local table layout; visible or selected sheets; cached formula values; wrapped cell text; pagination of wide sheets and long cells; selected output-page ranges; actual PDF preview | This is not Excel print-layout reproduction. Charts, drawings, merged-cell styling and Excel print settings are not reproduced. Word/PowerPoint rendering remains unfinished. |
| P17 Workflows | Start with images, Excel or PDF; choose compatible ordered steps; add/remove/reorder; validate transitions; per-step options; saved templates; runtime password fields; progress/cancel; actual final preview and Save as | Uses the engines currently implemented. Other catalogue tools become available as their engines are completed. No branching, scripting or process-death resumption. Multiple inputs are supported by the Images to PDF and Merge starting steps; other starts take one file. |

The requested **unlock Excel → Excel to PDF → lock PDF** chain is available as a starting example and runs real operations. Workflows can also start with Images to PDF and end with text extraction or a batch of rendered PNG/JPEG pages saved as ZIP. Save workflow remains in the fixed action area.

Templates contain only a name, step identifiers and explicitly allowed options. Passwords and source paths are never serialized. Templates are written atomically to app-private `files/workflows.json`; corrupt or unsupported template storage is reported and preserved. Existing Room history and DataStore settings are retained. Exported XLSX history offers external Open/Share; a complete in-app spreadsheet viewer remains unfinished.

File encryption/decryption wraps and restores the original XLSX package bytes. Editing protection changes only workbook/worksheet XML; other ZIP entry contents are retained. Inputs are never overwritten. DTD-bearing XML is rejected before editing. Preview rendering opens the package read-only and never evaluates external links. Formulas use saved cached results, so stale source caches remain stale.

Processing limits: 64 MB input, 128 MB expanded package, 32 MB per expanded entry, up to 100 sheets, 20,000 rows and 256 columns per sheet, and 2,000 generated pages. These bounds also apply to workbook previews. Workflows allow 20 steps and 100 saved templates. Jobs remain tied to the app process; intermediate results are deleted after the next step succeeds, and errors/cancellation clean staged output.

## Verification

The final debug and instrumentation builds passed, **12 JVM tests passed**, and **all 24 Android tests passed in one final run** on the isolated ToolkitTest API 36.1 x86_64 emulator. Lint reports **0 errors / 34 warnings**. [Recorded results and APK hash](validation/2026-10-07-office/checks.json), [build log](validation/2026-10-07-office/build.log) and [native suite](validation/2026-10-07-office/instrumentation.log) preserve the evidence.

The complete native UI path was also exercised with a synthetic encrypted workbook: document picker, current/new/confirm password fields, run, actual PDF preview, Save result, and independent opening of the exported file. PyMuPDF rejected a wrong password, accepted the chosen password and found the expected cell text/value after normalizing font ligatures. The original encrypted input was byte-identical afterward. Saved template JSON contained neither passwords nor input URIs; the template survived force-stop/relaunch. A seeded stale private Office temporary file was removed on restart. See [UI export verification](validation/2026-10-07-office/ui-workflow-verification.json), [editor](validation/2026-10-07-office/native-workflow-excel.png), [password form](validation/2026-10-07-office/native-workflow-passwords.png), [actual preview](validation/2026-10-07-office/native-workflow-preview.png) and [saved result](validation/2026-10-07-office/native-workflow-saved.png).

Earlier failed runs exposed Android DOM feature incompatibility, a static node-list assumption, clipped cell text and a missing PDF resource initialization. These were corrected. The UI theme test was also made repeatable when Dark was already selected; the full final run passed. No failing checks were suppressed. The inline-API lint warning concerns the simple line-break constant: both the old Layout and new LineBreaker fields have the same inlined integer value 0, verified in the installed SDK. Minimum-API/device coverage is still outstanding. Other warnings concern dependency/target updates, storage checks, Kotlin helpers and transitive library code as recorded in the earlier PDF report.

Office checks cover exact-byte encryption/decryption, wrong passwords, editing protection validated through Apache POI, unchanged non-protection ZIP content, selected sheets/pages, long cells continuing across pages, DTD rejection, cross-library encrypted inputs, the Excel-to-protected-PDF chain, cancellation after an intermediate result, and an image workflow with persisted layout options and selected-page PNG output.

An independent Python check uses `msoffcrypto` to verify the native encrypted file's password and integrity data, compares recovered bytes against the original, and opens the result using `openpyxl`. All fixtures are synthetic. [Interoperability evidence](validation/2026-10-07-office/interop.json) can be reproduced after instrumentation with `python scripts/verify-office-interop.py`. This is not a claim of a manual Microsoft Excel compatibility test or physical-phone coverage.

## Build and provenance

`office-runtime` builds a shaded Apache POI 5.5.1 runtime from pinned Maven dependencies. Android compatibility adapters are based on [poi-on-android](https://github.com/centic9/poi-on-android/tree/8e3cf66813feca0eef8283f0865145b4a80c02ee); Color and size value adapters are Toolkit implementations. Source provenance is in `third-party/poi-on-android-SOURCE.json`. Dependency notices are generated from resolved artifacts and bundled in Settings → Open-source licenses.

The app still has no Internet or broad storage permission. Document processing does not upload files. Download engines, full Office rendering and remaining selected PDF/viewer tools are still outstanding; see the [selected catalogue](feature-catalogue.md).
