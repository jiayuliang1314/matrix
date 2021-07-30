package sample.tencent.matrix.zp.base;

import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

public abstract class BaseFragment<V extends BaseViewModel> extends Fragment {
    public static final int NET_ERROR_CODE_GREENHOUSE_ERROR = 9200;
    public static final int NET_ERROR_CODE_APPLY_WITH_RESUME = 9210;
    public static final int NET_ERROR_CODE_INVITE_GEEK_IN_ROOM_ERROR = 9400;
    private static final String TAG = "BaseFragment";
    public boolean findInfoMissing = false;
    public boolean findInfoError = false;
    private BaseActivity mActivity;
    private View mRootView;
    //    private T mViewDataBinding;
    private V mViewModel;

    /**
     * Override for set binding variable
     *
     * @return variable id
     */
//    public abstract int getBindingVariable();

    /**
     * @return layout resource id
     */
    public abstract @LayoutRes
    int getLayoutId();

    public final FragmentActivity getBaseFragmentActivity() {
        return (FragmentActivity) getActivity();
    }

    /**
     * Override for set view model
     *
     * @return view model instance
     */
    public abstract V getViewModel();

//    public abstract int getCallbackVariable();

    public abstract Object getCallback();

    @Deprecated
    public BaseActivity getBaseActivity() {
        return mActivity;
    }

//    public T getViewDataBinding() {
//        return mViewDataBinding;
//    }


    public View getRootView() {
        return mRootView;
    }

    protected void initView() {
    }

    public void getData() {
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof BaseActivity) {
            BaseActivity activity = (BaseActivity) context;
            this.mActivity = activity;
            activity.onFragmentAttached();
        }

    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mViewModel = getViewModel();
        setHasOptionsMenu(false);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mRootView = LayoutInflater.from(getActivity()).inflate(getLayoutId(), null, false);
        initView();
        getData();
        return mRootView;
    }

    @Override
    public void onDetach() {
        mActivity = null;
        super.onDetach();
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
    }

    //region util
    public void setStatusbarColor(int color) {
        if (getActivity() != null && getActivity().getWindow() != null) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    getActivity().getWindow().setStatusBarColor(color);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
    //endregion

    protected void startFragment(Fragment fragment) {
        BaseAppCompatActivity baseFragmentActivity = (BaseAppCompatActivity)this.getBaseFragmentActivity();
        if (baseFragmentActivity != null) {
            baseFragmentActivity.startFragmentWithNoAnimation(fragment);
        } else {
            Log.e("QMUIFragment", "startFragment null:" + this);
        }
    }

    public interface Callback {

        void onFragmentAttached();

        void onFragmentDetached(String tag);
    }
}
