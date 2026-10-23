package com.frak89.diariocbt;
import android.content.Context;
import android.graphics.*;
import android.view.View;

/** Original vector graphics, inspired by colourful arcade typography (no game assets). */
public final class BrandArt {
    private BrandArt(){}
    public static class BrainOrbView extends View {
        private final Paint pen=new Paint(Paint.ANTI_ALIAS_FLAG);
        public BrainOrbView(Context c){super(c);setLayerType(View.LAYER_TYPE_HARDWARE,null);setCameraDistance(1800f*getResources().getDisplayMetrics().density);}
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);
            int save=canvas.save();
            float size=Math.min(getWidth(),getHeight())/290f;
            canvas.translate(getWidth()/2f,getHeight()/2f);
            canvas.scale(size,size);
            pen.setShader(new RadialGradient(0,0,135,new int[]{0x704782ff,0x2039aef8,0x0000040f},null,Shader.TileMode.CLAMP));
            pen.setStyle(Paint.Style.FILL);canvas.drawCircle(0,0,144,pen);pen.setShader(null);
            pen.setColor(0x906bdcef);pen.setStyle(Paint.Style.STROKE);pen.setStrokeWidth(2.2f);
            canvas.drawOval(-139,-66,139,66,pen);
            canvas.save();canvas.rotate(-38);canvas.drawOval(-125,-78,125,78,pen);canvas.restore();
            pen.setColor(0xffff87ba);pen.setStrokeWidth(4);pen.setStyle(Paint.Style.FILL);
            pen.setShader(new LinearGradient(-75,-65,92,100,0xffffa3c2,0xff8b64ed,Shader.TileMode.CLAMP));
            Path base=new Path();
            base.moveTo(0,-82);
            base.cubicTo(-40,-114,-105,-73,-100,-29);
            base.cubicTo(-128,4,-94,49,-67,57);
            base.cubicTo(-59,83,-28,85,-12,75);
            base.cubicTo(-2,100,25,96,37,76);
            base.cubicTo(80,88,109,51,90,21);
            base.cubicTo(119,-12,100,-61,70,-66);
            base.cubicTo(57,-98,24,-105,0,-82);
            base.close();canvas.drawPath(base,pen);
            pen.setShader(null);
            pen.setColor(0xffb972f2);pen.setStyle(Paint.Style.FILL);
            canvas.drawOval(-12,68,22,105,pen);
            pen.setStyle(Paint.Style.STROKE);pen.setStrokeWidth(3.5f);pen.setStrokeCap(Paint.Cap.ROUND);
            pen.setColor(0xff65489e);
            Path folds=new Path();
            folds.moveTo(-6,-82);folds.cubicTo(-21,-65,-21,-50,-9,-37);
            folds.moveTo(-58,-85);folds.cubicTo(-80,-62,-39,-51,-53,-31);
            folds.moveTo(-93,-24);folds.cubicTo(-65,-28,-75,-4,-56,2);
            folds.moveTo(-78,42);folds.cubicTo(-49,15,-26,51,-43,67);
            folds.moveTo(-7,-15);folds.cubicTo(-40,-13,-34,17,-10,31);
            folds.moveTo(24,-84);folds.cubicTo(7,-55,49,-52,34,-26);
            folds.moveTo(69,-65);folds.cubicTo(36,-48,91,-18,67,2);
            folds.moveTo(91,24);folds.cubicTo(60,8,50,54,70,54);
            folds.moveTo(7,35);folds.cubicTo(35,5,54,37,34,71);
            canvas.drawPath(folds,pen);
            pen.setColor(0xffedd0ff);pen.setStrokeWidth(3);
            canvas.drawLine(0,-65,0,62,pen);
            pen.setStyle(Paint.Style.FILL);pen.setColor(0xff66eff7);
            canvas.drawCircle(-127,-15,5,pen);canvas.drawCircle(130,24,4,pen);
            canvas.restoreToCount(save);
        }
    }
    public static class ArcadeLogoView extends View {
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        public ArcadeLogoView(Context c){super(c);}
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);
            float scale=Math.min(getWidth()/330f,getHeight()/145f);
            canvas.save();canvas.translate(getWidth()/2f,getHeight()/2f);canvas.scale(scale,scale);
            p.setTypeface(Typeface.create("sans-serif-black",Typeface.BOLD));
            p.setTextSize(117f);p.setTextAlign(Paint.Align.CENTER);
            String[] chars={"C","B","T"};int[] paints={0xffffd84b,0xfffa7d82,0xff70f1fb};
            for(int i=0;i<3;i++){
                canvas.save();canvas.translate(-104+i*103,10);canvas.rotate(i==1?4:i==0?-8:7);
                p.setStyle(Paint.Style.STROKE);p.setStrokeJoin(Paint.Join.ROUND);p.setStrokeWidth(13);
                p.setColor(0xff1d235c);canvas.drawText(chars[i],0,0,p);
                p.setStrokeWidth(5);p.setColor(0xff5e3b9c);canvas.drawText(chars[i],0,0,p);
                p.setStyle(Paint.Style.FILL);p.setShadowLayer(7,0,6,0xff160b33);
                p.setColor(paints[i]);canvas.drawText(chars[i],0,0,p);p.clearShadowLayer();
                canvas.restore();
            }
            canvas.restore();
        }
    }
}
