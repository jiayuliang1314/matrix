package sample.tencent.matrix.zp.ui.settings;//package com.inshot.screenrecorder.widget;

import android.app.Dialog;
import android.content.Context;
import android.view.View;
import android.widget.TextView;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Arrays;
import java.util.List;

import sample.tencent.matrix.R;


public class CustomDialog extends Dialog implements CustomDialogAdapter.OnItemOnClickListener, View.OnClickListener {
    public static final String[] ResourceCanaryMode = {"Auto Dump", "No Dump", "Manual Dump", "Silence Analyse"};
    public static final String[] ResourceCanaryForegroundTimeArray = {"2s", "10s", "30s", "45s", "60s", "120s"};
    public static final String[] ResourceCanaryBackTimeArray = {"1分钟", "5分钟", "10分钟", "15分钟", "20分钟", "30分钟"};
    public static final String[] ResourceCanaryCheckTimeArray = {"2次", "5次", "10次", "15次", "20次"};

    public static final int ResourceCanaryModeType = 0;
    public static final int ResourceCanaryForegroundTime = 1;
    public static final int ResourceCanaryBackTime = 2;
    public static final int ResourceCanaryCheckTime = 3;

    private final View cancel_of_customdialog;
    private final TextView title_of_customdialog;
    private final TextView resulttv;
    private final TextView subtitle_of_customdialog;
    private final RecyclerView rv_of_customdialog;
    private final int type;
    private final CustomDialogAdapter adapter;
    private List<String> now_items_list;

    public CustomDialog(Context context, String title, int type, TextView result) {
        super(context, R.style.screenrecordecustom_dialog);
        setContentView(R.layout.screenrecorder_custom_dialog);
        title_of_customdialog = findViewById(R.id.title_of_customdialog);
        this.resulttv = result;
        this.rv_of_customdialog = findViewById(R.id.rv_of_customdialog);
        this.cancel_of_customdialog = findViewById(R.id.cancel_of_customdialog);
        this.subtitle_of_customdialog = findViewById(R.id.subtitle_of_customdialog);
        this.type = type;
        this.rv_of_customdialog.setLayoutManager(new GridLayoutManager(context, 1));
        this.adapter = new CustomDialogAdapter(context, getItemsList(), this.type);
        this.adapter.setOnClickListener(this);
        this.rv_of_customdialog.setAdapter(this.adapter);
        this.title_of_customdialog.setText(title);
        this.cancel_of_customdialog.setOnClickListener(this);
    }

    private List<String> getItemsList() {
        switch (this.type) {
            case ResourceCanaryModeType:
                this.now_items_list = Arrays.asList(ResourceCanaryMode);
                break;
            case ResourceCanaryForegroundTime:
                this.now_items_list = Arrays.asList(ResourceCanaryForegroundTimeArray);
                break;
            case ResourceCanaryBackTime:
                this.now_items_list = Arrays.asList(ResourceCanaryBackTimeArray);
                break;
            case ResourceCanaryCheckTime:
                this.now_items_list = Arrays.asList(ResourceCanaryCheckTimeArray);
                break;
        }
        return this.now_items_list;
    }

    @Override
    public void onClick(View view) {
        if (view.getId() == R.id.cancel_of_customdialog) {
            dismiss();
        }
    }

    public void restore_data() {
        //todo
        this.adapter.restore_data();
    }

    @Override
    public void onClickItem(int i2) {
        if (this.resulttv != null) {
            this.resulttv.setText(this.now_items_list.get(i2));
        }
        dismiss();
        //todo
    }
}
