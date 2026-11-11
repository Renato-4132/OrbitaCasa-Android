package it.orbitacasa.mobile

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Calendar
import java.util.UUID
import kotlin.concurrent.thread
import kotlin.math.abs

data class RegolaNotif(
    val id: String = UUID.randomUUID().toString(),
    val pkg: String = TUTTE,
    val appLabel: String = "Tutte le app",
    val include: String = "",
    val exclude: String = "",
    val giorni: Int = 127,
    val usaFascia: Boolean = false,
    val daMin: Int = 8 * 60,
    val aMin: Int = 20 * 60,
    val conto: String = "",
    val metodo: String = "",
    val auto: String = "",
    val tag: String = "",
    val attiva: Boolean = true,
) {
    fun tags(): List<String> =
        listOf(tag.trim().trimStart('#').replace(" ", "")).filter { it.isNotEmpty() }

    fun corrisponde(minuscolo: String): Boolean {
        val inc = parole(include)
        val exc = parole(exclude)
        if (inc.isNotEmpty() && inc.none { minuscolo.contains(it) }) return false
        if (exc.any { minuscolo.contains(it) }) return false
        return true
    }

    fun attivaAlle(cal: Calendar): Boolean {
        val bit = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7
        if (giorni and (1 shl bit) == 0) return false
        if (!usaFascia) return true
        val ora = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        return if (daMin <= aMin) ora in daMin..aMin else (ora >= daMin || ora <= aMin)
    }

    fun riassunto(): String {
        val p = ArrayList<String>()
        if (include.isNotBlank()) p += "contiene: ${include.trim()}"
        if (exclude.isNotBlank()) p += "esclude: ${exclude.trim()}"
        if (giorni != 127) p += NOMI_GIORNI.filterIndexed { i, _ -> giorni and (1 shl i) != 0 }.joinToString(" ")
        if (usaFascia) p += "${fmtOra(daMin)}–${fmtOra(aMin)}"
        if (conto.isNotBlank()) p += "conto: ${conto.trim()}"
        if (metodo.isNotBlank()) p += "metodo: ${metodo.trim()}"
        when (auto) {
            Notif.AUTO_REGOLE -> p += "registra in automatico (regole)"
            Notif.AUTO_TUTTO -> p += "registra in automatico (sempre)"
        }
        if (tags().isNotEmpty()) p += "tag: " + tags().joinToString(" ") { "#$it" }
        return if (p.isEmpty()) "Tutte le notifiche" else p.joinToString(" · ")
    }

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("pkg", pkg)
        .put("appLabel", appLabel)
        .put("include", include)
        .put("exclude", exclude)
        .put("giorni", giorni)
        .put("usaFascia", usaFascia)
        .put("daMin", daMin)
        .put("aMin", aMin)
        .put("conto", conto)
        .put("metodo", metodo)
        .put("auto", auto)
        .put("tag", tag)
        .put("attiva", attiva)

    companion object {
        const val TUTTE = "*"
        val NOMI_GIORNI = listOf("Lun", "Mar", "Mer", "Gio", "Ven", "Sab", "Dom")

        fun parole(s: String): List<String> =
            s.split(',', '\n').map { it.trim().lowercase() }.filter { it.isNotEmpty() }

        fun fmtOra(min: Int): String = "%02d:%02d".format(min / 60, min % 60)

        fun fromJson(o: JSONObject) = RegolaNotif(
            id = o.optString("id", UUID.randomUUID().toString()),
            pkg = o.optString("pkg", TUTTE),
            appLabel = o.optString("appLabel", "Tutte le app"),
            include = o.optString("include", ""),
            exclude = o.optString("exclude", ""),
            giorni = o.optInt("giorni", 127),
            usaFascia = o.optBoolean("usaFascia", false),
            daMin = o.optInt("daMin", 8 * 60),
            aMin = o.optInt("aMin", 20 * 60),
            conto = o.optString("conto", ""),
            metodo = o.optString("metodo", ""),
            auto = o.optString("auto", ""),
            tag = o.optString("tag", ""),
            attiva = o.optBoolean("attiva", true),
        )
    }
}

class Proposta(
    val id: String,
    val quando: String,
    val app: String,
    val titolo: String,
    val testo: String,
    val importo: Double,
    val tipo: String,
    val desc: String,
    val fonte: String,
    var stato: String,
    val conto: String,
    val metodo: String,
    val auto: String,
    val tags: List<String>,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("quando", quando)
        .put("app", app)
        .put("title", titolo)
        .put("text", testo)
        .put("importo", importo)
        .put("tipo", tipo)
        .put("desc", desc)
        .put("fonte", fonte)
        .put("stato", stato)
        .put("conto", conto)
        .put("metodo", metodo)
        .put("auto", auto)
        .put("tags", JSONArray(tags))

    companion object {
        fun fromJson(o: JSONObject): Proposta {
            val t = ArrayList<String>()
            o.optJSONArray("tags")?.let { a -> for (i in 0 until a.length()) t.add(a.optString(i)) }
            return Proposta(
                id = o.optString("id"),
                quando = o.optString("quando"),
                app = o.optString("app"),
                titolo = o.optString("title"),
                testo = o.optString("text"),
                importo = o.optDouble("importo", 0.0),
                tipo = o.optString("tipo", "Uscita"),
                desc = o.optString("desc"),
                fonte = o.optString("fonte", "regole"),
                stato = o.optString("stato", "attesa"),
                conto = o.optString("conto"),
                metodo = o.optString("metodo"),
                auto = o.optString("auto"),
                tags = t,
            )
        }
    }
}

object Notif {
    const val AUTO_NO = ""
    const val AUTO_REGOLE = "regole"
    const val AUTO_TUTTO = "tutto"
    const val MAX_CODA = 500

    private const val FILE = "android_notifiche.json"
    private const val MEMORIA = "memoria_categorie.json"
    private const val PREFS = "notifiche"
    private const val CANALE = "notifiche_spese"

    class Ricordo(val categoria: String, val conto: String, val metodo: String, val tag: String)

    private val elencoRegole = ArrayList<RegolaNotif>()
    private val coda = ArrayList<Proposta>()
    private var memApp = JSONObject()
    private var dir: File? = null
    private val main = Handler(Looper.getMainLooper())

    var osservatore: (() -> Unit)? = null

    @Synchronized
    fun init(d: File) {
        dir = d
        elencoRegole.clear()
        coda.clear()
        memApp = JSONObject()
        try {
            val f = File(d, FILE)
            if (f.exists()) {
                val o = JSONObject(f.readText(Charsets.UTF_8))
                o.optJSONArray("regole")?.let { a ->
                    for (i in 0 until a.length()) a.optJSONObject(i)?.let { elencoRegole.add(RegolaNotif.fromJson(it)) }
                }
                o.optJSONArray("coda")?.let { a ->
                    for (i in 0 until a.length()) a.optJSONObject(i)?.let { coda.add(Proposta.fromJson(it)) }
                }
                o.optJSONObject("app")?.let { memApp = it }
            }
        } catch (_: Exception) {
        }
    }

    private fun salva() {
        val d = dir ?: return
        if (coda.size > MAX_CODA) {
            val resto = coda.takeLast(MAX_CODA)
            coda.clear()
            coda.addAll(resto)
        }
        val o = JSONObject()
        o.put("regole", JSONArray().also { a -> elencoRegole.forEach { a.put(it.toJson()) } })
        o.put("coda", JSONArray().also { a -> coda.forEach { a.put(it.toJson()) } })
        o.put("app", memApp)
        try {
            val f = File(d, FILE)
            val tmp = File(d, "$FILE.tmp")
            tmp.writeText(o.toString(), Charsets.UTF_8)
            Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } catch (_: Exception) {
        }
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun attive(ctx: Context): Boolean = prefs(ctx).getBoolean("attive", false)

    fun geminiAttivo(ctx: Context): Boolean = prefs(ctx).getBoolean("gemini", false)

    fun impostaAttive(ctx: Context, v: Boolean) {
        prefs(ctx).edit().putBoolean("attive", v).apply()
        applicaStato(ctx)
    }

    fun impostaGemini(ctx: Context, v: Boolean) {
        prefs(ctx).edit().putBoolean("gemini", v).apply()
    }

    fun accessoConcesso(ctx: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)

    fun applicaStato(ctx: Context) {
        try {
            val cn = ComponentName(ctx, NotifListener::class.java)
            val voluto = if (attive(ctx)) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            val pm = ctx.packageManager
            if (pm.getComponentEnabledSetting(cn) != voluto) {
                pm.setComponentEnabledSetting(cn, voluto, PackageManager.DONT_KILL_APP)
                if (voluto == PackageManager.COMPONENT_ENABLED_STATE_ENABLED && accessoConcesso(ctx)) {
                    NotificationListenerService.requestRebind(cn)
                }
            }
        } catch (_: Exception) {
        }
    }

    @Synchronized
    fun regoleLista(): List<RegolaNotif> = ArrayList(elencoRegole)

    @Synchronized
    fun salvaRegola(r: RegolaNotif) {
        val i = elencoRegole.indexOfFirst { it.id == r.id }
        if (i >= 0) elencoRegole[i] = r else elencoRegole.add(r)
        salva()
    }

    @Synchronized
    fun eliminaRegola(id: String) {
        elencoRegole.removeAll { it.id == id }
        salva()
    }

    @Synchronized
    fun primaRegola(pkg: String, titolo: String, testo: String, cal: Calendar): RegolaNotif? {
        val pagliaio = "$titolo $testo".lowercase()
        return elencoRegole
            .filter { it.attiva }
            .sortedBy { if (it.pkg == RegolaNotif.TUTTE) 1 else 0 }
            .firstOrNull { (it.pkg == RegolaNotif.TUTTE || it.pkg == pkg) && it.corrisponde(pagliaio) && it.attivaAlle(cal) }
    }

    @Synchronized
    fun inAttesa(): List<Proposta> = coda.filter { it.stato == "attesa" }.reversed()

    @Synchronized
    fun nAttesa(): Int = coda.count { it.stato == "attesa" }

    @Synchronized
    private fun imposta(id: String, stato: String) {
        coda.firstOrNull { it.id == id }?.stato = stato
        salva()
    }

    fun scarta(id: String) = imposta(id, "scartata")

    @Synchronized
    fun scartaTutte() {
        coda.filter { it.stato == "attesa" }.forEach { it.stato = "scartata" }
        salva()
    }

    @Synchronized
    private fun aggiungi(p: Proposta): Boolean {
        if (coda.any { it.id == p.id }) return false
        coda.add(p)
        salva()
        return true
    }

    @Synchronized
    private fun esiste(id: String): Boolean = coda.any { it.id == id }

    @Synchronized
    fun regolaApp(app: String): Ricordo {
        val o = memApp.optJSONObject(app.trim().lowercase())
        return Ricordo(
            o?.optString("categoria", "") ?: "",
            o?.optString("conto", "") ?: "",
            o?.optString("metodo", "") ?: "",
            o?.optString("tag", "") ?: "",
        )
    }

    @Synchronized
    private fun ricordaApp(app: String, r: Ricordo) {
        val k = app.trim().lowercase()
        if (k.isEmpty()) return
        memApp.put(
            k,
            JSONObject().put("categoria", r.categoria).put("conto", r.conto).put("metodo", r.metodo).put("tag", r.tag)
        )
        salva()
    }

    private fun memoriaDescrizioni(): JSONObject {
        val d = dir ?: return JSONObject()
        return try {
            JSONObject(File(d, MEMORIA).readText(Charsets.UTF_8))
        } catch (_: Exception) {
            JSONObject()
        }
    }

    private fun ricordaDescrizione(desc: String, cat: String) {
        val d = dir ?: return
        try {
            val f = File(d, MEMORIA)
            val o = if (f.exists()) JSONObject(f.readText(Charsets.UTF_8)) else JSONObject()
            o.put(desc.uppercase(), cat)
            val tmp = File(d, "$MEMORIA.tmp")
            tmp.writeText(o.toString(2), Charsets.UTF_8)
            Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } catch (_: Exception) {
        }
    }

    private fun categoriaDaTesto(p: Proposta): String {
        val testo = "${p.desc} ${p.titolo} ${p.testo}".lowercase()
        var migliore = ""
        for (c in Repo.categorie()) {
            val nome = c.trim()
            if (nome.length < 3 || nome.equals("generica", true)) continue
            val rx = Regex("(?<![\\p{L}\\p{N}_])" + Regex.escape(nome.lowercase()) + "(?![\\p{L}\\p{N}_])")
            if (nome.length > migliore.length && rx.containsMatchIn(testo)) migliore = nome
        }
        return migliore
    }

    fun categoriaSuggerita(p: Proposta): String {
        val mem = memoriaDescrizioni().optString(p.desc.trim().uppercase(), "")
        if (mem.isNotEmpty()) return mem
        val dal = categoriaDaTesto(p)
        if (dal.isNotEmpty()) return dal
        return regolaApp(p.app).categoria
    }

    fun contoValido(nome: String): String {
        val n = nome.trim().lowercase()
        if (n.isEmpty()) return ""
        return Repo.conti().firstOrNull { it.nome.lowercase() == n }?.nome ?: ""
    }

    fun metodoValido(nome: String): String {
        val n = nome.trim().lowercase()
        if (n.isEmpty()) return ""
        return Repo.METODI.firstOrNull { it.first.lowercase() == n }?.first ?: ""
    }

    fun normalizzaTag(s: String): List<String> =
        s.replace(",", " ").trim().split(Regex("\\s+"))
            .map { it.trimStart('#') }
            .filter { it.isNotEmpty() }
            .map { "#$it" }

    fun contoPredefinito(p: Proposta): String =
        contoValido(p.conto).ifEmpty { contoValido(regolaApp(p.app).conto) }

    fun metodoPredefinito(p: Proposta): String =
        metodoValido(p.metodo).ifEmpty { metodoValido(regolaApp(p.app).metodo) }

    fun tagPredefiniti(p: Proposta): List<String> =
        if (p.tags.isNotEmpty()) p.tags.map { "#" + it.trimStart('#') } else normalizzaTag(regolaApp(p.app).tag)

    fun dataDi(p: Proposta): LocalDate = try {
        LocalDateTime.parse(p.quando).toLocalDate()
    } catch (_: Exception) {
        LocalDate.now()
    }

    fun registra(
        p: Proposta, cat: String, desc: String, imp: Double, tipo: String,
        conto: String, metodo: String, tags: List<String>, ricordaDesc: Boolean,
    ): Boolean {
        val data = dataDi(p)
        val d = desc.trim().take(60)
        val doppio = Repo.voci().any { it.data == data && it.descrizione == d && abs(it.importo - imp) < 0.01 }
        if (doppio) {
            imposta(p.id, "scartata")
            return false
        }
        Repo.salvaVoce(null, data, cat, d, imp, tipo, metodo, tags, conto)
        ricordaApp(p.app, Ricordo(cat, conto, metodo, tags.joinToString(" ")))
        if (ricordaDesc) ricordaDescrizione(d, cat)
        imposta(p.id, "confermata")
        return true
    }

    private fun autoConsentita(p: Proposta): Boolean =
        p.auto == AUTO_TUTTO || (p.auto == AUTO_REGOLE && p.fonte == "regole")

    fun registraAuto(p: Proposta): Boolean {
        val cat = categoriaSuggerita(p)
        if (cat.isEmpty() || cat.equals("Generica", true) || cat !in Repo.categorie()) return false
        return registra(
            p, cat, p.desc, p.importo, p.tipo,
            contoPredefinito(p), metodoPredefinito(p), tagPredefiniti(p), false,
        )
    }

    private fun estraiGemini(ctx: Context, titolo: String, testo: String): NotifEstrai.Estratto? {
        if (Gemini.chiave(ctx).isEmpty()) return null
        return try {
            val prompt = "Questa è la notifica di uno smartphone. Dì se descrive un pagamento o un incasso " +
                "di denaro. Rispondi SOLO con JSON: " +
                "{\"movimento\": true/false, \"importo\": float, \"direzione\": \"Entrata/Uscita\", " +
                "\"descrizione\": \"esercente o motivo, max 40 caratteri\"}. " +
                "Se non è un movimento di denaro, movimento=false.\n\nTitolo: $titolo\nTesto: $testo"
            val d = Gemini.analizzaTesto(ctx, prompt)
            if (!d.optBoolean("movimento", false)) return null
            val imp = d.optDouble("importo", 0.0)
            if (imp.isNaN() || imp <= 0.0) return null
            val tipo = if (d.optString("direzione", "").lowercase().startsWith("entr")) "Entrata" else "Uscita"
            val desc = d.optString("descrizione", "").ifBlank { titolo.ifEmpty { testo } }.take(50)
            NotifEstrai.Estratto(Math.round(imp * 100.0) / 100.0, tipo, desc, "gemini")
        } catch (_: Exception) {
            null
        }
    }

    fun estrai(ctx: Context, titolo: String, testo: String, usaGemini: Boolean): NotifEstrai.Estratto? {
        NotifEstrai.conRegole(titolo, testo)?.let { return it }
        if (usaGemini && NotifEstrai.haSoldi("$titolo\n$testo")) return estraiGemini(ctx, titolo, testo)
        return null
    }

    private fun sha(s: String): String =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }.take(16)

    fun ricevi(ctx: Context, pkg: String, app: String, ts: Long, titolo: String, testo: String, r: RegolaNotif) {
        val id = sha("$pkg|$ts|$titolo|$testo")
        if (esiste(id)) return
        val dati = estrai(ctx, titolo, testo, geminiAttivo(ctx)) ?: return
        val quando = LocalDateTime.ofInstant(Instant.ofEpochMilli(ts), ZoneId.systemDefault()).withNano(0).toString()
        val p = Proposta(
            id, quando, app, titolo, testo, dati.importo, dati.tipo, dati.desc, dati.fonte,
            "attesa", r.conto, r.metodo, r.auto, r.tags(),
        )
        main.post {
            if (aggiungi(p)) {
                if (autoConsentita(p)) {
                    try {
                        registraAuto(p)
                    } catch (_: Exception) {
                    }
                }
                val importo = String.format(java.util.Locale.ITALY, "%.2f €", p.importo)
                when (p.stato) {
                    "confermata" -> avvisa(ctx, "Spesa registrata", p.desc + " · " + importo, p.id)
                    "attesa" -> avvisa(ctx, "Spesa da confermare", p.desc + " · " + importo, p.id)
                }
                osservatore?.invoke()
            }
        }
    }

    private fun avvisa(ctx: Context, titolo: String, testo: String, chiave: String) {
        try {
            if (!Avvisi.permessoNotifiche(ctx)) return
            if (Build.VERSION.SDK_INT >= 26) {
                val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
                nm.createNotificationChannel(
                    NotificationChannel(CANALE, "Spese da notifiche", NotificationManager.IMPORTANCE_LOW).apply {
                        description = "Esito della lettura delle notifiche: spesa registrata o da confermare"
                    }
                )
            }
            val apri = PendingIntent.getActivity(
                ctx, 0,
                Intent(ctx, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra("apri", "notifiche"),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val n = NotificationCompat.Builder(ctx, CANALE)
                .setSmallIcon(android.R.drawable.stat_notify_more)
                .setContentTitle(titolo)
                .setContentText(testo)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(apri)
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(ctx).notify(chiave.hashCode(), n)
        } catch (_: SecurityException) {
        }
    }
}

class NotifListener : NotificationListenerService() {
    private val ultime = LinkedHashMap<String, Pair<Int, Long>>()

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        try {
            gestisci(sbn)
        } catch (_: Exception) {
        }
    }

    private fun gestisci(sbn: StatusBarNotification) {
        val ctx = applicationContext
        Repo.init(ctx)
        if (!Notif.attive(ctx)) return
        val pkg = sbn.packageName
        if (pkg == packageName) return
        val n = sbn.notification
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        if (n.flags and Notification.FLAG_ONGOING_EVENT != 0) return
        val extras = n.extras
        val titolo = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val testo = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
        if (titolo.isBlank() && testo.isBlank()) return

        val regola = Notif.primaRegola(pkg, titolo, testo, Calendar.getInstance()) ?: return

        val impronta = (titolo + "\u0000" + testo).hashCode()
        val adesso = System.currentTimeMillis()
        val prima = ultime[sbn.key]
        if (prima != null && prima.first == impronta && adesso - prima.second < 30_000L) return
        ultime[sbn.key] = impronta to adesso
        if (ultime.size > 200) ultime.remove(ultime.keys.first())

        val app = etichetta(pkg)
        val ts = sbn.postTime
        thread {
            try {
                Notif.ricevi(ctx, pkg, app, ts, titolo.take(2000), testo.take(6000), regola)
            } catch (_: Exception) {
            }
        }
    }

    private fun etichetta(pkg: String): String = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (_: PackageManager.NameNotFoundException) {
        pkg
    }
}
