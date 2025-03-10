/*
 * Copyright (C) 2011 The CyanogenMod Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.colt.settings.preference;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.Fragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.Intent.ShortcutIconResource;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.ExpandableListView;
import android.widget.TextView;

import com.android.settings.R;
import com.colt.settings.preference.ShortcutPickHelper.AppExpandableAdapter.GroupInfo;

public class ShortcutPickHelper {

    private Activity mParent;
    private AlertDialog mAlertDialog;
    private OnPickListener mListener;
    private PackageManager mPackageManager;
    private static final int REQUEST_PICK_SHORTCUT = 100;
    private static final int REQUEST_PICK_APPLICATION = 101;
    private static final int REQUEST_CREATE_SHORTCUT = 102;
    private int lastFragmentId;

    public interface OnPickListener {
        void shortcutPicked(String uri, String friendlyName, boolean isApplication);
    }

    public ShortcutPickHelper(Activity parent, OnPickListener listener) {
        mParent = parent;
        mPackageManager = mParent.getPackageManager();
        mListener = listener;
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK) {
            switch (requestCode) {
            case REQUEST_PICK_APPLICATION:
                completeSetCustomApp(data);
                break;
            case REQUEST_CREATE_SHORTCUT:
                completeSetCustomShortcut(data);
                break;
            case REQUEST_PICK_SHORTCUT:
                processShortcut(data, REQUEST_PICK_APPLICATION, REQUEST_CREATE_SHORTCUT);
                break;
            }
        }
    }

    public void pickShortcut(String[] names, ShortcutIconResource[] icons, int fragmentId) {
        Bundle bundle = new Bundle();

        ArrayList<String> shortcutNames = new ArrayList<String>();
        if (names != null) {
            for (String s : names) {
                shortcutNames.add(s);
            }
        }
        shortcutNames.add(mParent.getString(R.string.profile_applist_title));
        shortcutNames.add(mParent.getString(R.string.picker_activities));
        bundle.putStringArrayList(Intent.EXTRA_SHORTCUT_NAME, shortcutNames);

        ArrayList<ShortcutIconResource> shortcutIcons = new ArrayList<ShortcutIconResource>();
        if (icons != null) {
            for (ShortcutIconResource s : icons) {
                shortcutIcons.add(s);
            }
        }
        shortcutIcons.add(ShortcutIconResource.fromContext(mParent, android.R.drawable.sym_def_app_icon));
        shortcutIcons.add(ShortcutIconResource.fromContext(mParent, R.drawable.activities_icon));
        bundle.putParcelableArrayList(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, shortcutIcons);

        Intent pickIntent = new Intent(Intent.ACTION_PICK_ACTIVITY);
        pickIntent.putExtra(Intent.EXTRA_INTENT, new Intent(Intent.ACTION_CREATE_SHORTCUT));
        pickIntent.putExtra(Intent.EXTRA_TITLE, mParent.getText(R.string.select_custom_app_title));
        pickIntent.putExtras(bundle);
        lastFragmentId = fragmentId;
        startFragmentOrActivity(pickIntent, REQUEST_PICK_SHORTCUT);
    }

    private void startFragmentOrActivity(Intent pickIntent, int requestCode) {
        if (lastFragmentId == 0) {
            mParent.startActivityForResult(pickIntent, requestCode);
        } else {
            Fragment cFrag = mParent.getFragmentManager().findFragmentById(lastFragmentId);
            if (cFrag != null) {
                mParent.startActivityFromFragment(cFrag, pickIntent, requestCode);
            }
        }
    }

    private void processShortcut(final Intent intent, int requestCodeApplication, int requestCodeShortcut) {
        // Handle case where user selected "Applications"
        String applicationName = mParent.getString(R.string.profile_applist_title);
        String application2name = mParent.getString(R.string.picker_activities);
        String shortcutName = intent.getStringExtra(Intent.EXTRA_SHORTCUT_NAME);
        if (applicationName != null && applicationName.equals(shortcutName)) {
            Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
            mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);

            Intent pickIntent = new Intent(Intent.ACTION_PICK_ACTIVITY);
            pickIntent.putExtra(Intent.EXTRA_INTENT, mainIntent);
            startFragmentOrActivity(pickIntent, requestCodeApplication);
        } else if (application2name != null && application2name.equals(shortcutName)){
            final List<PackageInfo> pInfos = mPackageManager.getInstalledPackages(PackageManager.GET_ACTIVITIES);
            ExpandableListView appListView = new ExpandableListView(mParent);
            AppExpandableAdapter appAdapter = new AppExpandableAdapter(pInfos, mParent);
            appListView.setAdapter(appAdapter);
            appListView.setOnChildClickListener(new ExpandableListView.OnChildClickListener() {
                @Override
                public boolean onChildClick(ExpandableListView parent, View v,
                        int groupPosition, int childPosition, long id) {
                    Intent shortIntent = new Intent(Intent.ACTION_MAIN);
                    String pkgName = ((GroupInfo)parent.getExpandableListAdapter().getGroup(groupPosition))
                            .info.packageName;
                    String actName = ((GroupInfo)parent.getExpandableListAdapter().getGroup(groupPosition))
                            .info.activities[childPosition].name;
                    shortIntent.setClassName(pkgName, actName);
                    completeSetCustomApp(shortIntent);
                    mAlertDialog.dismiss();
                    return true;
                }
            });
            Builder builder = new Builder(mParent);
            builder.setView(appListView);
            mAlertDialog = builder.create();
            mAlertDialog.setTitle(mParent.getString(R.string.select_custom_activity_title));
            mAlertDialog.show();
            mAlertDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialog) {
                    mListener.shortcutPicked(null, null, false);
                }
            });
        } else {
            startFragmentOrActivity(intent, requestCodeShortcut);
        }
    }

    public class AppExpandableAdapter extends BaseExpandableListAdapter {

        ArrayList<GroupInfo> allList = new ArrayList<GroupInfo>();
        final int groupPadding;

        public class LabelCompare implements Comparator<GroupInfo>{
            @Override
            public int compare(GroupInfo item1, GroupInfo item2) {
                String rank1 = item1.label.toLowerCase();
                String rank2 = item2.label.toLowerCase();
                int result = rank1.compareTo(rank2);
                if(result == 0) {
                    return 0;
                } else if(result < 0) {
                    return -1;
                } else {
                    return +1;
                }
            }
        }

        class GroupInfo {
            String label;
            PackageInfo info;
            GroupInfo (String l, PackageInfo p) {
                label = l;
                info = p;
            }
        }

        public AppExpandableAdapter(List<PackageInfo> pInfos, Context context) {
            for (PackageInfo i : pInfos) {
                allList.add(new GroupInfo(i.applicationInfo.loadLabel(mPackageManager).toString(), i));
            }
            Collections.sort(allList, new LabelCompare());
            groupPadding = context.getResources().getDimensionPixelSize(R.dimen.shortcut_picker_left_padding);
        }

        public String getChild(int groupPosition, int childPosition) {
            return allList.get(groupPosition).info.activities[childPosition].name;
        }

        public long getChildId(int groupPosition, int childPosition) {
            return childPosition;
        }

        public int getChildrenCount(int groupPosition) {
            if (allList.get(groupPosition).info.activities != null) {
                return allList.get(groupPosition).info.activities.length;
            } else {
                return 0;
            }
        }


        public View getChildView(int groupPosition, int childPosition, boolean isLastChild,
                View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = View.inflate(mParent, android.R.layout.simple_list_item_1, null);
                convertView.setPadding(groupPadding, 0, 0, 0);

            }
            TextView textView = (TextView)convertView.findViewById(android.R.id.text1);
            textView.setText(getChild(groupPosition, childPosition).replaceFirst(allList.get(groupPosition).info.packageName + ".", ""));
            return convertView;
        }

        public GroupInfo getGroup(int groupPosition) {
            return allList.get(groupPosition);
        }

        public int getGroupCount() {
            return allList.size();
        }

        public long getGroupId(int groupPosition) {
            return groupPosition;
        }

        public View getGroupView(int groupPosition, boolean isExpanded, View convertView,
                ViewGroup parent) {
            if (convertView == null) {
                convertView = View.inflate(mParent, android.R.layout.simple_list_item_1, null);
                convertView.setPadding(70, 0, 0, 0);
            }
            TextView textView = (TextView)convertView.findViewById(android.R.id.text1);
            textView.setText(getGroup(groupPosition).label.toString());
            return convertView;
        }

        public boolean isChildSelectable(int groupPosition, int childPosition) {
            return true;
        }

        public boolean hasStableIds() {
            return true;
        }

    }

    private void completeSetCustomApp(Intent data) {
        mListener.shortcutPicked(data.toUri(0), getFriendlyActivityName(data, false), true);
    }

    private void completeSetCustomShortcut(Intent data) {
        Intent intent = data.getParcelableExtra(Intent.EXTRA_SHORTCUT_INTENT);
        if (intent == null) return;
        // preserve shortcut name, we want to restore it later
        intent.putExtra(Intent.EXTRA_SHORTCUT_NAME, data.getStringExtra(Intent.EXTRA_SHORTCUT_NAME));
        // standardize the uri scheme to be sure we can launch it
        // TODO: add compatibility for apps that use ACTION_CONFIRM_PIN_SHORTCUT (drag widget on the home screen)
        String appUri = intent.toUri(Intent.URI_INTENT_SCHEME);
        appUri = appUri.replaceAll("com.android.contacts.action.QUICK_CONTACT", "android.intent.action.VIEW");
        mListener.shortcutPicked(appUri, getFriendlyShortcutName(intent), false);
    }

    private String getFriendlyActivityName(Intent intent, boolean labelOnly) {
        ActivityInfo ai = intent.resolveActivityInfo(mPackageManager, PackageManager.GET_ACTIVITIES);
        String friendlyName = null;
        if (ai != null) {
            friendlyName = ai.loadLabel(mPackageManager).toString();
            if (friendlyName == null && !labelOnly) {
                friendlyName = ai.name;
            }
        }
        return friendlyName != null || labelOnly ? friendlyName : intent.toUri(0);
    }

    private String getFriendlyShortcutName(Intent intent) {
        String activityName = getFriendlyActivityName(intent, true);
        String name = intent.getStringExtra(Intent.EXTRA_SHORTCUT_NAME);

        if (activityName != null && name != null) {
            return activityName + ": " + name;
        }
        return name != null ? name : intent.toUri(0);
    }

    public String getFriendlyNameForUri(String uri) {
        if (uri == null) {
            return null;
        }

        try {
            Intent intent = Intent.parseUri(uri, 0);
            if (Intent.ACTION_MAIN.equals(intent.getAction())) {
                return getFriendlyActivityName(intent, false);
            }
            return getFriendlyShortcutName(intent);
        } catch (URISyntaxException e) {
        }

        return uri;
    }
}
