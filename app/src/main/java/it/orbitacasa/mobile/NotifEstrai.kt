package it.orbitacasa.mobile

object NotifEstrai {
    class Estratto(val importo: Double, val tipo: String, val desc: String, val fonte: String)

    private val NUM = """(\d{1,3}(?:[.,\u00a0\u202f]\d{3})+(?:[.,]\d{1,2})?|\d+(?:[.,]\d{1,2})?)"""

    private val RE_IMPORTO = listOf(
        Regex("""(?:€|eur(?:o)?\b)[\s\u00a0\u202f]*""" + NUM, RegexOption.IGNORE_CASE),
        Regex(NUM + """[\s\u00a0\u202f]*(?:€|eur(?:o)?\b)""", RegexOption.IGNORE_CASE),
    )
    private val RE_SALDO = Regex("""saldo|disponibil|residuo|plafond|limite""", RegexOption.IGNORE_CASE)
    private val RE_SOLDI = Regex("""€|\beur(?:o)?\b""", RegexOption.IGNORE_CASE)
    private val RE_DEC = Regex("""[.,](\d{1,2})$""")
    private val RE_SEP = Regex("""[.,\u00a0\u202f]""")
    private val RE_SPAZI = Regex("""\s+""")

    private val USCITA = listOf(
        "pagament", "pagat", "addebit", "acquist", "prelievo", "hai pagato", "hai speso",
        "speso", "bonifico inviato", "bonifico disposto", "ricarica", "paypal",
        "transazione", "carta", "pos ",
        "payment", "paid", "purchase", "spent", "debited",
    )
    private val ENTRATA = listOf(
        "accredit", "hai ricevuto", "ricevuto", "bonifico ricevuto", "bonifico in entrata",
        "stipendio", "rimborso", "incasso", "versamento",
        "received", "refund", "credited",
    )

    private val RE_ESERCENTE = listOf(
        Regex("""(?:presso|a favore di|beneficiario|merchant|esercente)[:\s]+(.{2,40}?)(?:[.,;\n]|\s+(?:il|del|con|per|di|in)\s|$)""", RegexOption.IGNORE_CASE),
        Regex("""\bda\s+([A-Z][\p{L}\p{N}_&' .-]{1,30}?)(?:[.,;\n]|\s+(?:il|del|con|per)\s|$)"""),
        Regex("""\ba\s+([A-Z][\p{L}\p{N}_&' .-]{1,30}?)(?:[.,;\n]|\s+(?:il|del|con|per)\s|$)"""),
        Regex("""@\s*([\p{L}\p{N}_&' .-]{2,30})"""),
    )

    fun haSoldi(testo: String): Boolean = RE_SOLDI.containsMatchIn(testo)

    private fun numAFloat(s0: String): Double {
        val s = s0.trim()
        val m = RE_DEC.find(s)
        val intero: String
        val dec: String
        if (m != null) {
            intero = RE_SEP.replace(s.substring(0, m.range.first), "")
            dec = m.groupValues[1]
        } else {
            intero = RE_SEP.replace(s, "")
            dec = "0"
        }
        return "$intero.$dec".toDouble()
    }

    private fun importoDa(testo: String): Double? {
        val trovati = ArrayList<Pair<Int, Double>>()
        for (rx in RE_IMPORTO) {
            for (m in rx.findAll(testo)) {
                val da = (m.range.first - 25).coerceAtLeast(0)
                if (RE_SALDO.containsMatchIn(testo.substring(da, m.range.first))) continue
                val v = try {
                    numAFloat(m.groupValues[1])
                } catch (_: NumberFormatException) {
                    continue
                }
                if (v > 0) trovati.add(m.range.first to Math.round(v * 100.0) / 100.0)
            }
        }
        return trovati.minWithOrNull(compareBy({ it.first }, { it.second }))?.second
    }

    private fun tipoDa(testo: String): String {
        val t = testo.lowercase()
        val e = ENTRATA.any { it in t }
        val u = USCITA.any { it in t }
        return if (e && !u) "Entrata" else "Uscita"
    }

    private fun descrizioneDa(titolo: String, testo: String): String {
        for (rx in RE_ESERCENTE) {
            val m = rx.find(testo)
            if (m != null) return m.groupValues[1].trim { it == ' ' || it == '.' || it == '-' }.take(50)
        }
        var breve = testo.trim().replace("\n", " ")
        if (breve.length in 1..60) {
            for (rx in RE_IMPORTO) breve = rx.replace(breve, " ")
            breve = RE_SPAZI.replace(breve, " ").trim { it in " .,;:-" }
            if (breve.length >= 4) return breve.take(50)
        }
        return titolo.ifEmpty { testo }.trim().replace("\n", " ").take(50)
    }

    fun conRegole(titolo: String, testo: String): Estratto? {
        val tutto = "$titolo\n$testo"
        val imp = importoDa(tutto) ?: return null
        val t = tutto.lowercase()
        if ((USCITA + ENTRATA).none { it in t }) return null
        return Estratto(imp, tipoDa(tutto), descrizioneDa(titolo, testo), "regole")
    }
}
