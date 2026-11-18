package it.orbitacasa.mobile

import android.app.Activity
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import org.json.JSONObject
import java.io.File
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

    private val RELATIVE = Environment.DIRECTORY_DOCUMENTS + "/OrbitaCasa/updates/"
    private const val MIME_APK = "application/vnd.android.package-archive"

    private fun collezione(): Uri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

    private fun cartellaCache(ctx: Context): File = File(ctx.cacheDir, "apk")

    private fun nomeFile(r: Release): String = "OrbitaCasa-" + r.tag.trim().let { if (it.startsWith("v")) it else "v$it" } + ".apk"

    /** Scarica l'APK nella cache e controlla che sia davvero OrbitaCasa. [progresso] riceve la percentuale (0-100). */
    fun scarica(ctx: Context, r: Release, progresso: (Int) -> Unit): File {
        val dir = cartellaCache(ctx)
        dir.mkdirs()
        dir.listFiles()?.forEach { it.delete() }
        val f = File(dir, nomeFile(r))
        val c = URL(r.apkUrl).openConnection() as HttpURLConnection
        c.connectTimeout = 15000
        c.readTimeout = 30000
        try {
            if (c.responseCode != 200) throw IllegalStateException("GitHub ha risposto " + c.responseCode)
            val tot = c.contentLengthLong
            var letti = 0L
            var ultimo = -1
            c.inputStream.use { ins ->
                f.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = ins.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        letti += n
                        if (tot > 0) {
                            val p = (letti * 100 / tot).toInt()
                            if (p != ultimo) {
                                ultimo = p
                                progresso(p)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            f.delete()
            throw e
        } finally {
            c.disconnect()
        }
        val info = ctx.packageManager.getPackageArchiveInfo(f.path, 0)
        if (info == null || info.packageName != ctx.packageName) {
            f.delete()
            throw IllegalStateException("il file scaricato non è un aggiornamento di OrbitaCasa")
        }
        return f
    }

    private class Voce(val nome: String, val uri: Uri)

    private fun elenca(ctx: Context): List<Voce> {
        val out = ArrayList<Voce>()
        try {
            val proj = arrayOf(MediaStore.Files.FileColumns._ID, MediaStore.Files.FileColumns.DISPLAY_NAME)
            ctx.contentResolver.query(
                collezione(), proj, MediaStore.Files.FileColumns.RELATIVE_PATH + " = ?", arrayOf(RELATIVE), null
            )?.use { c ->
                while (c.moveToNext()) {
                    val nome = c.getString(1) ?: continue
                    out.add(Voce(nome, ContentUris.withAppendedId(collezione(), c.getLong(0))))
                }
            }
        } catch (_: Exception) {
        }
        return out
    }

    private fun eApkOrbita(nome: String) = nome.startsWith("OrbitaCasa-") && nome.endsWith(".apk")

    /** Tiene in Documenti/OrbitaCasa/updates/ solo questa copia: le altre versioni scaricate vengono cancellate. */
    fun archivia(ctx: Context, f: File, r: Release): Boolean {
        val cr = ctx.contentResolver
        return try {
            val nome = nomeFile(r)
            for (v in elenca(ctx)) if (eApkOrbita(v.nome)) runCatching { cr.delete(v.uri, null, null) }
            val cv = ContentValues().apply {
                put(MediaStore.Files.FileColumns.DISPLAY_NAME, nome)
                put(MediaStore.Files.FileColumns.MIME_TYPE, MIME_APK)
                put(MediaStore.Files.FileColumns.RELATIVE_PATH, RELATIVE)
                put(MediaStore.Files.FileColumns.IS_PENDING, 1)
            }
            val uri = cr.insert(collezione(), cv) ?: return false
            try {
                val os = cr.openOutputStream(uri, "w") ?: throw IllegalStateException("flusso non disponibile")
                os.use { o -> f.inputStream().use { it.copyTo(o) } }
                cr.update(uri, ContentValues().apply { put(MediaStore.Files.FileColumns.IS_PENDING, 0) }, null, null)
                true
            } catch (e: Exception) {
                runCatching { cr.delete(uri, null, null) }
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    /** All'avvio: svuota la cache e cancella dagli updates le versioni uguali o più vecchie di quella installata. */
    fun pulisci(ctx: Context, installata: String) {
        try {
            cartellaCache(ctx).listFiles()?.forEach { it.delete() }
            for (v in elenca(ctx)) {
                if (eApkOrbita(v.nome) && !piuRecente(v.nome, installata)) {
                    runCatching { ctx.contentResolver.delete(v.uri, null, null) }
                }
            }
        } catch (_: Exception) {
        }
    }
}
