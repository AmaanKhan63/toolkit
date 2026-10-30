# Media failure diagnostics

The user requested screenshot-ready errors or an exportable log for the next failure, and asked to stop deep live YouTube investigation. The existing push request remains active; UI approval remains pending.

- Capture a bounded, sanitized engine trace in memory. Include stages, selected stream IDs, retry/error lines, last progress and process exit code. Exclude raw request arguments, cookies, credentials, signed URLs and local filenames.
- On media download, inspection or save failure, persist a small plain-text report in private non-backed-up app storage. Include app/engine/Android/ABI versions, connectivity type, free storage, requested settings, timing and a report ID. Keep at most 20 reports across retries/restarts.
- Display a safe error code and report ID in the error so screenshots are useful. Add Save error report to failed media transfers and media inspection errors, using Android's create-document picker. The user chooses where to save and what to send; nothing uploads automatically.
- Verify redaction, bounded storage/retention, actual engine failure capture, report export and UI accessibility. Reuse local synthetic fixtures; no further live YouTube tests.
- Rebuild the phone APK and refresh relevant screenshots, then commit and push the repair branch. Document limitations: a report materially improves diagnosis but cannot guarantee a complete root cause for every external or OS failure.

Validation: 30 JVM tests pass. The final focused native run reports 13 checks (ten executed, three live cases gated), all passing; the compact transfer/report flow also passes. ARM64 APK signature matches 0.4.1. No further live YouTube testing was run. See the repair report and repository screenshot gallery.
