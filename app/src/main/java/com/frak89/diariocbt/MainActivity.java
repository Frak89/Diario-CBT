package com.frak89.diariocbt;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.graphics.drawable.*;
import android.media.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.speech.*;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.text.*;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    private final int BACK=0xff0a0d20, CARD=0xff151b35, FG=0xffe8f3ff, SUB=0xffaab9d3,
      CYAN=0xff63e8f2, GOLD=0xffffc54a, PURPLE=0xffad89ff;
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private JSONObject document;
    private String date,lang;
    private int step=0;
    private LinearLayout layout;
    private final ArrayList<EditText> currentFields=new ArrayList<>();
    private final ArrayList<String> currentKeys=new ArrayList<>();
    private TextToSpeech tts;
    private SpeechRecognizer recognizer;
    private MediaRecorder recorder;
    private MediaPlayer player;
    private String recordingKey;
    private float downX,downY;
    private boolean building;
    private int d(float px) {return (int)(px*getResources().getDisplayMetrics().density+.5f);}
    private String tr(String it,String en,String es) {return I18n.t(lang,it,en,es);}
    private void notifyUser(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BACK);getWindow().setNavigationBarColor(BACK);
        date=LocalDate.now().toString();lang=I18n.lang(this);
        try{document=SecureStore.read(this);}
        catch(Exception ex) {
            new AlertDialog.Builder(this).setTitle("Errore archivio cifrato")
             .setMessage("Impossibile decifrare i dati. I dati originali non verranno sovrascritti.")
             .setPositiveButton("Chiudi",(dialog,which)->finish()).setCancelable(false).show();
            return;
        }
        if(state!=null) step=state.getInt("step",0);
        tts=new TextToSpeech(this,status->{if(status==TextToSpeech.SUCCESS) configureVoice();});
        render();
    }
    @Override protected void onSaveInstanceState(Bundle state){saveDraft();state.putInt("step",step);super.onSaveInstanceState(state);}
    @Override protected void onPause(){saveDraft();super.onPause();}
    @Override protected void onDestroy(){
        if(tts!=null){tts.stop();tts.shutdown();}
        if(recognizer!=null) recognizer.destroy();
        if(recorder!=null){try{recorder.stop();}catch(Exception ignored){} recorder.release();}
        if(player!=null){player.release();}
        io.shutdown();super.onDestroy();
    }
    @Override public boolean dispatchTouchEvent(android.view.MotionEvent ev){
        if(ev.getActionMasked()==MotionEvent.ACTION_DOWN){downX=ev.getX();downY=ev.getY();}
        if(ev.getActionMasked()==MotionEvent.ACTION_UP){
            float dx=ev.getX()-downX, dy=ev.getY()-downY;
            if(Math.abs(dx)>d(120)&&Math.abs(dx)>Math.abs(dy)*1.8f){
                navigate(step+(dx<0?1:-1));return true;
            }
        }
        return super.dispatchTouchEvent(ev);
    }
    private JSONObject session() {
        JSONObject sessions=document.optJSONObject("sessions");
        if(sessions==null){sessions=new JSONObject();set(document,"sessions",sessions);}
        JSONObject s=sessions.optJSONObject(date);
        if(s==null){s=new JSONObject();set(sessions,date,s);}
        return s;
    }
    private void set(JSONObject obj,String key,Object v) {try{obj.put(key,v);}catch(Exception e){throw new IllegalStateException(e);}}
    private void persist(){try{SecureStore.write(this,document);}catch(Exception ex){notifyUser("Errore salvataggio: "+ex.getMessage());}}
    private void saveDraft(){
        if(building||document==null||currentFields.size()!=currentKeys.size())return;
        JSONObject s=session();boolean dirty=false;
        for(int j=0;j<currentFields.size();j++){
            String value=currentFields.get(j).getText().toString();
            String key=currentKeys.get(j);
            if(!value.equals(s.optString(key,""))){set(s,key,value);dirty=true;}
        }
        if(dirty)persist();
    }
    private void navigate(int target){
        if(target<0||target>6)return;
        saveDraft();step=target;render();
    }
    private GradientDrawable bg(int color,int border) {
        GradientDrawable out=new GradientDrawable();out.setColor(color);
        out.setCornerRadius(d(16));out.setStroke(d(1),border);return out;
    }
    private TextView label(String text,int size,int color,boolean bold) {
        TextView v=new TextView(this);v.setText(text);v.setTextSize(size);v.setTextColor(color);
        if(bold)v.setTypeface(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD);
        return v;
    }
    private LinearLayout col(){LinearLayout l=new LinearLayout(this);l.setOrientation(1);return l;}
    private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(0);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private void space(LinearLayout p,int px){p.addView(new View(this),new LinearLayout.LayoutParams(1,d(px)));}
    private Button button(String text, boolean primary){
        Button b=new Button(this);b.setAllCaps(false);b.setText(text);b.setTextSize(14);
        b.setTextColor(primary?BACK:FG);b.setBackground(bg(primary?CYAN:CARD,primary?CYAN:0xff35405e));
        b.setPadding(d(12),d(6),d(12),d(6));return b;
    }
    private void render(){
        if(document==null)return;
        building=true;currentFields.clear();currentKeys.clear();
        ScrollView sc=new ScrollView(this);sc.setFillViewport(true);sc.setBackgroundColor(BACK);
        layout=col();layout.setPadding(d(18),d(16),d(18),d(24));sc.addView(layout);
        setContentView(sc);
        LinearLayout top=row();
        TextView brand=label("🧠  CBT",27,GOLD,true);
        top.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        Button settings=button("⚙",false);settings.setOnClickListener(v->{saveDraft();showSettings();});
        top.addView(settings);layout.addView(top);space(layout,14);
        String[] titles=I18n.headings(lang);
        TextView heading=label(titles[step],24,FG,true);layout.addView(heading);
        space(layout,8);
        TextView progress=label((step+1)+"/7  ·  "+date+"  ·  "+
           tr("Serie","Streak","Racha")+": "+streak()+" 🔥",13,SUB,false);layout.addView(progress);
        space(layout,18);
        LinearLayout card=col();card.setBackground(bg(CARD,0xff2c3654));
        card.setPadding(d(15),d(18),d(15),d(18));layout.addView(card);
        if(step<6){
            String[][] qs=I18n.questions(lang);
            for(int i=0;i<qs[step].length;i++) addQuestion(card,qs[step][i],"p"+step+"_"+i,i);
        } else showFinal(card);
        space(layout,22);
        LinearLayout foot=row();
        if(step>0){
            Button prev=button("← "+tr("Indietro","Back","Atrás"),false);
            foot.addView(prev,new LinearLayout.LayoutParams(0,d(52),1));
            prev.setOnClickListener(v->navigate(step-1));
        }
        if(step>0) foot.addView(new View(this),new LinearLayout.LayoutParams(d(10),1));
        Button next=button(step==6?tr("Termina","Done","Terminar"):tr("Avanti →","Next →","Siguiente →"),true);
        foot.addView(next,new LinearLayout.LayoutParams(0,d(52),1));
        next.setOnClickListener(v->{if(step<6)navigate(step+1);else notifyUser(tr(
           "La tua sessione è salvata.","Your session is saved.","Tu sesión está guardada."));});
        layout.addView(foot);
        if(step<6){
            space(layout,10);
            TextView hint=label(tr("Scorri a sinistra o destra per cambiare pagina.",
                "Swipe left or right to change page.","Desliza a izquierda o derecha para cambiar de página."),
                12,SUB,false);layout.addView(hint);
        }
        building=false;
    }
    private void addQuestion(LinearLayout card,String question,String key,int qIndex){
        TextView title=label(question,16,FG,true);card.addView(title);space(card,6);
        EditText answer=new EditText(this);
        answer.setTextSize(16);answer.setTextColor(FG);answer.setHintTextColor(0xff7e90aa);
        answer.setBackground(bg(BACK,0xff354362));
        answer.setPadding(d(12),d(12),d(12),d(12));
        answer.setGravity(Gravity.TOP|Gravity.START);
        if(step==1){
            answer.setSingleLine(true);answer.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
            answer.setHint("0–10");
        } else {
            answer.setMinLines(2);answer.setMaxLines(6);
            answer.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE|
                android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
            answer.setHint(tr("Scrivi qui oppure usa il microfono…","Type or use the microphone…",
              "Escribe aquí o usa el micrófono…"));
        }
        answer.setText(session().optString(key,""));
        currentFields.add(answer);currentKeys.add(key);
        card.addView(answer,new LinearLayout.LayoutParams(-1,-2));space(card,6);
        answer.addTextChangedListener(new TextWatcher(){
            @Override public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            @Override public void onTextChanged(CharSequence s,int start,int before,int count){
                if(!building){set(session(),key,s.toString());persist();}
            }
            @Override public void afterTextChanged(Editable e){}
        });
        LinearLayout tools=row();
        Button listen=button("🔊",false);listen.setContentDescription(tr("Ascolta domanda","Read question aloud","Escuchar pregunta"));
        listen.setOnClickListener(v->speak(question));tools.addView(listen,new LinearLayout.LayoutParams(0,d(45),1));
        Button dictate=button("🎙",false);dictate.setContentDescription(tr("Dettatura locale","On-device dictation","Dictado local"));
        dictate.setOnClickListener(v->dictate(answer));tools.addView(dictate,new LinearLayout.LayoutParams(0,d(45),1));
        Button rec=button("⏺",false);rec.setContentDescription(tr("Registra voce","Record voice","Grabar voz"));
        rec.setOnClickListener(v->toggleRecord(key,rec));tools.addView(rec,new LinearLayout.LayoutParams(0,d(45),1));
        Button play=button("▶",false);play.setContentDescription(tr("Riascolta risposta","Replay recording","Reproducir grabación"));
        play.setOnClickListener(v->playRecording(key));tools.addView(play,new LinearLayout.LayoutParams(0,d(45),1));
        card.addView(tools);space(card,18);
    }
    private void showFinal(LinearLayout panel) {
        JSONObject s=session();
        TextView score=label(tr("Indicatore pratica CBT","CBT practice index","Índice de práctica CBT")+"   "+score()+"/10",
         19,GOLD,true);panel.addView(score);space(panel,6);
        TextView explain=label(tr("Misura gli esercizi compilati, non la salute mentale o il valore personale.",
             "Measures completed reflection tasks, not mental health or personal worth.",
             "Mide ejercicios completados, no la salud mental ni el valor personal."),
              13,SUB,false);panel.addView(explain);space(panel,18);
        Button generate=button(tr("Concludi e analizza","Complete and reflect","Terminar y analizar"),true);
        generate.setOnClickListener(v->completeAndAnalyze());panel.addView(generate);
        space(panel,18);
        String report=s.optString("report","");
        boolean ai=s.optBoolean("aiReport",false);
        TextView title=label(ai?tr("OSSERVAZIONE IA LOCALE","LOCAL AI OBSERVATION","OBSERVACIÓN IA LOCAL"):
           tr("RIFLESSIONE GUIDATA (senza IA)","GUIDED REFLECTION (no AI)","REFLEXIÓN GUIADA (sin IA)"),
            16,CYAN,true);panel.addView(title);space(panel,10);
        TextView observations=label(report.isEmpty()?tr(
           "Completa il diario e premi «Concludi e analizza».",
           "Finish the journal and tap Complete and reflect.",
           "Termina el diario y pulsa Terminar y analizar."):report,16,FG,false);
        panel.addView(observations);space(panel,15);
        TextView note=label(tr("Non è una diagnosi o un giudizio clinico.",
            "Not a diagnosis or clinical judgement.","No es un diagnóstico ni un juicio clínico."),12,SUB,false);
        panel.addView(note);
    }
    private int score(){
        JSONObject s=session();int n=0;
        for(String k:new String[]{"p2_0","p2_1","p3_0","p3_1","p4_1","p5_0"})
            if(!s.optString(k,"").trim().isEmpty())n++;
        return (int)Math.round(n*10.0/6.0);
    }
    private int streak(){
        JSONObject hist=document.optJSONObject("history");if(hist==null)return 0;
        LocalDate day=LocalDate.now();if(!hist.has(day.toString()))day=day.minusDays(1);
        int count=0;while(count<3650&&hist.has(day.toString())){count++;day=day.minusDays(1);}
        return count;
    }
    private String diaryText(){
        JSONObject s=session();String[][] prompts=I18n.questions(lang);StringBuilder b=new StringBuilder();
        for(int p=0;p<6;p++)for(int i=0;i<3;i++){
            String val=s.optString("p"+p+"_"+i,"").trim();
            if(!val.isEmpty())b.append(prompts[p][i]).append(": ").append(val).append("\n");
        }
        return b.toString();
    }
    private String fallback(){
        JSONObject s=session();String action=s.optString("p5_0","").trim();
        String alt=s.optString("p4_1","").trim();
        String t=tr("OSSERVAZIONE: Hai dedicato tempo a distinguere fatti, pensieri e sentimenti.",
          "OBSERVATION: You took time to distinguish facts, thoughts, and feelings.",
          "OBSERVACIÓN: Dedicaste tiempo a distinguir hechos, pensamientos y emociones.");
        String a=tr("ALTERNATIVA: Verifica se il pensiero è un fatto o un'ipotesi.",
          "ALTERNATIVE: Check whether the thought is a fact or a hypothesis.",
          "ALTERNATIVA: Comprueba si el pensamiento es un hecho o una hipótesis.");
        if(!alt.isEmpty())a+="\n"+tr("La tua alternativa: ","Your alternative: ","Tu alternativa: ")+alt;
        String next=action.isEmpty()?
          tr("PROSSIMI PASSI: Scegli un'azione da due minuti, poi annota che effetto ha avuto.",
           "NEXT STEPS: Choose a two-minute action, then note what happened.",
           "PRÓXIMOS PASOS: Elige una acción de dos minutos y anota el resultado."):
          tr("PROSSIMI PASSI: Prova questa azione concreta: ",
             "NEXT STEPS: Try this concrete action: ","PRÓXIMOS PASOS: Prueba esta acción concreta: ")+action;
        return t+"\n\n"+a+"\n\n"+next;
    }
    private void completeAndAnalyze(){
        saveDraft();
        JSONObject history=document.optJSONObject("history");
        if(history==null){history=new JSONObject();set(document,"history",history);}
        set(history,date,true);
        JSONObject s=session();
        set(s,"report",fallback());set(s,"aiReport",false);persist();render();
        if(!AiEngine.available(this)){
            notifyUser(tr("Per una valutazione generativa, importa il modello IA nelle impostazioni.",
              "Import a local AI model in Settings for a generated reflection.",
              "Importa un modelo IA local en Ajustes para generar la reflexión."));
            return;
        }
        String d=document.optString("diagnosis",""),g=document.optString("goals","");
        String diary=diaryText();
        notifyUser(tr("Analisi IA offline in corso…","Offline AI reflection…","Análisis IA sin conexión…"));
        AiEngine.report(this,lang,d,g,diary,(report,error)->{
            if(isFinishing())return;
            if(error!=null||report==null||report.trim().isEmpty()){
                notifyUser(tr("IA non disponibile: mantenuta la riflessione guidata.",
                   "AI unavailable: guided reflection kept.","IA no disponible: se mantiene la reflexión guiada."));
                return;
            }
            set(session(),"report",report.trim());set(session(),"aiReport",true);persist();
            if(step==6)render();
            AiEngine.encouragement(this,lang,d,g,(phrase,pError)->{
                if(pError==null&&phrase!=null&&!phrase.trim().isEmpty()&&phrase.length()<240){
                    set(document,"splashPhrase",phrase.trim());
                    set(document,"splashDiagnosis",d);persist();
                }
            });
        });
    }
    private void configureVoice(){
        if(tts==null)return;
        tts.setLanguage(I18n.locale(lang));
        Set<Voice> voices=tts.getVoices();
        if(voices!=null)for(Voice voice:voices){
            if(!voice.isNetworkConnectionRequired()&&voice.getLocale().getLanguage().equals(lang)){
                if(tts.setVoice(voice)==TextToSpeech.SUCCESS)break;
            }
        }
    }
    private void speak(String text){
        if(tts==null)return;configureVoice();
        tts.speak(text,TextToSpeech.QUEUE_FLUSH,null,"question");
    }
    private boolean micPermission(){
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED)return true;
        requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},501);
        notifyUser(tr("Autorizza il microfono e riprova.","Allow microphone access and retry.","Permite acceso al micrófono y reintenta."));
        return false;
    }
    private void dictate(EditText target){
        if(!micPermission())return;
        if(Build.VERSION.SDK_INT<31||!SpeechRecognizer.isOnDeviceRecognitionAvailable(this)){
            notifyUser(tr("Riconoscimento vocale locale non disponibile su questo dispositivo.",
              "On-device speech recognition not available.","El reconocimiento local de voz no está disponible."));
            return;
        }
        if(recognizer!=null){recognizer.destroy();recognizer=null;}
        recognizer=SpeechRecognizer.createOnDeviceSpeechRecognizer(this);
        recognizer.setRecognitionListener(new RecognitionListener(){
            public void onReadyForSpeech(Bundle b){}
            public void onBeginningOfSpeech(){}
            public void onRmsChanged(float v){}
            public void onBufferReceived(byte[] data){}
            public void onEndOfSpeech(){}
            public void onError(int error){notifyUser(tr("Dettatura non riuscita.","Dictation failed.","Falló el dictado."));}
            public void onResults(Bundle results){
                ArrayList<String> list=results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if(list!=null&&!list.isEmpty()){
                    String prev=target.getText().toString().trim();
                    target.setText(prev.isEmpty()?list.get(0):prev+" "+list.get(0));
                    target.setSelection(target.length());
                }
            }
            public void onPartialResults(Bundle b){}
            public void onEvent(int type,Bundle b){}
        });
        Intent i=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE,I18n.locale(lang).toLanguageTag());
        recognizer.startListening(i);
        notifyUser(tr("Parla ora…","Speak now…","Habla ahora…"));
    }
    private File audioFile(String key){File dir=new File(getFilesDir(),"audio");dir.mkdirs();
      return new File(dir,date+"_"+key+".m4a");}
    private void toggleRecord(String key,Button btn){
        if(recorder!=null){
            try{recorder.stop();}catch(Exception ex){audioFile(recordingKey).delete();}
            recorder.release();recorder=null;recordingKey=null;btn.setText("⏺");
            notifyUser(tr("Registrazione terminata.","Recording saved.","Grabación guardada."));
            return;
        }
        if(!micPermission())return;
        try{
            recorder=Build.VERSION.SDK_INT>=31?new MediaRecorder(this):new MediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setAudioEncodingBitRate(64000);
            recorder.setAudioSamplingRate(16000);
            recorder.setOutputFile(audioFile(key).getAbsolutePath());
            recorder.prepare();recorder.start();recordingKey=key;btn.setText("⏹");
        }catch(Exception ex){
            if(recorder!=null){recorder.release();recorder=null;}
            notifyUser(tr("Impossibile registrare.","Could not record.","No se pudo grabar."));
        }
    }
    private void playRecording(String key){
        File f=audioFile(key);
        if(!f.isFile()){notifyUser(tr("Nessun audio per questa risposta.","No recording for this answer.",
            "No hay grabación para esta respuesta."));return;}
        try{
            if(player!=null){player.release();player=null;}
            player=new MediaPlayer();player.setDataSource(f.getAbsolutePath());
            player.prepare();player.setOnCompletionListener(mp->{mp.release();player=null;});player.start();
        }catch(Exception ex){notifyUser(tr("Riproduzione non disponibile.","Playback unavailable.","Reproducción no disponible."));}
    }
    private void showSettings(){
        ScrollView sc=new ScrollView(this);LinearLayout body=col();body.setPadding(d(18),d(16),d(18),d(16));
        body.setBackgroundColor(CARD);sc.addView(body);
        body.addView(label(tr("Profilo e impostazioni","Profile and settings","Perfil y ajustes"),21,FG,true));
        space(body,12);
        body.addView(label(tr("Lingua","Language","Idioma"),15,FG,true));
        Spinner languages=new Spinner(this);
        String[] labels={"Italiano","English","Español"};
        languages.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,labels));
        languages.setSelection(lang.equals("en")?1:lang.equals("es")?2:0);
        body.addView(languages);space(body,16);
        body.addView(label(tr("Diagnosi già ricevuta (facoltativa)","Previously received diagnosis (optional)",
            "Diagnóstico ya recibido (opcional)"),14,FG,true));
        EditText diagnosis=new EditText(this);diagnosis.setTextColor(FG);diagnosis.setText(document.optString("diagnosis",""));
        diagnosis.setHintTextColor(SUB);diagnosis.setHint(tr("Non inserire se non desideri","Leave empty if preferred","Dejar vacío si prefieres"));
        body.addView(diagnosis);space(body,12);
        body.addView(label(tr("Obiettivi personali","Personal goals","Objetivos personales"),14,FG,true));
        EditText goals=new EditText(this);goals.setTextColor(FG);
        goals.setText(document.optString("goals",""));body.addView(goals);space(body,14);
        android.content.SharedPreferences p=getSharedPreferences("options",0);
        CheckBox reminder=new CheckBox(this);reminder.setTextColor(FG);
        reminder.setText(tr("Promemoria giornaliero","Daily reminder","Recordatorio diario"));
        reminder.setChecked(p.getBoolean("enabled",false));body.addView(reminder);
        int[] time={p.getInt("hour",21),p.getInt("minute",0)};
        Button clock=button(String.format(Locale.ROOT,"%02d:%02d",time[0],time[1]),false);
        clock.setOnClickListener(v->new TimePickerDialog(this,(picker,h,m)->{
            time[0]=h;time[1]=m;clock.setText(String.format(Locale.ROOT,"%02d:%02d",h,m));
        },time[0],time[1],true).show());body.addView(clock);
        CheckBox fullscreen=new CheckBox(this);fullscreen.setTextColor(FG);
        fullscreen.setText(tr("Chiedi popup a schermo intero (se autorizzato da Android)",
           "Request full-screen reminder (if Android allows it)",
           "Solicitar pantalla completa (si Android lo permite)"));
        fullscreen.setChecked(p.getBoolean("fullscreen",false));body.addView(fullscreen);
        space(body,10);
        body.addView(label(tr("Modello IA locale","On-device AI model","Modelo IA local"),15,FG,true));
        TextView status=label(AiEngine.available(this)?
           tr("Modello importato (compatibilità da verificare all'esecuzione).",
           "Model imported (runtime compatibility unverified).",
           "Modelo importado (compatibilidad por verificar)."):
           tr("Modello non importato: funziona solo la riflessione guidata.",
           "No model: guided reflection only.","Sin modelo: solo reflexión guiada."),13,SUB,false);
        body.addView(status);
        Button importButton=button(tr("Importa file .task","Import .task file","Importar archivo .task"),false);
        importButton.setOnClickListener(v->{
            Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("*/*");
            i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,540);
        });body.addView(importButton);space(body,12);
        Button save=button(tr("Salva impostazioni","Save settings","Guardar ajustes"),true);
        body.addView(save);
        AlertDialog dialog=new AlertDialog.Builder(this).setView(sc).create();
        save.setOnClickListener(v->{
            String newDiag=diagnosis.getText().toString().trim();
            String oldDiag=document.optString("diagnosis","");
            set(document,"diagnosis",newDiag);set(document,"goals",goals.getText().toString().trim());
            if(!newDiag.equals(oldDiag)){set(document,"splashPhrase","");set(document,"splashDiagnosis","");}
            persist();
            String l=languages.getSelectedItemPosition()==1?"en":languages.getSelectedItemPosition()==2?"es":"it";
            p.edit().putString("lang",l).putBoolean("enabled",reminder.isChecked())
                .putBoolean("fullscreen",fullscreen.isChecked())
                .putInt("hour",time[0]).putInt("minute",time[1]).apply();
            lang=l;ReminderReceiver.schedule(this);
            if(reminder.isChecked()&&Build.VERSION.SDK_INT>=33&&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},502);
            }
            if(fullscreen.isChecked()&&Build.VERSION.SDK_INT>=34){
                android.app.NotificationManager nm=(android.app.NotificationManager)getSystemService(NOTIFICATION_SERVICE);
                if(!nm.canUseFullScreenIntent()){
                    try{startActivity(new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                        Uri.parse("package:"+getPackageName())));}catch(Exception ignored){}
                }
            }
            dialog.dismiss();configureVoice();render();
        });
        dialog.show();
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request!=540||result!=RESULT_OK||data==null||data.getData()==null)return;
        Uri uri=data.getData();
        notifyUser(tr("Importazione modello in corso…","Importing model…","Importando modelo…"));
        io.execute(()->{
            Exception error=null;
            File destination=AiEngine.modelFile(this),temp=new File(destination.getParentFile(),"model.tmp");
            try(InputStream in=getContentResolver().openInputStream(uri);
                OutputStream out=new BufferedOutputStream(new FileOutputStream(temp))){
                if(in==null)throw new IOException("Missing stream");
                byte[] buf=new byte[1024*128];int n;long length=0;
                while((n=in.read(buf))!=-1){out.write(buf,0,n);length+=n;
                  if(length>7L*1024*1024*1024)throw new IOException("Model exceeds 7GB");}
                if(length<1024)throw new IOException("Model file too small");
            }catch(Exception ex){error=ex;}
            if(error==null){
                try{java.nio.file.Files.move(temp.toPath(),destination.toPath(),
                     java.nio.file.StandardCopyOption.REPLACE_EXISTING);}
                catch(Exception ex){error=ex;}
            }
            if(error!=null)temp.delete();
            Exception finalError=error;
            runOnUiThread(()->notifyUser(finalError==null?
              tr("Modello importato. Prova l'analisi per verificarlo.","Model imported. Test it with a reflection.",
                "Modelo importado. Pruébalo con un análisis."):
              tr("Importazione fallita: ","Import failed: ","Error al importar: ")+finalError.getMessage()));
        });
    }
}
