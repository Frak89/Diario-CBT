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
    private final int BACK=0xff080c1e, CARD=0xff141e35, FG=0xfff2f6ff, SUB=0xffa3b3d0,
      CYAN=0xff62e8ec, GOLD=0xffffc347, PURPLE=0xffaa85fa;
    private boolean changingPage=false;
    private boolean completingSession=false;
    private boolean transcriptRunning=false;
    private boolean finishAfterTranscript=false;
    private ScrollView activeScroll;
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
    private Button activeRecordingButton;
    private android.animation.ObjectAnimator recordingPulse;
    private float downX,downY;
    private boolean building;
    private int d(float px) {return (int)(px*getResources().getDisplayMetrics().density+.5f);}
    private String tr(String it,String en,String es) {return I18n.t(lang,it,en,es);}
    private void notifyUser(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
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
        JSONObject unfinished=session();
        if("generating".equals(unfinished.optString("reportStatus",""))){
            set(unfinished,"reportStatus","interrupted");
            set(unfinished,"aiError",tr("Analisi interrotta. Tocca «Riprova l'analisi IA».",
              "Interrupted analysis. Tap Retry AI reflection.",
              "Análisis interrumpido. Pulsa Reintentar IA."));
            persist();
        }
        tts=new TextToSpeech(this,status->{if(status==TextToSpeech.SUCCESS) configureVoice();});
        render();
        new Handler(Looper.getMainLooper()).postDelayed(this::offerBuiltInAI,700);
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
        if(target<0||target>6||changingPage||target==step)return;
        changingPage=true;
        final int direction=target>step?1:-1;
        if(recorder!=null)stopRecording();
        saveDraft();
        ViewGroup host=findViewById(android.R.id.content);
        View previous=host.getChildCount()>0?host.getChildAt(0):null;
        int width=getResources().getDisplayMetrics().widthPixels;
        step=target;
        render();
        if(previous!=null){
            View current=host.getChildAt(0);
            host.addView(previous,0,new android.widget.FrameLayout.LayoutParams(-1,-1));
            current.setTranslationX(direction*width*0.9f);
            current.setAlpha(0.84f);
            current.animate().translationX(0).alpha(1).setDuration(320)
                .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
            previous.animate().translationX(-direction*width*0.33f).alpha(0f)
                .setDuration(320).setInterpolator(new android.view.animation.DecelerateInterpolator())
                .withEndAction(()->{host.removeView(previous);changingPage=false;}).start();
        } else changingPage=false;
    }
    private GradientDrawable bg(int color,int border) {
        GradientDrawable out;
        if(color==CARD)out=new GradientDrawable(GradientDrawable.Orientation.TL_BR,
            new int[]{0xff1d2c4c,0xff111829});
        else if(color==CYAN)out=new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
            new int[]{0xff5ae4e8,0xff8eafff});
        else{out=new GradientDrawable();out.setColor(color);}
        out.setCornerRadius(d(22));out.setStroke(d(1),border);return out;
    }
    private GradientDrawable pageBackground(){
        return new GradientDrawable(GradientDrawable.Orientation.TL_BR,
            new int[]{0xff0c142a,0xff101027,0xff06171f});
    }
    private TextView label(String text,int size,int color,boolean bold) {
        TextView v=new TextView(this);v.setText(text);v.setTextSize(size);v.setTextColor(color);
        v.setTypeface(android.graphics.Typeface.create(bold?"sans-serif-medium":"sans-serif",
            bold?android.graphics.Typeface.BOLD:android.graphics.Typeface.NORMAL));
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
        ScrollView sc=new ScrollView(this);
        sc.setFillViewport(true);sc.setSmoothScrollingEnabled(true);
        sc.setClipToPadding(false);
        sc.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        sc.setBackground(pageBackground());activeScroll=sc;
        layout=col();layout.setPadding(d(18),d(16),d(18),d(140));sc.addView(layout);
        setContentView(sc);
        LinearLayout top=row();
        TextView brand=label("◈  DIARIO CBT",23,GOLD,true);
        top.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        Button settings=button("⚙",false);settings.setOnClickListener(v->{saveDraft();showSettings();});
        top.addView(settings);layout.addView(top);space(layout,14);
        String[] titles=I18n.headings(lang);
        TextView kicker=label(tr("PERCORSO GUIDATO  •  "+(step+1)+" DI 7",
          "GUIDED PATH  •  "+(step+1)+" OF 7",
          "RUTA GUIADA  •  "+(step+1)+" DE 7"),11,CYAN,true);
        layout.addView(kicker);space(layout,6);
        TextView heading=label(titles[step],27,FG,true);layout.addView(heading);
        space(layout,10);
        TextView progress=label("◷ "+date+"    ◆ "+
           tr("Serie","Streak","Racha")+": "+streak()+"  🔥",13,SUB,false);layout.addView(progress);
        space(layout,14);
        LinearLayout steps=row();steps.setGravity(Gravity.CENTER_VERTICAL);
        for(int j=0;j<7;j++){
            View block=new View(this);
            GradientDrawable shape=new GradientDrawable();
            shape.setCornerRadius(d(5));
            shape.setColor(j<=step?CYAN:0xff364362);
            block.setBackground(shape);
            LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,d(5),1);
            params.setMargins(d(2),0,d(2),0);steps.addView(block,params);
        }
        layout.addView(steps);space(layout,20);
        LinearLayout card=col();card.setBackground(bg(CARD,0xff35466b));
        card.setPadding(d(17),d(20),d(17),d(12));layout.addView(card);
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
        next.setOnClickListener(v->{if(step<6)navigate(step+1);else finishSession();});
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
        if(step==1 || (step==4 && qIndex==2)){
            card.addView(RatingButtons.create(this,question,session().optString(key,""),step==1,lang,
                score->{set(session(),key,Integer.toString(score));persist();},
                ()->speak(question)));
            space(card,14);return;
        }
        TextView title=label(question,16,FG,true);card.addView(title);space(card,6);
        EditText answer=new EditText(this);
        answer.setTextSize(16);answer.setTextColor(FG);answer.setHintTextColor(0xff7e90aa);
        answer.setBackground(bg(BACK,0xff354362));
        answer.setPadding(d(12),d(12),d(12),d(12));
        answer.setGravity(Gravity.TOP|Gravity.START);
        answer.setMinLines(2);answer.setMaxLines(6);
        answer.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE|
            android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        answer.setHint(I18n.hint(lang,step,qIndex));
        answer.setOnFocusChangeListener((view,hasFocus)->{
            if(hasFocus){
                answer.postDelayed(()->scrollFieldIntoView(answer),260);
            }
        });
        answer.setText(session().optString(key,""));
        currentFields.add(answer);currentKeys.add(key);
        card.addView(answer,new LinearLayout.LayoutParams(-1,-2));space(card,6);
        if(step==2&&qIndex==2)addEmotionChoices(card,answer);
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
        Button rec=button("⏺",false);rec.setContentDescription(tr("Registra voce","Record voice","Grabar voz"));
        rec.setOnClickListener(v->toggleRecord(key,rec));tools.addView(rec,new LinearLayout.LayoutParams(0,d(45),1));
        Button play=button("▶",false);play.setContentDescription(tr("Riascolta risposta","Replay recording","Reproducir grabación"));
        play.setOnClickListener(v->playRecording(key));tools.addView(play,new LinearLayout.LayoutParams(0,d(45),1));
        Button readAnswer=button("🔉",false);
        readAnswer.setContentDescription(tr("Ascolta la risposta","Read answer aloud","Escuchar respuesta"));
        readAnswer.setOnClickListener(v->speak(answer.getText().toString()));
        tools.addView(readAnswer,new LinearLayout.LayoutParams(0,d(45),1));
        card.addView(tools);
        TextView recHelp=label(tr("REC registra e trascrive automaticamente dopo lo stop (con consenso).",
              "REC automatically transcribes after stopping (with consent).",
              "REC transcribe automáticamente al terminar (con consentimiento)."),12,SUB,false);
        recHelp.setPadding(d(3),d(5),0,d(13));card.addView(recHelp);space(card,12);
    }
    private void scrollFieldIntoView(EditText field){
        if(activeScroll==null||!field.isAttachedToWindow())return;
        android.graphics.Rect r=new android.graphics.Rect(0,0,field.getWidth(),field.getHeight()+d(125));
        activeScroll.offsetDescendantRectToMyCoords(field,r);
        int destination=activeScroll.getScrollY()+r.bottom-activeScroll.getHeight()+d(20);
        if(destination>activeScroll.getScrollY())
            activeScroll.smoothScrollTo(0,Math.max(0,destination));
    }
    private void addEmotionChoices(LinearLayout card,EditText answer){
        TextView options=label(tr("Scegli le emozioni (anche più di una)",
          "Choose emotions (you can pick more than one)",
          "Elige emociones (puedes elegir varias)"),14,CYAN,true);
        options.setPadding(0,d(7),0,d(7));card.addView(options);
        String[] names=I18n.emotionOptions(lang);
        java.util.ArrayList<Button> chips=new java.util.ArrayList<>();
        for(int i=0;i<names.length;i+=2){
            LinearLayout line=row();
            for(int j=i;j<Math.min(i+2,names.length);j++){
                String option=names[j];
                Button choice=button(option,false);
                choice.setTextSize(12);
                LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,d(46),1);
                p.setMargins(d(2),d(2),d(2),d(2));line.addView(choice,p);chips.add(choice);
                choice.setOnClickListener(v->{
                    String value=answer.getText().toString();
                    if(value.contains(option)){
                        String clean=value.replace(option+"\n","").replace("\n"+option,"").replace(option,"").trim();
                        answer.setText(clean);
                    }else{
                        answer.setText(value.isEmpty()?option:value+"\n"+option);
                    }
                    answer.setSelection(answer.length());
                    for(Button b:chips){
                        boolean selected=answer.getText().toString().contains(b.getText().toString());
                        b.setBackground(bg(selected?CYAN:CARD,selected?CYAN:0xff35405e));
                        b.setTextColor(selected?BACK:FG);
                    }
                });
            }
            card.addView(line);
        }
        for(Button b:chips){
            boolean selected=answer.getText().toString().contains(b.getText().toString());
            if(selected){
                b.setBackground(bg(CYAN,CYAN));b.setTextColor(BACK);
            }
        }
        space(card,10);
    }
    private void showFinal(LinearLayout panel) {
        JSONObject entry=session();
        TextView score=label(tr("Esercizi CBT completati","Completed CBT exercises","Ejercicios CBT completados")
            +"   "+score()+"/10",19,GOLD,true);
        panel.addView(score);space(panel,6);
        panel.addView(label(tr("Questo punteggio conta gli esercizi svolti: non valuta una diagnosi o la persona.",
           "This counts completed tasks, not a diagnosis or a person's worth.",
           "Cuenta las tareas completadas, no una diagnosis ni el valor de la persona."),13,SUB,false));
        space(panel,22);
        String report=entry.optString("report","");
        String status=entry.optString("reportStatus","not_started");
        boolean ai=entry.optBoolean("aiReport",false);
        boolean generating="generating".equals(status);
        TextView title=label(generating?
          tr("IA QWEN3 • ANALISI IN CORSO","QWEN3 AI • ANALYZING","IA QWEN3 • ANALIZANDO"):
          ai?tr("OSSERVAZIONE PERSONALIZZATA • IA QWEN3",
                "PERSONALIZED OBSERVATION • QWEN3 AI","OBSERVACIÓN PERSONALIZADA • IA QWEN3"):
          tr("RIFLESSIONE GUIDATA • NON GENERATA DALL'IA",
                "GUIDED REFLECTION • NOT AI-GENERATED",
                "REFLEXIÓN GUIADA • NO GENERADA POR IA"),16,CYAN,true);
        panel.addView(title);space(panel,12);
        if(generating){
            ProgressBar busy=new ProgressBar(this);
            panel.addView(busy,new LinearLayout.LayoutParams(d(44),d(44)));
            TextView inProgress=label(tr(
                "Qwen3 sta leggendo le risposte sul telefono e preparando osservazioni e suggerimenti. Il tempo dipende dalle prestazioni del dispositivo.",
                "Qwen3 is analyzing answers locally and preparing suggestions. Processing time depends on your device.",
                "Qwen3 está analizando las respuestas en el teléfono. El tiempo depende del dispositivo."),15,FG,false);
            panel.addView(inProgress);space(panel,15);
        }else{
            if("error".equals(status)||"missing".equals(status)||"interrupted".equals(status)){
                String detail=entry.optString("aiError","");
                TextView warning=label(tr("IA non completata: ","AI did not complete: ",
                    "La IA no terminó: ")+detail,14,0xffffbd9b,false);
                panel.addView(warning);space(panel,14);
            }
            TextView observation=label(report.isEmpty()?
               tr("Premi «Termina» per generare l'osservazione.",
                  "Tap Done to generate your reflection.",
                  "Pulsa Terminar para generar la observación."):report,16,FG,false);
            panel.addView(observation);space(panel,14);
            if(!report.isEmpty()){
                Button hear=button("🔊 "+tr("Ascolta l'osservazione","Listen to reflection","Escuchar observación"),false);
                hear.setOnClickListener(v->speak(observation.getText().toString()));
                panel.addView(hear);space(panel,12);
            }
            if("error".equals(status)||"missing".equals(status)||"interrupted".equals(status)){
                Button retry=button(AiEngine.available(this)?
                   tr("Riprova l'analisi IA","Retry AI reflection","Reintentar análisis IA"):
                   tr("Attiva Qwen3 integrato","Enable built-in Qwen3","Activar Qwen3 integrado"),true);
                retry.setOnClickListener(v->{
                    if(AiEngine.available(this))completeAndAnalyze();
                    else downloadBuiltInModel();
                });panel.addView(retry);space(panel,12);
            }
        }
        panel.addView(label(tr("Non è una diagnosi né una valutazione medica.",
          "This is not a medical diagnosis or assessment.",
          "No es un diagnóstico ni una valoración médica."),12,SUB,false));
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
    private void finishSession(){
        if(completingSession)return;
        if(recorder!=null)stopRecording();
        saveDraft();
        if(transcriptRunning){
            finishAfterTranscript=true;
            notifyUser(tr("Attendo la trascrizione, poi preparo l'osservazione.",
              "Waiting for transcription before generating the reflection.",
              "Esperando la transcripción para generar la observación."));
            return;
        }
        completeAndAnalyze();
    }
    private void appendTranscript(String key,String transcript,EditText field){
        if(transcript==null||transcript.trim().isEmpty())return;
        JSONObject values=session();
        String existing=values.optString(key,"").trim();
        String updated=existing.isEmpty()?transcript.trim():existing+"\n"+transcript.trim();
        set(values,key,updated);
        set(values,"transcribed_"+key,true);persist();
        if(field!=null&&field.isAttachedToWindow()){
            field.setText(updated);field.setSelection(field.length());
            scrollFieldIntoView(field);
        }
    }
    private String shortAiError(Throwable error){
        Throwable root=error;int tries=0;
        while(root!=null&&root.getCause()!=null&&tries++<8)root=root.getCause();
        String reason=root==null?"Unknown error":root.getClass().getSimpleName()+
          (root.getMessage()==null?"":": "+root.getMessage());
        return reason.length()>260?reason.substring(0,260):reason;
    }
    private void completeAndAnalyze(){
        if(completingSession)return;
        saveDraft();
        JSONObject history=document.optJSONObject("history");
        if(history==null){history=new JSONObject();set(document,"history",history);}
        set(history,date,true);
        JSONObject entry=session();
        if(!AiEngine.available(this)){
            set(entry,"reportStatus","missing");
            set(entry,"aiError",tr("Modello Qwen3 non ancora pronto. Puoi attivarlo qui sotto.",
              "Qwen3 model not yet ready. Activate it below.",
              "El modelo Qwen3 no está preparado. Actívalo abajo."));
            set(entry,"report",fallback());
            set(entry,"aiReport",false);
            persist();render();return;
        }
        completingSession=true;
        final String analysisDate=date;
        set(entry,"reportStatus","generating");
        set(entry,"report","");
        set(entry,"aiError","");
        set(entry,"aiReport",false);
        persist();render();
        String diagnosis=document.optString("diagnosis",""),goals=document.optString("goals","");
        String diary=diaryText();
        new Handler(Looper.getMainLooper()).postDelayed(()->{
            if(isFinishing()||!completingSession)return;
            JSONObject inProgress=session();
            if(!"generating".equals(inProgress.optString("reportStatus","")))return;
            completingSession=false;
            set(inProgress,"reportStatus","error");
            set(inProgress,"aiError",tr(
                "La generazione sta impiegando troppo tempo su questo dispositivo. Prova il test IA nelle impostazioni.",
                "AI generation is taking too long on this device. Try the AI test in Settings.",
                "La generación tarda demasiado en este dispositivo. Prueba la IA desde Ajustes."));
            set(inProgress,"report",fallback());
            set(inProgress,"aiReport",false);
            persist();if(step==6)render();
        },180000);
        AiEngine.report(this,lang,diagnosis,goals,diary,(response,error)->{
            if(!"generating".equals(entry.optString("reportStatus","")))return;
            completingSession=false;
            if(isFinishing())return;
            JSONObject sessions=document.optJSONObject("sessions");
            JSONObject target=sessions==null?null:sessions.optJSONObject(analysisDate);
            if(target==null)return;
            String cleaned=response==null?"":response.replaceAll("(?s)<think>.*?</think>","").trim();
            if(error!=null||cleaned.isEmpty()){
                set(target,"reportStatus","error");
                set(target,"aiReport",false);
                set(target,"aiError",error!=null?shortAiError(error):
                    tr("Il modello non ha restituito testo utilizzabile.",
                       "The model returned no usable text.",
                       "El modelo no devolvió texto utilizable."));
                set(target,"report",fallback());
            }else{
                set(target,"report",cleaned);
                set(target,"reportStatus","done");
                set(target,"aiReport",true);
                set(target,"aiError","");
            }
            persist();if(step==6)render();
            if(!target.optBoolean("aiReport",false))return;
            AiEngine.encouragement(this,lang,diagnosis,goals,(phrase,pError)->{
                if(isFinishing())return;
                if(pError==null&&phrase!=null&&!phrase.trim().isEmpty()&&phrase.length()<240){
                    String message=phrase.replaceAll("(?s)<think>.*?</think>","").trim();
                    if(!message.isEmpty()){
                        set(document,"splashPhrase",message);
                        set(document,"splashDiagnosis",diagnosis);persist();
                    }
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
    private void recordingVisual(Button btn,boolean isRecording){
        btn.setText(isRecording?"⏹ REC":"⏺");
        btn.setTextSize(isRecording?12:14);
        btn.setTextColor(FG);
        btn.setBackground(bg(isRecording?0xffe32f54:CARD,
                             isRecording?0xffffb9cc:0xff35405e));
        btn.setContentDescription(isRecording?
          tr("Registrazione in corso: premi per fermare",
             "Recording: tap to stop","Grabando: toca para detener"):
          tr("Registra voce","Record voice","Grabar voz"));
    }
    private void stopRecording(){
        if(recorder==null)return;
        String endedKey=recordingKey;boolean saved=true;
        try{recorder.stop();}
        catch(Exception ex){
            saved=false;
            if(endedKey!=null)audioFile(endedKey).delete();
        }finally{recorder.release();recorder=null;}
        if(recordingPulse!=null){recordingPulse.cancel();recordingPulse=null;}
        if(activeRecordingButton!=null){
            recordingVisual(activeRecordingButton,false);
            activeRecordingButton.setAlpha(1f);
        }
        activeRecordingButton=null;recordingKey=null;
        if(!saved||endedKey==null){
            notifyUser(tr("Registrazione non riuscita.","Recording failed.","Falló la grabación."));
            return;
        }
        if(getSharedPreferences("options",0).getBoolean("online_stt_consent",false))
            autoTranscribe(endedKey);
        else notifyUser(tr("Audio salvato (trascrizione online disattivata).",
            "Audio saved (online transcription disabled).",
            "Audio guardado (transcripción en línea desactivada)."));
    }
    private void toggleRecord(String key,Button btn){
        if(recorder!=null){stopRecording();return;}
        if(transcriptRunning){
            notifyUser(tr("La registrazione precedente è ancora in trascrizione.",
                "The previous recording is still being transcribed.",
                "La grabación anterior sigue transcribiéndose."));return;
        }
        if(!micPermission())return;
        android.content.SharedPreferences p=getSharedPreferences("options",0);
        if(!p.getBoolean("stt_choice_made",false)){
            new AlertDialog.Builder(this)
              .setTitle(tr("Registrazione e trascrizione","Recording and transcription","Grabación y transcripción"))
              .setMessage(tr(
                 "Il pulsante REC salva una copia della registrazione sul telefono. Per ottenere automaticamente il testo, il servizio vocale di Android può inviare l'audio a un fornitore esterno tramite Internet. I dati del diario non vengono inviati dall'IA locale. Vuoi consentire la trascrizione online?",
                 "REC saves an audio copy on your phone. For automatic transcription, Android's speech service may send recorded audio to an external provider over the Internet. The local AI does not upload your journal. Allow online transcription?",
                 "REC guarda una copia del audio en el teléfono. Para transcribir automáticamente, el servicio de voz de Android puede enviar el audio a un proveedor externo por Internet. La IA local no envía tu diario. ¿Permitir transcripción en línea?"))
              .setPositiveButton(tr("Consenti e registra","Allow and record","Permitir y grabar"),(dialog,which)->{
                  p.edit().putBoolean("online_stt_consent",true).putBoolean("stt_choice_made",true).apply();
                  beginAudioRecording(key,btn);
              })
              .setNeutralButton(tr("Solo registra","Record only","Solo grabar"),(dialog,which)->{
                  p.edit().putBoolean("online_stt_consent",false).putBoolean("stt_choice_made",true).apply();
                  beginAudioRecording(key,btn);
              })
              .setNegativeButton(tr("Annulla","Cancel","Cancelar"),null)
              .show();
            return;
        }
        beginAudioRecording(key,btn);
    }
    private void beginAudioRecording(String key,Button btn){
        if(recorder!=null||transcriptRunning||!micPermission())return;
        try{
            recorder=Build.VERSION.SDK_INT>=31?new MediaRecorder(this):new MediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setAudioEncodingBitRate(64000);
            recorder.setAudioSamplingRate(16000);
            recorder.setOutputFile(audioFile(key).getAbsolutePath());
            recorder.prepare();recorder.start();
            set(session(),"transcribed_"+key,false);persist();
            recordingKey=key;activeRecordingButton=btn;recordingVisual(btn,true);
            recordingPulse=android.animation.ObjectAnimator.ofFloat(btn,"alpha",1.0f,0.60f,1.0f);
            recordingPulse.setDuration(900);
            recordingPulse.setRepeatCount(android.animation.ValueAnimator.INFINITE);
            recordingPulse.start();
        }catch(Exception ex){
            if(recorder!=null){recorder.release();recorder=null;}
            recordingKey=null;activeRecordingButton=null;recordingVisual(btn,false);
            notifyUser(tr("Impossibile registrare.","Could not record.","No se pudo grabar."));
        }
    }
    private void autoTranscribe(String key){
        File recording=audioFile(key);
        if(!recording.isFile())return;
        if(!RecordingTranscriber.supported(this)){
            set(session(),"speechError_"+key,"NO_RECOGNITION_SERVICE");persist();
            notifyUser(tr("Servizio vocale Android non disponibile. Audio salvato.",
                "Android speech service is unavailable. Audio saved.",
                "Servicio de voz de Android no disponible. Audio guardado."));
            return;
        }
        transcriptRunning=true;
        notifyUser(tr("Audio salvato. Trascrizione automatica in corso…",
            "Audio saved. Transcribing automatically…",
            "Audio guardado. Transcripción automática en curso…"));
        RecordingTranscriber.start(this,recording,I18n.locale(lang),(transcript,error)->{
            transcriptRunning=false;
            if(isFinishing())return;
            if(error!=null||transcript==null||transcript.trim().isEmpty()){
                String detail=error==null?"No speech recognized":error.getMessage();
                set(session(),"speechError_"+key,detail==null?"Unknown":detail.substring(0,Math.min(detail.length(),120)));
                persist();
                notifyUser(tr("Audio conservato. Trascrizione non riuscita: ",
                    "Audio preserved. Transcription failed: ",
                    "Audio conservado. Falló la transcripción: ")+detail);
            }else{
                EditText visibleField=null;
                for(int i=0;i<currentKeys.size();i++)
                    if(currentKeys.get(i).equals(key)){visibleField=currentFields.get(i);break;}
                appendTranscript(key,transcript,visibleField);
                notifyUser(tr("Testo trascritto e salvato nel diario.",
                    "Transcribed text saved to your journal.",
                    "Texto transcrito y guardado en el diario."));
            }
            if(finishAfterTranscript){
                finishAfterTranscript=false;
                completeAndAnalyze();
            }
        });
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
        CheckBox speechConsent=new CheckBox(this);
        speechConsent.setTextColor(FG);
        speechConsent.setText(tr(
           "Trascrizione automatica tramite servizio vocale online Android (audio potenzialmente inviato al fornitore)",
           "Automatic transcription via Android online speech service (audio may be sent to its provider)",
           "Transcripción automática con el servicio de voz online de Android (el audio puede enviarse al proveedor)"));
        speechConsent.setChecked(p.getBoolean("online_stt_consent",false));
        body.addView(speechConsent);space(body,12);
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
           tr("Qwen3 0.6B installato e verificato. Inferenza locale.",
           "Qwen3 0.6B installed and verified. On-device inference.",
           "Qwen3 0.6B instalado y verificado. Inferencia local."):
           tr("Qwen3 0.6B pronto al download automatico (Wi-Fi consigliato).",
           "Qwen3 0.6B ready to download (Wi-Fi recommended).",
           "Qwen3 0.6B listo para descargar (Wi-Fi recomendado)."),13,SUB,false);
        body.addView(status);
        Button importButton=button(ModelManager.bundled(this)?
           tr("Prepara IA già inclusa nell'APK","Prepare AI bundled in APK","Preparar IA incluida en APK"):
           tr("Attiva IA Qwen3 integrata (~614 MB)","Activate built-in Qwen3 AI (~614 MB)",
           "Activar IA Qwen3 integrada (~614 MB)"),false);
        importButton.setOnClickListener(v->downloadBuiltInModel());
        body.addView(importButton);space(body,7);
        Button testAi=button(tr("Verifica il funzionamento dell'IA sul telefono",
             "Test AI on this device","Probar la IA en este teléfono"),false);
        testAi.setOnClickListener(v->{
            if(!AiEngine.available(this)){
                notifyUser(tr("Modello Qwen3 non ancora pronto.",
                    "Qwen3 model not ready.","El modelo Qwen3 no está listo."));return;
            }
            notifyUser(tr("Verifica IA locale in corso…","Testing local AI…",
              "Probando IA local…"));
            AiEngine.encouragement(this,lang,document.optString("diagnosis",""),
                document.optString("goals",""),(answer,error)->{
                if(isFinishing())return;
                new AlertDialog.Builder(this)
                    .setTitle(error==null?tr("Test IA riuscito","AI test succeeded","Prueba IA correcta"):
                      tr("Errore di inferenza IA","AI inference error","Error de inferencia IA"))
                    .setMessage(error==null?answer:shortAiError(error))
                    .setPositiveButton("OK",null).show();
            });
        });
        body.addView(testAi);space(body,12);
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
                .putBoolean("online_stt_consent",speechConsent.isChecked())
                .putBoolean("stt_choice_made",true)
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
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }
    private void offerBuiltInAI(){
        if(isFinishing()||AiEngine.available(this))return;
        if(ModelManager.bundled(this)){
            downloadBuiltInModel();return;
        }
        android.content.SharedPreferences prefs=getSharedPreferences("options",0);
        if(prefs.getBoolean("model_intro_seen",false))return;
        prefs.edit().putBoolean("model_intro_seen",true).apply();
        new AlertDialog.Builder(this)
         .setTitle(tr("Qwen3, la tua IA privata","Qwen3, your private AI","Qwen3, tu IA privada"))
         .setMessage(tr("La valutazione CBT e la frase motivazionale sono generate da Qwen3 0.6B sul telefono. Per iniziare, l'app scarica automaticamente 614 MB da Hugging Face. Wi-Fi consigliato. Nessun diario è inviato online. Vuoi attivarla?",
            "CBT reflections and encouragement are generated on your phone by Qwen3 0.6B. The app needs to download 614 MB once from Hugging Face. Wi-Fi recommended. Your journal is never uploaded. Enable it now?",
            "Qwen3 0.6B crea reflexiones y frases de ánimo en tu teléfono. Se descargarán 614 MB una sola vez desde Hugging Face. Wi-Fi recomendado. Tu diario nunca se envía. ¿Activarla ahora?"))
         .setNegativeButton(tr("Più tardi","Later","Después"),(d,w)->{})
         .setPositiveButton(tr("Attiva IA","Enable AI","Activar IA"),(d,w)->downloadBuiltInModel())
         .show();
    }
    private void downloadBuiltInModel(){
        if(AiEngine.available(this)){
            notifyUser(tr("Qwen3 è già disponibile offline.","Qwen3 is already ready offline.",
                "Qwen3 ya está listo sin conexión."));return;
        }
        final java.util.concurrent.atomic.AtomicBoolean cancel=new java.util.concurrent.atomic.AtomicBoolean(false);
        final LinearLayout body=col();body.setPadding(d(24),d(22),d(24),d(18));
        TextView title=label("◈  QWEN3 0.6B",20,CYAN,true);body.addView(title);space(body,16);
        boolean bundled=ModelManager.bundled(this);
        TextView detail=label(bundled?
            tr("Preparazione dell'IA già inclusa nell'APK: verifica SHA-256. Non serve Internet.",
            "Preparing the AI bundled in this APK: SHA-256 verification. No internet needed.",
            "Preparando la IA incluida en el APK: verificación SHA-256, sin Internet."):
            tr("Download sicuro dei pesi IA: 614 MB. Nessun dato del diario viene trasferito.",
            "Downloading AI weights: 614 MB. No journal data is transferred.",
            "Descargando IA: 614 MB. No se envía tu diario."),15,FG,false);body.addView(detail);space(body,16);
        ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);bar.setProgress(0);body.addView(bar);
        TextView percent=label("0%",14,SUB,false);body.addView(percent);
        AlertDialog dialog=new AlertDialog.Builder(this).setView(body)
            .setNegativeButton(tr("Interrompi","Cancel","Cancelar"),(dlg,which)->cancel.set(true))
            .setCancelable(false).create();
        dialog.show();
        ModelManager.Listener listener=new ModelManager.Listener(){
            @Override public void progress(long done,long total){
                int value=(int)(done*100/total);
                bar.setProgress(value);
                percent.setText(value+"%   •   "+(done/1048576)+" / "+(total/1048576)+" MiB");
            }
            @Override public void completed(Exception error){
                if(!isFinishing())dialog.dismiss();
                if(error!=null){
                    notifyUser(tr("IA non installata: ","AI not installed: ","IA no instalada: ")+error.getMessage());
                }else if(!isFinishing()){
                    notifyUser(tr("IA pronta: Qwen3 funziona offline.","AI ready: Qwen3 works offline.",
                        "IA lista: Qwen3 funciona sin conexión."));
                    render();
                }
            }
        };
        if(bundled)ModelManager.installBundled(this,cancel,listener);
        else ModelManager.download(this,cancel,listener);
    }
}
