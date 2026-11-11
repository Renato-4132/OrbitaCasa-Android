package it.orbitacasa.mobile

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Locale

object Utenze {
    const val FILE = "utenze_db.json"

    val TIPI = listOf("Acqua", "Luce", "Gas")
    val ICONE = mapOf("Acqua" to "💧", "Luce" to "💡", "Gas" to "🔥")
    val UNITA = mapOf("Acqua" to "m³", "Luce" to "kWh", "Gas" to "Smc")
    /** Stessi campi e stesse chiavi dell'anagrafica del desktop (utenze.py). */
    val CAMPI_CONTRATTO = listOf(
        "Ragione sociale", "Telefono", "Email", "Numero contratto",
        "Codice Cliente", "Codice Utenza / Fornitura", "POD / PDR", "Note",
    )
    val CAMPI_OFFERTA = listOf(
        "Nome Offerta", "Tipo Tariffa", "Costo Unitario", "Quota Fissa", "Scadenza Contratto",
    )
    val CAMPI_ASSISTENZA = listOf("Pronto Intervento", "Modalita Pagamento", "IBAN")
    val CAMPI = CAMPI_CONTRATTO + CAMPI_OFFERTA + CAMPI_ASSISTENZA

    /** Etichetta mostrata; la chiave nel JSON resta quella del desktop. */
    val ETICHETTE = mapOf(
        "Costo Unitario" to "Costo unitario tutto incluso (€/unità)",
        "Quota Fissa" to "Quota fissa (info, non nel calcolo)",
        "Tipo Tariffa" to "Tipo tariffa (fissa/variabile)",
        "Pronto Intervento" to "Pronto intervento / guasti",
        "Modalita Pagamento" to "Modalità pagamento",
        "IBAN" to "IBAN addebito",
    )

    private lateinit var dir: File

    fun init(d: File) {
        dir = d
    }

    class Lettura(val mese: Int, val prec: Double, val att: Double, val consumo: Double)

    class Anno(val anno: String, val letture: List<Lettura>) {
        val totale: Double get() = letture.sumOf { it.consumo }
        val mesi: Int get() = letture.count { it.consumo > 0 || it.att > 0 }
    }

    private fun leggi(): JSONObject? =
        try {
            val f = File(dir, FILE)
            if (f.exists()) JSONObject(f.readText(Charsets.UTF_8)) else null
        } catch (_: Exception) {
            null
        }

    fun fmt(v: Double): String = String.format(Locale.ITALY, "%,.2f", v)

    fun anni(utenza: String): List<Anno> {
        val per = leggi()?.optJSONObject("letture_salvate")?.optJSONObject(utenza) ?: return emptyList()
        val out = ArrayList<Anno>()
        for (a in per.keys()) {
            val righe = per.optJSONArray(a) ?: continue
            val l = ArrayList<Lettura>()
            for (i in 0 until righe.length()) {
                val r = righe.optJSONArray(i) ?: continue
                val m = r.optString(0).substringBefore('/').toIntOrNull() ?: continue
                l.add(Lettura(m, r.optDouble(1, 0.0), r.optDouble(2, 0.0), r.optDouble(3, 0.0)))
            }
            if (l.any { it.prec != 0.0 || it.att != 0.0 || it.consumo != 0.0 }) out.add(Anno(a, l.sortedBy { it.mese }))
        }
        return out.sortedByDescending { it.anno }
    }

    fun anagrafica(utenza: String): Map<String, String> {
        val o = leggi()?.optJSONObject("anagrafiche")?.optJSONObject(utenza) ?: return emptyMap()
        val m = LinkedHashMap<String, String>()
        for (c in CAMPI) {
            val v = o.optString(c, "").trim()
            if (v.isNotEmpty()) m[c] = v
        }
        return m
    }

    /** Come leggi(), ma per scrivere: se il file esiste e non si legge, lo copia in .corrotto e si ferma. */
    private fun leggiPerScrittura(): JSONObject {
        val f = File(dir, FILE)
        if (!f.exists()) return JSONObject()
        try {
            return JSONObject(f.readText(Charsets.UTF_8))
        } catch (_: Exception) {
            try {
                Files.copy(f.toPath(), File(dir, "$FILE.corrotto").toPath(), StandardCopyOption.REPLACE_EXISTING)
            } catch (_: Exception) {
            }
            throw IllegalStateException("$FILE illeggibile, non lo sovrascrivo (copia in $FILE.corrotto)")
        }
    }

    /** Salva i campi dell'anagrafica; le chiavi interne del desktop (_import_auto, _categoria_bollette...) restano intatte. */
    fun salvaAnagrafica(utenza: String, valori: Map<String, String>) {
        val base = leggiPerScrittura()
        if (base.optJSONObject("letture_salvate") == null) base.put("letture_salvate", JSONObject())
        val anag = base.optJSONObject("anagrafiche") ?: JSONObject().also { base.put("anagrafiche", it) }
        val o = anag.optJSONObject(utenza) ?: JSONObject().also { anag.put(utenza, it) }
        for (c in CAMPI) o.put(c, (valori[c] ?: o.optString(c, "")).trim())
        val f = File(dir, FILE)
        val tmp = File(dir, "$FILE.tmp")
        tmp.writeText(base.toString(1), Charsets.UTF_8)
        Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }

    fun costoUnitario(utenza: String): Double? =
        leggi()?.optJSONObject("anagrafiche")?.optJSONObject(utenza)
            ?.optString("Costo Unitario", "")?.trim()?.replace(',', '.')?.toDoubleOrNull()

    /** Sempre 12 mesi: quelli senza riga salvata sono a zero. */
    fun anno12(utenza: String, anno: String): List<Lettura> {
        val per = anni(utenza).firstOrNull { it.anno == anno }?.letture ?: emptyList()
        return (1..12).map { m -> per.firstOrNull { it.mese == m } ?: Lettura(m, 0.0, 0.0, 0.0) }
    }

    fun totaleAnno(utenza: String, anno: Int): Double? =
        anni(utenza).firstOrNull { it.anno == anno.toString() }?.totale

    fun consumoMese(utenza: String, anno: Int, mese: Int): Double? {
        val l = anni(utenza).firstOrNull { it.anno == anno.toString() }?.letture?.firstOrNull { it.mese == mese }
            ?: return null
        return if (l.att > 0 || l.consumo > 0) l.consumo else null
    }

    fun variazione(corr: Double?, prec: Double?): String {
        if (corr == null || prec == null || prec <= 0) return "n/d"
        val v = (corr - prec) / prec * 100
        val f = if (v > 0.05) "▲" else if (v < -0.05) "▼" else "="
        return f + " " + String.format(Locale.ITALY, "%+.1f%%", v)
    }

    /** Lettura precedente proposta: l'attuale del mese prima (dicembre dell'anno prima per gennaio). */
    fun precSuggerita(utenza: String, anno: Int, mese: Int): Double {
        val (a, m) = if (mese == 1) (anno - 1) to 12 else anno to (mese - 1)
        val l = anni(utenza).firstOrNull { it.anno == a.toString() }?.letture?.firstOrNull { it.mese == m }
        return if (l != null && l.att > 0) l.att else 0.0
    }

    private fun riga4(anno: Int, mese: Int, prec: Double, att: Double): JSONArray =
        JSONArray().put(String.format(Locale.US, "%02d/%d", mese, anno)).put(prec).put(att)
            .put(Math.round(maxOf(0.0, att - prec) * 100) / 100.0)

    private fun leggiAnnoGrezzo(per: JSONObject, anno: Int): Array<DoubleArray> {
        val out = Array(12) { DoubleArray(2) }
        val righe = per.optJSONArray(anno.toString()) ?: return out
        for (i in 0 until righe.length()) {
            val r = righe.optJSONArray(i) ?: continue
            val m = r.optString(0).substringBefore('/').toIntOrNull() ?: continue
            if (m !in 1..12) continue
            out[m - 1][0] = r.optDouble(1, 0.0)
            out[m - 1][1] = r.optDouble(2, 0.0)
        }
        return out
    }

    private fun scriviAnnoGrezzo(per: JSONObject, anno: Int, v: Array<DoubleArray>) {
        val arr = JSONArray()
        for (m in 1..12) arr.put(riga4(anno, m, v[m - 1][0], v[m - 1][1]))
        per.put(anno.toString(), arr)
    }

    /**
     * Salva una lettura come sul desktop: ricalcola il consumo, la lettura precedente del mese dopo
     * diventa questa lettura attuale (da dicembre si passa a gennaio dell'anno dopo, creandolo se serve).
     */
    fun salvaLettura(utenza: String, anno: Int, mese: Int, prec: Double, att: Double) {
        val base = leggi() ?: JSONObject()
        val l = base.optJSONObject("letture_salvate") ?: JSONObject().also { base.put("letture_salvate", it) }
        if (base.optJSONObject("anagrafiche") == null) base.put("anagrafiche", JSONObject())
        val per = l.optJSONObject(utenza) ?: JSONObject().also { l.put(utenza, it) }
        val v = leggiAnnoGrezzo(per, anno)
        v[mese - 1][0] = prec
        v[mese - 1][1] = att
        if (mese < 12) {
            v[mese][0] = att
            scriviAnnoGrezzo(per, anno, v)
        } else {
            scriviAnnoGrezzo(per, anno, v)
            val v2 = leggiAnnoGrezzo(per, anno + 1)
            v2[0][0] = att
            scriviAnnoGrezzo(per, anno + 1, v2)
        }
        val f = File(dir, FILE)
        val tmp = File(dir, "$FILE.tmp")
        tmp.writeText(base.toString(1), Charsets.UTF_8)
        Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }

    private val KEYWORDS = mapOf(
        "Acqua" to listOf("(?<![a-zà-ú])acqua(?![a-zà-ú])", "(?<![a-zà-ú])idric", "(?<![a-zà-ú])acquedott"),
        "Luce" to listOf(
            "(?<![a-zà-ú])luce(?![a-zà-ú])", "(?<![a-zà-ú])elettric",
            "(?<![a-zà-ú])energia(?![a-zà-ú])", "(?<![a-zà-ú])kwh(?![a-zà-ú])",
        ),
        "Gas" to listOf(
            "(?<![a-zà-ú])gas(?![a-zà-ú])", "(?<![a-zà-ú])metano(?![a-zà-ú])", "(?<![a-zà-ú])gpl(?![a-zà-ú])",
        ),
    )

    private fun matchUtenze(descr: String): List<String> {
        val d = descr.lowercase()
        return KEYWORDS.filter { (_, kws) -> kws.any { Regex(it).containsMatchIn(d) } }.keys.toList()
    }

    /** Categoria di spesa scelta per le bollette di questa utenza ("" se nessuna). */
    fun categoriaBollette(utenza: String): String =
        leggi()?.optJSONObject("anagrafiche")?.optJSONObject(utenza)?.optString("_categoria_bollette", "")?.trim() ?: ""

    fun categoriaScelta(utenza: String): Boolean =
        leggi()?.optJSONObject("anagrafiche")?.optJSONObject(utenza)?.has("_categoria_bollette") == true

    /** Proposta come sul desktop: categoria col nome dell'utenza, altrimenti una che contiene "bollett"/"utenz". */
    fun categoriaProposta(utenza: String, cats: List<String>): String {
        val att = categoriaBollette(utenza)
        if (att.isNotEmpty() && att in cats) return att
        if (categoriaScelta(utenza)) return ""
        return cats.firstOrNull { utenza in matchUtenze(it) }
            ?: cats.firstOrNull { it.lowercase().let { c -> "bollett" in c || "utenz" in c } }
            ?: ""
    }

    fun salvaCategorieBollette(scelte: Map<String, String>) {
        val base = leggi() ?: JSONObject()
        if (base.optJSONObject("letture_salvate") == null) base.put("letture_salvate", JSONObject())
        val anag = base.optJSONObject("anagrafiche") ?: JSONObject().also { base.put("anagrafiche", it) }
        for ((u, c) in scelte) {
            val o = anag.optJSONObject(u) ?: JSONObject().also { anag.put(u, it) }
            o.put("_categoria_bollette", c)
        }
        val f = File(dir, FILE)
        val tmp = File(dir, "$FILE.tmp")
        tmp.writeText(base.toString(1), Charsets.UTF_8)
        Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }

    /** Bollette per mese ("MM/AAAA" -> totale) prese dalle uscite della categoria scelta. */
    fun bollette(utenza: String): Map<String, Double> {
        val cat = categoriaBollette(utenza)
        if (cat.isEmpty()) return emptyMap()
        val condivisa = TIPI.count { categoriaBollette(it) == cat } > 1
        val out = HashMap<String, Double>()
        for (v in Repo.voci()) {
            if (v.categoria != cat || v.tipo != "Uscita") continue
            if (condivisa && matchUtenze(v.descrizione) != listOf(utenza)) continue
            val k = String.format(Locale.US, "%02d/%d", v.data.monthValue, v.data.year)
            out[k] = (out[k] ?: 0.0) + v.importo
        }
        return out
    }

    fun vuoto(): Boolean = TIPI.all { anni(it).isEmpty() && anagrafica(it).isEmpty() }
}
