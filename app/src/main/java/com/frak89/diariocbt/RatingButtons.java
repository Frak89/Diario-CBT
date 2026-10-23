package com.frak89.diariocbt;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.function.IntConsumer;

/** Accessible 0..10 buttons, retaining the selected value across page navigation. */
public final class RatingButtons {
    private RatingButtons(){}
    private static int dp(Context c,int n){
        return (int)(n*c.getResources().getDisplayMetrics().density+.5f);
    }
    private static GradientDrawable background(Context c,boolean selected){
        GradientDrawable d=new GradientDrawable();
        if(selected) d.setColors(new int[]{0xff57e5e8,0xff93aeff});
        else d.setColor(0xff151d33);
        d.setCornerRadius(dp(c,16));
        d.setStroke(dp(c,1),selected?0xffb7fbff:0xff40516c);
        return d;
    }
    public static LinearLayout create(Context ctx,String question,String saved,boolean mood,
                                      String lang,IntConsumer onChange,Runnable speak){
        LinearLayout outer=new LinearLayout(ctx);
        outer.setOrientation(LinearLayout.VERTICAL);
        TextView heading=new TextView(ctx);
        heading.setText(question);
        heading.setTextColor(0xfff2f6ff);
        heading.setTextSize(16);heading.setTypeface(null,1);
        outer.addView(heading);
        TextView hint=new TextView(ctx);
        hint.setText(I18n.t(lang,
            mood?"0 = molto difficile   •   10 = molto bene":"0 = nessuna intensità   •   10 = massima intensità",
            mood?"0 = very low   •   10 = very good":"0 = no intensity   •   10 = highest intensity",
            mood?"0 = muy difícil   •   10 = muy bien":"0 = sin intensidad   •   10 = intensidad máxima"));
        hint.setTextColor(0xffa3b3d0);hint.setTextSize(12);
        hint.setPadding(0,dp(ctx,4),0,dp(ctx,8));outer.addView(hint);
        final ArrayList<Button> buttons=new ArrayList<>();
        int initial=-1;
        try{initial=Integer.parseInt(saved);}catch(Exception ignored){}
        for(int rowIndex=0;rowIndex<3;rowIndex++){
            LinearLayout row=new LinearLayout(ctx);
            row.setGravity(Gravity.CENTER_VERTICAL);
            for(int col=0;col<4;col++){
                final int value=rowIndex*4+col;
                if(value>10){
                    row.addView(new View(ctx),new LinearLayout.LayoutParams(0,dp(ctx,52),1));
                    continue;
                }
                Button button=new Button(ctx);
                button.setAllCaps(false);
                button.setText(Integer.toString(value));
                button.setTextSize(18);
                button.setPadding(0,0,0,0);
                button.setMinHeight(dp(ctx,50));
                button.setMinimumWidth(0);
                boolean selected=(value==initial);
                button.setSelected(selected);
                button.setTextColor(selected?0xff061127:0xffe4ecff);
                button.setBackground(background(ctx,selected));
                button.setContentDescription(question+", "+value+" / 10");
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(ctx,52),1);
                lp.setMargins(dp(ctx,3),dp(ctx,3),dp(ctx,3),dp(ctx,3));
                row.addView(button,lp);buttons.add(button);
                button.setOnClickListener(v->{
                    for(int j=0;j<buttons.size();j++){
                        Button b=buttons.get(j);
                        boolean active=j==value;
                        b.setSelected(active);
                        b.setTextColor(active?0xff061127:0xffe4ecff);
                        b.setBackground(background(ctx,active));
                    }
                    onChange.accept(value);
                    button.animate().scaleX(1.07f).scaleY(1.07f).setDuration(90)
                       .withEndAction(()->button.animate().scaleX(1f).scaleY(1f).setDuration(90).start()).start();
                });
            }
            outer.addView(row);
        }
        Button hear=new Button(ctx);
        hear.setAllCaps(false);
        hear.setText("🔊 "+I18n.t(lang,"Ascolta domanda","Listen to question","Escuchar pregunta"));
        hear.setTextSize(13);hear.setTextColor(0xffdbecff);
        hear.setBackground(background(ctx,false));
        LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,dp(ctx,42));
        hp.setMargins(dp(ctx,3),dp(ctx,8),dp(ctx,3),0);
        outer.addView(hear,hp);
        hear.setOnClickListener(v->speak.run());
        return outer;
    }
}
