package it.orbitacasa.mobile

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.util.Locale

object Calc {
    fun valuta(espr: String): Double? {
        var s = espr.replace(" ", "").replace("€", "").replace('×', '*').replace('÷', '/').replace(',', '.')
        while (s.isNotEmpty() && s.last() in "+-*/") s = s.dropLast(1)
        if (s.isEmpty()) return null
        var i = 0
        fun numero(): Double? {
            val st = i
            while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
            return s.substring(st, i).toDoubleOrNull()
        }
        fun termine(): Double? {
            var a = numero() ?: return null
            while (i < s.length && (s[i] == '*' || s[i] == '/')) {
                val op = s[i++]
                val b = numero() ?: return null
                if (op == '/' && b == 0.0) return null
                a = if (op == '*') a * b else a / b
            }
            return a
        }
        var neg = false
        if (s[0] == '-') { neg = true; i++ }
        var tot = termine() ?: return null
        if (neg) tot = -tot
        while (i < s.length) {
            val op = s[i++]
            if (op != '+' && op != '-') return null
            val b = termine() ?: return null
            tot = if (op == '+') tot + b else tot - b
        }
        return Math.round(tot * 100.0) / 100.0
    }

    private fun fmt(v: Double) = String.format(Locale.ITALY, "%.2f", v)

    fun collega(ctx: Context, et: TextInputEditText) {
        var p = et.parent
        while (p != null && p !is TextInputLayout) p = p.parent
        val til = p as? TextInputLayout ?: return
        til.endIconMode = TextInputLayout.END_ICON_CUSTOM
        til.endIconDrawable = ContextCompat.getDrawable(ctx, R.drawable.ic_calc)
        til.endIconContentDescription = "Calcolatrice"
        til.setEndIconOnClickListener { apri(ctx, et) }
    }

    private fun apri(ctx: Context, et: TextInputEditText) {
        val dp = ctx.resources.displayMetrics.density
        val sb = StringBuilder()
        val iniz = num(et.text?.toString() ?: "")
        if (iniz != null && iniz > 0) sb.append(fmt(iniz))

        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20 * dp).toInt(), (4 * dp).toInt(), (20 * dp).toInt(), (8 * dp).toInt())
        }
        val tvEspr = TextView(ctx).apply {
            textSize = 16f; gravity = Gravity.END
            setTextColor(ContextCompat.getColor(ctx, R.color.oc_text_mid))
        }
        val tvRis = TextView(ctx).apply {
            textSize = 32f; gravity = Gravity.END; setTypeface(typeface, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(ctx, R.color.oc_text))
        }
        col.addView(tvEspr)
        col.addView(tvRis)

        fun aggiorna() {
            tvEspr.text = if (sb.isEmpty()) " " else sb.toString()
            val v = valuta(sb.toString())
            tvRis.text = if (v != null) fmt(v) else "0,00"
        }

        fun tasto(t: String) {
            val ultimo = sb.lastOrNull()
            val isOp = t in listOf("+", "-", "×", "÷")
            when {
                t == "C" -> sb.setLength(0)
                t == "⌫" -> if (sb.isNotEmpty()) sb.setLength(sb.length - 1)
                isOp -> {
                    if (sb.isEmpty()) { if (t == "-") sb.append(t) }
                    else if (ultimo in listOf('+', '-', '×', '÷')) {
                        sb.setLength(sb.length - 1); sb.append(t)
                    } else sb.append(t)
                }
                t == "," -> {
                    val ult = sb.split('+', '-', '×', '÷').last()
                    if (!ult.contains(',')) sb.append(if (ult.isEmpty()) "0," else ",")
                }
                else -> sb.append(t)
            }
            aggiorna()
        }

        val griglia = GridLayout(ctx).apply { columnCount = 4 }
        val tasti = listOf(
            "C", "⌫", "÷", "×",
            "7", "8", "9", "-",
            "4", "5", "6", "+",
            "1", "2", "3", ",",
        )
        for (t in tasti) {
            val b = MaterialButton(ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle)
            b.text = t
            b.textSize = 20f
            b.insetTop = 0; b.insetBottom = 0
            b.setPadding(0, 0, 0, 0)
            b.setTextColor(ContextCompat.getColor(ctx, R.color.oc_text))
            val lp = GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED, 1f),
                GridLayout.spec(GridLayout.UNDEFINED, 1f)
            )
            lp.width = 0; lp.height = (54 * dp).toInt()
            lp.setMargins((2 * dp).toInt(), (2 * dp).toInt(), (2 * dp).toInt(), (2 * dp).toInt())
            b.layoutParams = lp
            b.setOnClickListener { tasto(t) }
            griglia.addView(b)
        }
        for ((t, span) in listOf("0" to 2, "00" to 2)) {
            val b = MaterialButton(ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle)
            b.text = t; b.textSize = 20f
            b.insetTop = 0; b.insetBottom = 0
            b.setPadding(0, 0, 0, 0)
            b.setTextColor(ContextCompat.getColor(ctx, R.color.oc_text))
            val lp = GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED, 1f),
                GridLayout.spec(GridLayout.UNDEFINED, span, 1f)
            )
            lp.width = 0; lp.height = (54 * dp).toInt()
            lp.setMargins((2 * dp).toInt(), (2 * dp).toInt(), (2 * dp).toInt(), (2 * dp).toInt())
            b.layoutParams = lp
            b.setOnClickListener { tasto(t) }
            griglia.addView(b)
        }
        col.addView(griglia)
        aggiorna()

        val dlg = OcBuilder(ctx)
            .setTitle("🧮 Calcolatrice")
            .setView(col)
            .setNegativeButton("Annulla", null)
            .setPositiveButton("Usa totale", null)
            .create()
        dlg.show()
        dlg.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val v = valuta(sb.toString())
            if (v == null || v <= 0.0) {
                tvRis.setTextColor(ContextCompat.getColor(ctx, R.color.oc_red))
                return@setOnClickListener
            }
            et.setText(fmt(v))
            et.setSelection(et.text?.length ?: 0)
            dlg.dismiss()
        }
    }
}
