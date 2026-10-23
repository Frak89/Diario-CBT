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
          {"What did you spend time on today?","What stayed with you today?","What is one thing you managed to do?"},
          {"Morning mood (0–10)","Afternoon mood (0–10)","Evening mood (0–10)"},
          {"What happened, in a few words?","What did you think in that moment?","How did you feel?"},
          {"What made you think that?","Is there another possible explanation?","What did you notice in your body?"},
          {"What would you say kindly to yourself?","Is there a different way to see it?","How intense was the feeling? (0–10)"},
          {"What small step could you take?","What help might you need?","What went a little better today?"},{}
        };
        if(l.equals("es")) return new String[][]{
          {"¿En qué has pasado el tiempo hoy?","Quale momento ti è rimasto in mente?","¿Qué has conseguido hacer hoy?"},
          {"Ánimo por la mañana (0–10)","Ánimo por la tarde (0–10)","Ánimo por la noche (0–10)"},
          {"¿Qué pasó, en pocas palabras?","¿Qué pensaste en ese momento?","¿Cómo te sentiste?"},
          {"¿Por qué pensaste eso?","¿Puede haber otra explicación?","¿Qué notaste en el cuerpo?"},
          {"¿Qué te dirías a ti mismo con cariño?","¿Puedes verlo de otra manera?","¿Intensidad de la emoción? (0–10)"},
          {"¿Qué pequeño paso puedes dar?","¿Qué ayuda necesitas?","¿Qué fue un poco mejor hoy?"},{}
        };
        return new String[][]{
          {"A cosa hai dedicato tempo oggi?","Quale momento ti è rimasto in mente?","Qual è una cosa che sei riuscito a fare?"},
          {"Umore mattino (0–10)","Umore pomeriggio (0–10)","Umore sera (0–10)"},
          {"Che cosa è successo, in poche parole?","Cosa hai pensato in quel momento?","Come ti sei sentito?"},
          {"Perché hai pensato quella cosa?","Potrebbe esserci un'altra spiegazione?","Cosa hai notato nel corpo?"},
          {"Cosa diresti a te stesso con gentilezza?","Puoi vedere la situazione in un altro modo?","Quanto era intensa l’emozione? (0–10)"},
          {"Quale piccolo passo puoi fare?","Di quale aiuto avresti bisogno?","Cosa è andato un po' meglio oggi?"},{}
        };
    }
    public static String hint(String lang, int page, int index) {
        String[][] it={
            {"Per esempio: lavoro, una passeggiata, una telefonata.","Anche una cosa piccola: una frase, un incontro, una pausa.","Per esempio: mi sono alzato, ho preparato pranzo."},
            {"","",""},
            {"Per esempio: una conversazione, un imprevisto, un ricordo.","Per esempio: «Non ce la faccio» oppure «Posso provarci».","Scegli una o più emozioni qui sotto oppure scrivi le tue."},
            {"Per esempio: «Me l'ha detto»; «Mi è già successo».","Per esempio: «Non conosco tutta la storia».","Per esempio: cuore accelerato, tensione, stanchezza."},
            {"Per esempio: «Sto facendo del mio meglio, un passo alla volta».","Per esempio: «È difficile, ma non significa che fallirò».",""},
            {"Per esempio: 5 minuti di movimento, un messaggio, una pausa.","Per esempio: parlare con un amico, chiedere supporto.","Per esempio: un gesto gentile, una cosa che hai completato."}
        };
        String[][] en={
            {"For example: work, a walk, a call.","Even something small: a phrase, a meeting, a break.","For example: I got up, I prepared lunch."},
            {"","",""},
            {"For example: a conversation, a surprise, a memory.","For example: «I can't do this» or «I can try».","Choose emotions below, or write your own."},
            {"For example: «They told me»; «It happened before».","For example: «I may not know the whole story».","For example: racing heart, tension, tiredness."},
            {"For example: «I'm trying, one step at a time».","For example: «This is hard, but it isn't proof I'll fail».",""},
            {"For example: a five-minute walk, a message, a break.","For example: talking to someone, asking for support.","For example: a kind gesture, something completed."}
        };
        String[][] es={
            {"Por ejemplo: trabajo, un paseo, una llamada.","Incluso algo pequeño: una frase, una pausa.","Por ejemplo: me levanté, preparé la comida."},
            {"","",""},
            {"Por ejemplo: una conversación, un imprevisto, un recuerdo.","Por ejemplo: «No puedo» o «Puedo intentarlo».","Elige emociones abajo o escribe las tuyas."},
            {"Por ejemplo: «Me lo dijeron»; «Ya pasó antes».","Por ejemplo: «Quizá no sé toda la historia».","Por ejemplo: corazón acelerado, tensión, cansancio."},
            {"Por ejemplo: «Estoy intentándolo, paso a paso».","Por ejemplo: «Es difícil, pero no prueba que vaya a fallar».",""},
            {"Por ejemplo: cinco minutos de paseo, un mensaje, una pausa.","Por ejemplo: hablar con alguien, pedir ayuda.","Por ejemplo: un gesto amable, algo terminado."}
        };
        if(page<0||page>=6||index<0||index>=3)return "";
        return lang.equals("en")?en[page][index]:lang.equals("es")?es[page][index]:it[page][index];
    }
    public static String[] emotionOptions(String lang) {
        if(lang.equals("en")) return new String[]{"😟 Anxiety","😢 Sadness","😠 Anger","😨 Fear","😞 Disappointment","😣 Frustration","😔 Guilt","😌 Relief","😊 Joy","💛 Gratitude","😴 Tiredness","😶 Numbness"};
        if(lang.equals("es")) return new String[]{"😟 Ansiedad","😢 Tristeza","😠 Rabia","😨 Miedo","😞 Decepción","😣 Frustración","😔 Culpa","😌 Alivio","😊 Alegría","💛 Gratitud","😴 Cansancio","😶 Vacío"};
        return new String[]{"😟 Ansia","😢 Tristezza","😠 Rabbia","😨 Paura","😞 Delusione","😣 Frustrazione","😔 Colpa","😌 Sollievo","😊 Gioia","💛 Gratitudine","😴 Stanchezza","😶 Vuoto"};
    }

}
