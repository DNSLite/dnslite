package me.xu.DNSLite;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.os.AsyncTask;
import android.os.Build.VERSION;
import android.preference.PreferenceManager;
import me.xu.tools.DNSProxyClient;
import me.xu.tools.Sudo;
import me.xu.tools.util;

public class DNSProxy {

	private Context context = null;
	
	public static final int Status_OK = 0;
	public static final int Status_ALREDY_RUNNING = 1;
	public static final int Status_BIN_NOTEXIST = 2;
	public static final int Status_SUFAIL = 3;
	public static final int Status_SETEXEC_FAIL = 4;
	public static final int Status_SEND_COMMAND_FAIL = 5;
	public static final int Status_START_SUCC = 6;
	public static final int Status_START_FAIL = 7;
	public static final int Status_UNKNOW_ERROR = 8;

	private boolean useTcp = false;
	private boolean auto_set_system_dns = true;
	private String listen_addr = null;
	private int max_idle_time = 0;
	private int clean_cache_gap = 0;

	private int run_status = -1;
	private String dnsBin = null;
	private String remoteDNS = null;

	public DNSProxy(Context context) {
		this.context = context;
	}
	
	public int getStatus() {
		return run_status;
	}
	
	private String getString(int resId) {
		return context.getString(resId);
	}

	public String getStatusStr() {
		switch (run_status) {
		case Status_OK:
			return getString(R.string.Status_OK);
		case Status_ALREDY_RUNNING:
			return getString(R.string.Status_ALREDY_RUNNING);
		case Status_BIN_NOTEXIST:
			return getString(R.string.Status_BIN_NOTEXIST);
		case Status_SUFAIL:
			return getString(R.string.Status_SUFAIL);
		case Status_SETEXEC_FAIL:
			return getString(R.string.Status_SETEXEC_FAIL);
		case Status_SEND_COMMAND_FAIL:
			return getString(R.string.Status_SEND_COMMAND_FAIL);
		case Status_START_SUCC:
			return getString(R.string.Status_START_SUCC);
		case Status_START_FAIL:
			return getString(R.string.Status_START_FAIL);
		case Status_UNKNOW_ERROR:
			return getString(R.string.Status_UNKNOW_ERROR);
		default:
			return getString(R.string.Status_OTHER_STATUS) + " : " + run_status;
		}
	}

	public boolean isStartSucc() {
		switch (run_status) {
		case Status_OK:
		case Status_START_SUCC:
		case Status_ALREDY_RUNNING:
			return true;
		default:
			return false;
		}
	}

	public void stopDNSService() {
		new StopDNS().execute();
	}

	public void startDNSService() {
		new StartDNS().execute();
	}

	private String getDnsBin() {
		if (dnsBin == null) {
			ApplicationInfo ainfo = context.getApplicationInfo();
			StringBuffer sb = new StringBuffer();

			if (VERSION.SDK_INT > 8) {
				sb.append(ainfo.nativeLibraryDir);
			} else {
				sb.append(ainfo.dataDir);
				sb.append("/lib");
			}
			sb.append("/libdnslite.so");
			dnsBin = sb.toString();
			File bin = new File(dnsBin);
			if (!bin.exists()) {
				bin = util.findFile(ainfo.dataDir, "libdnslite.so");
				if (bin != null) {
					dnsBin = bin.getAbsolutePath();
				}
			}
		}
		return dnsBin;
	}

	private boolean checkDnsBinExists() {
		try {
			File check = new File(getDnsBin());
			return check.exists();
		} catch (Exception e) {
			return false;
		}
	}

	private void loadDnsConfig() {
		SharedPreferences sharedPref = PreferenceManager
				.getDefaultSharedPreferences(context);
		int idle_time = 1;
		try {
			idle_time = Integer.valueOf(sharedPref.getString(
					DnsPreferences.KEY_IDLE_TIME, "30"));
		} catch (Exception e) {
		}
		idle_time *= 60;
		this.max_idle_time = idle_time;

		boolean enable_cache = sharedPref.getBoolean(
				DnsPreferences.KEY_ENABLE_CACHE, false);
		if (enable_cache) {
			this.clean_cache_gap = 7200;
		} else {
			this.clean_cache_gap = 0;
		}

		this.useTcp = sharedPref.getBoolean(DnsPreferences.KEY_USETCP, false);
		this.auto_set_system_dns = sharedPref.getBoolean(
				DnsPreferences.KEY_AUTO_SET_SYSTEM_DNS, true);
		boolean listen_local = sharedPref.getBoolean(
				DnsPreferences.KEY_LISTEN_LOCAL, false);
		this.listen_addr = (listen_local) ? "127.0.0.1" : null;
		remoteDNS = sharedPref.getString(DnsPreferences.KEY_REMOTE_DNS, null);
	}

	private String getDnsStartCmd(boolean appendTail) {
		loadDnsConfig();
		StringBuffer sb = new StringBuffer();

		sb.append(getDnsBin());

		if (useTcp) {
			sb.append(" -t");
		}

		File cache = context.getApplicationContext().getFileStreamPath(
				HostsDB.static_cache);
		if (cache != null && cache.exists()) {
			sb.append(" -d ");
			sb.append(cache.getAbsolutePath());
		}

		if (listen_addr != null) {
			listen_addr = listen_addr.trim();
			if (listen_addr.length() > 0) {
				sb.append(" -a ");
				sb.append(listen_addr);
			}
		}

		sb.append(" -i ");
		sb.append(max_idle_time);

		sb.append(" -g ");
		sb.append(clean_cache_gap);

		if (auto_set_system_dns) {
			sb.append(" -s");
		}

		if (remoteDNS != null) {
			remoteDNS = remoteDNS.replaceAll("\\s", ",").trim();
			if (remoteDNS.length() > 1) {
				sb.append(" -r ");
				sb.append(remoteDNS);
			}
		}

		if (appendTail) {
			sb.append(" && echo XDJ_START_OK || echo XDJ_START_FAIL \n");
		}
		return sb.toString();
	}

	private class StopDNS extends AsyncTask<Void, Void, Boolean> {
		@Override
		protected Boolean doInBackground(Void... v) {
			return DNSProxyClient.quit();
		}
	}

	private class StartDNS extends AsyncTask<Void, Void, Integer> {
		@Override
		protected Integer doInBackground(Void... v) {
			if (DNSProxyClient.isDnsRuning()) {
				return Status_ALREDY_RUNNING;
			}

			String cmd = DNSProxy.this.getDnsStartCmd(true);
			if (!DNSProxy.this.checkDnsBinExists()) {
				return Status_BIN_NOTEXIST;
			}
			Sudo sudo = new Sudo();

			try {
				if (!sudo.prepareSuProc()) {
					return Status_SUFAIL;
				}

				String chmodcmd = sudo.getCmdChmod(getDnsBin(), 755);
				if (chmodcmd != null) {
					if (!sudo.runcommand(chmodcmd + "\n")) {
						return Status_SEND_COMMAND_FAIL;
					}
				}

				String rv = null;
				BufferedReader suOut = sudo.getSuOut();

				if (!sudo.runcommand(cmd)) {
					return Status_SEND_COMMAND_FAIL;
				}
				int status = Status_START_FAIL;
				while ((rv = suOut.readLine()) != null) {
					if (rv.equals("XDJ_START_OK")) {
						status = Status_START_SUCC;
						break;
					} else if (rv.equals("XDJ_START_FAIL")) {
						status = Status_START_FAIL;
						break;
					} else if (rv.equals("Success. DNS Proxy.")) {
						status = Status_START_SUCC;
						break;
					}
				}
				sudo.runcommand("exit\n");
				return status;
			} catch (IOException e) {
				e.printStackTrace();
				return Status_UNKNOW_ERROR;
			} catch (Exception e) {
				e.printStackTrace();
				return Status_UNKNOW_ERROR;
			} finally {
				sudo.close();
			}
		}

		@Override
		protected void onPostExecute(Integer result) {
			run_status = result;
		}
	}
}
