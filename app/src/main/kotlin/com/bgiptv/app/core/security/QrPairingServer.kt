package com.bgiptv.app.core.security

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import fi.iki.elonen.NanoHTTPD
import org.json.JSONObject
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

data class PairingResult(
    val serverUrl: String,
    val username: String,
    val password: String,
)

/**
 * Serveur HTTP local sur la TV pour le setup via téléphone.
 * Utilise une clé éphémère AES-256 pour chiffrer les credentials en transit.
 */
@Singleton
class QrPairingServer @Inject constructor() {

    private var server: PairingHttpServer? = null
    private var ephemeralKey: ByteArray? = null
    private var onCredentialsReceived: ((PairingResult) -> Unit)? = null

    val port = 8181

    fun start(onReceived: (PairingResult) -> Unit): ByteArray {
        stop()
        ephemeralKey = generateEphemeralKey()
        onCredentialsReceived = onReceived
        server = PairingHttpServer(port, ephemeralKey!!) { result ->
            onReceived(result)
            stop()
        }
        server?.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
        return ephemeralKey!!
    }

    fun stop() {
        server?.stop()
        server = null
        ephemeralKey = null
        onCredentialsReceived = null
    }

    fun generateQrBitmap(localIp: String, keyBase64: String, sizePx: Int = 512): Bitmap {
        val url = "http://$localIp:$port?key=$keyBase64"
        val hints = mapOf(EncodeHintType.MARGIN to 1)
        val bits = QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.RGB_565)
        for (x in 0 until sizePx) {
            for (y in 0 until sizePx) {
                bitmap.setPixel(x, y, if (bits[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        return bitmap
    }

    private fun generateEphemeralKey(): ByteArray {
        val key = ByteArray(32)
        SecureRandom().nextBytes(key)
        return key
    }
}

private class PairingHttpServer(
    port: Int,
    private val key: ByteArray,
    private val onReceived: (PairingResult) -> Unit,
) : NanoHTTPD(port) {

    override fun serve(session: IHTTPSession): Response {
        return when {
            session.method == Method.GET && session.uri == "/" -> serveSetupPage()
            session.method == Method.POST && session.uri == "/submit" -> handleSubmit(session)
            else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "")
        }
    }

    private fun serveSetupPage(): Response {
        val keyBase64 = Base64.getEncoder().encodeToString(key)
        val html = """
            <!DOCTYPE html>
            <html lang="fr">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>BG IPTV — Setup</title>
                <style>
                    * { box-sizing: border-box; margin: 0; padding: 0; }
                    body { font-family: -apple-system, sans-serif; background: #0a0a0a; color: #fff; padding: 24px; }
                    h1 { font-size: 22px; margin-bottom: 4px; }
                    .sub { color: #888; font-size: 14px; margin-bottom: 24px; }
                    .lock { font-size: 12px; color: #555; margin-bottom: 24px; }
                    label { display: block; font-size: 13px; color: #aaa; margin-bottom: 6px; margin-top: 16px; }
                    input { width: 100%; padding: 12px; background: #1a1a1a; border: 1px solid #333; border-radius: 8px; color: #fff; font-size: 16px; }
                    input:focus { outline: none; border-color: #4A9EFF; }
                    .btn { display: block; width: 100%; padding: 14px; background: #4A9EFF; border: none; border-radius: 8px; color: #fff; font-size: 16px; font-weight: 600; margin-top: 24px; cursor: pointer; }
                    .paste-btn { display: block; width: 100%; padding: 12px; background: #1a1a1a; border: 1px solid #333; border-radius: 8px; color: #4A9EFF; font-size: 14px; margin-top: 12px; cursor: pointer; }
                    .success { display: none; text-align: center; padding: 40px; }
                    .success h2 { color: #44CC77; font-size: 24px; margin-bottom: 12px; }
                    select { width: 100%; padding: 12px; background: #1a1a1a; border: 1px solid #333; border-radius: 8px; color: #fff; font-size: 16px; }
                </style>
            </head>
            <body>
                <h1>BG IPTV</h1>
                <p class="sub">Configuration de ta playlist</p>
                <p class="lock">🔒 Chiffré — tes identifiants ne quittent pas ton réseau local</p>

                <div id="form">
                    <label>Type de playlist</label>
                    <select id="type">
                        <option value="xtream">Xtream Codes</option>
                        <option value="m3u">Lien M3U</option>
                    </select>

                    <label>URL serveur</label>
                    <input type="url" id="serverUrl" placeholder="http://provider.com:8080" inputmode="url">

                    <label>Identifiant</label>
                    <input type="text" id="username" autocomplete="username" autocapitalize="none">

                    <label>Mot de passe</label>
                    <input type="password" id="password" autocomplete="current-password">

                    <button class="btn" onclick="sendCredentials()">Envoyer vers la TV →</button>
                    <button class="paste-btn" onclick="pasteFull()">📋 Coller un lien complet</button>
                </div>

                <div class="success" id="success">
                    <h2>✓ Envoyé !</h2>
                    <p>Tu peux reprendre sur ta TV.</p>
                </div>

                <script>
                const KEY_B64 = "$keyBase64";

                function b64ToBytes(b64) {
                    const bin = atob(b64);
                    return Uint8Array.from(bin, c => c.charCodeAt(0));
                }

                async function encrypt(plaintext) {
                    const key = await crypto.subtle.importKey(
                        "raw", b64ToBytes(KEY_B64), "AES-GCM", false, ["encrypt"]
                    );
                    const iv = crypto.getRandomValues(new Uint8Array(12));
                    const encrypted = await crypto.subtle.encrypt(
                        { name: "AES-GCM", iv }, key,
                        new TextEncoder().encode(plaintext)
                    );
                    const combined = new Uint8Array(iv.length + encrypted.byteLength);
                    combined.set(iv);
                    combined.set(new Uint8Array(encrypted), iv.length);
                    return btoa(String.fromCharCode(...combined));
                }

                async function sendCredentials() {
                    const serverUrl = document.getElementById('serverUrl').value.trim();
                    const username = document.getElementById('username').value.trim();
                    const password = document.getElementById('password').value.trim();
                    if (!serverUrl || !username || !password) { alert('Tous les champs sont requis'); return; }

                    const payload = JSON.stringify({ serverUrl, username, password });
                    const encrypted = await encrypt(payload);

                    await fetch('/submit', { method: 'POST', body: encrypted,
                        headers: { 'Content-Type': 'text/plain' } });
                    document.getElementById('form').style.display = 'none';
                    document.getElementById('success').style.display = 'block';
                }

                async function pasteFull() {
                    try {
                        const text = await navigator.clipboard.readText();
                        parseFullUrl(text);
                    } catch {
                        const url = prompt('Colle le lien complet ici:');
                        if (url) parseFullUrl(url);
                    }
                }

                function parseFullUrl(fullUrl) {
                    try {
                        const u = new URL(fullUrl);
                        document.getElementById('serverUrl').value = u.origin;
                        document.getElementById('username').value = u.searchParams.get('username') || '';
                        document.getElementById('password').value = u.searchParams.get('password') || '';
                    } catch { alert('URL non reconnue'); }
                }
                </script>
            </body>
            </html>
        """.trimIndent()
        return newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", html)
    }

    private fun handleSubmit(session: IHTTPSession): Response {
        return try {
            val body = mutableMapOf<String, String>()
            session.parseBody(body)
            val encryptedB64 = body["postData"] ?: return newFixedLengthResponse(
                Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "Missing body"
            )

            val decrypted = decryptAesGcm(encryptedB64, key)
            val json = JSONObject(decrypted)
            val result = PairingResult(
                serverUrl = json.getString("serverUrl"),
                username = json.getString("username"),
                password = json.getString("password"),
            )
            onReceived(result)
            newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "OK")
        } catch (e: Exception) {
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Error")
        }
    }

    private fun decryptAesGcm(encryptedB64: String, key: ByteArray): String {
        val combined = Base64.getDecoder().decode(encryptedB64)
        val iv = combined.sliceArray(0..11)
        val ciphertext = combined.sliceArray(12 until combined.size)
        val secretKey = SecretKeySpec(key, "AES")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
        return String(cipher.doFinal(ciphertext))
    }
}
