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

object PensionePdf {
    private const val W = 595
    private const val H = 842
    private const val M = 30f
    private val DF = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    private fun righe(t: String, p: Paint, w: Float): List<String> {
        if (t.isBlank()) return listOf("")
        val out = ArrayList<String>()
        var rest = t.trim()
        while (rest.isNotEmpty()) {
            val n = maxOf(1, p.breakText(rest, true, w, null))
            if (n >= rest.length) {
                out.add(rest)
                break
            }
            var cut = rest.lastIndexOf(' ', n)
            if (cut <= 0) cut = n
            out.add(rest.substring(0, cut).trim())
            rest = rest.substring(cut).trim()
        }
        return out
    }

    fun crea(act: Activity, d: Pensione.Dati, chiave: String): File {
        val (titolo, l) = when (chiave) {
            Pensione.VERSAMENTI -> "Fondo pensione - Versamenti" to d.vers
            Pensione.VALORIZZAZIONI -> "Fondo pensione - Valorizzazioni" to d.valor
            else -> "Fondo pensione - Riscatti / Anticipazioni" to d.risc
        }
        val righeOrd = l.sortedBy { Pensione.data(it.data) ?: LocalDate.MIN }
        val conTipo = chiave != Pensione.VALORIZZAZIONI
        val xTipo = 92f
        val xDett = if (conTipo) 230f else 92f
        val doc = PdfDocument()
        val pn = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 9f; color = Color.BLACK }
        val pb = Paint(pn).apply { typeface = Typeface.DEFAULT_BOLD }
        val pt = Paint(pb).apply { textSize = 16f }
        val pg = Paint(pn).apply { color = Color.DKGRAY; textSize = 8.5f; typeface = Typeface.DEFAULT_BOLD }
        val pi = Paint(pb).apply { textAlign = Paint.Align.RIGHT }
        val linea = Paint().apply { color = Color.LTGRAY; strokeWidth = 0.6f }
        val destra = W - M

        var num = 0
        var pag: PdfDocument.Page? = null
        var y = 0f

        fun intestazione(c: Canvas) {
            c.drawText("Data", M, y, pg)
            if (conTipo) c.drawText("Tipo", xTipo, y, pg)
            c.drawText("Dettagli", xDett, y, pg)
            val r = Paint(pg).apply { textAlign = Paint.Align.RIGHT }
            c.drawText(if (conTipo) "Importo" else "Controvalore", destra, y, r)
            y += 5f
            c.drawLine(M, y, destra, y, linea)
            y += 12f
        }

        fun nuova() {
            pag?.let { doc.finishPage(it) }
            num++
            val p = doc.startPage(PdfDocument.PageInfo.Builder(W, H, num).create())
            pag = p
            val c = p.canvas
            y = 42f
            if (num == 1) {
                c.drawText(titolo, M, y, pt)
                y += 16f
                c.drawText("Data: " + LocalDate.now().format(DF), M, y, pn)
                y += 12f
                val fondo = listOf(d.piano.nome, d.piano.gestore, d.piano.comparto).filter { it.isNotBlank() }.joinToString(" - ")
                if (fondo.isNotEmpty()) {
                    for (r in righe("Fondo: $fondo", pn, destra - M)) {
                        c.drawText(r, M, y, pn)
                        y += 12f
                    }
                }
                c.drawText("Voci: ${l.size}", M, y, pn)
                y += 12f
                if (conTipo) {
                    c.drawText("Totale: " + euro(l.sumOf { it.importo }), M, y, pb)
                    y += 12f
                }
                y += 8f
            }
            intestazione(c)
        }

        nuova()
        if (righeOrd.isEmpty()) {
            pag!!.canvas.drawText("Nessuna voce presente.", M, y, pn)
        }
        for (m in righeOrd) {
            val dett = listOf(m.motivo, m.note).filter { it.isNotBlank() }.joinToString(" - ")
            val c1 = if (conTipo) righe(m.tipo, pn, xDett - xTipo - 8f) else listOf("")
            val c2 = righe(dett, pn, destra - 90f - xDett)
            val n = maxOf(c1.size, c2.size)
            val h = n * 11.5f + 6f
            if (y + h > H - 40f) nuova()
            val c = pag!!.canvas
            c.drawText(m.data, M, y, pn)
            c.drawText(euro(m.importo), destra, y, pi)
            c1.forEachIndexed { i, s -> c.drawText(s, xTipo, y + i * 11.5f, pn) }
            c2.forEachIndexed { i, s -> c.drawText(s, xDett, y + i * 11.5f, pn) }
            y += h - 2f
            c.drawLine(M, y, destra, y, linea)
            y += 12f
        }
        pag?.let { doc.finishPage(it) }

        val dir = File(act.cacheDir, "pdf").apply { mkdirs() }
        val nome = "pensione_" + chiave + "_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm")) + ".pdf"
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
            .setType("application/pdf")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, titolo)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        act.startActivity(Intent.createChooser(i, "Condividi"))
    }
}
