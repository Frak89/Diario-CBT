package com.frak89.diariocbt;

import java.util.Map;

/** Single source of truth for a CBT completion score (not a clinical score). */
public final class ProgressMetrics {
    public static final String[] SCORED_FIELDS={
        "p2_0","p2_1","p3_0","p3_1","p4_1","p5_0"
    };
    private ProgressMetrics(){}
    public static int score(Map<String,String> answers) {
        int completed=0;
        for(String key:SCORED_FIELDS) {
            String value=answers==null?null:answers.get(key);
            if(value!=null&&!value.trim().isEmpty()) completed++;
        }
        return (int)Math.round(completed*10.0/SCORED_FIELDS.length);
    }
}
