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
import kotlin.math.abs

object ConfrontoPdf {
    class Riga(val categoria: String, val e1: Double, val u1: Double, val e2: Double, val u2: Double)

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

    private fun saldo(x: Double) = (if (x >= 0) "+" else "−") + euro(abs(x))

    fun crea(act: Activity, l1: String, l2: String, dati: List<Riga>): File {
        val doc = PdfDocument()
        val pn = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 9f; color = Color.BLACK }
        val pb = Paint(pn).apply { typeface = Typeface.DEFAULT_BOLD }
        val pt = Paint(pb).apply { textSize = 16f }
        val pg = Paint(pn).apply { color = Color.DKGRAY; textSize = 8.5f; typeface = Typeface.DEFAULT_BOLD }
        val pgR = Paint(pg).apply { textAlign = Paint.Align.RIGHT }
        val pnR = Paint(pn).apply { textAlign = Paint.Align.RIGHT }
        val pbR = Paint(pb).apply { textAlign = Paint.Align.RIGHT }
        val verde = Color.rgb(27, 127, 59)
        val rosso = Color.rgb(198, 40, 40)
        val linea = Paint().apply { color = Color.LTGRAY; strokeWidth = 0.6f }
        val destra = W - M
        val xe1 = 215f
        val xu1 = 280f
        val xe2 = 345f
        val xu2 = 410f
        val xde = 487f
        val xdu = destra

        var num = 0
        var pag: PdfDocument.Page? = null
        var y = 0f

        fun intestazione(c: Canvas) {
            c.drawText("Categoria", M, y, pg)
            c.drawText("Entr. P1", xe1, y, pgR)
            c.drawText("Usc. P1", xu1, y, pgR)
            c.drawText("Entr. P2", xe2, y, pgR)
            c.drawText("Usc. P2", xu2, y, pgR)
            c.drawText("Δ entrate", xde, y, pgR)
            c.drawText("Δ uscite", xdu, y, pgR)
            y += 5f
            c.drawLine(M, y, destra, y, linea)
            y += 12f
        }

        val e1 = dati.sumOf { it.e1 }
        val u1 = dati.sumOf { it.u1 }
        val e2 = dati.sumOf { it.e2 }
        val u2 = dati.sumOf { it.u2 }

        fun nuova() {
            pag?.let { doc.finishPage(it) }
            num++
            val p = doc.startPage(PdfDocument.PageInfo.Builder(W, H, num).create())
            pag = p
            val c = p.canvas
            y = 42f
            if (num == 1) {
                c.drawText("Confronto periodi", M, y, pt)
                y += 16f
                c.drawText("Data: " + LocalDate.now().format(DF), M, y, pn)
                y += 12f
                c.drawText("P1: $l1", M, y, pb)
                y += 12f
                c.drawText("P2: $l2", M, y, pb)
                y += 14f
                c.drawText(
                    "P1  Entrate " + euro(e1) + "     Uscite " + euro(u1) + "     Saldo " + saldo(e1 - u1),
                    M, y, pn
                )
                y += 12f
                c.drawText(
                    "P2  Entrate " + euro(e2) + "     Uscite " + euro(u2) + "     Saldo " + saldo(e2 - u2),
                    M, y, pn
                )
                y += 12f
                val dS = (e2 - u2) - (e1 - u1)
                val pv = Paint(pb).apply { color = if (dS > 0.004) verde else if (dS < -0.004) rosso else Color.DKGRAY }
                c.drawText("Variazione saldo (P2 vs P1): " + saldo(dS), M, y, pv)
                y += 22f
            }
            intestazione(c)
        }

        nuova()
        for (r in dati) {
            val nomi = righe(r.categoria.ifBlank { "Senza categoria" }, pn, 135f)
            val h = nomi.size * 11.5f + 6f
            if (y + h > H - 40f) nuova()
            val c = pag!!.canvas
            nomi.forEachIndexed { i, s -> c.drawText(s, M, y + i * 11.5f, pn) }
            c.drawText(euro(r.e1), xe1, y, pnR)
            c.drawText(euro(r.u1), xu1, y, pnR)
            c.drawText(euro(r.e2), xe2, y, pnR)
            c.drawText(euro(r.u2), xu2, y, pnR)
            val de = r.e2 - r.e1
            val du = r.u2 - r.u1
            if (r.e1 > 0 || r.e2 > 0) {
                c.drawText(saldo(de), xde, y, Paint(pbR).apply { color = if (abs(de) < 0.005) Color.DKGRAY else if (de > 0) verde else rosso })
            }
            if (r.u1 > 0 || r.u2 > 0) {
                c.drawText(saldo(du), xdu, y, Paint(pbR).apply { color = if (abs(du) < 0.005) Color.DKGRAY else if (du > 0) rosso else verde })
            }
            y += h - 2f
            c.drawLine(M, y, destra, y, linea)
            y += 12f
        }
        pag?.let { doc.finishPage(it) }

        val dir = File(act.cacheDir, "pdf").apply { mkdirs() }
        val nome = "confronto_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm")) + ".pdf"
        val f = File(dir, nome)
        try {
            FileOutputStream(f).use { doc.writeTo(it) }
        } finally {
            doc.close()
        }
        return f
    }

    fun condividi(act: Activity, f: File, l1: String, l2: String) {
        val uri = FileProvider.getUriForFile(act, act.packageName + ".fileprovider", f)
        val i = Intent(Intent.ACTION_SEND)
            .setType("application/pdf")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, "Confronto $l1 / $l2")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        act.startActivity(Intent.createChooser(i, "Condividi confronto"))
    }
}
