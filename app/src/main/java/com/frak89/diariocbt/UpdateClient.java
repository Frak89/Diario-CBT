package com.frak89.diariocbt;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** User-approved, non-silent updates from signed GitHub Releases.
 * The Android package manager independently enforces same-package/same-signature.
 * GitHub Actions' ephemeral DEBUG builds CANNOT update an APK signed by another key.
 */
public final class UpdateClient {
    private static final String API="https://api.github.com/repos/Frak89/Diario-CBT/releases/latest";
    private static final ExecutorService IO=Executors.newSingleThreadExecutor();
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static final String STORE="updates";
    private UpdateClient(){}
    private static int[] version(String text){
        String[] parts=text.replaceFirst("^[vV]","").split("\\.");
        int[] result=new int[]{0,0,0};
        for(int i=0;i<Math.min(parts.length,3);i++){
            String p=parts[i].replaceAll("[^0-9].*$","");
            try{result[i]=Integer.parseInt(p);}catch(Exception ignored){}
        }
        return result;
    }
    private static boolean newer(String remote,String local){
        int[] a=version(remote),b=version(local);
        for(int i=0;i<3;i++){
            if(a[i]>b[i])return true;
            if(a[i]<b[i])return false;
        }
        return false;
    }
    public static void check(Activity activity,boolean manual){
        IO.execute(()->{
            try {
                HttpURLConnection connection=(HttpURLConnection)new URL(API).openConnection();
                connection.setConnectTimeout(13000);connection.setReadTimeout(13000);
                connection.setRequestProperty("Accept","application/vnd.github+json");
                connection.setRequestProperty("User-Agent","DiarioCBT-Android-Updater");
                int status=connection.getResponseCode();
                if(status==404){
                    MAIN.post(()->{
                        if(manual&&!activity.isFinishing())new AlertDialog.Builder(activity)
                            .setTitle("Aggiornamenti non ancora attivati")
                            .setMessage("Il progetto non ha ancora una release firmata in modo stabile. Il controllo automatico funzionerà dopo la prima release ufficiale. Gli APK di prova GitHub Actions possono avere firme differenti e non si aggiornano in sicurezza uno sopra l'altro.")
                            .setPositiveButton("OK",null).show();
                    });
                    connection.disconnect();return;
                }
                if(status!=200)throw new IOException("GitHub update service HTTP "+status);
                StringBuilder json=new StringBuilder();
                try(InputStreamReader in=new InputStreamReader(connection.getInputStream(),"UTF-8")){
                    char[] buf=new char[4096];int n;
                    while((n=in.read(buf))!=-1){
                        json.append(buf,0,n);
                        if(json.length()>2000000)throw new IOException("Oversized update response");
                    }
                }finally{connection.disconnect();}
                JSONObject release=new JSONObject(json.toString());
                if(release.optBoolean("draft",false)||release.optBoolean("prerelease",false))return;
                String tag=release.optString("tag_name","");
                String current=activity.getPackageManager()
                    .getPackageInfo(activity.getPackageName(),0).versionName;
                if(!newer(tag,current)){
                    if(manual)MAIN.post(()->{
                        if(!activity.isFinishing())new AlertDialog.Builder(activity)
                            .setMessage("Diario CBT "+current+" è già aggiornato.")
                            .setPositiveButton("OK",null).show();
                    });
                    return;
                }
                boolean full=ModelManager.bundled(activity);
                JSONArray assets=release.optJSONArray("assets");
                String url=null;
                if(assets!=null){
                    for(int i=0;i<assets.length();i++){
                        JSONObject asset=assets.optJSONObject(i);
                        if(asset==null)continue;
                        String name=asset.optString("name","");
                        String candidate=asset.optString("browser_download_url","");
                        if(!name.endsWith(".apk"))continue;
                        if(full&&!name.contains("FULL-AI"))continue;
                        if(!full&&!name.contains("Compact"))continue;
                        if(!candidate.startsWith("https://github.com/Frak89/Diario-CBT/releases/download/"))
                            continue;
                        url=candidate;break;
                    }
                }
                String download=url;
                MAIN.post(()->{
                    if(activity.isFinishing())return;
                    AlertDialog.Builder prompt=new AlertDialog.Builder(activity)
                        .setTitle("Diario CBT "+tag+" disponibile")
                        .setMessage("Nuova versione rilevata da GitHub. Il download richiede una conferma e Android verificherà la firma digitale prima di consentire l'installazione sopra quella attuale. I dati restano sul dispositivo se la firma è compatibile.")
                        .setNegativeButton("Più tardi",null);
                    if(download!=null)prompt.setPositiveButton("Scarica aggiornamento",
                        (dialog,which)->download(activity,download));
                    else prompt.setMessage("Nuova versione rilevata, ma manca un APK compatibile con la variante installata. Apri la release su GitHub per controllare.");
                    prompt.show();
                });
            }catch(Exception error){
                if(manual)MAIN.post(()->{
                    if(!activity.isFinishing())new AlertDialog.Builder(activity)
                        .setTitle("Controllo aggiornamenti non riuscito")
                        .setMessage(error.getMessage()).setPositiveButton("OK",null).show();
                });
            }
        });
    }
    private static void download(Activity activity,String url){
        try{
            DownloadManager dm=(DownloadManager)activity.getSystemService(Context.DOWNLOAD_SERVICE);
            if(dm==null)throw new IOException("DownloadManager not available");
            DownloadManager.Request req=new DownloadManager.Request(Uri.parse(url))
                .setTitle("Diario CBT — aggiornamento")
                .setDescription("APK firmato da verificare con Android")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setMimeType("application/vnd.android.package-archive")
                .setDestinationInExternalFilesDir(activity,Environment.DIRECTORY_DOWNLOADS,
                    "diario-cbt-update-"+System.currentTimeMillis()+".apk");
            long id=dm.enqueue(req);
            activity.getSharedPreferences(STORE,0).edit().putLong("downloadId",id).apply();
            new AlertDialog.Builder(activity).setTitle("Download avviato")
                .setMessage("Quando sarà completo, torna nell'app e usa Impostazioni → Installa aggiornamento scaricato. Il programma di installazione di Android richiederà la tua conferma.")
                .setPositiveButton("OK",null).show();
        }catch(Exception error){
            new AlertDialog.Builder(activity).setMessage(error.getMessage())
                .setPositiveButton("OK",null).show();
        }
    }
    public static void installDownloaded(Activity activity){
        long id=activity.getSharedPreferences(STORE,0).getLong("downloadId",-1L);
        if(id<0){
            new AlertDialog.Builder(activity).setMessage("Nessun aggiornamento scaricato dall'app.")
                .setPositiveButton("OK",null).show();return;
        }
        DownloadManager dm=(DownloadManager)activity.getSystemService(Context.DOWNLOAD_SERVICE);
        if(dm==null)return;
        DownloadManager.Query q=new DownloadManager.Query().setFilterById(id);
        try(android.database.Cursor c=dm.query(q)){
            if(c==null||!c.moveToFirst())throw new IOException("Download non trovato");
            int status=c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
            if(status!=DownloadManager.STATUS_SUCCESSFUL){
                throw new IOException(status==DownloadManager.STATUS_FAILED?
                    "Download non riuscito. Ripeti il controllo degli aggiornamenti.":
                    "Download ancora in corso.");
            }
            Uri uri=dm.getUriForDownloadedFile(id);
            if(uri==null)throw new IOException("APK scaricato non accessibile");
            if(Build.VERSION.SDK_INT>=26 && !activity.getPackageManager().canRequestPackageInstalls()){
                new AlertDialog.Builder(activity)
                    .setTitle("Autorizzazione installazione")
                    .setMessage("Autorizza l'installazione da Diario CBT nelle impostazioni Android, quindi premi nuovamente Installa aggiornamento. Il sistema controllerà che la firma sia compatibile.")
                    .setNegativeButton("Annulla",null)
                    .setPositiveButton("Impostazioni",(d,w)->activity.startActivity(new Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:"+activity.getPackageName()))))
                    .show();
                return;
            }
            Intent install=new Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri,"application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(install);
        }catch(Exception error){
            new AlertDialog.Builder(activity).setTitle("Aggiornamento non installato")
                .setMessage(error.getMessage())
                .setPositiveButton("OK",null).show();
        }
    }
}
