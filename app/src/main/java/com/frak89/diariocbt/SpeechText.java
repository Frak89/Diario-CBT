package com.frak89.diariocbt;

import java.util.ArrayList;
import java.util.List;

/** Converts Markdown-rich language-model output into natural speech.
 * Keeps meaningful punctuation while removing formatting tokens.
 */
public final class SpeechText {
    private SpeechText(){}
    public static String clean(String raw) {
        if(raw == null || raw.trim().isEmpty()) return "";
        String result=raw
            .replaceAll("(?s)<think>.*?</think>", " ")
            .replace("\r\n","\n")
            .replaceAll("(?s)`{3}[a-zA-Z0-9_-]*\n(.*?)`{3}", "$1")
            .replaceAll("!?\[([^\]]+)\]\((?:https?://)?[^)]+\)", "$1")
            .replaceAll("(?m)^\s{0,3}#{1,6}\s+", "")
            .replaceAll("(?m)^\s{0,3}>\s*", "")
            .replaceAll("(?m)^\s*[-*+]\s+", "")
            .replaceAll("(?m)^\s*[-=]{3,}\s*$", "")
            .replaceAll("\*{1,3}", "")
            .replaceAll("(?<!\w)_{1,3}|_{1,3}(?!\w)", "")
            .replace("`", "")
            .replaceAll("https?://\S+", "")
            .replaceAll("[ \t]+", " ")
            .replaceAll("\n{3,}", "\n\n")
            .trim();
        return result;
    }
    /** Divide long reflections so Android TextToSpeech does not exceed its per-request limit. */
    public static List<String> chunks(String raw,int limit){
        if(limit<100)throw new IllegalArgumentException("limit too small");
        String text=clean(raw);
        ArrayList<String> pieces=new ArrayList<>();
        while(!text.isEmpty()){
            if(text.length()<=limit){pieces.add(text);break;}
            int index=text.lastIndexOf('\n',limit);
            if(index<limit/2)index=text.lastIndexOf(". ",limit);
            if(index<limit/2)index=text.lastIndexOf(" ",limit);
            if(index<limit/2)index=limit;
            else if(index<text.length()&&text.charAt(index)=='.')index++;
            pieces.add(text.substring(0,index).trim());
            text=text.substring(index).trim();
        }
        return pieces;
    }
}
