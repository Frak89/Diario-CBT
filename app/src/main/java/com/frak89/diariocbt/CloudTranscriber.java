package com.frak89.diariocbt;

import android.os.Handler;
import android.os.Looper;
import org.json.JSONObject;

import javax.net.ssl.HttpsURLConnection;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Authenticated server-side speech recognition for already recorded M4A audio.
 * Unlike Android EXTRA_AUDIO_SOURCE this API explicitly accepts saved audio files.
 * Requires the owner's personal API credential and explicit online-audio consent.
 *
 * Do not ship an app-wide secret in a public APK. For multi-user production apps,
 * move this call to a per-user authenticated backend.
 */
public final class CloudTranscriber {
    public interface Callback { void done(String text, Exception error); }
    private static final ExecutorService QUEUE=Executors.newSingleThreadExecutor();
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static final String ENDPOINT="https://api.openai.com/v1/audio/transcriptions";
    private static final long MAX_BYTES=24L*1024*1024;
    private CloudTranscriber(){}

    public static void transcribe(File audio,String lang,String apiKey,Callback callback){
        QUEUE.execute(()->{
            String result=null;Exception error=null;
            try{result=request(audio,lang,apiKey);}
            catch(Exception e){error=e;}
            final String text=result;final Exception issue=error;
            MAIN.post(()->callback.done(text,issue));
        });
    }
    private static String request(File input,String lang,String apiKey) throws Exception {
        if(input==null||!input.isFile()||input.length()==0)
            throw new IOException("Audio recording is missing.");
        if(input.length()>MAX_BYTES)
            throw new IOException("Recording exceeds the transcription service file limit (24 MiB).");
        if(apiKey==null||!apiKey.startsWith("sk-")||apiKey.length()<12)
            throw new IOException("Configure your personal transcription API key in Settings.");
        String language=("en".equals(lang)||"es".equals(lang))?lang:"it";
        String boundary="----CBT-"+UUID.randomUUID().toString().replace("-","");
        byte[] begin=(
            "--"+boundary+"\r\n"+
            "Content-Disposition: form-data; name=\"model\"\r\n\r\n"+
            "gpt-4o-mini-transcribe\r\n"+
            "--"+boundary+"\r\n"+
            "Content-Disposition: form-data; name=\"language\"\r\n\r\n"+
            language+"\r\n"+
            "--"+boundary+"\r\n"+
            "Content-Disposition: form-data; name=\"response_format\"\r\n\r\n"+
            "json\r\n"+
            "--"+boundary+"\r\n"+
            "Content-Disposition: form-data; name=\"file\"; filename=\"recording.m4a\"\r\n"+
            "Content-Type: audio/mp4\r\n\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] finish=("\r\n--"+boundary+"--\r\n").getBytes(StandardCharsets.UTF_8);
        HttpsURLConnection connection=(HttpsURLConnection)new URL(ENDPOINT).openConnection();
        connection.setConnectTimeout(20000);
        connection.setReadTimeout(120000);
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setUseCaches(false);
        connection.setRequestProperty("Authorization","Bearer "+apiKey);
        connection.setRequestProperty("Content-Type","multipart/form-data; boundary="+boundary);
        connection.setRequestProperty("Accept","application/json");
        connection.setFixedLengthStreamingMode((long)begin.length+input.length()+finish.length);
        try{
            try(OutputStream out=connection.getOutputStream();
                BufferedInputStream in=new BufferedInputStream(new FileInputStream(input))){
                out.write(begin);
                byte[] chunk=new byte[65536];int n;
                while((n=in.read(chunk))!=-1)out.write(chunk,0,n);
                out.write(finish);out.flush();
            }
            int status=connection.getResponseCode();
            if(status==401||status==403)
                throw new IOException("API credential not authorized (HTTP "+status+").");
            if(status==429)
                throw new IOException("Transcription service rate limit or billing limit (HTTP 429).");
            if(status<200||status>=300)
                throw new IOException("Transcription request rejected (HTTP "+status+").");
            StringBuilder body=new StringBuilder();
            try(BufferedReader reader=new BufferedReader(new InputStreamReader(
               connection.getInputStream(),StandardCharsets.UTF_8))){
                char[] buf=new char[4096];int len;
                while((len=reader.read(buf))!=-1){
                    body.append(buf,0,len);
                    if(body.length()>1024*1024)throw new IOException("Oversized transcription response");
                }
            }
            String text=new JSONObject(body.toString()).optString("text","").trim();
            if(text.isEmpty())throw new IOException("The service did not recognize any speech.");
            return text;
        }finally{connection.disconnect();}
    }
}
