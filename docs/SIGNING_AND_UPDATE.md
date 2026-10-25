# Diario CBT — firma stabile e aggiornamenti senza disinstallare

L'app controlla una volta al giorno GitHub Releases e propone la versione più nuova. Android permette l'aggiornamento **sopra l'app esistente** soltanto quando applicationId e **certificato di firma** sono identici.

## Preparazione una sola volta

1. Genera sul tuo computer una nuova chiave di firma Android e custodiscine due copie offline (il file NON deve essere committato su GitHub):

```powershell
keytool -genkeypair -v -keystore diariocbt-release.jks -storetype PKCS12 -alias diariocbt -keyalg RSA -keysize 3072 -validity 36500
```

2. Apri GitHub → Frak89/Diario-CBT → Settings → Secrets and variables → Actions → New repository secret.

3. Inserisci questi quattro secrets:
   - `CBT_KEYSTORE_BASE64`: contenuto base64 di tutta la keystore. In PowerShell: `[Convert]::ToBase64String([IO.File]::ReadAllBytes("C:\Percorso\diariocbt-release.jks"))`.
   - `CBT_STORE_PASSWORD`: password della keystore.
   - `CBT_KEY_ALIAS`: alias scelto (es. `diariocbt`).
   - `CBT_KEY_PASSWORD`: password della chiave.

4. Apri Actions → **Publish stable signed Android release** → Run workflow → versione `0.3.1`. Il workflow fallirà in modo esplicito se manca un secret, invece di pubblicare un APK con la firma errata. Genera gli APK Compact e Full AI usando la **stessa** keystore.

5. Quando la release ufficiale è disponibile, l'app rileva gli aggiornamenti da GitHub, li scarica dopo conferma e avvia l'installer di Android. Android richiede l'autorizzazione all'installazione; non sono possibili installazioni silenziose.

## La prima migrazione dalle vecchie build

Le APK di test 0.2.x/0.3.0 pubblicate da Actions usavano la **firma debug del runner**, potenzialmente diversa da ogni altro APK. Queste firme NON sono sostituibili senza disinstallazione. Per evitare perdita dati: nella versione 0.3.1 di prova, usa prima **Salva backup locale completo**, scegli una password, verifica il backup, poi conserva il file **fuori dall'app**. Se Android rifiuta la prima release ufficiale per firma incompatibile, solo allora puoi disinstallare la versione di prova, installare quella ufficiale e utilizzare **Recupera backup locale**.

⚠️ Non disinstallare mai l'app prima di aver esportato e controllato il backup. Se perdi keystore/password di firma, gli aggiornamenti futuri senza disinstallazione non saranno più garantiti.

## Sicurezza dei dati
- L'app non manda diari o audio a GitHub; usa la rete solo per controllare e scaricare release.
- I backup .cbtbak sono cifrati e autenticati con una password scelta dall'utente, non contengono chiavi in chiaro sul disco.
- Le credenziali API fanno parte del backup cifrato.
- Il PDF è un documento **non cifrato**: conservare in luogo sicuro.
