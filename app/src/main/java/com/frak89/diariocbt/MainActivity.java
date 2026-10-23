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
    private boolean summaryVisible=false;
    private boolean calendarVisible=false;
    private java.time.YearMonth calendarMonth=java.time.YearMonth.now();
    private String archivedDay=null;
    private int archivedPosition=0;
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
        if(state!=null){
            step=state.getInt("step",0);
            summaryVisible=state.getBoolean("summaryVisible",false);
            calendarVisible=state.getBoolean("calendarVisible",false);
            archivedDay=state.getString("archivedDay",null);
            archivedPosition=state.getInt("archivedPosition",0);
            String savedMonth=state.getString("calendarMonth","");
            try{calendarMonth=java.time.YearMonth.parse(savedMonth);}
            catch(Exception ignored){}
        } else {
            JSONObject current=session();
            JSONObject days=document.optJSONObject("history");
            // Registered entries are never reopened as editable drafts.
            summaryVisible=days!=null&&days.has(date)&&hasDiaryContent(current);
        }
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
    @Override protected void onSaveInstanceState(Bundle state){
        saveDraft();
        state.putInt("step",step);
        state.putBoolean("summaryVisible",summaryVisible);
        state.putBoolean("calendarVisible",calendarVisible);
        state.putString("archivedDay",archivedDay);
        state.putInt("archivedPosition",archivedPosition);
        state.putString("calendarMonth",calendarMonth.toString());
        super.onSaveInstanceState(state);
    }
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
        if(archivedDay!=null){renderArchivedDetail();return;}
        if(calendarVisible){renderCalendar();return;}
        if(summaryVisible){
            renderSummary();
            return;
        }
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
        Button calendar=button("📅",false);
        calendar.setContentDescription(tr("Apri calendario diario","Open journal calendar","Abrir calendario diario"));
        calendar.setOnClickListener(v->openCalendar());
        top.addView(calendar);
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
        Button next=button(step==6?tr("Termina e mostra riepilogo","Finish & view summary","Terminar y ver resumen"):tr("Avanti →","Next →","Siguiente →"),true);
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
        TextView recHelp=label(tr(
              "REC salva l'audio; con API e consenso configurati lo trascrive automaticamente alla fine.",
              "REC saves audio; with API and consent enabled it transcribes automatically after stopping.",
              "REC guarda el audio; con API y consentimiento activos, transcribe al terminar."),12,SUB,false);
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
    private String practicalEncouragement(JSONObject entry){
        String achieved=entry.optString("p0_2","").trim();
        String next=entry.optString("p5_0","").trim();
        if(!next.isEmpty()){
            String mini=next.length()>125?next.substring(0,125).trim()+"…":next;
            return tr("Un progresso alla volta è sufficiente per oggi. Puoi iniziare con due minuti di: ",
                "One step at a time is enough for today. Begin with two minutes of: ",
                "Un paso a la vez es suficiente por hoy. Empieza con dos minutos de: ")+mini;
        }
        if(!achieved.isEmpty()){
            String brief=achieved.length()>110?achieved.substring(0,110).trim()+"…":achieved;
            return tr("Hai già individuato qualcosa che sei riuscito a fare: ",
                "You already identified something you managed to do: ",
                "Ya identificaste algo que lograste hacer: ")+brief+
                tr(". Puoi riconoscere quel passo senza dover risolvere tutto oggi.",
                    ". That step counts, even without solving everything today.",
                    ". Ese paso cuenta sin tener que resolverlo todo hoy.");
        }
        return tr("Scegli una sola azione fattibile in due minuti. Cominciare in piccolo può aiutare più che aspettare il momento perfetto.",
            "Choose one doable two-minute action. Starting small can be more useful than waiting for a perfect moment.",
            "Elige una acción posible de dos minutos. Empezar poco a poco puede ayudar más que esperar el momento perfecto.");
    }
    private void renderSummary(){
        building=true;currentFields.clear();currentKeys.clear();
        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);scroll.setBackground(pageBackground());
        activeScroll=scroll;
        LinearLayout content=col();content.setPadding(d(18),d(28),d(18),d(40));
        scroll.addView(content);setContentView(scroll);
        TextView title=label(tr("IL TUO RIEPILOGO","YOUR SUMMARY","TU RESUMEN"),13,CYAN,true);
        content.addView(title);space(content,10);
        content.addView(label(tr("Un passo alla volta","One step at a time","Paso a paso"),29,FG,true));
        space(content,6);
        content.addView(label(date,14,SUB,false));space(content,24);

        JSONObject entry=session();
        // Reconcile legacy v0.2.8 snapshots: if the page 7 score is 10/10
        // but completionScore was captured earlier as 0, repair it in place.
        int liveScore=score();
        if(entry.optInt("completionScore",-1)!=liveScore){
            set(entry,"completionScore",liveScore);
            persist();
        }
        LinearLayout stats=row();
        LinearLayout scoreCard=col();scoreCard.setPadding(d(16),d(17),d(12),d(15));
        scoreCard.setBackground(bg(CARD,0xff374568));
        scoreCard.addView(label(tr("PUNTEGGIO CBT","CBT SCORE","PUNTUACIÓN CBT"),12,SUB,true));
        scoreCard.addView(label(liveScore+" / 10",29,GOLD,true));
        stats.addView(scoreCard,new LinearLayout.LayoutParams(0,-2,1));
        stats.addView(new View(this),new LinearLayout.LayoutParams(d(8),1));
        LinearLayout streakCard=col();streakCard.setPadding(d(16),d(17),d(12),d(15));
        streakCard.setBackground(bg(CARD,0xff374568));
        streakCard.addView(label(tr("GIORNI DI SEGUITO","DAY STREAK","DÍAS SEGUIDOS"),12,SUB,true));
        streakCard.addView(label("🔥 "+entry.optInt("completionStreak",streak()),29,GOLD,true));
        stats.addView(streakCard,new LinearLayout.LayoutParams(0,-2,1));
        content.addView(stats);space(content,10);
        content.addView(label(tr("Il punteggio misura gli esercizi CBT compilati, non la salute mentale o il valore della persona.",
            "This score reflects CBT exercises completed, not mental health or personal worth.",
            "La puntuación refleja ejercicios CBT completados, no la salud mental ni el valor personal."),
            12,SUB,false));space(content,20);

        LinearLayout encouragement=col();encouragement.setBackground(bg(CARD,0xff374568));
        encouragement.setPadding(d(17),d(18),d(17),d(18));
        encouragement.addView(label(tr("🌱 UN INCORAGGIAMENTO UTILE",
            "🌱 A PRACTICAL ENCOURAGEMENT",
            "🌱 UN ÁNIMO ÚTIL"),16,CYAN,true));space(encouragement,9);
        String message=entry.optString("encouragement","").trim();
        if(message.isEmpty())message=practicalEncouragement(entry);
        final String spokenEncouragement=message;
        encouragement.addView(label(SpeechText.clean(message),16,FG,false));
        space(encouragement,12);
        Button hearEncouragement=button("🔊 "+tr("Ascolta incoraggiamento","Listen","Escuchar"),false);
        hearEncouragement.setOnClickListener(v->speak(spokenEncouragement));
        encouragement.addView(hearEncouragement);
        content.addView(encouragement);space(content,19);

        LinearLayout verdict=col();verdict.setPadding(d(17),d(20),d(17),d(17));
        verdict.setBackground(bg(CARD,0xff374568));
        verdict.addView(label(tr("OSSERVAZIONE FINALE","FINAL REFLECTION",
            "OBSERVACIÓN FINAL"),18,FG,true));space(verdict,12);
        String status=entry.optString("reportStatus","");
        String report=entry.optString("report","");
        String provider=entry.optString("aiProvider","local");
        if("generating".equals(status)){
            ProgressBar spinner=new ProgressBar(this);verdict.addView(spinner);
            verdict.addView(label(tr("L'IA sta preparando la tua osservazione.",
                "AI is preparing your reflection.","La IA está preparando tu observación."),15,SUB,false));
        }else{
            if(entry.optBoolean("aiReport",false)){
                verdict.addView(label("groq".equals(provider)?"Groq GPT-OSS 120B":"Qwen3 offline",12,CYAN,true));
                space(verdict,7);
            }
            if(!"done".equals(status)){
                String reason=entry.optString("aiError","");
                if(!reason.isEmpty()||"missing".equals(status)){
                    String guidance=AiEngine.reportAvailable(this)&&"missing".equals(status)?
                        tr("Ora l'IA è configurata: premi Genera analisi per ottenere il verdetto.",
                            "AI is now configured: tap Generate analysis to obtain your reflection.",
                            "La IA ya está configurada: pulsa Generar análisis para obtener la reflexión."):
                        AiEngine.reportAvailable(this)?reason:aiSetupExplanation();
                    verdict.addView(label(tr("IA non disponibile: ","AI unavailable: ",
                        "IA no disponible: ")+guidance,13,0xffffbd9b,false));
                    space(verdict,9);
                }
            }
            verdict.addView(label(SpeechText.clean(report.isEmpty()?fallback():report),16,FG,false));
            space(verdict,13);
            Button hear=button("🔊 "+tr("Ascolta il verdetto","Listen to reflection","Escuchar observación"),false);
            final String narration=report.isEmpty()?fallback():report;
            hear.setOnClickListener(v->speak(narration));
            verdict.addView(hear);
            space(verdict,11);
            boolean hasGeneratedAi=entry.optBoolean("aiReport",false);
            Button regenerate=button(hasGeneratedAi?
                 tr("↻ Rigenera analisi IA","↻ Regenerate AI reflection","↻ Regenerar análisis IA"):
                 AiEngine.reportAvailable(this)?
                 tr("✦ Genera analisi IA","✦ Generate AI reflection","✦ Generar análisis IA"):
                 tr("⚙ Configura IA online Groq","⚙ Configure Groq online AI","⚙ Configurar IA Groq"),false);
            regenerate.setOnClickListener(v->requestAnalysis());
            verdict.addView(regenerate);
        }
        content.addView(verdict);space(content,20);
        boolean alreadyRegistered=entry.optBoolean("diaryRegistered",false);
        if(alreadyRegistered){
            content.addView(label("✓ "+tr("Diario registrato e cifrato sul dispositivo",
                "Journal saved and encrypted on this device",
                "Diario guardado y cifrado en este dispositivo"),13,CYAN,true));
            space(content,10);
        }
        Button calendarButton=button("📅 "+tr("Calendario e storico","Calendar and history",
            "Calendario e historial"),false);
        calendarButton.setOnClickListener(v->openCalendar());
        content.addView(calendarButton,new LinearLayout.LayoutParams(-1,d(48)));
        space(content,12);
        Button finish=button(alreadyRegistered?
            tr("✓ Termina e chiudi il diario","✓ Finish and close journal",
                "✓ Terminar y cerrar diario"):
            tr("✓ Termina diario e registra","✓ Finish and save journal",
                "✓ Terminar diario y guardar"),true);
        finish.setOnClickListener(v->finishAndRegisterJournal());
        content.addView(finish,new LinearLayout.LayoutParams(-1,d(58)));
        space(content,12);
        Button review=button("← "+tr("Rivedi o modifica il diario",
            "Review or edit your journal","Revisar o editar el diario"),false);
        review.setOnClickListener(v->{summaryVisible=false;step=0;render();});
        content.addView(review,new LinearLayout.LayoutParams(-1,d(55)));
        space(content,14);
        content.addView(label(tr(
            "Le risposte, il punteggio, lo streak, il verdetto e l'incoraggiamento rimangono nel diario cifrato.",
            "Your answers, score, streak, AI reflection and encouragement remain in your encrypted journal.",
            "Las respuestas, puntuación, racha, observación e incentivo quedan en el diario cifrado."),
            12,SUB,false));
        building=false;
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
        String provider=entry.optString("aiProvider",AiEngine.provider(this));
        String modelTitle="groq".equals(provider)?"GROQ GPT-OSS 120B":"QWEN3 OFFLINE";
        TextView title=label(generating?
          modelTitle+" • "+tr("ANALISI IN CORSO","ANALYZING","ANALIZANDO"):
          ai?tr("OSSERVAZIONE PERSONALIZZATA • ","PERSONALIZED REFLECTION • ",
                "OBSERVACIÓN PERSONALIZADA • ")+modelTitle:
          tr("RIFLESSIONE GUIDATA • NON GENERATA DALL'IA",
                "GUIDED REFLECTION • NOT AI-GENERATED",
                "REFLEXIÓN GUIADA • NO GENERADA POR IA"),16,CYAN,true);
        panel.addView(title);space(panel,12);
        if(generating){
            ProgressBar busy=new ProgressBar(this);
            panel.addView(busy,new LinearLayout.LayoutParams(d(44),d(44)));
            TextView inProgress=label(tr(
                "L'IA sta preparando osservazioni e suggerimenti. Può richiedere qualche istante.",
                "The selected AI is generating your reflection and suggestions.",
                "La IA seleccionada está preparando la observación y los consejos."),15,FG,false);
            panel.addView(inProgress);space(panel,15);
        }else{
            if("error".equals(status)||"missing".equals(status)||"interrupted".equals(status)){
                String detail=entry.optString("aiError","");
                if(!AiEngine.reportAvailable(this))detail=aiSetupExplanation();
                else if("missing".equals(status))detail=tr(
                    "Groq ora è pronto. Premi Genera analisi IA.",
                    "Groq is ready. Tap Generate AI reflection.",
                    "Groq está listo. Pulsa Generar análisis IA.");
                TextView warning=label(tr("IA non completata: ","AI did not complete: ",
                    "La IA no terminó: ")+detail,14,0xffffbd9b,false);
                panel.addView(warning);space(panel,14);
            }
            TextView observation=label(SpeechText.clean(report.isEmpty()?
               tr("Premi «Termina» per generare l'osservazione.",
                  "Tap Done to generate your reflection.",
                  "Pulsa Terminar para generar la observación."):report),16,FG,false);
            panel.addView(observation);space(panel,14);
            if(!report.isEmpty()){
                Button hear=button("🔊 "+tr("Ascolta l'osservazione","Listen to reflection","Escuchar observación"),false);
                hear.setOnClickListener(v->speak(observation.getText().toString()));
                panel.addView(hear);space(panel,12);
            }
            if("error".equals(status)||"missing".equals(status)||"interrupted".equals(status)){
                Button retry=button(AiEngine.reportAvailable(this)?
                   tr("Genera analisi IA","Generate AI reflection","Generar análisis IA"):
                   tr("Configura IA Groq o Qwen3","Configure Groq or Qwen3","Configurar Groq o Qwen3"),true);
                retry.setOnClickListener(v->requestAnalysis());
                panel.addView(retry);space(panel,12);
            }
        }
        panel.addView(label(tr("Non è una diagnosi né una valutazione medica.",
          "This is not a medical diagnosis or assessment.",
          "No es un diagnóstico ni una valoración médica."),12,SUB,false));
    }
    private int score(){
        JSONObject current=session();
        java.util.HashMap<String,String> answers=new java.util.HashMap<>();
        for(String key:ProgressMetrics.SCORED_FIELDS)
            answers.put(key,current.optString(key,""));
        return ProgressMetrics.score(answers);
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
    /** Explicit end-of-session action. Do not relaunch AI or pretend a failed encrypted write worked. */
    private void finishAndRegisterJournal(){
        if(recorder!=null){
            stopRecording();
        }
        if(transcriptRunning || completingSession
             || "generating".equals(session().optString("reportStatus",""))){
            new AlertDialog.Builder(this)
                .setTitle(tr("Elaborazione in corso","Processing in progress",
                    "Procesamiento en curso"))
                .setMessage(tr(
                    "La trascrizione o l'osservazione IA sta terminando. Potrai registrare definitivamente il diario appena conclusa, senza perdere il risultato.",
                    "Transcription or AI reflection is still processing. Finish and save once it has completed so the result is retained.",
                    "La transcripción o la observación IA sigue procesándose. Guarda el diario cuando termine para conservar el resultado."))
                .setPositiveButton("OK",null).show();
            return;
        }
        saveDraft();
        markDayCompleted();
        JSONObject current=session();
        if(!current.optBoolean("diaryRegistered",false)){
            set(current,"diaryRegistered",true);
            set(current,"registeredAt",java.time.Instant.now().toString());
        }
        set(current,"completionScore",score());
        set(current,"completionStreak",streak());
        try{
            // Verify the encrypted journal is actually written before saying it is saved.
            SecureStore.write(this,document);
        }catch(Exception error){
            set(current,"diaryRegistered",false);
            new AlertDialog.Builder(this)
                .setTitle(tr("Salvataggio non riuscito","Save unsuccessful",
                    "No se pudo guardar"))
                .setMessage(tr(
                    "Il diario non è stato confermato nell'archivio cifrato. Resta aperto, così puoi riprovare senza perdere le risposte.",
                    "The encrypted journal write was not confirmed. The app will stay open so you can retry without losing your answers.",
                    "No se confirmó el guardado cifrado. El diario queda abierto para volver a intentarlo.")+
                    "\n\n"+error.getMessage())
                .setPositiveButton("OK",null).show();
            return;
        }
        new AlertDialog.Builder(this)
            .setTitle("✓ "+tr("Diario registrato","Journal saved","Diario guardado"))
            .setMessage(tr(
                "La giornata è salvata nell'archivio cifrato. Punteggio CBT: ",
                "Today's journal is encrypted and saved. CBT score: ",
                "El diario de hoy está cifrado y guardado. Puntuación CBT: ")
                +score()+"/10\n"+
                tr("Giorni consecutivi: ","Consecutive days: ","Días consecutivos: ")+streak()+" 🔥\n\n"+
                tr("Potrai riaprire il diario di oggi senza ripetere l'analisi IA.",
                   "You can reopen today's entry without running AI analysis again.",
                   "Podrás reabrir el diario de hoy sin ejecutar de nuevo el análisis IA."))
            .setNegativeButton(tr("Resta nel riepilogo","Stay in summary",
                "Quedar en el resumen"),(dialog,which)->render())
            .setPositiveButton(tr("Chiudi diario","Close journal",
                "Cerrar diario"),(dialog,which)->finish())
            .show();
    }
    private boolean hasDiaryContent(JSONObject value){
        if(value==null)return false;
        if(!value.optString("report","").trim().isEmpty())return true;
        if(value.optBoolean("diaryRegistered",false))return true;
        for(int p=0;p<6;p++)for(int i=0;i<3;i++){
            if(!value.optString("p"+p+"_"+i,"").trim().isEmpty())return true;
        }
        return false;
    }
    private org.json.JSONArray recordingsOnDay(String day){
        org.json.JSONArray result=new org.json.JSONArray();
        JSONObject archive=document.optJSONObject("archive");
        org.json.JSONArray stored=archive==null?null:archive.optJSONArray(day);
        if(stored!=null)for(int i=0;i<stored.length();i++){
            JSONObject entry=stored.optJSONObject(i);
            if(entry!=null)result.put(entry);
        }
        JSONObject sessions=document.optJSONObject("sessions");
        JSONObject legacy=sessions==null?null:sessions.optJSONObject(day);
        JSONObject history=document.optJSONObject("history");
        if(history!=null&&history.has(day)&&hasDiaryContent(legacy)){
            try{
                JSONObject safeCopy=new JSONObject(legacy.toString());
                set(safeCopy,"_legacyEntry",true);
                result.put(safeCopy);
            }catch(Exception ignored){}
        }
        return result;
    }
    private void openCalendar(){
        saveDraft();
        archivedDay=null;calendarVisible=true;
        summaryVisible=false;
        calendarMonth=java.time.YearMonth.from(LocalDate.now());
        render();
    }
    private void markDayCompleted(){
        JSONObject entry=session();
        JSONObject history=document.optJSONObject("history");
        if(history==null){history=new JSONObject();set(document,"history",history);}
        boolean dirty=false;
        if(!history.has(date)){set(history,date,true);dirty=true;}
        if(!entry.has("completedOn")){set(entry,"completedOn",date);dirty=true;}
        // The old code froze a 0/10 score at first finish. If the user edits
        // answers later, keep the score synchronized with those actual answers.
        int liveScore=score();
        if(entry.optInt("completionScore",-1)!=liveScore){
            set(entry,"completionScore",liveScore);dirty=true;
        }
        if(!entry.has("completionStreak")){
            set(entry,"completionStreak",streak());dirty=true;
        }
        if(dirty)persist();
    }
    private void finishSession(){
        if(recorder!=null)stopRecording();
        if(completingSession)return;
        saveDraft();
        if(transcriptRunning){
            finishAfterTranscript=true;
            notifyUser(tr("Attendo la trascrizione, poi apro il riepilogo.",
                "Waiting for transcription, then opening your summary.",
                "Espero la transcripción antes de abrir el resumen."));
            return;
        }
        markDayCompleted();
        summaryVisible=true;
        String status=session().optString("reportStatus","");
        if("done".equals(status)||"generating".equals(status)
            ||"error".equals(status)||"missing".equals(status)
            ||"interrupted".equals(status)){
            render();
            return;
        }
        completeAndAnalyze();
    }
    private void regenerateWithConfirmation(){
        if(completingSession)return;
        new AlertDialog.Builder(this)
            .setTitle(tr("Rigenerare l'osservazione IA?",
                "Regenerate AI reflection?","¿Regenerar la observación IA?"))
            .setMessage(tr(
                "Sostituirà l'osservazione salvata con una nuova analisi. Il punteggio e lo streak non cambieranno.",
                "This will replace the saved reflection. Your score and streak will not change.",
                "Sustituirá la observación guardada. Tu puntuación y racha no cambiarán."))
            .setNegativeButton(tr("Annulla","Cancel","Cancelar"),null)
            .setPositiveButton(tr("Rigenera","Regenerate","Regenerar"),
                (dialog,which)->completeAndAnalyze())
            .show();
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
    private String aiSetupExplanation(){
        android.content.SharedPreferences options=getSharedPreferences("options",0);
        String groqKey=document.optString("groqApiKey","").trim();
        boolean permission=options.getBoolean("groq_ai_consent",false);
        if(groqKey.startsWith("gsk_") && !permission){
            return tr("Groq è configurato per la voce, ma l'analisi del diario richiede un consenso distinto in Impostazioni → Osservazione IA online.",
                "Groq is configured, but journal analysis needs separate consent in Settings → Online AI Reflection.",
                "Groq está configurado, pero analizar el diario requiere otro consentimiento en Ajustes → IA online.");
        }
        if(!groqKey.startsWith("gsk_")){
            return tr("Per il verdetto IA online inserisci una chiave Groq valida e attiva il consenso specifico all'analisi del diario.",
                "For online AI reflections, add a valid Groq key and enable separate journal-analysis consent.",
                "Para el análisis IA online, añade una clave Groq válida y activa el consentimiento del diario.");
        }
        if(!permission){
            return tr("L'analisi Groq è disattivata per privacy. Abilitala esplicitamente nelle impostazioni.",
                "Groq analysis is off for privacy. Enable it explicitly in Settings.",
                "El análisis Groq está desactivado por privacidad. Actívalo explícitamente en Ajustes.");
        }
        return tr("Non è attivo nessun modello IA. Verifica Groq nelle impostazioni oppure prepara Qwen3 offline.",
            "No AI model is active. Check Groq Settings or prepare offline Qwen3.",
            "No hay un modelo IA activo. Revisa Groq o prepara Qwen3 offline.");
    }
    private void requestAnalysis(){
        if(completingSession)return;
        if(AiEngine.reportAvailable(this)){
            if(session().optBoolean("aiReport",false))regenerateWithConfirmation();
            else completeAndAnalyze();
        }else{
            showSettings();
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
        markDayCompleted();
        JSONObject entry=session();
        if(!AiEngine.reportAvailable(this)){
            set(entry,"reportStatus","missing");
            set(entry,"aiError",aiSetupExplanation());
            set(entry,"report",fallback());
            set(entry,"aiReport",false);
            persist();render();return;
        }
        completingSession=true;
        final String analysisDate=date;
        set(entry,"reportStatus","generating");
        set(entry,"aiProvider",AiEngine.provider(this));
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
            persist();if(summaryVisible||step==6)render();
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
            persist();if(summaryVisible||step==6)render();
            if(!target.optBoolean("aiReport",false))return;
            AiEngine.encouragement(this,lang,diagnosis,goals,diary,(phrase,pError)->{
                if(isFinishing())return;
                if(pError==null&&phrase!=null&&!phrase.trim().isEmpty()&&phrase.length()<240){
                    String message=phrase.replaceAll("(?s)<think>.*?</think>","").trim();
                    if(!message.isEmpty()){
                        set(target,"encouragement",message);
                        set(document,"splashPhrase",message);
                        set(document,"splashDiagnosis",diagnosis);
                        persist();
                        if(summaryVisible)render();
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
        if(tts==null)return;
        configureVoice();
        java.util.List<String> paragraphs=SpeechText.chunks(text,1500);
        int index=0;
        for(String item:paragraphs){
            if(item.isEmpty())continue;
            tts.speak(item,index==0?TextToSpeech.QUEUE_FLUSH:TextToSpeech.QUEUE_ADD,
                null,"cbt-"+System.nanoTime()+"-"+(index++));
        }
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
        if(cloudSttReady())autoTranscribe(endedKey);
        else notifyUser(tr("Audio salvato. Configura l'API nelle impostazioni per trascriverlo.",
            "Audio saved. Configure the API in Settings to transcribe it.",
            "Audio guardado. Configura la API en Ajustes para transcribirlo."));
    }
    private String sttProvider(){
        return getSharedPreferences("options",0).getString("stt_provider","groq");
    }
    private String sttKey(String provider){
        return document.optString("groq".equals(provider)?"groqApiKey":"cloudSttApiKey","");
    }
    private boolean cloudSttReady(){
        android.content.SharedPreferences p=getSharedPreferences("options",0);
        String provider=sttProvider();
        return p.getBoolean("cloud_stt_consent",false)
            && provider.equals(p.getString("stt_consent_provider",""))
            && !sttKey(provider).isEmpty();
    }
    private void toggleRecord(String key,Button btn){
        if(recorder!=null){stopRecording();return;}
        if(transcriptRunning){
            notifyUser(tr("Trascrizione precedente in corso. Aspetta la fine.",
                "Previous transcription is still running.",
                "La transcripción anterior está en curso."));return;
        }
        if(!micPermission())return;
        if(!cloudSttReady()){
            new AlertDialog.Builder(this)
             .setTitle(tr("Trascrizione automatica online","Automatic online transcription",
               "Transcripción automática en línea"))
             .setMessage(tr(
               "Per trascrivere automaticamente i file M4A puoi configurare Groq Whisper (piano gratuito con limiti) oppure OpenAI (API a pagamento). Serve la chiave personale del servizio selezionato e il consenso a inviare l'audio; puoi anche registrare senza trascrizione.",
               "To automatically transcribe saved M4A files, use Groq Whisper (free tier with limits) or OpenAI (paid API), with your personal API key and explicit consent to upload the recording. You can still record without transcription.",
               "Para transcribir grabaciones M4A puedes usar Groq Whisper (plan gratuito con límites) u OpenAI (API de pago). Se necesita una clave personal y consentimiento para enviar el audio. También puedes grabar sin transcripción."))
             .setPositiveButton(tr("Configura","Configure","Configurar"),(dialog,which)->showSettings())
             .setNeutralButton(tr("Solo registra","Record audio only","Solo grabar"),
                (dialog,which)->beginAudioRecording(key,btn))
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
        File recorded=audioFile(key);
        if(!recorded.isFile())return;
        if(!cloudSttReady()){
            set(session(),"speechError_"+key,"API_NOT_CONFIGURED");persist();
            notifyUser(tr("Audio salvato. Serve una chiave API personale per la trascrizione.",
                "Audio saved. Configure your personal API credential.",
                "Audio guardado. Configura tu clave API personal."));
            return;
        }
        transcriptRunning=true;
        final String recordedDate=date;
        final String provider=sttProvider();
        final String apiKey=sttKey(provider);
        final String selectedLang=lang;
        notifyUser(tr("Audio salvato. Trascrizione online in corso…",
            "Audio saved. Online transcription in progress…",
            "Audio guardado. Transcripción online en curso…"));
        CloudTranscriber.transcribe(recorded,selectedLang,apiKey,provider,(transcript,error)->{
            transcriptRunning=false;
            if(isFinishing())return;
            JSONObject sessions=document.optJSONObject("sessions");
            JSONObject values=sessions==null?null:sessions.optJSONObject(recordedDate);
            if(values==null)return;
            if(error!=null||transcript==null||transcript.trim().isEmpty()){
                String detail=error==null?"No speech recognized":error.getMessage();
                if(detail==null)detail="Unknown transcription error";
                set(values,"speechError_"+key,detail.substring(0,Math.min(detail.length(),180)));
                persist();
                // A Toast truncates the most important details of HTTP 429. Show a dialog.
                if(error instanceof CloudTranscriber.ApiError){
                    CloudTranscriber.ApiError api=(CloudTranscriber.ApiError)error;
                    AlertDialog.Builder warning=new AlertDialog.Builder(this)
                        .setTitle(tr("Problema API: audio conservato",
                            "API issue: audio preserved","Problema API: audio guardado"))
                        .setMessage(detail+"\n\n"+tr(
                            "La tua registrazione resta sul dispositivo. Potrai riprovare dalle impostazioni dopo aver risolto il problema.",
                            "Your recording remains on the device. Retry from Settings once the issue is resolved.",
                            "La grabación permanece en el teléfono. Reintenta desde Ajustes después de resolver el problema."))
                        .setNegativeButton(tr("Chiudi","Close","Cerrar"),null);
                    if(api.httpStatus==429){
                        warning.setPositiveButton("groq".equals(provider)?
                            tr("Limiti gratuiti Groq","Groq free limits","Límites gratis Groq"):
                            tr("Controlla fatturazione API","Check API billing","Comprobar facturación API"),
                            (dialog,which)->startActivity(new Intent(Intent.ACTION_VIEW,
                              Uri.parse("groq".equals(provider)?
                                "https://console.groq.com/settings/limits":
                                "https://platform.openai.com/settings/organization/billing/overview"))));
                    }
                    warning.show();
                }else{
                    notifyUser(tr("Audio conservato. Trascrizione non riuscita: ",
                        "Audio preserved. Transcription failed: ",
                        "Audio guardado. Falló la transcripción: ")+detail);
                }
            }else{
                String old=values.optString(key,"").trim();
                String updated=old.isEmpty()?transcript.trim():old+"\n"+transcript.trim();
                set(values,key,updated);
                set(values,"transcribed_"+key,true);
                set(values,"speechError_"+key,"");
                persist();
                if(date.equals(recordedDate)){
                    for(int i=0;i<currentKeys.size();i++){
                        if(currentKeys.get(i).equals(key)){
                            EditText answer=currentFields.get(i);
                            if(answer!=null&&answer.isAttachedToWindow()){
                                answer.setText(updated);
                                answer.setSelection(answer.length());
                                scrollFieldIntoView(answer);
                            }
                            break;
                        }
                    }
                }
                notifyUser(tr("Trascrizione completata e salvata nel diario.",
                    "Transcription complete and saved.",
                    "Transcripción completada y guardada."));
            }
            if(finishAfterTranscript){
                finishAfterTranscript=false;
                finishSession();
            }
        });
    }
    private void retryUntranscribedToday(){
        if(!cloudSttReady()){
            notifyUser(tr("Configura prima la chiave API e attiva il consenso.",
                "Configure the API key and consent first.",
                "Configura primero la clave API y el consentimiento."));
            return;
        }
        if(recorder!=null||transcriptRunning)return;
        java.util.ArrayList<String> pending=new java.util.ArrayList<>();
        for(int page=0;page<6;page++)for(int i=0;i<3;i++){
            if(page==1||(page==4&&i==2))continue;
            String k="p"+page+"_"+i;
            if(audioFile(k).isFile()&&!session().optBoolean("transcribed_"+k,false))
                pending.add(k);
        }
        if(pending.isEmpty()){
            notifyUser(tr("Non ci sono registrazioni in sospeso per oggi.",
                "No pending recordings for today.",
                "No hay grabaciones pendientes de hoy."));
            return;
        }
        retryPendingSequentially(pending,0);
    }
    private void retryPendingSequentially(java.util.ArrayList<String> pending,int index){
        if(index>=pending.size()||isFinishing())return;
        String key=pending.get(index);
        File recorded=audioFile(key);
        String provider=sttProvider();
        String apiKey=sttKey(provider);
        String selectedLang=lang;
        CloudTranscriber.transcribe(recorded,selectedLang,apiKey,provider,(text,error)->{
            if(isFinishing())return;
            if(error==null&&text!=null&&!text.trim().isEmpty()){
                appendTranscript(key,text,null);
                set(session(),"speechError_"+key,"");persist();
            }else{
                String reason=error==null?"No speech":error.getMessage();
                set(session(),"speechError_"+key,reason==null?"Unknown":reason);
                persist();
                notifyUser(tr("Una registrazione non è stata trascritta: ",
                    "A recording could not be transcribed: ",
                    "No se pudo transcribir una grabación: ")+reason);
            }
            if(index+1<pending.size())retryPendingSequentially(pending,index+1);
            else {render();notifyUser(tr("Recupero delle registrazioni terminato.",
                "Recording recovery completed.",
                "Recuperación de grabaciones terminada."));}
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
        body.addView(label(tr("TRASCRIZIONE AUDIO ONLINE",
          "ONLINE AUDIO TRANSCRIPTION","TRANSCRIPCIÓN DE AUDIO ONLINE"),15,CYAN,true));
        space(body,7);
        body.addView(label(tr(
            "Groq Whisper Large V3 ha un piano gratuito con limiti giornalieri: non richiede credito OpenAI. Richiede una chiave Groq gratuita. In alternativa resta OpenAI con fatturazione API.",
            "Groq Whisper Large V3 has a daily-limited free tier. A free Groq API key is required; OpenAI paid API remains available.",
            "Groq Whisper Large V3 tiene un plan gratuito con límites diarios y requiere una clave Groq. OpenAI de pago sigue disponible."),13,SUB,false));
        space(body,7);
        Spinner sttService=new Spinner(this);
        sttService.setAdapter(new ArrayAdapter<String>(this,
            android.R.layout.simple_spinner_dropdown_item,new String[]{
              "Groq Whisper V3 — Free Tier","OpenAI Speech-to-Text — Paid API"}));
        String selectedProvider=p.getString("stt_provider","groq");
        sttService.setSelection("openai".equals(selectedProvider)?1:0);
        body.addView(sttService);space(body,9);
        Button groqWebsite=button(tr("Crea chiave gratuita su Groq",
          "Get a free Groq API key","Obtener clave Groq gratuita"),false);
        groqWebsite.setOnClickListener(v->startActivity(new Intent(Intent.ACTION_VIEW,
            Uri.parse("https://console.groq.com/keys"))));
        body.addView(groqWebsite);space(body,8);
        EditText groqKey=new EditText(this);
        groqKey.setTextColor(FG);groqKey.setHintTextColor(SUB);
        groqKey.setSingleLine(true);
        groqKey.setInputType(android.text.InputType.TYPE_CLASS_TEXT|
            android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        groqKey.setHint(document.optString("groqApiKey","").isEmpty()?
          "Groq API key (gsk_…)":
          tr("Chiave Groq già cifrata — vuoto per conservarla",
              "Groq key encrypted — leave blank to keep",
              "Clave Groq cifrada — dejar en blanco para conservar"));
        body.addView(groqKey);
        CheckBox removeGroq=new CheckBox(this);removeGroq.setTextColor(FG);
        removeGroq.setText(tr("Elimina chiave Groq","Delete Groq key","Eliminar clave Groq"));
        body.addView(removeGroq);space(body,6);
        EditText cloudKey=new EditText(this);
        cloudKey.setTextColor(FG);cloudKey.setHintTextColor(SUB);cloudKey.setSingleLine(true);
        cloudKey.setInputType(android.text.InputType.TYPE_CLASS_TEXT|
            android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        cloudKey.setHint(document.optString("cloudSttApiKey","").isEmpty()?
          tr("Chiave OpenAI alternativa (sk-…)", "Alternative OpenAI key (sk-…)",
            "Clave OpenAI alternativa (sk-…)"):
          tr("Chiave OpenAI cifrata — vuoto per conservarla",
            "OpenAI key encrypted — leave blank to keep",
            "Clave OpenAI cifrada — dejar vacío para conservar"));
        body.addView(cloudKey);
        CheckBox removeKey=new CheckBox(this);removeKey.setTextColor(FG);
        removeKey.setText(tr("Elimina chiave OpenAI","Delete OpenAI key","Eliminar clave OpenAI"));
        body.addView(removeKey);space(body,7);
        CheckBox speechConsent=new CheckBox(this);speechConsent.setTextColor(FG);
        speechConsent.setText(tr(
          "Autorizzo a inviare il file audio al servizio di trascrizione selezionato",
          "I consent to send my recording to the selected transcription service",
          "Autorizo enviar mi audio al servicio de transcripción seleccionado"));
        speechConsent.setChecked(p.getBoolean("cloud_stt_consent",false)
            && selectedProvider.equals(p.getString("stt_consent_provider","")));
        sttService.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            public void onItemSelected(android.widget.AdapterView<?> parent,View view,int pos,long id){
                String provider=pos==0?"groq":"openai";
                speechConsent.setChecked(p.getBoolean("cloud_stt_consent",false)
                  && provider.equals(p.getString("stt_consent_provider","")));
            }
            public void onNothingSelected(android.widget.AdapterView<?> parent){}
        });
        body.addView(speechConsent);space(body,11);
        body.addView(label(tr("OSSERVAZIONE IA ONLINE (FACOLTATIVA)",
              "ONLINE AI REFLECTION (OPTIONAL)",
              "OBSERVACIÓN IA ONLINE (OPCIONAL)"),15,CYAN,true));
        space(body,5);
        body.addView(label(tr(
          "Groq GPT-OSS 120B può generare l'osservazione finale anche senza Qwen3 locale. Attivandolo, ogni volta che premi Termina vengono inviati a Groq i testi del diario, gli obiettivi e la diagnosi eventualmente dichiarata. Puoi lasciarlo disattivato e usare solo Qwen3 offline.",
          "Groq GPT-OSS 120B can produce the final reflection without Qwen3 locally. Enabling this sends your diary text, goals and any declared diagnosis to Groq whenever you complete a session. Leave it off to use local Qwen3.",
          "Groq GPT-OSS 120B puede generar la reflexión sin Qwen3 local. Al activarlo se envían a Groq el diario, objetivos y cualquier diagnóstico declarado al terminar. Desactívalo para usar Qwen3 local."),13,SUB,false));
        CheckBox onlineAiConsent=new CheckBox(this);onlineAiConsent.setTextColor(FG);
        onlineAiConsent.setText(tr(
          "Autorizzo l'invio dei contenuti CBT e della diagnosi dichiarata a Groq per l'IA online",
          "I consent to sending CBT journal content and declared diagnosis to Groq for online AI",
          "Autorizo enviar el diario CBT y diagnóstico declarado a Groq para IA online"));
        onlineAiConsent.setChecked(p.getBoolean("groq_ai_consent",false));
        body.addView(onlineAiConsent);space(body,12);
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
        Button testAi=button(tr("Verifica l'IA selezionata (Qwen3/Groq)",
             "Test selected AI (Qwen3/Groq)","Probar la IA elegida (Qwen3/Groq)"),false);
        testAi.setOnClickListener(v->{
            if(!AiEngine.reportAvailable(this)){
                notifyUser(tr("Attiva Qwen3 oppure Groq (con chiave e consenso), poi salva.",
                    "Enable Qwen3 or Groq (key and consent), then save.",
                    "Activa Qwen3 o Groq (clave y consentimiento) y guarda."));return;
            }
            notifyUser(tr("Verifica IA in corso…","Testing AI…",
              "Probando IA…"));
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
        Button recover=button(tr("Trascrivi le vecchie registrazioni di oggi",
           "Transcribe today's saved recordings",
           "Transcribir las grabaciones guardadas de hoy"),false);
        body.addView(recover);space(body,10);
        Button save=button(tr("Salva impostazioni","Save settings","Guardar ajustes"),true);
        body.addView(save);
        AlertDialog dialog=new AlertDialog.Builder(this).setView(sc).create();
        recover.setOnClickListener(v->{dialog.dismiss();retryUntranscribedToday();});
        save.setOnClickListener(v->{
            String newDiag=diagnosis.getText().toString().trim();
            String oldDiag=document.optString("diagnosis","");
            set(document,"diagnosis",newDiag);set(document,"goals",goals.getText().toString().trim());
            String typedKey=cloudKey.getText().toString().trim();
            if(removeKey.isChecked())set(document,"cloudSttApiKey","");
            else if(!typedKey.isEmpty())set(document,"cloudSttApiKey",typedKey);
            String enteredGroq=groqKey.getText().toString().trim();
            if(removeGroq.isChecked())set(document,"groqApiKey","");
            else if(!enteredGroq.isEmpty())set(document,"groqApiKey",enteredGroq);
            if(!newDiag.equals(oldDiag)){set(document,"splashPhrase","");set(document,"splashDiagnosis","");}
            persist();
            String l=languages.getSelectedItemPosition()==1?"en":languages.getSelectedItemPosition()==2?"es":"it";
            p.edit().putString("lang",l).putBoolean("enabled",reminder.isChecked())
                .putString("stt_provider",sttService.getSelectedItemPosition()==0?"groq":"openai")
                .putString("stt_consent_provider",speechConsent.isChecked()?
                    (sttService.getSelectedItemPosition()==0?"groq":"openai"):"")
                .putBoolean("cloud_stt_consent",speechConsent.isChecked())
                .putBoolean("groq_ai_consent",onlineAiConsent.isChecked())
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
        if(isFinishing()||AiEngine.reportAvailable(this))return;
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
