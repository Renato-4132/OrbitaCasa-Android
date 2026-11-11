package it.orbitacasa.mobile

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

class SchedaCampo(val k: String, val l: String, val t: String)

class SchedaExtra(val k: String, val l: String, val unita: String)

class SchedaScad(val stato: String, val testo: String, val giorni: Int?)

class SchedaMov(
    val id: String, val data: LocalDate?, val cat: String, val desc: String, val imp: Double,
    val tipo: String, val extra: Map<String, Double>, val esportato: Boolean,
) {
    val km: Double get() = extra["km"] ?: 0.0
    val litri: Double get() = extra["litri"] ?: 0.0
    val entrata: Boolean get() = tipo == "Entrata"
}

class SchedaCfg(
    val chiave: String, val file: String, val radice: String, val mov: String,
    val emoji: String, val titolo: String, val sing: String, val art: String, val plur: String,
    val max: Int, val catExport: String, val tag: String, val tagExp: String, val contoKey: String,
    val tipoMov: Boolean, val nomeHint: String,
    val categorie: List<String>, val campi: List<SchedaCampo>,
    val scadenze: List<Pair<String, String>>, val extra: List<SchedaExtra>,
    val nuovo: List<Pair<String, Any>>,
)

class Scheda(val cfg: SchedaCfg) {
    val CAMPI get() = cfg.campi
    val SCADENZE get() = cfg.scadenze
    val CATEGORIE get() = cfg.categorie

    private val DF = DateTimeFormatter.ofPattern("dd-MM-yyyy")
    private lateinit var file: File

    var errore: String? = null
        private set

    private val cap get() = cfg.sing.replaceFirstChar { it.uppercase() }

    fun init(dir: File) {
        file = File(dir, cfg.file)
    }

    private fun vuota() = JSONObject().put(cfg.radice, JSONArray())

    private fun leggiRadice(): JSONObject {
        errore = null
        if (!file.exists() || file.length() == 0L) return vuota()
        return try {
            val o = JSONObject(file.readText(Charsets.UTF_8))
            if (o.optJSONArray(cfg.radice) == null) throw IllegalStateException("formato non riconosciuto")
            o
        } catch (e: Exception) {
            errore = "${cfg.file} risulta illeggibile o corrotto: nessuna modifica effettuata (${e.message})"
            vuota()
        }
    }

    private fun scrivi(root: JSONObject) {
        val tmp = File(file.parentFile, "${cfg.file}.tmp")
        tmp.writeText(root.toString(2), Charsets.UTF_8)
        Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }

    private fun idNuovo() = UUID.randomUUID().toString()

    fun elenco(): List<JSONObject> {
        val root = leggiRadice()
        if (errore != null) return emptyList()
        val a = root.getJSONArray(cfg.radice)
        var cambiato = false
        val out = ArrayList<JSONObject>()
        for (i in 0 until a.length()) {
            val v = a.optJSONObject(i) ?: continue
            val m = v.optJSONArray(cfg.mov)
            if (m != null) {
                for (j in 0 until m.length()) {
                    val x = m.optJSONObject(j) ?: continue
                    if (x.optString("id").isBlank()) { x.put("id", idNuovo()); cambiato = true }
                }
            }
            out.add(v)
        }
        if (cambiato) try { scrivi(root) } catch (_: Exception) { }
        return out
    }

    fun per(id: String): JSONObject? = elenco().firstOrNull { it.optString("id") == id }

    private fun conScheda(id: String, blocco: (JSONObject) -> String?): String? {
        val root = leggiRadice()
        errore?.let { return it }
        val a = root.getJSONArray(cfg.radice)
        for (i in 0 until a.length()) {
            val v = a.optJSONObject(i) ?: continue
            if (v.optString("id") == id) {
                val err = blocco(v)
                if (err == null) scrivi(root)
                return err
            }
        }
        return "$cap non trovato."
    }

    fun num(o: JSONObject, k: String): Double {
        val v = o.opt(k)
        return when (v) {
            is Number -> v.toDouble()
            is String -> v.trim().replace(',', '.').toDoubleOrNull() ?: 0.0
            else -> 0.0
        }
    }

    fun fmtN(v: Double): String =
        if (v == Math.floor(v) && Math.abs(v) < 1e12) v.toLong().toString()
        else String.format(Locale.ITALY, "%.2f", v)

    fun parse(s: String?): LocalDate? =
        try { LocalDate.parse((s ?: "").trim().replace('/', '-'), DF) } catch (_: Exception) { null }

    fun dataIt(d: LocalDate): String = d.format(DF)

    fun scadenza(s: String?): SchedaScad {
        val d = parse(s) ?: return SchedaScad("nd", "Non impostata", null)
        val g = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), d).toInt()
        return when {
            g < 0 -> SchedaScad("rosso", "SCADUTA da ${-g} gg (${dataIt(d)})", g)
            g == 0 -> SchedaScad("rosso", "Scade OGGI (${dataIt(d)})", g)
            else -> SchedaScad(if (g <= 15) "rosso" else if (g <= 45) "ambra" else "verde", "tra $g gg (${dataIt(d)})", g)
        }
    }

    fun movimenti(v: JSONObject): List<SchedaMov> {
        val a = v.optJSONArray(cfg.mov) ?: return emptyList()
        val out = ArrayList<SchedaMov>()
        for (i in 0 until a.length()) {
            val m = a.optJSONObject(i) ?: continue
            val tipo = if (cfg.tipoMov && m.optString("tipo").equals("entrata", ignoreCase = true)) "Entrata" else "Uscita"
            out.add(
                SchedaMov(
                    m.optString("id"), parse(m.optString("data")), m.optString("categoria"),
                    m.optString("descrizione"), num(m, "importo"), tipo,
                    cfg.extra.associate { it.k to num(m, it.k) },
                    !m.isNull("esportato") && m.optString("esportato").isNotBlank(),
                )
            )
        }
        return out.sortedByDescending { it.data ?: LocalDate.MIN }
    }

    fun sottotitolo(v: JSONObject): List<String> = when (cfg.chiave) {
        "autopark" -> listOfNotNull(
            v.optString("modello").takeIf { it.isNotBlank() },
            v.optString("targa").takeIf { it.isNotBlank() },
            num(v, "km_attuali").takeIf { it > 0 }?.let { fmtN(it) + " km" },
        )
        "petcare" -> listOfNotNull(
            v.optString("razza").takeIf { it.isNotBlank() },
            num(v, "peso_attuale").takeIf { it > 0 }?.let { fmtN(it) + " kg" },
        )
        else -> listOfNotNull(
            v.optString("indirizzo").takeIf { it.isNotBlank() },
            v.optString("inquilino").takeIf { it.isNotBlank() },
        )
    }

    fun info(v: JSONObject): List<String> {
        val movs = movimenti(v)
        val tot = movs.sumOf { it.imp }
        val righe = ArrayList<String>()
        when (cfg.chiave) {
            "autopark" -> {
                val ki = num(v, "km_iniziale")
                val ka = num(v, "km_attuali")
                if (ka - ki > 0 && tot != 0.0) {
                    righe.add("Costo al km: € " + String.format(Locale.ITALY, "%,.3f", tot / (ka - ki)))
                }
                val rif = movs.filter { it.cat == "Carburante" && it.km > 0 && it.litri > 0 }
                    .map { it.km to it.litri }.sortedBy { it.first }
                val consumi = rif.zipWithNext().filter { (a, b) -> b.first > a.first && b.second > 0 }
                    .map { (a, b) -> b.second / (b.first - a.first) * 100 }
                if (consumi.isNotEmpty()) {
                    righe.add("Consumo medio: " + String.format(Locale.ITALY, "%,.1f", consumi.average()) + " l/100 km")
                }
                val tg = num(v, "prossimo_tagliando_km")
                if (tg > 0 && ka > 0) {
                    righe.add("Prossimo tagliando: ${fmtN(tg)} km (mancano ${fmtN(maxOf(tg - ka, 0.0))} km)")
                }
            }
            "petcare" -> {
                val nascita = parse(v.optString("data_nascita"))
                if (nascita != null && tot != 0.0) {
                    val giorni = java.time.temporal.ChronoUnit.DAYS.between(nascita, LocalDate.now())
                    if (giorni > 0) {
                        righe.add("Costo medio mensile: € " + String.format(Locale.ITALY, "%,.2f", tot / (giorni / 30.44)))
                    }
                }
                val rif = movs.filter { it.cat == "Alimentazione" && it.data != null && (it.extra["quantita_cibo"] ?: 0.0) > 0 }
                    .map { it.data!! to (it.extra["quantita_cibo"] ?: 0.0) }.sortedBy { it.first }
                val consumi = rif.zipWithNext().mapNotNull { (a, b) ->
                    val g = java.time.temporal.ChronoUnit.DAYS.between(a.first, b.first)
                    if (g > 0 && b.second > 0) b.second / g * 30.44 else null
                }
                if (consumi.isNotEmpty()) {
                    righe.add("Consumo medio cibo: " + String.format(Locale.ITALY, "%,.1f", consumi.average()) + " kg/mese")
                }
            }
            else -> {
                val can = num(v, "canone")
                if (can > 0) righe.add("Canone annuo: € " + String.format(Locale.ITALY, "%,.2f", can * 12))
            }
        }
        return righe
    }

    fun nuovo(nome: String): String? {
        val n = nome.trim().take(22)
        if (n.isEmpty()) return "Inserisci un nome."
        val root = leggiRadice()
        errore?.let { return it }
        val a = root.getJSONArray(cfg.radice)
        if (a.length() >= cfg.max) return "Limite raggiunto: massimo ${cfg.max} ${cfg.plur}."
        val v = JSONObject().put("id", idNuovo()).put("nome", n)
        for ((k, x) in cfg.nuovo) v.put(k, x)
        v.put("categorie", JSONArray(cfg.categorie)).put(cfg.mov, JSONArray())
        a.put(v)
        scrivi(root)
        return null
    }

    fun salvaAna(id: String, valori: Map<String, String>, conti: List<String>): String? =
        conScheda(id) { v ->
            val nuovi = JSONObject()
            for (c in cfg.campi) {
                val raw = (valori[c.k] ?: continue).trim()
                when (c.t) {
                    "date" -> if (raw.isEmpty()) nuovi.put(c.k, "") else {
                        val d = parse(raw) ?: return@conScheda "Data non valida: ${c.l}."
                        nuovi.put(c.k, dataIt(d))
                    }
                    "num" -> if (raw.isEmpty()) nuovi.put(c.k, 0.0) else {
                        val n = raw.replace(',', '.').toDoubleOrNull()
                        if (n == null || n < 0) return@conScheda "Valore non valido: ${c.l}."
                        nuovi.put(c.k, n)
                    }
                    "conto" -> {
                        if (raw.isNotEmpty() && raw != "(nessuno)" && raw !in conti) return@conScheda "Conto non valido."
                        nuovi.put(c.k, if (raw.isEmpty()) "(nessuno)" else raw)
                    }
                    else -> {
                        val t = if (c.k == "nome") raw.take(22) else raw
                        if (c.k == "nome" && t.isEmpty()) return@conScheda "Il nome è obbligatorio."
                        nuovi.put(c.k, t)
                    }
                }
            }
            for (k in nuovi.keys()) v.put(k, nuovi.get(k))
            null
        }

    fun elimina(id: String): String? {
        val root = leggiRadice()
        errore?.let { return it }
        val a = root.getJSONArray(cfg.radice)
        val nuovo = JSONArray()
        var trovato = false
        for (i in 0 until a.length()) {
            val v = a.optJSONObject(i)
            if (v != null && v.optString("id") == id) trovato = true else if (v != null) nuovo.put(v)
        }
        if (!trovato) return "$cap non trovato."
        root.put(cfg.radice, nuovo)
        scrivi(root)
        return null
    }

    fun movSalva(
        idScheda: String, idMov: String?, data: LocalDate, cat: String, desc: String,
        imp: Double, tipo: String, extra: Map<String, Double?>,
    ): String? {
        if (imp <= 0) return "Importo mancante o non valido."
        if (cat.isBlank()) return "Categoria obbligatoria."
        return conScheda(idScheda) { v ->
            val cats = v.optJSONArray("categorie") ?: JSONArray(cfg.categorie).also { v.put("categorie", it) }
            if ((0 until cats.length()).none { cats.optString(it) == cat }) cats.put(cat)
            val lista = v.optJSONArray(cfg.mov) ?: JSONArray().also { v.put(cfg.mov, it) }
            var riga: JSONObject? = null
            if (idMov != null) {
                for (i in 0 until lista.length()) {
                    val m = lista.optJSONObject(i)
                    if (m != null && m.optString("id") == idMov) riga = m
                }
                if (riga == null) return@conScheda "Movimento non trovato."
            }
            val r = riga ?: JSONObject().put("id", idNuovo()).also { lista.put(it) }
            r.put("data", dataIt(data)).put("categoria", cat).put("descrizione", desc).put("importo", imp)
            for (e in cfg.extra) {
                val x = extra[e.k]
                r.put(e.k, if (x != null && x > 0) x else "")
            }
            if (cfg.tipoMov) r.put("tipo", if (tipo == "Entrata") "Entrata" else "Uscita")
            val km = extra["km"]
            if (cfg.chiave == "autopark" && km != null && km > num(v, "km_attuali")) v.put("km_attuali", km)
            val peso = extra["peso"]
            if (cfg.chiave == "petcare" && peso != null && peso > 0) v.put("peso_attuale", peso)
            null
        }
    }

    fun movElimina(idScheda: String, ids: Set<String>): String? =
        conScheda(idScheda) { v ->
            val lista = v.optJSONArray(cfg.mov) ?: JSONArray()
            val nuova = JSONArray()
            for (i in 0 until lista.length()) {
                val m = lista.optJSONObject(i) ?: continue
                if (m.optString("id") !in ids) nuova.put(m)
            }
            v.put(cfg.mov, nuova)
            null
        }

    fun smarca(idScheda: String, ids: Set<String>): String? {
        var n = 0
        val err = conScheda(idScheda) { v ->
            val lista = v.optJSONArray(cfg.mov) ?: JSONArray()
            for (i in 0 until lista.length()) {
                val m = lista.optJSONObject(i) ?: continue
                if (m.optString("id") in ids && !m.isNull("esportato") && m.optString("esportato").isNotBlank()) {
                    m.put("esportato", JSONObject.NULL)
                    n++
                }
            }
            if (n == 0) "Le voci selezionate non risultano esportate." else null
        }
        return err
    }

    fun esporta(idScheda: String, ids: Set<String>, righe: Boolean): Pair<String?, String?> {
        var msg: String? = null
        val err = conScheda(idScheda) { v ->
            val lista = v.optJSONArray(cfg.mov) ?: JSONArray()
            val sel = ArrayList<JSONObject>()
            for (i in 0 until lista.length()) {
                val m = lista.optJSONObject(i) ?: continue
                if (m.optString("id") in ids && (m.isNull("esportato") || m.optString("esportato").isBlank())) sel.add(m)
            }
            if (sel.isEmpty()) return@conScheda "Nessuna voce da esportare tra quelle selezionate."
            fun tipoDi(m: JSONObject) =
                if (cfg.tipoMov && m.optString("tipo").equals("entrata", ignoreCase = true)) "Entrata" else "Uscita"
            val nome = v.optString("nome").ifBlank { cap }
            var conto = v.optString(cfg.contoKey)
            if (conto == "(nessuno)") conto = ""
            val oggi = LocalDate.now()
            Repo.aggiungiCategoria(cfg.catExport, "Uscita")
            if (!righe) {
                val saldo = sel.sumOf { if (tipoDi(it) == "Entrata") num(it, "importo") else -num(it, "importo") }
                if (saldo == 0.0) return@conScheda "Saldo zero, nessun movimento esportato."
                val eid = idNuovo()
                val tipoSaldo = if (saldo >= 0) "Entrata" else "Uscita"
                val desc = if (sel.size <= 1) "Saldo $nome" else "Saldo $nome (${sel.size} mov.)"
                Repo.salvaVoce(null, oggi, cfg.catExport, desc, Math.abs(saldo), tipoSaldo, "", listOf(cfg.tag, cfg.tagExp + eid), conto)
                sel.forEach { it.put("esportato", eid) }
                msg = "Saldo $nome ($tipoSaldo € ${String.format(Locale.ITALY, "%,.2f", Math.abs(saldo))}) esportato"
            } else {
                var creati = 0
                for (m in sel) {
                    val imp = num(m, "importo")
                    if (imp == 0.0) continue
                    val eid = idNuovo()
                    val c = m.optString("categoria")
                    val d = m.optString("descrizione")
                    var desc = if (c.isNotBlank()) "$nome: $c" else nome
                    if (d.isNotBlank()) desc += " - $d"
                    Repo.salvaVoce(null, oggi, cfg.catExport, desc, imp, tipoDi(m), "", listOf(cfg.tag, cfg.tagExp + eid), conto)
                    m.put("esportato", eid)
                    creati++
                }
                if (creati == 0) return@conScheda "Nessuna voce valida da esportare."
                msg = "$creati voce/i di $nome esportate singolarmente"
            }
            null
        }
        return msg to err
    }
}

val Park = Scheda(
    SchedaCfg(
        chiave = "autopark", file = "veicoli.json", radice = "veicoli", mov = "movimenti",
        emoji = "🚗", titolo = "AutoPark", sing = "veicolo", art = "il ", plur = "veicoli",
        max = 7, catExport = "AutoPark", tag = "#veicoli", tagExp = "#vexp:", contoKey = "conto_bancario",
        tipoMov = false, nomeHint = "Nome / targa (max 22 caratteri)",
        categorie = listOf(
            "Assicurazione", "Bollo", "Carburante", "Gomme", "Lavaggio", "Manutenzione Ordinaria",
            "Manutenzione Straordinaria", "Multe", "Parcheggio/Pedaggi", "Revisione", "Tagliando", "Varie",
        ),
        campi = listOf(
            SchedaCampo("nome", "Nome / Targa", "text"), SchedaCampo("modello", "Modello", "text"),
            SchedaCampo("targa", "Targa", "text"), SchedaCampo("km_iniziale", "Km iniziali", "num"),
            SchedaCampo("km_attuali", "Km attuali", "num"),
            SchedaCampo("data_immatricolazione", "Immatricolazione (gg-mm-aaaa)", "date"),
            SchedaCampo("scad_bollo", "Scadenza bollo (gg-mm-aaaa)", "date"),
            SchedaCampo("scad_assicurazione", "Scadenza assicurazione (gg-mm-aaaa)", "date"),
            SchedaCampo("scad_revisione", "Scadenza revisione (gg-mm-aaaa)", "date"),
            SchedaCampo("prossimo_tagliando_km", "Prossimo tagliando (km)", "num"),
            SchedaCampo("note", "Note", "text"), SchedaCampo("conto_bancario", "Conto bancario", "conto"),
        ),
        scadenze = listOf("scad_bollo" to "Bollo", "scad_assicurazione" to "Assicurazione", "scad_revisione" to "Revisione"),
        extra = listOf(
            SchedaExtra("km", "Km (facoltativo)", "km"),
            SchedaExtra("litri", "Litri (facoltativo, per il consumo)", "l"),
        ),
        nuovo = listOf(
            "modello" to "", "targa" to "", "km_iniziale" to 0.0, "km_attuali" to 0.0,
            "data_immatricolazione" to "", "scad_bollo" to "", "scad_assicurazione" to "",
            "scad_revisione" to "", "prossimo_tagliando_km" to 0.0, "note" to "",
        ),
    )
)

val Immo = Scheda(
    SchedaCfg(
        chiave = "immobili", file = "immobil.json", radice = "immobili", mov = "spese",
        emoji = "🏢", titolo = "ImmoBil", sing = "immobile", art = "l'", plur = "immobili",
        max = 7, catExport = "ImmoBil", tag = "#immobili", tagExp = "#immexp:", contoKey = "conto",
        tipoMov = true, nomeHint = "Nome (max 22 caratteri)",
        categorie = listOf(
            "Acconto Spese Condominiali", "Assicurazione Immobile", "Canone Locazione",
            "Cedolare Secca / IRPEF", "Conguaglio Spese", "Deposito Cauzionale",
            "Elettrodomestici e Arredi", "Imposta IMU", "Interessi Mutuo Casa",
            "Manutenzione Caldaia/Clima", "Provvigioni Agenzia", "Quota Registrazione Contratto",
            "Riparazioni Elettriche", "Riparazioni Idrauliche", "Ristrutturazioni (Detraibili)",
            "Spese Condominiali Inquilino", "Spese Condominiali Proprietario",
            "Spese Legali e Contratti", "Tassa TARI (Rifiuti)", "Utenze (Luce/Gas/Acqua)", "Varie",
        ),
        campi = listOf(
            SchedaCampo("nome", "Nome", "text"), SchedaCampo("indirizzo", "Indirizzo", "text"),
            SchedaCampo("inquilino", "Inquilino", "text"), SchedaCampo("canone", "Canone €/mese", "num"),
            SchedaCampo("scadenza_contratto", "Scadenza contratto (gg-mm-aaaa)", "date"),
            SchedaCampo("note", "Note", "text"), SchedaCampo("conto", "Conto", "conto"),
        ),
        scadenze = listOf("scadenza_contratto" to "Contratto"),
        extra = emptyList(),
        nuovo = listOf(
            "indirizzo" to "", "inquilino" to "", "canone" to 0.0, "scadenza_contratto" to "", "note" to "",
        ),
    )
)

val Pet = Scheda(
    SchedaCfg(
        chiave = "petcare", file = "animali.json", radice = "animali", mov = "movimenti",
        emoji = "🐾", titolo = "PetCare", sing = "animale", art = "l'", plur = "animali",
        max = 7, catExport = "PetCare", tag = "#animali", tagExp = "#aexp:", contoKey = "conto_bancario",
        tipoMov = false, nomeHint = "Nome (max 22 caratteri)",
        categorie = listOf(
            "Alimentazione", "Veterinario", "Vaccinazioni", "Antiparassitari", "Toelettatura",
            "Farmaci", "Accessori", "Pensione/Dog-sitter", "Assicurazione", "Varie",
        ),
        campi = listOf(
            SchedaCampo("nome", "Nome", "text"), SchedaCampo("razza", "Razza / Specie", "text"),
            SchedaCampo("microchip", "Microchip", "text"), SchedaCampo("peso_iniziale", "Peso iniziale (kg)", "num"),
            SchedaCampo("peso_attuale", "Peso attuale (kg)", "num"),
            SchedaCampo("data_nascita", "Data di nascita (gg-mm-aaaa)", "date"),
            SchedaCampo("scad_vaccino", "Scadenza vaccino (gg-mm-aaaa)", "date"),
            SchedaCampo("scad_antiparassitario", "Scadenza antiparassitario (gg-mm-aaaa)", "date"),
            SchedaCampo("scad_assicurazione", "Scadenza assicurazione (gg-mm-aaaa)", "date"),
            SchedaCampo("prossimo_controllo", "Prossimo controllo (gg-mm-aaaa)", "date"),
            SchedaCampo("note", "Note", "text"), SchedaCampo("conto_bancario", "Conto bancario", "conto"),
        ),
        scadenze = listOf(
            "scad_vaccino" to "Vaccinazioni", "scad_antiparassitario" to "Antiparassitario",
            "scad_assicurazione" to "Assicurazione", "prossimo_controllo" to "Controllo",
        ),
        extra = listOf(
            SchedaExtra("peso", "Peso (kg, facoltativo)", "kg"),
            SchedaExtra("quantita_cibo", "Cibo (kg, facoltativo, per il consumo)", "kg"),
        ),
        nuovo = listOf(
            "razza" to "", "microchip" to "", "peso_iniziale" to 0.0, "peso_attuale" to 0.0,
            "data_nascita" to "", "scad_vaccino" to "", "scad_antiparassitario" to "",
            "scad_assicurazione" to "", "prossimo_controllo" to "", "note" to "",
        ),
    )
)
