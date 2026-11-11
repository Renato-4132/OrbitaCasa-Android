package it.orbitacasa.mobile

import java.time.LocalDate

object Previsione {
    class Riga(
        val categoria: String,
        val speso: Double,
        val finoAOggi: Double,
        val previsione: Double,
        val budget: Double,
    )

    class Esito(
        val righe: List<Riga>,
        val giorno: Int,
        val giorniMese: Int,
        val totSpeso: Double,
        val totPrevisione: Double,
        val totBudget: Double,
        val spesoConBudget: Double,
        val previstoConBudget: Double,
    )

    fun calcola(voci: List<Voce>, oggi: LocalDate, budget: (String) -> Double): Esito {
        val gg = oggi.lengthOfMonth()
        val nomi = LinkedHashMap<String, String>()
        val speso = HashMap<String, Double>()
        val fino = HashMap<String, Double>()
        for (v in voci) {
            if (v.tipo != "Uscita") continue
            if (v.data.year != oggi.year || v.data.monthValue != oggi.monthValue) continue
            val k = v.categoria.trim().lowercase()
            nomi.getOrPut(k) { v.categoria.trim() }
            speso[k] = (speso[k] ?: 0.0) + v.importo
            if (!v.data.isAfter(oggi)) fino[k] = (fino[k] ?: 0.0) + v.importo
        }
        val righe = ArrayList<Riga>()
        for ((k, nome) in nomi) {
            val s = speso[k] ?: 0.0
            val f = fino[k] ?: 0.0
            if (s <= 0.0 && f <= 0.0) continue
            val prev = maxOf(s, f / oggi.dayOfMonth * gg)
            righe.add(Riga(nome, s, f, prev, budget(nome)))
        }
        righe.sortByDescending { it.previsione }
        val conBud = righe.filter { it.budget > 0 }
        return Esito(
            righe, oggi.dayOfMonth, gg,
            righe.sumOf { it.speso }, righe.sumOf { it.previsione },
            conBud.sumOf { it.budget }, conBud.sumOf { it.speso }, conBud.sumOf { it.previsione },
        )
    }

    class Mese(val mese: Int, val num: Int, val uscite: Double, val stimato: Double, val futuro: Boolean)

    class Anno(
        val mesi: List<Mese>,
        val totale: Double,
        val media: Double?,
        val proiezione: Double?,
    )

    fun annuale(voci: List<Voce>, oggi: LocalDate): Anno {
        val u = DoubleArray(13)
        val n = IntArray(13)
        for (v in voci) {
            if (v.tipo != "Uscita" || v.data.year != oggi.year) continue
            u[v.data.monthValue] += v.importo
            n[v.data.monthValue]++
        }
        val chiusi = (1 until oggi.monthValue).map { u[it] }
        val media = if (chiusi.isEmpty()) null else chiusi.sum() / chiusi.size
        val mesi = (1..12).map { m ->
            val futuro = m > oggi.monthValue
            val stim = if (media != null && m >= oggi.monthValue) maxOf(u[m], media) else u[m]
            Mese(m, n[m], u[m], stim, futuro)
        }
        val proiezione = media?.let { chiusi.sum() + (oggi.monthValue..12).sumOf { m -> maxOf(u[m], it) } }
        return Anno(mesi, u.sum(), media, proiezione)
    }
}
