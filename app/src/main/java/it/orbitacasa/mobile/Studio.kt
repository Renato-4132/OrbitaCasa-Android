package it.orbitacasa.mobile

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

/**
 * Modulo MyBusiness (studio): clienti, agenda, prestazioni, fatture/preventivi, cassa, magazzino, emittente.
 * Legge e scrive gli stessi file JSON del desktop (studio_*.json). I record restano JSONObject cosi'
 * eventuali campi sconosciuti del desktop vengono preservati. Date in formato ISO (aaaa-mm-gg).
 * Nessuna generazione dell'XML FatturaPA.
 */
object Studio {
    const val F_CLI = "studio_clienti.json"
    const val F_APP = "studio_appuntamenti.json"
    const val F_PRE = "studio_prestazioni.json"
    const val F_FAT = "studio_fatture.json"
    const val F_CAS = "studio_cassa.json"
    const val F_MAG = "studio_magazzino.json"
    const val F_EMI = "studio_emittente.json"

    val TIPI_DOC = listOf("Fattura", "Preventivo", "Nota Credito")
    val STATI_DOC = listOf("Bozza", "Emessa", "Pagata", "Scaduta", "Annullata")
    val STATI_APP = listOf("Confermato", "Completato", "Annullato")
    val PAGAMENTI = listOf("Bonifico", "Contanti", "Assegno", "Carta")
    val METODI_CASSA = listOf("Contanti", "Bonifico", "Carta", "POS", "Assegno", "Altro")
    val UNITA = listOf("pz", "kg", "lt", "m", "g", "ml", "m²", "mt")
    val ALIQUOTE = listOf(0, 4, 10, 22)
    val REGIMI = listOf("Ordinario", "Forfettario")
    val ALIQ_SOST = listOf("5% (primi 5 anni)", "15% (ordinaria)")
    val ALIQ_INPS = listOf("26,07% (Gestione Separata)", "24% (Gestione Separata, altra copertura)", "Altra Cassa (non calcolato)")
    val MESI = listOf(
        "", "Gennaio", "Febbraio", "Marzo", "Aprile", "Maggio", "Giugno",
        "Luglio", "Agosto", "Settembre", "Ottobre", "Novembre", "Dicembre",
    )

    private val DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    private lateinit var dir: File
    private val errMap = LinkedHashMap<String, String>()

    fun init(dir: File) {
        this.dir = dir
    }

    fun errori(): List<String> = errMap.values.toList()

    // ---------------------------------------------------------------- utilita'

    fun nuovoId(): String = UUID.randomUUID().toString().substring(0, 8)

    fun numero(s: String): Double? =
        s.trim().replace(',', '.').toDoubleOrNull()?.takeIf { !it.isNaN() && !it.isInfinite() }

    fun iso(d: LocalDate): String = d.toString()

    fun dataIso(s: String): LocalDate? = try {
        if (s.isBlank()) null else LocalDate.parse(s.trim().take(10))
    } catch (_: Exception) {
        null
    }

    fun dmy(iso: String): String = dataIso(iso)?.format(DMY) ?: iso

    /** "gg/mm/aaaa" -> "aaaa-mm-gg"; null se non valida. */
    fun daDmy(s: String): String? = try {
        LocalDate.parse(s.trim(), DMY).toString()
    } catch (_: Exception) {
        null
    }

    fun eur(x: Double): String = "€ " + String.format(Locale.ITALY, "%,.2f", x)

    fun fmtNum(x: Double): String = String.format(Locale.ITALY, "%,.2f", x)

    fun fmtQ(x: Double): String =
        if (x == Math.rint(x) && Math.abs(x) < 1e12) x.toLong().toString()
        else String.format(Locale.ITALY, "%.3f", x).trimEnd('0').trimEnd(',')

    fun s(o: JSONObject, k: String): String = if (o.isNull(k)) "" else o.optString(k, "")

    fun d(o: JSONObject, k: String, def: Double = 0.0): Double {
        if (o.isNull(k)) return def
        val v = o.opt(k)
        return when (v) {
            is Number -> v.toDouble()
            is String -> numero(v) ?: def
            else -> def
        }.let { if (it.isNaN() || it.isInfinite()) def else it }
    }

    fun nomeCliente(c: JSONObject): String = (s(c, "nome") + " " + s(c, "cognome")).trim()

    // ---------------------------------------------------------------- I/O JSON

    private class Letto(val valore: Any?, val corrotto: Boolean)

    private fun file(nome: String) = File(dir, nome)

    private fun leggi(nome: String): Letto {
        errMap.remove(nome)
        val f = file(nome)
        if (!f.isFile || f.length() == 0L) return Letto(null, false)
        return try {
            val v = JSONTokener(f.readText(Charsets.UTF_8)).nextValue()
            if (v is JSONObject || v is JSONArray) Letto(v, false) else throw IllegalStateException("formato")
        } catch (e: Exception) {
            errMap[nome] = "$nome risulta illeggibile o corrotto: nessuna modifica effettuata"
            Letto(null, true)
        }
    }

    private fun scrivi(nome: String, v: Any) {
        val f = file(nome)
        val tmp = File(f.parentFile, "$nome.tmp")
        val testo = when (v) {
            is JSONArray -> v.toString(2)
            is JSONObject -> v.toString(2)
            else -> v.toString()
        }
        tmp.writeText(testo, Charsets.UTF_8)
        Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }

    private fun comeLista(v: Any?): JSONArray = v as? JSONArray ?: JSONArray()

    fun lista(nome: String): List<JSONObject> {
        val a = comeLista(leggi(nome).valore)
        val out = ArrayList<JSONObject>()
        for (i in 0 until a.length()) a.optJSONObject(i)?.let { out.add(it) }
        return out
    }

    fun emittente(): JSONObject = (leggi(F_EMI).valore as? JSONObject) ?: JSONObject()

    private fun modificaLista(nome: String, blocco: (JSONArray) -> String?): String? {
        val l = leggi(nome)
        if (l.corrotto) return errMap[nome]
        val a = comeLista(l.valore)
        val err = blocco(a)
        if (err == null) {
            try {
                scrivi(nome, a)
            } catch (e: Exception) {
                return "Salvataggio non riuscito: " + (e.message ?: "errore sconosciuto")
            }
        }
        return err
    }

    private fun indiceId(a: JSONArray, id: String): Int {
        for (i in 0 until a.length()) if (a.optJSONObject(i)?.let { s(it, "id") } == id) return i
        return -1
    }

    /** Inserisce o sostituisce (per id) un record. Se manca l'id ne assegna uno. */
    fun salva(nome: String, rec: JSONObject): String? {
        if (s(rec, "id").isBlank()) rec.put("id", nuovoId())
        return modificaLista(nome) { a ->
            val i = indiceId(a, s(rec, "id"))
            if (i >= 0) a.put(i, rec) else a.put(rec)
            null
        }
    }

    fun elimina(nome: String, id: String): String? = modificaLista(nome) { a ->
        val out = JSONArray()
        for (i in 0 until a.length()) {
            val o = a.optJSONObject(i)
            if (o == null || s(o, "id") != id) out.put(a.opt(i))
        }
        while (a.length() > 0) a.remove(a.length() - 1)
        for (i in 0 until out.length()) a.put(out.opt(i))
        null
    }

    fun salvaEmittente(dati: Map<String, String>): String? {
        val l = leggi(F_EMI)
        if (l.corrotto) return errMap[F_EMI]
        val o = (l.valore as? JSONObject) ?: JSONObject()
        for ((k, v) in dati) o.put(k, v)
        return try {
            scrivi(F_EMI, o)
            null
        } catch (e: Exception) {
            "Salvataggio non riuscito: " + (e.message ?: "errore sconosciuto")
        }
    }

    fun forfettario(): Boolean = s(emittente(), "regime_fiscale") == "Forfettario"

    // ---------------------------------------------------------------- clienti

    fun clientiOrdinati(): List<JSONObject> = lista(F_CLI).sortedBy { s(it, "cognome").lowercase() + "|" + s(it, "nome").lowercase() }

    fun nomiClienti(): List<String> =
        lista(F_CLI).map { nomeCliente(it) }.filter { it.isNotBlank() }.toSortedSet().toList()

    fun clientePerNome(nome: String): JSONObject? =
        lista(F_CLI).firstOrNull { nomeCliente(it) == nome || s(it, "azienda") == nome }

    fun appuntamentiCliente(nome: String): List<JSONObject> =
        lista(F_APP).filter { s(it, "cliente_nome").trim() == nome }.sortedBy { s(it, "data") }

    fun documentiCliente(nome: String): List<JSONObject> =
        lista(F_FAT).filter { s(it, "cliente_nome").trim() == nome }.sortedByDescending { s(it, "data") }

    fun nomiPrestazioni(): List<String> = lista(F_PRE).map { s(it, "nome") }.filter { it.isNotBlank() }.toSortedSet().toList()

    // ---------------------------------------------------------------- documenti

    class Riga(val desc: String, val qty: Double, val prezzo: Double, val iva: Double)

    fun righe(doc: JSONObject): List<Riga> {
        val a = doc.optJSONArray("righe") ?: return emptyList()
        val out = ArrayList<Riga>()
        for (i in 0 until a.length()) {
            val r = a.optJSONObject(i) ?: continue
            out.add(Riga(s(r, "desc"), d(r, "qty", 1.0), d(r, "prezzo", 0.0), d(r, "iva", 22.0)))
        }
        return out
    }

    class Totali(val imponibile: Double, val iva: Double) {
        val totale: Double get() = imponibile + iva
    }

    fun totali(righe: List<Riga>): Totali =
        Totali(righe.sumOf { it.qty * it.prezzo }, righe.sumOf { it.qty * it.prezzo * it.iva / 100.0 })

    fun totali(doc: JSONObject): Totali = totali(righe(doc))

    /** Imponibile (senza IVA), come usato dal desktop per fatturato e KPI. */
    fun imponibile(doc: JSONObject): Double = righe(doc).sumOf { it.qty * it.prezzo }

    fun riepilogoIva(righe: List<Riga>): List<Pair<Double, Double>> {
        val m = java.util.TreeMap<Double, Double>()
        for (r in righe) m[r.iva] = (m[r.iva] ?: 0.0) + r.qty * r.prezzo * r.iva / 100.0
        return m.entries.map { it.key to it.value }
    }

    fun tipoDoc(doc: JSONObject): String = s(doc, "tipo_doc").ifBlank { "Fattura" }

    fun prossimoNumero(tipo: String, anno: Int = LocalDate.now().year): String {
        val filtrati = lista(F_FAT).filter { tipoDoc(it) == tipo && s(it, "numero").split("/").last() == anno.toString() }
        if (filtrati.isEmpty()) return String.format("%03d/%d", 1, anno)
        val nums = filtrati.mapNotNull { s(it, "numero").split("/").first().toIntOrNull() }
        val n = if (nums.size == filtrati.size) nums.max() + 1 else filtrati.size + 1
        return String.format("%03d/%d", n, anno)
    }

    fun numeriFatture(): List<String> = lista(F_FAT).filter { tipoDoc(it) == "Fattura" }.map { s(it, "numero") }

    class Soglia(val totaleAnno: Double, val soglia: Double, val perc: Double, val anno: String)

    /** Solo per forfettari su Fatture: fatturato dell'anno con questo documento rispetto alla soglia. */
    fun controlloSoglia(rec: JSONObject): Soglia? {
        val emi = emittente()
        if (s(emi, "regime_fiscale") != "Forfettario" || tipoDoc(rec) != "Fattura") return null
        val data = s(rec, "data")
        if (data.length < 4) return null
        val soglia = d(emi, "soglia_forfettario", 85000.0).let { if (it <= 0) 85000.0 else it }
        val anno = data.take(4)
        val questa = imponibile(rec)
        val altre = lista(F_FAT)
            .filter { tipoDoc(it) == "Fattura" && s(it, "data").startsWith(anno) && s(it, "id") != s(rec, "id") }
            .sumOf { imponibile(it) }
        val tot = altre + questa
        return Soglia(tot, soglia, tot / soglia, anno)
    }

    /**
     * Salva un documento. Per un documento NUOVO di tipo Fattura scala dal magazzino le quantita'
     * delle righe il cui nome coincide con un articolo (come nel desktop; qui non per i preventivi).
     */
    fun salvaDocumento(rec: JSONObject, nuovo: Boolean): String? {
        val err = salva(F_FAT, rec)
        if (err != null) return err
        if (nuovo && tipoDoc(rec) == "Fattura") {
            val rr = righe(rec).filter { it.desc.isNotBlank() }
            if (rr.isNotEmpty()) {
                return modificaLista(F_MAG) { a ->
                    for (r in rr) {
                        for (i in 0 until a.length()) {
                            val m = a.optJSONObject(i) ?: continue
                            if (s(m, "nome").trim().lowercase() == r.desc.trim().lowercase()) {
                                m.put("quantita", d(m, "quantita") - r.qty)
                                break
                            }
                        }
                    }
                    null
                }
            }
        }
        return null
    }

    /** Segna la fattura come Pagata e registra l'entrata in cassa. */
    fun incassa(idDoc: String, dataIso: String, metodo: String): String? {
        val doc = lista(F_FAT).firstOrNull { s(it, "id") == idDoc } ?: return "Documento non trovato"
        if (tipoDoc(doc) != "Fattura") return "Puoi incassare solo le Fatture"
        if (s(doc, "stato") == "Pagata") return "Documento già segnato come Pagato"
        val tot = totali(doc).totale
        val mov = JSONObject()
            .put("id", nuovoId())
            .put("data", dataIso)
            .put("tipo", "Entrata")
            .put("importo", tot)
            .put("categoria", "Fatture")
            .put("metodo", metodo)
            .put("note", "Incasso Fattura N° " + s(doc, "numero") + " — " + s(doc, "cliente_nome"))
        val e1 = salva(F_CAS, mov)
        if (e1 != null) return e1
        doc.put("stato", "Pagata")
        return salva(F_FAT, doc)
    }

    // ---------------------------------------------------------------- catalogo (autocompletamento righe)

    class VoceCat(val nome: String, val prezzo: Double, val iva: Int, val origine: String)

    fun catalogo(): List<VoceCat> {
        val out = ArrayList<VoceCat>()
        for (m in lista(F_MAG)) out.add(VoceCat(s(m, "nome"), d(m, "prezzo_unit"), 22, "Prodotto"))
        for (p in lista(F_PRE)) out.add(VoceCat(s(p, "nome"), d(p, "prezzo"), d(p, "iva", 22.0).toInt(), "Prestazione"))
        return out.filter { it.nome.isNotBlank() }.sortedBy { it.nome.lowercase() }
    }

    // ---------------------------------------------------------------- cassa

    class SaldoCassa(val entrate: Double, val uscite: Double) {
        val saldo: Double get() = entrate - uscite
    }

    fun saldoCassa(movs: List<JSONObject> = lista(F_CAS)): SaldoCassa {
        var e = 0.0
        var u = 0.0
        for (m in movs) if (s(m, "tipo") == "Uscita") u += d(m, "importo") else e += d(m, "importo")
        return SaldoCassa(e, u)
    }

    // ---------------------------------------------------------------- magazzino

    fun sottoSoglia(m: JSONObject): Boolean = d(m, "quantita") <= d(m, "soglia")

    // ---------------------------------------------------------------- agenda

    fun appuntamentiGiorno(giorno: String, filtro: String = "Tutti"): List<JSONObject> =
        lista(F_APP).filter { s(it, "data") == giorno && (filtro == "Tutti" || s(it, "stato") == filtro) }
            .sortedBy { s(it, "ora") }

    fun appuntamentiMese(anno: Int, mese: Int, filtro: String = "Tutti"): List<JSONObject> {
        val p = String.format("%04d-%02d", anno, mese)
        return lista(F_APP).filter { s(it, "data").startsWith(p) && (filtro == "Tutti" || s(it, "stato") == filtro) }
            .sortedWith(compareBy({ s(it, "data") }, { s(it, "ora") }))
    }

    /** Normalizza l'ora: vuota -> "", altrimenti HH:MM; null se non valida. */
    fun normOra(t: String): String? {
        val x = t.trim()
        if (x.isEmpty()) return ""
        val p = x.replace('.', ':').split(":")
        if (p.size != 2) return null
        val h = p[0].toIntOrNull() ?: return null
        val m = p[1].toIntOrNull() ?: return null
        if (h !in 0..23 || m !in 0..59) return null
        return String.format("%02d:%02d", h, m)
    }

    private const val CLI_SCAD = "Scadenza Fiscale"

    fun scadenzeProssime(oggi: LocalDate, n: Int = 6): List<JSONObject> =
        lista(F_APP).filter { s(it, "cliente_nome") == CLI_SCAD && s(it, "data") >= iso(oggi) }
            .sortedBy { s(it, "data") }.take(n)

    /** Aggiunge le scadenze fiscali dell'anno (come il desktop). Ritorna il numero aggiunto o un errore. */
    fun generaScadenze(anno: Int): Pair<Int, String?> {
        val regime = s(emittente(), "regime_fiscale").ifBlank { "Ordinario" }
        val sc = if (regime == "Forfettario") listOf(
            Triple(30, 6, "FORF-SALDO" to "Saldo Imposta Sostitutiva/INPS anno prec. + 1° acconto"),
            Triple(30, 11, "FORF-2ACC" to "2° acconto Imposta Sostitutiva e INPS"),
        ) else listOf(
            Triple(16, 3, "ORD-IVA-Q4" to "Liquidazione IVA IV trimestre anno prec."),
            Triple(16, 5, "ORD-IVA-Q1" to "Liquidazione IVA I trimestre"),
            Triple(20, 8, "ORD-IVA-Q2" to "Liquidazione IVA II trimestre"),
            Triple(16, 11, "ORD-IVA-Q3" to "Liquidazione IVA III trimestre"),
            Triple(30, 6, "ORD-IRPEF-SALDO" to "Saldo IRPEF anno prec. + 1° acconto"),
            Triple(30, 11, "ORD-IRPEF-2ACC" to "2° acconto IRPEF"),
            Triple(27, 12, "ORD-IVA-ACC" to "Acconto IVA"),
        )
        var aggiunte = 0
        val err = modificaLista(F_APP) { a ->
            for ((g, m, ct) in sc) {
                val tag = "[FISC-${ct.first}-$anno]"
                var presente = false
                for (i in 0 until a.length()) {
                    val x = a.optJSONObject(i) ?: continue
                    if (s(x, "cliente_nome") == CLI_SCAD && s(x, "note").contains(tag)) presente = true
                }
                if (presente) continue
                val data = try { LocalDate.of(anno, m, g) } catch (_: Exception) { continue }
                a.put(
                    JSONObject().put("id", nuovoId()).put("data", iso(data)).put("ora", "09:00")
                        .put("cliente_nome", CLI_SCAD).put("prestazione", ct.second).put("stato", "Confermato")
                        .put("note", "$tag Vedi Dashboard > Stima Tasse per l'importo aggiornato.")
                )
                aggiunte++
            }
            null
        }
        return aggiunte to err
    }

    // ---------------------------------------------------------------- dashboard

    class Dash(
        val fatMese: Double, val fatAnno: Double, val daIncassare: Double, val nAperte: Int,
        val saldo: Double, val appOggi: Int, val appSett: Int, val nSottoSoglia: Int,
    )

    private fun fatturati(): List<JSONObject> = lista(F_FAT).filter { s(it, "stato") == "Emessa" || s(it, "stato") == "Pagata" }

    fun dashboard(oggi: LocalDate = LocalDate.now()): Dash {
        val fat = lista(F_FAT)
        val emesse = fat.filter { s(it, "stato") == "Emessa" || s(it, "stato") == "Pagata" }
        val pm = String.format("%04d-%02d", oggi.year, oggi.monthValue)
        val fm = emesse.filter { s(it, "data").take(7) == pm }.sumOf { imponibile(it) }
        val fa = emesse.filter { s(it, "data").take(4) == oggi.year.toString() }.sumOf { imponibile(it) }
        val aperte = fat.filter { s(it, "stato") == "Emessa" }
        val app = lista(F_APP).filter { s(it, "stato") != "Annullato" }
        val appOggi = app.count { s(it, "data") == iso(oggi) }
        val fine = oggi.plusDays(6)
        val appSett = app.count { val dt = dataIso(s(it, "data")); dt != null && !dt.isBefore(oggi) && !dt.isAfter(fine) }
        return Dash(
            fm, fa, aperte.sumOf { imponibile(it) }, aperte.size, saldoCassa().saldo,
            appOggi, appSett, lista(F_MAG).count { sottoSoglia(it) },
        )
    }

    class Mese(val anno: Int, val mese: Int, val valore: Double) {
        val etichetta: String get() = String.format("%02d/%02d", mese, anno % 100)
    }

    fun ultimiMesi(oggi: LocalDate = LocalDate.now(), n: Int = 6): List<Mese> {
        val emesse = fatturati()
        val out = ArrayList<Mese>()
        for (i in n - 1 downTo 0) {
            val dt = oggi.withDayOfMonth(1).minusMonths(i.toLong())
            val p = String.format("%04d-%02d", dt.year, dt.monthValue)
            out.add(Mese(dt.year, dt.monthValue, emesse.filter { s(it, "data").take(7) == p }.sumOf { imponibile(it) }))
        }
        return out
    }

    class Tasse(
        val coeff: Double, val reddito: Double, val aliqSost: Double, val imposta: Double,
        val aliqInps: Double?, val inps: Double?, val totale: Double,
    )

    /** Stima tasse del regime forfettario (come la dashboard desktop). */
    fun stimaTasse(fatAnno: Double, emi: JSONObject = emittente()): Tasse? {
        if (s(emi, "regime_fiscale") != "Forfettario") return null
        val coeff = (numero(s(emi, "coefficiente_redditivita").ifBlank { "78" }) ?: 78.0) / 100.0
        val reddito = fatAnno * coeff
        val aliqSost = if (s(emi, "aliquota_sostitutiva").ifBlank { "15% (ordinaria)" }.startsWith("5")) 0.05 else 0.15
        val imposta = reddito * aliqSost
        val inpsStr = s(emi, "aliquota_inps").ifBlank { "26,07% (Gestione Separata)" }
        val aliqInps = when {
            inpsStr.startsWith("26") -> 0.2607
            inpsStr.startsWith("24") -> 0.24
            else -> null
        }
        val inps = aliqInps?.let { reddito * it }
        return Tasse(coeff, reddito, aliqSost, imposta, aliqInps, inps, imposta + (inps ?: 0.0))
    }

    fun sogliaForfettario(emi: JSONObject = emittente()): Double = d(emi, "soglia_forfettario", 85000.0).let { if (it <= 0) 85000.0 else it }
}
