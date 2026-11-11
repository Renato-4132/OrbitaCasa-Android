package it.orbitacasa.mobile

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object Demo {
    private const val URL_ZIP =
        "https://raw.githubusercontent.com/Renato-4132/OrbitaCasa-Android/main/Profilo_Demo.zip"

    fun scarica(ctx: Context): File {
        val dest = File(ctx.cacheDir, "Profilo_Demo.zip")
        val c = URL(URL_ZIP).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 60_000
        try {
            if (c.responseCode != 200) throw IllegalStateException("Download non riuscito (HTTP " + c.responseCode + ")")
            c.inputStream.use { i -> dest.outputStream().use { o -> i.copyTo(o) } }
        } finally {
            c.disconnect()
        }
        if (dest.length() < 1000) {
            dest.delete()
            throw IllegalStateException("File demo non valido")
        }
        return dest
    }

    fun messaggio(e: Throwable): String = when (e) {
        is java.net.UnknownHostException, is java.net.ConnectException -> "Nessuna connessione a internet."
        is java.net.SocketTimeoutException -> "Download troppo lento. Riprova."
        else -> e.message ?: "Errore sconosciuto"
    }
}
