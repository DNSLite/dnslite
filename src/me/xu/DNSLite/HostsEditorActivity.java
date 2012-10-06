package me.xu.DNSLite;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpGet;

import android.R.color;
import android.app.AlertDialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.database.Cursor;
import android.graphics.Rect;
import android.net.http.AndroidHttpClient;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.widget.SimpleCursorAdapter;
import android.support.v4.widget.SimpleCursorAdapter.ViewBinder;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout.LayoutParams;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.Toast;
import android.widget.ToggleButton;

public class HostsEditorActivity extends ListActivity {

	private PopupWindow mPop = null;
	private ProgressDialog progressDialog = null;
	private final String[] SCA_item = new String[] { "status", "domain", "ip" };
	private final int[] SCA_item_id = new int[] { R.id.status, R.id.domain,
			R.id.ip };

	private Cursor cursor = null;
	private HostsDB hdb = null;
	private SimpleCursorAdapter adapter = null;
	private long search_sid = -1;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.hosts_editor);
		hdb = HostsDB.GetInstance(this);
		try {
			search_sid = getIntent().getExtras().getLong("sid");
		} catch (Exception e) {
			search_sid = -1;
		}
		if (search_sid == -1) {
			cursor = hdb.getAllInUseHosts();
		} else {
			cursor = hdb.getAllHostsBySourceId(search_sid);
		}

		adapter = new SimpleCursorAdapter(this, R.layout.hosts_row, cursor,
				SCA_item, SCA_item_id, 0);

		ListView lv = getListView();
		lv.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
		lv.getEmptyView().setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {
				HostsEditorActivity.this.openOptionsMenu();
			}
		});
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
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.hosts_editor_option, menu);
		if (search_sid == -1) {
			menu.removeItem(R.id.menu_item_update);
			menu.removeItem(R.id.menu_item_merge);
			menu.removeItem(R.id.menu_item_empty);
			menu.removeItem(R.id.menu_item_disable);
		}
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Log.d(TAG, "search_sid:"+search_sid);
		switch (item.getItemId()) {
		case R.id.menu_item_add:
			addHosts(0, null, null);
			break;
		case R.id.menu_item_empty:
			hdb.removeHostBySid(search_sid);
			new RefreshList().execute();
			break;
		case R.id.menu_item_disable:
			hdb.disableHostInSource(search_sid);
			new RefreshList().execute();
			break;
		case R.id.menu_item_merge:
			if (search_sid == -1) {
				break;
			}
			hdb.mergeHostsSource(search_sid);
			new RefreshList().execute();
			break;
		case R.id.menu_item_update:
			if (search_sid == -1) {
				break;
			}
			if (search_sid == 0) {
				startProgressDialog("Import", "Import from /system/etc/hosts");
				new CheckLocalHosts().execute();
			} else {
				Cursor c = hdb.getHostsSource(search_sid);
				if (c != null && c.moveToFirst()) {
					String name = c.getString(c.getColumnIndex("name"));
					String url = c.getString(c.getColumnIndex("url"));
					c.close();
					c = null;
					startProgressDialog("Update " + name, "Load from " + url);
					new httpContentGet().execute(url,
							String.valueOf(search_sid));
				}
				if (c != null) {
					c.close();
				}
			}
			break;
		default:
			return super.onOptionsItemSelected(item);
		}
		return true;
	}

	private void startProgressDialog(String title, String message) {
		if (progressDialog == null) {
			progressDialog = new ProgressDialog(HostsEditorActivity.this);
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

	private void addHosts(final long _id, String domain, String ip) {
		LayoutInflater factory = LayoutInflater.from(HostsEditorActivity.this);
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

		new AlertDialog.Builder(HostsEditorActivity.this)
				.setTitle(R.string.hosts_host_text_entry)
				.setView(textEntryView)
				.setPositiveButton(android.R.string.ok,
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog,
									int whichButton) {
								/* User clicked OK so do some stuff */
								String domain = et_domain.getText().toString()
										.trim();
								String ip = et_ip.getText().toString().trim();
								if (domain.equals("") || ip.equals("")) {
									Toast.makeText(
											HostsEditorActivity.this,
											getString(R.string.hosts_host_add_input_error),
											Toast.LENGTH_SHORT).show();
									return;
								}
								if (hdb.newHost(_id, domain, ip, 0, 0, 1)) {
									new RefreshList().execute();
								} else {
									Toast.makeText(
											HostsEditorActivity.this,
											getString(R.string.hosts_host_add_fail),
											Toast.LENGTH_SHORT).show();
								}
							}
						}).setNegativeButton(android.R.string.cancel, null)
				.create().show();
	}

	private void enable(final long rowId) {
		if (hdb.enableHost(rowId)) {
			new RefreshList().execute();
		}
	}

	private void disable(final long rowId) {
		if (hdb.disableHost(rowId)) {
			new RefreshList().execute();
		}
	}

	private void comment(final long rowId) {
		if (hdb.commentHost(rowId)) {
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
		if (hdb.removeHost(rowId)) {
			new RefreshList().execute();
		}
	}

	private class RefreshList extends AsyncTask<Void, Void, Cursor> {
		protected Cursor doInBackground(Void... params) {
			if (search_sid == -1) {
				return hdb.getAllInUseHosts();
			} else {
				return hdb.getAllHostsBySourceId(search_sid);
			}
		}

		protected void onPostExecute(Cursor newCursor) {
			adapter.changeCursor(newCursor);
			cursor.close();
			cursor = newCursor;
		}
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
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
			case R.id.quick_actions_comment:
				comment(id);
				// Log.d(TAG, "comment id:" + id + " position:" + position);
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
			LayoutInflater mLayoutInflater = (LayoutInflater) HostsEditorActivity.this
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
			quick_actions_comment.setOnClickListener(onClickPopMenu);
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
		mPop.showAtLocation(m, Gravity.NO_GRAVITY, 0,
				anchorRect.bottom - m.getHeight());
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
			progressDialog.dismiss();
			new RefreshList().execute();
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
				if (!(schema.equals("http") || schema.equals("https") || schema.equals("ftp"))) {
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

		protected void onPostExecute(String result) {
			progressDialog.setProgress(0);
			progressDialog.dismiss();
			if (result != null) {
				Toast.makeText(HostsEditorActivity.this, getString(R.string.fail) +": " + result,
						Toast.LENGTH_SHORT).show();
			}
			new RefreshList().execute();
		}
	}
}
