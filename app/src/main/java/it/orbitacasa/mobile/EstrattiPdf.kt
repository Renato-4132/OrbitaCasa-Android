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

object EstrattiPdf {
    private const val W = 595
    private const val H = 842
    private const val M = 30f
    private val DF = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    class Riga(
        val data: LocalDate,
        val cat: String,
        val desc: String,
        val altro: String,
        val importo: Double,
        val entrata: Boolean,
    )

    class Sez(val titolo: String, val righe: List<Riga>) {
        val e: Double = righe.filter { it.entrata }.sumOf { it.importo }
        val u: Double = righe.filter { !it.entrata }.sumOf { it.importo }
    }

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

    private fun saldoTxt(x: Double): String = (if (x >= 0) "+" else "−") + euro(abs(x))

    fun crea(
        act: Activity, titolo: String, periodo: String, colAltro: String,
        sezioni: List<Sez>, nOp: Int, tE: Double, tU: Double, prefissoFile: String,
    ): File {
        val doc = PdfDocument()
        val pn = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 9f; color = Color.BLACK }
        val pb = Paint(pn).apply { typeface = Typeface.DEFAULT_BOLD }
        val pt = Paint(pb).apply { textSize = 16f }
        val pg = Paint(pb).apply { color = Color.DKGRAY; textSize = 8.5f }
        val pgr = Paint(pg).apply { textAlign = Paint.Align.RIGHT }
        val pnr = Paint(pn).apply { textAlign = Paint.Align.RIGHT }
        val pbr = Paint(pb).apply { textAlign = Paint.Align.RIGHT }
        val pe = Paint(pb).apply { color = Color.rgb(27, 127, 59); textAlign = Paint.Align.RIGHT }
        val pu = Paint(pb).apply { color = Color.rgb(198, 40, 40); textAlign = Paint.Align.RIGHT }
        val fondo = Paint().apply { color = Color.rgb(236, 236, 236); style = Paint.Style.FILL }
        val linea = Paint().apply { color = Color.LTGRAY; strokeWidth = 0.6f }
        val destra = W - M

        var num = 0
        var pag: PdfDocument.Page? = null
        var y = 0f

        fun cv(): Canvas = pag!!.canvas

        fun nuova() {
            pag?.let { doc.finishPage(it) }
            num++
            pag = doc.startPage(PdfDocument.PageInfo.Builder(W, H, num).create())
            y = 42f
        }

        fun testataSezione(t: String) {
            cv().drawRect(M, y - 11f, destra, y + 5f, fondo)
            cv().drawText(righe(t, pb, destra - M - 8f).first(), M + 4f, y, pb)
            y += 20f
            cv().drawText("Data", M, y, pg)
            cv().drawText("Categoria", 92f, y, pg)
            cv().drawText("Descrizione", 190f, y, pg)
            cv().drawText(colAltro, 400f, y, pg)
            cv().drawText("Importo", destra, y, pgr)
            y += 5f
            cv().drawLine(M, y, destra, y, linea)
            y += 12f
        }

        nuova()
        cv().drawText(titolo, M, y, pt)
        y += 16f
        for (r in righe("Periodo: $periodo", pn, destra - M)) {
            cv().drawText(r, M, y, pn)
            y += 12f
        }
        cv().drawText("Operazioni: $nOp", M, y, pn)
        y += 12f
        cv().drawText("Entrate: " + euro(tE) + "     Uscite: " + euro(tU) + "     Saldo: " + saldoTxt(tE - tU), M, y, pb)
        y += 22f

        cv().drawText("Riepilogo", M, y, pb)
        y += 14f
        cv().drawText("Nome", M, y, pg)
        cv().drawText("Entrate", 380f, y, pgr)
        cv().drawText("Uscite", 470f, y, pgr)
        cv().drawText("Saldo", destra, y, pgr)
        y += 5f
        cv().drawLine(M, y, destra, y, linea)
        y += 12f
        for (s in sezioni) {
            val nome = righe(s.titolo, pn, 300f)
            val h = nome.size * 11.5f + 4f
            if (y + h > H - 40f) nuova()
            nome.forEachIndexed { i, t -> cv().drawText(t, M, y + i * 11.5f, pn) }
            cv().drawText(euro(s.e), 380f, y, pnr)
            cv().drawText(euro(s.u), 470f, y, pnr)
            val sd = s.e - s.u
            cv().drawText(saldoTxt(sd), destra, y, if (sd >= 0) pe else pu)
            y += h
        }

        for (s in sezioni) {
            if (y + 80f > H - 40f) nuova() else y += 14f
            testataSezione(s.titolo)
            for (v in s.righe) {
                val c1 = righe(v.cat, pn, 90f)
                val c2 = righe(v.desc, pn, 204f)
                val c3 = righe(v.altro, pn, 88f)
                val n = maxOf(c1.size, c2.size, c3.size)
                val h = n * 11.5f + 6f
                if (y + h > H - 40f) {
                    nuova()
                    testataSezione(s.titolo + " (segue)")
                }
                cv().drawText(v.data.format(DF), M, y, pn)
                cv().drawText(euro(v.importo), destra, y, if (v.entrata) pe else pu)
                c1.forEachIndexed { i, t -> cv().drawText(t, 92f, y + i * 11.5f, pn) }
                c2.forEachIndexed { i, t -> cv().drawText(t, 190f, y + i * 11.5f, pn) }
                c3.forEachIndexed { i, t -> cv().drawText(t, 400f, y + i * 11.5f, pn) }
                y += h - 2f
                cv().drawLine(M, y, destra, y, linea)
                y += 12f
            }
            if (y + 16f > H - 40f) nuova()
            cv().drawText(
                "Entrate " + euro(s.e) + "   Uscite " + euro(s.u) + "   Saldo " + saldoTxt(s.e - s.u),
                destra, y, pbr
            )
            y += 8f
        }
        pag?.let { doc.finishPage(it) }

        val dir = File(act.cacheDir, "pdf").apply { mkdirs() }
        val nome = prefissoFile + "_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm")) + ".pdf"
        val f = File(dir, nome)
        try {
            FileOutputStream(f).use { doc.writeTo(it) }
        } finally {
            doc.close()
        }
        return f
    }

    fun condividi(act: Activity, f: File, oggetto: String) {
        val uri = FileProvider.getUriForFile(act, act.packageName + ".fileprovider", f)
        val i = Intent(Intent.ACTION_SEND)
            .setType("application/pdf")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, oggetto)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        act.startActivity(Intent.createChooser(i, "Condividi estratti"))
    }
}
