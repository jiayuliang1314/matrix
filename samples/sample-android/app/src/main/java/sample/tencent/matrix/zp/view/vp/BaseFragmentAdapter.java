package sample.tencent.matrix.zp.view.vp;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;

import java.util.ArrayList;
import java.util.List;

/**
 * @author jia on 2018/5/21.
 * PagerAdapter：缓存三个，通过重写instantiateItem和destroyItem达到创建和销毁view的目的。
 * FragmentPagerAdapter：内部通过FragmentManager来持久化每一个Fragment，在destroyItem方法调用时只是detach对应的Fragment，并没有真正移除！
 * FragmentPagerStateAdapter：内部通过FragmentManager来管理每一个Fragment，在destroyItem方法 调用时移除对应的Fragment。
 * <p>
 * 当使用FragmentStatePagerAdapter时，如果Fragment不显示，那么Fragment对象会被销毁，（滑过后会保存当前界面，以及下一个界面和上一个界面（如果有），最多保存3个，其他会被销毁掉）
 * 但在回调onDestroy()方法之前会回调onSaveInstanceState(Bundle outState)方法来保存Fragment的状态，下次Fragment显示时通过onCreate(Bundle savedInstanceState)把存储的状态值取出来，FragmentStatePagerAdapter 比较适合页面比较多的情况，像一个页面的ListView 。
 * <p>
 * PagerAdapter：当所要展示的视图比较简单时适用
 * FragmentPagerAdapter：当所要展示的视图是Fragment，并且数量比较少时适用
 * FragmentStatePagerAdapter：当所要展示的视图是Fragment，并且数量比较多时适用
 */
public class BaseFragmentAdapter extends FragmentPagerAdapter {

    private List<Fragment> fragments = new ArrayList<>();

    public BaseFragmentAdapter(FragmentManager fm, List<Fragment> fragments) {
        super(fm);
        this.fragments = fragments;
    }

    @Override
    public Fragment getItem(int position) {
        if (fragments == null || fragments.size() <= 0) {
            return null;
        }
        Fragment f = fragments.get(position);
        return f;
    }

    @Override
    public int getCount() {
        if (fragments == null) {
            return 0;
        }
        return fragments.size();
    }

    @Override
    public int getItemPosition(@NonNull Object object) {
        return POSITION_NONE;
    }
}
