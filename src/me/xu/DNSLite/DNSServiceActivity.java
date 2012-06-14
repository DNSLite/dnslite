package me.xu.DNSLite;

import me.xu.tools.DNSProxyClient;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
//import android.content.ComponentName;
//import android.content.ServiceConnection;
//import android.os.IBinder;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class DNSServiceActivity extends Activity implements OnClickListener {

	private boolean dnsliteRunning = false;
	private Button dnsStart = null;
	private Button dnsStop = null;
	private TextView wifi_ip = null;
	private ProgressDialog progressDialog = null;
	private WifiManager wifimanage = null;
	private boolean isReceiverRegistered = false;

//	private DNSService mBoundService;
//	private boolean mIsBound = false;
//	private ServiceConnection mConnection = new ServiceConnection() {
//		public void onServiceConnected(ComponentName className, IBinder service) {
//			mBoundService = ((DNSService.DNSBinder) service).getService();
//			if (mBoundService.isStartSucc()) {
//				doUnbindService();
//			} else {
//				Toast.makeText(DNSServiceActivity.this,
//						mBoundService.getStatusStr(), Toast.LENGTH_LONG).show();
//				if (progressDialog != null) {
//					progressDialog.dismiss();
//					progressDialog = null;
//				}
//			}
//		}
//
//		public void onServiceDisconnected(ComponentName className) {
//			mBoundService = null;
//			mIsBound = false;
//			if (progressDialog != null) {
//				progressDialog.dismiss();
//				progressDialog = null;
//			}
//		}
//
//	};
//
//	void doBindService() {
//		this.getApplicationContext().bindService(
//				new Intent(DNSServiceActivity.this, DNSService.class),
//				mConnection, Context.BIND_AUTO_CREATE);
//		mIsBound = true;
//	}
//
//	void doUnbindService() {
//		if (mIsBound) {
//			try {
//				this.getApplicationContext().unbindService(mConnection);
//			} catch (Exception e) {
//			}
//			mIsBound = false;
//			mBoundService = null;
//		}
//	}

	private String intToIp(int i) {
		return (i & 0xFF) + "." + ((i >> 8) & 0xFF) + "." + ((i >> 16) & 0xFF)
				+ "." + ((i >> 24) & 0xFF);
	}

	@Override
	protected void onResume() {
		super.onResume();
		new StatusCheck().execute();

		registerReceiver(mReceiver, new IntentFilter(
				WifiManager.WIFI_STATE_CHANGED_ACTION));
		isReceiverRegistered = true;
		Thread.yield();
	}

	@Override
	protected void onPause() {
		if (isReceiverRegistered) {
			try {
				unregisterReceiver(mReceiver);
			} catch (Exception ee) {
			}
		}
		isReceiverRegistered = false;
		super.onPause();
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
			finish();
			System.exit(0);
		}
		return super.onKeyDown(keyCode, event);
	}

	private BroadcastReceiver mReceiver = new BroadcastReceiver() {

		@Override
		public void onReceive(Context arg0, Intent arg1) {
			final String action = arg1.getAction();
			if (action.equals(WifiManager.WIFI_STATE_CHANGED_ACTION)) {
				DNSServiceActivity.this.checkWifiStatus();
			}
		}

	};

	public boolean checkWifiStatus() {
		if (wifimanage == null) {
			wifimanage = (WifiManager) this
					.getSystemService(Context.WIFI_SERVICE);
		}
		if (!wifimanage.isWifiEnabled()) {
			if (wifi_ip != null) {
				wifi_ip.setText(getString(R.string.wifi_is_disable));
			}
			return false;
		}

		WifiInfo wifiinfo = wifimanage.getConnectionInfo();
		if (wifiinfo.getIpAddress() == 0) {
			if (wifi_ip != null) {
				wifi_ip.setText(getString(R.string.wifi_is_disable));
			}
		} else {
			if (wifi_ip != null) {
				wifi_ip.setText("Wifi IP: " + intToIp(wifiinfo.getIpAddress()));
			}
		}
		return true;
	}

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.dns_server);

		Button dnsConfig = (Button) findViewById(R.id.dnsConfig);
		dnsStart = (Button) findViewById(R.id.dnsStart);
		dnsStop = (Button) findViewById(R.id.dnsStop);
		Button dnsCacheConfig = (Button) findViewById(R.id.dnsCacheConfig);
		Button dnsLog = (Button) findViewById(R.id.dnsLog);
		wifi_ip = (TextView) findViewById(R.id.wifi_ip);

		dnsConfig.setOnClickListener(this);
		dnsStart.setOnClickListener(this);
		dnsStop.setOnClickListener(this);
		dnsCacheConfig.setOnClickListener(this);
		dnsLog.setOnClickListener(this);
	}

	@Override
	public void onClick(View v) {
		switch (v.getId()) {
		case R.id.dnsConfig:
			startActivity(new Intent(DNSServiceActivity.this, DnsPreferences.class));
			break;
		case R.id.dnsStart:
			startProgressDialog(getString(R.string.dns_start),
					getString(R.string.dns_start));
			new DnsOp().execute(false);
			Thread.yield();
			break;
		case R.id.dnsStop:
			startProgressDialog(getString(R.string.dns_stop),
					getString(R.string.dns_stop));
			new DnsOp().execute(true);
			break;
		case R.id.dnsCacheConfig:
			startActivity(new Intent(DNSServiceActivity.this, DnsGroupList.class));
			break;
		case R.id.dnsLog:
			startActivity(new Intent(DNSServiceActivity.this, DnsLogsActivity.class));
			break;
		default:
			break;
		}
	}

	@Override
	protected void onDestroy() {
		if (progressDialog != null) {
			progressDialog.dismiss();
			progressDialog = null;
		}
		//doUnbindService();
		super.onDestroy();
	}
	
	private void fixButton() {
		if (dnsliteRunning) {
			if (dnsStart != null) {
				dnsStart.setVisibility(View.GONE);
			}
			if (dnsStop != null) {
				dnsStop.setVisibility(View.VISIBLE);
			}
		} else {
			if (dnsStart != null) {
				dnsStart.setVisibility(View.VISIBLE);
			}
			if (dnsStop != null) {
				dnsStop.setVisibility(View.GONE);
			}
		}
		checkWifiStatus();
	}

	private void startProgressDialog(String title, String message) {
		if (progressDialog == null) {
			progressDialog = new ProgressDialog(DNSServiceActivity.this);
		}
		progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
		progressDialog.setTitle(title);
		progressDialog.setIndeterminate(true);
		progressDialog.setMessage(message);
		progressDialog.setCancelable(true);
		progressDialog.show();
	}
	


	private class StatusCheck extends AsyncTask<Void, Void, Boolean> {
		protected Boolean doInBackground(Void... v) {
			return DNSProxyClient.isDnsRuning();
		}

		protected void onPostExecute(Boolean result) {
			dnsliteRunning = result;
			fixButton();
		}
	}
	
	private class DnsOp extends AsyncTask<Boolean, String, Integer> {
		protected Integer doInBackground(Boolean... stop) {
			if (stop[0] == true) {
				publishProgress("send quit to DNS Server ...");
				stopService(new Intent(DNSServiceActivity.this, DNSService.class));
				Thread.yield();
				try {
					Thread.sleep(200);
				} catch (InterruptedException e) {
				}
				
				int t = 5;
				do {
					publishProgress("wait DNS Server ..."+t);
					if (DNSProxyClient.isDnsRuning()) {
						--t;
					} else {
						return R.string.dns_stop_succ;
					}
					try {
						Thread.sleep(400);
					} catch (InterruptedException e) {
					}
				} while (t > 0);
				t = 5;
				do {
					publishProgress("wait DNS Server "+t);
					if (DNSProxyClient.isDnsRuning()) {
						--t;
						if (DNSProxyClient.quit()) {
							return R.string.dns_stop_succ;
						}
					} else {
						return R.string.dns_stop_succ;
					}
					try {
						Thread.sleep(400);
					} catch (InterruptedException e) {
					}
				} while (t > 0);
				return R.string.dns_stop_fail;
			} else {
				publishProgress("check DNS Server ...");
				if (DNSProxyClient.isDnsRuning()) {
					return R.string.dns_running;
				} else {

					HostsDB hdb = HostsDB.GetInstance(getApplicationContext());
					if (HostsDB.needRewriteDnsCache) {
						publishProgress("load DNS cache ...");
						hdb.writeDnsCacheFile();
					}
					stopService(new Intent(DNSServiceActivity.this, DNSService.class));
					Thread.yield();
					try {
						Thread.sleep(100);
					} catch (InterruptedException e) {
					}
					publishProgress(getString(R.string.dns_start));
					startService(new Intent(DNSServiceActivity.this, DNSService.class));
					Thread.yield();
					try {
						Thread.sleep(300);
					} catch (InterruptedException e) {
					}
				}
				
				int t = 5;
				do {
					publishProgress("check DNS Server ..."+t);
					if (!DNSProxyClient.isDnsRuning()) {
						--t;
					} else {
						return R.string.dns_start_succ;
					}
					try {
						Thread.sleep(400);
					} catch (InterruptedException e) {
					}
				} while (t > 0);
				return R.string.dns_start_fail;
			}
			// return "unknow ops";
		}

		@Override
		protected void onCancelled(Integer result) {
			progressDialog.dismiss();
			super.onCancelled(result);
		}

		@Override
		protected void onProgressUpdate(String... values) {
			progressDialog.setMessage(values[0]);
			super.onProgressUpdate(values);
		}

		protected void onPostExecute(Integer result) {
			progressDialog.dismiss();
			switch (result) {
			case R.string.dns_running:
				dnsliteRunning = true;
				break;
			case R.string.dns_stop_succ:
				dnsliteRunning = false;
				break;
			case R.string.dns_stop_fail:
				dnsliteRunning = true;
				break;
			case R.string.dns_start_succ:
				dnsliteRunning = true;
				break;
			case R.string.dns_start_fail:
				dnsliteRunning = false;
				break;
			}

			fixButton();

			switch (result) {
			case R.string.dns_stop_succ:
			case R.string.dns_start_succ:
			case R.string.dns_running:
				Toast.makeText(DNSServiceActivity.this, getString(result),
						Toast.LENGTH_SHORT).show();
				break;
			case R.string.dns_start_fail:
			case R.string.dns_stop_fail:
				Toast.makeText(DNSServiceActivity.this, getString(result),
						Toast.LENGTH_LONG).show();
				break;
			}
		}
	}
}