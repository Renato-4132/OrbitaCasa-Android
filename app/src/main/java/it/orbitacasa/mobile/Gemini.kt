package it.orbitacasa.mobile

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object Gemini {
    const val MODELLO_DEFAULT = "gemini-2.5-flash"
    private const val MAX_BYTES = 14L * 1024 * 1024
    private const val TIMEOUT_MS = 120_000

    class Errore(msg: String) : Exception(msg)

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("orbita", Context.MODE_PRIVATE)

    fun chiave(ctx: Context): String = prefs(ctx).getString("gemini_key", "")?.trim() ?: ""

    fun modello(ctx: Context): String =
        prefs(ctx).getString("gemini_model", "")?.trim()?.ifEmpty { null } ?: MODELLO_DEFAULT

    fun salva(ctx: Context, chiave: String, modello: String) {
        prefs(ctx).edit().putString("gemini_key", chiave.trim()).putString("gemini_model", modello.trim()).apply()
    }

    fun elencaModelli(chiave: String): List<String> {
        val out = ArrayList<String>()
        var pagina = ""
        for (giro in 0 until 10) {
            var u = "https://generativelanguage.googleapis.com/v1beta/models?pageSize=1000"
            if (pagina.isNotEmpty()) u += "&pageToken=" + java.net.URLEncoder.encode(pagina, "UTF-8")
            val conn = URL(u).openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = 20_000
                conn.readTimeout = 30_000
                conn.setRequestProperty("x-goog-api-key", chiave)
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val testo = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
                if (code !in 200..299) throw Exception("HTTP $code " + testo.take(300))
                val j = JSONObject(testo)
                val arr = j.optJSONArray("models") ?: JSONArray()
                for (i in 0 until arr.length()) {
                    val m = arr.getJSONObject(i)
                    val metodi = m.optJSONArray("supportedGenerationMethods") ?: continue
                    var ok = false
                    for (k in 0 until metodi.length()) if (metodi.getString(k) == "generateContent") ok = true
                    if (ok) out.add(m.optString("name").removePrefix("models/"))
                }
                pagina = j.optString("nextPageToken", "")
            } finally {
                conn.disconnect()
            }
            if (pagina.isEmpty()) break
        }
        return out.filter { it.isNotEmpty() }.distinct().sorted()
    }

    private fun mimeDi(f: File): String = when (f.extension.lowercase()) {
        "pdf" -> "application/pdf"
        "png" -> "image/png"
        "webp" -> "image/webp"
        "heic" -> "image/heic"
        else -> "image/jpeg"
    }

    private fun configurazioni(modello: String): List<JSONObject> {
        val l = ArrayList<JSONObject>()
        val m = Regex("gemini-(\\d+)(?:\\.(\\d+))?").find(modello.lowercase())
        val maj = m?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val min = m?.groupValues?.getOrNull(2)?.toIntOrNull() ?: 0
        fun base() = JSONObject().put("responseMimeType", "application/json")
        if (maj >= 3) {
            for (lv in listOf("minimal", "low")) {
                l.add(base().put("thinkingConfig", JSONObject().put("thinkingLevel", lv)))
            }
        } else if (maj == 2 && min >= 5) {
            l.add(base().put("thinkingConfig", JSONObject().put("thinkingBudget", 0)))
        }
        l.add(base())
        return l
    }

    private fun chiama(chiave: String, modello: String, mime: String?, b64: String?, prompt: String, cfg: JSONObject): String {
        val parti = JSONArray()
        if (mime != null && b64 != null) {
            parti.put(JSONObject().put("inline_data", JSONObject().put("mime_type", mime).put("data", b64)))
        }
        parti.put(JSONObject().put("text", prompt))
        val corpo = JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("parts", parti)))
            .put("generationConfig", cfg)
        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$modello:generateContent")
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 30_000
            conn.readTimeout = TIMEOUT_MS
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.setRequestProperty("x-goog-api-key", chiave)
            conn.outputStream.use { it.write(corpo.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val testo = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            if (code !in 200..299) throw Exception("HTTP $code " + testo.take(300))
            val parts = JSONObject(testo).optJSONArray("candidates")?.optJSONObject(0)
                ?.optJSONObject("content")?.optJSONArray("parts")
                ?: throw Errore("Risposta vuota da Gemini")
            val sb = StringBuilder()
            for (i in 0 until parts.length()) sb.append(parts.optJSONObject(i)?.optString("text", "") ?: "")
            return sb.toString()
        } finally {
            conn.disconnect()
        }
    }

    fun analizza(ctx: Context, file: File, prompt: String): JSONObject {
        if (chiave(ctx).isEmpty()) throw Errore("Imposta la chiave API Gemini")
        if (file.length() > MAX_BYTES) throw Errore("File troppo grande per l'analisi (max 14 MB)")
        val b64 = Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
        return esegui(ctx, mimeDi(file), b64, prompt)
    }

    fun analizzaTesto(ctx: Context, prompt: String): JSONObject = esegui(ctx, null, null, prompt)

    /** Risposta in testo libero (non JSON), per le domande sul database. */
    fun chiediTesto(ctx: Context, prompt: String): String {
        val chiave = chiave(ctx)
        if (chiave.isEmpty()) throw Errore("Imposta la chiave API Gemini")
        val modello = modello(ctx)
        val cfg = JSONObject().put("temperature", 0.2)
        var tent = 0
        while (true) {
            try {
                val t = chiama(chiave, modello, null, null, prompt, cfg).trim()
                if (t.isEmpty()) throw Errore("Risposta vuota da Gemini")
                return t
            } catch (e: Exception) {
                val m = e.message ?: ""
                tent++
                if (tent >= 3 || e is Errore || "HTTP 400" in m || "INVALID_ARGUMENT" in m ||
                    ("HTTP 429" in m && Regex("retry in \\d+h").containsMatchIn(m))
                ) throw e
                Thread.sleep(3000L * tent)
            }
        }
    }

    private fun esegui(ctx: Context, mime: String?, b64: String?, prompt: String): JSONObject {
        val chiave = chiave(ctx)
        if (chiave.isEmpty()) throw Errore("Imposta la chiave API Gemini")
        val modello = modello(ctx)
        val cfgs = configurazioni(modello)
        var i = 0
        var tent = 0
        var testo: String
        while (true) {
            try {
                testo = chiama(chiave, modello, mime, b64, prompt, cfgs[i])
                break
            } catch (e: Exception) {
                val m = e.message ?: ""
                val e400 = "HTTP 400" in m || "INVALID_ARGUMENT" in m
                if (e400 && i < cfgs.size - 1) {
                    i++
                    continue
                }
                tent++
                if (tent >= 3 || e400 || e is Errore || ("HTTP 429" in m && Regex("retry in \\d+h").containsMatchIn(m))) throw e
                Thread.sleep(3000L * tent)
            }
        }
        val raw = testo.trim().replace("```json", "").replace("```", "").trim()
        return try {
            JSONObject(raw)
        } catch (_: Exception) {
            throw Errore("Risposta di Gemini non leggibile")
        }
    }

    fun messaggio(e: Throwable): String {
        val m = e.message ?: ""
        return when {
            e is Errore -> m
            "API_KEY_INVALID" in m || "API key not valid" in m -> "Chiave API non valida."
            "HTTP 429" in m || "RESOURCE_EXHAUSTED" in m -> "Quota API Gemini esaurita. Riprova domani."
            "HTTP 503" in m || "UNAVAILABLE" in m -> "Gemini non disponibile. Riprova tra poco."
            "HTTP 504" in m || "DEADLINE" in m || e is java.net.SocketTimeoutException ||
                m.lowercase().contains("timed out") || m.lowercase().contains("timeout") -> "Gemini ha impiegato troppo tempo. Riprova."
            e is java.net.UnknownHostException || e is java.net.ConnectException -> "Nessuna connessione a internet."
            else -> "Analisi AI fallita: " + m.take(80)
        }
    }

    private fun str(j: JSONObject, k: String): String =
        if (j.isNull(k)) "" else j.optString(k, "").trim().let { if (it.equals("null", true)) "" else it }

    private val F_ITA = DateTimeFormatter.ofPattern("dd-MM-yyyy")

    class DatiContabile(
        val importo: Double, val descrizione: String, val data: LocalDate,
        val tipo: String?, val categoria: String?,
    )

    fun promptContabile(categorie: List<String>): String {
        val lista = categorie.joinToString(", ") { "\"$it\"" }
        return "Analizza questo documento PDF (fattura, ricevuta, cedolino, scontrino o simile).\n" +
            "Estrai i seguenti campi e restituisci SOLO un JSON (senza backtick):\n" +
            "{\"importo\": float, \"azienda\": \"nome breve\", \"data\": \"YYYY-MM-DD\", " +
            "\"fattura\": \"numero fattura o null\", \"scadenza\": \"GG-MM-AAAA o null\", " +
            "\"direzione\": \"Entrata o Uscita\", \"categoria\": \"la più adatta tra [$lista]\"}\n" +
            "REGOLE: importo 0.01 se non trovato. " +
            "Per la data usa quella del fatto economico (data valuta, data pagamento, data emissione/fattura, " +
            "scadenza/competenza), MAI la data di stampa/generazione del documento " +
            "(es. \"Stampa elaborata il\", \"Generato il\"); se non leggibile usa la data odierna. " +
            "Se nel documento compaiono più date etichettate 'scadenza' (es. scadenza del pagamento dovuto E " +
            "scadenza di un'offerta/contratto/promozione), usa per il campo 'scadenza' SOLO quella dell'importo " +
            "da pagare indicato in bolletta, ignorando le altre. azienda solo nome senza emoji; SOLO JSON."
    }

    fun leggiContabile(j: JSONObject, categorie: List<String>): DatiContabile {
        val imp = j.optDouble("importo", 0.01).let { if (it.isNaN() || it <= 0.0) 0.01 else it }
        var desc = str(j, "azienda").ifEmpty { "Documento" }
        var data = try { LocalDate.parse(str(j, "data")) } catch (_: Exception) { LocalDate.now() }
        val fattura = str(j, "fattura")
        if (fattura.isNotEmpty()) desc += " $fattura"
        val scad = str(j, "scadenza")
        if (scad.isNotEmpty()) {
            desc += " SCD:$scad"
            try { data = LocalDate.parse(scad, F_ITA) } catch (_: Exception) { }
        }
        val tipo = str(j, "direzione").takeIf { it == "Entrata" || it == "Uscita" }
        val c = str(j, "categoria")
        val cat = if (c in categorie) c else if ("Generica" in categorie) "Generica" else null
        return DatiContabile(imp, desc, data, tipo, cat)
    }

    class DatiPers(val descrizione: String, val categoria: String?, val scadenza: LocalDate?)

    fun promptPers(categorie: List<String>): String {
        val lista = categorie.joinToString(", ") { "\"$it\"" }
        return "Analizza questo documento PDF (fattura, ricevuta, certificato, documento d'identità, patente, " +
            "tessera, polizza, contratto o simile).\n" +
            "Estrai i seguenti campi e restituisci SOLO un JSON (senza backtick):\n" +
            "{\"descrizione\": \"titolo breve del documento (max 30 car.)\", " +
            "\"categoria\": \"la più adatta tra [$lista]\", \"scadenza\": \"GG-MM-AAAA o null\"}\n" +
            "REGOLE: descrizione concisa senza emoji. Per 'scadenza' cerca una data di scadenza/validità del " +
            "documento stesso (es. \"Valida fino al\", \"Scadenza\", \"Data di scadenza\"), NON la data di " +
            "emissione/rilascio né la data di stampa del documento; se non presente usa null. SOLO JSON."
    }

    fun leggiPers(j: JSONObject, categorie: List<String>): DatiPers {
        val desc = str(j, "descrizione").take(30)
        val c = str(j, "categoria")
        val scad = try { LocalDate.parse(str(j, "scadenza"), F_ITA) } catch (_: Exception) { null }
        return DatiPers(desc, if (c in categorie) c else null, scad)
    }
}
