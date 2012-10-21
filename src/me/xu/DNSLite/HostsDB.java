package me.xu.DNSLite;

import java.io.*;

import me.xu.tools.Sudo;
import me.xu.tools.util;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils.InsertHelper;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Environment;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class HostsDB extends SQLiteOpenHelper {

	public static boolean needRewriteHosts = false;
	private static HostsDB mInstance = null;
	private static SQLiteDatabase db = null;
	private static Context context = null;
	public static final String static_cache = "static_cache";
	public static boolean needRewriteDnsCache = false;

	private static final int DB_VERSION = 2;
	private static final String DB_NAME = "hosts.db";
	public static boolean first_run_hostsActivity = false;
	public static boolean first_run_hostsSource = false;
    public static final String DNSLITE_HOSTS_JSON = "dnslite_hosts.js";

    private HostsDB(Context context) {
		super(context, DB_NAME, null, DB_VERSION);
	}

	public static HostsDB GetInstance(Context context) {
		if (mInstance == null) {
			HostsDB.context = context.getApplicationContext();
			mInstance = new HostsDB(context.getApplicationContext());
			if (db == null) {
				db = mInstance.getWritableDatabase();
			}
		}
		return mInstance;
	}

	@Override
	public void onCreate(SQLiteDatabase db) {
		first_run_hostsActivity = true;
		first_run_hostsSource = true;
		HostsDB.db = db;
		db.execSQL("CREATE TABLE IF NOT EXISTS source(_id INTEGER PRIMARY KEY AUTOINCREMENT, name varchar(32) unique, url varchar(255));");
		// status :0 not use :1 in use :2 comment :3 not ip domain pair
		db.execSQL("CREATE TABLE IF NOT EXISTS hosts(_id INTEGER PRIMARY KEY AUTOINCREMENT, sid int KEY, status int, ip_group int, ip varchar(40) not null, domain varchar(65) not null, unique(sid,domain));");

		db.execSQL("CREATE TABLE IF NOT EXISTS dns_group(_id INTEGER PRIMARY KEY AUTOINCREMENT, name varchar(32) unique, url varchar(255));");
		// status :0 not use :1 in use
		db.execSQL("CREATE TABLE IF NOT EXISTS dns_hosts(_id INTEGER PRIMARY KEY AUTOINCREMENT, gid int KEY, status int, ip text not null, domain varchar(65) not null, unique(gid,domain));");
		initDnsDB();
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		HostsDB.db = db;
		// db.execSQL("DROP TABLE IF EXISTS source");
		// db.execSQL("DROP TABLE IF EXISTS hosts");
		db.execSQL("CREATE TABLE IF NOT EXISTS dns_group(_id INTEGER PRIMARY KEY AUTOINCREMENT, name varchar(32) unique, url varchar(255));");
		// status :0 not use :1 in use
		db.execSQL("CREATE TABLE IF NOT EXISTS dns_hosts(_id INTEGER PRIMARY KEY AUTOINCREMENT, gid int KEY, status int, ip text not null, domain varchar(65) not null, unique(gid,domain));");

		onCreate(db);
	}

	private void initDnsDB() {
		String initStr = HostsDB.context.getString(R.string.dns_init_db);
		if (initStr.length() < 1) {
			return;
		}
		String[] db_inits = initStr.split(";");
		if (db_inits == null) {
			return;
		}
		for (String inits : db_inits) {
			String[] infos = inits.split(":");
			if (infos == null || infos.length != 3) {
				continue;
			}
			String ip = infos[1];

			String[] domains = infos[2].split(",");
			if (domains == null || domains.length < 1) {
				continue;
			}

			long gid = addDnsGroup(infos[0], "", 0);
			if (gid == -1) {
				continue;
			}
			for (String domain : domains) {
				addDomainToGroup(domain, ip, gid, 0, 0);
			}
		}
	}

	public boolean import_dns_db(String filepath) {
		BufferedReader buf = null;
		try {
			File f = new File(Environment.getExternalStorageDirectory(),
					filepath);
			buf = new BufferedReader(new FileReader(f));
			String s = null;
			long cur_gid = 0;
			while ((s = buf.readLine()) != null) {
				s = s.trim();
				if (s.length() < 3) {
					continue;
				}
				int status = 0;
				if (s.charAt(0) == '#') {
					if (s.charAt(1) == '[') {
						if (s.endsWith("]")) {
							cur_gid = 0;
							String group = null;
							String url = null;
							String info = s.substring(2, s.length() - 1).trim();
							int n = info.lastIndexOf(' ');
							if (n > 0) {
								group = info.substring(0, n - 1);
								url = info.substring(n + 1);
							} else {
								group = info;
							}
							Cursor cursor = getDnsGroupByName(group);
							if (cursor != null) {
								if (cursor.moveToFirst()) {
									cur_gid = cursor.getInt(0);
								}
								cursor.close();
							}
							if (cur_gid == 0) {
								cur_gid = addDnsGroup(group, url, 0);
							}
						}
						continue;
					} else {
						status = 0;
					}
				} else {
					status = 1;
				}
				String ip = null;
				int i = 0;
				String[] info = s.split("\\s+");
				if (util.isInetAddress(info[0])) {
					ip = info[0];
					i = 1;
				}
				for (; i < info.length; ++i) {
					addDomainToGroup(info[i], ip, cur_gid, 0, status);
				}
			}
		} catch (FileNotFoundException e) {
			return false;
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		} finally {
			if (buf != null) {
				try {
					buf.close();
				} catch (IOException e) {
				}
			}
		}
		return true;
	}

	public boolean export_dns_db(String filepath) {
		PrintWriter pw = null;
		Cursor cgroup = getAllDnsGroup();
		if (cgroup == null || cgroup.getCount() < 1) {
			return false;
		}
		try {
			File f = new File(Environment.getExternalStorageDirectory(),
					filepath);
			pw = new PrintWriter(f);
			while (cgroup.moveToNext()) {
				String group = cgroup.getString(cgroup.getColumnIndex("name"));
				String url = cgroup.getString(cgroup.getColumnIndex("url"));
				if (url == null || url.length() < 1) {
					pw.println("#[" + group + "]");
				} else {
					pw.println("#[" + group + " " + url + "]");
				}
				Cursor c = getDnsHostsByGroup(cgroup.getLong(cgroup
						.getColumnIndex("_id")));
				if (c != null) {
					int statusIdx = c.getColumnIndex("status");
					int ipIdx = c.getColumnIndex("ip");
					int domainIdx = c.getColumnIndex("domain");
					while (c.moveToNext()) {
						int status = c.getInt(statusIdx);
						String ip = c.getString(ipIdx);
						if (ip == null) {
							ip = "";
						}
						if (status == 1) {
							pw.println(ip + " " + c.getString(domainIdx));
						} else {
							pw.println("#" + ip + " " + c.getString(domainIdx));
						}
					}
					c.close();
				}
				pw.println();
			}
		} catch (NullPointerException npe) {
			npe.printStackTrace();
			return false;
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			return false;
		} finally {
			if (pw != null) {
				pw.close();
			}
			if (cgroup != null) {
				cgroup.close();
			}
		}
		return true;
	}

    public long addDnsGroup(String name, String url, long _id) {
		if (name == null) {
			return 0;
		}
		name = name.replaceAll("\\s", "");
		if (name.length() < 1) {
			return 0;
		}
		ContentValues cv = new ContentValues();
		if (url != null) {
			url = url.replaceAll("\\s", "");
			cv.put("url", url);
		}
		cv.put("name", name);
		if (_id > 0) {
			return db.update("dns_group", cv, "_id=" + _id, null);
		}
		return db.insertWithOnConflict("dns_group", null, cv,
				SQLiteDatabase.CONFLICT_IGNORE);
	}

	public boolean delDnsGroup(long _id) {
		if (db.delete("dns_group", "_id=" + _id, null) > 0) {
			if (db.delete("dns_hosts", "gid=" + _id, null) > 0) {
				needRewriteDnsCache = true;
			}
			return true;
		}
		return false;
	}

	public int delDnsHostsByGroup(long _id) {
		needRewriteDnsCache = true;
		return db.delete("dns_hosts", "gid=" + _id, null);
	}

	public long updateAllIpInDnsGroup(long gid, String ip) {
		ContentValues cv = new ContentValues();
		cv.put("ip", ip);
		needRewriteDnsCache = true;
		return db.update("dns_hosts", cv, "gid=" + gid, null);
	}

	public long addDomainToGroup(String domain, String ip, long gid, long _id,
			int status) {
		domain = domain.replaceAll("\\s", "");
		if (domain.length() < 1) {
			return 0;
		}
		ContentValues cv = new ContentValues();
		if (ip != null) {
			ip = ip.replaceAll("\\s", "");
			cv.put("ip", ip);
		}
		cv.put("domain", domain);
		cv.put("gid", gid);
		cv.put("status", String.valueOf(status));
		if (status == 1) {
			needRewriteDnsCache = true;
		}
		if (_id > 0) {
			return db.update("dns_hosts", cv, "_id=" + _id, null);
		}
		return db.insertWithOnConflict("dns_hosts", null, cv,
				SQLiteDatabase.CONFLICT_IGNORE);
	}

	public long updateDomainById(String domain, String ip, long id) {
		if (domain == null && ip == null) {
			return 0;
		}
		ContentValues cv = new ContentValues();
		if (domain != null) {
			domain = domain.replaceAll("\\s", "");
			if (domain.length() < 1) {
				return 0;
			}
			cv.put("domain", domain);
		}
		if (ip != null) {
			ip = ip.replaceAll("\\s", "");
			cv.put("ip", ip);
		}
		needRewriteDnsCache = true;
		return db.update("dns_hosts", cv, "_id=" + id, null);
	}

	public boolean enableDnsHostById(long _id) {
		Cursor cursor = db.rawQuery("SELECT status from dns_hosts where _id="
				+ _id, null);
		int status = 0;
		if (cursor.moveToFirst()) {
			status = cursor.getInt(0);
		}
		cursor.close();
		if (status == 1) {
			return false;
		}

		ContentValues cv = new ContentValues();
		cv.put("status", "1");
		if (db.update("dns_hosts", cv, "_id=" + _id, null) > 0) {
			needRewriteDnsCache = true;
			return true;
		}
		return false;
	}

	public boolean disableDnsHostById(long _id) {
		Cursor cursor = db.rawQuery("SELECT status from dns_hosts where _id="
				+ _id, null);
		int status = 0;
		if (cursor.moveToFirst()) {
			status = cursor.getInt(0);
		}
		cursor.close();
		if (status == 0) {
			return false;
		}

		ContentValues cv = new ContentValues();
		cv.put("status", "0");
		if (db.update("dns_hosts", cv, "_id=" + _id, null) > 0) {
			needRewriteDnsCache = true;
			return true;
		}
		return false;
	}

	public boolean removeDnsHost(long _id) {
		if (db.delete("dns_hosts", "_id=" + _id, null) > 0) {
			needRewriteDnsCache = true;
			return true;
		}
		return false;
	}

	public int disableAllInGroup(long gid) {
		ContentValues cv = new ContentValues();
		cv.put("status", "0");
		needRewriteDnsCache = true;
		return db.update("dns_hosts", cv, "gid=" + gid, null);
	}

	public int enableAllInGroup(long gid) {
		ContentValues cv = new ContentValues();
		cv.put("status", "1");
		needRewriteDnsCache = true;
		return db.update("dns_hosts", cv, "gid=" + gid, null);
	}

	public Cursor getAllDnsGroup() {
		return db.rawQuery("SELECT _id,name,url from dns_group", null);
	}

	public Cursor getDnsGroup(long _id) {
		return db.rawQuery("SELECT name,url from dns_group where _id=" + _id,
				null);
	}

	public Cursor getDnsGroupByName(String name) {
		return db.rawQuery("SELECT _id from dns_group where name=? limit 1",
				new String[] { name });
	}

	public Cursor getDnsHostsByGroup(long gid) {
		if (gid > 0) {
			return db
					.rawQuery("SELECT * from dns_hosts where gid=" + gid, null);
		} else {
			return db.rawQuery("SELECT * from dns_hosts", null);
		}
	}

	public int addDnsHostLine(String line, int gid) {
		int len = line.length();
		if (len < 9) {
			return -1;
		}
		if (line.charAt(0) == '#') {
			return 0;
		}
		String status = "1";
		String[] lines = line.split("\\s+");
		if (lines.length < 2) {
			return -2;
		}

		if (!util.isInetAddress(lines[0])) {
			return -3;
		}

		String str_gid = String.valueOf(gid);
		InsertHelper ih = new InsertHelper(db, "dns_hosts");
		final int domainColumn = ih.getColumnIndex("domain");
		final int ipColumn = ih.getColumnIndex("ip");
		final int gidColumn = ih.getColumnIndex("gid");
		final int statusColumn = ih.getColumnIndex("status");
		try {
			int i;
			for (i = 1; i < lines.length; ++i) {
				lines[i] = lines[i].trim();
				if (lines[i].length() < 1) {
					continue;
				}
				if (lines[i].charAt(0) == '#') {
					break;
				}
				ih.prepareForReplace();
				ih.bind(domainColumn, lines[i]);
				ih.bind(ipColumn, lines[0]);
				ih.bind(gidColumn, str_gid);
				ih.bind(statusColumn, status);
				ih.execute();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		needRewriteDnsCache = true;
		return lines.length - 1;
	}

	public void writeDnsCacheFile() {
		needRewriteDnsCache = false;
		Cursor c = db.rawQuery(
				"SELECT ip,domain from dns_hosts where status=1", null);
		if (c != null) {
			FileOutputStream outStream = null;
			try {
				outStream = context.openFileOutput(static_cache,
						Context.MODE_PRIVATE);
				while (c.moveToNext()) {
					String ip = c.getString(0);
					if (ip == null || ip.length() < 1) {
						continue;
					}
					String domain = c.getString(1);
					if (domain == null || domain.length() < 1) {
						continue;
					}
					String line = ip + " " + domain + "\n";
					outStream.write(line.getBytes());
				}
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			} finally {
				if (outStream != null) {
					try {
						outStream.close();
					} catch (IOException e) {
					}
				}
			}
			c.close();
		}
	}

	public Cursor getValidDnsHost() {
		return db.rawQuery("SELECT ip,domain from dns_hosts where status=1",
				null);
	}

	/* DNS Hosts */

	/* hosts db */

    public boolean import_hosts_db(String file_path) {
        try {
            String import_json = file_get_content_sd_card(file_path);
            JSONObject import_obj = new JSONObject(import_json);
            JSONArray sources = import_obj.optJSONArray("host_sources");
            int size = sources.length();
            for (int index=0; index<size; index++) {
                JSONObject source = sources.optJSONObject(index);
                String name = source.optString("name");
                String url = source.optString("url");
                long source_id = 0;
                if (!name.equals("Local")) {
                    source_id = addSource(name, url, 0);
                }
                JSONArray hosts = source.optJSONArray("hosts");
                int hosts_size = hosts.length();
                for (int host_index=0; host_index<hosts_size; host_index++) {
                    JSONObject host = hosts.optJSONObject(host_index);
                    addHost(0, host.optString("domain"), host.optString("ip"),
                            (int)source_id, host.optInt("ip_group"), host.optInt("status"));
                }
            }
        } catch (FileNotFoundException e) {
            return false;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        } catch (JSONException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    private static String file_get_content_sd_card(String file_path) throws IOException {
        File file = new File(Environment.getExternalStorageDirectory(),
                file_path);
        InputStream inputStream = new FileInputStream(file);
        byte bytes[] = new byte[(int) file.length()];
        inputStream.read(bytes);
        inputStream.close();
        return new String(bytes);
    }

    public boolean export_hosts_db(String file_path) {
        Cursor hosts_group = getAllHostsSource();
        if (null == hosts_group) {
            return false;
        }
        try {
            JSONObject export = new JSONObject();
            JSONArray sources = new JSONArray();
            export.put("version", DB_VERSION);
            int _id_index = hosts_group.getColumnIndex("_id");
            int name_index = hosts_group.getColumnIndex("name");
            int url_index = hosts_group.getColumnIndex("url");
            while (hosts_group.moveToNext()) {
                String name = hosts_group.getString(name_index);
                String url = hosts_group.getString(url_index);

                JSONObject source = new JSONObject();
                source.put("name", name);
                if (!(url == null || url.length() < 1)) {
                    source.put("url", url);
                }

                Cursor c = getAllHostsBySourceId(hosts_group.getLong(_id_index));
                if (null == c) {
                    continue;
                }
                JSONArray hosts = new JSONArray();
                int status_index = c.getColumnIndex("status");
                int ip_index = c.getColumnIndex("ip");
                int domain_index = c.getColumnIndex("domain");
                int ip_group_index = c.getColumnIndex("ip_group");
                while (c.moveToNext()) {
                    JSONObject host = new JSONObject();
                    host.put("status", c.getInt(status_index));
                    host.put("ip", c.getString(ip_index));
                    host.put("domain", c.getString(domain_index));
                    host.put("ip_group", c.getString(ip_group_index));
                    hosts.put(host);
                }
                c.close();
                source.put("hosts", hosts);
                sources.put(source);
            }
            export.put("host_sources", sources);
            file_puts_content_sd_card(file_path, export.toString(1));
        } catch (JSONException e) {
            e.printStackTrace();
            return false;
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (hosts_group != null) {
                hosts_group.close();
            }
        }
        return true;
    }

    private static void file_puts_content_sd_card(String file_path, String export) throws IOException, JSONException {
        File file = new File(Environment.getExternalStorageDirectory(),
                file_path);
        FileOutputStream file_output = new FileOutputStream(file);
        file_output.write(export.getBytes());
        file_output.close();
    }

    public static void saved() {
		needRewriteHosts = false;
	}

	public long addHost(long _id, String domain, String ip, int sid,
			int ip_group, int status) {
		domain = domain.replaceAll("\\s", "");
		ip = ip.replaceAll("\\s", "");
		if (domain.length() < 1 || ip.length() < 1) {
			return 0;
		}
		ContentValues cv = new ContentValues();
		cv.put("domain", domain);
		cv.put("ip", ip);
		if (_id > 0) {
			return db.update("hosts", cv, "_id=" + _id, null);
		}
		cv.put("sid", String.valueOf(sid));
		cv.put("ip_group", String.valueOf(ip_group));
		cv.put("status", String.valueOf(status));
		return db.insertWithOnConflict("hosts", null, cv,
				SQLiteDatabase.CONFLICT_IGNORE);
	}

	public int addHostLine(String line, int sid, int ip_group) {
		int len = line.length();
		if (len < 9) {
			return -1;
		}
		if (line.charAt(0) == '#') {
			return 0;
		}
		String status = "1";
		if (sid != 0) {
			status = "0";
		}
		String[] lines = line.split("\\s+");
		if (lines.length < 2) {
			return -2;
		}

		if (!util.isInetAddress(lines[0])) {
			return -3;
		}

		String str_ip_group = String.valueOf(ip_group);
		String str_sid = String.valueOf(sid);
		InsertHelper ih = new InsertHelper(db, "hosts");
		final int domainColumn = ih.getColumnIndex("domain");
		final int ipColumn = ih.getColumnIndex("ip");
		final int sidColumn = ih.getColumnIndex("sid");
		final int ip_groupColumn = ih.getColumnIndex("ip_group");
		final int statusColumn = ih.getColumnIndex("status");
		try {
			int i;
			for (i = 1; i < lines.length; ++i) {
				lines[i] = lines[i].trim();
				if (lines[i].length() < 1) {
					continue;
				}
				if (lines[i].charAt(0) == '#') {
					break;
				}
				ih.prepareForReplace();
				ih.bind(domainColumn, lines[i]);
				ih.bind(ipColumn, lines[0]);
				ih.bind(sidColumn, str_sid);
				ih.bind(ip_groupColumn, str_ip_group);
				ih.bind(statusColumn, status);
				ih.execute();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		return lines.length - 1;
	}

	public boolean NeedRewriteHosts() {
		return needRewriteHosts;
	}

	public void resetEtcHosts() {
		db.execSQL("update hosts set status=0 where status=1;");
		needRewriteHosts = true;
	}

	public void resetHostsDB() {
		db.execSQL("delete from source;");
		db.execSQL("delete from hosts;");
		needRewriteHosts = true;
	}

	public boolean removeHostsSource(long _id) {
		if (db.delete("source", "_id=" + _id, null) > 0) {
			if (db.delete("hosts", "sid=" + _id, null) > 0) {
				needRewriteHosts = true;
			}
			return true;
		}
		return false;
	}

	public void replaceHostsSource(long source_id) {
		db.execSQL("update hosts set status=0 where status=1;");
		db.execSQL("update hosts set status=1 where sid=" + source_id
				+ " and status=0;");
		needRewriteHosts = true;
	}

	public void mergeHostsSource(long source_id) {
		db.execSQL("update hosts set status=1 where sid=" + source_id
				+ " and status=0;");
		needRewriteHosts = true;
	}

	public boolean enableHost(long _id) {
		Cursor cursor = db.rawQuery(
				"SELECT status from hosts where _id=" + _id, null);
		int status = 0;
		if (cursor.moveToFirst()) {
			status = cursor.getInt(0);
		}
		cursor.close();
		ContentValues cv = new ContentValues();
		cv.put("status", "1");
		if (db.update("hosts", cv, "_id=" + _id, null) > 0) {
			if (status != 1) {
				needRewriteHosts = true;
			}
			return true;
		}
		return false;
	}

	public boolean disableHost(long _id) {
		Cursor cursor = db.rawQuery(
				"SELECT status from hosts where _id=" + _id, null);
		int status = 0;
		if (cursor.moveToFirst()) {
			status = cursor.getInt(0);
		}
		cursor.close();
		ContentValues cv = new ContentValues();
		cv.put("status", "0");
		if (db.update("hosts", cv, "_id=" + _id, null) > 0) {
			if (status == 1) {
				needRewriteHosts = true;
			}
			return true;
		}
		return false;
	}

	public void disableHostInSource(long sid) {
		Cursor cursor = db.rawQuery(
				"SELECT count(*) from hosts where status=1 and sid=" + sid,
				null);
		int count = 0;
		if (cursor.moveToFirst()) {
			count = cursor.getInt(0);
		}
		cursor.close();
		ContentValues cv = new ContentValues();
		cv.put("status", "0");
		if (db.update("hosts", cv, "status=1 and sid=" + sid, null) > 0) {
			if (count > 0) {
				needRewriteHosts = true;
			}
		}
	}

	public boolean newHost(long _id, String domain, String ip, int sid,
			int ip_group, int status) {
		long rv = addHost(_id, domain, ip, sid, ip_group, status);
		if (rv > 0) {
			if (status == 1) {
				needRewriteHosts = true;
			}
			return true;
		}
		return false;
	}

	public boolean commentHost(long _id) {
		Cursor cursor = db.rawQuery(
				"SELECT status from hosts where _id=" + _id, null);
		int status = 0;
		if (cursor.moveToFirst()) {
			status = cursor.getInt(0);
		}
		cursor.close();
		ContentValues cv = new ContentValues();
		cv.put("status", "2");
		if (db.update("hosts", cv, "_id=" + _id, null) > 0) {
			if (status == 1) {
				needRewriteHosts = true;
			}
			return true;
		}
		return false;
	}

	public boolean removeHost(long _id) {
		Cursor cursor = db.rawQuery(
				"SELECT status from hosts where _id=" + _id, null);
		int status = 0;
		if (cursor.moveToFirst()) {
			status = cursor.getInt(0);
		}
		cursor.close();
		if (db.delete("hosts", "_id=" + _id, null) > 0) {
			if (status == 1) {
				needRewriteHosts = true;
			}
			return true;
		}
		return false;
	}

	public boolean removeHostBySid(long sid) {
		if (db.delete("hosts", "sid=" + sid, null) > 0) {
			needRewriteHosts = true;
			return true;
		}
		return false;
	}

	public long addSource(String name, String url, long _id) {
		name = name.replaceAll("\\s", "");
		url = url.replaceAll("\\s", "");
		if (name.length() < 1 || url.length() < 1) {
			return 0;
		}
		ContentValues cv = new ContentValues();
		cv.put("name", name);
		cv.put("url", url);
		if (_id > 0) {
			return db.update("source", cv, "_id=" + _id, null);
		}
		return db.insertWithOnConflict("source", null, cv,
				SQLiteDatabase.CONFLICT_IGNORE);
	}

	public Cursor getAllHostsSource() {
		return db
				.rawQuery(
						"SELECT _id,name,url from source union select 0,'Local','Manual Input'",
						null);
	}

	public Cursor getHostsSource(long _id) {
		return db
				.rawQuery("SELECT name,url from source where _id=" + _id, null);
	}

	public Cursor getAllHosts() {
		return db
				.rawQuery(
						"SELECT _id,sid,status,ip_group,ip,domain from hosts union select 0,0,1,0,'127.0.0.1','localhost'",
						null);
	}

	public Cursor getDistinctInUseHosts() {
		return db
				.rawQuery(
						"select * from (SELECT ip,domain from hosts where status=1 group by domain union select '127.0.0.1','localhost') group by domain",
						null);
	}

	public Cursor getAllInUseHosts() {
		Cursor c = db
				.rawQuery(
						"SELECT count(_id) from hosts where status=1 and ip='127.0.0.1' and domain='localhost'",
						null);
		boolean hav = false;
		if (c != null) {
			if (c.moveToFirst()) {
				if (c.getInt(0) > 0) {
					hav = true;
				}
			}
			c.close();
		}
		if (hav) {
			return db
					.rawQuery(
							"SELECT _id,sid,status,ip_group,ip,domain from hosts where status=1",
							null);
		} else {
			return db
					.rawQuery(
							"SELECT _id,sid,status,ip_group,ip,domain from hosts where status=1 union select 0,0,1,0,'127.0.0.1','localhost'",
							null);
		}
	}

	public Cursor getAllHostsBySourceId(long sid) {
		String sql = "SELECT * from hosts where sid=" + sid;
		Cursor cursor = db.rawQuery(sql, null);
		return cursor;
	}

	public Cursor getAllHostsByIp(String str) {
		String sql = "SELECT * from hosts where ip=?";
		return db.rawQuery(sql, new String[] { str });
	}

	public Cursor getAllHostsByDomain(String str) {
		String sql = "SELECT * from hosts where domain like ? or domain like ?";
		return db.rawQuery(sql, new String[] { str + "%", "%" + str });
	}

	public static void saveEtcHosts(Context mContext) {

		Sudo sudo = new Sudo();
		if (!sudo.prepareSuProc()) {
			Toast.makeText(mContext, mContext.getString(R.string.Status_SUFAIL),
					Toast.LENGTH_SHORT).show();
			sudo.close();
			return;
		}

		if (!sudo.mountRw("/system")) {
			Toast.makeText(mContext, "RE-MOUNT fail", Toast.LENGTH_SHORT)
					.show();
			sudo.close();
			return;
		}

		Cursor curs = null;
		try {
			HostsDB hdb = HostsDB.GetInstance(mContext);
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
				sudo.writeBytes("echo '" + line + "' >> /system/etc/hosts\n");
			}
			sudo.remountRo();
			sudo.writeBytes("exit\n");
			HostsDB.saved();
			if (curs != null) {
				curs.close();
				curs = null;
			}
			Toast.makeText(mContext, "Save Success!", Toast.LENGTH_SHORT)
					.show();
		} catch (IOException e) {
			Toast.makeText(mContext, "Save error, " + e.getMessage(),
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

	private void writeHostsCacheFile() {
		needRewriteHosts = false;
		Cursor c = getDistinctInUseHosts();
		if (c != null) {
			FileOutputStream outStream = null;
			try {
				outStream = context.openFileOutput("hosts",
						Context.MODE_PRIVATE);
				while (c.moveToNext()) {
					String ip = c.getString(0);
					if (ip == null || ip.length() < 1) {
						continue;
					}
					String domain = c.getString(1);
					if (domain == null || domain.length() < 1) {
						continue;
					}
					String line = ip + " " + domain + "\n";
					outStream.write(line.getBytes());
				}
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			} finally {
				if (outStream != null) {
					try {
						outStream.close();
					} catch (IOException e) {
					}
				}
			}
			c.close();
		}
	}
}
