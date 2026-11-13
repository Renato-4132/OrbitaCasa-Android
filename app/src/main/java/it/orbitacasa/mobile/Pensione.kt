package it.orbitacasa.mobile

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

object Pensione {
    const val FILE = "fondo_pensione.json"

    val TIPI_VERSAMENTO = listOf("TFR", "Lavoratore", "Datore di Lavoro", "Volontario")
    val TIPI_RISCATTO = listOf("Anticipazione", "Riscatto Parziale", "Riscatto Totale", "Trasferimento ad altro fondo")
    val TIPI_PIANO = listOf("Negoziale", "Aperto", "PIP")
    const val LIMITE_DEDUCIBILE_ANNUO = 5164.57
    private const val ALIQUOTA_BASE = 0.15
    private const val ALIQUOTA_MIN = 0.09
    private const val RIDUZIONE_ANNUA = 0.003

    const val VERSAMENTI = "versamenti"
    const val VALORIZZAZIONI = "valorizzazioni"
    const val RISCATTI = "riscatti"

    private val FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy")
    private val LEGGI = DateTimeFormatter.ofPattern("d-M-yyyy")

    private lateinit var file: File

    var errore: String? = null
        private set

    fun init(dir: File) {
        file = File(dir, FILE)
    }

    private fun pianoVuoto(): JSONObject = JSONObject()
        .put("nome_fondo", "").put("gestore", "").put("comparto", "")
        .put("tipo", "Negoziale").put("data_adesione", "").put("note", "")
        .put("tasso_inflazione_stimato", 2.0)
        .put("data_pensione", "")
        .put("versamento_annuo_stimato", 0.0)
        .put("rendimento_atteso_pct", 3.0)
        .put("costo_gestione_pct", 1.0)

    private fun vuoto(): JSONObject = JSONObject()
        .put("anagrafica", pianoVuoto())
        .put(VERSAMENTI, JSONArray())
        .put(VALORIZZAZIONI, JSONArray())
        .put(RISCATTI, JSONArray())

    private fun normalizza(o: JSONObject): JSONObject {
        val base = pianoVuoto()
        val an = o.optJSONObject("anagrafica") ?: JSONObject().also { o.put("anagrafica", it) }
        val chiavi = base.keys()
        while (chiavi.hasNext()) {
            val k = chiavi.next()
            if (!an.has(k)) an.put(k, base.get(k))
        }
        for (k in listOf(VERSAMENTI, VALORIZZAZIONI, RISCATTI)) {
            if (o.optJSONArray(k) == null) o.put(k, JSONArray())
        }
        return o
    }

    private fun leggi(): JSONObject {
        errore = null
        if (!file.exists() || file.length() == 0L) return vuoto()
        return try {
            normalizza(JSONObject(file.readText(Charsets.UTF_8)))
        } catch (e: Exception) {
            errore = "$FILE risulta illeggibile o corrotto: nessuna modifica effettuata"
            vuoto()
        }
    }

    private fun scrivi(o: JSONObject) {
        val tmp = File(file.parentFile, "$FILE.tmp")
        tmp.writeText(o.toString(2), Charsets.UTF_8)
        Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }

    private fun modifica(blocco: (JSONObject) -> String?): String? {
        val o = leggi()
        errore?.let { return it }
        val err = blocco(o)
        if (err == null) scrivi(o)
        return err
    }

    fun data(s: String): LocalDate? = try {
        if (s.isBlank()) null else LocalDate.parse(s.trim(), LEGGI)
    } catch (_: Exception) {
        null
    }

    fun fmt(d: LocalDate): String = d.format(FMT)

    private fun chiaveData(s: String): LocalDate = data(s) ?: LocalDate.MIN

    class Piano(
        val nome: String, val gestore: String, val comparto: String, val tipo: String,
        val adesione: String, val note: String, val inflazione: Double, val dataPensione: String,
        val versAnnuo: Double, val rendAtteso: Double, val costoGestione: Double,
    )

    class Mov(
        val id: String, val data: String, val tipo: String, val importo: Double,
        val motivo: String, val note: String,
    )

    class Dati(val piano: Piano, val vers: List<Mov>, val valor: List<Mov>, val risc: List<Mov>)

    private fun lista(o: JSONObject, chiave: String): List<Mov> {
        val a = o.optJSONArray(chiave) ?: return emptyList()
        val campo = if (chiave == VALORIZZAZIONI) "controvalore" else "importo"
        return (0 until a.length()).mapNotNull { i ->
            val r = a.optJSONObject(i) ?: return@mapNotNull null
            Mov(
                r.optString("id"), r.optString("data"),
                if (chiave == VERSAMENTI) r.optString("tipo", "Volontario") else r.optString("tipo"),
                r.optDouble(campo, 0.0), r.optString("motivo"), r.optString("note"),
            )
        }
    }

    fun dati(): Dati {
        val o = leggi()
        val a = o.optJSONObject("anagrafica") ?: pianoVuoto()
        val p = Piano(
            a.optString("nome_fondo"), a.optString("gestore"), a.optString("comparto"),
            a.optString("tipo", "Negoziale"), a.optString("data_adesione"), a.optString("note"),
            a.optDouble("tasso_inflazione_stimato", 2.0), a.optString("data_pensione"),
            a.optDouble("versamento_annuo_stimato", 0.0), a.optDouble("rendimento_atteso_pct", 3.0),
            a.optDouble("costo_gestione_pct", 1.0),
        )
        return Dati(p, lista(o, VERSAMENTI), lista(o, VALORIZZAZIONI), lista(o, RISCATTI))
    }

    fun ordinate(l: List<Mov>): List<Mov> = l.sortedByDescending { chiaveData(it.data) }

    // ---- calcoli (stessi del desktop) ----

    fun anniIscrizione(d: Dati, rif: LocalDate = LocalDate.now()): Double {
        val ades = data(d.piano.adesione) ?: return 0.0
        return maxOf(ChronoUnit.DAYS.between(ades, rif) / 365.25, 0.0)
    }

    fun aliquota(anni: Double): Double {
        if (anni <= 15) return ALIQUOTA_BASE
        return maxOf(ALIQUOTA_BASE - RIDUZIONE_ANNUA * (anni - 15), ALIQUOTA_MIN)
    }

    class Totali(
        val perTipo: Map<String, Double>, val versato: Double, val riscattato: Double,
        val controvalore: Double, val ultimaValor: Mov?, val rendEur: Double, val rendPct: Double,
        val deducibileAnno: Double, val anniIscr: Double, val aliquota: Double, val nettoOggi: Double,
    )

    fun totali(d: Dati, oggi: LocalDate = LocalDate.now()): Totali {
        val perTipo = LinkedHashMap<String, Double>()
        for (t in TIPI_VERSAMENTO) perTipo[t] = 0.0
        for (v in d.vers) perTipo[v.tipo] = (perTipo[v.tipo] ?: 0.0) + v.importo
        val versato = perTipo.values.sum()
        val riscattato = d.risc.sumOf { it.importo }
        val ultima = d.valor.sortedBy { chiaveData(it.data) }.lastOrNull()
        val controvalore = ultima?.importo ?: 0.0
        val rendEur = controvalore + riscattato - versato
        val rendPct = if (versato > 0) rendEur / versato * 100 else 0.0
        val deducibili = setOf("Lavoratore", "Datore di Lavoro", "Volontario")
        val dedAnno = d.vers.filter { it.tipo in deducibili && chiaveData(it.data).year == oggi.year }.sumOf { it.importo }
        val anni = anniIscrizione(d, oggi)
        val aliq = aliquota(anni)
        return Totali(perTipo, versato, riscattato, controvalore, ultima, rendEur, rendPct, dedAnno, anni, aliq, controvalore * (1 - aliq))
    }

    class ConfrontoTfr(val versato: Double, val teorico: Double, val tassoAnnuo: Double)

    fun confrontoTfr(d: Dati, oggi: LocalDate = LocalDate.now()): ConfrontoTfr {
        val infl = (if (d.piano.inflazione.isNaN()) 2.0 else d.piano.inflazione) / 100.0
        val tasso = 0.015 + 0.75 * infl
        var tot = 0.0
        var teo = 0.0
        for (v in d.vers) {
            if (v.tipo != "TFR") continue
            val dd = data(v.data)
            val anni = if (dd == null) 0.0 else maxOf(ChronoUnit.DAYS.between(dd, oggi) / 365.25, 0.0)
            tot += v.importo
            teo += v.importo * Math.pow(1 + tasso, anni)
        }
        return ConfrontoTfr(tot, teo, tasso)
    }

    class Proiezione(
        val anniMancanti: Double, val lordo: Double, val aliquota: Double, val netto: Double,
        val tassoNetto: Double, val anniIscr: Double,
    )

    fun proiezione(d: Dati, oggi: LocalDate = LocalDate.now()): Proiezione? {
        val pens = data(d.piano.dataPensione) ?: return null
        if (!pens.isAfter(oggi)) return null
        var capitale = totali(d, oggi).controvalore
        val tassoNetto = d.piano.rendAtteso / 100.0 - d.piano.costoGestione / 100.0
        val anniMancanti = ChronoUnit.DAYS.between(oggi, pens) / 365.25
        val interi = anniMancanti.toInt()
        repeat(interi) { capitale = capitale * (1 + tassoNetto) + d.piano.versAnnuo }
        val frazione = anniMancanti - interi
        capitale += capitale * tassoNetto * frazione + d.piano.versAnnuo * frazione
        val anni = anniIscrizione(d, pens)
        val aliq = aliquota(anni)
        return Proiezione(anniMancanti, capitale, aliq, capitale * (1 - aliq), tassoNetto, anni)
    }

    class Punto(val data: LocalDate, val versato: Double, val controvalore: Double)

    fun andamento(d: Dati): List<Punto> =
        d.valor.sortedBy { chiaveData(it.data) }.map { v ->
            val dd = chiaveData(v.data)
            val netto = d.vers.filter { chiaveData(it.data) <= dd }.sumOf { it.importo } -
                d.risc.filter { chiaveData(it.data) <= dd }.sumOf { it.importo }
            Punto(dd, netto, v.importo)
        }

    // ---- modifiche ----

    fun salvaPiano(
        nome: String, gestore: String, comparto: String, tipo: String, adesione: String,
        inflazione: Double, note: String, dataPensione: String, versAnnuo: Double,
        rendAtteso: Double, costoGestione: Double,
    ): String? {
        if (adesione.isNotBlank() && data(adesione) == null) return "Data adesione non valida."
        if (dataPensione.isNotBlank() && data(dataPensione) == null) return "Data pensionamento non valida."
        return modifica { o ->
            val a = o.getJSONObject("anagrafica")
            a.put("nome_fondo", nome.trim())
            a.put("gestore", gestore.trim())
            a.put("comparto", comparto.trim())
            a.put("tipo", tipo)
            a.put("data_adesione", data(adesione)?.let { fmt(it) } ?: "")
            a.put("tasso_inflazione_stimato", inflazione)
            a.put("note", note.trim())
            a.put("data_pensione", data(dataPensione)?.let { fmt(it) } ?: "")
            a.put("versamento_annuo_stimato", versAnnuo)
            a.put("rendimento_atteso_pct", rendAtteso)
            a.put("costo_gestione_pct", costoGestione)
            null
        }
    }

    private fun prefisso(chiave: String) = when (chiave) {
        VERSAMENTI -> "V"
        VALORIZZAZIONI -> "Z"
        else -> "R"
    }

    private fun nuovoId(a: JSONArray, pref: String): String {
        val ids = (0 until a.length()).mapNotNull { a.optJSONObject(it)?.optString("id") }.toSet()
        var i = 1
        while ("$pref$i" in ids) i++
        return "$pref$i"
    }

    fun salvaVoce(
        chiave: String, id: String?, data: LocalDate?, tipo: String, importo: Double,
        motivo: String, note: String,
    ): String? {
        if (data == null) return "Data non valida."
        if (importo <= 0) return "Importo non valido."
        return modifica { o ->
            val a = o.getJSONArray(chiave)
            val r = if (id == null) {
                JSONObject().put("id", nuovoId(a, prefisso(chiave))).also { a.put(it) }
            } else (0 until a.length()).mapNotNull { a.optJSONObject(it) }.firstOrNull { it.optString("id") == id }
                ?: return@modifica "Voce non trovata."
            r.put("data", fmt(data))
            if (chiave != VALORIZZAZIONI) r.put("tipo", tipo)
            r.put(if (chiave == VALORIZZAZIONI) "controvalore" else "importo", importo)
            if (chiave == RISCATTI) {
                r.put("motivo", motivo.trim().take(30))
                r.put("note", note.trim().take(30))
            } else {
                r.put("note", note.trim().take(40))
            }
            null
        }
    }

    fun elimina(chiave: String, id: String): String? = modifica { o ->
        val a = o.getJSONArray(chiave)
        val i = (0 until a.length()).firstOrNull { a.optJSONObject(it)?.optString("id") == id }
            ?: return@modifica "Voce non trovata."
        a.remove(i)
        null
    }
}
