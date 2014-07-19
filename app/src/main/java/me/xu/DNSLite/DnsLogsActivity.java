package me.xu.DNSLite;

import java.io.IOException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;

import me.xu.tools.DNSProxyClient;

import android.R.color;
import android.app.ListActivity;
import android.content.Context;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;

import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.ads.*;

public class DnsLogsActivity extends ListActivity {

	private DNSProxyClient dnsc = new DNSProxyClient();
	private LogAdapter mAdapter;
	private DNSProxyLog asyncLog = null;
	private ArrayList<String> mStrings = new ArrayList<String>();

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.dns_logs);

        AdView adView = (AdView)this.findViewById(R.id.adView);
        AdRequest adRequest = new AdRequest.Builder().build();
        adView.loadAd(adRequest);

		mAdapter = new LogAdapter(this, R.layout.dns_logs_row, mStrings);
		setListAdapter(mAdapter);
		asyncLog = new DNSProxyLog();
		asyncLog.execute();
	}

	@Override
	protected void onDestroy() {
		if (asyncLog != null) {
			asyncLog.cancel(true);
		}
		dnsc.close();
		try {
			Thread.sleep(500);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		super.onDestroy();
	}

	public class LogAdapter extends ArrayAdapter<String> {
		private int textViewResourceId;

		public LogAdapter(Context context, int textViewResourceId,
				ArrayList<String> mStrings) {
			super(context, textViewResourceId, mStrings);
			this.textViewResourceId = textViewResourceId;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			TextView tv;
			if (convertView == null) {
				tv = (TextView) getLayoutInflater().inflate(textViewResourceId,
						parent, false);
			} else {
				tv = (TextView) convertView;
			}
			if ((position % 2) == 0) {
				tv.setBackgroundColor(color.background_light);
			} else {
				tv.setBackgroundColor(0x300000FF);
			}

			String log = getItem(position);
			if (log.length() > 2 && log.charAt(1) == ':') {
				switch (log.charAt(0)) {
				case 'A':
					tv.setTextColor(Color.DKGRAY);
					break;
				case 'C':
					tv.setTextColor(Color.MAGENTA);
					break;
				case 'S':
					tv.setTextColor(Color.BLUE);
					break;
				case 'O':
					tv.setTextColor(Color.RED);
					break;
				default:
					tv.setTextColor(Color.BLACK);
					break;
				}
			} else {
				tv.setTextColor(Color.GRAY);
			}

			tv.setText(log);

			if (getCount() > 100) {
				this.remove(getItem(0));
				this.notifyDataSetChanged();
			}
			return tv;
		}

	}

	private class DNSProxyLog extends AsyncTask<Void, String, String> {
		protected String doInBackground(Void... v) {

			publishProgress(getString(R.string.dns_checking));
			if (!dnsc.connect()) {
				return getString(R.string.dns_check_connect_fail);
			}

			dnsc.setSoTimeout(200);

			if (!dnsc.preGetLog()) {
				return getString(R.string.dns_preGetLog_fail);
			}
			publishProgress(getString(R.string.dns_check_ok));
			String rv;
			while (true) {
				if (isCancelled()) {
					return null;
				}
				try {
					rv = dnsc.getLog();
					if (rv != null) {
						publishProgress(rv);
					}
				} catch (SocketException se) {
					return se.getMessage();
				} catch (SocketTimeoutException eout) {
				} catch (IOException e) {
					e.printStackTrace();
					return e.getMessage();
				}
			}
		}

		protected void onProgressUpdate(String... logs) {
			mAdapter.add(logs[0]);
		}

		protected void onPostExecute(String result) {
			if (result != null) {
				mAdapter.add(result);

				Toast.makeText(DnsLogsActivity.this, result, Toast.LENGTH_LONG)
						.show();
			}
		}
	}

}
