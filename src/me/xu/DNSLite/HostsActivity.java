package me.xu.DNSLite;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout.LayoutParams;
import android.widget.PopupWindow;
import android.widget.TextView;

public class HostsActivity extends FragmentActivity {

	public static final String TAG = "HostsA";

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		FragmentManager fm = getSupportFragmentManager();
		if (fm.findFragmentById(android.R.id.content) == null) {
			HostsFragment dnsfrag = new HostsFragment();
			fm.beginTransaction().add(android.R.id.content, dnsfrag).commit();
		}
	}

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

	public static class HostsFragment extends Fragment implements
			OnClickListener {
		private static final String TAG = "HostsFragment";
		private PopupWindow mPop = null;
		private Button btn_hosts_source_manage = null;
		private HostsDB hdb = null;

		@Override
		public void setUserVisibleHint(boolean isVisibleToUser) {
			super.setUserVisibleHint(isVisibleToUser);
			if (this.isVisible()) {
				if (isVisibleToUser) {
					onPageVisible();
				}
			}
		}

		@Override
		public View onCreateView(LayoutInflater inflater, ViewGroup container,
				Bundle savedInstanceState) {
			View view = inflater.inflate(R.layout.hosts, container, false);

			btn_hosts_source_manage = (Button) view
					.findViewById(R.id.btn_hosts_source_manage);
			btn_hosts_source_manage.setOnClickListener(this);

			Button btn_hosts_apply = (Button) view
					.findViewById(R.id.btn_hosts_apply);
			btn_hosts_apply.setOnClickListener(this);
			Button btn_hosts_resetEtc = (Button) view
					.findViewById(R.id.btn_hosts_resetEtc);
			btn_hosts_resetEtc.setOnClickListener(this);

			Button btn_hosts_edit = (Button) view
					.findViewById(R.id.btn_hosts_edit);
			btn_hosts_edit.setOnClickListener(this);
			Button btn_hosts_rawview = (Button) view
					.findViewById(R.id.btn_hosts_rawview);
			btn_hosts_rawview.setOnClickListener(this);

			return view;
		}

		@Override
		public void onCreate(Bundle savedInstanceState) {
			super.onCreate(savedInstanceState);
			HostsDB.GetInstance(getActivity().getApplicationContext());
		}

		public void onPageVisible() {
			if (HostsDB.first_run_hostsActivity) {
				showPopupInfo(btn_hosts_source_manage);
				HostsDB.first_run_hostsActivity = false;
			}
		}

		private final int ALERT_DIALOG_RESET_ETC = 1;
		private final int ALERT_DIALOG_REWRITE_HOST = 2;

		@Override
		public void onClick(View v) {
			switch (v.getId()) {
			case R.id.btn_hosts_edit:
				startActivity(new Intent(getActivity().getApplicationContext(),
						HostsEditorActivity.class));
				break;
			case R.id.btn_hosts_rawview:
				startActivity(new Intent(getActivity().getApplicationContext(),
						HostsRawEditorActivity.class));
				break;
			case R.id.btn_hosts_source_manage:
				startActivity(new Intent(getActivity().getApplicationContext(),
						HostSourceList.class));
				break;
			case R.id.btn_hosts_apply:
				HostsDB.saveEtcHosts(getActivity().getApplicationContext());
				break;
			case R.id.btn_hosts_resetEtc:

				DialogFragment newFragment = MyAlertDialogFragment.newInstance(
						ALERT_DIALOG_RESET_ETC,
						R.string.host_reset_etc_alert_msg);
				newFragment.setTargetFragment(this, 0);
				newFragment.show(getActivity().getSupportFragmentManager(),
						"dialog");
				break;
			default:
				break;
			}
		}

		@Override
		public void onDestroy() {
			super.onDestroy();
			if (mPop != null) {
				mPop.dismiss();
			}
		}

		public void doPositiveClick(int type) {
			switch (type) {
			case ALERT_DIALOG_REWRITE_HOST:
				HostsDB.saveEtcHosts(getActivity().getApplicationContext());
				break;
			case ALERT_DIALOG_RESET_ETC:
				hdb.resetEtcHosts();
				break;
			}
		}

		public static class MyAlertDialogFragment extends DialogFragment {

			public static MyAlertDialogFragment newInstance(int type,
					int message) {
				MyAlertDialogFragment frag = new MyAlertDialogFragment();
				Bundle args = new Bundle();
				args.putInt("type", type);
				args.putInt("message", message);
				frag.setArguments(args);
				return frag;
			}

			@Override
			public Dialog onCreateDialog(Bundle savedInstanceState) {
				final int type = getArguments().getInt("type");
				int message = getArguments().getInt("message");

				return new AlertDialog.Builder(getActivity())
						.setMessage(message)
						.setPositiveButton(android.R.string.yes,
								new DialogInterface.OnClickListener() {
									public void onClick(DialogInterface dialog,
											int whichButton) {
										((HostsFragment) getTargetFragment())
												.doPositiveClick(type);
									}
								}).setNegativeButton(android.R.string.no, null)
						.create();
			}
		}

		public void showPopupInfo(View anchor) {
			try {
				LayoutInflater mLayoutInflater = (LayoutInflater) getActivity()
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
				mPop.showAsDropDown(anchor, 0, -15);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
}
