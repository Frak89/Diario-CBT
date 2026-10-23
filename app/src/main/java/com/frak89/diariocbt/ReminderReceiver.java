package com.frak89.diariocbt;
import android.app.*;
import android.content.*;
import android.os.Build;
import java.time.*;
public class ReminderReceiver extends BroadcastReceiver {
    static final String CHANNEL="cbt_daily_reminder";
    static final String ALARM="com.frak89.diariocbt.ALARM";
    static final String SNOOZE="com.frak89.diariocbt.SNOOZE";
    static final String SKIP="com.frak89.diariocbt.SKIP";
    private static PendingIntent receiver(Context c,String action,int code) {
        return PendingIntent.getBroadcast(c,code,new Intent(c,ReminderReceiver.class).setAction(action),
          PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    public static void schedule(Context c) {
        android.content.SharedPreferences p=c.getSharedPreferences("options",0);
        AlarmManager am=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pi=receiver(c,ALARM,100);
        am.cancel(pi);
        if(!p.getBoolean("enabled",false)) return;
        java.util.Calendar next=java.util.Calendar.getInstance();
        next.set(java.util.Calendar.HOUR_OF_DAY,p.getInt("hour",21));
        next.set(java.util.Calendar.MINUTE,p.getInt("minute",0));
        next.set(java.util.Calendar.SECOND,0);
        next.set(java.util.Calendar.MILLISECOND,0);
        if(next.getTimeInMillis()<=System.currentTimeMillis()) next.add(java.util.Calendar.DAY_OF_YEAR,1);
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP,next.getTimeInMillis(),
          AlarmManager.INTERVAL_DAY,pi);
    }
    @Override public void onReceive(Context context,Intent intent) {
        if(intent==null) return;
        String action=intent.getAction();
        if(SKIP.equals(action)){
            ((NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE)).cancel(42);
            return;
        }
        if(SNOOZE.equals(action)){
            ((NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE)).cancel(42);
            long at=System.currentTimeMillis()+15*60*1000L;
            ((AlarmManager)context.getSystemService(Context.ALARM_SERVICE)).setAndAllowWhileIdle(
                 AlarmManager.RTC_WAKEUP,at,receiver(context,ALARM,101));
            return;
        }
        if(!ALARM.equals(action)) return;
        if(!context.getSharedPreferences("options",0).getBoolean("enabled",false)) return;
        try {
            String today=LocalDate.now().toString();
            if(SecureStore.read(context).optJSONObject("history")!=null
              && SecureStore.read(context).optJSONObject("history").has(today)) return;
        } catch(Exception ignored){}
        NotificationManager nm=(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL,"Diario CBT — promemoria",NotificationManager.IMPORTANCE_HIGH));
        if(Build.VERSION.SDK_INT>=33 &&
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED) return;
        String lang=I18n.lang(context);
        String title=I18n.t(lang,"È il momento di ascoltarti","A moment for yourself","Un momento para ti");
        String desc=I18n.t(lang,"Dedica 5 minuti al tuo diario CBT.","Take 5 minutes for your CBT journal.","Dedica 5 minutos a tu diario CBT.");
        PendingIntent start=PendingIntent.getActivity(context,1,new Intent(context,MainActivity.class),
           PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        PendingIntent full=PendingIntent.getActivity(context,2,new Intent(context,AlertActivity.class),
           PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b=new Notification.Builder(context,CHANNEL)
           .setSmallIcon(android.R.drawable.ic_popup_reminder).setContentTitle(title).setContentText(desc)
           .setCategory(Notification.CATEGORY_REMINDER).setPriority(Notification.PRIORITY_HIGH)
           .setContentIntent(start).setAutoCancel(true)
           .addAction(new Notification.Action.Builder(null,
                I18n.t(lang,"Inizia","Start","Empezar"),start).build())
           .addAction(new Notification.Action.Builder(null,
                I18n.t(lang,"Tra 15 min","15 minutes","15 minutos"),receiver(context,SNOOZE,3)).build())
           .addAction(new Notification.Action.Builder(null,
                I18n.t(lang,"Salta oggi","Skip today","Omitir hoy"),receiver(context,SKIP,4)).build());
        boolean enabled=context.getSharedPreferences("options",0).getBoolean("fullscreen",false);
        if(enabled && (Build.VERSION.SDK_INT<34 || nm.canUseFullScreenIntent())) b.setFullScreenIntent(full,true);
        nm.notify(42,b.build());
    }
}
