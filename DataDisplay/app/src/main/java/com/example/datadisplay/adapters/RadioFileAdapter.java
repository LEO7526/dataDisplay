package com.example.datadisplay.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.example.datadisplay.R;

import java.util.List;

public class RadioFileAdapter extends BaseAdapter {
    private final Context context;
    private final List<String> titles;

    public RadioFileAdapter(Context context, List<String> titles) {
        this.context = context;
        this.titles = titles;
    }

    @Override public int getCount() { return titles.size(); }
    @Override public Object getItem(int position) { return titles.get(position); }
    @Override public long getItemId(int position) { return position; }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        TextView textView;
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.item_radio_file, parent, false);
        }
        textView = convertView.findViewById(R.id.radioFileNameText);
        textView.setText(titles.get(position));
        return convertView;
    }
}