package it.orbitacasa.mobile

import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.textfield.TextInputEditText
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.math.max

private val FMT_DATA = DateTimeFormatter.ofPattern("dd-MM-yyyy")
private val FMT_MESE = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ITALIAN)
private val FMT_GIORNO = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ITALIAN)

fun euro(v: Double): String = "€ " + String.format(Locale.ITALY, "%,.2f", v)

fun num(s: String): Double? {
    val t = s.trim().replace("€", "").replace(" ", "").replace(',', '.')
    return if (t.isEmpty()) 0.0 else t.toDoubleOrNull()
}

private fun maiuscola(s: String): String = s.replaceFirstChar { it.uppercase() }

private fun descPulita(d: String): String =
    d.removePrefix("RIC·").trim().replace(Regex("\\s*ID:[0-9a-fA-F]{8}$"), "").trim()

object Lock {
    fun authenticators(): Int =
        if (Build.VERSION.SDK_INT >= 30) BIOMETRIC_WEAK or DEVICE_CREDENTIAL else BIOMETRIC_WEAK

    fun disponibile(ctx: Context): Boolean =
        BiometricManager.from(ctx).canAuthenticate(authenticators()) ==
            BiometricManager.BIOMETRIC_SUCCESS
}

class MainActivity : AppCompatActivity() {

    private val MAIL_SUPPORTO = "helporbitacasa@gmail.com"

    private enum class Pagina { HOME, MOVIMENTI, CONTI, CATEGORIE, ANALISI, TARGET, RICORRENZE, CALENDARIO, FINANZIAMENTI, AUTOPARK, IMMOBIL, PET, FONDO, GRAFICI, PIANIFICA, DOCUMENTI, DOCPERS, UTENZE, FAIRSHARE, POOL, CHECKOUT, RICERCA, PREVISIONE, PROMEMORIA, NOTIFICHE, CASACARE, PENSIONE, DIETA, STUDIO, INFLAZIONE, MARKET, STOCK, INFO, ESTRATTI, CHIEDI, INVEST }

    private class Tessera(val ico: String, val lbl: String, val azione: () -> Unit)

    private var pagina = Pagina.HOME
    private var mese: YearMonth = YearMonth.now()
    private var sbloccata = false
    private var ultimoStop = 0L
    private val handlerSync = android.os.Handler(android.os.Looper.getMainLooper())
    private val timerSync = object : Runnable {
        override fun run() {
            if (isFinishing || isDestroyed) return
            if (Sync.daPeriodico(this@MainActivity)) eseguiSync(true)
            avviaTimerSync()
        }
    }

    private fun avviaTimerSync() {
        handlerSync.removeCallbacks(timerSync)
        val m = Sync.minFg(this)
        if (m > 0) handlerSync.postDelayed(timerSync, m * 60_000L)
    }
    private var syncAvvioFatto = false
    private var meseCal: YearMonth = YearMonth.now()
    private var casaCat = "Tutte"
    private var casaStato = "Tutti"
    private var penVista = 0
    private var invVista = 0
    private var invFiltro = ""
    private var invInCorso = false
    private var dietVista = 0
    private var dietSett = 0
    private var dietPassiMese = 0
    private var dietPassiAnno = LocalDate.now().year
    private var dietAlimentiControllati = false
    private var stuVista = 0
    private var stuMese: YearMonth = YearMonth.now()
    private var stuGiorno: LocalDate = LocalDate.now()
    private var stuModoApp = 0
    private var stuFiltroApp = "Tutti"
    private var stuFiltroDoc = "Tutti"
    private var stuFiltroCas = "Tutti"
    private var scadutiAperti = false
    private var catCerca = ""
    private var catOrdine = "Nome"
    private var catSoloVuote = false
    private var catUsciteAperte = true
    private var catEntrateAperte = true
    private var meseProm: YearMonth = YearMonth.now()
    private var giornoProm: LocalDate? = LocalDate.now()
    private var giornoCal: LocalDate? = LocalDate.now()
    private var pianCal = false
    private var schedaAperta: String? = null
    private var schedaFiltroId = ""
    private var schedaPeriodo = 0
    private var schedaCategoria = "Tutte"
    private var schedaLimite = 30
    private var grafVista = 0
    private var grafSempre = false
    private var grafAnno = LocalDate.now().year
    private var grafConto = ""
    private var grafFuturi = false
    private var homeTab = "Movimenti"
    private var docAperto: String? = null
    private var utenzaAperta: String? = null
    private var docQ = ""
    private var docCat = ""
    private var docTipo = ""
    private var docStaged: java.io.File? = null
    private var fsAnno = "Tutti"
    private var fsMese = 0
    private var fsParte = "Tutti"
    private var fsCat = "Tutte"
    private var fsStato = "Aperto"
    private var ckVista = 0
    private val rc = Ricerca.Filtri()
    private var rcTesto = ""
    private var rcFuturi = true
    private var ckAnno = LocalDate.now().year
    private var poolVista = 0
    private var poolDati: JSONObject? = null
    private var poolOra = 0L
    private var poolErrore: String? = null
    private var poolInCorso = false
    private var poolChiuse = false
    private var ultimoControlloFm = 0L
    private class GruppoHome(val titolo: String, val ico: String, val tessere: List<Tessera>)
    private val homeGruppi = ArrayList<GruppoHome>()

    private val apriZip =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) importaZip(uri)
        }
    private val vocale =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
            val t = r.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (r.resultCode == RESULT_OK && !t.isNullOrBlank()) {
                chiediBozza = t
                invioChiedi()
            }
        }
    private val chiediAllegati = ArrayList<Pair<java.io.File, String>>()
    private val apriAllegatiChiedi =
        registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            if (uris.isNullOrEmpty()) return@registerForActivityResult
            val dir = java.io.File(cacheDir, "chiedi").also { it.mkdirs() }
            for (u in uris) {
                if (chiediAllegati.size >= 4) {
                    aviso("Massimo 4 allegati")
                    break
                }
                try {
                    var nome = ""
                    contentResolver.query(u, null, null, null, null)?.use { c ->
                        if (c.moveToFirst()) {
                            val i = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                            if (i >= 0) nome = c.getString(i) ?: ""
                        }
                    }
                    var ext = nome.substringAfterLast('.', "").lowercase()
                    if (ext !in listOf("pdf", "jpg", "jpeg", "png", "webp", "heic")) {
                        ext = when (contentResolver.getType(u)) {
                            "application/pdf" -> "pdf"
                            "image/png" -> "png"
                            "image/webp" -> "webp"
                            "image/heic" -> "heic"
                            else -> "jpg"
                        }
                    }
                    val dest = java.io.File(dir, "all_" + System.nanoTime() + "." + ext)
                    contentResolver.openInputStream(u)!!.use { i -> dest.outputStream().use { o -> i.copyTo(o) } }
                    chiediAllegati.add(dest to nome.ifBlank { "allegato." + ext })
                } catch (e: Exception) {
                    aviso("File non leggibile: " + e.message)
                }
            }
            if (pagina == Pagina.CHIEDI) renderChiedi()
        }
    private var estrattoControllo = false
    private var estrattoInCorso = false
    private var revisioneAperta = false
    private val apriEstratto =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) leggiEstratto(uri, estrattoControllo)
        }
    private val apriPdfPensione =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) importaPensione(uri)
        }
    private val apriJsonInvest =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) importaInvestJson(uri)
        }
    private val apriDocumento =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@registerForActivityResult
            try {
                docStaged = Doc.prepara(this, uri)
            } catch (e: Exception) {
                aviso("File non leggibile: " + e.message)
                return@registerForActivityResult
            }
            if (pagina == Pagina.DOCPERS) dialogDocPers(null) else dialogDocContabile(null)
        }
    private val creaZip =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
            if (uri != null) esportaZip(uri)
        }

    private val scanPdf =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
            if (r.resultCode == RESULT_OK) {
                val uri = com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
                    .fromActivityResultIntent(r.data)?.pdf?.uri
                if (uri != null) salvaScansione(uri) else aviso("Nessun documento acquisito")
            }
        }

    private fun <T : View> vista(id: Int): T = findViewById(id)

    private fun aviso(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Repo.init(applicationContext)

        vista<View>(R.id.btnHero).setOnClickListener { dialogVoce(null) }
        vista<View>(R.id.btnAdd).setOnClickListener { dialogVoce(null) }
        vista<View>(R.id.btnBack).setOnClickListener { mostra(Pagina.HOME) }
        vista<View>(R.id.btnBackLista).setOnClickListener { indietroLista() }
        vista<View>(R.id.btnAddGen).setOnClickListener {
            when (pagina) {
                Pagina.CONTI -> dialogConto(null)
                Pagina.CATEGORIE -> dialogCategoria(null)
                Pagina.TARGET -> dialogTarget()
                Pagina.RICORRENZE -> dialogRicorrenza()
                Pagina.CALENDARIO -> dialogVoce(null, giornoCal)
                Pagina.FINANZIAMENTI -> dialogFin(null)
                Pagina.FONDO -> dialogObiettivoFondo()
                Pagina.CASACARE -> dialogCasaCare(null)
                Pagina.PENSIONE -> penAggiungi()
                Pagina.INVEST -> invAggiungi()
                Pagina.DIETA -> menuDieta()
                Pagina.STUDIO -> stuNuovo()
                Pagina.MARKET -> mkNuovo()
                Pagina.STOCK -> dialogStkArticolo(null)
                Pagina.PIANIFICA -> dialogNuovoPiano()
                Pagina.DOCUMENTI, Pagina.DOCPERS -> nuovoDocumento()
                Pagina.UTENZE -> dialogNuovaLettura()
                Pagina.GRAFICI -> dialogContoGrafici()
                Pagina.FAIRSHARE -> dialogPartecipante(null)
                Pagina.POOL -> caricaPool()
                Pagina.CHECKOUT -> {
                    ckVista = (ckVista + 1) % 3
                    renderCheckout()
                }
                Pagina.RICERCA -> dialogFiltriRicerca()
                Pagina.PREVISIONE -> mostra(Pagina.CATEGORIE)
                Pagina.PROMEMORIA -> dialogProm(null, LocalDate.now())
                Pagina.NOTIFICHE -> dialogNotifRegola(null)
                Pagina.AUTOPARK, Pagina.IMMOBIL, Pagina.PET -> {
                    val aperto = schedaAperta?.let { sc.per(it) }
                    if (aperto == null) dialogNuovaScheda() else dialogMovScheda(aperto, null)
                }
                Pagina.ESTRATTI -> menuImportaEstratto()
                Pagina.CHIEDI -> {
                    chiediStorico.clear()
                    chiediBozza = ""
                    chiediAllegati.forEach { it.first.delete() }
                    chiediAllegati.clear()
                    renderChiedi()
                }
                else -> {}
            }
        }
        vista<View>(R.id.btnAddGen2).setOnClickListener {
            if (pagina == Pagina.CONTI) {
                if (Repo.conti().size < 2) aviso("Servono almeno due conti") else dialogTrasf(null)
            } else if (pagina == Pagina.FINANZIAMENTI) {
                menuCalcolatori()
            } else if (pagina == Pagina.FONDO) {
                dialogImportiFondo()
            } else if (pagina == Pagina.CASACARE) {
                dialogFiltriCasa()
            } else if (pagina == Pagina.PENSIONE) {
                menuPdfPensione()
            } else if (pagina == Pagina.INVEST) {
                menuInvest()
            } else if (pagina == Pagina.DIETA) {
                menuDieta()
            } else if (pagina == Pagina.STUDIO) {
                menuStudio()
            } else if (pagina == Pagina.INFLAZIONE) {
                infImp = null
                renderLista()
            } else if (pagina == Pagina.MARKET) {
                menuMarket()
            } else if (pagina == Pagina.STOCK) {
                menuStock()
            } else if (pagina == Pagina.CATEGORIE) {
                menuStrumentiCategorie()
            } else if (pagina == Pagina.PROMEMORIA) {
                dialogAvvisi()
            } else if (pagina == Pagina.NOTIFICHE) {
                dialogNotifProva()
            } else if (pagina == Pagina.FAIRSHARE) {
                dialogFiltriFs()
            } else if (pagina == Pagina.POOL) {
                poolVista = (poolVista + 1) % 4
                renderPool()
            } else if (pagina == Pagina.CHECKOUT) {
                dialogAvvisoCk()
            } else if (pagina == Pagina.RICERCA) {
                condividiRicerca()
            } else if (pagina == Pagina.ESTRATTI) {
                condividiEstratti()

            } else if (pagina == Pagina.DOCUMENTI) {
                dialogFiltriDoc()
            } else if (pagina == Pagina.DOCPERS) {
                dialogProfili()
            } else if (schedaDi(pagina) != null) {
                schedaAperta?.let { sc.per(it) }?.let { menuScheda(it) }
            }
        }

        frecciaPiccola(vista<MaterialButton>(R.id.btnPrev))
        frecciaPiccola(vista<MaterialButton>(R.id.btnNext))
        vista<View>(R.id.btnPrev).setOnClickListener {
            mese = mese.minusMonths(1)
            renderMovimenti()
        }
        vista<View>(R.id.btnNext).setOnClickListener {
            mese = mese.plusMonths(1)
            renderMovimenti()
        }
        vista<View>(R.id.boxMese).setOnClickListener {
            mese = YearMonth.now()
            renderMovimenti()
        }
        vista<TextInputEditText>(R.id.etSearch).doAfterTextChanged { renderMovimenti() }
        vista<View>(R.id.btnSblocca).setOnClickListener { chiediAutenticazione() }

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (pagina != Pagina.HOME) {
                        indietroLista()
                    } else {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        )

        val radice = window.decorView
        radice.viewTreeObserver.addOnGlobalLayoutListener {
            val r = android.graphics.Rect()
            radice.getWindowVisibleDisplayFrame(r)
            val tastiera = radice.height - r.bottom > 200 * resources.displayMetrics.density
            vista<View>(R.id.barraNav).visibility = if (tastiera) View.GONE else View.VISIBLE
        }

        val paginaSalvata = savedInstanceState?.getString("pagina")?.let { n -> Pagina.values().firstOrNull { it.name == n } }
        savedInstanceState?.getString("mese")?.let { runCatching { mese = YearMonth.parse(it) } }
        savedInstanceState?.getString("meseCal")?.let { runCatching { meseCal = YearMonth.parse(it) } }
        mostra(paginaSalvata ?: Pagina.HOME)
        if (savedInstanceState?.getBoolean("sbloccata") == true) {
            sbloccata = true
            mostraBlocco(false)
        } else if (Repo.lockOn) {
            mostraBlocco(true)
            chiediAutenticazione()
        } else {
            sbloccata = true
            mostraBlocco(false)
        }
        if (intent?.getStringExtra("apri") == "promemoria") mostra(Pagina.PROMEMORIA)
        if (intent?.getStringExtra("apri") == "notifiche") mostra(Pagina.NOTIFICHE)
        if (intent?.getStringExtra("apri") == "casacare") mostra(Pagina.CASACARE)
        Avvisi.iniziaDa(applicationContext)
        Notif.applicaStato(applicationContext)
        if (savedInstanceState == null) controllaAggiornamentiAvvio()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getStringExtra("apri") == "promemoria") mostra(Pagina.PROMEMORIA)
        if (intent.getStringExtra("apri") == "notifiche") mostra(Pagina.NOTIFICHE)
        if (intent.getStringExtra("apri") == "casacare") mostra(Pagina.CASACARE)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("pagina", pagina.name)
        outState.putString("mese", mese.toString())
        outState.putString("meseCal", meseCal.toString())
        outState.putBoolean("sbloccata", sbloccata)
    }

    override fun onStop() {
        super.onStop()
        ultimoStop = SystemClock.elapsedRealtime()
        handlerSync.removeCallbacks(timerSync)
        Notif.osservatore = null
    }

    override fun onResume() {
        super.onResume()
        controllaFineMese()
        Avvisi.pianifica(applicationContext)
        if (pagina == Pagina.NOTIFICHE) renderLista()
        val avvio = !syncAvvioFatto
        syncAvvioFatto = true
        if (Sync.daAutoSync(this, avvio)) eseguiSync(true, avvio)
        avviaTimerSync()
        SyncBg.assicura(applicationContext)
    }

    override fun onStart() {
        super.onStart()
        Notif.osservatore = { runOnUiThread { if (pagina == Pagina.NOTIFICHE || pagina == Pagina.HOME) refresh() } }
        if (Repo.lockOn && sbloccata && ultimoStop > 0 &&
            SystemClock.elapsedRealtime() - ultimoStop > Repo.lockSec * 1000L
        ) {
            sbloccata = false
            mostraBlocco(true)
            chiediAutenticazione()
        }
    }

    private fun mostraBlocco(si: Boolean) {
        vista<View>(R.id.lockOverlay).visibility = if (si) View.VISIBLE else View.GONE
        if (si) {
            vista<android.widget.ImageView>(R.id.imgLogoLock).let { iv ->
                iv.outlineProvider = object : android.view.ViewOutlineProvider() {
                    override fun getOutline(v: View, o: android.graphics.Outline) {
                        o.setRoundRect(0, 0, v.width, v.height, v.width * 0.22f)
                    }
                }
                iv.clipToOutline = true
            }
            val t = getSharedPreferences("orbita", MODE_PRIVATE).getLong("ultimo_accesso", 0L)
            vista<TextView>(R.id.txtUltimoAccesso).text = if (t > 0) "Ultimo accesso: " + fmtAccesso(t) else ""
        }
    }

    private fun meteoAnimato(iv: android.widget.ImageView, positivo: Boolean) {
        (iv.tag as? android.animation.Animator)?.cancel()
        iv.rotation = 0f
        iv.alpha = 1f
        val an: android.animation.ValueAnimator
        if (positivo) {
            iv.setImageResource(R.drawable.ic_meteo_sole)
            an = android.animation.ValueAnimator.ofFloat(0f, 360f).apply {
                duration = 960
                interpolator = android.animation.TimeInterpolator { (it * 12).toInt() / 12f }
                addUpdateListener { iv.rotation = it.animatedValue as Float }
            }
        } else {
            iv.setImageResource(R.drawable.ic_meteo_temporale)
            an = android.animation.ValueAnimator.ofFloat(0f, 8f).apply {
                duration = 960
                interpolator = android.view.animation.LinearInterpolator()
                addUpdateListener {
                    val i = (it.animatedValue as Float).toInt().coerceAtMost(7)
                    iv.alpha = 0.5f + 0.5f * kotlin.math.sin(Math.PI * i / 7).toFloat()
                }
            }
        }
        an.repeatCount = android.animation.ValueAnimator.INFINITE
        an.repeatMode = android.animation.ValueAnimator.RESTART
        iv.tag = an
        iv.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {}
            override fun onViewDetachedFromWindow(v: View) { an.cancel() }
        })
        an.start()
    }

    private fun nuovoMeteo(positivo: Boolean, lato: Int): android.widget.ImageView {
        val iv = android.widget.ImageView(this)
        iv.contentDescription = "Meteo del saldo"
        iv.scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
        iv.layoutParams = LinearLayout.LayoutParams(lato, lato).also {
            it.marginStart = (6 * resources.displayMetrics.density).toInt()
        }
        meteoAnimato(iv, positivo)
        return iv
    }

    private fun frecciaPiccola(b: MaterialButton) {
        val dp = resources.displayMetrics.density
        b.insetTop = 0
        b.insetBottom = 0
        b.minWidth = 0
        b.minimumWidth = 0
        b.minHeight = 0
        b.minimumHeight = 0
        b.setPadding((10 * dp).toInt(), 0, (10 * dp).toInt(), 0)
        b.textSize = 13f
        b.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, (36 * dp).toInt())
    }

    private fun fmtAccesso(t: Long): String =
        java.time.LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(t), java.time.ZoneId.systemDefault())
            .format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm"))

    private fun chiediAutenticazione() {
        val auth = Lock.authenticators()
        if (!Lock.disponibile(this)) {
            sbloccata = true
            mostraBlocco(false)
            return
        }
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    val sp = getSharedPreferences("orbita", MODE_PRIVATE)
                    val prec = sp.getLong("ultimo_accesso", 0L)
                    sp.edit().putLong("accesso_prec", prec)
                        .putLong("ultimo_accesso", System.currentTimeMillis()).apply()
                    sbloccata = true
                    mostraBlocco(false)
                    controllaFineMese()
                }
            }
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("OrbitaCasa")
            .setSubtitle("Sblocca con impronta o blocco schermo")
            .setAllowedAuthenticators(auth)
        if ((auth and DEVICE_CREDENTIAL) == 0) info.setNegativeButtonText("Annulla")
        prompt.authenticate(info.build())
    }

    private fun bloccaOra() {
        if (!Repo.lockOn) {
            aviso("Attiva prima il Blocco App")
            return
        }
        sbloccata = false
        mostraBlocco(true)
        chiediAutenticazione()
    }

    private val LOCK_OPZIONI = listOf(
        "Disattivato" to 0,
        "Dopo 30 secondi" to 30,
        "Dopo 1 minuto" to 60,
        "Dopo 2 minuti" to 120,
        "Dopo 3 minuti" to 180,
    )

    private fun lockEtichetta(): String = when {
        !Repo.lockOn -> "no"
        Repo.lockSec < 60 -> Repo.lockSec.toString() + "s"
        else -> (Repo.lockSec / 60).toString() + " min"
    }

    private fun cambiaBlocco() {
        val corrente = if (Repo.lockOn) Repo.lockSec else 0
        OcBuilder(this)
            .setTitle("Blocco app")
            .setItems(
                LOCK_OPZIONI.map { (if (it.second == corrente) "● " else "○ ") + it.first }.toTypedArray()
            ) { _, i ->
                val sec = LOCK_OPZIONI[i].second
                if (sec == 0) {
                    Repo.setLock(false)
                } else if (Lock.disponibile(this)) {
                    val eraAttivo = Repo.lockOn
                    Repo.setLockSec(sec)
                    Repo.setLock(true)
                    if (!eraAttivo) sbloccata = true
                } else {
                    aviso("Imposta prima un'impronta o un blocco schermo sul telefono")
                }
                renderHome()
            }
            .setNegativeButton("Chiudi", null)
            .show()
    }

    private fun mostra(p: Pagina) {
        if (p != Pagina.STOCK) stkSel = null
        pagina = p
        vista<View>(R.id.pageHome).visibility = if (p == Pagina.HOME) View.VISIBLE else View.GONE
        vista<View>(R.id.pageMovimenti).visibility = if (p == Pagina.MOVIMENTI) View.VISIBLE else View.GONE
        val lista = p == Pagina.CONTI || p == Pagina.CATEGORIE || p == Pagina.ANALISI || p == Pagina.TARGET || p == Pagina.RICORRENZE || p == Pagina.CALENDARIO || p == Pagina.FINANZIAMENTI || p == Pagina.AUTOPARK || p == Pagina.IMMOBIL || p == Pagina.PET || p == Pagina.FONDO || p == Pagina.GRAFICI || p == Pagina.PIANIFICA || p == Pagina.DOCUMENTI || p == Pagina.DOCPERS || p == Pagina.UTENZE || p == Pagina.FAIRSHARE || p == Pagina.POOL || p == Pagina.CHECKOUT || p == Pagina.RICERCA || p == Pagina.PREVISIONE || p == Pagina.PROMEMORIA || p == Pagina.NOTIFICHE || p == Pagina.CASACARE || p == Pagina.PENSIONE || p == Pagina.DIETA || p == Pagina.STUDIO || p == Pagina.INFLAZIONE || p == Pagina.MARKET || p == Pagina.STOCK || p == Pagina.INFO || p == Pagina.ESTRATTI || p == Pagina.CHIEDI || p == Pagina.INVEST
        vista<View>(R.id.pageLista).visibility = if (lista) View.VISIBLE else View.GONE
        when (p) {
            Pagina.HOME -> renderHome()
            Pagina.MOVIMENTI -> renderMovimenti()
            Pagina.POOL -> {
                renderLista()
                caricaPool()
            }
            else -> renderLista()
        }
        renderBarra()
    }

    private fun renderHome() {
        val ym = YearMonth.now()
        val delMese = Repo.voci().filter { YearMonth.from(it.data) == ym }
        val entrate = delMese.filter { it.tipo == "Entrata" }.sumOf { it.importo }
        val uscite = delMese.filter { it.tipo == "Uscita" }.sumOf { it.importo }
        val saldo = entrate - uscite
        vista<TextView>(R.id.rMese).text = maiuscola(ym.format(FMT_MESE))
        meteoAnimato(vista(R.id.imgMeteoHome), saldo >= 0)
        vista<TextView>(R.id.tvAccessoPrec).let { t ->
            val prec = getSharedPreferences("orbita", MODE_PRIVATE).getLong("accesso_prec", 0L)
            if (Repo.lockOn && prec > 0) {
                t.text = "Accesso precedente: " + fmtAccesso(prec)
                t.visibility = View.VISIBLE
            } else {
                t.visibility = View.INVISIBLE
            }
        }
        vista<TextView>(R.id.tvSyncHome).let { t ->
            val ult = if (Sync.configurato(this)) Sync.ultima(this) else 0L
            if (ult > 0) {
                val d = java.time.LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(ult), java.time.ZoneId.systemDefault())
                val quando = if (d.toLocalDate() == java.time.LocalDate.now()) "oggi " + d.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
                    else d.format(java.time.format.DateTimeFormatter.ofPattern("dd-MM HH:mm"))
                t.text = "Ultima sync: $quando"
                t.visibility = View.VISIBLE
            } else {
                t.visibility = View.GONE
            }
        }
        vista<TextView>(R.id.tvProfiloHome).let { t ->
            val prof = if (Sync.configurato(this)) Sync.profilo(this) else ""
            if (prof.isBlank()) {
                t.visibility = View.GONE
            } else {
                t.text = "👤 $prof"
                t.visibility = View.VISIBLE
            }
        }
        vista<TextView>(R.id.rEntrate).text = euro(entrate)
        vista<TextView>(R.id.rUscite).text = euro(uscite)
        val tvSaldo = vista<TextView>(R.id.rSaldo)
        tvSaldo.text = (if (saldo >= 0) "+" else "−") + euro(abs(saldo))
        tvSaldo.setTextColor(ContextCompat.getColor(this, if (saldo >= 0) R.color.oc_green else R.color.oc_red))

        riepilogoHome()
        preparaPrivacy()
        val cont = vista<LinearLayout>(R.id.groups)
        cont.removeAllViews()
        homeGruppi.clear()
        if (Repo.demoAttiva) {
            val banner = TextView(this)
            banner.text = "🎭 MODALITÀ DEMO: stai vedendo dati di esempio. I tuoi dati sono al sicuro. Tocca per uscire."
            banner.setTextColor(ContextCompat.getColor(this, R.color.oc_orange))
            banner.setTypeface(null, Typeface.BOLD)
            banner.setPadding(24, 16, 24, 16)
            banner.setOnClickListener { confermaEsciDemo() }
            cont.addView(banner)
        }
        gruppo(
            cont, "Movimenti", "🗓️",
            listOf(
                Tessera("📈", "Movimenti Mese") { mostra(Pagina.MOVIMENTI) },
                Tessera("🗓️", "Calendario") { mostra(Pagina.CALENDARIO) },
                Tessera("📌", "Pianifica") { mostra(Pagina.PIANIFICA) },
                Tessera("🔁", "Ricorrenze" + if (RicAuto.trova(Repo.voci(), Repo.ricorrenze(), LocalDate.now()).isNotEmpty()) " 💡" else "") { mostra(Pagina.RICORRENZE) },
                Tessera("🔔", "Promemoria" + Prom.conteggio().totale.let { if (it > 0) " ($it)" else "" }) { mostra(Pagina.PROMEMORIA) },
                Tessera("📩", "Notifiche" + Notif.nAttesa().let { if (it > 0) " ($it)" else "" }) { mostra(Pagina.NOTIFICHE) },
                Tessera("🔎", "Cerca Operazioni") { mostra(Pagina.RICERCA) },
                Tessera("🛒", "Supermercati") { mostra(Pagina.MARKET) },
                Tessera("📦", "StockBox") { mostra(Pagina.STOCK) },
            )
        )
        gruppo(
            cont, "Analisi", "📊",
            listOf(
                Tessera("📊", "Grafici") { mostra(Pagina.GRAFICI) },
                Tessera("🔍", "Analisi Categorie") { mostra(Pagina.ANALISI) },
                Tessera("🔁", "Checkout") { mostra(Pagina.CHECKOUT) },
                Tessera("📉", "Previsione") { mostra(Pagina.PREVISIONE) },
                Tessera("💳", "Estratti") { mostra(Pagina.ESTRATTI) },
                Tessera("🤖", "Chiedi al Database") { mostra(Pagina.CHIEDI) },
            )
        )
        gruppo(
            cont, "Finanze", "🗂️",
            listOf(
                Tessera("🏦", "Conti") { mostra(Pagina.CONTI) },
                Tessera("🏷️", "Categorie") { mostra(Pagina.CATEGORIE) },
                Tessera("🎯", "Target") { mostra(Pagina.TARGET) },
                Tessera("💶", "Finanziamenti") { mostra(Pagina.FINANZIAMENTI) },
                Tessera("💰", "Fondo Risparmio") { mostra(Pagina.FONDO) },
                Tessera("🏖️", "Fondo Pensione") { mostra(Pagina.PENSIONE) },
                Tessera("💹", "Investimenti") { mostra(Pagina.INVEST) },
                Tessera("💼", "MyBusiness") { mostra(Pagina.STUDIO) },
                Tessera("📈", "Inflazione") { mostra(Pagina.INFLAZIONE) },
            )
        )
        gruppo(
            cont, "Casa e beni", "🏠",
            listOf(
                Tessera("🧾", "Doc. Contabili") { mostra(Pagina.DOCUMENTI) },
                Tessera("🪪", "Doc. Personali") { mostra(Pagina.DOCPERS) },
                Tessera("💡", "Utenze") { mostra(Pagina.UTENZE) },
                Tessera("🛠️", "CasaCare") { mostra(Pagina.CASACARE) },
                Tessera("🤝", "FairShare") { mostra(Pagina.FAIRSHARE) },
                Tessera("👥", "CommunityPool") { mostra(Pagina.POOL) },
                Tessera("🚗", "AutoPark") { schedaAperta = null; mostra(Pagina.AUTOPARK) },
                Tessera("🏢", "ImmoBil") { schedaAperta = null; mostra(Pagina.IMMOBIL) },
                Tessera("🐾", "Pet Care") { schedaAperta = null; mostra(Pagina.PET) },
                Tessera("🥗", "Dieta") { mostra(Pagina.DIETA) },
            )
        )
        gruppo(
            cont, "Strumenti", "🛠️",
            listOf(
                Tessera("🔀", "Importa / Esporta") { dialogImportaEsporta() },
                Tessera("🗂️", "Copie di sicurezza") { dialogCopie() },
                Tessera("🧹", "Azzera dati") { dialogAzzera() },
                Tessera("🗄️", "Storico: " + if (Repo.retentionOn) "${Repo.retentionAnni} anni" else "tutto") { dialogRetention() },
                Tessera("📄", "Scansiona PDF") { scansionaPdf() },
                Tessera("🔗" + if (Sync.configurato(this)) " " + Sync.ultima(this).let { if (it > 0) java.text.SimpleDateFormat("HH:mm", Locale.ITALY).format(java.util.Date(it)) else "--:--" } else "", "Sync") { dialogSync() },
                Tessera("🤖", "Chiave Gemini: " + if (Gemini.chiave(this).isEmpty()) "no" else "ok") { dialogChiaveGemini { refresh() } },
                Tessera("🎭", if (Repo.demoAttiva) "Esci dalla demo" else "Modalità demo") {
                    if (Repo.demoAttiva) confermaEsciDemo() else confermaEntraDemo()
                },
                Tessera("🔄", "Aggiornamenti") { cercaAggiornamenti() },
                Tessera("ℹ️", "Info e Contatti") { mostra(Pagina.INFO) },
                Tessera("🧭", "Personalizza") { dialogBarra() },
                Tessera("🔒", "Blocco App: " + lockEtichetta()) { cambiaBlocco() },
                Tessera("💡", "SmartCat: " + if (Repo.smartCatOn) "attiva" else "no") {
                    Repo.setSmartCat(!Repo.smartCatOn)
                    renderHome()
                },
                Tessera("🔁", "Duplicati: " + if (Repo.checkDoppi) "attivo" else "no") {
                    Repo.setCheckDoppi(!Repo.checkDoppi)
                    renderHome()
                },
                Tessera("🙈", "Blur saldi: " + if (Repo.privacy) "attivo" else "no") {
                    Repo.setPrivacy(!Repo.privacy)
                    renderHome()
                    applicaPrivacy()
                },
            )
        )
        tabsHome(cont)
    }

    private val gestoPrivacy by lazy {
        android.view.GestureDetector(this, object : android.view.GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: android.view.MotionEvent): Boolean {
                Repo.setPrivacy(!Repo.privacy)
                applicaPrivacy()
                aviso(if (Repo.privacy) "Importi nascosti" else "Importi visibili")
                return true
            }
        })
    }

    private fun preparaPrivacy() {
        vista<View>(R.id.schedaRiep).setOnTouchListener { _, e ->
            gestoPrivacy.onTouchEvent(e)
            true
        }
        applicaPrivacy()
    }

    private fun applicaPrivacy() {
        val raggio = 3f * resources.displayMetrics.density
        for (id in listOf(R.id.rEntrate, R.id.rUscite, R.id.rSaldo, R.id.blocco503020)) {
            val v = vista<View>(id)
            if (Build.VERSION.SDK_INT >= 31) {
                v.setRenderEffect(
                    if (Repo.privacy) android.graphics.RenderEffect.createBlurEffect(raggio, raggio, android.graphics.Shader.TileMode.CLAMP) else null
                )
            } else {
                v.alpha = if (Repo.privacy) 0.08f else 1f
            }
        }
    }

    private fun riepilogoHome() {
        val box = vista<LinearLayout>(R.id.blocco503020)
        box.removeAllViews()
        val dp = resources.displayMetrics.density
        val verde = ContextCompat.getColor(this, R.color.oc_green)
        val rosso = ContextCompat.getColor(this, R.color.oc_red)
        val blu = ContextCompat.getColor(this, R.color.oc_blue)
        val arancio = ContextCompat.getColor(this, R.color.oc_orange)
        val medio = ContextCompat.getColor(this, R.color.oc_text_mid)
        val tutte = Repo.voci()

        val q = Repo.calcola503020(tutte.filter { YearMonth.from(it.data) == mese })
        if (q.entrate <= 0 || (q.bisogni + q.desideri + q.risparmio) <= 0) {
            val t = TextView(this)
            t.text = "50/30/20: servono entrate e categorie con la classe assegnata."
            t.textSize = 11f
            t.setTextColor(medio)
            box.addView(t)
        } else {
            val b = maxOf(q.bisogni, 0.0)
            val d = maxOf(q.desideri, 0.0)
            val r = maxOf(q.risparmio, 0.0)
            val somma = b + d + r
            val totale = maxOf(somma, q.entrate)
            val barra = LinearLayout(this)
            barra.orientation = LinearLayout.HORIZONTAL
            barra.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (8 * dp).toInt())
            fun pezzo(valore: Double, colore: Int) {
                if (valore <= 0) return
                val v = View(this)
                v.setBackgroundColor(colore)
                v.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, (valore / totale).toFloat())
                barra.addView(v)
            }
            pezzo(b, blu)
            pezzo(d, arancio)
            pezzo(r, verde)
            pezzo(totale - somma, ContextCompat.getColor(this, R.color.oc_surface3))
            box.addView(barra)

            val legenda = LinearLayout(this)
            legenda.orientation = LinearLayout.HORIZONTAL
            val lpLeg = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lpLeg.topMargin = (5 * dp).toInt()
            legenda.layoutParams = lpLeg
            fun voceLeg(nome: String, pallino: Int, valore: Double, ok: Boolean) {
                val t = TextView(this)
                val pct = String.format(Locale.ITALY, "%.0f%%", valore / q.entrate * 100)
                val s = android.text.SpannableString("● $nome $pct")
                s.setSpan(android.text.style.ForegroundColorSpan(pallino), 0, 1, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                s.setSpan(android.text.style.ForegroundColorSpan(if (ok) verde else rosso), 1, s.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                t.text = s
                t.textSize = 11f
                t.maxLines = 1
                t.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                legenda.addView(t)
            }
            voceLeg("Bisogni", blu, q.bisogni, q.bisogni <= q.entrate * 0.5)
            voceLeg("Desideri", arancio, q.desideri, q.desideri <= q.entrate * 0.3)
            voceLeg("Risparmio", verde, q.risparmio, q.risparmio >= q.entrate * 0.2)
            box.addView(legenda)
        }

        if (Repo.targetMese > 0 || Repo.targetAnno > 0) {
            val anno = LocalDate.now().year
            val spesoMese = Repo.uscite(mese.atDay(1), mese.atEndOfMonth()).sumOf { it.importo }
            val spesoAnno = Repo.uscite(LocalDate.of(anno, 1, 1), LocalDate.of(anno, 12, 31)).sumOf { it.importo }
            val tgt = LinearLayout(this)
            tgt.orientation = LinearLayout.VERTICAL
            val lpT = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lpT.topMargin = (10 * dp).toInt()
            tgt.layoutParams = lpT
            tgt.setOnClickListener { mostra(Pagina.TARGET) }
            fun e0(x: Double) = String.format(Locale.ITALY, "%,.0f", x)
            fun rigaTarget(titolo: String, speso: Double, limite: Double) {
                val resto = limite - speso
                val pct = speso / limite * 100.0
                val col = ContextCompat.getColor(
                    this, when { pct >= 100 -> R.color.oc_red; pct >= 80 -> R.color.oc_orange; else -> R.color.oc_green }
                )
                val testa = LinearLayout(this)
                testa.orientation = LinearLayout.HORIZONTAL
                val tl = TextView(this)
                tl.text = titolo
                tl.textSize = 11f
                tl.setTextColor(medio)
                tl.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                val tr = TextView(this)
                tr.text = e0(speso) + " di " + e0(limite) + " · " + (if (resto >= 0) "restano " + e0(resto) else "sforato di " + e0(-resto))
                tr.textSize = 11f
                tr.maxLines = 1
                testa.addView(tl)
                testa.addView(tr)
                tgt.addView(testa)
                val pb = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal)
                pb.max = 100
                pb.progress = pct.toInt().coerceIn(0, 100)
                pb.progressTintList = ColorStateList.valueOf(col)
                val plp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (8 * dp).toInt())
                plp.topMargin = (3 * dp).toInt()
                plp.bottomMargin = (6 * dp).toInt()
                pb.layoutParams = plp
                tgt.addView(pb)
            }
            if (Repo.targetMese > 0) rigaTarget("Target mese", spesoMese, Repo.targetMese)
            if (Repo.targetAnno > 0) rigaTarget("Target anno", spesoAnno, Repo.targetAnno)
            box.addView(tgt)
        }

        val pr = Fondo.analizza(tutte, 0.0, emptyList())
        if (pr.attendibile && (pr.eProj > 0 || pr.uProj > 0)) {
            val pctPr = if (pr.uProj > 0) (pr.eProj / pr.uProj).coerceIn(0.0, 1.0) else 1.0
            val colPr = ContextCompat.getColor(
                this, when { pctPr >= 1.0 -> R.color.oc_green; pctPr >= 0.5 -> R.color.oc_orange; else -> R.color.oc_red }
            )
            val tess = LinearLayout(this)
            tess.orientation = LinearLayout.VERTICAL
            val lpP = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lpP.topMargin = (10 * dp).toInt()
            tess.layoutParams = lpP
            tess.setOnClickListener { mostra(Pagina.FONDO) }
            val testaP = LinearLayout(this)
            testaP.orientation = LinearLayout.HORIZONTAL
            val tlP = TextView(this)
            tlP.text = "Previsione " + LocalDate.now().year
            tlP.textSize = 12f
            tlP.setTypeface(null, Typeface.BOLD)
            tlP.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            val trP = TextView(this)
            trP.text = segnoEuro(pr.rProj)
            trP.textSize = 12f
            trP.setTypeface(null, Typeface.BOLD)
            trP.setTextColor(if (pr.rProj >= 0) verde else rosso)
            testaP.addView(tlP)
            testaP.addView(trP)
            tess.addView(testaP)
            val pbP = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal)
            pbP.max = 100
            pbP.progress = (pctPr * 100).toInt()
            pbP.progressTintList = ColorStateList.valueOf(colPr)
            val plpP = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (8 * dp).toInt())
            plpP.topMargin = (3 * dp).toInt()
            pbP.layoutParams = plpP
            tess.addView(pbP)
            box.addView(tess)
        }
    }

    private class Dest(val pag: Pagina, val ico: String, val lbl: String)

    private val destinazioni = listOf(
        Dest(Pagina.MOVIMENTI, "📈", "Movimenti"),
        Dest(Pagina.CALENDARIO, "🗓️", "Calendario"),
        Dest(Pagina.GRAFICI, "📊", "Grafici"),
        Dest(Pagina.ANALISI, "🔍", "Analisi"),
        Dest(Pagina.CONTI, "🏦", "Conti"),
        Dest(Pagina.CATEGORIE, "🏷️", "Categorie"),
        Dest(Pagina.TARGET, "🎯", "Target"),
        Dest(Pagina.RICORRENZE, "🔁", "Ricorrenze"),
        Dest(Pagina.PIANIFICA, "📌", "Pianifica"),
        Dest(Pagina.PROMEMORIA, "🔔", "Promemoria"),
        Dest(Pagina.NOTIFICHE, "📩", "Notifiche"),
        Dest(Pagina.FINANZIAMENTI, "💶", "Finanz."),
        Dest(Pagina.FONDO, "💰", "Fondo"),
        Dest(Pagina.PENSIONE, "🏖️", "Pensione"),
        Dest(Pagina.INVEST, "💹", "Investimenti"),
        Dest(Pagina.DIETA, "🥗", "Dieta"),
        Dest(Pagina.STUDIO, "💼", "MyBusiness"),
        Dest(Pagina.INFLAZIONE, "📈", "Inflazione"),
        Dest(Pagina.DOCUMENTI, "🧾", "Contabili"),
        Dest(Pagina.DOCPERS, "🪪", "Personali"),
        Dest(Pagina.STOCK, "📦", "StockBox"),
        Dest(Pagina.UTENZE, "💡", "Utenze"),
        Dest(Pagina.MARKET, "🛒", "Spesa"),
        Dest(Pagina.FAIRSHARE, "🤝", "FairShare"),
        Dest(Pagina.POOL, "👥", "Pool"),
        Dest(Pagina.CHECKOUT, "🔁", "Checkout"),
        Dest(Pagina.RICERCA, "🔎", "Cerca"),
        Dest(Pagina.PREVISIONE, "📉", "Previsione"),
        Dest(Pagina.ESTRATTI, "💳", "Estratti"),
        Dest(Pagina.CHIEDI, "🤖", "Chiedi AI"),
        Dest(Pagina.AUTOPARK, "🚗", "AutoPark"),
        Dest(Pagina.IMMOBIL, "🏢", "ImmoBil"),
        Dest(Pagina.PET, "🐾", "Pet Care"),
        Dest(Pagina.CASACARE, "🛠️", "CasaCare"),
    )

    private val BARRA_BLOCCA = "BLOCCA"

    private fun apriDest(p: Pagina) {
        if (schedaDi(p) != null) schedaAperta = null
        mostra(p)
    }

    private fun renderBarra() {
        val barra = vista<LinearLayout>(R.id.barraNav)
        barra.removeAllViews()
        val attivo = ContextCompat.getColor(this, R.color.oc_gold)
        val spento = ContextCompat.getColor(this, R.color.oc_text_mid)
        val sel = android.util.TypedValue()
        theme.resolveAttribute(android.R.attr.selectableItemBackground, sel, true)

        fun voce(ico: String, lbl: String, on: Boolean, click: () -> Unit, lungo: () -> Unit) {
            val v = LinearLayout(this)
            v.orientation = LinearLayout.VERTICAL
            v.gravity = android.view.Gravity.CENTER
            v.setPadding(0, 10, 0, 10)
            v.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            v.setBackgroundResource(sel.resourceId)
            val i = TextView(this)
            i.text = ico
            i.textSize = 20f
            i.gravity = android.view.Gravity.CENTER
            val t = TextView(this)
            t.text = lbl
            t.textSize = 10f
            t.gravity = android.view.Gravity.CENTER
            t.setTextColor(if (on) attivo else spento)
            t.maxLines = 1
            t.ellipsize = android.text.TextUtils.TruncateAt.END
            if (on) t.setTypeface(null, Typeface.BOLD)
            v.addView(i)
            v.addView(t)
            v.setOnClickListener { click() }
            v.setOnLongClickListener { lungo(); true }
            barra.addView(v)
        }

        voce("🏠", "Home", pagina == Pagina.HOME, { mostra(Pagina.HOME) }, { aviso("La Home è fissa") })
        Repo.barra.forEachIndexed { slot, nome ->
            if (nome == BARRA_BLOCCA) {
                voce("🔐", "Blocca", false, { bloccaOra() }, { dialogSceltaBarra(slot) })
                return@forEachIndexed
            }
            val d = destinazioni.firstOrNull { it.pag.name == nome } ?: return@forEachIndexed
            voce(d.ico, d.lbl, pagina == d.pag, { apriDest(d.pag) }, { dialogSceltaBarra(slot) })
        }
    }

    private fun titoloSpiegato(titolo: String, testo: String): View {
        val dp = resources.displayMetrics.density
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding((24 * dp).toInt(), (20 * dp).toInt(), (24 * dp).toInt(), (8 * dp).toInt())
        val t = TextView(this)
        t.text = titolo
        t.textSize = 22f
        t.setTextColor(com.google.android.material.color.MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface, 0))
        val m = TextView(this)
        m.text = testo
        m.textSize = 14f
        m.setTextColor(com.google.android.material.color.MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant, 0))
        m.setPadding(0, (8 * dp).toInt(), 0, 0)
        box.addView(t)
        box.addView(m)
        return box
    }

    private fun apriCalendario(et: TextInputEditText) {
        val cur = Ricerca.dataLibera(et.text.toString()) ?: LocalDate.now()
        val dp = DatePickerDialog(
            this,
            { _, y, m, d -> et.setText(LocalDate.of(y, m + 1, d).format(FMT_DATA)) },
            cur.year, cur.monthValue - 1, cur.dayOfMonth
        )
        dp.setButton(android.content.DialogInterface.BUTTON_NEUTRAL, "Svuota") { _, _ -> et.setText("") }
        dp.show()
    }

    private fun dialogSceltaBarra(slot: Int) {
        val coll = java.text.Collator.getInstance(Locale.ITALY).apply { strength = java.text.Collator.PRIMARY }
        val ordinate = destinazioni.sortedWith { a, b -> coll.compare(a.lbl, b.lbl) }
            .map { Triple(it.pag.name, it.ico, it.lbl) } + Triple(BARRA_BLOCCA, "🔐", "Blocca ora")
        OcBuilder(this)
            .setCustomTitle(titoloSpiegato("Posizione " + (slot + 1) + " della barra", "Scegli la pagina o l'azione da mostrare in questa posizione. Se è già nella barra, le due pagine si scambiano di posto."))
            .setItems(ordinate.map { it.second + "  " + it.third }.toTypedArray()) { _, k ->
                val nuova = ordinate[k].first
                val l = Repo.barra.toMutableList()
                val gia = l.indexOf(nuova)
                if (gia >= 0) l[gia] = l[slot]
                l[slot] = nuova
                Repo.setBarra(l)
                renderBarra()
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun dialogBarra() {
        val voci = Repo.barra.mapIndexed { i, nome ->
            val d = destinazioni.firstOrNull { it.pag.name == nome }
            (i + 1).toString() + " · " + (d?.let { it.ico + " " + it.lbl } ?: if (nome == BARRA_BLOCCA) "🔐 Blocca ora" else nome)
        }
        OcBuilder(this)
            .setCustomTitle(titoloSpiegato("Personalizza barra", "Tocca una posizione per cambiarne la pagina. La Home resta sempre la prima. Scorciatoia: tieni premuta un'icona direttamente nella barra."))
            .setItems(voci.toTypedArray()) { _, k -> dialogSceltaBarra(k) }
            .setNegativeButton("Chiudi", null)
            .show()
    }

    private fun gruppo(cont: LinearLayout, titolo: String, ico: String, tessere: List<Tessera>) {
        homeGruppi.add(GruppoHome(titolo, ico, tessere))
    }

    private fun tabsHome(cont: LinearLayout) {
        if (homeGruppi.isEmpty()) return
        if (homeGruppi.none { it.titolo == homeTab }) homeTab = homeGruppi[0].titolo
        val dp = resources.displayMetrics.density
        val attivo = ContextCompat.getColor(this, R.color.oc_gold)
        val spento = ContextCompat.getColor(this, R.color.oc_text_mid)
        val barra = LinearLayout(this)
        barra.orientation = LinearLayout.HORIZONTAL
        barra.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        val corpo = LinearLayout(this)
        corpo.orientation = LinearLayout.VERTICAL
        val lpCorpo = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lpCorpo.topMargin = (8 * dp).toInt()
        corpo.layoutParams = lpCorpo

        fun disegna() {
            barra.removeAllViews()
            corpo.removeAllViews()
            for (g in homeGruppi) {
                val on = g.titolo == homeTab
                val v = LinearLayout(this)
                v.orientation = LinearLayout.VERTICAL
                v.gravity = android.view.Gravity.CENTER
                v.setPadding((2 * dp).toInt(), (8 * dp).toInt(), (2 * dp).toInt(), (8 * dp).toInt())
                val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                lp.setMargins((2 * dp).toInt(), 0, (2 * dp).toInt(), 0)
                v.layoutParams = lp
                if (on) v.setBackgroundResource(R.drawable.bg_tile)
                val i = TextView(this)
                i.text = g.ico
                i.textSize = 18f
                i.gravity = android.view.Gravity.CENTER
                val t = TextView(this)
                t.text = g.titolo
                t.textSize = 10f
                t.gravity = android.view.Gravity.CENTER
                t.maxLines = 1
                t.ellipsize = android.text.TextUtils.TruncateAt.END
                t.setTextColor(if (on) attivo else spento)
                if (on) t.setTypeface(null, Typeface.BOLD)
                v.addView(i)
                v.addView(t)
                v.setOnClickListener {
                    homeTab = g.titolo
                    disegna()
                }
                barra.addView(v)
            }
            val sel = homeGruppi.first { it.titolo == homeTab }
            for (terna in sel.tessere.chunked(3)) {
                val riga = LinearLayout(this)
                riga.orientation = LinearLayout.HORIZONTAL
                riga.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                for (t in terna) {
                    val v = layoutInflater.inflate(R.layout.item_tile, riga, false)
                    v.findViewById<TextView>(R.id.tvIco).text = t.ico
                    v.findViewById<TextView>(R.id.tvLbl).text = t.lbl
                    v.setOnClickListener { t.azione() }
                    riga.addView(v)
                }
                repeat(3 - terna.size) {
                    val vuoto = View(this)
                    val lp = LinearLayout.LayoutParams(0, 1, 1f)
                    lp.setMargins(8, 0, 8, 0)
                    vuoto.layoutParams = lp
                    riga.addView(vuoto)
                }
                corpo.addView(riga)
            }
        }
        disegna()
        cont.addView(barra)
        cont.addView(corpo)
    }

    private fun renderMovimenti() {
        val q = vista<TextInputEditText>(R.id.etSearch).text.toString().trim().lowercase()
        val delMese = Repo.voci().filter { YearMonth.from(it.data) == mese }
        val saldoMese = delMese.filter { it.tipo == "Entrata" }.sumOf { it.importo } -
            delMese.filter { it.tipo == "Uscita" }.sumOf { it.importo }
        vista<TextView>(R.id.tvMese).text = maiuscola(mese.format(FMT_MESE))
        meteoAnimato(vista(R.id.imgMeteoMov), saldoMese >= 0)
        val visibili = delMese.filter {
            q.isEmpty() ||
                (it.descrizione + " " + it.categoria + " " + it.metodo + " " + it.hashtag.joinToString(" "))
                    .lowercase().contains(q)
        }
        val entrate = visibili.filter { it.tipo == "Entrata" }.sumOf { it.importo }
        val uscite = visibili.filter { it.tipo == "Uscita" }.sumOf { it.importo }
        val saldo = entrate - uscite
        vista<TextView>(R.id.tvTotali).text = testoTotali(entrate, uscite, saldo)

        val lista = vista<LinearLayout>(R.id.listaVoci)
        lista.removeAllViews()
        if (visibili.isEmpty()) {
            val vuoto = TextView(this)
            vuoto.text = if (q.isEmpty()) "Nessun movimento in questo mese." else "Nessun risultato."
            vuoto.setTextColor(ContextCompat.getColor(this, R.color.oc_text_mid))
            vuoto.setPadding(8, 24, 8, 8)
            lista.addView(vuoto)
            return
        }
        val oggi = LocalDate.now()
        val verde = ContextCompat.getColor(this, R.color.oc_green)
        val rosso = ContextCompat.getColor(this, R.color.oc_red)
        val grigio = ContextCompat.getColor(this, R.color.oc_text_mid)

        val perGiorno = visibili.groupBy { it.data }.entries.sortedByDescending { it.key }
        for ((giorno, voci) in perGiorno) {
            val titolo = TextView(this)
            titolo.text = maiuscola(giorno.format(FMT_GIORNO))
            titolo.setTextColor(grigio)
            titolo.textSize = 12f
            titolo.setTypeface(null, Typeface.BOLD)
            titolo.setPadding(4, 12, 4, 6)
            lista.addView(titolo)
            for (v in voci) {
                val riga = layoutInflater.inflate(R.layout.item_voce, lista, false)
                val tvTit = riga.findViewById<TextView>(R.id.tvTit)
                val tvSub = riga.findViewById<TextView>(R.id.tvSub)
                val tvImp = riga.findViewById<TextView>(R.id.tvImp)
                val desc = descPulita(v.descrizione)
                tvTit.text = if (desc.isEmpty()) v.categoria else v.categoria + " · " + desc
                tvSub.text = listOfNotNull(
                    v.ora.takeIf { it.isNotBlank() },
                    v.metodo.takeIf { it.isNotBlank() }?.let { Repo.metodoConEmoji(it) },
                    v.conto.takeIf { it.isNotBlank() }?.let { "🏦 $it" },
                    v.hashtag.takeIf { it.isNotEmpty() }?.joinToString(" ") { "#" + it.trimStart('#') },
                    if (v.ricorrente) "ricorrente" else null,
                    if (v.data.isAfter(oggi)) "futura" else null,
                ).joinToString(" · ")
                tvSub.visibility = if (tvSub.text.isEmpty()) View.GONE else View.VISIBLE
                val entrata = v.tipo == "Entrata"
                tvImp.text = (if (entrata) "+" else "−") + euro(v.importo)
                tvImp.setTextColor(if (entrata) verde else rosso)
                if (v.data.isAfter(oggi)) {
                    tvTit.setTypeface(null, Typeface.BOLD_ITALIC)
                    tvSub.setTypeface(null, Typeface.ITALIC)
                }
                riga.setOnClickListener { dialogVoce(v) }
                lista.addView(riga)
            }
        }
    }

    private fun dialogVoce(
        v: Voce?, dataIniz: LocalDate? = null,
        catIniz: String? = null, impIniz: Double? = null, tipoIniz: String? = null, descIniz: String? = null,
    ) {
        val view = inflDialogo.inflate(R.layout.dialog_voce, null)
        val grp = view.findViewById<MaterialButtonToggleGroup>(R.id.grpTipo)
        val etImp = view.findViewById<TextInputEditText>(R.id.etImporto)
        Calc.collega(this, etImp)
        val acCat = view.findViewById<MaterialAutoCompleteTextView>(R.id.acCat)
        val btnData = view.findViewById<MaterialButton>(R.id.btnData)
        val etDescr = view.findViewById<TextInputEditText>(R.id.etDescr)
        val acMet = view.findViewById<MaterialAutoCompleteTextView>(R.id.acMet)
        val etTag = view.findViewById<TextInputEditText>(R.id.etTag)
        suggerimentiTag(etTag)
        val tilConto = view.findViewById<View>(R.id.tilConto)
        val acConto = view.findViewById<MaterialAutoCompleteTextView>(R.id.acConto)
        val tilPart = view.findViewById<View>(R.id.tilPart)
        val acPart = view.findViewById<MaterialAutoCompleteTextView>(R.id.acPart)

        var tipo = v?.tipo ?: tipoIniz ?: "Uscita"
        var data = v?.data ?: dataIniz ?: (if (mese == YearMonth.now()) LocalDate.now() else mese.atDay(1))
        var metodoSel = v?.metodo ?: ""

        fun categorieDisponibili(): List<String> {
            val base = Repo.categorie().filter { Repo.tipoCategoria(it) == tipo }.ifEmpty { Repo.categorie() }
            return (base + listOfNotNull(v?.categoria?.takeIf { it.isNotBlank() })).distinct()
        }

        fun aggiornaCategorie() {
            val cats = categorieDisponibili()
            acCat.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, cats))
            val attuale = acCat.text.toString()
            if (attuale !in cats) acCat.setText(cats.firstOrNull() ?: "Generica", false)
        }

        val nomiMetodi = ArrayList<String>()
        nomiMetodi.add("")
        Repo.METODI.forEach { nomiMetodi.add(it.first) }
        if (metodoSel.isNotBlank() && metodoSel !in nomiMetodi) nomiMetodi.add(metodoSel)
        fun etichettaMetodo(m: String) = if (m.isBlank()) "Nessuno" else Repo.metodoConEmoji(m)
        acMet.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_list_item_1, nomiMetodi.map { etichettaMetodo(it) })
        )
        acMet.setOnItemClickListener { _, _, pos, _ -> metodoSel = nomiMetodi[pos] }
        acMet.setText(etichettaMetodo(metodoSel), false)

        val nomiConti = ArrayList<String>()
        nomiConti.add("")
        Repo.conti().forEach { nomiConti.add(it.nome) }
        var contoSel = v?.conto ?: (Repo.contoPrincipale()?.nome ?: "")
        if (contoSel.isNotBlank() && contoSel !in nomiConti) nomiConti.add(contoSel)
        fun etichettaConto(c: String) = if (c.isBlank()) "Nessuno" else c
        if (nomiConti.size > 1) {
            tilConto.visibility = View.VISIBLE
            acConto.setAdapter(
                ArrayAdapter(this, android.R.layout.simple_list_item_1, nomiConti.map { etichettaConto(it) })
            )
            acConto.setOnItemClickListener { _, _, pos, _ -> contoSel = nomiConti[pos] }
            acConto.setText(etichettaConto(contoSel), false)
        }

        grp.check(if (tipo == "Entrata") R.id.btnEntrata else R.id.btnUscita)
        grp.addOnButtonCheckedListener { _, id, checked ->
            if (checked) {
                tipo = if (id == R.id.btnEntrata) "Entrata" else "Uscita"
                aggiornaCategorie()
            }
        }

        if (v != null) {
            etImp.setText(String.format(Locale.ITALY, "%.2f", v.importo))
            acCat.setText(v.categoria, false)
            etDescr.setText(v.descrizione)
            etTag.setText(v.hashtag.joinToString(" "))
        }
        aggiornaCategorie()
        if (v == null && catIniz != null) {
            acCat.setText(catIniz, false)
            if (impIniz != null && impIniz > 0) etImp.setText(String.format(Locale.ITALY, "%.2f", impIniz))
            if (!descIniz.isNullOrEmpty()) etDescr.setText(descIniz)
        }

        val etichetteFs = FairShare.etichette()
        if (etichetteFs.isNotEmpty()) {
            tilPart.visibility = View.VISIBLE
            val voci = listOf("Nessuno") + etichetteFs
            acPart.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, voci))
            acPart.setText(FairShare.tagDa(etDescr.text.toString()) ?: "Nessuno", false)
            acPart.setOnItemClickListener { _, _, pos, _ ->
                val scelta = if (pos == 0) null else voci[pos]
                etDescr.setText(FairShare.riscriviDescrizione(scelta, etDescr.text.toString()))
                etDescr.setSelection(etDescr.text?.length ?: 0)
            }
        }

        val tvSmart = view.findViewById<TextView>(R.id.tvSmart)
        if (v == null && Repo.smartCatOn) {
            val idx = Repo.smartCatIndice()
            tvSmart.visibility = View.VISIBLE
            tvSmart.text = "🛠️ SmartCat in attesa…"
            etImp.doAfterTextChanged { t ->
                val imp = num(t?.toString() ?: "")
                if (t.isNullOrBlank()) {
                    tvSmart.text = "🛠️ SmartCat in attesa…"
                    return@doAfterTextChanged
                }
                if (imp == null) return@doAfterTextChanged
                val sug = Repo.smartCat(idx, imp) ?: return@doAfterTextChanged
                if (sug.tipo != tipo) {
                    grp.check(if (sug.tipo == "Entrata") R.id.btnEntrata else R.id.btnUscita)
                }
                acCat.setText(sug.categoria, false)
                tvSmart.text = "💡 SmartCat attiva: " + sug.categoria
            }
        }

        btnData.text = "Data: " + data.format(FMT_DATA)
        btnData.setOnClickListener {
            DatePickerDialog(
                this,
                { _, y, m, d ->
                    data = LocalDate.of(y, m + 1, d)
                    btnData.text = "Data: " + data.format(FMT_DATA)
                },
                data.year, data.monthValue - 1, data.dayOfMonth
            ).show()
        }

        val builder = OcBuilder(this)
            .setTitle(if (v == null) "Nuova operazione" else "Modifica operazione")
            .setView(view)
            .setPositiveButton("Salva", null)
            .setNegativeButton("Annulla", null)
        if (v != null) builder.setNeutralButton("Elimina", null)
        val dlg = builder.create()
        dlg.show()

        dlg.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val imp = num(etImp.text.toString())
            if (imp == null || imp <= 0.0 || imp > 1_000_000_000.0) {
                etImp.error = "Importo non valido"
                return@setOnClickListener
            }
            val cat = acCat.text.toString().trim().ifEmpty { "Generica" }
            val tags = etTag.text.toString().split(Regex("[\\s,;]+"))
                .map { it.trimStart('#') }.filter { it.isNotEmpty() }
            val docCollegato = if (v != null) Doc.documentoDiSpesa(v) else null
            val descNuova = etDescr.text.toString().trim()
            val dataNuova = data
            val gemella = if (Repo.checkDoppi && v == null) {
                Repo.voci().firstOrNull {
                    it.data.year == data.year && it.data.monthValue == data.monthValue &&
                        it.categoria == cat && it.tipo == tipo && Math.abs(it.importo - imp) < 0.005
                }
            } else null
            if (gemella != null) {
                OcBuilder(this)
                    .setTitle("Duplicato mensile")
                    .setMessage("Esiste già un movimento identico in data " + gemella.data.format(FMT_DATA) + ".\nVuoi procedere comunque?")
                    .setNegativeButton("Annulla", null)
                    .setPositiveButton("Procedi") { _, _ ->
                        Repo.salvaVoce(null, data, cat, descNuova, imp, tipo, metodoSel, tags, contoSel)
                        if (pagina == Pagina.MOVIMENTI) mese = YearMonth.from(data)
                        if (pagina == Pagina.CALENDARIO) {
                            meseCal = YearMonth.from(data)
                            giornoCal = data
                        }
                        dlg.dismiss()
                        refresh()
                        aviso("Operazione salvata")
                    }
                    .show()
                return@setOnClickListener
            }
            Repo.salvaVoce(v?.id, data, cat, descNuova, imp, tipo, metodoSel, tags, contoSel)
            if (v != null && docCollegato != null &&
                (dataNuova != v.data || cat != v.categoria || descNuova != v.descrizione ||
                    Math.abs(imp - v.importo) >= 0.005 || tipo != v.tipo)
            ) {
                OcBuilder(this)
                    .setTitle("Documento allegato")
                    .setMessage("Aggiornare anche il documento archiviato?")
                    .setNegativeButton("No", null)
                    .setPositiveButton("Sì") { _, _ ->
                        try {
                            Doc.modificaContabile(docCollegato, dataNuova, cat, descNuova.replace("ALL·", "").trim(), tipo, imp)
                            refresh()
                            aviso("Documento aggiornato")
                        } catch (e: Exception) {
                            aviso("Documento non aggiornato: " + e.message)
                        }
                    }
                    .show()
            }
            if (pagina == Pagina.MOVIMENTI) mese = YearMonth.from(data)
            if (pagina == Pagina.CALENDARIO) {
                meseCal = YearMonth.from(data)
                giornoCal = data
            }
            dlg.dismiss()
            refresh()
            aviso("Operazione salvata")
        }
        if (v != null) {
            dlg.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                OcBuilder(this)
                    .setTitle("Eliminare l'operazione?")
                    .setMessage(v.categoria + " · " + euro(v.importo) + " del " + v.data.format(FMT_DATA))
                    .setNegativeButton("Annulla", null)
                    .setPositiveButton("Elimina") { _, _ ->
                        val docCollegato = Doc.documentoDiSpesa(v)
                        Repo.elimina(v.id)
                        dlg.dismiss()
                        refresh()
                        if (docCollegato != null) {
                            OcBuilder(this)
                                .setTitle("Elimina allegato")
                                .setMessage("Vuoi eliminare anche il documento archiviato (file e registro)?")
                                .setNegativeButton("No, tienilo", null)
                                .setPositiveButton("Sì, elimina") { _, _ ->
                                    try {
                                        Doc.eliminaContabile(docCollegato)
                                        refresh()
                                        aviso("Documento eliminato")
                                    } catch (e: Exception) {
                                        aviso("Documento non eliminato: " + e.message)
                                    }
                                }
                                .show()
                        }
                    }
                    .show()
            }
        }
    }

    private fun refresh() {
        when (pagina) {
            Pagina.HOME -> renderHome()
            Pagina.MOVIMENTI -> {
                renderMovimenti()
                renderHome()
            }
            else -> {
                renderLista()
                renderHome()
            }
        }
    }

    private fun renderPrevisione() {
        intestaLista("📉 Previsione mese e anno", "🏷️  Budget categorie")
        val cont = vista<LinearLayout>(R.id.listaGen)
        val oggi = LocalDate.now()
        val es = Previsione.calcola(Repo.voci(), oggi) { Repo.budgetMensile(it) }
        val rosso = ContextCompat.getColor(this, R.color.oc_red)
        val arancio = ContextCompat.getColor(this, R.color.oc_orange)
        val verde = ContextCompat.getColor(this, R.color.oc_green)
        fun pct(v: Double) = String.format(Locale.ITALY, "%.0f", v) + "%"

        sezioneForte(cont, "USCITE DEL MESE · " + maiuscola(oggi.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ITALY))).uppercase())
        if (es.righe.isEmpty()) {
            vuoto(cont, "Nessuna uscita registrata questo mese: niente da prevedere.")
            previsioneAnno(cont, oggi)
            return
        }
        val fino = es.righe.sumOf { it.finoAOggi }
        riga(
            cont, "Uscite del mese",
            "Già speso " + euro(fino) + " · in programma " + euro(es.totSpeso - fino),
            euro(es.totSpeso), null, null
        )
        if (es.totBudget > 0) {
            val sforo = es.spesoConBudget > es.totBudget
            riga(
                cont, "Categorie con budget",
                "Speso e in programma " + euro(es.spesoConBudget) + " · budget " + euro(es.totBudget),
                pct(es.spesoConBudget / es.totBudget * 100.0), if (sforo) rosso else verde, null
            )
        }

        sezioneForte(cont, "PER CATEGORIA", "Tocca una categoria per vedere le operazioni")
        for (r in es.righe) {
            val sotto = if (r.budget > 0)
                "Speso " + euro(r.speso) + " · budget " + euro(r.budget) + " (" + pct(r.speso / r.budget * 100.0) + ")" +
                    (if (r.speso > r.budget) " · oltre budget" else "")
            else "Speso " + euro(r.speso) + " · senza budget"
            val col = when {
                r.budget > 0 && r.speso > r.budget -> rosso
                r.budget > 0 && r.speso >= r.budget * 0.8 -> arancio
                else -> null
            }
            riga(cont, r.categoria, sotto, (if (r.budget > 0 && r.speso > r.budget) "▲ " else "") + euro(r.speso), col) {
                rc.azzera()
                rc.categoria = r.categoria
                rc.tipo = "Uscita"
                rc.anno = oggi.year.toString()
                rc.mese = oggi.monthValue
                rcTesto = ""
                mostra(Pagina.RICERCA)
            }
        }
        vuoto(
            cont,
            "Importi registrati nel mese, comprese le operazioni future (Pianifica, ricorrenze). " +
                "Nessuna proiezione: sono dati certi."
        )
        previsioneAnno(cont, oggi)
    }

    private fun previsioneAnno(cont: LinearLayout, oggi: LocalDate) {
        val rosso = ContextCompat.getColor(this, R.color.oc_red)
        val arancio = ContextCompat.getColor(this, R.color.oc_orange)
        val verde = ContextCompat.getColor(this, R.color.oc_green)
        fun pct(v: Double) = String.format(Locale.ITALY, "%.0f", v) + "%"
        val an = Previsione.annuale(Repo.voci(), oggi)
        val tm = Repo.targetMese
        val ta = Repo.targetAnno
        sezioneForte(cont, "PREVISIONE FINE ANNO · " + oggi.year)
        val proiez = an.proiezione
        if (proiez == null) {
            riga(cont, "Registrato nell'anno", "Servono almeno 3 mesi di dati o l'anno scorso per la stima.", euro(an.totale), null, null)
        } else {
            riga(
                cont, "Uscite previste a fine anno",
                "Registrato " + euro(an.totale) + " · media " + euro(an.media ?: 0.0) + "/mese",
                euro(proiez), null, null
            )
            if (ta > 0) {
                val oltre = proiez > ta
                riga(
                    cont, "Target anno " + euro(ta),
                    if (oltre) "Previsione oltre il target di " + euro(proiez - ta) else "Previsione nel target, restano " + euro(ta - proiez),
                    pct(proiez / ta * 100.0), if (oltre) rosso else verde, null
                )
            }
        }
        val sopra = an.mesi.count { tm > 0 && !it.futuro && it.uscite > tm }
        if (tm > 0) {
            riga(cont, "Mesi oltre il target mensile", "Target mese " + euro(tm) + " (solo mesi già iniziati)", sopra.toString(), if (sopra > 0) rosso else verde, null)
        }
        for (m in an.mesi) {
            val stima = proiez != null && m.futuro
            val valore = if (stima) m.stimato else m.uscite
            val p = if (tm > 0) valore / tm * 100.0 else null
            val col = when {
                p == null || (m.num == 0 && !stima) -> null
                p > 100 -> rosso
                p >= 80 -> arancio
                else -> verde
            }
            val nome = maiuscola(Ricerca.MESI[m.mese - 1])
            val sotto = when {
                stima -> "Stima" + if (m.uscite > 0) " · già registrato " + euro(m.uscite) else ""
                m.mese == oggi.monthValue -> "In corso · " + m.num + " operazioni"
                m.num == 0 -> "Nessuna operazione"
                else -> m.num.toString() + " operazioni"
            }
            riga(
                cont, nome, sotto + (if (p != null && (m.num > 0 || stima)) " · " + pct(p) + " del target" else ""),
                (if (stima) "~ " else "") + euro(valore), col, null
            )
        }
        vuoto(
            cont,
            "Come nel Fondo risparmio: mesi con dati reali (ultimi 365 giorni), poi lo stesso mese dell'anno scorso, " +
                "poi la media. Il segno ~ indica una stima."
        )
    }

    private fun renderLista() {
        when (pagina) {
            Pagina.CONTI -> renderConti()
            Pagina.CATEGORIE -> renderCategorie()
            Pagina.ANALISI -> renderAnalisi()
            Pagina.TARGET -> renderTarget()
            Pagina.RICORRENZE -> renderRicorrenze()
            Pagina.CALENDARIO -> renderCalendario()
            Pagina.FINANZIAMENTI -> renderFinanziamenti()
            Pagina.AUTOPARK, Pagina.IMMOBIL, Pagina.PET -> renderScheda()
            Pagina.FONDO -> renderFondo()
            Pagina.CASACARE -> renderCasaCare()
            Pagina.PENSIONE -> renderPensione()
            Pagina.INVEST -> renderInvest()
            Pagina.DIETA -> renderDieta()
            Pagina.STUDIO -> renderStudio()
            Pagina.INFLAZIONE -> renderInflazione()
            Pagina.MARKET -> renderMarket()
            Pagina.STOCK -> renderStock()
            Pagina.GRAFICI -> renderGrafici()
            Pagina.PIANIFICA -> renderPianifica()
            Pagina.DOCUMENTI -> renderDocumenti()
            Pagina.DOCPERS -> renderDocPers()
            Pagina.UTENZE -> renderUtenze()
            Pagina.FAIRSHARE -> renderFairshare()
            Pagina.POOL -> renderPool()
            Pagina.CHECKOUT -> renderCheckout()
            Pagina.RICERCA -> renderRicerca()
            Pagina.PREVISIONE -> renderPrevisione()
            Pagina.PROMEMORIA -> renderPromemoria()
            Pagina.NOTIFICHE -> renderNotifiche()
            Pagina.INFO -> renderInfo()
            Pagina.ESTRATTI -> renderEstratti()
            Pagina.CHIEDI -> renderChiedi()
            else -> {}
        }
    }

    private var b2Tint: ColorStateList? = null
    private var b2Testo: ColorStateList? = null

    private fun giallo(b: MaterialButton) {
        b.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.oc_gold))
        b.setTextColor(android.graphics.Color.BLACK)
    }

    private fun intestaLista(titolo: String, azione: String, azione2: String? = null) {
        vista<TextView>(R.id.tvTitoloLista).text = titolo
        vista<TextView>(R.id.tvAddGen).text = azione
        vista<View>(R.id.btnAddGen).visibility = if (azione.isEmpty()) View.GONE else View.VISIBLE
        val b2 = vista<MaterialButton>(R.id.btnAddGen2)
        if (b2Tint == null) { b2Tint = b2.backgroundTintList; b2Testo = b2.textColors }
        b2.backgroundTintList = b2Tint
        b2Testo?.let { b2.setTextColor(it) }
        b2.visibility = if (azione2 == null) View.GONE else View.VISIBLE
        if (azione2 != null) b2.text = azione2
        bottoniAffiancati(true)
        vista<LinearLayout>(R.id.listaGen).removeAllViews()
    }

    private fun bottoniAffiancati(si: Boolean) {
        val dp = resources.displayMetrics.density
        val barra = vista<LinearLayout>(R.id.barraAzioni)
        val b2 = vista<MaterialButton>(R.id.btnAddGen2)
        val b1 = vista<View>(R.id.btnAddGen)
        b2.insetTop = 0
        b2.insetBottom = 0
        barra.orientation = if (si) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        (b2.layoutParams as LinearLayout.LayoutParams).let {
            it.width = if (si) 0 else ViewGroup.LayoutParams.MATCH_PARENT
            it.height = if (si) (48 * dp).toInt() else ViewGroup.LayoutParams.WRAP_CONTENT
            it.weight = if (si) 1f else 0f
            it.topMargin = if (si) (8 * dp).toInt() else (6 * dp).toInt()
            it.marginEnd = if (si && b1.visibility == View.VISIBLE) (8 * dp).toInt() else 0
            b2.layoutParams = it
        }
        (b1.layoutParams as LinearLayout.LayoutParams).let {
            it.width = if (si) 0 else ViewGroup.LayoutParams.MATCH_PARENT
            it.height = if (si) (48 * dp).toInt() else ViewGroup.LayoutParams.WRAP_CONTENT
            it.weight = if (si) 1f else 0f
            it.topMargin = (8 * dp).toInt()
            b1.layoutParams = it
        }
    }

    private fun testoTotali(entrate: Double, uscite: Double, saldo: Double): String {
        val nb = '\u00A0'
        fun u(t: String) = t.replace(' ', nb)
        return u("Entrate " + euro(entrate)) + nb + "· " +
            u("Uscite " + euro(uscite)) + "\n" +
            u("Saldo " + (if (saldo >= 0) "+" else "−") + euro(abs(saldo)))
    }

    private fun sezione(cont: LinearLayout, testo: String) {
        val nl = testo.indexOf('\n')
        if (nl > 0) {
            sezioneForte(cont, testo.substring(0, nl), testo.substring(nl + 1))
            return
        }
        val ap = testo.lastIndexOf(" (")
        if (ap > 0 && testo.endsWith(")")) {
            val dentro = testo.substring(ap + 2, testo.length - 1)
            if (dentro.contains(' ') && dentro.length >= 8) {
                sezioneForte(cont, testo.substring(0, ap), dentro.substring(0, 1).uppercase() + dentro.substring(1).lowercase())
                return
            }
        }
        val punto = testo.indexOf(" · ")
        if (punto > 0 && testo.length > 30) {
            sezioneForte(cont, testo.substring(0, punto), testo.substring(punto + 3))
            return
        }
        sezioneForte(cont, testo)
    }

    private fun sezionePiccola(cont: LinearLayout, testo: String) {
        val t = TextView(this)
        t.text = testo
        t.setTextColor(ContextCompat.getColor(this, R.color.oc_text_mid))
        t.textSize = 12f
        t.setTypeface(null, Typeface.BOLD)
        t.setPadding(4, 14, 4, 6)
        cont.addView(t)
    }

    private fun sezioneForte(cont: LinearLayout, testo: String, sotto: String? = null, click: (() -> Unit)? = null) {
        val dp = resources.displayMetrics.density
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(0, (18 * dp).toInt(), 0, (6 * dp).toInt())
        val riga = LinearLayout(this)
        riga.orientation = LinearLayout.HORIZONTAL
        riga.gravity = android.view.Gravity.CENTER_VERTICAL
        val barra = View(this)
        barra.setBackgroundColor(ContextCompat.getColor(this, R.color.oc_gold))
        barra.minimumHeight = (22 * dp).toInt()
        barra.layoutParams = LinearLayout.LayoutParams((4 * dp).toInt(), ViewGroup.LayoutParams.MATCH_PARENT)
        val t = TextView(this)
        t.text = testo
        t.setTextColor(ContextCompat.getColor(this, R.color.oc_text))
        t.textSize = 16f
        t.setTypeface(null, Typeface.BOLD)
        t.setPadding((8 * dp).toInt(), 0, 0, 0)
        riga.addView(barra)
        riga.addView(t)
        col.addView(riga)
        if (sotto != null) {
            val s = TextView(this)
            s.text = sotto
            s.textSize = 11f
            s.setTextColor(ContextCompat.getColor(this, R.color.oc_text_mid))
            s.setPadding((12 * dp).toInt(), (2 * dp).toInt(), 0, 0)
            col.addView(s)
        }
        if (click != null) col.setOnClickListener { click() }
        cont.addView(col)
    }

    private fun vuoto(cont: LinearLayout, testo: String) {
        val dp = resources.displayMetrics.density
        val t = TextView(this)
        t.text = testo
        t.setTextColor(ContextCompat.getColor(this, R.color.oc_text_mid))
        t.setPadding(0, (6 * dp).toInt(), 0, (6 * dp).toInt())
        cont.addView(t)
    }

    private fun riga(
        cont: LinearLayout, titolo: String, sotto: String, valore: String, colore: Int?,
        click: (() -> Unit)?,
    ) {
        val r = layoutInflater.inflate(R.layout.item_voce, cont, false)
        r.findViewById<TextView>(R.id.tvTit).text = titolo
        val tvSub = r.findViewById<TextView>(R.id.tvSub)
        tvSub.text = sotto
        tvSub.visibility = if (sotto.isEmpty()) View.GONE else View.VISIBLE
        val tvImp = r.findViewById<TextView>(R.id.tvImp)
        tvImp.text = valore
        tvImp.maxWidth = (resources.displayMetrics.widthPixels * 0.55f).toInt()
        tvImp.gravity = android.view.Gravity.END or android.view.Gravity.CENTER_VERTICAL
        if (colore != null) tvImp.setTextColor(colore)
        if (click != null) r.setOnClickListener { click() } else {
            r.isClickable = false
            r.isFocusable = false
        }
        cont.addView(r)
    }

    private fun barra(cont: LinearLayout, titolo: String, speso: Double, limite: Double, unita: String) {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setBackgroundResource(R.drawable.bg_tile)
        box.setPadding(24, 20, 24, 20)
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.bottomMargin = 12
        box.layoutParams = lp

        val tit = TextView(this)
        tit.text = titolo
        tit.setTypeface(null, Typeface.BOLD)
        box.addView(tit)

        val pct = if (limite > 0) speso / limite * 100.0 else 0.0
        val colore = ContextCompat.getColor(
            this, when { pct >= 100 -> R.color.oc_red; pct >= 80 -> R.color.oc_orange; else -> R.color.oc_green }
        )
        val pb = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal)
        pb.max = 100
        pb.progress = pct.toInt().coerceIn(0, 100)
        pb.progressTintList = ColorStateList.valueOf(colore)
        val plp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        plp.topMargin = 10
        pb.layoutParams = plp
        box.addView(pb)

        val det = TextView(this)
        val resto = limite - speso
        det.text = euro(speso) + " di " + euro(limite) + " " + unita + "  ·  " +
            (if (resto >= 0) "restano " + euro(resto) else "sforato di " + euro(-resto)) +
            "  (" + String.format(Locale.ITALY, "%.0f", pct) + "%)"
        det.textSize = 12f
        det.setTextColor(ContextCompat.getColor(this, R.color.oc_text_mid))
        det.setPadding(0, 8, 0, 0)
        box.addView(det)
        cont.addView(box)
    }

    private val ctxDialogo: android.content.Context by lazy { android.view.ContextThemeWrapper(this, R.style.OcDialog) }

    private val inflDialogo: android.view.LayoutInflater by lazy {
        android.view.LayoutInflater.from(android.view.ContextThemeWrapper(this, R.style.OcDialog))
    }

    private fun corpoDialogo(): Pair<View, LinearLayout> {
        val v = inflDialogo.inflate(R.layout.view_dialogo, null)
        return v to v.findViewById<LinearLayout>(R.id.corpoDialogo)
    }

    private fun suggerimentiTag(et: EditText) {
        val tutti by lazy {
            Repo.voci().flatMap { it.hashtag }.map { it.trimStart('#') }.filter { it.isNotEmpty() }
                .distinct().sortedBy { it.lowercase() }
        }
        val sep = Regex("[\\s,;]+")
        val dp = resources.displayMetrics.density
        var ancora: android.view.View = et
        while (ancora !is com.google.android.material.textfield.TextInputLayout) {
            ancora = (ancora.parent as? android.view.View) ?: break
        }
        if (ancora !is com.google.android.material.textfield.TextInputLayout) ancora = et
        val pop = android.widget.PopupWindow(et.context)
        pop.isFocusable = false
        pop.isOutsideTouchable = false
        pop.setInputMethodMode(android.widget.PopupWindow.INPUT_METHOD_NOT_NEEDED)
        pop.elevation = 8 * dp
        et.addOnAttachStateChangeListener(object : android.view.View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: android.view.View) {}
            override fun onViewDetachedFromWindow(v: android.view.View) { pop.dismiss() }
        })
        et.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val t = s?.toString() ?: ""
                val parola = t.split(sep).lastOrNull() ?: ""
                val ult = parola.trimStart('#')
                val sug = if (ult.isEmpty() || !et.hasFocus() || !et.isAttachedToWindow) emptyList()
                else tutti.filter { it.startsWith(ult, true) && !it.equals(ult, true) }.take(5)
                if (sug.isEmpty()) {
                    pop.dismiss()
                    return
                }
                val box = LinearLayout(et.context)
                box.orientation = LinearLayout.VERTICAL
                val sfondo = android.graphics.drawable.GradientDrawable()
                sfondo.setColor(ContextCompat.getColor(et.context, R.color.oc_surface))
                sfondo.setStroke((1 * dp).toInt().coerceAtLeast(1), ContextCompat.getColor(et.context, R.color.oc_border))
                sfondo.cornerRadius = 8 * dp
                box.background = sfondo
                for (x in sug) {
                    val tv = TextView(et.context)
                    tv.text = "#" + x
                    tv.textSize = 16f
                    tv.setTextColor(ContextCompat.getColor(et.context, R.color.oc_text))
                    tv.setPadding((14 * dp).toInt(), (10 * dp).toInt(), (14 * dp).toInt(), (10 * dp).toInt())
                    tv.minHeight = (48 * dp).toInt()
                    tv.gravity = android.view.Gravity.CENTER_VERTICAL
                    tv.setOnClickListener {
                        val base = t.substring(0, t.length - parola.length)
                        et.setText(base + "#" + x + " ")
                        et.setSelection(et.text?.length ?: 0)
                    }
                    box.addView(tv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                }
                val w = et.width.coerceAtLeast((200 * dp).toInt())
                box.measure(
                    android.view.View.MeasureSpec.makeMeasureSpec(w, android.view.View.MeasureSpec.EXACTLY),
                    android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED)
                )
                val h = box.measuredHeight
                pop.dismiss()
                pop.contentView = box
                pop.width = w
                pop.height = ViewGroup.LayoutParams.WRAP_CONTENT
                pop.isClippingEnabled = false
                pop.showAsDropDown(ancora, 0, -(ancora.height + h))
                box.post {
                    if (pop.isShowing) pop.update(ancora, 0, -(ancora.height + box.height), -1, -1)
                }
            }
        })
    }

    private fun campoTesto(
        cont: LinearLayout, hint: String, testo: String = "",
        tipo: Int = InputType.TYPE_CLASS_TEXT, max: Int = 0,
    ): TextInputEditText {
        val til = inflDialogo.inflate(R.layout.view_campo, cont, false) as TextInputLayout
        til.hint = hint
        val et = til.findViewById<TextInputEditText>(R.id.campoTesto)
        et.inputType = tipo
        if (max > 0) et.filters = arrayOf(android.text.InputFilter.LengthFilter(max))
        et.setText(testo)
        cont.addView(til)
        return et
    }

    private val TIPO_DECIMALE =
        InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
    private val TIPO_DECIMALE_NEG = TIPO_DECIMALE or InputType.TYPE_NUMBER_FLAG_SIGNED

    private fun campoMenu(
        cont: LinearLayout, hint: String, voci: List<String>, selezione: String,
        onScelta: (Int) -> Unit = {},
    ): TextInputLayout {
        val til = inflDialogo.inflate(R.layout.view_menu, cont, false) as TextInputLayout
        til.hint = hint
        val ac = til.findViewById<MaterialAutoCompleteTextView>(R.id.campoMenu)
        ac.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, voci))
        ac.setText(selezione, false)
        ac.setOnItemClickListener { _, _, pos, _ -> onScelta(pos) }
        cont.addView(til)
        return til
    }

    private fun importoOk(testo: String, ammettiZero: Boolean = false, negativo: Boolean = false): Double? {
        val n = num(testo) ?: return null
        if (!negativo && n < 0) return null
        if (!ammettiZero && n <= 0.0) return null
        return if (n > 1_000_000_000.0 || n < -1_000_000_000.0) null else n
    }
    
    private fun renderConti() {
        intestaLista("🏦 Conti", "➕  Nuovo conto", "🔁  Nuovo trasferimento")
        val cont = vista<LinearLayout>(R.id.listaGen)
        val conti = Repo.conti()
        val verde = ContextCompat.getColor(this, R.color.oc_green)
        val rosso = ContextCompat.getColor(this, R.color.oc_red)

        if (conti.isEmpty()) {
            vuoto(cont, "Nessun conto. Aggiungi il primo, oppure importa i dati dal desktop.")
            return
        }
        val saldi = conti.associate { it.id to Repo.saldoEffettivo(it) }
        val totale = saldi.values.sum()
        riga(cont, "Totale conti", conti.size.toString() + " conti", euro(totale), if (totale >= 0) verde else rosso, null)

        sezione(cont, "CONTI")
        for (c in conti) {
            val saldo = saldi[c.id] ?: 0.0
            val sotto = listOfNotNull(
                maiuscola(c.tipo),
                c.iban.takeIf { it.isNotBlank() },
                c.note.takeIf { it.isNotBlank() },
            ).joinToString(" · ")
            riga(
                cont, c.nome + (if (c.principale) " ⭐" else ""), sotto, euro(saldo),
                if (saldo >= 0) verde else rosso
            ) { dialogConto(c) }
        }

        val nomi = conti.associate { it.id to it.nome }
        val trasf = Repo.trasferimenti()
        sezione(cont, "TRASFERIMENTI")
        if (trasf.isEmpty()) vuoto(cont, "Nessun trasferimento.")
        val oggi = LocalDate.now()
        for (t in trasf.take(60)) {
            val sotto = listOfNotNull(
                t.data.format(FMT_DATA),
                t.note.takeIf { it.isNotBlank() },
                if (t.idRicorrenza.isNotBlank()) "ricorrente" else null,
                if (t.auto) "risparmio automatico" else null,
                if (t.data.isAfter(oggi)) "futuro" else null,
            ).joinToString(" · ")
            riga(
                cont, (nomi[t.da] ?: "?") + " → " + (nomi[t.a] ?: "?"), sotto, euro(t.importo), null
            ) { dialogTrasf(t) }
        }
        if (trasf.size > 60) vuoto(cont, "…e altri " + (trasf.size - 60) + " trasferimenti meno recenti.")
    }

    private fun dialogConto(c: Conto?) {
        val (view, corpo) = corpoDialogo()
        val etNome = campoTesto(corpo, "Nome", c?.nome ?: "", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES, 40)
        var tipo = c?.tipo ?: "personale"
        campoMenu(corpo, "Tipo", Repo.TIPI_CONTO.map { maiuscola(it) }, maiuscola(tipo)) { tipo = Repo.TIPI_CONTO[it] }
        val etSaldo = campoTesto(
            corpo, "Saldo iniziale (€)",
            if (c != null) String.format(Locale.ITALY, "%.2f", c.saldoIniziale) else "",
            TIPO_DECIMALE_NEG, 14
        )
        val etIban = campoTesto(corpo, "IBAN (facoltativo)", c?.iban ?: "", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS, 34)
        val etNote = campoTesto(corpo, "Note (max 20 caratteri)", c?.note ?: "", InputType.TYPE_CLASS_TEXT, 20)
        val chk = MaterialCheckBox(this)
        chk.text = "Conto principale ⭐"
        chk.isChecked = c?.principale ?: Repo.conti().isEmpty()
        corpo.addView(chk)

        val b = OcBuilder(this)
            .setTitle(if (c == null) "Nuovo conto" else "Modifica conto")
            .setView(view)
            .setPositiveButton("Salva", null)
            .setNegativeButton("Annulla", null)
        if (c != null) b.setNeutralButton("Elimina", null)
        val dlg = b.create()
        dlg.show()
        dlg.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val saldo = importoOk(etSaldo.text.toString(), ammettiZero = true, negativo = true)
            if (saldo == null) {
                etSaldo.error = "Importo non valido"
                return@setOnClickListener
            }
            val err = Repo.salvaConto(
                c?.id, etNome.text.toString(), tipo, saldo, chk.isChecked,
                etIban.text.toString(), etNote.text.toString()
            )
            if (err != null) {
                etNome.error = err
                return@setOnClickListener
            }
            dlg.dismiss()
            refresh()
            aviso("Conto salvato")
        }
        if (c != null) {
            dlg.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                OcBuilder(this)
                    .setTitle("Eliminare il conto?")
                    .setMessage(
                        c.nome + "\nVerranno eliminati anche i trasferimenti collegati. " +
                            "Le operazioni restano, ma non saranno più associate al conto."
                    )
                    .setNegativeButton("Annulla", null)
                    .setPositiveButton("Elimina") { _, _ ->
                        Repo.eliminaConto(c.id)
                        dlg.dismiss()
                        refresh()
                    }
                    .show()
            }
        }
    }

    private fun dialogTrasf(t: Trasf?) {
        val conti = Repo.conti()
        val nomi = conti.map { it.nome }
        fun nomeDi(id: String?) = conti.firstOrNull { it.id == id }?.nome ?: nomi.firstOrNull() ?: ""
        var da = nomeDi(t?.da)
        var a = if (t != null) nomeDi(t.a) else (nomi.firstOrNull { it != da } ?: da)
        var data = t?.data ?: LocalDate.now()

        val (view, corpo) = corpoDialogo()
        campoMenu(corpo, "Da conto", nomi, da) { da = nomi[it] }
        campoMenu(corpo, "A conto", nomi, a) { a = nomi[it] }
        val etImp = campoTesto(
            corpo, "Importo (€)",
            if (t != null) String.format(Locale.ITALY, "%.2f", t.importo) else "", TIPO_DECIMALE, 12
        )
        val btnData = MaterialButton(ctxDialogo, null, com.google.android.material.R.attr.materialButtonOutlinedStyle)
        btnData.text = "Data: " + data.format(FMT_DATA)
        btnData.setOnClickListener {
            DatePickerDialog(
                this,
                { _, y, m, d ->
                    data = LocalDate.of(y, m + 1, d)
                    btnData.text = "Data: " + data.format(FMT_DATA)
                },
                data.year, data.monthValue - 1, data.dayOfMonth
            ).show()
        }
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.topMargin = 16
        corpo.addView(btnData, lp)
        val etNote = campoTesto(corpo, "Note (max 20 caratteri)", t?.note ?: "", InputType.TYPE_CLASS_TEXT, 20)

        val b = OcBuilder(this)
            .setTitle(if (t == null) "Nuovo trasferimento" else "Modifica trasferimento")
            .setView(view)
            .setPositiveButton("Salva", null)
            .setNegativeButton("Annulla", null)
        if (t != null) b.setNeutralButton("Elimina", null)
        val dlg = b.create()
        dlg.show()
        dlg.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val imp = importoOk(etImp.text.toString())
            if (imp == null) {
                etImp.error = "Importo non valido"
                return@setOnClickListener
            }
            val idDa = conti.firstOrNull { it.nome == da }?.id ?: ""
            val idA = conti.firstOrNull { it.nome == a }?.id ?: ""
            val err = Repo.salvaTrasferimento(t?.id, data, idDa, idA, imp, etNote.text.toString())
            if (err != null) {
                aviso(err)
                return@setOnClickListener
            }
            dlg.dismiss()
            refresh()
            aviso("Trasferimento salvato")
        }
        if (t != null) {
            dlg.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                val conferma = OcBuilder(this)
                    .setTitle("Eliminare il trasferimento?")
                    .setMessage(euro(t.importo) + " del " + t.data.format(FMT_DATA))
                    .setNegativeButton("Annulla", null)
                    .setPositiveButton(if (t.idRicorrenza.isBlank()) "Elimina" else "Solo questo") { _, _ ->
                        Repo.eliminaTrasferimento(t.id, false)
                        dlg.dismiss()
                        refresh()
                    }
                if (t.idRicorrenza.isNotBlank()) {
                    conferma.setNeutralButton("Tutta la serie") { _, _ ->
                        Repo.eliminaTrasferimento(t.id, true)
                        dlg.dismiss()
                        refresh()
                    }
                }
                conferma.show()
            }
        }
    }

    private val AN_SCHEDE = listOf(
        "🔍 Dettaglio", "Σ Aggrega", "⏳ Time Machine", "📈 Trend",
        "🕒 Distribuzione", "⚠️ Budget superato", "🎯 Target", "⚖️ 50/30/20", "🏷️ Per tag", "🆚 Confronto periodi"
    )
    private val MESI_IT: List<String> by lazy {
        (1..12).map { maiuscola(java.time.Month.of(it).getDisplayName(java.time.format.TextStyle.FULL, Locale.ITALIAN)) }
    }
    private val AN_GIORNI = listOf("Lunedì", "Martedì", "Mercoledì", "Giovedì", "Venerdì", "Sabato", "Domenica")
    private val AN_FASCE = listOf(
        Triple("Notte (00–06)", 0, 6), Triple("Mattina (06–12)", 6, 12),
        Triple("Pomeriggio (12–18)", 12, 18), Triple("Sera (18–24)", 18, 24)
    )
    private val AN_MODI_DIST = listOf("Giorno della settimana", "Fascia oraria", "Ora")
    private val AN_TUTTE = "Tutte le categorie"
    private val AN_SENZA_ORA = "Senza orario"

    private var catTab = 0
    private var anModo = "Giorno"
    private var anGiorno: LocalDate = LocalDate.now()
    private var anDal: LocalDate = LocalDate.now().withDayOfMonth(1)
    private var anAl: LocalDate = LocalDate.now()
    private var anMese: YearMonth = YearMonth.now()
    private var anAnno = LocalDate.now().year.toString()
    private var anMeseN = 0
    private var anCat = AN_TUTTE
    private var anConto = ""
    private var anFuturi = false
    private val anAgg = LinkedHashSet<String>()
    private val anTm = LinkedHashSet<String>()
    private var anDist = AN_MODI_DIST[0]
    private var anClasse = "Tutte"

    private fun renderAnalisi() {
        intestaLista("🔍 Analisi categorie", "")
        val cont = vista<LinearLayout>(R.id.listaGen)
        val scelta = pulsanteSel("Vista:  " + AN_SCHEDE[catTab] + "   ▾", true) {
            OcBuilder(this)
                .setTitle("Scegli la vista")
                .setItems(
                    (AN_SCHEDE.mapIndexed { i, t -> (if (i == catTab) "● " else "○ ") + t } + "🗂️ Gestione categorie").toTypedArray()
                ) { _, i ->
                    if (i >= AN_SCHEDE.size) {
                        mostra(Pagina.CATEGORIE)
                    } else {
                        catTab = i
                        renderAnalisi()
                    }
                }
                .setNegativeButton("Chiudi", null)
                .show()
        }
        cont.addView(scelta, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        when (catTab) {
            0 -> tabDettaglio(cont)
            1 -> tabAggrega(cont)
            2 -> tabTimeMachine(cont)
            3 -> tabTrend(cont)
            4 -> tabDistribuzione(cont)
            5 -> tabBudgetSuperato(cont)
            6 -> tabTarget(cont)
            7 -> tab503020(cont)
            8 -> tabTag(cont)
            else -> tabConfronto(cont)
        }
    }

    private fun anEntrata(v: Voce) = v.tipo.trim().equals("Entrata", ignoreCase = true)
    private fun anUscita(v: Voce) = v.tipo.trim().equals("Uscita", ignoreCase = true)
    private fun anPct(x: Double) = String.format(Locale.ITALY, "%.1f%%", x)
    private fun anPctS(x: Double) = String.format(Locale.ITALY, "%+.1f%%", x)
    private fun anSaldo(x: Double) = (if (x >= 0) "+" else "−") + euro(abs(x))
    private fun anDelta(cur: Double, prev: Double?): String {
        if (prev == null) return "—"
        if (prev <= 0.004) return if (cur > 0.004) "nuovo" else "—"
        return anPctS((cur - prev) / prev * 100)
    }
    private fun anAnni(): List<Int> =
        (Repo.voci().map { it.data.year } + LocalDate.now().year).distinct().sortedDescending()

    private fun anMovs(): List<Voce> = Repo.voci().filter { anConto.isEmpty() || it.conto == anConto }

    private fun anMenu(cont: LinearLayout, hint: String, voci: List<String>, sel: String, on: (Int) -> Unit) =
        campoMenu(cont, hint, voci, sel) { on(it); renderAnalisi() }

    private fun anRiga(cont: LinearLayout, f: (LinearLayout) -> Unit) {
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        f(r)
        val n = r.childCount
        for (i in 0 until n) {
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            (r.getChildAt(i).layoutParams as? LinearLayout.LayoutParams)?.let { lp.topMargin = it.topMargin; lp.bottomMargin = it.bottomMargin }
            if (i < n - 1) lp.marginEnd = (8 * resources.displayMetrics.density).toInt()
            r.getChildAt(i).layoutParams = lp
        }
        cont.addView(r)
    }

    private fun anCampoData(cont: LinearLayout, hint: String, valore: LocalDate, onData: (LocalDate) -> Unit) {
        val et = campoTesto(cont, hint, valore.format(FMT_DATA))
        et.isFocusable = false
        et.isCursorVisible = false
        et.setOnClickListener {
            DatePickerDialog(this, { _, y, m, d ->
                onData(LocalDate.of(y, m + 1, d))
                renderAnalisi()
            }, valore.year, valore.monthValue - 1, valore.dayOfMonth).show()
        }
    }

    private fun anContoFuturi(cont: LinearLayout, futuri: Boolean = true) {
        val conti = Repo.conti().map { it.nome }
        if (conti.isNotEmpty()) {
            val nomi = listOf("Tutti i conti") + conti
            anMenu(cont, "Conto", nomi, if (anConto.isEmpty()) nomi[0] else anConto) {
                anConto = if (it == 0) "" else nomi[it]
            }
        }
        if (futuri) {
            val chk = MaterialCheckBox(this)
            chk.text = "Includi movimenti futuri"
            chk.isChecked = anFuturi
            chk.setOnCheckedChangeListener { _, on -> anFuturi = on; renderAnalisi() }
            cont.addView(chk)
        }
    }

    private fun anTesto(cont: LinearLayout, testo: String, grassetto: Boolean = false, colore: Int? = null, size: Float = 14f) {
        val t = TextView(this)
        t.text = testo
        t.textSize = size
        t.setTextColor(colore ?: ContextCompat.getColor(this, R.color.oc_text))
        if (grassetto) t.setTypeface(null, Typeface.BOLD)
        val dp = resources.displayMetrics.density
        t.setPadding(0, (6 * dp).toInt(), 0, (6 * dp).toInt())
        cont.addView(t)
    }

    private fun anBarra(cont: LinearLayout, etichetta: String, valore: Double, max: Double, colore: Int, testo: String) {
        val dp = resources.displayMetrics.density
        val r = LinearLayout(this)
        r.orientation = LinearLayout.VERTICAL
        r.setPadding(0, (4 * dp).toInt(), 0, (4 * dp).toInt())
        val t = TextView(this)
        t.text = "$etichetta  ·  $testo"
        t.textSize = 13f
        t.setTextColor(ContextCompat.getColor(this, R.color.oc_text))
        r.addView(t)
        val w = if (max > 0) (abs(valore) / max).coerceIn(0.0, 1.0).toFloat() else 0f
        val b = LinearLayout(this)
        b.orientation = LinearLayout.HORIZONTAL
        val pieno = View(this)
        pieno.setBackgroundColor(colore)
        b.addView(pieno, LinearLayout.LayoutParams(0, (8 * dp).toInt(), w.coerceAtLeast(0.001f)))
        b.addView(View(this), LinearLayout.LayoutParams(0, (8 * dp).toInt(), (1f - w).coerceAtLeast(0.001f)))
        r.addView(b)
        cont.addView(r)
    }

    private fun anGrafico(
        cont: LinearLayout, etichette: List<String>, a: List<Double?>, b: List<Double?>,
        colA: Int, colB: Int, dettaglio: (Int) -> String,
    ) {
        val dp = resources.displayMetrics.density
        val grigio = ContextCompat.getColor(this, R.color.oc_text_mid)
        val g = GraficoView(this)
        g.imposta(
            GraficoView.Modo.BARRE_DOPPIE, etichette, a, b, colA, colB, grigio,
            ContextCompat.getColor(this, R.color.oc_border), grigio,
            ContextCompat.getColor(this, R.color.oc_surface),
        )
        val det = TextView(this)
        det.textSize = 13f
        det.setPadding(8, (6 * dp).toInt(), 8, (6 * dp).toInt())
        det.text = "Tocca il grafico per vedere i dettagli."
        det.setTextColor(grigio)
        det.minLines = 3
        g.alSelezionare = { i ->
            det.text = dettaglio(i)
            det.setTextColor(ContextCompat.getColor(this, R.color.oc_text))
        }
        cont.addView(g, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (170 * dp).toInt()))
        cont.addView(det)
    }

    private fun dialogScegliCategorie(titolo: String, set: MutableSet<String>, max: Int) {
        val conteggi = Repo.operazioniPerCategoria()
        val cats = Repo.categorie().sortedBy { it.lowercase() }
        val sel = BooleanArray(cats.size) { cats[it] in set }
        OcBuilder(this)
            .setTitle(titolo)
            .setMultiChoiceItems(cats.map { it + " (" + (conteggi[it] ?: 0) + ")" }.toTypedArray(), sel) { _, i, on -> sel[i] = on }
            .setNegativeButton("Annulla", null)
            .setPositiveButton("OK") { _, _ ->
                val scelte = cats.filterIndexed { i, _ -> sel[i] }
                set.clear()
                set.addAll(scelte.take(max))
                if (scelte.size > max) aviso("Massimo $max categorie: tenute le prime $max")
                renderAnalisi()
            }
            .show()
    }

    private fun anElencoMovimenti(titolo: String, lista: List<Voce>, neutro: Pair<String, () -> Unit>? = null) {
        val ordinata = lista.sortedWith(compareByDescending<Voce> { it.data }.thenByDescending { it.ora })
        val testo = ordinata.take(120).joinToString("\n") { v ->
            v.data.format(FMT_DATA) + "  " + (if (anEntrata(v)) "+" else "−") + euro(v.importo) + "  " +
                v.descrizione.ifBlank { v.categoria } + (if (v.conto.isNotBlank()) " · " + v.conto else "")
        } + (if (ordinata.size > 120) "\n… altri " + (ordinata.size - 120) else "")
        val b = OcBuilder(this)
            .setTitle(titolo)
            .setMessage(if (testo.isBlank()) "Nessun movimento." else testo)
            .setPositiveButton("Chiudi", null)
        if (neutro != null) b.setNeutralButton(neutro.first) { _, _ -> neutro.second() }
        b.show()
    }

    private fun tabTag(cont: LinearLayout) {
        val oggi = LocalDate.now()
        val rosso = ContextCompat.getColor(this, R.color.oc_red)
        val verde = ContextCompat.getColor(this, R.color.oc_green)
        val medio = ContextCompat.getColor(this, R.color.oc_text_mid)
        val modi = listOf("Giorno", "Mese", "Anno", "Periodo")
        val mesi = (0 until 36).map { YearMonth.from(oggi).minusMonths(it.toLong()) }
        val anni = anAnni()
        val annoD = anAnno.toIntOrNull() ?: oggi.year
        anRiga(cont) { r ->
            anMenu(r, "Modalità", modi, anModo) { anModo = modi[it] }
            when (anModo) {
                "Giorno" -> anCampoData(r, "Giorno", anGiorno) { anGiorno = it }
                "Mese" -> anMenu(r, "Mese", mesi.map { maiuscola(it.format(FMT_MESE)) }, maiuscola(anMese.format(FMT_MESE))) { anMese = mesi[it] }
                "Anno" -> anMenu(r, "Anno", anni.map { it.toString() }, annoD.toString()) { anAnno = anni[it].toString() }
            }
        }
        if (anModo == "Periodo") anRiga(cont) { r ->
            anCampoData(r, "Dal", anDal) { anDal = it }
            anCampoData(r, "Al", anAl) { anAl = it }
        }
        anContoFuturi(cont)

        val (da, a) = when (anModo) {
            "Giorno" -> anGiorno to anGiorno
            "Mese" -> anMese.atDay(1) to anMese.atEndOfMonth()
            "Anno" -> LocalDate.of(annoD, 1, 1) to LocalDate.of(annoD, 12, 31)
            else -> if (anDal.isAfter(anAl)) anAl to anDal else anDal to anAl
        }
        val taggate = anMovs().filter { v ->
            !v.data.isBefore(da) && !v.data.isAfter(a) && (anFuturi || !v.data.isAfter(oggi)) &&
                v.hashtag.any { it.trimStart('#').isNotEmpty() }
        }
        val perTag = LinkedHashMap<String, ArrayList<Voce>>()
        for (v in taggate) {
            for (t in v.hashtag.map { it.trimStart('#') }.filter { it.isNotEmpty() }.distinct()) {
                perTag.getOrPut(t.lowercase()) { ArrayList() }.add(v)
            }
        }
        if (perTag.isEmpty()) {
            anTesto(cont, "Nessun movimento con tag nel periodo.", colore = medio)
            return
        }
        val ordine = perTag.entries.sortedWith(compareByDescending<Map.Entry<String, ArrayList<Voce>>> { it.value.size }.thenBy { it.key })
        val top = ordine.first()
        anTesto(
            cont,
            "Voci taggate: " + taggate.size + " · Tag unici: " + perTag.size +
                "\nTag più usato: #" + top.key + " (" + top.value.size + ")" +
                "\nTotale importi: " + euro(taggate.sumOf { it.importo }),
            grassetto = true
        )
        sezione(cont, "PER TAG")
        for ((t, l) in ordine) {
            val ent = l.filter { anEntrata(it) }.sumOf { it.importo }
            val usc = l.filter { !anEntrata(it) }.sumOf { it.importo }
            riga(
                cont, "#" + t,
                l.size.toString() + (if (l.size == 1) " movimento" else " movimenti") + " · Entrate " + euro(ent) + " · Uscite " + euro(usc),
                anSaldo(ent - usc), if (ent - usc >= 0) verde else rosso
            ) {
                rc.azzera()
                rc.tag = t
                rc.dal = da.format(FMT_DATA)
                rc.al = a.format(FMT_DATA)
                rcTesto = ""
                rcFuturi = anFuturi
                mostra(Pagina.RICERCA)
            }
        }
    }

    private fun tabDettaglio(cont: LinearLayout) {
        val oggi = LocalDate.now()
        val rosso = ContextCompat.getColor(this, R.color.oc_red)
        val verde = ContextCompat.getColor(this, R.color.oc_green)
        val medio = ContextCompat.getColor(this, R.color.oc_text_mid)
        val modi = listOf("Giorno", "Mese", "Anno", "Periodo")
        val mesi = (0 until 36).map { YearMonth.from(oggi).minusMonths(it.toLong()) }
        val anni = anAnni()
        val annoD = anAnno.toIntOrNull() ?: oggi.year
        anRiga(cont) { r ->
            anMenu(r, "Modalità", modi, anModo) { anModo = modi[it] }
            when (anModo) {
                "Giorno" -> anCampoData(r, "Giorno", anGiorno) { anGiorno = it }
                "Mese" -> anMenu(r, "Mese", mesi.map { maiuscola(it.format(FMT_MESE)) }, maiuscola(anMese.format(FMT_MESE))) { anMese = mesi[it] }
                "Anno" -> anMenu(r, "Anno", anni.map { it.toString() }, annoD.toString()) { anAnno = anni[it].toString() }
            }
        }
        if (anModo == "Periodo") anRiga(cont) { r ->
            anCampoData(r, "Dal", anDal) { anDal = it }
            anCampoData(r, "Al", anAl) { anAl = it }
        }
        val nomiCat = listOf(AN_TUTTE) + Repo.categorie().sortedBy { it.lowercase() }
        anMenu(cont, "Categoria", nomiCat, if (anCat in nomiCat) anCat else AN_TUTTE) { anCat = nomiCat[it] }
        anContoFuturi(cont)

        val (da, a) = when (anModo) {
            "Giorno" -> anGiorno to anGiorno
            "Mese" -> anMese.atDay(1) to anMese.atEndOfMonth()
            "Anno" -> LocalDate.of(annoD, 1, 1) to LocalDate.of(annoD, 12, 31)
            else -> if (anDal.isAfter(anAl)) anAl to anDal else anDal to anAl
        }
        val sel = anMovs().filter { v ->
            !v.data.isBefore(da) && !v.data.isAfter(a) && (anFuturi || !v.data.isAfter(oggi)) &&
                (anCat == AN_TUTTE || v.categoria == anCat)
        }.sortedWith(compareByDescending<Voce> { it.data }.thenByDescending { it.ora })
        val ent = sel.filter { anEntrata(it) }.sumOf { it.importo }
        val usc = sel.filter { !anEntrata(it) }.sumOf { it.importo }
        anTesto(
            cont,
            (if (da == a) da.format(FMT_DATA) else da.format(FMT_DATA) + " → " + a.format(FMT_DATA)) +
                " · " + sel.size + (if (sel.size == 1) " movimento" else " movimenti") +
                "\nEntrate " + euro(ent) + " · Uscite " + euro(usc) + "\nSaldo " + anSaldo(ent - usc),
            grassetto = true, colore = if (ent - usc >= 0) verde else rosso
        )
        if (sel.isEmpty()) return
        if (anCat == AN_TUTTE) {
            sezione(cont, "PER CATEGORIA")
            val perCat = sel.groupBy { it.categoria }.map { (c, l) ->
                Triple(c, l.filter { anEntrata(it) }.sumOf { it.importo }, l.filter { !anEntrata(it) }.sumOf { it.importo })
            }.sortedByDescending { maxOf(it.second, it.third) }
            val mx = perCat.maxOf { maxOf(it.second, it.third) }
            for ((c, e, u) in perCat) {
                if (u > 0) anBarra(cont, c + " · uscite", u, mx, rosso, euro(u))
                if (e > 0) anBarra(cont, c + " · entrate", e, mx, verde, euro(e))
            }
        }
        sezione(cont, "MOVIMENTI")
        for (v in sel.take(150)) {
            val sotto = listOfNotNull(
                v.data.format(FMT_DATA), v.categoria, v.conto.takeIf { it.isNotBlank() }, v.ora.takeIf { it.isNotBlank() }
            ).joinToString(" · ")
            riga(
                cont, v.descrizione.ifBlank { v.categoria }, sotto,
                (if (anEntrata(v)) "+" else "−") + euro(v.importo), if (anEntrata(v)) verde else rosso
            ) { dialogVoce(v) }
        }
        if (sel.size > 150) anTesto(cont, "… altri " + (sel.size - 150) + " movimenti non mostrati", colore = medio, size = 12f)
    }

    private var cfAnno = false
    private var cfFuturi = true
    private var cfM1 = LocalDate.now().monthValue
    private var cfA1 = LocalDate.now().year
    private var cfM2 = LocalDate.now().monthValue
    private var cfA2 = LocalDate.now().year
    private var cfCat = AN_TUTTE
    private var cfGrafico = false

    private class CfTot(var n: Int = 0, var ent: Double = 0.0, var usc: Double = 0.0)

    private fun cfEtichetta(m: Int, a: Int) = if (cfAnno) a.toString() else String.format(Locale.ITALY, "%02d/%d", m, a)

    private fun cfVoci(m: Int, a: Int): List<Voce> {
        val oggi = LocalDate.now()
        return anMovs().filter { v ->
            v.data.year == a && (cfAnno || v.data.monthValue == m) && (cfFuturi || !v.data.isAfter(oggi))
        }
    }

    private fun cfPerCat(voci: List<Voce>): Map<String, CfTot> {
        val r = HashMap<String, CfTot>()
        for (v in voci) {
            val t = r.getOrPut(v.categoria) { CfTot() }
            t.n++
            if (anEntrata(v)) t.ent += v.importo else if (anUscita(v)) t.usc += v.importo
        }
        return r
    }

    private fun tabConfronto(cont: LinearLayout) {
        val oggi = LocalDate.now()
        val rosso = ContextCompat.getColor(this, R.color.oc_red)
        val verde = ContextCompat.getColor(this, R.color.oc_green)
        val medio = ContextCompat.getColor(this, R.color.oc_text_mid)
        val blu = ContextCompat.getColor(this, R.color.oc_blue)
        val arancio = ContextCompat.getColor(this, R.color.oc_orange)
        val anni = (anAnni() + (oggi.year - 1) + (oggi.year + 1)).distinct().sortedDescending()
        val nomiAnni = anni.map { it.toString() }

        anMenu(cont, "Modalità confronto", listOf("Mese", "Anno"), if (cfAnno) "Anno" else "Mese") { cfAnno = it == 1 }
        anContoFuturi(cont, false)
        val chk = MaterialCheckBox(this)
        chk.text = "Includi movimenti futuri nei totali"
        chk.isChecked = cfFuturi
        chk.setOnCheckedChangeListener { _, on -> cfFuturi = on; renderAnalisi() }
        cont.addView(chk)
        anRiga(cont) { r ->
            if (!cfAnno) anMenu(r, "Mese 1", MESI_IT, MESI_IT[cfM1 - 1]) { cfM1 = it + 1 }
            anMenu(r, "Anno 1", nomiAnni, cfA1.toString()) { cfA1 = anni[it] }
        }
        anRiga(cont) { r ->
            if (!cfAnno) anMenu(r, "Mese 2", MESI_IT, MESI_IT[cfM2 - 1]) { cfM2 = it + 1 }
            anMenu(r, "Anno 2", nomiAnni, cfA2.toString()) { cfA2 = anni[it] }
        }

        val v1 = cfVoci(cfM1, cfA1)
        val v2 = cfVoci(cfM2, cfA2)
        val c1 = cfPerCat(v1)
        val c2 = cfPerCat(v2)
        val l1 = cfEtichetta(cfM1, cfA1)
        val l2 = cfEtichetta(cfM2, cfA2)
        val e1 = c1.values.sumOf { it.ent }
        val u1 = c1.values.sumOf { it.usc }
        val e2 = c2.values.sumOf { it.ent }
        val u2 = c2.values.sumOf { it.usc }

        anTesto(
            cont, "P1 · " + l1 + "\nEntrate " + euro(e1) + " · Uscite " + euro(u1) + "\nSaldo " + anSaldo(e1 - u1),
            grassetto = true, colore = blu
        )
        anTesto(
            cont, "P2 · " + l2 + "\nEntrate " + euro(e2) + " · Uscite " + euro(u2) + "\nSaldo " + anSaldo(e2 - u2),
            grassetto = true, colore = arancio
        )
        val dS = (e2 - u2) - (e1 - u1)
        anTesto(
            cont, "Variazione saldo (P2 vs P1): " + anSaldo(dS),
            grassetto = true, colore = if (dS > 0.004) verde else if (dS < -0.004) rosso else medio
        )

        val rb = LinearLayout(this)
        rb.orientation = LinearLayout.HORIZONTAL
        val lpB = { LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) }
        rb.addView(pulsanteSel(if (cfGrafico) "📋 Tabella" else "📊 Grafico", false) { cfGrafico = !cfGrafico; renderAnalisi() }, lpB())
        rb.addView(pulsanteSel("📤 Condividi", false) { condividiConfronto(l1, l2, c1, c2) }, lpB())
        rb.addView(pulsanteSel("↺ Reset", false) {
            val t = LocalDate.now()
            cfM1 = t.monthValue; cfA1 = t.year; cfM2 = t.monthValue; cfA2 = t.year; cfCat = AN_TUTTE
            renderAnalisi()
        }, lpB())
        cont.addView(rb)

        if (cfGrafico) {
            val nomiCat = listOf(AN_TUTTE) + Repo.categorie().sortedBy { it.lowercase() }
            anMenu(cont, "Filtra categoria", nomiCat, if (cfCat in nomiCat) cfCat else AN_TUTTE) { cfCat = nomiCat[it] }
            val f1 = v1.filter { cfCat == AN_TUTTE || it.categoria == cfCat }
            val f2 = v2.filter { cfCat == AN_TUTTE || it.categoria == cfCat }
            fun serie(l: List<Voce>): Map<Int, Double> =
                l.groupBy { if (cfAnno) it.data.monthValue else it.data.dayOfMonth }.mapValues { (_, g) ->
                    g.filter { anEntrata(it) }.sumOf { it.importo } - g.filter { !anEntrata(it) }.sumOf { it.importo }
                }
            val s1 = serie(f1)
            val s2 = serie(f2)
            val chiavi = (s1.keys + s2.keys).toSortedSet().toList()
            if (chiavi.isEmpty()) {
                anTesto(cont, "Nessun movimento nei due periodi.", colore = medio)
                return
            }
            anTesto(cont, "Andamento saldo netto (" + (if (cfAnno) "mensile" else "giornaliero") + ")", grassetto = true)
            anTesto(cont, "■ P1: " + l1, colore = blu, size = 12f)
            anTesto(cont, "■ P2: " + l2, colore = arancio, size = 12f)
            anGrafico(
                cont,
                chiavi.map { if (cfAnno) MESI_IT[it - 1].take(3) else it.toString() },
                chiavi.map { s1[it] ?: 0.0 }, chiavi.map { s2[it] ?: 0.0 },
                blu, arancio
            ) { i ->
                val k = chiavi[i]
                val x1 = s1[k] ?: 0.0
                val x2 = s2[k] ?: 0.0
                (if (cfAnno) MESI_IT[k - 1] else "Giorno " + k) +
                    "\nP1 (" + l1 + "): " + anSaldo(x1) + "\nP2 (" + l2 + "): " + anSaldo(x2) +
                    "\nΔ P2 vs P1: " + anSaldo(x2 - x1)
            }
            return
        }

        val cats = (c1.keys + c2.keys).sortedBy { it.lowercase() }
        if (cats.isEmpty()) {
            anTesto(cont, "Nessun movimento nei due periodi.", colore = medio)
            return
        }
        sezione(cont, "PER CATEGORIA · tocca per i movimenti")
        for (c in cats) {
            val t1 = c1[c] ?: CfTot()
            val t2 = c2[c] ?: CfTot()
            fun parte(l: String, t: CfTot): String {
                val p = listOfNotNull(
                    if (t.ent > 0) "entrate " + euro(t.ent) else null,
                    if (t.usc > 0) "uscite " + euro(t.usc) else null,
                )
                return l + " (" + t.n + "): " + (if (p.isEmpty()) "—" else p.joinToString(" · "))
            }
            val righe = ArrayList<String>()
            righe.add(parte(l1, t1))
            righe.add(parte(l2, t2))
            if (t1.ent > 0 || t2.ent > 0) righe.add("Δ entrate " + anSaldo(t2.ent - t1.ent))
            if (t1.usc > 0 || t2.usc > 0) righe.add("Δ uscite " + anSaldo(t2.usc - t1.usc))
            val bud = Repo.budgetMensile(c)
            if (!cfAnno && bud > 0 && (t1.usc > 0 || t2.usc > 0)) {
                righe.add(
                    "Budget mese " + euro(bud) + " · " + l1 + " " + Math.round(t1.usc / bud * 100) +
                        "% · " + l2 + " " + Math.round(t2.usc / bud * 100) + "%"
                )
            }
            val usc = t1.usc > 0 || t2.usc > 0
            val d = if (usc) t2.usc - t1.usc else t2.ent - t1.ent
            val col = when {
                abs(d) < 0.005 -> medio
                usc -> if (d > 0) rosso else verde
                else -> if (d > 0) verde else rosso
            }
            riga(
                cont, c.ifBlank { "Senza categoria" }, righe.joinToString("\n"),
                (if (d >= 0) "+" else "−") + euro(abs(d)), col
            ) { dialogConfrontoCat(c, v1, v2, l1, l2) }
        }
    }

    private fun dialogConfrontoCat(cat: String, v1: List<Voce>, v2: List<Voce>, l1: String, l2: String) {
        val ord = compareBy<Voce> { it.data }.thenBy { it.ora }
        val tutte = v1.filter { it.categoria == cat }.sortedWith(ord).map { "P1" to it } +
            v2.filter { it.categoria == cat }.sortedWith(ord).map { "P2" to it }
        if (tutte.isEmpty()) return
        val righe = tutte.map { (p, v) ->
            p + " · " + v.data.format(FMT_DATA) + " · " + v.descrizione.ifBlank { v.categoria.ifBlank { "Senza categoria" } } +
                " · " + (if (anEntrata(v)) "+" else "−") + euro(v.importo)
        }
        OcBuilder(this)
            .setTitle(cat.ifBlank { "Senza categoria" } + " · " + l1 + " / " + l2)
            .setItems(righe.toTypedArray()) { _, i -> dialogVoce(tutte[i].second) }
            .setNegativeButton("Chiudi", null)
            .show()
    }

    private fun condividiConfronto(l1: String, l2: String, c1: Map<String, CfTot>, c2: Map<String, CfTot>) {
        val cats = (c1.keys + c2.keys).sortedBy { it.lowercase() }
        if (cats.isEmpty()) { aviso("Nessun movimento da condividere"); return }
        val dati = cats.map {
            val t1 = c1[it] ?: CfTot()
            val t2 = c2[it] ?: CfTot()
            ConfrontoPdf.Riga(it, t1.ent, t1.usc, t2.ent, t2.usc)
        }
        val f = try {
            ConfrontoPdf.crea(this, l1, l2, dati)
        } catch (e: Exception) {
            aviso("Errore nella creazione del PDF: " + e.message)
            return
        }
        ConfrontoPdf.condividi(this, f, l1, l2)
    }

    private class AnAgg(var num: Int = 0, var usc: Double = 0.0, var ent: Double = 0.0, var max: Double = 0.0) {
        val vol get() = if (usc > 0) usc else ent
    }

    private fun anAggrega(movs: List<Voce>, cats: Set<String>?, filtro: (LocalDate) -> Boolean): Map<String, AnAgg> {
        val ris = HashMap<String, AnAgg>()
        for (v in movs) {
            if (!filtro(v.data)) continue
            if (cats != null && v.categoria !in cats) continue
            val r = ris.getOrPut(v.categoria) { AnAgg() }
            r.num++
            r.max = maxOf(r.max, abs(v.importo))
            if (anEntrata(v)) r.ent += v.importo else if (anUscita(v)) r.usc += v.importo
        }
        return ris
    }

    private fun anMesiInPeriodo(date: List<LocalDate>, anno: Int?, meseN: Int?, oggi: LocalDate): Int {
        if (meseN != null) return 1
        if (anno != null) {
            if (anno < oggi.year) return 12
            val m = date.filter { it.year == anno }.map { it.monthValue }
            val ultimo = (m + (if (anno == oggi.year) oggi.monthValue else 1)).maxOrNull() ?: 1
            return ultimo.coerceIn(1, 12)
        }
        if (date.isEmpty()) return 1
        val primo = date.minOrNull()!!
        val ultimo = maxOf(date.maxOrNull()!!, oggi)
        return maxOf(1, (ultimo.year - primo.year) * 12 + ultimo.monthValue - primo.monthValue + 1)
    }

    private fun tabAggrega(cont: LinearLayout) {
        val oggi = LocalDate.now()
        val rosso = ContextCompat.getColor(this, R.color.oc_red)
        val verde = ContextCompat.getColor(this, R.color.oc_green)
        val arancio = ContextCompat.getColor(this, R.color.oc_orange)
        val medio = ContextCompat.getColor(this, R.color.oc_text_mid)
        val anni = anAnni()
        anRiga(cont) { r ->
            anMenu(r, "Mese", listOf("Tutti") + MESI_IT, if (anMeseN == 0) "Tutti" else MESI_IT[anMeseN - 1]) { anMeseN = it }
            anMenu(r, "Anno", listOf("Tutti") + anni.map { it.toString() }, anAnno) { anAnno = if (it == 0) "Tutti" else anni[it - 1].toString() }
        }
        anContoFuturi(cont)
        val anno = anAnno.toIntOrNull()
        val meseN: Int? = if (anMeseN == 0) null else anMeseN
        val movs = anMovs()
        fun filtro(d: LocalDate) =
            (anFuturi || !d.isAfter(oggi)) && (anno == null || d.year == anno) && (meseN == null || d.monthValue == meseN)

        val rigaBt = LinearLayout(this)
        rigaBt.orientation = LinearLayout.HORIZONTAL
        rigaBt.addView(pulsanteSel("Categorie (" + anAgg.size + "/10)", false) {
            dialogScegliCategorie("Categorie da analizzare (max 10)", anAgg, 10)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        rigaBt.addView(pulsanteSel("Top 10 per spesa", false) {
            val top = anAggrega(movs, null, ::filtro).entries.filter { it.value.usc > 0 }
                .sortedByDescending { it.value.usc }.take(10).map { it.key }
            anAgg.clear(); anAgg.addAll(top); renderAnalisi()
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        rigaBt.addView(pulsanteSel("Azzera", false) { anAgg.clear(); renderAnalisi() },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        cont.addView(rigaBt)

        val scelte = Repo.categorie().filter { it in anAgg }.toSet()
        val etich = (if (meseN == null) "Tutti i mesi" else MESI_IT[meseN - 1]) + " " + (anno?.toString() ?: "tutti gli anni")
        if (scelte.isEmpty()) {
            anTesto(cont, "Seleziona almeno una categoria da analizzare.", colore = medio)
            return
        }
        val ris = anAggrega(movs, scelte, ::filtro)
        val totUscPeriodo = anAggrega(movs, null, ::filtro).values.sumOf { it.usc }
        val dateSel = movs.filter { filtro(it.data) && it.categoria in scelte }.map { it.data }
        val nMesi = anMesiInPeriodo(dateSel, anno, meseN, oggi)
        val parzM = anno == oggi.year && meseN == oggi.monthValue
        val parzA = anno == oggi.year && meseN == null
        val parziale = parzM || parzA
        fun mkFiltro(y: Int, m: Int?): (LocalDate) -> Boolean = { d ->
            if (d.year != y || (m != null && d.monthValue != m)) false
            else if (parzM) d.dayOfMonth <= oggi.dayOfMonth
            else if (parzA) (d.monthValue < oggi.monthValue || (d.monthValue == oggi.monthValue && d.dayOfMonth <= oggi.dayOfMonth))
            else true
        }
        var risPrec: Map<String, AnAgg>? = null
        var risAnno: Map<String, AnAgg>? = null
        var txtPrec = ""
        var txtAnno = ""
        if (anno != null && meseN != null) {
            val (py, pm) = if (meseN > 1) anno to meseN - 1 else anno - 1 to 12
            risPrec = anAggrega(movs, scelte, mkFiltro(py, pm))
            risAnno = anAggrega(movs, scelte, mkFiltro(anno - 1, meseN))
            txtPrec = "Δ vs prec.: rispetto a " + MESI_IT[pm - 1] + " " + py
            txtAnno = "Δ anno prec.: rispetto a " + MESI_IT[meseN - 1] + " " + (anno - 1)
        } else if (anno != null) {
            risPrec = anAggrega(movs, scelte, mkFiltro(anno - 1, null))
            txtPrec = "Δ vs prec.: rispetto al " + (anno - 1)
        }
        val risOggi = anAggrega(movs, scelte) { filtro(it) && !it.isAfter(oggi) }
        val risCfr = if (parziale) risOggi else ris
        val budM = Repo.categorie().associateWith { Repo.budgetMensile(it) }.filterValues { it > 0 }
        val budA = Repo.categorie().associateWith { Repo.budgetAnnuo(it) }.filterValues { it > 0 }
        fun budgetPeriodo(c: String): Double = when {
            meseN != null -> budM[c] ?: 0.0
            anno != null && budA[c] != null -> budA[c]!!
            else -> (budM[c] ?: 0.0) * nMesi
        }
        val ggMese = YearMonth.from(oggi).lengthOfMonth()
        val ordinati = ris.entries.sortedBy { -(it.value.ent - it.value.usc) }

        var te = 0.0; var tu = 0.0; var tn = 0
        for ((c, r) in ordinati) {
            te += r.ent; tu += r.usc; tn += r.num
            val cfr = risCfr[c]?.vol ?: 0.0
            val pPrec = risPrec?.let { it[c]?.vol ?: 0.0 }
            val pAnno = risAnno?.let { it[c]?.vol ?: 0.0 }
            val bud = budgetPeriodo(c)
            val superato = bud > 0 && r.usc > bud
            val righe = ArrayList<String>()
            righe.add(r.num.toString() + " mov · Uscite " + euro(r.usc) + " · Entrate " + euro(r.ent))
            righe.add("Medio " + euro((r.usc + r.ent) / r.num) + " · Max " + euro(r.max) +
                (if (totUscPeriodo > 0 && r.usc > 0) " · " + anPct(r.usc / totUscPeriodo * 100) + " delle uscite" else ""))
            if (meseN == null) righe.add("Media/mese " + euro(r.vol / nMesi) + " (su " + nMesi + (if (nMesi == 1) " mese)" else " mesi)"))
            if (risPrec != null) righe.add("Δ vs prec. " + anDelta(cfr, pPrec) + (if (risAnno != null) " · Δ anno prec. " + anDelta(cfr, pAnno) else ""))
            if (bud > 0) righe.add("Budget " + euro(bud) + (if (r.usc > 0) " · " + anPct(r.usc / bud * 100) + (if (superato) " ▲" else "") else ""))
            if (parzM && (r.usc > 0 || (risOggi[c]?.usc ?: 0.0) > 0)) {
                val prev = maxOf(r.usc, (risOggi[c]?.usc ?: 0.0) / oggi.dayOfMonth * ggMese)
                righe.add("Prev. fine mese " + euro(prev))
            }
            riga(
                cont, c, righe.joinToString("\n"), anSaldo(r.ent - r.usc),
                if (superato) arancio else if (r.ent - r.usc >= 0) verde else rosso
            ) { anElencoMovimenti(c + " – " + etich, movs.filter { filtro(it.data) && it.categoria == c }) }
        }
        sezione(cont, "TOTALE · " + etich.uppercase())
        anTesto(
            cont,
            tn.toString() + " movimenti\nUscite " + euro(tu) + " · Entrate " + euro(te) + "\nSaldo " + anSaldo(te - tu),
            grassetto = true, colore = if (te - tu >= 0) verde else rosso
        )
        val note = listOf(txtPrec, txtAnno).filter { it.isNotEmpty() }.toMutableList()
        if (parziale && note.isNotEmpty()) note.add("(confronto sullo stesso intervallo di giorni)")
        if (meseN == null) note.add("Media/mese calcolata su $nMesi " + (if (nMesi == 1) "mese" else "mesi"))
        if (budM.isNotEmpty() || budA.isNotEmpty()) note.add("▲ = oltre budget")
        anTesto(cont, note.joinToString("\n"), colore = medio, size = 12f)
        sezione(cont, "CONFRONTO")
        val mx = ordinati.maxOfOrNull { maxOf(it.value.ent, it.value.usc) } ?: 0.0
        for ((c, r) in ordinati) {
            anBarra(cont, c + " · uscite", r.usc, mx, if (budgetPeriodo(c) > 0 && r.usc > budgetPeriodo(c)) arancio else rosso, euro(r.usc))
            if (r.ent > 0) anBarra(cont, c + " · entrate", r.ent, mx, verde, euro(r.ent))
        }
    }

    private fun tabTimeMachine(cont: LinearLayout) {
        val oggi = LocalDate.now()
        val rosso = ContextCompat.getColor(this, R.color.oc_red)
        val verde = ContextCompat.getColor(this, R.color.oc_green)
        val medio = ContextCompat.getColor(this, R.color.oc_text_mid)
        val anni = anAnni()
        anMenu(cont, "Anno", listOf("Tutti") + anni.map { it.toString() }, anAnno) { anAnno = if (it == 0) "Tutti" else anni[it - 1].toString() }
        anContoFuturi(cont)
        val anno = anAnno.toIntOrNull()
        val periodo = if (anno == null) "Tutti gli anni" else "Anno $anno"
        val c = anAggrega(anMovs().filter { it.categoria.isNotBlank() }, null) { d ->
            (anFuturi || !d.isAfter(oggi)) && (anno == null || d.year == anno)
        }
        val ris = anTm.filter { it in c }.sortedByDescending { c[it]!!.usc - c[it]!!.ent }
        val top10 = c.entries.sortedByDescending { it.value.usc - it.value.ent }.take(10)
        sezione(cont, "TOP 10 CATEGORIE PER RISPARMIO POSSIBILE")
        for ((cat, r) in top10) {
            val chk = MaterialCheckBox(this)
            chk.text = cat + " – " + r.num + "× · " + euro(r.usc - r.ent)
            chk.isChecked = cat in anTm
            chk.setOnCheckedChangeListener { _, on ->
                if (on) anTm.add(cat) else anTm.remove(cat)
                renderAnalisi()
            }
            cont.addView(chk)
        }
        val rb = LinearLayout(this)
        rb.orientation = LinearLayout.HORIZONTAL
        rb.addView(pulsanteSel("Altre categorie…", false) { dialogScegliCategorie("Categorie da escludere", anTm, 40) },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        rb.addView(pulsanteSel("Azzera", false) { anTm.clear(); renderAnalisi() },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        cont.addView(rb)
        if (ris.isEmpty()) {
            anTesto(cont, "Seleziona almeno una categoria: vedrai quanto avresti risparmiato escludendola.", colore = medio)
            return
        }
        sezione(cont, "RISPARMIO TEORICO · " + periodo.uppercase())
        val mx = ris.maxOf { abs(c[it]!!.usc - c[it]!!.ent) }
        var tot = 0.0
        var tn = 0
        for (k in ris) {
            val r = c[k]!!
            val ris1 = r.usc - r.ent
            tot += ris1; tn += r.num
            riga(cont, k, r.num.toString() + " mov · Uscite " + euro(r.usc) + " · Entrate " + euro(r.ent), euro(ris1),
                if (ris1 >= 0) verde else rosso) {
                anElencoMovimenti(k + " – " + periodo, anMovs().filter { v ->
                    v.categoria == k && (anFuturi || !v.data.isAfter(oggi)) && (anno == null || v.data.year == anno)
                })
            }
            anBarra(cont, k, ris1, mx, if (ris1 >= 0) verde else rosso, euro(ris1))
        }
        anTesto(
            cont,
            "Risparmio totale teorico: " + euro(tot) + "\nProiezione del risparmio ottenibile " +
                (if (anno == null) "su tutti gli anni disponibili" else "nel $anno") +
                ", escludendo le categorie selezionate (Uscite − Entrate).",
            grassetto = true, colore = if (tot >= 0) verde else rosso
        )
    }

    private fun tabTrend(cont: LinearLayout) {
        val oggi = LocalDate.now()
        val rosso = ContextCompat.getColor(this, R.color.oc_red)
        val verde = ContextCompat.getColor(this, R.color.oc_green)
        val medio = ContextCompat.getColor(this, R.color.oc_text_mid)
        val anni = anAnni()
        val annoT = anAnno.toIntOrNull() ?: oggi.year
        val nomiCat = listOf(AN_TUTTE) + Repo.categorie().sortedBy { it.lowercase() }
        anMenu(cont, "Anno", anni.map { it.toString() }, annoT.toString()) { anAnno = anni[it].toString() }
        anMenu(cont, "Categoria", nomiCat, if (anCat in nomiCat) anCat else AN_TUTTE) { anCat = nomiCat[it] }
        anContoFuturi(cont)
        val n = IntArray(13)
        val u = DoubleArray(13)
        val e = DoubleArray(13)
        for (v in anMovs()) {
            if (v.data.year != annoT || (!anFuturi && v.data.isAfter(oggi))) continue
            if (anCat != AN_TUTTE && v.categoria != anCat) continue
            val m = v.data.monthValue
            n[m]++
            if (anEntrata(v)) e[m] += v.importo else if (anUscita(v)) u[m] += v.importo
        }
        val nome = if (anCat == AN_TUTTE) "tutte le categorie" else anCat
        sezione(cont, "ANDAMENTO MENSILE · " + nome.uppercase() + " · " + annoT)
        anGrafico(
            cont, MESI_IT.map { it.take(3) }, (1..12).map { e[it] }, (1..12).map { u[it] }, verde, rosso
        ) { i ->
            val m = i + 1
            MESI_IT[i] + " " + annoT + "\nEntrate " + euro(e[m]) + " · Uscite " + euro(u[m]) +
                "\nSaldo " + anSaldo(e[m] - u[m]) + " · " + n[m] + " movimenti"
        }
        var prec: Double? = null
        val attivi = ArrayList<Pair<Int, Double>>()
        for (m in 1..12) {
            val futuro = LocalDate.of(annoT, m, 1).isAfter(oggi)
            val var_ = if (n[m] > 0 && prec != null && prec > 0) anPctS((u[m] - prec) / prec * 100) else ""
            if (n[m] > 0) {
                attivi.add(m to u[m])
                if (u[m] != 0.0) prec = u[m]
            }
            riga(
                cont, MESI_IT[m - 1] + (if (futuro) " (futuro)" else ""),
                if (n[m] == 0) "Nessun movimento" else n[m].toString() + " mov · Entrate " + euro(e[m]) +
                    (if (var_.isNotEmpty()) " · uscite " + var_ + " vs mese prec." else ""),
                if (n[m] == 0) "" else "−" + euro(u[m]),
                if (n[m] == 0) medio else if (e[m] >= u[m]) verde else rosso,
                if (n[m] == 0) null else ({
                    anModo = "Mese"; anMese = YearMonth.of(annoT, m); catTab = 0; renderAnalisi()
                })
            )
        }
        val tu = u.sum(); val te = e.sum()
        anTesto(
            cont, "Totale " + annoT + "\nEntrate " + euro(te) + " · Uscite " + euro(tu) + "\nSaldo " + anSaldo(te - tu),
            grassetto = true, colore = if (te - tu >= 0) verde else rosso
        )
        if (attivi.isNotEmpty()) {
            val mx = attivi.maxByOrNull { it.second }!!
            anTesto(
                cont,
                "Media uscite sui " + attivi.size + " mesi con movimenti: " + euro(tu / attivi.size) +
                    "\nMese con più uscite: " + MESI_IT[mx.first - 1] + " (" + euro(mx.second) + ")" +
                    "\nTocca un mese per aprirne il dettaglio.",
                colore = medio, size = 12f
            )
        }
    }

    private class AnGr(var n: Int = 0, var u: Double = 0.0, var e: Double = 0.0, val voci: ArrayList<Voce> = ArrayList())

    private fun tabDistribuzione(cont: LinearLayout) {
        val oggi = LocalDate.now()
        val rosso = ContextCompat.getColor(this, R.color.oc_red)
        val verde = ContextCompat.getColor(this, R.color.oc_green)
        val medio = ContextCompat.getColor(this, R.color.oc_text_mid)
        val anni = anAnni()
        val nomiCat = listOf(AN_TUTTE) + Repo.categorie().sortedBy { it.lowercase() }
        anMenu(cont, "Raggruppa per", AN_MODI_DIST, anDist) { anDist = AN_MODI_DIST[it] }
        anRiga(cont) { r ->
            anMenu(r, "Mese", listOf("Tutti") + MESI_IT, if (anMeseN == 0) "Tutti" else MESI_IT[anMeseN - 1]) { anMeseN = it }
            anMenu(r, "Anno", listOf("Tutti") + anni.map { it.toString() }, anAnno) { anAnno = if (it == 0) "Tutti" else anni[it - 1].toString() }
        }
        anMenu(cont, "Categoria", nomiCat, if (anCat in nomiCat) anCat else AN_TUTTE) { anCat = nomiCat[it] }
        anContoFuturi(cont)
        val anno = anAnno.toIntOrNull()
        val meseN: Int? = if (anMeseN == 0) null else anMeseN
        val movs = anMovs().filter { v ->
            (anno == null || v.data.year == anno) && (meseN == null || v.data.monthValue == meseN) &&
                (anFuturi || !v.data.isAfter(oggi)) && (anCat == AN_TUTTE || v.categoria == anCat)
        }
        val gruppi = java.util.TreeMap<Int, Pair<String, AnGr>>()
        if (anDist == AN_MODI_DIST[0]) AN_GIORNI.forEachIndexed { i, g -> gruppi[i] = g to AnGr() }
        if (anDist == AN_MODI_DIST[1]) AN_FASCE.forEachIndexed { i, f -> gruppi[i] = f.first to AnGr() }
        for (v in movs) {
            val h = v.ora.trim().split(":").firstOrNull()?.toIntOrNull()?.takeIf { it in 0..23 }
            val (k, nome) = when {
                anDist == AN_MODI_DIST[0] -> v.data.dayOfWeek.value - 1 to AN_GIORNI[v.data.dayOfWeek.value - 1]
                h == null -> 99 to AN_SENZA_ORA
                anDist == AN_MODI_DIST[1] -> {
                    val i = AN_FASCE.indexOfFirst { h >= it.second && h < it.third }
                    i to AN_FASCE[i].first
                }
                else -> h to String.format("%02d:00–%02d:59", h, h)
            }
            val g = gruppi.getOrPut(k) { nome to AnGr() }.second
            g.n++
            g.voci.add(v)
            if (anEntrata(v)) g.e += abs(v.importo) else if (anUscita(v)) g.u += abs(v.importo)
        }
        val elenco = gruppi.values.filter { anDist != AN_MODI_DIST[2] || it.second.n > 0 }
        val totU = elenco.sumOf { it.second.u }
        val totE = elenco.sumOf { it.second.e }
        val totN = elenco.sumOf { it.second.n }
        val nome = if (anCat == AN_TUTTE) "tutte le categorie" else anCat
        sezione(cont, "DISTRIBUZIONE\n" + anDist.uppercase() + " · " + nome.uppercase())
        if (totN == 0) {
            anTesto(cont, "Nessun movimento nel periodo.", colore = medio)
            return
        }
        anGrafico(cont, elenco.map { it.first.take(if (anDist == AN_MODI_DIST[0]) 3 else 5) }, elenco.map { it.second.e }, elenco.map { it.second.u }, verde, rosso) { i ->
            val (nm, g) = elenco[i]
            nm + "\nMovimenti " + g.n + " · Entrate " + euro(g.e) + " · Uscite " + euro(g.u) +
                (if (totU > 0 && g.u > 0) "\n" + anPct(g.u / totU * 100) + " delle uscite" else "")
        }
        for ((nm, g) in elenco) {
            riga(
                cont, nm,
                if (g.n == 0) "Nessun movimento" else g.n.toString() + " mov · Entrate " + euro(g.e) + " · Medio " + euro((g.u + g.e) / g.n) +
                    (if (totU > 0 && g.u > 0) " · " + anPct(g.u / totU * 100) else ""),
                if (g.n == 0) "" else "−" + euro(g.u), if (g.n == 0) medio else if (g.e >= g.u) verde else rosso,
                if (g.n == 0) null else ({ anElencoMovimenti(nm + " – " + nome, g.voci) })
            )
        }
        val top = elenco.filter { it.first != AN_SENZA_ORA }.maxByOrNull { it.second.u }
        anTesto(
            cont,
            "Totale " + totN + " movimenti\nEntrate " + euro(totE) + " · Uscite " + euro(totU) + "\nSaldo " + anSaldo(totE - totU) +
                (if (top != null && top.second.u > 0) "\nPiù uscite: " + top.first + " (" + euro(top.second.u) + ")" else ""),
            grassetto = true, colore = if (totE - totU >= 0) verde else rosso
        )
    }

    private class AnSf(
        val cat: String, val anno: Int, val mese: Int?, val bud: Double, val speso: Double,
        val num: Int, val quando: LocalDate?, val voci: List<Voce>,
    ) {
        val eccesso get() = speso - bud
    }

    private fun anMesiStorico(movs: List<Voce>, oggi: LocalDate, max: Int = 12): Int {
        val date = movs.map { it.data }.filter { !it.isAfter(oggi) }
        if (date.isEmpty()) return 0
        val primo = date.minOrNull()!!
        val chiusi = (oggi.year - primo.year) * 12 + oggi.monthValue - primo.monthValue
        return maxOf(0, minOf(max, chiusi))
    }

    private fun anTotaliMensili(movs: List<Voce>, entrata: Boolean, cat: String?, n: Int, oggi: LocalDate): List<Double> {
        val fin = ArrayList<YearMonth>()
        var ym = YearMonth.from(oggi)
        repeat(n) { ym = ym.minusMonths(1); fin.add(ym) }
        val tot = HashMap<YearMonth, Double>()
        for (v in movs) {
            if (anEntrata(v) != entrata || (!entrata && !anUscita(v))) continue
            if (cat != null && v.categoria != cat) continue
            val k = YearMonth.from(v.data)
            if (k in fin) tot[k] = (tot[k] ?: 0.0) + v.importo
        }
        return fin.map { tot[it] ?: 0.0 }
    }

    private fun anStima(valori: List<Double>): Triple<Double, Double, Int>? {
        val attivi = valori.filter { it > 0.004 }.sorted()
        if (attivi.size < 3) return null
        val med = if (attivi.size % 2 == 1) attivi[attivi.size / 2] else (attivi[attivi.size / 2 - 1] + attivi[attivi.size / 2]) / 2
        return Triple(Math.round(med * 100.0) / 100.0, Math.round(valori.sum() / valori.size * 12 * 100.0) / 100.0, attivi.size)
    }

    private fun tabBudgetSuperato(cont: LinearLayout) {
        val oggi = LocalDate.now()
        val rosso = ContextCompat.getColor(this, R.color.oc_red)
        val arancio = ContextCompat.getColor(this, R.color.oc_orange)
        val medio = ContextCompat.getColor(this, R.color.oc_text_mid)
        val anni = anAnni()
        anRiga(cont) { r ->
            anMenu(r, "Mese", listOf("Tutti") + MESI_IT, if (anMeseN == 0) "Tutti" else MESI_IT[anMeseN - 1]) { anMeseN = it }
            anMenu(r, "Anno", listOf("Tutti") + anni.map { it.toString() }, anAnno) { anAnno = if (it == 0) "Tutti" else anni[it - 1].toString() }
        }
        anContoFuturi(cont)
        val anno = anAnno.toIntOrNull()
        val meseN: Int? = if (anMeseN == 0) null else anMeseN
        val cats = Repo.categorie()
        val budM = cats.associateWith { Repo.budgetMensile(it) }.filterValues { it > 0 }
        val budA = cats.associateWith { Repo.budgetAnnuo(it) }.filterValues { it > 0 }

        val rb = LinearLayout(this)
        rb.orientation = LinearLayout.HORIZONTAL
        rb.addView(unaRiga(pulsanteSel("✏️ Modifica budget", false) {
            val ordinate = cats.sortedBy { it.lowercase() }
            OcBuilder(this).setTitle("Budget di quale categoria?")
                .setItems(ordinate.toTypedArray()) { _, i -> dialogCategoria(ordinate[i]) }
                .setNegativeButton("Annulla", null).show()
        }), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        rb.addView(unaRiga(pulsanteSel("💡 Suggerisci tutti", false) { dialogSuggerisciBudget() }),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        cont.addView(rb)

        fun filtro(d: LocalDate) =
            (anFuturi || !d.isAfter(oggi)) && (anno == null || d.year == anno) && (meseN == null || d.monthValue == meseN)
        val mesi = HashMap<Triple<String, Int, Int>, ArrayList<Voce>>()
        val anniMap = HashMap<Pair<String, Int>, ArrayList<Voce>>()
        for (v in anMovs()) {
            if (!filtro(v.data) || !anUscita(v)) continue
            if (v.categoria in budM) mesi.getOrPut(Triple(v.categoria, v.data.year, v.data.monthValue)) { ArrayList() }.add(v)
            if (v.categoria in budA && meseN == null) anniMap.getOrPut(v.categoria to v.data.year) { ArrayList() }.add(v)
        }
        fun riga1(c: String, y: Int, m: Int?, l: List<Voce>, bud: Double): AnSf? {
            val speso = l.sumOf { it.importo }
            if (speso <= bud) return null
            var cum = 0.0
            var quando: LocalDate? = null
            for (v in l.sortedWith(compareBy<Voce> { it.data }.thenBy { it.ora })) {
                cum += v.importo
                if (cum > bud) { quando = v.data; break }
            }
            return AnSf(c, y, m, bud, speso, l.size, quando, l)
        }
        val righe = ArrayList<AnSf>()
        for ((k, l) in mesi) riga1(k.first, k.second, k.third, l, budM[k.first]!!)?.let { righe.add(it) }
        for ((k, l) in anniMap) riga1(k.first, k.second, null, l, budA[k.first]!!)?.let { righe.add(it) }
        righe.sortWith(compareByDescending<AnSf> { it.eccesso }.thenBy { it.cat })
        val etich = (if (meseN == null) "Tutti i mesi" else MESI_IT[meseN - 1]) + " " + (anno?.toString() ?: "tutti gli anni")
        sezione(cont, "BUDGET SUPERATO · " + etich.uppercase())
        if (budM.isEmpty() && budA.isEmpty()) {
            anTesto(cont, "Nessun budget impostato: imposta i budget delle categorie per vedere i superamenti.", colore = medio)
            return
        }
        if (righe.isEmpty()) {
            anTesto(cont, "✅ Nessun budget superato (" + (budM.keys + budA.keys).size + " categorie con budget).", colore = medio)
            return
        }
        val perCat = righe.groupBy { it.cat }
        anTesto(
            cont,
            righe.size.toString() + (if (righe.size == 1) " superamento" else " superamenti") + " in " + perCat.size +
                (if (perCat.size == 1) " categoria" else " categorie") + "\nSuperamento totale " + euro(righe.sumOf { it.eccesso }),
            grassetto = true, colore = rosso
        )
        for (r in righe) {
            val periodo = if (r.mese != null) MESI_IT[r.mese - 1] + " " + r.anno else "Anno " + r.anno
            riga(
                cont, r.cat + " · " + periodo,
                "Budget " + euro(r.bud) + " · Speso " + euro(r.speso) + " (" + anPct(r.speso / r.bud * 100) + ")" +
                    "\n" + r.num + " mov" + (if (r.quando != null) " · superato il " + r.quando.format(FMT_DATA) else ""),
                "+" + euro(r.eccesso), arancio
            ) { anElencoMovimenti(r.cat + " – " + periodo, r.voci, "Modifica budget" to { dialogCategoria(r.cat) }) }
        }
        sezione(cont, "SUPERAMENTO PER CATEGORIA")
        val ord = perCat.entries.sortedByDescending { e -> e.value.sumOf { it.eccesso } }
        val mx = ord.maxOf { e -> e.value.sumOf { it.eccesso } }
        for ((c, l) in ord) anBarra(cont, c, l.sumOf { it.eccesso }, mx, arancio, euro(l.sumOf { it.eccesso }) + " · " + l.size + "×")
    }

    private fun dialogSuggerisciBudget() {
        val oggi = LocalDate.now()
        val movs = Repo.voci()
        val n = anMesiStorico(movs, oggi)
        val righe = Repo.categorie().sortedBy { it.lowercase() }.mapNotNull { c ->
            if (n < 3) null else anStima(anTotaliMensili(movs, false, c, n, oggi))?.let { c to it }
        }
        if (righe.isEmpty()) {
            aviso("Storico insufficiente: servono almeno 3 mesi chiusi con spese")
            return
        }
        val sel = BooleanArray(righe.size) { true }
        OcBuilder(this)
            .setTitle("Suggerisci budget (ultimi $n mesi chiusi)")
            .setMultiChoiceItems(
                righe.map { (c, s) ->
                    c + ": " + (Repo.budgetMensile(c).takeIf { it > 0 }?.let { euro(it) } ?: "—") + " → " + euro(s.first) +
                        "/mese · " + euro(s.second) + "/anno"
                }.toTypedArray(), sel
            ) { _, i, on -> sel[i] = on }
            .setNegativeButton("Annulla", null)
            .setPositiveButton("Applica") { _, _ ->
                var k = 0
                righe.forEachIndexed { i, (c, s) ->
                    if (sel[i]) { Repo.salvaCategoria(c, s.first, s.second, Repo.classe(c)); k++ }
                }
                refresh()
                aviso("Budget aggiornati per $k categorie")
            }
            .show()
    }

    private fun tabTarget(cont: LinearLayout) {
        val oggi = LocalDate.now()
        val rosso = ContextCompat.getColor(this, R.color.oc_red)
        val verde = ContextCompat.getColor(this, R.color.oc_green)
        val arancio = ContextCompat.getColor(this, R.color.oc_orange)
        val medio = ContextCompat.getColor(this, R.color.oc_text_mid)
        val anni = anAnni()
        val annoT = anAnno.toIntOrNull() ?: oggi.year
        anMenu(cont, "Anno", anni.map { it.toString() }, annoT.toString()) { anAnno = anni[it].toString() }
        anContoFuturi(cont)
        val rb = LinearLayout(this)
        rb.orientation = LinearLayout.HORIZONTAL
        rb.addView(pulsanteSel("✏️ Imposta target", false) { dialogTarget() },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        rb.addView(pulsanteSel("💡 Suggerisci", false) { dialogSuggerisciTarget() },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        cont.addView(rb)

        val tm = Repo.targetMese
        val ta = Repo.targetAnno
        val n = IntArray(13)
        val u = DoubleArray(13)
        val e = DoubleArray(13)
        for (v in anMovs()) {
            if (v.data.year != annoT || (!anFuturi && v.data.isAfter(oggi))) continue
            val m = v.data.monthValue
            n[m]++
            if (anEntrata(v)) e[m] += v.importo else if (anUscita(v)) u[m] += v.importo
        }
        sezione(cont, "TARGET DI SPESA · $annoT")
        if (tm <= 0 && ta <= 0) anTesto(cont, "Target non impostati: usa «Imposta target» o «Suggerisci target».", colore = medio)
        if (tm > 0) {
            anGrafico(cont, MESI_IT.map { it.take(3) }, (1..12).map { u[it] }, (1..12).map { tm }, rosso, arancio) { i ->
                val m = i + 1
                MESI_IT[i] + " " + annoT + "\nUscite " + euro(u[m]) + " · Target " + euro(tm) + " (" + anPct(u[m] / tm * 100) + ")"
            }
        }
        for (m in 1..12) {
            val perc = if (tm > 0) u[m] / tm * 100 else null
            val futuro = LocalDate.of(annoT, m, 1).isAfter(oggi)
            val col = when {
                n[m] == 0 || perc == null || futuro -> medio
                perc < 80 -> verde
                perc <= 100 -> arancio
                else -> rosso
            }
            riga(
                cont, MESI_IT[m - 1] + (if (futuro) " (futuro)" else ""),
                if (n[m] == 0) "Nessun movimento" else n[m].toString() + " mov" +
                    (if (tm > 0) " · Target " + euro(tm) + " · " + anPct(perc!!) + " · residuo " + anSaldo(tm - u[m]) else ""),
                if (n[m] == 0) "" else euro(u[m]), col,
                if (n[m] == 0) null else ({ anModo = "Mese"; anMese = YearMonth.of(annoT, m); anCat = AN_TUTTE; catTab = 0; renderAnalisi() })
            )
        }
        val tu = u.sum()
        val percA = if (ta > 0) tu / ta * 100 else null
        var proiez: Double? = null
        var media: Double? = null
        if (annoT == oggi.year) {
            val chiusi = (1 until oggi.monthValue).map { u[it] }
            if (chiusi.isNotEmpty()) {
                val md = chiusi.average()
                media = md
                proiez = chiusi.sum() + (oggi.monthValue..12).sumOf { maxOf(u[it], md) }
            }
        } else proiez = tu
        val sopra = (1..12).count { tm > 0 && u[it] > tm }
        anTesto(
            cont,
            "Totale $annoT: " + euro(tu) + (if (ta > 0) " di " + euro(ta) + " (" + anPct(percA!!) + ") · residuo " + anSaldo(ta - tu) else "") +
                (if (tm > 0) "\nMesi oltre target: $sopra" else "") +
                (if (proiez != null && annoT == oggi.year) "\nProiezione fine anno: " + euro(proiez) +
                    (if (ta > 0) (if (proiez > ta) " (oltre il target)" else " (nel target)") else "") else ""),
            grassetto = true, colore = if (ta > 0 && tu > ta) rosso else verde
        )
        if (media != null) anTesto(cont, "Proiezione: mesi chiusi + per ogni mese restante il maggiore tra il registrato e la media dei mesi chiusi (" + euro(media) + ").", colore = medio, size = 12f)
        val bm = Repo.categorie().sumOf { Repo.budgetMensile(it) }
        val ba = Repo.categorie().sumOf { c -> Repo.budgetAnnuo(c).takeIf { it > 0 } ?: (Repo.budgetMensile(c) * 12) }
        val coer = ArrayList<Pair<String, Boolean>>()
        if (tm > 0 && bm > 0) coer.add("Budget mensili categorie " + euro(bm) + (if (bm > tm) " > " else " ≤ ") + "Target mese " + euro(tm) + (if (bm > tm) " (incoerente)" else "") to (bm <= tm))
        if (ta > 0 && ba > 0) coer.add("Budget annuali categorie " + euro(ba) + (if (ba > ta) " > " else " ≤ ") + "Target anno " + euro(ta) + (if (ba > ta) " (incoerente)" else "") to (ba <= ta))
        if (tm > 0 && ta > 0) coer.add("Target mese × 12 = " + euro(tm * 12) + (if (tm * 12 > ta) " > " else " ≤ ") + "Target anno " + euro(ta) + (if (tm * 12 > ta) " (incoerente)" else "") to (tm * 12 <= ta))
        for ((t, ok) in coer) anTesto(cont, t, colore = if (ok) verde else rosso, size = 12f)
    }

    private fun dialogSuggerisciTarget() {
        val oggi = LocalDate.now()
        val movs = Repo.voci()
        val n = anMesiStorico(movs, oggi)
        val opz = ArrayList<Triple<String, Double, Double>>()
        if (n >= 3) {
            anStima(anTotaliMensili(movs, false, null, n, oggi))?.let {
                opz.add(Triple("Storico spese (mediana ultimi $n mesi)", it.first, it.second))
            }
            anStima(anTotaliMensili(movs, true, null, n, oggi))?.let {
                val m = Math.round(it.first * 0.8 * 100.0) / 100.0
                opz.add(Triple("Da entrate: 80% della mediana (" + euro(it.first) + ")", m, Math.round(m * 12 * 100.0) / 100.0))
            }
        }
        val cats = Repo.categorie()
        val sm = cats.sumOf { Repo.budgetMensile(it) }
        val sa = cats.sumOf { c -> Repo.budgetAnnuo(c).takeIf { it > 0 } ?: (Repo.budgetMensile(c) * 12) }
        if (sm > 0 || sa > 0) opz.add(Triple("Somma dei budget delle categorie", if (sm > 0) sm else Math.round(sa / 12 * 100.0) / 100.0, sa))
        if (opz.isEmpty()) {
            aviso("Storico insufficiente: servono almeno 3 mesi chiusi")
            return
        }
        OcBuilder(this)
            .setTitle("Suggerisci target")
            .setItems(opz.map { it.first + "\n" + euro(it.second) + "/mese · " + euro(it.third) + "/anno" }.toTypedArray()) { _, i ->
                Repo.setTarget(opz[i].second, opz[i].third)
                refresh()
                aviso("Target aggiornato")
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun tab503020(cont: LinearLayout) {
        val oggi = LocalDate.now()
        val rosso = ContextCompat.getColor(this, R.color.oc_red)
        val verde = ContextCompat.getColor(this, R.color.oc_green)
        val arancio = ContextCompat.getColor(this, R.color.oc_orange)
        val medio = ContextCompat.getColor(this, R.color.oc_text_mid)
        val anni = anAnni()
        val classi = listOf("Bisogni", "Desideri", "Risparmio")
        val quote = mapOf("Bisogni" to 50.0, "Desideri" to 30.0, "Risparmio" to 20.0)
        anRiga(cont) { r ->
            anMenu(r, "Anno", listOf("Tutti") + anni.map { it.toString() }, anAnno) { anAnno = if (it == 0) "Tutti" else anni[it - 1].toString() }
            anMenu(r, "Classe", listOf("Tutte") + classi, anClasse) { anClasse = (listOf("Tutte") + classi)[it] }
        }
        anContoFuturi(cont)
        val anno = anAnno.toIntOrNull()
        val filtraC = anClasse in classi
        val movs = anMovs().filter { v ->
            (anno == null || v.data.year == anno) && (anFuturi || !v.data.isAfter(oggi))
        }

        class Per(var entrate: Double = 0.0, val classi: HashMap<String, Double> = HashMap(), val na: HashMap<String, Double> = HashMap(), var n: Int = 0)
        fun calcola(chiave: (Voce) -> Int): Map<Int, Per> {
            val netti = HashMap<Int, HashMap<String, Double>>()
            val conta = HashMap<Int, Int>()
            for (v in movs) {
                val k = chiave(v)
                val imp = if (anEntrata(v)) -v.importo else v.importo
                val m = netti.getOrPut(k) { HashMap() }
                m[v.categoria] = (m[v.categoria] ?: 0.0) + imp
                conta[k] = (conta[k] ?: 0) + 1
            }
            val out = HashMap<Int, Per>()
            for ((k, m) in netti) {
                val p = Per(n = conta[k] ?: 0)
                for ((cat, x) in m) {
                    val nn = Math.round(x * 100.0) / 100.0
                    val cl = Repo.classe(cat)
                    if (cl in classi) p.classi[cl] = (p.classi[cl] ?: 0.0) + nn
                    else if (nn < 0) p.entrate += -nn
                    else if (nn > 0) p.na[cat] = nn
                }
                out[k] = p
            }
            return out
        }
        val gruppi = calcola { if (anno != null) it.data.monthValue else it.data.year }
        val tot = calcola { 0 }[0]
        val chiavi = if (anno != null) (1..12).toList() else gruppi.keys.sorted()
        val etich = anno?.toString() ?: "tutti gli anni"

        sezione(cont, "METODO 50/30/20 · " + etich.uppercase() + (if (filtraC) " · " + anClasse.uppercase() else ""))
        if (tot == null || tot.entrate <= 0) {
            anTesto(cont, "Nessuna entrata nel periodo: la percentuale si calcola sulle entrate nette.", colore = medio)
        } else {
            anTesto(cont, "Entrate nette " + euro(tot.entrate), grassetto = true)
            for (c in classi) {
                if (filtraC && c != anClasse) continue
                val v = tot.classi[c] ?: 0.0
                val obiettivo = tot.entrate * quote[c]!! / 100
                val ok = if (c == "Risparmio") v >= obiettivo else v <= obiettivo
                anBarra(
                    cont, c + " (" + (if (c == "Risparmio") "≥ " else "≤ ") + quote[c]!!.toInt() + "%)", v, maxOf(v, obiettivo),
                    if (ok) verde else rosso, euro(v) + " · " + anPct(v / tot.entrate * 100) + (if (ok) " ✔" else " ✖")
                )
            }
        }
        var inRegola = 0
        var conEntrate = 0
        for (k in chiavi) {
            val r = gruppi[k]
            val nome = if (anno != null) MESI_IT[k - 1] else k.toString()
            val futuro = anno != null && LocalDate.of(anno, k, 1).isAfter(oggi)
            if (r == null || r.entrate <= 0) {
                riga(cont, nome + (if (futuro) " (futuro)" else ""), if (r == null) "Nessun movimento" else "Nessuna entrata nel periodo", "", medio, null)
                continue
            }
            val rel = classi.filter { !filtraC || it == anClasse }
            val esiti = rel.associateWith { c ->
                val p = (r.classi[c] ?: 0.0) / r.entrate * 100
                if (c == "Risparmio") p >= quote[c]!! else p <= quote[c]!!
            }
            val fuori = esiti.count { !it.value }
            conEntrate++
            if (esiti.values.all { it }) inRegola++
            val righe = rel.joinToString(" · ") { c ->
                val v = r.classi[c] ?: 0.0
                c.take(3) + " " + euro(v) + " (" + anPct(v / r.entrate * 100) + ")" + (if (esiti[c] == true) "✔" else "✖")
            }
            riga(
                cont, nome + (if (futuro) " (futuro)" else ""),
                "Entrate " + euro(r.entrate) + "\n" + righe + (if (r.na.isNotEmpty()) "\nSenza classe " + euro(r.na.values.sum()) else ""),
                if (fuori == 0) "✔ in regola" else "$fuori fuori",
                if (fuori == 0) verde else if (fuori == 1 && rel.size > 1) arancio else rosso, null
            )
        }
        if (conEntrate > 0) anTesto(cont, "Periodi in regola: $inRegola su $conEntrate", grassetto = true, colore = if (inRegola == conEntrate) verde else arancio)
        if (tot != null && tot.na.isNotEmpty()) {
            val top3 = tot.na.entries.sortedByDescending { it.value }.take(3)
            anTesto(
                cont,
                "Uscite senza classe: " + euro(tot.na.values.sum()) + " (" + top3.joinToString(", ") { it.key + " " + euro(it.value) } +
                    ") · assegna la classe dalla scheda Categorie.",
                colore = rosso, size = 12f
            )
        }
        anTesto(cont, "Obiettivi: Bisogni ≤ 50% · Desideri ≤ 30% · Risparmio ≥ 20% delle entrate nette (uscite − entrate per categoria).", colore = medio, size = 12f)
    }

    private val CAT_ORDINI = listOf("Nome", "Spesa del mese", "N. operazioni")
    private val CAT_MOSTRA = listOf("Tutte", "Solo senza operazioni")

    private fun renderCategorie() {
        intestaLista("🏷️ Categorie", "", "⚙️  Strumenti")
        giallo(vista<MaterialButton>(R.id.btnAddGen2))
        val cont = vista<LinearLayout>(R.id.listaGen)
        val ym = YearMonth.now()
        val voci = Repo.voci()
        val totali = voci.filter { YearMonth.from(it.data) == ym }.groupBy { it.categoria }
            .mapValues { e -> e.value.sumOf { it.importo } }
        val conteggi = voci.groupingBy { it.categoria }.eachCount()
        val conti = Repo.conti().associate { it.id to it.nome }
        val tutte = Repo.categorie()
        val senzaOp = tutte.count { (conteggi[it] ?: 0) == 0 }
        vuoto(cont, tutte.size.toString() + " categorie · " + senzaOp + " senza operazioni")
        if (catCerca.isNotBlank() || catSoloVuote || catOrdine != "Nome") {
            vuoto(
                cont,
                "Filtro: " + (if (catCerca.isNotBlank()) "“" + catCerca.trim() + "” · " else "") +
                    (if (catSoloVuote) "solo senza operazioni · " else "") + "ordine: " + catOrdine.lowercase()
            )
        }
        val filtrate = tutte.filter { c ->
            (catCerca.isBlank() || c.contains(catCerca.trim(), ignoreCase = true)) &&
                (!catSoloVuote || (conteggi[c] ?: 0) == 0)
        }
        val ordine: Comparator<String> = when (catOrdine) {
            "Spesa del mese" -> compareByDescending<String> { totali[it] ?: 0.0 }.thenBy { it.lowercase() }
            "N. operazioni" -> compareByDescending<String> { conteggi[it] ?: 0 }.thenBy { it.lowercase() }
            else -> compareBy<String> { it.lowercase() }
        }

        for ((titolo, tipo) in listOf("USCITE" to "Uscita", "ENTRATE" to "Entrata")) {
            val gruppo = filtrate.filter { Repo.tipoCategoria(it) == tipo }.sortedWith(ordine)
            val aperto = if (tipo == "Uscita") catUsciteAperte else catEntrateAperte
            sezioneForte(
                cont, titolo,
                gruppo.size.toString() + (if (gruppo.size == 1) " categoria" else " categorie") +
                    (if (aperto) " · tocca per chiudere" else " · tocca per aprire")
            ) {
                if (tipo == "Uscita") catUsciteAperte = !catUsciteAperte else catEntrateAperte = !catEntrateAperte
                renderCategorie()
            }
            if (!aperto) continue
            if (gruppo.isEmpty()) vuoto(cont, "Nessuna categoria.")
            for (cat in gruppo) {
                val bm = Repo.budgetMensile(cat)
                val ba = Repo.budgetAnnuo(cat)
                val classe = Repo.classe(cat)
                val nOp = conteggi[cat] ?: 0
                val sotto = listOfNotNull(
                    classe.takeIf { it.isNotBlank() } ?: "senza classe",
                    if (classe == "Risparmio") conti[Repo.contoRisparmio(cat)]?.let { "→ $it" } else null,
                    if (bm > 0) "Budget " + euro(bm) + "/mese" else null,
                    if (ba > 0) euro(ba) + "/anno" else null,
                    if (bm <= 0 && ba <= 0) "senza budget" else null,
                    if (nOp == 0) "nessuna operazione" else null,
                ).joinToString(" · ")
                riga(cont, cat, sotto, "", null) { dialogCategoria(cat) }
            }
        }
    }

    private fun menuStrumentiCategorie() {
        OcBuilder(this)
            .setTitle("Strumenti categorie")
            .setItems(
                arrayOf(
                    "➕  Nuova categoria", "📚  Aggiungi categorie suggerite", "🔎  Cerca, ordina e filtra",
                    "🔀  Sposta o unisci categorie", "🗑️  Elimina categorie vuote", "🔍  Apri Analisi categorie"
                )
            ) { _, k ->
                when (k) {
                    0 -> dialogCategoria(null)
                    1 -> dialogCategorieSuggerite()
                    2 -> dialogCercaCategorie()
                    3 -> dialogSpostaCategorie()
                    4 -> dialogEliminaVuote()
                    else -> mostra(Pagina.ANALISI)
                }
            }
            .setNegativeButton("Chiudi", null)
            .show()
    }

    private val categorieSuggerite = listOf(
        "Abbigliamento",
        "Abbonamenti digitali",
        "Affitto Immobile",
        "Alimentari & Consumi",
        "Animali Domestici",
        "Arredamento",
        "Asporto / Fast food",
        "Assicurazione Immobile",
        "Assicurazione Veicoli",
        "Bollette & Abbonamenti",
        "Bollo Auto",
        "Caldaia",
        "Carburante",
        "Casa",
        "Cinema / Eventi",
        "Colazioni / Caffè fuori",
        "Commercialista",
        "Computer",
        "Conto Corrente",
        "Corsi / Formazione",
        "Dentista",
        "Emergenze",
        "Entrate Extra",
        "Farmaci",
        "Finanza & Risparmio",
        "Istruzione & Lavoro",
        "Libri / Materiali",
        "Manutenzione Auto",
        "Manutenzione casa",
        "Mutuo Immobile",
        "Palestra / Fitness",
        "Parrucchiere / Estetica",
        "Pellet",
        "Pensione",
        "Pranzi / Ristoranti",
        "Pulizie Domestiche",
        "Rate / Finanziamenti",
        "Regali",
        "Riparazioni Impreviste",
        "Salute & Benessere",
        "Servizi Cloud / Backup",
        "Software",
        "Spesa Discount",
        "Spesa supermercato",
        "Spese non Ricorrenti",
        "Stipendio",
        "Streaming (Netflix, Prime...)",
        "Tabacchi",
        "Tassa Rifiuti",
        "Taxi / Car sharing",
        "Telefonia / Cellulari",
        "Telefonia / Internet",
        "Tempo libero & Spese personali",
        "Trasporti pubblici",
        "Uscite Straordinarie",
        "Utenze (Acqua)",
        "Utenze (Gas)",
        "Utenze (Luce)",
        "Utenze professionali / Partita IVA",
        "Veicoli & Trasporti",
        "Videogiochi",
        "Visite Mediche",
        "Wellness / Spa"
    )

    private fun dialogCategorieSuggerite() {
        val presenti = Repo.categorie().map { it.lowercase() }.toSet()
        val mancanti = categorieSuggerite.filter { it.lowercase() !in presenti }
        if (mancanti.isEmpty()) {
            aviso("Hai già tutte le categorie suggerite")
            return
        }
        val tipoDi = { n: String -> if (listOf("Stipendio", "Pensione", "Extra").any { n.contains(it) }) "Entrata" else "Uscita" }
        val etichette = mancanti.map { it + "  (" + tipoDi(it) + ")" }.toTypedArray()
        val sel = BooleanArray(mancanti.size)
        val dlg = OcBuilder(this)
            .setTitle("Categorie suggerite")
            .setMultiChoiceItems(etichette, sel) { _, i, on -> sel[i] = on }
            .setNegativeButton("Annulla", null)
            .setNeutralButton("Tutte", null)
            .setPositiveButton("Aggiungi") { _, _ ->
                val scelte = mancanti.filterIndexed { i, _ -> sel[i] }
                if (scelte.isEmpty()) {
                    aviso("Seleziona almeno una categoria")
                } else {
                    val n = scelte.count { Repo.aggiungiCategoria(it, tipoDi(it)) }
                    refresh()
                    aviso("Aggiunte $n categorie")
                }
            }
            .create()
        dlg.show()
        dlg.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
            val tutte = sel.any { !it }
            for (i in sel.indices) {
                sel[i] = tutte
                dlg.listView.setItemChecked(i, tutte)
            }
        }
    }

    private fun dialogCercaCategorie() {
        val (view, corpo) = corpoDialogo()
        val etCerca = campoTesto(corpo, "Cerca nel nome", catCerca, InputType.TYPE_CLASS_TEXT, 40)
        var ordine = catOrdine
        var mostra = if (catSoloVuote) CAT_MOSTRA[1] else CAT_MOSTRA[0]
        campoMenu(corpo, "Ordina per", CAT_ORDINI, ordine) { ordine = CAT_ORDINI[it] }
        campoMenu(corpo, "Mostra", CAT_MOSTRA, mostra) { mostra = CAT_MOSTRA[it] }
        OcBuilder(this)
            .setTitle("Cerca e ordina")
            .setView(view)
            .setPositiveButton("Applica") { _, _ ->
                catCerca = etCerca.text.toString()
                catOrdine = ordine
                catSoloVuote = mostra == CAT_MOSTRA[1]
                refresh()
            }
            .setNegativeButton("Azzera") { _, _ ->
                catCerca = ""
                catOrdine = "Nome"
                catSoloVuote = false
                refresh()
            }
            .show()
    }

    private fun dialogSpostaCategorie() {
        val conteggi = Repo.operazioniPerCategoria()
        val cats = Repo.categorie().sortedBy { it.lowercase() }
        if (cats.size < 2) {
            aviso("Servono almeno due categorie")
            return
        }
        val etichette = cats.map { it + " (" + (conteggi[it] ?: 0) + ")" }.toTypedArray()
        val sel = BooleanArray(cats.size)
        OcBuilder(this)
            .setTitle("Sposta da quali categorie?")
            .setMultiChoiceItems(etichette, sel) { _, i, on -> sel[i] = on }
            .setNegativeButton("Annulla", null)
            .setPositiveButton("Avanti") { _, _ ->
                val scelte = cats.filterIndexed { i, _ -> sel[i] }
                if (scelte.isEmpty()) aviso("Nessuna categoria scelta") else dialogDestinazioneSposta(scelte)
            }
            .show()
    }

    private fun dialogDestinazioneSposta(origini: List<String>) {
        val conteggi = Repo.operazioniPerCategoria()
        val n = origini.sumOf { conteggi[it] ?: 0 }
        val dest = Repo.categorie().filter { it !in origini }.sortedBy { it.lowercase() }
        if (dest.isEmpty()) {
            aviso("Nessuna categoria di destinazione disponibile")
            return
        }
        val (view, corpo) = corpoDialogo()
        var scelta = dest[0]
        campoMenu(corpo, "Sposta in", dest, scelta) { scelta = dest[it] }
        val chk = MaterialCheckBox(this)
        chk.text = "Elimina le categorie di origine (tranne Generica)"
        chk.isChecked = true
        corpo.addView(chk)
        OcBuilder(this)
            .setTitle("Sposta $n operazioni")
            .setMessage(
                origini.joinToString(", ") +
                    "\n\nLe operazioni prendono il tipo (Entrata/Uscita) della categoria di destinazione. " +
                    "Budget e metodo 50/30/20 delle categorie eliminate si perdono."
            )
            .setView(view)
            .setNegativeButton("Annulla", null)
            .setPositiveButton("Sposta") { _, _ ->
                val (m, err) = Repo.spostaCategorie(origini, scelta, chk.isChecked)
                if (err != null) aviso(err) else {
                    refresh()
                    aviso("Spostate $m operazioni")
                }
            }
            .show()
    }

    private fun dialogEliminaVuote() {
        val vuote = Repo.categorieVuote().sortedBy { it.lowercase() }
        if (vuote.isEmpty()) {
            aviso("Nessuna categoria vuota: sposta prima le operazioni")
            return
        }
        val sel = BooleanArray(vuote.size)
        OcBuilder(this)
            .setTitle("Elimina categorie vuote")
            .setMultiChoiceItems(vuote.toTypedArray(), sel) { _, i, on -> sel[i] = on }
            .setNegativeButton("Annulla", null)
            .setPositiveButton("Elimina") { _, _ ->
                val scelte = vuote.filterIndexed { i, _ -> sel[i] }
                if (scelte.isEmpty()) {
                    aviso("Nessuna categoria scelta")
                } else {
                    OcBuilder(this)
                        .setTitle("Eliminare " + scelte.size + " categorie?")
                        .setMessage(scelte.joinToString(", "))
                        .setNegativeButton("Annulla", null)
                        .setPositiveButton("Elimina") { _, _ ->
                            val n = Repo.eliminaCategorieVuote(scelte)
                            refresh()
                            aviso("Eliminate $n categorie")
                        }
                        .show()
                }
            }
            .show()
    }

    private fun dialogCategoria(cat: String?) {
        val (view, corpo) = corpoDialogo()
        val etNome = campoTesto(corpo, "Nome", cat ?: "", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES, 40)
        var tipo = if (cat != null) Repo.tipoCategoria(cat) else "Uscita"
        val tipi = listOf("Uscita", "Entrata")
        campoMenu(corpo, "Tipo", tipi, tipo) { tipo = tipi[it] }
        fun testoNum(x: Double) = if (x > 0) String.format(Locale.ITALY, "%.2f", x) else ""
        val etMese = campoTesto(corpo, "Budget mensile (€, facoltativo)", testoNum(cat?.let { Repo.budgetMensile(it) } ?: 0.0), TIPO_DECIMALE, 12)
        val etAnno = campoTesto(corpo, "Budget annuale (€, facoltativo)", testoNum(cat?.let { Repo.budgetAnnuo(it) } ?: 0.0), TIPO_DECIMALE, 12)

        val classi = listOf("Nessuna") + Repo.CLASSI
        var classe = cat?.let { Repo.classe(it) }.orEmpty().ifBlank { "Nessuna" }
        val conti = Repo.conti()
        val nomiConto = listOf("(nessuno)") + conti.map { it.nome }
        var contoSel = conti.firstOrNull { it.id == cat?.let { c -> Repo.contoRisparmio(c) } }?.nome ?: "(nessuno)"
        lateinit var tilConto: TextInputLayout
        fun aggiornaConto() {
            tilConto.visibility =
                if (classe == "Risparmio" && conti.isNotEmpty()) View.VISIBLE else View.GONE
        }
        campoMenu(corpo, "Metodo 50/30/20", classi, classe) {
            classe = classi[it]
            aggiornaConto()
        }
        tilConto = campoMenu(corpo, "Conto di risparmio", nomiConto, contoSel) { contoSel = nomiConto[it] }
        aggiornaConto()

        val b = OcBuilder(this)
            .setTitle(if (cat == null) "Nuova categoria" else "Modifica categoria")
            .setView(view)
            .setPositiveButton("Salva", null)
            .setNegativeButton("Annulla", null)
        if (cat != null && cat != "Generica") b.setNeutralButton("Elimina", null)
        val dlg = b.create()
        dlg.show()
        dlg.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val m = importoOk(etMese.text.toString(), ammettiZero = true)
            val a = importoOk(etAnno.text.toString(), ammettiZero = true)
            if (m == null) {
                etMese.error = "Importo non valido"
                return@setOnClickListener
            }
            if (a == null) {
                etAnno.error = "Importo non valido"
                return@setOnClickListener
            }
            val contoId = conti.firstOrNull { it.nome == contoSel }?.id ?: ""
            val cl = if (classe == "Nessuna") "" else classe
            val nome = etNome.text.toString()
            val err = if (cat == null) Repo.creaCategoria(nome, tipo, m, a, cl, contoId)
            else Repo.modificaCategoria(cat, nome, tipo, m, a, cl, contoId)
            if (err != null) {
                etNome.error = err
                return@setOnClickListener
            }
            dlg.dismiss()
            refresh()
            aviso("Categoria salvata")
        }
        if (cat != null && cat != "Generica") {
            dlg.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                OcBuilder(this)
                    .setTitle("Eliminare la categoria?")
                    .setMessage(cat)
                    .setNegativeButton("Annulla", null)
                    .setPositiveButton("Elimina") { _, _ ->
                        if (Repo.eliminaCategoria(cat)) {
                            dlg.dismiss()
                            refresh()
                        } else {
                            aviso("Ci sono operazioni in questa categoria: spostale prima in un'altra")
                        }
                    }
                    .show()
            }
        }
    }

    private fun renderTarget() {
        intestaLista("🎯 Target", "✏️  Imposta target di spesa")
        val cont = vista<LinearLayout>(R.id.listaGen)
        val oggi = LocalDate.now()
        val ym = YearMonth.now()
        val inizioMese = ym.atDay(1)
        val fineMese = ym.atEndOfMonth()
        val inizioAnno = LocalDate.of(oggi.year, 1, 1)
        val fineAnno = LocalDate.of(oggi.year, 12, 31)
        val usciteMese = Repo.uscite(inizioMese, fineMese)
        val usciteAnno = Repo.uscite(inizioAnno, fineAnno)

        sezioneForte(cont, "TARGET DI SPESA")
        if (Repo.targetMese <= 0 && Repo.targetAnno <= 0) {
            vuoto(cont, "Nessun target impostato. Tocca il pulsante in basso per definirlo.")
        }
        if (Repo.targetMese > 0) {
            barra(cont, "Questo mese · " + maiuscola(ym.format(FMT_MESE)), usciteMese.sumOf { it.importo }, Repo.targetMese, "")
        }
        if (Repo.targetAnno > 0) {
            barra(cont, "Quest'anno · " + oggi.year, usciteAnno.sumOf { it.importo }, Repo.targetAnno, "")
        }

        val spesoM = usciteMese.groupBy { it.categoria }.mapValues { e -> e.value.sumOf { it.importo } }
        val conBudgetM = Repo.categorie().filter { Repo.budgetMensile(it) > 0 }
            .sortedByDescending { (spesoM[it] ?: 0.0) / Repo.budgetMensile(it) }
        if (conBudgetM.isNotEmpty()) {
            sezioneForte(cont, "BUDGET MENSILI PER CATEGORIA")
            val sfM = conBudgetM.count { (spesoM[it] ?: 0.0) > Repo.budgetMensile(it) }
            if (sfM > 0) vuoto(cont, "⚠️ " + sfM + (if (sfM == 1) " categoria sopra budget" else " categorie sopra budget") + " questo mese")
            for (c in conBudgetM) {
                barra(cont, c, usciteMese.filter { it.categoria == c }.sumOf { it.importo }, Repo.budgetMensile(c), "")
            }
        }
        val spesoA = usciteAnno.groupBy { it.categoria }.mapValues { e -> e.value.sumOf { it.importo } }
        val conBudgetA = Repo.categorie().filter { Repo.budgetAnnuo(it) > 0 }
            .sortedByDescending { (spesoA[it] ?: 0.0) / Repo.budgetAnnuo(it) }
        if (conBudgetA.isNotEmpty()) {
            sezioneForte(cont, "BUDGET ANNUALI PER CATEGORIA")
            val sfA = conBudgetA.count { (spesoA[it] ?: 0.0) > Repo.budgetAnnuo(it) }
            if (sfA > 0) vuoto(cont, "⚠️ " + sfA + (if (sfA == 1) " categoria sopra budget" else " categorie sopra budget") + " quest'anno")
            for (c in conBudgetA) {
                barra(cont, c, usciteAnno.filter { it.categoria == c }.sumOf { it.importo }, Repo.budgetAnnuo(c), "")
            }
        }

        val q = Repo.calcola503020(Repo.voci().filter { YearMonth.from(it.data) == ym })
        if (q.entrate > 0 && (q.bisogni + q.desideri + q.risparmio) > 0) {
            sezioneForte(cont, "METODO 50/30/20 · entrate " + euro(q.entrate))
            barra(cont, "Bisogni (obiettivo 50%)", q.bisogni, q.entrate * 0.5, "")
            barra(cont, "Desideri (obiettivo 30%)", q.desideri, q.entrate * 0.3, "")
            val r = LinearLayout(this)
            r.orientation = LinearLayout.VERTICAL
            cont.addView(r)
            riga(
                r, "Risparmio (obiettivo 20%)",
                "Messo da parte " + String.format(Locale.ITALY, "%.0f", q.risparmio / q.entrate * 100) + "% delle entrate",
                euro(q.risparmio), ContextCompat.getColor(this, R.color.oc_green), null
            )
            if (q.nonAssegnate > 0) {
                vuoto(cont, "Spese senza classe 50/30/20: " + euro(q.nonAssegnate) + " (assegnale dalle Categorie).")
            }
        }
    }

    private fun dialogTarget() {
        val (view, corpo) = corpoDialogo()
        fun testoNum(x: Double) = if (x > 0) String.format(Locale.ITALY, "%.2f", x) else ""
        val etM = campoTesto(corpo, "Target mensile di spesa (€)", testoNum(Repo.targetMese), TIPO_DECIMALE, 12)
        val etA = campoTesto(corpo, "Target annuale di spesa (€)", testoNum(Repo.targetAnno), TIPO_DECIMALE, 12)
        val dlg = OcBuilder(this)
            .setTitle("Target di spesa")
            .setMessage("Lascia vuoto per non avere un target.")
            .setView(view)
            .setPositiveButton("Salva", null)
            .setNegativeButton("Annulla", null)
            .create()
        dlg.show()
        dlg.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val m = importoOk(etM.text.toString(), ammettiZero = true)
            val a = importoOk(etA.text.toString(), ammettiZero = true)
            if (m == null) {
                etM.error = "Importo non valido"
                return@setOnClickListener
            }
            if (a == null) {
                etA.error = "Importo non valido"
                return@setOnClickListener
            }
            Repo.setTarget(m, a)
            dlg.dismiss()
            refresh()
            aviso("Target salvato")
        }
    }

    private fun renderRicorrenze() {
        intestaLista("🔁 Ricorrenze", "➕  Nuova ricorrenza")
        val cont = vista<LinearLayout>(R.id.listaGen)
        val lista = Repo.ricorrenze()
        val sug = RicAuto.trova(Repo.voci(), lista, LocalDate.now())
        if (sug.isNotEmpty()) {
            riga(
                cont, "💡 " + sug.size + (if (sug.size == 1) " possibile ricorrenza" else " possibili ricorrenze"),
                "Trovate nello storico, entrate e uscite. Tocca per vedere", "", null
            ) { dialogSuggerimenti(sug) }
        }
        if (lista.isEmpty()) {
            vuoto(cont, "Nessuna ricorrenza. Ogni ricorrenza genera subito tutte le sue operazioni.")
            return
        }
        val verde = ContextCompat.getColor(this, R.color.oc_green)
        val rosso = ContextCompat.getColor(this, R.color.oc_red)
        for (r in lista) {
            val desc = descPulita(r.desc)
            val sotto = listOfNotNull(
                r.tipo,
                r.inizio?.let { "dal " + it.format(FMT_DATA) },
                r.fine?.let { "al " + it.format(FMT_DATA) },
                r.fatte.toString() + "/" + r.n + " fatte",
            ).joinToString(" · ")
            val entrata = r.tipoVoce == "Entrata"
            riga(
                cont, r.cat + (if (desc.isEmpty()) "" else " · $desc"), sotto,
                (if (entrata) "+" else "−") + euro(r.imp), if (entrata) verde else rosso
            ) { dettaglioRicorrenza(r) }
        }
    }

    private fun dettaglioRicorrenza(r: Ric) {
        OcBuilder(this)
            .setTitle(r.cat + " · " + euro(r.imp))
            .setMessage(
                r.tipo + ", " + r.n + " ripetizioni (" + r.fatte + " già passate).\n" +
                    "Eliminandola si tolgono tutte le sue operazioni, anche quelle già registrate."
            )
            .setNegativeButton("Chiudi", null)
            .setPositiveButton("Elimina") { _, _ ->
                OcBuilder(this)
                    .setTitle("Eliminare la ricorrenza?")
                    .setMessage("Verranno eliminate tutte le operazioni collegate.")
                    .setNegativeButton("Annulla", null)
                    .setPositiveButton("Elimina") { _, _ ->
                        val n = Repo.eliminaRicorrenza(r.id)
                        refresh()
                        aviso("Eliminata: $n operazioni tolte")
                    }
                    .show()
            }
            .show()
    }

    private fun dialogSuggerimenti(sug: List<RicAuto.Sugg>) {
        val dp = resources.displayMetrics.density
        val verde = ContextCompat.getColor(this, R.color.oc_green)
        val rosso = ContextCompat.getColor(this, R.color.oc_red)
        val grigio = ContextCompat.getColor(this, R.color.oc_text_mid)
        lateinit var dlg: androidx.appcompat.app.AlertDialog
        val root = LinearLayout(ctxDialogo)
        root.orientation = LinearLayout.VERTICAL
        val scroll = android.widget.ScrollView(ctxDialogo)
        val lista = LinearLayout(ctxDialogo)
        lista.orientation = LinearLayout.VERTICAL
        lista.setPadding((16 * dp).toInt(), (8 * dp).toInt(), (16 * dp).toInt(), (8 * dp).toInt())
        scroll.addView(lista)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        for (sg in sug) {
            val riga = LinearLayout(ctxDialogo)
            riga.orientation = LinearLayout.VERTICAL
            riga.setBackgroundResource(R.drawable.bg_tile)
            riga.setPadding((14 * dp).toInt(), (12 * dp).toInt(), (14 * dp).toInt(), (12 * dp).toInt())
            val testa = LinearLayout(ctxDialogo)
            testa.orientation = LinearLayout.HORIZONTAL
            val tit = TextView(ctxDialogo)
            tit.text = sg.desc.ifBlank { sg.cat }
            tit.textSize = 17f
            tit.setTypeface(null, Typeface.BOLD)
            tit.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            val imp = TextView(ctxDialogo)
            val entrata = sg.tipo == "Entrata"
            imp.text = (if (entrata) "+" else "−") + euro(sg.imp)
            imp.textSize = 17f
            imp.setTypeface(null, Typeface.BOLD)
            imp.setTextColor(if (entrata) verde else rosso)
            testa.addView(tit)
            testa.addView(imp)
            val sub = TextView(ctxDialogo)
            sub.text = sg.cat + " · giorno " + sg.giorno + " · " + sg.mesi + " mesi di fila · prossima " + sg.prossima.format(FMT_DATA)
            sub.textSize = 13f
            sub.setTextColor(grigio)
            sub.setPadding(0, (4 * dp).toInt(), 0, 0)
            riga.addView(testa)
            riga.addView(sub)
            riga.setOnClickListener {
                dlg.dismiss()
                dialogRicorrenza(sg)
            }
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.topMargin = (6 * dp).toInt()
            lp.bottomMargin = (6 * dp).toInt()
            lista.addView(riga, lp)
        }
        val piede = LinearLayout(ctxDialogo)
        piede.orientation = LinearLayout.HORIZONTAL
        piede.setPadding((16 * dp).toInt(), (8 * dp).toInt(), (16 * dp).toInt(), (12 * dp).toInt())
        aggiungiPiede(piede, "Chiudi" to { dlg.dismiss() })
        root.addView(piede)
        dlg = OcBuilder(this).setTitle("💡 Possibili ricorrenze").setView(root).show()
    }

    private fun dialogRicorrenza(sug: RicAuto.Sugg? = null) {
        val periodi = listOf("Ogni giorno", "Ogni mese", "Ogni anno")
        var periodo = "Ogni mese"
        var tipo = sug?.tipo ?: "Uscita"
        var data = sug?.prossima ?: LocalDate.now()
        var metodo = sug?.metodo ?: ""
        var conto = if (sug != null && sug.conto.isNotBlank()) sug.conto else (Repo.contoPrincipale()?.nome ?: "")

        fun categorieTipo(): List<String> {
            val base = Repo.categorie().filter { Repo.tipoCategoria(it) == tipo }
            return base.ifEmpty { Repo.categorie() }
        }
        var cat = sug?.cat ?: categorieTipo().firstOrNull() ?: "Generica"

        val (view, corpo) = corpoDialogo()
        val grp = MaterialButtonToggleGroup(this)
        grp.isSingleSelection = true
        grp.isSelectionRequired = true
        val bU = MaterialButton(ctxDialogo, null, com.google.android.material.R.attr.materialButtonOutlinedStyle)
        bU.id = View.generateViewId(); bU.text = "Uscita"
        val bE = MaterialButton(ctxDialogo, null, com.google.android.material.R.attr.materialButtonOutlinedStyle)
        bE.id = View.generateViewId(); bE.text = "Entrata"
        for ((bt, col, fondo) in listOf(
            Triple(bU, R.color.oc_red, R.color.oc_tg_fondo_uscita),
            Triple(bE, R.color.oc_green, R.color.oc_tg_fondo_entrata),
        )) {
            bt.setTextColor(ContextCompat.getColor(this, col))
            bt.setTypeface(null, Typeface.BOLD)
            bt.strokeColor = ColorStateList.valueOf(ContextCompat.getColor(this, col))
            bt.strokeWidth = (2 * resources.displayMetrics.density).toInt()
            bt.backgroundTintList = ContextCompat.getColorStateList(this, fondo)
        }
        grp.addView(bU, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        grp.addView(bE, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        corpo.addView(grp)

        val etImp = campoTesto(
            corpo, "Importo (€)", if (sug != null) String.format(Locale.ITALY, "%.2f", sug.imp) else "", TIPO_DECIMALE, 12
        )
        Calc.collega(this, etImp)
        val tvSmart = TextView(this)
        tvSmart.textSize = 12f
        tvSmart.setTextColor(com.google.android.material.color.MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant, 0))
        tvSmart.setPadding(0, 8, 0, 0)
        tvSmart.visibility = View.GONE
        corpo.addView(tvSmart)
        val tilCat = campoMenu(corpo, "Categoria", categorieTipo(), cat) { cat = categorieTipo()[it] }
        val acCat = tilCat.findViewById<MaterialAutoCompleteTextView>(R.id.campoMenu)
        grp.addOnButtonCheckedListener { _, id, checked ->
            if (checked) {
                tipo = if (id == bE.id) "Entrata" else "Uscita"
                val cats = categorieTipo()
                acCat.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, cats))
                cat = cats.firstOrNull() ?: "Generica"
                acCat.setText(cat, false)
            }
        }
        grp.check(if (tipo == "Entrata") bE.id else bU.id)
        if (sug != null) {
            cat = sug.cat
            acCat.setText(cat, false)
        }

        if (Repo.smartCatOn) {
            val idx = Repo.smartCatIndice()
            tvSmart.visibility = View.VISIBLE
            tvSmart.text = "🛠️ SmartCat in attesa…"
            etImp.doAfterTextChanged { t ->
                val imp = num(t?.toString() ?: "")
                if (t.isNullOrBlank()) {
                    tvSmart.text = "🛠️ SmartCat in attesa…"
                    return@doAfterTextChanged
                }
                if (imp == null) return@doAfterTextChanged
                val sug = Repo.smartCat(idx, imp) ?: return@doAfterTextChanged
                if (sug.tipo != tipo) {
                    grp.check(if (sug.tipo == "Entrata") bE.id else bU.id)
                }
                cat = sug.categoria
                acCat.setText(sug.categoria, false)
                tvSmart.text = "💡 SmartCat attiva: " + sug.categoria
            }
        }

        campoMenu(corpo, "Periodo", periodi, periodo) { periodo = periodi[it] }
        val etN = campoTesto(corpo, "Numero di ripetizioni (1-365)", "12", InputType.TYPE_CLASS_NUMBER, 3)
        val btnData = MaterialButton(ctxDialogo, null, com.google.android.material.R.attr.materialButtonOutlinedStyle)
        btnData.text = "Prima data: " + data.format(FMT_DATA)
        btnData.setOnClickListener {
            DatePickerDialog(
                this,
                { _, y, m, d ->
                    data = LocalDate.of(y, m + 1, d)
                    btnData.text = "Prima data: " + data.format(FMT_DATA)
                },
                data.year, data.monthValue - 1, data.dayOfMonth
            ).show()
        }
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.topMargin = 16
        corpo.addView(btnData, lp)
        val etDescr = campoTesto(corpo, "Descrizione", sug?.desc ?: "", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)

        val nomiMetodi = listOf("") + Repo.METODI.map { it.first }
        campoMenu(
            corpo, "Metodo di pagamento", nomiMetodi.map { if (it.isBlank()) "Nessuno" else Repo.metodoConEmoji(it) },
            if (metodo.isBlank()) "Nessuno" else Repo.metodoConEmoji(metodo)
        ) {
            metodo = nomiMetodi[it]
        }
        val nomiConti = listOf("") + Repo.conti().map { it.nome }
        if (nomiConti.size > 1) {
            campoMenu(corpo, "Conto", nomiConti.map { if (it.isBlank()) "Nessuno" else it }, if (conto.isBlank()) "Nessuno" else conto) {
                conto = nomiConti[it]
            }
        } else conto = ""
        val etTag = campoTesto(corpo, "Tag (separati da spazio)", sug?.tag?.joinToString(" ") ?: "", InputType.TYPE_CLASS_TEXT)
        suggerimentiTag(etTag)

        val dlg = OcBuilder(this)
            .setTitle("Nuova ricorrenza")
            .setView(view)
            .setPositiveButton("Crea", null)
            .setNegativeButton("Annulla", null)
            .create()
        dlg.show()
        dlg.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val imp = importoOk(etImp.text.toString())
            if (imp == null) {
                etImp.error = "Importo non valido"
                return@setOnClickListener
            }
            val n = etN.text.toString().trim().toIntOrNull()
            if (n == null || n < 1 || n > 365) {
                etN.error = "Da 1 a 365"
                return@setOnClickListener
            }
            val tags = etTag.text.toString().split(Regex("[\\s,;]+"))
                .map { it.trimStart('#') }.filter { it.isNotEmpty() }
            val fatte = Repo.creaRicorrenza(
                periodo, n, data, cat, etDescr.text.toString().trim(), imp, tipo, metodo, tags, conto
            )
            dlg.dismiss()
            refresh()
            aviso("Ricorrenza creata: $fatte operazioni")
        }
    }

    private fun eur0(x: Double) = "€ " + String.format(Locale.ITALY, "%,.0f", Math.round(x).toDouble())
    private fun eur0s(x: Double) = (if (x < 0) "-" else "+") + eur0(Math.abs(x))

    private fun dialogContoGrafici() {
        val conti = Repo.conti().map { it.nome }
        val voci = (listOf("Patrimonio complessivo") + conti).toTypedArray()
        OcBuilder(this)
            .setTitle("Conto")
            .setItems(voci) { _, i ->
                grafConto = if (i == 0) "" else conti[i - 1]
                renderGrafici()
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun unaRiga(b: MaterialButton): MaterialButton {
        val dp = resources.displayMetrics.density
        b.maxLines = 1
        b.setPadding((6 * dp).toInt(), b.paddingTop, (6 * dp).toInt(), b.paddingBottom)
        b.setAutoSizeTextTypeUniformWithConfiguration(10, 14, 1, android.util.TypedValue.COMPLEX_UNIT_SP)
        return b
    }

    private fun pulsanteSel(testo: String, attivo: Boolean, click: () -> Unit): MaterialButton {
        val attr = if (attivo) com.google.android.material.R.attr.materialButtonStyle
        else com.google.android.material.R.attr.materialButtonOutlinedStyle
        val b = MaterialButton(this, null, attr)
        b.text = testo
        b.minWidth = 0
        b.isAllCaps = false
        b.setOnClickListener { click() }
        return b
    }

    private fun renderGrafici() {
        val nomeConto = if (grafConto.isEmpty()) "Patrimonio complessivo" else grafConto
        intestaLista("📊 Grafici e statistiche", "🏦  $nomeConto  ·  cambia conto")
        val cont = vista<LinearLayout>(R.id.listaGen)
        val dp = resources.displayMetrics.density
        val verde = ContextCompat.getColor(this, R.color.oc_green)
        val rosso = ContextCompat.getColor(this, R.color.oc_red)
        val blu = ContextCompat.getColor(this, R.color.oc_blue)
        val arancio = ContextCompat.getColor(this, R.color.oc_orange)
        val grigio = ContextCompat.getColor(this, R.color.oc_text_mid)

        val voci = Repo.voci().filter { grafConto.isEmpty() || it.conto == grafConto }
        val anni = Stat.anni(voci, grafAnno)
        if (grafAnno !in anni) grafAnno = LocalDate.now().year

        val riga1 = LinearLayout(this)
        riga1.orientation = LinearLayout.HORIZONTAL
        val nomiVista = listOf("📈 Andamento", if (grafSempre) "📊 Annuale" else "📊 Mensile", "🧾 Spese", "📋 Tabella")
        val bVista = ArrayList<MaterialButton>()
        nomiVista.forEachIndexed { i, t ->
            val b = pulsanteSel(t, grafVista == i) { grafVista = i; renderGrafici() }
            b.maxLines = 1
            b.setPadding((6 * dp).toInt(), b.paddingTop, (6 * dp).toInt(), b.paddingBottom)
            b.setAutoSizeTextTypeUniformWithConfiguration(10, 14, 1, android.util.TypedValue.COMPLEX_UNIT_SP)
            bVista.add(b)
            riga1.addView(b, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        cont.addView(riga1)

        val riga2 = LinearLayout(this)
        riga2.orientation = LinearLayout.HORIZONTAL
        riga2.gravity = android.view.Gravity.CENTER_VERTICAL
        val lpPer = { LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) }
        fun unaRiga(b: MaterialButton): MaterialButton {
            b.maxLines = 1
            b.setPadding((6 * dp).toInt(), b.paddingTop, (6 * dp).toInt(), b.paddingBottom)
            b.setAutoSizeTextTypeUniformWithConfiguration(10, 14, 1, android.util.TypedValue.COMPLEX_UNIT_SP)
            return b
        }
        val bAnno = unaRiga(pulsanteSel("Anno", !grafSempre) { grafSempre = false; renderGrafici() })
        val bSempre = unaRiga(pulsanteSel("Sempre", grafSempre) { grafSempre = true; renderGrafici() })
        riga2.addView(bAnno, lpPer())
        riga2.addView(bSempre, lpPer())
        val bTutti = bVista + listOf(bAnno, bSempre)
        cont.post {
            val m = bTutti.minOf { it.textSize }
            for (b in bTutti) {
                b.setAutoSizeTextTypeWithDefaults(android.widget.TextView.AUTO_SIZE_TEXT_TYPE_NONE)
                b.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, m)
            }
        }
        if (!grafSempre) {
            val i = anni.indexOf(grafAnno)
            val larg = (38 * dp).toInt()
            fun nav(t: String, ok: Boolean, vai: () -> Unit): MaterialButton {
                val b = pulsanteSel(t, false) { vai() }
                b.setPadding(0, 0, 0, 0)
                b.insetTop = 0
                b.insetBottom = 0
                b.isEnabled = ok
                return b
            }
            val prev = nav("‹", i > 0) { grafAnno = anni[i - 1]; renderGrafici() }
            val lbl = TextView(this)
            lbl.text = grafAnno.toString()
            lbl.setTypeface(null, Typeface.BOLD)
            lbl.gravity = android.view.Gravity.CENTER
            val next = nav("›", i < anni.size - 1) { grafAnno = anni[i + 1]; renderGrafici() }
            riga2.addView(prev, LinearLayout.LayoutParams(larg, (40 * dp).toInt()))
            riga2.addView(lbl, LinearLayout.LayoutParams((44 * dp).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT))
            riga2.addView(next, LinearLayout.LayoutParams(larg, (40 * dp).toInt()))
        }

        val vociPeriodo = if (grafSempre) voci else voci.filter { it.data.year == grafAnno }
        val saldoPeriodo = vociPeriodo.filter { it.tipo == "Entrata" }.sumOf { it.importo } -
            vociPeriodo.filter { it.tipo == "Uscita" }.sumOf { it.importo }
        if (voci.isNotEmpty()) riga2.addView(nuovoMeteo(saldoPeriodo >= 0, (20 * dp).toInt()))
        cont.addView(riga2)

        if (!grafSempre) {
            val chkF = MaterialCheckBox(this)
            chkF.text = "Includi movimenti futuri"
            chkF.isChecked = grafFuturi
            chkF.setOnCheckedChangeListener { _, ok -> grafFuturi = ok; renderGrafici() }
            cont.addView(chkF)
        }

        if (voci.isEmpty()) {
            vuoto(cont, "Nessuna operazione" + if (grafConto.isNotEmpty()) " su questo conto." else ".")
            return
        }

        val serie = if (grafSempre) Stat.storico(voci) else Stat.mensile(voci, grafAnno, grafFuturi)

        fun legenda(vararg voci: Pair<Int, String>) {
            val t = TextView(this)
            t.text = voci.joinToString("     ") { "■ " + it.second }
            val sp = android.text.SpannableString(t.text)
            var pos = 0
            for ((col, testo) in voci) {
                sp.setSpan(android.text.style.ForegroundColorSpan(col), pos, pos + 1, 0)
                pos += testo.length + 2 + 5
            }
            t.text = sp
            t.textSize = 12f
            t.setPadding(4, (10 * dp).toInt(), 4, (4 * dp).toInt())
            cont.addView(t)
        }

        fun apriPeriodo(i: Int) {
            rc.azzera()
            if (grafConto.isNotEmpty()) rc.conto = grafConto
            if (grafSempre) rc.anno = serie.lab[i] else { rc.anno = grafAnno.toString(); rc.mese = i + 1 }
            rcTesto = ""
            rcFuturi = grafSempre || grafFuturi
            mostra(Pagina.RICERCA)
        }

        fun grafico(modo: GraficoView.Modo, a: List<Double?>, b: List<Double?>, dettaglio: (Int) -> String) {
            val g = GraficoView(this)
            g.imposta(
                modo, serie.lab, a, b, blu, arancio, grigio,
                ContextCompat.getColor(this, R.color.oc_border), grigio,
                ContextCompat.getColor(this, R.color.oc_surface),
            )
            val det = TextView(this)
            det.textSize = 13f
            det.setPadding(4, (6 * dp).toInt(), 4, (6 * dp).toInt())
            det.text = "Tocca il grafico per vedere i dettagli."
            det.setTextColor(grigio)
            var selI = -1
            g.alSelezionare = { i ->
                val ha = serie.e.getOrNull(i) != null
                selI = if (ha) i else -1
                det.text = dettaglio(i) + if (ha) "\n👉 Tocca qui per i movimenti" else ""
                det.setTextColor(ContextCompat.getColor(this, R.color.oc_text))
            }
            det.setOnClickListener { if (selI >= 0) apriPeriodo(selI) }
            det.minLines = 4
            cont.addView(g, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (140 * dp).toInt()))
            cont.addView(det)
            fun adatta() {
                val sv = cont.parent as? View ?: return
                if (sv.height <= 0 || cont.height <= 0 || g.height <= 0 || !g.isAttachedToWindow) return
                val altri = cont.height - g.height
                val h = (sv.height - altri).coerceIn((100 * dp).toInt(), (420 * dp).toInt())
                if (abs(h - g.height) > 2) {
                    val lp = g.layoutParams
                    lp.height = h
                    g.layoutParams = lp
                }
            }
            g.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> adatta() }
            cont.post { adatta() }
        }

        fun dett(i: Int): String {
            val e = serie.e.getOrNull(i)
            val u = serie.u.getOrNull(i)
            val s = serie.s.getOrNull(i)
            if (e == null || u == null) return serie.lab[i] + ": nessun dato"
            return serie.lab[i] + (if (!grafSempre) " " + grafAnno else "") + "\nEntrate " + eur0(e) + " · Uscite " + eur0(u) +
                "\nSaldo " + eur0s(if (grafSempre || grafVista == 1) e - u else (s ?: 0.0))
        }

        when (grafVista) {
            0 -> {
                val prog = !grafSempre
                val saldo = serie.saldoFinale(prog)
                val tit = if (prog) "Saldo da inizio anno" else "Saldo complessivo"
                riga(cont, "Entrate", "", eur0(serie.totE), verde, null)
                riga(cont, "Uscite", "", eur0(serie.totU), rosso, null)
                val nota = if (grafConto.isEmpty() && serie.totE > 0) {
                    if (saldo >= 0) "Hai messo da parte il " + Math.round(saldo / serie.totE * 100) + "% di quanto hai incassato"
                    else "Hai speso più di quanto hai incassato"
                } else ""
                riga(cont, tit, nota, eur0s(saldo), if (saldo < 0) rosso else null, null)
                if (prog) {
                    legenda(blu to "Saldo progressivo da gennaio")
                    grafico(GraficoView.Modo.LINEA, serie.s, emptyList(), ::dett)
                } else {
                    legenda(blu to "Saldo positivo", arancio to "Saldo negativo")
                    grafico(GraficoView.Modo.BARRE_SEGNO, serie.s, emptyList(), ::dett)
                }
            }
            1 -> {
                legenda(blu to "Entrate", arancio to "Uscite")
                grafico(GraficoView.Modo.BARRE_DOPPIE, serie.e, serie.u, ::dett)
            }
            3 -> {
                for (i in serie.lab.indices) {
                    val e = serie.e.getOrNull(i) ?: continue
                    val u = serie.u.getOrNull(i) ?: continue
                    val etichetta = serie.lab[i] + if (grafSempre) "" else " $grafAnno"
                    riga(cont, etichetta, "Entrate " + eur0(e) + " · Uscite " + eur0(u), eur0s(e - u), if (e - u < 0) rosso else verde, null)
                }
                riga(
                    cont, "Totale", "Entrate " + eur0(serie.totE) + " · Uscite " + eur0(serie.totU),
                    eur0s(serie.totE - serie.totU), if (serie.totE - serie.totU < 0) rosso else verde, null
                )
            }
            else -> {
                val righe = Stat.categorie(voci, if (grafSempre) null else grafAnno)
                val tot = righe.sumOf { it.v }
                riga(cont, "Uscite totali", "Tocca una categoria per vedere i movimenti", eur0(tot), rosso, null)
                if (righe.isEmpty()) {
                    vuoto(cont, "Nessuna spesa in questo periodo.")
                    return
                }
                val mx = righe.maxOf { max(it.v, it.budget) }
                for (r in righe) {
                    val over = r.budget > 0 && r.v > r.budget
                    val pct = Math.round(r.v / tot * 100)
                    val sub = when {
                        r.budget <= 0 -> ""
                        over -> "⚠️ " + eur0(r.v - r.budget) + " sopra il budget di " + eur0(r.budget)
                        else -> "budget " + eur0(r.budget)
                    }
                    val box = LinearLayout(this)
                    box.orientation = LinearLayout.VERTICAL
                    box.setPadding(8, (8 * dp).toInt(), 8, (4 * dp).toInt())
                    val testa = LinearLayout(this)
                    testa.orientation = LinearLayout.HORIZONTAL
                    val nome = TextView(this)
                    nome.text = r.nome
                    val val1 = TextView(this)
                    val1.text = eur0(r.v) + "  " + (if (pct < 1) "<1" else pct.toString()) + "%"
                    val1.setTypeface(null, Typeface.BOLD)
                    testa.addView(nome, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    testa.addView(val1)
                    box.addView(testa)
                    val pb = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal)
                    pb.max = 1000
                    pb.progress = (r.v / mx * 1000).toInt()
                    pb.progressTintList = ColorStateList.valueOf(if (over) arancio else blu)
                    val plp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    plp.topMargin = (4 * dp).toInt()
                    box.addView(pb, plp)
                    if (sub.isNotEmpty()) {
                        val s = TextView(this)
                        s.text = sub
                        s.textSize = 12f
                        s.setTextColor(if (over) rosso else grigio)
                        box.addView(s)
                    }
                    box.setOnClickListener {
                        rc.azzera()
                        rc.tipo = "Uscita"
                        rc.categoria = r.nome
                        if (grafConto.isNotEmpty()) rc.conto = grafConto
                        if (!grafSempre) rc.anno = grafAnno.toString()
                        rcTesto = ""
                        rcFuturi = true
                        mostra(Pagina.RICERCA)
                    }
                    cont.addView(box)
                }
            }
        }
    }

    private var esAnno = ""
    private var esMese = 0
    private var esFuturi = false
    private var esVista = 0

    private fun renderEstratti() {
        intestaLista("💳 Estratti per metodo e conti", "📥  Importa", "📤  Condividi")
        val cont = vista<LinearLayout>(R.id.listaGen)
        val verde = ContextCompat.getColor(this, R.color.oc_green)
        val rosso = ContextCompat.getColor(this, R.color.oc_red)
        val medio = ContextCompat.getColor(this, R.color.oc_text_mid)
        val tutte = Repo.voci()
        if (tutte.isEmpty()) {
            vuoto(cont, "Nessuna operazione registrata.")
            return
        }
        val oggi = LocalDate.now()
        val tutti = "Tutti"
        val anni = listOf(tutti) + tutte.map { it.data.year }.distinct().sortedDescending().map { it.toString() }
        val mesiL = listOf(tutti) + Ricerca.MESI
        if (esAnno.isNotEmpty() && esAnno !in anni) esAnno = ""

        val rv = LinearLayout(this)
        rv.orientation = LinearLayout.HORIZONTAL
        listOf("💳 Per metodo", "🏦 Per conto").forEachIndexed { i, t ->
            rv.addView(
                unaRiga(pulsanteSel(t, esVista == i) { esVista = i; renderEstratti() }),
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            )
        }
        cont.addView(rv)

        anRiga(cont) { r ->
            campoMenu(r, "Anno", anni, if (esAnno.isEmpty()) tutti else esAnno) {
                esAnno = if (it == 0) "" else anni[it]
                renderEstratti()
            }
            campoMenu(r, "Mese", mesiL, mesiL[esMese]) {
                esMese = it
                renderEstratti()
            }
        }
        val chk = MaterialCheckBox(this)
        chk.text = "Includi movimenti futuri"
        chk.isChecked = esFuturi
        chk.setOnCheckedChangeListener { _, on -> esFuturi = on; renderEstratti() }
        cont.addView(chk)

        val annoN = esAnno.toIntOrNull()
        val voci = tutte.filter {
            (esFuturi || !it.data.isAfter(oggi)) &&
                (annoN == null || it.data.year == annoN) &&
                (esMese == 0 || it.data.monthValue == esMese)
        }
        val periodo = (if (esMese == 0) "Tutti i mesi" else Ricerca.MESI[esMese - 1]) + " " + (annoN?.toString() ?: "tutti gli anni")
        sezione(cont, (if (esVista == 0) "METODI DI PAGAMENTO" else "CONTI") + " · " + periodo.uppercase())
        if (voci.isEmpty()) {
            vuoto(cont, "Nessuna operazione nel periodo scelto.")
            return
        }
        val tE = voci.filter { it.tipo == "Entrata" }.sumOf { it.importo }
        val tU = voci.filter { it.tipo == "Uscita" }.sumOf { it.importo }
        val netto = tE - tU
        riga(
            cont, "Totale periodo",
            voci.size.toString() + " operazioni · Entrate " + euro(tE) + " · Uscite " + euro(tU),
            (if (netto >= 0) "+" else "−") + euro(abs(netto)), if (netto >= 0) verde else rosso, null
        )

        class G(val k: String, val e: Double, val u: Double, val n: Int)
        val gruppi = voci.groupBy { if (esVista == 0) it.metodo.trim() else it.conto.trim() }.map { (k, l) ->
            G(k, l.filter { it.tipo == "Entrata" }.sumOf { it.importo }, l.filter { it.tipo == "Uscita" }.sumOf { it.importo }, l.size)
        }.sortedByDescending { it.e + it.u }

        for (g in gruppi) {
            val titolo = if (esVista == 0) {
                if (g.k.isBlank()) "Non specificato" else Repo.metodoConEmoji(g.k)
            } else {
                if (g.k.isBlank()) "Senza conto" else g.k
            }
            val net = g.e - g.u
            val sotto = "Entrate " + euro(g.e) + " · Uscite " + euro(g.u) + " · " + g.n + " operazioni" +
                (if (tU > 0 && g.u > 0) " · " + Math.round(g.u / tU * 100) + "% delle uscite" else "")
            val apri: () -> Unit = {
                rc.azzera()
                if (esVista == 0) rc.metodo = g.k else rc.conto = g.k
                rc.anno = esAnno
                rc.mese = esMese
                rcTesto = ""
                rcFuturi = esFuturi
                mostra(Pagina.RICERCA)
            }
            val click: (() -> Unit)? = if (g.k.isBlank()) null else apri
            riga(cont, titolo, sotto, (if (net >= 0) "+" else "−") + euro(abs(net)), if (net >= 0) verde else rosso, click)
        }
        anTesto(cont, "Tocca una riga per vedere le singole operazioni in Cerca (con gli stessi filtri di anno e mese).", colore = medio, size = 12f)
    }

    private fun menuImportaEstratto() {
        OcBuilder(this)
            .setTitle("Estratto conto")
            .setItems(arrayOf("📥  Importa estratto (con revisione)", "🔎  Controlla estratto (riconcilia)")) { _, i ->
                avviaEstratto(i == 1)
            }
            .setNegativeButton("Chiudi", null)
            .show()
    }

    private fun avviaEstratto(controllo: Boolean) {
        if (estrattoInCorso) {
            aviso("Importazione già in corso.")
            return
        }
        if (!controllo && revisioneAperta) {
            aviso("Chiudi prima la finestra di revisione aperta.")
            return
        }
        if (Gemini.chiave(this).isEmpty()) {
            dialogChiaveGemini { avviaEstratto(controllo) }
            return
        }
        estrattoControllo = controllo
        apriEstratto.launch(
            arrayOf(
                "application/pdf", "image/*", "text/csv", "text/comma-separated-values",
                "application/csv", "application/vnd.ms-excel", "text/plain"
            )
        )
    }

    private fun copiaEstratto(uri: Uri): java.io.File {
        var nome = ""
        contentResolver.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val i = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (i >= 0) nome = c.getString(i) ?: ""
            }
        }
        var ext = nome.substringAfterLast('.', "").lowercase()
        val tipo = contentResolver.getType(uri) ?: ""
        if (ext !in listOf("pdf", "png", "jpg", "jpeg", "webp", "heic", "csv", "txt")) {
            ext = when {
                tipo == "application/pdf" -> "pdf"
                tipo == "image/png" -> "png"
                tipo == "image/webp" -> "webp"
                tipo.startsWith("image/") -> "jpg"
                tipo.contains("csv") || tipo == "application/vnd.ms-excel" || tipo == "text/plain" -> "csv"
                else -> "pdf"
            }
        }
        val f = java.io.File(cacheDir, "estratto_import.$ext")
        contentResolver.openInputStream(uri)!!.use { i -> f.outputStream().use { o -> i.copyTo(o) } }
        return f
    }

    private fun msgEstratto(e: Throwable): String {
        val m = e.message ?: ""
        return when {
            e !is Gemini.Errore && ("HTTP 400" in m || "INVALID_ARGUMENT" in m) -> "File non supportato o danneggiato."
            else -> Gemini.messaggio(e)
        }
    }

    private fun avvisoGrande(titolo: String, msg: String) {
        OcBuilder(this).setTitle(titolo).setMessage(msg).setPositiveButton("OK", null).show()
    }

    private fun leggiEstratto(uri: Uri, controllo: Boolean) {
        if (estrattoInCorso) {
            aviso("Importazione già in corso.")
            return
        }
        val file = try {
            copiaEstratto(uri)
        } catch (e: Exception) {
            aviso("File non leggibile: " + e.message)
            return
        }
        val csv = file.extension.lowercase().let { it == "csv" || it == "txt" }
        val attesa = OcBuilder(this)
            .setTitle("🤖 Gemini sta analizzando…")
            .setMessage(if (csv) "Lettura del CSV a blocchi di 100 righe: può richiedere un po' di tempo." else "Attendere…")
            .setCancelable(false)
            .create()
        attesa.show()
        estrattoInCorso = true
        val cats = Repo.categorie()
        Thread {
            val r = runCatching { EstrattoImport.leggi(this, file, cats) }
            runOnUiThread {
                estrattoInCorso = false
                try { attesa.dismiss() } catch (_: Exception) { }
                if (isFinishing || isDestroyed) return@runOnUiThread
                r.onFailure { avvisoGrande("Errore IA", msgEstratto(it)) }
                r.onSuccess { esitoLettura(it, file, csv, controllo) }
            }
        }.start()
    }

    private fun esitoLettura(l: EstrattoImport.Letto, file: java.io.File, csv: Boolean, controllo: Boolean) {
        if (l.singolo) {
            docStaged = file
            dialogDocContabile(null)
            return
        }
        val mov = l.movimenti
        if (mov.isEmpty()) {
            if (csv) {
                when {
                    l.quota != null -> avvisoGrande("Errore IA", "Quota giornaliera Gemini esaurita.\n" + EstrattoImport.attesaQuota(l.quota))
                    l.blocchiFalliti > 0 -> avvisoGrande("Errore IA", "Gemini non ha risposto correttamente per nessun blocco del CSV.\nRiprova tra qualche minuto.")
                    else -> avvisoGrande("Importazione", "Nessun movimento con data fino a oggi nel file.")
                }
            } else if (l.scartate > 0) {
                avvisoGrande("Errore IA", "Gemini non ha restituito dati utilizzabili per questo documento.\nRiprova o controlla il file.")
            } else {
                avvisoGrande("Importazione", "Nessun movimento con data fino a oggi nel documento.")
            }
            return
        }
        if (controllo) dialogRiconciliaEstratto(mov) else dialogRevisioneEstratto(mov)
        if (csv) {
            if (l.quota != null) {
                aviso("Quota giornaliera Gemini esaurita: importate solo le prime " + l.righeElaborate + " righe del CSV. " + EstrattoImport.attesaQuota(l.quota))
            } else if (l.blocchiFalliti > 0 || l.scartate > 0) {
                val parti = ArrayList<String>()
                if (l.blocchiFalliti > 0) parti.add(l.blocchiFalliti.toString() + " blocco/i")
                if (l.scartate > 0) parti.add(l.scartate.toString() + " riga/e")
                aviso(parti.joinToString(" e ") + " del CSV non importate (errore Gemini o dati incompleti).")
            } else if (l.futuri > 0) {
                aviso(l.futuri.toString() + " movimenti con data futura ignorati.")
            }
        } else if (l.scartate > 0) {
            aviso(l.scartate.toString() + " movimento/i non importato/i (dati incompleti nella risposta IA).")
        } else if (l.futuri > 0) {
            aviso(l.futuri.toString() + " movimenti con data futura ignorati.")
        }
    }

    private fun rigaDlg(
        cont: LinearLayout, titolo: String, sotto: String, valore: String, colore: Int?,
        click: (() -> Unit)?,
    ): View {
        val r = inflDialogo.inflate(R.layout.item_voce, cont, false)
        r.findViewById<TextView>(R.id.tvTit).text = titolo
        val tvSub = r.findViewById<TextView>(R.id.tvSub)
        tvSub.text = sotto
        tvSub.visibility = if (sotto.isEmpty()) View.GONE else View.VISIBLE
        val tvImp = r.findViewById<TextView>(R.id.tvImp)
        tvImp.text = valore
        tvImp.maxWidth = (resources.displayMetrics.widthPixels * 0.40f).toInt()
        tvImp.gravity = android.view.Gravity.END or android.view.Gravity.CENTER_VERTICAL
        if (colore != null) tvImp.setTextColor(colore)
        if (click != null) r.setOnClickListener { click() } else {
            r.isClickable = false
            r.isFocusable = false
        }
        cont.addView(r)
        return r
    }

    private fun bottoneDlg(testo: String, click: () -> Unit): MaterialButton {
        val b = MaterialButton(ctxDialogo, null, com.google.android.material.R.attr.materialButtonOutlinedStyle)
        b.text = testo
        b.isAllCaps = false
        b.maxLines = 1
        b.gravity = android.view.Gravity.CENTER
        b.textAlignment = View.TEXT_ALIGNMENT_CENTER
        val p = (6 * resources.displayMetrics.density).toInt()
        b.setPadding(p, b.paddingTop, p, b.paddingBottom)
        b.insetTop = 0
        b.insetBottom = 0
        b.textSize = 13f
        b.setOnClickListener { click() }
        return b
    }

    private fun aggiungiPiede(piede: LinearLayout, vararg voci: Pair<String, () -> Unit>) {
        val d = resources.displayMetrics.density
        voci.forEachIndexed { i, (t, f) ->
            val b = bottoneDlg(t, f)
            b.minHeight = 0
            b.minimumHeight = 0
            b.setPadding(0, 0, 0, 0)
            b.insetTop = 0
            b.insetBottom = 0
            val lp = LinearLayout.LayoutParams(0, (48 * d).toInt(), 1f)
            if (i > 0) lp.marginStart = (8 * d).toInt()
            piede.addView(b, lp)
        }
    }

    private fun dialogGrande(titolo: String, root: View): androidx.appcompat.app.AlertDialog {
        val dlg = OcBuilder(this).setTitle(titolo).setView(root).create()
        dlg.show()
        dlg.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        return dlg
    }

    private fun dialogSceltaSemplice(titolo: String, hint: String, voci: List<String>, iniz: String, onOk: (Int) -> Unit) {
        val (view, corpo) = corpoDialogo()
        var sel = voci.indexOf(iniz).coerceAtLeast(0)
        campoMenu(corpo, hint, voci, voci[sel]) { sel = it }
        OcBuilder(this).setTitle(titolo).setView(view)
            .setPositiveButton("Applica") { _, _ -> onOk(sel) }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun dialogTestoSemplice(titolo: String, hint: String, iniz: String, onOk: (String) -> Unit) {
        val (view, corpo) = corpoDialogo()
        val et = campoTesto(corpo, hint, iniz)
        OcBuilder(this).setTitle(titolo).setView(view)
            .setPositiveButton("Applica") { _, _ -> onOk(et.text.toString()) }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private class RigaRev(
        var inc: Boolean, var data: LocalDate, var desc: String, var imp: String,
        var cat: String, var conto: String, var tag: String, var metodo: String,
    )

    private fun dialogRigaRev(r: RigaRev, dopo: () -> Unit) {
        val (view, corpo) = corpoDialogo()
        val chk = MaterialCheckBox(ctxDialogo)
        chk.text = "Includi nell'importazione"
        chk.isChecked = r.inc
        corpo.addView(chk)
        var data = r.data
        bottoneData(corpo, "Data", data, false) { data = it ?: data }
        val etDesc = campoTesto(corpo, "Descrizione", r.desc, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES, 100)
        val etImp = campoTesto(corpo, "Importo € (negativo = uscita)", r.imp, TIPO_DECIMALE_NEG, 14)
        var cat = r.cat
        val cats = (Repo.categorie() + listOf("Generica", cat)).filter { it.isNotBlank() }.distinct()
        campoMenu(corpo, "Categoria", cats, cat) { cat = cats[it] }
        var conto = r.conto
        val conti = (listOf("") + Repo.conti().map { it.nome } + listOf(conto)).distinct()
        campoMenu(corpo, "Conto", conti.map { if (it.isBlank()) "Nessuno" else it }, if (conto.isBlank()) "Nessuno" else conto) { conto = conti[it] }
        val etTag = campoTesto(corpo, "Tag", r.tag)
        var metodo = r.metodo
        val metodi = (listOf("") + Repo.METODI.map { it.first } + listOf(metodo)).distinct()
        campoMenu(
            corpo, "Metodo di pagamento", metodi.map { if (it.isBlank()) "Nessuno" else Repo.metodoConEmoji(it) },
            if (metodo.isBlank()) "Nessuno" else Repo.metodoConEmoji(metodo)
        ) { metodo = metodi[it] }
        val dlg = OcBuilder(this).setTitle("Movimento da importare").setView(view)
            .setPositiveButton("OK", null)
            .setNegativeButton("Annulla", null)
            .create()
        dlg.show()
        dlg.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val testoImp = etImp.text.toString()
            val n = if (testoImp.isBlank()) null else num(testoImp)
            if (n == null || n > 1_000_000_000.0 || n < -1_000_000_000.0) {
                etImp.error = "Importo non valido"
                return@setOnClickListener
            }
            r.inc = chk.isChecked
            r.data = data
            r.desc = etDesc.text.toString()
            r.imp = String.format(Locale.ITALY, "%.2f", n)
            r.cat = cat
            r.conto = conto
            r.tag = etTag.text.toString()
            r.metodo = metodo
            dlg.dismiss()
            dopo()
        }
    }

    private fun dialogRevisioneEstratto(movimenti: List<EstrattoImport.Mov>) {
        if (revisioneAperta) {
            aviso("Chiudi prima la finestra di revisione aperta.")
            return
        }
        revisioneAperta = true
        val memoria = Repo.memoriaCategorie()
        val cats0 = Repo.categorie()
        val principale = Repo.contoPrincipale()?.nome ?: ""
        val righe = movimenti.sortedBy { it.data }.map { m ->
            val daMem = memoria.optString(m.desc.trim().uppercase(), "")
            val fin = daMem.ifEmpty { if (m.categoria in cats0) m.categoria else "" }.ifEmpty { "Generica" }
            RigaRev(true, m.data, m.desc, String.format(Locale.ITALY, "%.2f", m.importo), fin, principale, "", m.metodo)
        }.toMutableList()
        var colOrd = 1
        var discendente = false
        val dp = resources.displayMetrics.density
        val verde = ContextCompat.getColor(this, R.color.oc_green)
        val rosso = ContextCompat.getColor(this, R.color.oc_red)

        val root = LinearLayout(ctxDialogo)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding((12 * dp).toInt(), (4 * dp).toInt(), (12 * dp).toInt(), (4 * dp).toInt())
        root.minimumHeight = (resources.displayMetrics.heightPixels * 0.78f).toInt()
        val barra = LinearLayout(ctxDialogo)
        barra.orientation = LinearLayout.HORIZONTAL
        val hs = android.widget.HorizontalScrollView(ctxDialogo)
        hs.isHorizontalScrollBarEnabled = false
        hs.addView(barra)
        root.addView(hs)
        val lblInfo = TextView(ctxDialogo)
        lblInfo.textSize = 12f
        lblInfo.setTextColor(ContextCompat.getColor(this, R.color.oc_text_mid))
        lblInfo.setPadding(4, 8, 4, 4)
        root.addView(lblInfo)
        val scroll = android.widget.ScrollView(ctxDialogo)
        val lista = LinearLayout(ctxDialogo)
        lista.orientation = LinearLayout.VERTICAL
        scroll.addView(lista)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        val piede = LinearLayout(ctxDialogo)
        piede.orientation = LinearLayout.HORIZONTAL
        root.addView(piede)

        fun render() {
            lista.removeAllViews()
            lblInfo.text = righe.size.toString() + " movimenti · " + righe.count { it.inc } +
                " selezionati · tocca per modificare, tieni premuto per includere/escludere"
            for (r in righe) {
                val imp = num(r.imp)
                val sotto = listOfNotNull(
                    r.data.format(FMT_DATA), r.cat.ifBlank { null }, r.conto.ifBlank { null },
                    r.metodo.ifBlank { null }, r.tag.ifBlank { null }
                ).joinToString(" · ")
                val valore = if (imp == null) r.imp else (if (imp >= 0) "+" else "−") + euro(abs(imp))
                val v = rigaDlg(
                    lista, (if (r.inc) "☑ " else "☐ ") + r.desc, sotto, valore,
                    if (imp != null && imp >= 0) verde else rosso
                ) { dialogRigaRev(r) { render() } }
                v.alpha = if (r.inc) 1f else 0.45f
                v.setOnLongClickListener {
                    r.inc = !r.inc
                    render()
                    true
                }
            }
        }

        fun ordina(c: Int) {
            if (colOrd == c) discendente = !discendente else {
                colOrd = c
                discendente = false
            }
            val cmp: Comparator<RigaRev> = when (c) {
                1 -> compareBy<RigaRev> { it.data }
                2 -> compareBy<RigaRev> { it.desc.trim().lowercase() }
                3 -> compareBy<RigaRev> { num(it.imp) ?: 0.0 }
                4 -> compareBy<RigaRev> { it.cat.lowercase() }
                5 -> compareBy<RigaRev> { it.conto.lowercase() }
                6 -> compareBy<RigaRev> { it.tag.trim().lowercase() }
                else -> compareBy<RigaRev> { it.metodo.lowercase() }
            }
            righe.sortWith(if (discendente) cmp.reversed() else cmp)
            render()
        }

        fun nuovaCategoria() {
            dialogTestoSemplice("Nuova categoria", "Nome categoria", "") { t ->
                val nome = t.trim()
                if (nome.isEmpty()) return@dialogTestoSemplice
                if (Repo.aggiungiCategoria(nome, "Uscita")) aviso("Creata categoria «$nome»")
                else aviso("Categoria «$nome» esiste già.")
            }
        }

        fun perTutti() {
            OcBuilder(this)
                .setTitle("Per tutte le righe")
                .setItems(
                    arrayOf(
                        "🏦  Conto per tutti", "🏷️  Tag per tutti", "💳  Metodo per tutti",
                        "➕  Nuova categoria", "☑  Seleziona tutti", "☐  Deseleziona tutti"
                    )
                ) { _, i ->
                    when (i) {
                        0 -> {
                            val conti = listOf("") + Repo.conti().map { it.nome }
                            dialogSceltaSemplice(
                                "Conto per tutti", "Conto", conti.map { if (it.isBlank()) "(nessuno)" else it },
                                if (principale.isBlank()) "(nessuno)" else principale
                            ) { k ->
                                righe.forEach { it.conto = conti[k] }
                                aviso("Conto «" + (if (conti[k].isBlank()) "(nessuno)" else conti[k]) + "» applicato a tutte le righe.")
                                render()
                            }
                        }
                        1 -> dialogTestoSemplice("Tag per tutti", "Tag", "") { t ->
                            righe.forEach { it.tag = t }
                            aviso("Tag applicati a tutte le righe.")
                            render()
                        }
                        2 -> {
                            val metodi = listOf("") + Repo.METODI.map { it.first }
                            dialogSceltaSemplice(
                                "Metodo per tutti", "Metodo", metodi.map { if (it.isBlank()) "Nessuno" else Repo.metodoConEmoji(it) },
                                "Nessuno"
                            ) { k ->
                                righe.forEach { it.metodo = metodi[k] }
                                aviso("Metodo «" + metodi[k] + "» applicato a tutte le righe.")
                                render()
                            }
                        }
                        3 -> nuovaCategoria()
                        4 -> { righe.forEach { it.inc = true }; render() }
                        else -> { righe.forEach { it.inc = false }; render() }
                    }
                }
                .setNegativeButton("Chiudi", null)
                .show()
        }

        barra.addView(bottoneDlg("🔀 Ordina") {
            val nomi = listOf("Data", "Descrizione", "Importo €", "Categoria", "Conto", "Tag", "Metodo")
            OcBuilder(this).setTitle("Ordina per")
                .setItems(nomi.mapIndexed { k, n -> if (k + 1 == colOrd) n + (if (discendente) " ▼" else " ▲") else n }.toTypedArray()) { _, k -> ordina(k + 1) }
                .setNegativeButton("Chiudi", null).show()
        })
        barra.addView(bottoneDlg("⚙ Per tutti") { perTutti() })

        val dlg = dialogGrande("Revisione Movimenti IA Gemini", root)
        dlg.setOnDismissListener { revisioneAperta = false }

        fun salva() {
            var duplicati = 0
            val errori = ArrayList<String>()
            val pre = HashMap<LocalDate, ArrayList<Pair<Long, String>>>()
            for (v in Repo.voci()) pre.getOrPut(v.data) { ArrayList() }.add(Math.round(abs(v.importo) * 100.0) to v.tipo)
            val nuove = ArrayList<Repo.NuovaVoce>()
            val mem = Repo.memoriaCategorie()
            for (r in righe) {
                if (!r.inc) continue
                val imp = if (r.imp.isBlank()) null else num(r.imp)
                if (imp == null) {
                    errori.add("Importo non valido: " + r.imp)
                    continue
                }
                val desc = r.desc.trim().take(100)
                val cat = r.cat.ifBlank { "Generica" }
                val tipo = if (imp >= 0) "Entrata" else "Uscita"
                val k = Math.round(abs(imp) * 100.0)
                if (pre[r.data]?.any { it.first == k && it.second == tipo } == true) {
                    duplicati++
                    continue
                }
                nuove.add(Repo.NuovaVoce(r.data, cat, desc, abs(imp), tipo, r.metodo, parseTags(r.tag), r.conto))
                mem.put(desc.trim().uppercase(), cat)
            }
            Repo.salvaVociBlocco(nuove)
            try { Repo.salvaMemoriaCategorie(mem) } catch (_: Exception) { }
            dlg.dismiss()
            refresh()
            var msg = nuove.size.toString() + " importati, " + duplicati + " duplicati ignorati."
            if (errori.isNotEmpty()) msg += "\n⚠️ " + errori.size + " righe saltate per errori."
            avvisoGrande("Completato", msg)
        }

        aggiungiPiede(piede, "💾 Salva" to { salva() }, "Chiudi" to { dlg.dismiss() })
        render()
    }

    private fun dialogRiconciliaEstratto(righe: List<EstrattoImport.Mov>) {
        val conti = Repo.voci().map { it.conto }.filter { it.isNotBlank() }.distinct().sorted()
        val metodiLista = listOf(EstrattoImport.CONTO_CORRENTE) + Repo.METODI.map { it.first }
        val contiLista = listOf("(tutti i conti)") + conti
        val filtri = listOf("Tutti", "Mancanti", "In più", "Riconciliati", "Addebiti carta")
        val giorniLista = (0..10).map { it.toString() }
        var metodoSel = EstrattoImport.CONTO_CORRENTE
        var contoSel = contiLista[0]
        var giorni = EstrattoImport.GIORNI_TOLLERANZA
        var filtro = "Tutti"
        var esito: EstrattoImport.Esito? = null
        var tab: List<EstrattoImport.RigaTab> = emptyList()
        val dp = resources.displayMetrics.density
        val verde = ContextCompat.getColor(this, R.color.oc_green)
        val rosso = ContextCompat.getColor(this, R.color.oc_red)
        val arancio = ContextCompat.getColor(this, R.color.oc_orange)
        val azzurro = android.graphics.Color.parseColor("#61AFEF")

        val root = LinearLayout(ctxDialogo)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding((12 * dp).toInt(), (4 * dp).toInt(), (12 * dp).toInt(), (4 * dp).toInt())
        root.minimumHeight = (resources.displayMetrics.heightPixels * 0.78f).toInt()
        var tilConto: TextInputLayout? = null
        val lblRiep = TextView(ctxDialogo)
        val lblQuad = TextView(ctxDialogo)
        val lista = LinearLayout(ctxDialogo)
        lista.orientation = LinearLayout.VERTICAL

        fun ricalcola() {
            val metodo = if (metodoSel == EstrattoImport.CONTO_CORRENTE) null else metodoSel
            val contoQ = if (metodo != null || contoSel.startsWith("(")) null else contoSel
            tilConto?.isEnabled = metodo == null
            val es = EstrattoImport.riconciliaEstratto(righe, contoQ, metodo, giorni)
            esito = es
            tab = EstrattoImport.righeTabella(es, filtro)
            val testata = EstrattoImport.testata(es, righe, metodoSel, contoSel, giorni, filtro)
            lblRiep.text = testata[1]
            lblQuad.text = if (testata.size > 2) testata[2] else ""
            lblQuad.visibility = if (lblQuad.text.isEmpty()) View.GONE else View.VISIBLE
            lista.removeAllViews()
            if (tab.isEmpty()) {
                val t = TextView(ctxDialogo)
                t.text = "Nessuna riga per il filtro scelto."
                t.setPadding(8, 8, 8, 8)
                lista.addView(t)
            }
            for (t in tab) {
                val colore = when (t.stato) {
                    "OK" -> verde
                    "MANCA" -> rosso
                    "IN PIÙ" -> arancio
                    else -> azzurro
                }
                val testa = listOf(t.data, t.conto, t.metodo, t.tag).filter { it.isNotBlank() }.joinToString(" · ")
                val sotto = testa + (if (t.db != "—") "\n" + t.db else "")
                val id = EstrattoImport.idPerRiga(es, t)
                val click: (() -> Unit)? = when {
                    id != null -> { { Repo.voci().firstOrNull { it.id == id }?.let { v -> dialogVoce(v) } } }
                    t.stato == "MANCA" -> {
                        {
                            es.mancanti.firstOrNull {
                                it.data.format(FMT_DATA) == t.data && EstrattoImport.fmt(it.importo) == t.importo && it.desc == t.desc
                            }?.let { m ->
                                dialogVoce(
                                    null, m.data, m.categoria.ifBlank { "Generica" }, abs(m.importo),
                                    if (m.importo >= 0) "Entrata" else "Uscita", m.desc
                                )
                            }
                        }
                    }
                    else -> null
                }
                rigaDlg(lista, "[" + t.stato + "] " + (if (t.desc.isBlank()) "—" else t.desc), sotto, t.importo + " €", colore, click)
            }
        }

        campoMenu(root, "Estratto di", metodiLista, metodoSel) { metodoSel = metodiLista[it]; ricalcola() }
        tilConto = campoMenu(root, "Conto", contiLista, contoSel) { contoSel = contiLista[it]; ricalcola() }
        anRiga(root) { r ->
            campoMenu(r, "Tolleranza giorni", giorniLista, giorni.toString()) { giorni = it; ricalcola() }
            campoMenu(r, "Mostra", filtri, filtro) { filtro = filtri[it]; ricalcola() }
        }
        lblRiep.textSize = 12f
        lblRiep.setTypeface(null, Typeface.BOLD)
        lblRiep.setPadding(4, 8, 4, 2)
        lblQuad.textSize = 12f
        lblQuad.setTypeface(null, Typeface.BOLD)
        lblQuad.setPadding(4, 2, 4, 4)
        root.addView(lblRiep)
        root.addView(lblQuad)
        val scroll = android.widget.ScrollView(ctxDialogo)
        scroll.addView(lista)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        val piede = LinearLayout(ctxDialogo)
        piede.orientation = LinearLayout.HORIZONTAL
        root.addView(piede)
        val dlg = dialogGrande("Controllo Estratto Conto", root)

        fun esporta() {
            val es = esito ?: return
            val testo = EstrattoImport.testoEsportazione(es, righe, tab, metodoSel, contoSel, giorni, filtro)
            if (testo.isEmpty()) {
                aviso("Nessun dato da esportare.")
                return
            }
            val tv = TextView(ctxDialogo)
            tv.typeface = Typeface.MONOSPACE
            tv.textSize = 9f
            tv.text = testo
            tv.setPadding(16, 16, 16, 16)
            val sv = android.widget.ScrollView(ctxDialogo)
            val h = android.widget.HorizontalScrollView(ctxDialogo)
            h.addView(tv)
            sv.addView(h)
            OcBuilder(this)
                .setTitle("Esportazione Controllo Estratto")
                .setView(sv)
                .setPositiveButton("PDF") { _, _ ->
                    try {
                        EstrattiPdf.condividi(this, EstrattoImport.creaPdf(this, testo), "Controllo estratto")
                    } catch (e: Exception) {
                        aviso("Errore PDF: " + e.message)
                    }
                }
                .setNegativeButton("Chiudi", null)
                .show()
        }

        fun importaMancanti() {
            val es = esito ?: return
            if (es.mancanti.isEmpty()) {
                aviso("Nessun movimento mancante da importare.")
                return
            }
            val met = if (metodoSel == EstrattoImport.CONTO_CORRENTE) "" else metodoSel
            dlg.dismiss()
            dialogRevisioneEstratto(es.mancanti.map { EstrattoImport.Mov(it.data, it.desc, it.importo, it.categoria, met) })
        }

        aggiungiPiede(piede, "📥 Importa" to { importaMancanti() }, "📤 Esporta" to { esporta() }, "Chiudi" to { dlg.dismiss() })
        ricalcola()
    }

    private fun condividiEstratti() {
        val oggi = LocalDate.now()
        val annoN = esAnno.toIntOrNull()
        val voci = Repo.voci().filter {
            (esFuturi || !it.data.isAfter(oggi)) &&
                (annoN == null || it.data.year == annoN) &&
                (esMese == 0 || it.data.monthValue == esMese)
        }
        if (voci.isEmpty()) { aviso("Nessuna operazione nel periodo scelto"); return }
        val perConto = esVista == 1
        val gruppi = LinkedHashMap<String, ArrayList<EstrattiPdf.Riga>>()
        for (v in voci) {
            val k = if (perConto) v.conto.trim() else v.metodo.trim()
            val tag = if (v.hashtag.isNotEmpty()) v.hashtag.joinToString(" ") { "#" + it.trimStart('#') } else ""
            gruppi.getOrPut(k) { ArrayList() }.add(
                EstrattiPdf.Riga(
                    v.data, v.categoria, (v.descrizione + " " + tag).trim(),
                    if (perConto) v.metodo.trim() else v.conto.trim(), v.importo, v.tipo == "Entrata"
                )
            )
        }
        if (perConto) {
            val nomi = Repo.conti().associate { it.id to it.nome }
            for (t in Repo.trasferimenti()) {
                if (!esFuturi && t.data.isAfter(oggi)) continue
                if (annoN != null && t.data.year != annoN) continue
                if (esMese != 0 && t.data.monthValue != esMese) continue
                val imp = Math.round(t.importo * 100.0) / 100.0
                val nDa = nomi[t.da].orEmpty()
                val nA = nomi[t.a].orEmpty()
                if (nDa.isNotEmpty()) {
                    gruppi.getOrPut(nDa) { ArrayList() }.add(
                        EstrattiPdf.Riga(t.data, "Trasferimento", ("verso " + nA + " " + t.note).trim(), "", imp, false)
                    )
                }
                if (nA.isNotEmpty()) {
                    gruppi.getOrPut(nA) { ArrayList() }.add(
                        EstrattiPdf.Riga(t.data, "Trasferimento", ("da " + nDa + " " + t.note).trim(), "", imp, true)
                    )
                }
            }
        }
        val sezioni = gruppi.map { (k, l) ->
            EstrattiPdf.Sez(
                if (k.isBlank()) (if (perConto) "Senza conto" else "Non specificato") else k,
                l.sortedByDescending { it.data }
            )
        }.sortedByDescending { it.e + it.u }
        val tE = voci.filter { it.tipo == "Entrata" }.sumOf { it.importo }
        val tU = voci.filter { it.tipo == "Uscita" }.sumOf { it.importo }
        val periodo = (if (esMese == 0) "Tutti i mesi" else Ricerca.MESI[esMese - 1]) + " " + (annoN?.toString() ?: "tutti gli anni")
        val titolo = if (perConto) "Estratti per conto" else "Estratti per metodo di pagamento"
        val f = EstrattiPdf.crea(
            this, titolo, periodo, if (perConto) "Metodo" else "Conto",
            sezioni, voci.size, tE, tU, if (perConto) "estratti_conti" else "estratti_metodi"
        )
        EstrattiPdf.condividi(this, f, titolo)
    }

    private class ChiediMsg(val domanda: String, var risposta: String?, var errore: Boolean = false, val allegati: String = "")

    private val chiediStorico = ArrayList<ChiediMsg>()
    private var chiediBozza = ""
    private var chiediInCorso = false

    private fun renderChiedi() {
        intestaLista("🤖 Chiedi al Database", "🗑️  Nuova conversazione")
        val cont = vista<LinearLayout>(R.id.listaGen)
        val medio = ContextCompat.getColor(this, R.color.oc_text_mid)
        val rosso = ContextCompat.getColor(this, R.color.oc_red)
        if (Gemini.chiave(this).isEmpty()) {
            vuoto(cont, "Per fare domande ai tuoi dati serve la chiave API Gemini (gratuita su aistudio.google.com).")
            cont.addView(pulsanteSel("🔑 Imposta chiave Gemini", true) { dialogChiaveGemini { renderChiedi() } })
            return
        }
        anTesto(
            cont,
            "Ogni domanda invia a Google Gemini un riepilogo dei tuoi dati: totali per periodo, categorie, conti, budget, " +
                "finanziamenti e gli ultimi movimenti con la descrizione. Con 📎 puoi allegare PDF o foto (max 4, 12 MB in totale), " +
                "ad esempio due bollette da confrontare, oppure scegli con 📚 documenti già archiviati nell'app: anche gli allegati vengono inviati a Gemini. Le risposte sono indicative.",
            colore = medio, size = 12f
        )
        val til = layoutInflater.inflate(R.layout.view_campo, cont, false) as TextInputLayout
        til.hint = "Es. quanto ho speso in carburante a ottobre?"
        val et = til.findViewById<TextInputEditText>(R.id.campoTesto)
        et.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        et.minLines = 2
        et.maxLines = 5
        et.setText(chiediBozza)
        et.doAfterTextChanged { chiediBozza = it?.toString() ?: "" }
        cont.addView(til)
        val inv = pulsanteSel(if (chiediInCorso) "⏳ Penso…" else "➤ Chiedi", true) { invioChiedi() }
        inv.maxLines = 1
        inv.isEnabled = !chiediInCorso
        val mic = pulsanteSel("🎤", false) {
            val i = Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, "it-IT")
                .putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Fai la tua domanda")
            try {
                vocale.launch(i)
            } catch (e: Exception) {
                aviso("Riconoscimento vocale non disponibile")
            }
        }
        mic.isEnabled = !chiediInCorso
        val clip = pulsanteSel("📎", false) {
            if (chiediAllegati.size >= 4) aviso("Massimo 4 allegati")
            else apriAllegatiChiedi.launch(arrayOf("application/pdf", "image/*"))
        }
        clip.isEnabled = !chiediInCorso
        val arch = pulsanteSel("📚", false) { dialogAllegaArchivio() }
        arch.isEnabled = !chiediInCorso
        val rigaChiedi = LinearLayout(this)
        rigaChiedi.orientation = LinearLayout.HORIZONTAL
        rigaChiedi.addView(inv, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        for (b in listOf(clip, arch, mic)) {
            b.minWidth = 0
            b.minimumWidth = 0
            b.setPadding(0, b.paddingTop, 0, b.paddingBottom)
        }
        val lpMic = LinearLayout.LayoutParams((44 * resources.displayMetrics.density).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        lpMic.marginStart = (4 * resources.displayMetrics.density).toInt()
        rigaChiedi.addView(clip, LinearLayout.LayoutParams(lpMic))
        rigaChiedi.addView(arch, LinearLayout.LayoutParams(lpMic))
        rigaChiedi.addView(mic, lpMic)
        cont.addView(rigaChiedi)
        for (a in chiediAllegati.toList()) {
            cont.addView(pulsanteSel("✖  " + a.second.take(40), false) {
                a.first.delete()
                chiediAllegati.remove(a)
                renderChiedi()
            })
        }

        if (chiediStorico.isEmpty()) {
            vuoto(cont, "Puoi chiedere totali e confronti (\"spese di settembre rispetto a agosto\"), dettagli per conto o categoria, " +
                "stato dei finanziamenti, budget sforati, oppure consigli su come risparmiare.")
            return
        }
        val dp = resources.displayMetrics.density
        for (m in chiediStorico.asReversed()) {
            anTesto(cont, "❓ " + m.domanda, grassetto = true)
            if (m.allegati.isNotEmpty()) anTesto(cont, "📎 " + m.allegati, colore = medio, size = 12f)
            val t = TextView(this)
            t.text = m.risposta ?: "…"
            t.setTextIsSelectable(true)
            t.setBackgroundResource(R.drawable.bg_tile)
            t.setPadding((12 * dp).toInt(), (10 * dp).toInt(), (12 * dp).toInt(), (10 * dp).toInt())
            t.setTextColor(if (m.errore) rosso else ContextCompat.getColor(this, R.color.oc_text))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.bottomMargin = (10 * dp).toInt()
            cont.addView(t, lp)
        }
    }

    private fun invioChiedi() {
        val d = chiediBozza.trim()
        if (d.isEmpty()) {
            aviso("Scrivi una domanda")
            return
        }
        if (chiediInCorso) return
        val cronologia = chiediStorico.filter { !it.errore && it.risposta != null }.takeLast(4).map { it.domanda to it.risposta!! }
        val allegati = chiediAllegati.toList()
        val prompt = try {
            ChiediDb.prompt(d, cronologia, allegati.map { it.second })
        } catch (e: Exception) {
            aviso("Impossibile preparare i dati: " + e.message)
            return
        }
        val m = ChiediMsg(d, null, false, allegati.joinToString(", ") { it.second })
        chiediStorico.add(m)
        chiediBozza = ""
        chiediAllegati.clear()
        chiediInCorso = true
        renderChiedi()
        val app = applicationContext
        Thread {
            var testo: String? = null
            var err: String? = null
            try {
                testo = Gemini.chiediTestoAllegati(app, prompt, allegati.map { it.first })
            } catch (e: Exception) {
                err = Gemini.messaggio(e)
            } finally {
                allegati.forEach { it.first.delete() }
            }
            runOnUiThread {
                chiediInCorso = false
                m.risposta = testo ?: err ?: "Errore sconosciuto"
                m.errore = testo == null
                if (!isFinishing && !isDestroyed && pagina == Pagina.CHIEDI) renderChiedi()
            }
        }.start()
    }

    private fun renderRicerca() {
        intestaLista("🔎 Ricerca operazioni", "", "📤  Condividi")
        val cont = vista<LinearLayout>(R.id.listaGen)
        val til = layoutInflater.inflate(R.layout.view_campo, cont, false) as TextInputLayout
        til.hint = "Cerca in categoria, descrizione, importo, conto, tag…"
        val et = til.findViewById<TextInputEditText>(R.id.campoTesto)
        et.setText(rcTesto)
        cont.addView(til)

        val chk = MaterialCheckBox(this)
        chk.text = "Includi futuri"
        chk.isChecked = rcFuturi
        cont.addView(chk)

        val boxFiltri = LinearLayout(this)
        boxFiltri.orientation = LinearLayout.VERTICAL
        cont.addView(boxFiltri)
        val boxTot = LinearLayout(this)
        boxTot.orientation = LinearLayout.VERTICAL
        cont.addView(boxTot)
        val boxRis = LinearLayout(this)
        boxRis.orientation = LinearLayout.VERTICAL
        cont.addView(boxRis)

        val verde = ContextCompat.getColor(this, R.color.oc_green)
        val rosso = ContextCompat.getColor(this, R.color.oc_red)
        val arancio = ContextCompat.getColor(this, R.color.oc_orange)
        val oro = ContextCompat.getColor(this, R.color.oc_gold)

        fun aggiorna() {
            boxFiltri.removeAllViews()
            boxTot.removeAllViews()
            boxRis.removeAllViews()
            val desc = Ricerca.descrizioneFiltri(rc)
            riga(
                boxFiltri, "Filtri avanzati",
                if (Ricerca.attivo(rc)) "filtri attivi: " + desc.joinToString(", ") else "nessun filtro attivo",
                "⚙️", if (Ricerca.attivo(rc)) oro else null
            ) { dialogFiltriRicerca() }

            val voci = Repo.voci()
            val ris = Ricerca.cerca(voci, rcTesto, rcFuturi, rc)
            if (ris.isEmpty()) {
                vuoto(boxTot, "Nessuna corrispondenza per la ricerca attuale.")
                return
            }
            val tE = ris.filter { it.tipo == "Entrata" }.sumOf { it.importo }
            val tU = ris.filter { it.tipo == "Uscita" }.sumOf { it.importo }
            val netto = tE - tU
            val sotto = (if (rcTesto.isNotBlank()) "Parola chiave: '" + rcTesto.trim() + "' · " else "") +
                "Entrate " + euro(tE) + " · Uscite " + euro(tU)
            riga(
                boxTot, "Trovate " + ris.size + " operazioni", sotto,
                (if (netto >= 0) "+" else "−") + euro(abs(netto)), if (netto >= 0) verde else rosso, null
            )

            val rigaTot = boxTot.getChildAt(boxTot.childCount - 1)
            val tvTitTot = rigaTot.findViewById<TextView>(R.id.tvTit)
            val colTit = tvTitTot.parent as LinearLayout
            val posTit = colTit.indexOfChild(tvTitTot)
            colTit.removeView(tvTitTot)
            tvTitTot.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            val rigaTit = LinearLayout(this)
            rigaTit.orientation = LinearLayout.HORIZONTAL
            rigaTit.gravity = android.view.Gravity.CENTER_VERTICAL
            rigaTit.addView(tvTitTot)
            rigaTit.addView(nuovoMeteo(netto >= 0, (20 * resources.displayMetrics.density).toInt()))
            colTit.addView(rigaTit, posTit, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

            val speso = HashMap<Triple<Int, Int, String>, Double>()
            for (v in voci) if (v.tipo == "Uscita") {
                val k = Triple(v.data.year, v.data.monthValue, v.categoria.trim().lowercase())
                speso[k] = (speso[k] ?: 0.0) + v.importo
            }
            val oggi = LocalDate.now()
            for (v in ris.take(300)) {
                val futuro = v.data.isAfter(oggi)
                var sforato = false
                if (!futuro && v.tipo == "Uscita") {
                    val b = Repo.budgetMensile(v.categoria)
                    if (b > 0 && (speso[Triple(v.data.year, v.data.monthValue, v.categoria.trim().lowercase())] ?: 0.0) > b) sforato = true
                }
                val colore = when {
                    futuro -> arancio
                    v.tipo == "Entrata" -> verde
                    else -> rosso
                }
                val dettaglio = ArrayList<String>()
                dettaglio.add(v.data.format(FMT_DATA))
                dettaglio.add(v.categoria)
                if (v.conto.isNotBlank()) dettaglio.add(v.conto)
                if (v.metodo.isNotBlank()) dettaglio.add(Repo.metodoConEmoji(v.metodo))
                if (v.hashtag.isNotEmpty()) dettaglio.add(v.hashtag.joinToString(" ") { "#" + it.trimStart('#') })
                if (futuro) dettaglio.add("futura")
                riga(
                    boxRis, (if (sforato) "⚠️ " else "") + v.descrizione.ifBlank { v.categoria }, dettaglio.joinToString(" · "),
                    (if (v.tipo == "Entrata") "+" else "−") + euro(v.importo), colore
                ) { dialogVoce(v) }
                boxRis.getChildAt(boxRis.childCount - 1).setOnLongClickListener {
                    dialogVoce(
                        null, null, v.categoria, v.importo, v.tipo,
                        if (v.descrizione.contains("RIC·")) "" else v.descrizione.take(30)
                    )
                    true
                }
            }
            if (ris.size > 300) vuoto(boxRis, "…e altre " + (ris.size - 300) + " operazioni: restringi la ricerca.")
            else vuoto(boxRis, "Tocca per modificare · tieni premuto per usarla come modello di una nuova operazione.")
        }

        et.doAfterTextChanged { t ->
            rcTesto = t?.toString() ?: ""
            aggiorna()
        }
        chk.setOnCheckedChangeListener { _, on ->
            rcFuturi = on
            aggiorna()
        }
        aggiorna()
    }

    private fun dialogFiltriRicerca() {
        val (view, corpo) = corpoDialogo()
        val tutti = "Tutti"
        val voci = Repo.voci()
        val tipi = listOf(tutti, "Entrata", "Uscita")
        val cats = listOf(tutti) + Repo.categorie().sortedBy { it.lowercase() }
        val anni = listOf(tutti) + voci.map { it.data.year }.distinct().sortedDescending().map { it.toString() }
        val mesiL = listOf(tutti) + Ricerca.MESI
        val partL = listOf(tutti) + FairShare.etichette()
        val contiL = listOf(tutti) + Repo.conti().map { it.nome }
        val metodiL = listOf(tutti) + Repo.METODI.map { it.first }
        fun cur(l: List<String>, v: String) = if (v.isEmpty()) tutti else v
        var tipo = rc.tipo
        var cat = rc.categoria
        var anno = rc.anno
        var mese = rc.mese
        var part = rc.partecipante
        var conto = rc.conto
        var metodo = rc.metodo
        campoMenu(corpo, "Tipo", tipi, cur(tipi, tipo)) { tipo = if (it == 0) "" else tipi[it] }
        campoMenu(corpo, "Categoria", cats, cur(cats, cat)) { cat = if (it == 0) "" else cats[it] }
        campoMenu(corpo, "Anno", anni, cur(anni, anno)) { anno = if (it == 0) "" else anni[it] }
        campoMenu(corpo, "Mese", mesiL, mesiL[mese]) { mese = it }
        if (partL.size > 1) campoMenu(corpo, "Partecipante", partL, cur(partL, part)) { part = if (it == 0) "" else partL[it] }
        if (contiL.size > 1) campoMenu(corpo, "Conto", contiL, cur(contiL, conto)) { conto = if (it == 0) "" else contiL[it] }
        campoMenu(corpo, "Metodo di pagamento", metodiL, if (metodo.isEmpty()) tutti else metodo) { metodo = if (it == 0) "" else metodiL[it] }
        val etTag = campoTesto(corpo, "Tag (separati da spazio)", rc.tag)
        suggerimentiTag(etTag)
        val etTesto = campoTesto(corpo, "Descrizione contiene", rc.testo)
        val etDa = campoTesto(corpo, "Importo da (€)", rc.impDa, InputType.TYPE_CLASS_TEXT, 14)
        val etA = campoTesto(corpo, "Importo a (€)", rc.impA, InputType.TYPE_CLASS_TEXT, 14)
        val etDal = campoTesto(corpo, "Dal (GG-MM-AAAA)", rc.dal, InputType.TYPE_CLASS_TEXT, 10)
        val etAl = campoTesto(corpo, "Al (GG-MM-AAAA)", rc.al, InputType.TYPE_CLASS_TEXT, 10)
        for (et in listOf(etDal, etAl)) {
            et.isFocusable = false
            et.isFocusableInTouchMode = false
            et.isCursorVisible = false
            et.setOnClickListener { apriCalendario(et) }
        }
        val dlg = OcBuilder(this)
            .setTitle("Filtri avanzati")
            .setView(view)
            .setPositiveButton("Applica", null)
            .setNeutralButton("Azzera", null)
            .setNegativeButton("Annulla", null)
            .create()
        dlg.show()
        dlg.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val dal = etDal.text.toString()
            val al = etAl.text.toString()
            if (dal.isNotBlank() && Ricerca.dataLibera(dal) == null) { etDal.error = "Data non valida"; return@setOnClickListener }
            if (al.isNotBlank() && Ricerca.dataLibera(al) == null) { etAl.error = "Data non valida"; return@setOnClickListener }
            rc.tipo = tipo; rc.categoria = cat; rc.anno = anno; rc.mese = mese
            rc.partecipante = part; rc.conto = conto; rc.metodo = metodo
            rc.tag = etTag.text.toString(); rc.testo = etTesto.text.toString()
            rc.impDa = etDa.text.toString(); rc.impA = etA.text.toString()
            rc.dal = dal; rc.al = al
            dlg.dismiss()
            renderRicerca()
        }
        dlg.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
            rc.azzera()
            rcTesto = ""
            dlg.dismiss()
            renderRicerca()
        }
    }

    private fun condividiRicerca() {
        val ris = Ricerca.cerca(Repo.voci(), rcTesto, rcFuturi, rc)
        if (ris.isEmpty()) { aviso("Nessun risultato da condividere"); return }
        val tE = ris.filter { it.tipo == "Entrata" }.sumOf { it.importo }
        val tU = ris.filter { it.tipo == "Uscita" }.sumOf { it.importo }
        val filtri = (if (rcTesto.isNotBlank()) listOf("Parola chiave: '" + rcTesto.trim() + "'") else emptyList()) +
            Ricerca.descrizioneFiltri(rc)
        val filtriTxt = if (filtri.isEmpty()) "Nessuno" else filtri.joinToString(", ")
        val f = RicercaPdf.crea(this, ris, filtriTxt, tE, tU)
        RicercaPdf.condividi(this, f)
    }

    private fun controllaFineMese() {
        if (!sbloccata || isFinishing) return
        val ora = SystemClock.elapsedRealtime()
        if (ultimoControlloFm != 0L && ora - ultimoControlloFm < 3_600_000L) return
        ultimoControlloFm = ora
        val man = Checkout.daAvvisare(Repo.voci()) ?: return
        OcBuilder(this)
            .setTitle("Promemoria movimenti ricorrenti")
            .setMessage(
                "Hai dimenticato qualcosa? Le seguenti categorie, registrate abitualmente nei mesi scorsi, " +
                    "non risultano ancora presenti in questo mese:\n\n" + man.joinToString("\n") +
                    "\n\nApri la verifica dettagliata, oppure nascondi l'avviso fino al mese prossimo."
            )
            .setPositiveButton("Verifica") { _, _ ->
                ckVista = 1
                ckAnno = LocalDate.now().year
                mostra(Pagina.CHECKOUT)
            }
            .setNegativeButton("Nascondi") { _, _ -> Repo.setFmDismiss(LocalDate.now()) }
            .setNeutralButton("Più tardi", null)
            .show()
    }

    private fun caricaPool() {
        if (poolInCorso) return
        if (poolDati == null) Pool.carica(this)?.let {
            poolDati = it.first
            poolOra = it.second
        }
        poolInCorso = true
        if (pagina == Pagina.POOL) renderPool()
        Sync.poolDati(this, true) { j, err ->
            poolInCorso = false
            if (j != null) {
                poolDati = j
                poolOra = System.currentTimeMillis()
                poolErrore = null
                Pool.salva(this, j)
            } else {
                poolErrore = err ?: "Desktop non raggiungibile"
            }
            if (pagina == Pagina.POOL) renderPool()
        }
    }

    private fun poolInvia(corpo: JSONObject, esito: String) {
        poolInCorso = true
        if (pagina == Pagina.POOL) renderPool()
        Sync.poolAzione(this, corpo) { err ->
            poolInCorso = false
            aviso(err ?: esito)
            caricaPool()
        }
    }

    private fun renderPool() {
        val nomiVista = listOf("Spese", "Da confermare", "Le mie quote", "Saldi")
        intestaLista("👥 CommunityPool", "🔄  Aggiorna", "🔀  Vista: " + nomiVista[poolVista])
        val cont = vista<LinearLayout>(R.id.listaGen)
        val verde = ContextCompat.getColor(this, R.color.oc_green)
        val rosso = ContextCompat.getColor(this, R.color.oc_red)
        val oro = ContextCompat.getColor(this, R.color.oc_gold)
        val arancio = ContextCompat.getColor(this, R.color.oc_orange)
        val medio = ContextCompat.getColor(this, R.color.oc_text_mid)
        fun testo(t: String, col: Int) {
            val v = TextView(this)
            v.text = t
            v.setTextColor(col)
            val dpt = resources.displayMetrics.density
            v.setPadding(0, (6 * dpt).toInt(), 0, (6 * dpt).toInt())
            cont.addView(v)
        }
        val d = poolDati
        if (poolInCorso) {
            testo("Comunicazione con OrbitaCasa desktop…", medio)
        } else if (poolErrore != null) {
            val errPool = poolErrore ?: ""
            val msgPool = if (errPool.startsWith("Desktop non raggiungibile")) "🔴 Desktop offline · non raggiungibile" else errPool
            testo("Sincronizza OrbitaCasa desktop per usare il CommunityPool\n" + msgPool, arancio)
        }
        if (d == null) return
        if (!d.optBoolean("attivo", true)) {
            vuoto(cont, d.optString("messaggio").ifBlank { "CommunityPool non attivo su Desktop." })
            return
        }
        val attivo = !poolInCorso && poolErrore == null
        val quando = if (poolOra > 0) java.text.SimpleDateFormat("dd-MM HH:mm", Locale.ITALY).format(java.util.Date(poolOra)) else "--"
        testo(
            "Profilo su Desktop: " + d.optString("me") + " · aggiornato " + quando + if (attivo) "" else " (sola lettura)",
            medio
        )
        when (poolVista) {
            0 -> {
                sezione(cont, "SPESE CONDIVISE")
                val sp = d.optJSONArray("spese") ?: org.json.JSONArray()
                if (sp.length() == 0) vuoto(cont, "Nessuna spesa nel pool.")
                for (i in 0 until sp.length()) {
                    val s = sp.getJSONObject(i)
                    val sotto = s.optString("data_it") + " · " + s.optString("pagante") + " · " + s.optString("categoria") +
                        "\nCoperto € " + s.optString("coperto_t") + " · Residuo € " + s.optString("residuo_t") + " · " + s.optString("stato")
                    riga(cont, s.optString("descrizione"), sotto, "€ " + s.optString("importo_t"), if (s.optBoolean("mia_spesa")) verde else rosso) {
                        poolSpesaClick(s, attivo)
                    }
                }
            }
            1 -> {
                sezione(cont, "PROPOSTE DA CONFERMARE")
                val dc = d.optJSONArray("da_confermare") ?: org.json.JSONArray()
                if (dc.length() == 0) vuoto(cont, "Nessuna proposta in attesa sulle spese pagate da te.")
                for (i in 0 until dc.length()) {
                    val c = dc.getJSONObject(i)
                    val nota = c.optString("nota")
                    val sotto = c.optString("data_it") + " · Da: " + c.optString("da") + " · Residuo € " + c.optString("residuo_t") +
                        if (nota.isBlank()) "" else "\n" + nota
                    riga(cont, c.optString("descrizione"), sotto, "€ " + c.optString("proposta_t"), oro) {
                        if (attivo) dialogPoolRisposta(c) else aviso("Sincronizza OrbitaCasa desktop per usare il CommunityPool")
                    }
                }
            }
            2 -> {
                sezione(cont, "LE MIE QUOTE")
                val mie = d.optJSONArray("mie") ?: org.json.JSONArray()
                if (mie.length() == 0) vuoto(cont, "Non hai ancora proposto quote.")
                for (i in 0 until mie.length()) {
                    val q = mie.getJSONObject(i)
                    val stato = q.optString("stato")
                    val nota = q.optString("nota")
                    val sotto = q.optString("data_it") + " · Pagante: " + q.optString("pagante") + " · " + q.optString("stato_lbl") +
                        if (nota.isBlank()) "" else "\n" + nota
                    val valore = if (stato == "confermata") q.optString("importo_t") else q.optString("proposta_t")
                    val col = when (stato) {
                        "confermata" -> verde
                        "proposta" -> oro
                        else -> rosso
                    }
                    riga(cont, q.optString("descrizione"), sotto, "€ $valore", col) {
                        if (stato != "proposta") {
                            aviso("Solo le proposte in attesa si possono annullare")
                        } else if (!attivo) {
                            aviso("Sincronizza OrbitaCasa desktop per usare il CommunityPool")
                        } else {
                            OcBuilder(this)
                                .setTitle("Annulla proposta")
                                .setMessage("Annullare la proposta di € " + q.optString("proposta_t") + " su «" + q.optString("descrizione") + "»?")
                                .setNegativeButton("No", null)
                                .setPositiveButton("Annulla proposta") { _, _ ->
                                    poolInvia(JSONObject().put("azione", "annulla").put("id_contrib", q.optString("id_contrib")), "Proposta annullata")
                                }
                                .show()
                        }
                    }
                }
            }
            else -> {
                val sal = d.optJSONObject(if (poolChiuse) "saldi_tutti" else "saldi")
                riga(cont, "Spese chiuse", "Tocca per cambiare", if (poolChiuse) "Incluse" else "Nascoste", oro) {
                    poolChiuse = !poolChiuse
                    renderPool()
                }
                sezione(cont, "SALDI PER PROFILO")
                val righe = sal?.optJSONArray("righe") ?: org.json.JSONArray()
                if (righe.length() == 0) vuoto(cont, "Nessun movimento nel pool.")
                for (i in 0 until righe.length()) {
                    val r = righe.getJSONObject(i)
                    val n = r.optDouble("residuo_n", 0.0)
                    val sotto = "Anticipate € " + r.optString("anticipate") + " · Ricevute € " + r.optString("ricevute") + " · Versate € " + r.optString("versate")
                    val col = if (n > 0.004) verde else if (n < -0.004) rosso else null
                    riga(cont, r.optString("nome"), sotto, (if (n < -0.004) "−" else "") + euro(abs(n)), col, null)
                }
                val tot = sal?.optJSONObject("tot")
                if (tot != null) {
                    val sotto = "Anticipate € " + tot.optString("anticipate") + " · Ricevute € " + tot.optString("ricevute") + " · Versate € " + tot.optString("versate")
                    riga(cont, "Totale", sotto, "€ " + tot.optString("residuo"), oro, null)
                }
            }
        }
    }

    private fun poolSpesaClick(s: JSONObject, attivo: Boolean) {
        if (!attivo) {
            aviso("Sincronizza OrbitaCasa desktop per usare il CommunityPool")
            return
        }
        if (s.optBoolean("puo_proporre")) {
            dialogPoolProponi(s)
            return
        }
        aviso(
            when {
                s.optBoolean("mia_spesa") -> "Hai pagato tu questa spesa"
                s.optBoolean("mia_attesa") -> "Hai già una proposta in attesa su questa spesa"
                s.optString("stato") == "Chiusa" -> "La spesa è chiusa dal pagante"
                else -> "La spesa è già coperta"
            }
        )
    }

    private fun dialogPoolProponi(s: JSONObject) {
        val (view, corpo) = corpoDialogo()
        val info = TextView(this)
        info.text = s.optString("descrizione") + "\nTotale € " + s.optString("importo_t") + " · Residuo € " + s.optString("residuo_t")
        info.setPadding(4, 4, 4, 12)
        corpo.addView(info)
        val etImp = campoTesto(corpo, "Importo da coprire (€)", s.optString("residuo_t").replace(".", ""), TIPO_DECIMALE, 12)
        val etNota = campoTesto(corpo, "Nota (facoltativa)", "", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES, 200)
        val arr = poolDati?.optJSONArray("categorie") ?: org.json.JSONArray()
        val cats = (0 until arr.length()).map { arr.optString(it) }
        var cat = s.optString("categoria")
        if (cats.isNotEmpty() && cat !in cats) cat = cats[0]
        if (cats.isNotEmpty()) campoMenu(corpo, "Categoria", cats, cat) { cat = cats[it] }
        val dlg = OcBuilder(this)
            .setTitle("Proponi quota")
            .setView(view)
            .setPositiveButton("Invia", null)
            .setNegativeButton("Annulla", null)
            .create()
        dlg.show()
        dlg.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if (importoOk(etImp.text.toString()) == null) {
                etImp.error = "Importo non valido"
                return@setOnClickListener
            }
            dlg.dismiss()
            poolInvia(
                JSONObject()
                    .put("azione", "proponi")
                    .put("id_fam", s.optString("id_fam"))
                    .put("importo", etImp.text.toString().trim())
                    .put("nota", etNota.text.toString().trim())
                    .put("categoria", cat),
                "Quota inviata, in attesa di conferma"
            )
        }
    }

    private fun dialogPoolRisposta(c: JSONObject) {
        val (view, corpo) = corpoDialogo()
        val info = TextView(this)
        info.text = c.optString("descrizione") + "\nDa: " + c.optString("da") + " · Proposta € " + c.optString("proposta_t") +
            "\nResiduo della spesa € " + c.optString("residuo_t") +
            if (c.optBoolean("chiusa")) "\nLa spesa è chiusa: per confermare va riaperta dal desktop." else ""
        info.setPadding(4, 4, 4, 12)
        corpo.addView(info)
        val etImp = campoTesto(corpo, "Importo confermato (€)", c.optString("proposta_t").replace(".", ""), TIPO_DECIMALE, 12)
        val etNota = campoTesto(corpo, "Nota in caso di rifiuto (facoltativa)", "", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES, 200)
        OcBuilder(this)
            .setTitle("Proposta di quota")
            .setView(view)
            .setPositiveButton("Conferma") { _, _ ->
                poolInvia(
                    JSONObject()
                        .put("azione", "conferma")
                        .put("id_contrib", c.optString("id_contrib"))
                        .put("importo", etImp.text.toString().trim()),
                    "Quota confermata, rientro registrato"
                )
            }
            .setNegativeButton("Rifiuta") { _, _ ->
                poolInvia(
                    JSONObject()
                        .put("azione", "rifiuta")
                        .put("id_contrib", c.optString("id_contrib"))
                        .put("nota", etNota.text.toString().trim()),
                    "Quota rifiutata"
                )
            }
            .setNeutralButton("Chiudi", null)
            .show()
    }

    private fun renderCheckout() {
        val nomiVista = listOf("Mancanti del mese", "Controllo mensile", "Annuali")
        intestaLista("🔁 Checkout", "🔀  Vista: " + nomiVista[ckVista], "⚙️  Avviso fine mese")
        val cont = vista<LinearLayout>(R.id.listaGen)
        val verde = ContextCompat.getColor(this, R.color.oc_green)
        val rosso = ContextCompat.getColor(this, R.color.oc_red)
        val oro = ContextCompat.getColor(this, R.color.oc_gold)
        val arancio = ContextCompat.getColor(this, R.color.oc_orange)
        val voci = Repo.voci()
        val mesi3 = listOf("Gen", "Feb", "Mar", "Apr", "Mag", "Giu", "Lug", "Ago", "Set", "Ott", "Nov", "Dic")
        fun segnoNum(x: Double) = (if (x >= 0) "+" else "−") + euro(abs(x))

        when (ckVista) {
            0 -> {
                sezione(cont, "CATEGORIE ABITUALI NON ANCORA REGISTRATE")
                val man = Checkout.mancanti(voci)
                if (man.isEmpty()) {
                    vuoto(cont, "Nessuna categoria mancante: compaiono quando sono state registrate in almeno 4 giorni diversi degli ultimi 12 mesi e non ancora in questo mese.")
                }
                for (cat in man) {
                    val d = Checkout.dettaglio(cat, voci)
                    val u = d.ultima
                    val val_ = if (u != null) (if (u.tipo == "Entrata") "+" else "−") + euro(u.importo) else "N/D"
                    riga(
                        cont, cat,
                        (u?.let { "Ultima: " + it.data.format(FMT_DATA) + " · " } ?: "") + d.n + " volte/anno",
                        val_, if (u?.tipo == "Entrata") verde else rosso
                    ) {
                        val righe = ArrayList<Pair<String, String>>()
                        if (u != null) righe.add("Ultima" to u.data.format(FMT_DATA))
                        righe.add("Occorrenze" to d.n.toString() + " negli ultimi 12 mesi")
                        if (d.n > 0) {
                            righe.add("Totale" to euro(d.totale))
                            righe.add("Media" to euro(d.totale / d.n))
                            righe.add("Min / Max" to euro(d.min) + " / " + euro(d.max))
                        }
                        d.ogniGiorni?.let { righe.add("Ogni" to "~$it giorni") }
                        dialogSchedaCk(cat, righe, cat, u?.importo ?: 0.0)
                    }
                }
            }
            1 -> {
                val anni = (voci.map { it.data.year } + LocalDate.now().year).distinct().sortedDescending()
                riga(cont, "Anno di analisi", "Tocca per cambiare", ckAnno.toString(), oro) {
                    val (view, corpo) = corpoDialogo()
                    val lista = anni.map { it.toString() }
                    var scelto = ckAnno
                    campoMenu(corpo, "Anno", lista, ckAnno.toString()) { scelto = anni[it] }
                    OcBuilder(this)
                        .setTitle("Anno di analisi")
                        .setView(view)
                        .setNegativeButton("Annulla", null)
                        .setPositiveButton("Applica") { _, _ -> ckAnno = scelto; renderCheckout() }
                        .show()
                }
                val righe = Checkout.mensili(voci, ckAnno)
                if (righe.isEmpty()) vuoto(cont, "Nessuna categoria ricorrente trovata.")
                val oggi = LocalDate.now()
                val lettere = "GFMAMGLASOND"
                for (r in righe) {
                    val colore = when (r.cadenza) {
                        "Mensile", "Mensile Reg." -> verde
                        "Bimestrale" -> oro
                        "Trimestrale" -> arancio
                        else -> rosso
                    }
                    riga(
                        cont, r.cat, r.cadenza + " · media " + segnoNum(r.media),
                        segnoNum(r.totale), colore
                    ) {
                        val d = ArrayList<Pair<String, String>>()
                        d.add("Anno" to ckAnno.toString())
                        d.add("Movimenti" to r.n.toString())
                        d.add("Mesi presenti" to r.mesi.count { it }.toString() + "/12")
                        if (r.mancano.isNotEmpty()) d.add("Mancano" to r.mancano.joinToString(", ") { mesi3[it - 1] })
                        if (r.uscite != 0.0) d.add("Uscite" to euro(r.uscite))
                        if (r.entrate != 0.0) d.add("Entrate" to euro(r.entrate))
                        r.meseMax?.let { d.add("Mese max" to mesi3[it.first - 1] + "  " + euro(it.second)) }
                        r.meseMin?.let { d.add("Mese min" to mesi3[it.first - 1] + "  " + euro(it.second)) }
                        dialogSchedaCk(r.cat, d, r.cat, abs(r.media))
                    }
                    val colTxt = ContextCompat.getColor(this, R.color.oc_text_mid)
                    val griglia = LinearLayout(this)
                    griglia.orientation = LinearLayout.VERTICAL
                    griglia.setPadding(28, 0, 8, 10)
                    val riga1 = LinearLayout(this)
                    val riga2 = LinearLayout(this)
                    for (i in 0 until 12) {
                        val cur = ckAnno == oggi.year && i + 1 == oggi.monthValue
                        val sim = if (r.mesi[i]) "✔" else "✖"
                        fun cella(testo: String): TextView {
                            val c = TextView(this)
                            c.text = testo
                            c.textSize = 11f
                            c.gravity = android.view.Gravity.CENTER
                            c.setTextColor(colTxt)
                            c.maxLines = 1
                            c.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                            return c
                        }
                        riga1.addView(cella(if (cur) "[" + lettere[i] + "]" else lettere[i].toString()))
                        riga2.addView(cella(if (cur) "[$sim]" else sim))
                    }
                    griglia.addView(riga1)
                    griglia.addView(riga2)
                    cont.addView(griglia)
                }
                vuoto(cont, "✔ presente · ✖ assente · [ ] mese corrente. Tocca una categoria per il dettaglio e per inserire il movimento.")
            }
            else -> {
                sezione(cont, "VOCI CHE COMPAIONO OGNI ANNO (≥ 2 anni su 4) — CONFRONTO CON " + LocalDate.now().year)
                val righe = Checkout.annuali(voci)
                if (righe.isEmpty()) vuoto(cont, "Nessuna ricorrenza annuale rilevata.")
                for (r in righe) {
                    val sim = when (r.stato) { 0 -> "✔"; 1 -> "—"; else -> "✖" }
                    val col = when (r.stato) { 0 -> verde; 1 -> arancio; else -> rosso }
                    riga(
                        cont, r.cat,
                        "Mese atteso " + mesi3[r.mese - 1] + " · " + r.nAnni + " anni · media " + segnoNum(r.media),
                        sim, col
                    ) {
                        val d = ArrayList<Pair<String, String>>()
                        d.add("Mese atteso" to mesi3[r.mese - 1])
                        d.add("Anni" to r.anni.joinToString(", "))
                        if (r.occ > 0) {
                            d.add("Occorrenze" to r.occ.toString())
                            d.add("Min / Max" to euro(r.min) + " / " + euro(r.max))
                        }
                        d.add(LocalDate.now().year.toString() to when (r.stato) { 0 -> "registrata"; 1 -> "non ancora (mese futuro)"; else -> "mancante" })
                        dialogSchedaCk(r.cat, d, r.cat, abs(r.media))
                    }
                }
            }
        }
    }

    private fun dialogSchedaCk(titolo: String, righe: List<Pair<String, String>>, cat: String, importo: Double) {
        val reale = Checkout.nomeReale(cat)
        OcBuilder(this)
            .setTitle(titolo)
            .setMessage(righe.joinToString("\n") { it.first + ": " + it.second })
            .setNegativeButton("Chiudi", null)
            .setPositiveButton("Inserisci") { _, _ ->
                dialogVoce(null, null, reale, importo, Repo.tipoCategoria(reale))
            }
            .show()
    }

    private fun dialogAvvisoCk() {
        val (view, corpo) = corpoDialogo()
        val chk = MaterialCheckBox(this)
        chk.text = "Avviso di fine mese attivo"
        chk.isChecked = Repo.checkMese
        corpo.addView(chk)
        val et = campoTesto(corpo, "Giorni prima della fine del mese", Repo.sogliaGiorniRic.toString(), InputType.TYPE_CLASS_NUMBER, 2)
        OcBuilder(this)
            .setTitle("Avviso fine mese")
            .setMessage("Negli ultimi giorni del mese l'app segnala le categorie che registri di solito e non hai ancora inserito.")
            .setView(view)
            .setNegativeButton("Annulla", null)
            .setNeutralButton("Riattiva") { _, _ ->
                Repo.setFmDismiss(LocalDate.of(2000, 1, 1))
                ultimoControlloFm = 0L
                aviso("Avviso riattivato")
            }
            .setPositiveButton("Salva") { _, _ ->
                Repo.setCheckMese(chk.isChecked)
                Repo.setSogliaGiorniRic(et.text.toString().toIntOrNull() ?: 5)
            }
            .show()
    }

    private fun nomiMese(): List<String> =
        listOf("Tutti", "Gennaio", "Febbraio", "Marzo", "Aprile", "Maggio", "Giugno", "Luglio", "Agosto", "Settembre", "Ottobre", "Novembre", "Dicembre")

    private fun renderFairshare() {
        intestaLista("🤝 FairShare", "➕  Nuovo partecipante", "🔎  Filtri")
        val cont = vista<LinearLayout>(R.id.listaGen)
        val verde = ContextCompat.getColor(this, R.color.oc_green)
        val rosso = ContextCompat.getColor(this, R.color.oc_red)
        val oro = ContextCompat.getColor(this, R.color.oc_gold)
        val blu = ContextCompat.getColor(this, R.color.oc_blue)
        val debiti = FairShare.sincronizza()
        val parts = FairShare.partecipanti()

        val mesiN = nomiMese()
        val filtri = "Anno " + fsAnno + " · " + mesiN[fsMese] + " · " + fsParte + " · " + fsCat + " · " + fsStato
        riga(cont, "Filtri attivi", filtri, "🔎", null) { dialogFiltriFs() }

        val r = FairShare.riepiloga(debiti, fsAnno, fsMese, fsParte, fsCat, fsStato)

        riga(cont, "📄 Esporta estratto in PDF", "Dare e Avere con i filtri attivi", "PDF", oro) {
            if (r.righe.isEmpty()) aviso("Nessuna spesa da esportare con questi filtri")
            else stkPdf({ FairSharePdf.estratto(this, r, fsAnno, mesiN[fsMese], fsParte, fsCat, fsStato) }, "Estratto FairShare")
        }

        if (debiti.isEmpty()) {
            vuoto(
                cont,
                "Nessuna spesa FairShare. Aggiungi i partecipanti e, nel movimento, scegli il partecipante: " +
                    "la descrizione riceve il tag PER· / CNT· e la spesa viene divisa in quote. " +
                    "Una Entrata con lo stesso tag, categoria e importo della quota salda il debito."
            )
        }

        sezione(cont, "RIEPILOGO PER PERSONA")
        if (r.dovuto.isEmpty()) vuoto(cont, "Nessun dato per i filtri selezionati.")
        for (nome in r.dovuto.keys.sorted()) {
            val dov = r.dovuto.getValue(nome)
            val ver = r.versato.getValue(nome)
            val deb = r.debito.getValue(nome)
            val saldato = deb < 0.01
            riga(
                cont, nome,
                "Dovuto " + euro(dov) + " · Versato " + euro(ver),
                if (saldato) "Saldato" else "Deve " + euro(deb),
                if (saldato) verde else rosso, null
            )
        }
        if (r.dovuto.isNotEmpty()) {
            val td = r.dovuto.values.sum()
            val tv = r.versato.values.sum()
            val tb = r.debito.values.sum()
            riga(cont, "TOTALE", "Dovuto " + euro(td) + " · Versato " + euro(tv), euro(tb), if (tb < 0.01) verde else rosso, null)
        }

        if (r.chiDeve.isNotEmpty()) {
            sezione(cont, "CHI DEVE A CHI (debiti compensati)")
            for ((deb, cred, imp) in r.chiDeve) riga(cont, "$deb → $cred", "", euro(imp), rosso, null)
        }

        sezione(cont, "SPESE (tocca per segnare pagato / annullare)")
        if (r.righe.isEmpty()) vuoto(cont, "Nessuna spesa con i filtri selezionati.")
        for (x in r.righe.take(150)) {
            val d = x.deb
            val chiuso = d.stato == "chiuso"
            val sotto = d.data.format(FMT_DATA) + " · " + d.categoria + " · " + x.nome +
                " · totale " + euro(d.importo) +
                (if (x.pagato && x.dataPag.isNotEmpty()) " · pagato il " + x.dataPag else "")
            riga(
                cont, (if (x.pagato) "✅ " else "🔴 ") + d.descrizione.ifBlank { d.categoria }, sotto,
                euro(x.quota), if (x.pagato) (if (chiuso) blu else verde) else rosso
            ) { dialogPagamentoFs(d, x.nome, x.pagato) }
        }
        if (r.righe.size > 150) vuoto(cont, "…e altre " + (r.righe.size - 150) + " righe: restringi i filtri.")

        sezione(cont, "PARTECIPANTI (tocca per modificare)")
        val gestore = FairShare.nomeGestore()
        riga(
            cont, "Io: $gestore",
            if (FairShare.gestorePartecipa()) "Partecipo alle spese" else "Non partecipo alle spese",
            "⚙️", null
        ) { dialogGestoreFs() }
        if (parts.isEmpty()) vuoto(cont, "Nessun partecipante: usa «Nuovo partecipante».")
        for (p in parts) {
            val sotto = when (p.tipo) {
                "contenitore" -> "Contenitore · soci: " + p.soci.joinToString(", ")
                "personale" -> "Spese personali"
                else -> "Persona" + (p.perc?.let { " · " + String.format(Locale.ITALY, "%.2f", it) + "%" } ?: "")
            }
            riga(cont, FairShare.icona(p.tipo) + " " + p.nome, sotto, "✏️", oro) { dialogPartecipante(p) }
        }
    }

    private fun dialogFiltriFs() {
        val (view, corpo) = corpoDialogo()
        val oggi = LocalDate.now().year
        val anni = (Repo.voci().map { it.data.year } + oggi).distinct().sortedDescending().map { it.toString() }
        val listaAnni = listOf("Tutti") + anni
        val mesiN = nomiMese()
        val nomi = FairShare.partecipanti().map { it.nome } + FairShare.nomeGestore()
        val listaParti = listOf("Tutti") + nomi.distinct().sortedBy { it.lowercase() }
        val listaCat = listOf("Tutte") + Repo.categorie().sortedBy { it.lowercase() }
        val listaStato = listOf("Tutti", "Aperto", "Chiuso")
        var anno = fsAnno
        var mese = fsMese
        var parte = fsParte
        var cat = fsCat
        var stato = fsStato
        campoMenu(corpo, "Anno", listaAnni, anno) { anno = listaAnni[it] }
        campoMenu(corpo, "Mese", mesiN, mesiN[mese]) { mese = it }
        campoMenu(corpo, "Partecipante", listaParti, parte) { parte = listaParti[it] }
        campoMenu(corpo, "Categoria", listaCat, cat) { cat = listaCat[it] }
        campoMenu(corpo, "Stato", listaStato, stato) { stato = listaStato[it] }
        OcBuilder(this)
            .setTitle("Filtri FairShare")
            .setView(view)
            .setNegativeButton("Annulla", null)
            .setPositiveButton("Applica") { _, _ ->
                fsAnno = anno; fsMese = mese; fsParte = parte; fsCat = cat; fsStato = stato
                renderFairshare()
            }
            .show()
    }

    private fun dialogPagamentoFs(d: FairShare.Debito, nome: String, pagato: Boolean) {
        OcBuilder(this)
            .setTitle(if (pagato) "Annullare il pagamento?" else "Segnare come pagato?")
            .setMessage(nome + " · " + euro(d.quotaDi(nome)) + "\n" + d.descrizione.ifBlank { d.categoria } + " del " + d.data.format(FMT_DATA))
            .setNegativeButton(if (pagato) "No" else "Annulla", null)
            .setPositiveButton(if (pagato) "Sì, annulla" else "Segna pagato") { _, _ ->
                FairShare.segnaPagato(d.idSpesa, nome)
                renderFairshare()
            }
            .show()
    }

    private fun dialogGestoreFs() {
        val (view, corpo) = corpoDialogo()
        val etNome = campoTesto(corpo, "Il mio nome in FairShare", Repo.nomeGestore, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS, 40)
        val chk = MaterialCheckBox(this)
        chk.text = "Partecipo anch'io alle spese"
        chk.isChecked = FairShare.gestorePartecipa()
        corpo.addView(chk)
        val dlg = OcBuilder(this)
            .setTitle("Io in FairShare")
            .setMessage("Usa lo stesso nome del desktop (il nome del profilo), così i tag PER· coincidono.")
            .setView(view)
            .setPositiveButton("Salva", null)
            .setNegativeButton("Annulla", null)
            .create()
        dlg.show()
        dlg.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val n = etNome.text.toString().trim()
            if (n.isEmpty()) { etNome.error = "Inserisci un nome"; return@setOnClickListener }
            if (n.contains('·')) { etNome.error = "Non può contenere ·"; return@setOnClickListener }
            if (FairShare.partecipanti().any { it.nome == n }) { etNome.error = "Nome già usato da un partecipante"; return@setOnClickListener }
            val vecchio = FairShare.nomeGestore()
            if (n != vecchio && Repo.nomeGestore.isNotBlank()) {
                val alt = FairShare.partecipanti().map { it.nome } + vecchio + n
                Repo.fsRinomina(vecchio, n, alt)
            }
            Repo.setNomeGestore(n)
            FairShare.setGestorePartecipa(chk.isChecked)
            dlg.dismiss()
            renderFairshare()
        }
    }

    private fun dialogPartecipante(p: FairShare.Part?) {
        val (view, corpo) = corpoDialogo()
        val etNome = campoTesto(corpo, "Nome", p?.nome ?: "", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS, 40)
        val tipi = listOf("persona", "contenitore", "personale")
        val etichetteTipo = listOf("Persona (PER·)", "Contenitore di soci (CNT·)", "Spese personali (CTP·)")
        var tipo = p?.tipo ?: "persona"
        val etPerc = campoTesto(corpo, "Percentuale % (facoltativa, solo persone)", p?.perc?.let { String.format(Locale.ITALY, "%.2f", it) } ?: "", TIPO_DECIMALE, 6)
        val boxSoci = LinearLayout(this)
        boxSoci.orientation = LinearLayout.VERTICAL
        val persone = (FairShare.partecipanti().filter { it.tipo == "persona" && it.nome != p?.nome }.map { it.nome } + FairShare.nomeGestore()).distinct()
        val checks = ArrayList<Pair<String, MaterialCheckBox>>()
        val lblSoci = TextView(this)
        lblSoci.text = "Soci del contenitore:"
        lblSoci.setPadding(4, 12, 4, 4)
        boxSoci.addView(lblSoci)
        for (n in persone) {
            val c = MaterialCheckBox(this)
            c.text = n
            c.isChecked = p?.soci?.contains(n) == true
            boxSoci.addView(c)
            checks.add(n to c)
        }
        fun aggiornaTipo() {
            (etPerc.parent.parent as View).visibility = if (tipo == "persona") View.VISIBLE else View.GONE
            boxSoci.visibility = if (tipo == "contenitore") View.VISIBLE else View.GONE
        }
        val menuTipo = campoMenu(corpo, "Tipo", etichetteTipo, etichetteTipo[tipi.indexOf(tipo).coerceAtLeast(0)]) {
            tipo = tipi[it]
            aggiornaTipo()
        }

        corpo.removeView(menuTipo)
        corpo.addView(menuTipo, 1)
        corpo.addView(boxSoci)
        aggiornaTipo()

        val b = OcBuilder(this)
            .setTitle(if (p == null) "Nuovo partecipante" else "Modifica partecipante")
            .setView(view)
            .setPositiveButton("Salva", null)
            .setNegativeButton("Annulla", null)
        if (p != null) b.setNeutralButton("Elimina", null)
        val dlg = b.create()
        dlg.show()
        dlg.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val t = etPerc.text.toString().trim()
            val perc = if (tipo == "persona" && t.isNotEmpty()) num(t) else null
            if (tipo == "persona" && t.isNotEmpty() && perc == null) {
                etPerc.error = "Valore non valido"
                return@setOnClickListener
            }
            val soci = checks.filter { it.second.isChecked }.map { it.first }
            val err = FairShare.salvaPartecipante(p?.nome, etNome.text.toString(), tipo, soci, perc)
            if (err != null) {
                etNome.error = err
                return@setOnClickListener
            }
            dlg.dismiss()
            refresh()
        }
        if (p != null) {
            dlg.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                OcBuilder(this)
                    .setTitle("Eliminare " + p.nome + "?")
                    .setMessage("I tag già scritti nelle descrizioni dei movimenti restano com'erano.")
                    .setNegativeButton("Annulla", null)
                    .setPositiveButton("Elimina") { _, _ ->
                        FairShare.eliminaPartecipante(p.nome)
                        dlg.dismiss()
                        refresh()
                    }
                    .show()
            }
        }
    }

    private val MESI_BREVI = listOf("Gen", "Feb", "Mar", "Apr", "Mag", "Giu", "Lug", "Ago", "Set", "Ott", "Nov", "Dic")
    private val MESI_ESTESI = listOf(
        "Gennaio", "Febbraio", "Marzo", "Aprile", "Maggio", "Giugno",
        "Luglio", "Agosto", "Settembre", "Ottobre", "Novembre", "Dicembre"
    )

    private fun segnoEuro(x: Double) = (if (x >= 0) "+" else "−") + euro(Math.abs(x))

    private fun barraFondo(cont: LinearLayout, titolo: String, valore: Double, traguardo: Double) {
        val pct = if (traguardo > 0) (valore / traguardo).coerceIn(0.0, 1.0) else 0.0
        val colore = ContextCompat.getColor(
            this, when { pct >= 1.0 -> R.color.oc_green; pct >= 0.5 -> R.color.oc_orange; else -> R.color.oc_red }
        )
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setBackgroundResource(R.drawable.bg_tile)
        box.setPadding(24, 20, 24, 20)
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.bottomMargin = 12
        box.layoutParams = lp
        val tit = TextView(this)
        tit.text = titolo
        tit.setTypeface(null, Typeface.BOLD)
        box.addView(tit)
        val pb = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal)
        pb.max = 100
        pb.progress = (pct * 100).toInt()
        pb.progressTintList = ColorStateList.valueOf(colore)
        val plp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        plp.topMargin = 10
        pb.layoutParams = plp
        box.addView(pb)
        val det = TextView(this)
        det.text = euro(valore) + " / " + (if (traguardo > 0) euro(traguardo) else "—") + "  (" +
            String.format(Locale.ITALY, "%.1f", pct * 100) + "%)"
        det.textSize = 12f
        det.setTextColor(ContextCompat.getColor(this, R.color.oc_text_mid))
        det.setPadding(0, 8, 0, 0)
        box.addView(det)
        cont.addView(box)
    }

    private class InfoPiano(
        val p: Piano, val mesi: List<YearMonth>, val quote: List<Double>,
        val accantonato: Double, val quotaOra: Double?, val importoLive: Double?,
    )

    private fun infoPiano(p: Piano, voci: Map<String, Voce>): InfoPiano {
        val ymOggi = YearMonth.now()
        val mesi = Repo.mesiCoperti(p)
        val quote = Repo.quoteMensili(p.importoTotale, mesi.size)
        var acc = 0.0
        var ora: Double? = null
        mesi.forEachIndexed { i, ym ->
            if (ym.isBefore(ymOggi)) acc += quote[i] else if (ym == ymOggi) ora = quote[i]
        }
        return InfoPiano(p, mesi, quote, Math.round(acc * 100.0) / 100.0, ora, voci[p.idSpesa]?.importo)
    }

    private fun renderPianifica() {
        intestaLista("📌 Pianifica", "➕  Nuovo piano")
        val cont = vista<LinearLayout>(R.id.listaGen)
        val verde = ContextCompat.getColor(this, R.color.oc_green)
        val rosso = ContextCompat.getColor(this, R.color.oc_red)
        val voci = Repo.voci().associateBy { it.id }
        val piani = Repo.piani()
        if (piani.isEmpty()) {
            vuoto(
                cont,
                "Nessun piano. Con \"Nuovo piano\" scegli una spesa futura e la dividi in quote mensili da mettere da parte nei mesi prima."
            )
            return
        }
        val info = piani.map { infoPiano(it, voci) }
        val totImp = info.sumOf { it.p.importoTotale }
        val totAcc = info.sumOf { it.accantonato }
        val totOra = info.sumOf { it.quotaOra ?: 0.0 }

        sezioneForte(cont, "RIEPILOGO")
        riga(cont, "Piani attivi", "importo totale " + euro(totImp), info.size.toString(), null, null)
        riga(cont, "Da accantonare questo mese", "", euro(totOra), rosso, null)
        riga(cont, "Già accantonato", "", euro(totAcc), verde, null)
        riga(cont, "Residuo", "", euro(totImp - totAcc), null, null)

        sezioneForte(cont, "PIANI")
        for (i in info) {
            val n = i.mesi.size
            val trascorsi = i.mesi.count { it.isBefore(YearMonth.now()) }
            val pct = if (i.p.importoTotale > 0) i.accantonato / i.p.importoTotale * 100.0 else 0.0
            val note = listOfNotNull(
                "$trascorsi/$n mesi",
                "quota " + euro(i.quote.firstOrNull() ?: 0.0) + "/mese",
                String.format(Locale.ITALY, "%.0f%%", pct) + " accantonato",
                "scade " + i.p.scadenza.format(FMT_DATA),
                if (i.importoLive == null) "⚠️ spesa collegata non trovata" else null,
                if (i.importoLive != null && Math.abs(i.importoLive - i.p.importoTotale) >= 0.01)
                    "⚠️ importo ora " + euro(i.importoLive) else null,
            ).joinToString(" · ")
            riga(cont, i.p.categoria.ifBlank { i.p.nome }, i.p.descrizione.ifBlank { "" } + (if (i.p.descrizione.isBlank()) "" else "\n") + note, euro(i.p.importoTotale), null) {
                dialogPiano(i)
            }
        }

        sezione(cont, "PROSSIMI 12 MESI")
        var ym = YearMonth.now()
        repeat(12) {
            val tot = piani.sumOf { Repo.quotaMese(it, ym) ?: 0.0 }
            riga(
                cont, maiuscola(ym.format(FMT_MESE)), "", if (tot > 0) euro(tot) else "—",
                if (tot > 0) rosso else null, null
            )
            ym = ym.plusMonths(1)
        }
    }

    private fun dialogPiano(i: InfoPiano) {
        val oggi = YearMonth.now()
        val righe = i.mesi.mapIndexed { k, ym ->
            val stato = when {
                ym.isBefore(oggi) -> "✓ accantonata"
                ym == oggi -> "● questo mese"
                else -> "da fare"
            }
            maiuscola(ym.format(FMT_MESE)) + "  ·  " + euro(i.quote[k]) + "  ·  " + stato
        }
        val variato = i.importoLive != null && Math.abs(i.importoLive - i.p.importoTotale) >= 0.01
        val msg = "Totale " + euro(i.p.importoTotale) + " · scade " + i.p.scadenza.format(FMT_DATA) +
            "\naccantonato " + euro(i.accantonato) + " · residuo " + euro(i.p.importoTotale - i.accantonato) +
            "\n\n" + righe.joinToString("\n")
        val b = OcBuilder(this)
            .setTitle(i.p.categoria.ifBlank { i.p.nome })
            .setMessage(msg)
            .setPositiveButton("Chiudi", null)
            .setNegativeButton("Elimina piano") { _, _ ->
                OcBuilder(this)
                    .setTitle("Eliminare il piano?")
                    .setMessage("La spesa collegata non viene toccata.")
                    .setNegativeButton("Annulla", null)
                    .setPositiveButton("Elimina") { _, _ ->
                        Repo.eliminaPiano(i.p.id)
                        refresh()
                        aviso("Piano eliminato")
                    }
                    .show()
            }
        if (variato) {
            b.setNeutralButton("Ricalcola") { _, _ ->
                if (Repo.ricalcolaPiano(i.p.id)) { refresh(); aviso("Piano ricalcolato") }
                else aviso("Spesa collegata non trovata")
            }
        }
        b.show()
    }

    private fun dialogNuovoPiano() {
        val oggi = LocalDate.now()
        val gia = Repo.piani().map { it.idSpesa }.toSet()
        val cand = Repo.voci()
            .filter { it.tipo == "Uscita" && it.data.isAfter(oggi) && it.id !in gia && Repo.mesiMassimi(it.data) >= 1 }
            .sortedWith(compareBy({ it.data }, { it.categoria }))
            .take(300)
        if (cand.isEmpty()) {
            aviso("Nessuna spesa futura accantonabile: serve un'uscita con data dal mese prossimo in poi")
            return
        }
        val etichette = cand.map {
            it.data.format(FMT_DATA) + " · " + it.categoria +
                (if (it.descrizione.isBlank()) "" else " · " + it.descrizione.trim()) + " · " + euro(it.importo)
        }.toTypedArray()
        OcBuilder(this)
            .setTitle("Quale spesa accantonare?")
            .setItems(etichette) { _, k -> dialogMesiPiano(cand[k]) }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun dialogMesiPiano(v: Voce) {
        val mm = Repo.mesiMassimi(v.data)
        val (view, corpo) = corpoDialogo()
        val etMesi = campoTesto(corpo, "In quanti mesi (1-$mm)", mm.toString(), InputType.TYPE_CLASS_NUMBER, 2)
        val chk = android.widget.CheckBox(this)
        chk.text = "Includi il mese corrente"
        chk.isChecked = true
        corpo.addView(chk)
        val dlg = OcBuilder(this)
            .setTitle(v.categoria + " · " + euro(v.importo))
            .setMessage("Scade il " + v.data.format(FMT_DATA) + ". La quota va messa da parte ogni mese prima.")
            .setView(view)
            .setPositiveButton("Crea piano", null)
            .setNegativeButton("Annulla", null)
            .create()
        dlg.show()
        dlg.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val n = etMesi.text.toString().trim().toIntOrNull()
            val massimo = if (chk.isChecked) mm else maxOf(1, mm - 1)
            if (n == null || n < 1 || n > massimo) {
                etMesi.error = "Da 1 a $massimo"
                return@setOnClickListener
            }
            if (!Repo.creaPiano(v.id, n)) {
                aviso("Impossibile creare il piano (già presente o data non valida)")
                return@setOnClickListener
            }
            dlg.dismiss()
            refresh()
            aviso("Piano creato")
        }
    }

    private fun dialogSimulatore() {
        val (view, corpo) = corpoDialogo()
        fun num0(x: Double) = if (x > 0) String.format(Locale.ITALY, "%.2f", x) else ""
        val etOb = campoTesto(corpo, "Obiettivo da raggiungere (€)", num0(Fondo.obiettivoAnnuale()), TIPO_DECIMALE, 12)
        val etIn = campoTesto(corpo, "Capitale di partenza (€)", num0(Fondo.fondoAttuale()), TIPO_DECIMALE, 12)
        val etMe = campoTesto(corpo, "Versamento mensile (€)", "", TIPO_DECIMALE, 12)
        val etRe = campoTesto(corpo, "Rendimento annuo % (facoltativo)", num0(Fondo.tassoAnnuo()), TIPO_DECIMALE_NEG, 7)
        val etMesi = campoTesto(corpo, "Tempo in mesi (per sapere quanto versare)", "", InputType.TYPE_CLASS_NUMBER, 4)
        val tvRis = TextView(this)
        tvRis.setPadding(4, 16, 4, 0)
        tvRis.setTextColor(ContextCompat.getColor(this, R.color.oc_text))
        corpo.addView(tvRis)
        val dlg = OcBuilder(this)
            .setTitle("Simulatore risparmio")
            .setView(view)
            .setPositiveButton("Calcola", null)
            .setNegativeButton("Chiudi", null)
            .create()
        dlg.show()
        dlg.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val ob = importoOk(etOb.text.toString())
            val ini = importoOk(etIn.text.toString(), ammettiZero = true)
            val me = importoOk(etMe.text.toString(), ammettiZero = true)
            val re = importoOk(etRe.text.toString(), ammettiZero = true, negativo = true)
            val mesi = etMesi.text.toString().trim().toIntOrNull() ?: 0
            if (ob == null) { etOb.error = "Importo non valido"; return@setOnClickListener }
            if (ini == null) { etIn.error = "Importo non valido"; return@setOnClickListener }
            if (me == null) { etMe.error = "Importo non valido"; return@setOnClickListener }
            if (re == null) { etRe.error = "Valore non valido"; return@setOnClickListener }
            if (me <= 0 && mesi <= 0) {
                tvRis.text = "Inserisci il versamento mensile, oppure il tempo in mesi."
                return@setOnClickListener
            }
            val righe = ArrayList<String>()
            if (ini >= ob) righe.add("Hai già raggiunto l'obiettivo con il capitale di partenza.")
            else if (me > 0) {
                val s = Fondo.simula(ob, ini, me, re)
                if (s.mesi == null) {
                    righe.add("Con " + euro(me) + " al mese non arrivi all'obiettivo entro 100 anni.")
                } else {
                    val quando = YearMonth.now().plusMonths(s.mesi.toLong())
                    righe.add(
                        "Con " + euro(me) + " al mese arrivi a " + euro(ob) + " in " + s.mesi + " mesi (" +
                            (s.mesi / 12) + " anni e " + (s.mesi % 12) + " mesi), cioè a " +
                            maiuscola(quando.format(FMT_MESE)) + ".\nVersato: " + euro(s.versato) +
                            " · rendimento: " + euro(s.capitale - s.versato)
                    )
                }
            }
            if (mesi > 0 && ini < ob) {
                val v = Fondo.versamentoNecessario(ob, ini, mesi, re)
                righe.add("Per arrivarci in " + mesi + " mesi servono " + euro(v) + " al mese.")
            }
            tvRis.text = righe.joinToString("\n\n")
        }
    }

    private fun scansionaPdf() {
        val opz = com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(20)
            .setResultFormats(com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.RESULT_FORMAT_PDF)
            .setScannerMode(com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
        com.google.mlkit.vision.documentscanner.GmsDocumentScanning.getClient(opz)
            .getStartScanIntent(this)
            .addOnSuccessListener { sender ->
                scanPdf.launch(androidx.activity.result.IntentSenderRequest.Builder(sender).build())
            }
            .addOnFailureListener { e -> aviso("Scanner non disponibile: " + (e.message ?: "errore")) }
    }

    private fun salvaScansione(uri: android.net.Uri) {
        try {
            val dir = java.io.File(cacheDir, "pdf").apply { mkdirs() }
            val f = java.io.File(dir, "scansione_" + java.text.SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ITALY).format(java.util.Date()) + ".pdf")
            contentResolver.openInputStream(uri)?.use { i -> f.outputStream().use { o -> i.copyTo(o) } }
            PensionePdf.condividi(this, f, "Documento scansionato")
        } catch (e: Exception) {
            aviso("Impossibile salvare il PDF: " + (e.message ?: "errore"))
        }
    }

    private fun renderFondo() {
        intestaLista("💰 Fondo Risparmio", "➕  Nuovo obiettivo", "✏️  Imposta fondo")
        val cont = vista<LinearLayout>(R.id.listaGen)
        Fondo.errore?.let { vuoto(cont, "⚠️ $it") }
        val verde = ContextCompat.getColor(this, R.color.oc_green)
        val rosso = ContextCompat.getColor(this, R.color.oc_red)
        fun col(x: Double) = if (x >= 0) verde else rosso

        val fondo = Fondo.fondoAttuale()
        val annuale = Fondo.obiettivoAnnuale()
        val a = Fondo.analizza(Repo.voci(), annuale, Fondo.obiettivi())
        if (Fondo.errore != null && cont.childCount == 0) vuoto(cont, "⚠️ " + Fondo.errore)

        riga(cont, "🧮 Simulatore risparmio", "In quanto tempo arrivi a un obiettivo, o quanto versare al mese", "›", null) { dialogSimulatore() }
        sezioneForte(cont, "ULTIMI 365 GIORNI")
        riga(cont, "Entrate", "media " + euro(a.mediaE) + "/mese", euro(a.entrate365), verde, null)
        riga(cont, "Uscite", "media " + euro(a.mediaU) + "/mese", euro(a.uscite365), rosso, null)
        riga(cont, "Risparmio", "media " + euro(a.mediaR) + "/mese", euro(a.risparmio365), col(a.risparmio365), null)

        sezioneForte(cont, "FONDO DI EMERGENZA")
        riga(cont, "Fondo attuale", "Spesa media " + euro(a.mediaU) + "/mese", euro(fondo), null, { dialogImportiFondo() })
        val tasso = Fondo.tassoAnnuo()
        if (tasso > 0) {
            val annui = fondo * tasso / 100
            riga(
                cont, "Interessi stimati",
                "tasso " + String.format(Locale.ITALY, "%.2f", tasso) + "% annuo (lordo) · " + euro(annui / 12) + "/mese",
                euro(annui) + "/anno", verde, { dialogImportiFondo() }
            )
        }
        barraFondo(cont, "Obiettivo 3 mesi", fondo, a.fe3)
        barraFondo(cont, "Obiettivo 6 mesi", fondo, a.fe6)
        barraFondo(cont, "Obiettivo 12 mesi", fondo, a.fe12)

        sezioneForte(cont, "OBIETTIVO ANNUALE")
        if (annuale > 0) {
            barraFondo(cont, "Risparmio 365 gg", a.risparmio365, annuale)
            val diff = if (a.diffObj >= 0) "Sei in linea (+" + euro(a.diffObj) + "/mese)" else "Mancano " + euro(-a.diffObj) + "/mese"
            riga(cont, "Rata mensile necessaria", diff, euro(a.rataMensile), col(a.diffObj), null)
        } else {
            vuoto(cont, "Nessun obiettivo annuale: impostalo da \"Imposta fondo\".")
        }

        sezioneForte(cont, "OBIETTIVI DI RISPARMIO")
        if (a.obiettivi.isEmpty()) {
            vuoto(cont, "Nessun obiettivo impostato.")
        } else {
            a.obiettivi.forEachIndexed { i, s ->
                val tempo = s.mesiNecessari?.let { "$it mesi al ritmo attuale" } ?: "tempo N/D"
                val sotto = "entro " + s.ob.data + " (" + s.ob.mesi + " mesi) · rata " + euro(s.rata) + "/mese · " +
                    (if (s.ok) "sostenibile ✅" else "non sostenibile ⚠️") + " · " + tempo
                riga(cont, s.ob.nome, sotto, euro(s.ob.importo), if (s.ok) verde else rosso) {
                    OcBuilder(this)
                        .setTitle("Eliminare l'obiettivo?")
                        .setMessage(s.ob.nome + " · " + euro(s.ob.importo))
                        .setNegativeButton("Annulla", null)
                        .setPositiveButton("Elimina") { _, _ ->
                            val err = Fondo.eliminaObiettivo(i)
                            if (err != null) aviso(err) else { refresh(); aviso("Obiettivo eliminato") }
                        }
                        .show()
                }
            }
        }

        sezioneForte(cont, "PROIEZIONE " + LocalDate.now().year)
        riga(
            cont, "Risparmio atteso", "entrate " + euro(a.eProj) + " · uscite " + euro(a.uProj),
            euro(a.rProj), col(a.rProj), null
        )
        for (p in a.proiezione) {
            riga(
                cont, MESI_BREVI[p.ym.monthValue - 1] + if (p.futuro) "  📊 stimato" else "  ✓ reale",
                "entrate " + euro(p.e) + " · uscite " + euro(p.u), segnoEuro(p.r), col(p.r), null
            )
        }

        sezioneForte(cont, "ANDAMENTO ULTIMI 12 MESI")
        a.migliore?.let {
            riga(cont, "Mese migliore", MESI_ESTESI[it.ym.monthValue - 1] + " " + it.ym.year, segnoEuro(it.r), verde, null)
        }
        a.peggiore?.let {
            riga(cont, "Mese peggiore", MESI_ESTESI[it.ym.monthValue - 1] + " " + it.ym.year, segnoEuro(it.r), rosso, null)
        }
        var prec: Double? = null
        for (r in a.ultimi12) {
            val delta = when {
                prec == null -> "—"
                r.r >= prec -> "▲ +" + euro(r.r - prec)
                else -> "▼ −" + euro(prec - r.r)
            }
            riga(
                cont, MESI_ESTESI[r.ym.monthValue - 1] + " " + r.ym.year,
                "entrate " + euro(r.e) + " · uscite " + euro(r.u) + " · " + delta, segnoEuro(r.r), col(r.r), null
            )
            prec = r.r
        }
    }

    private fun dialogImportiFondo() {
        val (view, corpo) = corpoDialogo()
        fun testoNum(x: Double) = if (x > 0) String.format(Locale.ITALY, "%.2f", x) else ""
        val etFondo = campoTesto(corpo, "Fondo attuale (€)", testoNum(Fondo.fondoAttuale()), TIPO_DECIMALE, 12)
        val etAnn = campoTesto(corpo, "Obiettivo di risparmio annuale (€)", testoNum(Fondo.obiettivoAnnuale()), TIPO_DECIMALE, 12)
        val etTasso = campoTesto(corpo, "Tasso di interesse annuo del fondo (%)", testoNum(Fondo.tassoAnnuo()), TIPO_DECIMALE, 6)
        val dlg = OcBuilder(this)
            .setTitle("Fondo risparmio")
            .setMessage("Lascia vuoto per zero.")
            .setView(view)
            .setPositiveButton("Salva", null)
            .setNegativeButton("Annulla", null)
            .create()
        dlg.show()
        dlg.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val f = importoOk(etFondo.text.toString(), ammettiZero = true)
            val o = importoOk(etAnn.text.toString(), ammettiZero = true)
            if (f == null) { etFondo.error = "Importo non valido"; return@setOnClickListener }
            if (o == null) { etAnn.error = "Importo non valido"; return@setOnClickListener }
            val t = importoOk(etTasso.text.toString(), ammettiZero = true)
            if (t == null || t > 100.0) { etTasso.error = "Tasso non valido (0–100)"; return@setOnClickListener }
            val err = Fondo.salvaImporti(o, f, t)
            if (err != null) { aviso(err); return@setOnClickListener }
            dlg.dismiss()
            refresh()
            aviso("Fondo salvato")
        }
    }

    private fun dialogObiettivoFondo() {
        val (view, corpo) = corpoDialogo()
        val etNome = campoTesto(corpo, "Nome obiettivo", "", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES, 40)
        val etImp = campoTesto(corpo, "Importo da raggiungere (€)", "", TIPO_DECIMALE, 12)
        val etData = campoTesto(corpo, "Scadenza (mm/aaaa)", "", InputType.TYPE_CLASS_TEXT, 7)
        val dlg = OcBuilder(this)
            .setTitle("Nuovo obiettivo")
            .setView(view)
            .setPositiveButton("Aggiungi", null)
            .setNegativeButton("Annulla", null)
            .create()
        dlg.show()
        dlg.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val imp = importoOk(etImp.text.toString())
            if (imp == null) { etImp.error = "Importo non valido"; return@setOnClickListener }
            val err = Fondo.aggiungiObiettivo(etNome.text.toString(), imp, etData.text.toString())
            if (err != null) { aviso(err); return@setOnClickListener }
            dlg.dismiss()
            refresh()
            aviso("Obiettivo aggiunto")
        }
    }

    private val PEN_SEZ = listOf("Riepilogo", "Piano del fondo", "Versamenti", "Valorizzazioni", "Riscatti / Anticipazioni")

    private fun penChiave() = when (penVista) {
        2 -> Pensione.VERSAMENTI
        3 -> Pensione.VALORIZZAZIONI
        4 -> Pensione.RISCATTI
        else -> ""
    }

    private fun penAggiungi() {
        when (penVista) {
            1 -> dialogPianoPensione()
            3 -> dialogVocePensione(Pensione.VALORIZZAZIONI, null)
            4 -> dialogVocePensione(Pensione.RISCATTI, null)
            else -> dialogVocePensione(Pensione.VERSAMENTI, null)
        }
    }

    private fun penEtichettaAggiungi() =
        listOf("➕  Nuovo versamento", "✏️  Modifica piano", "➕  Nuovo versamento", "➕  Nuova valorizzazione", "➕  Nuovo riscatto")[penVista]

    private fun menuPdfPensione() {
        val voci = ArrayList<String>()
        val azioni = ArrayList<() -> Unit>()
        voci.add(penEtichettaAggiungi())
        azioni.add { penAggiungi() }
        voci.add("📥  Carica estratto del fondo (PDF)")
        azioni.add { apriPdfPensione.launch(arrayOf("application/pdf")) }
        if (penVista in 2..4) {
            voci.add("📤  Esporta PDF")
            azioni.add { esportaPensione() }
        }
        OcBuilder(this)
            .setTitle("Fondo Pensione")
            .setItems(voci.toTypedArray()) { _, i -> azioni[i]() }
            .setNegativeButton("Chiudi", null)
            .show()
    }

    private fun importaPensione(uri: android.net.Uri) {
        if (Gemini.chiave(this).isEmpty()) {
            dialogChiaveGemini { importaPensione(uri) }
            return
        }
        val file = try {
            val f = java.io.File(cacheDir, "pensione_import.pdf")
            contentResolver.openInputStream(uri)!!.use { i -> f.outputStream().use { o -> i.copyTo(o) } }
            f
        } catch (e: Exception) {
            aviso("File non leggibile: " + e.message)
            return
        }
        aviso("Lettura del PDF in corso…")
        Thread {
            val r = runCatching { Gemini.analizza(this, file, PensioneImport.prompt()) }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                r.onSuccess {
                    try {
                        dialogAnteprimaPensione(PensioneImport.leggi(it, Pensione.dati()))
                    } catch (_: Exception) {
                        aviso("Risposta IA non leggibile")
                    }
                }.onFailure { aviso(Gemini.messaggio(it)) }
            }
        }.start()
    }

    private fun dialogAnteprimaPensione(es: PensioneImport.Esito) {
        if (es.nuove.isEmpty()) {
            val extra = if (es.giaPresenti > 0) " (${es.giaPresenti} già presenti)" else ""
            aviso("Nessun dato nuovo da importare$extra")
            return
        }
        val sel = BooleanArray(es.nuove.size) { true }
        val titolo = "Importa " + es.nuove.size + (if (es.nuove.size == 1) " voce" else " voci") +
            (if (es.giaPresenti > 0) " · ${es.giaPresenti} già presenti" else "")
        OcBuilder(this)
            .setTitle(titolo)
            .setMultiChoiceItems(es.nuove.map { it.etichetta() }.toTypedArray(), sel) { _, i, on -> sel[i] = on }
            .setPositiveButton("Importa") { _, _ ->
                var ok = 0
                var errore: String? = null
                es.nuove.forEachIndexed { i, p ->
                    if (!sel[i]) return@forEachIndexed
                    val err = Pensione.salvaVoce(p.chiave, null, p.data, p.tipo, p.importo, p.motivo, "da PDF")
                    if (err == null) ok++ else errore = err
                }
                refresh()
                aviso(if (errore != null) "Importate $ok voci. Errore: $errore" else "Importate $ok voci")
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun esportaPensione() {
        val k = penChiave()
        if (k.isEmpty()) return
        val d = Pensione.dati()
        val titolo = when (k) {
            Pensione.VERSAMENTI -> "Versamenti fondo pensione"
            Pensione.VALORIZZAZIONI -> "Valorizzazioni fondo pensione"
            else -> "Riscatti fondo pensione"
        }
        val f = try {
            PensionePdf.crea(this, d, k)
        } catch (e: Exception) {
            aviso("Errore nella creazione del PDF: " + e.message)
            return
        }
        PensionePdf.condividi(this, f, titolo)
    }

    private fun renderPensione() {
        intestaLista("🏖️ Fondo Pensione", "", "🔀  Azioni")
        val cont = vista<LinearLayout>(R.id.listaGen)
        val d = Pensione.dati()
        Pensione.errore?.let { vuoto(cont, "⚠️ $it") }

        val scelta = pulsanteSel("Vista:  " + PEN_SEZ[penVista] + "   ▾", true) {
            OcBuilder(this)
                .setTitle("Scegli la vista")
                .setItems(PEN_SEZ.mapIndexed { i, t -> (if (i == penVista) "● " else "○ ") + t }.toTypedArray()) { _, i ->
                    penVista = i
                    renderPensione()
                }
                .setNegativeButton("Chiudi", null)
                .show()
        }
        cont.addView(scelta, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        when (penVista) {
            0 -> penRiepilogo(cont, d)
            1 -> penPiano(cont, d)
            else -> penElenco(cont, d, penChiave())
        }
    }

    private fun penRiepilogo(cont: LinearLayout, d: Pensione.Dati) {
        val dp = resources.displayMetrics.density
        val verde = ContextCompat.getColor(this, R.color.oc_green)
        val rosso = ContextCompat.getColor(this, R.color.oc_red)
        val arancio = ContextCompat.getColor(this, R.color.oc_orange)
        val blu = ContextCompat.getColor(this, R.color.oc_blue)
        val grigio = ContextCompat.getColor(this, R.color.oc_text_mid)
        val t = Pensione.totali(d)
        val p = d.piano
        val intest = listOf(p.nome, p.gestore, p.comparto).filter { it.isNotBlank() }.joinToString(" · ")
        if (intest.isNotEmpty()) vuoto(cont, intest)
        if (d.vers.isEmpty() && d.valor.isEmpty() && d.risc.isEmpty()) {
            vuoto(cont, "Nessun dato: aggiungi versamenti e valorizzazioni, oppure sincronizza col desktop.")
        }

        sezione(cont, "SITUAZIONE")
        riga(cont, "Totale versato", "", euro(t.versato), null, null)
        riga(cont, "Controvalore attuale", t.ultimaValor?.let { "al " + it.data } ?: "nessuna valorizzazione", euro(t.controvalore), null, null)
        riga(cont, "Totale riscattato", "", euro(t.riscattato), null, null)
        riga(
            cont, "Rendimento", String.format(Locale.ITALY, "%.1f", t.rendPct) + "%",
            segnoEuro(t.rendEur), if (t.rendEur >= 0) verde else rosso, null
        )

        sezione(cont, "VERSATO PER TIPO")
        for ((tipo, imp) in t.perTipo) riga(cont, tipo, "", euro(imp), null, null)
        if (t.deducibileAnno > Pensione.LIMITE_DEDUCIBILE_ANNUO) {
            riga(
                cont, "⚠ Superato il limite deducibile " + LocalDate.now().year,
                "Versamenti lavoratore + datore + volontari, contro un tetto di " + euro(Pensione.LIMITE_DEDUCIBILE_ANNUO),
                euro(t.deducibileAnno), arancio, null
            )
        }

        val tfr = Pensione.confrontoTfr(d)
        if (tfr.versato > 0) {
            sezione(cont, "CONFRONTO CON TFR IN AZIENDA (STIMA)")
            riga(cont, "TFR versato al fondo", "", euro(tfr.versato), null, null)
            riga(
                cont, "Rivalutazione teorica in azienda",
                "tasso stimato " + String.format(Locale.ITALY, "%.2f", tfr.tassoAnnuo * 100) + "%/anno",
                euro(tfr.teorico), null, null
            )
            riga(cont, "Differenza", "", segnoEuro(tfr.teorico - tfr.versato), null, null)
            vuoto(
                cont,
                "Stima approssimativa: 1,5% fisso + 75% dell'inflazione stimata (si imposta in Piano) su ogni versamento TFR " +
                    "dalla sua data a oggi. Non considera la fiscalità del fondo né quella del TFR in azienda."
            )
        }

        sezione(cont, "PROIEZIONE A SCADENZA")
        val pr = Pensione.proiezione(d)
        if (pr == null) {
            vuoto(cont, "Imposta una data di pensionamento futura nella sezione Piano per vedere la proiezione.")
        } else {
            riga(
                cont, "Capitale lordo stimato",
                "tra " + String.format(Locale.ITALY, "%.1f", pr.anniMancanti) + " anni · rendimento netto " +
                    String.format(Locale.ITALY, "%.2f", pr.tassoNetto * 100) + "%/anno",
                euro(pr.lordo), null, null
            )
            riga(
                cont, "Aliquota sulla prestazione",
                String.format(Locale.ITALY, "%.1f", pr.anniIscr) + " anni di iscrizione alla scadenza",
                String.format(Locale.ITALY, "%.1f", pr.aliquota * 100) + "%", null, null
            )
            riga(cont, "Capitale netto stimato", "dopo la tassazione della prestazione", euro(pr.netto), verde, null)
        }

        sezione(cont, "ANDAMENTO · VERSATO NETTO E CONTROVALORE")
        val punti = Pensione.andamento(d)
        if (punti.isEmpty()) {
            vuoto(cont, "Aggiungi almeno una valorizzazione per vedere il grafico.")
            return
        }
        val tl = TextView(this)
        val legTxt = "■ Versato netto     ■ Controvalore"
        val sp = android.text.SpannableString(legTxt)
        sp.setSpan(android.text.style.ForegroundColorSpan(blu), 0, 1, 0)
        val k2 = legTxt.lastIndexOf('■')
        sp.setSpan(android.text.style.ForegroundColorSpan(verde), k2, k2 + 1, 0)
        tl.text = sp
        tl.textSize = 12f
        tl.setPadding(4, (4 * dp).toInt(), 4, (4 * dp).toInt())
        cont.addView(tl)
        val g = GraficoView(this)
        g.imposta(
            GraficoView.Modo.LINEA,
            punti.map { String.format(Locale.ITALY, "%02d/%02d", it.data.monthValue, it.data.year % 100) },
            punti.map { it.versato }, punti.map { it.controvalore }, blu, verde, grigio,
            ContextCompat.getColor(this, R.color.oc_border), grigio,
            ContextCompat.getColor(this, R.color.oc_surface),
        )
        val det = TextView(this)
        det.textSize = 13f
        det.setPadding(8, (6 * dp).toInt(), 8, (6 * dp).toInt())
        det.text = "Tocca il grafico per vedere i dettagli."
        det.setTextColor(grigio)
        det.minLines = 2
        g.alSelezionare = { i ->
            val x = punti[i]
            det.text = Pensione.fmt(x.data) + "\nVersato netto " + euro(x.versato) + " · Controvalore " + euro(x.controvalore)
            det.setTextColor(ContextCompat.getColor(this, R.color.oc_text))
        }
        cont.addView(g, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (190 * dp).toInt()))
        cont.addView(det)
    }

    private fun penPiano(cont: LinearLayout, d: Pensione.Dati) {
        val p = d.piano
        fun v(s: String) = s.ifBlank { "—" }
        fun n(x: Double, u: String) = String.format(Locale.ITALY, "%.2f", x) + u
        val apri = { dialogPianoPensione() }
        sezione(cont, "DATI DEL PIANO")
        riga(cont, "Nome fondo", "", v(p.nome), null, apri)
        riga(cont, "Gestore", "", v(p.gestore), null, apri)
        riga(cont, "Comparto", "", v(p.comparto), null, apri)
        riga(cont, "Tipo piano", "", p.tipo, null, apri)
        riga(cont, "Data adesione", "", v(p.adesione), null, apri)
        riga(cont, "Inflazione media stimata", "usata per il confronto con la rivalutazione del TFR", n(p.inflazione, "%"), null, apri)
        riga(cont, "Note", "", v(p.note), null, apri)
        sezione(cont, "PROIEZIONE A SCADENZA E COSTI")
        riga(cont, "Data prevista pensionamento", "", v(p.dataPensione), null, apri)
        riga(cont, "Versamento annuo stimato", "", euro(p.versAnnuo), null, apri)
        riga(cont, "Rendimento atteso", "all'anno", n(p.rendAtteso, "%"), null, apri)
        riga(cont, "Costo di gestione (ISC)", "all'anno; riduce il rendimento atteso", n(p.costoGestione, "%"), null, apri)
    }

    private fun penElenco(cont: LinearLayout, d: Pensione.Dati, chiave: String) {
        val l = Pensione.ordinate(
            when (chiave) {
                Pensione.VERSAMENTI -> d.vers
                Pensione.VALORIZZAZIONI -> d.valor
                else -> d.risc
            }
        )
        if (l.isEmpty()) {
            vuoto(cont, "Nessuna voce. Tocca il pulsante in alto per aggiungerne una.")
            return
        }
        if (chiave != Pensione.VALORIZZAZIONI) {
            riga(cont, l.size.toString() + " voci", "", euro(l.sumOf { it.importo }), null, null)
        }
        for (m in l) {
            val sotto = listOf(m.tipo, m.motivo, m.note).filter { it.isNotBlank() }.joinToString(" · ")
            riga(cont, m.data, sotto, euro(m.importo), null) { menuVocePensione(chiave, m) }
        }
    }

    private fun menuVocePensione(chiave: String, m: Pensione.Mov) {
        OcBuilder(this)
            .setTitle(m.data + " · " + euro(m.importo))
            .setItems(arrayOf("✏️  Modifica", "🗑️  Elimina")) { _, k ->
                if (k == 0) dialogVocePensione(chiave, m) else OcBuilder(this)
                    .setTitle("Eliminare la voce?")
                    .setMessage(m.data + " · " + euro(m.importo))
                    .setNegativeButton("Annulla", null)
                    .setPositiveButton("Elimina") { _, _ ->
                        val err = Pensione.elimina(chiave, m.id)
                        if (err != null) aviso(err) else { refresh(); aviso("Voce eliminata") }
                    }
                    .show()
            }
            .setNegativeButton("Chiudi", null)
            .show()
    }

    private fun dialogVocePensione(chiave: String, m: Pensione.Mov?) {
        val (view, corpo) = corpoDialogo()
        var dt: LocalDate? = m?.let { Pensione.data(it.data) } ?: LocalDate.now()
        bottoneData(corpo, "Data", dt, false) { dt = it }
        var tipo = m?.tipo?.takeIf { it.isNotBlank() } ?: ""
        if (chiave != Pensione.VALORIZZAZIONI) {
            val base = if (chiave == Pensione.VERSAMENTI) Pensione.TIPI_VERSAMENTO else Pensione.TIPI_RISCATTO
            if (tipo.isEmpty()) tipo = base[0]
            val voci = if (tipo in base) base else base + tipo
            campoMenu(corpo, "Tipo", voci, tipo) { tipo = voci[it] }
        }
        val etImp = campoTesto(
            corpo, if (chiave == Pensione.VALORIZZAZIONI) "Controvalore (€)" else "Importo (€)",
            if (m != null) String.format(Locale.ITALY, "%.2f", m.importo) else "", TIPO_DECIMALE, 12
        )
        val cap = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        val etMotivo = if (chiave == Pensione.RISCATTI) campoTesto(corpo, "Motivo", m?.motivo ?: "", cap, 30) else null
        val etNote = campoTesto(corpo, "Note", m?.note ?: "", cap, if (chiave == Pensione.RISCATTI) 30 else 40)
        val nome = when (chiave) {
            Pensione.VERSAMENTI -> "versamento"
            Pensione.VALORIZZAZIONI -> "valorizzazione"
            else -> "riscatto"
        }
        val dlg = OcBuilder(this)
            .setTitle(if (m == null) "Nuovo $nome" else "Modifica $nome")
            .setView(view)
            .setPositiveButton("Salva", null)
            .setNegativeButton("Annulla", null)
            .create()
        dlg.show()
        dlg.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val imp = importoOk(etImp.text.toString())
            if (imp == null) { etImp.error = "Importo non valido"; return@setOnClickListener }
            val err = Pensione.salvaVoce(
                chiave, m?.id, dt, tipo, imp,
                etMotivo?.text?.toString() ?: "", etNote.text.toString()
            )
            if (err != null) { aviso(err); return@setOnClickListener }
            dlg.dismiss()
            refresh()
            aviso("Voce salvata")
        }
    }

    private val INV_SEZ = listOf("Dashboard", "Portafoglio", "Movimenti", "Dividendi", "Grafici")

    private fun invNum(x: Double, dec: Int = 4): String = String.format(Locale.ITALY, "%." + dec + "f", x)

    private fun invPct(x: Double): String = String.format(Locale.ITALY, "%+.2f", x) + "%"

    private fun invCampo(x: Double): String {
        if (x == Math.rint(x) && Math.abs(x) < 1e12) return x.toLong().toString()
        return String.format(Locale.ITALY, "%.6f", x).trimEnd('0').trimEnd(',')
    }

    private fun invAggiungi() {
        when (invVista) {
            2 -> dialogMovInvest(null, null)
            3 -> dialogDivInvest(null, null)
            else -> dialogTitoloInvest(null)
        }
    }

    private fun renderInvest() {
        intestaLista("💹 Investimenti", "", "🔀  Azioni")
        val cont = vista<LinearLayout>(R.id.listaGen)
        val d = Invest.dati()
        Invest.errore?.let { vuoto(cont, "⚠️ $it") }

        val scelta = pulsanteSel("Vista:  " + INV_SEZ[invVista] + "   ▾", true) {
            OcBuilder(this)
                .setTitle("Scegli la vista")
                .setItems(INV_SEZ.mapIndexed { i, t -> (if (i == invVista) "● " else "○ ") + t }.toTypedArray()) { _, i ->
                    invVista = i
                    renderInvest()
                }
                .setNegativeButton("Chiudi", null)
                .show()
        }
        cont.addView(scelta, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        when (invVista) {
            0 -> invDashboard(cont, d)
            1 -> invTitoli(cont, d)
            2 -> invMovimenti(cont, d)
            3 -> invDividendi(cont, d)
            else -> invGrafici(cont, d)
        }
    }

    private fun invSenzaPrezzo(cont: LinearLayout, t: Invest.Totali) {
        if (t.calcoli.any { it.quantita > 0 && it.prezzo <= 0.0 }) {
            vuoto(cont, "Alcuni titoli non hanno un prezzo: usa Azioni → Aggiorna prezzi.")
        }
    }

    private fun invDashboard(cont: LinearLayout, d: Invest.Dati) {
        val verde = ContextCompat.getColor(this, R.color.oc_green)
        val rosso = ContextCompat.getColor(this, R.color.oc_red)
        val arancio = ContextCompat.getColor(this, R.color.oc_orange)
        if (d.titoli.isEmpty()) {
            vuoto(cont, "Nessun titolo: aggiungine uno dal menu Azioni, oppure sincronizza col desktop.")
            return
        }
        val t = Invest.totali(d)
        invSenzaPrezzo(cont, t)
        sezione(cont, "SITUAZIONE")
        riga(cont, "Investito", "", euro(t.investito), null, null)
        riga(cont, "Valore attuale", "", euro(t.attuale), null, null)
        riga(cont, "P&L", invPct(t.plPct), segnoEuro(t.pl), if (t.pl >= 0) verde else rosso, null)
        riga(cont, "Dividendi incassati", "", euro(t.dividendi), null, null)
        riga(cont, "Rendimento reale", "P&L + dividendi", segnoEuro(t.rendReale), if (t.rendReale >= 0) verde else rosso, null)

        sezione(cont, "ALLOCAZIONE E RIBILANCIAMENTO")
        riga(cont, "Soglia di ribilanciamento", "", "±" + String.format(Locale.ITALY, "%.1f", d.soglia) + "%", null, null)
        for (c in t.calcoli) {
            val att = Invest.allocazione(c, t.attuale)
            val st = Invest.stato(att, c.target, d.soglia)
            val emo = when (st) { 1 -> "🟢"; 2 -> "🟡"; 3 -> "🔴"; else -> "⚪" }
            val colore: Int? = when (st) { 1 -> verde; 2 -> arancio; 3 -> rosso; else -> null }
            val sotto = "Attuale " + String.format(Locale.ITALY, "%.2f", att) + "%" +
                (if (c.target != 0.0) " · Target " + String.format(Locale.ITALY, "%.1f", c.target) + "%" else "")
            val valore = if (c.target != 0.0) String.format(Locale.ITALY, "%+.2f", att - c.target) + "%" else "—"
            riga(cont, emo + " " + c.ticker, sotto, valore, colore, null)
        }
    }

    private fun invTitoli(cont: LinearLayout, d: Invest.Dati) {
        val verde = ContextCompat.getColor(this, R.color.oc_green)
        val rosso = ContextCompat.getColor(this, R.color.oc_red)
        if (d.titoli.isEmpty()) {
            vuoto(cont, "Nessun titolo: aggiungine uno dal menu Azioni, oppure sincronizza col desktop.")
            return
        }
        val t = Invest.totali(d)
        invSenzaPrezzo(cont, t)
        sezione(cont, "TITOLI · " + t.calcoli.size)
        for (c in t.calcoli) {
            val tit = c.ticker + (if (c.descrizione.isNotBlank()) " · " + c.descrizione else "")
            val sotto = c.assetClass + " · Qtà " + invNum(c.quantita) + " · PMC " + invNum(c.pmc) + " · Prezzo " + invNum(c.prezzo) +
                "\nInvestito " + euro(c.investito) + " · P&L " + segnoEuro(c.plEur) + " (" + invPct(c.plPct) + ")"
            riga(cont, tit, sotto, euro(c.valore), if (c.plEur >= 0) verde else rosso) { menuTitoloInvest(c.ticker) }
        }
        riga(
            cont, "TOTALE", "Investito " + euro(t.investito) + " · P&L " + segnoEuro(t.pl) + " (" + invPct(t.plPct) + ")",
            euro(t.attuale), if (t.pl >= 0) verde else rosso, null
        )
    }

    private fun menuTitoloInvest(ticker: String) {
        OcBuilder(this)
            .setTitle(ticker)
            .setItems(arrayOf("➕  Nuovo movimento", "💶  Nuovo dividendo", "✏️  Modifica titolo", "🗑️  Rimuovi titolo")) { _, k ->
                when (k) {
                    0 -> dialogMovInvest(ticker, null)
                    1 -> dialogDivInvest(ticker, null)
                    2 -> dialogTitoloInvest(ticker)
                    else -> OcBuilder(this)
                        .setTitle("Rimuovere il titolo?")
                        .setMessage("Rimuovere $ticker con tutti i suoi movimenti e dividendi?")
                        .setNegativeButton("Annulla", null)
                        .setPositiveButton("Rimuovi") { _, _ ->
                            val err = Invest.rimuoviTitolo(ticker)
                            if (err != null) aviso(err) else { refresh(); aviso("Titolo rimosso") }
                        }
                        .show()
                }
            }
            .setNegativeButton("Chiudi", null)
            .show()
    }

    private fun invMovimenti(cont: LinearLayout, d: Invest.Dati) {
        val verde = ContextCompat.getColor(this, R.color.oc_green)
        val rosso = ContextCompat.getColor(this, R.color.oc_red)
        val tickers = d.titoli.map { it.ticker }
        if (invFiltro.isNotEmpty() && invFiltro !in tickers) invFiltro = ""
        cont.addView(
            pulsanteSel("Titolo:  " + invFiltro.ifEmpty { "Tutti" } + "   ▾", true) {
                val voci = listOf("Tutti") + tickers
                OcBuilder(this)
                    .setTitle("Titolo")
                    .setItems(voci.toTypedArray()) { _, i ->
                        invFiltro = if (i == 0) "" else voci[i]
                        renderLista()
                    }
                    .setNegativeButton("Chiudi", null)
                    .show()
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )
        val l = Invest.ordinaMov(
            d.titoli.filter { invFiltro.isEmpty() || it.ticker == invFiltro }.flatMap { tt -> tt.mov.map { tt.ticker to it } }
        )
        if (l.isEmpty()) {
            vuoto(cont, "Nessun movimento. Usa il menu Azioni per aggiungerne uno.")
            return
        }
        for ((tk, m) in l) {
            val sotto = m.tipo.replaceFirstChar { it.uppercase() } + " · Qtà " + invNum(m.quantita) + " · Prezzo " + invNum(m.prezzo) +
                " · Comm. " + euro(m.commissioni) + (if (m.note.isNotBlank()) "\n" + m.note else "")
            riga(cont, m.data + " · " + tk, sotto, euro(m.totale), if (m.tipo == "acquisto") verde else rosso) { menuMovInvest(tk, m) }
        }
    }

    private fun menuMovInvest(ticker: String, m: Invest.Mov) {
        OcBuilder(this)
            .setTitle(m.data + " · " + ticker)
            .setItems(arrayOf("✏️  Modifica", "🗑️  Elimina")) { _, k ->
                if (k == 0) dialogMovInvest(ticker, m) else OcBuilder(this)
                    .setTitle("Eliminare il movimento?")
                    .setMessage(m.data + " · " + ticker + " · " + euro(m.totale))
                    .setNegativeButton("Annulla", null)
                    .setPositiveButton("Elimina") { _, _ ->
                        val err = Invest.eliminaMov(ticker, m.id)
                        if (err != null) aviso(err) else { refresh(); aviso("Movimento eliminato") }
                    }
                    .show()
            }
            .setNegativeButton("Chiudi", null)
            .show()
    }

    private fun invDividendi(cont: LinearLayout, d: Invest.Dati) {
        val verde = ContextCompat.getColor(this, R.color.oc_green)
        val l = Invest.ordinaDiv(d.titoli.flatMap { tt -> tt.div.map { tt.ticker to it } })
        if (l.isEmpty()) {
            vuoto(cont, "Nessun dividendo. Usa il menu Azioni per aggiungerne uno.")
            return
        }
        riga(cont, l.size.toString() + " dividendi", "netto incassato", euro(l.sumOf { it.second.netto }), null, null)
        for ((tk, x) in l) {
            riga(cont, x.data + " · " + tk, "Lordo " + euro(x.lordo) + " · Ritenuta " + euro(x.ritenuta), euro(x.netto), verde) {
                menuDivInvest(tk, x)
            }
        }
    }

    private fun menuDivInvest(ticker: String, x: Invest.Div) {
        OcBuilder(this)
            .setTitle(x.data + " · " + ticker)
            .setItems(arrayOf("✏️  Modifica", "🗑️  Elimina")) { _, k ->
                if (k == 0) dialogDivInvest(ticker, x) else OcBuilder(this)
                    .setTitle("Eliminare il dividendo?")
                    .setMessage(x.data + " · " + ticker + " · " + euro(x.netto))
                    .setNegativeButton("Annulla", null)
                    .setPositiveButton("Elimina") { _, _ ->
                        val err = Invest.eliminaDiv(ticker, x.indice)
                        if (err != null) aviso(err) else { refresh(); aviso("Dividendo eliminato") }
                    }
                    .show()
            }
            .setNegativeButton("Chiudi", null)
            .show()
    }

    private fun invGrafici(cont: LinearLayout, d: Invest.Dati) {
        val s = Invest.serie(d)
        if (s == null) {
            vuoto(cont, "Nessun dato da visualizzare.")
            return
        }
        val blu = ContextCompat.getColor(this, R.color.oc_blue)
        val verde = ContextCompat.getColor(this, R.color.oc_green)
        val etich = s.date.map { String.format(Locale.ITALY, "%02d/%02d/%02d", it.dayOfMonth, it.monthValue, it.year % 100) }
        invGrafico(cont, "INVESTITO E DIVIDENDI CUMULATI", "Investito", "Dividendi", etich, s.investito, s.dividendi, blu, verde)
        invGrafico(cont, "P&L ATTUALE RISPETTO ALL'INVESTITO CUMULATO", "P&L", null, etich, s.pl, emptyList(), blu, verde)
    }

    private fun invGrafico(
        cont: LinearLayout, titolo: String, nomeA: String, nomeB: String?, etich: List<String>,
        a: List<Double>, b: List<Double>, colA: Int, colB: Int,
    ) {
        val dp = resources.displayMetrics.density
        val grigio = ContextCompat.getColor(this, R.color.oc_text_mid)
        sezione(cont, titolo)
        val leg = "■ $nomeA" + (if (nomeB != null) "     ■ $nomeB" else "")
        val sp = android.text.SpannableString(leg)
        sp.setSpan(android.text.style.ForegroundColorSpan(colA), 0, 1, 0)
        if (nomeB != null) {
            val k2 = leg.lastIndexOf('■')
            sp.setSpan(android.text.style.ForegroundColorSpan(colB), k2, k2 + 1, 0)
        }
        val tl = TextView(this)
        tl.text = sp
        tl.textSize = 12f
        tl.setPadding(4, (4 * dp).toInt(), 4, (4 * dp).toInt())
        cont.addView(tl)
        val g = GraficoView(this)
        g.imposta(
            GraficoView.Modo.LINEA, etich, a, if (nomeB != null) b else emptyList(), colA, colB, grigio,
            ContextCompat.getColor(this, R.color.oc_border), grigio, ContextCompat.getColor(this, R.color.oc_surface),
        )
        val det = TextView(this)
        det.textSize = 13f
        det.setPadding(8, (6 * dp).toInt(), 8, (6 * dp).toInt())
        det.text = "Tocca il grafico per vedere i dettagli."
        det.setTextColor(grigio)
        det.minLines = 2
        g.alSelezionare = { i ->
            det.text = etich[i] + "\n" + nomeA + " " + euro(a[i]) + (if (nomeB != null) " · " + nomeB + " " + euro(b[i]) else "")
            det.setTextColor(ContextCompat.getColor(this, R.color.oc_text))
        }
        cont.addView(g, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (190 * dp).toInt()))
        cont.addView(det)
    }

    private fun dialogTitoloInvest(ticker: String?) {
        val t = ticker?.let { tk -> Invest.dati().titoli.firstOrNull { it.ticker == tk } }
        if (ticker != null && t == null) {
            aviso("Titolo non trovato")
            return
        }
        val (view, corpo) = corpoDialogo()
        val cap = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        val maiusc = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
        val etTk = if (t == null) campoTesto(corpo, "Ticker (es. VWCE.AS)", "", maiusc, 20) else null
        val etDesc = campoTesto(corpo, "Descrizione", t?.descrizione ?: "", cap, 60)
        val etIsin = campoTesto(corpo, "ISIN (opzionale)", t?.isin ?: "", maiusc, 12)
        val etAsset = campoTesto(corpo, "Asset class", t?.assetClass?.takeIf { it != "-" } ?: "", cap, 30)
        val etTarget = campoTesto(
            corpo, "Target % (es. 55)", if (t != null && t.target != 0.0) invCampo(t.target) else "", TIPO_DECIMALE, 6
        )
        val dlg = OcBuilder(this)
            .setTitle(if (t == null) "Nuovo titolo" else "Modifica " + t.ticker)
            .setView(view)
            .setPositiveButton("Salva", null)
            .setNegativeButton("Annulla", null)
            .create()
        dlg.show()
        dlg.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val tgTxt = etTarget.text.toString().trim()
            val tg = if (tgTxt.isEmpty()) 0.0 else importoOk(tgTxt, true)
            if (tg == null || tg > 100.0) {
                etTarget.error = "Valore non valido"
                return@setOnClickListener
            }
            val err = Invest.salvaTitolo(
                t?.ticker ?: (etTk?.text?.toString() ?: ""), t == null, etDesc.text.toString(),
                etIsin.text.toString(), etAsset.text.toString(), tg
            )
            if (err != null) {
                if (etTk != null) etTk.error = err else aviso(err)
                return@setOnClickListener
            }
            dlg.dismiss()
            refresh()
            aviso("Titolo salvato")
        }
    }

    private fun dialogMovInvest(ticker: String?, m: Invest.Mov?) {
        val tickers = Invest.dati().titoli.map { it.ticker }
        if (tickers.isEmpty()) {
            aviso("Aggiungi prima un titolo")
            return
        }
        var tk = ticker ?: invFiltro.takeIf { it in tickers } ?: tickers[0]
        val (view, corpo) = corpoDialogo()
        if (ticker == null) campoMenu(corpo, "Titolo", tickers, tk) { tk = tickers[it] }
        var dt: LocalDate? = m?.let { Invest.data(it.data) } ?: LocalDate.now()
        bottoneData(corpo, "Data", dt, false) { dt = it }
        var tipo = m?.tipo?.takeIf { it in Invest.TIPI_MOV } ?: "acquisto"
        campoMenu(corpo, "Tipo", Invest.TIPI_MOV, tipo) { tipo = Invest.TIPI_MOV[it] }
        val etQ = campoTesto(corpo, "Quantità", if (m != null) invCampo(m.quantita) else "", TIPO_DECIMALE, 16)
        val etP = campoTesto(corpo, "Prezzo (€)", if (m != null) invCampo(m.prezzo) else "", TIPO_DECIMALE, 16)
        val etC = campoTesto(
            corpo, "Commissioni (€)", if (m != null && m.commissioni != 0.0) invCampo(m.commissioni) else "", TIPO_DECIMALE, 12
        )
        val etN = campoTesto(corpo, "Note", m?.note ?: "", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES, 60)
        val dlg = OcBuilder(this)
            .setTitle(if (m == null) "Nuovo movimento" + (ticker?.let { " · $it" } ?: "") else "Modifica movimento · $tk")
            .setView(view)
            .setPositiveButton("Salva", null)
            .setNegativeButton("Annulla", null)
            .create()
        dlg.show()
        dlg.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val q = importoOk(etQ.text.toString())
            if (q == null) { etQ.error = "Quantità non valida"; return@setOnClickListener }
            val p = if (etP.text.toString().isBlank()) null else importoOk(etP.text.toString(), true)
            if (p == null) { etP.error = "Prezzo non valido"; return@setOnClickListener }
            val cTxt = etC.text.toString().trim()
            val c = if (cTxt.isEmpty()) 0.0 else importoOk(cTxt, true)
            if (c == null) { etC.error = "Commissioni non valide"; return@setOnClickListener }
            val err = Invest.salvaMov(tk, m?.id, dt, tipo, q, p, c, etN.text.toString())
            if (err != null) { aviso(err); return@setOnClickListener }
            dlg.dismiss()
            refresh()
            aviso("Movimento salvato")
        }
    }

    private fun dialogDivInvest(ticker: String?, x: Invest.Div?) {
        val tickers = Invest.dati().titoli.map { it.ticker }
        if (tickers.isEmpty()) {
            aviso("Aggiungi prima un titolo")
            return
        }
        var tk = ticker ?: invFiltro.takeIf { it in tickers } ?: tickers[0]
        val (view, corpo) = corpoDialogo()
        if (ticker == null) campoMenu(corpo, "Titolo", tickers, tk) { tk = tickers[it] }
        var dt: LocalDate? = x?.let { Invest.data(it.data) } ?: LocalDate.now()
        bottoneData(corpo, "Data", dt, false) { dt = it }
        val etLordo = campoTesto(corpo, "Importo lordo (€)", if (x != null) invCampo(x.lordo) else "", TIPO_DECIMALE, 14)
        val etRit = campoTesto(corpo, "Ritenuta (€)", if (x != null && x.ritenuta != 0.0) invCampo(x.ritenuta) else "", TIPO_DECIMALE, 14)
        val etNetto = campoTesto(corpo, "Importo netto (€)", if (x != null) invCampo(x.netto) else "", TIPO_DECIMALE, 14)
        fun ricalcola() {
            val l = num(etLordo.text.toString()) ?: 0.0
            val r = num(etRit.text.toString()) ?: 0.0
            etNetto.setText(String.format(Locale.ITALY, "%.2f", maxOf(l - r, 0.0)))
        }
        etLordo.doAfterTextChanged { ricalcola() }
        etRit.doAfterTextChanged { ricalcola() }
        val dlg = OcBuilder(this)
            .setTitle(if (x == null) "Nuovo dividendo" + (ticker?.let { " · $it" } ?: "") else "Modifica dividendo · $tk")
            .setView(view)
            .setPositiveButton("Salva", null)
            .setNegativeButton("Annulla", null)
            .create()
        dlg.show()
        dlg.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val lordo = if (etLordo.text.toString().isBlank()) null else importoOk(etLordo.text.toString(), true)
            if (lordo == null) { etLordo.error = "Importo non valido"; return@setOnClickListener }
            val rTxt = etRit.text.toString().trim()
            val rit = if (rTxt.isEmpty()) 0.0 else importoOk(rTxt, true)
            if (rit == null) { etRit.error = "Ritenuta non valida"; return@setOnClickListener }
            val netto = if (etNetto.text.toString().isBlank()) null else importoOk(etNetto.text.toString(), true)
            if (netto == null) { etNetto.error = "Importo non valido"; return@setOnClickListener }
            val err = Invest.salvaDiv(tk, x?.indice, dt, lordo, rit, netto)
            if (err != null) { aviso(err); return@setOnClickListener }
            dlg.dismiss()
            refresh()
            aviso("Dividendo salvato")
        }
    }

    private fun menuInvest() {
        val voci = ArrayList<String>()
        val azioni = ArrayList<() -> Unit>()
        fun v(t: String, a: () -> Unit) {
            voci.add(t)
            azioni.add(a)
        }
        v("➕  Nuovo titolo") { dialogTitoloInvest(null) }
        v("➕  Nuovo movimento") { dialogMovInvest(null, null) }
        v("➕  Nuovo dividendo") { dialogDivInvest(null, null) }
        v("🔄  Aggiorna prezzi") { invAggiornaPrezzi() }
        v("🤖  Analisi AI") { menuAiInvest() }
        v("📄  Esporta PDF") { invEsportaPdf() }
        v("📤  Esporta JSON") { invEsportaJson() }
        v("📥  Importa JSON") { apriJsonInvest.launch(arrayOf("*/*")) }
        v("🗑️  Azzera portafoglio") { invAzzera() }
        OcBuilder(this)
            .setTitle("Investimenti")
            .setItems(voci.toTypedArray()) { _, i -> azioni[i]() }
            .setNegativeButton("Chiudi", null)
            .show()
    }

    private fun invAggiornaPrezzi() {
        if (invInCorso) {
            aviso("Aggiornamento già in corso…")
            return
        }
        if (Invest.dati().titoli.isEmpty()) {
            aviso("Nessun titolo da aggiornare")
            return
        }
        invInCorso = true
        aviso("Download prezzi da Yahoo Finance…")
        Thread {
            val r = try {
                Invest.aggiornaPrezzi()
            } catch (e: Exception) {
                null
            }
            runOnUiThread {
                invInCorso = false
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (r == null) {
                    aviso("Aggiornamento prezzi non riuscito")
                    return@runOnUiThread
                }
                if (r.nonTrovati.isNotEmpty()) {
                    OcBuilder(this)
                        .setTitle("Prezzi non trovati")
                        .setMessage(
                            "Nessun prezzo live trovato per:\n\n" + r.nonTrovati.joinToString("\n") { "  • $it" } +
                                "\n\nVerifica che il ticker sia corretto (es. VWCE.AS, AAPL) e che ci sia connessione a internet."
                        )
                        .setPositiveButton("Chiudi", null)
                        .show()
                } else {
                    aviso("Prezzi aggiornati")
                }
                if (pagina == Pagina.INVEST) renderLista()
            }
        }.start()
    }

    private fun menuAiInvest() {
        OcBuilder(this)
            .setTitle("Analisi AI")
            .setItems(
                arrayOf("📊  Analizza il portafoglio", "📈  Commenta il P&L", "🔍  Cerca un ETF o titolo", "💬  Fai una domanda")
            ) { _, i ->
                when (i) {
                    0 -> invAi("🤖 Analisi portafoglio", true) { Invest.promptPortafoglio(it) }
                    1 -> invAi("🤖 Commento P&L", true) { Invest.promptPl(it) }
                    2 -> invChiediTesto("Cerca un ETF o titolo", "Descrizione (es. ETF azionario mondiale)") { s ->
                        invAi("🤖 Ticker suggeriti", false) { Invest.promptTicker(s) }
                    }
                    else -> invChiediTesto("Fai una domanda", "Domanda sul tuo portafoglio") { s ->
                        invAi("🤖 Risposta", true) { Invest.promptDomanda(it, s) }
                    }
                }
            }
            .setNegativeButton("Chiudi", null)
            .show()
    }

    private fun invChiediTesto(titolo: String, hint: String, ok: (String) -> Unit) {
        val (view, corpo) = corpoDialogo()
        val et = campoTesto(
            corpo, hint, "",
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_MULTI_LINE, 300
        )
        val dlg = OcBuilder(this)
            .setTitle(titolo)
            .setView(view)
            .setPositiveButton("Invia", null)
            .setNegativeButton("Annulla", null)
            .create()
        dlg.show()
        dlg.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val s = et.text.toString().trim()
            if (s.isEmpty()) {
                et.error = "Scrivi qualcosa"
                return@setOnClickListener
            }
            dlg.dismiss()
            ok(s)
        }
    }

    private fun invAi(titolo: String, serveTitoli: Boolean, prompt: (Invest.Dati) -> String) {
        if (Gemini.chiave(this).isEmpty()) {
            dialogChiaveGemini { invAi(titolo, serveTitoli, prompt) }
            return
        }
        val d = Invest.dati()
        if (serveTitoli && d.titoli.isEmpty()) {
            aviso("Nessun titolo in portafoglio")
            return
        }
        val p = prompt(d)
        aviso("Analisi in corso…")
        val app = applicationContext
        Thread {
            var testo: String? = null
            var err: String? = null
            try {
                testo = Gemini.chiediTesto(app, p)
            } catch (e: Exception) {
                err = Gemini.messaggio(e)
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                val t = testo
                if (t != null) dietDialogTesto(titolo, t) else aviso(err ?: "Errore sconosciuto")
            }
        }.start()
    }

    private fun invEsportaPdf() {
        val f = try {
            InvestPdf.crea(this, Invest.dati())
        } catch (e: Exception) {
            aviso("Errore nella creazione del PDF: " + e.message)
            return
        }
        InvestPdf.condividi(this, f, "Portafoglio investimenti")
    }

    private fun invEsportaJson() {
        val testo = Invest.esportaTesto()
        if (testo == null) {
            aviso("Impossibile leggere il portafoglio")
            return
        }
        val dir = java.io.File(cacheDir, "pdf").apply { mkdirs() }
        val f = java.io.File(dir, "portafoglio_" + LocalDate.now() + ".json")
        try {
            f.writeText(testo, Charsets.UTF_8)
        } catch (e: Exception) {
            aviso("Impossibile esportare: " + e.message)
            return
        }
        InvestPdf.condividi(this, f, "Portafoglio investimenti")
    }

    private fun invCopiaSicurezza(motivo: String): Boolean {
        val snap = Repo.creaSnapshot(this, motivo)
        if (snap == null && Invest.dati().titoli.isNotEmpty()) {
            aviso("Copia di sicurezza non riuscita: operazione annullata")
            return false
        }
        return true
    }

    private fun invAzzera() {
        OcBuilder(this)
            .setTitle("Azzera portafoglio")
            .setMessage("Azzerare completamente il portafoglio investimenti? Prima viene creata una copia di sicurezza.")
            .setNegativeButton("Annulla", null)
            .setPositiveButton("Azzera") { _, _ ->
                if (!invCopiaSicurezza("azzeramento")) return@setPositiveButton
                val err = Invest.reset()
                if (err != null) aviso(err) else {
                    refresh()
                    aviso("Portafoglio azzerato")
                }
            }
            .show()
    }

    private fun importaInvestJson(uri: android.net.Uri) {
        val testo = try {
            contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
        } catch (e: Exception) {
            null
        }
        if (testo == null) {
            aviso("File non leggibile")
            return
        }
        val valido = try {
            JSONObject(testo).optJSONArray("titoli") != null
        } catch (e: Exception) {
            false
        }
        if (!valido) {
            aviso("File non valido")
            return
        }
        OcBuilder(this)
            .setTitle("Importa portafoglio")
            .setMessage("Sovrascrivere il portafoglio attuale con il file importato? Prima viene creata una copia di sicurezza.")
            .setNegativeButton("Annulla", null)
            .setPositiveButton("Importa") { _, _ ->
                if (!invCopiaSicurezza("import investimenti")) return@setPositiveButton
                val err = Invest.importaTesto(testo)
                if (err != null) aviso(err) else {
                    refresh()
                    aviso("Portafoglio importato")
                }
            }
            .show()
    }

    private val DIET_SEZ = listOf("Piano settimanale", "Confronto settimane", "Peso", "Fabbisogno calorico", "Passi", "Alimenti personalizzati")

    private fun dietNum(x: Double): String = if (x == Math.rint(x)) x.toLong().toString() else Dieta.fmt1(x)

    private fun renderDieta() {
        intestaLista("🥗 Dieta", "", "🔀  Azioni")
        val cont = vista<LinearLayout>(R.id.listaGen)
        dietControllaAlimenti()
        cont.addView(
            pulsanteSel("Vista:  " + DIET_SEZ[dietVista] + "   ▾", true) {
                OcBuilder(this)
                    .setTitle("Vista")
                    .setItems(DIET_SEZ.toTypedArray()) { _, k ->
                        dietVista = k
                        renderLista()
                    }
                    .setNegativeButton("Annulla", null)
                    .show()
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )
        val corpo = LinearLayout(this)
        corpo.orientation = LinearLayout.VERTICAL
        when (dietVista) {
            0 -> dietPiano(corpo)
            1 -> dietConfronto(corpo)
            2 -> dietPeso(corpo)
            3 -> dietFabb(corpo)
            4 -> dietPassi(corpo)
            else -> dietCustom(corpo)
        }
        for (e in Dieta.errori()) vuoto(cont, "⚠️ $e")
        cont.addView(corpo)
    }

    private fun dietControllaAlimenti() {
        if (dietAlimentiControllati) return
        dietAlimentiControllati = true
        if (Dieta.aggiornamentoDovuto()) dietAggiornaAlimenti(true)
    }

    private fun dietAggiornaAlimenti(silenzioso: Boolean) {
        if (!silenzioso) aviso("Download del database alimenti…")
        Thread {
            val r = try { Result.success(Dieta.scaricaAlimenti()) } catch (e: Exception) { Result.failure(e) }
            runOnUiThread {
                if (isFinishing || isDestroyed || silenzioso) return@runOnUiThread
                val n = r.getOrNull()
                if (n != null) aviso("Database aggiornato: $n alimenti") else aviso("Aggiornamento non riuscito: " + (r.exceptionOrNull()?.message ?: "errore di rete"))
            }
        }.start()
    }

    private fun dietEsito(err: String?, ok: String) {
        if (err != null) aviso(err) else {
            refresh()
            aviso(ok)
        }
    }

    private fun dietBarraMacro(cont: LinearLayout, prot: Int, gras: Int, carb: Int) {
        val tot = (prot + gras + carb).toDouble()
        if (tot <= 0) return
        val dp = resources.displayMetrics.density
        val barra = LinearLayout(this)
        barra.orientation = LinearLayout.HORIZONTAL
        fun seg(v: Int, col: Int) {
            if (v <= 0) return
            val x = View(this)
            x.setBackgroundColor(col)
            barra.addView(x, LinearLayout.LayoutParams(0, (8 * dp).toInt(), v.toFloat()))
        }
        seg(prot, android.graphics.Color.parseColor("#3D7FD4"))
        seg(gras, android.graphics.Color.parseColor("#CC3333"))
        seg(carb, android.graphics.Color.parseColor("#C98314"))
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.setMargins((8 * dp).toInt(), 0, (8 * dp).toInt(), (4 * dp).toInt())
        cont.addView(barra, lp)
    }

    private fun dietPiano(cont: LinearLayout) {
        val p = Dieta.piano()
        if (p.sett.isEmpty()) {
            vuoto(cont, "Nessuna settimana nel piano. Usa Azioni → Ripristina piano predefinito.")
            return
        }
        if (dietSett !in p.sett.indices) dietSett = 0
        if (p.sett.size <= 4) {
            val rigaS = LinearLayout(this)
            rigaS.orientation = LinearLayout.HORIZONTAL
            for ((i, s) in p.sett.withIndex()) {
                val b = pulsanteSel(s.titolo.replace("Settimana", "Sett."), i == dietSett) {
                    dietSett = i
                    renderLista()
                }
                rigaS.addView(b, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            }
            cont.addView(rigaS)
        } else {
            cont.addView(pulsanteSel(p.sett[dietSett].titolo + "   ▾", true) {
                OcBuilder(this)
                    .setItems(p.sett.map { it.titolo }.toTypedArray()) { _, k ->
                        dietSett = k
                        renderLista()
                    }
                    .setNegativeButton("Annulla", null)
                    .show()
            })
        }
        val s = p.sett[dietSett]
        val m = s.medie()
        val t = s.totali()
        sezione(cont, "RIEPILOGO · " + s.titolo.uppercase())
        riga(
            cont, "Media giornaliera",
            "P " + m[1] + " g · G " + m[2] + " g · C " + m[3] + " g · Z " + m[4] + " g · F " + m[5] + " g",
            Dieta.fmtInt(m[0]) + " kcal", ContextCompat.getColor(this, R.color.oc_green), null
        )
        dietBarraMacro(cont, m[1], m[2], m[3])
        riga(cont, "Totale settimana", "", Dieta.fmtInt(t[0]) + " kcal", null, null)
        sezione(cont, "GIORNI (tocca per modificare i pasti)")
        for ((gi, g) in s.giorni.withIndex()) {
            val sotto = (0 until 4).joinToString("\n") { Dieta.PASTI_ETICHETTA[it] + ": " + g.pasti[it].ifBlank { "—" } } +
                "\nP " + g.tot[1] + " · G " + g.tot[2] + " · C " + g.tot[3] + " · Z " + g.tot[4] + " · F " + g.tot[5]
            riga(cont, g.nome, sotto, Dieta.fmtInt(g.tot[0]) + " kcal", null) { dietMenuGiorno(dietSett, gi) }
        }
    }

    private fun dietMenuGiorno(s: Int, g: Int) {
        val p = Dieta.piano()
        val giorno = p.sett.getOrNull(s)?.giorni?.getOrNull(g) ?: return
        fun corto(t: String) = if (t.length > 34) t.take(33) + "…" else t.ifBlank { "—" }
        val voci = ArrayList<String>()
        for (i in 0 until 4) voci.add(Dieta.PASTI_ETICHETTA[i] + " — " + corto(giorno.pasti[i]))
        voci.add("📋  Copia giorno in…")
        voci.add("🧹  Azzera giorno")
        OcBuilder(this)
            .setTitle(giorno.nome + " · " + p.sett[s].titolo)
            .setItems(voci.toTypedArray()) { _, k ->
                when {
                    k < 4 -> dietDialogComposizione(s, g, k)
                    k == 4 -> dietDialogCopia(p, s, g)
                    else -> OcBuilder(this)
                        .setTitle("Azzerare " + giorno.nome + "?")
                        .setMessage("Pasti, composizioni e valori nutrizionali del giorno verranno cancellati.")
                        .setNegativeButton("Annulla", null)
                        .setPositiveButton("Azzera") { _, _ -> dietEsito(Dieta.azzeraGiorno(s, g), "Giorno azzerato") }
                        .show()
                }
            }
            .setNegativeButton("Chiudi", null)
            .show()
    }

    private fun dietDialogCopia(p: Dieta.Piano, s: Int, g: Int) {
        val (view, corpo) = corpoDialogo()
        val nomiS = p.sett.map { it.titolo }
        var ds = s
        var dg = g
        var tilG: TextInputLayout? = null
        campoMenu(corpo, "Settimana di destinazione", nomiS, nomiS[s]) { i ->
            ds = i
            val nomi = p.sett[i].giorni.map { it.nome }
            if (dg >= nomi.size) dg = 0
            (tilG?.editText as? MaterialAutoCompleteTextView)?.let {
                it.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, nomi))
                it.setText(nomi.getOrElse(dg) { "" }, false)
            }
        }
        tilG = campoMenu(corpo, "Giorno di destinazione", p.sett[s].giorni.map { it.nome }, p.sett[s].giorni[g].nome) { dg = it }
        OcBuilder(this)
            .setTitle("Copia " + p.sett[s].giorni[g].nome)
            .setView(view)
            .setPositiveButton("Copia") { _, _ ->
                val err = Dieta.copiaGiorno(s, g, ds, dg)
                dietEsito(err, "Giorno copiato (il nome del giorno di destinazione resta invariato)")
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun dietDialogComposizione(s: Int, g: Int, pasto: Int) {
        val piano = Dieta.piano()
        val giorno = piano.sett.getOrNull(s)?.giorni?.getOrNull(g) ?: return
        val items = ArrayList(piano.composizione(s, g, pasto))
        val tutti = Dieta.alimenti()
        val (view, corpo) = corpoDialogo()
        val dp = resources.displayMetrics.density
        val tvTot = TextView(ctxDialogo)
        tvTot.setTypeface(null, Typeface.BOLD)
        tvTot.setPadding(4, 4, 4, (8 * dp).toInt())
        corpo.addView(tvTot)
        val box = LinearLayout(ctxDialogo)
        box.orientation = LinearLayout.VERTICAL
        corpo.addView(box)

        lateinit var ridisegna: () -> Unit
        fun modificaGrammi(i: Int) {
            val it0 = items[i]
            val (v2, c2) = corpoDialogo()
            val et = campoTesto(c2, "Grammi", dietNum(it0.g), TIPO_DECIMALE, 6)
            OcBuilder(this)
                .setTitle(it0.nome)
                .setView(v2)
                .setPositiveButton("OK") { _, _ ->
                    val ng = num(et.text.toString())
                    if (ng == null || ng <= 0 || ng > 5000) {
                        aviso("Grammi non validi")
                        return@setPositiveButton
                    }
                    val a = tutti.firstOrNull { x -> x.nome == it0.nome }
                    items[i] = if (a != null) Dieta.voce(a, ng)
                    else Dieta.Item(it0.nome, ng, it0.v.map { x -> Dieta.round1(x * ng / it0.g) })
                    ridisegna()
                }
                .setNegativeButton("Annulla", null)
                .show()
        }
        ridisegna = {
            box.removeAllViews()
            if (items.isEmpty()) {
                val t = TextView(ctxDialogo)
                t.text = "Nessun alimento. Aggiungine uno."
                t.setPadding(4, 8, 4, 8)
                box.addView(t)
            }
            for ((i, x) in items.withIndex()) {
                val t = TextView(ctxDialogo)
                t.text = x.nome + " · " + dietNum(x.g) + " g\n" + dietNum(x.v[0]) + " kcal · P " + dietNum(x.v[1]) +
                    " · G " + dietNum(x.v[2]) + " · C " + dietNum(x.v[3])
                t.setPadding(4, (8 * dp).toInt(), 4, (8 * dp).toInt())
                t.setOnClickListener {
                    OcBuilder(this)
                        .setTitle(x.nome)
                        .setItems(arrayOf("✏️  Cambia grammi", "🗑️  Rimuovi")) { _, k ->
                            if (k == 0) modificaGrammi(i) else {
                                items.removeAt(i)
                                ridisegna()
                            }
                        }
                        .setNegativeButton("Chiudi", null)
                        .show()
                }
                box.addView(t)
            }
            val tot = DoubleArray(6)
            for (x in items) for (k in 0 until 6) tot[k] += x.v[k]
            tvTot.text = "Totale: " + dietNum(Dieta.round1(tot[0])) + " kcal · P " + dietNum(Dieta.round1(tot[1])) +
                " · G " + dietNum(Dieta.round1(tot[2])) + " · C " + dietNum(Dieta.round1(tot[3])) +
                " · Z " + dietNum(Dieta.round1(tot[4])) + " · F " + dietNum(Dieta.round1(tot[5]))
        }
        ridisegna()
        val btnAdd = MaterialButton(ctxDialogo, null, com.google.android.material.R.attr.materialButtonOutlinedStyle)
        btnAdd.text = "➕  Aggiungi alimento"
        btnAdd.isAllCaps = false
        btnAdd.setOnClickListener {
            dietDialogScegli(tutti) { x ->
                items.add(x)
                ridisegna()
            }
        }
        corpo.addView(btnAdd)
        val btnClr = MaterialButton(ctxDialogo, null, com.google.android.material.R.attr.materialButtonOutlinedStyle)
        btnClr.text = "🧹  Svuota composizione"
        btnClr.isAllCaps = false
        btnClr.setOnClickListener {
            items.clear()
            ridisegna()
        }
        corpo.addView(btnClr)
        OcBuilder(this)
            .setTitle(giorno.nome + " · " + Dieta.PASTI_ETICHETTA[pasto])
            .setView(view)
            .setPositiveButton("Salva") { _, _ ->
                dietEsito(Dieta.salvaComposizione(s, g, pasto, items.toList()), "Pasto salvato, totali del giorno aggiornati")
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun dietDialogScegli(tutti: List<Dieta.Alimento>, aggiungi: (Dieta.Item) -> Unit) {
        val dp = resources.displayMetrics.density
        val cats = Dieta.categorie(tutti)
        val root = LinearLayout(ctxDialogo)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding((20 * dp).toInt(), (4 * dp).toInt(), (20 * dp).toInt(), 0)
        val etCerca = campoTesto(root, "Cerca alimento", "", InputType.TYPE_CLASS_TEXT, 40)
        var cat = "Tutte"
        var filtra: () -> Unit = {}
        campoMenu(root, "Categoria", cats, cat) {
            cat = cats[it]
            filtra()
        }
        val etG = campoTesto(root, "Grammi", "100", TIPO_DECIMALE, 6)
        val lista = android.widget.ListView(ctxDialogo)
        var visibili: List<Dieta.Alimento> = emptyList()
        val adapter = object : ArrayAdapter<String>(ctxDialogo, android.R.layout.simple_list_item_1, ArrayList<String>()) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val v = super.getView(position, convertView, parent)
                (v as? TextView)?.textSize = 13f
                return v
            }
        }
        lista.adapter = adapter
        root.addView(lista, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (260 * dp).toInt()))
        filtra = {
            val q = etCerca.text.toString().trim().lowercase()
            visibili = tutti.filter { (cat == "Tutte" || it.cat == cat) && (q.isEmpty() || q in it.nome.lowercase()) }.take(150)
            adapter.clear()
            adapter.addAll(visibili.map {
                it.nome + "\n" + dietNum(it.v[0]) + " kcal · P " + dietNum(it.v[1]) + " · G " + dietNum(it.v[2]) +
                    " · C " + dietNum(it.v[3]) + "  (per 100 g)"
            })
            adapter.notifyDataSetChanged()
        }
        etCerca.doAfterTextChanged { filtra() }
        filtra()
        lista.setOnItemClickListener { _, _, pos, _ ->
            val g = num(etG.text.toString())
            val a = visibili.getOrNull(pos)
            if (g == null || g <= 0 || g > 5000) {
                aviso("Grammi non validi")
            } else if (a != null) {
                aggiungi(Dieta.voce(a, g))
                aviso("Aggiunto: " + a.nome + " (" + dietNum(g) + " g)")
            }
        }
        OcBuilder(this)
            .setTitle("Aggiungi alimento")
            .setView(root)
            .setNegativeButton("Fatto", null)
            .show()
    }

    private fun dietConfronto(cont: LinearLayout) {
        val p = Dieta.piano()
        val sett = p.sett.filter { it.giorni.isNotEmpty() }
        if (sett.isEmpty()) {
            vuoto(cont, "Nessun dato da confrontare.")
            return
        }
        val verde = ContextCompat.getColor(this, R.color.oc_green)
        val arancio = ContextCompat.getColor(this, R.color.oc_orange)
        val kc = sett.map { it.medie()[0] }
        sezione(cont, "MEDIE GIORNALIERE PER SETTIMANA")
        for (s in sett) {
            val m = s.medie()
            val col = if (sett.size > 1 && m[0] == kc.min()) verde else if (sett.size > 1 && m[0] == kc.max()) arancio else null
            riga(
                cont, s.titolo, "P " + m[1] + " g · G " + m[2] + " g · C " + m[3] + " g · Z " + m[4] + " g · F " + m[5] + " g",
                Dieta.fmtInt(m[0]) + " kcal", col, null
            )
            dietBarraMacro(cont, m[1], m[2], m[3])
        }
        val tot = List(6) { k -> Math.rint(sett.sumOf { it.medie()[k].toDouble() } / sett.size).toInt() }
        riga(
            cont, "Media complessiva", "P " + tot[1] + " g · G " + tot[2] + " g · C " + tot[3] + " g · Z " + tot[4] + " g · F " + tot[5] + " g",
            Dieta.fmtInt(tot[0]) + " kcal", verde, null
        )
        val leg = TextView(this)
        leg.text = "Barre macro: blu = proteine · rosso = grassi · arancio = carboidrati. Verde = settimana più leggera, arancio = più calorica."
        leg.textSize = 11f
        leg.setTextColor(ContextCompat.getColor(this, R.color.oc_text_mid))
        leg.setPadding(8, 10, 8, 4)
        cont.addView(leg)
    }

    private fun dietGrafico(
        cont: LinearLayout, modo: GraficoView.Modo, lab: List<String>, a: List<Double?>, b: List<Double?>,
        colA: Int, colB: Int, prefisso: String, suffisso: String, dec: Int, zero: Boolean, dettaglio: (Int) -> String,
    ) {
        val dp = resources.displayMetrics.density
        val grigio = ContextCompat.getColor(this, R.color.oc_text_mid)
        val g = GraficoView(this)
        g.prefissoAsse = prefisso
        g.suffissoAsse = suffisso
        g.decimaliAsse = dec
        g.includiZero = zero
        g.imposta(
            modo, lab, a, b, colA, colB, grigio,
            ContextCompat.getColor(this, R.color.oc_border), grigio,
            ContextCompat.getColor(this, R.color.oc_surface),
        )
        val det = TextView(this)
        det.textSize = 13f
        det.setPadding(8, (6 * dp).toInt(), 8, (6 * dp).toInt())
        det.text = "Tocca il grafico per vedere i dettagli."
        det.setTextColor(grigio)
        g.alSelezionare = { i ->
            det.text = dettaglio(i)
            det.setTextColor(ContextCompat.getColor(this, R.color.oc_text))
        }
        cont.addView(g, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (180 * dp).toInt()))
        cont.addView(det)
    }

    private fun dietPeso(cont: LinearLayout) {
        val d = Dieta.peso()
        val verde = ContextCompat.getColor(this, R.color.oc_green)
        val arancio = ContextCompat.getColor(this, R.color.oc_orange)
        val ultimo = d.pesate.lastOrNull()
        val alt = Dieta.numero(d.altezza)
        val ob = Dieta.numero(d.obiettivo)
        sezione(cont, "RIEPILOGO")
        if (ultimo == null) {
            vuoto(cont, "Nessuna pesata registrata. Usa Azioni → Nuova pesata.")
        } else {
            riga(cont, "Peso attuale", ultimo.data, dietNum(ultimo.peso) + " kg", null, null)
            val b = if (alt != null) Dieta.bmi(ultimo.peso, alt) else null
            riga(
                cont, "BMI", if (b != null) Dieta.bmiCategoriaLunga(b) else "Imposta l'altezza per calcolarlo",
                b?.let { dietNum(it) } ?: "—", null, { dietDialogPesoParam() }
            )
        }
        riga(
            cont, "Obiettivo",
            if (ob != null && ultimo != null) {
                val dist = Dieta.round1(ultimo.peso - ob)
                if (dist > 0) "Ancora " + dietNum(dist) + " kg da perdere" else "Obiettivo raggiunto!"
            } else "Tocca per impostare",
            if (ob != null) dietNum(ob) + " kg" else "—", null, { dietDialogPesoParam() }
        )
        riga(cont, "Altezza", "", if (alt != null) dietNum(alt) + " cm" else "—", null, { dietDialogPesoParam() })
        if (d.pesate.isNotEmpty()) {
            val pesi = d.pesate.map { it.peso }
            val delta = Dieta.round1(pesi.last() - pesi.first())
            riga(
                cont, "Statistiche",
                "Min " + dietNum(pesi.min()) + " · Max " + dietNum(pesi.max()) + " · Media " + dietNum(Dieta.round1(pesi.sum() / pesi.size)) + " kg",
                (if (delta > 0) "+" else "") + dietNum(delta) + " kg", if (delta > 0) arancio else verde, null
            )
            if (d.pesate.size >= 2) {
                sezione(cont, "ANDAMENTO")
                val ult = d.pesate.takeLast(60)
                val lab = ult.map { it.d.format(DateTimeFormatter.ofPattern("dd/MM")) }
                val a = ult.map { it.peso as Double? }
                val b = if (ob != null) ult.map { ob as Double? } else emptyList()
                dietGrafico(
                    cont, GraficoView.Modo.LINEA, lab, a, b, ContextCompat.getColor(this, R.color.oc_blue), arancio,
                    "", " kg", 1, false
                ) { i ->
                    val x = ult.getOrNull(i)
                    if (x == null) "" else x.data + " · " + dietNum(x.peso) + " kg" + (ob?.let { "  (obiettivo " + dietNum(it) + " kg)" } ?: "")
                }
            }
            sezione(cont, "PESATE (tocca per modificare)")
            for (i in d.pesate.indices.reversed()) {
                val x = d.pesate[i]
                var sotto = ""
                var col: Int? = null
                var v = ""
                if (i > 0) {
                    val diff = Dieta.round1(x.peso - d.pesate[i - 1].peso)
                    v = (if (diff > 0) "+" else "") + dietNum(diff)
                    col = if (diff > 0) arancio else verde
                    sotto = "Variazione " + v + " kg"
                }
                if (alt != null) Dieta.bmi(x.peso, alt)?.let { sotto += (if (sotto.isEmpty()) "" else " · ") + "BMI " + dietNum(it) }
                riga(cont, x.data, sotto, dietNum(x.peso) + " kg", col) { dietMenuPesata(x) }
            }
        }
    }

    private fun dietMenuPesata(x: Dieta.Pesata) {
        OcBuilder(this)
            .setTitle(x.data + " · " + dietNum(x.peso) + " kg")
            .setItems(arrayOf("✏️  Modifica", "🗑️  Elimina")) { _, k ->
                if (k == 0) dietDialogPesata(x) else dietEsito(Dieta.eliminaPesata(x.d), "Pesata eliminata")
            }
            .setNegativeButton("Chiudi", null)
            .show()
    }

    private fun dietDialogPesata(x: Dieta.Pesata?) {
        val (view, corpo) = corpoDialogo()
        var data = x?.d ?: LocalDate.now()
        bottoneData(corpo, "Data", data, false) { data = it ?: data }
        val ultimo = Dieta.peso().pesate.lastOrNull()
        val et = campoTesto(corpo, "Peso (kg)", x?.let { dietNum(it.peso) } ?: ultimo?.let { dietNum(it.peso) } ?: "", TIPO_DECIMALE, 6)
        OcBuilder(this)
            .setTitle(if (x == null) "Nuova pesata" else "Modifica pesata")
            .setView(view)
            .setPositiveButton("Salva") { _, _ ->
                val p = num(et.text.toString())
                if (p == null || p <= 0 || p > 700) {
                    aviso("Peso non valido")
                    return@setPositiveButton
                }
                var err = Dieta.aggiungiPesata(data, p)
                if (err == null && x != null && x.d != data) err = Dieta.eliminaPesata(x.d)
                dietEsito(err, "Pesata salvata")
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun dietDialogPesoParam() {
        val d = Dieta.peso()
        val (view, corpo) = corpoDialogo()
        val etA = campoTesto(corpo, "Altezza (cm)", d.altezza, TIPO_DECIMALE, 6)
        val etO = campoTesto(corpo, "Peso obiettivo (kg)", d.obiettivo, TIPO_DECIMALE, 6)
        OcBuilder(this)
            .setTitle("Altezza e obiettivo")
            .setView(view)
            .setPositiveButton("Salva") { _, _ ->
                dietEsito(Dieta.salvaAltezzaObiettivo(etA.text.toString(), etO.text.toString()), "Dati salvati")
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun dietFabb(cont: LinearLayout) {
        val f = Dieta.fabb()
        val verde = ContextCompat.getColor(this, R.color.oc_green)
        val rosso = ContextCompat.getColor(this, R.color.oc_red)
        val blu = ContextCompat.getColor(this, R.color.oc_blue)
        sezione(cont, "DATI PERSONALI (tocca per modificare)")
        riga(
            cont, f.sesso, "Peso " + f.peso + " kg · Altezza " + f.altezza + " cm · Età " + f.eta + " anni", "✏️", null,
            { dietDialogFabb() }
        )
        val fb = Dieta.fabbisognoSalvato()
        if (fb == null) {
            vuoto(cont, "Inserisci sesso, peso, altezza ed età per calcolare il fabbisogno.")
            return
        }
        sezione(cont, "METABOLISMO BASALE")
        riga(cont, "BMR (formula di Mifflin-St Jeor)", "", Dieta.fmtInt(fb.bmr) + " kcal", verde, null)
        sezione(cont, "FABBISOGNO PER LIVELLO DI ATTIVITÀ")
        for (l in fb.livelli) riga(cont, l.nome, "", Dieta.fmtInt(l.kcal) + " kcal", null, null)
        sezione(cont, "OBIETTIVI CONSIGLIATI")
        val cols = listOf(rosso, blu, verde)
        for ((i, l) in fb.obiettivi.withIndex()) riga(cont, l.nome, "", Dieta.fmtInt(l.kcal) + " kcal", cols[i], null)
        val media = Dieta.piano().mediaKcal()
        if (media != null) {
            val diff = media - fb.livelli[2].kcal
            sezione(cont, "CONFRONTO CON IL PIANO")
            riga(
                cont, "Media piano dieta", "Differenza rispetto al mantenimento (moderato): " + (if (diff >= 0) "+" else "") + diff + " kcal",
                Dieta.fmtInt(media) + " kcal", null, null
            )
        }
    }

    private fun dietDialogFabb() {
        val f = Dieta.fabb()
        val pd = Dieta.peso()
        val (view, corpo) = corpoDialogo()
        var sesso = f.sesso
        val sessi = listOf("Donna", "Uomo")
        campoMenu(corpo, "Sesso", sessi, if (sesso in sessi) sesso else "Donna") { sesso = sessi[it] }
        fun pre(v: String, alt: String) = if ((Dieta.numero(v) ?: 0.0) > 0) v else alt
        val etP = campoTesto(corpo, "Peso (kg)", pre(f.peso, pd.pesate.lastOrNull()?.let { dietNum(it.peso) } ?: ""), TIPO_DECIMALE, 6)
        val etA = campoTesto(corpo, "Altezza (cm)", pre(f.altezza, pd.altezza), TIPO_DECIMALE, 6)
        val etE = campoTesto(corpo, "Età (anni)", pre(f.eta, ""), InputType.TYPE_CLASS_NUMBER, 3)
        OcBuilder(this)
            .setTitle("Dati per il fabbisogno")
            .setView(view)
            .setPositiveButton("Salva") { _, _ ->
                dietEsito(Dieta.salvaFabb(sesso, etP.text.toString(), etA.text.toString(), etE.text.toString()), "Dati salvati")
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun dietPassi(cont: LinearLayout) {
        val d = Dieta.passi()
        val verde = ContextCompat.getColor(this, R.color.oc_green)
        val rosso = ContextCompat.getColor(this, R.color.oc_red)
        val arancio = ContextCompat.getColor(this, R.color.oc_orange)
        val anni = (d.voci.map { it.d.year } + LocalDate.now().year).toSet().sortedDescending()
        val mesi = listOf("Tutti", "Gennaio", "Febbraio", "Marzo", "Aprile", "Maggio", "Giugno", "Luglio", "Agosto", "Settembre", "Ottobre", "Novembre", "Dicembre")
        val filtri = LinearLayout(this)
        filtri.orientation = LinearLayout.HORIZONTAL
        filtri.addView(pulsanteSel("Anno: " + (if (dietPassiAnno == 0) "Tutti" else dietPassiAnno.toString()) + " ▾", false) {
            val voci = listOf("Tutti") + anni.map { it.toString() }
            OcBuilder(this)
                .setItems(voci.toTypedArray()) { _, k ->
                    dietPassiAnno = if (k == 0) 0 else anni[k - 1]
                    renderLista()
                }
                .setNegativeButton("Annulla", null)
                .show()
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        filtri.addView(pulsanteSel("Mese: " + mesi[dietPassiMese] + " ▾", false) {
            OcBuilder(this)
                .setItems(mesi.toTypedArray()) { _, k ->
                    dietPassiMese = k
                    renderLista()
                }
                .setNegativeButton("Annulla", null)
                .show()
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        cont.addView(filtri)

        val filtrate = d.voci.filter {
            (dietPassiAnno == 0 || it.d.year == dietPassiAnno) && (dietPassiMese == 0 || it.d.monthValue == dietPassiMese)
        }
        val st = Dieta.statPassi(d.voci, filtrate)
        sezione(cont, "STATISTICHE")
        val o = st.oggi
        riga(
            cont, "Oggi",
            if (o != null) o.pct.toString() + "% dell'obiettivo (" + Dieta.fmtInt(o.ob) + ")" else "Nessun dato per oggi",
            if (o != null) Dieta.fmtInt(o.passi) else "—", if (o == null) null else if (o.raggiunto) verde else rosso,
            { dietDialogPassi(null) }
        )
        riga(cont, "Media 7 giorni", "", st.media7?.let { Dieta.fmtInt(it) } ?: "—", null, null)
        riga(cont, "Media 30 giorni", "", st.media30?.let { Dieta.fmtInt(it) } ?: "—", null, null)
        riga(cont, "Record", "", st.record?.let { Dieta.fmtInt(it) } ?: "—", null, null)
        riga(cont, "Totale periodo filtrato", filtrate.size.toString() + " giorni", Dieta.fmtInt(st.totale), null, null)
        riga(cont, "Giorni con obiettivo raggiunto", "su " + st.giorni + " registrati", st.giorniOk.toString(), verde, null)
        if (filtrate.isEmpty()) {
            vuoto(cont, "Nessun dato per il periodo selezionato. Usa Azioni → Registra passi.")
            return
        }
        sezione(cont, "ANDAMENTO (passi / obiettivo)")
        val ult = filtrate.takeLast(30)
        dietGrafico(
            cont, GraficoView.Modo.BARRE_DOPPIE, ult.map { it.d.format(DateTimeFormatter.ofPattern("dd/MM")) },
            ult.map { it.passi.toDouble() as Double? }, ult.map { it.ob.toDouble() as Double? }, verde, arancio, "", "", 0, true
        ) { i ->
            val x = ult.getOrNull(i)
            if (x == null) "" else x.data + " · " + Dieta.fmtInt(x.passi) + " passi (obiettivo " + Dieta.fmtInt(x.ob) + ", " + x.pct + "%)"
        }
        sezione(cont, "REGISTRO (tocca per modificare)")
        for (x in filtrate.reversed()) {
            val sotto = x.pct.toString() + "% · " + (if (x.raggiunto) "Raggiunto" else "Mancato") + (if (x.note.isNotBlank()) " · " + x.note else "")
            riga(cont, x.data, sotto, Dieta.fmtInt(x.passi), if (x.raggiunto) verde else rosso) { dietMenuPassi(x) }
        }
    }

    private fun dietMenuPassi(x: Dieta.VocePassi) {
        OcBuilder(this)
            .setTitle(x.data + " · " + Dieta.fmtInt(x.passi) + " passi")
            .setItems(arrayOf("✏️  Modifica", "🗑️  Elimina")) { _, k ->
                if (k == 0) dietDialogPassi(x) else dietEsito(Dieta.eliminaPassi(x.d), "Registrazione eliminata")
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun dietDialogPassi(x: Dieta.VocePassi?) {
        val dati = Dieta.passi()
        val oggi = if (x == null) dati.voci.firstOrNull { it.d == LocalDate.now() } else null
        val base = x ?: oggi
        val (view, corpo) = corpoDialogo()
        var data = base?.d ?: LocalDate.now()
        bottoneData(corpo, "Data", data, false) { data = it ?: data }
        val etP = campoTesto(corpo, "Passi", base?.passi?.toString() ?: "", InputType.TYPE_CLASS_NUMBER, 6)
        val etO = campoTesto(corpo, "Obiettivo giornaliero", (base?.ob ?: dati.obDefault).toString(), InputType.TYPE_CLASS_NUMBER, 6)
        val etN = campoTesto(corpo, "Note", base?.note ?: "", InputType.TYPE_CLASS_TEXT, 40)
        OcBuilder(this)
            .setTitle(if (x == null) "Registra passi" else "Modifica passi")
            .setView(view)
            .setPositiveButton("Salva") { _, _ ->
                val p = etP.text.toString().trim().toIntOrNull()
                if (p == null) {
                    aviso("Inserisci il numero di passi")
                    return@setPositiveButton
                }
                val ob = etO.text.toString().trim().toIntOrNull() ?: Dieta.PASSI_OB_DEFAULT
                var err = Dieta.salvaPassi(data, p, ob, etN.text.toString())
                if (err == null && x != null && x.d != data) err = Dieta.eliminaPassi(x.d)
                dietEsito(err, "Passi salvati")
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun dietCustom(cont: LinearLayout) {
        val l = Dieta.custom().sortedBy { it.nome.lowercase() }
        sezione(cont, "ALIMENTI PERSONALIZZATI (valori per 100 g)")
        if (l.isEmpty()) {
            vuoto(cont, "Nessun alimento personalizzato. Usa Azioni → Nuovo alimento.")
            return
        }
        for (a in l) {
            riga(
                cont, a.nome, a.cat + " · P " + dietNum(a.v[1]) + " · G " + dietNum(a.v[2]) + " · C " + dietNum(a.v[3]) +
                    " · Z " + dietNum(a.v[4]) + " · F " + dietNum(a.v[5]),
                dietNum(a.v[0]) + " kcal", null
            ) {
                OcBuilder(this)
                    .setTitle(a.nome)
                    .setItems(arrayOf("✏️  Modifica", "🗑️  Elimina")) { _, k ->
                        if (k == 0) dietDialogCustom(a) else OcBuilder(this)
                            .setTitle("Eliminare " + a.nome + "?")
                            .setNegativeButton("Annulla", null)
                            .setPositiveButton("Elimina") { _, _ -> dietEsito(Dieta.eliminaCustom(setOf(a.nome)), "Alimento eliminato") }
                            .show()
                    }
                    .setNegativeButton("Chiudi", null)
                    .show()
            }
        }
    }

    private fun dietDialogCustom(a: Dieta.Alimento?) {
        val (view, corpo) = corpoDialogo()
        val cap = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        val cats = Dieta.categorie(Dieta.alimenti()).drop(1) + "Altro"
        val catsU = cats.distinct()
        var cat = a?.cat?.takeIf { it in catsU } ?: "Altro"
        val etN = campoTesto(corpo, "Nome", a?.nome ?: "", cap, 60)
        campoMenu(corpo, "Categoria", catsU, cat) { cat = catsU[it] }
        val etC = campoTesto(corpo, "Nuova categoria (facoltativa)", if (a != null && a.cat !in catsU) a.cat else "", cap, 30)
        fun v(i: Int) = a?.v?.get(i)?.let { dietNum(it) } ?: ""
        val etK = campoTesto(corpo, "Kcal per 100 g", v(0), TIPO_DECIMALE, 7)
        val etP = campoTesto(corpo, "Proteine (g)", v(1), TIPO_DECIMALE, 6)
        val etG = campoTesto(corpo, "Grassi (g)", v(2), TIPO_DECIMALE, 6)
        val etCb = campoTesto(corpo, "Carboidrati (g)", v(3), TIPO_DECIMALE, 6)
        val etZ = campoTesto(corpo, "di cui zuccheri (g)", v(4), TIPO_DECIMALE, 6)
        val etF = campoTesto(corpo, "Fibre (g)", v(5), TIPO_DECIMALE, 6)
        OcBuilder(this)
            .setTitle(if (a == null) "Nuovo alimento" else "Modifica alimento")
            .setView(view)
            .setPositiveButton("Salva") { _, _ ->
                val valori = listOf(etK, etP, etG, etCb, etZ, etF).map { num(it.text.toString()) ?: 0.0 }
                val nome = etN.text.toString().trim()
                val categoria = etC.text.toString().trim().ifEmpty { cat }
                var err = Dieta.salvaCustom(nome, categoria, valori)
                if (err == null && a != null && a.nome != nome) err = Dieta.eliminaCustom(setOf(a.nome))
                dietEsito(err, "Alimento salvato")
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun menuDieta() {
        val voci = ArrayList<String>()
        val az = ArrayList<() -> Unit>()
        fun add(t: String, f: () -> Unit) {
            voci.add(t)
            az.add(f)
        }
        when (dietVista) {
            2 -> add("⚖️  Nuova pesata") { dietDialogPesata(null) }
            3 -> add("✏️  Modifica dati personali") { dietDialogFabb() }
            4 -> add("👟  Registra passi") { dietDialogPassi(null) }
        }
        add("➕  Nuovo alimento personalizzato") { dietDialogCustom(null) }
        if (dietVista == 0 || dietVista == 1) add("🤖  Consulta AI sulla settimana") { dietConsultaAi() }
        add("📤  Esporta PDF") { dietEsportaPdf() }
        add("🔄  Aggiorna database alimenti") { dietAggiornaAlimenti(false) }
        if (dietVista == 0 || dietVista == 1) add("↩️  Ripristina piano predefinito") {
            OcBuilder(this)
                .setTitle("Ripristinare il piano?")
                .setMessage("Il piano settimanale e tutte le composizioni dei pasti torneranno ai valori predefiniti.")
                .setNegativeButton("Annulla", null)
                .setPositiveButton("Ripristina") { _, _ -> dietEsito(Dieta.ripristinaPiano(), "Piano ripristinato") }
                .show()
        }
        if (dietVista == 2) add("🧹  Azzera storico peso") {
            OcBuilder(this)
                .setTitle("Azzerare lo storico del peso?")
                .setNegativeButton("Annulla", null)
                .setPositiveButton("Azzera") { _, _ -> dietEsito(Dieta.azzeraPeso(), "Storico azzerato") }
                .show()
        }
        if (dietVista == 4) add("🧹  Azzera storico passi") {
            OcBuilder(this)
                .setTitle("Azzerare lo storico dei passi?")
                .setNegativeButton("Annulla", null)
                .setPositiveButton("Azzera") { _, _ -> dietEsito(Dieta.azzeraPassi(), "Storico azzerato") }
                .show()
        }
        OcBuilder(this)
            .setTitle("Azioni")
            .setItems(voci.toTypedArray()) { _, k -> az[k]() }
            .setNegativeButton("Chiudi", null)
            .show()
    }

    private fun dietEsportaPdf() {
        val f = try {
            DietaPdf.crea(this)
        } catch (e: Exception) {
            aviso("Impossibile creare il PDF: " + (e.message ?: "errore"))
            return
        }
        DietaPdf.condividi(this, f)
    }

    private fun dietConsultaAi() {
        if (Gemini.chiave(this).isEmpty()) {
            dialogChiaveGemini { dietConsultaAi() }
            return
        }
        val s = Dieta.piano().sett.getOrNull(dietSett)
        if (s == null) {
            aviso("Nessuna settimana da analizzare")
            return
        }
        val prompt = Dieta.promptAi(s)
        aviso("Analisi in corso…")
        val app = applicationContext
        Thread {
            var testo: String? = null
            var err: String? = null
            try {
                testo = Gemini.chiediTesto(app, prompt)
            } catch (e: Exception) {
                err = Gemini.messaggio(e)
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (testo != null) dietDialogTesto("🤖 " + s.titolo, testo) else aviso(err ?: "Errore sconosciuto")
            }
        }.start()
    }

    private fun dietDialogTesto(titolo: String, testo: String) {
        val (view, corpo) = corpoDialogo()
        val tv = TextView(ctxDialogo)
        tv.text = testo
        tv.setTextIsSelectable(true)
        tv.setPadding(4, 4, 4, 4)
        corpo.addView(tv)
        OcBuilder(this)
            .setTitle(titolo)
            .setView(view)
            .setPositiveButton("Chiudi", null)
            .setNeutralButton("Condividi") { _, _ ->
                val f = try {
                    DietaPdf.analisi(this, titolo, testo)
                } catch (e: Exception) {
                    aviso("Errore nella creazione del PDF: " + e.message)
                    return@setNeutralButton
                }
                PensionePdf.condividi(this, f, "Analisi dieta")
            }
            .show()
    }

    private val STU_SEZ = listOf("Dashboard", "Clienti", "Agenda", "Prestazioni", "Fatture", "Cassa", "Magazzino", "Emittente")
    private val STU_NUOVO = listOf(
        "", "➕  Nuovo cliente", "➕  Appuntamento", "➕  Nuova prestazione", "➕  Nuovo documento", "➕  Nuovo movimento",
        "➕  Nuovo articolo", "",
    )

    private var mkVista = 0
    private var mkSuper: String? = null
    private var mkFiltro: String? = null
    private var mkTesto = ""
    private var mkTestoCat = ""
    private val MK_SEZ = listOf("🔎 Confronto prezzi", "🧺 Lista spesa", "📒 Catalogo")

    private fun renderMarket() {
        intestaLista("🛒 Supermercati", if (mkVista == 2) "➕  Nuovo articolo" else "", "🔀  Azioni")
        val cont = vista<LinearLayout>(R.id.listaGen)
        val n = Market.pulisciScadute(LocalDate.now())
        if (n > 0) aviso("Disattivate $n promozioni scadute")
        val db = Market.carica()
        cont.addView(
            pulsanteSel("Sezione:  " + MK_SEZ[mkVista] + "   ▾", true) {
                OcBuilder(this)
                    .setTitle("Sezione")
                    .setItems(MK_SEZ.toTypedArray()) { _, k ->
                        mkVista = k
                        renderLista()
                    }
                    .setNegativeButton("Annulla", null)
                    .show()
            },
            stuLp()
        )
        Market.errore()?.let { vuoto(cont, "⚠️ $it") }
        when (mkVista) {
            0 -> mkConfronto(cont, db)
            1 -> mkLista(cont)
            else -> mkCatalogo(cont, db)
        }
    }

    private fun mkCampoRicerca(cont: LinearLayout, testo: String, onCambia: (String) -> Unit): LinearLayout {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        val et = campoTesto(box, "Cerca articolo, categoria o descrizione", testo, InputType.TYPE_CLASS_TEXT, 40)
        cont.addView(box)
        val ris = LinearLayout(this)
        ris.orientation = LinearLayout.VERTICAL
        cont.addView(ris)
        et.doAfterTextChanged { onCambia(it?.toString() ?: "") }
        return ris
    }

    private fun mkConfronto(cont: LinearLayout, db: Map<String, List<org.json.JSONObject>>) {
        val nomi = Market.nomi(db)
        if (mkFiltro != null && mkFiltro !in nomi) mkFiltro = null
        cont.addView(
            pulsanteSel("Supermercato:  " + (mkFiltro ?: "Tutti") + "   ▾", false) {
                val v = listOf("Tutti i supermercati") + nomi
                OcBuilder(this)
                    .setTitle("Supermercato")
                    .setItems(v.toTypedArray()) { _, k ->
                        mkFiltro = if (k == 0) null else v[k]
                        renderLista()
                    }
                    .setNegativeButton("Annulla", null)
                    .show()
            },
            stuLp()
        )
        val verde = ContextCompat.getColor(this, R.color.oc_green)
        val rosso = ContextCompat.getColor(this, R.color.oc_red)
        lateinit var ris: LinearLayout
        fun aggiorna() {
            ris.removeAllViews()
            val l = Market.confronto(db, mkTesto, mkFiltro, LocalDate.now())
            if (l.isEmpty()) {
                vuoto(ris, "Nessun articolo trovato. Aggiungi gli articoli dalla sezione Catalogo.")
                return
            }
            vuoto(ris, l.size.toString() + " offerte  ·  ⭐ = prezzo più basso  ·  🔥 = promo")
            for (o in l.take(200)) {
                val titolo = (if (o.top) "⭐ " else "") + (if (o.inPromo) "🔥 " else "") + o.nome +
                    (if (o.quantita.isNotBlank()) " (" + o.quantita + ")" else "")
                val sotto = listOfNotNull(
                    o.supermercato,
                    o.categoria.takeIf { it.isNotBlank() },
                    when (o.stato) {
                        Market.Stato.ATTIVA -> "Promo fino al " + o.fine + (o.prezzoBase?.let { " · intero " + Market.eur(it) } ?: "")
                        Market.Stato.FUTURA -> "Promo dal " + o.inizio
                        Market.Stato.SCADUTA -> "Promo scaduta"
                        else -> null
                    },
                ).joinToString(" · ")
                riga(ris, titolo, sotto, Market.eur(o.prezzo), if (o.inPromo) rosso else if (o.top) verde else null) {
                    OcBuilder(this)
                        .setTitle(o.nome)
                        .setItems(arrayOf("➕  Aggiungi alla lista spesa")) { _, _ -> mkAggiungi(o) }
                        .setNegativeButton("Chiudi", null)
                        .show()
                }
            }
            if (l.size > 200) vuoto(ris, "Mostrate le prime 200: affina la ricerca.")
        }
        ris = mkCampoRicerca(cont, mkTesto) {
            mkTesto = it
            aggiorna()
        }
        aggiorna()
    }

    private fun mkAggiungi(o: Market.Offerta) {
        val m = Market.migliore(Market.carica(), o.nome, LocalDate.now())
        fun fatto(x: Market.Offerta) {
            val q = Market.aggiungiALista(x)
            aviso("✓ " + x.nome + " (" + x.supermercato + ") · quantità $q")
        }
        if (m != null && m.prezzo < o.prezzo && m.supermercato != o.supermercato) {
            OcBuilder(this)
                .setTitle("Conviene altrove!")
                .setMessage(
                    "«" + o.nome + "» costa " + Market.eur(o.prezzo) + " da " + o.supermercato + ".\n\n" +
                        "Lo trovi a " + Market.eur(m.prezzo) + " da " + m.supermercato + ".\n\nVuoi aggiungerlo al prezzo migliore?"
                )
                .setPositiveButton("Migliore") { _, _ -> fatto(m) }
                .setNegativeButton("Questo") { _, _ -> fatto(o) }
                .setNeutralButton("Annulla", null)
                .show()
        } else {
            fatto(o)
        }
    }

    private fun mkLista(cont: LinearLayout) {
        val l = Market.leggiLista()
        if (l.isEmpty()) {
            vuoto(cont, "Lista vuota. Dal Confronto tocca un articolo e scegli «Aggiungi alla lista spesa».")
            return
        }
        val gruppi = l.groupBy { it.supermercato }.toSortedMap()
        riga(
            cont, "Totale stimato",
            l.sumOf { it.qta }.toString() + " pezzi · " + gruppi.size + " supermercati",
            Market.eur(Market.totaleLista(l)), null, null
        )
        for ((sm, righe) in gruppi) {
            sezione(cont, "🛒 " + sm.uppercase() + "  ·  parziale " + Market.eur(righe.sumOf { it.totale }))
            for (r in righe.sortedBy { it.nome.lowercase() }) {
                riga(
                    cont, r.nome + (if (r.qtaCatalogo.isNotBlank()) " (" + r.qtaCatalogo + ")" else ""),
                    Market.eur(r.prezzoUn) + " × " + r.qta, Market.eur(r.totale), null
                ) { mkRigaLista(r) }
            }
        }
    }

    private fun mkRigaLista(r: Market.Riga) {
        OcBuilder(this)
            .setTitle(r.nome)
            .setItems(arrayOf("➕  Una in più", "➖  Una in meno", "✏️  Imposta quantità", "🗑️  Rimuovi dalla lista")) { _, k ->
                when (k) {
                    0 -> Market.impostaQta(r.nome, r.supermercato, r.qta + 1)
                    1 -> Market.impostaQta(r.nome, r.supermercato, r.qta - 1)
                    2 -> null
                    else -> Market.impostaQta(r.nome, r.supermercato, 0)
                }.let { e ->
                    if (e != null) aviso(e)
                }
                if (k == 2) mkQuantita(r) else renderLista()
            }
            .setNegativeButton("Chiudi", null)
            .show()
    }

    private fun mkQuantita(r: Market.Riga) {
        val (view, corpo) = corpoDialogo()
        val et = campoTesto(corpo, "Quantità da comprare", r.qta.toString(), InputType.TYPE_CLASS_NUMBER, 4)
        OcBuilder(this)
            .setTitle(r.nome)
            .setView(view)
            .setPositiveButton("Salva") { _, _ ->
                val q = et.text.toString().trim().toIntOrNull()
                if (q == null || q < 0) {
                    aviso("Quantità non valida")
                    return@setPositiveButton
                }
                Market.impostaQta(r.nome, r.supermercato, q)?.let { aviso(it) }
                renderLista()
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun mkCatalogo(cont: LinearLayout, db: Map<String, List<org.json.JSONObject>>) {
        val nomi = Market.nomi(db)
        if (mkSuper == null || mkSuper !in nomi) mkSuper = nomi.firstOrNull()
        val sm = mkSuper ?: return
        cont.addView(
            pulsanteSel("Supermercato:  $sm   ▾", false) {
                OcBuilder(this)
                    .setTitle("Supermercato")
                    .setItems(nomi.toTypedArray()) { _, k ->
                        mkSuper = nomi[k]
                        renderLista()
                    }
                    .setNegativeButton("Annulla", null)
                    .show()
            },
            stuLp()
        )
        val verde = ContextCompat.getColor(this, R.color.oc_green)
        val tutti = db[sm] ?: emptyList()
        lateinit var ris: LinearLayout
        fun aggiorna() {
            ris.removeAllViews()
            val t = mkTestoCat.trim().lowercase()
            val oggi = LocalDate.now()
            val l = tutti.withIndex().filter { (_, a) ->
                t.isEmpty() || t in Market.s(a, "nome").lowercase() || t in Market.s(a, "categoria").lowercase() ||
                    t in Market.s(a, "descrizione").lowercase()
            }
            vuoto(ris, tutti.size.toString() + " articoli in " + sm + if (t.isNotEmpty()) "  ·  " + l.size + " trovati" else "")
            if (tutti.isEmpty()) vuoto(ris, "Catalogo vuoto. Usa ➕ Nuovo articolo.")
            for ((idx, a) in l.take(300)) {
                val st = Market.statoPromo(a, oggi)
                val pr = Market.prezzo(a, "prezzo")
                val pp = Market.prezzo(a, "prezzo_promo")
                val nome = Market.s(a, "nome")
                val sotto = listOfNotNull(
                    Market.s(a, "categoria").takeIf { it.isNotBlank() },
                    Market.s(a, "descrizione").takeIf { it.isNotBlank() },
                    Market.s(a, "data_inserimento_prezzo").takeIf { it.isNotBlank() }?.let { "prezzo del $it" },
                    when (st) {
                        Market.Stato.ATTIVA -> "🔥 promo fino al " + Market.s(a, "data_scadenza")
                        Market.Stato.FUTURA -> "promo dal " + Market.s(a, "data_inizio_promo")
                        Market.Stato.SCADUTA -> "promo scaduta"
                        else -> null
                    },
                ).joinToString(" · ")
                val valore = if (st == Market.Stato.ATTIVA && pp != null) Market.eur(pp) else if (pr != null) Market.eur(pr) else "—"
                riga(
                    ris, nome + (if (Market.s(a, "quantita").isNotBlank()) " (" + Market.s(a, "quantita") + ")" else ""),
                    sotto, valore, if (st == Market.Stato.ATTIVA) verde else null
                ) {
                    OcBuilder(this)
                        .setTitle(nome)
                        .setItems(arrayOf("✏️  Modifica", "↔️  Sposta in un altro supermercato", "🗑️  Elimina")) { _, k ->
                            when (k) {
                                0 -> dialogMkArticolo(sm, idx, a)
                                1 -> mkSposta(sm, idx, nome, nomi)
                                else -> stuConferma("Eliminare l'articolo?", nome, "Elimina") {
                                    val e = Market.elimina(sm, idx, nome)
                                    if (e != null) aviso(e) else {
                                        aviso("Articolo eliminato")
                                        renderLista()
                                    }
                                }
                            }
                        }
                        .setNegativeButton("Chiudi", null)
                        .show()
                }
            }
            if (l.size > 300) vuoto(ris, "Mostrati i primi 300: affina la ricerca.")
        }
        ris = mkCampoRicerca(cont, mkTestoCat) {
            mkTestoCat = it
            aggiorna()
        }
        aggiorna()
    }

    private fun mkSposta(sm: String, idx: Int, nome: String, nomi: List<String>) {
        val dest = nomi.filter { it != sm }
        if (dest.isEmpty()) {
            aviso("Non ci sono altri supermercati: creane uno dalle Azioni")
            return
        }
        OcBuilder(this)
            .setTitle("Sposta «$nome» in…")
            .setItems(dest.toTypedArray()) { _, k ->
                val e = Market.sposta(sm, idx, nome, dest[k])
                if (e != null) aviso(e) else {
                    aviso("Spostato in " + dest[k])
                    renderLista()
                }
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun mkNuovo() {
        if (mkVista != 2) return
        val sm = mkSuper
        if (sm == null) {
            aviso("Crea prima un supermercato dalle Azioni")
            return
        }
        dialogMkArticolo(sm, -1, null)
    }

    private fun dialogMkArticolo(sm: String, idx: Int, a: org.json.JSONObject?) {
        val (view, corpo) = corpoDialogo()
        fun v(k: String) = if (a == null) "" else Market.s(a, k)
        val etNome = campoTesto(corpo, "Nome articolo", v("nome"), InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES, 27)
        val etDesc = campoTesto(corpo, "Descrizione", v("descrizione"), InputType.TYPE_CLASS_TEXT, 35)
        val cat0 = v("categoria").ifBlank { Market.CATEGORIE.first() }
        val cats = if (cat0 in Market.CATEGORIE) Market.CATEGORIE else Market.CATEGORIE + cat0
        val tilCat = campoMenu(corpo, "Categoria", cats, cat0)
        val etPrezzo = campoTesto(corpo, "Prezzo (€)", v("prezzo"), TIPO_DECIMALE, 7)
        val etQta = campoTesto(corpo, "Quantità / formato (es. 500g)", v("quantita"), InputType.TYPE_CLASS_TEXT, 18)
        var dIns: LocalDate? = Market.data(v("data_inserimento_prezzo")) ?: LocalDate.now()
        bottoneData(corpo, "Data prezzo", dIns, false) { dIns = it }
        val tilPromo = campoMenu(corpo, "Promozione", listOf("No", "Sì"), if (a != null && Market.promoOn(a)) "Sì" else "No")
        val etPP = campoTesto(corpo, "Prezzo promo (€)", v("prezzo_promo"), TIPO_DECIMALE, 7)
        var dIni: LocalDate? = Market.data(v("data_inizio_promo"))
        bottoneData(corpo, "Inizio promo (pressione lunga = nessuna)", dIni, true) { dIni = it }
        var dFin: LocalDate? = Market.data(v("data_scadenza"))
        bottoneData(corpo, "Scadenza promo (pressione lunga = nessuna)", dFin, true) { dFin = it }
        OcBuilder(this)
            .setTitle(if (a == null) "Nuovo articolo · $sm" else "Modifica articolo")
            .setView(view)
            .setPositiveButton("Salva") { _, _ ->
                val f = Market.Form(
                    etNome.text.toString(), etDesc.text.toString(), stuTesto(tilCat), etPrezzo.text.toString(),
                    etQta.text.toString(), stuTesto(tilPromo) == "Sì", etPP.text.toString(),
                    dIni?.format(Market.DMY) ?: "", dFin?.format(Market.DMY) ?: "", dIns?.format(Market.DMY) ?: "",
                )
                val errori = Market.valida(f)
                if (errori.isNotEmpty()) {
                    aviso(errori.joinToString("\n"))
                    return@setPositiveButton
                }
                val nuovo = Market.nuovoArticolo(f, sm, LocalDate.now(), a)
                val e = if (a == null) Market.aggiungi(sm, nuovo) else Market.modifica(sm, idx, Market.s(a, "nome"), nuovo)
                if (e != null) aviso(e) else {
                    aviso("Articolo salvato")
                    renderLista()
                }
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun mkPdf(crea: () -> java.io.File, titolo: String) {
        try {
            PensionePdf.condividi(this, crea(), titolo)
        } catch (e: Exception) {
            aviso("Impossibile creare il PDF: " + (e.message ?: "errore"))
        }
    }

    private fun mkTestoInput(titolo: String, iniziale: String, ok: String, onOk: (String) -> Unit) {
        val (view, corpo) = corpoDialogo()
        val et = campoTesto(corpo, "Nome supermercato", iniziale, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS, 30)
        OcBuilder(this)
            .setTitle(titolo)
            .setView(view)
            .setPositiveButton(ok) { _, _ -> onOk(et.text.toString()) }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun menuMarket() {
        val voci = ArrayList<String>()
        val az = ArrayList<() -> Unit>()
        fun add(t: String, f: () -> Unit) {
            voci.add(t)
            az.add(f)
        }
        val sm = mkSuper
        when (mkVista) {
            1 -> {
                val l = Market.leggiLista()
                if (l.isNotEmpty()) {
                    add("📤  Condividi la lista (PDF)") { mkPdf({ MarketPdf.lista(this, l, LocalDate.now()) }, "Lista spesa") }
                    add("🗑️  Svuota la lista") {
                        stuConferma("Svuotare la lista?", "Verranno rimossi tutti gli articoli dalla lista spesa.", "Svuota") {
                            Market.salvaLista(emptyList())
                            renderLista()
                        }
                    }
                }
            }
            2 -> {
                add("🏪  Nuovo supermercato") {
                    mkTestoInput("Nuovo supermercato", "", "Crea") { n ->
                        val e = Market.nuovoSuper(n)
                        if (e != null) aviso(e) else {
                            mkSuper = n.trim()
                            renderLista()
                        }
                    }
                }
                if (sm != null) {
                    add("✏️  Rinomina «$sm»") {
                        mkTestoInput("Rinomina supermercato", sm, "Salva") { n ->
                            val e = Market.rinomina(sm, n)
                            if (e != null) aviso(e) else {
                                mkSuper = n.trim()
                                renderLista()
                            }
                        }
                    }
                    add("📤  Condividi catalogo «$sm» (PDF)") {
                        mkPdf({ MarketPdf.catalogo(this, sm, Market.carica()[sm] ?: emptyList()) }, "Catalogo $sm")
                    }
                    add("🧹  Svuota gli articoli di «$sm»") {
                        stuConferma("Azzerare il catalogo?", "Cancellare TUTTI gli articoli di «$sm»?", "Azzera") {
                            val e = Market.svuota(sm)
                            if (e != null) aviso(e) else renderLista()
                        }
                    }
                    add("🗑️  Elimina il supermercato «$sm»") {
                        stuConferma("Eliminare il supermercato?", "«$sm» verrà rimosso (solo se è vuoto).", "Elimina") {
                            val e = Market.eliminaSuper(sm)
                            if (e != null) aviso(e) else {
                                mkSuper = null
                                renderLista()
                            }
                        }
                    }
                }
            }
        }
        add("🔄  Aggiorna") { refresh() }
        OcBuilder(this)
            .setTitle("Azioni")
            .setItems(voci.toTypedArray()) { _, k -> az[k]() }
            .setNegativeButton("Chiudi", null)
            .show()
    }

    private var stkTesto = ""
    private var stkCat = Stock.TUTTE_CAT
    private var stkStato = Stock.STATI_FILTRO[0]
    private var stkSel: MutableSet<String>? = null

    private fun renderStock() {
        intestaLista("📦 StockBox", "➕  Nuovo articolo", "🔀  Azioni")
        val cont = vista<LinearLayout>(R.id.listaGen)
        val dati = Stock.carica()
        Stock.errore()?.let { vuoto(cont, "⚠️ $it") }
        val cats = listOf(Stock.TUTTE_CAT) + dati.tutteCategorie()
        if (stkCat !in cats) stkCat = Stock.TUTTE_CAT
        cont.addView(pulsanteSel("📷  Scansiona codice a barre", false) { stkScansiona() }, stuLp())
        cont.addView(
            pulsanteSel("Stato:  $stkStato   ▾", true) {
                OcBuilder(this)
                    .setTitle("Stato")
                    .setItems(Stock.STATI_FILTRO.toTypedArray()) { _, k ->
                        stkStato = Stock.STATI_FILTRO[k]
                        renderLista()
                    }
                    .setNegativeButton("Annulla", null)
                    .show()
            },
            stuLp()
        )
        cont.addView(
            pulsanteSel("Categoria:  $stkCat   ▾", false) {
                OcBuilder(this)
                    .setTitle("Categoria")
                    .setItems(cats.toTypedArray()) { _, k ->
                        stkCat = cats[k]
                        renderLista()
                    }
                    .setNegativeButton("Annulla", null)
                    .show()
            },
            stuLp()
        )
        val rosso = ContextCompat.getColor(this, R.color.oc_red)
        val oroSel = ContextCompat.getColor(this, R.color.oc_gold)
        lateinit var ris: LinearLayout
        fun aggiorna() {
            ris.removeAllViews()
            val l = Stock.filtra(dati, stkTesto, stkCat, stkStato)
            if (dati.articoli.isEmpty()) {
                vuoto(ris, "Magazzino vuoto. Usa ➕ Nuovo articolo.")
                return
            }
            val r = Stock.riepilogo(l)
            riga(
                ris, "Valore in magazzino",
                listOfNotNull(
                    "${r.n} su ${dati.articoli.size} articoli",
                    if (r.sotto > 0) "${r.sotto} sotto scorta" else null,
                    if (r.esauriti > 0) "${r.esauriti} esauriti" else null,
                    if (r.daOrdinare > 0) "${r.daOrdinare} da riordinare" else null,
                    if (r.scaduti > 0) "${r.scaduti} scaduti" else null,
                    if (r.inScadenza > 0) "${r.inScadenza} in scadenza" else null,
                ).joinToString(" · "),
                Stock.eur(r.valore), null, null
            )
            stkSel?.let { sel ->
                riga(
                    ris, "☑️ Selezionati: " + sel.size,
                    "Tocca gli articoli per selezionarli, poi usa Azioni", "Azioni", oroSel
                ) { stkAzioniMulti() }
                riga(ris, "Seleziona tutti i mostrati", l.size.toString() + " articoli", "Tutti", null) {
                    sel.addAll(l.map { Stock.s(it, "id") })
                    aggiorna()
                }
                riga(ris, "Esci dalla selezione", "", "✖", null) {
                    stkSel = null
                    renderLista()
                }
            }
            if (l.isEmpty()) {
                vuoto(ris, "Nessun articolo trovato.")
                return
            }
            for (a in l.take(300)) {
                val st = Stock.stato(a)
                val sc = Stock.scadenza(a)
                val tag = Stock.tagRiga(a)
                val ico = when (tag) {
                    "esaurito" -> "⛔ "
                    "sotto" -> "🔻 "
                    "eccesso" -> "🔺 "
                    "scaduto" -> "❌ "
                    "scadenza" -> "⏳ "
                    else -> ""
                }
                val mx = Stock.d(a, "qta_max")
                val sotto = listOfNotNull(
                    Stock.s(a, "categoria").takeIf { it.isNotBlank() },
                    st.nome,
                    "min " + Stock.q(Stock.d(a, "qta_min")) + if (mx > 0) " · max " + Stock.q(mx) else "",
                    if (st.daOrdinare > 0) "ordina " + Stock.q(st.daOrdinare) else null,
                    if (sc.testo.isNotEmpty()) "scad. " + sc.testo else null,
                    if (Stock.d(a, "prezzo") > 0) Stock.eur(Stock.d(a, "quantita") * Stock.d(a, "prezzo")) else null,
                ).joinToString(" · ")
                val critico = tag == "esaurito" || tag == "sotto" || tag == "scaduto"
                val id = Stock.s(a, "id")
                val spunta = stkSel?.let { if (id in it) "☑ " else "☐ " } ?: ""
                riga(
                    ris, spunta + ico + Stock.s(a, "nome"), sotto,
                    Stock.q(Stock.d(a, "quantita")) + " " + Stock.s(a, "unita"),
                    if (critico) rosso else null
                ) {
                    val corrente = stkSel
                    if (corrente == null) stkMenuArticolo(a) else {
                        if (!corrente.add(id)) corrente.remove(id)
                        aggiorna()
                    }
                }
                ris.getChildAt(ris.childCount - 1).setOnLongClickListener {
                    val corrente = stkSel ?: mutableSetOf<String>().also { stkSel = it }
                    corrente.add(id)
                    aggiorna()
                    true
                }
            }
            if (l.size > 300) vuoto(ris, "Mostrati i primi 300: affina la ricerca.")
        }
        ris = mkCampoRicerca(cont, stkTesto) {
            stkTesto = it
            aggiorna()
        }
        aggiorna()
    }

    private fun stkAzioniMulti() {
        val sel = stkSel ?: return
        if (sel.isEmpty()) {
            aviso("Seleziona almeno un articolo")
            return
        }
        OcBuilder(this)
            .setTitle("Azioni su " + sel.size + " articoli")
            .setItems(arrayOf("✏️  Cambia categoria e scorte", "🗑️  Elimina selezionati")) { _, k ->
                if (k == 0) stkModificaMulti(sel.toSet()) else stuConferma(
                    "Eliminare gli articoli?", sel.size.toString() + " articoli verranno eliminati dal magazzino.", "Elimina"
                ) {
                    val e = Stock.eliminaMultiplo(sel.toSet())
                    if (e != null) aviso(e) else {
                        aviso("Articoli eliminati")
                        stkSel = null
                        renderLista()
                    }
                }
            }
            .setNegativeButton("Chiudi", null)
            .show()
    }

    private fun stkModificaMulti(ids: Set<String>) {
        val nonCambiare = "— non cambiare —"
        val nessuna = "— nessuna categoria —"
        val cats = listOf(nonCambiare, nessuna) + Stock.carica().tutteCategorie()
        val (view, corpo) = corpoDialogo()
        var scelta = nonCambiare
        campoMenu(corpo, "Categoria", cats, nonCambiare) { scelta = cats[it] }
        val etMin = campoTesto(corpo, "Scorta minima (vuoto = non cambiare)", "", TIPO_DECIMALE)
        val etMax = campoTesto(corpo, "Scorta massima (vuoto = non cambiare, 0 = nessun massimo)", "", TIPO_DECIMALE)
        OcBuilder(this)
            .setTitle("Modifica " + ids.size + " articoli")
            .setView(view)
            .setNegativeButton("Annulla", null)
            .setPositiveButton("Applica") { _, _ ->
                val tMin = etMin.text.toString().trim()
                val tMax = etMax.text.toString().trim()
                val mn = if (tMin.isEmpty()) null else Stock.numOrNull(tMin)
                val mx = if (tMax.isEmpty()) null else Stock.numOrNull(tMax)
                if ((tMin.isNotEmpty() && mn == null) || (tMax.isNotEmpty() && mx == null)) {
                    aviso("Numero non valido nelle scorte")
                    return@setPositiveButton
                }
                val cat = when (scelta) { nonCambiare -> null; nessuna -> ""; else -> scelta }
                val e = Stock.modificaMultipla(ids, cat, mn, mx)
                if (e != null) aviso(e) else {
                    aviso(ids.size.toString() + " articoli modificati")
                    stkSel = null
                    renderLista()
                }
            }
            .show()
    }

    private fun stkScansiona() {
        scansionaCodice { cod ->
            val trovati = Stock.trovaPerCodice(Stock.carica(), cod)
            when {
                trovati.size == 1 -> stkMenuArticolo(trovati[0])
                trovati.size > 1 -> OcBuilder(this)
                    .setTitle("Codice $cod")
                    .setItems(trovati.map { Stock.s(it, "nome") + " · " + Stock.q(Stock.d(it, "quantita")) + " " + Stock.s(it, "unita") }.toTypedArray()) { _, k ->
                        stkMenuArticolo(trovati[k])
                    }
                    .setNegativeButton("Annulla", null)
                    .show()
                else -> OcBuilder(this)
                    .setTitle("Codice non trovato")
                    .setMessage("Nessun articolo con il codice $cod.")
                    .setPositiveButton("Nuovo articolo") { _, _ -> dialogStkArticolo(null, cod) }
                    .setNeutralButton("Cerca") { _, _ ->
                        stkTesto = cod
                        renderLista()
                    }
                    .setNegativeButton("Annulla", null)
                    .show()
            }
        }
    }

    private fun stkMenuArticolo(a: org.json.JSONObject) {
        val id = Stock.s(a, "id")
        val nome = Stock.s(a, "nome")
        OcBuilder(this)
            .setTitle(nome)
            .setItems(arrayOf("🔄  Carico / Scarico", "📜  Storico movimenti", "✏️  Modifica", "📄  Duplica", "🗑️  Elimina")) { _, k ->
                when (k) {
                    0 -> dialogStkMovimento(a)
                    1 -> stkStorico(a)
                    2 -> dialogStkArticolo(a)
                    3 -> {
                        val e = Stock.duplica(id)
                        if (e != null) aviso(e) else {
                            aviso("Articolo duplicato")
                            renderLista()
                        }
                    }
                    else -> stuConferma("Eliminare l'articolo?", nome, "Elimina") {
                        val e = Stock.elimina(id)
                        if (e != null) aviso(e) else {
                            aviso("Articolo eliminato")
                            renderLista()
                        }
                    }
                }
            }
            .setNegativeButton("Chiudi", null)
            .show()
    }

    private fun stkStorico(a: org.json.JSONObject) {
        val mov = a.optJSONArray("movimenti")
        val righe = ArrayList<String>()
        if (mov != null) for (i in mov.length() - 1 downTo 0) {
            val m = mov.optJSONObject(i) ?: continue
            val dl = Stock.d(m, "delta")
            righe.add(
                Stock.s(m, "data") + "   " + (if (dl > 0) "+" else "") + Stock.q(dl) + "   →  " + Stock.q(Stock.d(m, "qta")) +
                    (Stock.s(m, "nota").takeIf { it.isNotBlank() }?.let { "\n" + it } ?: "")
            )
        }
        if (righe.isEmpty()) {
            aviso("Nessun movimento registrato")
            return
        }
        OcBuilder(this)
            .setTitle("Storico · " + Stock.s(a, "nome"))
            .setItems(righe.toTypedArray(), null)
            .setNegativeButton("Chiudi", null)
            .show()
    }

    private fun dialogStkMovimento(a: org.json.JSONObject) {
        val (view, corpo) = corpoDialogo()
        val id = Stock.s(a, "id")
        val um = Stock.s(a, "unita")
        val ord = Stock.stato(a).daOrdinare
        val tipi = arrayListOf("➕ Carico (entrata)", "➖ Scarico (uscita)")
        val voceRiord = "📥 Carico riordino (+" + Stock.q(ord) + " $um)"
        if (ord > 0) tipi.add(voceRiord)
        val tv = TextView(ctxDialogo)
        tv.text = "Giacenza attuale: " + Stock.q(Stock.d(a, "quantita")) + " $um"
        tv.setPadding(4, 4, 4, 8)
        corpo.addView(tv)
        val tilTipo = campoMenu(corpo, "Tipo di movimento", tipi, tipi[0])
        val etQ = campoTesto(corpo, "Quantità", "", TIPO_DECIMALE, 10)
        val etNota = campoTesto(corpo, "Nota (facoltativa)", "", InputType.TYPE_CLASS_TEXT, 28)
        OcBuilder(this)
            .setTitle(Stock.s(a, "nome"))
            .setView(view)
            .setPositiveButton("Registra") { _, _ ->
                val t = stuTesto(tilTipo)
                val e: String?
                if (t == voceRiord) {
                    val r = Stock.caricaRiordino(listOf(id))
                    e = r.second
                } else {
                    val q = Stock.numOrNull(etQ.text.toString())
                    if (q == null || q <= 0) {
                        aviso("Inserisci una quantità maggiore di zero")
                        return@setPositiveButton
                    }
                    e = Stock.movimento(id, if (t.startsWith("➖")) -q else q, etNota.text.toString())
                }
                if (e != null) aviso(e) else {
                    aviso("Movimento registrato")
                    renderLista()
                }
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun dialogStkArticolo(a: org.json.JSONObject?, codiceIniz: String = "") {
        val dati = Stock.carica()
        val (view, corpo) = corpoDialogo()
        fun v(k: String) = if (a == null) "" else Stock.s(a, k)
        fun n(k: String) = if (a == null) "" else Stock.q(Stock.d(a, k)).replace(".", "").replace(',', '.')
        val etNome = campoTesto(corpo, "Articolo", v("nome"), InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES, Stock.MAX_NOME)
        val etCod = campoTesto(corpo, "Codice (facoltativo)", v("codice").ifBlank { codiceIniz }, InputType.TYPE_CLASS_TEXT, Stock.MAX_CODICE)
        val btnCod = MaterialButton(ctxDialogo, null, com.google.android.material.R.attr.materialButtonOutlinedStyle)
        btnCod.text = "📷 Scansiona codice a barre"
        btnCod.isAllCaps = false
        corpo.addView(btnCod)
        btnCod.setOnClickListener { scansionaCodice { v -> etCod.setText(v) } }
        val cat0 = v("categoria")
        val cats = (listOf("") + dati.tutteCategorie()).let { if (cat0 !in it) it + cat0 else it }
        val tilCat = campoMenu(corpo, "Categoria", cats, cat0)
        val etDesc = campoTesto(corpo, "Descrizione", if (a == null) "" else (a.optString("descrizione", "")), InputType.TYPE_CLASS_TEXT, Stock.MAX_DESC)
        val um0 = v("unita").ifBlank { "pz" }
        val tilUm = campoMenu(corpo, "Unità di misura", if (um0 in Stock.UNITA) Stock.UNITA else Stock.UNITA + um0, um0)
        val etQ = campoTesto(corpo, "Quantità in magazzino", if (a == null) "0" else n("quantita"), TIPO_DECIMALE, 10)
        val etMin = campoTesto(corpo, "Quantità minima (scorta)", if (a == null) "0" else n("qta_min"), TIPO_DECIMALE, 10)
        val etMax = campoTesto(corpo, "Quantità massima (0 = nessuna)", if (a == null) "0" else n("qta_max"), TIPO_DECIMALE, 10)
        val etPr = campoTesto(corpo, "Prezzo unitario (€)", if (a == null) "0" else Stock.itNum(Stock.d(a, "prezzo")).replace(".", "").replace(',', '.'), TIPO_DECIMALE, 10)
        var dScad: LocalDate? = Stock.data(v("scadenza"))
        bottoneData(corpo, "Scadenza (pressione lunga = nessuna)", dScad, true) { dScad = it }
        var dData: LocalDate? = Stock.data(v("data")) ?: LocalDate.now()
        bottoneData(corpo, "Data (se invariata si aggiorna a oggi)", dData, false) { dData = it }
        fun salva(ignoraDoppione: Boolean) {
            val f = Stock.Form(
                etNome.text.toString(), etCod.text.toString(), stuTesto(tilCat), etDesc.text.toString(), stuTesto(tilUm),
                etQ.text.toString(), etMin.text.toString(), etMax.text.toString(), etPr.text.toString(),
                dScad?.format(Stock.DMY) ?: "", dData?.format(Stock.DMY) ?: "",
            )
            val (err, campi) = Stock.leggi(f)
            if (err != null || campi == null) {
                aviso(err ?: "Dati non validi")
                return
            }
            if (a == null && !ignoraDoppione && Stock.doppione(dati, campi)) {
                stuConferma("Articolo già presente", "Esiste già «" + Stock.s(campi, "nome") + "» in questa categoria.\nAggiungerlo comunque?", "Aggiungi") { salva(true) }
                return
            }
            val e = if (a == null) Stock.aggiungi(campi) else Stock.modifica(Stock.s(a, "id"), campi)
            if (e != null) aviso(e) else {
                aviso(if (a == null) "Articolo aggiunto" else "Articolo modificato")
                renderLista()
            }
        }
        OcBuilder(this)
            .setTitle(if (a == null) "Nuovo articolo" else "Modifica articolo")
            .setView(view)
            .setPositiveButton("Salva") { _, _ -> salva(false) }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun stkPdf(crea: () -> java.io.File, titolo: String) {
        try {
            StockPdf.condividi(this, crea(), titolo)
        } catch (e: Exception) {
            aviso("Impossibile creare il PDF: " + (e.message ?: "errore"))
        }
    }

    private fun stkRiordino() {
        val dati = Stock.carica()
        val t = Stock.testoRiordino(dati)
        if (t.isEmpty()) {
            aviso("Nessun articolo da riordinare")
            return
        }
        OcBuilder(this)
            .setTitle("Lista riordino")
            .setMessage(t)
            .setPositiveButton("Condividi PDF") { _, _ -> stkPdf({ StockPdf.riordino(this, dati) }, "Lista riordino") }
            .setNegativeButton("Chiudi", null)
            .show()
    }

    private fun stkInListaSpesa() {
        val r = Stock.righeRiordino(Stock.carica())
        if (r.isEmpty()) {
            aviso("Nessun articolo da riordinare")
            return
        }
        stuConferma(
            "Lista spesa",
            "Aggiungere alla lista spesa di Supermercati i " + r.size + " articoli da riordinare? " +
                "Quelli che trovo nel catalogo usano l'offerta migliore, gli altri vanno sotto «" + Market.DA_ASSEGNARE + "».",
            "Aggiungi",
        ) {
            val (nelCatalogo, senza, errore) = Market.aggiungiRiordino(r, LocalDate.now())
            if (errore != null) aviso(errore) else aviso("Aggiunti $nelCatalogo dal catalogo e $senza da assegnare")
        }
    }

    private fun stkCaricaTutto() {
        val dati = Stock.carica()
        val r = Stock.righeRiordino(dati)
        if (r.isEmpty()) {
            aviso("Nessun articolo da riordinare")
            return
        }
        stuConferma("Carico riordino", "Caricare in magazzino la quantità di riordino di " + r.size + " articoli?", "Carica") {
            val (n, e) = Stock.caricaRiordino(r.map { Stock.s(it.first, "id") })
            if (e != null) aviso(e) else {
                aviso("Caricati $n articoli")
                renderLista()
            }
        }
    }

    private fun stkScorte() {
        val dati = Stock.carica()
        val sug = Stock.suggerimenti(Stock.filtra(dati, stkTesto, stkCat, stkStato))
        if (sug.isEmpty()) {
            aviso("Nessuna scorta da aggiornare (servono almeno 2 scarichi negli ultimi 90 giorni)")
            return
        }
        val testo = sug.joinToString("\n") {
            "• " + it.nome + ": min " + Stock.q(it.attuale) + " → " + Stock.q(it.suggerita) + " " + it.unita +
                " (consumo " + Stock.q(Math.round(it.alGiorno * 7 * 100) / 100.0) + "/sett.)" + if (it.sfora) "  ⚠ supera il max, escluso" else ""
        }
        OcBuilder(this)
            .setTitle("Scorta minima suggerita")
            .setMessage(testo)
            .setPositiveButton("Applica") { _, _ ->
                val (n, e) = Stock.applicaSuggerimenti(sug.map { it.id })
                if (e != null) aviso(e) else {
                    aviso("Scorta minima aggiornata su $n articoli")
                    renderLista()
                }
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun stkCategorie() {
        val dati = Stock.carica()
        val cc = Stock.conteggioCategorie(dati)
        val voci = arrayListOf("➕  Nuova categoria", "📚  Aggiungi categorie suggerite")
        for ((c, n) in cc) voci.add("$c  ($n)")
        OcBuilder(this)
            .setTitle("Categorie")
            .setItems(voci.toTypedArray()) { _, k ->
                when (k) {
                    0 -> mkTestoInput("Nuova categoria", "", "Crea") { n ->
                        val e = Stock.nuovaCategoria(n)
                        if (e != null) aviso(e) else renderLista()
                    }
                    1 -> {
                        val prof = Stock.PROFILI.keys.toList()
                        OcBuilder(this)
                            .setTitle("Profilo di utilizzo")
                            .setItems(prof.toTypedArray()) { _, j ->
                                val n = Stock.aggiungiCategorie(Stock.PROFILI[prof[j]] ?: emptyList())
                                aviso(if (n > 0) "$n categorie aggiunte" else "Categorie già presenti")
                                renderLista()
                            }
                            .setNegativeButton("Annulla", null)
                            .show()
                    }
                    else -> {
                        val (c, n) = cc[k - 2]
                        OcBuilder(this)
                            .setTitle(c)
                            .setItems(arrayOf("✏️  Rinomina / unisci", "🗑️  Elimina")) { _, j ->
                                if (j == 0) mkTestoInput("Rinomina categoria", c, "Salva") { nuovo ->
                                    val e = Stock.rinominaCategoria(c, nuovo)
                                    if (e != null) aviso(e) else renderLista()
                                } else stuConferma(
                                    "Eliminare la categoria?",
                                    "«$c» verrà rimossa; i $n articoli collegati resteranno senza categoria.", "Elimina"
                                ) {
                                    val e = Stock.eliminaCategoria(c)
                                    if (e != null) aviso(e) else renderLista()
                                }
                            }
                            .setNegativeButton("Chiudi", null)
                            .show()
                    }
                }
            }
            .setNegativeButton("Chiudi", null)
            .show()
    }

    private fun menuStock() {
        val voci = ArrayList<String>()
        val az = ArrayList<() -> Unit>()
        fun add(t: String, f: () -> Unit) {
            voci.add(t)
            az.add(f)
        }
        add("📷  Scansiona codice a barre") { stkScansiona() }
        add("☑️  Modifica multipla") {
            stkSel = mutableSetOf()
            renderLista()
        }
        add("🧾  Lista riordino") { stkRiordino() }
        add("🛒  Riordino → lista spesa") { stkInListaSpesa() }
        add("📥  Carica tutto il riordino") { stkCaricaTutto() }
        add("📈  Scorta minima suggerita") { stkScorte() }
        add("🏷️  Categorie") { stkCategorie() }
        add("📤  Condividi inventario (PDF)") {
            val dati = Stock.carica()
            val l = Stock.filtra(dati, stkTesto, stkCat, stkStato)
            val filtrato = stkTesto.isNotBlank() || stkCat != Stock.TUTTE_CAT || stkStato != Stock.STATI_FILTRO[0]
            if (l.isEmpty()) aviso("Nessun articolo da esportare") else stkPdf({ StockPdf.inventario(this, l, filtrato) }, "Inventario magazzino")
        }
        add("🔙  Reset magazzino") {
            stuConferma("Reset magazzino", "Cancellare TUTTI gli articoli del magazzino?", "Cancella") {
                val e = Stock.reset()
                if (e != null) aviso(e) else {
                    aviso("Magazzino resettato")
                    renderLista()
                }
            }
        }
        add("🔄  Aggiorna") { refresh() }
        OcBuilder(this)
            .setTitle("Azioni")
            .setItems(voci.toTypedArray()) { _, k -> az[k]() }
            .setNegativeButton("Chiudi", null)
            .show()
    }

    private var infImp: String? = null
    private var infDa = 0
    private var infA = Inflazione.ULTIMO

    private fun renderInflazione() {
        intestaLista("📈 Inflazione ISTAT", "", "🔄  Reimposta")
        val cont = vista<LinearLayout>(R.id.listaGen)
        val uscite = Repo.voci().filter { it.tipo == "Uscita" }.map { Triple(it.data.year, it.data.monthValue, it.importo) }
        val impDef = Inflazione.mediaMensile(uscite).toString()
        val daDef = Inflazione.annoDefault(Repo.voci().minOfOrNull { it.data.year })
        if (infImp == null) {
            infImp = impDef
            infDa = daDef
            infA = Inflazione.ULTIMO
        }
        vuoto(cont, "Dati ISTAT NIC (variazione annua dei prezzi al consumo). Media mensile uscite: " + euro(impDef.toDouble()))
        val corpo = LinearLayout(this)
        corpo.orientation = LinearLayout.VERTICAL
        val etImp = campoTesto(corpo, "Importo iniziale (€)", infImp!!, TIPO_DECIMALE, 12)
        val anni = Inflazione.ANNI.map { it.toString() }
        val risultati = LinearLayout(this)
        risultati.orientation = LinearLayout.VERTICAL
        fun aggiorna() {
            risultati.removeAllViews()
            infImp = etImp.text.toString()
            val imp = Inflazione.importo(infImp!!)
            val e = if (imp == null) null else Inflazione.calcola(imp, infDa, infA)
            if (e == null) {
                vuoto(risultati, "Inserisci un importo valido e un anno di arrivo successivo a quello di partenza.")
                return
            }
            val verde = ContextCompat.getColor(this, R.color.oc_green)
            val rosso = ContextCompat.getColor(this, R.color.oc_red)
            fun pct(v: Double) = String.format(Locale.ITALY, "%+.1f %%", v)
            sezione(risultati, "RISULTATO")
            riga(risultati, "Valore rivalutato", "Equivale a " + euro(imp!!) + " del " + infDa, euro(e.rivalutato), verde, null)
            riga(risultati, "Potere d'acquisto perso", pct(e.totale) + " rispetto al " + infDa, euro(e.perdita), rosso, null)
            riga(risultati, "Inflazione totale", "", pct(e.totale), null, null)
            riga(risultati, "Anni", "", e.anni.toString(), null, null)
            riga(risultati, "Media annua", "", String.format(Locale.ITALY, "%.2f%%/a", e.media), null, null)
            vuoto(
                risultati,
                if (e.elevata) "⚠  Inflazione elevata nel periodo" else "✔  Inflazione moderata nel periodo"
            )
            sezione(risultati, "ANDAMENTO DEL VALORE")
            dietGrafico(
                risultati, GraficoView.Modo.LINEA, e.etichette, e.valori, e.valori.map { null },
                verde, verde, "€", "", 0, false,
            ) { i ->
                val t = Inflazione.NIC[e.etichette[i].toInt()]
                e.etichette[i] + ": " + euro(e.valori[i]) + (if (i > 0 && t != null) "  (inflazione " + pct(t) + ")" else "")
            }
        }
        campoMenu(corpo, "Anno di partenza", anni, infDa.toString()) {
            infDa = anni[it].toInt()
            aggiorna()
        }
        campoMenu(corpo, "Anno di arrivo", anni, infA.toString()) {
            infA = anni[it].toInt()
            aggiorna()
        }
        etImp.doAfterTextChanged { aggiorna() }
        cont.addView(corpo)
        cont.addView(risultati)
        aggiorna()
    }

    private fun stuLp() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

    private fun stuEsito(err: String?, ok: String) {
        if (err != null) aviso(err) else {
            refresh()
            aviso(ok)
        }
    }

    private fun stuTesto(til: TextInputLayout): String = til.editText?.text?.toString()?.trim() ?: ""

    private fun stuConferma(titolo: String, msg: String, ok: String, azione: () -> Unit) {
        OcBuilder(this)
            .setTitle(titolo)
            .setMessage(msg)
            .setNegativeButton("Annulla", null)
            .setPositiveButton(ok) { _, _ -> azione() }
            .show()
    }

    private fun stuCampi(
        corpo: LinearLayout, def: List<Triple<String, String, Int>>, iniz: (String) -> String,
    ): Map<String, TextInputEditText> {
        val m = LinkedHashMap<String, TextInputEditText>()
        for ((k, h, t) in def) m[k] = campoTesto(corpo, h, iniz(k), t)
        return m
    }

    private fun stuT(m: Map<String, TextInputEditText>, k: String): String = m[k]?.text?.toString()?.trim() ?: ""

    private fun stuCopia(o: JSONObject?): JSONObject = if (o == null) JSONObject().put("id", Studio.nuovoId()) else JSONObject(o.toString())

    private fun renderStudio() {
        intestaLista("💼 MyBusiness", STU_NUOVO[stuVista], "🔀  Azioni")
        val cont = vista<LinearLayout>(R.id.listaGen)
        cont.addView(
            pulsanteSel("Sezione:  " + STU_SEZ[stuVista] + "   ▾", true) {
                OcBuilder(this)
                    .setTitle("Sezione")
                    .setItems(STU_SEZ.toTypedArray()) { _, k ->
                        stuVista = k
                        renderLista()
                    }
                    .setNegativeButton("Annulla", null)
                    .show()
            },
            stuLp()
        )
        val corpo = LinearLayout(this)
        corpo.orientation = LinearLayout.VERTICAL
        when (stuVista) {
            0 -> stuDashboard(corpo)
            1 -> stuClienti(corpo)
            2 -> stuAgenda(corpo)
            3 -> stuPrestazioni(corpo)
            4 -> stuFatture(corpo)
            5 -> stuCassa(corpo)
            6 -> stuMagazzino(corpo)
            else -> stuEmittente(corpo)
        }
        for (e in Studio.errori()) vuoto(cont, "⚠️ $e")
        cont.addView(corpo)
    }

    private fun stuNuovo() {
        when (stuVista) {
            1 -> dialogCliente(null)
            2 -> dialogAppuntamento(null)
            3 -> dialogPrestazione(null)
            4 -> dialogDocumentoStudio(null)
            5 -> dialogMovCassa(null)
            6 -> dialogArticolo(null)
        }
    }

    private fun menuStudio() {
        val voci = ArrayList<String>()
        val az = ArrayList<() -> Unit>()
        fun add(t: String, f: () -> Unit) {
            voci.add(t)
            az.add(f)
        }
        if (stuVista in 1..6) add(STU_NUOVO[stuVista]) { stuNuovo() }
        if (stuVista == 2) {
            add("📅  Vai a oggi") {
                stuMese = YearMonth.now()
                stuGiorno = LocalDate.now()
                renderLista()
            }
            add("🗓️  Genera scadenze fiscali " + LocalDate.now().year) {
                val (n, err) = Studio.generaScadenze(LocalDate.now().year)
                if (err != null) aviso(err) else {
                    refresh()
                    aviso(if (n > 0) "Aggiunte $n scadenze fiscali" else "Scadenze fiscali già presenti in agenda")
                }
            }
        }
        if (stuVista == 6) add("📤  Esporta PDF magazzino") { stuPdfMagazzino() }
        if (stuVista == 7) add("✏️  Modifica dati emittente") { dialogEmittente() }
        add("🔄  Aggiorna") { refresh() }
        OcBuilder(this)
            .setTitle("Azioni")
            .setItems(voci.toTypedArray()) { _, k -> az[k]() }
            .setNegativeButton("Chiudi", null)
            .show()
    }

    private fun stuDashboard(cont: LinearLayout) {
        val oggi = LocalDate.now()
        val k = Studio.dashboard(oggi)
        val verde = ContextCompat.getColor(this, R.color.oc_green)
        val rosso = ContextCompat.getColor(this, R.color.oc_red)
        val arancio = ContextCompat.getColor(this, R.color.oc_orange)
        val emi = Studio.emittente()
        sezione(cont, "INDICATORI")
        riga(cont, "Fatturato del mese", Studio.MESI[oggi.monthValue] + " " + oggi.year, Studio.eur(k.fatMese), verde, null)
        riga(cont, "Fatturato dell'anno", "Imponibile, fatture emesse e pagate", Studio.eur(k.fatAnno), verde, null)
        riga(cont, "Da incassare", k.nAperte.toString() + " fatture aperte", Studio.eur(k.daIncassare), arancio) {
            stuFiltroDoc = "Emessa"
            stuVista = 4
            renderLista()
        }
        riga(cont, "Saldo cassa", "", Studio.eur(k.saldo), if (k.saldo >= 0) verde else rosso) {
            stuVista = 5
            renderLista()
        }
        riga(cont, "Appuntamenti oggi", k.appSett.toString() + " nei prossimi 7 giorni", k.appOggi.toString(), null) {
            stuMese = YearMonth.now()
            stuGiorno = oggi
            stuVista = 2
            renderLista()
        }
        riga(cont, "Magazzino sotto soglia", "Voci con quantità ≤ soglia minima", k.nSottoSoglia.toString(), if (k.nSottoSoglia > 0) rosso else verde) {
            stuVista = 6
            renderLista()
        }
        if (Studio.forfettario()) {
            sezione(cont, "REGIME FORFETTARIO")
            barra(cont, "Soglia fatturato annuo", k.fatAnno, Studio.sogliaForfettario(emi), "(soglia forfettario)")
            val t = Studio.stimaTasse(k.fatAnno, emi)
            if (t != null) {
                sezione(cont, "STIMA TASSE E CONTRIBUTI DA ACCANTONARE (ANNO CORRENTE)")
                riga(cont, "Reddito imponibile", Studio.fmtQ(Math.rint(t.coeff * 1000) / 10.0) + "% del fatturato", Studio.eur(t.reddito), null, null)
                riga(cont, "Imposta sostitutiva stimata", Studio.fmtQ(t.aliqSost * 100) + "%", Studio.eur(t.imposta), null, null)
                if (t.inps != null && t.aliqInps != null) {
                    riga(cont, "Contributi INPS stimati", String.format(Locale.ITALY, "%.2f", t.aliqInps * 100) + "%", Studio.eur(t.inps), null, null)
                } else {
                    riga(cont, "Contributi previdenziali", "Altra cassa", "da calcolare a parte", null, null)
                }
                riga(cont, "Totale stimato da accantonare", "", Studio.eur(t.totale), arancio, null)
                vuoto(cont, "Stima indicativa, non sostituisce il calcolo del tuo commercialista.")
            }
        }
        sezione(cont, "FATTURATO ULTIMI 6 MESI")
        val mesi = Studio.ultimiMesi(oggi, 6)
        dietGrafico(
            cont, GraficoView.Modo.BARRE_SEGNO, mesi.map { it.etichetta },
            mesi.map { it.valore as Double? }, mesi.map { null as Double? }, verde, rosso, "€ ", "", 0, true
        ) { i ->
            val m = mesi.getOrNull(i)
            if (m == null) "" else Studio.MESI[m.mese] + " " + m.anno + " · " + Studio.eur(m.valore)
        }
        sezione(cont, "PROSSIMI APPUNTAMENTI")
        val prossimi = Studio.lista(Studio.F_APP)
            .filter { Studio.s(it, "stato") != "Annullato" && Studio.s(it, "data") >= Studio.iso(oggi) }
            .sortedWith(compareBy({ Studio.s(it, "data") }, { Studio.s(it, "ora") }))
            .take(8)
        if (prossimi.isEmpty()) vuoto(cont, "Nessun appuntamento in programma.")
        for (a in prossimi) {
            riga(
                cont, Studio.dmy(Studio.s(a, "data")) + " " + Studio.s(a, "ora"),
                Studio.s(a, "cliente_nome") + " · " + Studio.s(a, "prestazione"), "", null
            ) {
                stuGiorno = Studio.dataIso(Studio.s(a, "data")) ?: oggi
                stuMese = YearMonth.from(stuGiorno)
                stuVista = 2
                renderLista()
            }
        }
    }

    private fun stuClienti(cont: LinearLayout) {
        val l = Studio.clientiOrdinati()
        if (l.isEmpty()) {
            vuoto(cont, "Nessun cliente. Usa ➕ Nuovo cliente.")
            return
        }
        for (c in l) {
            val nome = Studio.nomeCliente(c)
            val az = Studio.s(c, "azienda")
            val titolo = if (nome.isBlank()) az else if (az.isNotBlank()) "$nome · $az" else nome
            val sotto = listOf("telefono", "email", "cf_piva", "note").map { Studio.s(c, it) }.filter { it.isNotBlank() }.joinToString(" · ")
            riga(cont, titolo, sotto, "", null) { stuMenuCliente(c) }
        }
    }

    private fun stuMenuCliente(c: JSONObject) {
        val nome = Studio.nomeCliente(c).ifBlank { Studio.s(c, "azienda") }
        OcBuilder(this)
            .setTitle(nome)
            .setItems(arrayOf("✏️  Modifica", "📚  Storico", "🗑️  Elimina")) { _, k ->
                when (k) {
                    0 -> dialogCliente(c)
                    1 -> dialogStorico(c)
                    else -> stuConferma("Eliminare il cliente?", nome, "Elimina") {
                        stuEsito(Studio.elimina(Studio.F_CLI, Studio.s(c, "id")), "Cliente eliminato")
                    }
                }
            }
            .setNegativeButton("Chiudi", null)
            .show()
    }

    private fun dialogStorico(c: JSONObject) {
        val nome = Studio.nomeCliente(c).ifBlank { Studio.s(c, "azienda") }
        val (view, corpo) = corpoDialogo()
        fun titolo(t: String) {
            val tv = TextView(ctxDialogo)
            tv.text = t
            tv.setTypeface(null, Typeface.BOLD)
            tv.setPadding(4, 16, 4, 6)
            corpo.addView(tv)
        }
        titolo("APPUNTAMENTI")
        val app = Studio.appuntamentiCliente(nome)
        if (app.isEmpty()) {
            val tv = TextView(ctxDialogo)
            tv.text = "Nessuno"
            tv.setPadding(4, 4, 4, 4)
            corpo.addView(tv)
        }
        for (a in app) {
            rigaDlg(
                corpo, Studio.dmy(Studio.s(a, "data")) + " " + Studio.s(a, "ora"),
                listOf(Studio.s(a, "prestazione"), Studio.s(a, "note")).filter { it.isNotBlank() }.joinToString(" · "),
                Studio.s(a, "stato"), null, null
            )
        }
        titolo("FATTURE / PREVENTIVI")
        val docs = Studio.documentiCliente(nome)
        if (docs.isEmpty()) {
            val tv = TextView(ctxDialogo)
            tv.text = "Nessuno"
            tv.setPadding(4, 4, 4, 4)
            corpo.addView(tv)
        }
        for (f in docs) {
            rigaDlg(
                corpo, Studio.tipoDoc(f) + " " + Studio.s(f, "numero"),
                Studio.dmy(Studio.s(f, "data")) + " · " + Studio.s(f, "stato"), Studio.eur(Studio.totali(f).totale), null, null
            )
        }
        OcBuilder(this).setTitle("Storico · $nome").setView(view).setPositiveButton("Chiudi", null).show()
    }

    private fun dialogCliente(c: JSONObject?) {
        val (view, corpo) = corpoDialogo()
        val t = InputType.TYPE_CLASS_TEXT
        val def = listOf(
            Triple("nome", "Nome *", t), Triple("cognome", "Cognome", t), Triple("azienda", "Azienda", t),
            Triple("telefono", "Telefono", InputType.TYPE_CLASS_PHONE),
            Triple("email", "Email", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS),
            Triple("cf_piva", "CF / P.IVA", t), Triple("indirizzo", "Indirizzo", t),
            Triple("cod_destinatario", "Codice destinatario SDI", t), Triple("pec", "PEC", t), Triple("note", "Note", t),
        )
        val m = stuCampi(corpo, def) { k -> if (c == null) "" else Studio.s(c, k) }
        OcBuilder(this)
            .setTitle(if (c == null) "Nuovo cliente" else "Modifica cliente")
            .setView(view)
            .setPositiveButton("Salva") { _, _ ->
                if (stuT(m, "nome").isEmpty()) {
                    aviso("Il nome è obbligatorio")
                    return@setPositiveButton
                }
                val rec = stuCopia(c)
                for ((k, _, _) in def) rec.put(k, if (k == "cod_destinatario") stuT(m, k).uppercase() else stuT(m, k))
                stuEsito(Studio.salva(Studio.F_CLI, rec), "Cliente salvato")
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun stuAgenda(cont: LinearLayout) {
        val oggi = LocalDate.now()
        val rosso = ContextCompat.getColor(this, R.color.oc_red)
        val arancio = ContextCompat.getColor(this, R.color.oc_orange)
        val verde = ContextCompat.getColor(this, R.color.oc_green)
        sezione(cont, "PROSSIME SCADENZE FISCALI")
        val sc = Studio.scadenzeProssime(oggi)
        if (sc.isEmpty()) vuoto(cont, "Nessuna scadenza generata. Usa Azioni → Genera scadenze fiscali.")
        for (a in sc) {
            val dt = Studio.dataIso(Studio.s(a, "data"))
            val gg = if (dt == null) 999L else java.time.temporal.ChronoUnit.DAYS.between(oggi, dt)
            val col = if (gg <= 7) rosso else if (gg <= 30) arancio else null
            val txt = if (gg == 0L) "oggi" else if (gg > 0) "tra $gg giorni" else "scaduta"
            riga(cont, Studio.s(a, "prestazione"), Studio.dmy(Studio.s(a, "data")), txt, col, null)
        }
        sezione(cont, "CALENDARIO")
        val nav = LinearLayout(this)
        nav.orientation = LinearLayout.HORIZONTAL
        nav.addView(pulsanteSel("◀", false) { stuMese = stuMese.minusMonths(1); renderLista() }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        nav.addView(
            pulsanteSel(Studio.MESI[stuMese.monthValue] + " " + stuMese.year, true) {
                stuMese = YearMonth.now()
                stuGiorno = LocalDate.now()
                renderLista()
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 2.5f)
        )
        nav.addView(pulsanteSel("▶", false) { stuMese = stuMese.plusMonths(1); renderLista() }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        cont.addView(nav)
        val opz = LinearLayout(this)
        opz.orientation = LinearLayout.HORIZONTAL
        opz.addView(
            pulsanteSel("Stato: $stuFiltroApp ▾", false) {
                val v = listOf("Tutti") + Studio.STATI_APP
                OcBuilder(this).setTitle("Mostra").setItems(v.toTypedArray()) { _, k -> stuFiltroApp = v[k]; renderLista() }
                    .setNegativeButton("Annulla", null).show()
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        opz.addView(
            pulsanteSel("Vista: " + (if (stuModoApp == 0) "Giorno" else "Mese") + " ▾", false) {
                OcBuilder(this).setTitle("Vista").setItems(arrayOf("Giorno", "Mese")) { _, k -> stuModoApp = k; renderLista() }
                    .setNegativeButton("Annulla", null).show()
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        cont.addView(opz)
        val delMese = Studio.appuntamentiMese(stuMese.year, stuMese.monthValue, stuFiltroApp)
        stuGriglia(cont, delMese.groupingBy { Studio.s(it, "data") }.eachCount())
        val lista: List<JSONObject>
        if (stuModoApp == 0) {
            sezione(cont, "APPUNTAMENTI DEL " + Studio.dmy(Studio.iso(stuGiorno)))
            lista = Studio.appuntamentiGiorno(Studio.iso(stuGiorno), stuFiltroApp)
        } else {
            sezione(cont, "APPUNTAMENTI DI " + Studio.MESI[stuMese.monthValue].uppercase())
            lista = delMese
        }
        if (lista.isEmpty()) vuoto(cont, "Nessun appuntamento.")
        for (a in lista) {
            val stato = Studio.s(a, "stato")
            val sotto = listOfNotNull(
                if (stuModoApp == 1) Studio.dmy(Studio.s(a, "data")) else null,
                Studio.s(a, "prestazione").takeIf { it.isNotBlank() },
                Studio.s(a, "note").takeIf { it.isNotBlank() },
            ).joinToString(" · ")
            riga(
                cont, Studio.s(a, "ora").ifBlank { "—" } + " · " + Studio.s(a, "cliente_nome"), sotto, stato,
                when (stato) { "Completato" -> verde; "Annullato" -> rosso; else -> null }
            ) { stuMenuApp(a) }
        }
    }

    private fun stuGriglia(cont: LinearLayout, conteggi: Map<String, Int>) {
        val dp = resources.displayMetrics.density
        val grigio = ContextCompat.getColor(this, R.color.oc_text_mid)
        val rosso = ContextCompat.getColor(this, R.color.oc_red)
        val testo = ContextCompat.getColor(this, R.color.oc_text)
        val oro = ContextCompat.getColor(this, R.color.oc_gold)
        val primo = stuMese.atDay(1)
        val off = primo.dayOfWeek.value - 1
        val giorni = stuMese.lengthOfMonth()
        val righe = (off + giorni + 6) / 7
        val intest = LinearLayout(this)
        intest.orientation = LinearLayout.HORIZONTAL
        intest.setPadding(0, (8 * dp).toInt(), 0, 0)
        for (n in listOf("Lun", "Mar", "Mer", "Gio", "Ven", "Sab", "Dom")) {
            val tv = TextView(this)
            tv.text = n
            tv.textSize = 11f
            tv.gravity = android.view.Gravity.CENTER
            tv.setTextColor(grigio)
            intest.addView(tv, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        cont.addView(intest)
        val oggi = LocalDate.now()
        for (r in 0 until righe) {
            val rr = LinearLayout(this)
            rr.orientation = LinearLayout.HORIZONTAL
            for (c in 0 until 7) {
                val g = r * 7 + c - off + 1
                val tv = TextView(this)
                tv.gravity = android.view.Gravity.CENTER
                tv.setPadding(0, (6 * dp).toInt(), 0, (6 * dp).toInt())
                if (g in 1..giorni) {
                    val data = stuMese.atDay(g)
                    val n = conteggi[Studio.iso(data)] ?: 0
                    tv.text = g.toString() + "\n" + (if (n > 0) "●$n" else " ")
                    tv.textSize = 13f
                    if (data == stuGiorno) {
                        tv.setBackgroundColor(oro)
                        tv.setTextColor(android.graphics.Color.BLACK)
                    } else {
                        tv.setTextColor(if (c >= 5) rosso else testo)
                        if (data == oggi) {
                            tv.setTypeface(null, Typeface.BOLD)
                            tv.setBackgroundResource(R.drawable.bg_tile)
                        }
                    }
                    tv.setOnClickListener {
                        stuGiorno = data
                        renderLista()
                    }
                } else {
                    tv.text = " \n "
                }
                rr.addView(tv, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            }
            cont.addView(rr)
        }
    }

    private fun stuMenuApp(a: JSONObject) {
        OcBuilder(this)
            .setTitle(Studio.s(a, "cliente_nome").ifBlank { "Appuntamento" })
            .setItems(arrayOf("✏️  Modifica", "✅  Segna completato", "🚫  Segna annullato", "🗑️  Elimina")) { _, k ->
                fun stato(s: String) {
                    val rec = JSONObject(a.toString())
                    rec.put("stato", s)
                    stuEsito(Studio.salva(Studio.F_APP, rec), "Appuntamento aggiornato")
                }
                when (k) {
                    0 -> dialogAppuntamento(a)
                    1 -> stato("Completato")
                    2 -> stato("Annullato")
                    else -> stuConferma("Eliminare l'appuntamento?", "", "Elimina") {
                        stuEsito(Studio.elimina(Studio.F_APP, Studio.s(a, "id")), "Appuntamento eliminato")
                    }
                }
            }
            .setNegativeButton("Chiudi", null)
            .show()
    }

    private fun dialogAppuntamento(a: JSONObject?) {
        val (view, corpo) = corpoDialogo()
        var data: LocalDate? = (if (a == null) null else Studio.dataIso(Studio.s(a, "data"))) ?: stuGiorno
        bottoneData(corpo, "Data", data, false) { data = it }
        val etOra = campoTesto(corpo, "Ora (HH:MM)", if (a == null) "" else Studio.s(a, "ora"), InputType.TYPE_CLASS_DATETIME or InputType.TYPE_DATETIME_VARIATION_TIME, 5)
        val curCli = if (a == null) "" else Studio.s(a, "cliente_nome")
        val vCli = (listOf("") + Studio.nomiClienti() + listOf(curCli)).distinct()
        val tilCli = campoMenu(corpo, "Cliente", vCli, curCli)
        val curPre = if (a == null) "" else Studio.s(a, "prestazione")
        val vPre = (listOf("") + Studio.nomiPrestazioni() + listOf(curPre)).distinct()
        val tilPre = campoMenu(corpo, "Prestazione", vPre, curPre)
        val tilSt = campoMenu(corpo, "Stato", Studio.STATI_APP, if (a == null) "Confermato" else Studio.s(a, "stato").ifBlank { "Confermato" })
        val etNote = campoTesto(corpo, "Note", if (a == null) "" else Studio.s(a, "note"), InputType.TYPE_CLASS_TEXT)
        OcBuilder(this)
            .setTitle(if (a == null) "Nuovo appuntamento" else "Modifica appuntamento")
            .setView(view)
            .setPositiveButton("Salva") { _, _ ->
                val dt = data
                if (dt == null) {
                    aviso("Data obbligatoria")
                    return@setPositiveButton
                }
                val ora = Studio.normOra(etOra.text.toString())
                if (ora == null) {
                    aviso("Ora non valida (HH:MM)")
                    return@setPositiveButton
                }
                val rec = stuCopia(a)
                rec.put("data", Studio.iso(dt)).put("ora", ora).put("cliente_nome", stuTesto(tilCli))
                    .put("prestazione", stuTesto(tilPre)).put("stato", stuTesto(tilSt).ifBlank { "Confermato" })
                    .put("note", etNote.text.toString().trim())
                stuGiorno = dt
                stuMese = YearMonth.from(dt)
                stuEsito(Studio.salva(Studio.F_APP, rec), "Appuntamento salvato")
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun stuPrestazioni(cont: LinearLayout) {
        val l = Studio.lista(Studio.F_PRE).sortedBy { Studio.s(it, "nome").lowercase() }
        if (l.isEmpty()) {
            vuoto(cont, "Nessuna prestazione. Usa ➕ Nuova prestazione.")
            return
        }
        for (p in l) {
            val dur = Studio.d(p, "durata").toInt()
            val sotto = listOfNotNull(
                Studio.s(p, "categoria").takeIf { it.isNotBlank() },
                if (dur > 0) "$dur min" else null,
                "IVA " + Studio.fmtQ(Studio.d(p, "iva", 22.0)) + "%",
                Studio.s(p, "note").takeIf { it.isNotBlank() },
            ).joinToString(" · ")
            riga(cont, Studio.s(p, "nome"), sotto, Studio.eur(Studio.d(p, "prezzo")), null) {
                OcBuilder(this)
                    .setTitle(Studio.s(p, "nome"))
                    .setItems(arrayOf("✏️  Modifica", "🗑️  Elimina")) { _, k ->
                        if (k == 0) dialogPrestazione(p) else stuConferma("Eliminare la prestazione?", Studio.s(p, "nome"), "Elimina") {
                            stuEsito(Studio.elimina(Studio.F_PRE, Studio.s(p, "id")), "Prestazione eliminata")
                        }
                    }
                    .setNegativeButton("Chiudi", null)
                    .show()
            }
        }
    }

    private fun dialogPrestazione(p: JSONObject?) {
        val (view, corpo) = corpoDialogo()
        val etNome = campoTesto(corpo, "Nome *", if (p == null) "" else Studio.s(p, "nome"), InputType.TYPE_CLASS_TEXT)
        val etCat = campoTesto(corpo, "Categoria", if (p == null) "" else Studio.s(p, "categoria"), InputType.TYPE_CLASS_TEXT)
        val etPrezzo = campoTesto(corpo, "Prezzo €", if (p == null) "" else Studio.fmtQ(Studio.d(p, "prezzo")), TIPO_DECIMALE, 12)
        val etDur = campoTesto(corpo, "Durata (minuti)", if (p == null) "" else Studio.d(p, "durata").toInt().takeIf { it > 0 }?.toString() ?: "", InputType.TYPE_CLASS_NUMBER, 4)
        val tilIva = campoMenu(corpo, "IVA %", Studio.ALIQUOTE.map { it.toString() }, if (p == null) "22" else Studio.fmtQ(Studio.d(p, "iva", 22.0)))
        val etNote = campoTesto(corpo, "Note", if (p == null) "" else Studio.s(p, "note"), InputType.TYPE_CLASS_TEXT)
        OcBuilder(this)
            .setTitle(if (p == null) "Nuova prestazione" else "Modifica prestazione")
            .setView(view)
            .setPositiveButton("Salva") { _, _ ->
                val nome = etNome.text.toString().trim()
                val prezzo = Studio.numero(etPrezzo.text.toString().ifBlank { "0" })
                if (nome.isEmpty()) {
                    aviso("Il nome è obbligatorio")
                    return@setPositiveButton
                }
                if (prezzo == null || prezzo < 0) {
                    aviso("Prezzo non valido")
                    return@setPositiveButton
                }
                val rec = stuCopia(p)
                rec.put("nome", nome).put("categoria", etCat.text.toString().trim()).put("prezzo", prezzo)
                    .put("durata", etDur.text.toString().trim().toIntOrNull() ?: 0)
                    .put("iva", stuTesto(tilIva).toIntOrNull() ?: 22).put("note", etNote.text.toString().trim())
                stuEsito(Studio.salva(Studio.F_PRE, rec), "Prestazione salvata")
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun stuFatture(cont: LinearLayout) {
        val verde = ContextCompat.getColor(this, R.color.oc_green)
        val rosso = ContextCompat.getColor(this, R.color.oc_red)
        cont.addView(
            pulsanteSel("Stato:  $stuFiltroDoc   ▾", false) {
                val v = listOf("Tutti") + Studio.STATI_DOC
                OcBuilder(this).setTitle("Mostra").setItems(v.toTypedArray()) { _, k -> stuFiltroDoc = v[k]; renderLista() }
                    .setNegativeButton("Annulla", null).show()
            },
            stuLp()
        )
        val l = Studio.lista(Studio.F_FAT)
            .filter { stuFiltroDoc == "Tutti" || Studio.s(it, "stato").ifBlank { "Bozza" } == stuFiltroDoc }
            .sortedByDescending { Studio.s(it, "data") }
        if (l.isEmpty()) {
            vuoto(cont, "Nessun documento. Usa ➕ Nuovo documento.")
            return
        }
        for (f in l) {
            val t = Studio.totali(f)
            val stato = Studio.s(f, "stato").ifBlank { "Bozza" }
            val sotto = listOfNotNull(
                Studio.dmy(Studio.s(f, "data")), stato,
                Studio.s(f, "scadenza").takeIf { it.isNotBlank() }?.let { "scad. " + Studio.dmy(it) },
                "imp. " + Studio.eur(t.imponibile) + " + IVA " + Studio.eur(t.iva),
            ).joinToString(" · ")
            riga(
                cont, Studio.tipoDoc(f) + " " + Studio.s(f, "numero") + " · " + Studio.s(f, "cliente_nome"), sotto, Studio.eur(t.totale),
                when (stato) { "Pagata" -> verde; "Scaduta", "Annullata" -> rosso; else -> null }
            ) { stuMenuDoc(f) }
        }
    }

    private fun stuMenuDoc(f: JSONObject) {
        val voci = ArrayList<String>()
        val az = ArrayList<() -> Unit>()
        fun add(t: String, a: () -> Unit) {
            voci.add(t)
            az.add(a)
        }
        add("✏️  Modifica") { dialogDocumentoStudio(f) }
        add("📄  PDF / Condividi") { stuPdfDocumento(f) }
        if (Studio.tipoDoc(f) == "Fattura" && Studio.s(f, "stato") != "Pagata") add("💶  Incassa in Cassa") { dialogIncassa(f) }
        add("🏷️  Cambia stato") {
            OcBuilder(this)
                .setTitle("Stato")
                .setItems(Studio.STATI_DOC.toTypedArray()) { _, k ->
                    val rec = JSONObject(f.toString())
                    rec.put("stato", Studio.STATI_DOC[k])
                    stuEsito(Studio.salva(Studio.F_FAT, rec), "Stato aggiornato")
                }
                .setNegativeButton("Annulla", null)
                .show()
        }
        add("🗑️  Elimina") {
            stuConferma("Eliminare il documento?", Studio.tipoDoc(f) + " " + Studio.s(f, "numero"), "Elimina") {
                stuEsito(Studio.elimina(Studio.F_FAT, Studio.s(f, "id")), "Documento eliminato")
            }
        }
        OcBuilder(this)
            .setTitle(Studio.tipoDoc(f) + " " + Studio.s(f, "numero"))
            .setItems(voci.toTypedArray()) { _, k -> az[k]() }
            .setNegativeButton("Chiudi", null)
            .show()
    }

    private fun stuPdfDocumento(f: JSONObject) {
        val file = try {
            StudioPdf.documento(this, f)
        } catch (e: Exception) {
            aviso("Impossibile creare il PDF: " + (e.message ?: "errore"))
            return
        }
        StudioPdf.condividi(this, file, Studio.tipoDoc(f) + " " + Studio.s(f, "numero"))
    }

    private fun stuPdfMagazzino() {
        if (Studio.lista(Studio.F_MAG).isEmpty()) {
            aviso("Magazzino vuoto")
            return
        }
        val file = try {
            StudioPdf.magazzino(this)
        } catch (e: Exception) {
            aviso("Impossibile creare il PDF: " + (e.message ?: "errore"))
            return
        }
        StudioPdf.condividi(this, file, "Magazzino")
    }

    private fun dialogIncassa(f: JSONObject) {
        val (view, corpo) = corpoDialogo()
        val tot = Studio.totali(f).totale
        val tv = TextView(ctxDialogo)
        tv.text = "Fattura N° " + Studio.s(f, "numero") + " — " + Studio.s(f, "cliente_nome") + "\nImporto: " + Studio.eur(tot)
        tv.setPadding(4, 4, 4, 8)
        corpo.addView(tv)
        var data: LocalDate? = LocalDate.now()
        bottoneData(corpo, "Data incasso", data, false) { data = it }
        val tilMet = campoMenu(corpo, "Metodo", listOf("") + Studio.METODI_CASSA, "")
        OcBuilder(this)
            .setTitle("Incassa in Cassa")
            .setView(view)
            .setPositiveButton("Conferma") { _, _ ->
                val dt = data ?: LocalDate.now()
                stuEsito(Studio.incassa(Studio.s(f, "id"), Studio.iso(dt), stuTesto(tilMet)), "Fattura incassata e movimento aggiunto in Cassa")
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun stuScegliCatalogo(onPick: (Studio.VoceCat) -> Unit) {
        val c = Studio.catalogo()
        if (c.isEmpty()) {
            aviso("Nessun prodotto o prestazione in catalogo")
            return
        }
        OcBuilder(this)
            .setTitle("Catalogo")
            .setItems(c.map { it.nome + " — " + Studio.eur(it.prezzo) + " (" + it.origine + ")" }.toTypedArray()) { _, k -> onPick(c[k]) }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun dialogRigaStudio(r: JSONObject?, forf: Boolean, onOk: (JSONObject) -> Unit) {
        val (view, corpo) = corpoDialogo()
        val etDesc = campoTesto(corpo, "Descrizione *", if (r == null) "" else Studio.s(r, "desc"), InputType.TYPE_CLASS_TEXT)
        val etQty = campoTesto(corpo, "Quantità", if (r == null) "1" else Studio.fmtQ(Studio.d(r, "qty", 1.0)), TIPO_DECIMALE, 10)
        val etPrezzo = campoTesto(corpo, "Prezzo unitario €", if (r == null) "" else Studio.fmtQ(Studio.d(r, "prezzo")), TIPO_DECIMALE_NEG, 12)
        var tilIva: TextInputLayout? = null
        if (!forf) tilIva = campoMenu(corpo, "IVA %", Studio.ALIQUOTE.map { it.toString() }, if (r == null) "22" else Studio.fmtQ(Studio.d(r, "iva", 22.0)))
        OcBuilder(this)
            .setTitle(if (r == null) "Nuova riga" else "Modifica riga")
            .setView(view)
            .setPositiveButton("OK") { _, _ ->
                val desc = etDesc.text.toString().trim()
                val q = Studio.numero(etQty.text.toString().ifBlank { "1" })
                val p = Studio.numero(etPrezzo.text.toString().ifBlank { "0" })
                if (desc.isEmpty()) {
                    aviso("La descrizione è obbligatoria")
                    return@setPositiveButton
                }
                if (q == null || p == null) {
                    aviso("Quantità o prezzo non validi")
                    return@setPositiveButton
                }
                val iva = if (forf) 0 else (tilIva?.let { stuTesto(it) }?.toIntOrNull() ?: 22)
                val rec = if (r == null) JSONObject() else JSONObject(r.toString())
                rec.put("desc", desc).put("qty", q).put("prezzo", p).put("iva", iva)
                onOk(rec)
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun dialogDocumentoStudio(d: JSONObject?) {
        val nuovo = d == null
        val forf = Studio.forfettario()
        val (view, corpo) = corpoDialogo()
        fun g(k: String) = if (d == null) "" else Studio.s(d, k)
        var tipo = if (d == null) "Fattura" else Studio.tipoDoc(d)
        var etNum: TextInputEditText? = null
        campoMenu(corpo, "Tipo documento", Studio.TIPI_DOC, tipo) {
            tipo = Studio.TIPI_DOC[it]
            if (nuovo) etNum?.setText(Studio.prossimoNumero(tipo))
        }
        etNum = campoTesto(corpo, "Numero", if (d == null) Studio.prossimoNumero(tipo) else g("numero"), InputType.TYPE_CLASS_TEXT, 20)
        var data: LocalDate? = (if (d == null) null else Studio.dataIso(g("data"))) ?: LocalDate.now()
        bottoneData(corpo, "Data", data, false) { data = it }
        var scad: LocalDate? = if (d == null) null else Studio.dataIso(g("scadenza"))
        bottoneData(corpo, "Scadenza (tieni premuto per azzerare)", scad, true) { scad = it }
        val curCli = g("cliente_nome")
        val tilCli = campoMenu(corpo, "Cliente", (listOf("") + Studio.nomiClienti() + listOf(curCli)).distinct(), curCli)
        val tilSt = campoMenu(corpo, "Stato", Studio.STATI_DOC, g("stato").ifBlank { "Bozza" })
        val tilPag = campoMenu(corpo, "Modalità di pagamento", Studio.PAGAMENTI, g("modalita_pagamento").ifBlank { "Bonifico" })
        val tilColl = campoMenu(corpo, "Fattura collegata (per Nota Credito)", (listOf("") + Studio.numeriFatture() + listOf(g("fattura_collegata_numero"))).distinct(), g("fattura_collegata_numero"))
        val etNote = campoTesto(corpo, "Note", g("note"), InputType.TYPE_CLASS_TEXT)

        val righe = ArrayList<JSONObject>()
        if (d != null) {
            val a = d.optJSONArray("righe")
            if (a != null) for (i in 0 until a.length()) a.optJSONObject(i)?.let { righe.add(JSONObject(it.toString())) }
        }
        val dp = resources.displayMetrics.density
        val tit = TextView(ctxDialogo)
        tit.text = "RIGHE DEL DOCUMENTO"
        tit.setTypeface(null, Typeface.BOLD)
        tit.setPadding(4, (16 * dp).toInt(), 4, (6 * dp).toInt())
        corpo.addView(tit)
        val box = LinearLayout(ctxDialogo)
        box.orientation = LinearLayout.VERTICAL
        corpo.addView(box)
        val tvTot = TextView(ctxDialogo)
        tvTot.setTypeface(null, Typeface.BOLD)
        tvTot.setPadding(4, (8 * dp).toInt(), 4, (8 * dp).toInt())
        corpo.addView(tvTot)
        lateinit var ridisegna: () -> Unit
        ridisegna = {
            box.removeAllViews()
            if (righe.isEmpty()) {
                val t = TextView(ctxDialogo)
                t.text = "Nessuna riga. Aggiungine una."
                t.setPadding(4, 8, 4, 8)
                box.addView(t)
            }
            for ((i, r) in righe.withIndex()) {
                val t = TextView(ctxDialogo)
                val q = Studio.d(r, "qty", 1.0)
                val p = Studio.d(r, "prezzo")
                val iv = Studio.d(r, "iva", 22.0)
                t.text = Studio.s(r, "desc") + "\n" + Studio.fmtQ(q) + " × " + Studio.fmtNum(p) + " · IVA " + Studio.fmtQ(iv) + "% = " + Studio.eur(q * p * (1 + iv / 100.0))
                t.setPadding(4, (8 * dp).toInt(), 4, (8 * dp).toInt())
                t.setOnClickListener {
                    OcBuilder(this)
                        .setTitle(Studio.s(r, "desc"))
                        .setItems(arrayOf("✏️  Modifica", "🗑️  Rimuovi")) { _, k ->
                            if (k == 0) dialogRigaStudio(r, forf) { nr -> righe[i] = nr; ridisegna() } else {
                                righe.removeAt(i)
                                ridisegna()
                            }
                        }
                        .setNegativeButton("Chiudi", null)
                        .show()
                }
                box.addView(t)
            }
            val tt = Studio.totali(righe.map { Studio.Riga(Studio.s(it, "desc"), Studio.d(it, "qty", 1.0), Studio.d(it, "prezzo"), Studio.d(it, "iva", 22.0)) })
            tvTot.text = "Imponibile " + Studio.eur(tt.imponibile) + "\nIVA " + Studio.eur(tt.iva) + "\nTotale " + Studio.eur(tt.totale)
        }
        ridisegna()
        val piede = LinearLayout(ctxDialogo)
        piede.orientation = LinearLayout.HORIZONTAL
        aggiungiPiede(
            piede,
            "➕  Riga" to { dialogRigaStudio(null, forf) { nr -> righe.add(nr); ridisegna() } },
            "📦  Catalogo" to {
                stuScegliCatalogo { v ->
                    righe.add(JSONObject().put("desc", v.nome).put("qty", 1.0).put("prezzo", v.prezzo).put("iva", if (forf) 0 else v.iva))
                    ridisegna()
                }
            },
        )
        corpo.addView(piede)
        if (forf) {
            val n = TextView(ctxDialogo)
            n.text = "Regime forfettario: IVA a 0% su tutte le righe."
            n.textSize = 12f
            n.setPadding(4, (8 * dp).toInt(), 4, 0)
            corpo.addView(n)
        }

        OcBuilder(this)
            .setTitle(if (nuovo) "Nuovo documento" else "Modifica documento")
            .setView(view)
            .setPositiveButton("Salva") { _, _ ->
                val dt = data
                if (dt == null) {
                    aviso("Data obbligatoria")
                    return@setPositiveButton
                }
                val ok = righe.filter { Studio.s(it, "desc").isNotBlank() }
                val arr = org.json.JSONArray()
                for (r in ok) arr.put(r)
                val rec = stuCopia(d)
                rec.put("tipo_doc", tipo)
                    .put("numero", (etNum?.text?.toString()?.trim() ?: "").ifBlank { Studio.prossimoNumero(tipo) })
                    .put("data", Studio.iso(dt)).put("scadenza", scad?.let { Studio.iso(it) } ?: "")
                    .put("cliente_nome", stuTesto(tilCli)).put("righe", arr)
                    .put("stato", stuTesto(tilSt).ifBlank { "Bozza" }).put("note", etNote.text.toString().trim())
                    .put("modalita_pagamento", stuTesto(tilPag).ifBlank { "Bonifico" })
                    .put("fattura_collegata_numero", stuTesto(tilColl))
                fun salva() = stuEsito(Studio.salvaDocumento(rec, nuovo), "Documento salvato")
                val sg = Studio.controlloSoglia(rec)
                if (sg != null && sg.perc >= 1.0) {
                    stuConferma(
                        "Soglia forfettario superata",
                        "Con questo documento il fatturato " + sg.anno + " arriverebbe a " + Studio.eur(sg.totaleAnno) +
                            ", oltre la soglia di " + Studio.eur(sg.soglia) + ".\n\nSalvare comunque?",
                        "Salva"
                    ) { salva() }
                } else {
                    if (sg != null && sg.perc >= 0.9) aviso("Attenzione: sei al " + String.format(Locale.ITALY, "%.0f", sg.perc * 100) + "% della soglia forfettario " + sg.anno)
                    salva()
                }
            }
            .setNegativeButton("Annulla", null)
            .show()
    }
    
    private fun stuCassa(cont: LinearLayout) {
        val verde = ContextCompat.getColor(this, R.color.oc_green)
        val rosso = ContextCompat.getColor(this, R.color.oc_red)
        val movs = Studio.lista(Studio.F_CAS)
        val sc = Studio.saldoCassa(movs)
        riga(cont, "Saldo cassa", "Entrate " + Studio.eur(sc.entrate) + " · Uscite " + Studio.eur(sc.uscite), Studio.eur(sc.saldo), if (sc.saldo >= 0) verde else rosso, null)
        cont.addView(
            pulsanteSel("Mostra:  $stuFiltroCas   ▾", false) {
                val v = listOf("Tutti", "Entrata", "Uscita")
                OcBuilder(this).setTitle("Mostra").setItems(v.toTypedArray()) { _, k -> stuFiltroCas = v[k]; renderLista() }
                    .setNegativeButton("Annulla", null).show()
            },
            stuLp()
        )
        val l = movs.filter { stuFiltroCas == "Tutti" || Studio.s(it, "tipo").ifBlank { "Entrata" } == stuFiltroCas }
            .sortedByDescending { Studio.s(it, "data") }
        if (l.isEmpty()) {
            vuoto(cont, "Nessun movimento. Usa ➕ Nuovo movimento.")
            return
        }
        for (m in l) {
            val entrata = Studio.s(m, "tipo") != "Uscita"
            val sotto = listOfNotNull(
                Studio.dmy(Studio.s(m, "data")), Studio.s(m, "metodo").takeIf { it.isNotBlank() },
                Studio.s(m, "note").takeIf { it.isNotBlank() },
            ).joinToString(" · ")
            riga(
                cont, Studio.s(m, "categoria").ifBlank { if (entrata) "Entrata" else "Uscita" }, sotto,
                (if (entrata) "+ " else "− ") + Studio.eur(Studio.d(m, "importo")), if (entrata) verde else rosso
            ) {
                OcBuilder(this)
                    .setTitle(Studio.s(m, "categoria").ifBlank { "Movimento" })
                    .setItems(arrayOf("✏️  Modifica", "🗑️  Elimina")) { _, k ->
                        if (k == 0) dialogMovCassa(m) else stuConferma("Eliminare il movimento?", "", "Elimina") {
                            stuEsito(Studio.elimina(Studio.F_CAS, Studio.s(m, "id")), "Movimento eliminato")
                        }
                    }
                    .setNegativeButton("Chiudi", null)
                    .show()
            }
        }
    }

    private fun dialogMovCassa(m: JSONObject?) {
        val (view, corpo) = corpoDialogo()
        val tilTipo = campoMenu(corpo, "Tipo", listOf("Entrata", "Uscita"), if (m == null) "Entrata" else Studio.s(m, "tipo").ifBlank { "Entrata" })
        var data: LocalDate? = (if (m == null) null else Studio.dataIso(Studio.s(m, "data"))) ?: LocalDate.now()
        bottoneData(corpo, "Data", data, false) { data = it }
        val etImp = campoTesto(corpo, "Importo €", if (m == null) "" else Studio.fmtQ(Studio.d(m, "importo")), TIPO_DECIMALE, 12)
        val etCat = campoTesto(corpo, "Categoria", if (m == null) "" else Studio.s(m, "categoria"), InputType.TYPE_CLASS_TEXT)
        val tilMet = campoMenu(corpo, "Metodo di pagamento", (listOf("") + Studio.METODI_CASSA + listOf(if (m == null) "" else Studio.s(m, "metodo"))).distinct(), if (m == null) "" else Studio.s(m, "metodo"))
        val etNote = campoTesto(corpo, "Note", if (m == null) "" else Studio.s(m, "note"), InputType.TYPE_CLASS_TEXT)
        OcBuilder(this)
            .setTitle(if (m == null) "Nuovo movimento" else "Modifica movimento")
            .setView(view)
            .setPositiveButton("Salva") { _, _ ->
                val dt = data
                val imp = importoOk(etImp.text.toString())
                if (dt == null) {
                    aviso("Data obbligatoria")
                    return@setPositiveButton
                }
                if (imp == null) {
                    aviso("Importo non valido")
                    return@setPositiveButton
                }
                val rec = stuCopia(m)
                rec.put("data", Studio.iso(dt)).put("tipo", stuTesto(tilTipo).ifBlank { "Entrata" }).put("importo", imp)
                    .put("categoria", etCat.text.toString().trim()).put("metodo", stuTesto(tilMet))
                    .put("note", etNote.text.toString().trim())
                stuEsito(Studio.salva(Studio.F_CAS, rec), "Movimento salvato")
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun stuMagazzino(cont: LinearLayout) {
        val rosso = ContextCompat.getColor(this, R.color.oc_red)
        val l = Studio.lista(Studio.F_MAG).sortedBy { Studio.s(it, "nome").lowercase() }
        if (l.isEmpty()) {
            vuoto(cont, "Magazzino vuoto. Usa ➕ Nuovo articolo.")
            return
        }
        val valTot = l.sumOf { Studio.d(it, "quantita") * Studio.d(it, "prezzo_unit") }
        riga(cont, "Valore magazzino", l.size.toString() + " voci · " + l.count { Studio.sottoSoglia(it) } + " sotto soglia", Studio.eur(valTot), null, null)
        sezione(cont, "ARTICOLI (in rosso: quantità ≤ soglia minima)")
        for (m in l) {
            val q = Studio.d(m, "quantita")
            val val0 = q * Studio.d(m, "prezzo_unit")
            val sotto = listOfNotNull(
                Studio.s(m, "categoria").takeIf { it.isNotBlank() },
                "soglia " + Studio.fmtQ(Studio.d(m, "soglia")),
                if (val0 != 0.0) "valore " + Studio.eur(val0) else null,
                Studio.s(m, "note").takeIf { it.isNotBlank() },
            ).joinToString(" · ")
            riga(
                cont, Studio.s(m, "nome"), sotto, Studio.fmtQ(q) + " " + Studio.s(m, "unita").ifBlank { "pz" },
                if (Studio.sottoSoglia(m)) rosso else null
            ) {
                OcBuilder(this)
                    .setTitle(Studio.s(m, "nome"))
                    .setItems(arrayOf("✏️  Modifica", "🗑️  Elimina")) { _, k ->
                        if (k == 0) dialogArticolo(m) else stuConferma("Eliminare la voce?", Studio.s(m, "nome"), "Elimina") {
                            stuEsito(Studio.elimina(Studio.F_MAG, Studio.s(m, "id")), "Voce eliminata")
                        }
                    }
                    .setNegativeButton("Chiudi", null)
                    .show()
            }
        }
    }

    private fun dialogArticolo(m: JSONObject?) {
        val (view, corpo) = corpoDialogo()
        val etNome = campoTesto(corpo, "Nome *", if (m == null) "" else Studio.s(m, "nome"), InputType.TYPE_CLASS_TEXT)
        val etCat = campoTesto(corpo, "Categoria", if (m == null) "" else Studio.s(m, "categoria"), InputType.TYPE_CLASS_TEXT)
        val etQ = campoTesto(corpo, "Quantità", if (m == null) "" else Studio.fmtQ(Studio.d(m, "quantita")), TIPO_DECIMALE_NEG, 12)
        val tilUm = campoMenu(corpo, "Unità", Studio.UNITA, if (m == null) "pz" else Studio.s(m, "unita").ifBlank { "pz" })
        val etSog = campoTesto(corpo, "Soglia minima", if (m == null) "" else Studio.fmtQ(Studio.d(m, "soglia")), TIPO_DECIMALE, 12)
        val etPu = campoTesto(corpo, "Prezzo unitario €", if (m == null) "" else Studio.fmtQ(Studio.d(m, "prezzo_unit")), TIPO_DECIMALE, 12)
        val etNote = campoTesto(corpo, "Note", if (m == null) "" else Studio.s(m, "note"), InputType.TYPE_CLASS_TEXT)
        OcBuilder(this)
            .setTitle(if (m == null) "Nuovo articolo" else "Modifica articolo")
            .setView(view)
            .setPositiveButton("Salva") { _, _ ->
                val nome = etNome.text.toString().trim()
                val q = Studio.numero(etQ.text.toString())
                val sog = Studio.numero(etSog.text.toString().ifBlank { "0" })
                val pu = Studio.numero(etPu.text.toString().ifBlank { "0" })
                if (nome.isEmpty()) {
                    aviso("Il nome è obbligatorio")
                    return@setPositiveButton
                }
                if (q == null) {
                    aviso("Quantità non valida")
                    return@setPositiveButton
                }
                if (sog == null || pu == null) {
                    aviso("Soglia o prezzo non validi")
                    return@setPositiveButton
                }
                val rec = stuCopia(m)
                rec.put("nome", nome).put("categoria", etCat.text.toString().trim()).put("quantita", q)
                    .put("unita", stuTesto(tilUm).ifBlank { "pz" }).put("soglia", sog).put("prezzo_unit", pu)
                    .put("note", etNote.text.toString().trim())
                stuEsito(Studio.salva(Studio.F_MAG, rec), "Articolo salvato")
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private val STU_EMI = listOf(
        "ragione_sociale" to "Ragione sociale / Nome", "indirizzo" to "Indirizzo (via e civico)", "cap" to "CAP",
        "comune" to "Comune", "provincia" to "Provincia (sigla)", "cf_piva" to "P.IVA / C.F.", "telefono" to "Telefono",
        "email" to "Email", "iban" to "IBAN", "regime_fiscale" to "Regime fiscale", "ateco" to "Codice ATECO",
        "coefficiente_redditivita" to "Coefficiente di redditività %", "aliquota_sostitutiva" to "Aliquota imposta sostitutiva",
        "aliquota_inps" to "Aliquota INPS",
    )

    private fun stuEmittente(cont: LinearLayout) {
        val e = Studio.emittente()
        sezione(cont, "DATI EMITTENTE (stampati su ogni fattura / preventivo)")
        riga(cont, "✏️  Modifica dati emittente", "", "", null) { dialogEmittente() }
        for ((k, etichetta) in STU_EMI) {
            val v = Studio.s(e, k)
            riga(cont, etichetta, v.ifBlank { "—" }, "", null) { dialogEmittente() }
        }
    }

    private fun dialogEmittente() {
        val e = Studio.emittente()
        val (view, corpo) = corpoDialogo()
        val testi = LinkedHashMap<String, TextInputEditText>()
        val menu = LinkedHashMap<String, TextInputLayout>()
        for ((k, h) in STU_EMI) {
            val cur = Studio.s(e, k)
            when (k) {
                "regime_fiscale" -> menu[k] = campoMenu(corpo, h, Studio.REGIMI, cur.ifBlank { "Ordinario" })
                "aliquota_sostitutiva" -> menu[k] = campoMenu(corpo, h, (Studio.ALIQ_SOST + listOf(cur)).filter { it.isNotBlank() }.distinct(), cur.ifBlank { Studio.ALIQ_SOST[1] })
                "aliquota_inps" -> menu[k] = campoMenu(corpo, h, (Studio.ALIQ_INPS + listOf(cur)).filter { it.isNotBlank() }.distinct(), cur.ifBlank { Studio.ALIQ_INPS[0] })
                else -> testi[k] = campoTesto(
                    corpo, h, cur,
                    when (k) {
                        "telefono" -> InputType.TYPE_CLASS_PHONE
                        "email" -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                        "coefficiente_redditivita" -> TIPO_DECIMALE
                        else -> InputType.TYPE_CLASS_TEXT
                    },
                    when (k) { "cap" -> 5; "provincia" -> 2; "iban" -> 34; "cf_piva" -> 16; else -> 0 }
                )
            }
        }
        OcBuilder(this)
            .setTitle("Dati emittente")
            .setView(view)
            .setPositiveButton("Salva") { _, _ ->
                val dati = LinkedHashMap<String, String>()
                for ((k, et) in testi) dati[k] = et.text.toString().trim()
                for ((k, til) in menu) dati[k] = stuTesto(til)
                stuEsito(Studio.salvaEmittente(dati), "Dati emittente salvati")
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun dialogPianoPensione() {
        val p = Pensione.dati().piano
        val (view, corpo) = corpoDialogo()
        val cap = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        fun nt(x: Double) = String.format(Locale.ITALY, "%.2f", x)
        val etNome = campoTesto(corpo, "Nome fondo", p.nome, cap, 60)
        val etGest = campoTesto(corpo, "Gestore", p.gestore, cap, 60)
        val etComp = campoTesto(corpo, "Comparto", p.comparto, cap, 60)
        var tipo = p.tipo
        val voci = if (tipo in Pensione.TIPI_PIANO) Pensione.TIPI_PIANO else Pensione.TIPI_PIANO + tipo
        campoMenu(corpo, "Tipo piano", voci, tipo) { tipo = voci[it] }
        var ades: LocalDate? = Pensione.data(p.adesione)
        bottoneData(corpo, "Data adesione (pressione lunga = nessuna)", ades, true) { ades = it }
        val etInfl = campoTesto(corpo, "Inflazione media stimata (%/anno)", nt(p.inflazione), TIPO_DECIMALE_NEG, 7)
        val etNote = campoTesto(corpo, "Note", p.note, cap, 80)
        var pens: LocalDate? = Pensione.data(p.dataPensione)
        bottoneData(corpo, "Data prevista pensionamento (pressione lunga = nessuna)", pens, true) { pens = it }
        val etVers = campoTesto(corpo, "Versamento annuo stimato (€)", if (p.versAnnuo > 0) nt(p.versAnnuo) else "", TIPO_DECIMALE, 12)
        val etRend = campoTesto(corpo, "Rendimento atteso (%/anno)", nt(p.rendAtteso), TIPO_DECIMALE_NEG, 7)
        val etCosto = campoTesto(corpo, "Costo di gestione annuo (% ISC)", nt(p.costoGestione), TIPO_DECIMALE_NEG, 7)
        val dlg = OcBuilder(this)
            .setTitle("Piano fondo pensione")
            .setView(view)
            .setPositiveButton("Salva", null)
            .setNegativeButton("Annulla", null)
            .create()
        dlg.show()
        dlg.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            fun pct(et: com.google.android.material.textfield.TextInputEditText, nomeCampo: String): Double? {
                val x = if (et.text.isNullOrBlank()) null else num(et.text.toString())
                if (x == null || x < -100 || x > 100) { et.error = "$nomeCampo non valido"; return null }
                return x
            }
            val infl = pct(etInfl, "Valore") ?: return@setOnClickListener
            val rend = pct(etRend, "Valore") ?: return@setOnClickListener
            val costo = pct(etCosto, "Valore") ?: return@setOnClickListener
            val vers = importoOk(etVers.text.toString(), ammettiZero = true)
            if (vers == null) { etVers.error = "Importo non valido"; return@setOnClickListener }
            val err = Pensione.salvaPiano(
                etNome.text.toString(), etGest.text.toString(), etComp.text.toString(), tipo,
                ades?.format(FMT_DATA) ?: "", infl, etNote.text.toString(),
                pens?.format(FMT_DATA) ?: "", vers, rend, costo
            )
            if (err != null) { aviso(err); return@setOnClickListener }
            dlg.dismiss()
            refresh()
            aviso("Piano salvato")
        }
    }

    private val CASA_STATI = listOf("Tutti", "Scadute/Urgenti", "In avvicinamento", "OK", "Da pianificare")
    private val CASA_TAG = mapOf("Scadute/Urgenti" to "rosso", "In avvicinamento" to "ambra", "OK" to "verde", "Da pianificare" to "grigio")

    private fun renderCasaCare() {
        intestaLista("🛠️ CasaCare", "➕  Nuova attività", "🔎  Filtri")
        val cont = vista<LinearLayout>(R.id.listaGen)
        Casa.errore?.let { vuoto(cont, "⚠️ $it") }
        val oggi = LocalDate.now()
        val tutte = Casa.voci().map { it to Casa.stato(it, oggi) }
        if (Casa.errore != null && cont.childCount == 0) vuoto(cont, "⚠️ " + Casa.errore)
        fun n(t: String) = tutte.count { it.second.tag == t }
        vuoto(cont, "🔴 " + n("rosso") + "  ·  🟠 " + n("ambra") + "  ·  🟢 " + n("verde") + "  ·  ⚪ " + n("grigio"))
        if (casaCat != "Tutte" || casaStato != "Tutti") vuoto(cont, "Filtro: $casaCat · $casaStato")
        val tagFiltro = CASA_TAG[casaStato]
        val visibili = tutte.filter { (v, st) ->
            (casaCat == "Tutte" || v.categoria == casaCat) && (tagFiltro == null || st.tag == tagFiltro)
        }
        if (visibili.isEmpty()) {
            vuoto(cont, "Nessuna attività.")
            return
        }
        val gruppi = listOf(
            "rosso" to "SCADUTE / URGENTI", "ambra" to "IN AVVICINAMENTO",
            "verde" to "OK", "grigio" to "DA PIANIFICARE",
        )
        for ((tag, titolo) in gruppi) {
            val lista = visibili.filter { it.second.tag == tag }
                .sortedWith(compareBy({ it.second.giorni ?: Int.MAX_VALUE }, { it.first.nome.lowercase() }))
            if (lista.isEmpty()) continue
            sezione(cont, titolo + " (" + lista.size + ")")
            for ((v, st) in lista) {
                val sotto = v.categoria + " · " + v.freqLabel + "\n" + st.testo +
                    (if (v.note.isNotBlank()) "\n" + v.note else "")
                riga(cont, (if (v.legge) "⚖ " else "") + v.nome, sotto, emojiScad(tag), null) { menuCasa(v) }
            }
        }
    }

    private fun menuCasa(v: Casa.Voce) {
        OcBuilder(this)
            .setTitle(v.nome)
            .setItems(arrayOf("✅  Segna eseguita oggi", "✏️  Modifica", "↩️  Azzera (mai eseguita)", "🗑️  Elimina")) { _, k ->
                when (k) {
                    0 -> {
                        val err = Casa.segnaOggi(v.id)
                        if (err != null) aviso(err) else { refresh(); aviso("Segnata come eseguita oggi") }
                    }
                    1 -> dialogCasaCare(v)
                    2 -> OcBuilder(this)
                        .setTitle("Azzera attività")
                        .setMessage("Riportare '" + v.nome + "' allo stato 'mai eseguita'?")
                        .setNegativeButton("Annulla", null)
                        .setPositiveButton("Azzera") { _, _ ->
                            val err = Casa.azzera(v.id)
                            if (err != null) aviso(err) else { refresh(); aviso("Attività azzerata") }
                        }
                        .show()
                    else -> OcBuilder(this)
                        .setTitle("Eliminare l'attività?")
                        .setMessage(v.nome)
                        .setNegativeButton("Annulla", null)
                        .setPositiveButton("Elimina") { _, _ ->
                            val err = Casa.elimina(v.id)
                            if (err != null) aviso(err) else { refresh(); aviso("Attività eliminata") }
                        }
                        .show()
                }
            }
            .setNegativeButton("Chiudi", null)
            .show()
    }

    private fun dialogFiltriCasa() {
        val (view, corpo) = corpoDialogo()
        val cats = listOf("Tutte") + Casa.voci().map { it.categoria }.distinct().sorted()
        var cat = if (casaCat in cats) casaCat else "Tutte"
        var stato = casaStato
        campoMenu(corpo, "Categoria", cats, cat) { cat = cats[it] }
        campoMenu(corpo, "Stato", CASA_STATI, stato) { stato = CASA_STATI[it] }
        OcBuilder(this)
            .setTitle("Filtri CasaCare")
            .setView(view)
            .setPositiveButton("Applica") { _, _ ->
                casaCat = cat
                casaStato = stato
                refresh()
            }
            .setNegativeButton("Azzera") { _, _ ->
                casaCat = "Tutte"
                casaStato = "Tutti"
                refresh()
            }
            .show()
    }

    private fun dialogCasaCare(v: Casa.Voce?) {
        val (view, corpo) = corpoDialogo()
        fun tilDi(et: View): View {
            var p = et.parent
            while (p != null && p !is TextInputLayout) p = p.parent
            return (p ?: et) as View
        }
        val extra = Casa.voci().map { it.categoria }.filter { it !in Casa.CATEGORIE }.distinct().sorted()
        val altra = "➕ Altra categoria…"
        val cats = Casa.CATEGORIE + extra + altra
        var cat = v?.categoria ?: Casa.CATEGORIE[0]
        var tilAltra: View? = null
        campoMenu(corpo, "Categoria", cats, cat) {
            cat = cats[it]
            tilAltra?.visibility = if (cat == altra) View.VISIBLE else View.GONE
        }
        val etAltra = campoTesto(corpo, "Nuova categoria", "", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES, 40)
        tilDi(etAltra).also { tilAltra = it; it.visibility = View.GONE }
        val etNome = campoTesto(corpo, "Attività", v?.nome ?: "", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES, 120)

        val etichette = Casa.FREQ.map { it.first }
        var fl = when {
            v == null -> "Ogni mese"
            v.freqLabel in etichette -> v.freqLabel
            else -> Casa.PERSONALIZZATA
        }
        var tilGg: View? = null
        campoMenu(corpo, "Frequenza", etichette, fl) {
            fl = etichette[it]
            tilGg?.visibility = if (fl == Casa.PERSONALIZZATA) View.VISIBLE else View.GONE
        }
        val etGg = campoTesto(corpo, "Giorni (frequenza personalizzata)", (v?.freqGiorni ?: 30).toString(), InputType.TYPE_CLASS_NUMBER, 5)
        tilDi(etGg).also { tilGg = it; it.visibility = if (fl == Casa.PERSONALIZZATA) View.VISIBLE else View.GONE }

        var ultima: LocalDate? = v?.ultima?.takeIf { it.isNotBlank() }?.let {
            try { LocalDate.parse(it, FMT_DATA) } catch (_: Exception) { null }
        }
        bottoneData(corpo, "Ultima esecuzione (pressione lunga = mai)", ultima, true) { ultima = it }
        val etNote = campoTesto(corpo, "Note", v?.note ?: "", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES, 120)
        val chk = MaterialCheckBox(this)
        chk.text = "⚖ Obbligatoria per legge (caldaia, canna fumaria, estintore)"
        chk.isChecked = v?.legge ?: false
        corpo.addView(chk)

        val dlg = OcBuilder(this)
            .setTitle(if (v == null) "Nuova attività" else "Modifica attività")
            .setView(view)
            .setPositiveButton("Salva", null)
            .setNegativeButton("Annulla", null)
            .create()
        dlg.show()
        dlg.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val categoria = if (cat == altra) etAltra.text.toString().trim() else cat
            if (categoria.isEmpty()) { etAltra.error = "Inserisci la categoria"; return@setOnClickListener }
            val giorni = Casa.FREQ.first { it.first == fl }.second
                ?: etGg.text.toString().trim().toIntOrNull() ?: -1
            val err = Casa.salva(
                v?.id, categoria, etNome.text.toString(), giorni, fl,
                ultima?.format(FMT_DATA) ?: "", etNote.text.toString(), chk.isChecked
            )
            if (err != null) { aviso(err); return@setOnClickListener }
            dlg.dismiss()
            refresh()
            aviso("Attività salvata")
        }
    }

    private fun schedaDi(p: Pagina): Scheda? = when (p) {
        Pagina.AUTOPARK -> Park
        Pagina.IMMOBIL -> Immo
        Pagina.PET -> Pet
        else -> null
    }

    private val sc: Scheda get() = schedaDi(pagina) ?: Park

    private fun gruppoDocumenti(
        cont: LinearLayout, aggiorna: ArrayList<() -> Unit>, chiave: String, titolo: String,
        riempi: (LinearLayout) -> Unit,
    ) {
        val head = layoutInflater.inflate(R.layout.item_group, cont, false)
        head.findViewById<TextView>(R.id.tvGroup).text = titolo
        val tvFreccia = head.findViewById<TextView>(R.id.tvFreccia)
        val corpo = LinearLayout(this)
        corpo.orientation = LinearLayout.VERTICAL
        riempi(corpo)
        fun agg() {
            val aperto = docAperto == chiave
            corpo.visibility = if (aperto) View.VISIBLE else View.GONE
            tvFreccia.text = if (aperto) "▼" else "▶"
        }
        agg()
        aggiorna.add { agg() }
        head.setOnClickListener {
            docAperto = if (docAperto == chiave) null else chiave
            aggiorna.forEach { it() }
        }
        cont.addView(head)
        cont.addView(corpo)
    }

    private fun nuovoDocumento() {
        if (pagina == Pagina.DOCPERS && Doc.nomiProfili().isEmpty()) {
            aviso("Crea prima un profilo")
            dialogProfili()
            return
        }
        apriDocumento.launch(arrayOf("application/pdf", "image/*"))
    }

    private fun renderDocumenti() {
        intestaLista("🧾 Documenti contabili", "➕  Nuovo documento", "🔎  Filtri")
        val cont = vista<LinearLayout>(R.id.listaGen)
        val tutti = Doc.archiviati()
        if (tutti.isEmpty()) {
            vuoto(cont, "Nessun documento contabile. Tocca «Nuovo documento» oppure usa «Importa dati» dalla home per caricare lo zip del desktop.")
            return
        }
        val q = docQ.trim().lowercase()
        val arch = tutti.filter {
            (docCat.isEmpty() || it.categoria == docCat) && (docTipo.isEmpty() || it.tipo == docTipo) &&
                (q.isEmpty() || (it.titolo + " " + it.categoria + " " + it.nomeFile).lowercase().contains(q))
        }
        val verde = ContextCompat.getColor(this, R.color.oc_green)
        val rosso = ContextCompat.getColor(this, R.color.oc_red)
        val entrate = arch.filter { it.tipo == "Entrata" }.sumOf { it.importo ?: 0.0 }
        val uscite = arch.filter { it.tipo != "Entrata" }.sumOf { it.importo ?: 0.0 }
        val senzaFile = arch.count { it.file == null }
        val filtri = listOfNotNull(
            q.takeIf { it.isNotEmpty() }?.let { "«$it»" }, docCat.takeIf { it.isNotEmpty() }, docTipo.takeIf { it.isNotEmpty() },
        )
        riga(
            cont, arch.size.toString() + " documenti" + (if (filtri.isNotEmpty()) " su ${tutti.size}" else ""),
            "Entrate " + euro(entrate) + " · Uscite " + euro(uscite) +
                (if (senzaFile > 0) " · $senzaFile senza file" else "") +
                (if (filtri.isNotEmpty()) "\nFiltri: " + filtri.joinToString(" · ") else ""),
            "", null, null
        )
        val aggiorna = ArrayList<() -> Unit>()
        val perAnno = arch.groupBy { it.data?.year?.toString() ?: "Senza data" }
            .toSortedMap(compareByDescending { it })
        if (filtri.isNotEmpty() && docAperto?.removePrefix("A:") !in perAnno.keys) docAperto = perAnno.keys.firstOrNull()?.let { "A:$it" }
        for ((anno, lista) in perAnno) {
            gruppoDocumenti(cont, aggiorna, "A:$anno", "🧾  $anno · ${lista.size}") { corpo ->
                for (d in lista) {
                    val sotto = listOfNotNull(
                        d.data?.let { Doc.fmt(it) },
                        d.categoria.takeIf { it.isNotEmpty() },
                        if (d.file == null) "⚠️ file non importato" else null,
                    ).joinToString(" · ")
                    val imp = d.importo
                    val colore = if (d.tipo == "Entrata") verde else rosso
                    riga(corpo, d.titolo, sotto, if (imp != null) euro(imp) else "", if (imp != null) colore else null) {
                        dialogDocumentoContabile(d)
                    }
                }
            }
        }
    }

    private fun dialogFiltriDoc() {
        val cats = Doc.archiviati().map { it.categoria }.filter { it.isNotEmpty() }.distinct().sorted()
        var cat = docCat
        var tipo = docTipo
        val (view, corpo) = corpoDialogo()
        val etQ = campoTesto(corpo, "Cerca (descrizione, categoria, file)", docQ)
        campoMenu(corpo, "Categoria", listOf("Tutte") + cats, cat.ifEmpty { "Tutte" }) { cat = if (it == 0) "" else cats[it - 1] }
        campoMenu(corpo, "Tipo", listOf("Tutti", "Entrata", "Uscita"), tipo.ifEmpty { "Tutti" }) {
            tipo = if (it == 0) "" else listOf("Entrata", "Uscita")[it - 1]
        }
        OcBuilder(this)
            .setTitle("Filtra documenti")
            .setView(view)
            .setPositiveButton("Applica") { _, _ ->
                docQ = etQ.text.toString(); docCat = cat; docTipo = tipo
                renderLista()
            }
            .setNeutralButton("Azzera") { _, _ ->
                docQ = ""; docCat = ""; docTipo = ""
                renderLista()
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun renderDocPers() {
        intestaLista("🪪 Documenti personali", "➕  Nuovo documento", "👤  Profili")
        val cont = vista<LinearLayout>(R.id.listaGen)
        val pers = Doc.personali()
        if (pers.isEmpty()) {
            vuoto(cont, "Nessun profilo. Tocca «Profili» per crearne uno, oppure usa «Importa dati» dalla home per caricare lo zip del desktop.")
            return
        }
        val oggi = LocalDate.now()
        val rosso = ContextCompat.getColor(this, R.color.oc_red)
        val arancio = ContextCompat.getColor(this, R.color.oc_orange)
        fun scad(sc: LocalDate): Pair<String, Int?> {
            val gg = java.time.temporal.ChronoUnit.DAYS.between(oggi, sc)
            val t = when {
                gg < 0 -> "scaduto da " + (-gg) + " gg"
                gg == 0L -> "scade oggi"
                gg <= 30 -> "tra $gg gg"
                else -> Doc.fmt(sc)
            }
            return t to (if (gg < 0) rosso else if (gg <= 30) arancio else null)
        }
        val urgenti = pers.flatMap { (p, l) ->
            l.filter { d -> d.scadenza != null && java.time.temporal.ChronoUnit.DAYS.between(oggi, d.scadenza) <= 30 }.map { p to it }
        }.sortedBy { it.second.scadenza }
        if (urgenti.isNotEmpty()) {
            sezione(cont, "⏰ SCADENZE · " + urgenti.size)
            for ((p, d) in urgenti) {
                val (t, c) = scad(d.scadenza!!)
                riga(cont, d.titolo, p + (if (d.categoria.isNotEmpty()) " · " + d.categoria else ""), t, c) { dialogDocumento(d) }
            }
        }
        val aggiorna = ArrayList<() -> Unit>()
        for ((profilo, lista) in pers) {
            gruppoDocumenti(cont, aggiorna, "P:$profilo", "👤  " + profilo.uppercase() + " · " + lista.size) { corpo ->
                if (lista.isEmpty()) vuoto(corpo, "Nessun documento in questo profilo.")
                for (d in lista) {
                    val sc = d.scadenza
                    val (valore, colore) = if (sc != null) scad(sc) else ("" to null)
                    val sotto = listOfNotNull(
                        d.categoria.takeIf { it.isNotEmpty() },
                        sc?.let { "scade " + Doc.fmt(it) },
                        if (d.file == null) "⚠️ file non importato" else null,
                    ).joinToString(" · ")
                    riga(corpo, d.titolo, sotto, valore, colore) { dialogDocumento(d) }
                }
            }
        }
    }

    private fun dialogProfili() {
        val pers = Doc.personali()
        val voci = pers.map { (p, l) -> "👤 $p · ${l.size} doc  (tocca per eliminare)" }
        val b = OcBuilder(this)
            .setTitle("Profili (max ${Doc.MAX_PROFILI})")
            .setPositiveButton("Nuovo profilo") { _, _ -> dialogNuovoProfilo() }
            .setNegativeButton("Chiudi", null)
        if (voci.isNotEmpty()) {
            b.setItems(voci.toTypedArray()) { _, k ->
                val nome = pers[k].first
                OcBuilder(this)
                    .setTitle("Elimina profilo")
                    .setMessage("Eliminare il profilo «$nome» e tutti i suoi documenti? L'operazione non è reversibile.")
                    .setNegativeButton("Annulla", null)
                    .setPositiveButton("Elimina") { _, _ ->
                        try {
                            Doc.eliminaProfilo(nome)
                        } catch (e: Exception) {
                            aviso("Eliminazione non riuscita: " + e.message)
                            return@setPositiveButton
                        }
                        if (docAperto == "P:$nome") docAperto = null
                        refresh()
                        aviso("Profilo eliminato")
                    }
                    .show()
            }
        } else b.setMessage("Nessun profilo.")
        b.show()
    }

    private fun dialogNuovoProfilo() {
        val (view, corpo) = corpoDialogo()
        val et = campoTesto(corpo, "Nome (max 20 caratteri)", "", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS, 20)
        val dlg = OcBuilder(this)
            .setTitle("Nuovo profilo")
            .setView(view)
            .setPositiveButton("Crea", null)
            .setNegativeButton("Annulla", null)
            .create()
        dlg.show()
        dlg.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val err = try { Doc.nuovoProfilo(et.text.toString()) } catch (e: Exception) { e.message }
            if (err != null) {
                et.error = err
                return@setOnClickListener
            }
            dlg.dismiss()
            refresh()
            aviso("Profilo creato")
        }
    }

    private fun bottoneData(
        corpo: LinearLayout, etichetta: String, iniziale: LocalDate?, azzerabile: Boolean, onCambia: (LocalDate?) -> Unit,
    ): (LocalDate?) -> Unit {
        var cur = iniziale
        val btn = MaterialButton(ctxDialogo, null, com.google.android.material.R.attr.materialButtonOutlinedStyle)
        fun testo() { btn.text = etichetta + ": " + (cur?.format(FMT_DATA) ?: "nessuna") }
        testo()
        btn.setOnClickListener {
            val b = cur ?: LocalDate.now()
            DatePickerDialog(
                this,
                { _, y, m, d -> cur = LocalDate.of(y, m + 1, d); testo(); onCambia(cur) },
                b.year, b.monthValue - 1, b.dayOfMonth
            ).show()
        }
        if (azzerabile) btn.setOnLongClickListener { cur = null; testo(); onCambia(null); true }
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.topMargin = 16
        corpo.addView(btn, lp)
        return { v: LocalDate? -> cur = v; testo() }
    }

    private fun bottoneIa(corpo: LinearLayout): MaterialButton {
        val btn = MaterialButton(ctxDialogo, null, com.google.android.material.R.attr.materialButtonOutlinedStyle)
        btn.text = "🤖 Compila con IA"
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.bottomMargin = 8
        corpo.addView(btn, lp)
        return btn
    }

    private fun collegaIa(
        btn: MaterialButton, file: java.io.File, prompt: String, vivo: () -> Boolean,
        applica: (org.json.JSONObject) -> Unit,
    ) {
        fun lancia() {
            if (Gemini.chiave(this).isEmpty()) {
                dialogChiaveGemini { lancia() }
                return
            }
            btn.isEnabled = false
            btn.text = "🤖 Analisi in corso…"
            Thread {
                val r = runCatching { Gemini.analizza(this, file, prompt) }
                runOnUiThread {
                    btn.isEnabled = true
                    btn.text = "🤖 Compila con IA"
                    if (!vivo()) return@runOnUiThread
                    r.onSuccess {
                        try {
                            applica(it)
                            aviso("Campi compilati: verifica e archivia")
                        } catch (_: Exception) {
                            aviso("Risposta IA non leggibile")
                        }
                    }.onFailure { aviso(Gemini.messaggio(it)) }
                }
            }.start()
        }
        btn.setOnClickListener { lancia() }
        btn.setOnLongClickListener { dialogChiaveGemini(null); true }
        if (Gemini.chiave(this).isNotEmpty()) lancia()
    }

    private fun campiGemini(corpo: LinearLayout): Pair<TextInputEditText, TextInputEditText> {
        val etK = campoTesto(
            corpo, "Chiave API Gemini", Gemini.chiave(this),
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        )
        val btnQrK = MaterialButton(ctxDialogo, null, com.google.android.material.R.attr.materialButtonOutlinedStyle)
        btnQrK.text = "📷 Scansiona QR chiave Gemini"
        corpo.addView(btnQrK)
        btnQrK.setOnClickListener { scansionaQr { v -> etK.setText(v) } }
        val etM = campoTesto(corpo, "Modello", Gemini.modello(this), InputType.TYPE_CLASS_TEXT)
        val btnMod = MaterialButton(ctxDialogo, null, com.google.android.material.R.attr.materialButtonOutlinedStyle)
        btnMod.text = "📋 Leggi modelli Gemini"
        corpo.addView(btnMod)
        btnMod.setOnClickListener {
            val k = etK.text.toString().trim()
            if (k.isEmpty()) {
                aviso("Inserisci o scansiona prima la chiave API")
                return@setOnClickListener
            }
            btnMod.isEnabled = false
            Thread {
                var modelli: List<String> = emptyList()
                var err: String? = null
                try {
                    modelli = Gemini.elencaModelli(k)
                } catch (e: Exception) {
                    err = Gemini.messaggio(e)
                }
                runOnUiThread {
                    btnMod.isEnabled = true
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    if (err != null) {
                        aviso("Errore API: $err")
                    } else if (modelli.isEmpty()) {
                        aviso("Nessun modello di generazione trovato")
                    } else {
                        val cur = etM.text.toString().trim()
                        OcBuilder(this)
                            .setTitle("Seleziona modello")
                            .setSingleChoiceItems(modelli.toTypedArray(), modelli.indexOf(cur)) { d, i ->
                                etM.setText(modelli[i])
                                d.dismiss()
                            }
                            .setNegativeButton("Annulla", null)
                            .show()
                    }
                }
            }.start()
        }
        return etK to etM
    }

    private fun dialogChiaveGemini(dopo: (() -> Unit)?) {
        val (view, corpo) = corpoDialogo()
        val info = TextView(ctxDialogo)
        info.text = "L'analisi invia il documento a Google Gemini con la tua chiave API personale " +
            "(gratuita su aistudio.google.com). La chiave resta solo su questo telefono."
        info.setPadding(4, 4, 4, 12)
        corpo.addView(info)
        val (etK, etM) = campiGemini(corpo)
        OcBuilder(this)
            .setTitle("🤖 Analisi automatica")
            .setView(view)
            .setPositiveButton("Salva") { _, _ ->
                val k = etK.text.toString().trim()
                if (k.isEmpty()) {
                    aviso("Chiave vuota")
                    return@setPositiveButton
                }
                Gemini.salva(this, k, etM.text.toString())
                aviso("Chiave salvata")
                dopo?.invoke()
            }
            .setNeutralButton("Rimuovi") { _, _ ->
                Gemini.salva(this, "", "")
                aviso("Chiave rimossa")
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun parseTags(t: String): List<String> =
        t.split(Regex("[\\s,;]+")).map { it.trimStart('#') }.filter { it.isNotEmpty() }

    private fun dialogDocContabile(d: Doc.Item?) {
        val staged = if (d == null) docStaged else null
        if (d == null && staged == null) return
        var data = d?.data ?: LocalDate.now()
        val catsBase = (Repo.categorie() + "Generica").distinct().sorted()
        var cat = d?.categoria?.ifEmpty { "Generica" } ?: "Generica"
        val cats = if (cat in catsBase) catsBase else (catsBase + cat).sorted()
        var tipo = d?.tipo?.ifEmpty { "Uscita" } ?: "Uscita"
        val sp0 = if (d != null) Doc.spesaCollegata(d) else null
        var metodo = sp0?.metodo ?: ""
        var conto = sp0?.conto ?: ""
        val (view, corpo) = corpoDialogo()
        if (d == null) {
            val tv = TextView(ctxDialogo)
            tv.text = "📎 File selezionato"
            tv.setPadding(4, 4, 4, 4)
            corpo.addView(tv)
        }
        val btnIa = if (d == null) bottoneIa(corpo) else null
        val etDesc = campoTesto(corpo, "Descrizione", d?.titolo ?: "", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES, 80)
        val etImp = campoTesto(
            corpo, "Importo (€)", d?.importo?.let { String.format(Locale.ITALY, "%.2f", it) } ?: "", TIPO_DECIMALE, 14
        )
        Calc.collega(this, etImp)
        val setData = bottoneData(corpo, "Data", data, false) { data = it ?: data }
        var tilTipo: TextInputLayout? = null
        var tilCat: TextInputLayout? = null
        tilCat = campoMenu(corpo, "Categoria", cats, cat) {
            cat = cats[it]
            if (d == null) {
                tipo = Repo.tipoCategoria(cat)
                (tilTipo?.editText as? MaterialAutoCompleteTextView)?.setText(tipo, false)
            }
        }
        tilTipo = campoMenu(corpo, "Tipo", listOf("Entrata", "Uscita"), tipo) { tipo = listOf("Entrata", "Uscita")[it] }
        var etTag: TextInputEditText? = null
        if (d == null || sp0 != null) {
            val nomiMetodi = listOf("") + Repo.METODI.map { it.first }
            campoMenu(
                corpo, "Metodo di pagamento (facoltativo)",
                nomiMetodi.map { if (it.isBlank()) "Nessuno" else Repo.metodoConEmoji(it) },
                if (metodo.isBlank()) "Nessuno" else Repo.metodoConEmoji(metodo)
            ) { metodo = nomiMetodi[it] }
            val nomiConti = listOf("") + Repo.conti().map { it.nome }
            if (nomiConti.size > 1) {
                campoMenu(
                    corpo, "Conto (facoltativo)", nomiConti.map { if (it.isBlank()) "Nessuno" else it },
                    if (conto.isBlank()) "Nessuno" else conto
                ) { conto = nomiConti[it] }
            }
            etTag = campoTesto(
                corpo, "Tag (separati da spazio)", sp0?.hashtag?.joinToString(" ") ?: "", InputType.TYPE_CLASS_TEXT
            )
            etTag?.let { suggerimentiTag(it) }
        }
        val b = OcBuilder(this)
            .setTitle(if (d == null) "Nuovo documento contabile" else "Modifica documento")
            .setView(view)
            .setPositiveButton("Salva", null)
            .setNegativeButton("Annulla", null)
        if (d != null) b.setNeutralButton("Elimina", null)
        val dlg = b.create()
        dlg.setOnDismissListener { if (d == null) { docStaged?.delete(); docStaged = null } }
        dlg.show()
        if (d != null) {
            dlg.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                dlg.dismiss()
                eliminaDocContabile(d)
            }
        }
        if (btnIa != null && staged != null) {
            val cp = Repo.categorie()
            collegaIa(btnIa, staged, Gemini.promptContabile(cp), { dlg.isShowing }) { j ->
                val r = Gemini.leggiContabile(j, cp)
                etDesc.setText(r.descrizione)
                etImp.setText(String.format(Locale.ITALY, "%.2f", r.importo))
                data = r.data
                setData(r.data)
                r.tipo?.let { tipo = it; (tilTipo?.editText as? MaterialAutoCompleteTextView)?.setText(it, false) }
                r.categoria?.let { c ->
                    if (c in cats) {
                        cat = c
                        (tilCat?.editText as? MaterialAutoCompleteTextView)?.setText(c, false)
                    }
                }
            }
        }
        dlg.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val desc = etDesc.text.toString().trim()
            if (desc.isEmpty()) {
                etDesc.error = "Inserisci una descrizione"
                return@setOnClickListener
            }
            val imp = importoOk(etImp.text.toString())
            if (imp == null) {
                etImp.error = "Importo non valido"
                return@setOnClickListener
            }
            if (d == null) {
                val tmp = staged!!
                val dup = try { Doc.duplicato(tmp) } catch (_: Exception) { null }
                fun procedi() {
                    val tags = parseTags(etTag?.text?.toString() ?: "")
                    try {
                        Doc.aggiungiContabile(tmp, data, cat, desc, tipo, imp)
                    } catch (e: Exception) {
                        aviso("Salvataggio non riuscito: " + e.message)
                        return
                    }
                    docStaged = null
                    dlg.dismiss()
                    val sp = Repo.voci().firstOrNull {
                        it.data == data && Math.abs(it.importo - imp) < 0.01 && it.tipo == tipo
                    }
                    if (sp != null) {
                        OcBuilder(this)
                            .setTitle("Aggiorna spese")
                            .setMessage(
                                "Ho trovato una spesa simile:\n\nCategoria: ${sp.categoria}\nDescrizione: ${Doc.senzaAll(sp.descrizione)}\n" +
                                    "Importo: ${euro(sp.importo)}\n\nSì → aggancia il documento alla spesa esistente\nNo → salva solo il documento"
                            )
                            .setNegativeButton("No", null)
                            .setPositiveButton("Sì") { _, _ ->
                                Repo.salvaVoce(
                                    sp.id, data, cat, "ALL· " + desc, imp, tipo,
                                    metodo.ifEmpty { sp.metodo }, if (tags.isEmpty()) sp.hashtag else tags, conto.ifEmpty { sp.conto }
                                )
                                refresh()
                            }
                            .show()
                    } else {
                        OcBuilder(this)
                            .setTitle("Nuova spesa")
                            .setMessage("Nessuna spesa trovata per questa data/importo.\n\nSì → aggiungi anche la spesa\nNo → salva solo il documento")
                            .setNegativeButton("No", null)
                            .setPositiveButton("Sì") { _, _ ->
                                Repo.salvaVoce(null, data, cat, "ALL· " + desc, imp, tipo, metodo, tags, conto)
                                refresh()
                            }
                            .show()
                    }
                    refresh()
                    aviso("Documento salvato")
                }
                if (dup != null) {
                    OcBuilder(this)
                        .setTitle("Documento già archiviato")
                        .setMessage("Questo file risulta già archiviato come:\n\n$dup\n\nVuoi importarlo comunque?")
                        .setNegativeButton("No", null)
                        .setPositiveButton("Importa comunque") { _, _ -> procedi() }
                        .show()
                } else procedi()
            } else {
                val sp = Doc.spesaCollegata(d)
                val tagsMod = parseTags(etTag?.text?.toString() ?: "")
                val metodoMod = metodo
                val contoMod = conto
                try {
                    Doc.modificaContabile(d, data, cat, desc, tipo, imp)
                } catch (e: Exception) {
                    aviso("Salvataggio non riuscito: " + e.message)
                    return@setOnClickListener
                }
                dlg.dismiss()
                refresh()
                aviso("Documento aggiornato")
                if (sp != null) {
                    OcBuilder(this)
                        .setTitle("Aggiorna spesa")
                        .setMessage(
                            "Ho trovato una spesa collegata:\n\nCategoria: ${sp.categoria}\nDescrizione: ${Doc.senzaAll(sp.descrizione)}\n" +
                                "Importo: ${euro(sp.importo)}\n\nVuoi aggiornare anche quella?"
                        )
                        .setNegativeButton("No", null)
                        .setPositiveButton("Sì") { _, _ ->
                            Repo.salvaVoce(sp.id, data, cat, "ALL· " + desc, imp, tipo, metodoMod, tagsMod, contoMod)
                            refresh()
                        }
                        .show()
                }
            }
        }
    }

    private fun eliminaDocContabile(d: Doc.Item) {
        val sp = Doc.spesaCollegata(d)
        fun elimina(conSpesa: Boolean?) {
            try {
                if (sp != null && conSpesa != null) {
                    if (conSpesa) Repo.elimina(sp.id)
                    else Repo.salvaVoce(sp.id, sp.data, sp.categoria, Doc.senzaAll(sp.descrizione), sp.importo, sp.tipo, sp.metodo, sp.hashtag, sp.conto)
                }
                Doc.eliminaContabile(d)
            } catch (e: Exception) {
                aviso("Eliminazione non riuscita: " + e.message)
                return
            }
            refresh()
            aviso("Documento eliminato")
        }
        OcBuilder(this)
            .setTitle("Elimina documento")
            .setMessage("Eliminare il documento «" + d.titolo + "»?")
            .setNegativeButton("Annulla", null)
            .setPositiveButton("Elimina") { _, _ ->
                if (sp == null) elimina(null)
                else OcBuilder(this)
                    .setTitle("Database spese")
                    .setMessage("Trovato un movimento collegato. Vuoi rimuovere anche quello?")
                    .setNegativeButton("No, tienilo") { _, _ -> elimina(false) }
                    .setPositiveButton("Sì, rimuovi") { _, _ -> elimina(true) }
                    .show()
            }
            .show()
    }

    private fun dialogDocPers(d: Doc.Item?) {
        val staged = if (d == null) docStaged else null
        if (d == null && staged == null) return
        val profili = Doc.nomiProfili()
        if (d == null && profili.isEmpty()) {
            aviso("Crea prima un profilo")
            return
        }
        var profilo = d?.profilo ?: (docAperto?.takeIf { it.startsWith("P:") }?.removePrefix("P:")?.takeIf { it in profili } ?: profili.first())
        var data = d?.data ?: LocalDate.now()
        var scad: LocalDate? = d?.scadenza
        var cats = Doc.categoriePers(profilo)
        var cat = d?.categoria?.ifEmpty { null } ?: cats.first()
        if (cat !in cats) cats = (cats + cat).sortedBy { it.lowercase(Locale.ITALY) }
        val (view, corpo) = corpoDialogo()
        var tilCat: TextInputLayout? = null
        val btnIa = if (d == null) bottoneIa(corpo) else null
        if (d == null) {
            campoMenu(corpo, "Profilo", profili, profilo) {
                profilo = profili[it]
                cats = Doc.categoriePers(profilo)
                if (cat !in cats) cat = cats.first()
                val ac = tilCat?.editText as? MaterialAutoCompleteTextView
                ac?.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, cats))
                ac?.setText(cat, false)
            }
        } else {
            val tv = TextView(ctxDialogo)
            tv.text = "👤 Profilo: $profilo"
            tv.setPadding(4, 4, 4, 4)
            corpo.addView(tv)
        }
        tilCat = campoMenu(corpo, "Categoria", cats, cat) { cat = cats[it] }
        val descIniz = if (d == null || d.categoria == d.titolo) "" else d.titolo
        val etDesc = campoTesto(corpo, "Descrizione", descIniz, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES, 60)
        bottoneData(corpo, "Data", data, false) { data = it ?: data }
        val setScad = bottoneData(corpo, "Scadenza (tieni premuto per togliere)", scad, true) { scad = it }
        val etNote = campoTesto(corpo, "Note", d?.note ?: "", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES, 120)
        val b = OcBuilder(this)
            .setTitle(if (d == null) "Nuovo documento personale" else "Modifica documento")
            .setView(view)
            .setPositiveButton("Salva", null)
            .setNegativeButton("Annulla", null)
        if (d != null) b.setNeutralButton("Elimina", null)
        val dlg = b.create()
        dlg.setOnDismissListener { if (d == null) { docStaged?.delete(); docStaged = null } }
        dlg.show()
        if (d != null) {
            dlg.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                dlg.dismiss()
                OcBuilder(this)
                    .setTitle("Conferma eliminazione")
                    .setMessage("Eliminare «" + d.titolo + "»? L'operazione non è reversibile.")
                    .setNegativeButton("Annulla", null)
                    .setPositiveButton("Elimina") { _, _ ->
                        try {
                            Doc.eliminaPers(d)
                        } catch (e: Exception) {
                            aviso("Eliminazione non riuscita: " + e.message)
                            return@setPositiveButton
                        }
                        refresh()
                        aviso("Documento eliminato")
                    }
                    .show()
            }
        }
        if (btnIa != null && staged != null) {
            collegaIa(btnIa, staged, Gemini.promptPers(cats), { dlg.isShowing }) { j ->
                val r = Gemini.leggiPers(j, cats)
                if (r.descrizione.isNotEmpty()) etDesc.setText(r.descrizione)
                r.categoria?.let { c ->
                    cat = c
                    (tilCat?.editText as? MaterialAutoCompleteTextView)?.setText(c, false)
                }
                val sc = r.scadenza
                if (sc != null) {
                    scad = sc
                    setScad(sc)
                }
            }
        }
        dlg.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val desc = etDesc.text.toString().trim()
            if (desc.isEmpty()) {
                etDesc.error = "Inserisci una descrizione"
                return@setOnClickListener
            }
            try {
                if (d == null) {
                    Doc.aggiungiPers(staged!!, profilo, data, cat, desc, etNote.text.toString().trim(), scad)
                    docStaged = null
                    docAperto = "P:$profilo"
                } else {
                    Doc.modificaPers(d, data, cat, desc, etNote.text.toString().trim(), scad)
                }
            } catch (e: Exception) {
                aviso("Salvataggio non riuscito: " + e.message)
                return@setOnClickListener
            }
            dlg.dismiss()
            refresh()
            aviso(if (d == null) "Documento archiviato" else "Documento aggiornato")
        }
    }

    private val mesiNome = listOf(
        "Gennaio", "Febbraio", "Marzo", "Aprile", "Maggio", "Giugno",
        "Luglio", "Agosto", "Settembre", "Ottobre", "Novembre", "Dicembre",
    )

    private fun renderUtenze() {
        intestaLista("💡 Utenze", "➕  Nuova lettura")
        val cont = vista<LinearLayout>(R.id.listaGen)
        if (Utenze.vuoto()) {
            vuoto(
                cont,
                "Nessuna utenza. Tocca «Nuova lettura» per iniziare, " +
                    "oppure importa lo zip di OrbitaCasa."
            )
        }
        riga(
            cont, "🏷️ Categorie delle bollette",
            Utenze.TIPI.joinToString(" · ") { it + ": " + (Utenze.categoriaBollette(it).ifEmpty { "—" }) },
            "", null
        ) { dialogCategorieBollette() }
        val aggiornaUtenze = ArrayList<() -> Unit>()
        for (u in Utenze.TIPI) {
            val anni = Utenze.anni(u)
            val anag = Utenze.anagrafica(u)
            val unita = Utenze.UNITA[u] ?: ""
            val head = layoutInflater.inflate(R.layout.item_group, cont, false)
            head.findViewById<TextView>(R.id.tvGroup).text = (Utenze.ICONE[u] ?: "") + "  " + u.uppercase()
            val tvFreccia = head.findViewById<TextView>(R.id.tvFreccia)
            val corpo = LinearLayout(this)
            corpo.orientation = LinearLayout.VERTICAL

            riga(
                corpo, "📇 " + (anag["Ragione sociale"] ?: "Anagrafica"),
                listOfNotNull(
                    anag["Numero contratto"]?.let { "Contratto $it" }, anag["POD / PDR"],
                    anag["Nome Offerta"],
                ).joinToString(" · ").ifEmpty { "Tocca per compilare" },
                "✏️", null
            ) { dialogAnagraficaUtenza(u) }
            if (anni.isEmpty()) vuoto(corpo, "Nessuna lettura.")
            for (a in anni) {
                riga(
                    corpo, a.anno, a.mesi.toString() + " mesi con lettura",
                    Utenze.fmt(a.totale) + " " + unita, null
                ) { dialogLetture(u, a) }
            }

            fun aggiorna() {
                val aperto = utenzaAperta == u
                corpo.visibility = if (aperto) View.VISIBLE else View.GONE
                tvFreccia.text = if (aperto) "▼" else "▶"
            }
            aggiorna()
            aggiornaUtenze.add { aggiorna() }
            head.setOnClickListener {
                utenzaAperta = if (utenzaAperta == u) null else u
                aggiornaUtenze.forEach { it() }
            }
            cont.addView(head)
            cont.addView(corpo)
        }
    }

    private fun dialogAnagraficaUtenza(u: String) {
        val attuale = Utenze.anagrafica(u)
        val (view, corpo) = corpoDialogo()
        val campi = LinkedHashMap<String, TextInputEditText>()
        fun gruppoCampi(titolo: String, chiavi: List<String>) {
            sezionePiccola(corpo, titolo)
            for (k in chiavi) {
                val tipo = when (k) {
                    "Telefono", "Pronto Intervento" -> InputType.TYPE_CLASS_PHONE
                    "Email" -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                    "Costo Unitario", "Quota Fissa" -> InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                    "IBAN" -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
                    "Note" -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                        InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                    else -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                }
                campi[k] = campoTesto(corpo, Utenze.ETICHETTE[k] ?: k, attuale[k] ?: "", tipo)
            }
        }
        gruppoCampi("CONTRATTO", Utenze.CAMPI_CONTRATTO)
        gruppoCampi("OFFERTA E DATI TECNICI", Utenze.CAMPI_OFFERTA)
        gruppoCampi("ASSISTENZA E PAGAMENTI", Utenze.CAMPI_ASSISTENZA)

        val dlg = OcBuilder(this)
            .setTitle((Utenze.ICONE[u] ?: "") + " Anagrafica " + u)
            .setView(view)
            .setPositiveButton("Salva", null)
            .setNegativeButton("Annulla", null)
            .create()
        dlg.show()
        dlg.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            for (k in listOf("Costo Unitario", "Quota Fissa")) {
                val t = campi[k]!!.text.toString().trim()
                if (t.isNotEmpty() && t.replace(',', '.').toDoubleOrNull() == null) {
                    campi[k]!!.error = "Valore non valido"
                    return@setOnClickListener
                }
            }
            try {
                Utenze.salvaAnagrafica(u, campi.mapValues { it.value.text.toString() })
            } catch (e: Exception) {
                aviso("Salvataggio non riuscito: " + e.message)
                return@setOnClickListener
            }
            dlg.dismiss()
            refresh()
            aviso("Anagrafica salvata")
        }
    }

    private fun dialogLetture(u: String, a: Utenze.Anno) {
        val unita = Utenze.UNITA[u] ?: ""
        val anno = a.anno.toIntOrNull() ?: return
        val (view, corpo) = corpoDialogo()
        val l12 = Utenze.anno12(u, a.anno)
        val cu = Utenze.costoUnitario(u)
        val boll = Utenze.bollette(u)

        val att = l12.filter { it.att > 0 || it.consumo > 0 }
        val tot = att.sumOf { it.consumo }
        val riepilogo = ArrayList<String>()
        riepilogo.add("Totale anno: " + Utenze.fmt(tot) + " $unita")
        if (att.isNotEmpty()) {
            val alto = att.maxByOrNull { it.consumo }!!
            val basso = att.minByOrNull { it.consumo }!!
            riepilogo.add("Media mensile: " + Utenze.fmt(tot / att.size) + " $unita")
            riepilogo.add("Più alto: " + mesiNome[alto.mese - 1] + " (" + Utenze.fmt(alto.consumo) + ")")
            riepilogo.add("Più basso: " + mesiNome[basso.mese - 1] + " (" + Utenze.fmt(basso.consumo) + ")")
        }
        if (cu != null && tot > 0) riepilogo.add("Stima costo: " + euro(tot * cu))
        if (Utenze.categoriaBollette(u).isNotEmpty()) {
            val tb = boll.filterKeys { it.endsWith("/" + a.anno) }.values.sum()
            riepilogo.add("Bollette anno: " + (if (tb > 0) euro(tb) else "—"))
        }
        if (Utenze.totaleAnno(u, anno - 1) != null) {
            riepilogo.add("vs " + (anno - 1) + ": " + Utenze.variazione(tot, Utenze.totaleAnno(u, anno - 1)))
        }
        val tv = TextView(ctxDialogo)
        tv.text = riepilogo.joinToString("\n")
        tv.setPadding(4, 4, 4, 12)
        corpo.addView(tv)

        var dlgRef: androidx.appcompat.app.AlertDialog? = null
        for (l in l12) {
            val r = inflDialogo.inflate(R.layout.item_voce, corpo, false)
            r.findViewById<TextView>(R.id.tvTit).text = mesiNome[l.mese - 1]
            val ha = l.att > 0 || l.consumo > 0
            val sotto = if (ha) {
                val v = Utenze.variazione(l.consumo, Utenze.consumoMese(u, anno - 1, l.mese))
                Utenze.fmt(l.prec) + " → " + Utenze.fmt(l.att) + (if (v != "n/d") "  ·  vs " + (anno - 1) + " " + v else "")
            } else "nessuna lettura"
            val tvSub = r.findViewById<TextView>(R.id.tvSub)
            val b = boll[String.format(Locale.US, "%02d/%d", l.mese, anno)]
            val rigaB = if (b != null) {
                var t = "Bolletta " + euro(b)
                if (cu != null && ha) t += " (" + String.format(Locale.ITALY, "%+.2f", b - l.consumo * cu) + " € sulla stima)"
                "\n" + t
            } else ""
            tvSub.text = sotto + rigaB
            r.findViewById<TextView>(R.id.tvImp).text = if (ha) Utenze.fmt(l.consumo) + " $unita" else "—"
            r.setOnClickListener {
                dlgRef?.dismiss()
                dialogLettura(u, anno, l.mese)
            }
            corpo.addView(r)
        }

        val dlg = OcBuilder(this)
            .setTitle((Utenze.ICONE[u] ?: "") + " " + u + " " + a.anno)
            .setView(view)
            .setPositiveButton("Confronta") { _, _ -> dialogConfrontoAnni(u, anno) }
            .setNegativeButton("Chiudi", null)
            .create()
        dlgRef = dlg
        dlg.show()
    }

    private fun dialogCategorieBollette() {
        val cats = Repo.categorie().sortedBy { it.lowercase() }
        if (cats.isEmpty()) {
            aviso("Nessuna categoria di spesa disponibile")
            return
        }
        val nessuna = "(nessuna)"
        val (view, corpo) = corpoDialogo()
        val tv = TextView(ctxDialogo)
        tv.text = "In quale categoria registri le bollette? Accanto a ogni mese compare la bolletta arrivata. " +
            "Puoi usare la stessa categoria per tutte (es. «Bollette»): acqua, luce e gas si riconoscono dalla descrizione della spesa."
        tv.textSize = 13f
        tv.setPadding(4, 4, 4, 8)
        corpo.addView(tv)
        val scelte = HashMap<String, String>()
        for (u in Utenze.TIPI) {
            val att = Utenze.categoriaProposta(u, cats)
            scelte[u] = att
            campoMenu(
                corpo, (Utenze.ICONE[u] ?: "") + " " + u, listOf(nessuna) + cats, att.ifEmpty { nessuna }
            ) { scelte[u] = if (it == 0) "" else cats[it - 1] }
        }
        val dlg = OcBuilder(this)
            .setTitle("Categorie delle bollette")
            .setView(view)
            .setPositiveButton("Salva", null)
            .setNegativeButton("Annulla", null)
            .create()
        dlg.show()
        dlg.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            try {
                Utenze.salvaCategorieBollette(scelte)
            } catch (e: Exception) {
                aviso("Salvataggio non riuscito: " + e.message)
                return@setOnClickListener
            }
            dlg.dismiss()
            refresh()
        }
    }

    private fun dialogConfrontoAnni(u: String, annoBase: Int) {
        val unita = Utenze.UNITA[u] ?: ""
        val presenti = Utenze.anni(u).mapNotNull { it.anno.toIntOrNull() }.sorted()
        if (presenti.size < 2) {
            aviso("Servono almeno due anni con letture")
            return
        }
        var a2 = annoBase
        var a1 = presenti.lastOrNull { it < a2 } ?: presenti.first { it != a2 }
        val (view, corpo) = corpoDialogo()
        val box = LinearLayout(ctxDialogo)
        box.orientation = LinearLayout.VERTICAL
        fun disegna() {
            box.removeAllViews()
            val tv = TextView(ctxDialogo)
            tv.typeface = Typeface.MONOSPACE
            tv.textSize = 12f
            val righe = ArrayList<String>()
            righe.add(String.format(Locale.ITALY, "%-4s %9s %9s %9s", "", a1.toString(), a2.toString(), "Var."))
            var t1 = 0.0
            var t2 = 0.0
            for (m in 1..12) {
                val c1 = Utenze.consumoMese(u, a1, m)
                val c2 = Utenze.consumoMese(u, a2, m)
                t1 += c1 ?: 0.0
                t2 += c2 ?: 0.0
                righe.add(
                    String.format(
                        Locale.ITALY, "%-4s %9s %9s %s", mesiNome[m - 1].take(3),
                        if (c1 != null) Utenze.fmt(c1) else "—",
                        if (c2 != null) Utenze.fmt(c2) else "—",
                        Utenze.variazione(c2, c1)
                    )
                )
            }
            righe.add(
                String.format(
                    Locale.ITALY, "%-4s %9s %9s %s", "Tot", Utenze.fmt(t1), Utenze.fmt(t2),
                    Utenze.variazione(t2, t1)
                )
            )
            tv.text = righe.joinToString("\n") + "\n\nUnità: $unita"
            box.addView(tv)
        }
        val voci = presenti.map { it.toString() }
        campoMenu(corpo, "Anno 1", voci, a1.toString()) { a1 = presenti[it]; disegna() }
        campoMenu(corpo, "Anno 2", voci, a2.toString()) { a2 = presenti[it]; disegna() }
        corpo.addView(box)
        disegna()
        OcBuilder(this)
            .setTitle((Utenze.ICONE[u] ?: "") + " " + u + " · confronto")
            .setView(view)
            .setNegativeButton("Chiudi", null)
            .show()
    }

    private fun dialogNuovaLettura() {
        var u = Utenze.TIPI.first()
        val corrente = LocalDate.now().year
        val anniVoci = (corrente downTo corrente - 10).map { it.toString() }
        var anno = corrente
        var mese = LocalDate.now().monthValue
        val (view, corpo) = corpoDialogo()
        campoMenu(corpo, "Utenza", Utenze.TIPI.map { (Utenze.ICONE[it] ?: "") + " " + it }, (Utenze.ICONE[u] ?: "") + " " + u) {
            u = Utenze.TIPI[it]
        }
        campoMenu(corpo, "Anno", anniVoci, anno.toString()) { anno = anniVoci[it].toInt() }
        campoMenu(corpo, "Mese", mesiNome, mesiNome[mese - 1]) { mese = it + 1 }
        OcBuilder(this)
            .setTitle("Nuova lettura")
            .setView(view)
            .setPositiveButton("Avanti") { _, _ -> dialogLettura(u, anno, mese) }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun dialogLettura(u: String, anno: Int, mese: Int) {
        val unita = Utenze.UNITA[u] ?: ""
        val esistente = Utenze.anno12(u, anno.toString())[mese - 1]
        val haDati = esistente.att > 0 || esistente.consumo > 0 || esistente.prec > 0
        val precIniz = if (haDati) esistente.prec else Utenze.precSuggerita(u, anno, mese)
        fun f(v: Double) = if (v == 0.0) "" else String.format(Locale.ITALY, "%.2f", v)

        val (view, corpo) = corpoDialogo()
        val etPrec = campoTesto(corpo, "Lettura precedente ($unita)", f(precIniz), TIPO_DECIMALE, 14)
        val etAtt = campoTesto(corpo, "Lettura attuale ($unita)", f(esistente.att), TIPO_DECIMALE, 14)
        val etCons = campoTesto(corpo, "Consumo ($unita)", f(esistente.consumo), TIPO_DECIMALE, 14)
        val chk = MaterialCheckBox(this)
        chk.text = "Inserisco solo il consumo"
        corpo.addView(chk)
        fun contenitore(et: View): View {
            var p = et.parent
            while (p != null && p !is TextInputLayout) p = p.parent
            return (p as? View) ?: et
        }
        fun aggiorna() {
            contenitore(etAtt).visibility = if (chk.isChecked) View.GONE else View.VISIBLE
            contenitore(etCons).visibility = if (chk.isChecked) View.VISIBLE else View.GONE
        }
        chk.setOnCheckedChangeListener { _, _ -> aggiorna() }
        aggiorna()

        val dlg = OcBuilder(this)
            .setTitle(u + " · " + mesiNome[mese - 1] + " " + anno)
            .setView(view)
            .setPositiveButton("Salva", null)
            .setNegativeButton("Annulla", null)
            .create()
        dlg.show()
        dlg.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val prec = importoOk(etPrec.text.toString(), ammettiZero = true)
            if (prec == null) {
                etPrec.error = "Valore non valido"
                return@setOnClickListener
            }
            val att: Double
            if (chk.isChecked) {
                val c = importoOk(etCons.text.toString(), ammettiZero = true)
                if (c == null) {
                    etCons.error = "Valore non valido"
                    return@setOnClickListener
                }
                att = Math.round((prec + c) * 100) / 100.0
            } else {
                val a = importoOk(etAtt.text.toString(), ammettiZero = true)
                if (a == null) {
                    etAtt.error = "Valore non valido"
                    return@setOnClickListener
                }
                att = a
            }
            fun salva() {
                try {
                    Utenze.salvaLettura(u, anno, mese, prec, att)
                } catch (e: Exception) {
                    aviso("Salvataggio non riuscito: " + e.message)
                    return
                }
                dlg.dismiss()
                refresh()
                aviso("Lettura salvata")
            }
            if (att < prec) {
                OcBuilder(this)
                    .setTitle("Lettura minore della precedente")
                    .setMessage("La lettura attuale è minore della precedente. Vuoi forzare l'inserimento?")
                    .setNegativeButton("No", null)
                    .setPositiveButton("Sì, forza") { _, _ -> salva() }
                    .show()
            } else salva()
        }
    }

    private fun bottoniDoc(b: com.google.android.material.dialog.MaterialAlertDialogBuilder, d: Doc.Item, modifica: () -> Unit) {
        val f = d.file
        if (f != null) {
            b.setPositiveButton("Apri") { _, _ ->
                if (!Doc.apri(this, f)) aviso("Nessuna app installata per aprire questo file")
            }
            b.setNeutralButton("Condividi") { _, _ -> Doc.condividi(this, f) }
            b.setNegativeButton("Modifica") { _, _ -> modifica() }
        } else {
            b.setPositiveButton("Modifica") { _, _ -> modifica() }
            b.setNegativeButton("Chiudi", null)
        }
    }

    private fun dialogDocumentoContabile(d: Doc.Item) {
        val sp = Doc.spesaCollegata(d)
        val extra = ArrayList<String>()
        if (sp != null) {
            if (sp.ora.isNotEmpty()) extra.add("Orario: " + sp.ora)
            if (sp.metodo.isNotEmpty()) extra.add("Pagamento: " + sp.metodo)
            if (sp.conto.isNotEmpty()) extra.add("Conto: " + sp.conto)
            if (sp.ricorrente) extra.add("Ricorrente: sì")
            if (sp.hashtag.isNotEmpty()) extra.add("Tag: " + sp.hashtag.joinToString(" ") { "#" + it.trimStart('#') })
            extra.add("Spesa: collegata")
        } else extra.add("Spesa: nessuna spesa collegata")
        val b = OcBuilder(this)
            .setTitle(d.titolo)
            .setMessage(d.dettaglio() + "\n" + extra.joinToString("\n"))
        bottoniDoc(b, d) { dialogDocContabile(d) }
        b.show()
    }

    private fun dialogDocumento(d: Doc.Item) {
        val b = OcBuilder(this)
            .setTitle(d.titolo)
            .setMessage(d.dettaglio())
        bottoniDoc(b, d) { dialogDocPers(d) }
        b.show()
    }

    private fun apriLink(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {
            aviso("Nessuna app per aprire il link")
        }
    }

    private fun versioneApp(): Pair<String, Long> = try {
        val pi = packageManager.getPackageInfo(packageName, 0)
        (pi.versionName ?: "?") to androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(pi)
    } catch (_: Exception) {
        "?" to 0L
    }

    private fun scriviSupporto() {
        val (v, c) = versioneApp()
        val corpo = "\n\n---\nOrbitaCasa Android $v ($c)\nAndroid ${Build.VERSION.RELEASE} · ${Build.MANUFACTURER} ${Build.MODEL}"
        val i = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
            .putExtra(Intent.EXTRA_EMAIL, arrayOf(MAIL_SUPPORTO))
            .putExtra(Intent.EXTRA_SUBJECT, "OrbitaCasa Android $v - supporto")
            .putExtra(Intent.EXTRA_TEXT, corpo)
        try {
            startActivity(i)
        } catch (_: Exception) {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            cm.setPrimaryClip(android.content.ClipData.newPlainText("mail", MAIL_SUPPORTO))
            aviso("Nessuna app e-mail: indirizzo copiato")
        }
    }

    private fun renderInfo() {
        intestaLista("Info e contatti", "")
        val cont = vista<LinearLayout>(R.id.listaGen)
        val (v, c) = versioneApp()
        val raw = "https://raw.githubusercontent.com/Renato-4132/OrbitaCasa/main/resources"

        sezione(cont, "QUESTA APP")
        riga(cont, "OrbitaCasa Android", "Versione $v (build $c)", "", null) {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            cm.setPrimaryClip(android.content.ClipData.newPlainText("versione", "OrbitaCasa Android $v ($c)"))
            aviso("Versione copiata")
        }

        sezione(cont, "CONTATTI")
        riga(cont, "✉️ Scrivi al supporto", MAIL_SUPPORTO, "›", null) { scriviSupporto() }

        sezione(cont, "PAGINE ORBITACASA")
        riga(cont, "📱 OrbitaCasa Android", "Sito ufficiale", "›", null) { apriLink("https://renato-4132.github.io/OrbitaCasa-Android/") }
        riga(cont, "🖥️ OrbitaCasa Desktop", "Sito ufficiale", "›", null) { apriLink("https://renato-4132.github.io/OrbitaCasa/") }

        sezione(cont, "MANUALI")
        riga(cont, "📘 Manuale OrbitaCasa", "PDF", "›", null) { apriLink("$raw/OrbitaCasa.pdf") }
        riga(cont, "🏠 Manutenzione e risparmio casa", "PDF", "›", null) { apriLink("$raw/Manuale_Manutenzione_Risparmio_Casa.pdf") }
        riga(cont, "🔢 Tabella contatori", "PDF", "›", null) { apriLink("$raw/Tabella%20Contatori.pdf") }

    }

    private fun indietroLista() {
        if (schedaDi(pagina) != null && schedaAperta != null) {
            schedaAperta = null
            renderLista()
        } else {
            mostra(Pagina.HOME)
        }
    }

    private fun emojiScad(stato: String) = when (stato) {
        "rosso" -> "🔴"; "ambra" -> "🟠"; "verde" -> "🟢"; else -> "⚪"
    }

    private fun esitoScheda(err: String?, ok: String) {
        if (err != null) aviso(err) else {
            refresh()
            aviso(ok)
        }
    }

    private fun renderScheda() {
        val s = sc
        val elenco = s.elenco()
        val aperto = schedaAperta?.let { id -> elenco.firstOrNull { it.optString("id") == id } }
        if (aperto != null) { renderDettaglio(aperto); return }
        schedaAperta = null
        intestaLista(s.cfg.emoji + " " + s.cfg.titolo, "➕  Nuovo " + s.cfg.sing)
        val cont = vista<LinearLayout>(R.id.listaGen)
        s.errore?.let { vuoto(cont, "⚠️ $it"); return }
        if (elenco.isEmpty()) {
            vuoto(cont, "Nessun ${s.cfg.sing}. Aggiungine uno, oppure importa i dati dal desktop (${s.cfg.file}).")
            return
        }
        for (v in elenco) {
            val peggiore = s.SCADENZE.map { s.scadenza(v.optString(it.first)) to it.second }
                .filter { it.first.giorni != null }.minByOrNull { it.first.giorni!! }
            val sub = (s.sottotitolo(v) + listOfNotNull(
                peggiore?.let { emojiScad(it.first.stato) + " " + it.second + " " + it.first.testo },
            )).joinToString(" · ")
            val saldo = s.movimenti(v).sumOf { if (it.entrata) -it.imp else it.imp }
            riga(cont, v.optString("nome"), sub, euro(saldo), null) {
                schedaAperta = v.optString("id")
                renderLista()
            }
        }
    }

    private fun renderDettaglio(v: JSONObject) {
        val s = sc
        intestaLista(s.cfg.emoji + " " + v.optString("nome"), "➕  Nuova " + if (s.cfg.tipoMov) "voce" else "spesa", "⚙️  Altro")
        val cont = vista<LinearLayout>(R.id.listaGen)
        val rosso = ContextCompat.getColor(this, R.color.oc_red)
        val verde = ContextCompat.getColor(this, R.color.oc_green)

        val sub = s.sottotitolo(v).joinToString(" · ")
        riga(cont, "Scheda " + s.cfg.sing, sub.ifEmpty { "Tocca per compilare i dati" }, "✏️", null) { dialogAnaScheda(v) }

        sezione(cont, "SCADENZE")
        for ((k, et) in s.SCADENZE) {
            val sd = s.scadenza(v.optString(k))
            riga(cont, emojiScad(sd.stato) + " " + et, sd.testo, "", null, null)
        }
        val info = s.info(v)
        if (info.isNotEmpty()) {
            sezione(cont, "STATISTICHE")
            info.forEach { riga(cont, it, "", "", null, null) }
        }
        val note = v.optString("note")
        if (note.isNotBlank()) {
            sezione(cont, "NOTE")
            vuoto(cont, note)
        }

        val movs = s.movimenti(v)
        sezione(cont, (if (s.cfg.tipoMov) "VOCI (" else "SPESE (") + movs.size + ")")
        if (movs.isEmpty()) {
            vuoto(cont, "Nessuna voce registrata.")
            return
        }
        val idScheda = v.optString("id")
        if (schedaFiltroId != idScheda) {
            schedaFiltroId = idScheda
            schedaPeriodo = 0
            schedaCategoria = "Tutte"
            schedaLimite = 30
        }
        val periodi = listOf("Ultimi 30 giorni", "Questo mese", "Ultimi 3 mesi", "Ultimi 12 mesi", "Quest'anno", "Anno scorso", "Tutto")
        val oggi = LocalDate.now()
        fun inPeriodo(d: LocalDate?): Boolean = when (schedaPeriodo) {
            0 -> d != null && !d.isBefore(oggi.minusDays(30))
            1 -> d != null && d.year == oggi.year && d.monthValue == oggi.monthValue
            2 -> d != null && !d.isBefore(oggi.minusMonths(3))
            3 -> d != null && !d.isBefore(oggi.minusMonths(12))
            4 -> d != null && d.year == oggi.year
            5 -> d != null && d.year == oggi.year - 1
            else -> true
        }
        val categorie = listOf("Tutte") + movs.map { it.cat }.filter { it.isNotBlank() }.distinct().sorted()
        if (schedaCategoria !in categorie) schedaCategoria = "Tutte"
        val barra = LinearLayout(this)
        barra.orientation = LinearLayout.HORIZONTAL
        barra.addView(
            pulsanteSel("📅 " + periodi[schedaPeriodo] + " ▾", false) {
                OcBuilder(this)
                    .setTitle("Periodo")
                    .setItems(periodi.toTypedArray()) { _, k ->
                        schedaPeriodo = k
                        schedaLimite = 30
                        renderLista()
                    }
                    .setNegativeButton("Annulla", null)
                    .show()
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        barra.addView(
            pulsanteSel("🏷️ " + schedaCategoria + " ▾", false) {
                OcBuilder(this)
                    .setTitle("Categoria")
                    .setItems(categorie.toTypedArray()) { _, k ->
                        schedaCategoria = categorie[k]
                        schedaLimite = 30
                        renderLista()
                    }
                    .setNegativeButton("Annulla", null)
                    .show()
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        cont.addView(barra)
        val visibili = movs.filter { inPeriodo(it.data) && (schedaCategoria == "Tutte" || it.cat == schedaCategoria) }
        val daEsp = visibili.count { !it.esportato }
        val saldo = visibili.sumOf { if (it.entrata) -it.imp else it.imp }
        riga(
            cont,
            (if (s.cfg.tipoMov) "Saldo (uscite − entrate)" else "Totale spese") + " · " + periodi[schedaPeriodo].lowercase() +
                (if (schedaCategoria != "Tutte") " · $schedaCategoria" else ""),
            visibili.size.toString() + " di " + movs.size + (if (daEsp > 0) " · $daEsp da esportare nei movimenti" else if (visibili.isNotEmpty()) " · tutte esportate" else ""),
            euro(saldo), rosso, null
        )
        if (visibili.isEmpty()) {
            vuoto(cont, "Nessuna voce con questi filtri.")
            return
        }
        for (m in visibili.take(schedaLimite)) {
            val sotto = (listOfNotNull(
                m.data?.let { s.dataIt(it) },
                m.desc.takeIf { it.isNotBlank() },
            ) + s.cfg.extra.mapNotNull { e ->
                m.extra[e.k]?.takeIf { it > 0 }?.let { s.fmtN(it) + " " + e.unita }
            }).joinToString(" · ")
            riga(cont, (if (m.esportato) "✅ " else "") + m.cat, sotto, euro(m.imp), if (m.entrata) verde else rosso) { dialogMovScheda(v, m) }
        }
        if (visibili.size > schedaLimite) {
            cont.addView(
                pulsanteSel("Mostra altre 30 (restano " + (visibili.size - schedaLimite) + ")", false) {
                    schedaLimite += 30
                    renderLista()
                },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            )
        }
    }

    private fun dialogNuovaScheda() {
        val s = sc
        val (view, corpo) = corpoDialogo()
        val et = campoTesto(corpo, s.cfg.nomeHint, "", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS, 22)
        val dlg = OcBuilder(this)
            .setTitle("Nuovo " + s.cfg.sing)
            .setView(view)
            .setPositiveButton("Crea", null)
            .setNegativeButton("Annulla", null)
            .create()
        dlg.show()
        dlg.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val err = s.nuovo(et.text.toString())
            if (err != null) {
                et.error = err
                return@setOnClickListener
            }
            dlg.dismiss()
            refresh()
            aviso(s.cfg.sing.replaceFirstChar { it.uppercase() } + " creato")
        }
    }

    private fun dialogAnaScheda(v: JSONObject) {
        val s = sc
        val (view, corpo) = corpoDialogo()
        val conti = Repo.conti().map { it.nome }
        val campi = LinkedHashMap<String, () -> String>()
        for (c in s.CAMPI) {
            when (c.t) {
                "conto" -> {
                    val att = v.optString(c.k).ifBlank { "(nessuno)" }
                    val til = campoMenu(corpo, c.l, listOf("(nessuno)") + conti, att)
                    val ac = til.findViewById<MaterialAutoCompleteTextView>(R.id.campoMenu)
                    campi[c.k] = { ac.text.toString() }
                }
                "num" -> {
                    val x = s.num(v, c.k)
                    val et = campoTesto(corpo, c.l, if (x > 0) s.fmtN(x) else "", TIPO_DECIMALE, 10)
                    campi[c.k] = { et.text.toString() }
                }
                "date" -> {
                    val et = campoTesto(corpo, c.l, v.optString(c.k), InputType.TYPE_CLASS_TEXT, 10)
                    campi[c.k] = { et.text.toString() }
                }
                else -> {
                    val et = campoTesto(corpo, c.l, v.optString(c.k), InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES, if (c.k == "nome") 22 else 0)
                    campi[c.k] = { et.text.toString() }
                }
            }
        }
        val dlg = OcBuilder(this)
            .setTitle("Scheda " + s.cfg.sing)
            .setView(view)
            .setPositiveButton("Salva", null)
            .setNegativeButton("Annulla", null)
            .create()
        dlg.show()
        dlg.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val err = s.salvaAna(v.optString("id"), campi.mapValues { it.value() }, conti)
            if (err != null) {
                aviso(err)
                return@setOnClickListener
            }
            dlg.dismiss()
            refresh()
            aviso("Scheda salvata")
        }
    }

    private fun dialogMovScheda(v: JSONObject, m: SchedaMov?) {
        val s = sc
        val (view, corpo) = corpoDialogo()
        val cats = ArrayList<String>()
        v.optJSONArray("categorie")?.let { a -> for (i in 0 until a.length()) cats.add(a.optString(i)) }
        if (cats.isEmpty()) cats.addAll(s.CATEGORIE)
        if (m != null && m.cat.isNotBlank() && m.cat !in cats) cats.add(m.cat)
        var acTipo: MaterialAutoCompleteTextView? = null
        if (s.cfg.tipoMov) {
            val tilTipo = campoMenu(corpo, "Tipo", listOf("Uscita", "Entrata"), m?.tipo ?: "Uscita")
            acTipo = tilTipo.findViewById(R.id.campoMenu)
        }
        val tilCat = campoMenu(corpo, "Categoria", cats, m?.cat ?: cats.first())
        val acCat = tilCat.findViewById<MaterialAutoCompleteTextView>(R.id.campoMenu)
        val oggi = if (m?.data != null) m.data else LocalDate.now()
        val etData = campoTesto(corpo, "Data (gg-mm-aaaa)", s.dataIt(oggi), InputType.TYPE_CLASS_TEXT, 10)
        val etImp = campoTesto(corpo, "Importo (€)", m?.let { String.format(Locale.ITALY, "%.2f", it.imp) } ?: "", TIPO_DECIMALE, 12)
        Calc.collega(this, etImp)
        val etDesc = campoTesto(corpo, "Descrizione", m?.desc ?: "", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)
        val etExtra = s.cfg.extra.map { e ->
            e to campoTesto(corpo, e.l, m?.extra?.get(e.k)?.takeIf { it > 0 }?.let { s.fmtN(it) } ?: "", TIPO_DECIMALE, 9)
        }
        val b = OcBuilder(this)
            .setTitle(if (m == null) "Nuova voce" else "Modifica voce")
            .setView(view)
            .setPositiveButton("Salva", null)
            .setNegativeButton("Annulla", null)
        if (m != null) b.setNeutralButton("Elimina", null)
        val dlg = b.create()
        dlg.show()
        dlg.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val data = s.parse(etData.text.toString())
            if (data == null) {
                etData.error = "Data non valida"
                return@setOnClickListener
            }
            val imp = importoOk(etImp.text.toString())
            if (imp == null) {
                etImp.error = "Importo non valido"
                return@setOnClickListener
            }
            val extra = HashMap<String, Double?>()
            for ((e, et) in etExtra) {
                val x = num(et.text.toString())
                if (x == null || x < 0) { et.error = "Valore non valido"; return@setOnClickListener }
                extra[e.k] = x
            }
            val err = s.movSalva(
                v.optString("id"), m?.id, data, acCat.text.toString().trim(),
                etDesc.text.toString().trim(), imp, acTipo?.text?.toString() ?: "Uscita", extra
            )
            if (err != null) {
                aviso(err)
                return@setOnClickListener
            }
            dlg.dismiss()
            refresh()
            aviso("Voce salvata")
        }
        if (m != null) {
            dlg.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                OcBuilder(this)
                    .setTitle("Eliminare la voce?")
                    .setMessage(m.cat + " · " + euro(m.imp))
                    .setNegativeButton("Annulla", null)
                    .setPositiveButton("Elimina") { _, _ ->
                        val err = s.movElimina(v.optString("id"), setOf(m.id))
                        dlg.dismiss()
                        esitoScheda(err, "Voce eliminata")
                    }
                    .show()
            }
        }
    }

    private fun menuScheda(v: JSONObject) {
        val s = sc
        val voci = arrayOf(
            "📤  Esporta voci nei movimenti",
            "↩️  Rimuovi il segno di esportazione",
            "🗑️  Elimina più voci",
            "🗑️  Elimina " + s.cfg.art + s.cfg.sing,
        )
        OcBuilder(this)
            .setTitle(v.optString("nome"))
            .setItems(voci) { _, i ->
                when (i) {
                    0 -> scegliMovScheda(v, "Esporta", false) { ids ->
                        OcBuilder(this)
                            .setTitle("Come esportare?")
                            .setItems(arrayOf("Un solo saldo", "Una riga per voce")) { _, k ->
                                val (msg, err) = s.esporta(v.optString("id"), ids, k == 1)
                                esitoScheda(err, msg ?: "Esportato")
                            }
                            .setNegativeButton("Annulla", null)
                            .show()
                    }
                    1 -> scegliMovScheda(v, "Rimuovi segno", true) { ids ->
                        esitoScheda(s.smarca(v.optString("id"), ids), "Segno di esportazione rimosso")
                    }
                    2 -> scegliMovScheda(v, "Elimina", null) { ids ->
                        OcBuilder(this)
                            .setTitle("Eliminare " + ids.size + " voci?")
                            .setNegativeButton("Annulla", null)
                            .setPositiveButton("Elimina") { _, _ ->
                                esitoScheda(s.movElimina(v.optString("id"), ids), "Voci eliminate")
                            }
                            .show()
                    }
                    3 -> OcBuilder(this)
                        .setTitle("Eliminare " + s.cfg.art + s.cfg.sing + "?")
                        .setMessage("Verranno eliminate anche tutte le sue voci. I movimenti già esportati restano.")
                        .setNegativeButton("Annulla", null)
                        .setPositiveButton("Elimina") { _, _ ->
                            val err = s.elimina(v.optString("id"))
                            if (err == null) schedaAperta = null
                            esitoScheda(err, s.cfg.sing.replaceFirstChar { it.uppercase() } + " eliminato")
                        }
                        .show()
                    else -> {}
                }
            }
            .setNegativeButton("Chiudi", null)
            .show()
    }

    private fun scegliMovScheda(v: JSONObject, azione: String, soloEsportate: Boolean?, ok: (Set<String>) -> Unit) {
        val s = sc
        val movs = s.movimenti(v).filter { soloEsportate == null || it.esportato == soloEsportate }
        if (movs.isEmpty()) {
            aviso("Nessuna voce disponibile per questa azione")
            return
        }
        val etichette = movs.map {
            (it.data?.let { d -> s.dataIt(d) } ?: "?") + " · " + it.cat + " · " + euro(it.imp) + (if (it.entrata) " (entrata)" else "") + (if (it.esportato) " ✅" else "")
        }.toTypedArray()
        val sel = BooleanArray(movs.size) { false }
        OcBuilder(this)
            .setTitle(azione)
            .setMultiChoiceItems(etichette, sel) { _, i, c -> sel[i] = c }
            .setPositiveButton(azione) { _, _ ->
                val ids = movs.filterIndexed { i, _ -> sel[i] }.map { it.id }.toSet()
                if (ids.isEmpty()) aviso("Nessuna voce selezionata") else ok(ids)
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun renderCalendario() {
        intestaLista("🗓️ Calendario", "➕  Nuova operazione")
        val cont = vista<LinearLayout>(R.id.listaGen)
        val dp = resources.displayMetrics.density
        val verde = ContextCompat.getColor(this, R.color.oc_green)
        val rosso = ContextCompat.getColor(this, R.color.oc_red)
        val grigio = ContextCompat.getColor(this, R.color.oc_text_mid)
        val oggi = LocalDate.now()

        val nav = LinearLayout(this)
        nav.orientation = LinearLayout.HORIZONTAL
        nav.gravity = android.view.Gravity.CENTER_VERTICAL
        val prev = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle)
        prev.text = "◀"
        prev.minWidth = 0
        prev.setOnClickListener {
            meseCal = meseCal.minusMonths(1)
            giornoCal = null
            renderCalendario()
        }
        val next = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle)
        next.text = "▶"
        next.minWidth = 0
        next.setOnClickListener {
            meseCal = meseCal.plusMonths(1)
            giornoCal = null
            renderCalendario()
        }
        frecciaPiccola(prev)
        frecciaPiccola(next)
        val titolo = TextView(this)
        titolo.text = maiuscola(meseCal.format(FMT_MESE))
        titolo.gravity = android.view.Gravity.CENTER
        titolo.setTypeface(null, Typeface.BOLD)
        titolo.textSize = 16f
        val vociMeseCal = Repo.voci().filter { YearMonth.from(it.data) == meseCal }
        val saldoMeseCal = vociMeseCal.filter { it.tipo == "Entrata" }.sumOf { it.importo } -
            vociMeseCal.filter { it.tipo == "Uscita" }.sumOf { it.importo }
        val centro = LinearLayout(this)
        centro.orientation = LinearLayout.HORIZONTAL
        centro.gravity = android.view.Gravity.CENTER
        centro.addView(titolo)
        centro.addView(nuovoMeteo(saldoMeseCal >= 0, (24 * dp).toInt()))
        centro.setOnClickListener {
            meseCal = YearMonth.now()
            giornoCal = oggi
            renderCalendario()
        }
        nav.addView(prev)
        nav.addView(centro, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        nav.addView(next)
        cont.addView(nav)

        val delMese = Repo.voci().filter { YearMonth.from(it.data) == meseCal }
        val perGiorno = delMese.groupBy { it.data }

        val head = LinearLayout(this)
        head.orientation = LinearLayout.HORIZONTAL
        head.setPadding(0, (8 * dp).toInt(), 0, (2 * dp).toInt())
        for (g in listOf("L", "M", "M", "G", "V", "S", "D")) {
            val t = TextView(this)
            t.text = g
            t.gravity = android.view.Gravity.CENTER
            t.textSize = 12f
            t.setTextColor(grigio)
            head.addView(t, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        cont.addView(head)

        fun corto(n: Double): String {
            val a = abs(n)
            val sg = if (n < 0) "−" else "+"
            return if (a >= 1000) sg + String.format(Locale.ITALY, "%.1f", a / 1000) + "k"
            else sg + String.format(Locale.ITALY, "%.0f", a)
        }
        val offset = meseCal.atDay(1).dayOfWeek.value - 1
        val giorniMese = meseCal.lengthOfMonth()
        val righe = (offset + giorniMese + 6) / 7
        for (r in 0 until righe) {
            val rigaV = LinearLayout(this)
            rigaV.orientation = LinearLayout.HORIZONTAL
            for (c in 0 until 7) {
                val idx = r * 7 + c - offset
                val cella = LinearLayout(this)
                cella.orientation = LinearLayout.VERTICAL
                cella.gravity = android.view.Gravity.CENTER_HORIZONTAL
                val lp = LinearLayout.LayoutParams(0, (54 * dp).toInt(), 1f)
                lp.setMargins(2, 2, 2, 2)
                cella.layoutParams = lp
                if (idx in 0 until giorniMese) {
                    val giorno = meseCal.atDay(idx + 1)
                    val voci = perGiorno[giorno].orEmpty()
                    val e = voci.filter { it.tipo == "Entrata" }.sumOf { it.importo }
                    val u = voci.filter { it.tipo == "Uscita" }.sumOf { it.importo }
                    val evid = giorno == giornoCal || giorno == oggi
                    if (evid) cella.setBackgroundResource(R.drawable.bg_tile)
                    val n = TextView(this)
                    n.text = (idx + 1).toString()
                    n.textSize = 14f
                    n.gravity = android.view.Gravity.CENTER_HORIZONTAL
                    n.setTypeface(null, if (evid) Typeface.BOLD else Typeface.NORMAL)
                    n.setPadding(0, (4 * dp).toInt(), 0, 0)
                    cella.addView(n)
                    if (voci.isNotEmpty()) {
                        val dots = TextView(this)
                        dots.text = (if (e > 0) "●" else "") + (if (u > 0) "●" else "")
                        dots.textSize = 8f
                        dots.gravity = android.view.Gravity.CENTER_HORIZONTAL
                        val sp = android.text.SpannableString(dots.text)
                        var pos = 0
                        if (e > 0) {
                            sp.setSpan(android.text.style.ForegroundColorSpan(verde), pos, pos + 1, 0); pos++
                        }
                        if (u > 0) sp.setSpan(android.text.style.ForegroundColorSpan(rosso), pos, pos + 1, 0)
                        dots.text = sp
                        cella.addView(dots)
                        val net = e - u
                        val tn = TextView(this)
                        tn.text = corto(net)
                        tn.textSize = 9f
                        tn.gravity = android.view.Gravity.CENTER_HORIZONTAL
                        tn.maxLines = 1
                        tn.setTypeface(null, Typeface.BOLD)
                        tn.setTextColor(if (net >= 0) verde else rosso)
                        cella.addView(tn)
                    }
                    cella.setOnClickListener {
                        giornoCal = giorno
                        renderCalendario()
                    }
                }
                rigaV.addView(cella)
            }
            cont.addView(rigaV)
        }

        val entrate = delMese.filter { it.tipo == "Entrata" }.sumOf { it.importo }
        val piani = Repo.promemoriaMese(meseCal)
        val idPiani = Repo.piani().map { it.idSpesa }.filter { it.isNotBlank() }.toSet()
        var uscite = delMese.filter { it.tipo == "Uscita" }.sumOf { it.importo }
        if (pianCal && Repo.piani().isNotEmpty()) {
            uscite = uscite - delMese.filter { it.tipo == "Uscita" && it.id in idPiani }.sumOf { it.importo } +
                piani.sumOf { it.second }
        }
        val saldo = entrate - uscite
        val tot = TextView(this)
        tot.text = testoTotali(entrate, uscite, saldo)
        tot.gravity = android.view.Gravity.CENTER
        tot.textSize = 13f
        tot.setPadding(0, (10 * dp).toInt(), 0, 0)
        cont.addView(tot)

        val sel = giornoCal
        if (sel != null && YearMonth.from(sel) == meseCal) {
            sezione(cont, maiuscola(sel.format(FMT_GIORNO)).uppercase())
            val voci = perGiorno[sel].orEmpty().sortedBy { it.ora.ifBlank { "99:99" } }
            if (voci.isEmpty()) vuoto(cont, "Nessuna operazione in questo giorno.")
            for (v in voci) {
                val desc = descPulita(v.descrizione)
                val sotto = listOfNotNull(
                    v.ora.takeIf { it.isNotBlank() },
                    v.metodo.takeIf { it.isNotBlank() }?.let { Repo.metodoConEmoji(it) },
                    v.conto.takeIf { it.isNotBlank() }?.let { "🏦 $it" },
                    v.hashtag.takeIf { it.isNotEmpty() }?.joinToString(" ") { "#" + it.trimStart('#') },
                    if (v.ricorrente) "🔁 ricorrenza" else null,
                    if (v.data.isAfter(oggi)) "futura" else null,
                ).joinToString(" · ")
                val entrata = v.tipo == "Entrata"
                riga(
                    cont, if (desc.isEmpty()) v.categoria else v.categoria + " · " + desc, sotto,
                    (if (entrata) "+" else "−") + euro(v.importo), if (entrata) verde else rosso
                ) { dialogVoce(v) }
            }
        }

        if (Repo.piani().isNotEmpty()) {
            sezione(cont, "📌 SPESE PIANIFICATE DEL MESE · " + (if (pianCal) "INCLUSE" else "ESCLUSE"))
            if (piani.isEmpty()) vuoto(cont, "Nessuna quota da accantonare in questo mese.")
            for ((piano, quota) in piani) {
                val nome = piano.categoria.ifBlank { piano.nome.ifBlank { "Pianificata" } }
                riga(
                    cont, nome, piano.descrizione, "−" + euro(quota),
                    rosso, null
                )
            }
            if (piani.size > 1) {
                riga(cont, "Totale quote", "", "−" + euro(piani.sumOf { it.second }), rosso, null)
            }
            val btn = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle)
            btn.text = "📌 " + if (pianCal) "Escludi dai totali" else "Includi nei totali"
            btn.setOnClickListener {
                pianCal = !pianCal
                renderCalendario()
            }
            cont.addView(btn)
        }
    }

    private fun renderFinanziamenti() {
        intestaLista("💶 Finanziamenti", "➕  Nuovo finanziamento", "🧮  Calcolatori")
        val cont = vista<LinearLayout>(R.id.listaGen)
        val elenco = Fin.elenco()
        if (elenco.isEmpty()) {
            vuoto(cont, "Nessun finanziamento. Aggiungine uno, oppure importa i dati dal desktop (finanziamenti.json).")
            return
        }
        val stati = elenco.map { it to Fin.stato(it) }
        val attivi = stati.mapNotNull { (_, r) -> r.first?.takeIf { it.stato != "estinto" } }

        val entratePerMese = Repo.voci().filter { it.tipo == "Entrata" }
            .groupBy { YearMonth.from(it.data) }.mapValues { e -> e.value.sumOf { it.importo } }
        val entrateMedie = if (entratePerMese.isEmpty()) 0.0 else entratePerMese.values.average()
        val rataMese = attivi.sumOf { it.rataMensile }
        val residuo = attivi.sumOf { it.residuo }
        val sotto = listOfNotNull(
            attivi.size.toString() + " attivi",
            "rate " + euro(rataMese) + "/mese",
            if (entrateMedie > 0) String.format(Locale.ITALY, "%.0f", rataMese / entrateMedie * 100) + "% delle entrate" else null,
            "interessi residui " + euro(attivi.sumOf { it.intRestanti }),
        ).joinToString(" · ")
        riga(cont, "Debito residuo totale", sotto, euro(residuo), ContextCompat.getColor(this, R.color.oc_red), null)

        sezione(cont, "FINANZIAMENTI")
        for ((f, r) in stati) {
            val st = r.first
            val nome = f.optString("nome") + (f.optString("conto").takeIf { it.isNotBlank() }?.let { "  🏦 $it" } ?: "")
            if (st == null) {
                riga(cont, nome, "⚠️ " + (r.second ?: "Dati non validi"), "", null) { dettaglioFin(f) }
                continue
            }
            val desc = when (st.stato) {
                "estinto" -> "✅ Estinto"
                "non_iniziato" -> "Non ancora iniziato"
                else -> st.ratePagate.toString() + "/" + st.rateTot + " rate"
            }
            val prox = st.prossimaData?.let { "prossima " + Fin.dataIt(it) + " · " + euro(st.prossimaImporto) }
            riga(
                cont, nome,
                listOfNotNull(desc, if (st.stato != "estinto") "rata " + euro(st.rataMensile) else null, prox).joinToString(" · "),
                euro(st.residuo), if (st.stato == "estinto") ContextCompat.getColor(this, R.color.oc_green) else null
            ) { dettaglioFin(f) }
        }
    }

    private fun dettaglioFin(f: JSONObject) {
        val id = f.optString("id")
        val (st, err) = Fin.stato(f)
        val sb = StringBuilder()
        sb.append(euro(f.optDouble("capitale"))).append(" al ")
            .append(String.format(Locale.ITALY, "%.2f", f.optDouble("tasso"))).append(" % · ")
            .append(f.optInt("mesi")).append(" mesi · dal ")
            .append(runCatching { Fin.dataIt(LocalDate.parse(f.optString("data_inizio"))) }.getOrDefault("?"))
        if (f.optInt("periodo", 1) == 3) sb.append(" · rate trimestrali")
        sb.append("\n\n")
        if (st == null) {
            sb.append("⚠️ ").append(err ?: "Dati non validi")
        } else {
            sb.append(
                when (st.stato) { "estinto" -> "✅ Estinto"; "non_iniziato" -> "Non ancora iniziato"; else -> "In corso" }
            ).append("\nRate pagate: ").append(st.ratePagate).append(" su ").append(st.rateTot)
                .append(" (").append(String.format(Locale.ITALY, "%.0f", st.pct)).append("% rimborsato)")
                .append("\nDebito residuo: ").append(euro(st.residuo))
                .append("\nRata ").append(if (st.periodo == 3) "trimestrale: " else "mensile: ")
                .append(euro(if (st.periodo == 3) st.rataMensile * 3 else st.rataMensile))
            st.prossimaData?.let {
                sb.append("\nProssima rata: ").append(Fin.dataIt(it)).append(" · ").append(euro(st.prossimaImporto))
            }
            sb.append("\nInteressi pagati: ").append(euro(st.intPagati))
                .append("\nInteressi restanti: ").append(euro(st.intRestanti))
                .append("\nFine prevista: ").append(st.fine?.let { Fin.dataIt(it) } ?: "-")
                .append("\nCosto totale: ").append(euro(st.costoTotale))
            st.risparmioInt?.let { r ->
                sb.append("\n\nCon le variazioni: ")
                    .append(if (r >= 0) "risparmi " + euro(r) + " di interessi" else "paghi " + euro(-r) + " di interessi in più")
                st.mesiDiff?.takeIf { it != 0 }?.let { d ->
                    sb.append(if (d > 0) ", finisci " + d + " mesi prima" else ", finisci " + (-d) + " mesi dopo")
                }
            }
        }
        OcBuilder(this)
            .setTitle(f.optString("nome"))
            .setMessage(sb.toString())
            .setPositiveButton("Modifica") { _, _ -> dialogFin(f) }
            .setNeutralButton("Variazioni") { _, _ -> dialogVariazioni(id) }
            .setNegativeButton("Piano rate") { _, _ -> pianoRate(f) }
            .show()
    }

    private fun pianoRate(f: JSONObject) {
        val (righe, err) = Fin.piano(f)
        val oggi = LocalDate.now()
        val sb = StringBuilder()
        if (err != null) sb.append("⚠️ ").append(err).append("\n\n")
        for (r in righe) {
            sb.append(if (!r.data.isAfter(oggi)) "✅ " else "⬜ ")
                .append(r.n).append(" · ").append(Fin.dataIt(r.data)).append("\n    rata ").append(euro(r.rata))
                .append(" (capitale ").append(euro(r.cap)).append(", interessi ").append(euro(r.inte)).append(")")
            if (r.extra > 0) sb.append(" ➕ extra ").append(euro(r.extra))
            sb.append("\n    residuo ").append(euro(r.residuo)).append("\n")
        }
        OcBuilder(this)
            .setTitle("Piano rate · " + f.optString("nome"))
            .setMessage(sb.toString().trimEnd())
            .setPositiveButton("Chiudi", null)
            .show()
    }

    private fun dialogFin(f: JSONObject?) {
        val (view, corpo) = corpoDialogo()
        fun testo(x: Double) = if (x > 0) String.format(Locale.ITALY, "%.2f", x) else ""
        val etNome = campoTesto(corpo, "Nome", f?.optString("nome") ?: "", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES, 60)
        val etCap = campoTesto(corpo, "Capitale (€)", testo(f?.optDouble("capitale") ?: 0.0), TIPO_DECIMALE, 12)
        val etTasso = campoTesto(corpo, "Tasso annuo (%)", if (f != null) String.format(Locale.ITALY, "%.2f", f.optDouble("tasso")) else "", TIPO_DECIMALE, 6)
        val etMesi = campoTesto(corpo, "Durata (mesi)", f?.optInt("mesi")?.toString() ?: "", InputType.TYPE_CLASS_NUMBER, 3)
        var data = f?.let { runCatching { LocalDate.parse(it.optString("data_inizio")) }.getOrNull() } ?: LocalDate.now()
        val btnData = MaterialButton(ctxDialogo, null, com.google.android.material.R.attr.materialButtonOutlinedStyle)
        btnData.text = "Prima rata: " + Fin.dataIt(data)
        btnData.setOnClickListener {
            DatePickerDialog(
                this,
                { _, y, m, d ->
                    data = LocalDate.of(y, m + 1, d)
                    btnData.text = "Prima rata: " + Fin.dataIt(data)
                },
                data.year, data.monthValue - 1, data.dayOfMonth
            ).show()
        }
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.topMargin = 16
        corpo.addView(btnData, lp)
        val etRata = campoTesto(corpo, "Rata fissa (€, vuoto = calcolata)", testo(f?.optDouble("rata") ?: 0.0), TIPO_DECIMALE, 10)
        val etSpese = campoTesto(corpo, "Spese per rata (€, facoltativo)", testo(f?.optDouble("spese") ?: 0.0), TIPO_DECIMALE, 8)
        val periodi = listOf("Mensile", "Trimestrale")
        var trim = f?.optInt("periodo", 1) == 3
        campoMenu(corpo, "Periodicità delle rate", periodi, if (trim) "Trimestrale" else "Mensile") { trim = it == 1 }
        val nomiConti = listOf("(nessuno)") + Repo.conti().map { it.nome }
        var conto = f?.optString("conto")?.takeIf { it.isNotBlank() } ?: "(nessuno)"
        if (Repo.conti().isNotEmpty()) {
            campoMenu(corpo, "Conto", nomiConti, conto) { conto = nomiConti[it] }
        }

        val b = OcBuilder(this)
            .setTitle(if (f == null) "Nuovo finanziamento" else "Modifica finanziamento")
            .setView(view)
            .setPositiveButton("Salva", null)
            .setNegativeButton("Annulla", null)
        if (f != null) b.setNeutralButton("Elimina", null)
        val dlg = b.create()
        dlg.show()
        dlg.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val cap = importoOk(etCap.text.toString())
            val tasso = importoOk(etTasso.text.toString(), ammettiZero = true)
            val mesi = etMesi.text.toString().trim().toIntOrNull()
            val rata = importoOk(etRata.text.toString(), ammettiZero = true)
            val spese = importoOk(etSpese.text.toString(), ammettiZero = true)
            when {
                cap == null -> etCap.error = "Importo non valido"
                tasso == null -> etTasso.error = "Tasso non valido"
                mesi == null -> etMesi.error = "Durata non valida"
                rata == null -> etRata.error = "Importo non valido"
                spese == null -> etSpese.error = "Importo non valido"
                else -> {
                    val err = Fin.salva(
                        f?.optString("id"), etNome.text.toString(), cap, tasso, mesi, data, rata, spese, trim,
                        if (conto == "(nessuno)") "" else conto
                    )
                    if (err != null) {
                        aviso(err)
                    } else {
                        dlg.dismiss()
                        refresh()
                        aviso("Finanziamento salvato")
                    }
                }
            }
        }
        if (f != null) {
            dlg.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                OcBuilder(this)
                    .setTitle("Eliminare il finanziamento?")
                    .setMessage(f.optString("nome") + "\nSi perdono anche le sue variazioni.")
                    .setNegativeButton("Annulla", null)
                    .setPositiveButton("Elimina") { _, _ ->
                        Fin.elimina(f.optString("id"))
                        dlg.dismiss()
                        refresh()
                    }
                    .show()
            }
        }
    }

    private fun dialogVariazioni(id: String) {
        val f = Fin.per(id) ?: return
        val var_ = f.optJSONArray("variazioni")
        val voci = ArrayList<String>()
        voci.add("➕  Aggiungi variazione")
        val n = var_?.length() ?: 0
        for (i in 0 until n) voci.add(Fin.descrivi(var_!!.getJSONObject(i)))
        OcBuilder(this)
            .setTitle("Variazioni · " + f.optString("nome"))
            .setItems(voci.toTypedArray()) { _, pos ->
                if (pos == 0) {
                    dialogVariazione(id)
                } else {
                    OcBuilder(this)
                        .setTitle("Eliminare la variazione?")
                        .setMessage(voci[pos])
                        .setNegativeButton("Annulla") { _, _ -> dialogVariazioni(id) }
                        .setPositiveButton("Elimina") { _, _ ->
                            val err = Fin.eliminaVariazione(id, pos - 1)
                            if (err != null) aviso(err)
                            refresh()
                            dialogVariazioni(id)
                        }
                        .show()
                }
            }
            .setNegativeButton("Chiudi", null)
            .show()
    }

    private fun dialogVariazione(id: String) {
        val tipi = listOf("extra", "durata", "prolunga", "tasso", "rata")
        val etichette = listOf(
            "Versamento extra (€)", "Nuova durata residua (mesi)", "Prolunga / accorcia (mesi, negativo = accorcia)",
            "Nuovo tasso (%)", "Nuova rata (€)"
        )
        var tipo = "extra"
        var strategia = "ricalcola"
        var data = LocalDate.now()
        val (view, corpo) = corpoDialogo()
        campoMenu(corpo, "Tipo", etichette, etichette[0]) { tipo = tipi[it] }
        val etVal = campoTesto(corpo, "Valore", "", TIPO_DECIMALE_NEG, 12)
        val btnData = MaterialButton(ctxDialogo, null, com.google.android.material.R.attr.materialButtonOutlinedStyle)
        btnData.text = "Data: " + Fin.dataIt(data)
        btnData.setOnClickListener {
            DatePickerDialog(
                this,
                { _, y, m, d ->
                    data = LocalDate.of(y, m + 1, d)
                    btnData.text = "Data: " + Fin.dataIt(data)
                },
                data.year, data.monthValue - 1, data.dayOfMonth
            ).show()
        }
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.topMargin = 16
        corpo.addView(btnData, lp)
        val strat = listOf("Ricalcola la rata", "Mantieni la rata e accorcia")
        campoMenu(corpo, "Dopo un versamento extra", strat, strat[0]) { strategia = if (it == 1) "accorcia" else "ricalcola" }

        val dlg = OcBuilder(this)
            .setTitle("Nuova variazione")
            .setView(view)
            .setPositiveButton("Aggiungi", null)
            .setNegativeButton("Annulla") { _, _ -> dialogVariazioni(id) }
            .create()
        dlg.show()
        dlg.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val v = num(etVal.text.toString())
            if (v == null || etVal.text.isNullOrBlank()) {
                etVal.error = "Valore non valido"
                return@setOnClickListener
            }
            val err = Fin.aggiungiVariazione(id, tipo, v, strategia, data)
            if (err != null) {
                aviso(err)
                return@setOnClickListener
            }
            dlg.dismiss()
            refresh()
            dialogVariazioni(id)
        }
    }

    private class Campo(val hint: String, val def: String = "", val opzioni: List<String>? = null)

    private fun menuCalcolatori() {
        val voci = arrayOf(
            "📊  Simulazione finanziamento", "💡  Rata sostenibile",
            "🏁  Estinzione anticipata", "🩺  Sostenibilità nel tempo"
        )
        OcBuilder(this)
            .setTitle("Calcolatori")
            .setItems(voci) { _, pos ->
                when (pos) {
                    0 -> dialogCalcolo(
                        "Simulazione finanziamento",
                        listOf(
                            Campo("Capitale (€)"), Campo("Durata (anni)"), Campo("Tasso annuo (%)"),
                            Campo("Spese mensili (€)", "0"), Campo("Ammortamento extra iniziale (€)", "0")
                        )
                    ) { v ->
                        Fin.simulazione(d(v, 0), d(v, 1), d(v, 2), d(v, 3), d(v, 4))
                    }
                    1 -> dialogCalcolo(
                        "Rata sostenibile",
                        listOf(
                            Campo("Rata mensile che puoi pagare (€)"), Campo("Durata (anni)"),
                            Campo("Tasso annuo (%)"), Campo("Spese mensili comprese (€)", "0")
                        )
                    ) { v -> Fin.rataSostenibile(d(v, 0), d(v, 1), d(v, 2), d(v, 3)) }
                    2 -> dialogCalcolo(
                        "Estinzione anticipata",
                        listOf(
                            Campo("Debito residuo (€)"), Campo("Mesi residui"), Campo("Tasso annuo (%)"),
                            Campo("Rata mensile attuale (€)"), Campo("Versamento extra annuo (€)", "0"),
                            Campo("Abbattimento iniziale (€)", "0"), Campo("Mese del versamento (1-12)", "12"),
                            Campo("Strategia", "Mantieni rata", listOf("Mantieni rata", "Ricalcola rata"))
                        )
                    ) { v ->
                        Fin.estinzione(
                            d(v, 0), d(v, 1).toInt(), d(v, 2), d(v, 3), d(v, 4), d(v, 5),
                            d(v, 6).toInt(), v[7] == "Mantieni rata"
                        )
                    }
                    else -> dialogCalcolo(
                        "Sostenibilità nel tempo",
                        listOf(
                            Campo("Entrate nette mensili (€)"), Campo("Spese fisse mensili (€)"),
                            Campo("Importo del finanziamento (€)"), Campo("Tasso annuo (%)"),
                            Campo("Durata (anni)", "20"), Campo("Orizzonte osservato (anni)", "10"),
                            Campo("Crescita annua costo della vita (%)", "2"), Campo("Crescita annua reddito (%)", "1"),
                            Campo("Rialzo dei tassi?", "No", listOf("No", "Sì")),
                            Campo("Rialzo (punti %)", "2"), Campo("Dall'anno", "5")
                        )
                    ) { v ->
                        Fin.sostenibilita(
                            d(v, 0), d(v, 1), d(v, 2), d(v, 3), d(v, 4).toInt(), d(v, 5).toInt(),
                            d(v, 6), d(v, 7), v[8] == "Sì", d(v, 9), d(v, 10).toInt()
                        )
                    }
                }
            }
            .setNegativeButton("Chiudi", null)
            .show()
    }

    private fun d(v: List<String>, i: Int): Double = num(v[i]) ?: -1.0

    private fun dialogCalcolo(titolo: String, campi: List<Campo>, calc: (List<String>) -> String) {
        val (view, corpo) = corpoDialogo()
        val letture = ArrayList<() -> String>()
        for (c in campi) {
            if (c.opzioni == null) {
                val et = campoTesto(corpo, c.hint, c.def, TIPO_DECIMALE, 12)
                letture.add { et.text.toString() }
            } else {
                var sel = c.def
                campoMenu(corpo, c.hint, c.opzioni, c.def) { sel = c.opzioni[it] }
                letture.add { sel }
            }
        }
        val ris = TextView(this)
        ris.setPadding(0, 24, 0, 0)
        ris.textSize = 14f
        ris.setTextIsSelectable(true)
        corpo.addView(ris)
        val dlg = OcBuilder(this)
            .setTitle(titolo)
            .setView(view)
            .setPositiveButton("Calcola", null)
            .setNegativeButton("Chiudi", null)
            .create()
        dlg.show()
        dlg.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            ris.text = try {
                calc(letture.map { it() })
            } catch (e: Exception) {
                "⚠️ Controlla i dati inseriti."
            }
        }
    }

    private fun cercaAggiornamenti() {
        aviso("Controllo aggiornamenti...")
        Thread {
            try {
                val r = Aggiorna.ultima()
                val attuale = Aggiorna.versioneInstallata(this)
                runOnUiThread {
                    when {
                        r == null -> aviso("Nessuna versione pubblicata")
                        !Aggiorna.piuRecente(r.tag, attuale) -> aviso("Sei aggiornato (versione $attuale)")
                        else -> OcBuilder(this)
                            .setTitle("Nuova versione " + r.tag)
                            .setMessage("Installata: $attuale. Scaricare e installare l'aggiornamento?")
                            .setNegativeButton("Più tardi", null)
                            .setPositiveButton("Aggiorna") { _, _ -> scaricaAggiornamento(r) }
                            .show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread { aviso("Controllo non riuscito: " + e.message) }
            }
        }.start()
    }

    private fun controllaAggiornamentiAvvio() {
        Thread {
            try {
                Aggiorna.pulisci(applicationContext, Aggiorna.versioneInstallata(this))
                val r = Aggiorna.ultima() ?: return@Thread
                val attuale = Aggiorna.versioneInstallata(this)
                if (!Aggiorna.piuRecente(r.tag, attuale)) return@Thread
                val prefs = getSharedPreferences("aggiornamenti", MODE_PRIVATE)
                if (prefs.getString("rimandata", null) == r.tag) return@Thread
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    OcBuilder(this)
                        .setTitle("Nuova versione " + r.tag)
                        .setMessage("Installata: $attuale. Scaricare e installare l'aggiornamento?")
                        .setNegativeButton("Più tardi") { _, _ ->
                            prefs.edit().putString("rimandata", r.tag).apply()
                        }
                        .setPositiveButton("Aggiorna") { _, _ -> scaricaAggiornamento(r) }
                        .show()
                }
            } catch (e: Exception) {
            }
        }.start()
    }

    private fun scaricaAggiornamento(r: Aggiorna.Release) {
        if (!packageManager.canRequestPackageInstalls()) {
            OcBuilder(this)
                .setTitle("Permesso per installare")
                .setMessage(
                    "Per installare l'aggiornamento Android chiede di consentire a OrbitaCasa di installare app. " +
                        "Concedi il permesso, torna indietro e tocca di nuovo Aggiorna."
                )
                .setNegativeButton("Annulla", null)
                .setPositiveButton("Apri impostazioni") { _, _ ->
                    try {
                        startActivity(
                            Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName"))
                        )
                    } catch (e: Exception) {
                        aviso("Apri Impostazioni → App → OrbitaCasa → Installa app sconosciute")
                    }
                }
                .show()
            return
        }
        val dlg = OcBuilder(this)
            .setTitle("Aggiornamento " + r.tag)
            .setMessage("Scarico… 0%")
            .setCancelable(false)
            .show()
        thread {
            try {
                val f = Aggiorna.scarica(applicationContext, r) { p ->
                    runOnUiThread { dlg.setMessage("Scarico… $p%") }
                }
                val salvato = Aggiorna.archivia(applicationContext, f, r)
                runOnUiThread {
                    dlg.dismiss()
                    if (!salvato) aviso("Copia in Documenti/OrbitaCasa/updates non riuscita, installo comunque")
                    try {
                        val uri = androidx.core.content.FileProvider.getUriForFile(this, "$packageName.fileprovider", f)
                        startActivity(
                            Intent(Intent.ACTION_VIEW)
                                .setDataAndType(uri, "application/vnd.android.package-archive")
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                        finishAndRemoveTask()
                    } catch (e: Exception) {
                        aviso("Non riesco ad avviare l'installazione: " + e.message)
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    dlg.dismiss()
                    aviso("Download non riuscito: " + e.message)
                }
            }
        }
    }

    private fun dialogSync() {
        if (Repo.demoAttiva) {
            aviso("La sincronizzazione non è disponibile in modalità demo")
            return
        }
        val (view, corpo) = corpoDialogo()
        val info = TextView(ctxDialogo)
        info.text = "Sincronizza movimenti, categorie, archivi e documenti con OrbitaCasa Desktop, " +
            "anche quando sei fuori casa. Indirizzo e token si trovano nella Configurazione di Desktop."
        info.setPadding(4, 4, 4, 12)
        corpo.addView(info)
        val tvPc = TextView(ctxDialogo)
        tvPc.textSize = 15f
        tvPc.setPadding(4, 4, 4, 4)
        corpo.addView(tvPc)
        val tvProf = TextView(ctxDialogo)
        tvProf.setPadding(4, 0, 4, 8)
        corpo.addView(tvProf)
        val btnRicollega = MaterialButton(ctxDialogo, null, com.google.android.material.R.attr.materialButtonOutlinedStyle)
        btnRicollega.text = "🔁 Ricollega a questo profilo"
        btnRicollega.isAllCaps = false
        btnRicollega.visibility = View.GONE
        corpo.addView(btnRicollega)
        fun mostraControllo() {
            if (!Sync.configurato(this)) {
                tvPc.text = "⚪ Non configurato"
                tvProf.text = ""
                return
            }
            tvPc.text = "⚪ Controllo Desktop..."
            tvProf.text = if (Sync.profilo(this).isNotBlank()) "Profilo collegato: ${Sync.profilo(this)}" else ""
            Sync.controlla(this) { c ->
                if (isFinishing || isDestroyed) return@controlla
                val coll = Sync.profilo(this)
                tvPc.text = when {
                    !c.online -> "🔴 Desktop offline · " + c.messaggio
                    !c.tokenOk -> "🟠 Desktop raggiungibile · " + c.messaggio
                    else -> "🟢 Desktop online"
                }
                val righe = ArrayList<String>()
                if (coll.isNotBlank()) righe.add("Profilo collegato: $coll")
                var diverso = false
                if (c.online && c.tokenOk) {
                    if (c.profiloPc.isBlank()) {
                        righe.add("Desktop non comunica il profilo attivo (aggiorna OrbitaCasa Desktop)")
                    } else {
                        righe.add("Profilo attivo su Desktop: ${c.profiloPc}")
                        if (coll.isNotBlank() && coll != c.profiloPc) {
                            righe.add("⚠ Profilo diverso: la sincronizzazione è bloccata")
                            diverso = true
                        }
                    }
                }
                tvProf.text = righe.joinToString("\n")
                if (c.segui.isNotBlank()) {
                    btnRicollega.text = "➡ Segui Desktop su «${c.segui}»"
                    btnRicollega.visibility = View.VISIBLE
                    btnRicollega.setOnClickListener {
                        OcBuilder(this)
                            .setTitle("Segui Desktop su «${c.segui}»")
                            .setMessage(
                                "Desktop ora usa il profilo «${c.segui}». I dati del profilo «$coll» restano " +
                                    "salvati sul telefono. Il telefono passa ai dati di «${c.segui}» " +
                                    "(se non ne ha, si scaricano da Desktop)."
                            )
                            .setNegativeButton("Annulla", null)
                            .setPositiveButton("Segui") { _, _ ->
                                try {
                                    Repo.cambiaDatiProfilo(applicationContext, coll.ifBlank { "Principale" }, c.segui)
                                    if (Sync.segui(applicationContext, c.segui)) {
                                        refresh()
                                        riapriSync()
                                        eseguiSync(false)
                                    } else {
                                        aviso("Credenziali del profilo non trovate")
                                    }
                                } catch (e: Exception) {
                                    aviso("Operazione non riuscita: " + e.message)
                                }
                            }
                            .show()
                    }
                    return@controlla
                }
                btnRicollega.text = "🔁 Ricollega a questo profilo"
                btnRicollega.visibility = if (diverso) View.VISIBLE else View.GONE
                btnRicollega.setOnClickListener {
                    OcBuilder(this)
                        .setTitle("Ricollega a «${c.profiloPc}»")
                        .setMessage(
                            "I dati attuali del telefono (profilo «$coll») restano salvati sul telefono e " +
                                "li ritrovi quando torni a quel profilo. Ora il telefono passa ai dati del " +
                                "profilo «${c.profiloPc}» (se non ne ha, si scaricano da Desktop)."
                        )
                        .setNegativeButton("Annulla", null)
                        .setPositiveButton("Ricollega") { _, _ ->
                            try {
                                Repo.cambiaDatiProfilo(applicationContext, coll, c.profiloPc)
                                Sync.ricollega(applicationContext, c.profiloPc)
                                refresh()
                                riapriSync()
                                eseguiSync(false)
                            } catch (e: Exception) {
                                aviso("Ricollegamento non riuscito: " + e.message)
                            }
                        }
                        .show()
                }
            }
        }
        val btnCambia = MaterialButton(ctxDialogo, null, com.google.android.material.R.attr.materialButtonOutlinedStyle)
        btnCambia.text = "👤 Cambia profilo su Desktop"
        btnCambia.isAllCaps = false
        btnCambia.visibility = if (Sync.configurato(this)) View.VISIBLE else View.GONE
        corpo.addView(btnCambia)
        btnCambia.setOnClickListener {
            aviso("Leggo i profili di Desktop...")
            Sync.elencoProfili(this) { el, err ->
                if (isFinishing || isDestroyed) return@elencoProfili
                if (el == null) {
                    aviso(err ?: "Impossibile leggere i profili")
                    return@elencoProfili
                }
                val etichette = el.profili.map {
                    (if (it.nome == el.attivo) "✓ " else "") + it.nome +
                        (if (!it.server) "  (Web Server spento)" else if (!it.ssl) "  (senza HTTPS)" else "")
                }.toTypedArray()
                OcBuilder(this)
                    .setTitle("Profilo su Desktop (attivo: ${el.attivo})")
                    .setItems(etichette) { _, k ->
                        val p = el.profili[k]
                        when {
                            p.nome == el.attivo -> aviso("Questo profilo è già attivo")
                            !p.server -> aviso("Nel profilo «${p.nome}» il Web Server è spento: attivalo prima da Desktop")
                            !p.ssl -> aviso("Nel profilo «${p.nome}» l'HTTPS è disattivato: serve per il telefono")
                            else -> confermaCambioProfilo(p.nome, el.attivo, p.password)
                        }
                    }
                    .setNegativeButton("Annulla", null)
                    .show()
            }
        }
        mostraControllo()
        val etUrl = campoTesto(
            corpo, "Indirizzo di Desktop (es. https://192.168.1.20:8081)", Sync.indirizzo(this),
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        )
        val btnQrUrl = MaterialButton(ctxDialogo, null, com.google.android.material.R.attr.materialButtonOutlinedStyle)
        btnQrUrl.text = "📷 Scansiona QR indirizzo"
        corpo.addView(btnQrUrl)
        val etTok = campoTesto(
            corpo, "Token", Sync.gettone(this),
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        )
        val btnQrTok = MaterialButton(ctxDialogo, null, com.google.android.material.R.attr.materialButtonOutlinedStyle)
        btnQrTok.text = "📷 Scansiona QR token"
        corpo.addView(btnQrTok)
        btnQrUrl.setOnClickListener {
            scansionaQr { v ->
                if (v.startsWith("http://", true) || v.startsWith("https://", true)) {
                    val i = v.indexOf("/api/")
                    etUrl.setText(if (i > 0) v.substring(0, i) else v.trimEnd('/'))
                } else {
                    aviso("Il QR non contiene un indirizzo (https://...)")
                }
            }
        }
        btnQrTok.setOnClickListener { scansionaQr { v -> etTok.setText(v) } }
        val chk = MaterialCheckBox(this)
        chk.text = "Sincronizza all'apertura dell'app"
        chk.isChecked = Sync.auto(this)
        corpo.addView(chk)

        fun selettore(titolo: String, voci: List<String>, corrente: Int, alScelto: (Int) -> Unit) {
            val t = TextView(ctxDialogo)
            t.text = titolo
            t.setPadding(4, 16, 4, 0)
            corpo.addView(t)
            val sp = android.widget.Spinner(ctxDialogo)
            sp.adapter = ArrayAdapter(ctxDialogo, android.R.layout.simple_spinner_dropdown_item, voci)
            sp.setSelection(corrente.coerceIn(0, voci.size - 1), false)
            sp.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: android.widget.AdapterView<*>?, v: View?, pos: Int, id: Long) {
                    alScelto(pos)
                }

                override fun onNothingSelected(p: android.widget.AdapterView<*>?) {}
            }
            corpo.addView(sp)
        }
        val vociBg = (0..12).map { if (it == 0) "Disattivato" else "ogni ${it * 5} minuti" }
        selettore("Sync in background (app chiusa o in secondo piano)", vociBg, Sync.minBg(this) / 5) { k ->
            if (k * 5 != Sync.minBg(this)) {
                Sync.salvaBg(this, k * 5)
                SyncBg.pianifica(applicationContext)
            }
        }
        val vociFg = (0..5).map { if (it == 0) "Disattivato" else if (it == 1) "ogni minuto" else "ogni $it minuti" }
        selettore("Sync a schermo acceso (app aperta)", vociFg, Sync.minFg(this)) { k ->
            if (k != Sync.minFg(this)) {
                Sync.salvaFg(this, k)
                avviaTimerSync()
            }
        }
        val stato = TextView(ctxDialogo)
        val ult = Sync.ultima(this)
        stato.text = (
            if (ult > 0) "Ultima sincronizzazione: " +
                java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.ITALY).format(java.util.Date(ult))
            else "Mai sincronizzato"
            ) + if (Sync.improntaFissata(this).isNotEmpty()) "\nCertificato di Desktop memorizzato" else ""
        stato.setPadding(4, 8, 4, 4)
        corpo.addView(stato)

        dlgSync = OcBuilder(this)
            .setTitle("🔗 Sync con OrbitaCasa Desktop")
            .setView(view)
            .setPositiveButton("Sync") { _, _ ->
                avviaSync(etUrl.text.toString(), etTok.text.toString(), chk.isChecked)
            }
            .setNeutralButton("Scollega") { _, _ ->
                Sync.scollega(this)
                SyncBg.pianifica(applicationContext)
                aviso("Sincronizzazione scollegata")
                refresh()
            }
            .setNegativeButton("Chiudi", null)
            .show()
        dlgSync?.setOnDismissListener { dlgSync = null }
    }

    private var dlgSync: androidx.appcompat.app.AlertDialog? = null

    private fun riapriSync() {
        dlgSync?.dismiss()
        dlgSync = null
        dialogSync()
    }

    private fun confermaCambioProfilo(nome: String, attivoPc: String, conPassword: Boolean) {
        if (conPassword) {
            val et = EditText(this)
            et.hint = "Password del profilo «$nome»"
            et.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            val box = FrameLayout(this)
            val m = (20 * resources.displayMetrics.density).toInt()
            box.setPadding(m, m / 2, m, 0)
            box.addView(et)
            OcBuilder(this)
                .setTitle("Profilo protetto")
                .setMessage("Per passare a «$nome» serve la sua password (la stessa che usi su Desktop). OrbitaCasa Desktop si riavvia con quel profilo e il telefono passa ai suoi dati.")
                .setView(box)
                .setNegativeButton("Annulla", null)
                .setPositiveButton("Continua") { _, _ ->
                    val pw = et.text.toString()
                    if (pw.isEmpty()) aviso("Inserisci la password")
                    else eseguiCambioProfilo(nome, attivoPc, pw)
                }
                .show()
            return
        }
        confermaCambioNormale(nome, attivoPc)
    }

    private fun confermaCambioNormale(nome: String, attivoPc: String) {
        OcBuilder(this)
            .setTitle("Passare a «$nome»?")
            .setMessage(
                "OrbitaCasa Desktop salva i dati e si riavvia con il profilo «$nome» (ci vuole qualche decina di secondi). " +
                    "Il telefono mette da parte i dati attuali e usa quelli di «$nome»; se non ne ha, li scarica da Desktop. " +
                    "Non chiudere l'app finché non finisce."
            )
            .setNegativeButton("Annulla", null)
            .setPositiveButton("Cambia") { _, _ -> eseguiCambioProfilo(nome, attivoPc, "") }
            .show()
    }

    private fun eseguiCambioProfilo(nome: String, attivoPc: String, password: String) {
        val dlg = OcBuilder(this)
            .setTitle("Cambio profilo")
            .setMessage("Richiesta a Desktop...")
            .setCancelable(false)
            .show()
        Sync.cambiaProfilo(this, nome, attivoPc, password, { t ->
            if (!isFinishing && !isDestroyed) dlg.setMessage(t)
        }) { err ->
            if (isFinishing || isDestroyed) return@cambiaProfilo
            dlg.dismiss()
            refresh()
            riapriSync()
            if (err == null) {
                eseguiSync(false)
            } else {
                OcBuilder(this)
                    .setTitle("Cambio profilo")
                    .setMessage(err)
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }

    private fun scansionaQr(onResult: (String) -> Unit) {
        val opzioni = com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(com.google.mlkit.vision.barcode.common.Barcode.FORMAT_QR_CODE)
            .build()
        com.google.mlkit.vision.codescanner.GmsBarcodeScanning.getClient(this, opzioni).startScan()
            .addOnSuccessListener { b ->
                val v = b.rawValue?.trim().orEmpty()
                if (v.isNotEmpty()) onResult(v) else aviso("QR vuoto")
            }
            .addOnFailureListener { e -> aviso("Scansione non riuscita: ${e.message}") }
    }

    private fun scansionaCodice(onResult: (String) -> Unit) {
        val opzioni = com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                com.google.mlkit.vision.barcode.common.Barcode.FORMAT_EAN_13,
                com.google.mlkit.vision.barcode.common.Barcode.FORMAT_EAN_8,
                com.google.mlkit.vision.barcode.common.Barcode.FORMAT_UPC_A,
                com.google.mlkit.vision.barcode.common.Barcode.FORMAT_UPC_E,
                com.google.mlkit.vision.barcode.common.Barcode.FORMAT_CODE_128,
                com.google.mlkit.vision.barcode.common.Barcode.FORMAT_CODE_39,
                com.google.mlkit.vision.barcode.common.Barcode.FORMAT_CODE_93,
                com.google.mlkit.vision.barcode.common.Barcode.FORMAT_ITF,
                com.google.mlkit.vision.barcode.common.Barcode.FORMAT_CODABAR,
                com.google.mlkit.vision.barcode.common.Barcode.FORMAT_QR_CODE,
                com.google.mlkit.vision.barcode.common.Barcode.FORMAT_DATA_MATRIX,
            )
            .build()
        com.google.mlkit.vision.codescanner.GmsBarcodeScanning.getClient(this, opzioni).startScan()
            .addOnSuccessListener { b ->
                val v = b.rawValue?.trim().orEmpty()
                if (v.isEmpty()) aviso("Codice vuoto")
                else if (v.length > Stock.MAX_CODICE) aviso("Codice troppo lungo (max ${Stock.MAX_CODICE} caratteri)")
                else onResult(v)
            }
            .addOnCanceledListener { }
            .addOnFailureListener { e -> aviso("Scansione non riuscita: ${e.message}") }
    }

    private fun avviaSync(indirizzo: String, token: String, auto: Boolean) {
        val u = Sync.normalizzaUrl(indirizzo)
        if (u == null) {
            aviso("Indirizzo non valido: serve https:// e l'indirizzo di Desktop")
            return
        }
        if (token.isBlank()) {
            aviso("Inserisci il token")
            return
        }
        Sync.salva(this, u, token, auto)
        if (Sync.improntaFissata(this).isEmpty()) {
            aviso("Controllo il certificato di Desktop...")
            thread {
                try {
                    val imp = Sync.leggiImpronta(u)
                    runOnUiThread { if (!isFinishing && !isDestroyed) chiediFiducia(imp) }
                } catch (e: Exception) {
                    runOnUiThread { aviso("Desktop non raggiungibile: " + e.message) }
                }
            }
            return
        }
        eseguiSync(false)
    }

    private fun chiediFiducia(impronta: String) {
        OcBuilder(this)
            .setTitle("Certificato di Desktop")
            .setMessage(
                "Impronta SHA-256:\n" + Sync.formatta(impronta) + "\n\n" +
                    "È il certificato autofirmato di OrbitaCasa sul tuo Desktop? Se ti fidi lo ricordo " +
                    "e da ora accetto solo questo: se cambia, la sincronizzazione si ferma."
            )
            .setNegativeButton("Annulla", null)
            .setPositiveButton("Mi fido") { _, _ ->
                Sync.fissaImpronta(this, impronta)
                eseguiSync(false)
            }
            .show()
    }

    private fun eseguiSync(auto: Boolean, riprova: Boolean = false) {
        if (!auto) aviso("Sincronizzazione in corso...")
        Sync.sincronizza(this) { e ->
            if (isFinishing || isDestroyed) return@sincronizza

            if (auto && riprova && !e.ok && e.blocco.isEmpty()) {
                vista<View>(R.id.lockOverlay).postDelayed({
                    if (!isFinishing && !isDestroyed && Sync.daAutoSync(this, true)) eseguiSync(true, false)
                }, 5000)
                return@sincronizza
            }
            if (e.modificato || !auto) refresh()
            if (auto) {
                if (e.ok && e.righe.firstOrNull() != "Tutto già allineato") aviso("Sync Desktop: " + e.righe.first())
                else if (!e.ok && e.blocco.isNotEmpty()) aviso("Sync Desktop fermata: " + (e.errore ?: ""))
            } else {
                OcBuilder(this)
                    .setTitle(if (e.ok) "✅ Sync con OrbitaCasa Desktop" else "Sync non riuscita")
                    .setMessage(if (e.ok) e.righe.joinToString("\n") else (e.errore ?: "Errore sconosciuto"))
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }

    private fun dialogImportaEsporta() {
        OcBuilder(this)
            .setTitle("Importa / Esporta dati")
            .setItems(arrayOf("📥  Importa dati", "📤  Esporta dati")) { _, k ->
                if (k == 0) confermaImporta()
                else creaZip.launch("OrbitaCasa-" + LocalDate.now() + ".zip")
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun dialogCopie(soloSnap: Boolean = false, dataSel: String = "Tutte") {
        if (Repo.demoAttiva) {
            aviso("Non disponibile in modalità demo")
            return
        }
        var tutte = Backup.elenca(this).sortedByDescending { it.modificato }
        val date = listOf("Tutte") + tutte.map { Backup.data(it) }.distinct()
        val dp = resources.displayMetrics.density
        val radice = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * dp).toInt(), (8 * dp).toInt(), (16 * dp).toInt(), 0)
        }
        val chk = android.widget.CheckBox(this).apply {
            text = "Solo snapshot"
            isChecked = soloSnap
        }
        val spin = android.widget.Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, date)
            setSelection(date.indexOf(dataSel).coerceAtLeast(0))
        }
        val lista = android.widget.ListView(this).apply {
            choiceMode = android.widget.ListView.CHOICE_MODE_MULTIPLE
        }
        var visibili: List<Backup.Voce> = emptyList()
        fun ricarica() {
            val d = spin.selectedItem as? String ?: "Tutte"
            visibili = tutte.filter {
                (!chk.isChecked || it.nome.endsWith(Backup.SUFFISSO_SNAP)) && (d == "Tutte" || Backup.data(it) == d)
            }
            val testi = visibili.map {
                val dim = if (it.dimensione < 1048576) "${maxOf(1L, it.dimensione / 1024)} KB" else "%.1f MB".format(it.dimensione / 1048576.0)
                Backup.tipo(it.nome) + "\n" + Backup.data(it) + " · " + dim
            }
            lista.adapter = object : ArrayAdapter<String>(this@MainActivity, android.R.layout.simple_list_item_multiple_choice, testi) {
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val v = super.getView(position, convertView, parent)
                    val lp = v.layoutParams
                    if (lp != null) {
                        lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
                        v.layoutParams = lp
                    }
                    (v as? android.widget.CheckedTextView)?.apply {
                        isSingleLine = false
                        maxLines = 4
                        ellipsize = null
                        textSize = 14f
                        minHeight = 0
                        minimumHeight = 0
                        setPadding((16 * dp).toInt(), (10 * dp).toInt(), (16 * dp).toInt(), (10 * dp).toInt())
                    }
                    return v
                }
            }
        }
        spin.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: android.widget.AdapterView<*>?, v: View?, pos: Int, id: Long) = ricarica()
            override fun onNothingSelected(p: android.widget.AdapterView<*>?) {}
        }
        chk.setOnCheckedChangeListener { _, _ -> ricarica() }
        fun scelti(): List<String> = (0 until visibili.size).filter { lista.isItemChecked(it) }.map { visibili[it].nome }

        fun rileggi() {
            tutte = Backup.elenca(this@MainActivity).sortedByDescending { it.modificato }
            val prec = spin.selectedItem as? String ?: "Tutte"
            val nuove = listOf("Tutte") + tutte.map { Backup.data(it) }.distinct()
            spin.adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, nuove)
            spin.setSelection(nuove.indexOf(prec).coerceAtLeast(0))
            ricarica()
        }

        fun stileBtn(b: MaterialButton, colore: Int) {
            b.isAllCaps = false
            b.maxLines = 1
            b.isSingleLine = true
            b.ellipsize = android.text.TextUtils.TruncateAt.END
            b.insetTop = 0
            b.insetBottom = 0
            b.minHeight = (40 * dp).toInt()
            b.minimumHeight = (40 * dp).toInt()
            b.textSize = 14f
            b.strokeWidth = (1 * dp).toInt()
            b.strokeColor = ColorStateList.valueOf(colore)
            b.setTextColor(colore)
        }
        val bBackup = MaterialButton(ctxDialogo, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Backup ora"
        }
        stileBtn(bBackup, ContextCompat.getColor(this, R.color.oc_outline_strong))
        bBackup.setTextColor(ContextCompat.getColor(this, R.color.oc_text))
        val bElimina = MaterialButton(ctxDialogo, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Elimina"
        }
        stileBtn(bElimina, ContextCompat.getColor(this, R.color.oc_red))
        val righe = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, (6 * dp).toInt(), 0, (6 * dp).toInt())
        }
        val lpA = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = (4 * dp).toInt() }
        val lpB = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = (4 * dp).toInt() }
        righe.addView(bBackup, lpA)
        righe.addView(bElimina, lpB)

        bBackup.setOnClickListener {
            bBackup.isEnabled = false
            bBackup.text = "In corso…"
            thread {
                val f = Repo.creaSnapshot(applicationContext, "manuale")
                runOnUiThread {
                    aviso(if (f != null) "Backup creato: " + Backup.rapporto else "Backup non riuscito")
                    bBackup.isEnabled = true
                    bBackup.text = "Backup ora"
                    rileggi()
                }
            }
        }
        bElimina.setOnClickListener {
            val sel = scelti()
            if (sel.isEmpty()) aviso("Seleziona almeno un file") else confermaEliminaSel(sel) { rileggi() }
        }

        radice.addView(chk)
        radice.addView(spin)
        radice.addView(righe)
        radice.addView(lista, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (280 * dp).toInt()))
        ricarica()

        val d = OcBuilder(this)
            .setTitle("Gestione backup")
            .setView(radice)
            .setPositiveButton("Ripristina", null)
            .setNegativeButton("Chiudi", null)
            .show()
        d.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val sel = scelti()
            if (sel.isEmpty()) {
                aviso("Seleziona almeno un file")
            } else if (sel.any { Backup.eZip(it) } && sel.size > 1) {
                aviso("Se scegli uno zip non puoi sceglierne altri")
            } else {
                d.dismiss()
                confermaRipristinoSel(sel)
            }
        }
    }

    private fun confermaRipristinoSel(nomi: List<String>) {
        if (nomi.isEmpty()) {
            aviso("Seleziona almeno un file")
            dialogCopie()
            return
        }
        val zip = nomi.filter { Backup.eZip(it) }
        if (zip.isNotEmpty() && nomi.size > 1) {
            aviso("Se scegli uno zip non puoi sceglierne altri")
            dialogCopie()
            return
        }
        val snap = zip.firstOrNull()?.endsWith(Backup.SUFFISSO_SNAP) == true
        val msg = when {
            snap -> "Ripristinare lo snapshot «${nomi[0]}»? I dati attuali vengono sostituiti e ne resta una copia. I documenti dello snapshot tornano nelle cartelle dell'app, sovrascrivendo quelli con lo stesso nome."
            zip.isNotEmpty() -> "Ripristinare l'archivio «${nomi[0]}»? I file dei documenti tornano nelle cartelle dell'app, sovrascrivendo quelli con lo stesso nome."
            else -> "Ripristinare i ${nomi.size} file selezionati? I file attuali vengono sovrascritti."
        }
        OcBuilder(this)
            .setTitle("Conferma ripristino")
            .setMessage(msg)
            .setNegativeButton("Annulla", null)
            .setPositiveButton("Ripristina") { _, _ ->
                thread {
                    try {
                        val n = when {
                            snap -> Repo.ripristinaSnapshot(applicationContext, nomi[0])
                            zip.isNotEmpty() -> Repo.ripristinaZipDocumenti(applicationContext, nomi[0])
                            else -> Repo.ripristinaFile(applicationContext, nomi)
                        }
                        runOnUiThread {
                            Repo.ricaricaDopoRipristino()
                            Avvisi.pianifica(applicationContext)
                            refresh()
                            aviso("Ripristinati $n file")
                        }
                    } catch (e: Exception) {
                        runOnUiThread { aviso("Ripristino non riuscito: " + e.message) }
                    }
                }
            }
            .show()
    }

    private fun confermaEliminaSel(nomi: List<String>, dopo: () -> Unit) {
        if (nomi.isEmpty()) {
            aviso("Seleziona almeno un file")
            return
        }
        OcBuilder(this)
            .setTitle("Elimina backup")
            .setMessage("Eliminare ${nomi.size} file di backup? Non si può annullare.")
            .setNegativeButton("Annulla", null)
            .setPositiveButton("Elimina") { _, _ ->
                thread {
                    val n = nomi.count { Backup.elimina(applicationContext, it) }
                    runOnUiThread {
                        aviso("$n file eliminati")
                        dopo()
                    }
                }
            }
            .show()
    }

    private fun dialogRetention() {
        if (Repo.demoAttiva) {
            aviso("Non disponibile in modalità demo")
            return
        }
        val (view, corpo) = corpoDialogo()
        val chk = MaterialCheckBox(this)
        chk.text = "Elimina i movimenti più vecchi"
        chk.isChecked = Repo.retentionOn
        corpo.addView(chk)
        val et = campoTesto(corpo, "Anni da tenere (1-10)", Repo.retentionAnni.toString(), InputType.TYPE_CLASS_NUMBER, 2)
        OcBuilder(this)
            .setTitle("Storico dati")
            .setMessage(
                "Quando è attivo, a ogni salvataggio i movimenti più vecchi degli anni scelti vengono tolti dal telefono. " +
                    "Prima si crea una copia in Copie di sicurezza. Con la Sync restano sul Desktop. " +
                    "Spegnendolo, i movimenti già tolti non tornano da soli: servono le Copie di sicurezza."
            )
            .setView(view)
            .setNegativeButton("Annulla", null)
            .setPositiveButton("Salva") { _, _ ->
                val anni = (et.text.toString().toIntOrNull() ?: 10).coerceIn(1, 10)
                if (!chk.isChecked) {
                    Repo.setRetention(false, anni)
                    renderHome()
                    return@setPositiveButton
                }
                val n = Repo.retentionDaRimuovere(anni)
                if (n == 0) {
                    Repo.setRetention(true, anni)
                    renderHome()
                    aviso("Storico attivo: $anni anni")
                    return@setPositiveButton
                }
                OcBuilder(this)
                    .setTitle("Eliminare $n movimenti?")
                    .setMessage("Ci sono $n movimenti più vecchi di $anni anni. Verranno tolti dal telefono, con una copia in Copie di sicurezza.")
                    .setNegativeButton("Annulla", null)
                    .setPositiveButton("Elimina") { _, _ ->
                        Repo.setRetention(true, anni)
                        thread {
                            val r = Repo.retentionApplicaOra()
                            runOnUiThread {
                                refresh()
                                aviso(if (r > 0) "Tolti $r movimenti" else "Storico attivo: $anni anni")
                            }
                        }
                    }
                    .show()
            }
            .show()
    }

    private fun dialogAzzera() {
        if (Repo.demoAttiva) {
            aviso("Non disponibile in modalità demo")
            return
        }
        val ambiti = Repo.AMBITI
        val scelti = BooleanArray(ambiti.size)
        OcBuilder(this)
            .setTitle("Azzera dati")
            .setMultiChoiceItems(ambiti.map { it.nome }.toTypedArray(), scelti) { _, k, ok -> scelti[k] = ok }
            .setNegativeButton("Annulla", null)
            .setPositiveButton("Avanti") { _, _ ->
                val ids = ambiti.filterIndexed { i, _ -> scelti[i] }.map { it.id }.toSet()
                if (ids.isEmpty()) aviso("Nessun ambito scelto") else confermaAzzera(ids)
            }
            .show()
    }

    private fun confermaAzzera(ids: Set<String>) {
        val sel = Repo.AMBITI.filter { it.id in ids }
        val filePdf = sel.any { it.cartelle.isNotEmpty() }
        OcBuilder(this)
            .setTitle(if (sel.size == 1) "Azzerare 1 ambito?" else "Azzerare ${sel.size} ambiti?")
            .setMessage(
                sel.joinToString("\n") { "• " + it.nome } + "\n\n" +
                    "I dati scelti vengono cancellati dal telefono; ne resta una copia in Copie di sicurezza." +
                    (if (filePdf) " I file dei documenti (PDF e foto) non sono nelle copie e non si recuperano." else "") +
                    " Se usi la Sync, Desktop potrebbe rimandare i dati azzerati."
            )
            .setNegativeButton("Annulla", null)
            .setPositiveButton("Azzera") { _, _ ->
                thread {
                    try {
                        val n = Repo.azzera(applicationContext, ids)
                        runOnUiThread {
                            Repo.ricaricaDopoRipristino()
                            Avvisi.pianifica(applicationContext)
                            refresh()
                            aviso(if (n == 1) "Azzerato 1 ambito" else "Azzerati $n ambiti")
                        }
                    } catch (e: Exception) {
                        runOnUiThread { aviso("Azzeramento non riuscito: " + e.message) }
                    }
                }
            }
            .show()
    }

    private fun dialogAllegaArchivio() {
        if (chiediAllegati.size >= 4) {
            aviso("Massimo 4 allegati")
            return
        }
        val lista = Doc.archiviati().filter { it.file?.isFile == true }.sortedByDescending { it.data?.toEpochDay() ?: 0L }
        if (lista.isEmpty()) {
            aviso("Nessun documento nell'archivio")
            return
        }
        val nomi = lista.map { d -> d.titolo.ifBlank { d.nomeFile } + (d.data?.let { " · " + Doc.fmt(it) } ?: "") }.toTypedArray()
        val sel = BooleanArray(lista.size)
        OcBuilder(this)
            .setTitle("Allega dall'archivio (max ${4 - chiediAllegati.size})")
            .setMultiChoiceItems(nomi, sel) { _, k, ok -> sel[k] = ok }
            .setNegativeButton("Annulla", null)
            .setPositiveButton("Allega") { _, _ ->
                val dirC = java.io.File(cacheDir, "chiedi").also { it.mkdirs() }
                for (i in lista.indices) {
                    if (!sel[i]) continue
                    if (chiediAllegati.size >= 4) {
                        aviso("Massimo 4 allegati")
                        break
                    }
                    try {
                        val src = lista[i].file ?: continue
                        val ext = src.extension.lowercase().ifBlank { "pdf" }
                        val dest = java.io.File(dirC, "all_" + System.nanoTime() + "." + ext)
                        src.copyTo(dest, overwrite = true)
                        chiediAllegati.add(dest to lista[i].nomeFile.ifBlank { src.name })
                    } catch (e: Exception) {
                        aviso("File non leggibile: " + e.message)
                    }
                }
                if (pagina == Pagina.CHIEDI) renderChiedi()
            }
            .show()
    }

    private fun confermaImporta() {
        OcBuilder(this)
            .setTitle("Importa dati")
            .setMessage(
                "Scegli lo zip di OrbitaCasa (backup o snapshot). I dati attuali del telefono " +
                    "vengono sostituiti; ne resta una copia di sicurezza. Se lo zip contiene i " +
                    "documenti (PDF e foto), vengono importati anche quelli."
            )
            .setNegativeButton("Annulla", null)
            .setPositiveButton("Scegli file") { _, _ -> apriZip.launch(arrayOf("*/*")) }
            .show()
    }

    private fun esportaZip(uri: Uri) {
        thread {
            try {
                val n = Repo.esporta(applicationContext, uri)
                runOnUiThread { aviso("Esportati $n file") }
            } catch (e: Exception) {
                runOnUiThread { aviso("Esportazione non riuscita: " + e.message) }
            }
        }
    }

    private var chiestoNotifiche = false

    private val richiediPermNotifiche =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
            Avvisi.pianifica(applicationContext)
            if (!ok) aviso("Senza il permesso le notifiche non arrivano")
            if (pagina == Pagina.PROMEMORIA) renderLista()
        }

    private fun chiediNotifiche() {
        if (Build.VERSION.SDK_INT >= 33 && !Avvisi.permessoNotifiche(this)) {
            richiediPermNotifiche.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun nomeGiorno(d: LocalDate): String =
        maiuscola(d.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ITALY)))

    private fun esitoProm(err: String?, ok: String) {
        if (err != null) {
            aviso(err)
        } else {
            Avvisi.pianifica(applicationContext)
            refresh()
            aviso(ok)
        }
    }

    private fun rigaProm(cont: LinearLayout, o: Prom.Occ, inRitardo: Boolean) {
        val v = o.voce
        val fatta = Prom.fatta(v, o.data)
        val ico = if (v.optBoolean("esterna")) (when (v.optString("apri")) { "autopark" -> "🚗"; "immobil" -> "🏢"; else -> "🐾" })
            else if (v.optString("tipo") == "Scadenza") "⏰" else "📅"
        val ora = v.optString("ora")
        val titolo = (if (fatta) "✓ " else "") + ico + " " + (if (ora.isNotEmpty()) "$ora  " else "") + v.optString("titolo")
        val sotto = listOfNotNull(
            if (inRitardo) Prom.fmtData(o.data) else null,
            if (v.optBoolean("esterna")) v.optString("origine") else v.optString("tipo"),
            Prom.testoFine(v).takeIf { it.isNotEmpty() },
            if (v.optString("ripetizione", "Nessuna") != "Nessuna") "ripete " + Prom.ripTesto(v) else null,
            v.optString("luogo").takeIf { it.isNotEmpty() },
            v.optString("note").trim().lines().firstOrNull()?.takeIf { it.isNotEmpty() },
        ).joinToString(" · ")
        val imp = Prom.importo(v)?.let { Prom.fmtEur(it) } ?: ""
        val col = when {
            inRitardo -> ContextCompat.getColor(this, R.color.oc_red)
            fatta -> ContextCompat.getColor(this, R.color.oc_text_mid)
            else -> null
        }
        riga(cont, titolo, sotto, imp, col) { menuProm(o) }
    }

    private fun renderPromemoria() {
        intestaLista("🔔 Promemoria", "➕  Nuovo promemoria", "⚙️  Avvisi")
        val cont = vista<LinearLayout>(R.id.listaGen)
        val agenda = Prom.tutte()
        Prom.errore?.let {
            vuoto(cont, "⚠️ $it")
            return
        }
        if (Avvisi.attivi(this) || Avvisi.riepilogoAttivo(this)) {
            if (!Avvisi.permessoNotifiche(this)) {
                riga(
                    cont, "🔕 Notifiche non attive", "Tocca per consentirle: senza permesso il telefono non avvisa",
                    "", ContextCompat.getColor(this, R.color.oc_red)
                ) { chiediNotifiche() }
                if (!chiestoNotifiche) {
                    chiestoNotifiche = true
                    chiediNotifiche()
                }
            }
        }
        if (agenda.isEmpty()) {
            vuoto(cont, "Nessun promemoria. Aggiungine uno, oppure importa i dati dal desktop.")
            return
        }
        val oggi = LocalDate.now()
        val ritardo = Prom.scaduti(agenda, oggi)
        val dp = resources.displayMetrics.density
        val rosso = ContextCompat.getColor(this, R.color.oc_red)
        val verde = ContextCompat.getColor(this, R.color.oc_green)
        val grigio = ContextCompat.getColor(this, R.color.oc_text_mid)

        val nav = LinearLayout(this)
        nav.orientation = LinearLayout.HORIZONTAL
        nav.gravity = android.view.Gravity.CENTER_VERTICAL
        nav.setPadding(0, (12 * dp).toInt(), 0, 0)
        val prev = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle)
        prev.text = "◀"
        prev.minWidth = 0
        prev.setOnClickListener {
            meseProm = meseProm.minusMonths(1)
            giornoProm = null
            renderPromemoria()
        }
        val next = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle)
        next.text = "▶"
        next.minWidth = 0
        next.setOnClickListener {
            meseProm = meseProm.plusMonths(1)
            giornoProm = null
            renderPromemoria()
        }
        val titolo = TextView(this)
        titolo.text = maiuscola(meseProm.format(FMT_MESE))
        titolo.gravity = android.view.Gravity.CENTER
        titolo.setTypeface(null, Typeface.BOLD)
        titolo.textSize = 16f
        titolo.setOnClickListener {
            meseProm = YearMonth.now()
            giornoProm = oggi
            renderPromemoria()
        }
        nav.addView(prev)
        nav.addView(titolo, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        nav.addView(next)
        cont.addView(nav)

        val head = LinearLayout(this)
        head.orientation = LinearLayout.HORIZONTAL
        head.setPadding(0, (8 * dp).toInt(), 0, (2 * dp).toInt())
        for (g in listOf("L", "M", "M", "G", "V", "S", "D")) {
            val t = TextView(this)
            t.text = g
            t.gravity = android.view.Gravity.CENTER
            t.textSize = 12f
            t.setTextColor(grigio)
            head.addView(t, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        cont.addView(head)

        val perGiorno = Prom.occorrenze(agenda, meseProm.atDay(1), meseProm.atEndOfMonth()).groupBy { it.data }
        val offset = meseProm.atDay(1).dayOfWeek.value - 1
        val giorniMese = meseProm.lengthOfMonth()
        val righe = (offset + giorniMese + 6) / 7
        for (r in 0 until righe) {
            val rigaV = LinearLayout(this)
            rigaV.orientation = LinearLayout.HORIZONTAL
            for (c in 0 until 7) {
                val idx = r * 7 + c - offset
                val cella = LinearLayout(this)
                cella.orientation = LinearLayout.VERTICAL
                cella.gravity = android.view.Gravity.CENTER_HORIZONTAL
                val lp = LinearLayout.LayoutParams(0, (48 * dp).toInt(), 1f)
                lp.setMargins(2, 2, 2, 2)
                cella.layoutParams = lp
                if (idx in 0 until giorniMese) {
                    val giorno = meseProm.atDay(idx + 1)
                    val voci = perGiorno[giorno].orEmpty()
                    val evid = giorno == giornoProm || giorno == oggi
                    if (evid) cella.setBackgroundResource(R.drawable.bg_tile)
                    val n = TextView(this)
                    n.text = (idx + 1).toString()
                    n.textSize = 14f
                    n.gravity = android.view.Gravity.CENTER_HORIZONTAL
                    n.setTypeface(null, if (evid) Typeface.BOLD else Typeface.NORMAL)
                    n.setPadding(0, (4 * dp).toInt(), 0, 0)
                    cella.addView(n)
                    if (voci.isNotEmpty()) {
                        val dots = LinearLayout(this)
                        dots.orientation = LinearLayout.HORIZONTAL
                        dots.gravity = android.view.Gravity.CENTER_HORIZONTAL
                        if (voci.any { it.voce.optString("tipo") == "Scadenza" }) {
                            val d1 = TextView(this)
                            d1.text = "●"
                            d1.textSize = 9f
                            d1.setTextColor(rosso)
                            dots.addView(d1)
                        }
                        if (voci.any { it.voce.optString("tipo") != "Scadenza" }) {
                            val d2 = TextView(this)
                            d2.text = "●"
                            d2.textSize = 9f
                            d2.setTextColor(verde)
                            dots.addView(d2)
                        }
                        cella.addView(dots)
                    }
                    cella.setOnClickListener {
                        giornoProm = giorno
                        renderPromemoria()
                    }
                }
                rigaV.addView(cella)
            }
            cont.addView(rigaV)
        }

        val g = giornoProm
        if (g == null) {
            vuoto(cont, "Tocca un giorno per vedere le voci.")
        } else {
            sezione(cont, (if (g == oggi) "OGGI · " else "") + nomeGiorno(g).uppercase())
            val voci = Prom.occorrenze(agenda, g, g)
            if (voci.isEmpty()) vuoto(cont, "Niente in agenda.") else voci.forEach { rigaProm(cont, it, false) }
        }
        if (ritardo.isNotEmpty()) {
            riga(
                cont, "⚠️ Scaduti / non completati (" + ritardo.size + ")  " + (if (scadutiAperti) "▲" else "▼"),
                "", "", rosso
            ) {
                scadutiAperti = !scadutiAperti
                renderPromemoria()
            }
            if (scadutiAperti) ritardo.forEach { rigaProm(cont, it, true) }
        }
    }

    private fun menuProm(o: Prom.Occ) {
        val v = o.voce
        if (v.optBoolean("esterna")) {
            val dest = when (v.optString("apri")) { "autopark" -> Pagina.AUTOPARK; "immobil" -> Pagina.IMMOBIL; else -> Pagina.PET }
            OcBuilder(this)
                .setTitle(v.optString("titolo"))
                .setMessage("Scadenza del " + Prom.fmtData(o.data) + " · " + v.optString("origine") + "\nSi modifica nella scheda di origine.")
                .setPositiveButton("Apri " + v.optString("origine")) { _, _ -> schedaAperta = null; mostra(dest) }
                .setNegativeButton("Chiudi", null)
                .show()
            return
        }
        val id = v.optString("id")
        val fatta = Prom.fatta(v, o.data)
        val nomi = ArrayList<String>()
        val azioni = ArrayList<() -> Unit>()
        nomi.add(if (fatta) "↩️  Segna da fare" else "✅  Segna fatto")
        azioni.add { esitoProm(Prom.alternaFatto(id, o.data), if (fatta) "Rimesso da fare" else "Segnato fatto") }
        nomi.add("✏️  Modifica")
        azioni.add { dialogProm(v, o.data) }
        nomi.add("⏩  Posticipa")
        azioni.add { menuPosticipa(o) }
        nomi.add("🗑️  Elimina")
        azioni.add { eliminaProm(v) }
        OcBuilder(this)
            .setTitle(v.optString("titolo"))
            .setItems(nomi.toTypedArray()) { _, i -> azioni[i]() }
            .setNegativeButton("Chiudi", null)
            .show()
    }

    private fun menuPosticipa(o: Prom.Occ) {
        val id = o.voce.optString("id")
        val nomi = ArrayList<String>()
        val modi = ArrayList<String>()
        if (Prom.oraDi(o.voce) != null) {
            nomi.add("Di 1 ora")
            modi.add("ora")
        }
        nomi.add("A domani")
        modi.add("giorno")
        nomi.add("Fra 7 giorni")
        modi.add("settimana")
        OcBuilder(this)
            .setTitle("Posticipa")
            .setItems(nomi.toTypedArray()) { _, i ->
                val (quando, err) = Prom.posticipa(id, o.data, modi[i])
                esitoProm(err, "Posticipata a " + (quando?.let { Prom.fmtData(it.toLocalDate()) } ?: ""))
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun eliminaProm(v: JSONObject) {
        val id = v.optString("id")
        val rip = v.optString("ripetizione", "Nessuna") != "Nessuna"
        OcBuilder(this)
            .setTitle("Eliminare il promemoria?")
            .setMessage(v.optString("titolo") + if (rip) "\nSi ripete: verranno eliminate tutte le occorrenze." else "")
            .setNegativeButton("Annulla", null)
            .setPositiveButton("Elimina") { _, _ -> esitoProm(Prom.elimina(id), "Promemoria eliminato") }
            .show()
    }

    private fun dialogProm(v: JSONObject?, dataBase: LocalDate) {
        var data = if (v != null) runCatching { Prom.data(v) }.getOrDefault(dataBase) else dataBase
        var tipo = v?.optString("tipo", "Appuntamento") ?: "Appuntamento"
        var ripFine: LocalDate? = v?.optString("rip_fine", "")?.takeIf { it.isNotEmpty() }
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val ripAttuale = v?.optString("ripetizione", "Nessuna") ?: "Nessuna"
        val ripValori = Prom.RIPETIZIONI + (if (ripAttuale !in Prom.RIPETIZIONI) listOf(ripAttuale) else emptyList())
        val ripEtichette = ripValori.map { if (it in Prom.RIPETIZIONI) it else it + " (" + Prom.ripTesto(v!!) + ")" }
        var rip = ripAttuale
        var pre = v?.optInt("preavviso", 0) ?: 0
        val preVoci = Prom.PREAVVISI.toMutableList()
        if (preVoci.none { it.second == pre }) preVoci.add("$pre giorni prima" to pre)
        var ant = v?.let { Prom.anticipoMin(it) } ?: Prom.ANTICIPO_ORA_MIN
        val antVoci = Prom.ANTICIPI_ORA.toMutableList()
        if (antVoci.none { it.second == ant }) antVoci.add("$ant minuti prima" to ant)

        val (view, corpo) = corpoDialogo()
        val etTit = campoTesto(
            corpo, "Titolo", v?.optString("titolo") ?: "",
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES, Prom.MAX_TITOLO
        )
        campoMenu(corpo, "Tipo", Prom.TIPI, tipo) { tipo = Prom.TIPI[it] }

        val lpBtn = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lpBtn.topMargin = 16
        val btnData = MaterialButton(ctxDialogo, null, com.google.android.material.R.attr.materialButtonOutlinedStyle)
        btnData.text = "Data: " + Prom.fmtData(data)
        btnData.setOnClickListener {
            DatePickerDialog(
                this,
                { _, y, m, d ->
                    data = LocalDate.of(y, m + 1, d)
                    btnData.text = "Data: " + Prom.fmtData(data)
                },
                data.year, data.monthValue - 1, data.dayOfMonth
            ).show()
        }
        corpo.addView(btnData, lpBtn)

        val etOra = campoTesto(corpo, "Ora (es. 18:30; vuoto = tutto il giorno)", v?.optString("ora") ?: "", InputType.TYPE_CLASS_TEXT, 5)
        val etImp = campoTesto(
            corpo, "Importo (€, facoltativo)",
            v?.let { Prom.importo(it) }?.let { String.format(Locale.ITALY, "%.2f", it) } ?: "", TIPO_DECIMALE, 10
        )
        Calc.collega(this, etImp)
        val etLuogo = campoTesto(corpo, "Luogo (facoltativo)", v?.optString("luogo") ?: "", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES, 40)

        campoMenu(corpo, "Ripetizione", ripEtichette, ripEtichette[ripValori.indexOf(rip)]) { rip = ripValori[it] }
        val btnFine = MaterialButton(ctxDialogo, null, com.google.android.material.R.attr.materialButtonOutlinedStyle)
        fun testoFine() { btnFine.text = "Ripeti fino al: " + (ripFine?.let { Prom.fmtData(it) } ?: "nessun limite") }
        testoFine()
        btnFine.setOnClickListener {
            val base = ripFine ?: data
            val dp = DatePickerDialog(
                this,
                { _, y, m, d ->
                    ripFine = LocalDate.of(y, m + 1, d)
                    testoFine()
                },
                base.year, base.monthValue - 1, base.dayOfMonth
            )
            dp.setButton(android.content.DialogInterface.BUTTON_NEUTRAL, "Nessun limite") { _, _ ->
                ripFine = null
                testoFine()
            }
            dp.show()
        }
        corpo.addView(btnFine, lpBtn)

        campoMenu(corpo, "Preavviso", preVoci.map { it.first }, preVoci.first { it.second == pre }.first) { pre = preVoci[it].second }
        campoMenu(corpo, "Avviso all'ora (se c'è l'ora)", antVoci.map { it.first }, antVoci.first { it.second == ant }.first) { ant = antVoci[it].second }
        val etNote = campoTesto(
            corpo, "Note (max " + Prom.MAX_NOTE + " caratteri)", v?.optString("note") ?: "",
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES, Prom.MAX_NOTE
        )

        val b = OcBuilder(this)
            .setTitle(if (v == null) "Nuovo promemoria" else "Modifica promemoria")
            .setView(view)
            .setPositiveButton("Salva", null)
            .setNegativeButton("Annulla", null)
        if (v != null) b.setNeutralButton("Elimina", null)
        val dlg = b.create()
        dlg.show()
        dlg.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val titolo = etTit.text.toString().trim()
            if (titolo.isEmpty()) { etTit.error = "Inserisci un titolo"; return@setOnClickListener }
            val ora = Prom.normalizzaOra(etOra.text.toString())
            if (ora == null) { etOra.error = "Ora non valida (es. 18, 18:30)"; return@setOnClickListener }
            val testoImp = etImp.text.toString()
            val imp = Prom.parseImporto(testoImp)
            if (testoImp.isNotBlank() && imp == null) { etImp.error = "Importo non valido"; return@setOnClickListener }
            val rf = ripFine
            if (rip != "Nessuna" && rf != null && rf.isBefore(data)) {
                aviso("La ripetizione finisce prima della data di inizio")
                return@setOnClickListener
            }
            val campi = JSONObject()
            campi.put("titolo", titolo)
            campi.put("tipo", tipo)
            campi.put("data", data.toString())
            campi.put("ora", ora)
            campi.put("importo", imp ?: JSONObject.NULL)
            campi.put("ripetizione", rip)
            campi.put("rip_fine", if (rip != "Nessuna" && rf != null) rf.toString() else "")
            campi.put("luogo", etLuogo.text.toString().trim())
            campi.put("note", etNote.text.toString().trim())
            campi.put("preavviso", pre)
            campi.put("anticipo_min", ant)
            if (v != null) {
                val gg = Prom.durataGg(v)
                campi.put("data_fine", if (gg > 0) data.plusDays(gg.toLong()).toString() else "")
                campi.put("ora_fine", if (ora.isEmpty()) "" else v.optString("ora_fine", ""))
            } else {
                campi.put("data_fine", "")
                campi.put("ora_fine", "")
                campi.put("rrule", "")
            }
            val err = Prom.salva(v?.optString("id"), campi)
            if (err != null) { aviso(err); return@setOnClickListener }
            dlg.dismiss()
            Avvisi.pianifica(applicationContext)
            refresh()
            aviso(if (v == null) "Promemoria aggiunto" else "Promemoria salvato")
        }
        if (v != null) {
            dlg.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                dlg.dismiss()
                eliminaProm(v)
            }
        }
    }

    private fun dialogAvvisi() {
        var all = Avvisi.attivi(this)
        var rie = Avvisi.riepilogoAttivo(this)
        val si = listOf("Attivo", "Spento")
        val (view, corpo) = corpoDialogo()
        campoMenu(corpo, "Avviso all'ora (voci con orario)", si, if (all) si[0] else si[1]) { all = it == 0 }
        campoMenu(corpo, "Riepilogo del giorno", si, if (rie) si[0] else si[1]) { rie = it == 0 }
        val etOra = campoTesto(corpo, "Ora del riepilogo (es. 08:00)", Avvisi.oraRiepilogo(this), InputType.TYPE_CLASS_TEXT, 5)
        val dlg = OcBuilder(this)
            .setTitle("Avvisi promemoria")
            .setMessage(
                "L'avviso all'ora parte prima dell'orario (si imposta su ogni promemoria). " +
                    "Il riepilogo dice quanti promemoria hai oggi, quanti sono scaduti e quanti in arrivo. " +
                    "A telefono fermo Android può ritardare gli avvisi di qualche minuto."
            )
            .setView(view)
            .setPositiveButton("Salva", null)
            .setNegativeButton("Annulla", null)
            .create()
        dlg.show()
        dlg.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val o = Prom.normalizzaOra(etOra.text.toString())
            if (o == null || o.isEmpty()) { etOra.error = "Ora non valida (es. 08:00)"; return@setOnClickListener }
            Avvisi.imposta(applicationContext, all, rie, o)
            dlg.dismiss()
            if (all || rie) chiediNotifiche()
            aviso("Avvisi salvati")
            if (pagina == Pagina.PROMEMORIA) renderLista()
        }
    }

    private fun renderNotifiche() {
        intestaLista("📩 Notifiche → spese", "➕  Nuova regola", "🧪  Prova testo")
        val cont = vista<LinearLayout>(R.id.listaGen)
        val verde = ContextCompat.getColor(this, R.color.oc_green)
        val rosso = ContextCompat.getColor(this, R.color.oc_red)
        val on = Notif.attive(this)
        riga(
            cont, "Lettura notifiche",
            if (on) "Le notifiche che passano le regole diventano spese" else "Spenta: l'app non legge nessuna notifica",
            if (on) "ON" else "OFF", if (on) verde else null
        ) { cambiaLetturaNotif() }
        if (on && !Notif.accessoConcesso(this)) {
            riga(
                cont, "🔕 Accesso alle notifiche non concesso",
                "Tocca per aprire le impostazioni del telefono e consentirlo", "", rosso
            ) { dialogNotifAccesso() }
        }
        val gem = Notif.geminiAttivo(this)
        riga(
            cont, "Ripiego Gemini",
            if (gem) "Se le regole fisse non capiscono, chiede a Gemini" else "Spento: nessun testo viene inviato a Google",
            if (gem) "ON" else "OFF", if (gem) verde else null
        ) { cambiaGeminiNotif() }

        val attesa = Notif.inAttesa()
        sezione(cont, "DA CONFERMARE (" + attesa.size + ")")
        if (attesa.isEmpty()) vuoto(cont, "Nessuna spesa in attesa.")
        val fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy")
        for (p in attesa) {
            val sotto = listOf(p.app, Notif.dataDi(p).format(fmt), p.tipo, if (p.fonte == "gemini") "Gemini" else "")
                .filter { it.isNotEmpty() }.joinToString(" · ")
            val entrata = p.tipo == "Entrata"
            riga(cont, p.desc, sotto, (if (entrata) "+" else "−") + euro(p.importo), if (entrata) verde else rosso) {
                dialogNotifConferma(p)
            }
        }
        if (attesa.size > 1) {
            riga(cont, "🧹 Scarta tutte", "Svuota l'elenco delle spese da confermare", "", null) {
                OcBuilder(this)
                    .setTitle("Scartare tutte?")
                    .setMessage("Le " + attesa.size + " spese in attesa verranno scartate.")
                    .setNegativeButton("Annulla", null)
                    .setPositiveButton("Scarta") { _, _ -> Notif.scartaTutte(); refresh() }
                    .show()
            }
        }

        val regole = Notif.regoleLista()
        sezione(cont, "REGOLE (" + regole.size + ")")
        if (regole.isEmpty()) vuoto(cont, "Nessuna regola: senza regole non viene letto nulla. Tocca «Nuova regola».")
        for (r in regole) {
            riga(cont, (if (r.attiva) "" else "⏸ ") + r.appLabel, r.riassunto(), "", null) { dialogNotifRegola(r) }
        }
    }

    private fun cambiaLetturaNotif() {
        val on = !Notif.attive(this)
        Notif.impostaAttive(this, on)
        if (on) {
            if (!Notif.accessoConcesso(this)) dialogNotifAccesso()
            chiediNotifiche()
        }
        refresh()
    }

    private fun cambiaGeminiNotif() {
        if (Notif.geminiAttivo(this)) {
            Notif.impostaGemini(this, false)
            refresh()
            return
        }
        if (Gemini.chiave(this).isEmpty()) {
            aviso("Imposta prima la chiave Gemini (Strumenti)")
            return
        }
        OcBuilder(this)
            .setTitle("Attivare il ripiego Gemini?")
            .setMessage(
                "Quando le regole fisse non riconoscono un pagamento ma nel testo c'è un importo in euro, " +
                    "il testo della notifica viene inviato a Gemini (Google) per capirlo. " +
                    "Vale per le notifiche che passano le tue regole."
            )
            .setNegativeButton("Annulla", null)
            .setPositiveButton("Attiva") { _, _ -> Notif.impostaGemini(this, true); refresh() }
            .show()
    }

    private fun dialogNotifAccesso() {
        OcBuilder(this)
            .setTitle("Accesso alle notifiche")
            .setMessage(
                "Per leggere le notifiche Android chiede un permesso speciale: apri l'elenco, scegli OrbitaCasa e attivalo. " +
                    "Se il telefono dice che l'impostazione è limitata, apri Info app di OrbitaCasa, tocca i tre puntini in alto " +
                    "e scegli «Consenti impostazioni con restrizioni», poi riprova."
            )
            .setNegativeButton("Annulla", null)
            .setPositiveButton("Apri impostazioni") { _, _ ->
                try {
                    startActivity(Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                } catch (_: Exception) {
                    aviso("Apri a mano: Impostazioni → Notifiche → Accesso alle notifiche")
                }
            }
            .show()
    }

    private fun oraInMinuti(t: String): Int? {
        val m = Regex("""^(\d{1,2})(?::(\d{2}))?$""").find(t.trim()) ?: return null
        val h = m.groupValues[1].toInt()
        val mi = m.groupValues[2].ifEmpty { "0" }.toInt()
        return if (h in 0..23 && mi in 0..59) h * 60 + mi else null
    }

    private fun sceltaAppNotif(ok: (String, String) -> Unit) {
        val pm = packageManager
        val lancio = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val app = pm.queryIntentActivities(lancio, 0)
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .filter { it.first != packageName }
            .distinctBy { it.first }
            .sortedBy { it.second.lowercase() }
        val tutte = listOf(RegolaNotif.TUTTE to "Tutte le app") + app
        OcBuilder(this)
            .setTitle("Scegli l'app")
            .setItems(tutte.map { it.second }.toTypedArray()) { _, i -> ok(tutte[i].first, tutte[i].second) }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun dialogNotifRegola(r: RegolaNotif?) {
        var pkg = r?.pkg ?: RegolaNotif.TUTTE
        var appLabel = r?.appLabel ?: "Tutte le app"
        var giorni = r?.giorni ?: 127
        var fascia = r?.usaFascia ?: false
        var auto = r?.auto ?: Notif.AUTO_NO
        var conto = r?.conto ?: ""
        var metodo = r?.metodo ?: ""
        var attiva = r?.attiva ?: true

        val (view, corpo) = corpoDialogo()
        val lpBtn = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lpBtn.topMargin = 16
        val btnApp = MaterialButton(ctxDialogo, null, com.google.android.material.R.attr.materialButtonOutlinedStyle)
        btnApp.text = "App: $appLabel"
        btnApp.setOnClickListener {
            sceltaAppNotif { p, l ->
                pkg = p
                appLabel = l
                btnApp.text = "App: $l"
            }
        }
        corpo.addView(btnApp, lpBtn)
        val etInc = campoTesto(corpo, "Inoltra solo se contiene (parole separate da virgola)", r?.include ?: "", InputType.TYPE_CLASS_TEXT, 200)
        val etExc = campoTesto(corpo, "Ignora se contiene (parole separate da virgola)", r?.exclude ?: "", InputType.TYPE_CLASS_TEXT, 200)

        val btnGiorni = MaterialButton(ctxDialogo, null, com.google.android.material.R.attr.materialButtonOutlinedStyle)
        fun testoGiorni() {
            btnGiorni.text = "Giorni: " + if (giorni == 127) "tutti" else
                RegolaNotif.NOMI_GIORNI.filterIndexed { i, _ -> giorni and (1 shl i) != 0 }.joinToString(" ")
        }
        testoGiorni()
        btnGiorni.setOnClickListener {
            val sel = BooleanArray(7) { giorni and (1 shl it) != 0 }
            OcBuilder(this)
                .setTitle("Giorni attivi")
                .setMultiChoiceItems(RegolaNotif.NOMI_GIORNI.toTypedArray(), sel) { _, i, on -> sel[i] = on }
                .setNegativeButton("Annulla", null)
                .setPositiveButton("OK") { _, _ ->
                    var g = 0
                    sel.forEachIndexed { i, on -> if (on) g = g or (1 shl i) }
                    if (g == 0) {
                        aviso("Seleziona almeno un giorno")
                    } else {
                        giorni = g
                        testoGiorni()
                    }
                }
                .show()
        }
        corpo.addView(btnGiorni, lpBtn)

        val orari = listOf("Tutto il giorno", "Solo in una fascia oraria")
        campoMenu(corpo, "Orario", orari, orari[if (fascia) 1 else 0]) { fascia = it == 1 }
        val etDa = campoTesto(corpo, "Fascia: dalle (es. 08:00)", RegolaNotif.fmtOra(r?.daMin ?: 480), InputType.TYPE_CLASS_TEXT, 5)
        val etA = campoTesto(corpo, "Fascia: alle (es. 20:00)", RegolaNotif.fmtOra(r?.aMin ?: 1200), InputType.TYPE_CLASS_TEXT, 5)

        val nomiConti = listOf("(nessuno)") + Repo.conti().map { it.nome }.let { l -> if (conto.isNotEmpty() && conto !in l) l + conto else l }
        campoMenu(corpo, "Conto", nomiConti, if (conto.isNotEmpty()) conto else nomiConti[0]) { conto = if (it == 0) "" else nomiConti[it] }
        val nomiMetodi = listOf("(nessuno)") + Repo.METODI.map { it.first }.let { l -> if (metodo.isNotEmpty() && metodo !in l) l + metodo else l }
        campoMenu(corpo, "Metodo di pagamento", nomiMetodi, if (metodo.isNotEmpty()) metodo else nomiMetodi[0]) { metodo = if (it == 0) "" else nomiMetodi[it] }
        val etTag = campoTesto(corpo, "Tag (uno solo, es. casa)", r?.tag ?: "", InputType.TYPE_CLASS_TEXT, 40)

        val autoVoci = listOf(
            Notif.AUTO_NO to "No, da confermare a mano",
            Notif.AUTO_REGOLE to "Sì, se riconosciuta dalle regole fisse",
            Notif.AUTO_TUTTO to "Sì, sempre (anche con Gemini)",
        )
        campoMenu(corpo, "Registra in automatico", autoVoci.map { it.second }, (autoVoci.firstOrNull { it.first == auto } ?: autoVoci[0]).second) {
            auto = autoVoci[it].first
        }
        val stati = listOf("Attiva", "Disattivata")
        campoMenu(corpo, "Stato della regola", stati, if (attiva) stati[0] else stati[1]) { attiva = it == 0 }

        val b = OcBuilder(this)
            .setTitle(if (r == null) "Nuova regola" else "Modifica regola")
            .setView(view)
            .setPositiveButton("Salva", null)
            .setNegativeButton("Annulla", null)
        if (r != null) b.setNeutralButton("Elimina", null)
        val dlg = b.create()
        dlg.show()
        dlg.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val da = oraInMinuti(etDa.text.toString())
            val a = oraInMinuti(etA.text.toString())
            if (fascia && (da == null || a == null)) {
                aviso("Orario non valido (es. 08:00)")
                return@setOnClickListener
            }
            val nuova = (r ?: RegolaNotif()).copy(
                pkg = pkg,
                appLabel = appLabel,
                include = etInc.text.toString().trim(),
                exclude = etExc.text.toString().trim(),
                giorni = giorni,
                usaFascia = fascia,
                daMin = da ?: (r?.daMin ?: 480),
                aMin = a ?: (r?.aMin ?: 1200),
                conto = conto,
                metodo = metodo,
                auto = auto,
                tag = etTag.text.toString().trim().trimStart('#'),
                attiva = attiva,
            )
            Notif.salvaRegola(nuova)
            dlg.dismiss()
            aviso("Regola salvata")
            refresh()
        }
        if (r != null) {
            dlg.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                dlg.dismiss()
                OcBuilder(this)
                    .setTitle("Eliminare la regola?")
                    .setMessage(r.appLabel + "\n" + r.riassunto())
                    .setNegativeButton("Annulla", null)
                    .setPositiveButton("Elimina") { _, _ ->
                        Notif.eliminaRegola(r.id)
                        refresh()
                        aviso("Regola eliminata")
                    }
                    .show()
            }
        }
    }

    private fun dialogNotifConferma(p: Proposta) {
        var tipo = p.tipo
        val cats = Repo.categorie()
        val sug = Notif.categoriaSuggerita(p)
        var cat = if (sug in cats) sug else if ("Generica" in cats) "Generica" else (cats.firstOrNull() ?: "")
        var conto = Notif.contoPredefinito(p)
        var metodo = Notif.metodoPredefinito(p)
        val nomiConti = listOf("(nessuno)") + Repo.conti().map { it.nome }
        val nomiMetodi = listOf("(nessuno)") + Repo.METODI.map { it.first }

        val (view, corpo) = corpoDialogo()
        val etDesc = campoTesto(corpo, "Descrizione", p.desc, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES, 60)
        val etImp = campoTesto(corpo, "Importo (€)", String.format(Locale.ITALY, "%.2f", p.importo), TIPO_DECIMALE, 12)
        Calc.collega(this, etImp)
        val tipi = listOf("Uscita", "Entrata")
        campoMenu(corpo, "Tipo", tipi, if (tipo in tipi) tipo else tipi[0]) { tipo = tipi[it] }
        if (cats.isNotEmpty()) campoMenu(corpo, "Categoria", cats, cat) { cat = cats[it] }
        campoMenu(corpo, "Conto", nomiConti, if (conto.isNotEmpty()) conto else nomiConti[0]) { conto = if (it == 0) "" else nomiConti[it] }
        campoMenu(corpo, "Metodo di pagamento", nomiMetodi, if (metodo.isNotEmpty()) metodo else nomiMetodi[0]) { metodo = if (it == 0) "" else nomiMetodi[it] }
        val etTag = campoTesto(corpo, "Tag", Notif.tagPredefiniti(p).joinToString(" "), InputType.TYPE_CLASS_TEXT, 60)
        suggerimentiTag(etTag)

        val originale = (p.titolo + "\n" + p.testo).trim().take(300)
        val tvOrig = TextView(this)
        tvOrig.text = originale
        tvOrig.maxLines = 4
        tvOrig.ellipsize = android.text.TextUtils.TruncateAt.END
        tvOrig.setPadding(0, 0, 0, (8 * resources.displayMetrics.density).toInt())
        corpo.addView(tvOrig, 0)
        val dlg = OcBuilder(this)
            .setTitle("Conferma la spesa")
            .setView(view)
            .setPositiveButton("Registra", null)
            .setNegativeButton("Annulla", null)
            .setNeutralButton("Scarta", null)
            .create()
        dlg.show()
        dlg.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val desc = etDesc.text.toString().trim()
            if (desc.isEmpty()) {
                etDesc.error = "Inserisci una descrizione"
                return@setOnClickListener
            }
            val imp = importoOk(etImp.text.toString())
            if (imp == null) {
                etImp.error = "Importo non valido"
                return@setOnClickListener
            }
            if (cat.isEmpty()) {
                aviso("Nessuna categoria disponibile")
                return@setOnClickListener
            }
            val ok = Notif.registra(p, cat, desc, imp, tipo, conto, metodo, Notif.normalizzaTag(etTag.text.toString()), true)
            dlg.dismiss()
            refresh()
            aviso(if (ok) "Spesa registrata" else "Doppione scartato: era già presente")
        }
        dlg.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
            Notif.scarta(p.id)
            dlg.dismiss()
            refresh()
        }
    }

    private fun dialogNotifProva() {
        val (view, corpo) = corpoDialogo()
        val etT = campoTesto(corpo, "Titolo della notifica", "", InputType.TYPE_CLASS_TEXT, 200)
        val etX = campoTesto(
            corpo, "Testo della notifica", "",
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE, 1000
        )
        val dlg = OcBuilder(this)
            .setTitle("Prova il riconoscimento")
            .setMessage("Incolla titolo e testo di una notifica per vedere cosa ne capirebbe l'app. Non registra nulla.")
            .setView(view)
            .setPositiveButton("Prova", null)
            .setNegativeButton("Chiudi", null)
            .create()
        dlg.show()
        dlg.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val t = etT.text.toString()
            val x = etX.text.toString()
            if (t.isBlank() && x.isBlank()) {
                etX.error = "Inserisci il testo"
                return@setOnClickListener
            }
            val gem = Notif.geminiAttivo(this)
            thread {
                val e = Notif.estrai(applicationContext, t, x, gem)
                runOnUiThread { esitoProvaNotif(t, x, e) }
            }
        }
    }

    private fun esitoProvaNotif(titolo: String, testo: String, e: NotifEstrai.Estratto?) {
        if (e == null) {
            OcBuilder(this)
                .setTitle("Non riconosciuta")
                .setMessage("Non sembra un pagamento o un incasso: verrebbe scartata.")
                .setPositiveButton("OK", null)
                .show()
            return
        }
        val prova = Proposta(
            "prova", LocalDate.now().atStartOfDay().toString(), "", titolo, testo,
            e.importo, e.tipo, e.desc, e.fonte, "attesa", "", "", "", emptyList(),
        )
        val cat = Notif.categoriaSuggerita(prova)
        val valida = cat.isNotEmpty() && !cat.equals("Generica", true) && cat in Repo.categorie()
        val righe = listOf(
            "Importo: " + euro(e.importo),
            "Tipo: " + e.tipo,
            "Descrizione: " + e.desc,
            "Riconosciuta da: " + if (e.fonte == "gemini") "Gemini" else "regole fisse",
            "Categoria: " + if (valida) cat else "non trovata, da scegliere a mano",
        ).joinToString("\n")
        OcBuilder(this)
            .setTitle("Riconosciuta")
            .setMessage(righe + "\n\nLa registrazione automatica scatta solo se la regola la prevede e la categoria è stata trovata.")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun confermaEntraDemo() {
        OcBuilder(this)
            .setTitle("Modalità demo")
            .setMessage(
                "Scarica un profilo di esempio da GitHub (serve internet) e lo mostra al posto dei tuoi dati. " +
                    "I tuoi dati vengono messi da parte e tornano esattamente come erano quando esci dalla demo. " +
                    "Quello che inserisci durante la demo viene perso all'uscita."
            )
            .setNegativeButton("Annulla", null)
            .setPositiveButton("Avvia demo") { _, _ ->
                aviso("Download del profilo demo…")
                thread {
                    try {
                        val zip = Demo.scarica(applicationContext)
                        try {
                            Repo.entraDemo(applicationContext, zip)
                        } finally {
                            zip.delete()
                        }
                        runOnUiThread {
                            Avvisi.pianifica(applicationContext)
                            mostra(Pagina.HOME)
                            aviso("Demo attiva")
                        }
                    } catch (e: Exception) {
                        runOnUiThread { aviso("Demo non avviata: " + Demo.messaggio(e)) }
                    }
                }
            }
            .show()
    }

    private fun confermaEsciDemo() {
        OcBuilder(this)
            .setTitle("Esci dalla demo")
            .setMessage("I dati di esempio vengono eliminati e tornano i tuoi dati.")
            .setNegativeButton("Resta in demo", null)
            .setPositiveButton("Esci") { _, _ ->
                thread {
                    try {
                        Repo.esciDemo(applicationContext)
                        runOnUiThread {
                            Avvisi.pianifica(applicationContext)
                            mostra(Pagina.HOME)
                            aviso("Dati ripristinati")
                        }
                    } catch (e: Exception) {
                        runOnUiThread { aviso("Uscita non riuscita: " + e.message) }
                    }
                }
            }
            .show()
    }

    private fun importaZip(uri: Uri) {
        thread {
            try {
                val letti = Repo.importa(applicationContext, uri)
                runOnUiThread {
                    Repo.ricarica()
                    Avvisi.pianifica(applicationContext)
                    refresh()
                    aviso("Importati: " + letti.joinToString(", "))
                }
            } catch (e: Exception) {
                runOnUiThread { aviso("Importazione non riuscita: " + e.message) }
            }
        }
    }
}
