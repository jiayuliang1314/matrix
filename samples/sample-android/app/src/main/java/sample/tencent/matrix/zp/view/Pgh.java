package sample.tencent.matrix.zp.view;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;

import sample.tencent.matrix.R;

//import com.stone.cold.screenrecorder.rain.R;
//import com.stone.cold.screenrecorder.rain.screenrecorder.ut.ae;
//import com.inshot.screenrecorder.utils.ae;
//import videoeditor.videorecorder.screenrecorder.R;

public class Pgh extends View {
    private Context a;
    private int b = 0;
    private float c = 0.0f;
    private float d = 60.0f;
    private int e = getResources().getColor(R.color.screenrecorder_au);
    private int f = getResources().getColor(R.color.screenrecorder_ct);
    private boolean g = false;
    private int h = 6;
    private int i = getResources().getColor(R.color.screenrecorder_c0);
    private int j = 1200;
    private int k = 30;
    private int l = 5;
    private boolean m = true;
    private float n = 0.0f;
    private Paint o;
    private LinearGradient p;
    private RectF q;
    private RectF r;
    private Interpolator s;
    private a t;
    private float u = 100.0f;

    public Pgh(Context context) {
        super(context);
        this.a = context;
        a(context, null);
        a();
    }

    public Pgh(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
        this.a = context;
        a(context, attributeSet);
        a();
    }

    public Pgh(Context context, AttributeSet attributeSet, int i2) {
        super(context, attributeSet, i2);
        this.a = context;
        a(context, attributeSet);
        a();
    }

    private void b(Canvas canvas) {
    }

    private void a(Context context, AttributeSet attributeSet) {
        TypedArray obtainStyledAttributes = context.obtainStyledAttributes(attributeSet, R.styleable.screenrecorderPgh);
        this.c = (float) obtainStyledAttributes.getInt(R.styleable.screenrecorderPgh_screenrecorderstart_progress, 0);
        this.d = (float) obtainStyledAttributes.getInt(R.styleable.screenrecorderPgh_screenrecorderend_progress, 60);
        this.e = obtainStyledAttributes.getColor(R.styleable.screenrecorderPgh_screenrecorderstart_color, getResources().getColor(R.color.screenrecorder_au));
        this.f = obtainStyledAttributes.getColor(R.styleable.screenrecorderPgh_screenrecorderend_color, getResources().getColor(R.color.screenrecorder_ct));
        this.g = obtainStyledAttributes.getBoolean(R.styleable.screenrecorderPgh_screenrecorderisTracked, false);
        this.h = obtainStyledAttributes.getDimensionPixelSize(R.styleable.screenrecorderPgh_screenrecordertrack_width, 16);
        this.b = obtainStyledAttributes.getInt(R.styleable.screenrecorderPgh_screenrecorderanimateType, 0);
        this.i = obtainStyledAttributes.getColor(R.styleable.screenrecorderPgh_screenrecordertrackColor, getResources().getColor(R.color.screenrecorder_c0));
        this.j = obtainStyledAttributes.getInt(R.styleable.screenrecorderPgh_screenrecorderprogressDuration, 1200);
        this.k = obtainStyledAttributes.getDimensionPixelSize(R.styleable.screenrecorderPgh_screenrecordercorner_radius_hgp, 5);
        this.l = obtainStyledAttributes.getDimensionPixelSize(R.styleable.screenrecorderPgh_screenrecordertext_padding_bottom, 5);
        this.m = obtainStyledAttributes.getBoolean(R.styleable.screenrecorderPgh_screenrecordertextMovedEnable, true);
        //Log.e("Moos-Progress-View", "progressDuration: " + this.j);
        obtainStyledAttributes.recycle();
        this.n = this.c;
    }

    private void a() {
        this.o = new Paint(1);
        this.o.setStyle(Paint.Style.FILL);
    }

    /* access modifiers changed from: protected */
    @Override
    public void onDraw(Canvas canvas) {
        int ooooo=noUseCode(1);

        super.onDraw(canvas);
        c();
        a(canvas);
        this.o.setShader(this.p);
        canvas.drawRoundRect(this.q, (float) this.k, (float) this.k, this.o);
        b(canvas);
    }

    /* access modifiers changed from: protected */
    @Override
    public void onSizeChanged(int i2, int i3, int i4, int i5) {
        int ooooo=noUseCode(1,1);

        super.onSizeChanged(i2, i3, i4, i5);
        LinearGradient linearGradient = new LinearGradient((float) getPaddingLeft(), (float) (getHeight() - getPaddingTop()), (float) (getWidth() - getPaddingRight()), (float) ((getHeight() / 2) + getPaddingTop() + this.h), this.e, this.f, Shader.TileMode.CLAMP);
        this.p = linearGradient;
    }

    private void a(Canvas canvas) {
        if (this.g) {
            this.o.setShader(null);
            this.o.setColor(this.i);
            canvas.drawRoundRect(this.r, (float) this.k, (float) this.k, this.o);
        }
    }

    public void setAnimateType(int i2) {
        int ooooo=noUseCode(1,2,3);

        this.b = i2;
        setObjectAnimatorType(i2);
    }

    private void setObjectAnimatorType(int i2) {
        //Log.e("Moos-Progress-View", "AnimatorType>>>>>>" + i2);
        switch (i2) {
            case 0:
                if (this.s != null) {
                    this.s = null;
                }
                this.s = new AccelerateDecelerateInterpolator();
                return;
            case 1:
                if (this.s != null) {
                    this.s = null;
                }
                this.s = new LinearInterpolator();
                return;
            case 2:
                if (this.s != null) {
                    this.s = null;
                    this.s = new AccelerateInterpolator();
                    return;
                }
                return;
            case 3:
                if (this.s != null) {
                    this.s = null;
                }
                this.s = new DecelerateInterpolator();
                return;
            case 4:
                if (this.s != null) {
                    this.s = null;
                }
                this.s = new OvershootInterpolator();
                return;
            default:
                return;
        }
    }

    public void setMaxRange(float f2,int i) {
        this.u = f2;
    }

    public float getProgress() {
        return this.n;
    }

    public void setProgress(float f2,int i) {
        this.n = f2;
        b();
    }

    public void setStartProgress(float f2) {
        this.c = f2;
        this.n = this.c;
        b();
    }

    public void setEndProgress(float f2) {
        this.d = f2;
        b();
    }

    public void setStartColor(int i2) {
        this.e = i2;
        LinearGradient linearGradient = new LinearGradient((float) getPaddingLeft(), (float) (getHeight() - getPaddingTop()), (float) (getWidth() - getPaddingRight()), (float) ((getHeight() / 2) + getPaddingTop() + this.h), this.e, this.f, Shader.TileMode.CLAMP);
        this.p = linearGradient;
        b();
    }

    public void setEndColor(int i2) {
        this.f = i2;
        LinearGradient linearGradient = new LinearGradient((float) getPaddingLeft(), (float) (getHeight() - getPaddingTop()), (float) (getWidth() - getPaddingRight()), (float) ((getHeight() / 2) + getPaddingTop() + this.h), this.e, this.f, Shader.TileMode.CLAMP);
        this.p = linearGradient;
        b();
    }

    public void setTrackWidth(int i2) {
        this.h = ae.a(this.a, (float) i2);
        b();
    }

    public void setTrackColor(int i2) {
        this.i = i2;
        b();
    }

    public void setProgressDuration(int i2) {
        this.j = i2;
    }

    public void setTrackEnabled(boolean z) {
        this.g = z;
        b();
    }

    public void setProgressTextMoved(boolean z) {
        this.m = z;
    }

    public void setProgressCornerRadius(int i2) {
        this.k = ae.a(this.a, (float) i2);
        b();
    }

    public void setProgressTextPaddingBottom(int i2) {
        this.l = ae.a(this.a, (float) i2);
    }

    private void b() {
        invalidate();
    }

    private void c() {
        this.q = new RectF(((float) getPaddingLeft()) + ((this.c * ((float) ((getWidth() - getPaddingLeft()) - getPaddingRight()))) / this.u), (float) getPaddingTop(), ((float) (getWidth() - getPaddingRight())) * (this.n / this.u), (float) (getHeight() - getPaddingBottom()));
        this.r = new RectF((float) getPaddingLeft(), (float) getPaddingTop(), (float) (getWidth() - getPaddingRight()), (float) (getHeight() - getPaddingBottom()));
    }

    public void setProgressViewUpdateListener(a aVar) {
        this.t = aVar;
    }

    public interface a {
    }
    public int noUseCode(int a){
     return a*a;
    }
    public int noUseCode(int a,int b){
     return a+a;
    }
    public int noUseCode(int a,int b,int c){
     return a+b+c;
    }
}
