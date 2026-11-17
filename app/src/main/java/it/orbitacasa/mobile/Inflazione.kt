package it.orbitacasa.mobile

import kotlin.math.pow
import kotlin.math.roundToLong

object Inflazione {
    val NIC: Map<Int, Double> = linkedMapOf(
        2000 to 2.6, 2001 to 2.3, 2002 to 2.6, 2003 to 2.8, 2004 to 2.3,
        2005 to 2.2, 2006 to 2.2, 2007 to 2.0, 2008 to 3.5, 2009 to 0.8,
        2010 to 1.6, 2011 to 2.9, 2012 to 3.3, 2013 to 1.2, 2014 to 0.2,
        2015 to 0.1, 2016 to -0.1, 2017 to 1.3, 2018 to 1.2, 2019 to 0.6,
        2020 to -0.1, 2021 to 1.9, 2022 to 8.1, 2023 to 5.7, 2024 to 1.0,
        2025 to 1.5,
    )

    val ANNI: List<Int> = NIC.keys.sorted()
    val ULTIMO: Int = ANNI.last()

    class Esito(
        val etichette: List<String>,
        val valori: List<Double>,
        val rivalutato: Double,
        val perdita: Double,
        val totale: Double,
        val anni: Int,
        val media: Double,
    ) {
        val elevata: Boolean get() = totale > 20
    }

    fun importo(t: String): Double? {
        var s = t.replace("€", "").replace(" ", "").trim()
        if (s.isEmpty()) return null
        if (s.contains(',')) s = s.replace(".", "").replace(',', '.')
        return s.toDoubleOrNull()
    }

    fun annoDefault(primoAnnoDati: Int?): Int =
        if (primoAnnoDati != null && primoAnnoDati in NIC && primoAnnoDati < ULTIMO) primoAnnoDati else ULTIMO - 5

    fun mediaMensile(uscite: List<Triple<Int, Int, Double>>): Int {
        if (uscite.isEmpty()) return 1000
        val m = HashMap<Pair<Int, Int>, Double>()
        for ((a, me, v) in uscite) m[a to me] = (m[a to me] ?: 0.0) + v
        return (m.values.sum() / m.size).roundToLong().toInt()
    }

    fun calcola(imp: Double, da: Int, a: Int): Esito? {
        if (imp <= 0.0 || da >= a) return null
        var coeff = 1.0
        val lab = arrayListOf(da.toString())
        val val0 = arrayListOf(imp)
        for (y in da + 1..a) {
            coeff *= 1 + (NIC[y] ?: 2.0) / 100
            lab.add(y.toString())
            val0.add(Math.round(imp * coeff * 100) / 100.0)
        }
        val n = a - da
        return Esito(lab, val0, imp * coeff, imp * coeff - imp, (coeff - 1) * 100, n, (coeff.pow(1.0 / n) - 1) * 100)
    }
}
