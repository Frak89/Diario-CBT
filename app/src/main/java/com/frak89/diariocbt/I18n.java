package com.frak89.diariocbt;

import java.util.Locale;
public final class I18n {
    private I18n() {}
    public static String lang(android.content.Context c) {
        return c.getSharedPreferences("options",0).getString("lang","it");
    }
    public static Locale locale(String lang) {
        return Locale.forLanguageTag(lang.equals("es")?"es-ES":lang.equals("en")?"en-US":"it-IT");
    }
    public static String t(String lang, String it, String en, String es) {
        return lang.equals("en")?en:lang.equals("es")?es:it;
    }
    public static String[] headings(String l) {
        if(l.equals("en")) return new String[]{"Meaningful moments","How did you feel?","Situation and thoughts","Explore the evidence","A kinder perspective","A small next step","Reflection and suggestions"};
        if(l.equals("es")) return new String[]{"Momentos importantes","¿Cómo te has sentido?","Situación y pensamientos","Explora las pruebas","Una mirada más amable","Un pequeño paso","Observación y sugerencias"};
        return new String[]{"Momenti significativi","Come ti sei sentito?","Situazione e pensieri","Esplora le prove","Una prospettiva gentile","Un piccolo passo","Osservazione e suggerimenti"};
    }
    public static String[][] questions(String l) {
        if(l.equals("en")) return new String[][]{
          {"What did you do today?","A meaningful moment","Something I managed to do"},
          {"Morning mood (0–10)","Afternoon mood (0–10)","Evening mood (0–10)"},
          {"What happened? Describe only what is observable.","What automatic thought came up?","What emotions did you notice?"},
          {"What supports that thought?","What facts challenge it?","How did you feel in your body?"},
          {"What could you say to a close friend?","What is another realistic interpretation?","How intense was the feeling? (0–10)"},
          {"What is one small achievable action now?","What support would help me?","What went a little better today?"},{}
        };
        if(l.equals("es")) return new String[][]{
          {"¿Qué has hecho hoy?","Un momento importante","Algo que he conseguido"},
          {"Ánimo por la mañana (0–10)","Ánimo por la tarde (0–10)","Ánimo por la noche (0–10)"},
          {"¿Qué ocurrió? Describe solo los hechos observables.","¿Qué pensamiento automático apareció?","¿Qué emociones notaste?"},
          {"¿Qué pruebas apoyan ese pensamiento?","¿Qué hechos lo cuestionan?","¿Qué sentiste en el cuerpo?"},
          {"¿Qué le dirías a un buen amigo?","¿Qué otra interpretación realista existe?","¿Intensidad de la emoción? (0–10)"},
          {"¿Qué pequeña acción puedes hacer ahora?","¿Qué apoyo te ayudaría?","¿Qué fue un poco mejor hoy?"},{}
        };
        return new String[][]{
          {"Cosa ho fatto oggi?","Un momento importante","Qualcosa che sono riuscito a fare"},
          {"Umore mattino (0–10)","Umore pomeriggio (0–10)","Umore sera (0–10)"},
          {"Cosa è accaduto? Descrivi soltanto fatti osservabili.","Quale pensiero automatico è emerso?","Quali emozioni hai provato?"},
          {"Quali fatti sostengono quel pensiero?","Quali fatti lo mettono in dubbio?","Cosa hai sentito nel corpo?"},
          {"Cosa diresti a una persona cara?","Quale interpretazione alternativa è realistica?","Quanto era intensa l’emozione? (0–10)"},
          {"Qual è una piccola azione fattibile ora?","Quale supporto potrebbe aiutarti?","Cos'è andato un po' meglio oggi?"},{}
        };
    }
}
