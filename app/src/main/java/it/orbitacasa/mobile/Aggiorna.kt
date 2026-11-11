package it.orbitacasa.mobile

import android.app.Activity
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object Aggiorna {
    private const val REPO = "Renato-4132/OrbitaCasa-Android"

    class Release(val tag: String, val apkUrl: String)

    private fun numeri(v: String) = Regex("\\d+").findAll(v).map { it.value.toInt() }.toList()

    fun piuRecente(nuova: String, attuale: String): Boolean {
        val a = numeri(nuova)
        val b = numeri(attuale)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    fun versioneInstallata(act: Activity): String =
        act.packageManager.getPackageInfo(act.packageName, 0).versionName ?: "0"

    fun ultima(): Release? {
        val c = URL("https://api.github.com/repos/$REPO/releases/latest").openConnection() as HttpURLConnection
        c.connectTimeout = 10000
        c.readTimeout = 10000
        c.setRequestProperty("Accept", "application/vnd.github+json")
        try {
            if (c.responseCode == 404) return null
            if (c.responseCode != 200) throw IllegalStateException("GitHub ha risposto " + c.responseCode)
            val o = JSONObject(c.inputStream.bufferedReader().readText())
            val assets = o.optJSONArray("assets") ?: return null
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name").endsWith(".apk")) {
                    return Release(o.optString("tag_name"), a.getString("browser_download_url"))
                }
            }
            return null
        } finally {
            c.disconnect()
        }
    }
}
