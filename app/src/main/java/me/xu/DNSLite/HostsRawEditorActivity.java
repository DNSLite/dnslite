package me.xu.DNSLite;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;

import me.xu.tools.Sudo;

import android.os.Bundle;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.view.KeyEvent;
import android.widget.TextView;

public class HostsRawEditorActivity extends Activity {
	private String ori_str;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.hosts_raw_editor);
		loadHosts();
	}

	private static String readFile(String path) throws IOException {
		FileInputStream stream = new FileInputStream(new File(path));
		try {
			FileChannel fc = stream.getChannel();
			MappedByteBuffer bb = fc.map(FileChannel.MapMode.READ_ONLY, 0,
					fc.size());
			/* Instead of using default, pass in a decoder. */
			return Charset.defaultCharset().decode(bb).toString();
		} finally {
			stream.close();
		}
	}

	public void loadHosts() {
		try {
			String str = readFile("/etc/hosts");
			TextView tv = (TextView) findViewById(R.id.hosts_raw_editor);
			ori_str = str;
			tv.setText(str);
		} catch (Exception ex) {
		}
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
			TextView tv = (TextView) findViewById(R.id.hosts_raw_editor);
			String obj_str = tv.getText().toString();
			if (ori_str.compareTo(obj_str) != 0) {
				ori_str = obj_str;
				new AlertDialog.Builder(this)
						.setMessage("Save /etc/hosts?")
						.setPositiveButton(android.R.string.yes,
								new DialogInterface.OnClickListener() {
									public void onClick(DialogInterface dialog,
											int whichButton) {
										Sudo.suSaveHosts(ori_str);
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

				return true;
			} else {
				finish();
			}
		}
		return super.onKeyDown(keyCode, event);
	}
}
