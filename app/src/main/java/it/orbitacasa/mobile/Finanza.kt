package it.orbitacasa.mobile

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

class RigaFin(
    val n: Int,
    val data: LocalDate,
    val rata: Double,
    val rataBase: Double,
    val cap: Double,
    val inte: Double,
    val residuo: Double,
    val extra: Double,
)

class StatoFin(
    val stato: String,
    val rateTot: Int,
    val ratePagate: Int,
    val residuo: Double,
    val pct: Double,
    val intPagati: Double,
    val intRestanti: Double,
    val rataMensile: Double,
    val prossimaData: LocalDate?,
    val prossimaImporto: Double,
    val fine: LocalDate?,
    val costoTotale: Double,
    val periodo: Int,
    val risparmioInt: Double?,
    val mesiDiff: Int?,
)

object Fin {
    const val FILE = "finanziamenti.json"
    const val MAX_FIN = 30
    const val MAX_VAR = 200
    val TIPI_VAR = listOf("extra", "durata", "prolunga", "tasso", "rata")
    private val DF = DateTimeFormatter.ofPattern("dd-MM-yyyy")

    fun dataIt(d: LocalDate): String = d.format(DF)

    fun rataFrancese(capitale: Double, tm: Double, mesi: Int): Double {
        if (mesi <= 0) return 0.0
        return if (tm > 0) {
            val f = (1 + tm).pow(mesi)
            val r = capitale * (tm * f) / (f - 1)
            if (r.isNaN() || r.isInfinite()) 0.0 else r
        } else capitale / mesi
    }

    private fun aggiungiMesi(d: LocalDate, n: Int): LocalDate {
        val ym = YearMonth.from(d).plusMonths(n.toLong())
        return ym.atDay(min(d.dayOfMonth, ym.lengthOfMonth()))
    }

    private fun mesiResidui(debito: Double, tm: Double, rata: Double): Int {
        if (debito <= 0.005) return 0
        if (tm <= 0) return ceil(debito / rata).toInt()
        val x = 1 - debito * tm / rata
        if (x <= 0) return 600
        return max(1, ceil(-ln(x) / ln(1 + tm) - 1e-9).toInt())
    }

    private fun periodo(f: JSONObject): Int = if (f.optInt("periodo", 1) == 3) 3 else 1

    private class Evento(val data: LocalDate, val tipo: String, val valore: Double, val strategia: String)

    private fun eventi(f: JSONObject): List<Evento> {
        val out = ArrayList<Evento>()
        val a = f.optJSONArray("variazioni") ?: return out
        for (i in 0 until a.length()) {
            val e = a.optJSONObject(i) ?: continue
            try {
                out.add(
                    Evento(
                        LocalDate.parse(e.getString("data")), e.getString("tipo"), e.getDouble("valore"),
                        e.optString("strategia", "ricalcola").ifBlank { "ricalcola" }
                    )
                )
            } catch (_: Exception) {
            }
        }
        return out.sortedBy { it.data }
    }

    fun piano(f: JSONObject, senzaVariazioni: Boolean = false): Pair<List<RigaFin>, String?> {
        val cap = f.getDouble("capitale")
        val p = periodo(f)
        var tm = f.getDouble("tasso") / 1200 * p
        val mesiTot = (f.getInt("mesi") + p - 1) / p
        val spese = f.optDouble("spese", 0.0) * p
        val inizio = LocalDate.parse(f.getString("data_inizio"))
        val rataFissa = f.optDouble("rata", 0.0)
        val custom = rataFissa > 0
        var rata = if (custom) rataFissa else rataFrancese(cap, tm, mesiTot)
        if (custom && tm > 0 && rata <= cap * tm + 0.005) return emptyList<RigaFin>() to "La rata è troppo bassa per coprire gli interessi."
        val ev: List<Evento> = if (senzaVariazioni) emptyList() else eventi(f)
        var ei = 0
        var vincolo = !custom
        var mesiRim = mesiTot
        val righe = ArrayList<RigaFin>()
        var debito = cap
        var n = 0
        while (debito > 0.005 && n < 600) {
            n++
            val data = aggiungiMesi(inizio, (n - 1) * p)
            var extraN = 0.0
            while (ei < ev.size && !ev[ei].data.isAfter(data)) {
                val e = ev[ei]
                ei++
                val v = e.valore
                if (!vincolo) mesiRim = mesiResidui(debito, tm, rata)
                when (e.tipo) {
                    "tasso" -> {
                        tm = v / 1200 * p
                        if (vincolo) rata = rataFrancese(debito, tm, max(mesiRim, 1))
                    }
                    "rata" -> {
                        rata = v
                        vincolo = false
                    }
                    "durata" -> {
                        mesiRim = max(1, Math.round(v * 12 / p).toInt())
                        vincolo = true
                        rata = rataFrancese(debito, tm, mesiRim)
                    }
                    "prolunga" -> {
                        mesiRim = max(1, mesiRim + Math.round(v * 12 / p).toInt())
                        vincolo = true
                        rata = rataFrancese(debito, tm, mesiRim)
                    }
                    "extra" -> {
                        val ex = min(v, debito)
                        debito -= ex
                        extraN += ex
                        if (e.strategia == "ricalcola" && debito > 0.005) {
                            vincolo = true
                            rata = rataFrancese(debito, tm, max(mesiRim, 1))
                        } else vincolo = false
                    }
                }
            }
            if (debito <= 0.005) {
                righe.add(RigaFin(n, data, extraN, 0.0, extraN, 0.0, 0.0, extraN))
                break
            }
            val interessi = debito * tm
            var quota = rata - interessi
            if (tm > 0 && quota <= 0) return righe to "Con queste variazioni la rata non copre gli interessi."
            if (quota >= debito || (vincolo && mesiRim <= 1)) quota = debito
            debito -= quota
            val base = quota + interessi + spese
            righe.add(RigaFin(n, data, base + extraN, base, quota + extraN, interessi, max(debito, 0.0), extraN))
            mesiRim--
        }
        if (debito > 0.005) return righe to "Il finanziamento non si estingue entro 50 anni."
        return righe to null
    }

    fun stato(f: JSONObject, oggi: LocalDate = LocalDate.now()): Pair<StatoFin?, String?> {
        val (righe, err) = piano(f)
        if (err != null || righe.isEmpty()) return null to (err ?: "Dati non validi.")
        val cap = f.getDouble("capitale")
        val p = periodo(f)
        val tot = righe.size
        val k = righe.count { !it.data.isAfter(oggi) }
        val residuo = when {
            k <= 0 -> cap
            k >= tot -> 0.0
            else -> righe[k - 1].residuo
        }
        val intPag = righe.take(k).sumOf { it.inte }
        val intRes = righe.drop(k).sumOf { it.inte }
        val prox = righe.getOrNull(k)
        var risp: Double? = null
        var diff: Int? = null
        if ((f.optJSONArray("variazioni")?.length() ?: 0) > 0) {
            val (base, e2) = piano(f, true)
            if (e2 == null && base.isNotEmpty()) {
                risp = base.sumOf { it.inte } - (intPag + intRes)
                diff = (base.size - tot) * p
            }
        }
        return StatoFin(
            stato = if (k >= tot) "estinto" else if (k == 0) "non_iniziato" else "in_corso",
            rateTot = tot, ratePagate = k, residuo = residuo,
            pct = if (cap > 0) 100.0 * (cap - residuo) / cap else 0.0,
            intPagati = intPag, intRestanti = intRes,
            rataMensile = righe[min(k, tot - 1)].rataBase / p,
            prossimaData = prox?.data, prossimaImporto = prox?.rata ?: 0.0,
            fine = righe.last().data, costoTotale = righe.sumOf { it.rata },
            periodo = p, risparmioInt = risp, mesiDiff = diff,
        ) to null
    }

    private lateinit var file: File

    fun init(dir: File) {
        file = File(dir, FILE)
    }

    private fun leggi(): JSONArray {
        try {
            if (file.exists()) {
                val o = JSONObject(file.readText(Charsets.UTF_8))
                return o.optJSONArray("finanziamenti") ?: JSONArray()
            }
        } catch (_: Exception) {
        }
        return JSONArray()
    }

    private fun scrivi(a: JSONArray) {
        val tmp = File(file.parentFile, "$FILE.tmp")
        tmp.writeText(JSONObject().put("finanziamenti", a).toString(2), Charsets.UTF_8)
        Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }

    fun elenco(): List<JSONObject> {
        val a = leggi()
        return (0 until a.length()).mapNotNull { a.optJSONObject(it) }
    }

    fun per(id: String): JSONObject? = elenco().firstOrNull { it.optString("id") == id }

    fun salva(
        id: String?, nome: String, capitale: Double, tasso: Double, mesi: Int,
        dataInizio: LocalDate, rata: Double, spese: Double, trimestrale: Boolean, conto: String,
    ): String? {
        val n = nome.trim().take(60)
        if (n.isEmpty()) return "Inserisci un nome per il finanziamento."
        if (capitale <= 0 || capitale > 2_000_000 || tasso < 0 || tasso > 35 || mesi < 1 || mesi > 600 ||
            rata < 0 || spese < 0
        ) return "Controlla i dati: capitale 1–2.000.000 €, tasso 0–35 %, durata da 1 a 600 mesi, rata e spese non negative."
        val f = JSONObject().put("nome", n).put("capitale", capitale).put("tasso", tasso).put("mesi", mesi)
            .put("data_inizio", dataInizio.toString()).put("rata", rata).put("spese", spese)
        if (trimestrale) f.put("periodo", 3)
        if (conto.isNotBlank()) f.put("conto", conto)
        val a = leggi()
        var trovato = -1
        if (id != null) {
            for (i in 0 until a.length()) if (a.optJSONObject(i)?.optString("id") == id) trovato = i
        }
        if (trovato >= 0) {
            val vecchio = a.getJSONObject(trovato)
            f.put("id", id)
            f.put("variazioni", vecchio.optJSONArray("variazioni") ?: JSONArray())
            vecchio.optJSONObject("esportate")?.let { f.put("esportate", it) }
            val err = piano(f).second
            if (err != null) return "$err Controlla le variazioni di questo finanziamento."
            a.put(trovato, f)
        } else {
            val err = piano(f).second
            if (err != null) return err
            if (a.length() >= MAX_FIN) return "Massimo $MAX_FIN finanziamenti."
            f.put("id", UUID.randomUUID().toString().replace("-", "").take(8))
            a.put(f)
        }
        scrivi(a)
        return null
    }

    fun elimina(id: String) {
        val a = leggi()
        val n = JSONArray()
        for (i in 0 until a.length()) {
            val o = a.optJSONObject(i) ?: continue
            if (o.optString("id") != id) n.put(o)
        }
        scrivi(n)
    }

    fun aggiungiVariazione(
        id: String, tipo: String, valore: Double, strategia: String, data: LocalDate,
    ): String? {
        if (tipo !in TIPI_VAR) return "Tipo di variazione sconosciuto."
        val lim = mapOf(
            "extra" to (0.01 to 2_000_000.0), "durata" to (1.0 to 600.0), "prolunga" to (-480.0 to 480.0),
            "tasso" to (0.0 to 35.0), "rata" to (0.01 to 100_000.0),
        )[tipo]!!
        var v = valore
        if (tipo == "durata" || tipo == "prolunga") v = Math.round(v).toDouble()
        if (v < lim.first || v > lim.second || (tipo == "prolunga" && v == 0.0)) return "Valore non valido per questa variazione."
        if (tipo == "durata" || tipo == "prolunga") v /= 12
        val a = leggi()
        for (i in 0 until a.length()) {
            val f = a.optJSONObject(i) ?: continue
            if (f.optString("id") != id) continue
            val var_ = f.optJSONArray("variazioni") ?: JSONArray().also { f.put("variazioni", it) }
            if (var_.length() >= MAX_VAR) return "Massimo $MAX_VAR variazioni per finanziamento."
            val nuova = JSONObject().put("data", data.toString()).put("tipo", tipo).put("valore", v)
                .put("strategia", if (strategia == "accorcia") "accorcia" else "ricalcola")
            val lista = ArrayList<JSONObject>()
            for (j in 0 until var_.length()) var_.optJSONObject(j)?.let { lista.add(it) }
            lista.add(nuova)
            lista.sortBy { it.optString("data") }
            f.put("variazioni", JSONArray(lista))
            piano(f).second?.let { return it }
            scrivi(a)
            return null
        }
        return "Finanziamento non trovato."
    }

    fun eliminaVariazione(id: String, indice: Int): String? {
        val a = leggi()
        for (i in 0 until a.length()) {
            val f = a.optJSONObject(i) ?: continue
            if (f.optString("id") != id) continue
            val v = f.optJSONArray("variazioni") ?: return "Variazione non trovata."
            if (indice !in 0 until v.length()) return "Variazione non trovata."
            v.remove(indice)
            piano(f).second?.let { return it }
            scrivi(a)
            return null
        }
        return "Finanziamento non trovato."
    }

    fun descrivi(e: JSONObject): String {
        val v = e.optDouble("valore", 0.0)
        val t = when (e.optString("tipo")) {
            "extra" -> "Versamento extra " + euro(v) + " (" + (if (e.optString("strategia") == "accorcia") "mantieni rata, accorcia" else "ricalcola rata") + ")"
            "durata" -> "Nuova durata residua: " + Math.round(v * 12) + " mesi"
            "prolunga" -> (if (v >= 0) "Prolungamento di " else "Accorciamento di ") + abs(Math.round(v * 12)) + " mesi"
            "tasso" -> "Nuovo tasso " + String.format(Locale.ITALY, "%.2f", v) + " %"
            "rata" -> "Nuova rata " + euro(v)
            else -> e.optString("tipo")
        }
        return e.optString("data").let { d -> runCatching { dataIt(LocalDate.parse(d)) }.getOrDefault(d) } + " · " + t
    }

    private fun pct(x: Double) = String.format(Locale.ITALY, "%.1f", x) + " %"

    fun simulazione(cap: Double, anni: Double, tasso: Double, spese: Double, extra: Double): String {
        if (cap <= 0 || anni < 1 || tasso < 0 || cap > 500000 || anni > 35 || tasso > 35 || spese < 0 || extra < 0) {
            return "⚠️ Valori non validi: capitale 1–500.000 €, durata 1–35 anni, tasso 0–35 %, spese ed extra non negativi."
        }
        if (extra >= cap) return "⚠️ L'ammortamento extra deve essere minore del capitale."
        val mesi = anni.toInt() * 12
        val tm = tasso / 100 / 12
        val debito = cap - extra
        val rata = rataFrancese(debito, tm, mesi)
        val interessi = rata * mesi - debito
        return "Rata mensile: " + euro(rata + spese) + (if (spese > 0) "  (di cui spese " + euro(spese) + ")" else "") +
            "\nInteressi totali: " + euro(interessi) +
            "\nCapitale + interessi: " + euro(cap + interessi) +
            "\nTotale da pagare: " + euro(debito + interessi + spese * mesi + extra) +
            "\nRate: $mesi"
    }

    fun rataSostenibile(rataDisp: Double, anni: Double, tasso: Double, spese: Double): String {
        if (rataDisp <= 0 || anni < 1 || tasso < 0) return "⚠️ Rata, durata e tasso devono essere valori positivi."
        if (anni > 50 || tasso > 35) return "⚠️ Durata massima 50 anni, tasso massimo 35 %."
        val rataCap = rataDisp - spese
        if (rataCap <= 0) return "⚠️ La rata sostenibile deve essere maggiore delle spese mensili."
        val mesi = anni.toInt() * 12
        val tm = tasso / 100 / 12
        val capMax = if (tm > 0) rataCap * (1 - (1 + tm).pow(-mesi)) / tm else rataCap * mesi
        val interessi = rataCap * mesi - capMax
        return "Finanziamento massimo: " + euro(capMax) +
            "\nRata (capitale + interessi): " + euro(rataCap) +
            "\nInteressi totali: " + euro(interessi) +
            "\nTotale pagato: " + euro(rataDisp * mesi) + "  ($mesi rate)"
    }

    private val MESI_ITA = listOf(
        "", "Gennaio", "Febbraio", "Marzo", "Aprile", "Maggio", "Giugno",
        "Luglio", "Agosto", "Settembre", "Ottobre", "Novembre", "Dicembre"
    )

    fun estinzione(
        residuo: Double, mesiRim: Int, tasso: Double, rataOrig: Double,
        extraAnnuo: Double, extraSubito: Double, meseVers: Int, mantieniRata: Boolean,
    ): String {
        if (residuo <= 0 || mesiRim <= 0 || tasso < 0 || residuo > 1_000_000 || mesiRim > 600 || tasso > 25 ||
            extraAnnuo < 0 || extraSubito < 0 || meseVers !in 1..12
        ) return "⚠️ Controlla i dati: debito 1–1.000.000 €, mesi 1–600, tasso 0–25 %, mese di versamento 1–12, importi non negativi."
        if (extraSubito > residuo) return "⚠️ L'abbattimento iniziale non può superare il debito residuo."
        val tm = tasso / 100 / 12
        if (rataOrig <= residuo * tm + 0.01) return "⚠️ La rata è troppo bassa per coprire gli interessi mensili."
        val oggi = LocalDate.now()
        val interessiStd = rataOrig * mesiRim - residuo
        var debito = residuo
        var totVers = 0.0
        var totInt = 0.0
        var rataCorr = rataOrig
        var anticipoFatto = extraSubito <= 0
        var n = 0
        var ultima = 0.0
        while (debito > 0.01 && n < 600) {
            n++
            val mRata = (oggi.monthValue + n - 1) % 12 + 1
            if (!anticipoFatto && mRata == meseVers) {
                val abb = min(extraSubito, debito)
                debito -= abb
                totVers += abb
                anticipoFatto = true
                if (!mantieniRata) {
                    val mRes = mesiRim - (n - 1)
                    if (mRes > 0) rataCorr = rataFrancese(debito, tm, mRes)
                }
            }
            val interessi = debito * tm
            totInt += interessi
            val versExtra = if (mRata == meseVers) extraAnnuo else 0.0
            val rataMese = rataCorr
            if (versExtra > 0 && !mantieniRata) {
                val mRes = mesiRim - n
                if (mRes > 0) {
                    val dp = debito - (rataCorr - interessi)
                    val dn = max(0.0, dp - versExtra)
                    rataCorr = if (dn > 0) rataFrancese(dn, tm, mRes) else 0.0
                }
            }
            val abbPot = (rataMese - interessi) + versExtra
            val versReale: Double
            if (abbPot >= debito) {
                versReale = debito + interessi
                debito = 0.0
            } else {
                versReale = rataMese + versExtra
                debito -= abbPot
            }
            totVers += versReale
            ultima = versReale
        }
        val annoF = oggi.year + (oggi.monthValue + n - 1) / 12
        val meseF = (oggi.monthValue + n - 1) % 12 + 1
        return "Estinzione: " + MESI_ITA[meseF] + " " + annoF + "  ($n mesi)" +
            "\nMesi risparmiati: " + (mesiRim - n) +
            "\nTotale versato: " + euro(totVers) +
            "\nInteressi con il piano: " + euro(totInt) +
            "\nInteressi senza versamenti: " + euro(interessiStd) +
            "\nRisparmio sugli interessi: " + euro(max(0.0, interessiStd - totInt)) +
            "\nUltimo versamento: " + euro(ultima)
    }

    private fun sostRata(capitale: Double, tassoPct: Double, anni: Double): Double {
        val n = Math.round(anni * 12).toInt()
        val r = tassoPct / 100 / 12
        if (n <= 0) return 0.0
        if (r == 0.0) return capitale / n
        val f = (1 + r).pow(n)
        val x = capitale * r * f / (f - 1)
        return if (x.isNaN() || x.isInfinite()) 0.0 else x
    }

    fun sostenibilita(
        entrate0: Double, spese0: Double, capitale: Double, tasso: Double, anni: Int, orizzonte: Int,
        inflazione: Double, crescita: Double, stress: Boolean, stressDelta: Double, stressAnno: Int,
    ): String {
        val err = ArrayList<String>()
        if (entrate0 <= 0 || entrate0 > 50000) err.add("entrate nette mensili tra 1 e 50.000 €")
        if (spese0 < 0 || spese0 > 50000) err.add("spese fisse mensili tra 0 e 50.000 €")
        if (capitale <= 0 || capitale > 2_000_000) err.add("finanziamento tra 1 e 2.000.000 €")
        if (tasso < 0 || tasso > 30) err.add("tasso tra 0% e 30%")
        if (inflazione < 0 || inflazione > 30) err.add("crescita costo della vita tra 0% e 30%")
        if (crescita < 0 || crescita > 30) err.add("crescita del reddito tra 0% e 30%")
        if (anni !in 1..35) err.add("durata tra 1 e 35 anni")
        if (orizzonte < 1 || orizzonte > anni) err.add("orizzonte tra 1 anno e la durata del piano")
        if (stress) {
            if (stressDelta < 0 || stressDelta > 20) err.add("rialzo tassi tra 0 e 20 punti")
            if (stressAnno < 1 || stressAnno >= anni) err.add("anno del rialzo prima della fine del piano")
        }
        if (err.isNotEmpty()) return "⚠️ Correggi: " + err.joinToString("; ") + "."

        val n = anni * 12
        val piano = ArrayList<Double>()
        var debito = capitale
        var t = tasso
        var rata = sostRata(debito, t, n / 12.0)
        for (m in 1..n) {
            if (stress && m == stressAnno * 12 + 1) {
                t = tasso + stressDelta
                rata = sostRata(debito, t, (n - m + 1) / 12.0)
            }
            val interessi = debito * t / 100 / 12
            val quota = min(rata - interessi, debito)
            debito = max(0.0, debito - quota)
            piano.add(rata)
        }
        val sb = StringBuilder()
        var peggiore = 0
        for (y in 0..orizzonte) {
            val entrate = entrate0 * (1 + crescita / 100).pow(y)
            val spese = spese0 * (1 + inflazione / 100).pow(y)
            val rataAnno = if (y == 0) piano.firstOrNull() ?: 0.0 else {
                val tratto = piano.subList((y - 1) * 12, min(y * 12, piano.size))
                if (tratto.isEmpty()) 0.0 else tratto.sum() / tratto.size
            }
            val residuo = entrate - spese - rataAnno
            val p = if (entrate > 0) rataAnno / entrate * 100 else 0.0
            val s = if (residuo < 0 || p >= 40) 2 else if (p >= 30) 1 else 0
            peggiore = max(peggiore, s)
            sb.append(if (y == 0) "Oggi" else "Anno $y").append(": ")
                .append(listOf("🟢", "🟡", "🔴")[s]).append(" rata ").append(euro(rataAnno))
                .append(" (").append(pct(p)).append(" delle entrate) · resta ").append(euro(residuo)).append("\n")
        }
        val verdetto = listOf(
            "🟢 Sostenibile: la rata resta sotto il 30% delle entrate.",
            "🟡 Attenzione: in alcuni anni la rata supera il 30% delle entrate.",
            "🔴 A rischio: la rata supera il 40% delle entrate o il saldo va in negativo.",
        )[peggiore]
        return verdetto + "\n\n" + sb.toString().trimEnd()
    }
}
