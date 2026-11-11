package it.orbitacasa.mobile

import java.time.LocalDate
import java.time.temporal.ChronoUnit

object Checkout {
    private const val SOGLIA_CHECKOUT = 4
    private const val MESI_INDIETRO = 12
    private const val ANNI_STORICO = 4
    private const val MAX_VOLTE_ANNO = 2

    fun titolo(s: String): String {
        val sb = StringBuilder()
        var prev = false
        for (c in s) {
            if (c.isLetter()) {
                sb.append(if (prev) c.lowercaseChar() else c.uppercaseChar())
                prev = true
            } else {
                sb.append(c)
                prev = false
            }
        }
        return sb.toString()
    }

    private fun diffMesi(ref: LocalDate, d: LocalDate) = (ref.year - d.year) * 12 + (ref.monthValue - d.monthValue)

    private fun baseCategorie(): Set<String> = Repo.categorie().filter { it.isNotBlank() }.map { titolo(it) }.toSet()

    fun nomeReale(catTitolo: String): String =
        Repo.categorie().firstOrNull { titolo(it) == catTitolo } ?: catTitolo

    fun mancanti(voci: List<Voce>, ref: LocalDate = LocalDate.now()): List<String> {
        val base = baseCategorie()
        val presenti = HashSet<String>()
        val conteggio = HashMap<String, Int>()
        val viste = HashSet<Pair<LocalDate, String>>()
        for (v in voci) {
            if (v.categoria.isBlank()) continue
            val c = titolo(v.categoria.trim())
            val diff = diffMesi(ref, v.data)
            if (diff == 0) presenti.add(c)
            else if (diff in 1..MESI_INDIETRO && c in base && viste.add(v.data to c)) {
                conteggio[c] = (conteggio[c] ?: 0) + 1
            }
        }
        return base.filter { (conteggio[it] ?: 0) >= SOGLIA_CHECKOUT && it !in presenti }.sorted()
    }

    class Dett(
        val cat: String, val ultima: Voce?, val n: Int, val totale: Double,
        val min: Double, val max: Double, val ogniGiorni: Long?,
    )

    fun dettaglio(cat: String, voci: List<Voce>, ref: LocalDate = LocalDate.now()): Dett {
        val oggi = LocalDate.now()
        var ultima: Voce? = null
        val importi = ArrayList<Double>()
        val date = HashSet<LocalDate>()
        for (v in voci) {
            if (titolo(v.categoria.trim()) != cat) continue
            val diff = diffMesi(ref, v.data)
            if (diff < 1 || diff > MESI_INDIETRO) continue
            if (v.data.isAfter(oggi)) continue
            importi.add(v.importo)
            date.add(v.data)
            if (ultima == null || v.data.isAfter(ultima.data)) ultima = v
        }
        val ord = date.sorted()
        val gap = if (ord.size > 1) Math.round(ChronoUnit.DAYS.between(ord.first(), ord.last()).toDouble() / (ord.size - 1)) else null
        return Dett(cat, ultima, importi.size, importi.sum(), importi.minOrNull() ?: 0.0, importi.maxOrNull() ?: 0.0, gap)
    }

    class RigaMese(
        val cat: String, val totale: Double, val media: Double, val cadenza: String,
        val mesi: BooleanArray, val n: Int, val uscite: Double, val entrate: Double,
        val mancano: List<Int>, val meseMax: Pair<Int, Double>?, val meseMin: Pair<Int, Double>?,
    )

    fun mensili(voci: List<Voce>, anno: Int): List<RigaMese> {
        val oggi = LocalDate.now()
        val totali = HashMap<String, Double>()
        val conteggio = HashMap<String, Int>()
        val presenti = HashMap<String, MutableSet<Int>>()
        val dati = HashMap<String, HashMap<Int, DoubleArray>>()
        for (v in voci) {
            if (v.data.year != anno || v.categoria.isBlank()) continue
            val cat = titolo(v.categoria.trim())
            val entrata = v.tipo.trim().equals("Entrata", true)
            totali[cat] = (totali[cat] ?: 0.0) + (if (entrata) v.importo else -v.importo)
            conteggio[cat] = (conteggio[cat] ?: 0) + 1
            presenti.getOrPut(cat) { HashSet() }.add(v.data.monthValue)
            val a = dati.getOrPut(cat) { HashMap() }.getOrPut(v.data.monthValue) { DoubleArray(2) }
            if (entrata) a[0] += v.importo else a[1] += v.importo
        }
        val soglia = if (anno == oggi.year) 1 else 2
        val out = ArrayList<RigaMese>()
        for (cat in presenti.keys.sorted()) {
            val mp = presenti.getValue(cat)
            if (mp.size < soglia) continue
            val tot = totali[cat] ?: 0.0
            val n = conteggio[cat] ?: 1
            val ord = mp.sorted()
            var avg = 0.0
            if (ord.size > 1) avg = (1 until ord.size).map { ord[it] - ord[it - 1] }.average()
            val cadenza = when {
                mp.size == 12 -> "Mensile"
                avg in 0.8..1.2 -> "Mensile Reg."
                avg in 1.5..2.5 -> "Bimestrale"
                avg > 2.5 && avg <= 3.5 -> "Trimestrale"
                else -> "Irregolare"
            }
            val dm = dati[cat] ?: HashMap()
            val vol = dm.mapValues { it.value[0] + it.value[1] }
            val mx = if (vol.size > 1) vol.maxByOrNull { it.value } else null
            val mn = if (vol.size > 1) vol.minByOrNull { it.value } else null
            out.add(
                RigaMese(
                    cat, tot, tot / n, cadenza, BooleanArray(12) { (it + 1) in mp }, n,
                    dm.values.sumOf { it[1] }, dm.values.sumOf { it[0] },
                    (1..12).filter { it !in mp && !(anno == oggi.year && it > oggi.monthValue) },
                    mx?.let { it.key to it.value }, mn?.let { it.key to it.value },
                )
            )
        }
        return out
    }

    class RigaAnn(
        val cat: String, val mese: Int, val media: Double, val nAnni: Int,
        val stato: Int, // 0 registrata, 1 mese futuro, 2 mancante
        val anni: List<Int>, val occ: Int, val min: Double, val max: Double,
    )

    fun annuali(voci: List<Voce>): List<RigaAnn> {
        val oggi = LocalDate.now()
        val storico = HashMap<Pair<String, Int>, MutableSet<Int>>()
        val importi = HashMap<Pair<String, Int>, MutableList<Double>>()
        val presentiCorr = HashMap<String, MutableSet<Int>>()
        val occAnno = HashMap<String, HashMap<Int, Int>>()
        for (v in voci) {
            if (v.categoria.isBlank()) continue
            val cat = titolo(v.categoria.trim())
            val fa = oggi.year - v.data.year
            if (fa == 0) {
                presentiCorr.getOrPut(cat) { HashSet() }.add(v.data.monthValue)
            } else if (fa in 1..ANNI_STORICO) {
                val netto = if (v.tipo.trim().equals("Entrata", true)) v.importo else -v.importo
                val k = cat to v.data.monthValue
                storico.getOrPut(k) { HashSet() }.add(v.data.year)
                importi.getOrPut(k) { ArrayList() }.add(netto)
                val m = occAnno.getOrPut(cat) { HashMap() }
                m[v.data.year] = (m[v.data.year] ?: 0) + 1
            }
        }
        val anniPerCat = HashMap<String, MutableSet<Int>>()
        for ((k, a) in storico) anniPerCat.getOrPut(k.first) { HashSet() }.addAll(a)
        val out = ArrayList<RigaAnn>()
        for ((k, anni) in storico) {
            if (anni.size < 2) continue
            if ((occAnno[k.first]?.values?.maxOrNull() ?: 1) > MAX_VOLTE_ANNO) continue
            val imp = importi[k] ?: emptyList()
            val media = if (imp.isEmpty()) 0.0 else imp.sum() / imp.size
            val presente = k.second in (presentiCorr[k.first] ?: emptySet<Int>())
            val stato = if (presente) 0 else if (k.second > oggi.monthValue) 1 else 2
            val ass = imp.map { Math.abs(it) }
            out.add(
                RigaAnn(
                    k.first, k.second, media, anniPerCat[k.first]?.size ?: 0, stato, anni.sorted(),
                    ass.size, ass.minOrNull() ?: 0.0, ass.maxOrNull() ?: 0.0,
                )
            )
        }
        return out.sortedWith(compareBy({ it.mese }, { it.cat }))
    }

    fun daAvvisare(voci: List<Voce>, oggi: LocalDate = LocalDate.now()): List<String>? {
        if (!Repo.checkMese) return null
        val gg = ChronoUnit.DAYS.between(oggi, oggi.withDayOfMonth(oggi.lengthOfMonth()))
        if (gg > Repo.sogliaGiorniRic || gg < 0) return null
        Repo.fmDismiss?.let { if (it.year == oggi.year && it.monthValue == oggi.monthValue) return null }
        val base = baseCategorie()
        if (base.isEmpty()) return null
        val presenti = HashSet<String>()
        val mesiCat = HashMap<String, MutableSet<Pair<Int, Int>>>()
        for (v in voci) {
            if (v.categoria.isBlank()) continue
            val c = titolo(v.categoria.trim())
            val diff = diffMesi(oggi, v.data)
            if (diff == 0) presenti.add(c)
            else if (diff in 1..MESI_INDIETRO && c in base) mesiCat.getOrPut(c) { HashSet() }.add(v.data.year to v.data.monthValue)
        }
        val man = base.filter { (mesiCat[it]?.size ?: 0) >= 4 && it !in presenti }.sorted()
        return man.ifEmpty { null }
    }
}
