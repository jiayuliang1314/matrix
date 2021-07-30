package sample.tencent.matrix.zp.view.vp;

import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.viewpager.widget.PagerAdapter;

import sample.tencent.matrix.zp.view.vp.kernel.BasePageAdapterCallback;

//import com.stone.cold.screenrecorder.rain.view.vp.kernel.BasePageAdapterCallback;

//import wavely.base.ui.viewpager.framework.BasePageAdapterCallback;

/**
 * @author jia
 */
public class BasePageAdapter extends PagerAdapter {
    private BasePageAdapterCallback mBasePageAdapterCallback;

    public BasePageAdapter(@NonNull BasePageAdapterCallback basePageAdapterCallback) {
        mBasePageAdapterCallback = basePageAdapterCallback;
    }

    @Override
    public int getCount() {
        return mBasePageAdapterCallback.getCount();
    }

    @Override
    public Object instantiateItem(ViewGroup container, int position) {
        View view = mBasePageAdapterCallback.instantiateItem(position);
        container.addView(view);
        return view;
    }

    @Override
    public void destroyItem(ViewGroup container, int position, Object object) {
        // super.destroyItem(container,position,object); 这一句要删除，否则报错
        container.removeView((View) object);
    }

    @Override
    public boolean isViewFromObject(@NonNull View view, @NonNull Object object) {
        return view == object;
    }
}
