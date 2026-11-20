package it.orbitacasa.mobile

import android.app.Activity
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object DietaPdf {
    private const val W = 842
    private const val H = 595
    private const val ML = 38f
    private const val MR = 38f
    private const val MT = 48f
    private const val MB = 48f

    private val VERDE = Color.rgb(26, 107, 46)
    private val VERDE2 = Color.rgb(46, 140, 74)
    private val VERDE_CH = Color.rgb(240, 247, 242)
    private val RIGA_ALT = Color.rgb(245, 252, 247)
    private val GRIGIO = Color.rgb(140, 140, 140)
    private val LINEA = Color.rgb(204, 224, 209)
    private val BLU = Color.rgb(18, 87, 143)
    private val BLU2 = Color.rgb(46, 120, 179)
    private val BLU_CH = Color.rgb(237, 245, 252)
    private val BLU_LINEA = Color.rgb(191, 224, 242)
    private val ROSSO = Color.rgb(191, 51, 26)
    private val ARANCIO = Color.rgb(204, 77, 0)

    private class Scrittore {
        val doc = PdfDocument()
        private var pag: PdfDocument.Page? = null
        lateinit var c: Canvas
        var y = 0f
        var n = 0
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        private val r = Paint()

        fun nuova() {
            chiudi()
            n++
            val pg = doc.startPage(PdfDocument.PageInfo.Builder(W, H, n).create())
            pag = pg
            c = pg.canvas
            y = MT
        }

        fun chiudi() {
            val pg = pag ?: return
            val fy = H - MB + 8
            linea(ML, fy - 4, W - MR, fy - 4, LINEA, 0.5f)
            testo("Piano indicativo. Consultare un nutrizionista per esigenze specifiche.", ML, fy + 6, 7f, GRIGIO)
            testo("Pag. $n", W - MR - 30, fy + 6, 7f, GRIGIO)
            doc.finishPage(pg)
            pag = null
        }

        fun serve(h: Float): Boolean {
            if (y + h <= H - MB - 6f) return false
            nuova()
            return true
        }

        fun rett(x: Float, yy: Float, w: Float, h: Float, riempi: Int?, bordo: Int?, sp: Float = 0.4f) {
            if (riempi != null) {
                r.style = Paint.Style.FILL
                r.color = riempi
                c.drawRect(x, yy, x + w, yy + h, r)
            }
            if (bordo != null) {
                r.style = Paint.Style.STROKE
                r.strokeWidth = sp
                r.color = bordo
                c.drawRect(x, yy, x + w, yy + h, r)
            }
        }

        fun linea(x1: Float, y1: Float, x2: Float, y2: Float, col: Int, sp: Float) {
            r.style = Paint.Style.STROKE
            r.strokeWidth = sp
            r.color = col
            c.drawLine(x1, y1, x2, y2, r)
        }

        fun testo(
            s: String, x: Float, yy: Float, size: Float = 8f, col: Int = Color.BLACK,
            bold: Boolean = false, larg: Float? = null, centro: Boolean = false,
        ) {
            p.textSize = size
            p.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            p.color = col
            p.style = Paint.Style.FILL
            var xx = x
            if (centro && larg != null) xx = x + (larg - p.measureText(s)) / 2f
            c.drawText(s, xx, yy, p)
        }

        fun righe(t: String, size: Float, bold: Boolean, w: Float): List<String> {
            if (t.isBlank()) return listOf("")
            p.textSize = size
            p.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            val out = ArrayList<String>()
            var rest = t.trim()
            while (rest.isNotEmpty()) {
                val k = maxOf(1, p.breakText(rest, true, w, null))
                if (k >= rest.length) {
                    out.add(rest)
                    break
                }
                var cut = rest.lastIndexOf(' ', k)
                if (cut <= 0) cut = k
                out.add(rest.substring(0, cut).trim())
                rest = rest.substring(cut).trim()
            }
            return out
        }

        fun titolo(t: String, col: Int, size: Float = 15f) {
            testo(t, ML, y, size, col, true)
            y += 18f
        }

        fun filetto(col: Int) {
            linea(ML, y, W - MR, y, col, 1f)
            y += 14f
        }

        fun banda(testoRiga: String, larg: Float, h: Float, riempi: Int, bordo: Int, col: Int, size: Float = 8.5f) {
            rett(ML, y, larg, h, riempi, bordo, 0.3f)
            testo(testoRiga, ML + 8f, y + h * 0.66f, size, col, true)
            y += h + 10f
        }
    }

    private fun segno(x: Double) = (if (x >= 0) "+" else "") + Dieta.fmt1(x)

    fun crea(act: Activity): File {
        val piano = Dieta.piano()
        val peso = Dieta.peso()
        val fabb = Dieta.fabb()
        val passi = Dieta.passi()
        val w = Scrittore()

        val ultimo = peso.pesate.lastOrNull()
        val altezza = peso.altezza.ifBlank { "--" }
        val obiettivo = peso.obiettivo.ifBlank { "--" }
        val altNum = Dieta.numero(peso.altezza)
        val bmiNum = if (ultimo != null && altNum != null) Dieta.bmi(ultimo.peso, altNum) else null
        val bmiTxt = if (bmiNum != null) "$bmiNum — ${Dieta.bmiCategoriaLunga(bmiNum)}" else "--"
        val info = "Peso Attuale: ${ultimo?.peso ?: "--"} kg (${ultimo?.data ?: ""})  |  Altezza: $altezza cm  |  " +
            "Obiettivo: $obiettivo kg  |  BMI: $bmiTxt"

        for (s in piano.sett) settimana(w, s, info)
        if (peso.pesate.isNotEmpty()) paginaPeso(w, peso, altNum, info)
        paginaFabbisogno(w, piano, fabb, bmiTxt)
        if (passi.voci.isNotEmpty()) paginaPassi(w, passi)
        if (w.n == 0) {
            w.nuova()
            w.titolo("Piano Dieta Mediterranea", VERDE)
            w.testo("Nessun dato presente.", ML, w.y + 10f, 10f)
        }
        w.chiudi()

        val dir = File(act.cacheDir, "pdf").apply { mkdirs() }
        val f = File(dir, "dieta_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm")) + ".pdf")
        try {
            FileOutputStream(f).use { w.doc.writeTo(it) }
        } finally {
            w.doc.close()
        }
        return f
    }

    fun analisi(act: Activity, titolo: String, testo: String): File {
        val pulisci = { t: String -> t.replace(Regex("[\\uD800-\\uDFFF\\uFE0F\\u200D]"), "").trim() }
        val w = Scrittore()
        val largo = W - ML - MR
        w.nuova()
        w.titolo("Analisi dieta", VERDE)
        w.testo(pulisci(titolo), ML, w.y, 10f, VERDE2)
        w.y += 10f
        w.filetto(VERDE)
        for (riga in testo.lines().map { pulisci(it) }) {
            if (riga.isEmpty()) {
                w.y += 6f
                continue
            }
            val titoloSez = riga.length <= 80 && riga.any { it.isLetter() } && riga == riga.uppercase()
            val size = if (titoloSez) 10f else 9f
            if (titoloSez) w.y += 4f
            for (r in w.righe(riga, size, titoloSez, largo)) {
                w.serve(14f)
                w.testo(r, ML, w.y + 9f, size, if (titoloSez) VERDE else Color.BLACK, titoloSez)
                w.y += 13f
            }
        }
        w.chiudi()
        val dir = File(act.cacheDir, "pdf").apply { mkdirs() }
        val f = File(dir, "analisi_dieta_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm")) + ".pdf")
        try {
            FileOutputStream(f).use { w.doc.writeTo(it) }
        } finally {
            w.doc.close()
        }
        return f
    }

    fun condividi(act: Activity, f: File) = PensionePdf.condividi(act, f, "Piano dieta")

    private fun settimana(w: Scrittore, s: Dieta.Sett, info: String) {
        val cw = listOf(62f, 115f, 115f, 115f, 90f, 38f, 36f, 36f, 36f, 36f, 36f)
        val hdr = listOf("Giorno", "Colazione", "Pranzo", "Cena", "Spuntino", "Kcal", "Prot.", "Gras.", "Carb.", "Zucc.", "Fibre")
        val tw = cw.sum()
        w.nuova()
        w.titolo("Piano Dieta Mediterranea", VERDE)
        w.testo(s.titolo, ML, w.y, 10f, VERDE2)
        w.y += 10f
        w.filetto(VERDE)
        w.rett(ML, w.y, tw, 25f, VERDE_CH, GRIGIO, 0.3f)
        w.testo(info, ML + 10f, w.y + 16f, 8f, Color.BLACK, true)
        w.y += 35f
        w.rett(ML, w.y, tw, 18f, VERDE2, null)
        var xc = ML
        for ((i, h) in hdr.withIndex()) {
            w.testo(h, xc + 2f, w.y + 12f, 7.5f, Color.WHITE, true, cw[i] - 2f, true)
            xc += cw[i]
        }
        w.y += 18f
        val rh = 48f
        for ((ri, g) in s.giorni.withIndex()) {
            if (w.y + rh > H - MB - 40f) break
            w.rett(ML, w.y, tw, rh, if (ri % 2 == 0) RIGA_ALT else Color.WHITE, LINEA)
            xc = ML
            w.testo(g.nome, xc + 3f, w.y + 26f, 8f, VERDE, true)
            xc += cw[0]
            for (pi in 0 until 4) {
                val righe = w.righe(g.pasti[pi], 6.5f, false, cw[1 + pi] - 4f)
                for ((i, t) in righe.take(4).withIndex()) w.testo(t, xc + 2f, w.y + 12f + i * 9f, 6.5f)
                xc += cw[1 + pi]
            }
            for (k in 0 until 6) {
                w.testo(g.tot[k].toString(), xc + 1f, w.y + 26f, 7.5f, Color.BLACK, false, cw[5 + k] - 1f, true)
                xc += cw[5 + k]
            }
            w.y += rh
        }
        w.rett(ML, w.y, tw, 18f, VERDE_CH, VERDE2, 0.6f)
        w.testo("TOTALE SETTIMANA", ML + 3f, w.y + 12f, 7.5f, VERDE, true)
        xc = ML + cw.take(5).sum()
        val tot = s.totali()
        for (k in 0 until 6) {
            w.testo(tot[k].toString(), xc + 1f, w.y + 12f, 7.5f, VERDE, true, cw[5 + k] - 1f, true)
            xc += cw[5 + k]
        }
        w.y += 22f
        val m = s.medie()
        w.testo(
            "Media/giorno: ${m[0]} kcal | Prot ${m[1]}g | Grassi ${m[2]}g | Carb ${m[3]}g | Zucc ${m[4]}g | Fibre ${m[5]}g",
            ML, w.y + 9f, 7.5f, GRIGIO
        )
    }

    private fun paginaPeso(w: Scrittore, peso: Dieta.PesoDati, altNum: Double?, info: String) {
        val cw = listOf(90f, 70f, 70f, 80f)
        val hdr = listOf("Data", "Peso kg", "Variaz.", "BMI")
        val tw = cw.sum()
        val larg = W - ML - MR

        fun intesta() {
            w.rett(ML, w.y, tw, 18f, VERDE2, null)
            var xc = ML
            for ((i, h) in hdr.withIndex()) {
                w.testo(h, xc + 2f, w.y + 12f, 8f, Color.WHITE, true, cw[i] - 2f, true)
                xc += cw[i]
            }
            w.y += 18f
        }

        w.nuova()
        w.titolo("Monitoraggio Peso", VERDE)
        w.filetto(VERDE)
        w.rett(ML, w.y, larg, 22f, VERDE_CH, GRIGIO, 0.3f)
        w.testo(info, ML + 8f, w.y + 15f, 8.5f, Color.BLACK, true)
        w.y += 32f
        intesta()
        for ((i, r) in peso.pesate.withIndex()) {
            if (w.serve(18f)) {
                w.titolo("Monitoraggio Peso (continua)", VERDE, 13f)
                intesta()
            }
            w.rett(ML, w.y, tw, 18f, if (i % 2 == 0) RIGA_ALT else Color.WHITE, LINEA)
            var varTxt = "—"
            var varCol = Color.BLACK
            if (i > 0) {
                val diff = Dieta.round1(r.peso - peso.pesate[i - 1].peso)
                varTxt = segno(diff)
                varCol = if (diff > 0) ARANCIO else Color.rgb(26, 128, 26)
            }
            val bmiTxt = altNum?.let { Dieta.bmi(r.peso, it)?.toString() } ?: "—"
            val vals = listOf(r.data, Dieta.fmt1(r.peso).replace(',', '.'), varTxt, bmiTxt)
            var xc = ML
            for ((k, v) in vals.withIndex()) {
                w.testo(v, xc + 2f, w.y + 13f, 8f, if (k == 2) varCol else Color.BLACK, false, cw[k] - 2f, true)
                xc += cw[k]
            }
            w.y += 18f
        }
        w.y += 10f
        w.serve(60f)
        val pesi = peso.pesate.map { it.peso }
        val delta = Dieta.round1(pesi.last() - pesi.first())
        w.banda(
            "Pesate: ${pesi.size}   |   Min: ${pesi.minOrNull() ?: 0.0} kg   |   Max: ${pesi.maxOrNull() ?: 0.0} kg   |   " +
                "Media: ${Dieta.round1(pesi.sum() / pesi.size)} kg   |   Variazione totale: ${segno(delta)} kg",
            larg.toFloat(), 20f, VERDE_CH, VERDE2, VERDE, 8f
        )
        val ob = Dieta.numero(peso.obiettivo)
        if (ob != null) {
            val dist = Dieta.round1(pesi.last() - ob)
            w.testo(
                "Distanza dall'obiettivo: ${segno(dist)} kg  (" + (if (dist > 0) "da perdere" else "obiettivo raggiunto!") + ")",
                ML, w.y, 9f, VERDE2, true
            )
        }
    }

    private fun paginaFabbisogno(w: Scrittore, piano: Dieta.Piano, f: Dieta.Fabb, bmiTxt: String) {
        val p = Dieta.numero(f.peso)
        val a = Dieta.numero(f.altezza)
        val e = f.eta.trim().toIntOrNull()
        if (p == null || a == null || e == null) return
        val fb = Dieta.calcolaFabb(f.sesso, p, a, e) ?: return
        val larg = (W - ML - MR)
        w.nuova()
        w.titolo("Fabbisogno Calorico", VERDE)
        w.filetto(VERDE)
        w.rett(ML, w.y, larg, 22f, VERDE_CH, GRIGIO, 0.3f)
        w.testo(
            "Sesso: ${f.sesso}   |   Peso: ${f.peso} kg   |   Altezza: ${f.altezza} cm   |   Età: ${f.eta} anni   |   BMI: $bmiTxt",
            ML + 8f, w.y + 15f, 8.5f, Color.BLACK, true
        )
        w.y += 32f
        w.rett(ML, w.y, larg, 22f, VERDE2, null)
        w.testo("Metabolismo Basale (BMR): ${fb.bmr} kcal/giorno", ML + 8f, w.y + 15f, 10f, Color.WHITE, true)
        w.y += 32f
        for ((i, l) in fb.livelli.withIndex()) {
            w.rett(ML, w.y, larg, 20f, if (i % 2 == 0) RIGA_ALT else Color.WHITE, LINEA)
            w.testo("Fabbisogno: ${l.nome}", ML + 8f, w.y + 14f, 9f)
            w.testo("${l.kcal} kcal", W - MR - 70f, w.y + 14f, 9f, Color.BLACK, true)
            w.y += 20f
        }
        w.y += 16f
        w.testo("Obiettivi Consigliati", ML, w.y, 10f, VERDE2, true)
        w.y += 14f
        val colori = listOf(Color.rgb(204, 51, 51), Color.rgb(26, 102, 204), Color.rgb(26, 153, 51))
        for ((i, l) in fb.obiettivi.withIndex()) {
            w.rett(ML, w.y, larg, 20f, VERDE_CH, LINEA)
            w.testo(l.nome, ML + 8f, w.y + 14f, 9f)
            w.testo("${l.kcal} kcal", W - MR - 70f, w.y + 14f, 9f, colori[i], true)
            w.y += 20f
        }
        w.y += 16f
        val media = piano.mediaKcal()
        if (media != null) {
            val diff = media - fb.livelli[2].kcal
            w.rett(ML, w.y, larg, 24f, VERDE_CH, VERDE2, 0.5f)
            w.testo(
                "Media piano dieta: $media kcal/giorno   |   Differenza vs mantenimento: ${if (diff >= 0) "+" else ""}$diff kcal",
                ML + 8f, w.y + 16f, 9f, VERDE, true
            )
        }
    }

    private fun paginaPassi(w: Scrittore, d: Dieta.PassiDati) {
        val larg = W - ML - MR
        val cw = listOf(80f, 65f, 70f, 45f, 100f, larg - 80f - 65f - 70f - 45f - 100f)
        val hdr = listOf("Data", "Passi", "Obiettivo", "%", "Esito", "Note")
        val tw = cw.sum()
        val voci = d.voci
        val oggi = LocalDate.now()

        fun intesta() {
            w.rett(ML, w.y, tw, 18f, BLU2, null)
            var xc = ML
            for ((i, h) in hdr.withIndex()) {
                w.testo(h, xc + 2f, w.y + 12f, 8f, Color.WHITE, true, cw[i] - 2f, true)
                xc += cw[i]
            }
            w.y += 18f
        }

        val ok = voci.count { it.raggiunto }
        val ultimi30 = voci.filter { val dd = java.time.temporal.ChronoUnit.DAYS.between(it.d, oggi); dd in 0..29 }.map { it.passi }
        var riep = "Giorni registrati: ${voci.size}   |   Record: ${Dieta.fmtInt(voci.maxOf { it.passi })} passi"
        if (ultimi30.isNotEmpty()) riep += "   |   Media (30gg): ${Dieta.fmtInt(Math.rint(ultimi30.sum().toDouble() / ultimi30.size).toInt())}"
        riep += "   |   Giorni obiettivo raggiunto: $ok/${voci.size}"

        w.nuova()
        w.titolo("Storico Passi", BLU)
        w.filetto(BLU)
        w.rett(ML, w.y, larg.toFloat(), 22f, BLU_CH, GRIGIO, 0.3f)
        w.testo(riep, ML + 8f, w.y + 15f, 8.5f, Color.BLACK, true)
        w.y += 32f
        intesta()
        for ((i, r) in voci.withIndex()) {
            if (w.serve(16f)) {
                w.titolo("Storico Passi (continua)", BLU, 13f)
                intesta()
            }
            w.rett(ML, w.y, tw, 16f, if (i % 2 == 0) BLU_CH else Color.WHITE, BLU_LINEA)
            val colEsito = if (r.raggiunto) VERDE else ROSSO
            val vals = listOf(
                r.data, Dieta.fmtInt(r.passi), Dieta.fmtInt(r.ob), "${r.pct}%",
                if (r.raggiunto) "Raggiunto" else "Mancato", r.note,
            )
            var xc = ML
            for ((k, v) in vals.withIndex()) {
                val testo = if (k == 5) w.righe(v, 7.5f, false, cw[k] - 4f).first() else v
                w.testo(testo, xc + 2f, w.y + 11f, 7.5f, if (k == 4) colEsito else Color.BLACK, false, cw[k] - 2f, k != 5)
                xc += cw[k]
            }
            w.y += 16f
        }
        w.y += 8f
        w.serve(28f)
        val tot = voci.sumOf { it.passi }
        w.rett(ML, w.y, tw, 20f, BLU_CH, BLU2, 0.5f)
        w.testo(
            "Totale passi: ${Dieta.fmtInt(tot)}   |   Media giornaliera: ${Dieta.fmtInt(Math.rint(tot.toDouble() / voci.size).toInt())}   |   " +
                "Record: ${Dieta.fmtInt(voci.maxOf { it.passi })}   |   Giorni obiettivo: $ok/${voci.size}",
            ML + 8f, w.y + 14f, 8f, BLU, true
        )
    }
}
