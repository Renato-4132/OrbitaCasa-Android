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

object RicercaPdf {
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

    fun crea(act: Activity, ris: List<Voce>, filtri: String, tE: Double, tU: Double): File {
        val doc = PdfDocument()
        val pn = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 9f; color = Color.BLACK }
        val pb = Paint(pn).apply { typeface = Typeface.DEFAULT_BOLD }
        val pt = Paint(pb).apply { textSize = 16f }
        val pg = Paint(pn).apply { color = Color.DKGRAY; textSize = 8.5f; typeface = Typeface.DEFAULT_BOLD }
        val pe = Paint(pb).apply { color = Color.rgb(27, 127, 59); textAlign = Paint.Align.RIGHT }
        val pu = Paint(pb).apply { color = Color.rgb(198, 40, 40); textAlign = Paint.Align.RIGHT }
        val linea = Paint().apply { color = Color.LTGRAY; strokeWidth = 0.6f }
        val destra = W - M

        var num = 0
        var pag: PdfDocument.Page? = null
        var y = 0f

        fun intestazione(c: Canvas) {
            c.drawText("Data", M, y, pg)
            c.drawText("Categoria", 92f, y, pg)
            c.drawText("Descrizione", 190f, y, pg)
            c.drawText("Conto", 400f, y, pg)
            val r = Paint(pg).apply { textAlign = Paint.Align.RIGHT }
            c.drawText("Importo", destra, y, r)
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
                c.drawText("Risultati ricerca", M, y, pt)
                y += 16f
                c.drawText("Data: " + LocalDate.now().format(DF), M, y, pn)
                y += 12f
                for (r in righe("Filtri: $filtri", pn, destra - M)) {
                    c.drawText(r, M, y, pn)
                    y += 12f
                }
                c.drawText("Operazioni trovate: ${ris.size}", M, y, pn)
                y += 12f
                c.drawText("Entrate: " + euro(tE) + "     Uscite: " + euro(tU) + "     Saldo: " + euro(tE - tU), M, y, pb)
                y += 20f
            }
            intestazione(c)
        }

        nuova()
        for (v in ris) {
            val tag = if (v.hashtag.isNotEmpty()) v.hashtag.joinToString(" ") { "#$it" } else ""
            val desc = (v.descrizione + " " + tag).trim()
            val c1 = righe(v.categoria, pn, 90f)
            val c2 = righe(desc, pn, 204f)
            val c3 = righe(v.conto, pn, 88f)
            val n = maxOf(c1.size, c2.size, c3.size)
            val h = n * 11.5f + 6f
            if (y + h > H - 40f) nuova()
            val c = pag!!.canvas
            c.drawText(v.data.format(DF), M, y, pn)
            c.drawText(euro(v.importo), destra, y, if (v.tipo == "Entrata") pe else pu)
            c1.forEachIndexed { i, s -> c.drawText(s, 92f, y + i * 11.5f, pn) }
            c2.forEachIndexed { i, s -> c.drawText(s, 190f, y + i * 11.5f, pn) }
            c3.forEachIndexed { i, s -> c.drawText(s, 400f, y + i * 11.5f, pn) }
            y += h - 2f
            c.drawLine(M, y, destra, y, linea)
            y += 12f
        }
        pag?.let { doc.finishPage(it) }

        val dir = File(act.cacheDir, "pdf").apply { mkdirs() }
        val nome = "ricerca_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm")) + ".pdf"
        val f = File(dir, nome)
        try {
            FileOutputStream(f).use { doc.writeTo(it) }
        } finally {
            doc.close()
        }
        return f
    }

    fun condividi(act: Activity, f: File) {
        val uri = FileProvider.getUriForFile(act, act.packageName + ".fileprovider", f)
        val i = Intent(Intent.ACTION_SEND)
            .setType("application/pdf")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, "Risultati ricerca")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        act.startActivity(Intent.createChooser(i, "Condividi risultati"))
    }
}
