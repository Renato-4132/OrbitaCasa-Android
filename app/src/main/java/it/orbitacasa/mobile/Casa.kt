package it.orbitacasa.mobile

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

object Casa {
    const val FILE = "manutenzione_casa.json"
    private const val SOGLIA_ROSSA = 15
    private const val SOGLIA_AMBRA = 45
    private val FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy")

    private lateinit var file: File

    var errore: String? = null
        private set

    fun init(dir: File) {
        file = File(dir, FILE)
    }

    val CATEGORIE = listOf(
        "Impianto Idrico",
        "Rubinetti e Docce",
        "Buone Abitudini",
        "Lavatrice",
        "Lavastoviglie",
        "Ferro da Stiro",
        "Frigorifero e Congelatore",
        "Scaldabagno / Caldaia",
        "Riscaldamento e Camino",
        "Climatizzazione",
        "Ventilazione (VMC) e Cappa",
        "Esterni e Tetto",
        "Automazioni",
        "Sicurezza",
        "Monitoraggio Consumi",
        "Dispensa e Alimenti",
        "Varie",
    )

    const val PERSONALIZZATA = "Personalizzata (gg)"
    val FREQ: List<Pair<String, Int?>> = listOf(
        "Ogni settimana" to 7,
        "Ogni 2 settimane" to 14,
        "Ogni mese" to 30,
        "Ogni 2 mesi" to 60,
        "Ogni 3 mesi" to 90,
        "Ogni 6 mesi" to 182,
        "Ogni anno" to 365,
        "Ogni 2 anni" to 730,
        "Ogni 5 anni" to 1825,
        "Personalizzata (gg)" to null,
    )

    private class Def(val c: String, val n: String, val g: Int, val l: String, val legge: Boolean)
    private val DEF = listOf(
        Def("Impianto Idrico", "Pulizia filtro raccoglitore centralizzato", 90, "Ogni 3 mesi", false),
        Def("Impianto Idrico", "Controllo livello sale addolcitore", 30, "Ogni mese", false),
        Def("Impianto Idrico", "Pulizia filtro aspirazione autoclave (case con pozzo)", 90, "Ogni 3 mesi", false),
        Def("Impianto Idrico", "Controllo tubature esposte al gelo (inizio inverno)", 365, "Ogni anno", false),
        Def("Impianto Idrico", "Svuotamento fanghi fossa biologica/depuratore", 730, "Ogni 2 anni", false),
        Def("Rubinetti e Docce", "Pulizia frangigetto (aeratori) rubinetti", 60, "Ogni 2 mesi", false),
        Def("Rubinetti e Docce", "Pulizia soffione doccia", 30, "Ogni mese", false),
        Def("Buone Abitudini", "Versare acqua negli scarichi poco usati (sifone)", 14, "Ogni 2 settimane", false),
        Def("Buone Abitudini", "Pulizia preventiva scarichi (bicarbonato/aceto)", 30, "Ogni mese", false),
        Def("Buone Abitudini", "Spolvero pareti (angoli e zone dietro i mobili)", 30, "Ogni mese", false),
        Def("Lavatrice", "Pulizia filtro di scarico (pompa)", 60, "Ogni 2 mesi", false),
        Def("Lavatrice", "Pulizia filtro ingresso acqua + rubinetto", 182, "Ogni 6 mesi", false),
        Def("Lavatrice", "Lavaggio a vuoto ad alta temperatura (90°, anticalcare)", 60, "Ogni 2 mesi", false),
        Def("Lavatrice", "Pulizia guarnizione oblò", 7, "Ogni settimana", false),
        Def("Lavatrice", "Pulizia vaschetta detersivo/ammorbidente", 30, "Ogni mese", false),
        Def("Lavastoviglie", "Pulizia filtro fondo vasca", 14, "Ogni 2 settimane", false),
        Def("Lavastoviglie", "Controllo sale rigenerante", 30, "Ogni mese", false),
        Def("Lavastoviglie", "Ciclo a vuoto ad alta temperatura (anticalcare)", 30, "Ogni mese", false),
        Def("Lavastoviglie", "Pulizia braccetti irroratori", 30, "Ogni mese", false),
        Def("Lavastoviglie", "Pulizia guarnizione sportello", 14, "Ogni 2 settimane", false),
        Def("Ferro da Stiro", "Pulizia piastra", 30, "Ogni mese", false),
        Def("Ferro da Stiro", "Decalcificazione serbatoio/caldaia interna", 30, "Ogni mese", false),
        Def("Frigorifero e Congelatore", "Pulizia serpentina/griglia posteriore (condensatore)", 182, "Ogni 6 mesi", false),
        Def("Frigorifero e Congelatore", "Pulizia vaschetta raccolta condensa", 60, "Ogni 2 mesi", false),
        Def("Frigorifero e Congelatore", "Pulizia canalina/foro scarico condensa interno", 90, "Ogni 3 mesi", false),
        Def("Frigorifero e Congelatore", "Pulizia interna (ripiani, cassetti, guarnizioni)", 30, "Ogni mese", false),
        Def("Frigorifero e Congelatore", "Controllo tenuta guarnizione sportello", 182, "Ogni 6 mesi", false),
        Def("Scaldabagno / Caldaia", "Pulizia filtro defangatore circuito riscaldamento", 365, "Ogni anno", false),
        Def("Scaldabagno / Caldaia", "Pulizia filtro impurità ingresso caldaia (lato sanitario)", 182, "Ogni 6 mesi", false),
        Def("Scaldabagno / Caldaia", "Controllo/sostituzione anodo di magnesio", 730, "Ogni 2 anni", false),
        Def("Scaldabagno / Caldaia", "Azionamento manuale valvola di sicurezza/sfiato", 90, "Ogni 3 mesi", false),
        Def("Scaldabagno / Caldaia", "Manutenzione completa caldaia a gas (tecnico abilitato)", 365, "Ogni anno", true),
        Def("Riscaldamento e Camino", "Pulizia cenere/condotto stufa a pellet (uso stagionale)", 60, "Ogni 2 mesi", false),
        Def("Riscaldamento e Camino", "Pulizia canna fumaria (spazzacamino abilitato)", 365, "Ogni anno", true),
        Def("Riscaldamento e Camino", "Sfiato aria dai radiatori (inizio stagione)", 365, "Ogni anno", false),
        Def("Climatizzazione", "Pulizia filtri aria climatizzatore (stagione d'uso)", 30, "Ogni mese", false),
        Def("Climatizzazione", "Controllo/pulizia unità esterna (foglie, detriti)", 182, "Ogni 6 mesi", false),
        Def("Climatizzazione", "Manutenzione con carica gas (tecnico frigorista)", 365, "Ogni anno", false),
        Def("Ventilazione (VMC) e Cappa", "Pulizia filtri VMC", 90, "Ogni 3 mesi", false),
        Def("Ventilazione (VMC) e Cappa", "Sostituzione filtri VMC", 365, "Ogni anno", false),
        Def("Ventilazione (VMC) e Cappa", "Pulizia filtro antigrasso cappa cucina", 30, "Ogni mese", false),
        Def("Ventilazione (VMC) e Cappa", "Sostituzione filtro carboni attivi cappa", 182, "Ogni 6 mesi", false),
        Def("Esterni e Tetto", "Pulizia gronde e pluviali", 365, "Ogni anno", false),
        Def("Esterni e Tetto", "Controllo guaina impermeabilizzante terrazzi/balconi", 365, "Ogni anno", false),
        Def("Esterni e Tetto", "Pulizia pozzetti e caditoie esterne", 182, "Ogni 6 mesi", false),
        Def("Esterni e Tetto", "Pulizia pannelli fotovoltaici", 182, "Ogni 6 mesi", false),
        Def("Automazioni", "Lubrificazione guide/cinghie tapparelle e cancelli", 365, "Ogni anno", false),
        Def("Automazioni", "Verifica batteria di backup automazioni", 182, "Ogni 6 mesi", false),
        Def("Sicurezza", "Test pulsante rilevatore fughe gas", 30, "Ogni mese", false),
        Def("Sicurezza", "Verifica batteria backup rilevatore gas", 182, "Ogni 6 mesi", false),
        Def("Sicurezza", "Sostituzione sensore rilevatore gas", 1825, "Ogni 5 anni", false),
        Def("Sicurezza", "Test pulsante differenziale (salvavita)", 182, "Ogni 6 mesi", false),
        Def("Sicurezza", "Verifica dispositivo riarmo automatico frigo/congelatore", 182, "Ogni 6 mesi", false),
        Def("Sicurezza", "Test pulsante rilevatore di fumo", 30, "Ogni mese", false),
        Def("Sicurezza", "Pulizia sensore rilevatore di fumo", 182, "Ogni 6 mesi", false),
        Def("Sicurezza", "Sostituzione batteria rilevatore di fumo", 365, "Ogni anno", false),
        Def("Sicurezza", "Controllo visivo estintore (pressione, sicura)", 182, "Ogni 6 mesi", false),
        Def("Sicurezza", "Revisione estintore (tecnico abilitato)", 365, "Ogni anno", true),
        Def("Sicurezza", "Test pulsante rilevatore monossido di carbonio (CO)", 30, "Ogni mese", false),
        Def("Sicurezza", "Sostituzione sensore rilevatore CO", 1825, "Ogni 5 anni", false),
        Def("Monitoraggio Consumi", "Lettura contatori acqua/luce/gas", 30, "Ogni mese", false),
        Def("Monitoraggio Consumi", "Controllo ripartizione consumi (STAND BY/HIGH/OTHER)", 60, "Ogni 2 mesi", false),
        Def("Monitoraggio Consumi", "Test perdite d'acqua (contatore a tutto chiuso)", 90, "Ogni 3 mesi", false),
        Def("Monitoraggio Consumi", "Test cassetta WC con colorante alimentare", 182, "Ogni 6 mesi", false),
        Def("Dispensa e Alimenti", "Controllo dispensa per tarme/infestanti", 90, "Ogni 3 mesi", false),
    )

    private fun nuovoId(): String = UUID.randomUUID().toString().replace("-", "").substring(0, 12)

    private val predefinite: JSONArray by lazy {
        val a = JSONArray()
        for (d in DEF) {
            a.put(
                JSONObject().put("id", nuovoId()).put("categoria", d.c).put("nome", d.n)
                    .put("freq_giorni", d.g).put("freq_label", d.l).put("ultima", "")
                    .put("note", "").put("legge", d.legge)
            )
        }
        a
    }

    private fun leggi(): JSONObject {
        errore = null
        if (!file.exists() || file.length() == 0L) return JSONObject().put("voci", predefinite)
        return try {
            val o = JSONObject(file.readText(Charsets.UTF_8))
            if (o.optJSONArray("voci") == null) o.put("voci", JSONArray())
            o
        } catch (e: Exception) {
            errore = "$FILE risulta illeggibile o corrotto: nessuna modifica effettuata"
            JSONObject().put("voci", JSONArray())
        }
    }

    private fun scrivi(o: JSONObject) {
        val tmp = File(file.parentFile, "$FILE.tmp")
        tmp.writeText(o.toString(2), Charsets.UTF_8)
        Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }

    private fun modifica(blocco: (JSONArray) -> String?): String? {
        val o = leggi()
        errore?.let { return it }
        val err = blocco(o.getJSONArray("voci"))
        if (err == null) scrivi(o)
        return err
    }

    private fun trova(a: JSONArray, id: String): JSONObject? =
        (0 until a.length()).mapNotNull { a.optJSONObject(it) }.firstOrNull { it.optString("id") == id }

    class Voce(
        val id: String, val categoria: String, val nome: String, val freqGiorni: Int,
        val freqLabel: String, val ultima: String, val note: String, val legge: Boolean,
    )

    class Stato(val testo: String, val tag: String, val giorni: Int?)

    fun voci(): List<Voce> {
        val a = leggi().getJSONArray("voci")
        return (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            val g = o.optInt("freq_giorni", 0).takeIf { it > 0 } ?: o.optString("freq_giorni").toIntOrNull() ?: 0
            Voce(
                o.optString("id"), o.optString("categoria", "Varie"), o.optString("nome"), g,
                o.optString("freq_label"), o.optString("ultima"), o.optString("note"), o.optBoolean("legge", false)
            )
        }
    }

    private fun data(s: String): LocalDate? = try {
        if (s.isBlank()) null else LocalDate.parse(s.trim(), FMT)
    } catch (_: Exception) {
        null
    }

    fun prossima(v: Voce): LocalDate? = data(v.ultima)?.plusDays(v.freqGiorni.toLong())

    fun stato(v: Voce, oggi: LocalDate = LocalDate.now()): Stato {
        val p = prossima(v) ?: return Stato("Mai eseguita — da pianificare", "grigio", null)
        val g = java.time.temporal.ChronoUnit.DAYS.between(oggi, p).toInt()
        val ps = p.format(FMT)
        return when {
            g < 0 -> Stato("SCADUTA da ${-g} gg ($ps)", "rosso", g)
            g == 0 -> Stato("Scade OGGI ($ps)", "rosso", g)
            g <= SOGLIA_ROSSA -> Stato("tra $g gg ($ps)", "rosso", g)
            g <= SOGLIA_AMBRA -> Stato("tra $g gg ($ps)", "ambra", g)
            else -> Stato("tra $g gg ($ps)", "verde", g)
        }
    }

    fun segnaOggi(id: String): String? = modifica { a ->
        val o = trova(a, id) ?: return@modifica "Attività non trovata."
        o.put("ultima", LocalDate.now().format(FMT))
        null
    }

    fun azzera(id: String): String? = modifica { a ->
        val o = trova(a, id) ?: return@modifica "Attività non trovata."
        o.put("ultima", "")
        null
    }

    fun elimina(id: String): String? = modifica { a ->
        val i = (0 until a.length()).firstOrNull { a.optJSONObject(it)?.optString("id") == id }
            ?: return@modifica "Attività non trovata."
        a.remove(i)
        null
    }

    fun salva(
        id: String?, categoria: String, nome: String, giorni: Int, etichetta: String,
        ultima: String, note: String, legge: Boolean,
    ): String? {
        if (nome.trim().isEmpty()) return "Il nome dell'attività non può essere vuoto."
        if (giorni <= 0) return "La frequenza deve essere un numero di giorni positivo."
        if (ultima.isNotBlank() && data(ultima) == null) return "Data non valida (gg-mm-aaaa)."
        return modifica { a ->
            val o = if (id == null) {
                JSONObject().put("id", nuovoId()).also { a.put(it) }
            } else trova(a, id) ?: return@modifica "Attività non trovata."
            o.put("categoria", categoria.trim().ifEmpty { "Varie" })
            o.put("nome", nome.trim())
            o.put("freq_giorni", giorni)
            o.put("freq_label", etichetta)
            o.put("ultima", ultima.trim())
            o.put("note", note.trim())
            o.put("legge", legge)
            null
        }
    }
}
