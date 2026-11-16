package it.orbitacasa.mobile

import android.app.Activity
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** PDF di MyBusiness: fattura / preventivo / nota di credito e inventario di magazzino. */
object StudioPdf {
    private const val W = 595
    private const val H = 842
    private const val M = 45f
    private val BLU = Color.rgb(26, 56, 115)
    private val BLU_L = Color.rgb(46, 97, 173)
    private val ACC = Color.rgb(0, 140, 204)
    private val GRIGIO_L = Color.rgb(242, 245, 247)
    private val GRIGIO_M = Color.rgb(204, 209, 217)
    private val GRIGIO_D = Color.rgb(115, 115, 122)
    private val NERO = Color.rgb(26, 26, 31)
    private val ROSSO = Color.rgb(184, 26, 26)
    private val VERDE = Color.rgb(20, 128, 56)
    private val ARANCIO = Color.rgb(204, 115, 0)

    private class Pagina(val doc: PdfDocument) {
        var n = 0
        lateinit var page: PdfDocument.Page
        lateinit var c: Canvas
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val txt = Paint(Paint.ANTI_ALIAS_FLAG)
        val ln = Paint().apply { strokeWidth = 0.5f }

        fun nuova() {
            chiudi()
            n++
            page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, n).create())
            c = page.canvas
            rett(0f, 0f, 4f, H.toFloat(), ACC)
        }

        var alChiudere: (() -> Unit)? = null

        fun chiudi() {
            if (n > 0) {
                alChiudere?.invoke()
                doc.finishPage(page)
            }
        }

        fun rett(x0: Float, y0: Float, x1: Float, y1: Float, col: Int, bordo: Int? = null) {
            fill.color = col
            c.drawRect(x0, y0, x1, y1, fill)
            if (bordo != null) {
                val s = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = bordo; strokeWidth = 0.8f }
                c.drawRect(x0, y0, x1, y1, s)
            }
        }

        fun linea(x0: Float, y0: Float, x1: Float, y1: Float, col: Int = GRIGIO_M, sp: Float = 0.5f) {
            ln.color = col
            ln.strokeWidth = sp
            c.drawLine(x0, y0, x1, y1, ln)
        }

        fun testo(x: Float, y: Float, t: String, size: Float = 9f, col: Int = NERO, bold: Boolean = false, destra: Boolean = false) {
            txt.color = col
            txt.textSize = size
            txt.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            txt.textAlign = if (destra) Paint.Align.RIGHT else Paint.Align.LEFT
            c.drawText(t, x, y, txt)
        }

        fun larghezza(t: String, size: Float, bold: Boolean): Float {
            txt.textSize = size
            txt.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            return txt.measureText(t)
        }

        fun righe(t: String, size: Float, w: Float): List<String> {
            if (t.isBlank()) return emptyList()
            txt.textSize = size
            txt.typeface = Typeface.DEFAULT
            val out = ArrayList<String>()
            for (par in t.split("\n")) {
                var rest = par.trim()
                if (rest.isEmpty()) continue
                while (rest.isNotEmpty()) {
                    val k = maxOf(1, txt.breakText(rest, true, w, null))
                    if (k >= rest.length) {
                        out.add(rest)
                        break
                    }
                    var cut = rest.lastIndexOf(' ', k)
                    if (cut <= 0) cut = k
                    out.add(rest.substring(0, cut).trim())
                    rest = rest.substring(cut).trim()
                }
            }
            return out
        }

        fun piede(sinistra: String, destra: String) {
            rett(0f, H - 32f, W.toFloat(), H.toFloat(), BLU)
            linea(0f, H - 32f, W.toFloat(), H - 32f, ACC, 2f)
            testo(M, H - 14f, sinistra, 7f, GRIGIO_M)
            testo(W - M - 4f, H - 14f, destra, 7f, GRIGIO_M, destra = true)
        }
    }

    private fun tronca(p: Pagina, t: String, size: Float, w: Float): String {
        if (p.larghezza(t, size, false) <= w) return t
        var s = t
        while (s.length > 1 && p.larghezza("$s…", size, false) > w) s = s.dropLast(1)
        return "$s…"
    }

    private fun scrivi(act: Activity, doc: PdfDocument, prefisso: String): File {
        val dir = File(act.cacheDir, "pdf").apply { mkdirs() }
        val f = File(dir, prefisso + "_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".pdf")
        try {
            FileOutputStream(f).use { doc.writeTo(it) }
        } finally {
            doc.close()
        }
        return f
    }

    private fun dmy(iso: String): String = Studio.dmy(iso)

    private fun num(x: Double): String = Studio.fmtNum(x)

    // ---------------------------------------------------------------- documento

    fun documento(act: Activity, docu: JSONObject): File {
        val emi = Studio.emittente()
        val forf = Studio.s(emi, "regime_fiscale") == "Forfettario"
        val nomeCli = Studio.s(docu, "cliente_nome")
        val cli = Studio.clientePerNome(nomeCli) ?: JSONObject()
        val doc = PdfDocument()
        val p = Pagina(doc)
        val rs = Studio.s(emi, "ragione_sociale")
        val cf = Studio.s(emi, "cf_piva")
        val oggi = LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
        p.alChiudere = {
            val sx = listOf(rs, if (cf.isNotBlank()) "P.IVA $cf" else "").filter { it.isNotBlank() }.joinToString(" | ")
            p.piede(sx, "Pag. ${p.n}  —  Stampato il $oggi")
        }
        p.nuova()
        val colR = W - M

        p.rett(0f, 0f, W.toFloat(), 80f, BLU)
        p.rett(0f, 80f, W.toFloat(), 84f, ACC)
        p.testo(M, 35f, Studio.tipoDoc(docu).uppercase(), 22f, Color.WHITE, true)
        p.testo(M, 55f, "N°  " + Studio.s(docu, "numero"), 12f, ACC, true)
        val xData = colR - 150f
        p.testo(xData, 30f, "Data emissione", 7f, GRIGIO_M)
        p.testo(xData, 44f, dmy(Studio.s(docu, "data")), 11f, Color.WHITE, true)
        val sca = Studio.s(docu, "scadenza")
        if (sca.isNotBlank()) {
            p.testo(xData, 58f, "Scadenza", 7f, GRIGIO_M)
            p.testo(xData, 70f, dmy(sca), 10f, ARANCIO, true)
        }

        var y = 96f
        val mid = W / 2f + 6f
        val boxH = 84f
        p.rett(M, y, mid - 6f, y + boxH, GRIGIO_L, GRIGIO_M)
        p.testo(M + 8f, y + 12f, "EMITTENTE", 7f, BLU_L, true)
        p.linea(M + 8f, y + 16f, mid - 14f, y + 16f)
        val righeE = listOf(
            Triple(Studio.s(emi, "ragione_sociale"), true, 9f),
            Triple(Studio.s(emi, "indirizzo"), false, 8f),
            Triple(listOf(Studio.s(emi, "cap"), Studio.s(emi, "comune"), Studio.s(emi, "provincia")).filter { it.isNotBlank() }.joinToString(" "), false, 8f),
            Triple(if (cf.isNotBlank()) "P.IVA / CF: $cf" else "", false, 8f),
            Triple(listOf(Studio.s(emi, "telefono"), Studio.s(emi, "email")).filter { it.isNotBlank() }.joinToString(" | "), false, 7f),
        )
        var ye = y + 27f
        for ((t, b, sz) in righeE) if (t.isNotBlank()) {
            p.testo(M + 8f, ye, tronca(p, t, sz, mid - M - 24f), sz, if (b) NERO else GRIGIO_D, b)
            ye += sz + 3f
        }
        p.rett(mid + 6f, y, colR, y + boxH, GRIGIO_L, GRIGIO_M)
        p.testo(mid + 14f, y + 12f, "DESTINATARIO", 7f, BLU_L, true)
        p.linea(mid + 14f, y + 16f, colR - 8f, y + 16f)
        val cfC = Studio.s(cli, "cf_piva")
        val righeC = listOf(
            Triple(Studio.s(cli, "azienda").ifBlank { nomeCli }, true, 9f),
            Triple(Studio.s(cli, "indirizzo"), false, 8f),
            Triple(if (cfC.isNotBlank()) "P.IVA / CF: $cfC" else "", false, 8f),
            Triple(Studio.s(cli, "email").ifBlank { Studio.s(cli, "telefono") }, false, 7f),
        )
        var yc = y + 27f
        for ((t, b, sz) in righeC) if (t.isNotBlank()) {
            p.testo(mid + 14f, yc, tronca(p, t, sz, colR - mid - 22f), sz, if (b) NERO else GRIGIO_D, b)
            yc += sz + 3f
        }
        y += boxH + 16f

        val xDesc = M + 4f
        val xQty = M + 268f
        val xPrez = M + 348f
        val xIva = M + 400f
        val xImp = colR - 4f
        val hdrH = 18f
        fun intestazione() {
            p.rett(M, y, colR, y + hdrH, BLU)
            p.testo(xDesc, y + 12f, "DESCRIZIONE", 7.5f, Color.WHITE, true)
            p.testo(xQty, y + 12f, "QTÀ", 7.5f, Color.WHITE, true, true)
            p.testo(xPrez, y + 12f, "PREZZO", 7.5f, Color.WHITE, true, true)
            p.testo(xIva, y + 12f, "IVA %", 7.5f, Color.WHITE, true, true)
            p.testo(xImp, y + 12f, "IMPORTO", 7.5f, Color.WHITE, true, true)
            y += hdrH
        }
        intestazione()
        val righe = Studio.righe(docu)
        val rowH = 15f
        for ((i, r) in righe.withIndex()) {
            if (y + rowH > H - 170f) {
                p.nuova()
                y = M
                intestazione()
            }
            p.rett(M, y, colR, y + rowH, if (i % 2 == 0) GRIGIO_L else Color.WHITE)
            p.linea(M, y + rowH, colR, y + rowH, GRIGIO_M, 0.3f)
            val sub = r.qty * r.prezzo * (1 + r.iva / 100.0)
            p.testo(xDesc, y + 10f, tronca(p, r.desc, 8f, 255f), 8f)
            p.testo(xQty, y + 10f, Studio.fmtQ(r.qty), 8f, destra = true)
            p.testo(xPrez, y + 10f, num(r.prezzo), 8f, destra = true)
            p.testo(xIva, y + 10f, String.format("%.0f%%", r.iva), 8f, destra = true)
            p.testo(xImp, y + 10f, num(sub), 8f, destra = true)
            y += rowH
        }
        y += 10f
        p.linea(M, y, colR, y, BLU_L, 1f)
        y += 12f
        val tot = Studio.totali(righe)
        val boxX = colR - 190f
        fun riepilogo(label: String, v: Double, evid: Boolean = false) {
            if (evid) {
                p.rett(boxX - 4f, y - 2f, colR, y + 13f, BLU)
                p.testo(boxX + 4f, y + 9f, label, 9f, Color.WHITE, true)
                p.testo(colR - 4f, y + 9f, num(v), 9f, Color.WHITE, true, true)
            } else {
                p.testo(boxX + 4f, y + 9f, label, 8f, GRIGIO_D)
                p.testo(colR - 4f, y + 9f, num(v), 8f, destra = true)
                p.linea(boxX, y + 14f, colR, y + 14f, GRIGIO_M, 0.3f)
            }
        }
        riepilogo("Imponibile", tot.imponibile)
        y += 16f
        if (!forf) for ((al, v) in Studio.riepilogoIva(righe)) {
            riepilogo(String.format("IVA %.0f%%", al), v)
            y += 16f
        }
        y += 2f
        riepilogo("TOTALE DOCUMENTO", tot.totale, true)
        y += 22f
        if (forf) {
            p.rett(M, y, colR, y + 10f, GRIGIO_L)
            p.testo(M + 4f, y + 8f, "REGIME FORFETTARIO", 7f, BLU_L, true)
            y += 14f
            val dic = "Operazione effettuata ai sensi dell'art. 1, commi 54-89, L. 190/2014 (regime forfettario). " +
                "Operazione senza applicazione dell'IVA, non soggetta a ritenuta d'acconto ai sensi dell'art. 1, comma 67, L. 190/2014."
            for (l in p.righe(dic, 7.5f, colR - M - 8f)) {
                p.testo(M + 4f, y, l, 7.5f, GRIGIO_D)
                y += 11f
            }
            if (tot.totale > 77.47) {
                p.testo(M + 4f, y, "Imposta di bollo assolta in modo virtuale ove dovuta (art. 15 DPR 642/1972).", 7f, GRIGIO_D)
                y += 12f
            }
            y += 4f
        }
        val stato = Studio.s(docu, "stato")
        if (stato.isNotBlank()) {
            val col = when (stato) {
                "Pagata" -> VERDE
                "Scaduta", "Annullata" -> ROSSO
                else -> ARANCIO
            }
            p.rett(M, y - 1f, M + 90f, y + 13f, col)
            p.testo(M + 4f, y + 9f, "● " + stato.uppercase(), 8f, Color.WHITE, true)
            y += 20f
        }
        val pag = Studio.s(docu, "modalita_pagamento")
        if (pag.isNotBlank()) {
            p.testo(M, y + 6f, "Modalità di pagamento: $pag", 8f, GRIGIO_D)
            y += 14f
        }
        val coll = Studio.s(docu, "fattura_collegata_numero")
        if (coll.isNotBlank()) {
            p.testo(M, y + 6f, "Fattura collegata: $coll", 8f, GRIGIO_D)
            y += 14f
        }
        val note = Studio.s(docu, "note")
        if (note.isNotBlank()) {
            y += 4f
            p.rett(M, y, colR, y + 10f, GRIGIO_L)
            p.testo(M + 4f, y + 8f, "NOTE", 7f, BLU_L, true)
            y += 14f
            for (l in p.righe(note, 8f, colR - M - 8f)) {
                if (y > H - 50f) {
                    p.nuova()
                    y = M
                }
                p.testo(M + 4f, y, l, 8f, GRIGIO_D)
                y += 12f
            }
        }
        val iban = Studio.s(emi, "iban")
        if (iban.isNotBlank()) {
            if (y > H - 80f) {
                p.nuova()
                y = M
            }
            y += 8f
            p.linea(M, y, colR, y)
            y += 10f
            p.testo(M, y, "COORDINATE BANCARIE", 7f, BLU_L, true)
            y += 11f
            p.testo(M, y, "IBAN: $iban  —  Intestato a: " + Studio.s(emi, "ragione_sociale"), 8f, GRIGIO_D)
        }
        p.chiudi()
        val nome = "documento_" + Studio.s(docu, "numero").replace('/', '_')
        return scrivi(act, doc, nome)
    }

    // ---------------------------------------------------------------- magazzino

    fun magazzino(act: Activity): File {
        val emi = Studio.emittente()
        val voci = Studio.lista(Studio.F_MAG).sortedBy { Studio.s(it, "nome").lowercase() }
        val doc = PdfDocument()
        val p = Pagina(doc)
        val colR = W - M
        val oggi = LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
        val rs = Studio.s(emi, "ragione_sociale")
        val xNome = M + 4f
        val xCat = M + 168f
        val xQty = M + 288f
        val xUm = M + 296f
        val xSog = M + 366f
        val xVal = M + 446f
        p.alChiudere = { p.piede(rs.ifBlank { "Magazzino" }, "Pag. ${p.n}  —  $oggi") }
        p.nuova()
        p.rett(0f, 0f, W.toFloat(), 60f, BLU)
        p.rett(0f, 60f, W.toFloat(), 63f, ACC)
        p.testo(M, 32f, "MAGAZZINO / SCORTE", 18f, Color.WHITE, true)
        p.testo(M, 50f, "Stampato il $oggi", 8f, GRIGIO_M)
        if (rs.isNotBlank()) p.testo(colR, 38f, rs, 9f, Color.WHITE, true, true)
        var y = 76f
        val hdrH = 18f
        val rowH = 14f
        fun intestazione() {
            p.rett(M, y, colR, y + hdrH, BLU)
            p.testo(xNome, y + 12f, "NOME", 7.5f, Color.WHITE, true)
            p.testo(xCat, y + 12f, "CATEGORIA", 7.5f, Color.WHITE, true)
            p.testo(xQty, y + 12f, "QTÀ", 7.5f, Color.WHITE, true, true)
            p.testo(xUm, y + 12f, "UM", 7.5f, Color.WHITE, true)
            p.testo(xSog, y + 12f, "SOGLIA", 7.5f, Color.WHITE, true, true)
            p.testo(xVal, y + 12f, "VALORE €", 7.5f, Color.WHITE, true, true)
            y += hdrH
        }
        intestazione()
        var totVal = 0.0
        var sotto = 0
        for ((i, m) in voci.withIndex()) {
            if (y + rowH > H - 60f) {
                p.nuova()
                y = M
                intestazione()
            }
            val q = Studio.d(m, "quantita")
            val sg = Studio.d(m, "soglia")
            val val0 = q * Studio.d(m, "prezzo_unit")
            totVal += val0
            val sotSoglia = q <= sg
            if (sotSoglia) sotto++
            p.rett(M, y, colR, y + rowH, if (i % 2 == 0) GRIGIO_L else Color.WHITE)
            p.linea(M, y + rowH, colR, y + rowH, GRIGIO_M, 0.3f)
            p.testo(xNome, y + 10f, tronca(p, Studio.s(m, "nome"), 8f, 160f), 8f)
            p.testo(xCat, y + 10f, tronca(p, Studio.s(m, "categoria"), 8f, 110f), 8f, GRIGIO_D)
            p.testo(xQty, y + 10f, Studio.fmtQ(q), 8f, if (sotSoglia) ROSSO else NERO, sotSoglia, true)
            p.testo(xUm, y + 10f, Studio.s(m, "unita").ifBlank { "pz" }, 8f, GRIGIO_D)
            p.testo(xSog, y + 10f, Studio.fmtQ(sg), 8f, GRIGIO_D, destra = true)
            p.testo(xVal, y + 10f, num(val0), 8f, destra = true)
            y += rowH
        }
        y += 6f
        p.linea(M, y, colR, y, ACC, 1f)
        y += 4f
        if (y + 18f > H - 40f) {
            p.nuova()
            y = M
        }
        p.rett(M, y, colR, y + 18f, BLU)
        p.testo(xNome, y + 12f, "Totale voci: ${voci.size}   Sotto soglia: $sotto", 8f, Color.WHITE, true)
        p.testo(xVal, y + 12f, num(totVal) + " €", 8f, Color.WHITE, true, true)
        p.chiudi()
        return scrivi(act, doc, "magazzino")
    }

    fun condividi(act: Activity, f: File, titolo: String) = PensionePdf.condividi(act, f, titolo)
}
