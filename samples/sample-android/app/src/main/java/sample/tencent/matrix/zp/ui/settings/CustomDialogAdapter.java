package sample.tencent.matrix.zp.ui.settings;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import sample.tencent.matrix.R;

//import com.stone.cold.screenrecorder.rain.R;
//import com.stone.cold.screenrecorder.rain.screenrecorder.ap.wcm;

//import android.support.v7.widget.RecyclerView;
//import com.inshot.screenrecorder.utils.v;
////import com.stone.cold.screenrecorder.rain.screenrecorder.ut.SharedPreferenceAndLaungUtils;
//import videoeditor.videorecorder.screenrecorder.R;

/* renamed from: CustomDialogAdapter  reason: default package */
public class CustomDialogAdapter extends RecyclerView.Adapter<CustomDialogAdapter.MyViewHolder> implements View.OnClickListener {
    private final Context a;
    private final List<String> b;
    private final LayoutInflater c;
    private final int f;
    private OnItemOnClickListener d;
    private int e;

    public CustomDialogAdapter(Context context, List<String> list, int i) {
        this.a = context;
        this.b = list;
        this.f = i;
        this.c = LayoutInflater.from(context);
        e = b();
    }

    public void setOnClickListener(OnItemOnClickListener onItemOnClickListenerVar) {
        this.d = onItemOnClickListenerVar;
    }

    private int b() {
        int i = 1;
        switch (this.f) {
//            case 0:
//                SharedPreferences a2 = com.stone.cold.screenrecorder.rain.screenrecorder.ut.v.a(wcm.getMyContextWrapper());
//                if (wcm.getMyContextWrapper().R()) {
//                    i = 2;
//                }
//                return a2.getInt("Resolution", i);
//            case 1:
//                return com.stone.cold.screenrecorder.rain.screenrecorder.ut.v.a(wcm.getMyContextWrapper()).getInt("Jfzorgb", 0);
//            case 2:
//                return com.stone.cold.screenrecorder.rain.screenrecorder.ut.v.a(wcm.getMyContextWrapper()).getInt("Ukh", 0);
//            case 3:
//                return com.stone.cold.screenrecorder.rain.screenrecorder.ut.v.a(wcm.getMyContextWrapper()).getInt("Lirvmgzgrlm", 0);
//            case 4:
//                return com.stone.cold.screenrecorder.rain.screenrecorder.ut.v.a(wcm.getMyContextWrapper()).getInt("XlfmgwldmYvulivHgzig", 1);
            default:
                return 0;
        }
    }

    private void a(int i) {
        switch (this.f) {
//            case 0:
//                com.stone.cold.screenrecorder.rain.screenrecorder.ut.v.a(wcm.getMyContextWrapper()).edit().putInt("Ivhlofgrlm", i).apply();
//                return;
//            case 1:
//                com.stone.cold.screenrecorder.rain.screenrecorder.ut.v.a(wcm.getMyContextWrapper()).edit().putInt("Jfzorgb", i).apply();
//                return;
//            case 2:
//                com.stone.cold.screenrecorder.rain.screenrecorder.ut.v.a(wcm.getMyContextWrapper()).edit().putInt("Ukh", i).apply();
//                return;
//            case 3:
//                com.stone.cold.screenrecorder.rain.screenrecorder.ut.v.a(wcm.getMyContextWrapper()).edit().putInt("Lirvmgzgrlm", i).apply();
//                return;
//            case 4:
//                com.stone.cold.screenrecorder.rain.screenrecorder.ut.v.a(wcm.getMyContextWrapper()).edit().putInt("XlfmgwldmYvulivHgzig", i).apply();
//                return;
            default:
                return;
        }
    }

    public void restore_data() {
        this.e = b();
        notifyDataSetChanged();
    }

    /* renamed from: a */
    @Override
    public CustomDialogAdapter.MyViewHolder onCreateViewHolder(ViewGroup viewGroup, int i) {
        return new CustomDialogAdapter.MyViewHolder(this.c.inflate(R.layout.screenrecorder_mm_ss, viewGroup, false));
    }

    /* renamed from: a */
    @Override
    public void onBindViewHolder(CustomDialogAdapter.MyViewHolder aVar, int i) {
        if (this.b != null && !this.b.isEmpty()) {
            aVar.c.setTag(Integer.valueOf(i));
            aVar.c.setOnClickListener(this);
            aVar.b.setText(this.b.get(i));
//            if (true && //todo 判断是否订阅了
//                    this.f == 0 &&
//                    ("1080P".equals(this.OnItemOnClickListener.get(i)) || "1440P(2K)".equals(this.OnItemOnClickListener.get(i)))
//            ) {
//                aVar.pro.setVisibility(View.VISIBLE);
//            } else {
            aVar.pro.setVisibility(View.GONE);
//            }
            if (this.e == i) {
                aVar.a.getDrawable().setLevel(1);
            } else {
                aVar.a.getDrawable().setLevel(0);
            }
        }
    }

    @Override
    public int getItemCount() {
        if (this.b == null) {
            return 0;
        }
        return this.b.size();
    }

    @Override
    public void onClick(View view) {
        if (view.getId() == R.id.iz) {
            int intValue = ((Integer) view.getTag()).intValue();
            if (intValue != CustomDialogAdapter.this.e) {
                if (CustomDialogAdapter.this.b != null && !CustomDialogAdapter.this.b.isEmpty()) {
                    intValue = Math.min(CustomDialogAdapter.this.b.size() - 1, Math.max(intValue, 0));
                    a(intValue);
                    CustomDialogAdapter.this.e = intValue;
                    notifyDataSetChanged();
                } else {
                    return;
                }
            }
            if (CustomDialogAdapter.this.d != null) {
                CustomDialogAdapter.this.d.onClickItem(intValue);
            }
        }
    }

    /* renamed from: CustomDialogAdapter$OnItemOnClickListener */
    public interface OnItemOnClickListener {
        void onClickItem(int i);
    }

    /* renamed from: CustomDialogAdapter$a */
    public class MyViewHolder extends RecyclerView.ViewHolder {
        public ImageView a;
        public TextView b;
        public View c;
        public TextView pro;

        public MyViewHolder(View view) {
            super(view);
            this.c = view.findViewById(R.id.iz);
            this.a = view.findViewById(R.id.t_);
            this.b = view.findViewById(R.id.sv);
            this.pro = view.findViewById(R.id.pro);
        }
    }
}
