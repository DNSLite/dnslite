package me.xu.DNSLite;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Rect;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.app.ListFragment;
import android.support.v4.widget.SimpleCursorAdapter;
import android.util.Log;
import android.view.*;
import android.widget.*;

public class HostsGroupListActivity extends FragmentActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.fragment_hosts_group);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.hosts_source_option, menu);
        return true;
    }

    public static class DetailsActivity extends FragmentActivity {
        private static final String TAG = "DetailsActivity";

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
                finish();
                return;
            }

            if (savedInstanceState == null) {
                DetailsFragment details = new DetailsFragment();
                details.setArguments(getIntent().getExtras());
                getSupportFragmentManager().beginTransaction()
                        .add(android.R.id.content, details).commit();
            }
        }
    }

    public static class TitlesFragment extends ListFragment {
        private static String TAG = "HostsGroupTitles";
        boolean mDualPane;
        long m_cur_check_group_id = 0;

        private Cursor cursor = null;
        private HostsDB hdb = null;
        private SimpleCursorAdapter m_adapter;
        private ListView m_list_view;

        @Override
        public void onActivityCreated(Bundle savedInstanceState) {
            super.onActivityCreated(savedInstanceState);
            setEmptyText(getText(R.string.no_hosts_source));
            setListShown(false);
            m_list_view = getListView();

            hdb = HostsDB.GetInstance(getActivity());
            cursor = hdb.getAllHostsSource();

            m_adapter = new SimpleCursorAdapter(getActivity(), android.R.layout.simple_list_item_2,
                    cursor, new String[]{"name", "url"}, new int[]{android.R.id.text1,
                    android.R.id.text2}, 0);

            registerForContextMenu(m_list_view);

            setListAdapter(m_adapter);

            // Check to see if we have a frame in which to embed the details
            // fragment directly in the containing UI.
            View detailsFrame = getActivity().findViewById(R.id.details);
            mDualPane = detailsFrame != null
                    && detailsFrame.getVisibility() == View.VISIBLE;

            if (savedInstanceState != null) {
                // Restore last state for checked position.
                m_cur_check_group_id = savedInstanceState.getLong("curChoice", 0);
            }

            if (mDualPane) {
                // In dual-pane mode, the list view highlights the selected
                // item.
                getListView().setChoiceMode(ListView.CHOICE_MODE_SINGLE);
                // Make sure our UI is in the correct state.
                showDetails(m_cur_check_group_id);
            }
        }

        @Override
        public void onSaveInstanceState(Bundle outState) {
            super.onSaveInstanceState(outState);
            outState.putLong("curChoice", m_cur_check_group_id);
        }

        @Override
        public void onListItemClick(ListView l, View v, int position, long id) {
            Log.d(TAG, "id:" + id + ", pos:" + position);
            getListView().setItemChecked(position, true);
            showDetails(id);
        }

        void showDetails(long group_id) {
            m_cur_check_group_id = group_id;

            if (mDualPane) {
                // Check what fragment is currently shown, replace if needed.
                DetailsFragment details = (DetailsFragment) getFragmentManager()
                        .findFragmentById(R.id.details);
                if (details == null || details.getShownGroupId() != group_id) {
                    Log.d(TAG, "details:" + details + ", group_id:" + group_id);
                    // Make new fragment to show this selection.
                    details = DetailsFragment
                            .newInstance(group_id);

                    // Execute a transaction, replacing any existing fragment
                    // with this one inside the frame.
                    FragmentTransaction ft = getFragmentManager()
                            .beginTransaction();
                    ft.replace(R.id.details, details);
                    ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
                    ft.commit();
                }

            } else {
                // Otherwise we need to launch a new activity to display
                // the dialog fragment with selected text.
                Intent intent = new Intent();
                intent.setClass(getActivity(), DetailsActivity.class);
                intent.putExtra("group_id", group_id);
                startActivity(intent);
            }
        }
    }

    public static class DetailsFragment extends ListFragment {

        private PopupWindow mPop = null;
        private ProgressDialog progressDialog = null;

        public static DetailsFragment newInstance(long group_id) {
            DetailsFragment f = new DetailsFragment();

            // Supply index input as an argument.
            Bundle args = new Bundle();
            args.putLong("group_id", group_id);
            f.setArguments(args);

            return f;
        }

        private static String TAG = "HostsGroupHosts";

        private Cursor cursor = null;
        private HostsDB hdb = null;
        private SimpleCursorAdapter m_adapter;
        private final String[] SCA_item = new String[] { "status", "domain", "ip" };
        private final int[] SCA_item_id = new int[] { R.id.status, R.id.domain,
                R.id.ip };

        @Override
        public void onActivityCreated(Bundle savedInstanceState) {
            super.onActivityCreated(savedInstanceState);
            setHasOptionsMenu(true);
            setEmptyText(getText(R.string.no_hosts));
            setListShown(false);

            hdb = HostsDB.GetInstance(this.getActivity().getApplicationContext());
            if (getShownGroupId() == -1) {
                cursor = hdb.getAllInUseHosts();
            } else {
                cursor = hdb.getAllHostsBySourceId(getShownGroupId());
            }

            ListView lv = getListView();
            lv.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
            lv.getEmptyView().setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    DetailsFragment.this.getActivity().openOptionsMenu();
                }
            });

            m_adapter = new SimpleCursorAdapter(getActivity(), R.layout.hosts_row, cursor,
                    SCA_item, SCA_item_id, 0);

            m_adapter.setViewBinder(new SimpleCursorAdapter.ViewBinder() {
                public boolean setViewValue(View view, Cursor cursor,
                                            int columnIndex) {
                    switch (view.getId()) {
                        case R.id.status:
                            ToggleButton v = (ToggleButton) view;
                            int result = cursor.getInt(cursor.getColumnIndex("status"));
                            v.setChecked((result == 1));
                            ((View) view.getParent()).setBackgroundColor((cursor
                                    .getPosition() % 2 == 0) ? android.R.color.background_light
                                    : 0x300000FF);
                            return true;
                    }
                    return false;
                }
            });
            setListAdapter(m_adapter);
        }

        @Override
        public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
            inflater.inflate(R.menu.hosts_editor_option, menu);
            if (getShownGroupId() == -1) {
                menu.removeItem(R.id.menu_item_update);
                menu.removeItem(R.id.menu_item_merge);
                menu.removeItem(R.id.menu_item_empty);
                menu.removeItem(R.id.menu_item_disable);
            }
            super.onCreateOptionsMenu(menu, inflater);
        }

        @Override
        public void onDestroyView() {
            super.onDestroyView();
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

        public long getShownGroupId() {
            return getArguments().getLong("group_id", -1);
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
        public void onListItemClick(ListView l, View v, int position, long id) {

            // Log.d(TAG, "id:" + id + " position:" + position);
            l.setItemChecked(position, true);

            if (mPop == null) {
                LayoutInflater mLayoutInflater = (LayoutInflater) this.getActivity()
                        .getSystemService(LAYOUT_INFLATER_SERVICE);
                View popmenu = mLayoutInflater.inflate(
                        R.layout.hosts_editor_popmenu, null);
                popmenu.measure(LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                Button quick_actions_enable = (Button) popmenu
                        .findViewById(R.id.quick_actions_enable);
                quick_actions_enable.setOnClickListener(onClickPopMenu);
                Button quick_actions_disable = (Button) popmenu
                        .findViewById(R.id.quick_actions_disable);
                quick_actions_disable.setOnClickListener(onClickPopMenu);
                Button quick_actions_delete = (Button) popmenu
                        .findViewById(R.id.quick_actions_delete);
                quick_actions_delete.setOnClickListener(onClickPopMenu);
                Button quick_actions_edit = (Button) popmenu
                        .findViewById(R.id.quick_actions_edit);
                quick_actions_edit.setOnClickListener(onClickPopMenu);
                mPop = new PopupWindow(popmenu, LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
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

        private void addHosts(final long _id, String domain, String ip) {
            LayoutInflater factory = LayoutInflater.from(this.getActivity());
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

            new AlertDialog.Builder(this.getActivity())
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
                                                DetailsFragment.this.getActivity(),
                                                getString(R.string.hosts_host_add_input_error),
                                                Toast.LENGTH_SHORT).show();
                                        return;
                                    }
                                    if (hdb.newHost(_id, domain, ip, 0, 0, 1)) {
                                        new RefreshList().execute();
                                    } else {
                                        Toast.makeText(
                                                DetailsFragment.this.getActivity(),
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

        private void delete(final long rowId) {
            new AlertDialog.Builder(this.getActivity())
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
                if (getShownGroupId() == -1) {
                    return hdb.getAllInUseHosts();
                } else {
                    return hdb.getAllHostsBySourceId(getShownGroupId());
                }
            }

            protected void onPostExecute(Cursor newCursor) {
                m_adapter.changeCursor(newCursor);
                cursor.close();
                cursor = newCursor;
            }
        }

    }
}
