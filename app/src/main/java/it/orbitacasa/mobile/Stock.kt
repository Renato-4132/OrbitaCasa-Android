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
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object Stock {
    const val FILE = "magazzino.json"

    val CATEGORIE_DEFAULT = listOf(
        "Alimentari", "Bevande", "Pulizia casa", "Igiene personale", "Medicinali",
        "Ferramenta", "Elettronica", "Cancelleria", "Abbigliamento", "Giardino",
        "Auto / Moto", "Animali", "Varie",
    )

    val PROFILI: LinkedHashMap<String, List<String>> = linkedMapOf(
        "Casa / Dispensa" to CATEGORIE_DEFAULT,
        "Officina / Garage" to listOf(
            "Viteria e Bulloneria", "Utensili a mano", "Elettroutensili", "Materiale elettrico",
            "Idraulica", "Vernici e Colle", "Lubrificanti", "Ricambi auto", "Abrasivi", "Protezioni (DPI)",
        ),
        "Ufficio / Studio" to listOf(
            "Carta e Stampa", "Cancelleria", "Toner e Cartucce", "Cavi e Accessori",
            "Hardware", "Archiviazione", "Pulizia ufficio", "Materiale da riunione",
        ),
        "Negozio / Bottega" to listOf(
            "Merce in vendita", "Imballaggi", "Etichette", "Espositori", "Materiale promozionale",
            "Resi e Difettosi", "Campionario",
        ),
        "Bar / Ristorante" to listOf(
            "Materie prime", "Carne e Pesce", "Ortofrutta", "Panetteria", "Vini e Bevande",
            "Caffè e Tè", "Monouso e Tovagliato", "Pulizia e Sanificazione",
        ),
        "Farmacia / Salute" to listOf(
            "Farmaci da banco", "Integratori", "Medicazione", "Igiene", "Dispositivi medici", "Cosmetici",
        ),
        "Hobby / Bricolage" to listOf(
            "Colori e Pennelli", "Carta e Cartoncini", "Tessuti e Filati", "Perline e Minuteria",
            "Legno e Modellismo", "Colle e Adesivi", "Attrezzi hobby",
        ),
        "Giardino / Orto" to listOf(
            "Semi e Bulbi", "Terricci e Concimi", "Fitofarmaci", "Vasi e Contenitori",
            "Attrezzi da giardino", "Irrigazione", "Mangimi",
        ),
    )

    val UNITA = listOf("pz", "kg", "g", "lt", "ml", "m", "m²", "conf", "scatola", "set")
    const val TUTTE_CAT = "Tutte le categorie"
    val STATI_FILTRO = listOf(
        "Tutti gli stati", "Da riordinare", "Sotto scorta", "Esauriti", "In eccesso", "Disponibile",
        "In scadenza", "Scaduti",
    )
    const val GIORNI_SCADENZA = 30
    const val MAX_QTA = 900.0
    const val MAX_PREZZO = 9000.0
    const val MAX_NOME = 24
    const val MAX_CODICE = 30
    const val MAX_CAT = 30
    const val MAX_DESC = 45
    const val MAX_NOME_CAT = 40
    const val MAX_MOV = 100

    val DMY: DateTimeFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy")
    private val DMY2: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    class Dati(val categorie: MutableList<String>, val articoli: MutableList<JSONObject>) {
        fun tutteCategorie(): List<String> =
            (categorie + articoli.map { s(it, "categoria") }.filter { it.isNotEmpty() }).toSet().sortedBy { it.lowercase() }
    }

    class Stato(val nome: String, val tag: String, val daOrdinare: Double)
    class Scad(val nome: String, val tag: String, val testo: String)

    private lateinit var dir: File
    private var err: String? = null

    fun init(dir: File) {
        this.dir = dir
    }

    fun errore(): String? = err

    fun s(o: JSONObject, k: String): String = if (o.isNull(k)) "" else o.optString(k, "").trim()

    fun d(o: JSONObject, k: String): Double {
        val v = o.opt(k)
        val x = when (v) {
            is Number -> v.toDouble()
            is String -> num(v)
            else -> 0.0
        }
        return if (x.isNaN() || x.isInfinite()) 0.0 else x
    }

    fun num(t: String): Double {
        var x = t.replace("€", "").replace(" ", "").trim()
        if (x.isEmpty()) return 0.0
        if (',' in x) x = x.replace(".", "").replace(',', '.')
        else if (x.count { it == '.' } > 1) x = x.replace(".", "")
        return x.toDoubleOrNull() ?: 0.0
    }

    fun numOrNull(t: String): Double? {
        var x = t.replace("€", "").replace(" ", "").trim()
        if (x.isEmpty()) return 0.0
        if (',' in x) x = x.replace(".", "").replace(',', '.')
        else if (x.count { it == '.' } > 1) x = x.replace(".", "")
        val r = x.toDoubleOrNull() ?: return null
        return if (r.isNaN() || r.isInfinite()) null else r
    }

    fun data(t: String): LocalDate? {
        val x = t.trim()
        if (x.isEmpty()) return null
        return try {
            LocalDate.parse(x, DMY)
        } catch (_: Exception) {
            try {
                LocalDate.parse(x, DMY2)
            } catch (_: Exception) {
                null
            }
        }
    }

    fun oggi(): String = LocalDate.now().format(DMY)

    fun itNum(v: Double, dec: Int = 2): String = String.format(Locale.ITALY, "%,." + dec + "f", v)

    fun eur(v: Double): String = itNum(v) + " €"

    fun q(v: Double): String {
        if (abs(v - Math.round(v)) < 1e-9) return String.format(Locale.ITALY, "%,d", Math.round(v))
        return String.format(Locale.ITALY, "%,.3f", v).trimEnd('0').trimEnd(',')
    }

    private fun scrivi(dati: Dati): String? {
        if (err != null) return err
        return try {
            val a = JSONArray()
            for (x in dati.articoli) a.put(x)
            val o = JSONObject().put("versione", 1).put("categorie", JSONArray().also { ja -> for (c in dati.categorie) ja.put(c) }).put("articoli", a)
            val f = File(dir, FILE)
            val tmp = File(dir, "$FILE.tmp")
            tmp.writeText(o.toString(2), Charsets.UTF_8)
            Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING)
            null
        } catch (e: Exception) {
            "Salvataggio non riuscito: " + e.message
        }
    }

    fun salva(dati: Dati): String? = scrivi(dati)

    fun carica(): Dati {
        err = null
        val cat = ArrayList(CATEGORIE_DEFAULT)
        val art = ArrayList<JSONObject>()
        val f = File(dir, FILE)
        if (!f.isFile || f.length() == 0L) return Dati(cat, art)
        try {
            val v = JSONTokener(f.readText(Charsets.UTF_8)).nextValue()
            val lista: JSONArray?
            if (v is JSONObject) {
                lista = v.optJSONArray("articoli")
                if (v.has("categorie")) {
                    cat.clear()
                    val c = v.optJSONArray("categorie")
                    if (c != null) for (i in 0 until c.length()) {
                        val n = c.optString(i, "").trim()
                        if (n.isNotEmpty() && n !in cat) cat.add(n)
                    }
                }
            } else {
                lista = v as? JSONArray
            }
            if (lista == null) throw IllegalStateException("formato")
            val ids = HashSet<String>()
            for (i in 0 until lista.length()) {
                val o = lista.optJSONObject(i) ?: continue
                if (s(o, "nome").isEmpty()) continue
                val n = normalizza(o)
                if (!ids.add(s(n, "id"))) n.put("id", nuovoId().also { ids.add(it) })
                art.add(n)
            }
        } catch (e: Exception) {
            err = "$FILE risulta illeggibile: nessuna modifica effettuata (il file non è stato toccato)"
            return Dati(ArrayList(CATEGORIE_DEFAULT), ArrayList())
        }
        return Dati(cat, art)
    }

    fun nuovoId(): String = UUID.randomUUID().toString().replace("-", "").substring(0, 12)

    fun normalizza(a: JSONObject): JSONObject {
        val o = JSONObject(a.toString())
        val sc = data(s(a, "scadenza"))
        val mov = a.optJSONArray("movimenti") ?: JSONArray()
        val nm = JSONArray()
        val da = max(0, mov.length() - MAX_MOV)
        for (i in da until mov.length()) mov.optJSONObject(i)?.let { nm.put(it) }
        o.put("id", s(a, "id").ifEmpty { nuovoId() })
            .put("codice", s(a, "codice"))
            .put("nome", s(a, "nome"))
            .put("categoria", s(a, "categoria"))
            .put("descrizione", if (a.isNull("descrizione")) "" else a.optString("descrizione", ""))
            .put("unita", s(a, "unita").ifEmpty { "pz" })
            .put("quantita", d(a, "quantita")).put("qta_min", d(a, "qta_min")).put("qta_max", d(a, "qta_max"))
            .put("prezzo", d(a, "prezzo"))
            .put("scadenza", sc?.format(DMY) ?: "")
            .put("data", s(a, "data").ifEmpty { oggi() })
            .put("movimenti", nm)
        return o
    }

    fun stato(a: JSONObject): Stato {
        val q = d(a, "quantita")
        val mn = d(a, "qta_min")
        val mx = d(a, "qta_max")
        val esaurito = q <= 0
        val sotto = mn > 0 && q <= mn
        val (nome, tag) = when {
            esaurito -> "Esaurito" to "esaurito"
            sotto -> "Sotto scorta" to "sotto"
            mx > 0 && q > mx -> "In eccesso" to "eccesso"
            else -> "Disponibile" to ""
        }
        var ord = 0.0
        if (sotto || (esaurito && (mn > 0 || mx > 0))) {
            val target = if (mx > 0) mx else mn * 2
            ord = Math.round(max(target - q, 0.0) * 1000) / 1000.0
        }
        return Stato(nome, tag, ord)
    }

    fun scadenza(a: JSONObject, oggi: LocalDate = LocalDate.now(), giorni: Int = GIORNI_SCADENZA): Scad {
        val dt = data(s(a, "scadenza")) ?: return Scad("", "", "")
        val testo = dt.format(DMY)
        if (d(a, "quantita") <= 0) return Scad("", "", testo)
        val delta = java.time.temporal.ChronoUnit.DAYS.between(oggi, dt)
        if (delta < 0) return Scad("Scaduto", "scaduto", "$testo (scaduto)")
        if (delta <= giorni) return Scad("In scadenza", "scadenza", "$testo (" + (if (delta == 0L) "oggi" else "$delta gg") + ")")
        return Scad("", "", testo)
    }

    fun tagRiga(a: JSONObject): String {
        val sc = scadenza(a)
        return if (sc.tag.isNotEmpty()) sc.tag else stato(a).tag
    }

    fun normCodice(c: String): String {
        val x = c.filter { !it.isWhitespace() }.uppercase()
        return if (x.isNotEmpty() && x.all { it.isDigit() }) x.trimStart('0').ifEmpty { "0" } else x
    }

    fun trovaPerCodice(dati: Dati, codice: String): List<JSONObject> {
        val n = normCodice(codice)
        if (n.isEmpty()) return emptyList()
        return dati.articoli.filter { normCodice(s(it, "codice")) == n }.sortedBy { s(it, "nome").lowercase() }
    }

    fun corrisponde(a: JSONObject, testo: String): Boolean {
        val parole = testo.lowercase().trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (parole.isEmpty()) return true
        val st = stato(a)
        val sc = scadenza(a)
        val pagliaio = listOf(
            s(a, "codice"), s(a, "descrizione"), s(a, "nome"), s(a, "categoria"), s(a, "unita"),
            st.nome, sc.testo, s(a, "data"),
        ).joinToString(" ").lowercase()
        return parole.all { it in pagliaio }
    }

    fun filtra(dati: Dati, testo: String, cat: String, stt: String, oggi: LocalDate = LocalDate.now()): List<JSONObject> =
        dati.articoli.filter { a ->
            if (cat != TUTTE_CAT && s(a, "categoria") != cat) return@filter false
            val st = stato(a)
            val sc = scadenza(a, oggi)
            val ok = when (stt) {
                "Da riordinare" -> st.daOrdinare > 0
                "Sotto scorta" -> st.nome == "Sotto scorta"
                "Esauriti" -> st.nome == "Esaurito"
                "In eccesso" -> st.nome == "In eccesso"
                "Disponibile" -> st.nome == "Disponibile"
                "In scadenza" -> sc.nome == "In scadenza"
                "Scaduti" -> sc.nome == "Scaduto"
                else -> true
            }
            ok && corrisponde(a, testo)
        }.sortedBy { s(it, "nome").lowercase() }

    class Riepilogo(val n: Int, val valore: Double, val sotto: Int, val esauriti: Int, val daOrdinare: Int, val scaduti: Int, val inScadenza: Int)

    fun riepilogo(l: List<JSONObject>): Riepilogo = Riepilogo(
        l.size, l.sumOf { d(it, "quantita") * d(it, "prezzo") },
        l.count { stato(it).tag == "sotto" }, l.count { stato(it).tag == "esaurito" },
        l.count { stato(it).daOrdinare > 0 }, l.count { scadenza(it).nome == "Scaduto" },
        l.count { scadenza(it).nome == "In scadenza" },
    )

    class Form(
        val nome: String, val codice: String, val categoria: String, val descrizione: String, val unita: String,
        val quantita: String, val min: String, val max: String, val prezzo: String, val scadenza: String, val data: String,
    )

    fun leggi(f: Form): Pair<String?, JSONObject?> {
        val nome = f.nome.trim()
        if (nome.isEmpty()) return "Il campo Articolo è obbligatorio." to null
        if (nome.length > MAX_NOME) return "Il nome può avere al massimo $MAX_NOME caratteri." to null
        if (f.codice.trim().length > MAX_CODICE) return "Il codice può avere al massimo $MAX_CODICE caratteri." to null
        if (f.categoria.trim().length > MAX_CAT) return "La categoria può avere al massimo $MAX_CAT caratteri." to null
        val q = numOrNull(f.quantita)
        val mn = numOrNull(f.min)
        val mx = numOrNull(f.max)
        val pr = numOrNull(f.prezzo)
        if (q == null || mn == null || mx == null || pr == null) return "Numero non valido in quantità, scorte o prezzo." to null
        if (min(min(q, mn), min(mx, pr)) < 0) return "Quantità, scorte e prezzo non possono essere negativi." to null
        if (pr > MAX_PREZZO) return "Il prezzo unitario non può superare 9.000 €." to null
        if (max(q, max(mn, mx)) > MAX_QTA) return "Le quantità non possono superare 900." to null
        if (mx != 0.0 && mx < mn) return "La quantità massima non può essere inferiore alla minima (lascia 0 se non vuoi un massimo)." to null
        if (data(f.data) == null) return "Data non valida: usa il formato gg-mm-aaaa." to null
        val sc = f.scadenza.trim()
        if (sc.isNotEmpty() && data(sc) == null) return "Scadenza non valida: usa gg-mm-aaaa oppure lascia vuoto." to null
        val desc = f.descrizione.trim()
        if (desc.length > MAX_DESC) return "La descrizione può avere al massimo $MAX_DESC caratteri." to null
        val o = JSONObject()
            .put("nome", nome).put("codice", f.codice.trim()).put("categoria", f.categoria.trim())
            .put("descrizione", desc).put("unita", f.unita.trim().ifEmpty { "pz" })
            .put("quantita", q).put("qta_min", mn).put("qta_max", mx).put("prezzo", pr)
            .put("scadenza", if (sc.isEmpty()) "" else data(sc)!!.format(DMY)).put("data", f.data.trim())
        return null to o
    }

    private fun aggiungiCat(dati: Dati, c: String) {
        if (c.isNotEmpty() && c !in dati.tutteCategorie()) dati.categorie.add(c)
    }

    fun uguali(a: JSONObject, b: JSONObject): Boolean =
        listOf("nome", "codice", "categoria", "descrizione", "unita", "scadenza").all { s(a, it) == s(b, it) } &&
            listOf("quantita", "qta_min", "qta_max", "prezzo").all { abs(d(a, it) - d(b, it)) < 1e-9 }

    fun doppione(dati: Dati, nuovo: JSONObject): Boolean =
        dati.articoli.any { s(it, "nome").equals(s(nuovo, "nome"), true) && s(it, "categoria").equals(s(nuovo, "categoria"), true) }

    fun aggiungi(campi: JSONObject): String? {
        val dati = carica()
        if (err != null) return err
        val n = normalizza(campi)
        if (d(n, "quantita") > 0) {
            n.optJSONArray("movimenti")!!.put(
                JSONObject().put("data", s(campi, "data")).put("delta", d(campi, "quantita")).put("qta", d(campi, "quantita")).put("nota", "Carico iniziale")
            )
        }
        dati.articoli.add(n)
        aggiungiCat(dati, s(n, "categoria"))
        return salva(dati)
    }

    fun modifica(id: String, campi: JSONObject): String? {
        val dati = carica()
        if (err != null) return err
        val a = dati.articoli.firstOrNull { s(it, "id") == id } ?: return "Articolo non più presente"
        val dataOrig = s(a, "data")
        val mod = !uguali(a, campi)
        val nuovaData = if (s(campi, "data") == dataOrig && mod) oggi() else s(campi, "data")
        val qOld = d(a, "quantita")
        val qNew = d(campi, "quantita")
        if (abs(qOld - qNew) > 1e-9) {
            val mov = a.optJSONArray("movimenti") ?: JSONArray().also { a.put("movimenti", it) }
            mov.put(JSONObject().put("data", oggi()).put("delta", Math.round((qNew - qOld) * 1000) / 1000.0).put("qta", qNew).put("nota", "Rettifica da modifica"))
            a.put("movimenti", taglia(mov))
        }
        for (k in listOf("nome", "codice", "categoria", "descrizione", "unita", "quantita", "qta_min", "qta_max", "prezzo", "scadenza")) a.put(k, campi.opt(k))
        a.put("data", nuovaData)
        aggiungiCat(dati, s(a, "categoria"))
        return salva(dati)
    }

    private fun taglia(m: JSONArray): JSONArray {
        if (m.length() <= MAX_MOV) return m
        val n = JSONArray()
        for (i in m.length() - MAX_MOV until m.length()) n.put(m.get(i))
        return n
    }

    fun elimina(id: String): String? {
        val dati = carica()
        if (err != null) return err
        if (!dati.articoli.removeAll { s(it, "id") == id }) return "Articolo non più presente"
        return salva(dati)
    }

    fun eliminaMultiplo(ids: Set<String>): String? {
        if (ids.isEmpty()) return "Nessun articolo selezionato."
        val dati = carica()
        if (err != null) return err
        if (!dati.articoli.removeAll { s(it, "id") in ids }) return "Articoli non più presenti"
        return salva(dati)
    }

    fun modificaMultipla(ids: Set<String>, categoria: String?, min: Double?, max: Double?): String? {
        if (ids.isEmpty()) return "Nessun articolo selezionato."
        if (categoria == null && min == null && max == null) return "Scegli almeno un campo da cambiare."
        if (categoria != null && categoria.length > MAX_CAT) return "La categoria può avere al massimo $MAX_CAT caratteri."
        if ((min != null && (min < 0 || min > MAX_QTA)) || (max != null && (max < 0 || max > MAX_QTA))) {
            return "Le scorte devono essere tra 0 e 900."
        }
        val dati = carica()
        if (err != null) return err
        val sel = dati.articoli.filter { s(it, "id") in ids }
        if (sel.isEmpty()) return "Articoli non più presenti"
        for (a in sel) {
            val nMin = min ?: d(a, "qta_min")
            val nMax = max ?: d(a, "qta_max")
            if (nMax != 0.0 && nMax < nMin) {
                return "Per «" + s(a, "nome") + "» il massimo sarebbe inferiore al minimo. Nessun articolo è stato modificato."
            }
        }
        for (a in sel) {
            if (categoria != null) a.put("categoria", categoria)
            if (min != null) a.put("qta_min", min)
            if (max != null) a.put("qta_max", max)
            a.put("data", oggi())
        }
        if (!categoria.isNullOrEmpty()) aggiungiCat(dati, categoria)
        return salva(dati)
    }

    fun duplica(id: String): String? {
        val dati = carica()
        if (err != null) return err
        val a = dati.articoli.firstOrNull { s(it, "id") == id } ?: return "Articolo non più presente"
        val n = normalizza(JSONObject(a.toString()).put("id", "").put("nome", (s(a, "nome") + " (copia)").take(MAX_NOME + 7)).put("movimenti", JSONArray()).put("data", oggi()))
        dati.articoli.add(n)
        return salva(dati)
    }

    fun movimento(id: String, delta: Double, nota: String, forza: Boolean = false): String? {
        if (delta == 0.0 || delta.isNaN() || delta.isInfinite()) return "Inserisci una quantità maggiore di zero."
        val dati = carica()
        if (err != null) return err
        val a = dati.articoli.firstOrNull { s(it, "id") == id } ?: return "Articolo non più presente"
        val q = d(a, "quantita")
        if (delta < 0 && -delta > q + 1e-9 && !forza) return "Giacenza insufficiente: disponibili " + q(q) + " " + s(a, "unita")
        val nuova = Math.round((q + delta) * 1000) / 1000.0
        if (nuova > MAX_QTA) return "La giacenza non può superare 900."
        if (nuova < 0) return "Giacenza insufficiente: disponibili " + q(q) + " " + s(a, "unita")
        a.put("quantita", nuova).put("data", oggi())
        val mov = a.optJSONArray("movimenti") ?: JSONArray()
        mov.put(JSONObject().put("data", oggi()).put("delta", Math.round(delta * 1000) / 1000.0).put("qta", nuova)
            .put("nota", nota.trim().ifEmpty { if (delta > 0) "Carico" else "Scarico" }))
        a.put("movimenti", taglia(mov))
        return salva(dati)
    }

    fun caricaRiordino(ids: Collection<String>): Pair<Int, String?> {
        val dati = carica()
        if (err != null) return 0 to err
        var n = 0
        for (a in dati.articoli) {
            if (s(a, "id") !in ids) continue
            val o = stato(a).daOrdinare
            if (o <= 0) continue
            val nuova = Math.round((d(a, "quantita") + o) * 1000) / 1000.0
            if (nuova > MAX_QTA) return n to "La giacenza non può superare 900 («" + s(a, "nome") + "»): nessuna modifica salvata."
        }
        for (a in dati.articoli) {
            if (s(a, "id") !in ids) continue
            val o = stato(a).daOrdinare
            if (o <= 0) continue
            val nuova = Math.round((d(a, "quantita") + o) * 1000) / 1000.0
            a.put("quantita", nuova).put("data", oggi())
            val mov = a.optJSONArray("movimenti") ?: JSONArray()
            mov.put(JSONObject().put("data", oggi()).put("delta", o).put("qta", nuova).put("nota", "Carico riordino"))
            a.put("movimenti", taglia(mov))
            n++
        }
        return n to salva(dati)
    }

    class Sugg(val id: String, val nome: String, val unita: String, val alGiorno: Double, val attuale: Double, val suggerita: Double, val max: Double) {
        val sfora: Boolean get() = max > 0 && suggerita > max
    }

    fun scortaMinSuggerita(a: JSONObject, oggi: LocalDate = LocalDate.now(), finestra: Int = 90, copertura: Int = 14): Pair<Double, Double>? {
        val mov = a.optJSONArray("movimenti") ?: return null
        val l = ArrayList<Pair<LocalDate, JSONObject>>()
        for (i in 0 until mov.length()) {
            val m = mov.optJSONObject(i) ?: continue
            val dt = data(s(m, "data")) ?: continue
            l.add(dt to m)
        }
        if (l.isEmpty()) return null
        val inizio = maxOf(oggi.minusDays(finestra.toLong()), l.minOf { it.first })
        val scarichi = l.filter { (dt, m) -> d(m, "delta") < 0 && s(m, "nota") != "Rettifica da modifica" && !dt.isBefore(inizio) }
            .map { -d(it.second, "delta") }
        if (scarichi.size < 2) return null
        val giorni = max(java.time.temporal.ChronoUnit.DAYS.between(inizio, oggi).toInt(), copertura)
        val alGiorno = scarichi.sum() / giorni
        var v = alGiorno * copertura
        v = if (s(a, "unita").ifEmpty { "pz" } in listOf("pz", "conf", "scatola", "set")) {
            val n = v.toInt()
            (n + if (v > n) 1 else 0).toDouble()
        } else Math.round(v * 100) / 100.0
        v = min(v, 900.0)
        return if (v > 0) alGiorno to v else null
    }

    fun suggerimenti(base: List<JSONObject>): List<Sugg> = base.mapNotNull { a ->
        val r = scortaMinSuggerita(a) ?: return@mapNotNull null
        if (abs(r.second - d(a, "qta_min")) <= 1e-9) null
        else Sugg(s(a, "id"), s(a, "nome"), s(a, "unita"), r.first, d(a, "qta_min"), r.second, d(a, "qta_max"))
    }

    fun applicaSuggerimenti(ids: Collection<String>): Pair<Int, String?> {
        val dati = carica()
        if (err != null) return 0 to err
        var n = 0
        for (a in dati.articoli) {
            if (s(a, "id") !in ids) continue
            val r = scortaMinSuggerita(a) ?: continue
            val mx = d(a, "qta_max")
            if (mx > 0 && r.second > mx) continue
            a.put("qta_min", r.second).put("data", oggi())
            n++
        }
        return n to (if (n > 0) salva(dati) else null)
    }

    fun conteggioCategorie(dati: Dati): List<Pair<String, Int>> =
        dati.tutteCategorie().map { c -> c to dati.articoli.count { s(it, "categoria") == c } }

    fun nuovaCategoria(nome: String): String? {
        val n = nome.trim()
        if (n.isEmpty()) return "Nome obbligatorio"
        if (n.length > MAX_NOME_CAT) return "Nome troppo lungo (max $MAX_NOME_CAT)"
        val dati = carica()
        if (err != null) return err
        if (dati.tutteCategorie().any { it.equals(n, true) }) return "La categoria «$n» esiste già"
        dati.categorie.add(n)
        return salva(dati)
    }

    fun rinominaCategoria(vecchio: String, nuovo: String): String? {
        val n = nuovo.trim()
        if (n.isEmpty()) return "Nome obbligatorio"
        if (n.length > MAX_NOME_CAT) return "Nome troppo lungo (max $MAX_NOME_CAT)"
        if (n == vecchio) return null
        val dati = carica()
        if (err != null) return err
        dati.categorie.removeAll { it == vecchio }
        if (n !in dati.categorie) dati.categorie.add(n)
        for (a in dati.articoli) if (s(a, "categoria") == vecchio) a.put("categoria", n)
        return salva(dati)
    }

    fun eliminaCategoria(nome: String): String? {
        val dati = carica()
        if (err != null) return err
        dati.categorie.removeAll { it == nome }
        for (a in dati.articoli) if (s(a, "categoria") == nome) a.put("categoria", "")
        return salva(dati)
    }

    fun aggiungiCategorie(nomi: List<String>): Int {
        val dati = carica()
        if (err != null) return 0
        val esist = dati.tutteCategorie().map { it.lowercase() }.toSet()
        val nuove = nomi.filter { it.lowercase() !in esist }
        dati.categorie.addAll(nuove)
        if (nuove.isNotEmpty()) salva(dati)
        return nuove.size
    }

    fun reset(): String? {
        if (err != null) return err
        return try {
            File(dir, FILE).delete()
            null
        } catch (e: Exception) {
            "Reset non riuscito: " + e.message
        }
    }

    fun righeRiordino(dati: Dati): List<Pair<JSONObject, Double>> =
        dati.articoli.sortedWith(compareBy({ s(it, "categoria").lowercase() }, { s(it, "nome").lowercase() }))
            .mapNotNull { a -> stato(a).daOrdinare.takeIf { it > 0 }?.let { a to it } }

    fun testoRiordino(dati: Dati, oggi: String = oggi()): String {
        val r = righeRiordino(dati)
        if (r.isEmpty()) return ""
        val sb = StringBuilder("LISTA RIORDINO MAGAZZINO — $oggi\n\n")
        var tot = 0.0
        for ((a, o) in r) {
            val costo = o * d(a, "prezzo")
            tot += costo
            sb.append("• ").append(s(a, "nome"))
            if (s(a, "categoria").isNotEmpty()) sb.append(" (").append(s(a, "categoria")).append(")")
            sb.append(": ordinare ").append(q(o)).append(" ").append(s(a, "unita"))
            sb.append("  (giacenza ").append(q(d(a, "quantita"))).append(", min ").append(q(d(a, "qta_min"))).append(")")
            if (d(a, "prezzo") > 0) sb.append("  ~ ").append(eur(costo))
            sb.append("\n")
        }
        sb.append("\nTOTALE STIMATO: ").append(eur(tot))
        return sb.toString()
    }
}
