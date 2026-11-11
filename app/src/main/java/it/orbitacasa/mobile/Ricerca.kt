package it.orbitacasa.mobile

import java.time.LocalDate
import java.time.format.DateTimeFormatter

object Ricerca {
    val MESI = listOf(
        "Gennaio", "Febbraio", "Marzo", "Aprile", "Maggio", "Giugno",
        "Luglio", "Agosto", "Settembre", "Ottobre", "Novembre", "Dicembre",
    )
    private val FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy")

    class Filtri {
        var tipo = ""
        var categoria = ""
        var anno = ""
        var mese = 0
        var partecipante = ""
        var conto = ""
        var metodo = ""
        var tag = ""
        var testo = ""
        var impDa = ""
        var impA = ""
        var dal = ""
        var al = ""

        fun azzera() {
            tipo = ""; categoria = ""; anno = ""; mese = 0; partecipante = ""; conto = ""; metodo = ""
            tag = ""; testo = ""; impDa = ""; impA = ""; dal = ""; al = ""
        }
    }

    fun dataLibera(s: String): LocalDate? {
        val t = s.trim().replace('/', '-').replace('.', '-')
        if (t.isEmpty()) return null
        return try { LocalDate.parse(t, FMT) } catch (_: Exception) { null }
    }

    fun numLibero(s: String): Double? {
        var t = s.trim().replace("€", "").replace(" ", "")
        if (t.isEmpty()) return null
        if (t.contains(',')) t = t.replace(".", "").replace(',', '.')
        return t.toDoubleOrNull()
    }

    private fun tagList(s: String): List<String> =
        s.trim().split(Regex("[\\s,;]+")).map { it.trimStart('#').lowercase() }.filter { it.isNotEmpty() }

    fun attivo(f: Filtri): Boolean =
        f.tipo.isNotEmpty() || f.categoria.isNotEmpty() || f.anno.isNotEmpty() || f.mese != 0 ||
            f.partecipante.isNotEmpty() || f.conto.isNotEmpty() || f.metodo.isNotEmpty() ||
            tagList(f.tag).isNotEmpty() || f.testo.trim().isNotEmpty() ||
            numLibero(f.impDa) != null || numLibero(f.impA) != null ||
            dataLibera(f.dal) != null || dataLibera(f.al) != null

    fun descrizioneFiltri(f: Filtri): List<String> {
        val out = ArrayList<String>()
        if (f.tipo.isNotEmpty()) out.add(f.tipo)
        if (f.categoria.isNotEmpty()) out.add("Categoria " + f.categoria)
        if (f.anno.isNotEmpty()) out.add("Anno " + f.anno)
        if (f.mese != 0) out.add(MESI[f.mese - 1])
        if (f.partecipante.isNotEmpty()) out.add(f.partecipante)
        if (f.conto.isNotEmpty()) out.add("Conto " + f.conto)
        if (f.metodo.isNotEmpty()) out.add("Metodo " + f.metodo)
        val tg = tagList(f.tag)
        if (tg.isNotEmpty()) out.add(tg.joinToString(" ") { "#$it" })
        if (f.testo.trim().isNotEmpty()) out.add("«" + f.testo.trim().lowercase() + "»")
        val da = numLibero(f.impDa)
        val a = numLibero(f.impA)
        if (da != null || a != null) {
            out.add("Importo " + (da?.let { String.format(java.util.Locale.ITALY, "%.2f", it) } ?: "…") + " → " +
                (a?.let { String.format(java.util.Locale.ITALY, "%.2f", it) } ?: "…"))
        }
        val dd = dataLibera(f.dal)
        val aa = dataLibera(f.al)
        if (dd != null || aa != null) out.add((dd?.format(FMT) ?: "…") + " → " + (aa?.format(FMT) ?: "…"))
        return out
    }

    private fun proprietario(descrLow: String, candidati: List<String>): String {
        for (nome in candidati) {
            for (ico in FairShare.ICONE) {
                if (descrLow.contains((ico + nome).lowercase()) || descrLow.contains((ico + " " + nome).lowercase())) return nome
            }
        }
        return ""
    }

    fun cerca(voci: List<Voce>, parola0: String, futuri: Boolean, f: Filtri): List<Voce> {
        val parola = parola0.trim().lowercase()
        val oggi = LocalDate.now()
        val dal = dataLibera(f.dal)
        val al = dataLibera(f.al)
        val tag = tagList(f.tag)
        val testo = f.testo.trim().lowercase()
        val impDa = numLibero(f.impDa)
        val impA = numLibero(f.impA)
        val nomi = FairShare.partecipanti().map { it.nome }.toMutableList()
        if (FairShare.gestorePartecipa() && FairShare.nomeGestore() !in nomi) nomi.add(FairShare.nomeGestore())
        var nomeFiltro = f.partecipante
        for (pre in listOf("CTP· ", "CNT· ", "PER· ")) if (nomeFiltro.startsWith(pre)) { nomeFiltro = nomeFiltro.removePrefix(pre); break }
        val usaOwner = f.partecipante.isNotEmpty()

        val out = ArrayList<Voce>()
        for (v in voci) {
            val d = v.data
            if (!futuri && d.isAfter(oggi)) continue
            if (dal != null && d.isBefore(dal)) continue
            if (al != null && d.isAfter(al)) continue
            val descr = v.descrizione.lowercase()
            val cat = v.categoria.lowercase()
            val tipo = v.tipo.lowercase()
            val tagTxt = v.hashtag.joinToString(" ").lowercase()
            if (parola.isNotEmpty()) {
                val campi = listOf(cat, descr, tipo, v.importo.toString(), v.conto.lowercase(), v.metodo.lowercase(), tagTxt)
                if (campi.none { it.contains(parola) }) continue
            }
            if (testo.isNotEmpty() && !descr.contains(testo)) continue
            if (f.categoria.isNotEmpty() && cat != f.categoria.lowercase()) continue
            if (f.tipo.isNotEmpty() && tipo != f.tipo.lowercase()) continue
            if (f.anno.isNotEmpty() && d.year.toString() != f.anno) continue
            if (f.mese != 0 && d.monthValue != f.mese) continue
            if (usaOwner && nomeFiltro.lowercase() != proprietario(descr, nomi).lowercase()) continue
            if (f.conto.isNotEmpty() && v.conto != f.conto) continue
            if (f.metodo.isNotEmpty() && v.metodo != f.metodo) continue
            if (tag.isNotEmpty() && !tag.all { tagTxt.contains(it) }) continue
            if (impDa != null && v.importo < impDa) continue
            if (impA != null && v.importo > impA) continue
            out.add(v)
        }
        return out.sortedByDescending { it.data }
    }
}
