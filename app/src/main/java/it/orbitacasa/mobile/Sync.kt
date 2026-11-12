package it.orbitacasa.mobile

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.File
import java.io.IOException
import java.net.URL
import java.net.URLEncoder
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CountDownLatch
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLException
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlin.math.abs

private fun syHex(b: ByteArray): String = b.joinToString("") { "%02x".format(it) }

private fun sySha256(b: ByteArray): String = syHex(MessageDigest.getInstance("SHA-256").digest(b))

private fun syEnc(s: String): String = URLEncoder.encode(s, "UTF-8")

private fun syCanon(x: Any?, sb: StringBuilder) {
    when (x) {
        null -> sb.append("null")
        JSONObject.NULL -> sb.append("null")
        is JSONObject -> {
            val ks = ArrayList<String>()
            val it = x.keys()
            while (it.hasNext()) ks.add(it.next())
            ks.sort()
            sb.append('{')
            for ((i, k) in ks.withIndex()) {
                if (i > 0) sb.append(',')
                sb.append(JSONObject.quote(k)).append(':')
                syCanon(x.opt(k), sb)
            }
            sb.append('}')
        }
        is JSONArray -> {
            sb.append('[')
            for (i in 0 until x.length()) {
                if (i > 0) sb.append(',')
                syCanon(x.opt(i), sb)
            }
            sb.append(']')
        }
        is Number -> {
            val d = x.toDouble()
            if (d == Math.rint(d) && abs(d) < 1e15) sb.append(d.toLong()) else sb.append(d)
        }
        is Boolean -> sb.append(x)
        else -> sb.append(JSONObject.quote(x.toString()))
    }
}

private fun syH(x: Any?): String {
    val sb = StringBuilder()
    syCanon(x, sb)
    return syHex(MessageDigest.getInstance("SHA-1").digest(sb.toString().toByteArray(Charsets.UTF_8))).take(16)
}

private fun syHVoce(data: String, e: JSONObject): String = syH(JSONObject().put("d", data).put("e", e))

private fun syVoce(h: String, l: String): JSONObject = JSONObject().put("h", h).put("l", l)

object Sync {
    private const val PREFS = "orbita_sync"
    private const val STATO = "sync_stato.json"
    private const val PROTO = 1
    private const val MAX_AUTO_MS = 10 * 60 * 1000L
    private const val CHUNK_SCARICA = 400
    private const val CHUNK_APPLICA = 100
    private const val MAX_DOC_BYTES = 40L * 1024 * 1024
    private const val MAX_FILE_CHARS = 8 * 1024 * 1024
    private val EST_DOC = setOf("pdf", "jpg", "jpeg", "png", "webp", "gif", "heic")

    private class SyncErr(msg: String) : Exception(msg)

    class Esito(val righe: List<String>, val errore: String?, val modificato: Boolean) {
        val ok: Boolean get() = errore == null
    }

    @Volatile
    private var inCorso = false
    private val main = Handler(Looper.getMainLooper())

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun indirizzo(c: Context): String = prefs(c).getString("url", "") ?: ""
    fun gettone(c: Context): String = prefs(c).getString("token", "") ?: ""
    fun improntaFissata(c: Context): String = prefs(c).getString("impronta", "") ?: ""
    fun auto(c: Context): Boolean = prefs(c).getBoolean("auto", false)
    fun ultima(c: Context): Long = prefs(c).getLong("ultima", 0L)

    fun configurato(c: Context): Boolean =
        indirizzo(c).isNotBlank() && gettone(c).isNotBlank() && improntaFissata(c).isNotBlank()

    fun daAutoSync(c: Context): Boolean =
        configurato(c) && auto(c) && !Repo.demoAttiva && !inCorso &&
            System.currentTimeMillis() - ultima(c) > MAX_AUTO_MS

    fun normalizzaUrl(s: String): String? {
        var t = s.trim()
        if (t.isEmpty()) return null
        if (t.startsWith("http://", ignoreCase = true)) return null
        if (!t.startsWith("https://", ignoreCase = true)) t = "https://$t"
        t = t.trimEnd('/')
        val i = t.indexOf("/api/")
        if (i > 0) t = t.substring(0, i)
        return try {
            val u = URL(t)
            if (u.host.isNullOrEmpty()) null else t
        } catch (_: Exception) {
            null
        }
    }

    private fun stessoServer(a: String, b: String): Boolean = try {
        val x = URL(a)
        val y = URL(b)
        x.host.equals(y.host, ignoreCase = true) && x.port == y.port
    } catch (_: Exception) {
        false
    }

    fun salva(c: Context, indirizzoNuovo: String, token: String, auto: Boolean) {
        val vecchio = indirizzo(c)
        val e = prefs(c).edit()
            .putString("url", indirizzoNuovo)
            .putString("token", token.trim())
            .putBoolean("auto", auto)
        if (!stessoServer(vecchio, indirizzoNuovo)) e.putString("impronta", "")
        e.apply()
    }

    fun fissaImpronta(c: Context, impronta: String) {
        prefs(c).edit().putString("impronta", impronta).apply()
    }

    fun scollega(c: Context) {
        prefs(c).edit().clear().apply()
        try {
            File(Repo.dirDati(), STATO).delete()
        } catch (_: Exception) {
        }
    }

    fun formatta(impronta: String): String =
        impronta.uppercase().chunked(2).joinToString(":")

    fun leggiImpronta(indirizzoPc: String): String {
        var trovato: X509Certificate? = null
        val tm = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                if (chain.isNotEmpty()) trovato = chain[0]
            }

            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        }
        val ctx = SSLContext.getInstance("TLS")
        ctx.init(null, arrayOf<TrustManager>(tm), null)
        val cn = URL(indirizzoPc).openConnection() as HttpsURLConnection
        cn.sslSocketFactory = ctx.socketFactory
        cn.hostnameVerifier = HostnameVerifier { _, _ -> true }
        cn.connectTimeout = 8000
        cn.readTimeout = 8000
        try {
            cn.connect()
        } finally {
            cn.disconnect()
        }
        val cert = trovato ?: throw IOException("Il PC non ha presentato nessun certificato")
        return sySha256(cert.encoded)
    }

    private class Http(private val base: String, private val token: String, private val impronta: String) {
        class Risp(val codice: Int, val json: JSONObject?)

        private val sf: SSLSocketFactory by lazy {
            val tm = object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
                    throw CertificateException("non previsto")
                }

                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                    if (chain.isEmpty() || sySha256(chain[0].encoded) != impronta) {
                        throw CertificateException("Il certificato del PC è cambiato")
                    }
                }

                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            }
            val ctx = SSLContext.getInstance("TLS")
            ctx.init(null, arrayOf<TrustManager>(tm), null)
            ctx.socketFactory
        }

        private fun apri(path: String, metodo: String): HttpsURLConnection {
            val cn = URL(base + path).openConnection() as HttpsURLConnection
            cn.sslSocketFactory = sf
            cn.hostnameVerifier = HostnameVerifier { _, _ -> true }
            cn.connectTimeout = 8000
            cn.readTimeout = 90000
            cn.requestMethod = metodo
            cn.setRequestProperty("Authorization", "Bearer $token")
            cn.setRequestProperty("Accept", "application/json")
            return cn
        }

        private fun leggi(cn: HttpsURLConnection): Risp {
            val code = cn.responseCode
            val txt = try {
                val flusso = if (code in 200..299) cn.inputStream else cn.errorStream
                flusso?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            } catch (_: IOException) {
                ""
            }
            val j = try {
                JSONObject(txt)
            } catch (_: Exception) {
                null
            }
            cn.disconnect()
            return Risp(code, j)
        }

        fun get(path: String): Risp = leggi(apri(path, "GET"))

        fun post(path: String, body: JSONObject): Risp {
            val cn = apri(path, "POST")
            val b = body.toString().toByteArray(Charsets.UTF_8)
            cn.doOutput = true
            cn.setFixedLengthStreamingMode(b.size)
            cn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            cn.outputStream.use { it.write(b) }
            return leggi(cn)
        }

        fun scarica(path: String, dest: File): Int {
            val cn = apri(path, "GET")
            val code = cn.responseCode
            if (code !in 200..299) {
                cn.disconnect()
                return code
            }
            cn.inputStream.use { i -> dest.outputStream().use { o -> i.copyTo(o) } }
            cn.disconnect()
            return code
        }

        fun invia(path: String, f: File): Risp {
            val cn = apri(path, "POST")
            cn.doOutput = true
            cn.setFixedLengthStreamingMode(f.length())
            cn.setRequestProperty("Content-Type", "application/octet-stream")
            f.inputStream().use { i -> cn.outputStream.use { o -> i.copyTo(o) } }
            return leggi(cn)
        }
    }

    private fun verifica(r: Http.Risp, cosa: String) {
        when {
            r.codice == 401 -> throw SyncErr("Token non valido: controllalo in Configurazione sul PC")
            r.codice == 429 -> throw SyncErr("Troppi tentativi errati: il PC ti ha bloccato per 10 minuti")
            r.codice !in 200..299 -> {
                val msg = r.json?.optString("errore").orEmpty().ifBlank { "errore HTTP ${r.codice}" }
                throw SyncErr("$cosa: $msg")
            }
        }
    }

    private class Stato(val o: JSONObject) {
        fun sez(n: String): JSONObject {
            var s = o.optJSONObject(n)
            if (s == null) {
                s = JSONObject()
                o.put(n, s)
            }
            return s
        }
    }

    private fun leggiStato(): Stato = try {
        Stato(JSONObject(File(Repo.dirDati(), STATO).readText(Charsets.UTF_8)))
    } catch (_: Exception) {
        Stato(JSONObject())
    }

    private fun scriviStato(s: Stato) {
        val f = File(Repo.dirDati(), STATO)
        val tmp = File(Repo.dirDati(), "$STATO.tmp")
        tmp.writeText(s.o.toString(), Charsets.UTF_8)
        Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }

    private class Ctx(val app: Context, val http: Http, val st: Stato) {
        val righe = ArrayList<String>()
        val cont = HashMap<String, Int>()
        val forza = HashSet<String>()
        val backupVoci = JSONArray()
        var conflitti = 0
        val ts: String = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date())
        fun inc(k: String, n: Int = 1) {
            cont[k] = (cont[k] ?: 0) + n
        }

        fun n(k: String): Int = cont[k] ?: 0
    }

    private fun <T> suMain(f: () -> T): T {
        if (Looper.myLooper() == Looper.getMainLooper()) return f()
        val latch = CountDownLatch(1)
        var r: Result<T>? = null
        main.post {
            r = runCatching(f)
            latch.countDown()
        }
        latch.await()
        return r!!.getOrThrow()
    }

    private fun scriviBackup(c: Ctx, sotto: String, nome: String, testo: String) {
        try {
            val f = File(c.app.filesDir, "sync_backup/${c.ts}/$sotto/$nome")
            f.parentFile?.mkdirs()
            f.writeText(testo, Charsets.UTF_8)
        } catch (_: Exception) {
        }
    }

    private fun potaBackup(app: Context) {
        try {
            val dirs = File(app.filesDir, "sync_backup").listFiles()?.filter { it.isDirectory }?.sortedBy { it.name }
                ?: return
            for (d in dirs.dropLast(5)) d.deleteRecursively()
        } catch (_: Exception) {
        }
    }

    fun sincronizza(ctx: Context, fine: (Esito) -> Unit) {
        if (inCorso) {
            fine(Esito(emptyList(), "Sincronizzazione già in corso", false))
            return
        }
        inCorso = true
        val app = ctx.applicationContext
        Thread {
            val esito = try {
                esegui(app)
            } catch (e: SyncErr) {
                Esito(emptyList(), e.message, false)
            } catch (e: SSLException) {
                Esito(
                    emptyList(),
                    "Connessione sicura non riuscita (certificato del PC cambiato?). " +
                        "Scollega e ricollega la sincronizzazione. (${e.message})",
                    false
                )
            } catch (e: IOException) {
                Esito(emptyList(), "PC non raggiungibile: ${e.message}", false)
            } catch (e: Exception) {
                Esito(emptyList(), "Errore: ${e.message}", false)
            }
            inCorso = false
            main.post { fine(esito) }
        }.start()
    }

    private fun esegui(app: Context): Esito {
        if (Repo.demoAttiva) throw SyncErr("La sincronizzazione non è disponibile in modalità demo")
        if (!configurato(app)) throw SyncErr("Sincronizzazione non configurata")
        val http = Http(indirizzo(app), gettone(app), improntaFissata(app))
        val s = http.get("/api/sync/stato")
        verifica(s, "stato")
        if (s.json?.optBoolean("ok") != true) throw SyncErr("Il PC non ha risposto correttamente")
        if ((s.json?.optInt("proto", 0) ?: 0) != PROTO) {
            throw SyncErr("Versione della sincronizzazione sul PC non compatibile: aggiorna OrbitaCasa sul PC")
        }
        potaBackup(app)
        val c = Ctx(app, http, leggiStato())

        try {
            for (giro in 1..2) {
                c.conflitti = 0
                sincBlocchi(c)
                sincFiles(c)
                sincVoci(c)
                if (c.conflitti == 0) break
            }
            sincDocs(c)
        } finally {
            if (c.backupVoci.length() > 0) scriviBackup(c, "movimenti", "voci_sovrascritte.json", c.backupVoci.toString(2))
            scriviStato(c.st)
        }

        val giu = c.n("voci_giu") + c.n("voci_del_tel") + c.n("blocchi_giu") + c.n("file_giu") + c.n("doc_giu")
        if (giu > 0) suMain { Repo.ricarica() }

        prefs(app).edit().putLong("ultima", System.currentTimeMillis()).apply()

        val r = ArrayList<String>()
        if (c.n("voci_giu") + c.n("voci_su") + c.n("voci_del_pc") + c.n("voci_del_tel") > 0) {
            r.add(
                "Movimenti: ${c.n("voci_giu")} dal PC, ${c.n("voci_su")} al PC, " +
                    "${c.n("voci_del_pc") + c.n("voci_del_tel")} eliminati"
            )
        }
        if (c.n("blocchi_giu") + c.n("blocchi_su") > 0) {
            r.add("Impostazioni e categorie: ${c.n("blocchi_giu")} dal PC, ${c.n("blocchi_su")} al PC")
        }
        if (c.n("file_giu") + c.n("file_su") > 0) {
            r.add("Archivi: ${c.n("file_giu")} dal PC, ${c.n("file_su")} al PC")
        }
        if (c.n("doc_giu") + c.n("doc_su") > 0) {
            r.add("Documenti: ${c.n("doc_giu")} dal PC, ${c.n("doc_su")} al PC")
        }
        if (c.n("conflitti") > 0) {
            r.add("Conflitti: ${c.n("conflitti")} (ha vinto il PC, la copia del telefono è in sync_backup)")
        }
        if (c.n("saltati") > 0) {
            r.add("${c.n("saltati")} elementi modificati durante la sincronizzazione: riprova")
        }
        r.addAll(c.righe)
        if (r.isEmpty()) r.add("Tutto già allineato")
        return Esito(r, null, giu > 0)
    }

    private fun sincBlocchi(c: Ctx) {
        val r = c.http.get("/api/sync/blocchi")
        verifica(r, "blocchi")
        val srv = r.json?.optJSONObject("blocchi") ?: throw SyncErr("Risposta blocchi non valida")
        val sez = c.st.sez("blocchi")
        val loc = suMain { Repo.BLOCCHI_SYNC.associateWith { Repo.syncBlocco(it) } }
        val lhSnap = HashMap<String, String>()
        for ((n, v) in loc) lhSnap[n] = syH(v)

        val scarica = LinkedHashMap<String, Pair<String, Any>>()
        val backupDi = HashSet<String>()
        val carica = JSONObject()
        val caricaH = HashMap<String, String>()

        for (nome in Repo.BLOCCHI_SYNC) {
            val s = srv.optJSONObject(nome) ?: continue
            val hs = s.optString("h")
            val vs: Any = s.opt("valore") ?: continue
            if (vs !is JSONObject && vs !is JSONArray) continue
            val lv = loc[nome] ?: continue
            val lh = lhSnap[nome] ?: continue
            if (syH(vs) == lh) {
                sez.put(nome, syVoce(hs, lh))
                continue
            }
            val st = sez.optJSONObject(nome)
            if (st == null) {
                scarica[nome] = hs to vs
                backupDi.add(nome)
                continue
            }
            val sCh = st.optString("h") != hs
            val lCh = st.optString("l") != lh
            when {
                sCh && lCh -> {
                    scarica[nome] = hs to vs
                    backupDi.add(nome)
                    c.inc("conflitti")
                }
                sCh -> scarica[nome] = hs to vs
                lCh -> {
                    carica.put(nome, JSONObject().put("base", hs).put("valore", lv))
                    caricaH[nome] = lh
                }
            }
        }

        if (scarica.isNotEmpty()) {
            val nuovi = HashMap<String, String>()
            suMain {
                var toccato = false
                for ((nome, p) in scarica) {
                    if (syH(Repo.syncBlocco(nome)) != lhSnap[nome]) {
                        c.inc("saltati")
                        continue
                    }
                    if (nome in backupDi) {
                        scriviBackup(c, "blocchi", "$nome.json", Repo.syncBlocco(nome).toString())
                    }
                    Repo.syncImpostaBlocco(nome, p.second)
                    nuovi[nome] = "x"
                    toccato = true
                }
                if (toccato) Repo.syncSalva()
                for (nome in nuovi.keys.toList()) nuovi[nome] = syH(Repo.syncBlocco(nome))
            }
            for ((nome, p) in scarica) {
                val nl = nuovi[nome] ?: continue
                sez.put(nome, syVoce(p.first, nl))
                c.inc("blocchi_giu")
            }
        }

        if (carica.length() > 0) {
            val rr = c.http.post("/api/sync/blocchi", JSONObject().put("blocchi", carica))
            verifica(rr, "invio blocchi")
            val ap = rr.json?.optJSONArray("applicati")
            if (ap != null) {
                for (i in 0 until ap.length()) {
                    val o = ap.optJSONObject(i) ?: continue
                    val nome = o.optString("nome")
                    sez.put(nome, syVoce(o.optString("h"), caricaH[nome] ?: ""))
                    c.inc("blocchi_su")
                }
            }
            val rf = rr.json?.optJSONArray("rifiutati")
            if (rf != null) {
                for (i in 0 until rf.length()) {
                    val o = rf.optJSONObject(i) ?: continue
                    if (o.optString("motivo") == "conflitto") c.conflitti++
                    else c.righe.add("Blocco ${o.optString("nome")} non accettato: ${o.optString("motivo")}")
                }
            }
        }
    }

    private fun leggiLocale(f: File): Pair<String?, Any?> {
        if (!f.isFile) return null to null
        val txt = try {
            f.readText(Charsets.UTF_8)
        } catch (_: Exception) {
            return null to null
        }
        val v = try {
            JSONTokener(txt).nextValue()
        } catch (_: Exception) {
            null
        }
        return txt to (if (v is JSONObject || v is JSONArray) v else null)
    }

    private fun scriviJson(f: File, v: Any) {
        val tmp = File(f.parentFile, f.name + ".tmp")
        val txt = when (v) {
            is JSONObject -> v.toString(2)
            is JSONArray -> v.toString(2)
            else -> v.toString()
        }
        tmp.writeText(txt, Charsets.UTF_8)
        if (f.exists()) {
            try {
                f.copyTo(File(f.parentFile, f.name + ".bak"), overwrite = true)
            } catch (_: Exception) {
            }
        }
        Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }

    private fun sincFiles(c: Ctx) {
        val r = c.http.get("/api/sync/files")
        verifica(r, "elenco archivi")
        val files = r.json?.optJSONObject("files") ?: throw SyncErr("Risposta archivi non valida")
        val sez = c.st.sez("files")
        val dir = Repo.dirDati()
        val nomi = ArrayList<String>()
        val it = files.keys()
        while (it.hasNext()) nomi.add(it.next())

        for (nome in nomi) {
            val hs = if (files.isNull(nome)) "" else files.optString(nome)
            val f = File(dir, "$nome.json")
            val (txt, lv) = leggiLocale(f)
            if (txt != null && lv == null) {
                c.righe.add("$nome: file sul telefono non leggibile, saltato")
                continue
            }
            val lh = if (lv != null) syH(lv) else ""
            val st = sez.optJSONObject(nome)

            var azione = 0
            var conflitto = false
            if (hs.isEmpty()) {
                if (lv != null) azione = 2 else sez.remove(nome)
            } else if (lv == null) {
                azione = 1
            } else if (st == null) {
                azione = 3
            } else {
                val sCh = st.optString("h") != hs
                val lCh = st.optString("l") != lh
                when {
                    sCh && lCh -> {
                        azione = 3
                        conflitto = true
                    }
                    sCh -> azione = 1
                    lCh -> azione = 2
                }
            }

            when (azione) {
                1, 3 -> {
                    val rr = c.http.get("/api/sync/file?nome=" + syEnc(nome))
                    verifica(rr, "archivio $nome")
                    val cont: Any = rr.json?.opt("contenuto") ?: continue
                    if (cont !is JSONObject && cont !is JSONArray) continue
                    val h2 = rr.json?.optString("h").orEmpty().ifEmpty { hs }
                    val ch = syH(cont)
                    if (lv != null && ch == lh) {
                        sez.put(nome, syVoce(h2, lh))
                        continue
                    }
                    val backup = azione == 3
                    val fatto = suMain {
                        val (t2, v2) = leggiLocale(f)
                        val ora = if (v2 != null) syH(v2) else ""
                        if (ora != lh) {
                            false
                        } else {
                            if (backup && t2 != null) scriviBackup(c, "archivi", "$nome.json", t2)
                            scriviJson(f, cont)
                            true
                        }
                    }
                    if (fatto) {
                        sez.put(nome, syVoce(h2, ch))
                        c.inc("file_giu")
                        if (conflitto) c.inc("conflitti")
                    } else {
                        c.inc("saltati")
                    }
                }
                2 -> {
                    if (txt == null || lv == null) continue
                    if (txt.length > MAX_FILE_CHARS) {
                        c.righe.add("$nome: troppo grande (oltre 8 MB), non inviato")
                        continue
                    }
                    val body = JSONObject()
                        .put("nome", nome)
                        .put("base", if (hs.isEmpty()) JSONObject.NULL else hs)
                        .put("contenuto", lv)
                    val rr = c.http.post("/api/sync/file", body)
                    when {
                        rr.codice == 401 || rr.codice == 429 -> verifica(rr, "archivio $nome")
                        rr.codice == 409 -> c.conflitti++
                        rr.codice in 200..299 && rr.json?.optBoolean("ok") == true -> {
                            sez.put(nome, syVoce(rr.json?.optString("h").orEmpty(), lh))
                            c.inc("file_su")
                        }
                        else -> {
                            val msg = rr.json?.optString("errore").orEmpty().ifBlank { "HTTP ${rr.codice}" }
                            c.righe.add("$nome non accettato dal PC: $msg")
                        }
                    }
                }
            }
        }
    }

    private fun norm(x: Any?): String = when (x) {
        null -> ""
        JSONObject.NULL -> ""
        is Number -> String.format(Locale.ROOT, "%.2f", x.toDouble())
        is JSONArray -> {
            val l = ArrayList<String>()
            for (i in 0 until x.length()) l.add(norm(x.opt(i)))
            l.sorted().joinToString("|")
        }
        else -> x.toString().trim()
    }

    private fun stessoMovimento(a: JSONObject, da: String, b: JSONObject, db: String): Boolean {
        if (da != db) return false
        for (k in listOf("categoria", "descrizione", "importo", "tipo", "metodo_pagamento", "conto", "ora", "hashtag")) {
            val x = norm(a.opt(k))
            val y = norm(b.opt(k))
            if (x == y) continue
            if ((k == "metodo_pagamento" || k == "ora") && (x.isEmpty() || y.isEmpty())) continue
            return false
        }
        return true
    }

    private class VoceSrv(val data: String, val h: String)

    private fun sincVoci(c: Ctx) {
        val r = c.http.get("/api/sync/indice")
        verifica(r, "indice movimenti")
        val arr = r.json?.optJSONArray("voci") ?: throw SyncErr("Risposta indice non valida")
        val srv = HashMap<String, VoceSrv>()
        for (i in 0 until arr.length()) {
            val a = arr.optJSONArray(i) ?: continue
            srv[a.optString(0)] = VoceSrv(a.optString(1), a.optString(2))
        }
        val sez = c.st.sez("voci")
        val snap = suMain { Repo.syncVoci() }
        val lh = HashMap<String, String>()
        for ((id, p) in snap) lh[id] = syHVoce(p.first, p.second)

        val scarica = LinkedHashSet<String>()
        val daConfrontare = LinkedHashSet<String>()
        val conflittiId = HashSet<String>()
        val carica = LinkedHashMap<String, String?>()
        val elimina = LinkedHashMap<String, String>()
        val eliminaLocali = LinkedHashSet<String>()

        val tutti = HashSet<String>()
        tutti.addAll(srv.keys)
        tutti.addAll(snap.keys)
        val kk = sez.keys()
        while (kk.hasNext()) tutti.add(kk.next())

        for (id in tutti) {
            val s = srv[id]
            val l = snap[id]
            val st = sez.optJSONObject(id)
            if (s != null && l != null) {
                if (st == null) {
                    daConfrontare.add(id)
                } else if (id in c.forza) {
                    scarica.add(id)
                } else {
                    val sCh = st.optString("h") != s.h
                    val lCh = st.optString("l") != lh[id]
                    when {
                        sCh && lCh -> {
                            scarica.add(id)
                            conflittiId.add(id)
                        }
                        sCh -> scarica.add(id)
                        lCh -> carica[id] = st.optString("h")
                    }
                }
            } else if (s != null) {
                if (st == null) scarica.add(id)
                else if (st.optString("h") == s.h) elimina[id] = s.h
                else scarica.add(id)
            } else if (l != null) {
                if (st == null) carica[id] = null
                else if (st.optString("l") != lh[id]) carica[id] = null
                else eliminaLocali.add(id)
            } else {
                sez.remove(id)
            }
        }

        val daScaricare = ArrayList<String>(scarica)
        daScaricare.addAll(daConfrontare)
        val scaricate = ArrayList<JSONObject>()
        for (blocco in daScaricare.chunked(CHUNK_SCARICA)) {
            val rr = c.http.post("/api/sync/scarica", JSONObject().put("ids", JSONArray(blocco)))
            verifica(rr, "scarico movimenti")
            val vv = rr.json?.optJSONArray("voci") ?: continue
            for (i in 0 until vv.length()) vv.optJSONObject(i)?.let { scaricate.add(it) }
        }

        if (scaricate.isNotEmpty() || eliminaLocali.isNotEmpty()) {
            val nuoviL = HashMap<String, String>()
            val nuoviH = HashMap<String, String>()
            val ugualiH = HashMap<String, String>()
            val rimossi = ArrayList<String>()
            suMain {
                val cur = Repo.syncVoci()
                fun invariato(id: String): Boolean {
                    val a = cur[id]
                    val ora = a?.let { syHVoce(it.first, it.second) }
                    return ora == lh[id]
                }
                var toccato = false
                for (o in scaricate) {
                    val e = o.optJSONObject("entry") ?: continue
                    val id = e.optString("id_spesa")
                    val d = o.optString("date")
                    val hs = o.optString("h")
                    if (id.isEmpty()) continue
                    if (!invariato(id)) {
                        c.inc("saltati")
                        continue
                    }
                    val l = snap[id]
                    if (l != null) {
                        if (id in daConfrontare && stessoMovimento(l.second, l.first, e, d)) {
                            ugualiH[id] = hs
                            continue
                        }
                        if (id in daConfrontare || id in conflittiId) {
                            c.backupVoci.put(JSONObject().put("id", id).put("date", l.first).put("entry", l.second))
                            c.inc("conflitti")
                        }
                    }
                    Repo.syncImpostaVoce(d, e)
                    toccato = true
                    nuoviH[id] = hs
                    c.inc("voci_giu")
                }
                for (id in eliminaLocali) {
                    if (!invariato(id)) {
                        c.inc("saltati")
                        continue
                    }
                    Repo.syncRimuoviVoce(id)
                    toccato = true
                    rimossi.add(id)
                    c.inc("voci_del_tel")
                }
                if (toccato) Repo.syncSalva()
                val dopo = Repo.syncVoci()
                for (id in nuoviH.keys) dopo[id]?.let { nuoviL[id] = syHVoce(it.first, it.second) }
            }
            for ((id, hs) in ugualiH) sez.put(id, syVoce(hs, lh[id] ?: ""))
            for ((id, hs) in nuoviH) sez.put(id, syVoce(hs, nuoviL[id] ?: ""))
            for (id in rimossi) sez.remove(id)
        }

        fun esitoApplica(rr: Http.Risp) {
            verifica(rr, "invio movimenti")
            val ap = rr.json?.optJSONArray("applicati")
            if (ap != null) {
                for (i in 0 until ap.length()) {
                    val o = ap.optJSONObject(i) ?: continue
                    val id = o.optString("id")
                    sez.put(id, syVoce(o.optString("h"), lh[id] ?: ""))
                    c.inc("voci_su")
                }
            }
            val el = rr.json?.optJSONArray("eliminati")
            if (el != null) {
                for (i in 0 until el.length()) {
                    sez.remove(el.optString(i))
                    c.inc("voci_del_pc")
                }
            }
            val rf = rr.json?.optJSONArray("rifiutati")
            if (rf != null) {
                for (i in 0 until rf.length()) {
                    val o = rf.optJSONObject(i) ?: continue
                    val id = o.optString("id")
                    val motivo = o.optString("motivo")
                    when {
                        motivo == "bloccata" || o.optBoolean("bloccata") -> {
                            c.forza.add(id)
                            c.conflitti++
                        }
                        motivo == "conflitto" || motivo == "eliminata" -> c.conflitti++
                        else -> c.righe.add("Movimento non accettato dal PC: $motivo")
                    }
                }
            }
        }

        val ups = ArrayList<JSONObject>()
        for ((id, base) in carica) {
            val l = snap[id] ?: continue
            ups.add(
                JSONObject()
                    .put("date", l.first)
                    .put("base", base ?: JSONObject.NULL)
                    .put("entry", l.second)
            )
        }
        for (blocco in ups.chunked(CHUNK_APPLICA)) {
            esitoApplica(c.http.post("/api/sync/applica", JSONObject().put("upsert", JSONArray(blocco))))
        }
        val dels = elimina.map { (id, base) -> JSONObject().put("id", id).put("base", base) }
        for (blocco in dels.chunked(CHUNK_APPLICA)) {
            esitoApplica(c.http.post("/api/sync/applica", JSONObject().put("elimina", JSONArray(blocco))))
        }
    }

    private fun percorsoDoc(nome: String): String? {
        if (nome.isEmpty() || nome.contains("..") || nome.contains('\\') || nome.startsWith("/")) return null
        if (nome.startsWith("documenti_personali/")) return nome
        if (nome.contains('/')) return null
        return "documenti/$nome"
    }

    private fun fondi(c: Ctx, nome: String, base: Any?, locale: Any?, remoto: Any): Any {
        if (remoto is JSONArray) {
            val out = JSONArray()
            val visti = HashSet<String>()
            val srcs = ArrayList<JSONArray>()
            srcs.add(remoto)
            if (locale is JSONArray) srcs.add(locale)
            for (src in srcs) {
                for (i in 0 until src.length()) {
                    if (visti.add(syH(src.opt(i)))) out.put(src.opt(i))
                }
            }
            return out
        }
        val rem = remoto as JSONObject
        val loc = locale as? JSONObject ?: return rem
        val bas = base as? JSONObject
        val out = JSONObject()
        val chiavi = LinkedHashSet<String>()
        val kr = rem.keys()
        while (kr.hasNext()) chiavi.add(kr.next())
        val kl = loc.keys()
        while (kl.hasNext()) chiavi.add(kl.next())
        var confl = false
        for (k in chiavi) {
            val lv = loc.opt(k)
            val rv = rem.opt(k)
            val bv = bas?.opt(k)
            when {
                lv == null -> out.put(k, rv)
                rv == null -> if (!(bv != null && syH(lv) == syH(bv))) out.put(k, lv)
                syH(lv) == syH(rv) -> out.put(k, rv)
                bv != null && syH(rv) == syH(bv) -> out.put(k, lv)
                else -> {
                    out.put(k, rv)
                    confl = true
                }
            }
        }
        if (confl) {
            c.inc("conflitti")
            scriviBackup(c, "documenti_personali", nome.replace('/', '_'), loc.toString(2))
        }
        return out
    }

    private fun sincMeta(c: Ctx, nome: String, f: File, mt: Long) {
        val basi = c.st.sez("pers_base")
        val sez = c.st.sez("docs")
        val locale = leggiLocale(f).second
        val baseTxt = basi.optString(nome, "")
        val base: Any? = if (baseTxt.isEmpty()) null else try {
            JSONTokener(baseTxt).nextValue()
        } catch (_: Exception) {
            null
        }
        val invariato = locale != null && base != null && sez.optLong(nome, -1L) == mt && syH(locale) == syH(base)
        if (invariato) return
        f.parentFile?.mkdirs()
        val tmp = File(f.parentFile, f.name + ".sync.tmp")
        val code = c.http.scarica("/api/sync/doc?nome=" + syEnc(nome), tmp)
        if (code !in 200..299) {
            tmp.delete()
            if (code == 401 || code == 429) verifica(Http.Risp(code, null), "documenti")
            return
        }
        val remoto = try {
            JSONTokener(tmp.readText(Charsets.UTF_8)).nextValue()
        } catch (_: Exception) {
            null
        }
        tmp.delete()
        if (remoto !is JSONObject && remoto !is JSONArray) return
        val fusa = fondi(c, nome, base, locale, remoto)
        if (locale == null || syH(fusa) != syH(locale)) {
            suMain { scriviJson(f, fusa) }
            c.inc("doc_giu")
        }
        if (syH(fusa) == syH(remoto)) {
            sez.put(nome, mt)
            basi.put(nome, fusa.toString())
            return
        }
        val rr = c.http.invia("/api/sync/doc?nome=" + syEnc(nome) + "&base_mt=" + mt, f)
        if (rr.codice == 401 || rr.codice == 429) verifica(rr, "documenti")
        if (rr.codice in 200..299 && rr.json?.optBoolean("ok") == true) {
            c.inc("doc_su")
            sez.put(nome, rr.json?.optLong("mt", mt) ?: mt)
            basi.put(nome, fusa.toString())
        } else if (rr.codice == 409) {
            c.inc("saltati")
        } else {
            c.righe.add("Documento $nome non inviato: ${rr.json?.optString("errore").orEmpty().ifBlank { "HTTP ${rr.codice}" }}")
        }
    }

    private fun invioMeta(c: Ctx, nome: String, f: File) {
        val rr = c.http.invia("/api/sync/doc?nome=" + syEnc(nome) + "&base_mt=0", f)
        if (rr.codice == 401 || rr.codice == 429) verifica(rr, "documenti")
        if (rr.codice in 200..299 && rr.json?.optBoolean("ok") == true) {
            c.inc("doc_su")
            c.st.sez("docs").put(nome, rr.json?.optLong("mt", 0L) ?: 0L)
            leggiLocale(f).second?.let { c.st.sez("pers_base").put(nome, it.toString()) }
        } else if (rr.codice == 409) {
            c.inc("saltati")
        } else {
            c.righe.add("Documento $nome non inviato: ${rr.json?.optString("errore").orEmpty().ifBlank { "HTTP ${rr.codice}" }}")
        }
    }

    private fun locPersonali(dir: File): List<String> {
        val out = ArrayList<String>()
        val rad = File(dir, "documenti_personali")
        if (!rad.isDirectory) return out
        if (File(rad, "profili.json").isFile) out.add("documenti_personali/profili.json")
        val profili = rad.listFiles()?.filter { it.isDirectory && !it.name.startsWith(".") }?.sortedBy { it.name }
        for (p in profili ?: emptyList()) {
            for (m in listOf("registry.json", "categorie.json")) {
                if (File(p, m).isFile) out.add("documenti_personali/${p.name}/$m")
            }
            val docs = File(p, "documenti").listFiles()?.filter {
                it.isFile && !it.name.startsWith(".") && it.name.substringAfterLast('.', "").lowercase() in EST_DOC
            }?.sortedBy { it.name }
            for (d in docs ?: emptyList()) out.add("documenti_personali/${p.name}/documenti/${d.name}")
        }
        return out
    }

    private fun sincDocs(c: Ctx) {
        val r = c.http.get("/api/sync/docs")
        verifica(r, "elenco documenti")
        val arr = r.json?.optJSONArray("docs") ?: return
        val dir = Repo.dirDati()
        val sez = c.st.sez("docs")
        val sulPc = HashSet<String>()
        val metaPc = ArrayList<Pair<String, Long>>()

        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val nome = o.optString("nome")
            val mt = o.optLong("mt")
            val rel = percorsoDoc(nome) ?: continue
            sulPc.add(nome)
            val meta = nome.startsWith("documenti_personali/") && nome.endsWith(".json")
            if (meta) {
                metaPc.add(nome to mt)
                continue
            }
            val f = File(dir, rel)
            try {
                if (!f.isFile && scaricaDoc(c, nome, f)) {
                    sez.put(nome, mt)
                    c.inc("doc_giu")
                }
            } catch (e: SyncErr) {
                throw e
            } catch (e: IOException) {
                throw e
            } catch (e: Exception) {
                c.righe.add("Documento $nome non sincronizzato: ${e.message}")
            }
        }

        val cart = File(dir, "documenti")
        val locali = cart.listFiles()?.filter {
            it.isFile && !it.name.startsWith(".") && it.name !in sulPc &&
                it.name.substringAfterLast('.', "").lowercase() in EST_DOC &&
                it.length() in 1..MAX_DOC_BYTES && percorsoDoc(it.name) != null
        } ?: emptyList()
        for (f in locali) {
            val rr = c.http.invia("/api/sync/doc?nome=" + syEnc(f.name), f)
            if (rr.codice == 401 || rr.codice == 429) verifica(rr, "documenti")
            if (rr.codice in 200..299 && rr.json?.optBoolean("ok") == true) c.inc("doc_su")
            else c.righe.add("Documento ${f.name} non inviato: ${rr.json?.optString("errore").orEmpty().ifBlank { "HTTP ${rr.codice}" }}")
        }

        val perso = locPersonali(dir)
        for (nome in perso) {
            if (nome in sulPc || sez.has(nome) || nome.endsWith(".json")) continue
            val f = File(dir, nome)
            if (f.length() !in 1..MAX_DOC_BYTES || percorsoDoc(nome) == null) continue
            try {
                val rr = c.http.invia("/api/sync/doc?nome=" + syEnc(nome), f)
                if (rr.codice == 401 || rr.codice == 429) verifica(rr, "documenti")
                if (rr.codice in 200..299 && rr.json?.optBoolean("ok") == true) {
                    sez.put(nome, rr.json?.optLong("mt", 0L) ?: 0L)
                    c.inc("doc_su")
                } else {
                    c.righe.add("Documento $nome non inviato: ${rr.json?.optString("errore").orEmpty().ifBlank { "HTTP ${rr.codice}" }}")
                }
            } catch (e: SyncErr) {
                throw e
            } catch (e: IOException) {
                throw e
            } catch (e: Exception) {
                c.righe.add("Documento $nome non inviato: ${e.message}")
            }
        }

        for ((nome, mt) in metaPc) {
            val f = File(dir, percorsoDoc(nome) ?: continue)
            try {
                sincMeta(c, nome, f, mt)
            } catch (e: SyncErr) {
                throw e
            } catch (e: IOException) {
                throw e
            } catch (e: Exception) {
                c.righe.add("Documento $nome non sincronizzato: ${e.message}")
            }
        }

        for (nome in perso) {
            if (!nome.endsWith(".json") || nome in sulPc || sez.has(nome)) continue
            val f = File(dir, nome)
            if (f.length() !in 1..MAX_FILE_CHARS.toLong() || percorsoDoc(nome) == null) continue
            try {
                invioMeta(c, nome, f)
            } catch (e: SyncErr) {
                throw e
            } catch (e: IOException) {
                throw e
            } catch (e: Exception) {
                c.righe.add("Documento $nome non inviato: ${e.message}")
            }
        }
    }

    private fun scaricaDoc(c: Ctx, nome: String, f: File): Boolean {
        f.parentFile?.mkdirs()
        val tmp = File(f.parentFile, f.name + ".sync.tmp")
        val code = c.http.scarica("/api/sync/doc?nome=" + syEnc(nome), tmp)
        if (code !in 200..299) {
            tmp.delete()
            if (code == 401 || code == 429) verifica(Http.Risp(code, null), "documenti")
            return false
        }
        Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING)
        return true
    }
}
