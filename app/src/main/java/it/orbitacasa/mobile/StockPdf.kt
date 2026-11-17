package it.orbitacasa.mobile

import android.app.Activity
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object StockPdf {
    private const val W = 842
    private const val H = 595
    private const val M = 30f

    class Col(val titolo: String, val x: Float, val larg: Float, val destra: Boolean = false)

    private fun adatta(p: Paint, t: String, w: Float): String {
        if (p.measureText(t) <= w) return t
        val n = p.breakText(t, true, w - p.measureText("…"), null)
        return t.substring(0, maxOf(0, n)).trimEnd() + "…"
    }

    fun tabella(
        act: Activity, titolo: String, info: List<String>, cols: List<Col>,
        righe: List<List<String>>, totale: String?, prefisso: String,
    ): File {
        val doc = PdfDocument()
        val pn = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 9f; color = Color.BLACK }
        val pb = Paint(pn).apply { typeface = Typeface.DEFAULT_BOLD }
        val pt = Paint(pb).apply { textSize = 16f }
        val pg = Paint(pb).apply { color = Color.DKGRAY; textSize = 8.5f }
        val linea = Paint().apply { color = Color.LTGRAY; strokeWidth = 0.6f }
        val destra = W - M
        var num = 0
        var pag: PdfDocument.Page? = null
        var y = 0f

        fun testo(c: Canvas, t: String, col: Col, p: Paint) {
            val s = adatta(p, t, col.larg)
            if (col.destra) c.drawText(s, col.x + col.larg - p.measureText(s), y, p)
            else c.drawText(s, col.x, y, p)
        }

        fun nuova() {
            pag?.let { doc.finishPage(it) }
            num++
            val pg0 = doc.startPage(PdfDocument.PageInfo.Builder(W, H, num).create())
            pag = pg0
            val c = pg0.canvas
            y = 40f
            if (num == 1) {
                c.drawText(titolo, M, y, pt)
                y += 16f
                for (i in info) {
                    c.drawText(i, M, y, pn)
                    y += 12f
                }
                y += 6f
            }
            for (col in cols) testo(c, col.titolo, col, pg)
            y += 5f
            c.drawLine(M, y, destra, y, linea)
            y += 12f
        }

        nuova()
        if (righe.isEmpty()) pag!!.canvas.drawText("Nessun articolo.", M, y, pn)
        for (r in righe) {
            if (y + 14f > H - 40f) nuova()
            val c = pag!!.canvas
            cols.forEachIndexed { i, col -> testo(c, r.getOrElse(i) { "" }, col, pn) }
            y += 4f
            c.drawLine(M, y, destra, y, linea)
            y += 11f
        }
        if (totale != null) {
            if (y + 20f > H - 30f) nuova()
            val c = pag!!.canvas
            y += 4f
            val s = totale
            c.drawText(s, destra - pb.measureText(s), y, pb)
        }
        pag?.let { doc.finishPage(it) }
        val dir = File(act.cacheDir, "pdf").apply { mkdirs() }
        val f = File(dir, prefisso + "_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm")) + ".pdf")
        try {
            FileOutputStream(f).use { doc.writeTo(it) }
        } finally {
            doc.close()
        }
        return f
    }

    fun inventario(act: Activity, l: List<org.json.JSONObject>, filtrato: Boolean): File {
        val cols = listOf(
            Col("Articolo", M, 150f), Col("Categoria", 185f, 110f), Col("Qtà", 300f, 55f, true), Col("U.M.", 362f, 40f),
            Col("Min", 405f, 45f, true), Col("Max", 455f, 45f, true), Col("Prezzo", 505f, 65f, true),
            Col("Valore", 575f, 75f, true), Col("Stato", 665f, 75f), Col("Scadenza", 745f, 70f),
        )
        val ord = l.sortedWith(compareBy({ Stock.s(it, "categoria").lowercase() }, { Stock.s(it, "nome").lowercase() }))
        val righe = ord.map { a ->
            listOf(
                Stock.s(a, "nome"), Stock.s(a, "categoria"), Stock.q(Stock.d(a, "quantita")), Stock.s(a, "unita"),
                Stock.q(Stock.d(a, "qta_min")), if (Stock.d(a, "qta_max") > 0) Stock.q(Stock.d(a, "qta_max")) else "-",
                Stock.eur(Stock.d(a, "prezzo")), Stock.eur(Stock.d(a, "quantita") * Stock.d(a, "prezzo")),
                Stock.stato(a).nome, Stock.scadenza(a).testo.ifEmpty { "-" },
            )
        }
        val tot = l.sumOf { Stock.d(it, "quantita") * Stock.d(it, "prezzo") }
        return tabella(
            act, "Inventario magazzino" + if (filtrato) " (filtrato)" else "",
            listOf("Data: " + Stock.oggi(), "Articoli: " + l.size),
            cols, righe, "VALORE TOTALE  " + Stock.eur(tot), "inventario",
        )
    }

    fun riordino(act: Activity, dati: Stock.Dati): File {
        val cols = listOf(
            Col("Articolo", M, 150f), Col("Categoria", 185f, 110f), Col("Qtà", 300f, 55f, true),
            Col("Min", 362f, 45f, true), Col("Max", 412f, 45f, true), Col("Ordina", 470f, 90f),
            Col("Prezzo", 565f, 65f, true), Col("Costo", 635f, 75f, true), Col("Scadenza", 725f, 85f),
        )
        val r = Stock.righeRiordino(dati)
        var tot = 0.0
        val righe = r.map { (a, o) ->
            val costo = o * Stock.d(a, "prezzo")
            tot += costo
            listOf(
                Stock.s(a, "nome"), Stock.s(a, "categoria"), Stock.q(Stock.d(a, "quantita")), Stock.q(Stock.d(a, "qta_min")),
                if (Stock.d(a, "qta_max") > 0) Stock.q(Stock.d(a, "qta_max")) else "-",
                Stock.q(o) + " " + Stock.s(a, "unita"), Stock.eur(Stock.d(a, "prezzo")), Stock.eur(costo),
                Stock.scadenza(a).testo.ifEmpty { "-" },
            )
        }
        return tabella(
            act, "Lista riordino magazzino", listOf("Data: " + Stock.oggi(), "Articoli da riordinare: " + r.size),
            cols, righe, "TOTALE STIMATO  " + Stock.eur(tot), "riordino",
        )
    }

    fun condividi(act: Activity, f: File, titolo: String) = PensionePdf.condividi(act, f, titolo)
}
