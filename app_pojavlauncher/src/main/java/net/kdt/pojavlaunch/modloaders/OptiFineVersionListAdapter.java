package net.kdt.pojavlaunch.modloaders;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.ExpandableListAdapter;
import android.widget.TextView;

import net.paraguacraft.mobile.PgVersions;

import java.util.ArrayList;
import java.util.List;

public class OptiFineVersionListAdapter extends BaseExpandableListAdapter implements ExpandableListAdapter {

    private final List<String> mMinecraftVersions = new ArrayList<>();
    private final List<List<OptiFineUtils.OptiFineVersion>> mOptifineVersions = new ArrayList<>();
    private final LayoutInflater mLayoutInflater;

    public OptiFineVersionListAdapter(OptiFineUtils.OptiFineVersions optiFineVersions, LayoutInflater mLayoutInflater) {
        this.mLayoutInflater = mLayoutInflater;
        if (optiFineVersions == null || optiFineVersions.minecraftVersions == null) return;
        for (int i = 0; i < optiFineVersions.minecraftVersions.size(); i++) {
            String mc = optiFineVersions.minecraftVersions.get(i);
            if (!PgVersions.isReleaseId(mc)) continue;
            if (optiFineVersions.optifineVersions == null || i >= optiFineVersions.optifineVersions.size()) continue;
            mMinecraftVersions.add(mc);
            mOptifineVersions.add(optiFineVersions.optifineVersions.get(i));
        }
    }

    @Override
    public int getGroupCount() {
        return mMinecraftVersions.size();
    }

    @Override
    public int getChildrenCount(int i) {
        return mOptifineVersions.get(i).size();
    }

    @Override
    public Object getGroup(int i) {
        return mMinecraftVersions.get(i);
    }

    @Override
    public Object getChild(int i, int i1) {
        return mOptifineVersions.get(i).get(i1);
    }

    @Override
    public long getGroupId(int i) {
        return i;
    }

    @Override
    public long getChildId(int i, int i1) {
        return i1;
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }

    @Override
    public View getGroupView(int i, boolean b, View convertView, ViewGroup viewGroup) {
        if(convertView == null)
            convertView = mLayoutInflater.inflate(android.R.layout.simple_expandable_list_item_1, viewGroup, false);

        ((TextView) convertView).setText((String)getGroup(i));

        return convertView;
    }

    @Override
    public View getChildView(int i, int i1, boolean b, View convertView, ViewGroup viewGroup) {
        if(convertView == null)
            convertView = mLayoutInflater.inflate(android.R.layout.simple_expandable_list_item_1, viewGroup, false);
        ((TextView) convertView).setText(((OptiFineUtils.OptiFineVersion)getChild(i,i1)).versionName);
        return convertView;
    }

    @Override
    public boolean isChildSelectable(int i, int i1) {
        return true;
    }
}
