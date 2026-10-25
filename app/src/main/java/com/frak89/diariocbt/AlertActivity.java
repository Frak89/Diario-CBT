package com.frak89.diariocbt;
import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
public class AlertActivity extends Activity {
 @Override public void onCreate(Bundle b) {
    super.onCreate(b);
    setShowWhenLocked(true); setTurnScreenOn(true);
    getWindow().setStatusBarColor(Color.rgb(9,10,27));
    LinearLayout layout=new LinearLayout(this);layout.setOrientation(1);
    layout.setGravity(Gravity.CENTER);layout.setPadding(32,32,32,32);
    layout.setBackgroundColor(Color.rgb(9,10,27));setContentView(layout);
    String l=I18n.lang(this);
    TextView brain=new TextView(this);brain.setText("🧠");
    brain.setTextSize(82);brain.setGravity(Gravity.CENTER);layout.addView(brain);
    TextView title=new TextView(this);title.setTextSize(29);title.setTextColor(Color.WHITE);
    title.setGravity(Gravity.CENTER);title.setText(I18n.t(l,"È il momento di ascoltarti","Time to listen to yourself","Es momento de escucharte"));
    layout.addView(title);
    TextView sub=new TextView(this);sub.setTextColor(0xffaab7d3);
    sub.setTextSize(17);sub.setGravity(Gravity.CENTER);sub.setPadding(8,28,8,28);
    sub.setText(I18n.t(l,"Il tuo diario CBT ti aspetta. Nessuna pressione: un passo alla volta.",
     "Your CBT journal awaits. No pressure: one step at a time.","Tu diario CBT te espera. Sin presión: paso a paso."));
    layout.addView(sub);
    boolean preview=getIntent().getBooleanExtra("reminder_preview",false);
    if(preview){
        TextView previewNote=new TextView(this);
        previewNote.setText(I18n.t(l,
            "Anteprima della schermata. La sua apertura automatica dipende dai permessi Android.",
            "Preview only. Automatic full-screen launch depends on Android permissions.",
            "Solo vista previa. La apertura automática depende de los permisos de Android."));
        previewNote.setTextColor(0xff62e8ec);previewNote.setGravity(Gravity.CENTER);
        previewNote.setPadding(0,0,0,32);layout.addView(previewNote);
        addButton(layout,I18n.t(l,"Chiudi anteprima","Close preview","Cerrar vista previa"),
            this::finish);
        return;
    }
    addButton(layout,I18n.t(l,"Inizia CBT","Start CBT","Empezar CBT"),()->{
       startActivity(new Intent(this,MainActivity.class));finish();
    });
    addButton(layout,I18n.t(l,"Tra 15 minuti","In 15 minutes","Dentro de 15 minutos"),()->{
       sendBroadcast(new Intent(this,ReminderReceiver.class).setAction(ReminderReceiver.SNOOZE));finish();
    });
    addButton(layout,I18n.t(l,"Salta oggi","Skip today","Omitir hoy"),()->{
       sendBroadcast(new Intent(this,ReminderReceiver.class).setAction(ReminderReceiver.SKIP));finish();
    });
 }
 private void addButton(LinearLayout l,String s,Runnable action) {
    Button b=new Button(this);b.setAllCaps(false);b.setText(s);b.setTextSize(18);
    l.addView(b,new LinearLayout.LayoutParams(-1,-2));b.setOnClickListener(v->action.run());
 }
}
