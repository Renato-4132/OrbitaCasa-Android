package it.orbitacasa.mobile

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.LocalDate
import java.time.YearMonth

object Fondo {
    const val FILE = "fondo_risparmio.json"

    private lateinit var file: File

    var errore: String? = null
        private set

    fun init(dir: File) {
        file = File(dir, FILE)
    }

    private fun vuoto(): JSONObject =
        JSONObject().put("obiettivo_annuale", 0.0).put("fondo_attuale", 0.0).put("obiettivi", JSONArray())

    private fun leggi(): JSONObject {
        errore = null
        if (!file.exists() || file.length() == 0L) return vuoto()
        return try {
            JSONObject(file.readText(Charsets.UTF_8))
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

    class Obiettivo(val nome: String, val importo: Double, val data: String, val mesi: Int)

    fun obiettivoAnnuale(): Double = leggi().optDouble("obiettivo_annuale", 0.0)
    fun fondoAttuale(): Double = leggi().optDouble("fondo_attuale", 0.0)

    fun obiettivi(): List<Obiettivo> {
        val a = leggi().optJSONArray("obiettivi") ?: return emptyList()
        return (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            Obiettivo(o.optString("nome"), o.optDouble("importo", 0.0), o.optString("data"), o.optInt("mesi", 0))
        }
    }

    fun salvaImporti(obiettivoAnnuale: Double, fondo: Double): String? {
        if (obiettivoAnnuale < 0 || fondo < 0) return "Valori non validi."
        return modifica {
            it.put("obiettivo_annuale", obiettivoAnnuale)
            it.put("fondo_attuale", fondo)
            null
        }
    }

    fun aggiungiObiettivo(nome: String, importo: Double, scadenza: String): String? {
        val n = nome.trim()
        if (n.isEmpty()) return "Inserisci un nome."
        if (importo <= 0) return "Importo non valido."
        val parti = scadenza.trim().split("/")
        val dt = try {
            YearMonth.of(parti[1].trim().toInt(), parti[0].trim().toInt())
        } catch (_: Exception) {
            return "Scadenza non valida: scrivi mese e anno come mm/aaaa."
        }
        val oggi = YearMonth.now()
        val mesi = (dt.year - oggi.year) * 12 + (dt.monthValue - oggi.monthValue)
        if (mesi <= 0) return "La scadenza deve essere in un mese futuro."
        return modifica {
            val a = it.optJSONArray("obiettivi") ?: JSONArray().also { x -> it.put("obiettivi", x) }
            a.put(JSONObject().put("nome", n).put("importo", importo).put("data", scadenza.trim()).put("mesi", mesi))
            null
        }
    }

    fun eliminaObiettivo(idx: Int): String? = modifica {
        val a = it.optJSONArray("obiettivi") ?: JSONArray()
        if (idx !in 0 until a.length()) return@modifica "Obiettivo non trovato."
        val nuovo = JSONArray()
        for (i in 0 until a.length()) if (i != idx) nuovo.put(a.get(i))
        it.put("obiettivi", nuovo)
        null
    }

    class Mese(val ym: YearMonth, val e: Double, val u: Double, val futuro: Boolean = false) {
        val r get() = e - u
    }

    class ObiettivoStato(val ob: Obiettivo, val rata: Double, val ok: Boolean, val mesiNecessari: Int?)

    class Analisi(
        val entrate365: Double, val uscite365: Double, val mediaE: Double, val mediaU: Double, val mediaR: Double,
        val proiezione: List<Mese>, val eProj: Double, val uProj: Double,
        val ultimi12: List<Mese>, val migliore: Mese?, val peggiore: Mese?,
        val fe3: Double, val fe6: Double, val fe12: Double,
        val rataMensile: Double, val diffObj: Double, val pctObj: Double,
        val obiettivi: List<ObiettivoStato>,
    ) {
        val risparmio365 get() = entrate365 - uscite365
        val rProj get() = eProj - uProj
    }

    fun analizza(voci: List<Voce>, obiettivoAnnuale: Double, obiettivi: List<Obiettivo>): Analisi {
        val oggi = LocalDate.now()
        val inizio = oggi.minusDays(364)
        val mesiDati = LinkedHashMap<YearMonth, DoubleArray>() // [entrate, uscite]
        var e365 = 0.0
        var u365 = 0.0
        for (v in voci) {
            if (v.data.isBefore(inizio) || v.data.isAfter(oggi)) continue
            val a = mesiDati.getOrPut(YearMonth.from(v.data)) { DoubleArray(2) }
            if (v.tipo == "Entrata") { e365 += v.importo; a[0] += v.importo } else { u365 += v.importo; a[1] += v.importo }
        }
        val nMesi = if (mesiDati.isEmpty()) 1 else mesiDati.size
        val mediaE = e365 / nMesi
        val mediaU = u365 / nMesi
        val mediaR = (e365 - u365) / nMesi
        val annoCorr = oggi.year
        val prec = HashMap<Int, DoubleArray>()
        for (v in voci) {
            if (v.data.year != annoCorr - 1) continue
            val a = prec.getOrPut(v.data.monthValue) { DoubleArray(2) }
            if (v.tipo == "Entrata") a[0] += v.importo else a[1] += v.importo
        }
        val proj = ArrayList<Mese>()
        var eProj = 0.0
        var uProj = 0.0
        for (m in 1..12) {
            val ym = YearMonth.of(annoCorr, m)
            val reale = mesiDati[ym]
            val x = if (reale != null) Mese(ym, reale[0], reale[1], false) else {
                val p = prec[m]
                if (p != null) Mese(ym, p[0], p[1], true) else Mese(ym, mediaE, mediaU, true)
            }
            proj.add(x)
            eProj += x.e
            uProj += x.u
        }
        val tot = HashMap<YearMonth, DoubleArray>()
        for (v in voci) {
            val a = tot.getOrPut(YearMonth.from(v.data)) { DoubleArray(2) }
            if (v.tipo == "Entrata") a[0] += v.importo else a[1] += v.importo
        }
        val adesso = YearMonth.from(oggi)
        val ultimi = (11 downTo 0).map { i ->
            val ym = adesso.minusMonths(i.toLong())
            val a = tot[ym]
            Mese(ym, a?.get(0) ?: 0.0, a?.get(1) ?: 0.0)
        }

        val rata = if (obiettivoAnnuale > 0) obiettivoAnnuale / 12 else 0.0
        val pctObj = if (obiettivoAnnuale > 0) ((e365 - u365) / obiettivoAnnuale).coerceIn(0.0, 1.0) else 0.0
        val stati = obiettivi.map { ob ->
            val r = if (ob.mesi > 0) ob.importo / ob.mesi else ob.importo
            ObiettivoStato(ob, r, mediaR >= r, if (mediaR > 0) (ob.importo / mediaR).toInt() else null)
        }
        return Analisi(
            e365, u365, mediaE, mediaU, mediaR, proj, eProj, uProj, ultimi,
            ultimi.maxByOrNull { it.r }, ultimi.minByOrNull { it.r },
            mediaU * 3, mediaU * 6, mediaU * 12, rata, mediaR - rata, pctObj, stati,
        )
    }
}
