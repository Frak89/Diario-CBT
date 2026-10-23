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
    private static final String OPENAI_ENDPOINT="https://api.openai.com/v1/audio/transcriptions";
    private static final String GROQ_ENDPOINT="https://api.groq.com/openai/v1/audio/transcriptions";
    private static final long MAX_BYTES=24L*1024*1024;
    private CloudTranscriber(){}
    /** Sanitized API diagnosis only. Never include request headers, secrets or recordings. */
    public static final class ApiError extends IOException {
        public final int httpStatus;
        public final String apiCode;
        public final String category;
        public final String retryAfter;
        ApiError(int status,String code,String category,String retryAfter,String message){
            super(message);
            this.httpStatus=status;
            this.apiCode=code;
            this.category=category;
            this.retryAfter=retryAfter;
        }
        public boolean billingRelated(){
            return "credit_balance_exhausted".equals(apiCode)
                || "organization_spend_limit_exceeded".equals(apiCode)
                || "project_spend_limit_exceeded".equals(apiCode)
                || "organization_usage_limit_exceeded".equals(apiCode)
                || "insufficient_quota".equals(apiCode)
                || "insufficient_quota".equals(category);
        }
    }
    private static String describeError(int http,String code,String type,String language,String retryAfter){
        String it,en,es;
        switch(code){
            case "credit_balance_exhausted":
                it="Credito API esaurito. Controlla il saldo o aggiungi credito su platform.openai.com.";
                en="API credit balance exhausted. Check your balance on platform.openai.com.";
                es="Crédito API agotado. Revisa el saldo en platform.openai.com.";break;
            case "organization_spend_limit_exceeded":
            case "project_spend_limit_exceeded":
                it="Limite di spesa API raggiunto. Controlla i limiti di progetto e organizzazione.";
                en="API spend limit reached. Check project and organization spending limits.";
                es="Límite de gasto API alcanzado. Revisa los límites de proyecto y organización.";break;
            case "organization_usage_limit_exceeded":
                it="Limite di utilizzo API dell'organizzazione raggiunto.";
                en="Organization API usage limit reached.";
                es="Límite de uso API de la organización alcanzado.";break;
            case "rate_limit_exceeded":
                it="Troppe richieste API in poco tempo. Riprova quando il limite si azzera.";
                en="Too many API requests. Retry once the rate limit resets.";
                es="Demasiadas solicitudes API. Reintenta cuando el límite se restablezca.";break;
            default:
                if("insufficient_quota".equals(type)){
                    it="Credito o quota API insufficiente. Controlla fatturazione e limiti API.";
                    en="Insufficient API balance or quota. Check API billing and usage limits.";
                    es="Saldo o cuota API insuficiente. Revisa facturación y límites de la API.";
                }else if(http==429){
                    it="HTTP 429: limite di richieste o credito API non disponibile. Verifica saldo e limiti.";
                    en="HTTP 429: API rate limit or insufficient balance. Check balance and limits.";
                    es="HTTP 429: límite de solicitudes o saldo insuficiente. Revisa el saldo y los límites.";
                }else if(http==401||http==403){
                    it="Chiave API non autorizzata. Controlla progetto, permessi e chiave.";
                    en="API key not authorized. Check project, permissions and key.";
                    es="Clave API no autorizada. Revisa el proyecto, permisos y clave.";
                }else{
                    it="Errore del servizio di trascrizione (HTTP "+http+").";
                    en="Transcription service error (HTTP "+http+").";
                    es="Error del servicio de transcripción (HTTP "+http+").";
                }
        }
        String message=I18n.t(language,it,en,es);
        if(http==429 && retryAfter!=null&&!retryAfter.isEmpty()&&retryAfter.matches("[0-9]{1,5}"))
            message+=" "+I18n.t(language,"Attesa suggerita: ","Suggested wait: ","Espera sugerida: ")
              +retryAfter+" s.";
        return message+" [HTTP "+http+(code.isEmpty()?"":", "+code)+"]";
    }
    private static ApiError decodeError(HttpsURLConnection connection,int status,String language) {
        String code="",type="";
        try(java.io.InputStream stream=connection.getErrorStream()){
            if(stream!=null){
                byte[] data=new byte[16384];
                int total=0,n;
                while(total<data.length&&(n=stream.read(data,total,data.length-total))>0)total+=n;
                String json=new String(data,0,total,StandardCharsets.UTF_8);
                JSONObject error=new JSONObject(json).optJSONObject("error");
                if(error!=null){
                    code=error.optString("code","");
                    type=error.optString("type","");
                }
            }
        }catch(Exception ignored){}
        if(code==null)code="";if(type==null)type="";
        String retryAfter=connection.getHeaderField("Retry-After");
        return new ApiError(status,code,type,retryAfter,
            describeError(status,code,type,language,retryAfter));
    }

    public static void transcribe(File audio,String lang,String apiKey,String provider,Callback callback){
        QUEUE.execute(()->{
            String result=null;Exception error=null;
            try{result=request(audio,lang,apiKey,provider);}
            catch(Exception e){error=e;}
            final String text=result;final Exception issue=error;
            MAIN.post(()->callback.done(text,issue));
        });
    }
    private static String request(File input,String lang,String apiKey,String provider) throws Exception {
        boolean groq="groq".equals(provider);
        if(!groq && !"openai".equals(provider))throw new IOException("Unsupported transcription provider");
        if(input==null||!input.isFile()||input.length()==0)
            throw new IOException("Audio recording is missing.");
        if(input.length()>MAX_BYTES)
            throw new IOException("Recording exceeds the transcription service file limit (24 MiB).");
        if(apiKey==null||apiKey.length()<16||(groq?!apiKey.startsWith("gsk_"):!apiKey.startsWith("sk-")))
            throw new IOException("Configure a valid "+(groq?"Groq (gsk_...)":"OpenAI (sk-...)")+" API key in Settings.");
        String language=("en".equals(lang)||"es".equals(lang))?lang:"it";
        String boundary="----CBT-"+UUID.randomUUID().toString().replace("-","");
        String model=groq?"whisper-large-v3":"gpt-4o-mini-transcribe";
        byte[] begin=(
            "--"+boundary+"\r\n"+
            "Content-Disposition: form-data; name=\"model\"\r\n\r\n"+
            model+"\r\n"+
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
        HttpsURLConnection connection=(HttpsURLConnection)new URL(groq?GROQ_ENDPOINT:OPENAI_ENDPOINT).openConnection();
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
            if(status<200||status>=300)
                throw decodeError(connection,status,language);
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
