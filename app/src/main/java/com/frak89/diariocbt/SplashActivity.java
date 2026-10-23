package com.frak89.diariocbt;
import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import android.view.animation.*;
import android.widget.*;
public class SplashActivity extends Activity {
 private int dp(int a){return (int)(a*getResources().getDisplayMetrics().density+.5f);}
 @Override public void onCreate(Bundle b) {
    super.onCreate(b);
    getWindow().setStatusBarColor(0xff070a18);
    LinearLayout screen=new LinearLayout(this);screen.setGravity(Gravity.CENTER);
    screen.setPadding(dp(18),dp(30),dp(18),dp(30));screen.setOrientation(LinearLayout.VERTICAL);
    screen.setBackgroundColor(0xff070a18);setContentView(screen);
    TextView brain=new TextView(this);brain.setGravity(Gravity.CENTER);brain.setText("🧠");
    brain.setTextSize(108);brain.setLayerType(View.LAYER_TYPE_HARDWARE,null);
    screen.addView(brain,new LinearLayout.LayoutParams(-1,dp(180)));
    brain.setCameraDistance(dp(1400));
    brain.animate().rotationY(360f).setDuration(2100).setInterpolator(new AccelerateDecelerateInterpolator()).start();
    TextView logo=new TextView(this);logo.setText("C B T");
    logo.setTextSize(65);logo.setTypeface(Typeface.create("sans-serif-black",Typeface.BOLD));
    logo.setTextColor(0xffffc13e);logo.setGravity(Gravity.CENTER);
    logo.setShadowLayer(dp(6),dp(4),dp(7),0xffe34e9a);
    screen.addView(logo);
    TextView subtitle=new TextView(this);subtitle.setText("DIARIO  •  MENTE  •  CRESCITA");
    subtitle.setTextColor(0xff6ceaf5);subtitle.setGravity(Gravity.CENTER);
    subtitle.setTextSize(12);screen.addView(subtitle);
    TextView message=new TextView(this);message.setTextColor(0xffdbe4f2);
    message.setGravity(Gravity.CENTER);message.setTextSize(18);
    message.setPadding(dp(12),dp(40),dp(12),0);
    String fallback=I18n.t(I18n.lang(this),"Un piccolo passo conta.","Every small step matters.","Cada pequeño paso cuenta.");
    String phrase=fallback;
    try {
       String stored=SecureStore.read(this).optString("splashPhrase","");
       String diagnosis=SecureStore.read(this).optString("diagnosis","");
       String phraseDiagnosis=SecureStore.read(this).optString("splashDiagnosis","");
       if(!stored.isEmpty() && diagnosis.equals(phraseDiagnosis)) phrase=stored;
    } catch(Exception ignored){}
    message.setText(phrase);screen.addView(message);
    new Handler(Looper.getMainLooper()).postDelayed(()->{
       if(!isFinishing()) {startActivity(new Intent(this,MainActivity.class));finish();}
    },2600);
 }
}
