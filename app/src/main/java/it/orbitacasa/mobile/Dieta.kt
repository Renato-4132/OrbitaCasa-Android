package it.orbitacasa.mobile

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

object Dieta {
    const val F_PIANO = "dieta_piano.json"
    const val F_PESO = "peso_storico.json"
    const val F_FABB = "fabbisogno_dati.json"
    const val F_PASSI = "pedometro.json"
    const val F_CUSTOM = "alimenti_custom.json"

    private const val URL_ALIMENTI = "https://raw.githubusercontent.com/Renato-4132/OrbitaCasa/main/resources/alimenti.json"
    private const val CACHE_ALIMENTI = "alimenti_cache.json"
    private const val MIN_ALIMENTI = 50
    const val PASSI_OB_DEFAULT = 10000

    val PASTI = listOf("colazione", "pranzo", "cena", "spuntino")
    val PASTI_ETICHETTA = listOf("Colazione", "Pranzo", "Cena", "Spuntino")
    val MACRO = listOf("Kcal", "Prot.", "Gras.", "Carb.", "Zucc.", "Fibre")
    val LIVELLI_ATTIVITA = listOf(
        "Sedentario (Ufficio)" to 1.2,
        "Leggero (Attività 1-2v)" to 1.375,
        "Moderato (Sport 3-5v)" to 1.55,
        "Attivo (Lavoro Fisico)" to 1.725,
    )

    private val FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy")
    private val LEGGI = DateTimeFormatter.ofPattern("d-M-yyyy")

    private const val DEFAULT_JSON = """[{"titolo":"Settimana 1","giorni":[["Lunedì","Pane, olio EVO, caffè","Pasta al pomodoro, insalata","Zuppa di ceci, pane","Frutta, noci",1850,72,52,240,45,22],["Martedì","Yogurt greco, miele, noci","Spaghetti aglio olio peperoncino","Tonno alla piastra, patate","Arancia",1780,88,48,210,38,20],["Mercoledì","Pane integrale, ricotta, miele","Pasta e fagioli, pane","Frittata di zucchine","Mandorle, mela",1720,79,55,200,35,24],["Giovedì","Avena, latte, banana","Risotto allo zafferano","Pollo alla brace, peperoni","Yogurt",1900,95,50,225,42,18],["Venerdì","Uova strapazzate, pane","Zuppa lenticchie, pane","Branzino al forno, patate","Frutta secca",1760,85,47,215,40,26],["Sabato","Pane, olio EVO, pomodoro","Tagliata di manzo, rucola","Insalata di farro, feta","Kiwi",2050,102,70,220,38,20],["Domenica","Cornetto integrale, succo arancia","Pasta al forno, melanzane","Minestrone, pane","Yogurt greco",1950,80,60,255,50,28]]},{"titolo":"Settimana 2","giorni":[["Lunedì","Yogurt bianco, frutta, mandorle","Pasta pesto genovese, caprese","Crema di piselli, crostini","Noci, arancia",1830,70,54,235,44,22],["Martedì","Pane, olio EVO, pomodoro","Riso integrale, verdure, feta","Tonno al forno, pomodorini","Mela",1760,86,46,215,38,20],["Mercoledì","Avena, latte, miele, noci","Zuppa cannellini e salvia, pane","Uova al tegamino, asparagi","Pera, mandorle",1700,77,53,205,36,25],["Giovedì","Pane integrale, ricotta, marmellata","Pasta e ceci al rosmarino","Tacchino alla brace, zucch.","Frutta",1880,98,48,225,40,20],["Venerdì","Yogurt greco, miele, banana","Risotto asparagi e parmigiano","Orata al forno, patate","Kiwi",1770,83,49,220,40,22],["Sabato","Uova strapazzate, pane","Costata di manzo, insalata","Taboulé, cetrioli, menta","Frutta secca",2030,100,68,225,38,20],["Domenica","Pane, olio EVO, latte","Lasagne ragù di verdure","Ribollita toscana, pane","Yogurt",1920,78,58,250,48,26]]},{"titolo":"Settimana 3","giorni":[["Lunedì","Avena, latte, frutta fresca","Pasta al pomodoro, finocchi","Minestra lenticchie, spinaci","Mandorle",1800,71,50,235,44,24],["Martedì","Yogurt greco, noci, miele","Spaghetti alle vongole","Tonno al sesamo, patate","Arancia",1790,90,48,212,38,20],["Mercoledì","Pane, olio EVO, pomodoro","Pasta melanzane ricotta salata","Frittata cipolle e patate","Mela, noci",1730,76,56,208,36,22],["Giovedì","Uova strapazzate, pane","Zuppa farro e borlotti, pane","Pollo alla brace, timo","Yogurt",1910,97,52,222,40,18],["Venerdì","Yogurt bianco, frutta, mandorle","Risotto al limone con gamberi","Pesce spada alla brace","Kiwi",1780,88,50,218,38,22],["Sabato","Cornetto integrale, succo","Bistecca, fagioli all'uccelletto","Insalata di orzo, feta","Frutta",2060,103,72,228,40,20],["Domenica","Pane, olio EVO, latte","Pasta al forno, ricotta, spinaci","Vellutata di zucca, crostini","Yogurt greco",1930,79,61,248,48,26]]},{"titolo":"Settimana 4","giorni":[["Lunedì","Yogurt greco, miele, noci","Pasta sugo olive, insalata","Zuppa ceci e bietole, pane","Pera",1820,73,51,238,44,24],["Martedì","Pane, olio EVO, pomodoro","Pasta e piselli, insalata","Tonno alla siciliana","Mandorle",1770,87,47,213,38,20],["Mercoledì","Avena, latte, banana","Riso verdure e curcuma","Frittata asparagi, formaggio","Mela, noci",1710,75,54,206,36,22],["Giovedì","Pane integrale, ricotta, marmellata","Minestrone, pane rustico","Spiedini pollo e verdure","Frutta",1890,96,49,228,40,20],["Venerdì","Uova strapazzate, pane","Pasta alle sarde, finocchietto","Salmone al forno, patate","Kiwi",1800,90,55,210,38,24],["Sabato","Yogurt greco, noci, arancia","Arrosticini, peperoni arrosto","Insalata farro, mozz.","Frutta secca",2040,101,69,222,38,20],["Domenica","Cornetto integrale, succo","Pasta al pesto di pistacchi","Caponata siciliana, uovo","Yogurt",1940,78,62,250,48,26]]}]"""

    private lateinit var dir: File
    private var assetAlimenti: () -> String? = { null }
    private val errMap = LinkedHashMap<String, String>()
    @Volatile private var baseCache: List<Alimento>? = null

    fun init(dir: File, asset: () -> String?) {
        this.dir = dir
        this.assetAlimenti = asset
        baseCache = null
    }

    fun errori(): List<String> = errMap.values.toList()

    fun data(s: String): LocalDate? = try {
        if (s.isBlank()) null else LocalDate.parse(s.trim(), LEGGI)
    } catch (_: Exception) {
        null
    }

    fun fmt(d: LocalDate): String = d.format(FMT)

    fun numero(s: String): Double? = s.trim().replace(',', '.').toDoubleOrNull()?.takeIf { !it.isNaN() && !it.isInfinite() }

    private fun rint(x: Double): Long = Math.rint(x).toLong()

    fun round1(x: Double): Double = Math.round(x * 10.0) / 10.0

    fun fmtInt(n: Int): String = String.format(Locale.ITALY, "%,d", n)

    fun fmt1(x: Double): String = String.format(Locale.ITALY, "%.1f", x)

    fun bmi(pesoKg: Double, altezzaCm: Double): Double? {
        val h = altezzaCm / 100.0
        if (pesoKg <= 0 || h <= 0) return null
        return round1(pesoKg / (h * h))
    }

    fun bmiCategoria(b: Double): String = when {
        b < 18.5 -> "Sottopeso"
        b < 25.0 -> "Normopeso"
        b < 30.0 -> "Sovrappeso"
        b < 35.0 -> "Obesità I"
        b < 40.0 -> "Obesità II"
        else -> "Obesità III"
    }

    fun bmiCategoriaLunga(b: Double): String = when {
        b < 18.5 -> "Sottopeso — Peso insufficiente, rischio malnutrizione"
        b < 25.0 -> "Normopeso — Peso nella norma, mantieni lo stile di vita"
        b < 30.0 -> "Sovrappeso — Peso eccessivo, rischio cardiovascolare lieve"
        b < 35.0 -> "Obesità I — Rischio moderato, consulta il medico"
        b < 40.0 -> "Obesità II — Rischio elevato, intervento consigliato"
        else -> "Obesità III — Rischio molto elevato, consulta subito il medico"
    }

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

    private fun <T : Any> modifica(
        nome: String, adatta: (Any?) -> T, consentiCorrotto: Boolean = false, blocco: (T) -> String?,
    ): String? {
        val l = leggi(nome)
        if (l.corrotto && !consentiCorrotto) return errMap[nome]
        if (l.corrotto) {
            try {
                file(nome).copyTo(File(dir, "$nome.bak"), overwrite = true)
            } catch (_: Exception) {
            }
        }
        val o = adatta(l.valore)
        val err = blocco(o)
        if (err == null) {
            try {
                scrivi(nome, o)
            } catch (e: Exception) {
                return "Salvataggio non riuscito: " + (e.message ?: "errore sconosciuto")
            }
            errMap.remove(nome)
        }
        return err
    }

    class Giorno(val nome: String, val pasti: List<String>, val tot: List<Int>)

    class Sett(val titolo: String, val giorni: List<Giorno>) {
        fun totali(): List<Int> = List(6) { i -> giorni.sumOf { it.tot[i] } }
        fun medie(): List<Int> =
            if (giorni.isEmpty()) List(6) { 0 } else totali().map { Math.rint(it.toDouble() / giorni.size).toInt() }
    }

    class Item(val nome: String, val g: Double, val v: List<Double>)

    class Piano(val sett: List<Sett>, private val comp: Map<String, List<Item>>) {
        fun composizione(s: Int, g: Int, pasto: Int): List<Item> = comp["${s}_${g}_${PASTI[pasto]}"] ?: emptyList()

        fun mediaKcal(): Int? {
            val m = sett.filter { it.giorni.isNotEmpty() }.map { it.medie()[0] }
            return if (m.isEmpty()) null else Math.rint(m.sum().toDouble() / m.size).toInt()
        }
    }

    private fun defaultPiano(): JSONArray = JSONArray(DEFAULT_JSON)

    private fun adattaPiano(v: Any?): JSONObject {
        val o = (v as? JSONObject) ?: JSONObject()
        if (o.optJSONArray("piano") == null) o.put("piano", defaultPiano())
        if (o.optJSONObject("composizioni") == null) o.put("composizioni", JSONObject())
        return o
    }

    private fun giornoDa(r: JSONArray): Giorno {
        fun t(i: Int) = if (i < r.length() && !r.isNull(i)) r.optString(i, "") else ""
        fun n(i: Int) = if (i < r.length()) rint(r.optDouble(i, 0.0)).toInt() else 0
        return Giorno(t(0), (1..4).map { t(it) }, (5..10).map { n(it) })
    }

    private fun itemDa(a: JSONArray): Item = Item(
        a.optString(0, ""), a.optDouble(1, 0.0), (2..7).map { a.optDouble(it, 0.0) },
    )

    fun piano(): Piano {
        val l = leggi(F_PIANO)
        val o = if (l.corrotto) adattaPiano(null) else adattaPiano(l.valore)
        val sett = ArrayList<Sett>()
        val arr = o.getJSONArray("piano")
        for (i in 0 until arr.length()) {
            val s = arr.optJSONObject(i) ?: continue
            val gg = s.optJSONArray("giorni") ?: JSONArray()
            val giorni = ArrayList<Giorno>()
            for (k in 0 until gg.length()) gg.optJSONArray(k)?.let { giorni.add(giornoDa(it)) }
            sett.add(Sett(s.optString("titolo", "Settimana ${i + 1}"), giorni))
        }
        val comp = HashMap<String, List<Item>>()
        val co = o.getJSONObject("composizioni")
        val chiavi = co.keys()
        while (chiavi.hasNext()) {
            val k = chiavi.next()
            val a = co.optJSONArray(k) ?: continue
            val items = ArrayList<Item>()
            for (j in 0 until a.length()) a.optJSONArray(j)?.let { items.add(itemDa(it)) }
            comp[k] = items
        }
        return Piano(sett, comp)
    }

    private fun rigaRaw(o: JSONObject, s: Int, g: Int): JSONArray? =
        o.optJSONArray("piano")?.optJSONObject(s)?.optJSONArray("giorni")?.optJSONArray(g)

    private fun pad(r: JSONArray) {
        while (r.length() < 11) r.put(if (r.length() <= 4) "" else 0)
    }

    private fun ricalcola(o: JSONObject, s: Int, g: Int) {
        val r = rigaRaw(o, s, g) ?: return
        pad(r)
        val comp = o.optJSONObject("composizioni") ?: JSONObject()
        val tot = DoubleArray(6)
        for (p in PASTI) {
            val a = comp.optJSONArray("${s}_${g}_$p") ?: continue
            for (k in 0 until a.length()) {
                val voce = a.optJSONArray(k) ?: continue
                for (t in 0 until 6) tot[t] += voce.optDouble(2 + t, 0.0)
            }
        }
        for (t in 0 until 6) r.put(5 + t, rint(tot[t]))
    }

    fun salvaComposizione(s: Int, g: Int, pasto: Int, items: List<Item>): String? =
        modifica(F_PIANO, { adattaPiano(it) }) { o ->
            val r = rigaRaw(o, s, g) ?: return@modifica "Giorno non trovato."
            pad(r)
            val arr = JSONArray()
            for (item in items) {
                val a = JSONArray().put(item.nome).put(item.g)
                for (x in item.v) a.put(x)
                arr.put(a)
            }
            o.getJSONObject("composizioni").put("${s}_${g}_${PASTI[pasto]}", arr)
            if (items.isNotEmpty()) r.put(1 + pasto, items.joinToString(", ") { it.nome })
            ricalcola(o, s, g)
            null
        }

    fun copiaGiorno(sS: Int, gS: Int, sD: Int, gD: Int): String? {
        if (sS == sD && gS == gD) return "Sorgente e destinazione sono lo stesso giorno."
        return modifica(F_PIANO, { adattaPiano(it) }) { o ->
            val src = rigaRaw(o, sS, gS) ?: return@modifica "Giorno non trovato."
            val dstArr = o.getJSONArray("piano").optJSONObject(sD)?.optJSONArray("giorni")
                ?: return@modifica "Giorno non trovato."
            val dst = dstArr.optJSONArray(gD) ?: return@modifica "Giorno non trovato."
            val nome = dst.optString(0, "")
            val nuova = JSONArray(src.toString())
            pad(nuova)
            nuova.put(0, nome)
            dstArr.put(gD, nuova)
            val comp = o.getJSONObject("composizioni")
            for (p in PASTI) {
                val a = comp.optJSONArray("${sS}_${gS}_$p")
                val kd = "${sD}_${gD}_$p"
                if (a != null) comp.put(kd, JSONArray(a.toString())) else comp.remove(kd)
            }
            null
        }
    }

    fun azzeraGiorno(s: Int, g: Int): String? =
        modifica(F_PIANO, { adattaPiano(it) }) { o ->
            val r = rigaRaw(o, s, g) ?: return@modifica "Giorno non trovato."
            pad(r)
            for (i in 1..4) r.put(i, "")
            for (i in 5..10) r.put(i, 0)
            val comp = o.getJSONObject("composizioni")
            for (p in PASTI) comp.remove("${s}_${g}_$p")
            null
        }

    fun ripristinaPiano(): String? =
        modifica(F_PIANO, { adattaPiano(it) }, consentiCorrotto = true) { o ->
            o.put("piano", defaultPiano())
            o.put("composizioni", JSONObject())
            null
        }

    fun promptAi(s: Sett): String {
        val righe = s.giorni.joinToString("\n") {
            "  ${it.nome}: col=${it.pasti[0]} | pranzo=${it.pasti[1]} | cena=${it.pasti[2]} | spunt=${it.pasti[3]} | " +
                "${it.tot[0]}kcal / ${it.tot[1]}g prot / ${it.tot[2]}g grassi / ${it.tot[3]}g carb / ${it.tot[4]}g zucc / ${it.tot[5]}g fibre"
        }
        return "Analizza il piano alimentare settimanale mediterraneo e fornisci:\n" +
            "1. VALUTAZIONE NUTRIZIONALE: equilibrio macronutrienti e calorie.\n" +
            "2. PUNTI DI FORZA: aspetti positivi.\n" +
            "3. MIGLIORAMENTI: 2-3 modifiche concrete.\n" +
            "4. CONSIGLI PRATICI: porzioni, orari, idratazione.\n\n" +
            "PIANO - ${s.titolo}:\n$righe\n\n" +
            "REGOLE: no Markdown (*, #), titoli in MAIUSCOLO, italiano conciso."
    }

    class Pesata(val data: String, val d: LocalDate, val peso: Double)
    class PesoDati(val pesate: List<Pesata>, val altezza: String, val obiettivo: String)

    private fun adattaPeso(v: Any?): JSONObject {
        val o = when (v) {
            is JSONObject -> v
            is JSONArray -> JSONObject().put("pesate", v).put("altezza", "").put("obiettivo", "")
            else -> JSONObject().put("pesate", JSONArray()).put("altezza", "").put("obiettivo", "")
        }
        if (o.optJSONArray("pesate") == null) o.put("pesate", JSONArray())
        return o
    }

    fun peso(): PesoDati {
        val l = leggi(F_PESO)
        val o = if (l.corrotto) adattaPeso(null) else adattaPeso(l.valore)
        val a = o.getJSONArray("pesate")
        val out = ArrayList<Pesata>()
        for (i in 0 until a.length()) {
            val r = a.optJSONObject(i) ?: continue
            val d = data(r.optString("data", "")) ?: continue
            val p = r.optDouble("peso", Double.NaN)
            if (p.isNaN() || p <= 0) continue
            out.add(Pesata(fmt(d), d, p))
        }
        out.sortBy { it.d }
        return PesoDati(out, o.optString("altezza", "").trim(), o.optString("obiettivo", "").trim())
    }

    fun aggiungiPesata(d: LocalDate, peso: Double): String? {
        if (peso <= 0 || peso > 700) return "Peso non valido."
        return modifica(F_PESO, { adattaPeso(it) }) { o ->
            val a = o.getJSONArray("pesate")
            val voci = ArrayList<JSONObject>()
            for (i in 0 until a.length()) {
                val r = a.optJSONObject(i) ?: continue
                if (data(r.optString("data", "")) == d) continue
                voci.add(r)
            }
            voci.add(JSONObject().put("data", fmt(d)).put("peso", round1(peso)))
            voci.sortBy { data(it.optString("data", "")) ?: LocalDate.MIN }
            val nuovo = JSONArray()
            for (v in voci) nuovo.put(v)
            o.put("pesate", nuovo)
            null
        }
    }

    fun eliminaPesata(d: LocalDate): String? =
        modifica(F_PESO, { adattaPeso(it) }) { o ->
            val a = o.getJSONArray("pesate")
            val nuovo = JSONArray()
            for (i in 0 until a.length()) {
                val r = a.optJSONObject(i) ?: continue
                if (data(r.optString("data", "")) != d) nuovo.put(r)
            }
            o.put("pesate", nuovo)
            null
        }

    fun azzeraPeso(): String? =
        modifica(F_PESO, { adattaPeso(it) }) { o ->
            o.put("pesate", JSONArray())
            null
        }

    fun salvaAltezzaObiettivo(altezza: String, obiettivo: String): String? {
        val a = altezza.trim()
        val ob = obiettivo.trim()
        if (a.isNotEmpty() && (numero(a)?.let { it > 0 && it < 300 } != true)) return "Altezza non valida."
        if (ob.isNotEmpty() && (numero(ob)?.let { it > 0 && it < 700 } != true)) return "Obiettivo non valido."
        return modifica(F_PESO, { adattaPeso(it) }) { o ->
            o.put("altezza", a)
            o.put("obiettivo", ob)
            null
        }
    }

    class Fabb(val sesso: String, val peso: String, val altezza: String, val eta: String)

    fun fabb(): Fabb {
        val l = leggi(F_FABB)
        val o = l.valore as? JSONObject
        fun s(k: String, d: String) = o?.optString(k, d)?.takeIf { it.isNotBlank() } ?: d
        return Fabb(s("sesso", "Donna"), s("peso", "0"), s("altezza", "0"), s("eta", "0"))
    }

    fun salvaFabb(sesso: String, peso: String, altezza: String, eta: String): String? {
        if (sesso != "Donna" && sesso != "Uomo") return "Sesso non valido."
        val p = numero(peso)
        val a = numero(altezza)
        val e = eta.trim().toIntOrNull()
        if (p == null || p <= 0 || p > 700) return "Peso non valido."
        if (a == null || a <= 0 || a > 300) return "Altezza non valida."
        if (e == null || e <= 0 || e > 120) return "Età non valida."
        return modifica(F_FABB, { (it as? JSONObject) ?: JSONObject() }) { o ->
            o.put("sesso", sesso)
            o.put("peso", peso.trim())
            o.put("altezza", altezza.trim())
            o.put("eta", eta.trim())
            null
        }
    }

    class Livello(val nome: String, val kcal: Int)
    class Fabbisogno(val bmr: Int, val livelli: List<Livello>, val obiettivi: List<Livello>)

    fun calcolaFabb(sesso: String, peso: Double, altezza: Double, eta: Int): Fabbisogno? {
        if (peso <= 0 || altezza <= 0 || eta <= 0) return null
        val bmr = 10 * peso + 6.25 * altezza - 5 * eta + (if (sesso == "Uomo") 5 else -161)
        val livelli = LIVELLI_ATTIVITA.map { Livello(it.first, rint(bmr * it.second).toInt()) }
        val base = livelli[2].kcal
        val obiettivi = listOf(
            Livello("Per perdere peso (Deficit 15%)", rint(base * 0.85).toInt()),
            Livello("Per mantenere il peso attuale", base),
            Livello("Per aumentare massa (+10%)", rint(base * 1.10).toInt()),
        )
        return Fabbisogno(rint(bmr).toInt(), livelli, obiettivi)
    }

    fun fabbisognoSalvato(): Fabbisogno? {
        val f = fabb()
        return calcolaFabb(f.sesso, numero(f.peso) ?: return null, numero(f.altezza) ?: return null, f.eta.trim().toIntOrNull() ?: return null)
    }

    class VocePassi(val data: String, val d: LocalDate, val passi: Int, val ob: Int, val note: String) {
        val pct: Int get() = if (ob > 0) Math.rint(passi * 100.0 / ob).toInt() else 0
        val raggiunto: Boolean get() = passi >= ob
    }

    class PassiDati(val voci: List<VocePassi>, val obDefault: Int)

    private fun adattaPassi(v: Any?): JSONObject {
        val o = (v as? JSONObject) ?: JSONObject()
        if (o.optJSONObject("passi") == null) o.put("passi", JSONObject())
        if (!o.has("obiettivo_default")) o.put("obiettivo_default", PASSI_OB_DEFAULT)
        return o
    }

    fun passi(): PassiDati {
        val l = leggi(F_PASSI)
        val o = if (l.corrotto) adattaPassi(null) else adattaPassi(l.valore)
        val p = o.getJSONObject("passi")
        val out = ArrayList<VocePassi>()
        val chiavi = p.keys()
        while (chiavi.hasNext()) {
            val k = chiavi.next()
            val d = data(k) ?: continue
            val r = p.optJSONObject(k) ?: continue
            out.add(
                VocePassi(
                    fmt(d), d, r.optInt("passi", 0).coerceAtLeast(0),
                    r.optInt("obiettivo", PASSI_OB_DEFAULT).let { if (it > 0) it else PASSI_OB_DEFAULT },
                    r.optString("note", ""),
                )
            )
        }
        out.sortBy { it.d }
        val ob = o.optInt("obiettivo_default", PASSI_OB_DEFAULT).let { if (it > 0) it else PASSI_OB_DEFAULT }
        return PassiDati(out, ob)
    }

    private fun togliData(p: JSONObject, d: LocalDate) {
        val da = ArrayList<String>()
        val chiavi = p.keys()
        while (chiavi.hasNext()) {
            val k = chiavi.next()
            if (data(k) == d) da.add(k)
        }
        for (k in da) p.remove(k)
    }

    fun salvaPassi(d: LocalDate, passi: Int, ob: Int, note: String): String? {
        if (passi < 0 || passi > 200_000) return "Numero di passi non valido."
        val obiettivo = if (ob > 0) ob else PASSI_OB_DEFAULT
        return modifica(F_PASSI, { adattaPassi(it) }) { o ->
            val p = o.getJSONObject("passi")
            togliData(p, d)
            p.put(fmt(d), JSONObject().put("passi", passi).put("obiettivo", obiettivo).put("note", note.trim()))
            o.put("obiettivo_default", obiettivo)
            null
        }
    }

    fun eliminaPassi(d: LocalDate): String? =
        modifica(F_PASSI, { adattaPassi(it) }) { o ->
            togliData(o.getJSONObject("passi"), d)
            null
        }

    fun azzeraPassi(): String? =
        modifica(F_PASSI, { adattaPassi(it) }) { o ->
            o.put("passi", JSONObject())
            null
        }

    class StatPassi(
        val oggi: VocePassi?, val media7: Int?, val media30: Int?, val record: Int?,
        val totale: Int, val giorniOk: Int, val giorni: Int,
    )

    fun statPassi(tutte: List<VocePassi>, filtrate: List<VocePassi>, oggi: LocalDate = LocalDate.now()): StatPassi {
        fun media(gg: Long): Int? {
            val v = tutte.filter { val dif = java.time.temporal.ChronoUnit.DAYS.between(it.d, oggi); dif in 0 until gg }.map { it.passi }
            return if (v.isEmpty()) null else Math.rint(v.sum().toDouble() / v.size).toInt()
        }
        return StatPassi(
            tutte.firstOrNull { it.d == oggi }, media(7), media(30), tutte.maxOfOrNull { it.passi },
            filtrate.sumOf { it.passi }, tutte.count { it.raggiunto }, tutte.size,
        )
    }

    class Alimento(val nome: String, val cat: String, val v: List<Double>)

    private fun alimentoDa(a: JSONArray): Alimento? {
        if (a.length() < 8) return null
        val nome = a.optString(0, "").trim()
        if (nome.isEmpty()) return null
        return Alimento(nome, a.optString(1, "Altro").ifBlank { "Altro" }, (2..7).map { a.optDouble(it, 0.0) })
    }

    private const val RX_STR = "(?:\"((?:[^\"\\\\]|\\\\.)*)\"|'((?:[^'\\\\]|\\\\.)*)')"
    private const val RX_NUM = "(-?\\d+(?:\\.\\d+)?)"
    private val RX_TUPLA = Regex(
        "\\(\\s*" + RX_STR + "\\s*,\\s*" + RX_STR + "\\s*,\\s*" +
            (1..6).joinToString("\\s*,\\s*") { RX_NUM } + "\\s*,?\\s*\\)"
    )

    private fun unesc(s: String) = s.replace("\\'", "'").replace("\\\"", "\"").replace("\\\\", "\\")

    fun parseAlimenti(testo: String): List<Alimento> {
        val t = testo.trim()
        if (t.startsWith("[")) {
            try {
                val a = JSONArray(t)
                val out = ArrayList<Alimento>()
                for (i in 0 until a.length()) a.optJSONArray(i)?.let { alimentoDa(it) }?.let { out.add(it) }
                if (out.isNotEmpty()) return out
            } catch (_: Exception) {
            }
        }
        val pulito = t.lineSequence().filter { !it.trimStart().startsWith("#") }.joinToString("\n")
        val out = ArrayList<Alimento>()
        for (m in RX_TUPLA.findAll(pulito)) {
            val g = m.groupValues
            val nome = unesc(if (g[1].isNotEmpty()) g[1] else g[2]).trim()
            val cat = unesc(if (g[3].isNotEmpty()) g[3] else g[4]).trim()
            if (nome.isEmpty()) continue
            out.add(Alimento(nome, cat.ifBlank { "Altro" }, (5..10).map { g[it].toDouble() }))
        }
        return out
    }

    private fun baseDb(): List<Alimento> {
        baseCache?.let { return it }
        var l: List<Alimento> = emptyList()
        val cache = File(dir.parentFile, CACHE_ALIMENTI)
        if (cache.isFile) l = try { parseAlimenti(cache.readText(Charsets.UTF_8)) } catch (_: Exception) { emptyList() }
        if (l.size < MIN_ALIMENTI) l = try { parseAlimenti(assetAlimenti() ?: "") } catch (_: Exception) { emptyList() }
        baseCache = l
        return l
    }

    fun aggiornamentoDovuto(giorni: Int = 7): Boolean {
        val cache = File(dir.parentFile, CACHE_ALIMENTI)
        return !cache.isFile || System.currentTimeMillis() - cache.lastModified() > giorni * 86_400_000L
    }

    fun scaricaAlimenti(): Int {
        val c = URL(URL_ALIMENTI).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 20_000
        val testo = try {
            if (c.responseCode != 200) throw IllegalStateException("HTTP " + c.responseCode)
            c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            c.disconnect()
        }
        val l = parseAlimenti(testo)
        if (l.size < MIN_ALIMENTI) throw IllegalStateException("Database scaricato non valido")
        val cache = File(dir.parentFile, CACHE_ALIMENTI)
        val tmp = File(cache.parentFile, "$CACHE_ALIMENTI.tmp")
        tmp.writeText(testo, Charsets.UTF_8)
        Files.move(tmp.toPath(), cache.toPath(), StandardCopyOption.REPLACE_EXISTING)
        baseCache = null
        return l.size
    }

    fun custom(): List<Alimento> {
        val l = leggi(F_CUSTOM)
        val a = l.valore as? JSONArray ?: return emptyList()
        val out = ArrayList<Alimento>()
        for (i in 0 until a.length()) a.optJSONArray(i)?.let { alimentoDa(it) }?.let { out.add(it) }
        return out
    }

    fun alimenti(): List<Alimento> {
        val cust = custom()
        val nomi = cust.map { it.nome }.toSet()
        return (baseDb().filter { it.nome !in nomi } + cust).sortedBy { it.nome.lowercase() }
    }

    fun categorie(l: List<Alimento>): List<String> = listOf("Tutte") + l.map { it.cat }.toSet().sorted()

    fun salvaCustom(nome: String, cat: String, v: List<Double>): String? {
        val n = nome.trim()
        if (n.isEmpty()) return "Il nome non può essere vuoto."
        if (v.size != 6 || v.any { it.isNaN() || it.isInfinite() || it < 0 || it > 100_000 }) return "I valori numerici non sono validi."
        return modifica(F_CUSTOM, { (it as? JSONArray) ?: JSONArray() }) { a ->
            val nuovo = JSONArray()
            for (i in 0 until a.length()) {
                val r = a.optJSONArray(i) ?: continue
                if (r.optString(0, "") != n) nuovo.put(r)
            }
            val riga = JSONArray().put(n).put(cat.trim().ifBlank { "Altro" })
            for (x in v) riga.put(round1(x))
            nuovo.put(riga)
            while (a.length() > 0) a.remove(0)
            for (i in 0 until nuovo.length()) a.put(nuovo.get(i))
            null
        }
    }

    fun eliminaCustom(nomi: Set<String>): String? =
        modifica(F_CUSTOM, { (it as? JSONArray) ?: JSONArray() }) { a ->
            val nuovo = JSONArray()
            for (i in 0 until a.length()) {
                val r = a.optJSONArray(i) ?: continue
                if (r.optString(0, "") !in nomi) nuovo.put(r)
            }
            while (a.length() > 0) a.remove(0)
            for (i in 0 until nuovo.length()) a.put(nuovo.get(i))
            null
        }

    fun voce(a: Alimento, grammi: Double): Item {
        val f = grammi / 100.0
        return Item(a.nome, grammi, a.v.map { round1(it * f) })
    }
}
