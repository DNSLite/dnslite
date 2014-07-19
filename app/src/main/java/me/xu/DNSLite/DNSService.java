package me.xu.DNSLite;

import me.xu.tools.DNSProxyClient;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.NetworkInfo.State;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;

public class DNSService extends Service {
	private static String TAG = "DNSService";
	private final static int ctl_STOP = 1;
	private final static int ctl_START = 2;
	private final static int ctl_RE_SET_DNS = 3;

	private BroadcastReceiver mReceiver = new BroadcastReceiver() {

		public void job_on_connect_action() {
			ConnectivityManager connManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
			NetworkInfo info = connManager.getActiveNetworkInfo();
			if (info == null || !info.isAvailable()) {
				return;
			}
			State state = null;

			try {
				state = connManager.getNetworkInfo(
						ConnectivityManager.TYPE_MOBILE).getState();
				if (State.CONNECTED == state) {
					new DnsOp().execute(ctl_RE_SET_DNS);
				}
			} catch (Exception e) {}

			try {
				state = connManager.getNetworkInfo(
						ConnectivityManager.TYPE_WIFI).getState();
				if (State.CONNECTED == state) {
					new DnsOp().execute(ctl_RE_SET_DNS);
				}
			} catch (Exception e) {}
		}

        @Override
        public void onReceive(Context ctx, Intent intent) {
            if (intent == null) {
                return;
            }
            String action = intent.getAction();
			if (ConnectivityManager.CONNECTIVITY_ACTION.equals(action)) {
				this.job_on_connect_action();
			}
		}

	};

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
		super.onCreate();
		registerReceiver(mReceiver, new IntentFilter(
				ConnectivityManager.CONNECTIVITY_ACTION));
		new DnsOp().execute(ctl_START);
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
		try {
			unregisterReceiver(mReceiver);
		} catch (Exception ee) {
		}
		new DnsOp().execute(ctl_STOP);
		super.onDestroy();
	}

	private class DnsOp extends AsyncTask<Integer, Void, Integer> {
		protected Integer doInBackground(Integer... cmd) {
			switch (cmd[0]) {
			case ctl_STOP: {
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
			}
			case ctl_START: {

				if (DNSProxyClient.isDnsRuning()) {
					return R.string.dns_running;
				} else {
					DNSProxy dnsproxy = new DNSProxy(getApplicationContext());
					dnsproxy.startDNSService();
					return R.string.dns_start_succ;
				}
			}
			case ctl_RE_SET_DNS: {
				return DNSProxyClient.re_set_dns() ? 0 : -1;
			}
			default:
				break;
			}
			return -1;
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
