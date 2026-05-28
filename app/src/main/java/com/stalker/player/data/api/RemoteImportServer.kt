package com.stalker.player.data.api

import android.content.Context
import com.stalker.player.data.model.Profile
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class RemoteImportServer(
    private val context: Context,
    private val accessCode: String,
    private val profilesProvider: () -> List<Profile>,
    private val onProfileImported: (Profile) -> Unit,
    private val onProfileDeleted: (String) -> Unit
) {
    companion object {
        const val PORT = 8080
        private const val MAX_BODY_BYTES = 60 * 1024 * 1024
    }

    private val running = AtomicBoolean(false)
    private val executor = Executors.newCachedThreadPool()
    private var serverSocket: ServerSocket? = null

    fun start() {
        if (!running.compareAndSet(false, true)) return
        executor.execute {
            try {
                serverSocket = ServerSocket(PORT)
                while (running.get()) {
                    val socket = serverSocket?.accept() ?: break
                    executor.execute { handle(socket) }
                }
            } catch (_: Exception) {
                running.set(false)
            }
        }
    }

    fun stop() {
        running.set(false)
        try { serverSocket?.close() } catch (_: Exception) { }
        executor.shutdownNow()
    }

    fun localUrl(): String {
        return "http://${localIpAddress()}:$PORT/?code=$accessCode"
    }

    private fun handle(socket: Socket) {
        socket.use { s ->
            try {
                s.soTimeout = 15_000
                val input = BufferedInputStream(s.getInputStream())
                val request = readRequest(input)
                val response = when {
                    !request.isAuthorized() -> htmlResponse(authPage(request.query["code"].orEmpty()), 401)
                    request.method == "GET" -> htmlResponse(homePage(null))
                    request.method == "POST" && request.path == "/import" -> handleImport(request)
                    request.method == "POST" && request.path == "/delete" -> handleDelete(request)
                    else -> textResponse(404, "Not found")
                }
                s.getOutputStream().writeResponse(response)
            } catch (e: Exception) {
                s.getOutputStream().writeResponse(htmlResponse(homePage("Errore: ${escape(e.message ?: e.toString())}"), 500))
            }
        }
    }

    private fun handleImport(request: HttpRequest): HttpResponse {
        val contentType = request.headers["content-type"].orEmpty()
        val fields = if (contentType.startsWith("multipart/form-data", ignoreCase = true)) {
            parseMultipart(request.body, contentType)
        } else {
            parseUrlEncoded(String(request.body, StandardCharsets.UTF_8)).mapValues { FormPart(it.value) }
        }

        val type = fields["type"]?.text.orEmpty().ifBlank { "m3u" }.lowercase(Locale.ROOT)
        val name = fields["name"]?.text.orEmpty().ifBlank { defaultProfileName(type) }
        val url = fields["url"]?.text.orEmpty().trim()
        val mac = fields["mac"]?.text.orEmpty().trim()
        val username = fields["username"]?.text.orEmpty().trim()
        val password = fields["password"]?.text.orEmpty().trim()
        val uploaded = fields["file"]

        val finalUrl = when {
            type == "m3u" && uploaded?.bytes?.isNotEmpty() == true -> saveUploadedPlaylist(name, uploaded.bytes)
            else -> url
        }

        require(finalUrl.isNotBlank()) { "Inserisci un URL oppure carica un file M3U." }
        if (type == "xtream") require(username.isNotBlank() && password.isNotBlank()) { "Username e password Xtream sono obbligatori." }
        if (type == "mac") require(mac.isNotBlank()) { "MAC address obbligatorio per profilo MAC/STB." }

        onProfileImported(Profile(name = name, url = finalUrl, mac = mac, username = username, password = password, type = type))
        return htmlResponse(homePage("Profilo '${escape(name)}' importato. Ora puoi caricarlo dalla TV in Profili."))
    }

    private fun handleDelete(request: HttpRequest): HttpResponse {
        val fields = parseUrlEncoded(String(request.body, StandardCharsets.UTF_8))
        val name = fields["name"].orEmpty()
        require(name.isNotBlank()) { "Profilo non valido" }
        onProfileDeleted(name)
        return htmlResponse(homePage("Profilo '${escape(name)}' eliminato."))
    }

    private fun saveUploadedPlaylist(name: String, bytes: ByteArray): String {
        val safeName = name.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), "-").trim('-').ifBlank { "playlist" }
        val fileName = "remote-$safeName-${System.currentTimeMillis()}.m3u"
        context.openFileOutput(fileName, Context.MODE_PRIVATE).use { it.write(bytes) }
        return fileName
    }

    private fun readRequest(input: InputStream): HttpRequest {
        val headerBytes = ByteArrayOutputStream()
        var previous3 = -1
        var previous2 = -1
        var previous1 = -1
        while (true) {
            val b = input.read()
            if (b == -1) break
            headerBytes.write(b)
            if (previous3 == '\r'.code && previous2 == '\n'.code && previous1 == '\r'.code && b == '\n'.code) break
            previous3 = previous2
            previous2 = previous1
            previous1 = b
        }

        val headerText = headerBytes.toString(StandardCharsets.ISO_8859_1.name())
        val lines = headerText.split("\r\n").filter { it.isNotBlank() }
        require(lines.isNotEmpty()) { "Richiesta vuota" }
        val requestLine = lines.first().split(" ")
        val headers = lines.drop(1).mapNotNull { line ->
            val idx = line.indexOf(':')
            if (idx <= 0) null else line.substring(0, idx).lowercase(Locale.ROOT) to line.substring(idx + 1).trim()
        }.toMap()
        val length = headers["content-length"]?.toIntOrNull() ?: 0
        require(length <= MAX_BODY_BYTES) { "File troppo grande. Limite: ${MAX_BODY_BYTES / 1024 / 1024} MB" }
        val body = ByteArray(length)
        var read = 0
        while (read < length) {
            val count = input.read(body, read, length - read)
            if (count == -1) break
            read += count
        }
        val rawPath = requestLine.getOrElse(1) { "/" }
        val path = rawPath.substringBefore('?')
        val query = parseUrlEncoded(rawPath.substringAfter('?', ""))
        return HttpRequest(requestLine[0], path, query, headers, if (read == length) body else body.copyOf(read))
    }

    private fun parseUrlEncoded(body: String): Map<String, String> {
        if (body.isBlank()) return emptyMap()
        return body.split('&').mapNotNull { pair ->
            val idx = pair.indexOf('=')
            if (idx < 0) null else decode(pair.substring(0, idx)) to decode(pair.substring(idx + 1))
        }.toMap()
    }

    private fun parseMultipart(body: ByteArray, contentType: String): Map<String, FormPart> {
        val boundary = contentType.substringAfter("boundary=", "").trim().trim('"')
        require(boundary.isNotBlank()) { "Multipart non valido" }
        val raw = String(body, StandardCharsets.ISO_8859_1)
        val parts = linkedMapOf<String, FormPart>()
        raw.split("--$boundary").forEach { part ->
            if (part.isBlank() || part.startsWith("--")) return@forEach
            val clean = part.removePrefix("\r\n").removeSuffix("\r\n")
            val splitAt = clean.indexOf("\r\n\r\n")
            if (splitAt <= 0) return@forEach
            val headers = clean.substring(0, splitAt)
            val data = clean.substring(splitAt + 4)
            val disposition = headers.lineSequence().firstOrNull { it.startsWith("Content-Disposition", ignoreCase = true) }.orEmpty()
            val name = Regex("name=\"([^\"]+)\"").find(disposition)?.groupValues?.getOrNull(1) ?: return@forEach
            val filename = Regex("filename=\"([^\"]*)\"").find(disposition)?.groupValues?.getOrNull(1).orEmpty()
            val bytes = data.toByteArray(StandardCharsets.ISO_8859_1)
            parts[name] = if (filename.isBlank()) FormPart(text = data) else FormPart(text = filename, bytes = bytes)
        }
        return parts
    }

    private fun htmlResponse(body: String, status: Int = 200) = HttpResponse(status, "text/html; charset=utf-8", body.toByteArray(StandardCharsets.UTF_8))
    private fun textResponse(status: Int, body: String) = HttpResponse(status, "text/plain; charset=utf-8", body.toByteArray(StandardCharsets.UTF_8))

    private fun OutputStream.writeResponse(response: HttpResponse) {
        val reason = if (response.status == 200) "OK" else "Error"
        write("HTTP/1.1 ${response.status} $reason\r\n".toByteArray())
        write("Content-Type: ${response.contentType}\r\n".toByteArray())
        write("Content-Length: ${response.body.size}\r\n".toByteArray())
        write("Connection: close\r\n\r\n".toByteArray())
        write(response.body)
        flush()
    }

    private fun homePage(message: String?): String {
        val notice = message?.let { "<div class='notice'>$it</div>" }.orEmpty()
        val profiles = profilesProvider()
        val profileRows = if (profiles.isEmpty()) {
            "<p class='empty'>Nessun profilo salvato.</p>"
        } else {
            profiles.joinToString("") { profile ->
                """
                <div class="profile"><div><strong>${escape(profile.name)}</strong><span>${escape(typeLabel(profile.type))}</span><small>${escape(profile.url)}</small></div><form method="post" action="/delete" onsubmit="return confirm('Eliminare ${escape(profile.name)}?')"><input type="hidden" name="code" value="$accessCode"><input type="hidden" name="name" value="${escape(profile.name)}"><button class="danger" type="submit">Elimina</button></form></div>
                """.trimIndent()
            }
        }
        return """
            <!doctype html><html lang="it"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
            <title>AstraTV Remote Import</title><style>
            *{box-sizing:border-box}html,body{max-width:100%;overflow-x:hidden}body{margin:0;background:#0b111d;color:#e7edf4;font-family:system-ui,-apple-system,Segoe UI,sans-serif;min-height:100vh;padding:clamp(12px,3vw,22px)}body:before{content:"";position:fixed;inset:0;background:radial-gradient(circle at 70% 12%,rgba(127,166,201,.24),transparent 34%),linear-gradient(135deg,#080d18,#182539 62%,#27384d);z-index:-1}
            main{width:min(100%,980px);margin:auto;display:grid;grid-template-columns:minmax(0,1.15fr) minmax(260px,.85fr);gap:18px}.card{min-width:0;background:rgba(17,22,29,.88);border:1px solid rgba(127,166,201,.22);border-radius:26px;padding:clamp(16px,3vw,24px);box-shadow:0 20px 70px rgba(0,0,0,.38)}
            h1{margin:0 0 8px;font-size:clamp(28px,5vw,34px)}h2{margin:0 0 14px;font-size:20px}p{color:#aeb8c8;line-height:1.5}.grid{display:grid;grid-template-columns:minmax(0,1fr) minmax(0,1fr);gap:14px}.hidden{display:none}@media(max-width:860px){main,.grid{grid-template-columns:1fr}.card{border-radius:22px}}
            label{display:block;font-size:13px;color:#9fb0c5;margin:14px 0 6px}input,select{width:100%;min-width:0;background:#0f1622;color:#e7edf4;border:1px solid #314158;border-radius:14px;padding:13px;font-size:16px}input[type=file]{padding:10px}.notice{background:rgba(127,166,201,.14);border:1px solid rgba(127,166,201,.32);border-radius:16px;padding:14px;margin:18px 0;color:#dcefff}
            button{margin-top:20px;width:100%;border:0;border-radius:16px;background:#7fa6c9;color:#08101b;padding:15px;font-weight:800;font-size:16px}.hint{font-size:13px;color:#8c99aa}.profile{min-width:0;max-width:100%;display:grid;grid-template-columns:minmax(0,1fr) auto;gap:12px;align-items:center;background:#101824;border:1px solid rgba(127,166,201,.14);border-radius:16px;padding:13px;margin:10px 0}.profile>div{min-width:0;max-width:100%;overflow:hidden}.profile strong,.profile small{display:block;min-width:0;max-width:100%}.profile span{display:inline-block;margin:5px 0;color:#7fa6c9;font-size:12px}.profile small{color:#8c99aa;overflow-wrap:anywhere;word-break:break-word;white-space:normal;line-height:1.35}.profile form{margin:0}.danger{margin:0;background:#d06c62;color:#180807;padding:10px 12px;font-size:13px}.empty{font-size:14px}@media(max-width:520px){.profile{grid-template-columns:1fr}.danger{width:100%}}
            </style></head><body><main><h1>AstraTV</h1><p>Importa una lista o un profilo dal telefono/PC. Dopo il salvataggio, apri <b>Profili</b> sulla TV e carica il nuovo profilo.</p>$notice
            <section class="card"><h2>Nuovo profilo</h2><form method="post" action="/import" enctype="multipart/form-data"><input type="hidden" name="code" value="$accessCode"><div class="grid"><div><label>Tipo</label><select name="type" id="type"><option value="m3u">M3U / M3U8</option><option value="xtream">Xtream Codes</option><option value="mac">MAC / STB</option></select></div><div><label>Nome profilo</label><input name="name" placeholder="Casa, Provider, Test..."></div></div>
            <div data-kind="m3u"><label>URL lista M3U</label><input name="url" placeholder="https://server/get.php?..."><label>Oppure carica file M3U</label><input name="file" type="file" accept=".m3u,.m3u8,text/*"><div class="hint">Limite upload: 60 MB.</div></div>
            <div data-kind="xtream" class="hidden"><label>Server Xtream</label><input name="url" placeholder="http://server:porta"><div class="grid"><div><label>Username</label><input name="username"></div><div><label>Password</label><input name="password" type="password"></div></div></div>
            <div data-kind="mac" class="hidden"><label>URL portale</label><input name="url" placeholder="http://portal:8080/c/"><label>MAC address</label><input name="mac" placeholder="00:1A:79:XX:XX:XX"></div>
            <button type="submit">Salva su AstraTV</button></form></section><section class="card"><h2>Profili salvati</h2>$profileRows</section></main><script>const t=document.getElementById('type');function sync(){document.querySelectorAll('[data-kind]').forEach(e=>{const active=e.dataset.kind===t.value;e.classList.toggle('hidden',!active);e.querySelectorAll('input,select,textarea').forEach(i=>i.disabled=!active);});}t.addEventListener('change',sync);sync();</script></body></html>
        """.trimIndent()
    }

    private fun typeLabel(type: String) = when (type) {
        "xtream" -> "Xtream Codes"
        "mac" -> "MAC / STB"
        else -> "M3U / M3U8"
    }

    private fun authPage(typedCode: String): String = """
        <!doctype html><html lang="it"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>AstraTV</title><style>body{margin:0;background:#0b111d;color:#e7edf4;font-family:system-ui;min-height:100vh;display:grid;place-items:center;padding:24px}main{width:min(420px,100%);background:#11161d;border:1px solid rgba(127,166,201,.25);border-radius:24px;padding:24px}input,button{width:100%;box-sizing:border-box;border-radius:14px;padding:14px;font-size:18px}input{background:#0f1622;color:#e7edf4;border:1px solid #314158}button{margin-top:14px;border:0;background:#7fa6c9;color:#08101b;font-weight:800}</style></head><body><main><h1>AstraTV</h1><p>Inserisci il codice mostrato sulla TV.</p><form method="get"><input name="code" value="${escape(typedCode)}" placeholder="Codice TV"><button>Entra</button></form></main></body></html>
    """.trimIndent()

    private fun HttpRequest.isAuthorized(): Boolean {
        if (query["code"] == accessCode) return true
        if (method == "POST") {
            val contentType = headers["content-type"].orEmpty()
            val bodyText = String(body, StandardCharsets.UTF_8)
            return if (contentType.startsWith("multipart/form-data", ignoreCase = true)) {
                bodyText.contains("name=\"code\"") && bodyText.contains("\r\n$accessCode\r\n")
            } else {
                parseUrlEncoded(bodyText)["code"] == accessCode
            }
        }
        return false
    }

    private fun defaultProfileName(type: String) = when (type) {
        "xtream" -> "Xtream remoto"
        "mac" -> "MAC remoto"
        else -> "M3U remoto"
    }

    private fun localIpAddress(): String {
        NetworkInterface.getNetworkInterfaces().toList().forEach { network ->
            if (!network.isUp || network.isLoopback) return@forEach
            network.inetAddresses.toList().forEach { address ->
                if (address is Inet4Address && !address.isLoopbackAddress) return address.hostAddress ?: "127.0.0.1"
            }
        }
        return "127.0.0.1"
    }

    private fun decode(value: String) = URLDecoder.decode(value, StandardCharsets.UTF_8.name())
    private fun escape(value: String) = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")

    private data class HttpRequest(val method: String, val path: String, val query: Map<String, String>, val headers: Map<String, String>, val body: ByteArray)
    private data class HttpResponse(val status: Int, val contentType: String, val body: ByteArray)
    private data class FormPart(val text: String = "", val bytes: ByteArray = ByteArray(0))
}
