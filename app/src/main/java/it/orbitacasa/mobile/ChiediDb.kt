package it.orbitacasa.mobile

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Costruisce il contesto e il prompt per "Chiedi al Database" (Gemini). */
object ChiediDb {
    private val F = DateTimeFormatter.ofPattern("dd-MM-yyyy")
    private const val MAX_CONTESTO = 60_000
    private val MESI = listOf(
        "gennaio", "febbraio", "marzo", "aprile", "maggio", "giugno",
        "luglio", "agosto", "settembre", "ottobre", "novembre", "dicembre",
    )

    private fun e(x: Double) = String.format(Locale.ITALY, "%,.2f", x) + "€"

    private fun cat(v: Voce) = v.categoria.ifBlank { "Senza categoria" }

    private fun riepilogo(voci: List<Voce>, topCat: Int): String {
        if (voci.isEmpty()) return "nessun movimento"
        val ent = voci.filter { it.tipo == "Entrata" }.sumOf { it.importo }
        val usc = voci.filter { it.tipo != "Entrata" }.sumOf { it.importo }
        val sb = StringBuilder()
        sb.append("entrate ").append(e(ent)).append(", uscite ").append(e(usc))
            .append(", saldo ").append(e(ent - usc)).append(", ").append(voci.size).append(" movimenti")
        val cats = voci.filter { it.tipo != "Entrata" }
            .groupBy { cat(it) }
            .map { it.key to it.value.sumOf { v -> v.importo } }
            .sortedByDescending { it.second }
            .take(topCat)
        if (cats.isNotEmpty()) {
            sb.append("\n   categorie di uscita: ").append(cats.joinToString("; ") { it.first + " " + e(it.second) })
        }
        val conti = voci.groupBy { it.conto.ifBlank { "Non assegnato" } }
        if (conti.size > 1) {
            sb.append("\n   per conto/persona: ").append(
                conti.entries.joinToString("; ") { en ->
                    en.key + " entrate " + e(en.value.filter { it.tipo == "Entrata" }.sumOf { it.importo }) +
                        " uscite " + e(en.value.filter { it.tipo != "Entrata" }.sumOf { it.importo })
                }
            )
        }
        return sb.toString()
    }

    fun contesto(): String {
        val oggi = LocalDate.now()
        val tutti = Repo.voci()
        val passati = tutti.filter { !it.data.isAfter(oggi) }
        val sb = StringBuilder()

        val giorni = listOf("lunedì", "martedì", "mercoledì", "giovedì", "venerdì", "sabato", "domenica")
        sb.append("OGGI È ").append(oggi.format(F)).append(" (").append(giorni[oggi.dayOfWeek.value - 1]).append(")\n")
        sb.append("Movimenti registrati: ").append(tutti.size)
        if (tutti.isNotEmpty()) {
            sb.append(" dal ").append(tutti.minOf { it.data }.format(F)).append(" al ").append(tutti.maxOf { it.data }.format(F))
        }
        sb.append("\n\n")

        // --- periodi relativi
        val lunedi = oggi.with(DayOfWeek.MONDAY)
        val mp = YearMonth.from(oggi).minusMonths(1)
        val periodi = linkedMapOf(
            "oggi" to (oggi to oggi),
            "ieri" to (oggi.minusDays(1) to oggi.minusDays(1)),
            "questa settimana" to (lunedi to oggi),
            "settimana scorsa" to (lunedi.minusWeeks(1) to lunedi.minusDays(1)),
            "ultimi 30 giorni" to (oggi.minusDays(29) to oggi),
            "questo mese" to (oggi.withDayOfMonth(1) to oggi),
            "mese scorso" to (mp.atDay(1) to mp.atEndOfMonth()),
            "quest'anno" to (LocalDate.of(oggi.year, 1, 1) to oggi),
            "anno scorso" to (LocalDate.of(oggi.year - 1, 1, 1) to LocalDate.of(oggi.year - 1, 12, 31)),
        )
        sb.append("RIEPILOGO PER PERIODI TEMPORALI (movimenti fino a oggi)\n")
        for ((nome, r) in periodi) {
            val l = passati.filter { !it.data.isBefore(r.first) && !it.data.isAfter(r.second) }
            sb.append("- ").append(nome).append(" (").append(r.first.format(F)).append(" → ").append(r.second.format(F))
                .append("): ").append(riepilogo(l, 8)).append("\n")
        }

        // --- mese per mese
        sb.append("\nRIEPILOGO MESE PER MESE, CON CATEGORIE (ultimi 24 mesi con movimenti)\n")
        val perMese = passati.groupBy { YearMonth.from(it.data) }.toSortedMap(compareByDescending<YearMonth> { it }).entries.take(24)
        for ((ym, l) in perMese) {
            sb.append("- ").append(MESI[ym.monthValue - 1]).append(" ").append(ym.year).append(": ")
                .append(riepilogo(l, 6)).append("\n")
        }

        // --- ultimi 12 mesi: categorie e metodi
        val da12 = oggi.minusMonths(12)
        val u12 = passati.filter { it.data.isAfter(da12) }
        val catAnno = u12.filter { it.tipo != "Entrata" }.groupBy { cat(it) }
            .map { it.key to it.value.sumOf { v -> v.importo } }.sortedByDescending { it.second }.take(25)
        if (catAnno.isNotEmpty()) {
            sb.append("\nUSCITE PER CATEGORIA, ULTIMI 12 MESI\n")
            for ((c, t) in catAnno) sb.append("- ").append(c).append(": ").append(e(t)).append("\n")
        }
        val metodi = u12.groupBy { it.metodo.ifBlank { "Non specificato" } }
        if (metodi.isNotEmpty()) {
            sb.append("\nPER METODO DI PAGAMENTO, ULTIMI 12 MESI\n")
            for ((m, l) in metodi.entries.sortedByDescending { en -> en.value.sumOf { it.importo } }) {
                sb.append("- ").append(m).append(": entrate ").append(e(l.filter { it.tipo == "Entrata" }.sumOf { it.importo }))
                    .append(", uscite ").append(e(l.filter { it.tipo != "Entrata" }.sumOf { it.importo })).append("\n")
            }
        }

        // --- conti e trasferimenti
        val conti = Repo.conti()
        if (conti.isNotEmpty()) {
            sb.append("\nCONTI (saldo attuale)\n")
            for (c in conti) {
                sb.append("- ").append(c.nome).append(" [").append(c.tipo).append("]")
                    .append(if (c.principale) " principale" else "").append(": ").append(e(Repo.saldoEffettivo(c))).append("\n")
            }
            val nomi = conti.associate { it.id to it.nome }
            val trasf = Repo.trasferimenti().filter { !it.data.isAfter(oggi) }.take(15)
            if (trasf.isNotEmpty()) {
                sb.append("\nTRASFERIMENTI TRA CONTI (ultimi ").append(trasf.size).append(", non sono entrate né uscite)\n")
                for (t in trasf) {
                    sb.append("- ").append(t.data.format(F)).append(": ").append(nomi[t.da] ?: "?").append(" → ")
                        .append(nomi[t.a] ?: "?").append(" ").append(e(t.importo)).append("\n")
                }
            }
        }

        // --- budget e target
        val ym = YearMonth.from(oggi)
        val speseMese = passati.filter { it.tipo == "Uscita" && YearMonth.from(it.data) == ym }
            .groupBy { it.categoria }.mapValues { it.value.sumOf { v -> v.importo } }
        val speseAnno = passati.filter { it.tipo == "Uscita" && it.data.year == oggi.year }
            .groupBy { it.categoria }.mapValues { it.value.sumOf { v -> v.importo } }
        val righeBud = ArrayList<String>()
        for (c in Repo.categorie()) {
            val bm = Repo.budgetMensile(c)
            val ba = Repo.budgetAnnuo(c)
            if (bm > 0) {
                val s = speseMese[c] ?: 0.0
                righeBud.add("- " + c + ": budget mensile " + e(bm) + ", speso questo mese " + e(s) + (if (s > bm) " (SFORATO)" else ""))
            }
            if (ba > 0) {
                val s = speseAnno[c] ?: 0.0
                righeBud.add("- " + c + ": budget annuo " + e(ba) + ", speso quest'anno " + e(s) + (if (s > ba) " (SFORATO)" else ""))
            }
        }
        if (righeBud.isNotEmpty()) sb.append("\nBUDGET PER CATEGORIA\n").append(righeBud.joinToString("\n")).append("\n")
        if (Repo.targetMese > 0 || Repo.targetAnno > 0) {
            sb.append("\nTARGET DI SPESA: mensile ").append(e(Repo.targetMese)).append(", annuo ").append(e(Repo.targetAnno)).append("\n")
        }

        // --- finanziamenti
        runCatching {
            val fin = Fin.elenco()
            if (fin.isNotEmpty()) {
                sb.append("\nFINANZIAMENTI E MUTUI\n")
                for (f in fin) {
                    sb.append("- ").append(f.optString("nome")).append(": capitale ").append(e(f.optDouble("capitale", 0.0)))
                        .append(", tasso ").append(String.format(Locale.ITALY, "%.2f", f.optDouble("tasso", 0.0))).append("%")
                    val st = Fin.stato(f).first
                    if (st != null) {
                        sb.append(", stato ").append(st.stato).append(", rate pagate ").append(st.ratePagate).append("/").append(st.rateTot)
                            .append(", residuo ").append(e(st.residuo)).append(", rata ").append(e(st.rataMensile))
                        st.prossimaData?.let { sb.append(", prossima rata ").append(e(st.prossimaImporto)).append(" il ").append(it.format(F)) }
                        st.fine?.let { sb.append(", fine ").append(it.format(F)) }
                        sb.append(", interessi restanti ").append(e(st.intRestanti))
                    }
                    sb.append("\n")
                }
            }
        }

        // --- fondo risparmio
        runCatching {
            val att = Fondo.fondoAttuale()
            val ob = Fondo.obiettivoAnnuale()
            val obs = Fondo.obiettivi()
            if (att > 0 || ob > 0 || obs.isNotEmpty()) {
                sb.append("\nFONDO RISPARMIO: attuale ").append(e(att)).append(", obiettivo annuale ").append(e(ob)).append("\n")
                for (o in obs) sb.append("- obiettivo ").append(o.nome).append(": ").append(e(o.importo)).append(" entro ").append(o.data).append("\n")
            }
        }

        // --- movimenti recenti
        val recenti = tutti.filter { !it.data.isBefore(oggi.minusDays(60)) }
            .sortedWith(compareByDescending<Voce> { it.data }.thenByDescending { it.ora })
            .take(150)
        if (recenti.isNotEmpty()) {
            sb.append("\nMOVIMENTI RECENTI (ultimi 60 giorni, max 150; i futuri sono già pianificati)\n")
            for (v in recenti) {
                sb.append(v.data.format(F)).append(" | ").append(cat(v)).append(" | ").append(v.descrizione.take(60))
                    .append(" | ").append(if (v.tipo == "Entrata") "Entrata " else "Uscita ").append(e(v.importo))
                if (v.conto.isNotBlank()) sb.append(" | ").append(v.conto)
                if (v.metodo.isNotBlank()) sb.append(" | ").append(v.metodo)
                if (v.data.isAfter(oggi)) sb.append(" | FUTURO")
                sb.append("\n")
            }
        }
        return if (sb.length > MAX_CONTESTO) sb.substring(0, MAX_CONTESTO) + "\n[contesto troncato]" else sb.toString()
    }

    fun prompt(domanda: String, cronologia: List<Pair<String, String>>): String {
        val crono = if (cronologia.isEmpty()) "" else
            "CONVERSAZIONE PRECEDENTE:\n" +
                cronologia.joinToString("\n") { "D: " + it.first + "\nR: " + it.second.take(1200) } + "\n\n"
        return "Sei un assistente esperto di finanza personale integrato nell'app OrbitaCasa.\n" +
            "Rispondi sulla base dei dati forniti sotto. Se un dato richiesto non è disponibile nel contesto, " +
            "dillo chiaramente invece di inventare numeri.\n\n" +
            "DATI DEL DATABASE:\n" + contesto() + "\n\n" +
            crono +
            "DOMANDA DELL'UTENTE:\n" + domanda.trim() + "\n\n" +
            "REGOLE DI RISPOSTA:\n" +
            "1. NON usare simboli Markdown (*, #, -, `, []). Per gli elenchi usa frasi separate o numeri.\n" +
            "2. Scrivi gli importi in formato italiano (virgola decimale, punto per le migliaia), es. 1.234,56€.\n" +
            "3. Sii diretto, concreto e sintetico. Se utile aggiungi numeri e percentuali.\n" +
            "4. Per i periodi relativi (oggi, ieri, questa settimana, questo mese, mese scorso, quest'anno, anno scorso) " +
            "usa SEMPRE la sezione \"RIEPILOGO PER PERIODI TEMPORALI\". Per mesi nominati usa \"RIEPILOGO MESE PER MESE\". " +
            "Se non viene indicato l'anno, usa il mese più recente con quel nome.\n" +
            "5. Per CONFRONTARE più mesi riporta i valori separatamente e calcola la differenza, senza sommarli.\n" +
            "6. Per persone o conti specifici cerca la riga \"per conto/persona\" del periodo o mese corrispondente.\n" +
            "7. I trasferimenti tra conti NON sono entrate né uscite: non vanno sommati ai totali.\n" +
            "8. Prima di dire che un dato non è disponibile controlla tutte le sezioni. Se il dato manca davvero, dillo.\n" +
            "9. Per consigli o strategie parti dai dati dell'utente (finanziamenti, budget, categorie) e proponi 2 o 3 azioni " +
            "concrete con numeri; chiudi con una frase breve che sono indicazioni generali e non una consulenza finanziaria.\n"
    }
}
