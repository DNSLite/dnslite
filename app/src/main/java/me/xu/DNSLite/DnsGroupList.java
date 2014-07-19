package me.xu.DNSLite;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Locale;

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
import android.support.v4.widget.SimpleCursorAdapter;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

public class DnsGroupList extends ListActivity {

	private Cursor cursor = null;
	private HostsDB hdb = null;
	private SimpleCursorAdapter adapter = null;
	private ProgressDialog progressDialog = null;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.dns_group);

		hdb = HostsDB.GetInstance(this);
		cursor = hdb.getAllDnsGroup();

		adapter = new SimpleCursorAdapter(this, R.layout.host_source_row,
				cursor, new String[] { "name", "url" }, new int[] {
						R.id.text1, R.id.text2 }, 0);
		setListAdapter(adapter);

		ListView lv = getListView();
		registerForContextMenu(lv);
		lv.getEmptyView().setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {
				DnsGroupList.this.openOptionsMenu();
			}
		});
	}

	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		view(id);
		super.onListItemClick(l, v, position, id);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.dns_group_option, menu);
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.menu_dns_group_add:
			addDnsGroup(0, null, null);
			break;
		case R.id.menu_dns_import:
			new AlertDialog.Builder(this)
			.setMessage(R.string.dns_import_desc)
			.setPositiveButton(android.R.string.yes,
					new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog,
								int which) {
							boolean status = hdb.import_dns_db(hdb.DNSLITE_JSON);
							new RefreshList().execute();
							Toast.makeText(
									DnsGroupList.this,
									getString(status ? R.string.dns_import_succ
											: R.string.dns_import_fail), Toast.LENGTH_SHORT)
									.show();
						}
					}).setNegativeButton(android.R.string.cancel, null)
			.show();
			break;
		case R.id.menu_dns_export:
			new AlertDialog.Builder(this)
			.setMessage(R.string.export_desc)
			.setPositiveButton(android.R.string.yes,
					new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog,
								int which) {
							boolean status = hdb.export_db(hdb.DNSLITE_JSON);
							Toast.makeText(
									DnsGroupList.this,
									getString(status ? R.string.dns_export_succ
											: R.string.dns_export_fail), Toast.LENGTH_SHORT)
									.show();
						}
					}).setNegativeButton(android.R.string.cancel, null)
			.show();
			break;
		default:
			break;
		}
		return super.onOptionsItemSelected(item);
	}

	public void onCreateContextMenu(ContextMenu menu, View v,
			ContextMenuInfo menuInfo) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.dns_group_context, menu);
		super.onCreateContextMenu(menu, v, menuInfo);
	}

	public boolean onContextItemSelected(MenuItem item) {
		AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item
				.getMenuInfo();
		switch (item.getItemId()) {
		case R.id.menu_dns_update: {
			TextView tv1 = (TextView) info.targetView.findViewById(R.id.text1);
			String name = tv1.getText().toString();
			TextView tv2 = (TextView) info.targetView.findViewById(R.id.text2);
			String url = tv2.getText().toString();
			update(info.id, name, url);
		}
			break;
		case R.id.menu_dns_edit: {
			TextView tv1 = (TextView) info.targetView.findViewById(R.id.text1);
			String name = tv1.getText().toString();
			TextView tv2 = (TextView) info.targetView.findViewById(R.id.text2);
			String url = tv2.getText().toString();
			addDnsGroup(info.id, name, url);
		}
			break;
		case R.id.menu_dns_view:
			view(info.id);
			break;
		case R.id.menu_dns_disable:
			hdb.disableAllInGroup(info.id);
			break;
		case R.id.menu_dns_enableAll:
			hdb.enableAllInGroup(info.id);
			break;
		case R.id.menu_dns_delete:
			delete(info.id);
			break;
		case R.id.menu_dns_empty:
			hdb.delDnsHostsByGroup(info.id);
			break;
		default:
			break;
		}
		return super.onContextItemSelected(item);
	}

	private void startProgressDialog(String title, String message) {
		if (progressDialog == null) {
			progressDialog = new ProgressDialog(DnsGroupList.this);
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

	private void update(final long rowId, String name, String url) {
		startProgressDialog("Sync " + name, "Load from " + url);
		new httpContentGet().execute(url, String.valueOf(rowId));
	}

	private void view(final long rowId) {
		Intent v = new Intent(DnsGroupList.this, DnsHostsActivity.class);
		v.putExtra("gid", rowId);
		startActivity(v);
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
		if (hdb.delDnsGroup(rowId)) {
			new RefreshList().execute();
		}
	}

	private void addDnsGroup(final long rowId, String name, String url) {
		LayoutInflater factory = LayoutInflater.from(DnsGroupList.this);
		final View textEntryView = factory.inflate(R.layout.hosts_source_add,
				null);
		final EditText et_url = (EditText) textEntryView
				.findViewById(R.id.url_edit);
		et_url.setHint(R.string.dns_group_add_url_hit);
		if (name != null) {
			EditText et_name = (EditText) textEntryView
					.findViewById(R.id.name_edit);
			et_name.setText(name);
		}
		if (url != null) {
			et_url.setText(url);
		}
		new AlertDialog.Builder(DnsGroupList.this)
				.setMessage(R.string.dns_group_text_entry)
				.setView(textEntryView)
				.setPositiveButton(android.R.string.ok,
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog,
									int whichButton) {
								/* User clicked OK so do some stuff */
								EditText et_name = (EditText) textEntryView
										.findViewById(R.id.name_edit);
								String name = et_name.getText().toString()
										.trim();
								String url = et_url.getText().toString().trim();
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
									Toast.makeText(
											DnsGroupList.this,
											getString(R.string.dns_group_add_input_error),
											Toast.LENGTH_SHORT).show();
									return;
								}
								if (hdb.addDnsGroup(name, url, rowId) > 0) {
									new RefreshList().execute();
								} else {
									Toast.makeText(
											DnsGroupList.this,
											getString(R.string.dns_group_add_fail),
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
	}

	private class RefreshList extends AsyncTask<Void, Void, Cursor> {
		protected Cursor doInBackground(Void... params) {
			return hdb.getAllDnsGroup();
		}

		protected void onPostExecute(Cursor newCursor) {
			adapter.changeCursor(newCursor);
			cursor.close();
			cursor = newCursor;
		}
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
				String schema = urls[0].substring(0, pos).toLowerCase(Locale.US);
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
				Toast.makeText(DnsGroupList.this, getString(R.string.fail) +": " + result,
						Toast.LENGTH_SHORT).show();
			}
		}
	}

}
