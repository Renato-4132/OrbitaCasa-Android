package it.orbitacasa.mobile

import android.app.Activity
import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object EstrattoImport {
    const val GIORNI_TOLLERANZA = 5
    const val GIORNI_SCADENZA = 22
    const val SOGLIA_DESC = 0.0
    const val CONTO_CORRENTE = "Conto corrente"
    private const val BLOCCO_CSV = 100

    val METODI_CREDITO = listOf("C.Credito 1", "C.Credito 2")

    private val PAROLE_ADDEBITO_CARTA = listOf(
        "NEXI", "CARTASI", "CARTA SI", "ADDEBITO CARTA", "SALDO CARTA",
        "ESTRATTO CARTA", "AMERICAN EXPRESS", "AMEX", "MASTERCARD", "VISA",
        "CARTA DI CREDITO", "CARTA CREDITO", "PAGAMENTO CARTA",
    )

    private val F_ISO = DateTimeFormatter.ISO_LOCAL_DATE
    private val F_ITA = DateTimeFormatter.ofPattern("dd-MM-yyyy")

    class Mov(
        var data: LocalDate,
        var desc: String,
        var importo: Double,
        var categoria: String,
        var metodo: String = "",
    )

    class DbMov(
        val data: LocalDate,
        val descrizione: String,
        val categoria: String,
        val importo: Double,
        val conto: String,
        val metodo: String,
        val tag: String,
        val id: String,
    )

    class Letto(
        val singolo: Boolean,
        val movimenti: List<Mov>,
        val futuri: Int,
        val scartate: Int,
        val blocchiFalliti: Int,
        val righeElaborate: Int,
        val quota: String?,
    )

    class Carta(val riga: Mov, val atteso: Double, val differenza: Double, val mese: String)
    class Quadratura(val estratto: Double, val db: Double, val differenza: Double)

    class Esito(
        val ok: List<Pair<Mov, DbMov>>,
        val mancanti: List<Mov>,
        val inPiu: List<DbMov>,
        val carta: List<Carta>,
        val quadratura: Quadratura?,
    )

    class QuotaGiornaliera(msg: String) : Exception(msg)

    fun attesaQuota(msg: String): String {
        val m = Regex("riprova tra ([0-9hms]+)").find(msg)
        return if (m != null) "Riprova tra ${m.groupValues[1]}." else "Riprova domani o abilita la fatturazione su ai.google.dev."
    }

    private fun s(j: JSONObject, k: String): String =
        if (!j.has(k) || j.isNull(k)) "" else j.optString(k, "").trim()

    private fun vuotoNull(t: String) = t.isEmpty() || t.equals("null", true) || t.equals("none", true)

    private fun regolaCat(categorie: List<String>): String {
        val lista = if (categorie.isEmpty()) "Generica" else categorie.joinToString(", ") { "\"$it\"" }
        return "Assegna a ogni movimento la categoria più adatta scegliendo SOLO tra: [$lista]. " +
            "Se nessuna corrisponde usa 'Generica'."
    }

    fun promptDocumento(categorie: List<String>): String =
        "Analizza questo documento e convertilo in JSON.\n" +
            "REGOLE:\n" +
            "1. Determina se il documento è un DOCUMENTO SINGOLO (fattura, ricevuta, " +
            "bolletta, cedolino, pensione, busta paga: un solo importo da pagare o " +
            "ricevere, anche se mostra voci di dettaglio, trattenute o imposte) oppure " +
            "un ESTRATTO (lista di movimenti bancari o più transazioni distinte).\n" +
            "2. Se ESTRATTO o LISTA MOVIMENTI: ogni movimento separato, " +
            "importo negativo per uscite e positivo per entrate.\n" +
            "3. Se DOCUMENTO SINGOLO: una sola voce con il totale o l'importo netto finale " +
            "(non elencare le singole voci di dettaglio), " +
            "descrizione = nome fornitore, importo sempre negativo (uscita), " +
            "estrai anche numero fattura (campo fattura) e scadenza fattura(GG-MM-AAAA o null). " +
            "Se nel documento compaiono più date etichettate 'scadenza' (es. scadenza del " +
            "pagamento dovuto E scadenza di un'offerta/contratto/promozione), usa SOLO la " +
            "scadenza dell'importo da pagare indicato in bolletta, ignorando le altre.\n" +
            "4. Se la data non è leggibile usa la data di oggi.\n" +
            "5. ${regolaCat(categorie)}\n" +
            "6. Restituisci SOLO un array JSON dove il PRIMO elemento ha anche " +
            "il campo \"tipo_documento\": \"fattura\" oppure \"estratto\": " +
            "[{\"tipo_documento\": \"fattura|estratto\", \"data\": \"YYYY-MM-DD\", \"desc\": \"stringa\", " +
            "\"importo\": float, \"categoria\": \"stringa\", " +
            "\"fattura\": \"stringa o null\", \"scadenza\": \"GG-MM-AAAA o null\"}]."

    private fun promptBloccoCsv(categorie: List<String>, campione: String): String =
        "Analizza queste righe di un CSV bancario e convertile in JSON.\n" +
            "REGOLE:\n" +
            "1. Identifica Data, Descrizione e Importo.\n" +
            "2. Negativi = uscite, Positivi = entrate.\n" +
            "3. ${regolaCat(categorie)}\n" +
            "4. Restituisci SOLO un array JSON, senza testo attorno: " +
            "[{\"data\": \"YYYY-MM-DD\", \"desc\": \"stringa\", \"importo\": float, \"categoria\": \"stringa\"}].\n" +
            "CAMPIONE:\n$campione"

    private fun estraiMov(d: JSONObject, descObbligatoria: Boolean): Mov? {
        return try {
            var desc = if (descObbligatoria) {
                if (!d.has("desc") || d.isNull("desc")) return null
                d.getString("desc")
            } else {
                s(d, "desc").ifEmpty { "Movimento" }
            }
            val fattura = s(d, "fattura")
            val scad = s(d, "scadenza")
            if (!vuotoNull(fattura)) desc += " $fattura"
            if (!vuotoNull(scad)) desc += " SCD:$scad"
            val data = LocalDate.parse(d.getString("data"), F_ISO)
            val imp = d.get("importo").toString().replace(",", ".").toDouble()
            if (imp.isNaN() || imp.isInfinite()) return null
            val cat = s(d, "categoria").ifEmpty { "Generica" }
            Mov(data, desc, imp, cat)
        } catch (_: Exception) {
            null
        }
    }

    private fun estraiArray(raw: String): JSONArray {
        var t = raw.trim()
        if ("```json" in t) t = t.split("```json")[1].split("```")[0].trim()
        else if ("```" in t) t = t.split("```")[1].split("```")[0].trim()
        try {
            return JSONArray(t)
        } catch (_: Exception) {
        }
        try {
            val o = JSONObject(t)
            for (k in o.keys()) {
                val a = o.optJSONArray(k)
                if (a != null) return a
            }
        } catch (_: Exception) {
        }
        val i = t.indexOf('[')
        val j = t.lastIndexOf(']')
        if (i != -1 && j > i) return JSONArray(t.substring(i, j + 1))
        throw Gemini.Errore("Risposta di Gemini non leggibile")
    }

    private fun chiedi(ctx: Context, file: File?, prompt: String): JSONArray =
        estraiArray(Gemini.analizzaGrezzo(ctx, file, prompt))

    private fun quota(e: Exception): String? {
        val m = e.message ?: ""
        if ("HTTP 429" in m && (Regex("retry in (\\d+h[0-9hms.]*)").containsMatchIn(m) || "PerDay" in m)) {
            val a = Regex("retry in (\\d+h[0-9hms.]*)").find(m)
            return "429 quota giornaliera Gemini esaurita" +
                (if (a != null) " (riprova tra ${a.groupValues[1].split('.')[0]}s)" else "")
        }
        return null
    }

    fun leggi(ctx: Context, file: File, categorie: List<String>): Letto {
        val ext = file.extension.lowercase()
        val oggi = LocalDate.now()
        if (ext != "csv" && ext != "txt") {
            val dati = chiedi(ctx, file, promptDocumento(categorie))
            val primo = dati.optJSONObject(0)
            val tipo = if (primo != null) s(primo, "tipo_documento").ifEmpty { "estratto" } else "estratto"
            if (tipo == "fattura" && dati.length() == 1) return Letto(true, emptyList(), 0, 0, 0, 0, null)
            val mov = ArrayList<Mov>()
            var scartate = 0
            for (i in 0 until dati.length()) {
                val d = dati.optJSONObject(i)
                val m = if (d != null) estraiMov(d, false) else null
                if (m == null) scartate++ else mov.add(m)
            }
            val tot = mov.size
            val validi = mov.filter { !it.data.isAfter(oggi) }
            return Letto(false, validi, tot - validi.size, scartate, 0, 0, null)
        }

        val righe = leggiRighe(file)
        if (righe.isEmpty()) throw Gemini.Errore("Il file CSV è vuoto o illeggibile.")
        val intestazione = righe[0]
        var dati = righe.drop(1)
        val chiavi = dati.map { dataRiga(it) }
        var future = 0
        if (dati.isNotEmpty() && chiavi.all { it != null }) {
            val coppie = chiavi.zip(dati).filter { !it.first!!.isAfter(oggi) }
            future = dati.size - coppie.size
            dati = coppie.sortedByDescending { it.first!! }.map { it.second }
        }
        val blocchi = dati.chunked(BLOCCO_CSV).ifEmpty { listOf(emptyList()) }
        val tutti = ArrayList<JSONObject>()
        var falliti = 0
        var quotaMsg: String? = null
        var elaborate = 0
        for ((idx, blocco) in blocchi.withIndex()) {
            if (blocco.isEmpty()) continue
            val campione = intestazione + blocco.joinToString("")
            var arr: JSONArray? = null
            try {
                arr = chiedi(ctx, null, promptBloccoCsv(categorie, campione))
            } catch (e: Exception) {
                val q = quota(e)
                if (q != null) {
                    quotaMsg = q
                    falliti += blocchi.size - idx
                    break
                }
            }
            if (arr == null) {
                falliti++
                continue
            }
            for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { tutti.add(it) }
            elaborate += blocco.size
        }
        val mov = ArrayList<Mov>()
        var scartate = 0
        for (d in tutti) {
            val m = estraiMov(d, true)
            if (m == null) scartate++ else mov.add(m)
        }
        val tot = mov.size
        val validi = mov.filter { !it.data.isAfter(oggi) }
        future += tot - validi.size
        return Letto(false, validi, future, scartate, falliti, elaborate, quotaMsg)
    }

    private fun leggiRighe(f: File): List<String> {
        val bytes = f.readBytes()
        val testo = try {
            val dec = Charsets.UTF_8.newDecoder()
            dec.decode(java.nio.ByteBuffer.wrap(bytes)).toString()
        } catch (_: Exception) {
            String(bytes, Charsets.ISO_8859_1)
        }
        val out = ArrayList<String>()
        var inizio = 0
        while (inizio < testo.length) {
            val fine = testo.indexOf('\n', inizio)
            if (fine == -1) {
                out.add(testo.substring(inizio))
                break
            }
            out.add(testo.substring(inizio, fine + 1))
            inizio = fine + 1
        }
        return out
    }

    private fun dataRiga(r: String): LocalDate? {
        Regex("(\\d{4})-(\\d{2})-(\\d{2})").find(r)?.let {
            return try {
                LocalDate.of(it.groupValues[1].toInt(), it.groupValues[2].toInt(), it.groupValues[3].toInt())
            } catch (_: Exception) {
                null
            }
        }
        Regex("(\\d{1,2})[/.-](\\d{1,2})[/.-](\\d{4})").find(r)?.let {
            return try {
                LocalDate.of(it.groupValues[3].toInt(), it.groupValues[2].toInt(), it.groupValues[1].toInt())
            } catch (_: Exception) {
                null
            }
        }
        return null
    }

    private fun norm(t: String): String =
        Regex("[^A-Z0-9 ]+").replace(t.uppercase(), " ").trim()

    private fun ratio(a: String, b: String): Double {
        val n = a.length + b.length
        if (n == 0) return 1.0
        return 2.0 * corrispondenze(a, 0, a.length, b, 0, b.length) / n
    }

    private fun corrispondenze(a: String, alo: Int, ahi: Int, b: String, blo: Int, bhi: Int): Int {
        if (alo >= ahi || blo >= bhi) return 0
        var bi = alo
        var bj = blo
        var bs = 0
        var prev = IntArray(bhi - blo + 1)
        for (i in alo until ahi) {
            val cur = IntArray(bhi - blo + 1)
            for (j in blo until bhi) {
                if (a[i] == b[j]) {
                    val k = prev[j - blo] + 1
                    cur[j - blo + 1] = k
                    if (k > bs) {
                        bi = i - k + 1
                        bj = j - k + 1
                        bs = k
                    }
                }
            }
            prev = cur
        }
        if (bs == 0) return 0
        return bs + corrispondenze(a, alo, bi, b, blo, bj) +
            corrispondenze(a, bi + bs, ahi, b, bj + bs, bhi)
    }

    private fun sim(x: String, y: String): Double {
        val a = norm(x)
        val b = norm(y)
        if (a.isEmpty() || b.isEmpty()) return 0.0
        var r = ratio(a, b)
        val ta = a.split(" ").filter { it.isNotEmpty() }.toSet()
        val tb = b.split(" ").filter { it.isNotEmpty() }.toSet()
        if (ta.isNotEmpty() && tb.isNotEmpty()) {
            r = max(r, ta.intersect(tb).size.toDouble() / min(ta.size, tb.size) * 0.9)
        }
        return r
    }

    private fun metodoPulito(m: String): String {
        val t = m.trim()
        for ((_, simb) in Repo.METODI) {
            if (simb.isNotEmpty() && t.startsWith("$simb ")) return t.substring(simb.length + 1).trim()
        }
        return t
    }

    fun movimentiDb(conto: String?, metodo: String?, escludi: List<String> = emptyList()): List<DbMov> {
        val out = ArrayList<DbMov>()
        for (v in Repo.voci()) {
            if (conto != null && v.conto != conto) continue
            val met = metodoPulito(v.metodo)
            if (metodo != null && met != metodo) continue
            if (met in escludi) continue
            val imp = if (v.tipo == "Entrata") v.importo else -v.importo
            val tag = v.hashtag.joinToString(", ") { if (it.startsWith("#")) it else "#$it" }
            out.add(DbMov(v.data, v.descrizione, v.categoria, imp, v.conto, met, tag, v.id))
        }
        return out
    }

    private class Coppia(val score: Double, val i: Int, val j: Int)

    private fun riconcilia(
        righe: List<Mov>, db: List<DbMov>, giorni: Int,
    ): Triple<List<Pair<Mov, DbMov>>, List<Mov>, List<DbMov>> {
        if (righe.isEmpty()) return Triple(emptyList(), emptyList(), emptyList())
        val dMin = righe.minOf { it.data }
        val dMax = righe.maxOf { it.data }
        val cand = db.filter {
            (!it.data.isBefore(dMin.minusDays(giorni.toLong())) && !it.data.isAfter(dMax.plusDays(giorni.toLong()))) ||
                ("SCD:" in it.descrizione.uppercase() &&
                    !it.data.isBefore(dMin.minusDays(giorni.toLong())) &&
                    !it.data.isAfter(dMax.plusDays(GIORNI_SCADENZA.toLong())))
        }
        val coppie = ArrayList<Coppia>()
        for ((i, r) in righe.withIndex()) {
            for ((j, m) in cand.withIndex()) {
                if (abs(r.importo - m.importo) > 0.005) continue
                var dd = abs(ChronoUnit.DAYS.between(m.data, r.data)).toInt()
                var daScad = false
                var anticipo = 0
                if (dd > giorni) {
                    anticipo = ChronoUnit.DAYS.between(r.data, m.data).toInt()
                    if (!("SCD:" in m.descrizione.uppercase() && anticipo > 0 && anticipo <= GIORNI_SCADENZA)) continue
                    daScad = true
                    dd = giorni
                }
                val sc = sim(r.desc, m.descrizione)
                if (sc < SOGLIA_DESC) continue
                var score = 0.6 * sc + 0.4 * (1 - dd.toDouble() / (giorni + 1))
                if (daScad) score -= anticipo * 0.001
                coppie.add(Coppia(score, i, j))
            }
        }
        val ord = coppie.sortedByDescending { it.score }
        val usateR = HashSet<Int>()
        val usateM = HashSet<Int>()
        val abbinate = java.util.TreeMap<Int, Int>()
        for (c in ord) {
            if (c.i in usateR || c.j in usateM) continue
            usateR.add(c.i)
            usateM.add(c.j)
            abbinate[c.i] = c.j
        }
        val ok = abbinate.map { (i, j) -> righe[i] to cand[j] }
        val mancanti = righe.filterIndexed { i, _ -> i !in abbinate }
        val inPiu = cand.filterIndexed { j, m -> j !in usateM && !m.data.isBefore(dMin) && !m.data.isAfter(dMax) }
        return Triple(ok, mancanti, inPiu)
    }

    private fun mesePrecedente(d: LocalDate): Pair<Int, Int> =
        if (d.monthValue == 1) Pair(d.year - 1, 12) else Pair(d.year, d.monthValue - 1)

    private fun r2(x: Double) = Math.round(x * 100.0) / 100.0

    fun riconciliaEstratto(righe: List<Mov>, conto: String?, metodo: String?, giorni: Int): Esito {
        val db = if (metodo != null) movimentiDb(conto, metodo)
        else movimentiDb(conto, null, METODI_CREDITO)
        val (ok, mancanti, inPiu) = riconcilia(righe, db, giorni)
        if (metodo != null) {
            var quad: Quadratura? = null
            if (righe.isNotEmpty()) {
                val dMin = righe.minOf { it.data }
                val dMax = righe.maxOf { it.data }
                val totE = r2(righe.sumOf { it.importo })
                val totD = r2(db.filter { !it.data.isBefore(dMin) && !it.data.isAfter(dMax) }.sumOf { it.importo })
                quad = Quadratura(totE, totD, r2(totE - totD))
            }
            return Esito(ok, mancanti, inPiu, emptyList(), quad)
        }
        val veri = ArrayList<Mov>()
        val carte = ArrayList<Carta>()
        var tutteCredito: List<DbMov>? = null
        for (r in mancanti) {
            val testo = norm(r.desc)
            if (r.importo < 0 && PAROLE_ADDEBITO_CARTA.any { it in testo }) {
                if (tutteCredito == null) {
                    tutteCredito = METODI_CREDITO.flatMap { movimentiDb(null, it) }
                }
                val (anno, mese) = mesePrecedente(r.data)
                val atteso = r2(tutteCredito.filter { it.data.year == anno && it.data.monthValue == mese }.sumOf { it.importo })
                carte.add(Carta(r, atteso, r2(r.importo - atteso), String.format("%02d/%d", mese, anno)))
            } else {
                veri.add(r)
            }
        }
        return Esito(ok, veri, inPiu, carte, null)
    }

    fun fmt(v: Double): String =
        String.format(java.util.Locale.ITALY, "%,.2f", v)

    class RigaTab(val stato: String, val data: String, val desc: String, val importo: String,
                  val conto: String, val metodo: String, val tag: String, val db: String)

    fun righeTabella(es: Esito, filtro: String): List<RigaTab> {
        val out = ArrayList<RigaTab>()
        if (filtro == "Tutti" || filtro == "Mancanti") {
            for (r in es.mancanti.sortedBy { it.data }) {
                out.add(RigaTab("MANCA", r.data.format(F_ITA), r.desc, fmt(r.importo), "", "", "", "—"))
            }
        }
        if (filtro == "Tutti" || filtro == "Addebiti carta") {
            for (ad in es.carta) {
                val nota = if (abs(ad.differenza) < 0.01)
                    "Quadra con le carte di credito ${ad.mese} nel DB (${fmt(ad.atteso)} €)"
                else "Carte di credito ${ad.mese} nel DB: ${fmt(ad.atteso)} € → differenza ${fmt(ad.differenza)} €"
                out.add(RigaTab("ADDEB. CARTA", ad.riga.data.format(F_ITA), ad.riga.desc, fmt(ad.riga.importo), "", "", "", nota))
            }
        }
        if (filtro == "Tutti" || filtro == "In più") {
            for (m in es.inPiu.sortedBy { it.data }) {
                out.add(RigaTab("IN PIÙ", m.data.format(F_ITA), "—", fmt(m.importo), m.conto, m.metodo, m.tag,
                    (if (m.categoria.isNotEmpty()) m.categoria + " · " else "") + m.descrizione))
            }
        }
        if (filtro == "Tutti" || filtro == "Riconciliati") {
            for ((r, m) in es.ok) {
                val dd = ChronoUnit.DAYS.between(m.data, r.data).toInt()
                val nota = (if (m.categoria.isNotEmpty()) m.categoria + " · " else "") + m.descrizione +
                    (if (dd != 0) "  [data DB ${String.format("%02d-%02d", m.data.dayOfMonth, m.data.monthValue)}, ${if (dd > 0) "+" else ""}${dd}g]" else "")
                out.add(RigaTab("OK", r.data.format(F_ITA), r.desc, fmt(r.importo), m.conto, m.metodo, m.tag, nota))
            }
        }
        return out
    }

    fun idPerRiga(es: Esito, r: RigaTab): String? {
        if (r.stato == "IN PIÙ") {
            return es.inPiu.firstOrNull { it.data.format(F_ITA) == r.data && fmt(it.importo) == r.importo }?.id
        }
        if (r.stato == "OK") {
            return es.ok.firstOrNull { it.first.data.format(F_ITA) == r.data && fmt(it.first.importo) == r.importo && it.first.desc == r.desc }?.second?.id
        }
        return null
    }

    fun testata(es: Esito, righe: List<Mov>, metodoSel: String, contoSel: String, giorni: Int, filtro: String): List<String> {
        val totManc = es.mancanti.sumOf { it.importo }
        val totPiu = es.inPiu.sumOf { it.importo }
        val l = ArrayList<String>()
        l.add("Estratto di: $metodoSel | Conto: $contoSel | Tolleranza: ${giorni}g | Mostra: $filtro")
        var t = "Estratto: ${righe.size} righe | ${es.ok.size} riconciliate | " +
            "${es.mancanti.size} mancanti (${fmt(totManc)} €) | ${es.inPiu.size} in più nel DB (${fmt(totPiu)} €)"
        if (es.carta.isNotEmpty()) t += " | ${es.carta.size} addebiti carta"
        l.add(t)
        es.quadratura?.let {
            l.add("Quadratura $metodoSel: estratto ${fmt(it.estratto)} € — database ${fmt(it.db)} € = differenza ${fmt(it.differenza)} €")
        }
        return l
    }

    private fun tronca(t: String, n: Int) = if (t.length <= n) t else t.substring(0, n - 1) + "…"

    fun testoTabella(righe: List<RigaTab>): List<String> {
        if (righe.isEmpty()) return emptyList()
        val etichette = listOf("Esito", "Data", "Descrizione", "Importo €", "Conto", "Metodo", "Tag", "Corrispondenza nel database")
        val maxC = mapOf(2 to 32, 4 to 14, 5 to 12, 6 to 16, 7 to 38)
        val dati = righe.map { r ->
            listOf(r.stato, r.data, r.desc, r.importo, r.conto, r.metodo, r.tag, r.db)
                .mapIndexed { k, x -> tronca(x, maxC[k] ?: 99) }
        }
        val facoltative = setOf(4, 5, 6)
        val usa = etichette.indices.filter { k -> !(k in facoltative && dati.all { it[k] == "" || it[k] == "—" }) }
        val larg = usa.associateWith { k -> max(etichette[k].length, dati.maxOf { it[k].length }) }
        fun cella(v: List<String>, k: Int) = if (k == 3) v[k].padStart(larg[k]!!) else v[k].padEnd(larg[k]!!)
        fun f(v: List<String>) = usa.joinToString(" | ") { cella(v, it) }.trimEnd()
        return listOf(f(etichette), usa.joinToString("-+-") { "-".repeat(larg[it]!!) }) + dati.map { f(it) }
    }

    fun testoEsportazione(es: Esito, righe: List<Mov>, tab: List<RigaTab>,
                          metodoSel: String, contoSel: String, giorni: Int, filtro: String): String {
        val lines = testoTabella(tab)
        if (lines.isEmpty()) return ""
        val ora = LocalDateTime.now()
        val wTab = lines.maxOf { it.length }
        val cab = testata(es, righe, metodoSel, contoSel, giorni, filtro)
            .flatMap { wrap(it, max(wTab, 60)) }
        return "CONTROLLO ESTRATTO CONTO - " + ora.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) + "\n\n" +
            cab.joinToString("\n") + "\n\n" + lines.joinToString("\n") + "\n"
    }

    private fun wrap(t: String, w: Int): List<String> {
        val out = ArrayList<String>()
        var cur = ""
        for (p in t.split(" ")) {
            if (cur.isEmpty()) cur = p
            else if (cur.length + 1 + p.length <= w) cur += " $p"
            else {
                out.add(cur)
                cur = p
            }
        }
        if (cur.isNotEmpty()) out.add(cur)
        return out
    }

    fun creaPdf(act: Activity, testo: String): File {
        val lines = testo.replace("→", "->").split("\n")
        val doc = PdfDocument()
        val pw = 842
        val ph = 595
        val mg = 30f
        val maxLen = max(1, lines.maxOf { it.length })
        val fs = max(4f, min(7f, (pw - 2 * mg) / (maxLen * 0.6f)))
        val lh = fs + 2f
        val p = Paint()
        p.typeface = Typeface.MONOSPACE
        p.textSize = fs
        p.color = android.graphics.Color.BLACK
        var n = 1
        var pag = doc.startPage(PdfDocument.PageInfo.Builder(pw, ph, n).create())
        var y = mg
        for (l in lines) {
            if (y > ph - mg) {
                doc.finishPage(pag)
                n++
                pag = doc.startPage(PdfDocument.PageInfo.Builder(pw, ph, n).create())
                y = mg
            }
            pag.canvas.drawText(l, mg, y, p)
            y += lh
        }
        doc.finishPage(pag)
        val dir = File(act.cacheDir, "pdf").apply { mkdirs() }
        val ora = LocalDateTime.now()
        val f = File(dir, "Controllo_Estratto_" + ora.format(DateTimeFormatter.ofPattern("dd-MM-yyyy_HHmm")) + ".pdf")
        try {
            FileOutputStream(f).use { doc.writeTo(it) }
        } finally {
            doc.close()
        }
        return f
    }
}
