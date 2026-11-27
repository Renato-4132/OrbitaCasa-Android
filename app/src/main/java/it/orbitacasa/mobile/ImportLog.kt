package it.orbitacasa.mobile

import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object ImportLog {
    const val ESTRATTO = "ESTRATTO"
    const val SYNC = "SYNC"
    const val NOTIF = "NOTIF"

    private const val FILE = "log_importazioni.txt"
    private const val MAX_RIGHE = 2000
    private val FMT_TS = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
    private val FMT_D = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    private val FMT_DB = DateTimeFormatter.ofPattern("dd-MM-yyyy")
    private val lock = Any()

    class Riga(
        val quando: String, val tipo: String, val data: String, val desc: String,
        val importo: String, val direzione: String, val categoria: String,
        val conto: String, val id: String, val metodo: String, val tag: String,
    )

    private fun file(): File = File(Repo.dirDati(), FILE)

    private fun pulisci(t: String?): String = (t ?: "").replace("|", "/").replace("\n", " ").trim()

    fun scrivi(
        tipo: String, data: LocalDate, desc: String, importo: Double, direzione: String,
        categoria: String, conto: String = "", metodo: String = "", tag: String = "", id: String = "",
    ) {
        val riga = listOf(
            LocalDateTime.now().format(FMT_TS), tipo, data.format(FMT_D), pulisci(desc),
            String.format(java.util.Locale.ITALY, "%,.2f", Math.abs(importo)) + " €",
            direzione, pulisci(categoria), pulisci(conto).ifEmpty { "—" }, id,
            pulisci(metodo).ifEmpty { "—" }, pulisci(tag).ifEmpty { "—" },
        ).joinToString(" | ")
        try {
            synchronized(lock) {
                val f = file()
                f.appendText(riga + "\n", Charsets.UTF_8)
                val tutte = f.readLines(Charsets.UTF_8)
                if (tutte.size > MAX_RIGHE) f.writeText(tutte.takeLast(MAX_RIGHE).joinToString("\n") + "\n", Charsets.UTF_8)
            }
        } catch (_: Exception) {
        }
    }

    fun scriviDaEntry(tipo: String, data: String, e: JSONObject) {
        val d = try { LocalDate.parse(data, FMT_DB) } catch (_: Exception) { return }
        val tags = e.optJSONArray("hashtag")?.let { a -> (0 until a.length()).joinToString(" ") { a.optString(it) } } ?: ""
        scrivi(
            tipo, d, e.optString("descrizione"), e.optDouble("importo", 0.0), e.optString("tipo"),
            e.optString("categoria"), e.optString("conto"), e.optString("metodo_pagamento"), tags,
            e.optString("id_spesa"),
        )
    }

    fun leggi(tipo: String? = null): List<Riga> {
        val out = ArrayList<Riga>()
        try {
            synchronized(lock) {
                val f = file()
                if (!f.isFile) return out
                for (r in f.readLines(Charsets.UTF_8)) {
                    if (r.isBlank()) continue
                    val p = r.split("|").map { it.trim() }.toMutableList()
                    while (p.size < 11) p.add("")
                    if (tipo != null && !p[1].equals(tipo, true)) continue
                    out.add(Riga(p[0], p[1], p[2], p[3], p[4], p[5], p[6], p[7], p[8], p[9], p[10]))
                }
            }
        } catch (_: Exception) {
        }
        return out.reversed()
    }

    fun svuota(tipo: String? = null): Int {
        synchronized(lock) {
            val f = file()
            if (!f.isFile) return 0
            val righe = f.readLines(Charsets.UTF_8).filter { it.isNotBlank() }
            val resta = if (tipo == null) emptyList() else righe.filter {
                !it.split("|").getOrElse(1) { "" }.trim().equals(tipo, true)
            }
            f.writeText(if (resta.isEmpty()) "" else resta.joinToString("\n") + "\n", Charsets.UTF_8)
            return righe.size - resta.size
        }
    }
}
