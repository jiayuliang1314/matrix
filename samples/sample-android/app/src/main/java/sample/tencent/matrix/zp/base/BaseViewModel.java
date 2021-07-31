package sample.tencent.matrix.zp.base;

//import androidx.databinding.ObservableBoolean;
import androidx.lifecycle.ViewModel;

//import com.stone.cold.screenrecorder.rain.base.arch.net.ApiResponse;
//import com.stone.cold.screenrecorder.rain.base.arch.rxjava.AppSchedulerProvider;
//import com.stone.cold.screenrecorder.rain.base.arch.rxjava.SchedulerProvider;


//import io.reactivex.Flowable;
//import io.reactivex.disposables.CompositeDisposable;
//import io.reactivex.disposables.Disposable;
//import io.reactivex.functions.Action;
//import io.reactivex.functions.Consumer;
//import io.reactivex.internal.functions.Functions;

//import static com.stone.cold.screenrecorder.rain.base.arch.rxjava.BaseSubscriber.ERROR_TYPE;
//import wavely.base.Rx.AppSchedulerProvider;
//import wavely.base.data.BaseRepository;
//import wavely.base.utils.MixpanelAnalysis;
//import wavely.sdk.net.ApiResponse;
//import wavely.sdk.rx.SchedulerProvider;
//import wavely.sdk.utils.ThrowableCatcher;

//import static wavely.sdk.net.BaseSubscriber.ERROR_TYPE;

public abstract class BaseViewModel extends ViewModel {
//    public final ObservableBoolean mLoading = new ObservableBoolean(false);
//    private final SchedulerProvider mSchedulerProvider;
//    private final MediatorLiveData<ApiResponse> mNetLiveData = new MediatorLiveData<>();

//    protected CompositeDisposable mCompositeDisposable;

    public BaseViewModel() {
//        mSchedulerProvider = new AppSchedulerProvider();
//        mCompositeDisposable = new CompositeDisposable();
    }

//    public LiveData<ApiResponse> getNetLiveData() {
//        return mNetLiveData;
//    }

//    @MainThread
//    public void resetError() {
//        mNetLiveData.setValue(null);
//    }

    @Override
    protected void onCleared() {
//        mCompositeDisposable.dispose();
        super.onCleared();
    }

    public void clearNet() {
//        if (mCompositeDisposable != null) {
//            mCompositeDisposable.dispose();
//        }
    }

//    public ObservableBoolean isLoading() {
//        return mLoading;
//    }

//    public SchedulerProvider getSchedulerProvider() {
//        return mSchedulerProvider;
//    }

    public void setLoading(boolean isLoading) {
//        mLoading.set(isLoading);
    }
//
//    protected <T> void addSubscription(Flowable<T> observable, Consumer<T> consumer, Consumer<? super Throwable> onError) {
//        if (observable != null) {
//            addSubscription(observable, consumer, onError, null, Functions.EMPTY_ACTION);
//        }
//    }
//
//    protected <T> void addSubscription(Flowable<T> observable, Consumer<T> before, Consumer<T> consumer, Consumer<? super Throwable> onError) {
//        addSubscription(observable, consumer, onError, before, Functions.EMPTY_ACTION);
//    }

    /**
     * @param flowable   发射器
     * @param consumer
     * @param onError
     * @param before
     * @param onComplete
     * @param <T>
     */
//    protected <T> void addSubscription(Flowable<T> flowable, Consumer<T> consumer, Consumer<? super Throwable> onError, Consumer<T> before, Action onComplete) {
//        flowable = flowable.subscribeOn(getSchedulerProvider().io());
//        if (before != null) {
//            flowable = flowable.observeOn(getSchedulerProvider().io()).doOnNext(before);
//        }
//        Disposable disposable = flowable
//                .observeOn(getSchedulerProvider().ui(), true)
//                .subscribe(t -> {
//                    if (t != null && t instanceof ApiResponse) {
//                        mNetLiveData.postValue((ApiResponse) t);
////                        Log.i("ApiResponse", "ApiResponse error " + (ApiResponse) (t));
//
//                        if (((ApiResponse) t).getCode() > 0) {
//                            try {
//                                JSONObject props = new JSONObject();
//                                props.put("Error_Path", "");
//                                props.put("Error_Code", "" + ((ApiResponse) t).getCode());
//                                props.put("Error_Msg", "" + ((ApiResponse) t).getMessage());
//                                //props.put("no_exp", getViewModel().workExperienceShow.get());
////                                MixpanelAnalysis.getInstance()
////                                        .logEvent("Network_Fail", props);
//                            } catch (Exception e) {
//                                e.printStackTrace();
//                            }
//                        }
//                    }
//                    consumer.accept(t);
//                }, throwable -> {
//                    if (throwable instanceof IOException) {
//                        ApiResponse response = new ApiResponse<>();
//                        response.setCode(404);
//                        response.setErrorType(ERROR_TYPE);
//                        response.setMessage("Oops, network error. Please try again.");
//                        mNetLiveData.postValue(response);
//
//                        try {
//                            JSONObject props = new JSONObject();
//                            props.put("Fail_Code", "" + response.getCode());
//                            props.put("Fail_Msg", "" + response.getMessage());
//                            //props.put("no_exp", getViewModel().workExperienceShow.get());
////                            MixpanelAnalysis.getInstance()
////                                    .logEvent("Network_Fail", props);
//                        } catch (Exception e) {
//                            e.printStackTrace();
//                        }
//                    }
//                    if (!TextUtils.isEmpty(throwable.getMessage())
//                            && !throwable.getMessage().equals("1016")   //suspend不在这处理
//                            && throwable.getMessage().contains("#")     //RetrofitUtil response返回有误的时候
//                    ) {
//                        String message = throwable.getMessage();
//                        String[] strings = message.split("#");
//                        ApiResponse response = new ApiResponse<>();
//                        response.setCode(404);
//                        try {
//                            response.setErrorType(Integer.parseInt(strings[1]));
//                        } catch (Exception e) {
//                            e.printStackTrace();
//                        }
//                        response.setMessage(strings[0]);
//                        mNetLiveData.postValue(response);
//                    }
//                    mLoading.set(false);
////                    ThrowableCatcher.reportThrowable(throwable);
//                    onError.accept(throwable);
//                }, onComplete);
//        if (mCompositeDisposable.isDisposed()) {
//            mCompositeDisposable = new CompositeDisposable();
//        }
//        mCompositeDisposable.add(disposable);
//    }

//    public LiveData<ApiResponse> getMostMatchExpectId(String jobId) {
//        MutableLiveData<ApiResponse> liveData = new MutableLiveData<>();
//        addSubscription(BaseRepository.getInstance().getMostMatchExpectId(jobId), OnItemOnClickListener -> liveData.setValue(OnItemOnClickListener), throwable -> {
//            liveData.setValue(null);
//        });
//        return liveData;
//    }
//
//    public LiveData<ApiResponse> getMostMatchJobId(String expectId) {
//        MutableLiveData<ApiResponse> liveData = new MutableLiveData<>();
//        addSubscription(BaseRepository.getInstance().getMostMatchJobId(expectId), OnItemOnClickListener -> liveData.setValue(OnItemOnClickListener), throwable -> {
//            liveData.setValue(null);
//        });
//        return liveData;
//    }
}
