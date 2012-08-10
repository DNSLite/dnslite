package me.xu.DNSLite;

import android.support.v4.app.NotificationCompat;
import me.xu.tools.DNSProxyClient;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;

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
	public void onCreate() {
		new DnsOp().execute(false);
		super.onCreate();
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
	
	@Override
	public void onDestroy() {
		new DnsOp().execute(true);
		super.onDestroy();
	}

	private class DnsOp extends AsyncTask<Boolean, String, Integer> {
		protected Integer doInBackground(Boolean... stop) {
			if (stop[0] == true) {
				int t = 5;
				do {
					if (DNSProxyClient.quit()) {
						return R.string.dns_stop_succ;
					}
					if (DNSProxyClient.isDnsRuning()) {
						--t;
					} else {
						return R.string.dns_stop_succ;
					}
					try {
						Thread.sleep(1000);
					} catch (InterruptedException e) {
					}
				} while (t > 0);
				return R.string.dns_stop_fail;
			} else {
				if (DNSProxyClient.isDnsRuning()) {
					return R.string.dns_running;
				} else {					
					DNSProxy dnsproxy = new DNSProxy(getApplicationContext());
					dnsproxy.startDNSService();
					return R.string.dns_start_succ;
				}
			}
		}
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
