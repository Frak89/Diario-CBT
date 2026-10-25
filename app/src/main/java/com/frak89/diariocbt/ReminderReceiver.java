package com.frak89.diariocbt;

import android.Manifest;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

import org.json.JSONObject;
import java.time.LocalDate;
import java.util.Calendar;

/**
 * Android reminder delivery:
 * - one scheduled alarm per day, rearmed after delivery
 * - exact timing if the user granted exact-alarm special access
 * - idle-safe inexact fallback when that access isn't available
 * - an immediate test bypassing both the configured time and completed-day filter
 *
 * Full-screen intents remain restricted by OS policy. An unlocked device normally
 * shows heads-up rather than launching a full-screen activity, even if authorized.
 */
public final class ReminderReceiver extends BroadcastReceiver {
    public static final String CHANNEL="cbt_reminder_alerts_v2";
    public static final String ALARM="com.frak89.diariocbt.ALARM";
    public static final String SNOOZE="com.frak89.diariocbt.SNOOZE";
    public static final String SKIP="com.frak89.diariocbt.SKIP";
    private static final int REMINDER_ID=42;
    private static final int SCHEDULE_REQUEST=100;
    private static final int SNOOZE_REQUEST=101;

    private static PendingIntent receiver(Context c,String action,int code) {
        Intent i=new Intent(c,ReminderReceiver.class).setAction(action);
        return PendingIntent.getBroadcast(c,code,i,
            PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    private static AlarmManager alarmManager(Context c) {
        return (AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
    }
    private static void setAlarm(Context c,long at,PendingIntent intent){
        AlarmManager am=alarmManager(c);
        if(am==null)return;
        try {
            if(Build.VERSION.SDK_INT>=31 && am.canScheduleExactAlarms()){
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,intent);
            }else if(Build.VERSION.SDK_INT<31){
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,intent);
            }else{
                // Works without special exact-alarm access, but is not clock-minute precise.
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,intent);
            }
        }catch(SecurityException denied){
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,intent);
        }
    }
    public static long nextTrigger(Context c,long now){
        SharedPreferences p=c.getSharedPreferences("options",0);
        return ReminderTimes.nextMillis(now,java.time.ZoneId.systemDefault(),
            p.getInt("hour",21),p.getInt("minute",0));
    }
    public static void schedule(Context c){
        SharedPreferences p=c.getSharedPreferences("options",0);
        AlarmManager am=alarmManager(c);
        if(am==null)return;
        PendingIntent pending=receiver(c,ALARM,SCHEDULE_REQUEST);
        am.cancel(pending);
        if(!p.getBoolean("enabled",false)){
            p.edit().remove("next_scheduled_at").apply();
            return;
        }
        long next=nextTrigger(c,System.currentTimeMillis());
        setAlarm(c,next,pending);
        p.edit().putLong("next_scheduled_at",next).apply();
        ensureChannel(c);
    }
    public static boolean hasNotificationPermission(Context c){
        NotificationManager nm=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
        if(nm==null || !nm.areNotificationsEnabled())return false;
        return Build.VERSION.SDK_INT<33 ||
           c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED;
    }
    public static boolean isChannelOpen(Context c){
        NotificationManager nm=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
        if(nm==null)return false;
        NotificationChannel channel=nm.getNotificationChannel(CHANNEL);
        return channel==null || channel.getImportance()!=NotificationManager.IMPORTANCE_NONE;
    }
    public static void ensureChannel(Context c){
        NotificationManager nm=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
        if(nm==null)return;
        NotificationChannel channel=new NotificationChannel(CHANNEL,
            "Diario CBT • promemoria giornalieri",NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("Promemoria visibili per la compilazione del diario");
        channel.enableVibration(true);
        channel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
        nm.createNotificationChannel(channel);
    }
    /** Immediate test from settings; never silently skipped because a diary was completed. */
    public static boolean sendTest(Context c) {
        ensureChannel(c);
        return show(c,true);
    }
    private static boolean hasCompletedToday(Context c){
        try{
            JSONObject root=SecureStore.read(c);
            JSONObject history=root.optJSONObject("history");
            String today=LocalDate.now().toString();
            return history!=null && history.has(today);
        }catch(Exception ignored){
            // Do not suppress a reminder merely because reading encrypted storage failed.
            return false;
        }
    }
    private static boolean show(Context c,boolean test){
        if(!hasNotificationPermission(c)||!isChannelOpen(c))return false;
        if(!test&&!c.getSharedPreferences("options",0).getBoolean("enabled",false))return false;
        if(!test&&hasCompletedToday(c))return false;
        NotificationManager nm=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
        if(nm==null)return false;
        String language=I18n.lang(c);
        String title=test?
            I18n.t(language,"Notifica CBT di prova","CBT test reminder","Recordatorio CBT de prueba"):
            I18n.t(language,"È il momento di ascoltarti","A moment for yourself","Un momento para ti");
        String message=I18n.t(language,"Dedica qualche minuto al tuo diario.",
            "Take a few minutes for your journal.","Dedica unos minutos a tu diario.");
        Intent startIntent=new Intent(c,MainActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent start=PendingIntent.getActivity(c,103,startIntent,
            PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Intent alertIntent=new Intent(c,AlertActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent full=PendingIntent.getActivity(c,104,alertIntent,
            PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder=new Notification.Builder(c,CHANNEL)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(message)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setPriority(Notification.PRIORITY_HIGH)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setContentIntent(start)
            .setAutoCancel(true)
            .setOngoing(false)
            .setWhen(System.currentTimeMillis())
            .setShowWhen(true)
            .addAction(new Notification.Action.Builder(null,
                I18n.t(language,"Inizia","Start","Empezar"),start).build())
            .addAction(new Notification.Action.Builder(null,
                I18n.t(language,"Tra 15 min","In 15 min","En 15 min"),
                receiver(c,SNOOZE,3)).build())
            .addAction(new Notification.Action.Builder(null,
                I18n.t(language,"Salta oggi","Skip today","Omitir hoy"),
                receiver(c,SKIP,4)).build());
        boolean wantFull=c.getSharedPreferences("options",0).getBoolean("fullscreen",false);
        if(wantFull&&(Build.VERSION.SDK_INT<34||nm.canUseFullScreenIntent())){
            builder.setFullScreenIntent(full,true);
        }
        nm.notify(REMINDER_ID,builder.build());
        return true;
    }
    @Override public void onReceive(Context c,Intent intent){
        if(intent==null)return;
        String action=intent.getAction();
        NotificationManager nm=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
        if(SKIP.equals(action)){
            if(nm!=null)nm.cancel(REMINDER_ID);
            return;
        }
        if(SNOOZE.equals(action)){
            if(nm!=null)nm.cancel(REMINDER_ID);
            setAlarm(c,System.currentTimeMillis()+15*60*1000L,
                receiver(c,ALARM,SNOOZE_REQUEST));
            return;
        }
        if(ALARM.equals(action)){
            // Rearm first: even if the notification is blocked today, tomorrow is retained.
            if(c.getSharedPreferences("options",0).getBoolean("enabled",false))schedule(c);
            show(c,false);
        }
    }
}
