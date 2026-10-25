package com.frak89.diariocbt;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/** Choose next wall-clock reminder date without assuming fixed 24h across DST. */
public final class ReminderTimes {
    private ReminderTimes(){}
    public static long nextMillis(long currentMillis,ZoneId zone,int hour,int minute){
        if(hour<0||hour>23||minute<0||minute>59)
            throw new IllegalArgumentException("Invalid reminder clock time");
        Instant now=Instant.ofEpochMilli(currentMillis);
        LocalDate day=now.atZone(zone).toLocalDate();
        ZonedDateTime next=LocalDateTime.of(day,LocalTime.of(hour,minute)).atZone(zone);
        if(!next.toInstant().isAfter(now))
            next=LocalDateTime.of(day.plusDays(1),LocalTime.of(hour,minute)).atZone(zone);
        return next.toInstant().toEpochMilli();
    }
}
