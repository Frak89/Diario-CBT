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


## v0.2.7 — Groq Free Tier audio + optional GPT-OSS 120B journal analysis
- GroqCloud **Free Plan** uses rate limits rather than prepaid API credit. Sign up and create a free API key at https://console.groq.com/keys . Groq's free tier is not unlimited, may change, and returns HTTP 429 after limits.
- **Whisper Large V3** `whisper-large-v3` transcribes M4A recordings in Italian, English and Spanish via `https://api.groq.com/openai/v1/audio/transcriptions`. Select Groq or OpenAI under Settings → Online Audio Transcription. Groq is the default for new installations. Existing OpenAI API keys remain stored separately.
- The user must provide their own Groq key (`gsk_...`) in Settings; there are no shared secrets in the public APK. Groq and OpenAI keys are both encrypted with the existing journal's Android Keystore AES-GCM mechanism and are never printed in error dialogs.
- **Explicit per-provider voice-consent** is required before recording uploads. Changing providers invalidates the prior provider's consent. Recorded M4A remains in private app storage if a service is unavailable or rate-limited.
- **Optional Groq GPT-OSS 120B** online CBT reflection and personalized splash encouragement are available independently of local Qwen3. They are OFF by default and require a **distinct opt-in consent** because the diary's full text, personal goals, and any user-declared diagnosis are sent to Groq's API when a session completes. For privacy, local Qwen3 remains the default unless cloud consent is enabled.
- Groq's current data and retention rules are explained at https://console.groq.com/docs/your-data . A limited Groq Free Tier should not be presented as medical advice or unlimited free healthcare.
- The local Qwen3 Full AI APK still includes a model for offline use, but can use Groq only when explicitly selected.
- Both providers' microphone uploads require an Internet connection. OpenAI's HTTP429 credit/billing problem is not solved by switching the same key: Groq uses its own free-tier account/key.

Development builds are signed with an ephemeral debug key; do not uninstall a previous app containing sensitive journal content unless backups are available. Device inference and cloud requests require runtime testing beyond CI compilation.

## v0.2.8 — Riepilogo giornaliero, punteggio, serie, incoraggiamento e TTS

- **Termina e mostra riepilogo** salva la giornata una sola volta e apre il riepilogo con il **punteggio CBT 0–10** (completamento di sei esercizi, non diagnosi), la **serie di giorni consecutivi**, il verdetto salvato e un incoraggiamento utile.
- Riaprire il riepilogo **non rigenera l'analisi IA**. Un pulsante separato «Rigenera analisi IA» richiede conferma prima di sostituire il testo. Se la generazione è ancora in corso, il riepilogo mostra lo stato senza creare una seconda richiesta.
- Una nuova frase di incoraggiamento è generata dalla stessa IA già scelta, facendo riferimento a un'azione o sforzo presente nel diario e proponendo un passo piccolo e concreto. È memorizzata nel diario del giorno e usata per lo splash successivo. Se l'IA non è disponibile si mostra una proposta guidata, chiaramente distinta, ricavata dalle risposte scritte.
- La sintesi vocale rimuove Markdown (per esempio **asterischi**, intestazioni, link) e divide i testi lunghi in blocchi compatibili con TextToSpeech. Il testo originale dell'osservazione rimane visualizzato e salvato.
- Il riepilogo giornaliero rimane consultabile riaprendo l'app, con un tasto per rivedere o correggere le risposte.
- I test unitari Java controllano la pulizia di Markdown e i blocchi TTS; GitHub Actions ora esegue test e build.

**Attenzione:** tutte le build sono APK di prova firmati in CI con chiave debug non stabile; non cancellare l'installazione corrente con dati importanti senza backup. Il test automatizzato non certifica il funzionamento dell'IA sul Pixel 7 Pro.


## v0.2.9 — Archiviazione multi-sessione, calendario CBT, punteggio corretto, Groq
- Risolto il disallineamento tra punteggio 10/10 nella pagina finale e 0/10 nel riepilogo: il risultato viene sempre calcolato dalle sei risposte effettive, e la fotografia storica errata viene aggiornata.
- Quando Groq è configurato soltanto per trascrivere, l'analisi del diario resta disabilitata per privacy; l'errore esplicita il consenso aggiuntivo, con pulsante «Configura IA online Groq» invece di obbligare a Qwen3. Groq non riceve testo CBT/diagnosi senza opt-in separato.
- «✓ Termina diario e registra» copia tutte le risposte e l'osservazione IA in `archive[YYYY-MM-DD][]`, conserva separatamente più registrazioni dello stesso giorno, salva il JSON con Android Keystore AES-GCM, e **solo dopo la riuscita** azzera i campi del modulo per la nuova compilazione. Un errore non cancella le risposte.
- Le registrazioni audio M4A sono duplicate in una cartella privata dell'app per mantenere la storia anche con sessioni successive nello stesso giorno. Come nella versione precedente, **i file audio non sono cifrati individualmente**, benché restino nello storage privato dell'app.
- Calendario accessibile dall'icona 📅 sull'interfaccia o dal riepilogo. I giorni con un diario completato hanno una marcatura evidente. Toccando il giorno, si visualizzano tutte le registrazioni di quella data, con testi integrali delle 6 fasi, audio disponibili, punteggio, streak, osservazione IA e incoraggiamento.
- Lo storico di versioni precedenti viene visualizzato tramite le sessioni già presenti in `sessions` senza migrazione distruttiva.
- Il testo mostrato nel riepilogo e nella pagina conclusiva non contiene gli asterischi Markdown dell'IA; il testo originale resta salvato.
- Build di test v0.2.9 con test automatici; la compilazione non sostituisce un collaudo reale su Android 17.

Non disinstallare una precedente app con dati importanti, perché gli APK di test GitHub Actions possono avere firme di debug non compatibili fra una release e l'altra.

## v0.3.0 — Android 17 notification/reminder fix
- Fixed the prior silent reminders: the old implementation used `setInexactRepeating`, which Android may delay substantially, and users could enable reminders without actually granting `POST_NOTIFICATIONS`.
- A daily one-shot alarm now re-arms itself after each broadcast (including when the diary for today is already complete). Uses `setExactAndAllowWhileIdle` only if exact-alarm special access is granted; otherwise `setAndAllowWhileIdle`, which is intentionally **inexact**. Never bypasses Android's timing restrictions.
- Manifest declares `SCHEDULE_EXACT_ALARM` (special user permission, optional), `POST_NOTIFICATIONS` and `USE_FULL_SCREEN_INTENT`. Reschedules after boot, app upgrade, clock/time-zone changes and exact-alarm permission changes. Re-arms when the app launches with reminders enabled.
- New high-importance reminders notification channel `cbt_reminder_alerts_v2`; respects user preference to disable it.
- Settings now display whether notifications, precise alarms, and full-screen access are permitted. Added:
  - **Invia notifica di prova**: posts an immediate notification, bypassing schedule and completed-day skip, requesting runtime notification permission if missing.
  - **Anteprima popup a pieno schermo**: user-triggered preview of the custom reminder panel. This only tests UI, not OS-delivered full-screen intents.
  - Settings shortcuts for app notification permissions, special exact alarms and full-screen alert permissions.
- On Android 14+, automatic full-screen notifications are constrained to eligible alarm/calling uses; this journaling application cannot guarantee full-screen launches. On an unlocked phone an authorized full-screen-intent generally appears as an expanded heads-up notification rather than launching an activity. A high-priority heads-up is the graceful fallback.
- Unit tests cover scheduling at the requested local time, next-day recurrence, invalid input and Europe/Rome DST changes.
- The app does not require cloud access for local reminders. Sensitive diary contents are never placed into the notification.

**Testing on the actual Pixel 7 Pro** is still required: notification runtime permission, battery policies, user-disabled channels, precise time permission, locked vs unlocked screen, and 24-hour rescheduling. Test APK signatures remain ephemeral and may prevent direct updating from previous builds; do not uninstall an app with valuable entries without backing them up.


## v0.3.1: PDF, backup locale e aggiornamenti

Il calendario ora permette di esportare un singolo diario storico come PDF multipagina e, tramite il comando dedicato, tutti i diari storici. I PDF contengono domande, risposte, osservazione IA, incoraggiamento, punteggio e streak. Gli audio non vengono incorporati nel PDF.

Il backup locale completo salva i dati e gli audio in un file .cbtbak con password (PBKDF2-HMAC-SHA256, AES-256-CTR e HMAC-SHA256). La password deve essere lunga almeno otto caratteri e non è recuperabile. Il backup è indipendente dalla chiave Android Keystore: è trasportabile su un telefono diverso o dopo reinstallazione. Include anche credenziali API, esclusivamente all'interno del file cifrato. Il ripristino verifica prima l'autenticità del file, prepara l'audio e tenta di ripristinare il contenuto precedente in caso di errore. Limite di backup 512 MB; crea prima una copia dei dati presenti, perché il ripristino sostituisce l'archivio.

Gli aggiornamenti vengono verificati automaticamente una volta al giorno tramite le release ufficiali di GitHub e, quando esiste una nuova versione, l'utente può scaricare l'APK e avviarne l'installazione con conferma Android. Gli APK devono essere firmati con lo stesso certificato per installarsi sopra una versione precedente e preservare i dati. Le build di prova GitHub Actions usano una firma di debug che può cambiare. Occorre configurare una chiave di firma stabile tramite i secrets della repository e pubblicare una release ufficiale per garantire aggiornamenti futuri. Se la firma preesistente non coincide, sarà necessario esportare il backup, reinstallare e recuperare il backup: non disinstallare prima del backup.
