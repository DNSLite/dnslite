package me.xu.DNSLite;

import java.util.ArrayList;

import android.net.Uri;
import me.xu.tools.Sudo;
import me.xu.tools.util;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TabHost;
import android.widget.TabWidget;
import android.widget.TextView;
import android.widget.Toast;

public class DNSLiteActivity extends FragmentActivity {
	TabHost mTabHost;
	ViewPager mViewPager;
	TabsAdapter mTabsAdapter;

	public final static String TAB_TAG_DNS = "DNS";
	public final static String TAB_TAG_HOSTS = "hosts";

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
			if (HostsDB.needRewriteHosts) {
				new AlertDialog.Builder(this)
						.setMessage(R.string.host_rewrite_alert_msg)
						.setPositiveButton(android.R.string.yes,
								new DialogInterface.OnClickListener() {
									public void onClick(DialogInterface dialog,
											int whichButton) {
										HostsDB.saveEtcHosts(getApplicationContext());
										HostsDB.saved();
										finish();
									}
								})
						.setNegativeButton(android.R.string.no,
								new DialogInterface.OnClickListener() {
									public void onClick(DialogInterface dialog,
											int whichButton) {
										finish();
									}
								}).create().show();
			}
		}
		return super.onKeyDown(keyCode, event);
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {

		super.onCreate(savedInstanceState);
		setContentView(R.layout.tabs);
		mTabHost = (TabHost) findViewById(android.R.id.tabhost);
		mTabHost.setup();

		mViewPager = (ViewPager) findViewById(R.id.pager);
		mTabsAdapter = new TabsAdapter(this, mTabHost, mViewPager);

		mTabsAdapter.addTab(
				mTabHost.newTabSpec(TAB_TAG_DNS).setIndicator(
						getString(R.string.dns_lable)),
				DNSServiceActivity.DNSServiceFragment.class, null);
		mTabsAdapter.addTab(
				mTabHost.newTabSpec(TAB_TAG_HOSTS).setIndicator(TAB_TAG_HOSTS),
				HostsActivity.HostsFragment.class, null);

		if (savedInstanceState != null) {
			mTabHost.setCurrentTabByTag(savedInstanceState.getString("tab"));
		}
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putString("tab", mTabHost.getCurrentTabTag());
	}

	/**
	 * This is a helper class that implements the management of tabs and all
	 * details of connecting a ViewPager with associated TabHost. It relies on a
	 * trick. Normally a tab host has a simple API for supplying a View or
	 * Intent that each tab will show. This is not sufficient for switching
	 * between pages. So instead we make the content part of the tab host 0dp
	 * high (it is not shown) and the TabsAdapter supplies its own dummy view to
	 * show as the tab content. It listens to changes in tabs, and takes care of
	 * switch to the correct paged in the ViewPager whenever the selected tab
	 * changes.
	 */
	public static class TabsAdapter extends FragmentPagerAdapter implements
			TabHost.OnTabChangeListener, ViewPager.OnPageChangeListener {
		private final Context mContext;
		private final TabHost mTabHost;
		private final ViewPager mViewPager;
		private final ArrayList<TabInfo> mTabs = new ArrayList<TabInfo>();

		static final class TabInfo {
			@SuppressWarnings("unused")
			private final String tag;
			private final Class<?> clss;
			private final Bundle args;

			TabInfo(String _tag, Class<?> _class, Bundle _args) {
				tag = _tag;
				clss = _class;
				args = _args;
			}
		}

		static class DummyTabFactory implements TabHost.TabContentFactory {
			private final Context mContext;

			public DummyTabFactory(Context context) {
				mContext = context;
			}

			@Override
			public View createTabContent(String tag) {
				View v = new View(mContext);
				v.setMinimumWidth(0);
				v.setMinimumHeight(0);
				return v;
			}
		}

		public TabsAdapter(FragmentActivity activity, TabHost tabHost,
				ViewPager pager) {
			super(activity.getSupportFragmentManager());
			mContext = activity;
			mTabHost = tabHost;
			mViewPager = pager;
			mTabHost.setOnTabChangedListener(this);
			mViewPager.setAdapter(this);
			mViewPager.setOnPageChangeListener(this);
		}

		public void addTab(TabHost.TabSpec tabSpec, Class<?> clss, Bundle args) {
			tabSpec.setContent(new DummyTabFactory(mContext));
			String tag = tabSpec.getTag();

			TabInfo info = new TabInfo(tag, clss, args);
			mTabs.add(info);
			mTabHost.addTab(tabSpec);

			if (Build.VERSION.SDK_INT < 11) {
				int i = mTabHost.getTabWidget().getChildCount() - 1;
				if (i > -1) {
					View view = mTabHost.getTabWidget().getChildAt(i);
					if (view != null) {
						View iv = view.findViewById(android.R.id.icon);
						if (iv != null) {
							iv.setVisibility(View.GONE);
						}
						view.getLayoutParams().height = 55;
					}
				}
			}
			notifyDataSetChanged();
		}

		@Override
		public int getCount() {
			return mTabs.size();
		}

		@Override
		public Fragment getItem(int position) {
			TabInfo info = mTabs.get(position);
			return Fragment.instantiate(mContext, info.clss.getName(),
					info.args);
		}

		@Override
		public void onTabChanged(String tabId) {
			int position = mTabHost.getCurrentTab();
			mViewPager.setCurrentItem(position);
		}

		@Override
		public void onPageScrolled(int position, float positionOffset,
				int positionOffsetPixels) {
		}

		@Override
		public void onPageSelected(int position) {
			// Unfortunately when TabHost changes the current tab, it kindly
			// also takes care of putting focus on it when not in touch mode.
			// The jerk.
			// This hack tries to prevent this from pulling focus out of our
			// ViewPager.
			TabWidget widget = mTabHost.getTabWidget();
			int oldFocusability = widget.getDescendantFocusability();
			widget.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
			mTabHost.setCurrentTab(position);
			widget.setDescendantFocusability(oldFocusability);
		}

		@Override
		public void onPageScrollStateChanged(int state) {
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
        case R.id.menu_donate:
            donatePayPalOnClick();
            break;
		case R.id.menu_about:
			show_about();
			break;
		case R.id.menu_fix_netdns:
			fix_netdns();
			break;
		case R.id.menu_share:
			sendShare(R.string.menu_share, R.string.menu_share_tpl);
			break;
		case R.id.menu_feedback:
			sendFeedback(
					"\"DNSLite\"<xudejian2008@gmail.com>",
					getString(R.string.menu_feedback),
					getString(R.string.menu_feedback) + " : "
							+ this.getPackageName(), "Do you like dnslite?");
			break;
		default:
			return super.onOptionsItemSelected(item);
		}
		return true;
	}
    /**
     * Donate button with PayPal by opening browser with defined URL For possible parameters see:
     * https://cms.paypal.com/us/cgi-bin/?cmd=_render-content&content_ID=developer/
     * e_howto_html_Appx_websitestandard_htmlvariables
     */
    public void donatePayPalOnClick() {

        Uri.Builder uriBuilder = new Uri.Builder();
        uriBuilder.scheme("https").authority("www.paypal.com").path("cgi-bin/webscr");
        uriBuilder.appendQueryParameter("cmd", "_donations");

        uriBuilder.appendQueryParameter("business", "7CX2DG8VSMBN2");
        uriBuilder.appendQueryParameter("lc", "US");
        uriBuilder.appendQueryParameter("item_name", "Donate DNSLite");
        uriBuilder.appendQueryParameter("no_note", "1");
        // uriBuilder.appendQueryParameter("no_note", "0");
        // uriBuilder.appendQueryParameter("cn", "Note to the developer");
        uriBuilder.appendQueryParameter("no_shipping", "1");
        uriBuilder.appendQueryParameter("currency_code", "USD");

        // uriBuilder.appendQueryParameter("bn", "PP-DonationsBF:btn_donate_LG.gif:NonHosted");
        Uri payPalUri = uriBuilder.build();

        Intent viewIntent = new Intent(Intent.ACTION_VIEW, payPalUri);
        startActivity(viewIntent);
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
		String configDNS = sharedPref.getString(DnsPreferences.KEY_FIX_DNS,
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
                                DNSServiceActivity.DNSServiceFragment ds = (DNSServiceActivity.DNSServiceFragment)mTabsAdapter.instantiateItem(mViewPager, 0);
                                ds.updateCurrentDNSView();
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
		intent.setType("text/plain");
		String[] strEmailReciver = new String[] { mailTo };
		intent.putExtra(android.content.Intent.EXTRA_SUBJECT, subject);
		intent.putExtra(android.content.Intent.EXTRA_EMAIL, strEmailReciver);
		intent.putExtra(android.content.Intent.EXTRA_TEXT, buf.toString());
		startActivity(Intent.createChooser(intent, title));
	}

	private void sendShare(int subject, int body) {
		Intent intent = new Intent(android.content.Intent.ACTION_SEND);
		intent.setType("text/plain");
		intent.putExtra(android.content.Intent.EXTRA_SUBJECT,
				getString(subject));
		intent.putExtra(android.content.Intent.EXTRA_TEXT, getString(body));
		startActivity(Intent.createChooser(intent, getString(subject)));
	}
}
