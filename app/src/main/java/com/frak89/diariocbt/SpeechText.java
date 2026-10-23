package com.frak89.diariocbt;

import java.util.ArrayList;
import java.util.List;

/** Turns Markdown formatting in AI verdicts into natural spoken text. */
public final class SpeechText {
    private SpeechText() {}
    public static String clean(String raw) {
        if(raw == null || raw.trim().isEmpty()) return "";
        return raw
            .replaceAll("(?s)<think>.*?</think>", " ")
            .replace("\r\n", "\n")
            .replaceAll("(?m)^\\s{0,3}#{1,6}\\s+", "")
            .replaceAll("\\[([^\\]]+)\\]\\([^)]*\\)", "$1")
            .replaceAll("(?m)^\\s*>\\s*", "")
            .replaceAll("(?m)^\\s*[-*+]\\s+", "")
            .replaceAll("(?m)^\\s*[-=]{3,}\\s*$", "")
            .replaceAll("[*_\u0060]+", "")
            .replaceAll("https?://\\S+", "")
            .replaceAll("[ \\t]+", " ")
            .replaceAll("\\n{3,}", "\n\n")
            .trim();
    }
    public static List<String> chunks(String raw, int max) {
        if(max<100) throw new IllegalArgumentException("maximum chunk is too small");
        String value=clean(raw);
        List<String> results=new ArrayList<>();
        while(!value.isEmpty()) {
            if(value.length()<=max) {results.add(value);break;}
            int cut=value.lastIndexOf('\n',max);
            if(cut<max/2)cut=value.lastIndexOf(". ",max);
            if(cut<max/2)cut=value.lastIndexOf(' ',max);
            if(cut<max/2)cut=max;
            else if(value.charAt(cut)=='.')cut++;
            results.add(value.substring(0,cut).trim());
            value=value.substring(cut).trim();
        }
        return results;
    }
}
