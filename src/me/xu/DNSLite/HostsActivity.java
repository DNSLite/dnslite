package me.xu.DNSLite;

import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

import me.xu.tools.Sudo;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.LinearLayout.LayoutParams;

public class HostsActivity extends Activity implements OnClickListener {
	private PopupWindow mPop = null;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.hosts);

		Button btn_hosts_source_manage = (Button) findViewById(R.id.btn_hosts_source_manage);
		btn_hosts_source_manage.setOnClickListener(this);

		Button btn_hosts_apply = (Button) findViewById(R.id.btn_hosts_apply);
		btn_hosts_apply.setOnClickListener(this);
		Button btn_hosts_resetEtc = (Button) findViewById(R.id.btn_hosts_resetEtc);
		btn_hosts_resetEtc.setOnClickListener(this);

		Button btn_hosts_edit = (Button) findViewById(R.id.btn_hosts_edit);
		btn_hosts_edit.setOnClickListener(this);
		Button btn_hosts_rawview = (Button) findViewById(R.id.btn_hosts_rawview);
		btn_hosts_rawview.setOnClickListener(this);

		HostsDB.GetInstance(getApplicationContext());
		if (HostsDB.first_run_hostsActivity) {
			Timer timer = new Timer();
			timer.schedule(new firstRunPopupWindow(), 500);
		}
	}

	@Override
	public void onClick(View v) {
		switch (v.getId()) {
		case R.id.btn_hosts_edit:
			startActivity(new Intent(HostsActivity.this,
					HostsEditorActivity.class));
			break;
		case R.id.btn_hosts_rawview:
			startActivity(new Intent(HostsActivity.this,
					HostsRawEditorActivity.class));
			break;
		case R.id.btn_hosts_source_manage:
			startActivity(new Intent(HostsActivity.this, HostSourceList.class));
			break;
		case R.id.btn_hosts_apply:
			saveEtcHosts();
			break;
		case R.id.btn_hosts_resetEtc:

			new AlertDialog.Builder(this)
					.setMessage("reset hosts?\nClear in use hosts.")
					.setPositiveButton(android.R.string.yes,
							new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog,
										int whichButton) {
									HostsDB.GetInstance(HostsActivity.this)
											.resetEtcHosts();
								}
							}).setNegativeButton(android.R.string.no, null)
					.create().show();

			break;
		default:
			break;
		}
	}

	private void saveEtcHosts() {

		Sudo sudo = new Sudo();
		if (!sudo.prepareSuProc()) {
			Toast.makeText(this, getString(R.string.Status_SUFAIL),
					Toast.LENGTH_SHORT).show();
			sudo.close();
			return;
		}

		if (!sudo.mountRw("/system")) {
			Toast.makeText(this, "RE-MOUNT fail", Toast.LENGTH_SHORT).show();
			sudo.close();
			return;
		}

		Cursor curs = null;
		try {
			HostsDB hdb = HostsDB.GetInstance(getApplicationContext());
			curs = hdb.getDistinctInUseHosts();
			sudo.writeBytes("chmod 644 /system/etc/hosts\n");
			int iIP = curs.getColumnIndex("ip");
			int iDomain = curs.getColumnIndex("domain");
			sudo.writeBytes("> /system/etc/hosts\n");
			while (curs.moveToNext()) {
				String ip = curs.getString(iIP);
				if (ip == null || ip.length() < 1) {
					continue;
				}
				String domain = curs.getString(iDomain);
				if (domain == null || domain.length() < 1) {
					continue;
				}
				String line = ip + " " + domain;
				line = line.replace('"', ' ');
				line = line.replace('\'', ' ');
				line = line.replace('\\', ' ');
				line = line.trim();
				sudo.writeBytes("echo '"+line+"' >> /system/etc/hosts\n");
			}
			sudo.remountRo();
			sudo.writeBytes("exit\n");
			HostsDB.saved();
			if (curs != null) {
				curs.close();
				curs = null;
			}
			Toast.makeText(this, "Save Success!", Toast.LENGTH_SHORT).show();
		} catch (IOException e) {
			Toast.makeText(this, "Save error, " + e.getMessage(),
					Toast.LENGTH_SHORT).show();
			e.printStackTrace();
		} finally {
			if (curs != null) {
				curs.close();
				curs = null;
			}
			sudo.close();
		}
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		if (mPop != null) {
			mPop.dismiss();
		}
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
			if (HostsDB.needRewriteHosts) {
				new AlertDialog.Builder(this)
						.setMessage("Rewrite /system/etc/hosts?")
						.setPositiveButton(android.R.string.yes,
								new DialogInterface.OnClickListener() {
									public void onClick(DialogInterface dialog,
											int whichButton) {
										saveEtcHosts();
										finish();
										System.exit(0);
									}
								})
						.setNegativeButton(android.R.string.no,
								new DialogInterface.OnClickListener() {
									public void onClick(DialogInterface dialog,
											int whichButton) {
										finish();
									}
								}).create().show();
				return true;
			} else {
				finish();
				System.exit(0);
			}
		}
		return super.onKeyDown(keyCode, event);
	}

	private Handler mHandler = new Handler() {

		public void handleMessage(Message msg) {
			switch (msg.what) {
			case 1:
				try {
					LayoutInflater mLayoutInflater = (LayoutInflater) HostsActivity.this
							.getSystemService(LAYOUT_INFLATER_SERVICE);
					View popmenu = mLayoutInflater.inflate(
							R.layout.hosts_raw_editor, null);
					TextView tv = (TextView) popmenu
							.findViewById(R.id.hosts_raw_editor);
					tv.setText(R.string.first_run_manage);
					tv.setBackgroundColor(17170433);
					tv.setEnabled(false);
					popmenu.measure(LayoutParams.WRAP_CONTENT,
							LayoutParams.WRAP_CONTENT);
					mPop = new PopupWindow(popmenu, LayoutParams.WRAP_CONTENT,
							LayoutParams.WRAP_CONTENT);
					mPop.setBackgroundDrawable(getResources().getDrawable(
							R.drawable.popup_full_bright));
					mPop.setOutsideTouchable(true);
					mPop.setFocusable(false);
					Button btn_hosts_source_manage = (Button) HostsActivity.this
							.findViewById(R.id.btn_hosts_source_manage);
					mPop.showAsDropDown(btn_hosts_source_manage, 0, -15);
				} catch (Exception e) {
					e.printStackTrace();
				}
				HostsDB.first_run_hostsActivity = false;
				break;
			}
		};
	};

	private class firstRunPopupWindow extends TimerTask {
		@Override
		public void run() {
			Message message = new Message();
			message.what = 1;
			mHandler.sendMessage(message);
		}
	}
}
