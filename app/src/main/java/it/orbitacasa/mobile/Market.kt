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

object Market {
    const val FILE = "supermercati.json"
    const val F_LISTA = "lista_spesa_super.json"

    val DEFAULT = listOf("Coop", "Dpiu", "Esselunga", "Eurospin", "Lidl", "Maurys")

    val CATEGORIE = listOf(
        "Affettati", "Acqua", "Animali Domestici", "Articoli Bimbi",
        "Assorbenza", "Auto e Elettronica", "Birre", "Biscotti", "Bucato",
        "Caffè e Bevande", "Cancelleria/Party", "Carne", "Carta Casa/Igiene",
        "Carta e Alluminio", "Casalinghi/Tessile", "Cereali Colazione",
        "Cibi Etnici", "Cioccolato", "Conserve Pesce", "Cosmetici",
        "Cura dei Capelli", "Cura del Corpo", "Dolciumi e Caramelle",
        "Farine e Lieviti", "Formaggi Freschi", "Formaggi Stagionati",
        "Frutta Fresca", "Gastronomia", "Gelati", "Giardino e Fai da",
        "Igiene Persona", "Insalate Pronte", "Integratori/Sanitari",
        "Latte e Burro", "Latticini e Yogurt", "Legumi Secchi/Scatole",
        "Liquori e Distillati", "Marmellate/Creme", "Merende e Snack",
        "Molluschi/Crostacei", "Olio", "Ortaggi e Tuberi", "Pane e Panini",
        "Pasta Fresca", "Pasta Secca", "Pesce", "Piatti Pronti",
        "Pizze Surgelate", "Pollame", "Prodotti Bio", "Prodotti Dietetici",
        "Prodotti Veg/Vegan", "Pulizia Casa", "Riso", "Salumi",
        "Salse e Condimenti", "Snack Panetteria", "Snack Salati",
        "Sottoli/Sottaceti", "Spugne e Guanti", "Succhi/Bibite",
        "Surgelati Pesce", "Surgelati Vari", "Surgelati Verdura",
        "Sushi e Tartare", "Uova", "Varie", "Verdura Fresca",
        "Vini e Spumanti",
    )

    val DMY: DateTimeFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy")

    enum class Stato { NESSUNA, ATTIVA, FUTURA, SCADUTA }

    class Form(
        val nome: String, val descrizione: String, val categoria: String, val prezzo: String,
        val quantita: String, val promo: Boolean, val prezzoPromo: String,
        val inizioPromo: String, val scadenzaPromo: String, val dataInserimento: String,
    )

    class Offerta(
        val nome: String, val quantita: String, val supermercato: String, val descrizione: String,
        val categoria: String, val prezzo: Double, val prezzoBase: Double?, val stato: Stato,
        val inizio: String, val fine: String, val top: Boolean,
    ) {
        val inPromo: Boolean get() = stato == Stato.ATTIVA
    }

    class Riga(val nome: String, val supermercato: String, val qtaCatalogo: String, var qta: Int, val prezzoUn: Double) {
        val totale: Double get() = qta * prezzoUn
    }

    private lateinit var dir: File
    private var err: String? = null

    fun init(dir: File) {
        this.dir = dir
    }

    fun errore(): String? = err

    fun eur(x: Double): String = "€ " + String.format(Locale.ITALY, "%,.2f", x)

    fun data(s: String): LocalDate? = try {
        if (s.isBlank()) null else LocalDate.parse(s.trim(), DMY)
    } catch (_: Exception) {
        null
    }

    fun s(o: JSONObject, k: String): String = if (o.isNull(k)) "" else o.optString(k, "").trim()

    fun prezzo(o: JSONObject, k: String): Double? {
        if (o.isNull(k)) return null
        val v = o.opt(k)
        val d = when (v) {
            is Number -> v.toDouble()
            is String -> v.trim().replace(',', '.').toDoubleOrNull()
            else -> null
        }
        return d?.takeIf { !it.isNaN() && !it.isInfinite() }
    }

    fun promoOn(o: JSONObject): Boolean = when (val v = o.opt("promo")) {
        is Boolean -> v
        is String -> v.equals("true", true) || v == "1"
        is Number -> v.toInt() != 0
        else -> false
    }

    private fun ordina(l: MutableList<JSONObject>) {
        l.sortBy { s(it, "nome").lowercase() }
    }

    private fun leggiFile(nome: String): Any? {
        val f = File(dir, nome)
        if (!f.isFile || f.length() == 0L) return null
        return try {
            JSONTokener(f.readText(Charsets.UTF_8)).nextValue()
        } catch (e: Exception) {
            throw IllegalStateException(nome)
        }
    }

    private fun scrivi(nome: String, v: Any) {
        val f = File(dir, nome)
        val tmp = File(f.parentFile, "$nome.tmp")
        tmp.writeText(if (v is JSONArray) v.toString(2) else (v as JSONObject).toString(2), Charsets.UTF_8)
        Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }

    fun carica(): LinkedHashMap<String, MutableList<JSONObject>> {
        err = null
        val out = LinkedHashMap<String, MutableList<JSONObject>>()
        val v = try {
            leggiFile(FILE)
        } catch (e: Exception) {
            err = "$FILE risulta illeggibile o corrotto: nessuna modifica effettuata"
            null
        }
        if (v is JSONObject) {
            for (k in v.keys().asSequence().toList().sorted()) {
                val a = v.optJSONArray(k) ?: continue
                val l = ArrayList<JSONObject>()
                for (i in 0 until a.length()) a.optJSONObject(i)?.let { l.add(it) }
                ordina(l)
                out[k] = l
            }
        }
        if (out.isEmpty()) for (n in DEFAULT) out[n] = ArrayList()
        return out
    }

    private fun salva(db: Map<String, MutableList<JSONObject>>): String? {
        if (err != null) return err
        return try {
            val o = JSONObject()
            for (k in db.keys.sorted()) {
                val l = db[k]!!
                ordina(l)
                val a = JSONArray()
                for (x in l) a.put(x)
                o.put(k, a)
            }
            scrivi(FILE, o)
            null
        } catch (e: Exception) {
            "Salvataggio non riuscito: " + e.message
        }
    }

    fun nomi(db: Map<String, List<JSONObject>>): List<String> = db.keys.sorted()

    private val LIMITI = listOf("NOME" to 27, "DESCRIZIONE" to 35, "CATEGORIA" to 24, "QUANTITA" to 18, "PREZZO" to 7, "PREZZO_PROMO" to 7)

    fun valida(f: Form): List<String> {
        val e = ArrayList<String>()
        val prezzo = f.prezzo.trim().replace(',', '.')
        val pp = f.prezzoPromo.trim().replace(',', '.')
        if (f.nome.isBlank() || prezzo.isBlank()) e.add("Nome e prezzo sono obbligatori.")
        if (f.promo) {
            if (pp.isBlank()) e.add("Con la promozione attiva il prezzo promo è obbligatorio.")
            if (f.inizioPromo.isBlank()) e.add("Con la promozione attiva la data inizio promo è obbligatoria.")
            if (f.scadenzaPromo.isBlank()) e.add("Con la promozione attiva la data scadenza promo è obbligatoria.")
        }
        val valori = mapOf(
            "NOME" to f.nome.trim(), "DESCRIZIONE" to f.descrizione.trim(), "CATEGORIA" to f.categoria.trim(),
            "QUANTITA" to f.quantita.trim(), "PREZZO" to prezzo, "PREZZO_PROMO" to pp,
        )
        for ((k, max) in LIMITI) {
            val n = valori[k]!!.length
            if (n > max) e.add("Il campo $k ha $n caratteri, il massimo è $max.")
        }
        for ((k, v) in listOf("PREZZO" to prezzo, "PREZZO_PROMO" to pp)) {
            if (v.isNotEmpty()) {
                val d = v.toDoubleOrNull()
                if (d == null || d.isNaN() || d.isInfinite()) e.add("Il campo $k ('$v') non è un numero valido.")
                else if (d < 0) e.add("Il campo $k non può essere negativo.")
            }
        }
        val d1 = if (f.dataInserimento.isBlank()) null else data(f.dataInserimento)
        val d2 = if (f.inizioPromo.isBlank()) null else data(f.inizioPromo)
        val d3 = if (f.scadenzaPromo.isBlank()) null else data(f.scadenzaPromo)
        if (f.dataInserimento.isNotBlank() && d1 == null) e.add("DATA INSERIMENTO PREZZO non valida (GG-MM-AAAA).")
        if (f.inizioPromo.isNotBlank() && d2 == null) e.add("DATA INIZIO PROMO non valida (GG-MM-AAAA).")
        if (f.scadenzaPromo.isNotBlank() && d3 == null) e.add("DATA SCADENZA non valida (GG-MM-AAAA).")
        if (f.promo && d2 != null && d3 != null && !d2.isBefore(d3)) e.add("La data inizio promo deve precedere la scadenza.")
        return e
    }

    fun nuovoArticolo(f: Form, supermercato: String, oggi: LocalDate, vecchio: JSONObject? = null): JSONObject {
        val o = if (vecchio == null) JSONObject() else JSONObject(vecchio.toString())
        val ins = f.dataInserimento.trim().ifBlank { oggi.format(DMY) }
        o.put("nome", f.nome.trim()).put("descrizione", f.descrizione.trim()).put("categoria", f.categoria.trim())
            .put("prezzo", f.prezzo.trim().replace(',', '.'))
            .put("promo", f.promo)
            .put("prezzo_promo", if (f.promo) f.prezzoPromo.trim().replace(',', '.') else "")
            .put("supermercato", supermercato)
            .put("quantita", f.quantita.trim())
            .put("data_scadenza", if (f.promo) f.scadenzaPromo.trim() else "")
            .put("data_inserimento_prezzo", ins)
            .put("data_inizio_promo", if (f.promo) f.inizioPromo.trim() else "")
        return o
    }

    fun statoPromo(a: JSONObject, oggi: LocalDate): Stato {
        if (!promoOn(a) || s(a, "prezzo_promo").isBlank()) return Stato.NESSUNA
        val ini = data(s(a, "data_inizio_promo")) ?: return Stato.NESSUNA
        if (oggi.isBefore(ini)) return Stato.FUTURA
        val fine = data(s(a, "data_scadenza"))
        return if (fine == null || !oggi.isAfter(fine)) Stato.ATTIVA else Stato.SCADUTA
    }

    fun prezzoEffettivo(a: JSONObject, oggi: LocalDate): Double? =
        if (statoPromo(a, oggi) == Stato.ATTIVA) prezzo(a, "prezzo_promo") else prezzo(a, "prezzo")

    fun confronto(db: Map<String, List<JSONObject>>, testo: String, filtro: String?, oggi: LocalDate): List<Offerta> {
        val t = testo.trim().lowercase()
        val tutte = ArrayList<Offerta>()
        val supers = if (filtro == null) db.keys.sorted() else listOf(filtro)
        for (sm in supers) {
            for (a in db[sm] ?: continue) {
                val nome = s(a, "nome")
                val cat = s(a, "categoria")
                val desc = s(a, "descrizione")
                if (t.isNotEmpty() && !(t in nome.lowercase() || t in cat.lowercase() || t in desc.lowercase())) continue
                val p = prezzoEffettivo(a, oggi) ?: continue
                val st = statoPromo(a, oggi)
                tutte.add(
                    Offerta(
                        nome, s(a, "quantita"), sm, desc, cat, p, prezzo(a, "prezzo"), st,
                        s(a, "data_inizio_promo"), s(a, "data_scadenza"), false,
                    )
                )
            }
        }
        val gruppi = tutte.groupBy { it.nome.lowercase() to it.quantita.lowercase() }
        val visti = HashSet<String>()
        val out = ArrayList<Offerta>()
        for (o in tutte.sortedBy { it.nome.lowercase() }) {
            if (!visti.add(o.nome + "|" + o.quantita + "|" + o.supermercato + "|" + o.prezzo)) continue
            var top = false
            if (filtro == null) {
                val g = gruppi[o.nome.lowercase() to o.quantita.lowercase()] ?: emptyList()
                val min = g.minOf { it.prezzo }
                top = o.prezzo == min && g.count { it.prezzo == min } < g.size
            }
            out.add(
                Offerta(
                    o.nome, o.quantita, o.supermercato, o.descrizione, o.categoria, o.prezzo, o.prezzoBase,
                    o.stato, o.inizio, o.fine, top,
                )
            )
        }
        return out
    }

    fun migliore(db: Map<String, List<JSONObject>>, nome: String, oggi: LocalDate): Offerta? =
        confronto(db, "", null, oggi).filter { it.nome.equals(nome, true) }.minByOrNull { it.prezzo }

    fun disattivaScadute(db: Map<String, MutableList<JSONObject>>, oggi: LocalDate): Int {
        var n = 0
        for (l in db.values) for (a in l) {
            if (!promoOn(a)) continue
            val fine = data(s(a, "data_scadenza")) ?: continue
            if (fine.isBefore(oggi)) {
                a.put("promo", false).put("prezzo_promo", "").put("data_scadenza", "").put("data_inizio_promo", "")
                n++
            }
        }
        return n
    }

    fun pulisciScadute(oggi: LocalDate): Int {
        val db = carica()
        if (err != null) return 0
        val n = disattivaScadute(db, oggi)
        if (n > 0 && salva(db) != null) return 0
        return n
    }

    private fun controlla(db: Map<String, List<JSONObject>>, sm: String, idx: Int, nome: String): String? {
        val l = db[sm] ?: return "Supermercato non trovato"
        if (idx !in l.indices || s(l[idx], "nome") != nome) return "Articolo non più presente: ricarica la lista"
        return null
    }

    fun aggiungi(sm: String, a: JSONObject): String? {
        val db = carica()
        if (err != null) return err
        db.getOrPut(sm) { ArrayList() }.add(a)
        return salva(db)
    }

    fun modifica(sm: String, idx: Int, nomeAtteso: String, a: JSONObject): String? {
        val db = carica()
        if (err != null) return err
        controlla(db, sm, idx, nomeAtteso)?.let { return it }
        db[sm]!![idx] = a
        return salva(db)
    }

    fun elimina(sm: String, idx: Int, nomeAtteso: String): String? {
        val db = carica()
        if (err != null) return err
        controlla(db, sm, idx, nomeAtteso)?.let { return it }
        db[sm]!!.removeAt(idx)
        return salva(db)
    }

    fun sposta(sm: String, idx: Int, nomeAtteso: String, dest: String): String? {
        if (sm == dest) return "L'articolo è già in questo supermercato"
        val db = carica()
        if (err != null) return err
        controlla(db, sm, idx, nomeAtteso)?.let { return it }
        val a = db[sm]!!.removeAt(idx)
        a.put("supermercato", dest)
        db.getOrPut(dest) { ArrayList() }.add(a)
        return salva(db)
    }

    fun nuovoSuper(nome: String): String? {
        val n = nome.trim()
        if (n.isEmpty()) return "Nome obbligatorio"
        if (n.length > 30) return "Nome troppo lungo (max 30)"
        val db = carica()
        if (err != null) return err
        if (db.keys.any { it.equals(n, true) }) return "Il supermercato '$n' esiste già"
        db[n] = ArrayList()
        return salva(db)
    }

    fun rinomina(vecchio: String, nuovo: String): String? {
        val n = nuovo.trim()
        if (n.isEmpty()) return "Nome obbligatorio"
        if (n.length > 30) return "Nome troppo lungo (max 30)"
        if (n == vecchio) return null
        val db = carica()
        if (err != null) return err
        if (db.keys.any { it.equals(n, true) && it != vecchio }) return "Il supermercato '$n' esiste già"
        val l = db.remove(vecchio) ?: return "Supermercato non trovato"
        for (a in l) a.put("supermercato", n)
        db[n] = l
        val e = salva(db)
        if (e == null) {
            val lista = leggiLista()
            if (lista.any { it.supermercato == vecchio }) {
                salvaLista(lista.map { if (it.supermercato == vecchio) Riga(it.nome, n, it.qtaCatalogo, it.qta, it.prezzoUn) else it })
            }
        }
        return e
    }

    fun svuota(sm: String): String? {
        val db = carica()
        if (err != null) return err
        val l = db[sm] ?: return "Supermercato non trovato"
        l.clear()
        return salva(db)
    }

    fun eliminaSuper(sm: String): String? {
        val db = carica()
        if (err != null) return err
        val l = db[sm] ?: return "Supermercato non trovato"
        if (l.isNotEmpty()) return "Il supermercato contiene ancora ${l.size} articoli: svuotalo prima"
        db.remove(sm)
        return salva(db)
    }

    fun leggiLista(): List<Riga> {
        val v = try {
            leggiFile(F_LISTA)
        } catch (e: Exception) {
            null
        }
        val a = v as? JSONArray ?: return emptyList()
        val out = ArrayList<Riga>()
        for (i in 0 until a.length()) {
            val o = a.optJSONObject(i) ?: continue
            val q = o.optInt("qta", 0)
            if (q <= 0) continue
            out.add(Riga(s(o, "nome"), s(o, "supermercato"), s(o, "qta_catalogo"), q, o.optDouble("prezzo_un", 0.0)))
        }
        return out
    }

    fun salvaLista(l: List<Riga>): String? = try {
        val a = JSONArray()
        for (r in l) {
            a.put(
                JSONObject().put("nome", r.nome).put("supermercato", r.supermercato)
                    .put("qta_catalogo", r.qtaCatalogo).put("qta", r.qta).put("prezzo_un", r.prezzoUn)
            )
        }
        scrivi(F_LISTA, a)
        null
    } catch (e: Exception) {
        "Salvataggio lista non riuscito: " + e.message
    }

    fun aggiungiALista(o: Offerta): Int {
        val l = leggiLista().toMutableList()
        val r = l.firstOrNull { it.nome == o.nome && it.supermercato == o.supermercato }
        if (r != null) r.qta += 1 else l.add(Riga(o.nome, o.supermercato, o.quantita, 1, o.prezzo))
        salvaLista(l)
        return (r?.qta ?: 1)
    }

    fun impostaQta(nome: String, sm: String, qta: Int): String? {
        val l = leggiLista().toMutableList()
        val i = l.indexOfFirst { it.nome == nome && it.supermercato == sm }
        if (i < 0) return null
        if (qta <= 0) l.removeAt(i) else l[i].qta = qta
        return salvaLista(l)
    }

    const val DA_ASSEGNARE = "Da assegnare"

    fun aggiungiRiordino(righe: List<Pair<JSONObject, Double>>, oggi: LocalDate): Triple<Int, Int, String?> {
        val db = carica()
        val l = leggiLista().toMutableList()
        var trovati = 0
        var senza = 0
        for ((a, ord) in righe) {
            val nomeStock = Stock.s(a, "nome")
            val qta = Math.ceil(ord - 1e-9).toInt().coerceAtLeast(1)
            val o = migliore(db, nomeStock, oggi)
            val nome = o?.nome ?: nomeStock
            val sm = o?.supermercato ?: DA_ASSEGNARE
            val r = l.firstOrNull { it.nome.equals(nome, true) && it.supermercato == sm }
            if (r != null) {
                r.qta += qta
            } else {
                l.add(Riga(nome, sm, o?.quantita ?: Stock.s(a, "unita"), qta, o?.prezzo ?: Stock.d(a, "prezzo")))
            }
            if (o != null) trovati++ else senza++
        }
        return Triple(trovati, senza, salvaLista(l))
    }

    fun totaleLista(l: List<Riga>): Double = l.sumOf { it.totale }
}
