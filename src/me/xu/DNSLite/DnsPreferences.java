package me.xu.DNSLite;

import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.widget.Toast;

public class DnsPreferences extends PreferenceActivity implements
		OnSharedPreferenceChangeListener {

	private boolean changed = false;
	public static final String KEY_USETCP = "useTcp";
	public static final String KEY_LISTEN_LOCAL = "listen_local";
	public static final String KEY_IDLE_TIME = "idle_time";
	public static final String KEY_AUTO_SET_SYSTEM_DNS = "auto_set_system_dns";
	public static final String KEY_REMOTE_DNS = "remote_dns";
	public static final String KEY_NET_DNS = "netdns";
	public static final String KEY_ENABLE_CACHE = "enableCache";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.xml.dnsconfig);
	}

	@Override
	protected void onPause() {
		PreferenceManager.getDefaultSharedPreferences(this)
				.unregisterOnSharedPreferenceChangeListener(this);
		if (changed) {
			Toast.makeText(this, getString(R.string.preferenceChangeTips),
					Toast.LENGTH_SHORT).show();
			changed = false;
		}
		super.onPause();
	}

	@Override
	protected void onResume() {
		PreferenceManager.getDefaultSharedPreferences(this)
				.registerOnSharedPreferenceChangeListener(this);
		super.onResume();
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
			String key) {
		changed = true;
	}

}
