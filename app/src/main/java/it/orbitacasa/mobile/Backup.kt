package it.orbitacasa.mobile

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Backup nella cartella pubblica Documenti/OrbitaCasa/backup/, sul modello di esegui_backup_zip.py del desktop.
 *
 *  - file singoli:  DD-MM-YYYY-<nome>.json            (stesso giorno = sovrascritto, ne restano 5 per file)
 *  - snapshot:      DD-MM-YYYY-snapshot_db.zip        (contiene db/..., stesso giorno = sovrascritto, ne restano 5)
 *  - documenti:     Archivio_Doc_Contabili-DDMMYYYY_HHMM.zip e Archivio_Doc_Personali_DDMMYYYY_HHMM.zip
 *                   (solo se qualcosa è cambiato dall'ultimo zip, ne restano 3 per tipo)
 */
object Backup {
    const val SUFFISSO_SNAP = "-snapshot_db.zip"
    const val PREF_CONTABILI = "Archivio_Doc_Contabili"
    const val PREF_PERSONALI = "Archivio_Doc_Personali"

    private const val MAX_FILE = 5
    private const val MAX_SNAP = 5
    private const val MAX_DOC = 3

    private val RELATIVE = Environment.DIRECTORY_DOCUMENTS + "/OrbitaCasa/backup/"

    class Voce(val nome: String, val modificato: Long, val dimensione: Long, val uri: Uri)

    private fun collezione(): Uri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

    fun elenca(ctx: Context): List<Voce> {
        val out = ArrayList<Voce>()
        try {
            val proj = arrayOf(
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.DISPLAY_NAME,
                MediaStore.Files.FileColumns.DATE_MODIFIED,
                MediaStore.Files.FileColumns.SIZE,
            )
            ctx.contentResolver.query(
                collezione(), proj,
                MediaStore.Files.FileColumns.RELATIVE_PATH + " = ?", arrayOf(RELATIVE), null
            )?.use { c ->
                while (c.moveToNext()) {
                    val nome = c.getString(1) ?: continue
                    out.add(Voce(nome, c.getLong(2) * 1000L, c.getLong(3), ContentUris.withAppendedId(collezione(), c.getLong(0))))
                }
            }
        } catch (_: Exception) {
        }
        return out
    }

    private fun trova(ctx: Context, nome: String): Voce? = elenca(ctx).firstOrNull { it.nome == nome }

    fun apri(ctx: Context, nome: String): InputStream? {
        val v = trova(ctx, nome) ?: return null
        return try {
            ctx.contentResolver.openInputStream(v.uri)
        } catch (_: Exception) {
            null
        }
    }

    fun elimina(ctx: Context, nome: String): Boolean {
        val v = trova(ctx, nome) ?: return false
        return try {
            ctx.contentResolver.delete(v.uri, null, null) > 0
        } catch (_: Exception) {
            false
        }
    }

    /** Copia [src] in Documenti/OrbitaCasa/backup/[nome], sovrascrivendo un file con lo stesso nome. */
    private fun salva(ctx: Context, nome: String, mime: String, src: File): Boolean {
        val cr = ctx.contentResolver
        return try {
            val esistente = trova(ctx, nome)
            if (esistente != null) {
                val os = cr.openOutputStream(esistente.uri, "wt") ?: return false
                os.use { o -> src.inputStream().use { it.copyTo(o) } }
                true
            } else {
                val cv = ContentValues().apply {
                    put(MediaStore.Files.FileColumns.DISPLAY_NAME, nome)
                    put(MediaStore.Files.FileColumns.MIME_TYPE, mime)
                    put(MediaStore.Files.FileColumns.RELATIVE_PATH, RELATIVE)
                    put(MediaStore.Files.FileColumns.IS_PENDING, 1)
                }
                val uri = cr.insert(collezione(), cv) ?: return false
                try {
                    val os = cr.openOutputStream(uri, "w") ?: throw IllegalStateException("flusso non disponibile")
                    os.use { o -> src.inputStream().use { it.copyTo(o) } }
                    val fatto = ContentValues().apply { put(MediaStore.Files.FileColumns.IS_PENDING, 0) }
                    cr.update(uri, fatto, null, null)
                    true
                } catch (e: Exception) {
                    try {
                        cr.delete(uri, null, null)
                    } catch (_: Exception) {
                    }
                    false
                }
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun oggi(): String = SimpleDateFormat("dd-MM-yyyy", Locale.ROOT).format(Date())

    private fun adesso(): String = SimpleDateFormat("ddMMyyyy_HHmm", Locale.ROOT).format(Date())

    /** Chiave ordinabile (yyyyMMddHHmm) ricavata dal nome; se manca, dalla data di modifica. */
    private fun chiave(v: Voce): String {
        Regex("^(\\d{2})-(\\d{2})-(\\d{4})-").find(v.nome)?.let {
            return it.groupValues[3] + it.groupValues[2] + it.groupValues[1] + "0000"
        }
        Regex("[-_](\\d{2})(\\d{2})(\\d{4})_(\\d{4})").find(v.nome)?.let {
            return it.groupValues[3] + it.groupValues[2] + it.groupValues[1] + it.groupValues[4]
        }
        return SimpleDateFormat("yyyyMMddHHmm", Locale.ROOT).format(Date(v.modificato))
    }

    private fun pota(ctx: Context, max: Int, filtro: (String) -> Boolean) {
        try {
            val tutti = elenca(ctx).filter { filtro(it.nome) }.sortedBy { chiave(it) }
            for (v in tutti.dropLast(max)) {
                try {
                    ctx.contentResolver.delete(v.uri, null, null)
                } catch (_: Exception) {
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun comprimi(dir: File, files: List<String>, cartelle: List<String>, prefisso: String, tmp: File): Int {
        var n = 0
        ZipOutputStream(tmp.outputStream().buffered()).use { zip ->
            for (nome in files) {
                val f = File(dir, nome)
                if (!f.isFile) continue
                zip.putNextEntry(ZipEntry(prefisso + nome))
                f.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
                n++
            }
            for (cartella in cartelle) {
                val radice = File(dir, cartella)
                if (!radice.isDirectory) continue
                radice.walkTopDown()
                    .filter { it.isFile && !it.name.endsWith(".tmp") && !it.name.endsWith(".corrotto") }
                    .sortedBy { it.path }
                    .forEach { f ->
                        val rel = f.relativeTo(dir).path.replace('\\', '/')
                        zip.putNextEntry(ZipEntry(prefisso + rel))
                        f.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                        n++
                    }
            }
        }
        return n
    }

    private fun ultimaModifica(dir: File, files: List<String>, cartelle: List<String>): Long {
        var m = 0L
        for (nome in files) File(dir, nome).takeIf { it.isFile }?.let { m = maxOf(m, it.lastModified()) }
        for (c in cartelle) {
            val r = File(dir, c)
            if (!r.isDirectory) continue
            r.walkTopDown().filter { it.isFile }.forEach { m = maxOf(m, it.lastModified()) }
        }
        return m
    }

    private fun zipDocumenti(ctx: Context, dir: File, prefisso: String, files: List<String>, cartelle: List<String>) {
        val ultimaDoc = ultimaModifica(dir, files, cartelle)
        if (ultimaDoc == 0L) return
        val ultimo = elenca(ctx).filter { it.nome.startsWith(prefisso) && it.nome.endsWith(".zip") }.maxByOrNull { it.modificato }
        if (ultimo != null && ultimaDoc <= ultimo.modificato) return
        val tmp = File(ctx.cacheDir, "backup_doc.tmp")
        try {
            val n = comprimi(dir, files, cartelle, "", tmp)
            if (n == 0) return
            val sep = if (prefisso == PREF_CONTABILI) "-" else "_"
            if (salva(ctx, prefisso + sep + adesso() + ".zip", "application/zip", tmp)) {
                pota(ctx, MAX_DOC) { it.startsWith(prefisso) && it.endsWith(".zip") }
            }
        } catch (_: Exception) {
        } finally {
            tmp.delete()
        }
    }

    /**
     * Esegue il backup. Restituisce il nome dello snapshot creato, oppure null se non è riuscito
     * (o se non c'era nessun file da salvare).
     */
    @Synchronized
    fun esegui(ctx: Context, dir: File, files: List<String>, conDocumenti: Boolean): String? {
        val tmpSnap = File(ctx.cacheDir, "backup_snap.tmp")
        val nomeSnap = oggi() + SUFFISSO_SNAP
        try {
            val n = comprimi(dir, files, emptyList(), "db/", tmpSnap)
            if (n == 0) return null
            if (!salva(ctx, nomeSnap, "application/zip", tmpSnap)) return null
        } catch (_: Exception) {
            return null
        } finally {
            tmpSnap.delete()
        }
        pota(ctx, MAX_SNAP) { it.endsWith(SUFFISSO_SNAP) }

        for (nome in files) {
            val f = File(dir, nome)
            if (!f.isFile) continue
            if (salva(ctx, oggi() + "-" + nome, "application/json", f)) {
                pota(ctx, MAX_FILE) { Regex("^\\d{2}-\\d{2}-\\d{4}-(.+)$").matchEntire(it)?.groupValues?.get(1) == nome }
            }
        }

        if (conDocumenti) {
            zipDocumenti(ctx, dir, PREF_CONTABILI, listOf("documenti_archiviati.json"), listOf("documenti"))
            zipDocumenti(ctx, dir, PREF_PERSONALI, emptyList(), listOf("documenti_personali"))
        }
        return nomeSnap
    }

    fun eZip(nome: String): Boolean = nome.endsWith(".zip")

    fun tipo(nome: String): String = when {
        nome.endsWith(SUFFISSO_SNAP) -> "Snapshot DB Completo"
        nome.startsWith(PREF_PERSONALI) -> "Docs Personali (ZIP)"
        nome.startsWith(PREF_CONTABILI) -> "Docs Contabili (ZIP)"
        else -> nome.split('-', limit = 4).getOrNull(3) ?: nome
    }

    fun data(v: Voce): String {
        if (Regex("\\d{2}-\\d{2}-\\d{4}-.*").matches(v.nome)) return v.nome.take(10)
        val m = Regex("[-_](\\d{2})(\\d{2})(\\d{4})_").find(v.nome)
        if (m != null) return m.groupValues[1] + "-" + m.groupValues[2] + "-" + m.groupValues[3]
        return SimpleDateFormat("dd-MM-yyyy", Locale.ROOT).format(Date(v.modificato))
    }
}
