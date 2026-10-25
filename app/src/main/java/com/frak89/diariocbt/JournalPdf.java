package com.frak89.diariocbt;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Locale;

/** Fully offline, printable PDF: complete questionnaire + summary + AI reflection. */
public final class JournalPdf {
    private JournalPdf(){}
    private static final int WIDTH=595, HEIGHT=842, LEFT=44, RIGHT=551, TOP=52, BOTTOM=795;
    private static final int DARK=Color.rgb(21,35,63), BLUE=Color.rgb(17,109,127);
    private static final class Pages {
        final PdfDocument pdf=new PdfDocument();
        final Paint paint=new Paint(3);
        PdfDocument.Page page;
        Canvas canvas;
        int y=TOP,index=0;
        void next() {
            if(page!=null)pdf.finishPage(page);
            page=pdf.startPage(new PdfDocument.PageInfo.Builder(WIDTH,HEIGHT,++index).create());
            canvas=page.getCanvas(); y=TOP;
            paint.setColor(BLUE);paint.setTextSize(12);
            paint.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));
            canvas.drawText("DIARIO CBT  /  ARCHIVIO PERSONALE",LEFT,31,paint);
            paint.setColor(Color.LTGRAY);paint.setStrokeWidth(0.6f);
            canvas.drawLine(LEFT,38,RIGHT,38,paint);
            canvas.drawLine(LEFT,806,RIGHT,806,paint);
            paint.setTextSize(9);paint.setColor(DARK);
            canvas.drawText("Pagina "+index,LEFT,820,paint);
            paint.setTypeface(Typeface.DEFAULT);
        }
        void line(String value,int size,boolean heading) {
            if(page==null||y> BOTTOM-size-14)next();
            paint.setTextSize(size);
            paint.setTypeface(heading?Typeface.create(Typeface.DEFAULT,Typeface.BOLD):Typeface.DEFAULT);
            paint.setColor(heading?BLUE:DARK);
            int remaining=RIGHT-LEFT;
            String text=value==null?"":value.replace('\t',' ').replace('\r',' ').trim();
            if(text.isEmpty()){y+=size;return;}
            while(!text.isEmpty()){
                if(y>BOTTOM-size-12)next();
                int n=paint.breakText(text,true,remaining,null);
                if(n<=0)n=1;
                if(n<text.length()){
                    int gap=text.lastIndexOf(' ',n);
                    if(gap>n/3)n=gap;
                }
                String chunk=text.substring(0,n).trim();
                if(!chunk.isEmpty())canvas.drawText(chunk,LEFT,y,paint);
                y+=size+6;
                text=text.substring(n).trim();
            }
        }
        void section(String title){y+=10;line(title,16,true);y+=7;}
        void finish(OutputStream stream)throws IOException {
            if(page!=null)pdf.finishPage(page);
            try{pdf.writeTo(stream);}finally{pdf.close();}
        }
    }
    public static void export(Context context,JSONObject entry,String date,String language,OutputStream out)
            throws IOException {
        if(entry==null)throw new IOException("No diary session selected.");
        Pages pages=new Pages();
        String[] headings=I18n.headings(language);
        String[][] questions=I18n.questions(language);
        pages.section("Diario CBT — "+date);
        pages.line("Punteggio esercizi CBT: "+entry.optInt("completionScore",0)+"/10",13,false);
        pages.line("Giorni consecutivi: "+entry.optInt("completionStreak",0),13,false);
        String created=entry.optString("recordedAt",entry.optString("registeredAt",""));
        if(!created.isEmpty())pages.line("Registrazione: "+created,11,false);
        pages.line("Il punteggio riguarda la compilazione, non la salute mentale.",10,false);
        for(int p=0;p<6;p++){
            pages.section((p+1)+". "+headings[p]);
            for(int q=0;q<questions[p].length;q++){
                pages.line(questions[p][q],12,true);
                String text=entry.optString("p"+p+"_"+q,"").trim();
                pages.line(text.isEmpty()?"(nessuna risposta)":text,11,false);
                pages.y+=8;
            }
        }
        pages.section("Osservazione finale");
        pages.line(SpeechText.clean(entry.optString("report","Nessuna osservazione salvata.")),11,false);
        pages.section("Incoraggiamento");
        pages.line(SpeechText.clean(entry.optString("encouragement","")),11,false);
        pages.y+=24;
        pages.line("Documento personale. Non costituisce diagnosi o valutazione clinica.",10,false);
        pages.finish(out);
    }
    public static void exportAll(Context ctx,JSONObject document,String language,OutputStream out)
            throws IOException {
        Pages pages=new Pages();
        pages.section("Diario CBT — storico completo");
        JSONObject archive=document.optJSONObject("archive");
        JSONObject sessions=document.optJSONObject("sessions");
        JSONObject history=document.optJSONObject("history");
        java.util.TreeSet<String> dates=new java.util.TreeSet<>();
        if(archive!=null){java.util.Iterator<String> i=archive.keys();while(i.hasNext())dates.add(i.next());}
        if(history!=null){java.util.Iterator<String> i=history.keys();while(i.hasNext())dates.add(i.next());}
        String[] headings=I18n.headings(language);String[][] qs=I18n.questions(language);
        int count=0;
        for(String day:dates) {
            JSONArray entries=archive==null?null:archive.optJSONArray(day);
            int len=entries==null?0:entries.length();
            for(int which=0;which<=len;which++){
                JSONObject entry=which<len?entries.optJSONObject(which):
                    sessions==null?null:sessions.optJSONObject(day);
                if(entry==null)continue;
                if(which==len && entry.optString("report","").isEmpty()
                    && !entry.optBoolean("diaryRegistered",false))continue;
                pages.section(day+" — registrazione "+(++count));
                pages.line("Punteggio: "+entry.optInt("completionScore",0)+"/10   Streak: "+
                    entry.optInt("completionStreak",0),12,false);
                for(int p=0;p<6;p++) {
                    pages.section(headings[p]);
                    for(int q=0;q<qs[p].length;q++){
                        pages.line(qs[p][q],11,true);
                        pages.line(entry.optString("p"+p+"_"+q,"—"),11,false);
                    }
                }
                pages.section("Osservazione finale");
                pages.line(SpeechText.clean(entry.optString("report","")),11,false);
                pages.section("Incoraggiamento");
                pages.line(SpeechText.clean(entry.optString("encouragement","")),11,false);
            }
        }
        if(count==0)pages.line("Nessuna registrazione archiviata.",12,false);
        pages.finish(out);
    }
}
