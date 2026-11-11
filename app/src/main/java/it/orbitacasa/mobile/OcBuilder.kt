package it.orbitacasa.mobile

import android.content.Context
import android.text.TextUtils
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class OcBuilder(private val ctx: Context) : MaterialAlertDialogBuilder(ctx) {
    override fun create(): AlertDialog {
        val dlg = super.create()
        dlg.setOnShowListener { tasti(dlg) }
        return dlg
    }

    private fun tasti(dlg: AlertDialog) {
        try {
            val primo: View = listOf(
                AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL
            ).firstNotNullOfOrNull { dlg.getButton(it) } ?: return
            var p = primo.parent
            while (p != null && p.javaClass.simpleName != "ButtonBarLayout") p = p.parent
            if (p !is LinearLayout) return
            try {
                p.javaClass.getMethod("setAllowStacking", Boolean::class.javaPrimitiveType).invoke(p, false)
            } catch (_: Exception) {
            }
            val dp = ctx.resources.displayMetrics.density
            val h = (6 * dp).toInt()
            val m = (3 * dp).toInt()
            val quanti = (0 until p.childCount).count { val c = p.getChildAt(it); c is Button && c.visibility != View.GONE }
            for (i in 0 until p.childCount) {
                val c = p.getChildAt(i)
                if (c is Button) {
                    if (c.visibility == View.GONE) continue
                    val lp = (c.layoutParams as? LinearLayout.LayoutParams) ?: LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT)
                    if (quanti == 1) {
                        lp.width = LinearLayout.LayoutParams.WRAP_CONTENT
                        lp.weight = 0f
                        p.gravity = android.view.Gravity.END
                    } else {
                        lp.width = 0
                        lp.weight = 1f
                    }
                    lp.marginStart = m
                    lp.marginEnd = m
                    c.layoutParams = lp
                    c.setPadding(h, 0, h, 0)
                    c.minWidth = 0
                    c.minimumWidth = 0
                    c.maxLines = 1
                    c.ellipsize = TextUtils.TruncateAt.END
                    c.textSize = 14f
                } else {
                    c.visibility = View.GONE
                }
            }
            p.requestLayout()
        } catch (_: Exception) {
        }
    }
}
