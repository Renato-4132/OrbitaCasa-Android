package it.orbitacasa.mobile

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.max

object FairShare {
    val ICONE = listOf("CNT·", "PER·", "CTP·")
    private val FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    private const val NO_W = "(?![\\p{L}\\p{N}_])"

    class Part(val nome: String, val tipo: String, val soci: List<String>, val perc: Double?)

    class Pag(var pagato: Boolean, var data: String?, var sorgente: String?)

    class Debito(
        val idSpesa: String,
        val data: LocalDate,
        val categoria: String,
        val descrizione: String,
        val importo: Double,
        val quote: Map<String, Double>,
        val quota: Double,
        val creditore: String,
        val partecipanti: List<String>,
        val pagamenti: LinkedHashMap<String, Pag>,
        val salvato: Map<String, List<Any?>>,
    ) {
        var stato: String = "aperto"
        fun quotaDi(nome: String): Double = quote[nome] ?: quota
    }

    class Riga(val deb: Debito, val nome: String, val quota: Double, val pagato: Boolean, val dataPag: String)

    class Riepilogo(
        val righe: List<Riga>,
        val dovuto: Map<String, Double>,
        val versato: Map<String, Double>,
        val debito: Map<String, Double>,
        val chiDeve: List<Triple<String, String, Double>>,
    )

    fun partecipanti(): List<Part> {
        val a = Repo.fsBlocco().optJSONArray("partecipanti") ?: return emptyList()
        val out = ArrayList<Part>()
        for (i in 0 until a.length()) {
            val x = a.opt(i)
            if (x is JSONObject) {
                val nome = x.optString("nome").trim()
                if (nome.isEmpty()) continue
                val soci = ArrayList<String>()
                x.optJSONArray("soci")?.let { s -> for (j in 0 until s.length()) soci.add(s.optString(j)) }
                val perc = if (x.has("percentuale") && !x.isNull("percentuale")) x.optDouble("percentuale", Double.NaN) else Double.NaN
                out.add(Part(nome, x.optString("tipo", "persona").ifBlank { "persona" }, soci, if (perc.isNaN()) null else perc))
            } else if (x is String && x.isNotBlank()) {
                out.add(Part(x, "persona", emptyList(), null))
            }
        }
        return out
    }

    fun gestorePartecipa(): Boolean = Repo.fsBlocco().optBoolean("gestore_partecipa", true)

    fun setGestorePartecipa(v: Boolean) {
        Repo.fsBlocco().put("gestore_partecipa", v)
        Repo.fsSalva()
    }

    fun nomeGestore(): String = Repo.nomeGestore.ifBlank { "Io" }

    fun icona(tipo: String) = when (tipo) {
        "contenitore" -> "CNT·"
        "personale" -> "CTP·"
        else -> "PER·"
    }

    fun etichette(): List<String> {
        val parts = partecipanti()
        val out = ArrayList<String>()
        if (gestorePartecipa() && parts.none { it.nome == nomeGestore() }) out.add("PER· " + nomeGestore())
        for (p in parts) out.add(icona(p.tipo) + " " + p.nome)
        return out
    }

    private fun percentualeDi(nome: String): Double? = partecipanti().firstOrNull { it.nome == nome }?.perc

    fun calcolaQuote(imp: Double, partecipanti: List<String>): Map<String, Double> {
        val parti = partecipanti.distinct()
        val n = parti.size
        if (n == 0) return emptyMap()
        val perc = parti.associateWith { nm -> percentualeDi(nm)?.takeIf { it >= 0 } }
        var fissi: Map<String, Double> = perc.filterValues { it != null }.mapValues { it.value!! }
        val liberi = parti.filter { perc[it] == null }
        var somma = fissi.values.sum()
        if (somma > 100.0 + 1e-9) {
            val f = 100.0 / somma
            fissi = fissi.mapValues { it.value * f }
            somma = 100.0
        }
        var fin: Map<String, Double> = fissi
        if (liberi.isNotEmpty()) {
            val pl = max(0.0, 100.0 - somma) / liberi.size
            fin = fissi + liberi.associateWith { pl }
        } else if (somma <= 0) {
            fin = parti.associateWith { 100.0 / n }
        } else if (abs(somma - 100.0) > 0.01) {
            val f = 100.0 / somma
            fin = fissi.mapValues { it.value * f }
        }
        val segno = if (imp < 0) -1 else 1
        val centTot = Math.round(abs(imp) * 100.0).toInt()
        val quote = LinkedHashMap<String, Double>()
        var residuo = centTot
        for ((i, nm) in parti.withIndex()) {
            val c = if (i == n - 1) residuo else {
                val x = minOf(residuo, (centTot * (fin[nm] ?: 0.0) / 100.0 + 0.5 + 1e-9).toInt())
                residuo -= x
                x
            }
            quote[nm] = segno * c / 100.0
        }
        return quote
    }

    private class Ctx(
        val gestore: String,
        val persone: List<String>,
        val contenitori: Map<String, List<String>>,
        val rxPer: Regex?,
        val rxCnt: Regex?,
        val rxPulisci: Regex,
    )

    private fun rx(sigla: String, nomi: Collection<String>): Regex? {
        val n = nomi.filter { it.isNotEmpty() }.toSet().sortedByDescending { it.length }
        if (n.isEmpty()) return null
        return Regex(sigla + "·(" + n.joinToString("|") { Regex.escape(it) } + ")" + NO_W)
    }

    private fun contesto(): Ctx {
        val gestore = nomeGestore()
        val parts = partecipanti()
        val set = parts.filter { it.tipo == "persona" }.map { it.nome }.toMutableSet()
        if (gestorePartecipa()) set.add(gestore)
        val persone = set.sortedWith(compareBy({ -it.length }, { it }))
        val cont = parts.filter { it.tipo == "contenitore" }.associate { p -> p.nome to p.soci.filter { it in persone } }
        val nomiAlt = (parts.map { it.nome } + gestore).toSet().sortedByDescending { it.length }
        val rxPulisci = Regex("(?:PER|CNT|CTP)·(?:" + nomiAlt.joinToString("|") { Regex.escape(it) } + ")" + NO_W)
        return Ctx(gestore, persone, cont, rx("PER", persone), rx("CNT", cont.keys), rxPulisci)
    }

    private fun analizza(desc: String, ctx: Ctx): Pair<String, List<String>>? {
        var parti: List<String> = emptyList()
        ctx.rxCnt?.let { r ->
            for (m in r.findAll(desc)) {
                val s = ctx.contenitori[m.groupValues[1]] ?: emptyList()
                if (s.isNotEmpty()) { parti = s; break }
            }
        }
        val creditore = ctx.rxPer?.find(desc)?.groupValues?.get(1)
        if (parti.isEmpty() && creditore != null) parti = ctx.persone.ifEmpty { listOf(creditore) }
        if (parti.isEmpty()) return null
        return (creditore ?: ctx.gestore) to parti.toSet().sorted()
    }

    fun tagDa(desc: String): String? {
        val noti = (partecipanti().map { it.nome } + nomeGestore()).filter { it.isNotEmpty() }.toSet().sortedByDescending { it.length }
        val metodi = Repo.METODI.map { it.second }.filter { it.isNotEmpty() }.toSet()
        var d = desc.trim()
        for (s in metodi) if (d.startsWith(s)) { d = d.removePrefix(s).trim(); break }
        for (ic in ICONE) for (nome in noti) if (d.startsWith(ic + nome)) return "$ic $nome"
        return null
    }

    fun riscriviDescrizione(scelta: String?, desc0: String): String {
        var nomeNuovo = ""
        var icoNuova = ""
        if (!scelta.isNullOrBlank()) {
            nomeNuovo = scelta.trim()
            for (i in ICONE) if (nomeNuovo.startsWith(i)) { nomeNuovo = nomeNuovo.removePrefix(i).trim(); break }
            icoNuova = "PER·"
            partecipanti().firstOrNull { it.nome == nomeNuovo }?.let { icoNuova = icona(it.tipo) }
        }
        var desc = desc0.trim()
        var prefPag = ""
        for (s in Repo.METODI.map { it.second }.filter { it.isNotEmpty() }.toSet()) {
            if (desc.startsWith(s)) { prefPag = s; desc = desc.removePrefix(s).trim(); break }
        }
        val noti = (partecipanti().map { it.nome } + nomeGestore()).sortedByDescending { it.length }
        var tolto = false
        for (i in ICONE) {
            for (nome in noti) {
                val pref = i + nome
                if (nome.isNotEmpty() && desc.startsWith(pref)) {
                    desc = desc.removePrefix(pref).trim()
                    tolto = true
                    break
                }
            }
            if (tolto) break
        }
        if (!tolto) for (i in ICONE) if (desc.startsWith(i)) { desc = desc.removePrefix(i).trim(); break }
        return listOf(prefPag, if (nomeNuovo.isNotEmpty()) icoNuova + nomeNuovo else "", desc)
            .filter { it.isNotEmpty() }.joinToString(" ")
    }

    private fun salvatiDa(fs: JSONObject?): Map<String, List<Any?>> {
        val pag = fs?.optJSONObject("pagamenti") ?: return emptyMap()
        val out = LinkedHashMap<String, List<Any?>>()
        for (k in pag.keys()) {
            val i = pag.optJSONObject(k) ?: continue
            val sorg = i.optString("sorgente")
            if (sorg != "manuale" && sorg != "auto") continue
            out[k] = listOf(i.optBoolean("pagato"), if (i.isNull("data")) null else i.optString("data"), sorg)
        }
        return out
    }

    private fun payloadDi(d: Debito): Map<String, List<Any?>> {
        val out = LinkedHashMap<String, List<Any?>>()
        for ((nm, i) in d.pagamenti) {
            if (i.sorgente == "manuale" || i.sorgente == "auto") out[nm] = listOf(i.pagato, i.data, i.sorgente)
        }
        return out
    }

    private fun jsonPayload(m: Map<String, List<Any?>>): JSONObject? {
        if (m.isEmpty()) return null
        val pag = JSONObject()
        for ((nm, l) in m) {
            pag.put(nm, JSONObject().put("pagato", l[0] as Boolean).put("data", l[1] ?: JSONObject.NULL).put("sorgente", l[2] as String))
        }
        return JSONObject().put("pagamenti", pag)
    }

    private fun statoDi(d: Debito) {
        val tutti = d.partecipanti.isNotEmpty() && d.partecipanti.all { d.pagamenti[it]?.pagato == true }
        d.stato = if (tutti) "chiuso" else "aperto"
    }

    private fun costruisci(): List<Debito> {
        val ctx = contesto()
        val voci = Repo.voci().sortedBy { it.data }
        val debiti = ArrayList<Debito>()
        for (v in voci) {
            if (v.tipo.trim() != "Uscita") continue
            val imp = Math.round(v.importo * 100.0) / 100.0
            if (imp <= 0) continue
            val desc = v.descrizione.trim()
            val (creditore, parti) = analizza(desc, ctx) ?: continue
            val dataStr = v.data.format(FMT)
            val salvati = v.fs?.optJSONObject("pagamenti")
            val pagamenti = LinkedHashMap<String, Pag>()
            for (nm in parti) {
                val info = salvati?.optJSONObject(nm)
                val sorg = info?.optString("sorgente") ?: ""
                if (info != null && (sorg == "manuale" || sorg == "auto")) {
                    pagamenti[nm] = Pag(info.optBoolean("pagato"), if (info.isNull("data")) null else info.optString("data"), sorg)
                } else if (nm == creditore) {
                    pagamenti[nm] = Pag(true, dataStr, "creditore")
                } else {
                    pagamenti[nm] = Pag(false, null, null)
                }
            }
            val d = Debito(
                idSpesa = v.id,
                data = v.data,
                categoria = v.categoria.trim(),
                descrizione = ctx.rxPulisci.replace(desc, "").trim().split(Regex("\\s+")).joinToString(" "),
                importo = imp,
                quote = calcolaQuote(imp, parti),
                quota = Math.round(imp / parti.size * 100.0) / 100.0,
                creditore = creditore,
                partecipanti = parti,
                pagamenti = pagamenti,
                salvato = salvatiDa(v.fs),
            )
            statoDi(d)
            debiti.add(d)
        }
        val entrate = HashMap<Triple<String, String, Double>, ArrayList<LocalDate>>()
        ctx.rxPer?.let { r ->
            for (v in voci) {
                if (v.tipo.trim() != "Entrata") continue
                val m = r.find(v.descrizione) ?: continue
                val imp = Math.round(v.importo * 100.0) / 100.0
                entrate.getOrPut(Triple(m.groupValues[1], v.categoria.trim(), imp)) { ArrayList() }.add(v.data)
            }
        }
        for (d in debiti) {
            for ((nm, info) in d.pagamenti.entries.toList()) {
                val sorg = info.sorgente ?: ""
                if (sorg == "manuale" || sorg == "creditore") continue
                val lista = entrate[Triple(nm, d.categoria, Math.round(d.quotaDi(nm) * 100.0) / 100.0)]
                if (lista != null && lista.isNotEmpty()) {
                    val dEnt = lista.removeAt(0)
                    if (!info.pagato) d.pagamenti[nm] = Pag(true, dEnt.format(FMT), "auto")
                } else if (info.pagato && sorg == "auto") {
                    d.pagamenti[nm] = Pag(false, null, null)
                }
            }
            statoDi(d)
        }
        return debiti
    }

    fun sincronizza(): List<Debito> {
        val debiti = costruisci()
        val mod = ArrayList<Pair<String, JSONObject?>>()
        for (d in debiti) {
            val nuovo = payloadDi(d)
            if (nuovo != d.salvato) mod.add(d.idSpesa to jsonPayload(nuovo))
        }
        if (mod.isNotEmpty()) Repo.fsImposta(mod)
        return debiti
    }

    fun segnaPagato(idSpesa: String, nome: String): Boolean {
        val d = sincronizza().firstOrNull { it.idSpesa == idSpesa } ?: return false
        val era = d.pagamenti[nome]?.pagato == true
        d.pagamenti[nome] = if (era) Pag(false, null, "manuale")
        else Pag(true, LocalDate.now().format(FMT), "manuale")
        Repo.fsImposta(listOf(idSpesa to jsonPayload(payloadDi(d))))
        return !era
    }

    fun compensa(chiDeve: Map<Pair<String, String>, Double>): Map<Pair<String, String>, Double> {
        val netto = LinkedHashMap<Pair<String, String>, Double>()
        for ((k, imp) in chiDeve) {
            val diff = Math.round((imp - (chiDeve[k.second to k.first] ?: 0.0)) * 100.0) / 100.0
            if (diff > 0.005) netto[k] = diff
        }
        return netto
    }

    fun riepiloga(debiti: List<Debito>, anno: String, mese: Int, parte: String, cat: String, stato: String): Riepilogo {
        val dovuto = HashMap<String, Double>()
        val versato = HashMap<String, Double>()
        val debito = HashMap<String, Double>()
        val chi = LinkedHashMap<Pair<String, String>, Double>()
        val righe = ArrayList<Riga>()
        for (d in debiti) {
            if (anno != "Tutti" && d.data.year.toString() != anno) continue
            if (mese > 0 && d.data.monthValue != mese) continue
            if (cat != "Tutte" && d.categoria != cat) continue
            if (stato != "Tutti" && !d.stato.equals(stato, ignoreCase = true)) continue
            for (nome in d.partecipanti) {
                if (parte != "Tutti" && nome != parte) continue
                val quota = d.quotaDi(nome)
                val info = d.pagamenti[nome]
                val pagato = info?.pagato == true
                righe.add(Riga(d, nome, quota, pagato, info?.data ?: ""))
                dovuto[nome] = (dovuto[nome] ?: 0.0) + quota
                versato.putIfAbsent(nome, 0.0)
                debito.putIfAbsent(nome, 0.0)
                if (pagato) versato[nome] = versato.getValue(nome) + quota
                else {
                    debito[nome] = debito.getValue(nome) + quota
                    if (d.creditore.isNotEmpty() && d.creditore != nome) {
                        val k = nome to d.creditore
                        chi[k] = (chi[k] ?: 0.0) + quota
                    }
                }
            }
        }
        righe.sortBy { it.deb.data }
        val netto = compensa(chi).entries
            .sortedWith(compareBy({ it.key.second }, { it.key.first }))
            .map { Triple(it.key.first, it.key.second, it.value) }
        return Riepilogo(righe, dovuto, versato, debito, netto)
    }

    fun salvaPartecipante(vecchio: String?, nome: String, tipo: String, soci: List<String>, perc: Double?): String? {
        val n = nome.trim()
        if (n.isEmpty()) return "Inserisci un nome."
        if (n.contains('·')) return "Il nome non può contenere il simbolo ·"
        if (perc != null && (perc < 0 || perc > 100)) return "La percentuale deve essere tra 0 e 100."
        val esistenti = partecipanti().map { it.nome } + nomeGestore()
        if (n != vecchio && n in esistenti) return "'$n' è già presente."
        if (tipo == "contenitore" && soci.isEmpty()) return "Scegli almeno un socio per il contenitore."
        if (vecchio != null && n != vecchio) {
            val nomiAlt = (partecipanti().map { it.nome } + nomeGestore() + vecchio)
            Repo.fsRinomina(vecchio, n, nomiAlt)
        }
        val arr = Repo.fsBlocco().getJSONArray("partecipanti")
        val nuovo = JSONObject().put("nome", n).put("tipo", tipo)
        if (tipo == "contenitore") nuovo.put("soci", JSONArray(soci))
        if (tipo == "persona" && perc != null) nuovo.put("percentuale", perc)
        val key = vecchio ?: n
        var idx = -1
        for (i in 0 until arr.length()) {
            val o = arr.opt(i)
            val nm = if (o is JSONObject) o.optString("nome") else o?.toString()
            if (nm == n || nm == key) { idx = i; break }
        }
        if (idx >= 0) arr.put(idx, nuovo) else arr.put(nuovo)
        Repo.fsSalva()
        return null
    }

    fun eliminaPartecipante(nome: String) {
        val arr = Repo.fsBlocco().getJSONArray("partecipanti")
        val nuovo = JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.opt(i)
            val nm = if (o is JSONObject) o.optString("nome") else o?.toString()
            if (nm == nome) continue
            if (o is JSONObject && o.optString("tipo") == "contenitore") {
                val soci = o.optJSONArray("soci")
                if (soci != null) {
                    val resto = JSONArray()
                    for (j in 0 until soci.length()) if (soci.optString(j) != nome) resto.put(soci.optString(j))
                    if (resto.length() == 0) continue // contenitore rimasto senza soci
                    o.put("soci", resto)
                }
            }
            nuovo.put(o)
        }
        Repo.fsBlocco().put("partecipanti", nuovo)
        Repo.fsSalva()
    }
}
