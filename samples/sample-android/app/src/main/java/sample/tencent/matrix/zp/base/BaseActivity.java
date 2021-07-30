package sample.tencent.matrix.zp.base;

import android.annotation.TargetApi;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.LayoutRes;
import androidx.annotation.Nullable;
//import androidx.databinding.DataBindingUtil;
//import androidx.databinding.ViewDataBinding;

//import io.reactivex.disposables.CompositeDisposable;
//import io.reactivex.disposables.Disposable;

public abstract class BaseActivity<V extends BaseViewModel> extends BaseAppCompatActivity implements BaseFragment.Callback {
//    protected CompositeDisposable mCompositeDisposable;
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
    public abstract
    @LayoutRes
    int getLayoutId();

    /**
     * Override for set view model
     *
     * @return view model instance
     */
    public abstract V getViewModel();

//    public abstract int getCallbackVariable();

    public abstract Object getCallback();

    public abstract void initStatusBar();

    public abstract void initStatusBarView();


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initStatusBar();
        performDataBinding();
        initStatusBarView();
    }

    private void performDataBinding() {
//        mViewDataBinding = DataBindingUtil.setContentView(this, getLayoutId());
        setContentView(getLayoutId());
        this.mViewModel = mViewModel == null ? getViewModel() : mViewModel;
//        mViewDataBinding.setVariable(getBindingVariable(), mViewModel);
//        mViewDataBinding.setVariable(getCallbackVariable(), getCallback());
//        mViewDataBinding.executePendingBindings();
    }

//    public T getViewDataBinding() {
//        return mViewDataBinding;
//    }

    @TargetApi(Build.VERSION_CODES.M)
    public boolean hasPermission(String permission) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
                checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }

//    protected void addDisposable(Disposable disposable) {
//        if (mCompositeDisposable == null) {
//            mCompositeDisposable = new CompositeDisposable();
//        }
//        mCompositeDisposable.add(disposable);
//    }

    @Override
    protected void onDestroy() {
//        if (mCompositeDisposable != null) {
//            mCompositeDisposable.clear();
//        }
        super.onDestroy();
    }

    @Override
    public void onFragmentAttached() {

    }

    @Override
    public void onFragmentDetached(String tag) {

    }

//    public void showGeneralNotification(String text) {
//        showNotification(text, CustomNotificationTop.GENERAL);
//    }
//
//    public void showSuccessNotification(String text) {
//        showNotification(text, CustomNotificationTop.SUCCESS);
//    }
//
//    public void showDebugNotification(String text) {
//        showNotification(text, CustomNotificationTop.DEBUG);
//    }
//
//    public void showUrgentNotification(String text) {
//        showNotification(text, CustomNotificationTop.URGENT);
//    }
//
//    private void showNotification(String text, int type) {
//        if (TextUtils.isEmpty(text)) {
//            return;
//        }
//        CustomNotificationTop customNotificationTop = new CustomNotificationTop(this);
//        switch (type) {
//            case CustomNotificationTop.DEBUG://case CustomNotificationTop.SUCCESS:
//                customNotificationTop.setText(text).setType(CustomNotificationTop.DEBUG).setOnClickListener(null).show(getViewDataBinding().getRoot());
//                break;
//            case CustomNotificationTop.GENERAL:
//                customNotificationTop.setText(text).setType(CustomNotificationTop.GENERAL).setOnClickListener(null).show(getViewDataBinding().getRoot());
//                break;
//            case CustomNotificationTop.URGENT:
//                customNotificationTop.setText(text).setType(CustomNotificationTop.URGENT).setOnClickListener(null).show(getViewDataBinding().getRoot());
//                break;
//        }
//    }

//    public void showNotification(ApiResponse apiResponse) {
//        if (apiResponse == null || TextUtils.isEmpty(apiResponse.getMessage())) {
//            return;
//        }
//        if (apiResponse.getCode() == 0) {//成功由app端负责
////            showSuccessNotification(apiResponse.getMessage());
//        } else {
//            switch (apiResponse.getErrorType()) {
//                case CustomNotificationTop.DEBUG://debug
//                    showDebugNotification(apiResponse.getMessage());
//                    break;
//                case CustomNotificationTop.GENERAL:
//                    showGeneralNotification(apiResponse.getMessage());
//                    break;
//                case CustomNotificationTop.URGENT:
//                    showUrgentNotification(apiResponse.getMessage());
//                    break;
//            }
//        }
//    }

//    public void setTopBarTypeFace(QMUICollapsingTopBarLayout collapsingTopbarLayout) {
//        Typeface typeFaceHold = Typeface.createFromAsset(
//                getAssets(), getString(R.string.screenrecorder_base_font_name_ibmplexsans_semibold));
//        collapsingTopbarLayout.setCollapsedTitleTypeface(typeFaceHold);
//        collapsingTopbarLayout.setExpandedTitleTypeface(typeFaceHold);
//    }

}
