package me.xu.DNSLite;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.*;
import android.widget.LinearLayout.LayoutParams;

import java.io.File;

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

            Button btn_hosts_export = (Button) view
                    .findViewById(R.id.btn_hosts_export);
            btn_hosts_export.setOnClickListener(this);
            Button btn_hosts_import = (Button) view
                    .findViewById(R.id.btn_hosts_import);
            btn_hosts_import.setOnClickListener(this);
            Button btn_hosts_share = (Button) view
                    .findViewById(R.id.btn_share);
            btn_hosts_share.setOnClickListener(this);

            hdb = HostsDB.GetInstance(getActivity().getApplicationContext());
            return view;
        }

        public void onPageVisible() {
            if (HostsDB.first_run_hostsActivity) {
                showPopupInfo(btn_hosts_source_manage);
                HostsDB.first_run_hostsActivity = false;
            }
        }

        @Override
        public void onClick(View v) {
            switch (v.getId()) {
            case R.id.btn_hosts_source_manage:
                startActivity(new Intent(getActivity().getApplicationContext(),
                        HostSourceList.class));
                break;
            case R.id.btn_hosts_apply:
                HostsDB.saveEtcHosts(getActivity().getApplicationContext());
                break;
                case R.id.btn_hosts_export:
                    new AlertDialog.Builder(this.getActivity())
                            .setMessage(R.string.export_desc)
                            .setPositiveButton(android.R.string.yes,
                                    new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog,
                                                            int which) {
                                            do_export_hosts();
                                        }
                                    }).setNegativeButton(android.R.string.cancel, null)
                            .show();
                    break;
                case R.id.btn_hosts_import:
                    new AlertDialog.Builder(this.getActivity())
                            .setMessage(R.string.hosts_import_desc)
                            .setPositiveButton(android.R.string.yes,
                                    new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog,
                                                            int which) {
                                            do_import_hosts();
                                        }
                                    }).setNegativeButton(android.R.string.cancel, null)
                            .show();
                    break;
                case R.id.btn_share:
                    do_share_hosts();
                    break;
            default:
                break;
            }
        }

        public static String getMimeType(String url) {
            String type = null;
            String extension = MimeTypeMap.getFileExtensionFromUrl(url);
            if (extension != null) {
                MimeTypeMap mime = MimeTypeMap.getSingleton();
                type = mime.getMimeTypeFromExtension(extension);
                Log.d(TAG, url+":"+extension+":"+type);
            }
            return type;
        }

        private void do_share_hosts() {
            File hosts = new File(Environment.getExternalStorageDirectory(),
                    HostsDB.DNSLITE_JSON);
            Log.d(TAG, ""+ getMimeType(HostsDB.DNSLITE_JSON));
            String mime = "text/javascript";
            do_share_files(hosts, mime, R.string.share_hosts_title, R.string.share_hosts_text);
        }

        private void do_share_files(File file, String mime, int subject, int text) {
            Intent intent = new Intent(android.content.Intent.ACTION_SEND);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(file));
            intent.setType(mime);
            intent.putExtra(android.content.Intent.EXTRA_SUBJECT,
                    getString(subject));
            intent.putExtra(android.content.Intent.EXTRA_TEXT, getString(text));
            startActivity(Intent.createChooser(intent, getString(subject)));
        }

        private void do_import_hosts() {
            boolean status = hdb.import_hosts_db(HostsDB.DNSLITE_JSON);
            Toast.makeText(
                    this.getActivity(),
                    getString(status ? R.string.hosts_import_succ
                            : R.string.hosts_import_fail), Toast.LENGTH_SHORT)
                    .show();
        }

        private void do_export_hosts() {
            boolean status = hdb.export_db(HostsDB.DNSLITE_JSON);
            Toast.makeText(
                    this.getActivity(),
                    getString(status ? R.string.hosts_export_succ
                            : R.string.hosts_export_fail), Toast.LENGTH_SHORT)
                    .show();
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            if (mPop != null) {
                mPop.dismiss();
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
