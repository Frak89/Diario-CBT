# Diario CBT — Android
### v0.2.0 — Splash arcade, interfaccia moderna e Qwen3 offline integrato

Applicazione Android nativa per **riflessioni CBT guidate** in italiano, inglese e spagnolo. Il progetto fornisce un supporto al diario, non una diagnosi o un trattamento medico.

## Funzionalità
- Percorso in 7 schermate con barra avanzamento e **transizione orizzontale animata** (Avanti/Indietro e swipe).
- Interfaccia scura con gradienti, moduli arrotondati, indicatori neon.
- Splash screen con **cervello originale disegnato in vettoriale che ruota in 3D**, logo CBT arcade originale e frase motivazionale. Non usa risorse grafiche di altri giochi.
- Testo, riconoscimento vocale **offline quando supportato**, registrazione audio locale, lettura ad alta voce sia delle domande sia delle risposte e del riepilogo.
- Riepilogo con indicatore 0–10 sulla compilazione degli esercizi, non sulla salute mentale, e streak.
- Diagnosi clinica **autodichiarata, facoltativa** usata come solo contesto per suggerimenti pratici di IA.
- Promemoria locale quotidiano; popup a schermo intero solo se Android lo consente.
- Testi del diario cifrati con Android Keystore/AES-GCM; backup automatico disabilitato. **Audio registrato ancora in chiaro nello spazio privato dell'app**.

## IA generativa integrata: Qwen3 0.6B
Questa versione non richiede che l'utente ricerchi o importi manualmente un file modello. Il runtime **LiteRT-LM** è integrato e al primo avvio una finestra offre il download con un solo tocco.

- Modello: **Qwen3-0.6B**, LiteRT Community, licenza **Apache-2.0**.
- Fonte: https://huggingface.co/litert-community/Qwen3-0.6B
- File: `Qwen3-0.6B.litertlm` (613.722.264 byte; circa 586 MiB).
- SHA-256 atteso: `c5f39b4191926786c3e6668b62af4d6eb15248dfc7a5348252a9b406f33f2108`.
- Download in memoria privata dell'app, con possibilità di riprendere download incompleti e verifica SHA-256 prima dell'uso.
- L'IA elabora il diario **in locale** usando GPU e fallback CPU. Non vengono effettuate chiamate a servizi IA remoti né caricati diari sul web.
- **Due APK per scegliere**:
  - **Full AI Offline** — il modello da 614 MB è **già incorporato nell'APK** (~669 MB). Al primo avvio l'app lo copia nello spazio privato e controlla SHA-256: nessuna connessione Internet necessaria.
  - **Compatto** — APK ~24 MB, runtime IA già integrato; al primo avvio è offerto il download automatico del modello. Dopo il download, l'analisi resta offline.
- Senza modello, si usa solo una riflessione deterministica **etichettata senza IA generativa**; l'utente può attivare Qwen3 in seguito dalle impostazioni.
- La compatibilità e le prestazioni effettive su **Pixel 7 Pro Android 17** vanno ancora verificate su dispositivo; la build automatica verifica la compilazione, non l'inferenza reale.

Il motore analizza i pensieri automatici, propone una prospettiva realistica e due piccoli passi concreti sotto l'osservazione finale. Un'altra richiesta genera una frase motivazionale per la splash successiva, senza diagnosi nuove o etichette.

## Build
Da GitHub: `Actions → Android FULL AI offline APK → Artifacts` per l'APK già completo di Qwen3. In alternativa `Actions → Android test release → Artifacts` per l'APK compatto. Gli artifact sono ZIP: estrai l'APK prima dell'installazione. In Android Studio: JDK 17, SDK 36 e Gradle 8.13.

**Nota APK:** viene compilato con `assembleRelease`, ma è **firmato con chiave di debug per test**, non una release pubblicabile.

## Privacy e sicurezza
- Diagnosi autodichiarate usate soltanto come contesto opzionale.
- Nessun indicatore CBT è una valutazione clinica o una misura del valore della persona.
- Media registrati non cifrati: non utilizzare informazioni vocali sensibili in un ambiente non fidato.
- Pesi IA scaricati solo da Hugging Face, non viene trasferito alcun contenuto del diario.
- La versione **Full AI** non richiede download del modello, ma necessita spazio libero per installazione APK e copia privata del modello. La versione **compatta** richiede il primo download (~614 MB), preferibilmente via Wi-Fi.
- Il modello **può commettere errori**: i consigli sono da valutare criticamente. In situazioni urgenti contattare servizi di emergenza o un professionista.

## Versione
v0.2.0 prototipo. Modifiche sviluppate nel branch `feature/modern-ui-integrated-qwen3`.


## v0.2.4 — Diario CBT su Android
- Domande più semplici, con suggerimenti concreti nei campi di testo (IT/EN/ES).
- Emozioni selezionabili con un tocco, anche multiple; resta disponibile la scrittura libera.
- «Cosa diresti a te stesso con gentilezza?» al posto della domanda riferita a una persona cara.
- Campi con scorrimento automatico sopra la tastiera e spazio aggiuntivo per le domande successive.
- **Un solo pulsante REC** per registrazione e trascrizione automatica dopo lo stop; rimosse dettatura e trascrizione separate. È necessario il consenso iniziale all'eventuale trasferimento dell'audio al fornitore del servizio vocale Android, revocabile dalle impostazioni. La trascrizione via `EXTRA_AUDIO_SOURCE` dipende dalla compatibilità del riconoscitore installato sul telefono e richiede Android 13+; se non è supportata, l'audio originale viene conservato e si mostra l'errore reale, senza fingere di aver trascritto.
- Schermata finale Qwen3 con stato di elaborazione visibile, risultato reale, motivazione degli errori e pulsante di riprova. Non spaccia più il riepilogo predefinito per IA. Controllo IA nelle impostazioni.
- Icona dell'app ricostruita dalle curve del cervello dello splash, con icona adattiva e versione monocromatica.
- Attenzione: i dati clinici rimangono sensibili; l'IA locale non invia il diario a servizi remoti, ma il servizio opzionale di trascrizione **può inviare l'audio a un fornitore esterno** dopo il consenso.
- Non si deve cancellare la vecchia app senza un backup, perché gli APK di test GitHub Actions sono firmati con chiavi di debug non stabili fra build.


## v0.2.5 — Fix audio STT ERROR_CLIENT (5)
**Android's `SpeechRecognizer.ERROR_CLIENT=5`** indicates a client-side recognition failure. The Android `EXTRA_AUDIO_SOURCE` recorded-audio option is explicitly optional for recognizer vendors and fails on some Pixel/GrapheneOS configurations. This app no longer uses it for automatic transcription.

**Working cloud pathway**: after REC stops, the saved M4A audio file is sent directly to `https://api.openai.com/v1/audio/transcriptions` with the model `gpt-4o-mini-transcribe`, and the returned text is automatically appended to the answer and encrypted on-device. It requires:
- An OpenAI API account with active billing and **the user's own API key** (not a ChatGPT subscription). No shared hard-coded API credential is in the APK.
- Explicit consent from Settings to transmit audio to OpenAI. Audio may contain sensitive personal health information. Turning off consent disables future uploads.
- Internet access. The recording file remains on the phone even if the request fails.
- The user's secret is encrypted locally inside the existing AES-GCM / Android Keystore journal store, not in Gradle or GitHub. The input field never redisplays the saved key.
- Previously saved but untranscribed audio can be retried from Settings using `Trascrivi le vecchie registrazioni di oggi`. A separate transcription button beside each REC is **not** required.
- The app currently calls the provider directly from the user's personal device. For public distribution with multi-user accounts, replace the direct API key arrangement with an authenticated backend using per-user credentials.

**Not included**: any free, shared transcription API service, or a guarantee that a user's API billing/credential already exists. Without setup, REC continues to preserve audio but makes no false promise of automatic transcription.

**Existing data**: do not uninstall the current APK just to update, unless you have safely backed up its local diary/audio. GitHub Actions tests use an ephemeral debug signing key. These APKs may not be installable over a prior test build.
