package me.xu.DNSLite;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpGet;

import android.R.color;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.net.http.AndroidHttpClient;
import android.os.Build.VERSION;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout.LayoutParams;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.SimpleCursorAdapter.ViewBinder;
import android.widget.ToggleButton;

import android.os.AsyncTask;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.database.Cursor;
import android.graphics.Rect;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import android.widget.SimpleCursorAdapter;

public class DnsHostsActivity extends ListActivity {

	private PopupWindow mPop = null;
	private ProgressDialog progressDialog = null;
	private final String[] SCA_item = new String[] { "status", "domain", "ip" };
	private final int[] SCA_item_id = new int[] { R.id.status, R.id.domain,
			R.id.ip };

	private Cursor cursor = null;
	private HostsDB hdb = null;
	private SimpleCursorAdapter adapter = null;
	private long search_gid = -1;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.hosts_editor);
		hdb = HostsDB.GetInstance(this);
		try {
			search_gid = getIntent().getExtras().getLong("gid");
		} catch (Exception e) {
			search_gid = 0;
		}
		cursor = hdb.getDnsHostsByGroup(search_gid);

		if (VERSION.SDK_INT > 10) {
			adapter = new SimpleCursorAdapter(this, R.layout.hosts_row, cursor,
					SCA_item, SCA_item_id, 0);
		} else {
			adapter = new SimpleCursorAdapter(this, R.layout.hosts_row, cursor,
					SCA_item, SCA_item_id);
		}

		ListView lv = getListView();
		lv.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
		TextView etv = (TextView) lv.getEmptyView();
		if (etv != null) {
			etv.setText(R.string.dns_group_no_hosts);
			etv.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					DnsHostsActivity.this.openOptionsMenu();
				}
			});
		}

		adapter.setViewBinder(new ViewBinder() {
			public boolean setViewValue(View view, Cursor cursor,
					int columnIndex) {
				switch (view.getId()) {
				case R.id.status:
					ToggleButton v = (ToggleButton) view;
					int result = cursor.getInt(cursor.getColumnIndex("status"));
					v.setChecked((result == 1));
					((View) view.getParent()).setBackgroundColor((cursor
							.getPosition() % 2 == 0) ? color.background_light
							: 0x300000FF);
					return true;
				}
				return false;
			}
		});
		setListAdapter(adapter);

		if (cursor.getCount() > 5) {
			View adView = this.findViewById(R.id.adView);
			adView.setVisibility(View.GONE);
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.dns_hosts_option, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Log.d(TAG, "search_sid:"+search_sid);
		switch (item.getItemId()) {
		case R.id.menu_dns_hosts_add:
			addHosts(0, null, null);
			break;
		case R.id.menu_dns_empty:
			// bug 1.3
			//hdb.removeDnsHost(search_gid);
			hdb.delDnsHostsByGroup(search_gid);
			new RefreshList().execute();
			break;
		case R.id.menu_dns_disable:
			hdb.disableAllInGroup(search_gid);
			new RefreshList().execute();
			break;
		case R.id.menu_dns_enableAll:
			hdb.enableAllInGroup(search_gid);
			new RefreshList().execute();
			break;
		case R.id.menu_dns_setip:
			setIP();
			break;
		case R.id.menu_dns_update:
			Cursor c = hdb.getDnsGroup(search_gid);
			if (c != null && c.moveToFirst()) {
				String name = c.getString(c.getColumnIndex("name"));
				String url = c.getString(c.getColumnIndex("url"));
				c.close();
				c = null;
				startProgressDialog("Update " + name, "Load from " + url);
				new httpContentGet().execute(url, String.valueOf(search_gid));
			}
			if (c != null) {
				c.close();
			}
			break;
		default:
			return super.onOptionsItemSelected(item);
		}
		return true;
	}

	private void startProgressDialog(String title, String message) {
		if (progressDialog == null) {
			progressDialog = new ProgressDialog(DnsHostsActivity.this);
		}
		progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
		progressDialog.setTitle(title);
		progressDialog.setMax(100);
		progressDialog.setProgress(0);
		progressDialog.setIndeterminate(false);
		progressDialog.setMessage(message);
		progressDialog.setCancelable(false);
		progressDialog.show();
	}

	private void setIP() {
		LayoutInflater factory = LayoutInflater.from(DnsHostsActivity.this);
		final View textEntryView = factory.inflate(R.layout.hosts_host_add,
				null);
		View et_domain = textEntryView.findViewById(R.id.domain_edit);
		et_domain.setVisibility(View.GONE);
		View domain = textEntryView.findViewById(R.id.domain);
		domain.setVisibility(View.GONE);

		new AlertDialog.Builder(DnsHostsActivity.this)
				.setMessage(R.string.dns_setip_entry)
				.setView(textEntryView)
				.setPositiveButton(android.R.string.ok,
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog,
									int whichButton) {
								/* User clicked OK so do some stuff */
								EditText et_ip = (EditText) textEntryView
										.findViewById(R.id.ip_edit);
								String ip = et_ip.getText().toString().trim();
								if (hdb.updateAllIpInDnsGroup(search_gid, ip) > 0) {
									new RefreshList().execute();
								} else {
									Toast.makeText(DnsHostsActivity.this,
											getString(R.string.dns_setip_err),
											Toast.LENGTH_SHORT).show();
								}
							}
						}).setNegativeButton(android.R.string.cancel, null)
				.create().show();
	}

	private void addHosts(final long _id, String domain, String ip) {
		LayoutInflater factory = LayoutInflater.from(DnsHostsActivity.this);
		final View textEntryView = factory.inflate(R.layout.hosts_host_add,
				null);
		final EditText et_domain = (EditText) textEntryView
				.findViewById(R.id.domain_edit);
		final EditText et_ip = (EditText) textEntryView
				.findViewById(R.id.ip_edit);
		if (domain != null) {
			et_domain.setText(domain);
		}
		if (ip != null) {
			et_ip.setText(ip);
		}

		new AlertDialog.Builder(DnsHostsActivity.this)
				.setMessage(R.string.dns_host_text_entry)
				.setView(textEntryView)
				.setPositiveButton(android.R.string.ok,
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog,
									int whichButton) {
								/* User clicked OK so do some stuff */
								String domain = et_domain.getText().toString()
										.trim();
								String ip = et_ip.getText().toString().trim();
								if (domain.length() < 1) {
									Toast.makeText(
											DnsHostsActivity.this,
											getString(R.string.dns_host_domain_null),
											Toast.LENGTH_LONG).show();
									return;
								}
								if (domain.lastIndexOf('*') > 0) {
									Toast.makeText(
											DnsHostsActivity.this,
											getString(R.string.dns_host_domain_format_err),
											Toast.LENGTH_LONG).show();
									return;
								}
								if (hdb.addDomainToGroup(domain, ip,
										search_gid, _id, 1) > 0) {
									new RefreshList().execute();
								} else {
									Toast.makeText(
											DnsHostsActivity.this,
											getString(R.string.hosts_host_add_fail),
											Toast.LENGTH_SHORT).show();
								}
							}
						}).setNegativeButton(android.R.string.cancel, null)
				.create().show();
	}

	private void enable(final long rowId) {
		if (hdb.enableDnsHostById(rowId)) {
			new RefreshList().execute();
		}
	}

	private void disable(final long rowId) {
		if (hdb.disableDnsHostById(rowId)) {
			new RefreshList().execute();
		}
	}

	private void delete(final long rowId) {
		new AlertDialog.Builder(this)
				.setTitle("Delete")
				.setPositiveButton(android.R.string.yes,
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog,
									int which) {
								deleteData(rowId);
							}
						}).setNegativeButton(android.R.string.cancel, null)
				.show();
	}

	private void deleteData(long rowId) {
		if (hdb.removeDnsHost(rowId)) {
			new RefreshList().execute();
		}
	}

	private class RefreshList extends AsyncTask<Void, Void, Cursor> {
		protected Cursor doInBackground(Void... params) {
			return hdb.getDnsHostsByGroup(search_gid);
		}

		protected void onPostExecute(Cursor newCursor) {
			try {
				if (newCursor.getCount() > 5) {
					View adView = DnsHostsActivity.this
							.findViewById(R.id.adView);
					adView.setVisibility(View.GONE);
				}
			} catch (Exception e) {
			}
			adapter.changeCursor(newCursor);
			cursor.close();
			cursor = newCursor;
		}
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		if (HostsDB.needRewriteDnsCache) {
			hdb.writeDnsCacheFile();
		}
		if (mPop != null) {
			mPop.dismiss();
		}
		if (cursor != null) {
			cursor.close();
		}
		if (progressDialog != null) {
			progressDialog.dismiss();
		}
	}

	private View.OnClickListener onClickPopMenu = new View.OnClickListener() {

		@Override
		public void onClick(View v) {
			ListView l = getListView();
			int position = l.getCheckedItemPosition();
			long ids[] = l.getCheckedItemIds();
			if (ids.length < 1) {
				mPop.dismiss();
				return;
			}
			long id = ids[0];
			int vid = v.getId();
			mPop.dismiss();

			switch (vid) {
			case R.id.quick_actions_enable:
				enable(id);
				// Log.d(TAG, "enable id:" + id + " position:" + position);
				break;
			case R.id.quick_actions_disable:
				disable(id);
				// Log.d(TAG, "disable id:" + id + " position:" + position);
				break;
			case R.id.quick_actions_delete:
				delete(id);
				// Log.d(TAG, "delete id:" + id + " position:" + position);
				break;
			case R.id.quick_actions_edit:
				// Log.d(TAG, "edit id:" + id + " position:" + position);
				if (cursor.moveToPosition(position)) {
					String domain = cursor.getString(cursor
							.getColumnIndex("domain"));
					String ip = cursor.getString(cursor.getColumnIndex("ip"));
					addHosts(id, domain, ip);
				}
				break;
			default:
				break;
			}
		}
	};

	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {

		// Log.d(TAG, "id:" + id + " position:" + position);
		l.setItemChecked(position, true);

		if (mPop == null) {
			LayoutInflater mLayoutInflater = (LayoutInflater) DnsHostsActivity.this
					.getSystemService(LAYOUT_INFLATER_SERVICE);
			View popmenu = mLayoutInflater.inflate(
					R.layout.hosts_editor_popmenu, null);
			popmenu.measure(LayoutParams.WRAP_CONTENT,
					LayoutParams.WRAP_CONTENT);
			Button quick_actions_enable = (Button) popmenu
					.findViewById(R.id.quick_actions_enable);
			quick_actions_enable.setOnClickListener(onClickPopMenu);
			Button quick_actions_disable = (Button) popmenu
					.findViewById(R.id.quick_actions_disable);
			quick_actions_disable.setOnClickListener(onClickPopMenu);
			Button quick_actions_comment = (Button) popmenu
					.findViewById(R.id.quick_actions_comment);
			quick_actions_comment.setVisibility(View.GONE);

			Button quick_actions_delete = (Button) popmenu
					.findViewById(R.id.quick_actions_delete);
			quick_actions_delete.setOnClickListener(onClickPopMenu);
			Button quick_actions_edit = (Button) popmenu
					.findViewById(R.id.quick_actions_edit);
			quick_actions_edit.setOnClickListener(onClickPopMenu);
			mPop = new PopupWindow(popmenu, LayoutParams.WRAP_CONTENT,
					LayoutParams.WRAP_CONTENT);
			mPop.setBackgroundDrawable(getResources().getDrawable(
					R.drawable.popup_full_bright));
			mPop.setOutsideTouchable(true);
			mPop.setFocusable(true);
		} else {
			if (mPop.isShowing()) {
				mPop.dismiss();
			}
		}

		View m = v.findViewById(R.id.more_button);
		int[] location = new int[2];
		m.getLocationOnScreen(location);
		Rect anchorRect = new Rect(location[0], location[1], location[0]
				+ m.getWidth(), location[1] + m.getHeight());
		mPop.showAtLocation(m, Gravity.NO_GRAVITY,
				anchorRect.right - m.getWidth(),
				anchorRect.bottom - m.getHeight());
	}

	private class httpContentGet extends AsyncTask<String, Integer, String> {
		protected String doInBackground(String... urls) {
			int gid = 0;
			try {
				gid = Integer.parseInt(urls[1]);
			} catch (Exception e) {
			}

			if (urls == null || urls[0] == null || urls[0].length() < 1) {
				return getString(R.string.no_need_update);
			} else {
				int pos = urls[0].indexOf("://");
				if (pos < 1) {
					return getString(android.R.string.httpErrorBadUrl);
				}
				String schema = urls[0].substring(0, pos).toLowerCase();
				if (!(schema.equals("http") || schema.equals("https") || schema
						.equals("ftp"))) {
					return getString(android.R.string.httpErrorUnsupportedScheme);
				}
			}

			AndroidHttpClient client = AndroidHttpClient.newInstance("Android");
			try {
				HttpGet httpGet = new HttpGet(urls[0]);
				HttpResponse response;
				response = client.execute(httpGet);
				if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
					BufferedReader rd = new BufferedReader(
							new InputStreamReader(response.getEntity()
									.getContent()));
					long len = response.getEntity().getContentLength();
					long plen = 0;
					String line;
					hdb.delDnsHostsByGroup(gid);
					int last_progress = 0;
					int cur_progress = 0;
					while ((line = rd.readLine()) != null) {
						plen += line.length();
						hdb.addDnsHostLine(line, gid);
						cur_progress = (int) ((plen / (float) len) * 100);
						if (cur_progress > last_progress) {
							publishProgress(cur_progress);
							last_progress = cur_progress;
						}
					}
				}
			} catch (IllegalStateException ise) {
				ise.printStackTrace();
				return ise.getMessage();
			} catch (IllegalArgumentException urle) {
				urle.printStackTrace();
				return urle.getMessage();
			} catch (IOException e) {
				e.printStackTrace();
				return e.getMessage();
			} finally {
				client.close();
			}
			return null;
		}

		protected void onProgressUpdate(Integer... progress) {
			progressDialog.setProgress(progress[0]);
		}

		protected void onPostExecute(String result) {
			progressDialog.dismiss();
			if (result != null) {
				Toast.makeText(DnsHostsActivity.this, getString(R.string.fail) +": " + result,
						Toast.LENGTH_SHORT).show();
			}
			new RefreshList().execute();
		}
	}
}
