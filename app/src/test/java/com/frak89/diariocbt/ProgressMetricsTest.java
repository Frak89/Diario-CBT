package com.frak89.diariocbt;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.HashMap;
import java.util.Map;

public class ProgressMetricsTest {
    @Test public void zeroWhenNoExercisesHaveAnswers(){
        assertEquals(0,ProgressMetrics.score(new HashMap<>()));
    }
    @Test public void tenWhenEveryExerciseHasAnAnswer(){
        Map<String,String> answers=new HashMap<>();
        for(String key:ProgressMetrics.SCORED_FIELDS)answers.put(key,"Un piccolo passo.");
        assertEquals(10,ProgressMetrics.score(answers));
    }
    @Test public void usesCurrentAnswersInsteadOfEarlierEmptySnapshot(){
        // Regression: v0.2.8 cached completionScore=0 before six answers were filled.
        int obsoleteSavedScore=0;
        Map<String,String> answers=new HashMap<>();
        for(String key:ProgressMetrics.SCORED_FIELDS)answers.put(key,"Compilato");
        int live=ProgressMetrics.score(answers);
        assertEquals(10,live);
        assertNotEquals(obsoleteSavedScore,live);
    }
    @Test public void ignoresWhitespaceAnswers(){
        Map<String,String> answers=new HashMap<>();
        answers.put("p2_0","  ");
        answers.put("p2_1","Provo");
        assertEquals(2,ProgressMetrics.score(answers));
    }
}
