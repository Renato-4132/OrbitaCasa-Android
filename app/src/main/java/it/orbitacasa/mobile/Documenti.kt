package it.orbitacasa.mobile

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

object Doc {
    class Item(
        val titolo: String,
        val categoria: String,
        val data: LocalDate?,
        val scadenza: LocalDate?,
        val note: String,
        val importo: Double?,
        val tipo: String,
        val nomeFile: String,
        val file: File?,
        val chiave: String = "",
        val profilo: String = "",
    ) {
        fun dettaglio(): String {
            val r = ArrayList<String>()
            if (categoria.isNotEmpty()) r.add("Categoria: $categoria")
            if (data != null) r.add("Data: " + Doc.fmt(data))
            if (scadenza != null) r.add("Scadenza: " + Doc.fmt(scadenza))
            if (importo != null) r.add("Importo: " + euro(importo) + if (tipo.isNotEmpty()) " ($tipo)" else "")
            if (note.isNotEmpty()) r.add("Note: $note")
            r.add(if (file != null) "File: $nomeFile" else "File non importato dal desktop")
            return r.joinToString("\n")
        }
    }

    private val DF = DateTimeFormatter.ofPattern("dd-MM-yyyy")
    private val DF_RAW = DateTimeFormatter.ofPattern("ddMMyyyy")

    private lateinit var dir: File

    fun init(d: File) {
        dir = d
    }

    fun fmt(d: LocalDate): String = d.format(DF)

    private fun dataRaw(s: String): LocalDate? =
        try { LocalDate.parse(s.trim(), DF_RAW) } catch (_: Exception) { null }

    private fun dataFmt(s: String): LocalDate? =
        try { LocalDate.parse(s.trim(), DF) } catch (_: Exception) { null }

    private fun pulisci(s: String): String {
        var t = s.trim()
        while (t.startsWith("ALL·")) t = t.removePrefix("ALL·").trim()
        return t
    }

    private fun leggiOggetto(f: File): JSONObject? =
        try { if (f.exists()) JSONObject(f.readText(Charsets.UTF_8)) else null } catch (_: Exception) { null }

    private fun soloNome(s: String): String = s.substringAfterLast('/').substringAfterLast('\\')

    fun archiviati(): List<Item> {
        val reg = leggiOggetto(File(dir, "documenti_archiviati.json")) ?: return emptyList()
        val cartella = File(dir, "documenti")
        val out = ArrayList<Item>()
        for (k in reg.keys()) {
            val o = reg.optJSONObject(k) ?: continue
            val nome = soloNome(k)
            val f = File(cartella, nome).takeIf { it.isFile }
            val cent = o.optString("importo_raw", "").toLongOrNull()
            val desc = pulisci(o.optString("descrizione_esatta", ""))
            out.add(
                Item(
                    titolo = if (desc.isEmpty()) nome else desc,
                    categoria = o.optString("categoria_esatta", ""),
                    data = dataRaw(o.optString("data_raw", "")),
                    scadenza = null,
                    note = "",
                    importo = cent?.let { it / 100.0 },
                    tipo = o.optString("tipo_esatto", ""),
                    nomeFile = nome,
                    file = f,
                    chiave = k,
                )
            )
        }
        return out.sortedByDescending { it.data ?: LocalDate.MIN }
    }

    fun personali(): List<Pair<String, List<Item>>> {
        val radice = File(dir, "documenti_personali")
        if (!radice.isDirectory) return emptyList()
        val nomi = LinkedHashSet<String>()
        try {
            val f = File(radice, "profili.json")
            if (f.exists()) {
                val a = JSONArray(f.readText(Charsets.UTF_8))
                for (i in 0 until a.length()) nomi.add(a.optString(i))
            }
        } catch (_: Exception) {
        }
        radice.listFiles()?.filter { it.isDirectory }?.sortedBy { it.name.lowercase() }?.forEach { nomi.add(it.name) }
        val out = ArrayList<Pair<String, List<Item>>>()
        for (p in nomi) {
            if (p.isEmpty() || p.contains('/') || p.contains('\\') || p == "..") continue
            val cart = File(File(radice, p), "documenti")
            val reg = leggiOggetto(File(File(radice, p), "registry.json")) ?: continue
            val lista = ArrayList<Item>()
            for (k in reg.keys()) {
                val o = reg.optJSONObject(k) ?: continue
                val nome = soloNome(k)
                val f = File(cart, nome).takeIf { it.isFile }
                val cat = o.optString("categoria", "")
                val desc = o.optString("descrizione", "")
                lista.add(
                    Item(
                        titolo = if (desc.isEmpty()) (if (cat.isEmpty()) nome else cat) else desc,
                        categoria = cat,
                        data = dataRaw(o.optString("data_raw", "")) ?: dataFmt(o.optString("data_fmt", "")),
                        scadenza = dataFmt(o.optString("data_scadenza", "")),
                        note = o.optString("note", ""),
                        importo = null,
                        tipo = "",
                        nomeFile = nome,
                        file = f,
                        chiave = k,
                        profilo = p,
                    )
                )
            }
            out.add(p to lista.sortedWith(compareBy<Item>({ it.scadenza ?: LocalDate.MAX }, { it.titolo.lowercase(Locale.ITALY) })))
        }
        return out
    }

    private val ESTENSIONI = setOf("pdf", "jpg", "jpeg", "png", "webp", "gif", "heic")

    private fun estOk(nome: String) = nome.substringAfterLast('.', "").lowercase() in ESTENSIONI

    fun destinazione(nomeZip: String): String? {
        val p = nomeZip.replace('\\', '/').split('/').filter { it.isNotEmpty() && it != "." }
        if (p.isEmpty() || p.any { it == ".." }) return null
        val i = p.indexOf("documenti_personali")
        if (i >= 0) {
            val r = p.drop(i + 1)
            return when {
                r.size == 1 && r[0] == "profili.json" -> "documenti_personali/profili.json"
                r.size == 2 && (r[1] == "registry.json" || r[1] == "categorie.json") ->
                    "documenti_personali/${r[0]}/${r[1]}"
                r.size == 3 && r[1] == "documenti" && estOk(r[2]) ->
                    "documenti_personali/${r[0]}/documenti/${r[2]}"
                else -> null
            }
        }
        val n = p.size
        if (n >= 2 && p[n - 2] == "documenti" && estOk(p[n - 1])) return "documenti/" + p[n - 1]
        return null
    }

    private fun mime(f: File): String = when (f.extension.lowercase()) {
        "pdf" -> "application/pdf"
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "heic" -> "image/heic"
        else -> "*/*"
    }

    private fun uri(act: Activity, f: File) =
        FileProvider.getUriForFile(act, act.packageName + ".fileprovider", f)

    fun apri(act: Activity, f: File): Boolean = try {
        act.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri(act, f), mime(f))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        )
        true
    } catch (_: ActivityNotFoundException) {
        false
    }

    fun condividi(act: Activity, f: File) {
        val i = Intent(Intent.ACTION_SEND)
            .setType(mime(f))
            .putExtra(Intent.EXTRA_STREAM, uri(act, f))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        act.startActivity(Intent.createChooser(i, "Condividi documento"))
    }

    val CAT_PERSONALI = listOf(
        "Atto Notarile", "Altro", "Assicurazione", "Carta d'Identità",
        "Certificato", "Codice Fiscale", "Contratto", "Curriculum",
        "Dichiarazione Redditi", "Diploma", "Garanzia", "Laurea",
        "Manuale", "Passaporto", "Patente", "Polizza",
        "Proprietà Immobile", "Referto Medico", "Tessera Sanitaria",
        "Utenza Casa", "Verbale",
    )
    const val MAX_PROFILI = 5

    private fun leggiPerScrittura(f: File): JSONObject {
        if (!f.exists()) return JSONObject()
        try {
            return JSONObject(f.readText(Charsets.UTF_8))
        } catch (_: Exception) {
            try {
                Files.copy(f.toPath(), File(f.parentFile, f.name + ".corrotto").toPath(), StandardCopyOption.REPLACE_EXISTING)
            } catch (_: Exception) {
            }
            throw IllegalStateException(f.name + " illeggibile, non lo sovrascrivo (copia in .corrotto)")
        }
    }

    private fun scrivi(f: File, testo: String) {
        f.parentFile?.mkdirs()
        val tmp = File(f.parentFile, f.name + ".tmp")
        tmp.writeText(testo, Charsets.UTF_8)
        Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }

    private fun sha256(f: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { i ->
            val b = ByteArray(65536)
            while (true) {
                val n = i.read(b)
                if (n < 0) break
                md.update(b, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    fun prepara(act: Activity, uri: Uri): File {
        var nome = ""
        act.contentResolver.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (i >= 0) nome = c.getString(i) ?: ""
            }
        }
        var ext = nome.substringAfterLast('.', "").lowercase()
        if (ext !in ESTENSIONI) {
            ext = when (act.contentResolver.getType(uri)) {
                "image/jpeg" -> "jpg"
                "image/png" -> "png"
                "image/webp" -> "webp"
                else -> "pdf"
            }
        }
        val dest = File(act.cacheDir, "doc_nuovo.$ext")
        act.contentResolver.openInputStream(uri)!!.use { i -> dest.outputStream().use { o -> i.copyTo(o) } }
        return dest
    }

    fun senzaAll(s: String): String {
        var t = s
        while (t.startsWith("ALL·")) t = t.removePrefix("ALL·").trim()
        return t
    }

    private fun parole(t: String): Set<String> =
        Regex("[\\p{L}\\p{N}_]+").findAll(
            senzaAll(t).replace(Regex("(?:CNT|PER|CTP)·\\s*\\S+"), " ").replace("ALL·", " ").lowercase()
        ).map { it.value }.toSet()

    fun descCompatibili(a: String, b: String): Boolean =
        a == b || senzaAll(a) == senzaAll(b) || parole(a) == parole(b)

    private fun sanC(s: String, max: Int): String {
        val t = s.trim().replace(' ', '_').replace(Regex("[^\\p{L}\\p{N}_.-]"), "")
        return if (t.length > max) t.substring(0, max) else t.uppercase()
    }

    private fun sanP(s: String): String =
        s.trim().replace(' ', '_').replace(Regex("[^\\p{L}\\p{N}_.-]"), "").take(30).uppercase()

    private fun cent(v: Double): Long = Math.round(v * 100.0)

    private fun nomeContabile(data: LocalDate, desc: String, tipo: String, cat: String, importo: Double, ext: String) =
        data.format(DF_RAW) + "_" + sanC(desc, 30) + "_" + tipo + "_" + sanC(cat, 20) + "_" + cent(importo) + "." + ext

    private fun regContabile() = File(dir, "documenti_archiviati.json")
    private fun cartellaContabili() = File(dir, "documenti")

    fun duplicato(tmp: File): String? {
        val reg = leggiOggetto(regContabile()) ?: return null
        val h = sha256(tmp)
        for (k in reg.keys()) if (reg.optJSONObject(k)?.optString("sha256_hash") == h) return k
        return null
    }

    fun aggiungiContabile(tmp: File, data: LocalDate, cat: String, desc: String, tipo: String, importo: Double): String {
        val ext = tmp.extension.ifEmpty { "pdf" }
        val nome = nomeContabile(data, desc, tipo, cat, importo, ext)
        val reg = leggiPerScrittura(regContabile())
        cartellaContabili().mkdirs()
        Files.copy(tmp.toPath(), File(cartellaContabili(), nome).toPath(), StandardCopyOption.REPLACE_EXISTING)
        reg.put(
            nome,
            JSONObject().put("data_raw", data.format(DF_RAW)).put("categoria_esatta", cat)
                .put("descrizione_esatta", "ALL· " + senzaAll(desc)).put("importo_raw", cent(importo))
                .put("tipo_esatto", tipo).put("timestamp", LocalDateTime.now().toString())
                .put("sha256_hash", sha256(tmp))
        )
        scrivi(regContabile(), reg.toString(4))
        tmp.delete()
        return nome
    }

    fun modificaContabile(d: Item, data: LocalDate, cat: String, desc: String, tipo: String, importo: Double): String {
        val reg = leggiPerScrittura(regContabile())
        val vecchio = reg.optJSONObject(d.chiave) ?: throw IllegalStateException("Documento non trovato nel registro")
        val ext = d.nomeFile.substringAfterLast('.', "pdf")
        val nuovo = nomeContabile(data, senzaAll(desc), tipo, cat, importo, ext)
        val vecchioF = File(cartellaContabili(), d.nomeFile)
        if (nuovo != d.chiave && vecchioF.exists()) {
            Files.move(vecchioF.toPath(), File(cartellaContabili(), nuovo).toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
        reg.remove(d.chiave)
        val o = JSONObject().put("data_raw", data.format(DF_RAW)).put("categoria_esatta", cat)
            .put("descrizione_esatta", "ALL· " + senzaAll(desc)).put("importo_raw", cent(importo))
            .put("tipo_esatto", tipo).put("timestamp", vecchio.optString("timestamp", LocalDateTime.now().toString()))
        vecchio.optString("sha256_hash", "").takeIf { it.isNotEmpty() }?.let { o.put("sha256_hash", it) }
        reg.put(nuovo, o)
        scrivi(regContabile(), reg.toString(4))
        return nuovo
    }

    fun eliminaContabile(d: Item) {
        val reg = leggiPerScrittura(regContabile())
        d.file?.delete()
        reg.remove(d.chiave)
        scrivi(regContabile(), reg.toString(4))
    }

    fun spesaCollegata(d: Item): Voce? {
        val data = d.data ?: return null
        val imp = d.importo ?: return null
        val c = Repo.voci().filter { it.data == data && Math.abs(it.importo - imp) < 0.01 && it.tipo == d.tipo }
        return c.firstOrNull { descCompatibili(it.descrizione, d.titolo) } ?: c.firstOrNull()
    }

    fun documentoDiSpesa(v: Voce): Item? {
        if (!v.descrizione.contains("ALL·")) return null
        val c = archiviati().filter {
            it.data == v.data && Math.abs((it.importo ?: -1.0) - v.importo) < 0.01 && it.tipo == v.tipo
        }
        return c.firstOrNull { descCompatibili(it.titolo, v.descrizione) } ?: c.firstOrNull()
    }

    private fun radicePers() = File(dir, "documenti_personali")
    private fun dirProfilo(p: String) = File(radicePers(), p)

    fun categoriePers(profilo: String): List<String> {
        try {
            val f = File(dirProfilo(profilo), "categorie.json")
            if (f.exists()) {
                val a = JSONArray(f.readText(Charsets.UTF_8))
                val l = (0 until a.length()).map { a.optString(it) }.filter { it.isNotEmpty() }
                if (l.isNotEmpty()) return l.sortedBy { it.lowercase(Locale.ITALY) }
            }
        } catch (_: Exception) {
        }
        return CAT_PERSONALI.sortedBy { it.lowercase(Locale.ITALY) }
    }

    fun nomiProfili(): List<String> = personali().map { it.first }

    fun nuovoProfilo(nome: String): String? {
        val n = nome.trim()
        if (n.isEmpty()) return "Inserisci un nome."
        if (n.length > 20) return "Massimo 20 caratteri."
        if (n.contains('/') || n.contains('\\') || n == "." || n == "..") return "Nome non valido."
        val esistenti = nomiProfili()
        if (esistenti.any { it == n }) return "Il profilo '$n' esiste già."
        if (esistenti.size >= MAX_PROFILI) return "Puoi creare al massimo $MAX_PROFILI profili."
        val lista = (esistenti + n).sorted()
        File(dirProfilo(n), "documenti").mkdirs()
        scrivi(File(dirProfilo(n), "registry.json"), "{}")
        scrivi(File(dirProfilo(n), "categorie.json"), JSONArray(CAT_PERSONALI.sorted()).toString(2))
        scrivi(File(radicePers(), "profili.json"), JSONArray(lista).toString(2))
        return null
    }

    fun eliminaProfilo(nome: String) {
        val lista = nomiProfili().filter { it != nome }
        scrivi(File(radicePers(), "profili.json"), JSONArray(lista).toString(2))
        dirProfilo(nome).deleteRecursively()
    }

    private fun regPers(p: String) = File(dirProfilo(p), "registry.json")

    private fun recPers(data: LocalDate, cat: String, desc: String, note: String, scad: LocalDate?, ts: String) =
        JSONObject().put("data_raw", data.format(DF_RAW)).put("data_fmt", data.format(DF))
            .put("categoria", cat).put("descrizione", desc).put("note", note)
            .put("data_scadenza", scad?.format(DF) ?: "").put("timestamp", ts)

    fun aggiungiPers(tmp: File, profilo: String, data: LocalDate, cat: String, desc: String, note: String, scad: LocalDate?) {
        val ext = tmp.extension.ifEmpty { "pdf" }
        val nome = data.format(DF_RAW) + "_" + sanP(desc) + "_" + sanP(cat) + "." + ext
        val reg = leggiPerScrittura(regPers(profilo))
        val cart = File(dirProfilo(profilo), "documenti").also { it.mkdirs() }
        Files.copy(tmp.toPath(), File(cart, nome).toPath(), StandardCopyOption.REPLACE_EXISTING)
        reg.put(nome, recPers(data, cat, desc, note, scad, LocalDateTime.now().toString()))
        scrivi(regPers(profilo), reg.toString(4))
        tmp.delete()
    }

    fun modificaPers(d: Item, data: LocalDate, cat: String, desc: String, note: String, scad: LocalDate?) {
        val reg = leggiPerScrittura(regPers(d.profilo))
        val ts = reg.optJSONObject(d.chiave)?.optString("timestamp", "") ?: ""
        if (reg.optJSONObject(d.chiave) == null) throw IllegalStateException("Documento non trovato nel registro")
        reg.put(d.chiave, recPers(data, cat, desc, note, scad, ts.ifEmpty { LocalDateTime.now().toString() }))
        scrivi(regPers(d.profilo), reg.toString(4))
    }

    fun eliminaPers(d: Item) {
        val reg = leggiPerScrittura(regPers(d.profilo))
        d.file?.delete()
        reg.remove(d.chiave)
        scrivi(regPers(d.profilo), reg.toString(4))
    }
}
