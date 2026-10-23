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
- **Il modello non è dentro l'APK**: per la dimensione di oltre mezzo GB viene scaricato automaticamente alla prima attivazione su consenso dell'utente. Il motore resta pronto per gli utilizzi offline successivi.
- Senza modello, si usa solo una riflessione deterministica **etichettata senza IA generativa**; l'utente può attivare Qwen3 in seguito dalle impostazioni.
- La compatibilità e le prestazioni effettive su **Pixel 7 Pro Android 17** vanno ancora verificate su dispositivo; la build automatica verifica la compilazione, non l'inferenza reale.

Il motore analizza i pensieri automatici, propone una prospettiva realistica e due piccoli passi concreti sotto l'osservazione finale. Un'altra richiesta genera una frase motivazionale per la splash successiva, senza diagnosi nuove o etichette.

## Build
Da GitHub: `Actions → Android test release → Artifacts` (ZIP contenente l'APK). Oppure in Android Studio: JDK 17, SDK 36 e Gradle 8.13.

**Nota APK:** viene compilato con `assembleRelease`, ma è **firmato con chiave di debug per test**, non una release pubblicabile.

## Privacy e sicurezza
- Diagnosi autodichiarate usate soltanto come contesto opzionale.
- Nessun indicatore CBT è una valutazione clinica o una misura del valore della persona.
- Media registrati non cifrati: non utilizzare informazioni vocali sensibili in un ambiente non fidato.
- Pesi IA scaricati solo da Hugging Face, non viene trasferito alcun contenuto del diario.
- Richiede un download importante (~614 MB), Wi-Fi consigliato e spazio di archiviazione sufficiente (durante il download fino al doppio per cache parziali).
- Il modello **può commettere errori**: i consigli sono da valutare criticamente. In situazioni urgenti contattare servizi di emergenza o un professionista.

## Versione
v0.2.0 prototipo. Modifiche sviluppate nel branch `feature/modern-ui-integrated-qwen3`.
