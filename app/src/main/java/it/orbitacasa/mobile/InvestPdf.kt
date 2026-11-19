package it.orbitacasa.mobile

import android.app.Activity
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object InvestPdf {
    private const val W = 595
    private const val H = 842
    private const val M = 30f
    private val DF = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    private fun n(v: Double, dec: Int = 2): String = String.format(Locale.ITALY, "%,." + dec + "f", v)
    private fun s(v: Double, dec: Int = 2): String = String.format(Locale.ITALY, "%+,." + dec + "f", v)

    private fun taglia(t: String, p: Paint, w: Float): String {
        if (p.measureText(t) <= w) return t
        val k = p.breakText(t, true, w - p.measureText("…"), null)
        return t.take(maxOf(k, 0)).trimEnd() + "…"
    }

    fun crea(act: Activity, d: Invest.Dati): File {
        val t = Invest.totali(d)
        val doc = PdfDocument()
        val pn = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 8.5f; color = Color.BLACK }
        val pb = Paint(pn).apply { typeface = Typeface.DEFAULT_BOLD }
        val pt = Paint(pb).apply { textSize = 16f }
        val ps = Paint(pb).apply { textSize = 10.5f }
        val pg = Paint(pn).apply { color = Color.DKGRAY; typeface = Typeface.DEFAULT_BOLD; textSize = 8f }
        val linea = Paint().apply { color = Color.LTGRAY; strokeWidth = 0.6f }
        val destra = W - M

        var num = 0
        var pag: PdfDocument.Page? = null
        var y = 0f

        fun nuova() {
            pag?.let { doc.finishPage(it) }
            num++
            val p = doc.startPage(PdfDocument.PageInfo.Builder(W, H, num).create())
            pag = p
            y = 42f
            if (num == 1) {
                val c = p.canvas
                c.drawText("Portafoglio investimenti", M, y, pt)
                y += 16f
                c.drawText("Data: " + LocalDate.now().format(DF), M, y, pn)
                y += 18f
            }
        }

        fun spazio(h: Float) {
            if (y + h > H - 40f) nuova()
        }

        fun testata(titolo: String, colonne: List<Triple<String, Float, Boolean>>) {
            spazio(40f)
            pag!!.canvas.drawText(titolo, M, y, ps)
            y += 14f
            val c: Canvas = pag!!.canvas
            for ((nome, x, dx) in colonne) {
                c.drawText(nome, x, y, Paint(pg).apply { textAlign = if (dx) Paint.Align.RIGHT else Paint.Align.LEFT })
            }
            y += 5f
            c.drawLine(M, y, destra, y, linea)
            y += 12f
        }

        fun riga(valori: List<String>, colonne: List<Triple<String, Float, Boolean>>, grassetto: Boolean = false) {
            if (y + 14f > H - 40f) {
                nuova()
            }
            val c = pag!!.canvas
            val base = if (grassetto) pb else pn
            valori.forEachIndexed { i, v ->
                val (_, x, dx) = colonne[i]
                val pp = Paint(base).apply { textAlign = if (dx) Paint.Align.RIGHT else Paint.Align.LEFT }
                c.drawText(v, x, y, pp)
            }
            y += 4f
            c.drawLine(M, y, destra, y, linea)
            y += 11f
        }

        nuova()

        val c0 = pag!!.canvas
        val kpi = listOf(
            "Investito" to "€ " + n(t.investito),
            "Valore attuale" to "€ " + n(t.attuale),
            "P&L" to "€ " + s(t.pl) + " (" + s(t.plPct) + "%)",
            "Dividendi incassati" to "€ " + n(t.dividendi),
            "Rendimento reale" to "€ " + s(t.rendReale),
        )
        for ((k, v) in kpi) {
            c0.drawText(k, M, y, pn)
            c0.drawText(v, 200f, y, pb)
            y += 12f
        }
        y += 8f

        val colP = listOf(
            Triple("Ticker", M, false), Triple("Qtà", 165f, true), Triple("PMC €", 225f, true),
            Triple("Prezzo €", 285f, true), Triple("Valore €", 350f, true), Triple("Investito €", 420f, true),
            Triple("P&L €", 490f, true), Triple("P&L %", destra, true),
        )
        testata("Portafoglio", colP)
        if (t.calcoli.isEmpty()) {
            pag!!.canvas.drawText("Nessun titolo presente.", M, y, pn)
            y += 14f
        }
        for (c in t.calcoli) {
            riga(
                listOf(
                    c.ticker, n(c.quantita, 4), n(c.pmc, 4), n(c.prezzo, 4), n(c.valore), n(c.investito),
                    s(c.plEur), s(c.plPct) + "%"
                ),
                colP
            )
        }
        if (t.calcoli.isNotEmpty()) {
            riga(listOf("TOTALE", "", "", "", n(t.attuale), n(t.investito), s(t.pl), s(t.plPct) + "%"), colP, true)
        }
        y += 10f

        val colA = listOf(
            Triple("Ticker", M, false), Triple("Attuale %", 200f, true), Triple("Target %", 270f, true),
            Triple("Scostamento", 350f, true), Triple("Stato", 360f, false),
        )
        testata("Allocazione e ribilanciamento", colA)
        for (c in t.calcoli) {
            val att = Invest.allocazione(c, t.attuale)
            val st = Invest.stato(att, c.target, d.soglia)
            val stato = when (st) {
                1 -> "OK"
                2 -> "Attenzione"
                3 -> "Ribilancia"
                else -> "-"
            }
            riga(
                listOf(
                    c.ticker, n(att) + "%", if (c.target != 0.0) n(c.target, 1) + "%" else "-",
                    if (c.target != 0.0) s(att - c.target) + "%" else "-", stato
                ),
                colA
            )
        }
        y += 10f

        val colM = listOf(
            Triple("Data", M, false), Triple("Ticker", 92f, false), Triple("Tipo", 150f, false),
            Triple("Qtà", 250f, true), Triple("Prezzo €", 315f, true), Triple("Comm. €", 365f, true),
            Triple("Totale €", 430f, true), Triple("Note", 440f, false),
        )
        testata("Movimenti", colM)
        val mov = Invest.ordinaMov(d.titoli.flatMap { tt -> tt.mov.map { tt.ticker to it } })
        if (mov.isEmpty()) {
            pag!!.canvas.drawText("Nessun movimento presente.", M, y, pn)
            y += 14f
        }
        for ((tk, m) in mov) {
            riga(
                listOf(
                    m.data, tk, m.tipo.replaceFirstChar { it.uppercase() }, n(m.quantita, 4), n(m.prezzo, 4),
                    n(m.commissioni), n(m.totale), taglia(m.note, pn, destra - 440f)
                ),
                colM
            )
        }
        y += 10f

        val colD = listOf(
            Triple("Data", M, false), Triple("Ticker", 100f, false), Triple("Lordo €", 280f, true),
            Triple("Ritenuta €", 360f, true), Triple("Netto €", 440f, true),
        )
        testata("Dividendi", colD)
        val div = Invest.ordinaDiv(d.titoli.flatMap { tt -> tt.div.map { tt.ticker to it } })
        if (div.isEmpty()) {
            pag!!.canvas.drawText("Nessun dividendo presente.", M, y, pn)
            y += 14f
        }
        for ((tk, x) in div) {
            riga(listOf(x.data, tk, n(x.lordo), n(x.ritenuta), n(x.netto)), colD)
        }
        if (div.isNotEmpty()) {
            riga(listOf("TOTALE", "", "", "", n(div.sumOf { it.second.netto })), colD, true)
        }
        pag?.let { doc.finishPage(it) }

        val dir = File(act.cacheDir, "pdf").apply { mkdirs() }
        val nome = "investimenti_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm")) + ".pdf"
        val f = File(dir, nome)
        try {
            FileOutputStream(f).use { doc.writeTo(it) }
        } finally {
            doc.close()
        }
        return f
    }

    fun condividi(act: Activity, f: File, titolo: String) {
        val uri = FileProvider.getUriForFile(act, act.packageName + ".fileprovider", f)
        val i = Intent(Intent.ACTION_SEND)
            .setType(if (f.extension.lowercase() == "json") "application/json" else "application/pdf")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, titolo)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        act.startActivity(Intent.createChooser(i, "Condividi"))
    }
}
