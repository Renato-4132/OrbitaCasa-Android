package it.orbitacasa.mobile

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

object Avvisi {
    private const val CANALE_ORA = "promemoria_ora"
    private const val CANALE_GIORNO = "promemoria_giorno"
    private const val PREFS = "avvisi"
    private const val K_ATTIVI = "attivi"
    private const val K_RIEPILOGO = "riepilogo"
    private const val K_ORA_RIEP = "ora_riepilogo"
    private const val K_INVIATI = "inviati"
    private const val REQ_ALLARME = 7101
    private const val RITARDO_MAX_MIN = 30L
    private const val ORIZZONTE_GIORNI = 3L

    const val ORA_RIEP_DEFAULT = "08:00"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun attivi(ctx: Context): Boolean = prefs(ctx).getBoolean(K_ATTIVI, true)
    fun riepilogoAttivo(ctx: Context): Boolean = prefs(ctx).getBoolean(K_RIEPILOGO, true)
    fun oraRiepilogo(ctx: Context): String = prefs(ctx).getString(K_ORA_RIEP, ORA_RIEP_DEFAULT) ?: ORA_RIEP_DEFAULT

    fun imposta(ctx: Context, attivi: Boolean, riepilogo: Boolean, ora: String) {
        prefs(ctx).edit().putBoolean(K_ATTIVI, attivi).putBoolean(K_RIEPILOGO, riepilogo).putString(K_ORA_RIEP, ora).apply()
        pianifica(ctx)
    }

    fun permessoNotifiche(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun creaCanali(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CANALE_ORA, "Promemoria all'ora", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Avviso poco prima di un appuntamento o di una scadenza con orario"
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CANALE_GIORNO, "Riepilogo del giorno", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Una volta al giorno: promemoria di oggi, scaduti e in arrivo"
            }
        )
    }

    private class Evento(val quando: LocalDateTime, val chiave: String, val titolo: String, val testo: String, val canale: String)

    private fun eventi(ctx: Context, da: LocalDateTime): List<Evento> {
        val res = ArrayList<Evento>()
        val agenda = Prom.agenda()
        val oggi = da.toLocalDate()
        if (attivi(ctx)) {
            var g = oggi
            while (!g.isAfter(oggi.plusDays(ORIZZONTE_GIORNI))) {
                for (v in agenda) {
                    val h = Prom.oraDi(v) ?: continue
                    if (!Prom.occorreInizio(v, g) || Prom.fatta(v, g)) continue
                    val inizio = LocalDateTime.of(g, h)
                    val avviso = inizio.minusMinutes(Prom.anticipoMin(v).toLong())
                    val chiave = v.optString("id") + "|" + g + "|ora"
                    val titolo = "🔔 " + v.optString("ora") + "  " + v.optString("titolo")
                    res.add(Evento(avviso, chiave, titolo, testoVoce(v), CANALE_ORA))
                }
                g = g.plusDays(1)
            }
        }
        if (riepilogoAttivo(ctx)) {
            val h = try { LocalTime.parse(oraRiepilogo(ctx)) } catch (_: Exception) { LocalTime.of(8, 0) }
            for (k in 0..ORIZZONTE_GIORNI) {
                val g = oggi.plusDays(k)
                res.add(Evento(LocalDateTime.of(g, h), "riepilogo|$g", "Promemoria", "", CANALE_GIORNO))
                res.add(Evento(LocalDateTime.of(g, h), "casa|$g", "CasaCare", "", CANALE_GIORNO))
            }
        }
        return res.sortedBy { it.quando }
    }

    private fun testoVoce(v: org.json.JSONObject): String {
        val p = ArrayList<String>()
        p.add("[" + v.optString("tipo") + "]")
        Prom.importo(v)?.let { p.add(Prom.fmtEur(it)) }
        if (v.optString("luogo").isNotEmpty()) p.add("@ " + v.optString("luogo"))
        val nota = v.optString("note").trim().lines().firstOrNull().orEmpty()
        if (nota.isNotEmpty()) p.add(nota)
        return p.joinToString(" · ")
    }

    private fun testoRiepilogo(c: Prom.Conteggio): String {
        val parti = ArrayList<String>()
        if (c.oggi > 0) parti.add(c.oggi.toString() + " in agenda oggi")
        if (c.scaduti > 0) parti.add(c.scaduti.toString() + (if (c.scaduti == 1) " scaduta" else " scadute"))
        if (c.inArrivo > 0) parti.add(c.inArrivo.toString() + " in arrivo (preavviso)")
        return parti.joinToString(", ")
    }

    private fun inviati(ctx: Context): MutableSet<String> =
        (prefs(ctx).getStringSet(K_INVIATI, null) ?: emptySet<String>()).toMutableSet()

    private fun segnaInviato(ctx: Context, chiave: String) {
        val oggi = LocalDate.now().toString()
        val s = inviati(ctx).filter { it.contains(oggi) || it.contains(LocalDate.now().plusDays(1).toString()) }.toMutableSet()
        s.add(chiave)
        prefs(ctx).edit().putStringSet(K_INVIATI, s).apply()
    }

    private fun mostra(ctx: Context, id: Int, canale: String, titolo: String, testo: String, apriPag: String = "promemoria") {
        if (!permessoNotifiche(ctx)) return
        val apri = PendingIntent.getActivity(
            ctx, if (apriPag == "promemoria") 0 else 1, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra("apri", apriPag),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(ctx, canale)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(titolo)
            .setContentText(testo)
            .setStyle(NotificationCompat.BigTextStyle().bigText(testo))
            .setPriority(if (canale == CANALE_ORA) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(apri)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(id, n)
        } catch (_: SecurityException) {
        }
    }

    fun controlla(ctx: Context) {
        try {
            creaCanali(ctx)
            val adesso = LocalDateTime.now()
            val fatti = inviati(ctx)
            for (e in eventi(ctx, adesso.minusMinutes(RITARDO_MAX_MIN))) {
                if (e.quando.isAfter(adesso)) continue
                if (e.quando.isBefore(adesso.minusMinutes(RITARDO_MAX_MIN)) && e.canale == CANALE_ORA) continue
                if (e.chiave in fatti) continue
                if (e.canale == CANALE_GIORNO) {
                    if (e.quando.toLocalDate() != adesso.toLocalDate()) continue
                    if (e.chiave.startsWith("casa|")) {
                        segnaInviato(ctx, e.chiave)
                        val urg = Casa.voci().map { it to Casa.stato(it, adesso.toLocalDate()) }
                            .filter { it.second.tag == "rosso" }.sortedBy { it.second.giorni ?: 0 }
                        if (urg.isEmpty()) continue
                        val scad = urg.count { (it.second.giorni ?: 0) < 0 }
                        val intro = (if (scad > 0) "$scad scadute · " else "") + (urg.size - scad) + " in scadenza\n"
                        val elenco = urg.take(3).joinToString("\n") { "• " + it.first.nome + " — " + it.second.testo.substringBefore(" (") }
                        mostra(ctx, 7201, CANALE_GIORNO, "CasaCare: " + urg.size + " da fare", intro + elenco, "casacare")
                        continue
                    }
                    val c = Prom.conteggio(adesso.toLocalDate())
                    segnaInviato(ctx, e.chiave)
                    if (c.totale == 0) continue
                    mostra(ctx, 7200, CANALE_GIORNO, "Promemoria: " + c.totale + " da vedere", testoRiepilogo(c))
                } else {
                    segnaInviato(ctx, e.chiave)
                    mostra(ctx, 7300 + (e.chiave.hashCode() and 0xFFFF), CANALE_ORA, e.titolo, e.testo.ifEmpty { "Tra poco" })
                }
            }
        } catch (_: Exception) {
        } finally {
            pianifica(ctx)
        }
    }

    private fun intentAllarme(ctx: Context): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, REQ_ALLARME, Intent(ctx, AvvisiReceiver::class.java).setAction("it.orbitacasa.mobile.AVVISI"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    fun pianifica(ctx: Context) {
        try {
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val pi = intentAllarme(ctx)
            am.cancel(pi)
            val adesso = LocalDateTime.now()
            val fatti = inviati(ctx)
            val prossimo = eventi(ctx, adesso).firstOrNull { it.quando.isAfter(adesso) && it.chiave !in fatti }
            val limite = adesso.plusHours(12)
            val quando = listOfNotNull(prossimo?.quando, limite).minOrNull() ?: limite
            val ms = quando.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            if (Build.VERSION.SDK_INT >= 23) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi)
            else am.set(AlarmManager.RTC_WAKEUP, ms, pi)
        } catch (_: Exception) {
        }
    }

    fun iniziaDa(ctx: Context) {
        Prom.init(File(ctx.filesDir, "db"))
        Casa.init(File(ctx.filesDir, "db"))
        Park.init(File(ctx.filesDir, "db"))
        Immo.init(File(ctx.filesDir, "db"))
        Pet.init(File(ctx.filesDir, "db"))
        creaCanali(ctx)
    }
}

class AvvisiReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        Avvisi.iniziaDa(context)
        Avvisi.controlla(context)
    }
}

class AvviReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        Avvisi.iniziaDa(context)
        Avvisi.controlla(context)
    }
}
