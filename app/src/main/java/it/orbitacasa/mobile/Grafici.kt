package it.orbitacasa.mobile

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import java.time.LocalDate
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

object Stat {
    class Serie(val lab: List<String>, val e: List<Double?>, val u: List<Double?>, val s: List<Double?>) {
        val totE get() = e.sumOf { it ?: 0.0 }
        val totU get() = u.sumOf { it ?: 0.0 }
        fun saldoFinale(progressivo: Boolean): Double =
            if (progressivo) s.lastOrNull { it != null } ?: 0.0 else s.sumOf { it ?: 0.0 }
    }

    class RigaCat(val nome: String, val v: Double, val budget: Double)

    private val MESI = listOf("Gen", "Feb", "Mar", "Apr", "Mag", "Giu", "Lug", "Ago", "Set", "Ott", "Nov", "Dic")

    fun anni(voci: List<Voce>, extra: Int): List<Int> =
        (voci.map { it.data.year } + LocalDate.now().year + extra).toSortedSet().toList()
        
    fun mensile(voci: List<Voce>, anno: Int): Serie {
        val oggi = LocalDate.now()
        val limite = if (anno == oggi.year) oggi.monthValue else 12
        val e = DoubleArray(12)
        val u = DoubleArray(12)
        for (v in voci) {
            if (v.data.year != anno) continue
            val i = v.data.monthValue - 1
            if (v.tipo == "Entrata") e[i] += v.importo else u[i] += v.importo
        }
        var acc = 0.0
        val s = (0 until 12).map { i ->
            acc += e[i] - u[i]
            if (i < limite) acc else null
        }
        return Serie(
            MESI,
            (0 until 12).map { if (it < limite) e[it] else null },
            (0 until 12).map { if (it < limite) u[it] else null },
            s,
        )
    }

    fun storico(voci: List<Voce>): Serie {
        val per = java.util.TreeMap<Int, DoubleArray>()
        for (v in voci) {
            val a = per.getOrPut(v.data.year) { DoubleArray(2) }
            if (v.tipo == "Entrata") a[0] += v.importo else a[1] += v.importo
        }
        return Serie(
            per.keys.map { it.toString() },
            per.values.map { it[0] },
            per.values.map { it[1] },
            per.values.map { it[0] - it[1] },
        )
    }

    fun categorie(voci: List<Voce>, anno: Int?): List<RigaCat> {
        val tot = HashMap<String, Double>()
        for (v in voci) {
            if (v.tipo == "Entrata") continue
            if (anno != null && v.data.year != anno) continue
            val c = v.categoria.ifBlank { "Senza categoria" }
            tot[c] = (tot[c] ?: 0.0) + v.importo
        }
        return tot.filter { it.value > 0 }.map { (c, x) ->
            val b = if (anno == null) 0.0 else Repo.budgetAnnuo(c).takeIf { it > 0 } ?: (Repo.budgetMensile(c) * 12)
            RigaCat(c, x, b)
        }.sortedByDescending { it.v }
    }
}

class GraficoView(ctx: Context) : View(ctx) {
    enum class Modo { LINEA, BARRE_SEGNO, BARRE_DOPPIE }

    private var modo = Modo.LINEA
    private var etichette: List<String> = emptyList()
    private var a: List<Double?> = emptyList()
    private var b: List<Double?> = emptyList()
    private var colA = 0
    private var colB = 0
    private var colTesto = 0
    private var colGriglia = 0
    private var colBase = 0
    private var colPunto = 0
    private var selezione = -1
    private var plotSx = 0f
    private var plotLarg = 1f

    var alSelezionare: ((Int) -> Unit)? = null

    private val dp = resources.displayMetrics.density
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    fun imposta(
        modo: Modo, etichette: List<String>, a: List<Double?>, b: List<Double?>,
        colA: Int, colB: Int, colTesto: Int, colGriglia: Int, colBase: Int, colPunto: Int,
    ) {
        this.modo = modo
        this.etichette = etichette
        this.a = a
        this.b = b
        this.colA = colA
        this.colB = colB
        this.colTesto = colTesto
        this.colGriglia = colGriglia
        this.colBase = colBase
        this.colPunto = colPunto
        selezione = -1
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val h = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED) (240 * dp).toInt()
        else MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), h)
    }

    private fun asse(v: Double): String {
        val x = abs(v)
        val t = if (x >= 1000) {
            String.format(Locale.ITALY, "%.1f", x / 1000).removeSuffix(",0") + "k"
        } else {
            String.format(Locale.ITALY, "%.0f", x)
        }
        return (if (v < 0) "-" else "") + "€ " + t
    }

    private fun scala(minV: Double, maxV: Double): Triple<Double, Double, Double> {
        val range = if (maxV - minV > 0) maxV - minV else 1.0
        val raw = range / 5
        val mag = 10.0.pow(floor(log10(raw)))
        val norm = raw / mag
        val step = (if (norm <= 1) 1.0 else if (norm <= 2) 2.0 else if (norm <= 5) 5.0 else 10.0) * mag
        return Triple(floor(minV / step) * step, ceil(maxV / step) * step, step)
    }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        val n = etichette.size
        if (n == 0) return
        val valori = (a + (if (modo == Modo.BARRE_DOPPIE) b else emptyList())).filterNotNull()
        val minV = min(0.0, valori.minOrNull() ?: 0.0)
        val maxV = max(0.0, valori.maxOrNull() ?: 0.0)
        val (lo, hi, step) = scala(minV, maxV)

        p.textSize = 11 * dp
        val tacche = ArrayList<Double>()
        var t = lo
        while (t <= hi + step / 2) { tacche.add(t); t += step }
        val sinistra = (tacche.maxOf { p.measureText(asse(it)) } + 8 * dp)
        val alto = 8 * dp
        val basso = height - 22 * dp
        val destra = width - 6 * dp
        val larg = destra - sinistra
        val alt = basso - alto
        plotSx = sinistra
        plotLarg = larg
        fun yPx(v: Double) = (alto + (hi - v) / (hi - lo) * alt).toFloat()

        p.style = Paint.Style.FILL
        p.textAlign = Paint.Align.RIGHT
        for (v in tacche) {
            val y = yPx(v)
            p.color = if (abs(v) < step / 1000) colBase else colGriglia
            p.strokeWidth = if (abs(v) < step / 1000) 1.5f * dp else 1f * dp
            c.drawLine(sinistra.toFloat(), y, destra, y, p)
            p.color = colTesto
            c.drawText(asse(v), sinistra.toFloat() - 5 * dp, y + 4 * dp, p)
        }

        val slot = larg / n
        val y0 = yPx(0.0)

        if (selezione in 0 until n) {
            p.color = colGriglia
            c.drawRect((sinistra + slot * selezione).toFloat(), alto.toFloat(), (sinistra + slot * (selezione + 1)).toFloat(), basso.toFloat(), p)
        }

        fun barra(x0: Double, x1: Double, v: Double, col: Int) {
            p.color = col
            val y = yPx(v)
            val r = RectF(x0.toFloat(), min(y, y0), x1.toFloat(), max(y, y0))
            if (r.height() < 1f) r.bottom = r.top + 1f
            c.drawRoundRect(r, 3 * dp, 3 * dp, p)
        }

        when (modo) {
            Modo.BARRE_SEGNO -> {
                val w = min(slot * 0.7, 28.0 * dp)
                for (i in 0 until n) {
                    val v = a.getOrNull(i) ?: continue
                    val cx = sinistra + slot * (i + 0.5)
                    barra(cx - w / 2, cx + w / 2, v, if (v >= 0) colA else colB)
                }
            }
            Modo.BARRE_DOPPIE -> {
                val w = min(slot * 0.38, (if (n > 6) 12.0 else 24.0) * dp)
                for (i in 0 until n) {
                    val cx = sinistra + slot * (i + 0.5)
                    a.getOrNull(i)?.let { barra(cx - w - 1 * dp, cx - 1 * dp, it, colA) }
                    b.getOrNull(i)?.let { barra(cx + 1 * dp, cx + w + 1 * dp, it, colB) }
                }
            }
            Modo.LINEA -> {
                val path = Path()
                var aperto = false
                p.style = Paint.Style.STROKE
                p.strokeWidth = 2 * dp
                p.color = colA
                for (i in 0 until n) {
                    val v = a.getOrNull(i)
                    if (v == null) { aperto = false; continue }
                    val x = (sinistra + slot * (i + 0.5)).toFloat()
                    if (!aperto) { path.moveTo(x, yPx(v)); aperto = true } else path.lineTo(x, yPx(v))
                }
                c.drawPath(path, p)
                p.style = Paint.Style.FILL
                for (i in 0 until n) {
                    val v = a.getOrNull(i) ?: continue
                    val x = (sinistra + slot * (i + 0.5)).toFloat()
                    p.color = colPunto
                    c.drawCircle(x, yPx(v), 4.5f * dp, p)
                    p.color = colA
                    c.drawCircle(x, yPx(v), 3f * dp, p)
                }
            }
        }

        p.style = Paint.Style.FILL
        p.color = colTesto
        p.textAlign = Paint.Align.CENTER
        val largMax = etichette.maxOf { p.measureText(it) } + 6 * dp
        val salto = max(1, ceil(largMax / slot).toInt())
        for (i in 0 until n step salto) {
            c.drawText(etichette[i], (sinistra + slot * (i + 0.5)).toFloat(), height - 6 * dp, p)
        }
    }

    private fun indiceA(x: Float): Int {
        val n = etichette.size
        return ((x - plotSx) / plotLarg * n).toInt().coerceIn(0, n - 1)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (etichette.isEmpty()) return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                val i = indiceA(e.x)
                if (i != selezione) {
                    selezione = i
                    invalidate()
                    alSelezionare?.invoke(i)
                }
            }
            MotionEvent.ACTION_UP -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                performClick()
            }
            MotionEvent.ACTION_CANCEL -> parent?.requestDisallowInterceptTouchEvent(false)
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()
}
