# Download certificate validation review

The five `TrustAllX509TrustManager` lint warnings were traced into bundled Bouncy Castle EST helpers, rather than Toolkit's download-client configuration. They remain visible in lint; no suppression or global trust-store change was made.

## Source and bytecode findings

- PDFBox brings `bcpkix-jdk15to18:1.72`; the Office runtime also bundles Bouncy Castle 1.79. `org.bouncycastle.est.jcajce.JcaJceUtils$1` contains empty trust checks. The related `$2` helper has an empty client check but validates server certificate paths and usage. These are EST helper APIs, not automatically installed HTTPS defaults.
- No first-party use of EST, `JcaJceUtils`, a custom trust manager, a permissive hostname verifier or a replacement default HTTPS socket factory was found in app/Office source.
- `AriaSession` explicitly passes `--check-certificate=true` and the bundled CA file. Media processes set Python's `SSL_CERT_FILE`; Toolkit does not pass yt-dlp's certificate-bypass option.
- The pinned Android yt-dlp updater's inspected bytecode uses URL connections and Commons IO `copyURLToFile`, without a custom trust manager. The bytecode excerpt is archived with this review.

## Native negative test

`DownloadTlsTest.nativeDownloadersAndDefaultHttpsRejectAnUntrustedCertificate` runs an emulator-loopback HTTPS server with a fresh synthetic self-signed certificate, valid for the loopback address. It does not install that certificate as a trusted root. The test passed on ToolkitTest, API 36.1 x86_64, on 2026-10-08:

1. Android's default `HttpsURLConnection` rejected the handshake.
2. The actual bundled aria2 engine reported a certificate/SSL failure.
3. The actual bundled Python/yt-dlp engine reported a certificate rejection.
4. The server observed connections from the clients, ruling out a test that merely failed to reach the server.

The local HTML viewer separately passed native rendered-text checks with JavaScript, network, file and content access disabled. Its previous failing test tried to inspect the DOM with JavaScript while intentionally disabling JavaScript; the replacement uses WebView's native find API.

Evidence: [focused native result](validation/2026-10-08-toolkit/focused-native.txt), [updater bytecode](validation/2026-10-08-toolkit/updater-bytecode.txt). These findings resolve the suspected active download TLS bypass for the inspected configuration. They do not constitute a general dependency vulnerability audit, Telegram account validation, or certification of every Android/WebView version.
