package com.frak89.diariocbt;

import android.content.Context;
import android.content.Intent;
import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Optional transcription of existing AAC/M4A recordings using Android's ON-DEVICE recognizer.
 * Audio is decoded to PCM within private cache and fed to EXTRA_AUDIO_SOURCE (API 33+).
 * Availability is dependent on the installed speech service; providers may ignore injected
 * audio or lack segmented recognition. Never fall back to an online speech recognizer.
 */
public final class RecordingTranscriber {
    public interface Callback { void onComplete(String transcript,Exception error); }
    private static final ExecutorService IO=Executors.newSingleThreadExecutor();
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private RecordingTranscriber() {}
    public static boolean supported(Context context){
        return Build.VERSION.SDK_INT>=33 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context);
    }
    public static void start(Context context, File input, Locale language, Callback callback){
        Context app=context.getApplicationContext();
        if(!supported(app)){
            MAIN.post(()->callback.onComplete(null,new IllegalStateException(
                "On-device recorded-audio recognition is not available on this Android device.")));
            return;
        }
        if(input==null||!input.isFile()){
            MAIN.post(()->callback.onComplete(null,new IOException("No audio file to transcribe.")));
            return;
        }
        IO.execute(()->{
            Decoded pcm=null;
            try{
                pcm=decode(app,input);
                Decoded prepared=pcm;
                MAIN.post(()->new RecognitionSession(app,prepared,language,callback).start());
            }catch(Exception ex){
                if(pcm!=null)pcm.file.delete();
                Exception error=ex;
                MAIN.post(()->callback.onComplete(null,error));
            }
        });
    }
    private static class Decoded {
        File file;int sampleRate;int channels;
    }
    private static Decoded decode(Context ctx,File input) throws Exception {
        MediaExtractor extractor=new MediaExtractor();
        MediaCodec decoder=null;
        File temp=File.createTempFile("cbt-transcribe-", ".pcm",ctx.getCacheDir());
        Decoded result=new Decoded();result.file=temp;
        try{
            extractor.setDataSource(input.getAbsolutePath());
            int track=-1;
            for(int i=0;i<extractor.getTrackCount();i++){
                String mime=extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME);
                if(mime!=null&&mime.startsWith("audio/")){track=i;break;}
            }
            if(track<0)throw new IOException("No audio track found");
            extractor.selectTrack(track);
            MediaFormat format=extractor.getTrackFormat(track);
            result.sampleRate=format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
            result.channels=format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
            String mime=format.getString(MediaFormat.KEY_MIME);
            decoder=MediaCodec.createDecoderByType(mime);
            decoder.configure(format,null,null,0);
            decoder.start();
            MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();
            boolean inputEnded=false,outputEnded=false;
            int iterations=0;
            try(OutputStream out=new BufferedOutputStream(new FileOutputStream(temp))){
                while(!outputEnded&&iterations++<50000){
                    if(!inputEnded){
                        int idx=decoder.dequeueInputBuffer(10000);
                        if(idx>=0){
                            ByteBuffer buffer=decoder.getInputBuffer(idx);
                            if(buffer==null)throw new IOException("Codec input buffer unavailable");
                            buffer.clear();
                            int n=extractor.readSampleData(buffer,0);
                            if(n<0){
                                decoder.queueInputBuffer(idx,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                                inputEnded=true;
                            }else{
                                long pts=extractor.getSampleTime();
                                decoder.queueInputBuffer(idx,0,n,Math.max(0,pts),0);
                                extractor.advance();
                            }
                        }
                    }
                    int idx=decoder.dequeueOutputBuffer(info,10000);
                    if(idx==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){
                        MediaFormat pcmFormat=decoder.getOutputFormat();
                        if(pcmFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE))
                            result.sampleRate=pcmFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                        if(pcmFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT))
                            result.channels=pcmFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                        if(pcmFormat.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                            pcmFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)!=AudioFormat.ENCODING_PCM_16BIT)
                            throw new IOException("Speech service requires PCM 16-bit audio.");
                    }else if(idx>=0){
                        ByteBuffer buffer=decoder.getOutputBuffer(idx);
                        if(buffer!=null&&info.size>0){
                            buffer.position(info.offset);buffer.limit(info.offset+info.size);
                            byte[] chunk=new byte[Math.min(16384,info.size)];
                            while(buffer.hasRemaining()){
                                int n=Math.min(chunk.length,buffer.remaining());
                                buffer.get(chunk,0,n);out.write(chunk,0,n);
                            }
                        }
                        outputEnded=(info.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0;
                        decoder.releaseOutputBuffer(idx,false);
                    }
                }
            }
            if(!outputEnded||temp.length()==0)throw new IOException("Audio decoding did not finish");
            if(result.sampleRate<=0||result.channels<1||result.channels>2)
                throw new IOException("Unsupported audio format");
            return result;
        }catch(Exception ex){
            temp.delete();throw ex;
        }finally{
            if(decoder!=null){
                try{decoder.stop();}catch(Exception ignored){}
                decoder.release();
            }
            extractor.release();
        }
    }
    private static final class RecognitionSession implements RecognitionListener {
        private final Context context;
        private final Decoded source;
        private final Locale language;
        private final Callback callback;
        private final AtomicBoolean completed=new AtomicBoolean(false);
        private final StringBuilder segments=new StringBuilder();
        private ParcelFileDescriptor[] pipe;
        private SpeechRecognizer recognizer;
        private Runnable timeout;
        RecognitionSession(Context c,Decoded s,Locale l,Callback cb){
            context=c;source=s;language=l;callback=cb;
        }
        void start(){
            try{
                pipe=ParcelFileDescriptor.createPipe();
                recognizer=SpeechRecognizer.createOnDeviceSpeechRecognizer(context);
                recognizer.setRecognitionListener(this);
                Intent request=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                request.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                request.putExtra(RecognizerIntent.EXTRA_LANGUAGE,language.toLanguageTag());
                request.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE,pipe[0]);
                request.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT,source.channels);
                request.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE,source.sampleRate);
                request.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING,AudioFormat.ENCODING_PCM_16BIT);
                request.putExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION,RecognizerIntent.EXTRA_AUDIO_SOURCE);
                timeout=()->finish(null,new IOException("On-device transcription timeout"));
                MAIN.postDelayed(timeout,180000);
                recognizer.startListening(request);
                ParcelFileDescriptor writer=pipe[1];
                IO.execute(()->{
                    try(InputStream in=new BufferedInputStream(new FileInputStream(source.file));
                        OutputStream out=new ParcelFileDescriptor.AutoCloseOutputStream(writer)){
                        byte[] chunk=new byte[8192];int n;
                        // Stream at audio speed so on-device services have time to consume each segment.
                        while(!completed.get()&&(n=in.read(chunk))!=-1){
                            out.write(chunk,0,n);
                            long ms=Math.max(1,(long)n*1000/(source.sampleRate*source.channels*2L));
                            try{Thread.sleep(ms);}catch(InterruptedException interrupted){
                                Thread.currentThread().interrupt();break;
                            }
                        }
                        out.flush();
                    }catch(Exception e){
                        Exception error=e;
                        MAIN.post(()->finish(null,error));
                    }
                });
            }catch(Exception e){finish(null,e);}
        }
        private void finish(String transcript,Exception error){
            if(!completed.compareAndSet(false,true))return;
            if(timeout!=null)MAIN.removeCallbacks(timeout);
            if(pipe!=null){
                try{pipe[0].close();}catch(Exception ignored){}
                try{pipe[1].close();}catch(Exception ignored){}
            }
            if(recognizer!=null){
                SpeechRecognizer old=recognizer;
                MAIN.post(old::destroy);
            }
            source.file.delete();
            String result=transcript;
            if(result==null&&segments.length()>0)result=segments.toString();
            callback.onComplete(result,error);
        }
        private String getText(Bundle result){
            if(result==null)return "";
            ArrayList<String> guesses=result.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            return guesses==null||guesses.isEmpty()?"":guesses.get(0).trim();
        }
        @Override public void onSegmentResults(Bundle bundle){
            String t=getText(bundle);
            if(!t.isEmpty()){
                if(segments.length()>0)segments.append(' ');
                segments.append(t);
            }
        }
        @Override public void onEndOfSegmentedSession(){
            finish(segments.toString(),null);
        }
        @Override public void onResults(Bundle bundle){
            String t=segments.length()>0?segments.toString():getText(bundle);
            finish(t,null);
        }
        @Override public void onError(int error){
            finish(null,new IOException("Speech service error code "+error));
        }
        @Override public void onReadyForSpeech(Bundle b){}
        @Override public void onBeginningOfSpeech(){}
        @Override public void onRmsChanged(float v){}
        @Override public void onBufferReceived(byte[] data){}
        @Override public void onEndOfSpeech(){}
        @Override public void onPartialResults(Bundle bundle){}
        @Override public void onEvent(int type,Bundle params){}
    }
}
