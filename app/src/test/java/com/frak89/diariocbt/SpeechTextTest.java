package com.frak89.diariocbt;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.List;

public class SpeechTextTest {
    @Test public void doesNotPronounceAsterisks() {
        String t=SpeechText.clean("## **Osservazione**\n- **Respira** e prova *un passo*.");
        assertEquals("Osservazione\nRespira e prova un passo.",t);
        assertFalse(t.contains("*"));
    }
    @Test public void keepsLinkLabelsWithoutUrls() {
        assertEquals("Visita il sito.",SpeechText.clean("Visita [il sito](https://example.com)."));
    }
    @Test public void longReportsAreChunkedWithoutMarkdown() {
        String content="**Titolo**\n"+("Questa è una frase di esempio. ").repeat(320);
        List<String> parts=SpeechText.chunks(content,900);
        assertTrue(parts.size()>2);
        for(String part:parts){
            assertTrue(part.length()<=900);
            assertFalse(part.contains("*"));
        }
    }
}
