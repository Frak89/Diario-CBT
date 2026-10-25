package com.frak89.diariocbt;

import org.junit.Test;
import java.time.*;
import static org.junit.Assert.*;

public class ReminderTimesTest {
    private final ZoneId rome=ZoneId.of("Europe/Rome");

    @Test public void schedulesLaterToday(){
        long now=ZonedDateTime.of(2026,10,24,18,0,0,0,rome).toInstant().toEpochMilli();
        long expected=ZonedDateTime.of(2026,10,24,21,0,0,0,rome).toInstant().toEpochMilli();
        assertEquals(expected,ReminderTimes.nextMillis(now,rome,21,0));
    }
    @Test public void schedulesTomorrowWhenTimeHasPassed(){
        long now=ZonedDateTime.of(2026,10,24,23,0,0,0,rome).toInstant().toEpochMilli();
        long expected=ZonedDateTime.of(2026,10,25,21,0,0,0,rome).toInstant().toEpochMilli();
        assertEquals(expected,ReminderTimes.nextMillis(now,rome,21,0));
    }
    @Test public void adaptsToDaylightSaving(){
        long now=ZonedDateTime.of(2026,3,28,23,0,0,0,rome).toInstant().toEpochMilli();
        long expected=ZonedDateTime.of(2026,3,29,21,0,0,0,rome).toInstant().toEpochMilli();
        assertEquals(expected,ReminderTimes.nextMillis(now,rome,21,0));
    }
    @Test public void requiresValidClockTime(){
        try{ReminderTimes.nextMillis(0L,rome,25,0);fail("Expected validation failure");}
        catch(IllegalArgumentException expected){}
    }
}
