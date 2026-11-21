package it.orbitacasa.mobile

import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs

object RicAuto {
    class Sugg(
        val tipo: String,
        val cat: String,
        val desc: String,
        val imp: Double,
        val giorno: Int,
        val prossima: LocalDate,
        val mesi: Int,
        val conto: String,
        val metodo: String,
        val tag: List<String>,
    )

    private fun pulita(d: String): String =
        d.removePrefix("RIC·").trim().replace(Regex("\\s*ID:[0-9a-fA-F]{8}$"), "").trim()

    private fun chiave(d: String): String =
        pulita(d).lowercase().replace(Regex("[^\\p{L} ]"), " ").replace(Regex("\\s+"), " ").trim()

    private fun mediana(v: List<Double>): Double {
        val s = v.sorted()
        return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2.0
    }

    fun trova(voci: List<Voce>, ric: List<Ric>, oggi: LocalDate): List<Sugg> {
        val da = oggi.minusMonths(18)
        val gruppi = voci
            .filter {
                !it.ricorrente && (it.tipo == "Uscita" || it.tipo == "Entrata") && it.importo > 0 &&
                    !it.data.isAfter(oggi) && !it.data.isBefore(da)
            }
            .groupBy { Triple(it.tipo, it.categoria, chiave(it.descrizione)) }
        val ymOggi = YearMonth.from(oggi)
        val out = ArrayList<Sugg>()
        for ((k, lista) in gruppi) {
            if (lista.size < 3) continue
            val m = mediana(lista.map { it.importo })
            val tol = maxOf(1.0, 0.10 * m)
            val cl = lista.filter { abs(it.importo - m) <= tol }.sortedBy { it.data }
            if (cl.size < 3) continue
            val mesi = cl.map { YearMonth.from(it.data) }
            if (mesi.distinct().size != mesi.size) continue
            if (mesi.last().isBefore(ymOggi.minusMonths(1))) continue
            var corsa = 1
            for (i in mesi.size - 1 downTo 1) {
                if (mesi[i - 1] == mesi[i].minusMonths(1)) corsa++ else break
            }
            if (corsa < 3) continue
            val run = cl.takeLast(corsa)
            val giorno = mediana(run.map { it.data.dayOfMonth.toDouble() }).toInt()
            if (run.any { abs(it.data.dayOfMonth - giorno) > 5 }) continue
            val imp = Math.round(mediana(run.map { it.importo }) * 100.0) / 100.0
            val bloccata = ric.any { r ->
                r.tipoVoce == k.first && r.cat == k.second &&
                    (r.fine == null || !r.fine.isBefore(oggi)) &&
                    ((k.third.isNotEmpty() && chiave(r.desc) == k.third) ||
                        ((k.third.isEmpty() || chiave(r.desc).isEmpty()) && abs(r.imp - imp) <= maxOf(1.0, 0.10 * imp)))
            }
            if (bloccata) continue
            var ym = YearMonth.from(run.last().data).plusMonths(1)
            var prossima = ym.atDay(minOf(giorno, ym.lengthOfMonth()))
            while (!prossima.isAfter(oggi)) {
                ym = ym.plusMonths(1)
                prossima = ym.atDay(minOf(giorno, ym.lengthOfMonth()))
            }
            val ultimo = run.last()
            out.add(
                Sugg(
                    k.first, k.second, pulita(ultimo.descrizione), imp, giorno, prossima, corsa,
                    ultimo.conto, ultimo.metodo, ultimo.hashtag,
                )
            )
        }
        return out.sortedByDescending { it.imp }.take(30)
    }
}
