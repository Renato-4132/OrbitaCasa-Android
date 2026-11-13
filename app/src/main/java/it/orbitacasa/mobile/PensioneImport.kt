package it.orbitacasa.mobile

import org.json.JSONObject
import java.time.LocalDate
import kotlin.math.abs

/** Importazione da PDF (estratto conto del fondo) del Fondo Pensione, tramite Gemini. */
object PensioneImport {
    class Prop(
        val chiave: String, val data: LocalDate, val tipo: String, val importo: Double, val motivo: String,
    ) {
        fun etichetta(): String {
            val tit = when (chiave) {
                Pensione.VERSAMENTI -> "Versamento · $tipo"
                Pensione.VALORIZZAZIONI -> "Valorizzazione"
                else -> "Riscatto · $tipo"
            }
            return Pensione.fmt(data) + "  " + tit + "  " + euro(importo)
        }
    }

    class Esito(val nuove: List<Prop>, val giaPresenti: Int)

    fun prompt(): String =
        "Leggi questo documento di un fondo pensione italiano (estratto conto, comunicazione periodica, " +
            "posizione individuale o riepilogo contributi). Rispondi SOLO con un oggetto JSON in questa forma:\n" +
            "{\"valorizzazione\": {\"data\": \"gg-mm-aaaa\", \"controvalore\": numero} oppure null,\n" +
            " \"versamenti\": [{\"data\": \"gg-mm-aaaa\", \"tipo\": \"TFR|Lavoratore|Datore di Lavoro|Volontario\", \"importo\": numero}],\n" +
            " \"riscatti\": [{\"data\": \"gg-mm-aaaa\", \"tipo\": \"Anticipazione|Riscatto Parziale|Riscatto Totale|Trasferimento ad altro fondo\", \"importo\": numero, \"motivo\": \"testo breve\"}]}\n" +
            "Regole: \"valorizzazione\" è il valore (controvalore) della posizione alla data di riferimento del documento. " +
            "Le date sono nel formato gg-mm-aaaa. Gli importi sono numeri positivi con il punto come separatore decimale, " +
            "senza simbolo di valuta. Includi in \"versamenti\" e \"riscatti\" solo i movimenti realmente presenti nel documento, " +
            "uno per ogni riga con la sua data e il suo tipo di contributo; se non ce ne sono usa una lista vuota. " +
            "Non inventare nulla e non sommare movimenti diversi."

    private fun num(j: JSONObject, k: String): Double? {
        val v = j.opt(k) ?: return null
        if (v is Number) return v.toDouble()
        var s = v.toString().replace("€", "").replace(" ", "").replace(" ", "").trim()
        if (s.isEmpty() || s == "null") return null
        if (s.contains(',')) s = s.replace(".", "").replace(',', '.')
        return s.toDoubleOrNull()
    }

    private fun data(j: JSONObject, k: String): LocalDate? {
        val s = j.optString(k, "").trim().replace('/', '-').replace('.', '-')
        if (s.isEmpty() || s == "null") return null
        return try {
            if (Regex("^\\d{4}-\\d{1,2}-\\d{1,2}$").matches(s)) LocalDate.parse(
                s.split('-').let { "%04d-%02d-%02d".format(it[0].toInt(), it[1].toInt(), it[2].toInt()) }
            ) else Pensione.data(s)
        } catch (_: Exception) {
            null
        }
    }

    private fun tipoVers(t: String): String {
        val s = t.lowercase()
        return when {
            "tfr" in s || "liquidazione" in s -> "TFR"
            "datore" in s || "azienda" in s -> "Datore di Lavoro"
            "lavorator" in s || "aderente" in s || "dipendente" in s -> "Lavoratore"
            else -> "Volontario"
        }
    }

    private fun tipoRisc(t: String): String {
        val s = t.lowercase()
        return when {
            "trasfer" in s -> "Trasferimento ad altro fondo"
            "total" in s -> "Riscatto Totale"
            "parzial" in s -> "Riscatto Parziale"
            else -> "Anticipazione"
        }
    }

    private fun giaPresente(l: List<Pensione.Mov>, data: LocalDate, tipo: String?, imp: Double): Boolean =
        l.any {
            Pensione.data(it.data) == data && (tipo == null || it.tipo == tipo) && abs(it.importo - imp) < 0.005
        }

    fun leggi(j: JSONObject, d: Pensione.Dati): Esito {
        val nuove = ArrayList<Prop>()
        var gia = 0

        j.optJSONObject("valorizzazione")?.let { v ->
            val dt = data(v, "data")
            val imp = num(v, "controvalore")
            if (dt != null && imp != null && imp > 0) {
                if (d.valor.any { Pensione.data(it.data) == dt }) gia++
                else nuove.add(Prop(Pensione.VALORIZZAZIONI, dt, "", imp, ""))
            }
        }
        j.optJSONArray("versamenti")?.let { a ->
            for (i in 0 until a.length()) {
                val r = a.optJSONObject(i) ?: continue
                val dt = data(r, "data") ?: continue
                val imp = num(r, "importo") ?: continue
                if (imp <= 0) continue
                val tipo = tipoVers(r.optString("tipo"))
                val doppio = nuove.any { it.chiave == Pensione.VERSAMENTI && it.data == dt && it.tipo == tipo && abs(it.importo - imp) < 0.005 }
                if (giaPresente(d.vers, dt, tipo, imp)) gia++ else if (!doppio) nuove.add(Prop(Pensione.VERSAMENTI, dt, tipo, imp, ""))
            }
        }
        j.optJSONArray("riscatti")?.let { a ->
            for (i in 0 until a.length()) {
                val r = a.optJSONObject(i) ?: continue
                val dt = data(r, "data") ?: continue
                val imp = num(r, "importo") ?: continue
                if (imp <= 0) continue
                val tipo = tipoRisc(r.optString("tipo"))
                val doppio = nuove.any { it.chiave == Pensione.RISCATTI && it.data == dt && it.tipo == tipo && abs(it.importo - imp) < 0.005 }
                if (giaPresente(d.risc, dt, tipo, imp)) gia++
                else if (!doppio) nuove.add(Prop(Pensione.RISCATTI, dt, tipo, imp, r.optString("motivo")))
            }
        }
        return Esito(nuove.sortedBy { it.data }, gia)
    }
}
