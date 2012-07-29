package me.xu.DNSLite;

import me.xu.tools.DNSProxyClient;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;

public class DNSServiceActivity extends FragmentActivity {

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		FragmentManager fm = getSupportFragmentManager();
		if (fm.findFragmentById(android.R.id.content) == null) {
			DNSServiceFragment dnsfrag = new DNSServiceFragment();
            fm.beginTransaction().add(android.R.id.content, dnsfrag).commit();
        }
	}

	public static class DNSServiceFragment extends Fragment implements OnClickListener {

		private boolean dnsliteRunning = false;
		private Button dnsStart = null;
		private Button dnsStop = null;
		private TextView wifi_ip = null;
		private ProgressDialog progressDialog = null;
		private WifiManager wifimanage = null;
		private boolean isReceiverRegistered = false;

		@Override
		public View onCreateView(LayoutInflater inflater, ViewGroup container,
				Bundle savedInstanceState) {
			View view = inflater.inflate(R.layout.dns_server, container, false);
			Button dnsConfig = (Button) view.findViewById(R.id.dnsConfig);
			dnsStart = (Button) view.findViewById(R.id.dnsStart);
			dnsStop = (Button) view.findViewById(R.id.dnsStop);
			Button dnsCacheConfig = (Button) view.findViewById(R.id.dnsCacheConfig);
			Button dnsLog = (Button) view.findViewById(R.id.dnsLog);
			wifi_ip = (TextView) view.findViewById(R.id.wifi_ip);

			dnsConfig.setOnClickListener(this);
			dnsStart.setOnClickListener(this);
			dnsStop.setOnClickListener(this);
			dnsCacheConfig.setOnClickListener(this);
			dnsLog.setOnClickListener(this);
			return view;
			//return super.onCreateView(inflater, container, savedInstanceState);
		}
		
		private String intToIp(int i) {
			return (i & 0xFF) + "." + ((i >> 8) & 0xFF) + "."
					+ ((i >> 16) & 0xFF) + "." + ((i >> 24) & 0xFF);
		}

		@Override
		public void onResume() {
			super.onResume();
			new StatusCheck().execute();

			getActivity().registerReceiver(mReceiver,
					new IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION));
			isReceiverRegistered = true;
			Thread.yield();
		}

		@Override
		public void onPause() {
			if (isReceiverRegistered) {
				try {
					getActivity().unregisterReceiver(mReceiver);
				} catch (Exception ee) {
				}
			}
			isReceiverRegistered = false;
			super.onPause();
		}

		private BroadcastReceiver mReceiver = new BroadcastReceiver() {

			@Override
			public void onReceive(Context arg0, Intent arg1) {
				final String action = arg1.getAction();
				if (action.equals(WifiManager.WIFI_STATE_CHANGED_ACTION)) {
					checkWifiStatus();
				}
			}

		};

		public boolean checkWifiStatus() {
			if (wifimanage == null) {
				wifimanage = (WifiManager) getActivity().getSystemService(
						Context.WIFI_SERVICE);
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
					wifi_ip.setText("Wifi IP: "
							+ intToIp(wifiinfo.getIpAddress()));
				}
			}
			return true;
		}

		@Override
		public void onClick(View v) {
			switch (v.getId()) {
			case R.id.dnsConfig:
				startActivity(new Intent(getActivity().getApplicationContext(),
						DnsPreferences.class));
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
				startActivity(new Intent(getActivity().getApplicationContext(),
						DnsGroupList.class));
				break;
			case R.id.dnsLog:
				startActivity(new Intent(getActivity().getApplicationContext(),
						DnsLogsActivity.class));
				break;
			default:
				break;
			}
		}

		@Override
		public void onDestroy() {
			if (progressDialog != null) {
				progressDialog.dismiss();
				progressDialog = null;
			}
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
				progressDialog = new ProgressDialog(getActivity());
			}
			progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
			//progressDialog.setTitle(title);
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
					if (DNSProxyClient.quit()) {
						return R.string.dns_stop_succ;
					}

					int t = 5;
					do {
						publishProgress("wait DNS Server ... " + t);
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
						publishProgress("wait DNS Server " + t);
						if (DNSProxyClient.isDnsRuning()) {
							--t;
							if (DNSProxyClient.quit()) {
								return R.string.dns_stop_succ;
							}
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
					publishProgress("check DNS Server ...");
					if (DNSProxyClient.isDnsRuning()) {
						return R.string.dns_running;
					} else {

						HostsDB hdb = HostsDB
								.GetInstance(getActivity().getApplicationContext());
						if (HostsDB.needRewriteDnsCache) {
							publishProgress("load DNS cache ...");
							hdb.writeDnsCacheFile();
						}

						publishProgress(getString(R.string.dns_start));
						DNSProxy dnsproxy = new DNSProxy(
								getActivity().getApplicationContext());
						dnsproxy.startDNSService();
						Thread.yield();
					}

					int t = 5;
					do {
						publishProgress("check DNS Server ... " + t);
						if (!DNSProxyClient.isDnsRuning()) {
							--t;
						} else {
							return R.string.dns_start_succ;
						}
						try {
							Thread.sleep(1000);
						} catch (InterruptedException e) {
						}
					} while (t > 0);
					return R.string.dns_start_fail;
				}
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
					Toast.makeText(getActivity().getApplicationContext(), getString(result),
							Toast.LENGTH_SHORT).show();
					break;
				case R.string.dns_start_fail:
				case R.string.dns_stop_fail:
					Toast.makeText(getActivity().getApplicationContext(), getString(result),
							Toast.LENGTH_LONG).show();
					break;
				}
			}
		}
	}

}