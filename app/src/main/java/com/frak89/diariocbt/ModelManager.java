package com.frak89.diariocbt;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Public Apache-2.0 Qwen3 0.6B weights, automatically installed on device once. */
public final class ModelManager {
    public static final String MODEL="Qwen3-0.6B";
    public static final long BYTES=613722264L;
    public static final String SHA256="c5f39b4191926786c3e6668b62af4d6eb15248dfc7a5348252a9b406f33f2108";
    public static final String URL="https://huggingface.co/litert-community/Qwen3-0.6B/resolve/main/Qwen3-0.6B.litertlm";
    public interface Listener {
        void progress(long done,long total);
        void completed(Exception error);
    }
    private static final ExecutorService QUEUE=Executors.newSingleThreadExecutor();
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private ModelManager(){}
    public static File file(Context ctx){
        File folder=new File(ctx.getFilesDir(),"models");
        if(!folder.exists() && !folder.mkdirs()) throw new IllegalStateException("Cannot create model folder");
        return new File(folder,"Qwen3-0.6B.litertlm");
    }
    public static boolean ready(Context ctx) {
        File f=file(ctx);
        return f.isFile()&&f.length()==BYTES&&
          SHA256.equals(ctx.getSharedPreferences("model_integrity",0).getString("sha",""));
    }
    public static void download(Context context,AtomicBoolean cancelled,Listener listener) {
        final Context app=context.getApplicationContext();
        QUEUE.execute(()->{
            Exception issue=null;
            try {downloadNow(app,cancelled,listener);}
            catch(Exception ex){issue=ex;}
            Exception result=issue;
            MAIN.post(()->listener.completed(result));
        });
    }
    private static void downloadNow(Context app,AtomicBoolean cancelled,Listener listener) throws Exception {
        File dest=file(app),part=new File(dest.getParentFile(),"Qwen3-0.6B.part");
        if(ready(app)){MAIN.post(()->listener.progress(BYTES,BYTES));return;}
        if(part.length()>BYTES)part.delete();
        long existing=part.length();
        HttpURLConnection conn=(HttpURLConnection)new URL(URL).openConnection();
        conn.setConnectTimeout(25000);conn.setReadTimeout(60000);
        conn.setInstanceFollowRedirects(true);
        conn.setRequestProperty("User-Agent","DiarioCBT/0.2 Android");
        if(existing>0)conn.setRequestProperty("Range","bytes="+existing+"-");
        try {
            int code=conn.getResponseCode();
            if(code!=200&&code!=206)throw new IOException("Download error HTTP "+code);
            if(code==200 && existing>0){existing=0;}
            if(code==206&&existing==0)throw new IOException("Unexpected partial response");
            long offset=existing;
            try(InputStream in=new BufferedInputStream(conn.getInputStream());
                FileOutputStream out=new FileOutputStream(part,offset>0)) {
                byte[] chunk=new byte[128*1024];int n;
                long lastReported=0;
                while((n=in.read(chunk))!=-1){
                    if(cancelled.get())throw new InterruptedIOException("Download stopped");
                    out.write(chunk,0,n);offset+=n;
                    if(offset>BYTES)throw new IOException("Oversized model download");
                    if(offset-lastReported>2*1024*1024){
                        lastReported=offset;long value=offset;
                        MAIN.post(()->listener.progress(value,BYTES));
                    }
                }
                out.getFD().sync();
            }
        } finally {conn.disconnect();}
        if(part.length()!=BYTES)throw new IOException("Incomplete model: "+part.length()+" / "+BYTES);
        MessageDigest hash=MessageDigest.getInstance("SHA-256");
        try(InputStream in=new BufferedInputStream(new FileInputStream(part))){
            byte[] chunk=new byte[1024*1024];int n;
            while((n=in.read(chunk))!=-1){
                if(cancelled.get())throw new InterruptedIOException("Download stopped");
                hash.update(chunk,0,n);
            }
        }
        StringBuilder result=new StringBuilder();
        for(byte b:hash.digest())result.append(String.format(Locale.ROOT,"%02x",b&255));
        if(!SHA256.equals(result.toString())){
            part.delete();throw new IOException("Model SHA-256 does not match. Retry on a trusted network.");
        }
        try {Files.move(part.toPath(),dest.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
        catch(AtomicMoveNotSupportedException ex){
            Files.move(part.toPath(),dest.toPath(),StandardCopyOption.REPLACE_EXISTING);
        }
        if(!app.getSharedPreferences("model_integrity",0).edit().putString("sha",SHA256).commit())
           throw new IOException("Unable to mark verified model");
        MAIN.post(()->listener.progress(BYTES,BYTES));
    }
}
