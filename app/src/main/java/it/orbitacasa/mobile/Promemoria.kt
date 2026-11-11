package it.orbitacasa.mobile

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

object Prom {
    const val FILE = "promemoria.json"

    val TIPI = listOf("Appuntamento", "Scadenza")
    val RIPETIZIONI = listOf("Nessuna", "Giornaliera", "Settimanale", "Mensile", "Annuale")
    val PREAVVISI = listOf(
        "Il giorno stesso" to 0, "1 giorno prima" to 1, "3 giorni prima" to 3,
        "7 giorni prima" to 7, "14 giorni prima" to 14, "30 giorni prima" to 30,
    )
    val ANTICIPI_ORA = listOf(
        "5 minuti prima" to 5, "15 minuti prima" to 15, "30 minuti prima" to 30,
        "1 ora prima" to 60, "2 ore prima" to 120,
    )
    const val ANTICIPO_ORA_MIN = 15
    const val MAX_TITOLO = 35
    const val MAX_NOTE = 150
    const val LIMITE_SCADUTI_GG = 60
    const val LIMITE_RIPETUTE_APPUNTAMENTI_GG = 14
    const val LIMITE_RIPETUTE_SCADENZE_GG = 366

    private val GIORNI = listOf("lun", "mar", "mer", "gio", "ven", "sab", "dom")
    private val WD = listOf("MO", "TU", "WE", "TH", "FR", "SA", "SU")
    private val UNITA_SING = mapOf("giorni" to "giorno", "settimane" to "settimana", "mesi" to "mese", "anni" to "anno")
    private val FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy")

    private lateinit var file: File

    var errore: String? = null
        private set

    fun init(dir: File) {
        file = File(dir, FILE)
    }

    fun fmtData(d: LocalDate): String = d.format(FMT)

    private fun radice(): JSONObject {
        errore = null
        if (!file.exists() || file.length() == 0L) return JSONObject()
        return try {
            JSONObject(file.readText(Charsets.UTF_8))
        } catch (e: Exception) {
            try {
                Files.copy(file.toPath(), File(file.parentFile, "$FILE.corrotto").toPath(), StandardCopyOption.REPLACE_EXISTING)
            } catch (_: Exception) {
            }
            errore = "$FILE risulta illeggibile (copia in $FILE.corrotto): nessuna modifica effettuata"
            JSONObject()
        }
    }

    private fun normalizza(v: JSONObject): JSONObject? {
        val d = v.optString("data", "")
        try {
            LocalDate.parse(d)
        } catch (_: Exception) {
            return null
        }
        if (!v.has("id") || v.optString("id").isEmpty()) v.put("id", nuovoId())
        if (!v.has("titolo")) v.put("titolo", "")
        if (!v.has("tipo")) v.put("tipo", "Appuntamento")
        if (!v.has("ora")) v.put("ora", "")
        if (!v.has("ripetizione")) v.put("ripetizione", "Nessuna")
        if (!v.has("note")) v.put("note", "")
        if (v.optJSONArray("fatti") == null) v.put("fatti", JSONArray())
        if (v.optJSONArray("escluse") == null) v.put("escluse", JSONArray())
        for (k in listOf("ora_fine", "data_fine", "luogo", "rip_fine", "rrule")) {
            if (v.opt(k) !is String) v.put(k, "")
        }
        v.put("preavviso", v.optInt("preavviso", 0).coerceAtLeast(0))
        v.put("anticipo_min", anticipoMin(v))
        return v
    }

    fun agenda(): List<JSONObject> {
        val a = radice().optJSONArray("agenda") ?: return emptyList()
        return (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let { normalizza(it) } }
    }

    fun nota(): String = radice().optString("note", "")

    private fun scrivi(root: JSONObject, voci: List<JSONObject>) {
        val arr = JSONArray()
        voci.forEach { arr.put(it) }
        root.put("agenda", arr)
        if (!root.has("note")) root.put("note", "")
        if (!root.has("fonti")) root.put("fonti", JSONObject())
        if (!root.has("digest")) root.put("digest", JSONObject().put("attivo", false).put("ora", "08:00"))
        root.put("promemoria", testoRiepilogo(root.optString("note", ""), voci))
        val tmp = File(file.parentFile, "$FILE.tmp")
        tmp.writeText(root.toString(4), Charsets.UTF_8)
        Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }

    private fun modifica(blocco: (MutableList<JSONObject>) -> String?): String? {
        val root = radice()
        errore?.let { return it }
        val voci = agenda().toMutableList()
        val err = blocco(voci)
        if (err == null) {
            try {
                scrivi(root, voci)
            } catch (e: Exception) {
                return "Salvataggio non riuscito: " + e.message
            }
        }
        return err
    }

    fun nuovoId(): String = UUID.randomUUID().toString().replace("-", "").substring(0, 8)

    private fun tocca(v: JSONObject) {
        v.put("seq", v.optInt("seq", 0) + 1)
        v.put("modificata", java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")))
    }

    fun data(v: JSONObject): LocalDate = LocalDate.parse(v.optString("data"))

    fun anticipoMin(v: JSONObject): Int {
        val n = v.optInt("anticipo_min", ANTICIPO_ORA_MIN)
        return n.coerceIn(1, 1440)
    }

    fun durataGg(v: JSONObject): Int {
        return try {
            val df = v.optString("data_fine", "")
            if (df.isNotEmpty()) java.time.temporal.ChronoUnit.DAYS.between(data(v), LocalDate.parse(df)).toInt().coerceIn(0, 366) else 0
        } catch (_: Exception) {
            0
        }
    }

    fun fatta(v: JSONObject, d: LocalDate): Boolean {
        val f = v.optJSONArray("fatti") ?: return false
        val k = d.toString()
        return (0 until f.length()).any { f.optString(it) == k }
    }

    private fun escluse(v: JSONObject, iso: String): Boolean {
        val e = v.optJSONArray("escluse") ?: return false
        return (0 until e.length()).any { e.optString(it) == iso }
    }

    private fun ripParams(v: JSONObject): Pair<Int, String>? {
        val n = v.optInt("rip_n", 1)
        val u = v.optString("rip_unita", "")
        if (n < 1 || n > 99 || u !in UNITA_SING) return null
        return n to u
    }

    private class Rr(
        val freq: String, val n: Int, val byday: List<Pair<Int, Int>>, val bymd: List<Int>,
        val bymo: List<Int>, val setpos: List<Int>, val wkst: Int,
    )

    private val RE_BYDAY = Regex("([+-]?\\d{1,2})?(MO|TU|WE|TH|FR|SA|SU)")

    private fun parseRrule(s: String): Rr {
        val r = HashMap<String, String>()
        for (p in s.split(";")) {
            val i = p.indexOf('=')
            if (i > 0) r[p.substring(0, i).trim().uppercase()] = p.substring(i + 1).trim().uppercase()
        }
        val n = maxOf(1, r["INTERVAL"]?.toIntOrNull() ?: 1)
        val byday = ArrayList<Pair<Int, Int>>()
        for (x in (r["BYDAY"] ?: "").split(",")) {
            val m = RE_BYDAY.matchEntire(x.trim()) ?: continue
            byday.add((m.groupValues[1].toIntOrNull() ?: 0) to WD.indexOf(m.groupValues[2]))
        }
        fun lista(k: String) = (r[k] ?: "").split(",").mapNotNull { it.trim().toIntOrNull() }
        val wk = r["WKST"]?.let { WD.indexOf(it) }?.takeIf { it >= 0 } ?: 0
        return Rr(r["FREQ"] ?: "", n, byday, lista("BYMONTHDAY"), lista("BYMONTH"), lista("BYSETPOS"), wk)
    }

    private fun giorniMese(R: Rr, y: Int, m: Int, d0: LocalDate): List<Int> {
        val ultimo = YearMonth.of(y, m).lengthOfMonth()
        var giorni = HashSet<Int>()
        fun wdDi(g: Int) = LocalDate.of(y, m, g).dayOfWeek.value - 1
        if (R.bymd.isNotEmpty()) {
            for (x in R.bymd) {
                val g = if (x > 0) x else ultimo + 1 + x
                if (g in 1..ultimo) giorni.add(g)
            }
            if (R.byday.isNotEmpty()) {
                val wds = R.byday.map { it.second }.toSet()
                giorni = giorni.filter { wdDi(it) in wds }.toHashSet()
            }
        } else if (R.byday.isNotEmpty()) {
            for ((o, wd) in R.byday) {
                val dd = (1..ultimo).filter { wdDi(it) == wd }
                when {
                    o == 0 -> giorni.addAll(dd)
                    o > 0 && o <= dd.size -> giorni.add(dd[o - 1])
                    o < 0 && -o <= dd.size -> giorni.add(dd[dd.size + o])
                }
            }
        } else if (d0.dayOfMonth <= ultimo) {
            giorni.add(d0.dayOfMonth)
        }
        var lista = giorni.sorted()
        if (R.setpos.isNotEmpty() && lista.isNotEmpty()) {
            lista = R.setpos.filter { it != 0 && Math.abs(it) <= lista.size }
                .map { if (it > 0) lista[it - 1] else lista[lista.size + it] }.toSortedSet().toList()
        }
        return lista
    }

    private fun mdOk(R: Rr, d: LocalDate): Boolean {
        if (R.bymd.isEmpty()) return true
        val ultimo = d.lengthOfMonth()
        return d.dayOfMonth in R.bymd.map { if (it > 0) it else ultimo + 1 + it }
    }

    private fun occorreRrule(rrule: String, d0: LocalDate, d: LocalDate): Boolean {
        if (d.isBefore(d0)) return false
        if (d == d0) return true
        val R = parseRrule(rrule)
        val f = R.freq
        val n = R.n
        if (R.bymo.isNotEmpty() && d.monthValue !in R.bymo && f != "YEARLY") return false
        val giorni = java.time.temporal.ChronoUnit.DAYS.between(d0, d)
        when (f) {
            "DAILY" -> {
                if (giorni % n != 0L) return false
                if (R.byday.isNotEmpty() && (d.dayOfWeek.value - 1) !in R.byday.map { it.second }) return false
                return mdOk(R, d)
            }
            "WEEKLY" -> {
                fun inizio(x: LocalDate) = x.minusDays((((x.dayOfWeek.value - 1) - R.wkst + 7) % 7).toLong())
                val sett = java.time.temporal.ChronoUnit.DAYS.between(inizio(d0), inizio(d)) / 7
                if (sett % n != 0L) return false
                val wds = R.byday.map { it.second }.toSet().ifEmpty { setOf(d0.dayOfWeek.value - 1) }
                return (d.dayOfWeek.value - 1) in wds
            }
            "MONTHLY" -> {
                val mesi = (d.year - d0.year) * 12 + d.monthValue - d0.monthValue
                if (mesi % n != 0) return false
                return d.dayOfMonth in giorniMese(R, d.year, d.monthValue, d0)
            }
            "YEARLY" -> {
                if ((d.year - d0.year) % n != 0 || d.monthValue !in (R.bymo.ifEmpty { listOf(d0.monthValue) })) return false
                return d.dayOfMonth in giorniMese(R, d.year, d.monthValue, d0)
            }
        }
        return false
    }

    private fun testoRrule(s: String): String {
        val R = parseRrule(s)
        val u = mapOf(
            "DAILY" to ("giorno" to "giorni"), "WEEKLY" to ("settimana" to "settimane"),
            "MONTHLY" to ("mese" to "mesi"), "YEARLY" to ("anno" to "anni"),
        )[R.freq] ?: return "personalizzata"
        val t = if (R.n == 1) "ogni ${u.first}" else "ogni ${R.n} ${u.second}"
        val det = ArrayList<String>()
        if (R.byday.isNotEmpty()) {
            det.add(R.byday.joinToString(", ") { (o, wd) ->
                val nome = GIORNI[wd]
                when {
                    o == 0 -> nome
                    o == -1 -> "ultimo $nome"
                    o < 0 -> "${-o}° $nome dalla fine"
                    else -> "$o° $nome"
                }
            })
        }
        if (R.bymd.isNotEmpty()) det.add("giorno " + R.bymd.joinToString(", ") { if (it == -1) "ultimo" else it.toString() })
        if (R.bymo.isNotEmpty()) det.add("mesi " + R.bymo.joinToString(", "))
        return t + if (det.isNotEmpty()) " (" + det.joinToString("; ") + ")" else ""
    }

    private fun giorniDaRrule(s: String): List<Int>? {
        if (s.isEmpty()) return null
        val R = parseRrule(s)
        if (R.freq != "WEEKLY" || R.byday.isEmpty() || R.byday.any { it.first != 0 } ||
            R.bymd.isNotEmpty() || R.bymo.isNotEmpty() || R.setpos.isNotEmpty()
        ) return null
        return R.byday.map { it.second }.toSortedSet().toList()
    }

    fun occorreInizio(v: JSONObject, d: LocalDate): Boolean {
        val d0 = try { data(v) } catch (_: Exception) { return false }
        if (d.isBefore(d0)) return false
        val iso = d.toString()
        val rf = v.optString("rip_fine", "")
        if ((rf.isNotEmpty() && iso > rf) || escluse(v, iso)) return false
        val rr = v.optString("rrule", "")
        if (rr.isNotEmpty()) return occorreRrule(rr, d0, d)
        val ultimo = d.lengthOfMonth()
        val giorni = java.time.temporal.ChronoUnit.DAYS.between(d0, d)
        return when (v.optString("ripetizione", "Nessuna")) {
            "Nessuna" -> d == d0
            "Giornaliera" -> true
            "Settimanale" -> giorni % 7 == 0L
            "Mensile" -> d.dayOfMonth == minOf(d0.dayOfMonth, ultimo)
            "Annuale" -> d.monthValue == d0.monthValue && d.dayOfMonth == minOf(d0.dayOfMonth, ultimo)
            "Personalizzata" -> {
                val (n, u) = ripParams(v) ?: return d == d0
                when (u) {
                    "giorni" -> giorni % n == 0L
                    "settimane" -> giorni % (7L * n) == 0L
                    "mesi" -> ((d.year - d0.year) * 12 + d.monthValue - d0.monthValue) % n == 0 &&
                        d.dayOfMonth == minOf(d0.dayOfMonth, ultimo)
                    else -> (d.year - d0.year) % n == 0 && d.monthValue == d0.monthValue &&
                        d.dayOfMonth == minOf(d0.dayOfMonth, ultimo)
                }
            }
            else -> false
        }
    }

    fun occorre(v: JSONObject, d: LocalDate): Boolean {
        for (k in 0..durataGg(v)) if (occorreInizio(v, d.minusDays(k.toLong()))) return true
        return false
    }

    private fun chiaveOrdine(v: JSONObject): String = v.optString("ora", "") + "\u0000" + v.optString("titolo", "").lowercase()

    class Occ(val data: LocalDate, val voce: JSONObject)

    private val ordine = compareBy<Occ>({ it.data }, { chiaveOrdine(it.voce) })

    fun occorrenze(agenda: List<JSONObject>, d1: LocalDate, d2: LocalDate): List<Occ> {
        val res = ArrayList<Occ>()
        var g = d1
        while (!g.isAfter(d2)) {
            val voci = agenda.filter { occorre(it, g) }.sortedBy { chiaveOrdine(it) }
            voci.forEach { res.add(Occ(g, it)) }
            g = g.plusDays(1)
        }
        return res
    }

    fun scaduti(agenda: List<JSONObject>, oggi: LocalDate): List<Occ> {
        val ieri = oggi.minusDays(1)
        val res = ArrayList<Occ>()
        for (v in agenda) {
            val d0 = try { data(v) } catch (_: Exception) { continue }
            if (v.optString("ripetizione", "Nessuna") == "Nessuna") {
                val df = d0.plusDays(durataGg(v).toLong())
                if (!df.isBefore(oggi.minusDays(LIMITE_SCADUTI_GG.toLong())) && !df.isAfter(ieri) && !fatta(v, d0)) {
                    res.add(Occ(d0, v))
                }
                continue
            }
            val limite = if (v.optString("tipo") == "Scadenza") LIMITE_RIPETUTE_SCADENZE_GG else LIMITE_RIPETUTE_APPUNTAMENTI_GG
            for (k in 0..limite) {
                val d = ieri.minusDays(k.toLong())
                if (d.isBefore(d0)) break
                if (occorreInizio(v, d)) {
                    if (!fatta(v, d)) res.add(Occ(d, v))
                    break
                }
            }
        }
        return res.sortedWith(ordine)
    }

    fun anticipi(agenda: List<JSONObject>, oggi: LocalDate): List<Occ> {
        val res = ArrayList<Occ>()
        for (v in agenda) {
            val pre = v.optInt("preavviso", 0)
            for (k in 1..pre) {
                val d = oggi.plusDays(k.toLong())
                if (occorreInizio(v, d) && !fatta(v, d)) {
                    res.add(Occ(d, v))
                    break
                }
            }
        }
        return res.sortedWith(ordine)
    }

    fun fmtEur(x: Double): String = String.format(Locale.ITALY, "%,.2f", x) + " €"

    fun importo(v: JSONObject): Double? {
        if (!v.has("importo") || v.isNull("importo")) return null
        val d = v.optDouble("importo", Double.NaN)
        return if (d.isNaN()) null else d
    }

    fun parseImporto(s: String): Double? {
        var t = s.replace("€", "").trim()
        if (t.isEmpty()) return null
        if (t.contains(",")) t = t.replace(".", "").replace(",", ".")
        return t.toDoubleOrNull()
    }

    fun normalizzaOra(testo: String): String? {
        val s = testo.trim().replace('.', ':').replace(',', ':').replace(" ", "")
        if (s.isEmpty()) return ""
        val h: String
        val m: String
        if (s.contains(':')) {
            h = s.substringBefore(':')
            m = s.substringAfter(':').ifEmpty { "00" }
        } else if (s.all { it.isDigit() }) {
            when (s.length) {
                1, 2 -> { h = s; m = "00" }
                3 -> { h = s.substring(0, 1); m = s.substring(1) }
                4 -> { h = s.substring(0, 2); m = s.substring(2) }
                else -> return null
            }
        } else return null
        if (!h.all { it.isDigit() } || !m.all { it.isDigit() } || h.isEmpty()) return null
        val hh = h.toInt()
        val mm = m.toInt()
        if (hh !in 0..23 || mm !in 0..59) return null
        return String.format(Locale.ITALY, "%02d:%02d", hh, mm)
    }

    fun oraDi(v: JSONObject): LocalTime? = try {
        val o = v.optString("ora", "").trim()
        if (o.isEmpty()) null else LocalTime.parse(o, DateTimeFormatter.ofPattern("H:mm"))
    } catch (_: Exception) {
        null
    }

    fun ripTesto(v: JSONObject): String {
        val r = v.optString("ripetizione", "Nessuna")
        val rr = v.optString("rrule", "")
        var t = when {
            r == "Avanzata" -> testoRrule(rr)
            r == "Personalizzata" -> ripParams(v)?.let { (n, u) -> if (n == 1) "ogni ${UNITA_SING[u]}" else "ogni $n $u" } ?: "personalizzata"
            else -> r.lowercase()
        }
        val gg = giorniDaRrule(rr)
        if (gg != null && r != "Avanzata") t += " (" + gg.joinToString(", ") { GIORNI[it] } + ")"
        val rf = v.optString("rip_fine", "")
        if (rf.isNotEmpty()) try { t += " fino al " + fmtData(LocalDate.parse(rf)) } catch (_: Exception) { }
        return t
    }

    fun testoFine(v: JSONObject): String {
        val df = v.optString("data_fine", "")
        val of = v.optString("ora_fine", "")
        try {
            if (df.isNotEmpty()) return "fino al " + fmtData(LocalDate.parse(df)) + if (of.isNotEmpty()) " $of" else ""
        } catch (_: Exception) {
        }
        return if (of.isNotEmpty()) "fino alle $of" else ""
    }

    private fun rigaVoce(d: LocalDate, v: JSONObject): String {
        val p = ArrayList<String>()
        p.add(fmtData(d))
        if (v.optString("ora").isNotEmpty()) p.add(v.optString("ora"))
        p.add("[" + v.optString("tipo") + "]")
        p.add(v.optString("titolo"))
        var riga = p.joinToString(" ")
        importo(v)?.let { riga += " (" + fmtEur(it) + ")" }
        if (v.optString("ripetizione", "Nessuna") != "Nessuna") riga += " - ripete: " + ripTesto(v)
        if (testoFine(v).isNotEmpty()) riga += " (" + testoFine(v) + ")"
        if (v.optString("luogo").isNotEmpty()) riga += " @ " + v.optString("luogo")
        val nota = v.optString("note").trim().lines().firstOrNull().orEmpty()
        if (nota.isNotEmpty()) riga += " - $nota"
        return riga
    }

    fun testoAgenda(agenda: List<JSONObject>, giorni: Int, oggi: LocalDate = LocalDate.now()): String {
        val righe = ArrayList<String>()
        val ritardo = scaduti(agenda, oggi)
        if (ritardo.isNotEmpty()) {
            righe.add("SCADUTI / NON COMPLETATI:")
            ritardo.forEach { righe.add("  " + rigaVoce(it.data, it.voce)) }
            righe.add("")
        }
        righe.add("PROSSIMI $giorni GIORNI:")
        var n = 0
        for (o in occorrenze(agenda, oggi, oggi.plusDays(giorni.toLong()))) {
            if (fatta(o.voce, o.data)) continue
            righe.add("  " + rigaVoce(o.data, o.voce))
            n++
        }
        if (n == 0) righe.add("  (nessuna voce)")
        return righe.joinToString("\n")
    }

    private fun testoRiepilogo(nota: String, agenda: List<JSONObject>): String {
        val blocchi = ArrayList<String>()
        if (nota.trim().isNotEmpty()) blocchi.add(nota.trim())
        if (agenda.isNotEmpty()) blocchi.add("AGENDA (appuntamenti e scadenze):\n" + testoAgenda(agenda, 30))
        return blocchi.joinToString("\n\n")
    }

    class Conteggio(val oggi: Int, val scaduti: Int, val inArrivo: Int) {
        val totale get() = oggi + scaduti + inArrivo
    }

    fun conteggio(oggi: LocalDate = LocalDate.now()): Conteggio {
        val a = agenda()
        val n = a.count { occorre(it, oggi) && !fatta(it, oggi) }
        return Conteggio(n, scaduti(a, oggi).size, anticipi(a, oggi).size)
    }

    fun alternaFatto(id: String, d: LocalDate): String? = modifica { voci ->
        val v = voci.firstOrNull { it.optString("id") == id } ?: return@modifica "Voce non trovata."
        val f = v.optJSONArray("fatti") ?: JSONArray().also { v.put("fatti", it) }
        val k = d.toString()
        val idx = (0 until f.length()).firstOrNull { f.optString(it) == k }
        if (idx != null) f.remove(idx) else f.put(k)
        tocca(v)
        null
    }

    fun elimina(id: String): String? = modifica { voci ->
        if (!voci.removeAll { it.optString("id") == id }) "Voce non trovata." else null
    }

    fun salva(id: String?, campi: JSONObject): String? = modifica { voci ->
        if (id == null) {
            val v = JSONObject(campi.toString())
            v.put("id", nuovoId())
            v.put("fatti", JSONArray())
            v.put("escluse", JSONArray())
            v.put("seq", 0)
            tocca(v)
            voci.add(v)
        } else {
            val v = voci.firstOrNull { it.optString("id") == id } ?: return@modifica "Voce non trovata."
            val cambiata = campi.optString("data") != v.optString("data") ||
                campi.optString("ripetizione") != v.optString("ripetizione", "Nessuna")
            for (k in campi.keys()) v.put(k, campi.get(k))
            if (cambiata) {
                v.put("fatti", JSONArray())
                v.put("escluse", JSONArray())
                v.put("rrule", "")
                if (campi.optString("ripetizione") != "Personalizzata") { v.remove("rip_n"); v.remove("rip_unita") }
            }
            tocca(v)
        }
        null
    }

    fun posticipa(id: String, d: LocalDate, modo: String): Pair<LocalDateTime?, String?> {
        var risultato: LocalDateTime? = null
        val err = modifica { voci ->
            val v = voci.firstOrNull { it.optString("id") == id } ?: return@modifica "Voce non trovata."
            val adesso = LocalDateTime.now()
            val ora = v.optString("ora", "").trim()
            val h = oraDi(v)
            val baseD = if (d.isAfter(adesso.toLocalDate())) d else adesso.toLocalDate()
            val nd: LocalDate
            val nh: String
            when (modo) {
                "ora" -> {
                    if (h == null) return@modifica "Questa voce non ha un'ora."
                    val dal = LocalDateTime.of(d, h).let { if (it.isAfter(adesso)) it else adesso }.plusHours(1)
                    nd = dal.toLocalDate()
                    nh = String.format(Locale.ITALY, "%02d:%02d", dal.hour, dal.minute)
                }
                "giorno" -> { nd = baseD.plusDays(1); nh = ora }
                else -> { nd = baseD.plusDays(7); nh = ora }
            }
            if (v.optString("ripetizione", "Nessuna") == "Nessuna") {
                val gg = durataGg(v)
                v.put("data", nd.toString())
                v.put("ora", nh)
                v.put("data_fine", if (gg > 0) nd.plusDays(gg.toLong()).toString() else "")
                if (nh != ora) v.put("ora_fine", "")
                v.put("fatti", JSONArray())
                tocca(v)
            } else {
                val nuova = JSONObject()
                for (k in listOf("titolo", "tipo", "importo", "note", "preavviso", "email", "anticipo_min", "luogo")) {
                    if (v.has(k)) nuova.put(k, v.get(k))
                }
                nuova.put("id", nuovoId())
                nuova.put("data", nd.toString())
                nuova.put("ora", nh)
                nuova.put("ripetizione", "Nessuna")
                nuova.put("fatti", JSONArray())
                nuova.put("escluse", JSONArray())
                tocca(nuova)
                voci.add(nuova)
                val f = v.optJSONArray("fatti") ?: JSONArray().also { v.put("fatti", it) }
                if ((0 until f.length()).none { f.optString(it) == d.toString() }) f.put(d.toString())
                tocca(v)
            }
            risultato = LocalDateTime.of(nd, h?.let { LocalTime.parse(nh, DateTimeFormatter.ofPattern("H:mm")) } ?: LocalTime.MIDNIGHT)
            null
        }
        return risultato to err
    }
}
