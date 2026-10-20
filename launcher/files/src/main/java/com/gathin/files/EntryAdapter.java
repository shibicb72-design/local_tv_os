package com.gathin.files;

import android.content.Context;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.gathin.roman.RomanRows;
import com.gathin.roman.RomanStyle;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/** Rows of the browser and of the roots screen (F7). Plain views, stable ids from the path hash. */
final class EntryAdapter extends RecyclerView.Adapter<EntryAdapter.Holder> {

    /** What one row shows; built off the main thread by {@link #rowsOf} / {@link #rootRows}. */
    static final class Row {
        final String name;
        final String path;
        final boolean isDir;
        final Entry entry;      // null on the roots screen
        final String size;
        final String date;

        Row(String name, String path, boolean isDir, Entry entry, String size, String date) {
            this.name = name;
            this.path = path;
            this.isDir = isDir;
            this.entry = entry;
            this.size = size;
            this.date = date;
        }
    }

    interface OnRowClick {
        void onRowClick(Row row);
    }

    private final RomanStyle style = RomanStyle.defaults();
    private final OnRowClick click;
    private List<Row> rows = new ArrayList<>();

    EntryAdapter(OnRowClick click) {
        this.click = click;
        setHasStableIds(true);
    }

    static List<Row> rowsOf(Context ctx, List<Entry> entries) {
        DateFormat df = android.text.format.DateFormat.getMediumDateFormat(ctx);
        List<Row> out = new ArrayList<>(entries.size());
        for (Entry e : entries) {
            out.add(new Row(e.isDir ? e.name + "/" : e.name, e.path, e.isDir, e,
                    e.isDir ? "" : Formatter.formatShortFileSize(ctx, e.size),
                    df.format(new Date(e.mtime))));
        }
        return out;
    }

    static List<Row> rootRows(List<VolumeModel.Root> roots) {
        List<Row> out = new ArrayList<>(roots.size());
        for (VolumeModel.Root r : roots) {
            out.add(new Row(r.label, r.path, true, null, r.readOnly ? "read only" : "", ""));
        }
        return out;
    }

    void submit(List<Row> next) {
        rows = next;
        notifyDataSetChanged();
    }

    Row get(int position) {
        return rows.get(position);
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    @Override
    public long getItemId(int position) {
        return rows.get(position).path.hashCode();
    }

    @Override
    public Holder onCreateViewHolder(ViewGroup parent, int viewType) {
        Context c = parent.getContext();
        float dp = c.getResources().getDisplayMetrics().density;

        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setFocusable(true);
        row.setClickable(true);
        row.setBackground(RomanRows.rowBackground(c, style));
        int padH = (int) (16 * dp);
        row.setPadding(padH, 0, padH, 0);
        row.setLayoutParams(new RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) (48 * dp)));

        TextView name = text(c);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        row.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView size = text(c);
        size.setGravity(Gravity.END);
        size.setAlpha(0.8f);
        row.addView(size, new LinearLayout.LayoutParams((int) (110 * dp), ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView date = text(c);
        date.setGravity(Gravity.END);
        date.setAlpha(0.8f);
        row.addView(date, new LinearLayout.LayoutParams((int) (150 * dp), ViewGroup.LayoutParams.WRAP_CONTENT));

        return new Holder(row, name, size, date);
    }

    /** Row text follows the row's focus state (stone ink on the gold row). */
    private TextView text(Context c) {
        TextView tv = new TextView(c);
        RomanRows.rowText(tv, style);
        tv.setDuplicateParentStateEnabled(true);
        return tv;
    }

    @Override
    public void onBindViewHolder(Holder h, int position) {
        final Row r = rows.get(position);
        h.name.setText(r.name);
        h.size.setText(r.size);
        h.date.setText(r.date);
        h.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                click.onRowClick(r);
            }
        });
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final TextView name;
        final TextView size;
        final TextView date;

        Holder(View v, TextView name, TextView size, TextView date) {
            super(v);
            this.name = name;
            this.size = size;
            this.date = date;
        }
    }
}
