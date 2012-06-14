package me.xu.DNSLite;

import me.xu.tools.Sudo;
import me.xu.tools.util;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.app.ActivityGroup;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.TabHost;
import android.widget.TextView;
import android.widget.Toast;

public class DNSLiteActivity extends ActivityGroup {
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.tabs);

		TabHost tabHost = (TabHost) findViewById(R.id.tabs_tabHost01);
		tabHost.setup(getLocalActivityManager());
		tabHost.addTab(tabHost.newTabSpec("DNS")
				.setContent(new Intent(this, DNSServiceActivity.class))
				.setIndicator(getString(R.string.dns_lable), null));
		tabHost.addTab(tabHost.newTabSpec("hosts")
				.setContent(new Intent(this, HostsActivity.class))
				.setIndicator("hosts", null));
		int count = tabHost.getTabWidget().getChildCount();
		for (int i = 0; i < count; ++i) {
			View view = tabHost.getTabWidget().getChildAt(i);
			View iv = view.findViewById(android.R.id.icon);
			iv.setVisibility(View.GONE);
			view.getLayoutParams().height = 55;
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.tab_option, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.menu_about:
			show_about();
			break;
		case R.id.menu_fix_netdns:
			fix_netdns();
			break;
		case R.id.menu_feedback:
			sendFeedback(
					"\"DNSLite\"<xudejian2008@gmail.com>",
					getString(R.string.menu_feedback),
					getString(R.string.menu_feedback) + " : "
							+ this.getPackageName(), "");
			break;
		default:
			return super.onOptionsItemSelected(item);
		}
		return true;
	}

	private void show_about() {
		new AlertDialog.Builder(this).setIcon(R.drawable.ic_launcher)
				.setTitle(R.string.about_title)
				.setMessage(R.string.about_message)
				.setPositiveButton(android.R.string.ok, null).create().show();
	}

	private void fix_netdns() {

		View fix_netdns = LayoutInflater.from(this).inflate(
				R.layout.fix_netdns, null);
		final EditText netdns1 = (EditText) fix_netdns
				.findViewById(R.id.netdns1);
		final EditText netdns2 = (EditText) fix_netdns
				.findViewById(R.id.netdns2);

		SharedPreferences sharedPref = PreferenceManager
				.getDefaultSharedPreferences(this);
		String configDNS = sharedPref.getString(DnsPreferences.KEY_NET_DNS,
				null);
		if (configDNS != null) {
			configDNS = configDNS.replaceAll(" ", ",");
			String[] dns = configDNS.split(",");
			int i = 0;
			for (String d : dns) {
				d = d.trim();
				if (d.length() < 1) {
					continue;
				}
				if (!util.isInetAddress(d)) {
					continue;
				}
				if (i == 0) {
					netdns1.setText(d);
					i++;
				} else if (i == 1) {
					netdns2.setText(d);
					break;
				} else {
					break;
				}
			}
		}
		final String[] netdns = Sudo.getProperties(new String[] { "net.dns1",
				"net.dns2" });
		if (netdns != null) {
			TextView tvdns1 = (TextView) fix_netdns.findViewById(R.id.tvdns1);
			tvdns1.setText(tvdns1.getText() + " " + getString(R.string.cur)
					+ " : " + netdns[0]);
			TextView tvdns2 = (TextView) fix_netdns.findViewById(R.id.tvdns2);
			tvdns2.setText(tvdns2.getText() + " " + getString(R.string.cur)
					+ " : " + netdns[1]);
		}

		new AlertDialog.Builder(this)
				.setTitle(R.string.fix_netdns_entry)
				.setView(fix_netdns)
				.setPositiveButton(android.R.string.ok,
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog,
									int whichButton) {
								String dns1 = netdns1.getText().toString()
										.trim();
								String dns2 = netdns2.getText().toString()
										.trim();
								String[] names = null;
								String[] vals = null;
								if (dns2.length() < 1
										&& netdns[1].trim().length() < 1) {
									names = new String[] { "net.dns1" };
									vals = new String[] { "" };
								} else {
									names = new String[] { "net.dns1",
											"net.dns2" };
									vals = new String[] { "", "" };

									if (dns2.length() > 0
											&& util.isInetAddress(dns2)) {
										vals[1] = dns2;
									}
								}

								if (dns1.length() > 0
										&& util.isInetAddress(dns1)) {
									vals[0] = dns1;
								}

								boolean succ = Sudo.setProperties(names, vals);
								Toast.makeText(
										getBaseContext(),
										getString(succ ? R.string.fix_success
												: R.string.fix_failure),
										Toast.LENGTH_SHORT).show();
							}
						}).setNegativeButton(android.R.string.cancel, null)
				.create().show();
	}

	private void sendFeedback(String mailTo, String title, String subject,
			String body) {
		StringBuffer buf = new StringBuffer();
		buf.append(body);
		buf.append("\n\n");
		buf.append("Product Model: " + Build.MODEL + "\n");
		buf.append("VERSION.RELEASE: " + Build.VERSION.RELEASE + "\n");
		buf.append("VERSION.INCREMENTAL: " + Build.VERSION.INCREMENTAL + "\n");
		buf.append("VERSION.SDK: " + Build.VERSION.SDK_INT + "\n");
		Intent intent = new Intent(android.content.Intent.ACTION_SEND);
		intent.setType("plain/text");
		String[] strEmailReciver = new String[] { mailTo };
		intent.putExtra(android.content.Intent.EXTRA_SUBJECT, subject);
		intent.putExtra(android.content.Intent.EXTRA_EMAIL, strEmailReciver);
		intent.putExtra(android.content.Intent.EXTRA_TEXT, buf.toString());
		startActivity(Intent.createChooser(intent, title));
	}
}