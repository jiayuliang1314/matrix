package sample.tencent.matrix.zp.view.vp.kernel;

import android.view.View;

/**
 * @author jia
 */
public interface BasePageAdapterCallback {
    /**
     * 返回page数量
     *
     * @return page数量
     */
    int getCount();

    /**
     * instantiateItem
     *
     * @param position 位置
     * @return 返回position的view
     */
    View instantiateItem(int position);
}
