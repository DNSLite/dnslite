package me.xu.DNSLite;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

import me.xu.tools.DNSProxyClient;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.NetworkInfo.State;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import me.xu.tools.Sudo;

public class DNSServiceActivity extends FragmentActivity {

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		FragmentManager fm = getSupportFragmentManager();
		if (fm.findFragmentById(android.R.id.content) == null) {
			DNSServiceFragment dnsfrag = new DNSServiceFragment();
			fm.beginTransaction().add(android.R.id.content, dnsfrag).commit();
		}
	}

	public static class DNSServiceFragment extends Fragment implements
			OnClickListener {

		private boolean dnsliteRunning = false;
		private Button dnsStart = null;
		private Button dnsStop = null;
		private TextView wifi_ip = null;
		private TextView cur_dns = null;
		private ProgressDialog progressDialog = null;
		private boolean isReceiverRegistered = false;

		private DNSService mBoundService;
		private boolean mIsBound = false;
		private ServiceConnection mConnection = new ServiceConnection() {
			public void onServiceConnected(ComponentName className,
					IBinder service) {
				mBoundService = ((DNSService.DNSBinder) service).getService();
				doUnbindService();
				new DnsOp().execute(false);
			}

			public void onServiceDisconnected(ComponentName className) {
				mBoundService = null;
				mIsBound = false;
				if (progressDialog != null) {
					progressDialog.dismiss();
					progressDialog = null;
				}
			}

		};

		void doBindService() {
			getActivity().getApplicationContext().bindService(
					new Intent(getActivity().getApplicationContext(),
							DNSService.class), mConnection,
					Context.BIND_AUTO_CREATE);
			mIsBound = true;
		}

		void doUnbindService() {
			if (mIsBound) {
				try {
					getActivity().getApplicationContext().unbindService(
							mConnection);
				} catch (Exception e) {
				}
				mIsBound = false;
				mBoundService = null;
			}
		}

		@Override
		public View onCreateView(LayoutInflater inflater, ViewGroup container,
				Bundle savedInstanceState) {
			View view = inflater.inflate(R.layout.dns_server, container, false);
			Button dnsConfig = (Button) view.findViewById(R.id.dnsConfig);
			dnsStart = (Button) view.findViewById(R.id.dnsStart);
			dnsStop = (Button) view.findViewById(R.id.dnsStop);
			Button dnsCacheConfig = (Button) view
					.findViewById(R.id.dnsCacheConfig);
			Button dnsLog = (Button) view.findViewById(R.id.dnsLog);
			wifi_ip = (TextView) view.findViewById(R.id.wifi_ip);
            cur_dns = (TextView) view.findViewById(R.id.cur_dns);

			dnsConfig.setOnClickListener(this);
			dnsStart.setOnClickListener(this);
			dnsStop.setOnClickListener(this);
			dnsCacheConfig.setOnClickListener(this);
			dnsLog.setOnClickListener(this);
			return view;
			// return super.onCreateView(inflater, container,
			// savedInstanceState);
		}

		private String intToIp(int i) {
			return (i & 0xFF) + "." + ((i >> 8) & 0xFF) + "."
					+ ((i >> 16) & 0xFF) + "." + ((i >> 24) & 0xFF);
		}

		public String getLocalIPAddress() {
			try {
				for (Enumeration<NetworkInterface> mEnumeration = NetworkInterface
						.getNetworkInterfaces(); mEnumeration.hasMoreElements();) {

					NetworkInterface intf = mEnumeration.nextElement();
					for (Enumeration<InetAddress> enumIPAddr = intf
							.getInetAddresses(); enumIPAddr.hasMoreElements();) {
						InetAddress inetAddress = enumIPAddr.nextElement();
						// 如果不是回环地址
						if (!inetAddress.isLoopbackAddress()) {
							// 直接返回本地IP地址
							return inetAddress.getHostAddress().toString();
						}
					}
				}

			} catch (SocketException e) {
				e.printStackTrace();
			}
			return null;
		}

		@Override
		public void onResume() {
			super.onResume();
			new StatusCheck().execute();

			getActivity().registerReceiver(mReceiver,
					new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
			isReceiverRegistered = true;
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
				if (action.equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
					checkNetStatus();
				}
			}

		};

        public void updateCurrentDNSView() {
            final String[] netdns = Sudo.getProperties(new String[]{"net.dns1",
                    "net.dns2"});
            if (netdns != null) {
                cur_dns.setText("DNS1: " + netdns[0] + "\nDNS2: "+netdns[1]);
            }
        }

		private boolean checkNetStatus() {

			ConnectivityManager connManager = (ConnectivityManager) getActivity()
					.getSystemService(CONNECTIVITY_SERVICE);
			NetworkInfo info = connManager.getActiveNetworkInfo();
			if (info == null || !info.isAvailable()) {
				if (wifi_ip != null) {
					wifi_ip.setText(getString(R.string.wifi_is_disable));
				}
				return false;
			}
			String name = info.getTypeName();
            State state = null;
            try {
                state = connManager.getNetworkInfo(
                        ConnectivityManager.TYPE_MOBILE).getState();
                if (State.CONNECTED == state) {
                    if (wifi_ip != null) {
                        wifi_ip.setText(name + " IP:" + getLocalIPAddress());
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

			try {
				state = connManager.getNetworkInfo(
						ConnectivityManager.TYPE_WIFI).getState();
				if (State.CONNECTED == state) {
					WifiManager wifimanage = (WifiManager) getActivity()
							.getSystemService(Context.WIFI_SERVICE);
					WifiInfo wifiinfo = wifimanage.getConnectionInfo();
					if (wifiinfo.getIpAddress() == 0) {
						if (wifi_ip != null) {
							wifi_ip.setText(getString(R.string.wifi_is_disable));
						}
					} else {
						if (wifi_ip != null) {
							wifi_ip.setText(name + " IP: "
									+ intToIp(wifiinfo.getIpAddress()));
						}
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
            new ResetDns().execute();
            new StatusCheck().execute(100, 1500);
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

				HostsDB hdb = HostsDB.GetInstance(getActivity()
						.getApplicationContext());
				if (HostsDB.needRewriteDnsCache) {
					progressDialog.setMessage("load DNS cache ...");
					hdb.writeDnsCacheFile();
				}
				progressDialog.setMessage(getString(R.string.dns_start));
				doUnbindService();
				getActivity().stopService(
						new Intent(getActivity().getApplicationContext(),
								DNSService.class));
				getActivity().startService(
						new Intent(getActivity().getApplicationContext(),
								DNSService.class));
				doBindService();
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
			doUnbindService();
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
            updateCurrentDNSView();
		}

		private void startProgressDialog(String title, String message) {
			if (progressDialog == null) {
				progressDialog = new ProgressDialog(getActivity());
			}
			progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
			// progressDialog.setTitle(title);
			progressDialog.setMessage(message);
			progressDialog.setCancelable(true);
			progressDialog.show();
		}

		private class StatusCheck extends AsyncTask<Integer, Boolean, Void> {
			protected Void doInBackground(Integer... delay) {
                int count = delay.length;
                if (count < 1) {
                    publishProgress(DNSProxyClient.isDnsRuning());
                    return null;
                }
                for (int i=0; i<count; i++) {
                    try {
                        Thread.sleep(delay[i]);
                    } catch (InterruptedException e) {
                    }
                    publishProgress(DNSProxyClient.isDnsRuning());
                }
                return null;
			}

            @Override
            protected void onProgressUpdate(Boolean... values) {
                dnsliteRunning = values[0];
                fixButton();
                super.onProgressUpdate(values);
            }
		}

        private class ResetDns extends AsyncTask<Void, Void, Boolean> {
            protected Boolean doInBackground(Void... cmd) {
                return DNSProxyClient.re_set_dns();
            }
            protected void onPostExecute(Boolean res) {
                fixButton();
            }
        }

		private class DnsOp extends AsyncTask<Boolean, String, Integer> {
			protected Integer doInBackground(Boolean... stop) {
				if (stop[0] == true) {
					publishProgress("send quit to DNS Server ...");
					doUnbindService();
					getActivity().stopService(
							new Intent(getActivity().getApplicationContext(),
									DNSService.class));
					if (DNSProxyClient.quit()) {
						return R.string.dns_stop_succ;
					}

					int t = 5;
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
					}

					int t = 5;
					do {
						publishProgress("wait DNS Server ... " + t);
						if (DNSProxyClient.isDnsRuning()) {
							return R.string.dns_start_succ;
						}
						--t;
						try {
							Thread.sleep(1000);
						} catch (InterruptedException e) {
						}
					} while (t > 0);
					return R.string.dns_start_fail;
				}
			}

			@Override
			protected void onCancelled() {
				progressDialog.dismiss();
				super.onCancelled();
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
					Toast.makeText(getActivity().getApplicationContext(),
							getString(result), Toast.LENGTH_SHORT).show();
					break;
				case R.string.dns_start_fail:
				case R.string.dns_stop_fail:
					Toast.makeText(getActivity().getApplicationContext(),
							getString(result), Toast.LENGTH_LONG).show();
					break;
				}
			}
		}
	}

}
