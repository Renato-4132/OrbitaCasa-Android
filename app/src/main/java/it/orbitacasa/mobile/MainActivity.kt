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

    private enum class Pagina { HOME, MOVIMENTI, CONTI, CATEGORIE, ANALISI, TARGET, RICORRENZE, CALENDARIO, FINANZIAMENTI, AUTOPARK, IMMOBIL, PET, FONDO, GRAFICI, PIANIFICA, DOCUMENTI, DOCPERS, UTENZE, FAIRSHARE, CHECKOUT, RICERCA, PREVISIONE, PROMEMORIA, NOTIFICHE, CASACARE, PENSIONE, INFO, ESTRATTI, CHIEDI }

    private class Tessera(val ico: String, val lbl: String, val azione: () -> Unit)

    private var pagina = Pagina.HOME
    private var mese: YearMonth = YearMonth.now()
    private var sbloccata = false
    private var ultimoStop = 0L
    private var meseCal: YearMonth = YearMonth.now()
    private var casaCat = "Tutte"
    private var casaStato = "Tutti"
    private var penVista = 0
    private var scadutiAperti = false
    private var catCerca = ""
    private var catOrdine = "Nome"
    private var catSoloVuote = false
    private var catUsciteAperte = false
    private var catEntrateAperte = false
    private var meseProm: YearMonth = YearMonth.now()
    private var giornoProm: LocalDate? = LocalDate.now()
    private var giornoCal: LocalDate? = LocalDate.now()
    private var pianCal = false
    private var schedaAperta: String? = null
    private var grafVista = 0
    private var grafSempre = false
    private var grafAnno = LocalDate.now().year
    private var grafConto = ""
    private val gruppiAperti = HashSet<String>()
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
    private var ultimoControlloFm = 0L
    private val aggiornaGruppi = ArrayList<() -> Unit>()

    private val apriZip =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) importaZip(uri)
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
                Pagina.PIANIFICA -> dialogNuovoPiano()
                Pagina.DOCUMENTI, Pagina.DOCPERS -> nuovoDocumento()
                Pagina.UTENZE -> dialogNuovaLettura()
                Pagina.GRAFICI -> dialogContoGrafici()
                Pagina.FAIRSHARE -> dialogPartecipante(null)
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
                Pagina.CHIEDI -> {
                    chiediStorico.clear()
                    chiediBozza = ""
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
                esportaPensione()
            } else if (pagina == Pagina.CATEGORIE) {
                menuStrumentiCategorie()
            } else if (pagina == Pagina.PROMEMORIA) {
                dialogAvvisi()
            } else if (pagina == Pagina.NOTIFICHE) {
                dialogNotifProva()
            } else if (pagina == Pagina.FAIRSHARE) {
                dialogFiltriFs()
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
        vista<View>(R.id.btnPrev).setOnClickListener {
            mese = mese.minusMonths(1)
            renderMovimenti()
        }
        vista<View>(R.id.btnNext).setOnClickListener {
            mese = mese.plusMonths(1)
            renderMovimenti()
        }
        vista<View>(R.id.btnOggi).setOnClickListener {
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
        Notif.osservatore = null
    }

    override fun onResume() {
        super.onResume()
        controllaFineMese()
        Avvisi.pianifica(applicationContext)
        if (pagina == Pagina.NOTIFICHE) renderLista()
        if (Sync.daAutoSync(this)) eseguiSync(true)
    }

    override fun onStart() {
        super.onStart()
        Notif.osservatore = { runOnUiThread { if (pagina == Pagina.NOTIFICHE || pagina == Pagina.HOME) refresh() } }
        if (Repo.lockOn && sbloccata && ultimoStop > 0 &&
            SystemClock.elapsedRealtime() - ultimoStop > 30_000
        ) {
            sbloccata = false
            mostraBlocco(true)
            chiediAutenticazione()
        }
    }

    private fun mostraBlocco(si: Boolean) {
        vista<View>(R.id.lockOverlay).visibility = if (si) View.VISIBLE else View.GONE
    }

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

    private fun cambiaBlocco() {
        if (Repo.lockOn) {
            Repo.setLock(false)
        } else if (Lock.disponibile(this)) {
            Repo.setLock(true)
            sbloccata = true
        } else {
            aviso("Imposta prima un'impronta o un blocco schermo sul telefono")
        }
        renderHome()
    }

    private fun mostra(p: Pagina) {
        pagina = p
        vista<View>(R.id.pageHome).visibility = if (p == Pagina.HOME) View.VISIBLE else View.GONE
        vista<View>(R.id.pageMovimenti).visibility = if (p == Pagina.MOVIMENTI) View.VISIBLE else View.GONE
        val lista = p == Pagina.CONTI || p == Pagina.CATEGORIE || p == Pagina.ANALISI || p == Pagina.TARGET || p == Pagina.RICORRENZE || p == Pagina.CALENDARIO || p == Pagina.FINANZIAMENTI || p == Pagina.AUTOPARK || p == Pagina.IMMOBIL || p == Pagina.PET || p == Pagina.FONDO || p == Pagina.GRAFICI || p == Pagina.PIANIFICA || p == Pagina.DOCUMENTI || p == Pagina.DOCPERS || p == Pagina.UTENZE || p == Pagina.FAIRSHARE || p == Pagina.CHECKOUT || p == Pagina.RICERCA || p == Pagina.PREVISIONE || p == Pagina.PROMEMORIA || p == Pagina.NOTIFICHE || p == Pagina.CASACARE || p == Pagina.PENSIONE || p == Pagina.INFO || p == Pagina.ESTRATTI || p == Pagina.CHIEDI
        vista<View>(R.id.pageLista).visibility = if (lista) View.VISIBLE else View.GONE
        when (p) {
            Pagina.HOME -> renderHome()
            Pagina.MOVIMENTI -> renderMovimenti()
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
        vista<TextView>(R.id.rEntrate).text = euro(entrate)
        vista<TextView>(R.id.rUscite).text = euro(uscite)
        val tvSaldo = vista<TextView>(R.id.rSaldo)
        tvSaldo.text = (if (saldo >= 0) "+" else "−") + euro(abs(saldo))
        tvSaldo.setTextColor(ContextCompat.getColor(this, if (saldo >= 0) R.color.oc_green else R.color.oc_red))

        val cont = vista<LinearLayout>(R.id.groups)
        cont.removeAllViews()
        aggiornaGruppi.clear()
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
                Tessera("🔔", "Promemoria" + Prom.conteggio().totale.let { if (it > 0) " ($it)" else "" }) { mostra(Pagina.PROMEMORIA) },
                Tessera("📩", "Notifiche" + Notif.nAttesa().let { if (it > 0) " ($it)" else "" }) { mostra(Pagina.NOTIFICHE) },
                Tessera("🔎", "Cerca Operazioni") { mostra(Pagina.RICERCA) },
            )
        )
        gruppo(
            cont, "Analisi", "📊",
            listOf(
                Tessera("📊", "Grafici") { mostra(Pagina.GRAFICI) },
                Tessera("🔍", "Analisi Categorie") { mostra(Pagina.ANALISI) },
                Tessera("🔁", "Checkout") { mostra(Pagina.CHECKOUT) },
                Tessera("📉", "Proiezione Mese") { mostra(Pagina.PREVISIONE) },
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
                Tessera("🔁", "Ricorrenze") { mostra(Pagina.RICORRENZE) },
                Tessera("💶", "Finanziamenti") { mostra(Pagina.FINANZIAMENTI) },
                Tessera("💰", "Fondo Risparmio") { mostra(Pagina.FONDO) },
                Tessera("🏖️", "Fondo Pensione") { mostra(Pagina.PENSIONE) },
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
                Tessera("🚗", "AutoPark") { schedaAperta = null; mostra(Pagina.AUTOPARK) },
                Tessera("🏢", "ImmoBil") { schedaAperta = null; mostra(Pagina.IMMOBIL) },
                Tessera("🐾", "Pet Care") { schedaAperta = null; mostra(Pagina.PET) },
            )
        )
        gruppo(
            cont, "Strumenti", "🛠️",
            listOf(
                Tessera("🔀", "Importa / Esporta") { dialogImportaEsporta() },
                Tessera("🔗", "Sync PC: " + if (Sync.configurato(this)) "attiva" else "no") { dialogSync() },
                Tessera("🤖", "Chiave Gemini: " + if (Gemini.chiave(this).isEmpty()) "no" else "ok") { dialogChiaveGemini { refresh() } },
                Tessera("🎭", if (Repo.demoAttiva) "Esci dalla demo" else "Modalità demo") {
                    if (Repo.demoAttiva) confermaEsciDemo() else confermaEntraDemo()
                },
                Tessera("🔄", "Aggiornamenti") { cercaAggiornamenti() },
                Tessera("ℹ️", "Info e Contatti") { mostra(Pagina.INFO) },
                Tessera("🧭", "Personalizza") { dialogBarra() },
                Tessera("🔒", "Blocco App: " + if (Repo.lockOn) "attivo" else "no") { cambiaBlocco() },
                Tessera("💡", "SmartCat: " + if (Repo.smartCatOn) "attiva" else "no") {
                    Repo.setSmartCat(!Repo.smartCatOn)
                    renderHome()
                },
            )
        )
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
        Dest(Pagina.DOCUMENTI, "🧾", "Contabili"),
        Dest(Pagina.DOCPERS, "🪪", "Personali"),
        Dest(Pagina.UTENZE, "💡", "Utenze"),
        Dest(Pagina.FAIRSHARE, "🤝", "FairShare"),
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
        OcBuilder(this)
            .setCustomTitle(titoloSpiegato("Posizione " + (slot + 1) + " della barra", "Scegli la pagina da mostrare in questa posizione. Se è già nella barra, le due pagine si scambiano di posto."))
            .setItems(destinazioni.map { it.ico + "  " + it.lbl }.toTypedArray()) { _, k ->
                val nuova = destinazioni[k].pag.name
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
            (i + 1).toString() + " · " + (d?.let { it.ico + " " + it.lbl } ?: nome)
        }
        OcBuilder(this)
            .setCustomTitle(titoloSpiegato("Personalizza barra", "Tocca una posizione per cambiarne la pagina. La Home resta sempre la prima. Scorciatoia: tieni premuta un'icona direttamente nella barra."))
            .setItems(voci.toTypedArray()) { _, k -> dialogSceltaBarra(k) }
            .setNegativeButton("Chiudi", null)
            .show()
    }

    private fun gruppo(cont: LinearLayout, titolo: String, ico: String, tessere: List<Tessera>) {
        val head = layoutInflater.inflate(R.layout.item_group, cont, false)
        val tvTit = head.findViewById<TextView>(R.id.tvGroup)
        val tvFreccia = head.findViewById<TextView>(R.id.tvFreccia)
        tvTit.text = "$ico  $titolo"

        val griglia = LinearLayout(this)
        griglia.orientation = LinearLayout.VERTICAL
        griglia.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        for (coppia in tessere.chunked(3)) {
            val riga = LinearLayout(this)
            riga.orientation = LinearLayout.HORIZONTAL
            riga.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            for (t in coppia) {
                val v = layoutInflater.inflate(R.layout.item_tile, riga, false)
                v.findViewById<TextView>(R.id.tvIco).text = t.ico
                v.findViewById<TextView>(R.id.tvLbl).text = t.lbl
                v.setOnClickListener { t.azione() }
                riga.addView(v)
            }
            repeat(3 - coppia.size) {
                val vuoto = View(this)
                val lp = LinearLayout.LayoutParams(0, 1, 1f)
                lp.setMargins(8, 0, 8, 0)
                vuoto.layoutParams = lp
                riga.addView(vuoto)
            }
            griglia.addView(riga)
        }

        fun aggiorna() {
            val aperto = titolo in gruppiAperti
            griglia.visibility = if (aperto) View.VISIBLE else View.GONE
            tvFreccia.text = if (aperto) "▼" else "▶"
        }
        aggiorna()
        aggiornaGruppi.add { aggiorna() }
        head.setOnClickListener {
            val eraAperto = titolo in gruppiAperti
            gruppiAperti.clear()
            if (!eraAperto) gruppiAperti.add(titolo)
            aggiornaGruppi.forEach { it() }
        }
        val blocco = LinearLayout(this)
        blocco.orientation = LinearLayout.VERTICAL
        blocco.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        blocco.addView(head)
        blocco.addView(griglia)
        cont.addView(blocco)
    }

    private fun renderMovimenti() {
        vista<TextView>(R.id.tvMese).text = maiuscola(mese.format(FMT_MESE))
        val q = vista<TextInputEditText>(R.id.etSearch).text.toString().trim().lowercase()
        val delMese = Repo.voci().filter { YearMonth.from(it.data) == mese }
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
                    v.hashtag.takeIf { it.isNotEmpty() }?.joinToString(" ") { "#$it" },
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
        intestaLista("📉 Previsione fine mese", "🏷️  Budget categorie")
        val cont = vista<LinearLayout>(R.id.listaGen)
        val oggi = LocalDate.now()
        val es = Previsione.calcola(Repo.voci(), oggi) { Repo.budgetMensile(it) }
        val rosso = ContextCompat.getColor(this, R.color.oc_red)
        val arancio = ContextCompat.getColor(this, R.color.oc_orange)
        val verde = ContextCompat.getColor(this, R.color.oc_green)
        fun pct(v: Double) = String.format(Locale.ITALY, "%.0f", v) + "%"

        sezione(cont, maiuscola(oggi.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ITALY))).uppercase())
        if (es.righe.isEmpty()) {
            vuoto(cont, "Nessuna uscita registrata questo mese: niente da prevedere.")
            previsioneAnno(cont, oggi)
            return
        }
        riga(
            cont, "Uscite previste a fine mese",
            "Speso finora " + euro(es.totSpeso) + " · giorno " + es.giorno + " di " + es.giorniMese,
            euro(es.totPrevisione), null, null
        )
        if (es.totBudget > 0) {
            val sforo = es.previstoConBudget > es.totBudget
            riga(
                cont, "Categorie con budget",
                "Speso " + euro(es.spesoConBudget) + " · previsione " + euro(es.previstoConBudget) +
                    " · budget " + euro(es.totBudget),
                pct(es.spesoConBudget / es.totBudget * 100.0), if (sforo) rosso else verde, null
            )
        }

        sezione(cont, "PER CATEGORIA (tocca per vedere le operazioni)")
        for (r in es.righe) {
            val sotto = if (r.budget > 0)
                "Speso " + euro(r.speso) + " · budget " + euro(r.budget) + " (" + pct(r.speso / r.budget * 100.0) + ")" +
                    (if (r.previsione > r.budget) " · oltre budget a fine mese" else "")
            else "Speso " + euro(r.speso) + " · senza budget"
            val col = when {
                r.budget > 0 && r.previsione > r.budget -> rosso
                r.budget > 0 && r.previsione >= r.budget * 0.8 -> arancio
                else -> null
            }
            riga(cont, r.categoria, sotto, (if (r.budget > 0 && r.previsione > r.budget) "▲ " else "") + euro(r.previsione), col) {
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
            "Stima lineare per categoria: speso fino a oggi ÷ giorno " + es.giorno + " × " + es.giorniMese +
                " giorni, mai sotto quanto già registrato (comprese le operazioni future del mese)." +
                (if (es.giorno <= 3) " Nei primi giorni del mese la stima è poco affidabile." else "")
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
        sezione(cont, "PREVISIONE FINE ANNO · " + oggi.year)
        val proiez = an.proiezione
        if (proiez == null) {
            riga(cont, "Registrato nell'anno", "Nessun mese chiuso: la proiezione parte da febbraio.", euro(an.totale), null, null)
        } else {
            riga(
                cont, "Uscite previste a fine anno",
                "Registrato " + euro(an.totale) + " · media mesi chiusi " + euro(an.media ?: 0.0) + "/mese",
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
            val stima = proiez != null && m.mese >= oggi.monthValue
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
                m.mese == oggi.monthValue && stima -> "In corso · registrato " + euro(m.uscite)
                m.futuro && stima -> "Stima" + if (m.uscite > 0) " · già registrato " + euro(m.uscite) else ""
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
            "Proiezione: mesi chiusi + per ogni mese restante il maggiore tra il registrato e la media dei mesi chiusi. " +
                "Il segno ~ indica una stima."
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
            Pagina.GRAFICI -> renderGrafici()
            Pagina.PIANIFICA -> renderPianifica()
            Pagina.DOCUMENTI -> renderDocumenti()
            Pagina.DOCPERS -> renderDocPers()
            Pagina.UTENZE -> renderUtenze()
            Pagina.FAIRSHARE -> renderFairshare()
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
        vista<LinearLayout>(R.id.listaGen).removeAllViews()
    }

    private fun testoTotali(entrate: Double, uscite: Double, saldo: Double): String {
        val nb = '\u00A0'
        fun u(t: String) = t.replace(' ', nb)
        return u("Entrate " + euro(entrate)) + nb + "· " +
            u("Uscite " + euro(uscite)) + "\n" +
            u("Saldo " + (if (saldo >= 0) "+" else "−") + euro(abs(saldo)))
    }

    private fun sezione(cont: LinearLayout, testo: String) {
        val t = TextView(this)
        t.text = testo
        t.setTextColor(ContextCompat.getColor(this, R.color.oc_text_mid))
        t.textSize = 12f
        t.setTypeface(null, Typeface.BOLD)
        t.setPadding(4, 14, 4, 6)
        cont.addView(t)
    }

    private fun vuoto(cont: LinearLayout, testo: String) {
        val t = TextView(this)
        t.text = testo
        t.setTextColor(ContextCompat.getColor(this, R.color.oc_text_mid))
        t.setPadding(8, 8, 8, 8)
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
        "🕒 Distribuzione", "⚠️ Budget superato", "🎯 Target", "⚖️ 50/30/20"
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
                .setItems(AN_SCHEDE.mapIndexed { i, t -> (if (i == catTab) "● " else "○ ") + t }.toTypedArray()) { _, i ->
                    catTab = i
                    renderAnalisi()
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
            else -> tab503020(cont)
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
        t.setPadding(8, 8, 8, 8)
        cont.addView(t)
    }

    private fun anBarra(cont: LinearLayout, etichetta: String, valore: Double, max: Double, colore: Int, testo: String) {
        val dp = resources.displayMetrics.density
        val r = LinearLayout(this)
        r.orientation = LinearLayout.VERTICAL
        r.setPadding(8, 6, 8, 6)
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
        sezione(cont, "DISTRIBUZIONE PER " + anDist.uppercase() + " · " + nome.uppercase())
        if (totN == 0) {
            anTesto(cont, "Nessun movimento nel periodo.", colore = medio)
            return
        }
        anGrafico(cont, elenco.map { it.first.take(5) }, elenco.map { it.second.e }, elenco.map { it.second.u }, verde, rosso) { i ->
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
        val rosso = ContextCompat.getColor(this, R.color.oc_red)
        val verde = ContextCompat.getColor(this, R.color.oc_green)
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
            riga(
                cont, (if (aperto) "▼ " else "▶ ") + titolo + " · " + maiuscola(ym.format(FMT_MESE)) + " (" + gruppo.size + ")",
                "", "", null
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
                    if (nOp == 0) "nessuna operazione" else "$nOp operazioni",
                    if (bm > 0) "Budget " + euro(bm) + "/mese" else null,
                    if (ba > 0) euro(ba) + "/anno" else null,
                    classe.takeIf { it.isNotBlank() },
                    if (classe == "Risparmio") conti[Repo.contoRisparmio(cat)]?.let { "→ $it" } else null,
                ).joinToString(" · ")
                val tot = totali[cat] ?: 0.0
                riga(
                    cont, cat, sotto, if (tot > 0) euro(tot) else "",
                    if (tipo == "Entrata") verde else rosso
                ) { dialogCategoria(cat) }
            }
        }
    }

    private fun menuStrumentiCategorie() {
        OcBuilder(this)
            .setTitle("Strumenti categorie")
            .setItems(
                arrayOf(
                    "➕  Nuova categoria", "🔎  Cerca, ordina e filtra", "🔀  Sposta o unisci categorie",
                    "🗑️  Elimina categorie vuote", "🔍  Apri Analisi categorie"
                )
            ) { _, k ->
                when (k) {
                    0 -> dialogCategoria(null)
                    1 -> dialogCercaCategorie()
                    2 -> dialogSpostaCategorie()
                    3 -> dialogEliminaVuote()
                    else -> mostra(Pagina.ANALISI)
                }
            }
            .setNegativeButton("Chiudi", null)
            .show()
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

        sezione(cont, "TARGET DI SPESA")
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
            sezione(cont, "BUDGET MENSILI PER CATEGORIA")
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
            sezione(cont, "BUDGET ANNUALI PER CATEGORIA")
            val sfA = conBudgetA.count { (spesoA[it] ?: 0.0) > Repo.budgetAnnuo(it) }
            if (sfA > 0) vuoto(cont, "⚠️ " + sfA + (if (sfA == 1) " categoria sopra budget" else " categorie sopra budget") + " quest'anno")
            for (c in conBudgetA) {
                barra(cont, c, usciteAnno.filter { it.categoria == c }.sumOf { it.importo }, Repo.budgetAnnuo(c), "")
            }
        }

        val q = Repo.calcola503020(Repo.voci().filter { YearMonth.from(it.data) == ym })
        if (q.entrate > 0 && (q.bisogni + q.desideri + q.risparmio) > 0) {
            sezione(cont, "METODO 50/30/20 · entrate " + euro(q.entrate))
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

    private fun dialogRicorrenza() {
        val periodi = listOf("Ogni giorno", "Ogni mese", "Ogni anno")
        var periodo = "Ogni mese"
        var tipo = "Uscita"
        var data = LocalDate.now()
        var metodo = ""
        var conto = Repo.contoPrincipale()?.nome ?: ""

        fun categorieTipo(): List<String> {
            val base = Repo.categorie().filter { Repo.tipoCategoria(it) == tipo }
            return base.ifEmpty { Repo.categorie() }
        }
        var cat = categorieTipo().firstOrNull() ?: "Generica"

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

        val etImp = campoTesto(corpo, "Importo (€)", "", TIPO_DECIMALE, 12)
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
        grp.check(bU.id)

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
        val etDescr = campoTesto(corpo, "Descrizione", "", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)

        val nomiMetodi = listOf("") + Repo.METODI.map { it.first }
        campoMenu(corpo, "Metodo di pagamento", nomiMetodi.map { if (it.isBlank()) "Nessuno" else Repo.metodoConEmoji(it) }, "Nessuno") {
            metodo = nomiMetodi[it]
        }
        val nomiConti = listOf("") + Repo.conti().map { it.nome }
        if (nomiConti.size > 1) {
            campoMenu(corpo, "Conto", nomiConti.map { if (it.isBlank()) "Nessuno" else it }, if (conto.isBlank()) "Nessuno" else conto) {
                conto = nomiConti[it]
            }
        } else conto = ""
        val etTag = campoTesto(corpo, "Tag (separati da spazio)", "", InputType.TYPE_CLASS_TEXT)

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
        val nomiVista = listOf("📈 Andamento", if (grafSempre) "📊 Annuale" else "📊 Mensile", "🧾 Spese")
        nomiVista.forEachIndexed { i, t ->
            val b = pulsanteSel(t, grafVista == i) { grafVista = i; renderGrafici() }
            b.maxLines = 1
            b.setPadding((6 * dp).toInt(), b.paddingTop, (6 * dp).toInt(), b.paddingBottom)
            b.setAutoSizeTextTypeUniformWithConfiguration(10, 14, 1, android.util.TypedValue.COMPLEX_UNIT_SP)
            riga1.addView(b, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        cont.addView(riga1)

        val riga2 = LinearLayout(this)
        riga2.orientation = LinearLayout.HORIZONTAL
        riga2.gravity = android.view.Gravity.CENTER_VERTICAL
        val lpPer = { LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) }
        riga2.addView(pulsanteSel("Anno", !grafSempre) { grafSempre = false; renderGrafici() }, lpPer())
        riga2.addView(pulsanteSel("Sempre", grafSempre) { grafSempre = true; renderGrafici() }, lpPer())
        if (!grafSempre) {
            val i = anni.indexOf(grafAnno)
            val larg = (44 * dp).toInt()
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
            riga2.addView(lbl, LinearLayout.LayoutParams((48 * dp).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT))
            riga2.addView(next, LinearLayout.LayoutParams(larg, (40 * dp).toInt()))
        }
        cont.addView(riga2)

        if (voci.isEmpty()) {
            vuoto(cont, "Nessuna operazione" + if (grafConto.isNotEmpty()) " su questo conto." else ".")
            return
        }

        val serie = if (grafSempre) Stat.storico(voci) else Stat.mensile(voci, grafAnno)

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
            g.alSelezionare = { i -> det.text = dettaglio(i); det.setTextColor(ContextCompat.getColor(this, R.color.oc_text)) }
            det.minLines = 3
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
            else -> {
                val righe = Stat.categorie(voci, if (grafSempre) null else grafAnno)
                val tot = righe.sumOf { it.v }
                riga(cont, "Uscite totali", "", eur0(tot), rosso, null)
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
        intestaLista("💳 Estratti per metodo e conti", "", "📤  Condividi")
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
            val tag = if (v.hashtag.isNotEmpty()) v.hashtag.joinToString(" ") { "#$it" } else ""
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

    private class ChiediMsg(val domanda: String, var risposta: String?, var errore: Boolean = false)

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
                "finanziamenti e gli ultimi movimenti con la descrizione. Le risposte sono indicative.",
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
        val inv = pulsanteSel(if (chiediInCorso) "⏳ Sto pensando…" else "➤ Chiedi", true) { invioChiedi() }
        inv.isEnabled = !chiediInCorso
        cont.addView(inv)

        if (chiediStorico.isEmpty()) {
            vuoto(cont, "Puoi chiedere totali e confronti (\"spese di settembre rispetto a agosto\"), dettagli per conto o categoria, " +
                "stato dei finanziamenti, budget sforati, oppure consigli su come risparmiare.")
            return
        }
        val dp = resources.displayMetrics.density
        for (m in chiediStorico.asReversed()) {
            anTesto(cont, "❓ " + m.domanda, grassetto = true)
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
        val prompt = try {
            ChiediDb.prompt(d, cronologia)
        } catch (e: Exception) {
            aviso("Impossibile preparare i dati: " + e.message)
            return
        }
        val m = ChiediMsg(d, null)
        chiediStorico.add(m)
        chiediBozza = ""
        chiediInCorso = true
        renderChiedi()
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
                if (v.hashtag.isNotEmpty()) dettaglio.add(v.hashtag.joinToString(" ") { "#$it" })
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
                sezione(cont, "CATEGORIE ABITUALI NON ANCORA REGISTRATE QUESTO MESE")
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
                    val tv = TextView(this)
                    tv.typeface = Typeface.MONOSPACE
                    tv.textSize = 11f
                    tv.setTextColor(ContextCompat.getColor(this, R.color.oc_text_mid))
                    tv.setPadding(28, 0, 8, 10)
                    val l1 = StringBuilder()
                    val l2 = StringBuilder()
                    for (i in 0 until 12) {
                        val cur = ckAnno == oggi.year && i + 1 == oggi.monthValue
                        val sim = if (r.mesi[i]) "✔" else "✖"
                        l1.append(if (cur) "[" + lettere[i] + "]" else " " + lettere[i] + " ")
                        l2.append(if (cur) "[$sim]" else " $sim ")
                    }
                    tv.text = l1.toString() + "\n" + l2.toString()
                    cont.addView(tv)
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

        sezione(cont, "RIEPILOGO")
        riga(cont, "Piani attivi", "importo totale " + euro(totImp), info.size.toString(), null, null)
        riga(cont, "Da accantonare questo mese", "", euro(totOra), rosso, null)
        riga(cont, "Già accantonato", "", euro(totAcc), verde, null)
        riga(cont, "Residuo", "", euro(totImp - totAcc), null, null)

        sezione(cont, "PIANI")
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

        sezione(cont, "ULTIMI 365 GIORNI")
        riga(cont, "Entrate", "media " + euro(a.mediaE) + "/mese", euro(a.entrate365), verde, null)
        riga(cont, "Uscite", "media " + euro(a.mediaU) + "/mese", euro(a.uscite365), rosso, null)
        riga(cont, "Risparmio", "media " + euro(a.mediaR) + "/mese", euro(a.risparmio365), col(a.risparmio365), null)

        sezione(cont, "FONDO DI EMERGENZA")
        riga(cont, "Fondo attuale", "Spesa media " + euro(a.mediaU) + "/mese", euro(fondo), null, { dialogImportiFondo() })
        barraFondo(cont, "Obiettivo 3 mesi", fondo, a.fe3)
        barraFondo(cont, "Obiettivo 6 mesi", fondo, a.fe6)
        barraFondo(cont, "Obiettivo 12 mesi", fondo, a.fe12)

        sezione(cont, "OBIETTIVO ANNUALE")
        if (annuale > 0) {
            barraFondo(cont, "Risparmio 365 gg", a.risparmio365, annuale)
            val diff = if (a.diffObj >= 0) "Sei in linea (+" + euro(a.diffObj) + "/mese)" else "Mancano " + euro(-a.diffObj) + "/mese"
            riga(cont, "Rata mensile necessaria", diff, euro(a.rataMensile), col(a.diffObj), null)
        } else {
            vuoto(cont, "Nessun obiettivo annuale: impostalo da \"Imposta fondo\".")
        }

        sezione(cont, "OBIETTIVI DI RISPARMIO")
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

        sezione(cont, "PROIEZIONE " + LocalDate.now().year)
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

        sezione(cont, "ANDAMENTO ULTIMI 12 MESI")
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
            val err = Fondo.salvaImporti(o, f)
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
        val azioni = listOf("➕  Nuovo versamento", "✏️  Modifica piano", "➕  Nuovo versamento", "➕  Nuova valorizzazione", "➕  Nuovo riscatto")
        intestaLista("🏖️ Fondo Pensione", azioni[penVista], if (penVista in 2..4) "📄  Esporta PDF" else null)
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
            sezione(corpo, titolo)
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
        val daEsp = movs.count { !it.esportato }
        val saldo = movs.sumOf { if (it.entrata) -it.imp else it.imp }
        riga(
            cont, if (s.cfg.tipoMov) "Saldo (uscite − entrate)" else "Totale spese",
            if (daEsp > 0) "$daEsp da esportare nei movimenti" else "Tutte esportate",
            euro(saldo), rosso, null
        )
        for (m in movs) {
            val sotto = (listOfNotNull(
                m.data?.let { s.dataIt(it) },
                m.desc.takeIf { it.isNotBlank() },
            ) + s.cfg.extra.mapNotNull { e ->
                m.extra[e.k]?.takeIf { it > 0 }?.let { s.fmtN(it) + " " + e.unita }
            }).joinToString(" · ")
            riga(cont, (if (m.esportato) "✅ " else "") + m.cat, sotto, euro(m.imp), if (m.entrata) verde else rosso) { dialogMovScheda(v, m) }
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
        val titolo = TextView(this)
        titolo.text = maiuscola(meseCal.format(FMT_MESE))
        titolo.gravity = android.view.Gravity.CENTER
        titolo.setTypeface(null, Typeface.BOLD)
        titolo.textSize = 16f
        titolo.setOnClickListener {
            meseCal = YearMonth.now()
            giornoCal = oggi
            renderCalendario()
        }
        nav.addView(prev)
        nav.addView(titolo, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
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
                    v.hashtag.takeIf { it.isNotEmpty() }?.joinToString(" ") { "#$it" },
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
                            .setMessage("Installata: $attuale. Scaricare l'aggiornamento dal browser?")
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
                val r = Aggiorna.ultima() ?: return@Thread
                val attuale = Aggiorna.versioneInstallata(this)
                if (!Aggiorna.piuRecente(r.tag, attuale)) return@Thread
                val prefs = getSharedPreferences("aggiornamenti", MODE_PRIVATE)
                if (prefs.getString("rimandata", null) == r.tag) return@Thread
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    OcBuilder(this)
                        .setTitle("Nuova versione " + r.tag)
                        .setMessage("Installata: $attuale. Scaricare l'aggiornamento dal browser?")
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
        try {
            startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(r.apkUrl)))
        } catch (e: Exception) {
            aviso("Nessun browser disponibile per scaricare l'aggiornamento")
        }
    }

    private fun dialogSync() {
        if (Repo.demoAttiva) {
            aviso("La sincronizzazione non è disponibile in modalità demo")
            return
        }
        val (view, corpo) = corpoDialogo()
        val info = TextView(ctxDialogo)
        info.text = "Sincronizza movimenti, categorie, archivi e documenti con OrbitaCasa sul PC, " +
            "nella rete di casa. Indirizzo e token sono quelli della Configurazione sul PC " +
            "(il token è lo stesso del webhook). Se un dato cambia su entrambi, vince il PC " +
            "e la copia del telefono resta in una cartella di sicurezza."
        info.setPadding(4, 4, 4, 12)
        corpo.addView(info)
        val etUrl = campoTesto(
            corpo, "Indirizzo del PC (es. https://192.168.1.20:8081)", Sync.indirizzo(this),
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
        val tvGem = TextView(ctxDialogo)
        tvGem.text = "🤖 Analisi automatica (Gemini)"
        tvGem.setPadding(4, 16, 4, 4)
        corpo.addView(tvGem)
        val (etGemK, etGemM) = campiGemini(corpo)
        val chk = MaterialCheckBox(this)
        chk.text = "Sincronizza all'apertura dell'app"
        chk.isChecked = Sync.auto(this)
        corpo.addView(chk)
        val stato = TextView(ctxDialogo)
        val ult = Sync.ultima(this)
        stato.text = (
            if (ult > 0) "Ultima sincronizzazione: " +
                java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.ITALY).format(java.util.Date(ult))
            else "Mai sincronizzato"
            ) + if (Sync.improntaFissata(this).isNotEmpty()) "\nCertificato del PC memorizzato" else ""
        stato.setPadding(4, 8, 4, 4)
        corpo.addView(stato)

        OcBuilder(this)
            .setTitle("🔗 Sync con il PC")
            .setView(view)
            .setPositiveButton("Sincronizza") { _, _ ->
                val gk = etGemK.text.toString().trim()
                if (gk.isNotEmpty() && (gk != Gemini.chiave(this) || etGemM.text.toString().trim() != Gemini.modello(this))) {
                    Gemini.salva(this, gk, etGemM.text.toString())
                }
                avviaSync(etUrl.text.toString(), etTok.text.toString(), chk.isChecked)
            }
            .setNeutralButton("Scollega") { _, _ ->
                Sync.scollega(this)
                aviso("Sincronizzazione scollegata")
                refresh()
            }
            .setNegativeButton("Chiudi", null)
            .show()
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

    private fun avviaSync(indirizzo: String, token: String, auto: Boolean) {
        val u = Sync.normalizzaUrl(indirizzo)
        if (u == null) {
            aviso("Indirizzo non valido: serve https:// e l'indirizzo del PC")
            return
        }
        if (token.isBlank()) {
            aviso("Inserisci il token")
            return
        }
        Sync.salva(this, u, token, auto)
        if (Sync.improntaFissata(this).isEmpty()) {
            aviso("Controllo il certificato del PC...")
            thread {
                try {
                    val imp = Sync.leggiImpronta(u)
                    runOnUiThread { if (!isFinishing && !isDestroyed) chiediFiducia(imp) }
                } catch (e: Exception) {
                    runOnUiThread { aviso("PC non raggiungibile: " + e.message) }
                }
            }
            return
        }
        eseguiSync(false)
    }

    private fun chiediFiducia(impronta: String) {
        OcBuilder(this)
            .setTitle("Certificato del PC")
            .setMessage(
                "Impronta SHA-256:\n" + Sync.formatta(impronta) + "\n\n" +
                    "È il certificato autofirmato di OrbitaCasa sul tuo PC? Se ti fidi lo ricordo " +
                    "e da ora accetto solo questo: se cambia, la sincronizzazione si ferma."
            )
            .setNegativeButton("Annulla", null)
            .setPositiveButton("Mi fido") { _, _ ->
                Sync.fissaImpronta(this, impronta)
                eseguiSync(false)
            }
            .show()
    }

    private fun eseguiSync(auto: Boolean) {
        if (!auto) aviso("Sincronizzazione in corso...")
        Sync.sincronizza(this) { e ->
            if (isFinishing || isDestroyed) return@sincronizza
            if (e.modificato || !auto) refresh()
            if (auto) {
                if (e.ok && e.modificato) aviso("Sync PC: " + e.righe.first())
            } else {
                OcBuilder(this)
                    .setTitle(if (e.ok) "✅ Sync con il PC" else "Sync non riuscita")
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

        val originale = (p.titolo + "\n" + p.testo).trim().take(300)
        val dlg = OcBuilder(this)
            .setTitle("Conferma la spesa")
            .setMessage(originale)
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
