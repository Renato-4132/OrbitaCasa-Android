package it.orbitacasa.mobile

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object Invest {
    const val FILE = "portafoglio.json"

    val TIPI_MOV = listOf("acquisto", "vendita")

    private val FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy")
    private val LEGGI = DateTimeFormatter.ofPattern("d-M-yyyy")

    private lateinit var file: File

    var errore: String? = null
        private set

    val prezzi = HashMap<String, Double>()

    fun init(dir: File) {
        file = File(dir, FILE)
    }

    private fun vuoto(): JSONObject = JSONObject()
        .put("impostazioni", JSONObject().put("valuta", "EUR").put("soglia_ribilanciamento", 2.0))
        .put("titoli", JSONArray())

    private fun normalizza(o: JSONObject): JSONObject {
        if (o.optJSONObject("impostazioni") == null) {
            o.put("impostazioni", JSONObject().put("valuta", "EUR").put("soglia_ribilanciamento", 2.0))
        }
        if (o.optJSONArray("titoli") == null) o.put("titoli", JSONArray())
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

    class Mov(
        val id: String, val data: String, val tipo: String, val quantita: Double,
        val prezzo: Double, val commissioni: Double, val note: String,
    ) {
        val totale: Double get() = quantita * prezzo + (if (tipo == "acquisto") commissioni else -commissioni)
    }

    class Div(val indice: Int, val data: String, val lordo: Double, val ritenuta: Double, val netto: Double)

    class Titolo(
        val ticker: String, val descrizione: String, val isin: String, val assetClass: String,
        val target: Double, val ultimoPrezzo: Double, val mov: List<Mov>, val div: List<Div>,
    )

    class Dati(val soglia: Double, val titoli: List<Titolo>)

    private fun movimenti(t: JSONObject): List<Mov> {
        val a = t.optJSONArray("movimenti") ?: return emptyList()
        return (0 until a.length()).mapNotNull { i ->
            val r = a.optJSONObject(i) ?: return@mapNotNull null
            Mov(
                r.optString("id").ifBlank { "#$i" }, r.optString("data"), r.optString("tipo", "acquisto"),
                r.optDouble("quantita", 0.0), r.optDouble("prezzo", 0.0), r.optDouble("commissioni", 0.0),
                r.optString("note"),
            )
        }
    }

    private fun dividendi(t: JSONObject): List<Div> {
        val a = t.optJSONArray("dividendi") ?: return emptyList()
        return (0 until a.length()).mapNotNull { i ->
            val r = a.optJSONObject(i) ?: return@mapNotNull null
            val lordo = r.optDouble("importo_lordo", r.optDouble("importo", 0.0))
            val rit = r.optDouble("ritenuta", 0.0)
            val netto = r.optDouble("importo_netto", r.optDouble("importo", lordo - rit))
            Div(i, r.optString("data"), lordo, rit, netto)
        }
    }

    fun dati(): Dati {
        val o = leggi()
        val soglia = o.optJSONObject("impostazioni")?.optDouble("soglia_ribilanciamento", 2.0) ?: 2.0
        val a = o.optJSONArray("titoli") ?: JSONArray()
        val l = (0 until a.length()).mapNotNull { i ->
            val t = a.optJSONObject(i) ?: return@mapNotNull null
            val tk = t.optString("ticker")
            if (tk.isBlank()) return@mapNotNull null
            Titolo(
                tk, t.optString("descrizione"), t.optString("isin"), t.optString("asset_class", "-"),
                t.optDouble("target_pct", 0.0), t.optDouble("ultimo_prezzo", 0.0), movimenti(t), dividendi(t),
            )
        }
        return Dati(if (soglia.isNaN()) 2.0 else soglia, l)
    }

    class Calc(
        val ticker: String, val descrizione: String, val assetClass: String, val target: Double,
        val quantita: Double, val pmc: Double, val prezzo: Double, val valore: Double,
        val investito: Double, val plEur: Double, val plPct: Double, val dividendi: Double,
    )

    fun calcola(t: Titolo): Calc {
        var quantita = 0.0
        var costo = 0.0
        for (m in t.mov) {
            if (m.tipo == "acquisto") {
                quantita += m.quantita
                costo += m.quantita * m.prezzo + m.commissioni
            } else if (m.tipo == "vendita") {
                if (quantita > 0) {
                    val pmc = if (quantita != 0.0) costo / quantita else 0.0
                    quantita -= m.quantita
                    costo -= m.quantita * pmc
                }
            }
        }
        quantita = maxOf(quantita, 0.0)
        costo = maxOf(costo, 0.0)
        val pmc = if (quantita > 0) costo / quantita else 0.0
        val dividendi = t.div.sumOf { it.netto }
        val prezzo = prezzi[t.ticker] ?: t.ultimoPrezzo
        val valore = quantita * prezzo
        val pl = valore - costo
        val plPct = if (costo > 0) pl / costo * 100 else 0.0
        return Calc(t.ticker, t.descrizione, t.assetClass, t.target, quantita, pmc, prezzo, valore, costo, pl, plPct, dividendi)
    }

    class Totali(
        val calcoli: List<Calc>, val investito: Double, val attuale: Double, val pl: Double,
        val plPct: Double, val dividendi: Double, val rendReale: Double,
    )

    fun totali(d: Dati): Totali {
        val c = d.titoli.map { calcola(it) }
        val inv = c.sumOf { it.investito }
        val att = c.sumOf { it.valore }
        val div = c.sumOf { it.dividendi }
        val pl = att - inv
        return Totali(c, inv, att, pl, if (inv > 0) pl / inv * 100 else 0.0, div, pl + div)
    }

    fun allocazione(c: Calc, totAttuale: Double): Double = if (totAttuale > 0) c.valore / totAttuale * 100 else 0.0

    fun stato(att: Double, target: Double, soglia: Double): Int {
        if (target == 0.0) return 0
        val sc = Math.abs(att - target)
        return when {
            sc <= soglia -> 1
            sc <= soglia * 2 -> 2
            else -> 3
        }
    }

    class Serie(
        val date: List<LocalDate>, val investito: List<Double>, val dividendi: List<Double>, val pl: List<Double>,
    )

    fun serie(d: Dati): Serie? {
        class Ev(val d: LocalDate, val tipo: String, val v: Double)
        val ev = ArrayList<Ev>()
        for (t in d.titoli) {
            for (m in t.mov) {
                val dd = data(m.data) ?: continue
                val costo = if (m.tipo == "acquisto") m.quantita * m.prezzo + m.commissioni
                else -(m.quantita * m.prezzo - m.commissioni)
                ev.add(Ev(dd, m.tipo, costo))
            }
            for (x in t.div) {
                val dd = data(x.data) ?: continue
                ev.add(Ev(dd, "dividendo", x.netto))
            }
        }
        if (ev.isEmpty()) return null
        ev.sortBy { it.d }
        val giorni = ev.map { it.d }.distinct().sorted()
        val attuale = d.titoli.sumOf { calcola(it).valore }
        var inv = 0.0
        var div = 0.0
        val si = ArrayList<Double>()
        val sd = ArrayList<Double>()
        val sp = ArrayList<Double>()
        for (g in giorni) {
            for (e in ev) {
                if (e.d != g) continue
                when (e.tipo) {
                    "dividendo" -> div += e.v
                    "acquisto", "vendita" -> inv += e.v
                }
            }
            si.add(inv)
            sd.add(div)
            sp.add(attuale - inv)
        }
        return Serie(giorni, si, sd, sp)
    }

    private fun trova(o: JSONObject, ticker: String): JSONObject? {
        val a = o.getJSONArray("titoli")
        return (0 until a.length()).mapNotNull { a.optJSONObject(it) }.firstOrNull { it.optString("ticker") == ticker }
    }

    fun salvaTitolo(
        ticker: String, nuovo: Boolean, descrizione: String, isin: String, asset: String, target: Double,
    ): String? {
        val tk = ticker.trim().uppercase()
        if (tk.isEmpty()) return "Ticker obbligatorio."
        return modifica { o ->
            val esistente = trova(o, tk)
            if (nuovo) {
                if (esistente != null) return@modifica "Ticker $tk già presente."
                o.getJSONArray("titoli").put(
                    JSONObject()
                        .put("ticker", tk)
                        .put("descrizione", descrizione.trim())
                        .put("isin", isin.trim())
                        .put("asset_class", asset.trim().ifEmpty { "Altro" })
                        .put("target_pct", target)
                        .put("movimenti", JSONArray())
                        .put("dividendi", JSONArray())
                )
            } else {
                val t = esistente ?: return@modifica "Titolo non trovato."
                t.put("descrizione", descrizione.trim())
                t.put("isin", isin.trim())
                t.put("asset_class", asset.trim().ifEmpty { "Altro" })
                t.put("target_pct", target)
            }
            null
        }
    }

    fun rimuoviTitolo(ticker: String): String? = modifica { o ->
        val a = o.getJSONArray("titoli")
        val i = (0 until a.length()).firstOrNull { a.optJSONObject(it)?.optString("ticker") == ticker }
            ?: return@modifica "Titolo non trovato."
        a.remove(i)
        prezzi.remove(ticker)
        null
    }

    private fun nuovoId(a: JSONArray): String {
        val ids = (0 until a.length()).mapNotNull { a.optJSONObject(it)?.optString("id") }.toSet()
        while (true) {
            val id = java.util.UUID.randomUUID().toString().take(8)
            if (id !in ids) return id
        }
    }

    fun salvaMov(
        ticker: String, id: String?, data: LocalDate?, tipo: String, quantita: Double,
        prezzo: Double, commissioni: Double, note: String,
    ): String? {
        if (data == null) return "Data non valida."
        if (quantita <= 0) return "Quantità non valida."
        if (prezzo < 0 || commissioni < 0) return "Prezzo o commissioni non validi."
        return modifica { o ->
            val t = trova(o, ticker) ?: return@modifica "Titolo non trovato."
            val a = t.optJSONArray("movimenti") ?: JSONArray().also { t.put("movimenti", it) }
            val r = if (id == null) {
                JSONObject().put("id", nuovoId(a)).also { a.put(it) }
            } else {
                (0 until a.length()).firstOrNull { i ->
                    val x = a.optJSONObject(i)
                    x != null && x.optString("id").ifBlank { "#$i" } == id
                }?.let { a.optJSONObject(it) } ?: return@modifica "Movimento non trovato."
            }
            if (r.optString("id").isBlank()) r.put("id", nuovoId(a))
            r.put("data", fmt(data))
            r.put("tipo", tipo)
            r.put("quantita", quantita)
            r.put("prezzo", prezzo)
            r.put("commissioni", commissioni)
            r.put("note", note.trim())
            null
        }
    }

    fun eliminaMov(ticker: String, id: String): String? = modifica { o ->
        val t = trova(o, ticker) ?: return@modifica "Titolo non trovato."
        val a = t.optJSONArray("movimenti") ?: return@modifica "Movimento non trovato."
        val i = (0 until a.length()).firstOrNull { k ->
            val x = a.optJSONObject(k)
            x != null && x.optString("id").ifBlank { "#$k" } == id
        } ?: return@modifica "Movimento non trovato."
        a.remove(i)
        null
    }

    fun salvaDiv(
        ticker: String, indice: Int?, data: LocalDate?, lordo: Double, ritenuta: Double, netto: Double,
    ): String? {
        if (data == null) return "Data non valida."
        if (lordo < 0 || ritenuta < 0 || netto < 0) return "Importi non validi."
        return modifica { o ->
            val t = trova(o, ticker) ?: return@modifica "Titolo non trovato."
            val a = t.optJSONArray("dividendi") ?: JSONArray().also { t.put("dividendi", it) }
            val r = if (indice == null) {
                JSONObject().also { a.put(it) }
            } else a.optJSONObject(indice) ?: return@modifica "Dividendo non trovato."
            r.put("data", fmt(data))
            r.put("importo_lordo", lordo)
            r.put("ritenuta", ritenuta)
            r.put("importo_netto", netto)
            null
        }
    }

    fun eliminaDiv(ticker: String, indice: Int): String? = modifica { o ->
        val t = trova(o, ticker) ?: return@modifica "Titolo non trovato."
        val a = t.optJSONArray("dividendi") ?: return@modifica "Dividendo non trovato."
        if (indice < 0 || indice >= a.length()) return@modifica "Dividendo non trovato."
        a.remove(indice)
        null
    }

    fun ordinaMov(l: List<Pair<String, Mov>>): List<Pair<String, Mov>> =
        l.sortedByDescending { chiaveData(it.second.data) }

    fun ordinaDiv(l: List<Pair<String, Div>>): List<Pair<String, Div>> =
        l.sortedByDescending { chiaveData(it.second.data) }

    fun reset(): String? {
        errore = null
        return try {
            scrivi(vuoto())
            prezzi.clear()
            null
        } catch (e: Exception) {
            "Impossibile azzerare il portafoglio: " + (e.message ?: "errore")
        }
    }

    fun esportaTesto(): String? = try {
        if (file.exists()) file.readText(Charsets.UTF_8) else vuoto().toString(2)
    } catch (_: Exception) {
        null
    }

    fun importaTesto(testo: String): String? {
        val nuovo = try {
            JSONObject(testo)
        } catch (_: Exception) {
            return "File non valido."
        }
        if (nuovo.optJSONArray("titoli") == null) return "File non valido."
        return try {
            scrivi(normalizza(nuovo))
            prezzi.clear()
            errore = null
            null
        } catch (e: Exception) {
            "Impossibile importare: " + (e.message ?: "errore")
        }
    }

    private fun prezzoLive(ticker: String): Double? {
        for (host in listOf("query1", "query2")) {
            try {
                val u = URL(
                    "https://$host.finance.yahoo.com/v8/finance/chart/" +
                        URLEncoder.encode(ticker, "UTF-8") + "?range=1d&interval=1d"
                )
                val c = u.openConnection() as HttpURLConnection
                c.connectTimeout = 10000
                c.readTimeout = 10000
                c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) OrbitaCasa")
                try {
                    if (c.responseCode != 200) continue
                    val j = JSONObject(c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
                    val r = j.getJSONObject("chart").getJSONArray("result").getJSONObject(0)
                    val p = r.getJSONObject("meta").optDouble("regularMarketPrice", Double.NaN)
                    if (!p.isNaN() && p > 0) return p
                } finally {
                    c.disconnect()
                }
            } catch (_: Exception) {
            }
        }
        return null
    }

    class EsitoPrezzi(val aggiornati: Int, val nonTrovati: List<String>)

    fun aggiornaPrezzi(): EsitoPrezzi {
        val tickers = dati().titoli.map { it.ticker }
        val trovati = LinkedHashMap<String, Double>()
        val mancanti = ArrayList<String>()
        for (tk in tickers) {
            val p = prezzoLive(tk)
            if (p != null) trovati[tk] = p else mancanti.add(tk)
        }
        if (trovati.isNotEmpty()) {
            synchronized(prezzi) { prezzi.putAll(trovati) }
            modifica { o ->
                for ((tk, p) in trovati) trova(o, tk)?.put("ultimo_prezzo", p)
                null
            }
        }
        return EsitoPrezzi(trovati.size, mancanti)
    }

    private fun it2(v: Double, dec: Int = 2): String = String.format(java.util.Locale.ITALY, "%,." + dec + "f", v)

    fun riepilogoIa(d: Dati): String {
        val t = totali(d)
        val sb = StringBuilder()
        sb.append("PORTAFOGLIO:\n")
        sb.append("Investito: €").append(it2(t.investito)).append(" | Valore attuale: €").append(it2(t.attuale)).append('\n')
        sb.append("P&L: €").append(it2(t.pl)).append(" (").append(String.format(java.util.Locale.ITALY, "%+.2f", t.plPct))
            .append("%) | Dividendi: €").append(it2(t.dividendi)).append(" | Rendimento reale: €").append(it2(t.rendReale)).append('\n')
        sb.append("Soglia ribilanciamento: ").append(d.soglia).append("%\n\nTITOLI:")
        for (c in t.calcoli) {
            val att = allocazione(c, t.attuale)
            sb.append("\n• ").append(c.ticker).append(" (").append(c.assetClass).append(") — qtà ")
                .append(String.format(java.util.Locale.ITALY, "%.4f", c.quantita))
                .append(", PMC €").append(String.format(java.util.Locale.ITALY, "%.4f", c.pmc))
                .append(", prezzo €").append(String.format(java.util.Locale.ITALY, "%.4f", c.prezzo))
                .append(", valore €").append(it2(c.valore))
                .append(", P&L ").append(String.format(java.util.Locale.ITALY, "%+.2f", c.plPct)).append("%")
                .append(", alloc ").append(String.format(java.util.Locale.ITALY, "%.1f", att))
                .append("% su target ").append(String.format(java.util.Locale.ITALY, "%.1f", c.target)).append("%")
        }
        return sb.toString()
    }

    fun promptPortafoglio(d: Dati): String =
        "Sei un consulente finanziario. Analizza questo portafoglio di investimenti e fornisci: " +
            "1) commento sulla diversificazione, 2) rischi principali, 3) suggerimenti di ribilanciamento concreti, " +
            "4) valutazione generale. Rispondi in italiano, in modo chiaro e diretto.\n\n" + riepilogoIa(d)

    fun promptPl(d: Dati): String =
        "Sei un consulente finanziario. Commenta il P&L di questo portafoglio: spiega se la performance è buona o no, " +
            "confronta con benchmark comuni (es. MSCI World), e dai suggerimenti per migliorare il rendimento. " +
            "Rispondi in italiano.\n\n" + riepilogoIa(d)

    fun promptTicker(desc: String): String =
        "Sei un esperto di mercati finanziari. L'utente cerca un ETF o titolo con questa descrizione: '$desc'. " +
            "Suggerisci 3-5 ticker adatti (con borsa, es. VWCE.AS per Euronext Amsterdam), spiegando brevemente cosa è " +
            "ciascuno e perché è adatto. Tieni conto che l'utente è italiano quindi preferisci titoli disponibili su " +
            "borse europee. Rispondi in italiano."

    fun promptDomanda(d: Dati, dom: String): String =
        "Sei un consulente finanziario. Rispondi a questa domanda dell'utente tenendo conto del suo portafoglio " +
            "attuale. Rispondi in italiano.\n\nPORTAFOGLIO:\n" + riepilogoIa(d) + "\n\nDOMANDA: " + dom
}
