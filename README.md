**📱 OrbitaCasa Mobile 🇮🇹**

**OrbitaCasa in tasca: i tuoi conti sempre con te, allineati al PC quando vuoi, con le spese che si registrano da sole dalle notifiche.**

---

![Android](https://img.shields.io/badge/Android-8.0+-3DDC84?style=flat-square&logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-100%25-7F52FF?style=flat-square&logo=kotlin&logoColor=white)
![Standalone](https://img.shields.io/badge/App-Standalone-4f46e5?style=flat-square)
![Versione](https://img.shields.io/badge/VERSIONE-1.0.65-orange?style=flat-square)
![Cloud](https://img.shields.io/badge/Cloud-Zero_di_default-22c55e?style=flat-square)

---

**🚀 L'ESSENZA DELL'APP**

**OrbitaCasa Mobile** è l'app Android **standalone** di [OrbitaCasa](https://github.com/Renato-4132/OrbitaCasa). Non ha bisogno del PC acceso, di un server o di una connessione: funziona da sola, con i dati salvati sul telefono. Quando vuoi, la colleghi al tuo OrbitaCasa sul PC e i dati restano allineati, oppure li porti avanti e indietro con uno **zip**.

* 🔗 **Sync diretta con il PC**: movimenti, categorie, archivi e documenti si allineano con OrbitaCasa desktop nella tua rete di casa, anche in automatico all'apertura dell'app.
* 📥 **Import / Export con uno zip**: lo stesso backup o snapshot di OrbitaCasa desktop, in entrambe le direzioni.
* 📩 **Notifiche → spese**: l'app legge le notifiche di banca e carte e ne ricava le spese, da confermare o registrate in automatico.
* 📷 **Configurazione con i QR**: indirizzo del PC, token e chiave Gemini si inseriscono inquadrando un QR, senza digitare.
* 🛡️ **Zero Cloud di default**: i dati restano sul telefono, il backup Android è disattivato.
* 🔒 **Blocco app**: impronta, volto o sblocco del dispositivo.
* 📴 **Funziona offline**: nessun server, nessun account, nessun abbonamento.
* 🧩 **Stessi file, stesse chiavi del desktop**: l'app legge e scrive i JSON di OrbitaCasa senza perdere i campi che non conosce.
* 🔔 **Promemoria con notifiche**: avvisi all'ora giusta e riepilogo del giorno.
* 🎭 **Modalità demo**: provala con dati di esempio, senza toccare i tuoi.

---

**🔥 COSA PUOI FARE DAL TELEFONO**

<details>
<summary><b>Clicca per vedere tutte le funzioni</b></summary>

---

**🟠 Movimenti e pianificazione**

* **Movimenti del mese** — Entrate, uscite e saldo del mese sempre in primo piano nella Home
* **Calendario** — Vista dei movimenti per giorno
* **Pianifica** — Dilaziona una spesa in quote fino alla cifra prefissata
* **Promemoria** — Agenda con avvisi all'ora, anticipo configurabile e riepilogo del giorno (di default alle 08:00)
* **Notifiche** — Le spese lette dalle notifiche, con la coda "Da confermare" e le regole di lettura
* **Cerca operazioni** — Filtri avanzati (anche per tag) e condivisione dei risultati

**🟢 Analisi e controllo**

* **Grafici** — Andamento di entrate e uscite
* **Checkout** — Ti segnala le categorie in cui spendi di solito (almeno 4 volte negli ultimi 12 mesi) ma che questo mese non hai ancora registrato, con avviso di fine mese
* **Proiezione mese** — Per categoria: speso, speso fino a oggi, previsione e budget

**🔵 Finanze**

* **Conti** — Fino a 10 conti (personale, comune, figli, risparmio, altro) con saldo iniziale e IBAN
* **Trasferimenti** tra conti
* **Categorie e Target** — Budget mensile e annuale per categoria, con classe 50/30/20 (Bisogni, Desideri, Risparmio), più target di spesa mensile e annuale
* **Ricorrenze** — Affitto, abbonamenti, stipendio: inseriti una volta
* **Finanziamenti** — Rate e piano di ammortamento dei prestiti
* **Fondo Risparmio** — Obiettivo annuale, fondo attuale e obiettivi
* **SmartCat** — Suggerisce la categoria in base allo storico (si attiva e disattiva da Strumenti)

**🟡 Casa e beni**

* **Documenti contabili** — Fatture e bollette archiviate, collegate ai movimenti
* **Documenti personali** — Per profilo, con categorie e scadenze
* **Utenze** — Letture e consumi di luce, gas e acqua su 12 mesi
* **FairShare** — Spese divise tra più persone, con le quote
* **AutoPark** — Veicoli, scadenze, spese
* **ImmoBil** — Immobili, entrate e uscite
* **Pet Care** — Schede degli animali

**🟣 Strumenti**

* **Importa / Esporta** — Zip di OrbitaCasa in entrambe le direzioni, con copia di sicurezza prima di sostituire i dati
* **Sync PC** — Sincronizzazione diretta con OrbitaCasa desktop
* **Estrazione dati con Gemini (opzionale)** — Leggi un documento PDF o una foto e l'app compila i campi per te
* **Blocco app** con biometria o sblocco del dispositivo
* **Barra di navigazione personalizzabile**
* **Aggiornamenti** — Controlla se c'è una nuova versione su GitHub, anche all'avvio
* **Info e contatti** — Versione, supporto, siti ufficiali e manuali PDF
* **Modalità demo** e **tema chiaro e scuro**

</details>

---

**🔗 SINCRONIZZAZIONE CON IL PC**

<details>
<summary><b>Clicca per i dettagli</b></summary>

Da **Home → Strumenti → 🔗 Sync PC** colleghi il telefono a OrbitaCasa acceso sul tuo PC, nella rete di casa.

**Cosa si sincronizza**

* **Movimenti** — in entrambe le direzioni, anche le eliminazioni
* **Impostazioni e categorie**
* **Archivi** — conti, utenze, veicoli, immobili e gli altri file di OrbitaCasa
* **Documenti** — contabili e personali, PDF e foto

**Come si configura**

1. Sul PC, in OrbitaCasa, trovi **l'indirizzo** del server e il **token** (è lo stesso del webhook).
2. Sul telefono apri Sync PC e li inserisci a mano oppure **scansioni i QR** con i pulsanti 📷.
3. Tocca **Sincronizza**: al primo collegamento l'app memorizza il certificato del PC.
4. Se vuoi, spunta **Sincronizza all'apertura dell'app** (parte al massimo una volta ogni 10 minuti).

**Sicurezza e conflitti**

* Connessione solo **HTTPS**, con il **certificato del PC fissato** al primo collegamento: se cambia, l'app si ferma e ti chiede di ricollegare.
* Se lo stesso dato cambia su entrambi i lati, **vince il PC**; la copia del telefono resta in una cartella di sicurezza.
* A ogni sincronizzazione l'app fa prima una copia di sicurezza dei dati.
* **Scollega** cancella la configurazione e lo stato di sincronizzazione, non i tuoi dati.
* Non è disponibile in modalità demo.

</details>

---

**📩 NOTIFICHE → SPESE**

<details>
<summary><b>Clicca per i dettagli</b></summary>

L'app può leggere le notifiche di banca e carte e trasformarle in spese, con lo stesso flusso di OrbitaNotify, ma direttamente sul telefono.

* **Interruttore generale**: da **Home → Movimenti → 📩 Notifiche** accendi o spegni la lettura. Da spenta, l'app non legge nulla.
* **Regole per app**: scegli da quale app leggere e filtra per testo (*contiene* / *esclude*), giorni della settimana e fascia oraria. Senza regole non viene letto niente.
* **Conto, metodo e tag** assegnati in automatico a ogni regola.
* **Da confermare**: le spese lette finiscono in coda; le confermi, le correggi o le scarti.
* **Registrazione automatica** (per regola): in base alle regole di lettura, oppure sempre.
* **Prova testo**: incolli il testo di una notifica e vedi cosa ne ricava l'app prima di attivare la regola.
* **Ripiego Gemini (opzionale)**: se le regole fisse non capiscono la notifica, puoi far chiedere a Gemini. Se resta spento, nessun testo viene inviato a Google.
* **Avvisi di esito**: una notifica ti dice se la spesa è stata registrata o è da confermare.

Serve concedere una volta l'**accesso alle notifiche** dalle impostazioni di Android: l'app ti ci porta con un tocco.

</details>

---

**📷 QR CODE**

<details>
<summary><b>Clicca per i dettagli</b></summary>

Dove serve scrivere indirizzi o chiavi lunghe, puoi inquadrare un QR con il pulsante 📷:

* **Sync PC** → QR dell'**indirizzo** del PC e QR del **token**
* **Chiave Gemini** → QR della **chiave API**

</details>

---

**🔐 PRIVACY E PERMESSI**

<details>
<summary><b>Clicca per i dettagli</b></summary>

I tuoi dati **non escono dal telefono**, a meno che tu non attivi tu una funzione che lo richiede.

| Permesso | A cosa serve |
|---|---|
| Biometria | Blocco app (opzionale) |
| Notifiche | Avvisi dei promemoria e delle spese lette |
| Avvio del dispositivo | Riprogrammare gli avvisi dopo un riavvio o un aggiornamento |
| Accesso alle notifiche | Solo per **Notifiche → spese** (opzionale, lo concedi tu dalle impostazioni di Android) |
| Internet | **Sync con il tuo PC** (rete di casa), **estrazione con Gemini** (se inserisci la tua chiave) e **ricerca aggiornamenti** su GitHub |

* La **chiave API di Gemini** resta solo sul telefono, nelle impostazioni private dell'app.
* La **sync** parla solo con il tuo PC, via HTTPS con certificato fissato: nessun server di terzi.
* Le notifiche vengono lette solo con la lettura accesa e solo se passano le tue regole; il testo va a Gemini solo se attivi il ripiego.
* Il backup automatico di Android è **disattivato**: i dati non finiscono su servizi cloud a tua insaputa.
* Nessun account, nessuna telemetria, nessuna pubblicità.

</details>

---

**⚙️ INSTALLAZIONE**

<details>
<summary><b>Clicca per vedere le istruzioni</b></summary>

**📦 Dalla release (consigliato)**

1. Apri la pagina delle [Release](https://github.com/Renato-4132/OrbitaCasa-Android/releases/latest) dal telefono.
2. Scarica l'**APK** dell'ultima versione.
3. Aprilo e consenti l'installazione da questa fonte, se Android te lo chiede.

> **Requisiti:** Android 8.0 (API 26) o successivo.

**🔄 Aggiornamenti**

L'app controlla l'ultima release su GitHub all'avvio e, quando vuoi, da **Home → Strumenti → 🔄 Aggiornamenti**. Se c'è una versione più recente, ti porta al download dell'APK dal browser.

</details>

---

**🧭 PRIMI PASSI**

1. Installa l'app e aprila. Se vuoi dare un'occhiata prima, prova la **modalità demo**.
2. Porta i tuoi dati in uno di questi modi:
   * **Sync PC** (Home → Strumenti → Sync PC): indirizzo e token, anche con i QR, e poi *Sincronizza*;
   * **Importa uno zip** di OrbitaCasa desktop (Home → Strumenti → Importa / Esporta);
   * oppure parti da zero aggiungendo il primo conto.
3. Attiva il **blocco app** se vuoi proteggere i dati.
4. Se vuoi, aggiungi la **chiave Gemini** per l'estrazione automatica dai documenti.
5. Se vuoi, accendi **Notifiche → spese** e crea la prima regola per la tua banca.
6. Tieni allineato il PC con la **sync** o con **Esporta**: anche solo uno zip ogni tanto come backup.

> 💡 **Consiglio:** la sync ti evita di passare gli zip a mano, ma uno zip ogni tanto resta un ottimo backup.

---

**🔗 ECOSISTEMA ORBITACASA**

* 🖥️ [**OrbitaCasa**](https://github.com/Renato-4132/OrbitaCasa) — l'app desktop completa
* 🌐 [**OrbitaWeb**](https://github.com/Renato-4132/OrbitaWeb) — collega il telefono al server web di OrbitaCasa
* 🔔 [**OrbitaNotify**](https://github.com/Renato-4132/OrbitaNotify) — trasforma le notifiche di banca e carte in spese da confermare e le manda al desktop

**Quale scegliere?** OrbitaCasa Mobile è l'app che funziona **da sola**, con i dati sul telefono, e che puoi allineare al PC con la sync o con gli zip. Legge anche le notifiche per conto suo, senza bisogno di OrbitaNotify. OrbitaWeb invece si collega a un OrbitaCasa acceso sul tuo PC o server. Puoi usarle insieme.

---

📩 **ASSISTENZA TECNICA**

Per bug, suggerimenti o supporto: helporbitacasa@gmail.com

---

**📄 LICENZA**

Vedi il file `LICENSE` del repository.

---
