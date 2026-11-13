package it.orbitacasa.mobile

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.math.abs

data class Voce(
    val id: String,
    val data: LocalDate,
    val categoria: String,
    val descrizione: String,
    val importo: Double,
    val tipo: String,
    val ora: String,
    val metodo: String,
    val hashtag: List<String>,
    val ricorrente: Boolean,
    val conto: String = "",
    val fs: JSONObject? = null,
)

data class Conto(
    val id: String,
    val nome: String,
    val tipo: String,
    val saldoIniziale: Double,
    val principale: Boolean,
    val iban: String,
    val note: String,
)

data class Trasf(
    val id: String,
    val data: LocalDate,
    val da: String,
    val a: String,
    val importo: Double,
    val note: String,
    val idRicorrenza: String,
)

data class Ric(
    val id: String,
    val tipo: String,
    val n: Int,
    val inizio: LocalDate?,
    val fine: LocalDate?,
    val cat: String,
    val desc: String,
    val imp: Double,
    val tipoVoce: String,
    val metodo: String,
    val fatte: Int,
)

data class Piano(
    val id: String,
    val nome: String,
    val categoria: String,
    val importoTotale: Double,
    val inizio: LocalDate,
    val scadenza: LocalDate,
    val idSpesa: String,
    val descrizione: String = "",
)

object Repo {
    val FILES = listOf(
        "spese_db.json",
        "portafoglio_db.json",
        "veicoli.json",
        "animali.json",
        "immobil.json",
        "finanziamenti.json",
        "fondo_risparmio.json",
        "rubrica.json",
        "utenze_db.json",
        "documenti_archiviati.json",
        "supermercati.json",
        "memoria_categorie.json",
        "promemoria.json",
        "portafoglio.json",
        "fondo_pensione.json",
        "spese_pianificate.json",
        "dieta_piano.json",
        "alimenti_custom.json",
        "peso_storico.json",
        "fabbisogno_dati.json",
        "pedometro.json",
        "manutenzione_casa.json",
        "magazzino.json",
        "studio_clienti.json",
        "studio_appuntamenti.json",
        "studio_prestazioni.json",
        "studio_fatture.json",
        "studio_emittente.json",
        "studio_cassa.json",
        "studio_magazzino.json",
    )

    private const val MAIN = "spese_db.json"
    private const val SETT = "android_impostazioni.json"
    private const val PF = "portafoglio_db.json"

    val TIPI_CONTO = listOf("personale", "comune", "figli", "risparmio", "altro")
    const val MAX_CONTI = 10
    val CLASSI = listOf("Bisogni", "Desideri", "Risparmio")

    val METODI: List<Pair<String, String>> = listOf(
        "Contanti" to "💰", "RID/SDD" to "🔄", "Bonifico" to "🏦", "C.Credito 1" to "💎",
        "C.Credito 2" to "💜", "C.Debito" to "💳", "Contactless" to "📶", "PayPal" to "📯",
        "Bollettino" to "📮", "Prepagata" to "🏪", "Assegno" to "🪙", "Revolut" to "🔘",
        "Apple Pay" to "🍎", "Google Pay" to "🎯", "Postepay" to "🏣", "Satispay" to "📲",
        "Scalapay" to "🔀", "Amazon Pay" to "🛒", "Altro" to "",
    )

    fun metodoConEmoji(m: String): String {
        val sim = METODI.firstOrNull { it.first == m }?.second ?: ""
        return if (sim.isEmpty()) m else "$sim $m"
    }
    private val DF = DateTimeFormatter.ofPattern("dd-MM-yyyy")

    private lateinit var dir: File
    private lateinit var prefs: SharedPreferences
    private var db = JSONObject()
    private var pf = nuovoPf()

    var rev = 0
        private set
    var lockOn = false
        private set
    var targetMese = 0.0
        private set
    var targetAnno = 0.0
        private set
    var smartCatOn = true
        private set
    var smartCatToll = 15.0
        private set
    var nomeGestore = ""
        private set
    var checkMese = true
        private set
    var sogliaGiorniRic = 5
        private set
    var fmDismiss: LocalDate? = null
        private set
    val BARRA_DEFAULT = listOf("MOVIMENTI", "CALENDARIO", "GRAFICI", "CONTI")
    var barra: List<String> = BARRA_DEFAULT
        private set

    fun setBarra(l: List<String>) {
        if (l.size != 4) return
        barra = l
        salvaImpostazioni()
    }

    fun init(ctx: Context) {
        if (::dir.isInitialized) return
        dir = File(ctx.filesDir, "db").also { it.mkdirs() }
        Fin.init(dir)
        Park.init(dir)
        Immo.init(dir)
        Pet.init(dir)
        Fondo.init(dir)
        Casa.init(dir)
        Pensione.init(dir)
        Doc.init(dir)
        Utenze.init(dir)
        Prom.init(dir)
        Notif.init(dir)
        prefs = ctx.getSharedPreferences("orbita", Context.MODE_PRIVATE)
        lockOn = prefs.getBoolean("lock", false)
        load()
        caricaImpostazioni()
    }

    private fun caricaImpostazioni() {
        try {
            val f = File(dir, SETT)
            if (f.exists()) {
                val o = JSONObject(f.readText(Charsets.UTF_8))
                targetMese = o.optDouble("target_mese", 0.0).coerceAtLeast(0.0)
                targetAnno = o.optDouble("target_anno", 0.0).coerceAtLeast(0.0)
                smartCatOn = o.optBoolean("smartcat_enabled", true)
                smartCatToll = o.optDouble("smartcat_toll", 15.0).coerceAtLeast(0.0)
                nomeGestore = o.optString("fairshare_gestore", "")
                checkMese = o.optBoolean("check_mese", true)
                sogliaGiorniRic = o.optInt("soglia_giorni_ricorrenti", 5).coerceIn(0, 28)
                fmDismiss = runCatching { LocalDate.parse(o.optString("fm_dismiss", "")) }.getOrNull()
                o.optJSONArray("barra")?.let { a ->
                    val l = (0 until a.length()).map { a.optString(it) }
                    if (l.size == 4) barra = l
                }
            }
        } catch (_: Exception) {
        }
    }

    fun setTarget(mese: Double, anno: Double) {
        targetMese = mese.coerceAtLeast(0.0)
        targetAnno = anno.coerceAtLeast(0.0)
        salvaImpostazioni()
    }

    fun setNomeGestore(n: String) {
        nomeGestore = n.trim()
        salvaImpostazioni()
    }

    fun setCheckMese(on: Boolean) {
        checkMese = on
        salvaImpostazioni()
    }

    fun setSogliaGiorniRic(g: Int) {
        sogliaGiorniRic = g.coerceIn(0, 28)
        salvaImpostazioni()
    }

    fun setFmDismiss(d: LocalDate) {
        fmDismiss = d
        salvaImpostazioni()
    }

    fun setSmartCat(on: Boolean) {
        smartCatOn = on
        salvaImpostazioni()
    }

    private fun salvaImpostazioni() {
        try {
            File(dir, SETT).writeText(
                JSONObject()
                    .put("target_mese", targetMese)
                    .put("target_anno", targetAnno)
                    .put("smartcat_enabled", smartCatOn)
                    .put("smartcat_toll", smartCatToll)
                    .put("fairshare_gestore", nomeGestore)
                    .put("check_mese", checkMese)
                    .put("soglia_giorni_ricorrenti", sogliaGiorniRic)
                    .put("fm_dismiss", fmDismiss?.toString() ?: "")
                    .put("barra", JSONArray(barra))
                    .toString(2),
                Charsets.UTF_8
            )
        } catch (_: Exception) {
        }
    }

    fun setLock(on: Boolean) {
        lockOn = on
        prefs.edit().putBoolean("lock", on).apply()
    }

    fun ricarica() {
        load()
        rev++
    }

    private fun nuovoDb(): JSONObject = JSONObject()
        .put("categorie", JSONArray().put("Generica"))
        .put("categorie_tipi", JSONObject().put("Generica", "Uscita"))
        .put("spese", JSONArray())
        .put("ricorrenze", JSONObject())
        .put("budget_categorie", JSONObject())
        .put("budget_annuale_categorie", JSONObject())
        .put("pianificate", JSONObject().put("piani", JSONArray()))
        .put(
            "fairshare",
            JSONObject().put("gestore_partecipa", true).put("partecipanti", JSONArray())
        )

    private fun nuovoPf(): JSONObject = JSONObject()
        .put("saldo_fisico", 0.0)
        .put("saldo_data", LocalDate.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy")))
        .put("storico_saldo", JSONArray())
        .put("conti", JSONArray())
        .put("trasferimenti", JSONArray())

    private fun loadPf() {
        val f = File(dir, PF)
        var o: JSONObject? = null
        for (c in listOf(f, File(dir, "$PF.bak"))) {
            if (!c.exists()) continue
            try {
                o = JSONObject(c.readText(Charsets.UTF_8))
                break
            } catch (_: Exception) {
            }
        }
        pf = o ?: nuovoPf()
        if (pf.optJSONArray("conti") == null) pf.put("conti", JSONArray())
        if (pf.optJSONArray("trasferimenti") == null) pf.put("trasferimenti", JSONArray())
    }

    private fun savePf() {
        val f = File(dir, PF)
        val tmp = File(dir, "$PF.tmp")
        tmp.writeText(pf.toString(2), Charsets.UTF_8)
        if (f.exists()) {
            try {
                f.copyTo(File(dir, "$PF.bak"), overwrite = true)
            } catch (_: Exception) {
            }
        }
        Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING)
        rev++
    }

    private fun load() {
        loadPf()
        val f = File(dir, MAIN)
        var loaded: JSONObject? = null
        for (c in listOf(f, File(dir, "$MAIN.bak"))) {
            if (!c.exists()) continue
            try {
                loaded = JSONObject(c.readText(Charsets.UTF_8))
                break
            } catch (_: Exception) {
            }
        }
        if (loaded == null && f.exists()) {
            try {
                f.copyTo(File(dir, "$MAIN.corrotto"), overwrite = true)
            } catch (_: Exception) {
            }
        }
        db = loaded ?: nuovoDb()
        var dirty = loaded == null && !f.exists()

        if (db.optJSONArray("categorie") == null) {
            db.put("categorie", JSONArray().put("Generica")); dirty = true
        }
        if (db.optJSONObject("categorie_tipi") == null) {
            db.put("categorie_tipi", JSONObject()); dirty = true
        }
        if (db.optJSONArray("spese") == null) {
            db.put("spese", JSONArray()); dirty = true
        }
        if (db.optJSONObject("ricorrenze") == null) {
            db.put("ricorrenze", JSONObject()); dirty = true
        }
        if (pulisciPiani()) dirty = true
        val sp = db.getJSONArray("spese")
        for (i in 0 until sp.length()) {
            val en = sp.optJSONObject(i)?.optJSONArray("entries") ?: continue
            for (j in 0 until en.length()) {
                val e = en.optJSONObject(j) ?: continue
                if (str(e, "id_spesa").isBlank()) {
                    e.put("id_spesa", nuovoId()); dirty = true
                }
            }
        }
        if (dirty) save()
    }

    private fun save() {
        val f = File(dir, MAIN)
        val tmp = File(dir, "$MAIN.tmp")
        tmp.writeText(db.toString(2), Charsets.UTF_8)
        if (f.exists()) {
            try {
                f.copyTo(File(dir, "$MAIN.bak"), overwrite = true)
            } catch (_: Exception) {
            }
        }
        Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING)
        rev++
    }

    private fun str(o: JSONObject, k: String): String =
        if (o.isNull(k)) "" else o.optString(k, "")

    private fun nuovoId(): String = UUID.randomUUID().toString().replace("-", "").take(12)

    private fun parseData(s: String): LocalDate? =
        try {
            LocalDate.parse(s, DF)
        } catch (_: Exception) {
            null
        }

    private fun toVoce(d: LocalDate, e: JSONObject): Voce {
        val tags = ArrayList<String>()
        e.optJSONArray("hashtag")?.let { a ->
            for (i in 0 until a.length()) tags.add(a.optString(i))
        }
        return Voce(
            id = str(e, "id_spesa"),
            data = d,
            categoria = str(e, "categoria"),
            descrizione = str(e, "descrizione"),
            importo = e.optDouble("importo", 0.0),
            tipo = str(e, "tipo").ifBlank { "Uscita" },
            ora = str(e, "ora"),
            metodo = str(e, "metodo_pagamento"),
            hashtag = tags,
            ricorrente = e.has("id_ricorrenza") && !e.isNull("id_ricorrenza"),
            conto = str(e, "conto"),
            fs = e.optJSONObject("fairshare"),
        )
    }

    fun voci(): List<Voce> {
        val out = ArrayList<Voce>()
        val sp = db.optJSONArray("spese") ?: return out
        for (i in 0 until sp.length()) {
            val day = sp.optJSONObject(i) ?: continue
            val d = parseData(day.optString("date")) ?: continue
            val en = day.optJSONArray("entries") ?: continue
            for (j in 0 until en.length()) {
                val e = en.optJSONObject(j) ?: continue
                out.add(toVoce(d, e))
            }
        }
        return out
    }

    fun fsBlocco(): JSONObject {
        var b = db.optJSONObject("fairshare")
        if (b == null) {
            b = JSONObject().put("gestore_partecipa", true).put("partecipanti", JSONArray())
            db.put("fairshare", b)
        }
        if (b.optJSONArray("partecipanti") == null) b.put("partecipanti", JSONArray())
        return b
    }

    fun fsSalva() = save()

    fun fsImposta(modifiche: List<Pair<String, JSONObject?>>) {
        var m = false
        for ((id, payload) in modifiche) {
            val e = ricercaEntry(id) ?: continue
            if (payload == null) e.remove("fairshare") else e.put("fairshare", payload)
            m = true
        }
        if (m) save()
    }

    fun fsRinomina(vecchio: String, nuovo: String, nomiAlt: List<String>): Int {
        val alt = nomiAlt.filter { it.isNotEmpty() }.toSet().sortedByDescending { it.length }
        val rx = Regex("(PER|CNT|CTP)·(" + alt.joinToString("|") { Regex.escape(it) } + ")(?![\\p{L}\\p{N}_])")
        fun sost(t: String): String = rx.replace(t) { m ->
            if (m.groupValues[2] == vecchio) m.groupValues[1] + "·" + nuovo else m.value
        }
        var n = 0
        val sp = db.optJSONArray("spese") ?: JSONArray()
        for (i in 0 until sp.length()) {
            val en = sp.optJSONObject(i)?.optJSONArray("entries") ?: continue
            for (j in 0 until en.length()) {
                val e = en.optJSONObject(j) ?: continue
                var mod = false
                val d = str(e, "descrizione")
                val d2 = sost(d)
                if (d2 != d) { e.put("descrizione", d2); mod = true }
                val pag = e.optJSONObject("fairshare")?.optJSONObject("pagamenti")
                if (pag != null && pag.has(vecchio)) {
                    val x = pag.get(vecchio)
                    pag.remove(vecchio)
                    pag.put(nuovo, x)
                    mod = true
                }
                if (mod) n++
            }
        }
        val ric = db.optJSONObject("ricorrenze")
        if (ric != null) {
            for (k in ric.keys()) {
                val r = ric.optJSONObject(k) ?: continue
                if (r.has("desc") && !r.isNull("desc")) r.put("desc", sost(r.optString("desc")))
            }
        }
        val piani = db.optJSONObject("pianificate")?.optJSONArray("piani")
        if (piani != null) {
            for (i in 0 until piani.length()) {
                val p = piani.optJSONObject(i) ?: continue
                for (k in listOf("descrizione", "nome")) {
                    if (p.has(k) && !p.isNull(k)) p.put(k, sost(p.optString(k)))
                }
            }
        }
        val parts = fsBlocco().getJSONArray("partecipanti")
        for (i in 0 until parts.length()) {
            val p = parts.optJSONObject(i) ?: continue
            if (p.optString("nome") == vecchio) p.put("nome", nuovo)
            val soci = p.optJSONArray("soci") ?: continue
            for (j in 0 until soci.length()) if (soci.optString(j) == vecchio) soci.put(j, nuovo)
        }
        save()
        return n
    }

    private fun rimuoviEntry(id: String): JSONObject? {
        val sp = db.getJSONArray("spese")
        for (i in 0 until sp.length()) {
            val day = sp.optJSONObject(i) ?: continue
            val en = day.optJSONArray("entries") ?: continue
            for (j in 0 until en.length()) {
                val e = en.optJSONObject(j) ?: continue
                if (str(e, "id_spesa") == id) {
                    en.remove(j)
                    if (en.length() == 0) sp.remove(i)
                    return e
                }
            }
        }
        return null
    }

    private fun ordinaGiorni() {
        val sp = db.getJSONArray("spese")
        val giorni = ArrayList<JSONObject>()
        for (i in 0 until sp.length()) sp.optJSONObject(i)?.let { giorni.add(it) }
        giorni.sortBy { parseData(it.optString("date")) ?: LocalDate.MIN }
        val nuovo = JSONArray()
        giorni.forEach { nuovo.put(it) }
        db.put("spese", nuovo)
    }

    private fun setOpt(o: JSONObject, k: String, v: String) {
        if (v.isBlank()) o.remove(k) else o.put(k, v)
    }

    fun salvaVoce(
        id: String?,
        data: LocalDate,
        categoria: String,
        descrizione: String,
        importo: Double,
        tipo: String,
        metodo: String,
        hashtag: List<String>,
        conto: String = "",
    ) {
        val entry: JSONObject? = if (id != null) rimuoviEntry(id) else null
        val e = entry ?: JSONObject()
            .put("id_spesa", nuovoId())
            .put("ora", LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")))
        e.put("categoria", categoria)
        e.put("descrizione", descrizione)
        e.put("importo", Math.round(importo * 100.0) / 100.0)
        e.put("tipo", tipo)
        setOpt(e, "metodo_pagamento", metodo)
        setOpt(e, "conto", conto)
        if (hashtag.isEmpty()) e.remove("hashtag") else e.put("hashtag", JSONArray(hashtag))

        inserisciEntry(data, e)
        save()
    }

    private fun inserisciEntry(data: LocalDate, e: JSONObject) {
        val sp = db.getJSONArray("spese")
        val chiave = data.format(DF)
        var giorno: JSONObject? = null
        for (i in 0 until sp.length()) {
            val d = sp.optJSONObject(i)
            if (d != null && d.optString("date") == chiave) {
                giorno = d
                break
            }
        }
        val g = giorno ?: JSONObject().put("date", chiave).put("entries", JSONArray()).also {
            sp.put(it)
            ordinaGiorni()
        }
        g.getJSONArray("entries").put(e)
    }

    fun elimina(id: String) {
        rimuoviEntry(id)
        rimuoviPianiPerSpesa(id)
        save()
    }

    val BLOCCHI_SYNC = listOf(
        "categorie", "categorie_tipi", "categorie_classi", "categorie_conti_risparmio",
        "ricorrenze", "budget_categorie", "budget_annuale_categorie", "pianificate",
        "fairshare", "memoria_categorie",
    )

    fun dirDati(): File = dir

    fun syncVoci(): LinkedHashMap<String, Pair<String, JSONObject>> {
        val out = LinkedHashMap<String, Pair<String, JSONObject>>()
        val sp = db.optJSONArray("spese") ?: return out
        for (i in 0 until sp.length()) {
            val day = sp.optJSONObject(i) ?: continue
            val d = day.optString("date")
            val en = day.optJSONArray("entries") ?: continue
            for (j in 0 until en.length()) {
                val e = en.optJSONObject(j) ?: continue
                val id = str(e, "id_spesa")
                if (id.isNotBlank()) out[id] = d to JSONObject(e.toString())
            }
        }
        return out
    }

    fun syncImpostaVoce(data: String, entry: JSONObject) {
        val d = parseData(data) ?: return
        val id = str(entry, "id_spesa")
        if (id.isBlank()) return
        rimuoviEntry(id)
        inserisciEntry(d, JSONObject(entry.toString()))
    }

    fun syncRimuoviVoce(id: String) {
        rimuoviEntry(id)
    }

    fun syncSalva() = save()

    fun syncBlocco(nome: String): Any {
        return when (nome) {
            "categorie" -> JSONArray((db.optJSONArray("categorie") ?: JSONArray()).toString())
            "memoria_categorie" -> try {
                JSONObject(File(dir, "memoria_categorie.json").readText(Charsets.UTF_8))
            } catch (_: Exception) {
                JSONObject()
            }
            "pianificate" -> JSONObject().put(
                "piani", JSONArray((db.optJSONObject("pianificate")?.optJSONArray("piani") ?: JSONArray()).toString())
            )
            "fairshare" -> {
                val b = db.optJSONObject("fairshare")
                JSONObject()
                    .put("gestore_partecipa", b?.optBoolean("gestore_partecipa", true) ?: true)
                    .put("partecipanti", JSONArray((b?.optJSONArray("partecipanti") ?: JSONArray()).toString()))
            }
            else -> JSONObject((db.optJSONObject(nome) ?: JSONObject()).toString())
        }
    }

    fun syncImpostaBlocco(nome: String, v: Any) {
        when (nome) {
            "memoria_categorie" -> {
                val f = File(dir, "memoria_categorie.json")
                val tmp = File(dir, "memoria_categorie.json.tmp")
                tmp.writeText((v as JSONObject).toString(2), Charsets.UTF_8)
                Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            "fairshare" -> {
                val b = fsBlocco()
                val src = v as JSONObject
                b.put("gestore_partecipa", src.optBoolean("gestore_partecipa", true))
                b.put("partecipanti", src.optJSONArray("partecipanti") ?: JSONArray())
            }
            "pianificate" ->
                db.put("pianificate", JSONObject().put("piani", (v as JSONObject).optJSONArray("piani") ?: JSONArray()))
            else -> db.put(nome, v)
        }
    }

    private fun dateRicorrenza(tipo: String, inizio: LocalDate, n: Int): List<LocalDate> {
        val out = ArrayList<LocalDate>()
        for (i in 0 until n) {
            when (tipo) {
                "Ogni giorno" -> out.add(inizio.plusDays(i.toLong()))
                "Ogni mese" -> {
                    val ym = YearMonth.from(inizio).plusMonths(i.toLong())
                    out.add(ym.atDay(minOf(inizio.dayOfMonth, ym.lengthOfMonth())))
                }
                "Ogni anno" -> {
                    val anno = inizio.year + i
                    out.add(
                        try {
                            LocalDate.of(anno, inizio.monthValue, inizio.dayOfMonth)
                        } catch (_: Exception) {
                            LocalDate.of(anno, inizio.monthValue, 1)
                        }
                    )
                }
            }
        }
        return out
    }

    fun ricorrenze(): List<Ric> {
        val o = db.optJSONObject("ricorrenze") ?: return emptyList()
        val oggi = LocalDate.now()
        val out = ArrayList<Ric>()
        val chiavi = o.keys()
        while (chiavi.hasNext()) {
            val id = chiavi.next()
            val r = o.optJSONObject(id) ?: continue
            val date = ArrayList<LocalDate>()
            r.optJSONArray("date_list")?.let { a ->
                for (i in 0 until a.length()) parseData(a.optString(i))?.let { d -> date.add(d) }
            }
            out.add(
                Ric(
                    id = id,
                    tipo = str(r, "tipo"),
                    n = r.optInt("n", date.size),
                    inizio = parseData(str(r, "data_inizio")),
                    fine = date.lastOrNull(),
                    cat = str(r, "cat"),
                    desc = str(r, "desc"),
                    imp = r.optDouble("imp", 0.0),
                    tipoVoce = str(r, "tipo_voce").ifBlank { "Uscita" },
                    metodo = str(r, "metodo_pagamento"),
                    fatte = date.count { !it.isAfter(oggi) },
                )
            )
        }
        return out.sortedWith(compareBy<Ric> { it.inizio ?: LocalDate.MAX }.thenBy { it.desc })
    }

    fun creaRicorrenza(
        tipo: String,
        n: Int,
        inizio: LocalDate,
        cat: String,
        descr: String,
        imp: Double,
        tipoVoce: String,
        metodo: String,
        hashtag: List<String>,
        conto: String = "",
    ): Int {
        val date = dateRicorrenza(tipo, inizio, n)
        if (date.isEmpty()) return 0
        val id = UUID.randomUUID().toString()
        val desc = if (descr.isNotBlank()) "RIC· $descr ID:${id.take(8)}" else "RIC· ID:${id.take(8)}"
        val importo = Math.round(imp * 100.0) / 100.0
        val ora = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
        for (dt in date) {
            val e = JSONObject()
                .put("id_spesa", nuovoId())
                .put("categoria", cat)
                .put("descrizione", desc)
                .put("importo", importo)
                .put("tipo", tipoVoce)
                .put("id_ricorrenza", id)
                .put("ora", ora)
            setOpt(e, "metodo_pagamento", metodo)
            setOpt(e, "conto", conto)
            if (hashtag.isNotEmpty()) e.put("hashtag", JSONArray(hashtag))
            inserisciEntry(dt, e)
        }
        val lista = JSONArray()
        date.forEach { lista.put(it.format(DF)) }
        oggetto("ricorrenze").put(
            id,
            JSONObject()
                .put("tipo", tipo)
                .put("n", n)
                .put("data_inizio", inizio.format(DF))
                .put("cat", cat)
                .put("desc", desc)
                .put("imp", importo)
                .put("tipo_voce", tipoVoce)
                .put("conto", conto)
                .put("metodo_pagamento", metodo)
                .put("hashtag", JSONArray(hashtag))
                .put("date_list", lista)
        )
        save()
        return date.size
    }

    fun eliminaRicorrenza(id: String): Int {
        var tolte = 0
        val sp = db.getJSONArray("spese")
        var i = sp.length() - 1
        while (i >= 0) {
            val en = sp.optJSONObject(i)?.optJSONArray("entries")
            if (en != null) {
                var j = en.length() - 1
                while (j >= 0) {
                    val e = en.optJSONObject(j)
                    if (e != null && str(e, "id_ricorrenza") == id) {
                        rimuoviPianiPerSpesa(str(e, "id_spesa"))
                        en.remove(j)
                        tolte++
                    }
                    j--
                }
                if (en.length() == 0) sp.remove(i)
            }
            i--
        }
        db.optJSONObject("ricorrenze")?.remove(id)
        save()
        return tolte
    }

    private fun parseIso(s: String): LocalDate? =
        try {
            LocalDate.parse(s)
        } catch (_: Exception) {
            null
        }

    private fun arrayPiani(): JSONArray {
        val blocco = oggetto("pianificate")
        return blocco.optJSONArray("piani") ?: JSONArray().also { blocco.put("piani", it) }
    }

    private fun pulisciPiani(): Boolean {
        val a = db.optJSONObject("pianificate")?.optJSONArray("piani") ?: return false
        val oggi = YearMonth.now()
        var tolti = false
        var i = a.length() - 1
        while (i >= 0) {
            val sc = a.optJSONObject(i)?.let { parseIso(str(it, "data_scadenza")) }
            if (sc != null && !YearMonth.from(sc).isAfter(oggi)) {
                a.remove(i)
                tolti = true
            }
            i--
        }
        return tolti
    }

    private fun rimuoviPianiPerSpesa(idSpesa: String) {
        if (idSpesa.isBlank()) return
        val a = db.optJSONObject("pianificate")?.optJSONArray("piani") ?: return
        var i = a.length() - 1
        while (i >= 0) {
            if (a.optJSONObject(i)?.let { str(it, "id_spesa_collegata") } == idSpesa) a.remove(i)
            i--
        }
    }

    fun piani(): List<Piano> {
        val a = db.optJSONObject("pianificate")?.optJSONArray("piani") ?: return emptyList()
        val out = ArrayList<Piano>()
        for (i in 0 until a.length()) {
            val p = a.optJSONObject(i) ?: continue
            val ini = parseIso(str(p, "inizio")) ?: continue
            val sc = parseIso(str(p, "data_scadenza")) ?: continue
            out.add(
                Piano(
                    id = str(p, "id"),
                    nome = str(p, "nome").ifBlank { str(p, "categoria") },
                    categoria = str(p, "categoria"),
                    importoTotale = p.optDouble("importo_totale", 0.0),
                    inizio = ini,
                    scadenza = sc,
                    idSpesa = str(p, "id_spesa_collegata"),
                    descrizione = str(p, "descrizione"),
                )
            )
        }
        return out.sortedBy { it.scadenza }
    }

    fun mesiCoperti(p: Piano): List<YearMonth> {
        val out = ArrayList<YearMonth>()
        var ym = YearMonth.from(p.inizio)
        val fine = YearMonth.from(p.scadenza)
        while (ym.isBefore(fine)) {
            out.add(ym)
            ym = ym.plusMonths(1)
        }
        return out
    }

    fun quoteMensili(totale: Double, mesi: Int): List<Double> {
        if (mesi <= 0) return emptyList()
        val base = Math.round(totale / mesi * 100.0) / 100.0
        val q = MutableList(mesi) { base }
        val diff = Math.round((totale - base * mesi) * 100.0) / 100.0
        q[mesi - 1] = Math.round((q[mesi - 1] + diff) * 100.0) / 100.0
        return q
    }

    fun quotaMese(p: Piano, ym: YearMonth): Double? {
        val mesi = mesiCoperti(p)
        val idx = mesi.indexOf(ym)
        if (idx < 0) return null
        return quoteMensili(p.importoTotale, mesi.size)[idx]
    }

    fun promemoriaMese(ym: YearMonth): List<Pair<Piano, Double>> =
        piani().mapNotNull { p -> quotaMese(p, ym)?.let { q -> p to q } }

    fun mesiMassimi(d: LocalDate): Int {
        val oggi = LocalDate.now()
        if (!d.isAfter(oggi)) return 0
        val m = (d.year - oggi.year) * 12 + (d.monthValue - oggi.monthValue)
        return minOf(m, 36).coerceAtLeast(0)
    }

    fun pianoPerSpesa(idSpesa: String): Piano? = piani().firstOrNull { it.idSpesa == idSpesa }

    fun creaPiano(idSpesa: String, mesi: Int): Boolean {
        val v = voci().firstOrNull { it.id == idSpesa } ?: return false
        val max = mesiMassimi(v.data)
        if (max < 1 || pianoPerSpesa(idSpesa) != null) return false
        val n = mesi.coerceIn(1, max)
        val inizio = YearMonth.from(v.data).minusMonths(n.toLong()).atDay(1)
        val originale = ricercaEntry(idSpesa)
        val piano = JSONObject()
            .put("id", nuovoId())
            .put("nome", v.descrizione.trim().ifBlank { v.categoria })
            .put("descrizione", v.descrizione.trim())
            .put("categoria", v.categoria)
            .put("conto", originale?.let { str(it, "conto") } ?: "")
            .put("hashtag", JSONArray(v.hashtag))
            .put("metodo_pagamento", v.metodo)
            .put("importo_totale", v.importo)
            .put("data_scadenza", v.data.toString())
            .put("inizio", inizio.toString())
            .put("mesi", n)
            .put("quota", quoteMensili(v.importo, n)[0])
            .put("id_spesa_collegata", idSpesa)
            .put("creato_il", LocalDate.now().toString())
        arrayPiani().put(piano)
        save()
        return true
    }

    private fun ricercaEntry(id: String): JSONObject? {
        val sp = db.optJSONArray("spese") ?: return null
        for (i in 0 until sp.length()) {
            val en = sp.optJSONObject(i)?.optJSONArray("entries") ?: continue
            for (j in 0 until en.length()) {
                val e = en.optJSONObject(j) ?: continue
                if (str(e, "id_spesa") == id) return e
            }
        }
        return null
    }

    fun eliminaPiano(id: String) {
        val a = arrayPiani()
        var i = a.length() - 1
        while (i >= 0) {
            if (a.optJSONObject(i)?.let { str(it, "id") } == id) a.remove(i)
            i--
        }
        save()
    }

    fun ricalcolaPiano(id: String): Boolean {
        val piano = piani().firstOrNull { it.id == id } ?: return false
        val v = voci().firstOrNull { it.id == piano.idSpesa } ?: return false
        val a = arrayPiani()
        for (i in 0 until a.length()) {
            val p = a.optJSONObject(i) ?: continue
            if (str(p, "id") != id) continue
            val n = mesiCoperti(piano).size.coerceAtLeast(1)
            p.put("importo_totale", v.importo)
            p.put("quota", quoteMensili(v.importo, n)[0])
            p.remove("_importo_confermato")
            save()
            return true
        }
        return false
    }

    class Suggerimento(val categoria: String, val tipo: String)

    class SmartCatIdx(val voci: List<Triple<String, Double, Int>>)

    fun smartCatIndice(): SmartCatIdx {
        val unAnnoFa = LocalDate.now().minusDays(365)
        val esistenti = categorie().toSet()
        val mappa = LinkedHashMap<String, Int>()
        for (v in voci()) {
            if (v.data.isBefore(unAnnoFa)) continue
            val cat = v.categoria
            if (cat.isBlank() || cat == "Categoria Rimossa" || cat !in esistenti) continue
            val imp = Math.round(v.importo * 100.0) / 100.0
            val k = cat + "|" + imp
            mappa[k] = (mappa[k] ?: 0) + 1
        }
        val out = ArrayList<Triple<String, Double, Int>>()
        for ((k, f) in mappa) {
            val i = k.lastIndexOf('|')
            val imp = k.substring(i + 1).toDoubleOrNull() ?: continue
            out.add(Triple(k.substring(0, i), imp, f))
        }
        return SmartCatIdx(out)
    }

    fun smartCat(idx: SmartCatIdx, importo: Double): Suggerimento? {
        var migliore = Double.POSITIVE_INFINITY
        var cat: String? = null
        for ((c, imp, freq) in idx.voci) {
            if (abs(imp - importo) < 0.01) {
                val p = -2000.0 - freq
                if (p < migliore) { migliore = p; cat = c }
            }
        }
        if (migliore > -1000.0) {
            for ((c, imp, freq) in idx.voci) {
                val diff = abs(imp - importo)
                val p = when {
                    diff <= 0.05 -> -1000.0 - freq + diff * 10
                    diff <= importo * 0.02 -> diff - freq * 2
                    diff <= smartCatToll -> diff - freq
                    else -> continue
                }
                if (p < migliore) { migliore = p; cat = c }
            }
        }
        val c = cat ?: return null
        return Suggerimento(c, db.optJSONObject("categorie_tipi")?.optString(c, "Uscita")?.ifBlank { "Uscita" } ?: "Uscita")
    }

    fun categorie(): List<String> {
        val a = db.optJSONArray("categorie") ?: return emptyList()
        return (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() }
    }

    fun tipoCategoria(c: String): String =
        db.optJSONObject("categorie_tipi")?.optString(c, "Uscita") ?: "Uscita"

    private fun numMap(k: String, c: String): Double {
        val o = db.optJSONObject(k) ?: return 0.0
        return if (o.isNull(c)) 0.0 else o.optDouble(c, 0.0)
    }

    fun budgetMensile(c: String): Double = numMap("budget_categorie", c)

    fun budgetAnnuo(c: String): Double = numMap("budget_annuale_categorie", c)

    fun classe(c: String): String = db.optJSONObject("categorie_classi")?.optString(c, "") ?: ""

    private fun oggetto(k: String): JSONObject =
        db.optJSONObject(k) ?: JSONObject().also { db.put(k, it) }

    fun salvaCategoria(c: String, mensile: Double, annuo: Double, classe: String) {
        val m = Math.round(mensile * 100.0) / 100.0
        val a = Math.round(annuo * 100.0) / 100.0
        if (m > 0.0) oggetto("budget_categorie").put(c, m) else db.optJSONObject("budget_categorie")?.remove(c)
        if (a > 0.0) oggetto("budget_annuale_categorie").put(c, a)
        else db.optJSONObject("budget_annuale_categorie")?.remove(c)
        if (classe.isNotBlank()) oggetto("categorie_classi").put(c, classe)
        else db.optJSONObject("categorie_classi")?.remove(c)
        save()
    }

    class Quote(
        val entrate: Double,
        val bisogni: Double,
        val desideri: Double,
        val risparmio: Double,
        val nonAssegnate: Double,
    )

    fun calcola503020(periodo: List<Voce>): Quote {
        val netto = HashMap<String, Double>()
        for (v in periodo) {
            val imp = if (v.tipo.trim().equals("Entrata", ignoreCase = true)) -v.importo else v.importo
            netto[v.categoria] = (netto[v.categoria] ?: 0.0) + imp
        }
        var entrate = 0.0
        var b = 0.0
        var d = 0.0
        var r = 0.0
        var na = 0.0
        for ((cat, tot) in netto) {
            val n = Math.round(tot * 100.0) / 100.0
            when (classe(cat)) {
                "Bisogni" -> b += n
                "Desideri" -> d += n
                "Risparmio" -> r += n
                else -> {
                    if (n < 0) {
                        entrate += -n
                    } else if (n > 0) {
                        na += n
                    }
                }
            }
        }
        return Quote(entrate, b, d, r, na)
    }

    fun aggiungiCategoria(nome: String, tipo: String): Boolean {
        val n = nome.trim()
        if (n.isEmpty() || categorie().any { it.equals(n, ignoreCase = true) }) return false
        db.getJSONArray("categorie").put(n)
        db.getJSONObject("categorie_tipi").put(n, tipo)
        save()
        return true
    }

    fun eliminaCategoria(nome: String): Boolean {
        if (voci().any { it.categoria == nome }) return false
        val a = db.getJSONArray("categorie")
        for (i in 0 until a.length()) {
            if (a.optString(i) == nome) {
                a.remove(i)
                break
            }
        }
        for (k in listOf(
            "categorie_tipi", "categorie_classi", "categorie_conti_risparmio",
            "budget_categorie", "budget_annuale_categorie"
        )) {
            db.optJSONObject(k)?.remove(nome)
        }
        save()
        return true
    }

    fun operazioniPerCategoria(): Map<String, Int> = voci().groupingBy { it.categoria }.eachCount()

    private fun rimuoviCategoria(nome: String) {
        val a = db.getJSONArray("categorie")
        for (i in 0 until a.length()) {
            if (a.optString(i) == nome) {
                a.remove(i)
                break
            }
        }
        for (k in listOf(
            "categorie_tipi", "categorie_classi", "categorie_conti_risparmio",
            "budget_categorie", "budget_annuale_categorie"
        )) {
            db.optJSONObject(k)?.remove(nome)
        }
    }

    fun categorieVuote(): List<String> {
        val usate = HashSet<String>()
        voci().forEach { usate.add(it.categoria) }
        db.optJSONObject("ricorrenze")?.let { r ->
            for (id in r.keys()) r.optJSONObject(id)?.let { usate.add(str(it, "cat")) }
        }
        db.optJSONObject("pianificate")?.optJSONArray("piani")?.let { pa ->
            for (i in 0 until pa.length()) pa.optJSONObject(i)?.let { usate.add(str(it, "categoria")) }
        }
        return categorie().filter { it != "Generica" && it !in usate }
    }

    fun eliminaCategorieVuote(nomi: List<String>): Int {
        val ok = categorieVuote().toSet()
        var n = 0
        for (c in nomi) {
            if (c in ok) {
                rimuoviCategoria(c)
                n++
            }
        }
        if (n > 0) save()
        return n
    }

    fun spostaCategorie(origini: List<String>, dest: String, eliminaOrigini: Boolean): Pair<Int, String?> {
        val da = origini.filter { it != dest }.toSet()
        if (da.isEmpty()) return 0 to "Scegli almeno una categoria di origine diversa dalla destinazione"
        if (categorie().none { it == dest }) return 0 to "Categoria di destinazione non trovata"
        val tipoDest = tipoCategoria(dest)
        var n = 0
        val sp = db.optJSONArray("spese")
        if (sp != null) {
            for (i in 0 until sp.length()) {
                val en = sp.optJSONObject(i)?.optJSONArray("entries") ?: continue
                for (j in 0 until en.length()) {
                    val e = en.optJSONObject(j) ?: continue
                    if (str(e, "categoria") in da) {
                        e.put("categoria", dest)
                        e.put("tipo", tipoDest)
                        n++
                    }
                }
            }
        }
        db.optJSONObject("ricorrenze")?.let { r ->
            for (id in r.keys()) {
                val o = r.optJSONObject(id) ?: continue
                if (str(o, "cat") in da) {
                    o.put("cat", dest)
                    if (o.has("tipo")) o.put("tipo", tipoDest)
                }
            }
        }
        db.optJSONObject("pianificate")?.optJSONArray("piani")?.let { pa ->
            for (i in 0 until pa.length()) {
                val o = pa.optJSONObject(i) ?: continue
                if (str(o, "categoria") in da) o.put("categoria", dest)
            }
        }
        if (eliminaOrigini) for (c in da) if (c != "Generica") rimuoviCategoria(c)
        save()
        return n to null
    }

    fun conti(): List<Conto> {
        val a = pf.optJSONArray("conti") ?: return emptyList()
        val out = ArrayList<Conto>()
        for (i in 0 until a.length()) {
            val c = a.optJSONObject(i) ?: continue
            out.add(
                Conto(
                    id = str(c, "id"),
                    nome = str(c, "nome"),
                    tipo = str(c, "tipo").ifBlank { "altro" },
                    saldoIniziale = c.optDouble("saldo", 0.0),
                    principale = c.optBoolean("principale", false),
                    iban = str(c, "iban"),
                    note = str(c, "note"),
                )
            )
        }
        return out
    }

    fun contoPrincipale(): Conto? = conti().firstOrNull { it.principale }

    private val MARKER_SPESE = setOf("__spese__", "Contabilità")

    private fun trasfOk(t: JSONObject) = !t.optBoolean("virtuale", false) &&
        t.optString("da") !in MARKER_SPESE && t.optString("a") !in MARKER_SPESE

    fun trasferimenti(): List<Trasf> {
        val a = pf.optJSONArray("trasferimenti") ?: return emptyList()
        val out = ArrayList<Trasf>()
        for (i in 0 until a.length()) {
            val t = a.optJSONObject(i) ?: continue
            if (!trasfOk(t)) continue
            val d = parseData(str(t, "data")) ?: continue
            out.add(
                Trasf(
                    id = str(t, "id"), data = d, da = str(t, "da"), a = str(t, "a"),
                    importo = t.optDouble("importo", 0.0), note = str(t, "note"),
                    idRicorrenza = str(t, "id_ricorrenza"),
                )
            )
        }
        return out.sortedByDescending { it.data }
    }

    fun saldoEffettivo(c: Conto): Double {
        val oggi = LocalDate.now()
        var saldo = c.saldoIniziale
        for (v in voci()) {
            if (v.data.isAfter(oggi) || v.conto != c.nome) continue
            saldo += if (v.tipo == "Entrata") v.importo else -v.importo
        }
        for (t in trasferimenti()) {
            if (t.data.isAfter(oggi)) continue
            val imp = Math.round(t.importo * 100.0) / 100.0
            if (t.da == c.id) saldo -= imp else if (t.a == c.id) saldo += imp
        }
        return saldo
    }

    private fun nuovoIdPf(prefisso: String, a: JSONArray): String {
        val usati = HashSet<String>()
        for (i in 0 until a.length()) a.optJSONObject(i)?.let { usati.add(str(it, "id")) }
        var n = 1
        while ("$prefisso$n" in usati) n++
        return "$prefisso$n"
    }

    fun salvaConto(
        id: String?, nome: String, tipo: String, saldo: Double,
        principale: Boolean, iban: String, note: String,
    ): String? {
        val n = nome.trim()
        if (n.isEmpty()) return "Inserisci il nome del conto"
        val a = pf.getJSONArray("conti")
        for (i in 0 until a.length()) {
            val c = a.optJSONObject(i) ?: continue
            if (str(c, "nome").equals(n, ignoreCase = true) && str(c, "id") != id) {
                return "Esiste già un conto con questo nome"
            }
        }
        val t = if (tipo in TIPI_CONTO) tipo else "altro"
        val s = Math.round(saldo * 100.0) / 100.0
        var vecchioNome: String? = null
        if (id == null && a.length() >= MAX_CONTI) return "Non puoi avere più di $MAX_CONTI conti"
        if (principale) {
            for (i in 0 until a.length()) a.optJSONObject(i)?.put("principale", false)
        }
        if (id != null) {
            for (i in 0 until a.length()) {
                val c = a.optJSONObject(i) ?: continue
                if (str(c, "id") != id) continue
                vecchioNome = str(c, "nome")
                c.put("nome", n).put("tipo", t).put("saldo", s).put("principale", principale)
                    .put("iban", iban.trim()).put("note", note.trim().take(20))
            }
        } else {
            a.put(
                JSONObject().put("id", nuovoIdPf("c", a)).put("nome", n).put("tipo", t).put("saldo", s)
                    .put("principale", principale).put("iban", iban.trim()).put("note", note.trim().take(20))
            )
        }
        if (vecchioNome != null && vecchioNome != n) {
            val sp = db.getJSONArray("spese")
            for (i in 0 until sp.length()) {
                val en = sp.optJSONObject(i)?.optJSONArray("entries") ?: continue
                for (j in 0 until en.length()) {
                    val e = en.optJSONObject(j) ?: continue
                    if (str(e, "conto") == vecchioNome) e.put("conto", n)
                }
            }
            save()
        }
        savePf()
        return null
    }

    fun eliminaConto(id: String) {
        val t = pf.getJSONArray("trasferimenti")
        var i = t.length() - 1
        while (i >= 0) {
            val o = t.optJSONObject(i)
            if (o != null && (str(o, "da") == id || str(o, "a") == id)) t.remove(i)
            i--
        }
        val a = pf.getJSONArray("conti")
        i = a.length() - 1
        while (i >= 0) {
            if (a.optJSONObject(i)?.let { str(it, "id") } == id) a.remove(i)
            i--
        }
        savePf()
    }

    fun salvaTrasferimento(
        id: String?, data: LocalDate, da: String, a: String, importo: Double, note: String,
    ): String? {
        if (da.isBlank() || a.isBlank() || da == a) return "Scegli due conti diversi"
        if (importo <= 0.0) return "Importo non valido"
        val imp = Math.round(importo * 100.0) / 100.0
        val lista = pf.getJSONArray("trasferimenti")
        if (id != null) {
            for (i in 0 until lista.length()) {
                val t = lista.optJSONObject(i) ?: continue
                if (str(t, "id") != id) continue
                t.put("data", data.format(DF)).put("da", da).put("a", a)
                    .put("importo", imp).put("note", note.trim().take(20))
            }
        } else {
            lista.put(
                JSONObject().put("id", nuovoIdPf("t", lista)).put("data", data.format(DF))
                    .put("da", da).put("a", a).put("importo", imp).put("note", note.trim().take(20))
            )
        }
        savePf()
        return null
    }

    fun eliminaTrasferimento(id: String, intera: Boolean) {
        val lista = pf.getJSONArray("trasferimenti")
        var ric = ""
        for (i in 0 until lista.length()) {
            val t = lista.optJSONObject(i) ?: continue
            if (str(t, "id") == id) ric = str(t, "id_ricorrenza")
        }
        var i = lista.length() - 1
        while (i >= 0) {
            val t = lista.optJSONObject(i)
            if (t != null && (str(t, "id") == id || (intera && ric.isNotEmpty() && str(t, "id_ricorrenza") == ric))) {
                lista.remove(i)
            }
            i--
        }
        savePf()
    }

    fun contoRisparmio(c: String): String =
        db.optJSONObject("categorie_conti_risparmio")?.optString(c, "") ?: ""

    private fun applicaCategoria(
        c: String, tipo: String, mensile: Double, annuo: Double, classe: String, contoId: String,
    ) {
        db.getJSONObject("categorie_tipi").put(c, tipo)
        val m = Math.round(mensile * 100.0) / 100.0
        val a = Math.round(annuo * 100.0) / 100.0
        if (m > 0.0) oggetto("budget_categorie").put(c, m) else db.optJSONObject("budget_categorie")?.remove(c)
        if (a > 0.0) oggetto("budget_annuale_categorie").put(c, a)
        else db.optJSONObject("budget_annuale_categorie")?.remove(c)
        if (classe in CLASSI) oggetto("categorie_classi").put(c, classe)
        else db.optJSONObject("categorie_classi")?.remove(c)
        if (classe == "Risparmio" && contoId.isNotBlank()) oggetto("categorie_conti_risparmio").put(c, contoId)
        else db.optJSONObject("categorie_conti_risparmio")?.remove(c)
    }

    fun creaCategoria(
        nome: String, tipo: String, mensile: Double, annuo: Double, classe: String, contoId: String,
    ): String? {
        val n = nome.trim()
        if (n.isEmpty()) return "Inserisci il nome della categoria"
        if (categorie().any { it.equals(n, ignoreCase = true) }) return "Categoria già esistente"
        db.getJSONArray("categorie").put(n)
        applicaCategoria(n, tipo, mensile, annuo, classe, contoId)
        save()
        return null
    }

    private fun spostaChiave(k: String, vecchio: String, nuovo: String) {
        val o = db.optJSONObject(k) ?: return
        if (o.has(vecchio)) {
            val v = o.get(vecchio)
            o.remove(vecchio)
            o.put(nuovo, v)
        }
    }

    fun modificaCategoria(
        vecchio: String, nuovo: String, tipo: String, mensile: Double, annuo: Double,
        classe: String, contoId: String,
    ): String? {
        val n = nuovo.trim()
        if (n.isEmpty()) return "Inserisci il nome della categoria"
        if (n != vecchio) {
            if (vecchio == "Generica") return "La categoria Generica non si può rinominare"
            if (categorie().any { it.equals(n, ignoreCase = true) && it != vecchio }) {
                return "Categoria già esistente"
            }
            val arr = db.getJSONArray("categorie")
            for (i in 0 until arr.length()) if (arr.optString(i) == vecchio) arr.put(i, n)
            for (k in listOf(
                "categorie_tipi", "categorie_classi", "categorie_conti_risparmio",
                "budget_categorie", "budget_annuale_categorie"
            )) spostaChiave(k, vecchio, n)
            val sp = db.getJSONArray("spese")
            for (i in 0 until sp.length()) {
                val en = sp.optJSONObject(i)?.optJSONArray("entries") ?: continue
                for (j in 0 until en.length()) {
                    val e = en.optJSONObject(j) ?: continue
                    if (str(e, "categoria") == vecchio) e.put("categoria", n)
                }
            }
            db.optJSONObject("ricorrenze")?.let { r ->
                for (id in r.keys()) {
                    val o = r.optJSONObject(id) ?: continue
                    if (str(o, "cat") == vecchio) o.put("cat", n)
                }
            }
            db.optJSONObject("pianificate")?.optJSONArray("piani")?.let { pa ->
                for (i in 0 until pa.length()) {
                    val o = pa.optJSONObject(i) ?: continue
                    if (str(o, "categoria") == vecchio) o.put("categoria", n)
                }
            }
        }
        applicaCategoria(n, tipo, mensile, annuo, classe, contoId)
        save()
        return null
    }

    fun uscite(da: LocalDate, a: LocalDate): List<Voce> =
        voci().filter { it.tipo == "Uscita" && !it.data.isBefore(da) && !it.data.isAfter(a) }

    private const val DEMO = "demo"
    val demoAttiva: Boolean get() = prefs.getBoolean(DEMO, false)

    private fun svuotaDir() {
        dir.listFiles()?.forEach { it.deleteRecursively() }
    }

    private fun ricaricaDopoScambio() {
        targetMese = 0.0
        targetAnno = 0.0
        smartCatOn = true
        smartCatToll = 15.0
        nomeGestore = ""
        checkMese = true
        sogliaGiorniRic = 5
        fmDismiss = null
        barra = BARRA_DEFAULT
        caricaImpostazioni()
        Notif.init(dir)
        load()
        rev++
    }

    fun entraDemo(ctx: Context, zip: File): List<String> {
        if (demoAttiva) throw IllegalStateException("Modalità demo già attiva")
        val reale = File(ctx.filesDir, "db_reale")
        reale.deleteRecursively()
        if (!dir.copyRecursively(reale, overwrite = true)) {
            reale.deleteRecursively()
            throw IllegalStateException("Impossibile salvare i dati attuali: demo annullata")
        }
        try {
            svuotaDir()
            val letti = importa(ctx, Uri.fromFile(zip))
            prefs.edit().putBoolean(DEMO, true).apply()
            ricaricaDopoScambio()
            return letti
        } catch (e: Exception) {
            svuotaDir()
            reale.copyRecursively(dir, overwrite = true)
            reale.deleteRecursively()
            ricaricaDopoScambio()
            throw e
        }
    }

    fun esciDemo(ctx: Context) {
        val reale = File(ctx.filesDir, "db_reale")
        if (!reale.isDirectory) throw IllegalStateException("Copia dei dati veri non trovata: demo non chiusa")
        svuotaDir()
        if (!reale.copyRecursively(dir, overwrite = true)) {
            throw IllegalStateException("Ripristino non riuscito: riprova a uscire dalla demo")
        }
        reale.deleteRecursively()
        prefs.edit().putBoolean(DEMO, false).apply()
        ricaricaDopoScambio()
    }

    fun esporta(ctx: Context, uri: Uri): Int {
        var n = 0
        val os = ctx.contentResolver.openOutputStream(uri)
            ?: throw IllegalStateException("Impossibile scrivere il file")
        os.use { out ->
            ZipOutputStream(out.buffered()).use { zip ->
                for (name in FILES) {
                    val f = File(dir, name)
                    if (!f.exists()) continue
                    zip.putNextEntry(ZipEntry("db/$name"))
                    f.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                    n++
                }
                val sett = File(dir, SETT)
                if (sett.exists()) {
                    zip.putNextEntry(ZipEntry("db/$SETT"))
                    sett.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                    n++
                }

                for (cartella in listOf("documenti", "documenti_personali")) {
                    val radice = File(dir, cartella)
                    if (!radice.isDirectory) continue
                    radice.walkTopDown()
                        .filter { it.isFile && !it.name.endsWith(".tmp") && !it.name.endsWith(".corrotto") }
                        .sortedBy { it.path }
                        .forEach { f ->
                            val rel = f.relativeTo(dir).path.replace('\\', '/')
                            zip.putNextEntry(ZipEntry("db/$rel"))
                            f.inputStream().use { it.copyTo(zip) }
                            zip.closeEntry()
                            n++
                        }
                }
            }
        }
        return n
    }

    fun importa(ctx: Context, uri: Uri): List<String> {
        val trovati = LinkedHashMap<String, ByteArray>()
        val stage = File(ctx.cacheDir, "doc_stage")
        stage.deleteRecursively()
        var nDoc = 0
        try {
            val ins = ctx.contentResolver.openInputStream(uri)
                ?: throw IllegalStateException("Impossibile leggere il file")
            ins.use { raw ->
                ZipInputStream(raw.buffered()).use { zip ->
                    var e: ZipEntry? = zip.nextEntry
                    while (e != null) {
                        if (!e.isDirectory) {
                            val dest = Doc.destinazione(e.name)
                            val base = e.name.substringAfterLast('/').substringAfterLast('\\')
                            if (dest != null) {
                                val out = File(stage, dest)
                                out.parentFile?.mkdirs()
                                out.outputStream().use { o -> zip.copyTo(o) }
                                nDoc++
                            } else if ((base in FILES || base == "config.json") && base !in trovati) {
                                trovati[base] = zip.readBytes()
                            }
                        }
                        zip.closeEntry()
                        e = zip.nextEntry
                    }
                }
            }
            if (trovati.keys.none { it in FILES } && nDoc == 0) {
                throw IllegalStateException("Nello zip non ci sono file di OrbitaCasa (spese_db.json, documenti...)")
            }
            for ((nome, bytes) in trovati) {
                if (nome == "config.json") continue
                val testo = bytes.toString(Charsets.UTF_8)
                try {
                    JSONTokener(testo).nextValue()
                    if (nome == MAIN) {
                        val o = JSONObject(testo)
                        if (o.optJSONArray("spese") == null) throw IllegalStateException("manca \"spese\"")
                    }
                } catch (ex: Exception) {
                    throw IllegalStateException("$nome non è valido: ${ex.message}")
                }
            }
            val prev = File(ctx.filesDir, "db_prev").also { it.mkdirs() }
            for (name in FILES) {
                val f = File(dir, name)
                if (f.exists()) f.copyTo(File(prev, name), overwrite = true)
            }
            val letti = ArrayList<String>()
            for ((nome, bytes) in trovati) {
                if (nome == "config.json") {
                    try {
                        val c = JSONObject(bytes.toString(Charsets.UTF_8))
                        setTarget(c.optDouble("target_mese", 0.0), c.optDouble("target_anno", 0.0))
                        letti.add("target (da config.json)")
                    } catch (_: Exception) {
                    }
                } else {
                    File(dir, nome).writeBytes(bytes)
                    letti.add(nome)
                }
            }
            File(dir, "sync_stato.json").delete()
            if (nDoc > 0) {
                stage.walkTopDown().filter { it.isFile }.forEach { f ->
                    val dst = File(dir, f.relativeTo(stage).path)
                    dst.parentFile?.mkdirs()
                    f.copyTo(dst, overwrite = true)
                }
                letti.add("$nDoc file di documenti")
            }
            return letti
        } finally {
            stage.deleteRecursively()
        }
    }
}
