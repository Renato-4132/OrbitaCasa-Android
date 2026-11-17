package it.orbitacasa.mobile

import android.app.Activity
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object MarketPdf {
    private val M = 30f

    fun lista(act: Activity, l: List<Market.Riga>, oggi: LocalDate): File {
        val cols = listOf(
            StockPdf.Col("Supermercato", M, 110f), StockPdf.Col("Articolo", 145f, 250f), StockPdf.Col("Formato", 400f, 100f),
            StockPdf.Col("Qtà", 505f, 40f, true), StockPdf.Col("Prezzo", 550f, 80f, true), StockPdf.Col("Totale", 635f, 90f, true),
        )
        val righe = ArrayList<List<String>>()
        for ((sm, rr) in l.groupBy { it.supermercato }.toSortedMap()) {
            for (r in rr.sortedBy { it.nome.lowercase() }) {
                righe.add(listOf(sm, r.nome, r.qtaCatalogo, r.qta.toString(), Market.eur(r.prezzoUn), Market.eur(r.totale)))
            }
            righe.add(listOf(sm, "Totale parziale", "", "", "", Market.eur(rr.sumOf { it.totale })))
        }
        return StockPdf.tabella(
            act, "Lista spesa ottimizzata",
            listOf("Data: " + oggi.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")), "Articoli: " + l.size),
            cols, righe, "TOTALE GENERALE STIMATO  " + Market.eur(Market.totaleLista(l)), "lista_spesa",
        )
    }

    fun catalogo(act: Activity, sm: String, articoli: List<org.json.JSONObject>): File {
        val cols = listOf(
            StockPdf.Col("Articolo", M, 230f), StockPdf.Col("Quantità", 265f, 90f), StockPdf.Col("Prezzo", 360f, 70f, true),
            StockPdf.Col("Categoria", 445f, 120f), StockPdf.Col("Promo", 575f, 70f, true),
            StockPdf.Col("Dal", 660f, 75f), StockPdf.Col("Al", 740f, 75f),
        )
        val righe = ArrayList<List<String>>()
        for (a in articoli.sortedBy { Market.s(it, "nome").lowercase() }) {
            val p = Market.prezzo(a, "prezzo") ?: continue
            val pp = Market.prezzo(a, "prezzo_promo")
            val promo = Market.promoOn(a) && pp != null
            righe.add(
                listOf(
                    Market.s(a, "nome"), Market.s(a, "quantita"), Market.eur(p), Market.s(a, "categoria"),
                    if (promo) Market.eur(pp!!) else "",
                    if (promo) Market.s(a, "data_inizio_promo") else "", if (promo) Market.s(a, "data_scadenza") else "",
                )
            )
        }
        val ora = LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
        return StockPdf.tabella(
            act, "Catalogo supermercato: $sm", listOf("Data: $ora", "Articoli: " + righe.size),
            cols, righe, null, "catalogo",
        )
    }
}
