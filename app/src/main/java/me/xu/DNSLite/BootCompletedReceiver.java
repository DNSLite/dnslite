package me.xu.DNSLite;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

public class BootCompletedReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED)) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            boolean auto_start = prefs.getBoolean(
                    DnsPreferences.KEY_AUTO_START_ON_BOOTCOMPLETED, false);
            if (auto_start) {
                Intent newIntent = new Intent(context, DNSService.class);
                newIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startService(newIntent);
            }
        }
    }
}
