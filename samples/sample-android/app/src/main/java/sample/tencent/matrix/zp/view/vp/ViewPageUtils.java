package sample.tencent.matrix.zp.view.vp;

import androidx.annotation.NonNull;
import androidx.viewpager.widget.ViewPager;

public class ViewPageUtils {
    public static void clearView(@NonNull ViewPager viewPager) {
        viewPager.removeAllViews();
        viewPager.removeAllViewsInLayout();
    }
}
