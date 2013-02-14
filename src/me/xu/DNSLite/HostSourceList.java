package me.xu.DNSLite;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpGet;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.http.AndroidHttpClient;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.widget.SimpleCursorAdapter;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.LinearLayout.LayoutParams;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

public class HostSourceList extends ListActivity {

	private Cursor cursor = null;
	private HostsDB hdb = null;
	private PopupWindow mPop = null;
	private SimpleCursorAdapter adapter = null;
	private ProgressDialog progressDialog = null;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.host_source);
		hdb = HostsDB.GetInstance(this);
		cursor = hdb.getAllHostsSource();

		adapter = new SimpleCursorAdapter(this, R.layout.host_source_row,
				cursor, new String[] { "name", "url" }, new int[] { R.id.text1,
						R.id.text2 }, 0);

		setListAdapter(adapter);

		ListView lv = getListView();
		registerForContextMenu(lv);
	}

	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		view(id);
		super.onListItemClick(l, v, position, id);
	}

	@Override
	protected void onResume() {
		super.onResume();

		if (HostsDB.first_run_hostsSource) {
			Timer timer = new Timer();
			timer.schedule(new firstRunPopupWindow(), 500);
		}
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.menu_item_show_active:
			view(-1);
			break;
		case R.id.menu_host_source_add:
			addHostsSource(0, null, null);
			break;
		case R.id.menu_hosts_raw_edit:
			startActivity(new Intent(this, HostsRawEditorActivity.class));
			break;
		case R.id.menu_hosts_reset:
			hosts_reset(ALERT_DIALOG_RESET_ETC,
					R.string.host_reset_etc_alert_msg);
			break;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.hosts_source_option, menu);
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v,
			ContextMenuInfo menuInfo) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.hosts_source_context, menu);

		AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;
		if (info.id == 0) {
			menu.findItem(R.id.menu_item_delete).setEnabled(false);
			menu.findItem(R.id.menu_item_edit).setEnabled(false);
		}
		super.onCreateContextMenu(menu, v, menuInfo);
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item
				.getMenuInfo();
		switch (item.getItemId()) {
		case R.id.menu_item_update: {
			TextView tv1 = (TextView) info.targetView.findViewById(R.id.text1);
			String name = tv1.getText().toString();
			TextView tv2 = (TextView) info.targetView.findViewById(R.id.text2);
			String url = tv2.getText().toString();
			update(info.id, name, url);
		}
			break;
		case R.id.menu_item_edit: {
			TextView tv1 = (TextView) info.targetView.findViewById(R.id.text1);
			String name = tv1.getText().toString();
			TextView tv2 = (TextView) info.targetView.findViewById(R.id.text2);
			String url = tv2.getText().toString();
			addHostsSource(info.id, name, url);
		}
			break;
		case R.id.menu_item_merge:
			merge(info.id);
			break;
		case R.id.menu_item_disable:
			disable_hosts_bysid(info.id);
			break;
		case R.id.menu_item_delete:
			delete(info.id);
			break;
		case R.id.menu_item_empty:
			hdb.removeHostBySid(info.id);
			break;
		default:
			break;
		}
		return super.onContextItemSelected(item);
	}

	private void startProgressDialog(String title, String message) {
		if (progressDialog == null) {
			progressDialog = new ProgressDialog(HostSourceList.this);
		}
		progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
		progressDialog.setTitle(title);
		progressDialog.setMax(100);
		progressDialog.setProgress(0);
		progressDialog.setIndeterminate(false);
		progressDialog.setMessage(message);
		progressDialog.show();
	}

	private void update(final long rowId, String name, String url) {
		if (rowId == 0) {
			startProgressDialog("Import", "Import from /system/etc/hosts");
			new CheckLocalHosts().execute();
		} else {
			startProgressDialog("Update " + name, "Load from " + url);
			new httpContentGet().execute(url, String.valueOf(rowId));
			return;
		}
	}

	private void view(final long rowId) {
		Intent v = new Intent(HostSourceList.this, HostsEditorActivity.class);
		v.putExtra("sid", rowId);
		startActivity(v);
	}

	private void merge(final long rowId) {
		hdb.mergeHostsSource(rowId);
	}

	private void disable_hosts_bysid(final long rowId) {
		hdb.disableHostInSource(rowId);
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
		if (hdb.removeHostsSource(rowId)) {
			new RefreshList().execute();
		}
	}

	private void addHostsSource(final long rowId, String name, String url) {
		LayoutInflater factory = LayoutInflater.from(HostSourceList.this);
		final View textEntryView = factory.inflate(R.layout.hosts_source_add,
				null);
		if (name != null) {
			EditText et_name = (EditText) textEntryView
					.findViewById(R.id.name_edit);
			et_name.setText(name);
		}
		if (url != null) {
			EditText et_url = (EditText) textEntryView
					.findViewById(R.id.url_edit);
			et_url.setText(url);
		}
		new AlertDialog.Builder(HostSourceList.this)
				.setTitle(R.string.hosts_source_text_entry)
				.setView(textEntryView)
				.setPositiveButton(android.R.string.ok,
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog,
									int whichButton) {
								/* User clicked OK so do some stuff */
								EditText et_name = (EditText) textEntryView
										.findViewById(R.id.name_edit);
								EditText et_url = (EditText) textEntryView
										.findViewById(R.id.url_edit);
								String name = et_name.getText().toString()
										.trim();
								String url = et_url.getText().toString().trim();
								if (url.length() < 1) {
									Toast.makeText(
											HostSourceList.this,
											getString(R.string.hosts_source_add_input_error),
											Toast.LENGTH_SHORT).show();
									return;
								}
								if (name.length() < 1) {
									int start = url.indexOf("://");
									int end = 0;
									if (start != -1) {
										start += 3;
										end = url.indexOf('/', start);
										if (end != -1 && end > start) {
											name = url.substring(start, end);
										}
									}
								}
								if (name.length() < 1) {
									name = url;
								}
								if (hdb.addSource(name, url, rowId) > 0) {
									new RefreshList().execute();
								} else {
									Toast.makeText(
											HostSourceList.this,
											getString(R.string.hosts_source_add_fail),
											Toast.LENGTH_SHORT).show();
								}
							}
						}).setNegativeButton(android.R.string.cancel, null)
				.create().show();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		if (cursor != null) {
			cursor.close();
		}
		if (progressDialog != null) {
			progressDialog.dismiss();
		}
		if (mPop != null) {
			mPop.dismiss();
		}
	}

	private final int ALERT_DIALOG_RESET_ETC = 1;
	private final int ALERT_DIALOG_REWRITE_HOST = 2;

	public void doPositiveClick(int type) {
		switch (type) {
		case ALERT_DIALOG_REWRITE_HOST:
			HostsDB.saveEtcHosts(this);
			break;
		case ALERT_DIALOG_RESET_ETC:
			hdb.resetEtcHosts();
			break;
		}
	}

	private void hosts_reset(final int type, int message) {
		new AlertDialog.Builder(this)
				.setMessage(message)
				.setPositiveButton(android.R.string.yes,
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog,
									int which) {
								doPositiveClick(type);
							}
						}).setNegativeButton(android.R.string.cancel, null)
				.show();
	}

	private class RefreshList extends AsyncTask<Void, Void, Cursor> {
		protected Cursor doInBackground(Void... params) {
			Cursor newCursor = hdb.getAllHostsSource();
			return newCursor;
		}

		protected void onPostExecute(Cursor newCursor) {
			adapter.changeCursor(newCursor);
			cursor.close();
			cursor = newCursor;
		}
	}

	private class CheckLocalHosts extends AsyncTask<Void, Integer, Void> {
		protected Void doInBackground(Void... params) {

			FileReader fr = null;
			BufferedReader buf = null;
			File file = null;

			try {
				file = new File("/system/etc/hosts");
				if (!file.exists()) {
					return null;
				}
				fr = new FileReader(file);
				buf = new BufferedReader(fr);
				String s = null;
				long len = file.length();
				long plen = 0;
				int last_progress = 0;
				int cur_progress = 0;
				while ((s = buf.readLine()) != null) {
					plen += s.length();
					s = s.trim();
					hdb.addHostLine(s, 0, 0);
					cur_progress = (int) ((plen / (float) len) * 100);
					if (cur_progress > last_progress) {
						publishProgress(cur_progress);
						last_progress = cur_progress;
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				try {
					buf.close();
					fr.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			return null;
		}

		protected void onProgressUpdate(Integer... progress) {
			progressDialog.setProgress(progress[0]);
		}

		protected void onPostExecute(Void result) {
			progressDialog.setProgress(0);
			progressDialog.dismiss();
		}
	}

	private class httpContentGet extends AsyncTask<String, Integer, String> {
		protected String doInBackground(String... urls) {
			int sid = 0;
			try {
				sid = Integer.parseInt(urls[1]);
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

                if (this.isCancelled()) {
                    return null;
                }
				if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
					BufferedReader rd = new BufferedReader(
							new InputStreamReader(response.getEntity()
									.getContent()));
					long len = response.getEntity().getContentLength();
					long plen = 0;
					String line;
					hdb.removeHostBySid(sid);
					int last_progress = 0;
					int cur_progress = 0;
					while ((line = rd.readLine()) != null) {
						plen += line.length();
						hdb.addHostLine(line, sid, 0);
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

        @Override
        protected void onCancelled() {
            super.onCancelled();
            progressDialog.dismiss();
        }

        protected void onPostExecute(String result) {
			progressDialog.dismiss();
			if (result != null) {
				Toast.makeText(HostSourceList.this,
						getString(R.string.fail) + ": " + result,
						Toast.LENGTH_SHORT).show();
			}
		}
	}

	private Handler mHandler = new Handler() {

		public void handleMessage(Message msg) {
			switch (msg.what) {
			case 1:
				try {
					LayoutInflater mLayoutInflater = (LayoutInflater) HostSourceList.this
							.getSystemService(LAYOUT_INFLATER_SERVICE);
					View popmenu = mLayoutInflater.inflate(
							R.layout.hosts_raw_editor, null);
					TextView tv = (TextView) popmenu
							.findViewById(R.id.hosts_raw_editor);
					tv.setText(R.string.first_run_localsource);
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
					ListView lv = getListView();
					mPop.showAtLocation(lv, Gravity.CENTER, 0, 0);
				} catch (Exception e) {
					e.printStackTrace();
				}
				HostsDB.first_run_hostsSource = false;
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
