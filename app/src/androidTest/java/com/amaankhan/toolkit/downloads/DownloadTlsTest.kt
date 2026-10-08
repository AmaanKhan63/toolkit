package com.amaankhan.toolkit.downloads

import androidx.test.core.app.ApplicationProvider
import com.amaankhan.toolkit.ToolkitApplication
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.*
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralNames
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.math.BigInteger
import java.net.InetAddress
import java.net.URL
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.CertificateFactory
import java.util.Date
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.*
import kotlin.concurrent.thread

/** Unknown test CA: do not modify the device or app trust store to run this. */
class DownloadTlsTest {
    private val app = ApplicationProvider.getApplicationContext<ToolkitApplication>()

    @Test fun nativeDownloadersAndDefaultHttpsRejectAnUntrustedCertificate() = runBlocking {
        UntrustedServer().use { server ->
            withContext(Dispatchers.IO) {
                try {
                    (URL(server.url).openConnection() as HttpsURLConnection).apply { connectTimeout = 5000; readTimeout = 5000 }.let { connection ->
                        try { connection.inputStream.use { it.read() }; fail("Platform HTTPS accepted the unknown test CA") }
                        finally { connection.disconnect() }
                    }
                } catch (_: SSLHandshakeException) { }

                val directory = File(app.cacheDir, "tls-fixture-${System.nanoTime()}").apply { mkdirs() }
                try {
                    AriaSession(app, directory).use { engine ->
                        engine.start()
                        val id = engine.rpc("addUri", JSONArray().put(server.url), JSONObject().put("max-tries", "1").put("connect-timeout", "5").put("timeout", "5")) as String
                        val failure = withTimeout(20000) {
                            while (true) {
                                val state = engine.rpc("tellStatus", id) as JSONObject
                                check(state.getString("status") != "complete") { "aria2 accepted the unknown test CA" }
                                if (state.getString("status") == "error") return@withTimeout state
                                delay(100)
                            }
                            error("unreachable")
                        }
                        val message = failure.optString("errorMessage")
                        assertTrue(message, message.contains("certificate", true) || message.contains("SSL", true))
                    }
                    val lines = mutableListOf<String>()
                    val request = YoutubeDLRequest(server.url).apply {
                        addOption("--skip-download"); addOption("--dump-single-json")
                        addOption("--socket-timeout", "5"); addOption("--retries", "0"); addOption("--extractor-retries", "0")
                    }
                    try {
                        withTimeout(30000) { runMedia(app, request, capture = true) { lines += it } }
                        fail("yt-dlp accepted the unknown test CA")
                    } catch (e: CancellationException) { throw e }
                    catch (_: IllegalStateException) {
                        assertTrue("Expected certificate rejection, got ${lines.joinToString("\n")}", lines.any { it.contains("certificate", true) })
                    }
                    assertTrue("Clients did not reach the TLS fixture", server.connections.get() >= 3)
                } finally { directory.deleteRecursively() }
            }
        }
    }
}

private class UntrustedServer : AutoCloseable {
    private val socket: SSLServerSocket
    val connections = AtomicInteger()
    val url get() = "https://127.0.0.1:${socket.localPort}/fixture.txt"
    init {
        val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val subject = X500Name("CN=Toolkit synthetic TLS fixture")
        val now = System.currentTimeMillis()
        val certificate = JcaX509v3CertificateBuilder(subject, BigInteger(80, SecureRandom()), Date(now - 60000), Date(now + 3600000), subject, pair.public)
            .addExtension(Extension.subjectAlternativeName, false, GeneralNames(GeneralName(GeneralName.iPAddress, "127.0.0.1")))
            .build(JcaContentSignerBuilder("SHA256withRSA").build(pair.private))
        val x509 = CertificateFactory.getInstance("X.509").generateCertificate(certificate.encoded.inputStream())
        val password = "synthetic-test-only".toCharArray()
        val keys = KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null); setKeyEntry("fixture", pair.private, password, arrayOf(x509)) }
        val managers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(keys, password) }
        socket = SSLContext.getInstance("TLS").apply { init(managers.keyManagers, null, SecureRandom()) }.serverSocketFactory.createServerSocket(0, 10, InetAddress.getByName("127.0.0.1")) as SSLServerSocket
        thread(isDaemon = true, name = "synthetic-tls") {
            while (!socket.isClosed) {
                try {
                    (socket.accept() as SSLSocket).use { client ->
                        connections.incrementAndGet(); client.soTimeout = 3000
                        runCatching {
                            client.startHandshake()
                            client.inputStream.bufferedReader().readLine()
                            client.outputStream.write("HTTP/1.1 200 OK\r\nContent-Length: 7\r\nConnection: close\r\n\r\nfixture".toByteArray())
                        }
                    }
                } catch (_: java.io.IOException) { }
            }
        }
    }
    override fun close() { socket.close() }
}
