package it.orbitacasa.mobile

import android.content.Context
import org.json.JSONObject
import java.io.File

object Pool {
    private const val FILE = "pool_cache.json"

    fun carica(ctx: Context): Pair<JSONObject, Long>? {
        return try {
            val f = File(ctx.filesDir, FILE)
            if (!f.isFile) return null
            JSONObject(f.readText(Charsets.UTF_8)) to f.lastModified()
        } catch (_: Exception) {
            null
        }
    }

    fun salva(ctx: Context, dati: JSONObject) {
        try {
            File(ctx.filesDir, FILE).writeText(dati.toString(), Charsets.UTF_8)
        } catch (_: Exception) {
        }
    }
}
