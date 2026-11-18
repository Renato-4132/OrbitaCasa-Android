package it.orbitacasa.mobile

import android.app.Activity
import java.io.File
import java.time.format.DateTimeFormatter

object FairSharePdf {
    private val FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    // Estratto Dare/Avere: stessa struttura del desktop (spese per persona, riepilogo, chi deve a chi),
    // con i filtri attivi della pagina FairShare.
    fun estratto(
        act: Activity, r: FairShare.Riepilogo, anno: String, mese: String, parte: String, cat: String, stato: String,
    ): File {
        val cols = listOf(
            StockPdf.Col("Data", 30f, 70f), StockPdf.Col("Categoria", 105f, 100f), StockPdf.Col("Descrizione", 210f, 200f),
            StockPdf.Col("Totale", 415f, 65f, true), StockPdf.Col("Quota", 485f, 65f, true),
            StockPdf.Col("Persona", 560f, 90f), StockPdf.Col("Pagato", 655f, 40f),
            StockPdf.Col("Data pag.", 700f, 65f), StockPdf.Col("Stato", 770f, 45f),
        )
        val righe = ArrayList<List<String>>()
        for (x in r.righe) {
            val d = x.deb
            righe.add(
                listOf(
                    d.data.format(FMT), d.categoria, d.descrizione.ifBlank { d.categoria },
                    Stock.eur(d.importo), Stock.eur(x.quota), x.nome,
                    if (x.pagato) "SI" else "NO", x.dataPag, if (d.stato == "chiuso") "Chiuso" else "Aperto",
                )
            )
        }
        righe.add(emptyList())
        righe.add(listOf("RIEPILOGO PER PERSONA"))
        righe.add(listOf("", "", "Persona", "Dovuto", "Versato", "Residuo"))
        for (nome in r.dovuto.keys.sorted()) {
            val deb = r.debito.getValue(nome)
            righe.add(
                listOf(
                    "", "", nome, Stock.eur(r.dovuto.getValue(nome)), Stock.eur(r.versato.getValue(nome)),
                    if (deb < 0.01) "Saldato" else Stock.eur(deb),
                )
            )
        }
        if (r.dovuto.isNotEmpty()) {
            righe.add(
                listOf(
                    "", "", "TOTALE", Stock.eur(r.dovuto.values.sum()), Stock.eur(r.versato.values.sum()),
                    Stock.eur(r.debito.values.sum()),
                )
            )
        }
        if (r.chiDeve.isNotEmpty()) {
            righe.add(emptyList())
            righe.add(listOf("CHI DEVE A CHI (debiti compensati)"))
            for ((deb, cred, imp) in r.chiDeve) righe.add(listOf("", "", "$deb -> $cred", Stock.eur(imp)))
        }
        return StockPdf.tabella(
            act, "FairShare - Dare e Avere per spesa",
            listOf(
                "Filtri: Anno=$anno  Mese=$mese  Partecipante=$parte  Categoria=$cat  Stato=$stato",
                "Data: " + Stock.oggi() + "   Righe: " + r.righe.size,
            ),
            cols, righe, null, "fairshare_dare_avere",
        )
    }
}
