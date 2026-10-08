package com.amaankhan.toolkit.downloads

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Only the encrypted credential envelope is persisted; its key is non-exportable Android Keystore material. */
class TelegramSecrets(context: Context, namespace: String = "telegram-secrets") {
    private val prefs = context.getSharedPreferences(namespace, Context.MODE_PRIVATE)
    private val alias = "Toolkit.$namespace"
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun read(): JSONObject? {
        val value = prefs.getString("envelope", null) ?: return null
        val data = Base64.decode(value, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, data.copyOfRange(0, 12))) }
        return JSONObject(String(cipher.doFinal(data.copyOfRange(12, data.size)), Charsets.UTF_8))
    }
    fun save(value: JSONObject) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val encrypted = cipher.iv + cipher.doFinal(value.toString().toByteArray())
        check(prefs.edit().putString("envelope", Base64.encodeToString(encrypted, Base64.NO_WRAP)).commit()) { "Unable to save this connection." }
    }
    fun clear() { prefs.edit().clear().commit() }
}

data class TelegramStatus(val step: String = "credentials", val hint: String = "", val error: String = "")
data class TelegramChat(val id: Long, val title: String)
data class TelegramFile(val chat: Long, val message: Long, val id: Int, val name: String, val bytes: Long, val kind: String, val remote: String)
data class TelegramPage(val files: List<TelegramFile>, val next: Long)

class TelegramClient(private val context: Context, private val store: DownloadStore) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val secrets = TelegramSecrets(context)
    private val mutableStatus = MutableStateFlow(TelegramStatus())
    val status = mutableStatus.asStateFlow()
    @Volatile private var client: Client? = null
    @Volatile private var credentials: JSONObject? = null
    @Volatile private var loggingOut = false
    @Synchronized fun start() {
        if (client != null) return
        credentials = secrets.read()
        if (credentials == null) { mutableStatus.value = TelegramStatus(); return }
        System.loadLibrary("tdjni")
        Client.execute(TdApi.SetLogVerbosityLevel(0))
        Client.execute(TdApi.SetLogStream(TdApi.LogStreamEmpty()))
        mutableStatus.value = TelegramStatus("connecting", "Connecting to Telegram")
        client = Client.create({ update -> if (update is TdApi.UpdateAuthorizationState) scope.launch { authorize(update.authorizationState) } }, { mutableStatus.value = TelegramStatus("error", error = "Telegram connection failed. Reopen this screen to retry.") }, null)
    }
    suspend fun connect(apiId: String, apiHash: String) = withContext(Dispatchers.IO) {
        require(apiId.toIntOrNull()?.let { it > 0 } == true && apiHash.matches(Regex("[a-fA-F0-9]{32}"))) { "Enter the API ID and 32-character API hash from my.telegram.org." }
        val key = ByteArray(32).also { SecureRandom().nextBytes(it) }
        secrets.save(JSONObject().put("id", apiId.toInt()).put("hash", apiHash).put("key", Base64.encodeToString(key, Base64.NO_WRAP)))
        start()
    }
    private suspend fun authorize(state: TdApi.AuthorizationState) {
        try {
            withTimeout(5000) { while (client == null) delay(10) }
            when (state) {
                is TdApi.AuthorizationStateWaitTdlibParameters -> {
                    val saved = credentials ?: error("Enter API credentials.")
                    val root = File(context.noBackupFilesDir, "telegram").apply { mkdirs() }
                    send(TdApi.SetTdlibParameters().apply {
                        databaseDirectory = File(root, "database").path; filesDirectory = File(root, "files").path
                        databaseEncryptionKey = Base64.decode(saved.getString("key"), Base64.NO_WRAP)
                        useFileDatabase = true; useChatInfoDatabase = true; useMessageDatabase = false; useSecretChats = false
                        apiId = saved.getInt("id"); apiHash = saved.getString("hash")
                        systemLanguageCode = "en"; deviceModel = Build.MODEL; systemVersion = Build.VERSION.RELEASE; applicationVersion = "Toolkit 0.4"
                    })
                }
                is TdApi.AuthorizationStateWaitPhoneNumber -> mutableStatus.value = TelegramStatus("phone", "Enter your phone number with country code")
                is TdApi.AuthorizationStateWaitCode -> mutableStatus.value = TelegramStatus("code", "Enter the login code sent by Telegram")
                is TdApi.AuthorizationStateWaitPassword -> mutableStatus.value = TelegramStatus("password", state.passwordHint.orEmpty())
                is TdApi.AuthorizationStateWaitEmailAddress -> mutableStatus.value = TelegramStatus("email", "Telegram requires an email address for this login")
                is TdApi.AuthorizationStateWaitEmailCode -> mutableStatus.value = TelegramStatus("email-code", "Enter the code sent to your email")
                is TdApi.AuthorizationStateReady -> mutableStatus.value = TelegramStatus("ready", "Connected")
                is TdApi.AuthorizationStateClosed -> {
                    client = null
                    if (loggingOut) { File(context.noBackupFilesDir, "telegram").deleteRecursively(); secrets.clear(); credentials = null; loggingOut = false }
                    mutableStatus.value = TelegramStatus(if (credentials == null) "credentials" else "closed", "Connection closed")
                }
                is TdApi.AuthorizationStateLoggingOut, is TdApi.AuthorizationStateClosing -> mutableStatus.value = TelegramStatus("connecting", "Disconnecting")
                else -> mutableStatus.value = TelegramStatus("unsupported", "Complete account registration or verification in the official Telegram app, then reconnect.")
            }
        } catch (e: Exception) { mutableStatus.value = mutableStatus.value.copy(error = safeError(e)) }
    }
    suspend fun submit(value: String) {
        when (status.value.step) {
            "phone" -> send(TdApi.SetAuthenticationPhoneNumber(value, null))
            "code" -> send(TdApi.CheckAuthenticationCode(value))
            "password" -> send(TdApi.CheckAuthenticationPassword(value))
            "email" -> send(TdApi.SetAuthenticationEmailAddress(value))
            "email-code" -> send(TdApi.CheckAuthenticationEmailCode(TdApi.EmailAddressAuthenticationCode(value)))
            else -> error("Wait for Telegram to request the next login step.")
        }
    }
    suspend fun signOut() {
        check(store.dao.all().none { it.kind == "TELEGRAM" && it.state in setOf("QUEUED", "RUNNING", "SAVING") }) { "Pause Telegram downloads before signing out." }
        loggingOut = true
        try { send(TdApi.LogOut()) } catch (e: Exception) { loggingOut = false; throw e }
    }
    suspend fun chats(query: String, limit: Int = 100): List<TelegramChat> {
        require(status.value.step == "ready") { "Connect to Telegram first." }
        val ids = if (query.isNotBlank()) send(TdApi.SearchChats(query, null, limit)).chatIds.toList() else {
            runCatching { send(TdApi.LoadChats(TdApi.ChatListMain(), limit)) }
            runCatching { send(TdApi.LoadChats(TdApi.ChatListArchive(), limit)) }
            (send(TdApi.GetChats(TdApi.ChatListMain(), limit)).chatIds.toList() + send(TdApi.GetChats(TdApi.ChatListArchive(), limit)).chatIds.toList()).distinct()
        }
        return ids.map { id -> val chat = send(TdApi.GetChat(id)); TelegramChat(id, chat.title) }
    }
    suspend fun files(chat: Long, query: String, type: String, from: Long = 0): TelegramPage {
        val filter = when (type) {
            "Documents" -> TdApi.SearchMessagesFilterDocument(); "Videos" -> TdApi.SearchMessagesFilterVideo(); "Audio" -> TdApi.SearchMessagesFilterAudio()
            "Photos" -> TdApi.SearchMessagesFilterPhoto(); else -> TdApi.SearchMessagesFilterEmpty()
        }
        val response = send(TdApi.SearchChatMessages().apply { chatId = chat; this.query = query; fromMessageId = from; limit = 50; this.filter = filter })
        return TelegramPage(response.messages.mapNotNull(::fileFromMessage), response.nextFromMessageId)
    }
    private fun fileFromMessage(message: TdApi.Message): TelegramFile? {
        if (!message.canBeSaved) return null
        val tuple: Triple<TdApi.File, String, String> = when (val c = message.content) {
            is TdApi.MessageDocument -> Triple(c.document.document, c.document.fileName, "Document")
            is TdApi.MessageVideo -> Triple(c.video.video, c.video.fileName.ifBlank { "Video-${message.id}.mp4" }, "Video")
            is TdApi.MessageAudio -> Triple(c.audio.audio, c.audio.fileName.ifBlank { "Audio-${message.id}.m4a" }, "Audio")
            is TdApi.MessageAnimation -> Triple(c.animation.animation, c.animation.fileName.ifBlank { "Animation-${message.id}.mp4" }, "Animation")
            is TdApi.MessageVoiceNote -> Triple(c.voiceNote.voice, "Voice-${message.id}.ogg", "Voice")
            is TdApi.MessagePhoto -> Triple(c.photo.sizes.maxByOrNull { it.width.toLong() * it.height }?.photo ?: return null, "Photo-${message.id}.jpg", "Photo")
            else -> return null
        }
        return TelegramFile(message.chatId, message.id, tuple.first.id, safeDownloadName(tuple.second), maxOf(tuple.first.size, tuple.first.expectedSize), tuple.third, tuple.first.remote.id)
    }
    suspend fun enqueue(files: List<TelegramFile>, folder: String) {
        files.forEach { f -> store.enqueue(DownloadKind.TELEGRAM, f.remote, f.name, folder, JSONObject().put("chat", f.chat).put("message", f.message)) }
    }
    suspend fun download(entry: DownloadEntry) = withContext(Dispatchers.IO) {
        start()
        withTimeout(30_000) { while (status.value.step == "connecting") delay(100) }
        check(status.value.step == "ready") { "Connect to Telegram in Downloads before resuming." }
        val options = JSONObject(entry.options)
        val fresh = fileFromMessage(send(TdApi.GetMessage(options.getLong("chat"), options.getLong("message")))) ?: error("This message is no longer downloadable.")
        var file = send(TdApi.DownloadFile(fresh.id, 16, 0, 0, false))
        var lastBytes = file.local.downloadedSize; var lastTime = System.currentTimeMillis()
        try {
            while (!file.local.isDownloadingCompleted) {
                currentCoroutineContext().ensureActive()
                check(file.local.canBeDownloaded || file.local.isDownloadingActive) { "Telegram cannot download this file. Check access to the chat." }
                delay(750)
                file = send(TdApi.GetFile(fresh.id))
                val now = System.currentTimeMillis(); val bytes = file.local.downloadedSize; val total = maxOf(file.size, file.expectedSize)
                store.dao.progress(entry.id, bytes, total, ((bytes - lastBytes) * 1000 / (now - lastTime).coerceAtLeast(1)).coerceAtLeast(0), if (total > 0) (bytes * 100 / total).toInt() else -1, "Downloading from Telegram")
                lastTime = now; lastBytes = bytes
            }
            val source = File(file.local.path)
            val root = File(context.noBackupFilesDir, "telegram")
            check(source.isFile && source.canonicalPath.startsWith(root.canonicalPath + File.separator)) { "Telegram file is unavailable." }
            val dir = store.directory(entry.id); val output = File(dir, "data/${safeDownloadName(entry.name)}").apply { parentFile?.mkdirs() }
            source.inputStream().use { input -> output.outputStream().use { target ->
                val buffer = ByteArray(128 * 1024)
                while (true) { currentCoroutineContext().ensureActive(); val count = input.read(buffer); if (count < 0) break; target.write(buffer, 0, count) }
            } }
            store.dao.get(entry.id)?.let { store.dao.update(it.copy(ready = true, files = JSONArray().put(JSONObject().put("path", output.relativeTo(dir).path).put("name", entry.name).put("bytes", output.length())).toString(), bytes = output.length(), total = output.length(), progress = 100)) }
            send(TdApi.DeleteFile(fresh.id))
        } finally { withContext(NonCancellable) { runCatching { send(TdApi.CancelDownloadFile(fresh.id, false)) } } }
    }
    private suspend fun <T : TdApi.Object> send(request: TdApi.Function<T>): T = withTimeout(45_000) {
        suspendCancellableCoroutine { continuation ->
            val connection = client
            if (connection == null) { continuation.resumeWithException(IllegalStateException("Connect to Telegram first.")); return@suspendCancellableCoroutine }
            connection.send(request, { response ->
                if (continuation.isActive) {
                    if (response is TdApi.Error) continuation.resumeWithException(IllegalStateException("Telegram: ${response.message.take(160)}"))
                    else { @Suppress("UNCHECKED_CAST") continuation.resume(response as T) }
                }
            })
        }
    }
    fun safeError(e: Exception) = (e.message ?: "Telegram request failed.").take(200)
}
