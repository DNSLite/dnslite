package me.xu.DNSLite;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;

public class DNSService extends Service {

	private final DNSBinder mBinder = new DNSBinder();

	@Override
	public IBinder onBind(Intent arg0) {
		return (IBinder) mBinder;
	}

	public class DNSBinder extends Binder {
		DNSService getService() {
			return DNSService.this;
		}
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		if (intent != null) {
			try {
				Bundle bundle = intent.getExtras();
				if (bundle != null) {
					String _idle_exit = bundle.getString("_idle_exit");
					if (_idle_exit != null && _idle_exit.equals("1")) {
						showNotification();
						stopSelf();
					}
				}
			} catch (Exception e) {
			}
		}
		return START_STICKY;
	}

	private void showNotification() {

		PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
				new Intent(this, DNSLiteActivity.class), 0);

		NotificationManager mNM = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

		Notification notif = new NotificationCompat.Builder(this)
				.setContentIntent(contentIntent)
				.setSmallIcon(R.drawable.ic_launcher)
				.setLargeIcon(
						BitmapFactory.decodeResource(this.getResources(),
								R.drawable.ic_launcher)).setAutoCancel(true)
				.setContentTitle(getText(R.string.app_name))
				.setContentText(getText(R.string.alarm_service_stoped))
				.getNotification();

		mNM.notify(R.string.alarm_service_stoped, notif);
	}

}
