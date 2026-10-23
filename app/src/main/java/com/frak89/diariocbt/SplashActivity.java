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
    getWindow().setStatusBarColor(0xff070a18);getWindow().setNavigationBarColor(0xff070a18);
    LinearLayout screen=new LinearLayout(this);screen.setGravity(Gravity.CENTER);
    screen.setPadding(dp(20),dp(24),dp(20),dp(24));screen.setOrientation(LinearLayout.VERTICAL);
    screen.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
        new int[]{0xff08172c,0xff14112c,0xff071623}));setContentView(screen);
    BrandArt.BrainOrbView brain=new BrandArt.BrainOrbView(this);
    screen.addView(brain,new LinearLayout.LayoutParams(-1,dp(235)));
    brain.setAlpha(0f);brain.animate().alpha(1f).setDuration(550).start();
    brain.animate().rotationY(360f).setDuration(2150).setInterpolator(new AccelerateDecelerateInterpolator()).start();
    BrandArt.ArcadeLogoView logo=new BrandArt.ArcadeLogoView(this);
    screen.addView(logo,new LinearLayout.LayoutParams(-1,dp(132)));
    logo.setScaleX(0.84f);logo.setScaleY(0.84f);
    logo.animate().scaleX(1).scaleY(1).setDuration(630).setInterpolator(new OvershootInterpolator(1.3f)).start();
    TextView subtitle=new TextView(this);subtitle.setText("DIARIO   •   MENTE   •   CRESCITA");
    subtitle.setTextColor(0xff67ecf6);subtitle.setGravity(Gravity.CENTER);
    subtitle.setTextSize(12);subtitle.setLetterSpacing(0.12f);screen.addView(subtitle);
    TextView message=new TextView(this);message.setTextColor(0xffe0eafa);
    message.setGravity(Gravity.CENTER);message.setTextSize(18);
    message.setPadding(dp(14),dp(28),dp(14),0);
    String fallback=I18n.t(I18n.lang(this),"Ogni piccolo passo merita spazio.",
        "Every small step deserves its place.","Cada pequeño paso merece su lugar.");
    String phrase=fallback;
    try {
       String stored=SecureStore.read(this).optString("splashPhrase","");
       String diagnosis=SecureStore.read(this).optString("diagnosis","");
       String phraseDiagnosis=SecureStore.read(this).optString("splashDiagnosis","");
       if(!stored.isEmpty() && diagnosis.equals(phraseDiagnosis)) phrase=stored;
    } catch(Exception ignored){}
    message.setText(phrase);screen.addView(message);
    message.setAlpha(0);message.animate().alpha(1f).setStartDelay(650).setDuration(750).start();
    new Handler(Looper.getMainLooper()).postDelayed(()->{
       if(!isFinishing()) {startActivity(new Intent(this,MainActivity.class));finish();}
    },2750);
 }
}
