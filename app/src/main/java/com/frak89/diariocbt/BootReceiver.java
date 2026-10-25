package com.frak89.diariocbt;
import android.app.AlarmManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Restore user-configured reminders after reboot, app upgrade and clock changes. */
public final class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent intent){
        if(intent==null)return;
        String action=intent.getAction();
        if(Intent.ACTION_BOOT_COMPLETED.equals(action)
          ||Intent.ACTION_TIME_CHANGED.equals(action)
          ||Intent.ACTION_TIMEZONE_CHANGED.equals(action)
          ||Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)
          ||AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED.equals(action)){
            ReminderReceiver.schedule(c);
        }
    }
}
