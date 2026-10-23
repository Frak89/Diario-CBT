# Diario-CBT — Android nativo

Prototipo **0.1.0**: diario CBT guidato, navigazione Avanti/Indietro e swipe, monitoraggio emotivo, note e registrazione audio, domande lette con TTS, riconoscimento **solo locale** quando il dispositivo lo supporta, profilo clinico facoltativo, valutazione 0–10 non diagnostica, streak e suggerimenti pratici.

**Lingue:** italiano, inglese, spagnolo.

**IA generativa on-device:** runtime integrato MediaPipe LLM Inference. Per usare un modello linguistico reale occorre scaricare/importare dall'app un file modello `.task` *compatibile* e di licenza appropriata. Il repository **non contiene pesi** (sono centinaia di MB/GB): senza di essi l'app propone una scheda CBT deterministica, identificata come **senza IA generativa**. Il pulsante «Importa modello IA» copia il modello in una directory privata; l'analisi non usa servizi remoti. La generazione richiede un dispositivo supportato: verificare in particolare Pixel 7 Pro (l'API è ottimizzata per dispositivi più recenti). Una frase incoraggiante **generata dall'IA** sulla base della diagnosi dichiarata viene memorizzata a fine sessione, e visualizzata allo splash successivo. Prima del primo utilizzo IA viene usato un testo motivazionale **predefinito**, non falsamente attribuito a un modello.

**Privacy:** testo e profilo cifrati AES-GCM con chiave Android Keystore in spazio privato; audio in file privati **non ancora cifrati**. Backup automatico disabilitato. Non aggiungere analytics o servizi esterni senza consenso.

**Promemoria:** notifica locale a orario configurabile, con heads-up e richiesta *facoltativa* per apertura a schermo intero. Android 14+ riserva full-screen intent ai casi consentiti e approvati dall'utente; una normale app diario non può garantire popup automatici. Ricorrenze con AlarmManager sono approssimative. La schermata permette «Inizia», «Tra 15 minuti», «Salta oggi».

**Punteggio:** è un indicatore di completamento di esercizi CBT, non una misura diagnostica, del benessere o del valore della persona. Le risposte e le diagnosi sono informazioni sensibili: evitare GitHub issues contenenti diari reali.

**Compilazione:** Android Studio con JDK 17, SDK 36 o GitHub Actions → *Android test release*; l'APK ottenuto è firmato **con chiave di debug per i test**, anche se il task si chiama `assembleRelease`. Non è una release di distribuzione. Il progetto non include Gradle wrapper, ma il workflow installa Gradle 8.13. L'esecuzione su dispositivo non è ancora certificata.

**Nota:** le osservazioni di IA non sostituiscono pareri clinici o psicoterapia; la diagnosi è inserita dall'utente, mai dedotta dall'app.
