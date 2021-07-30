package sample.tencent.matrix.zp.view;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.os.Build;
import android.util.TypedValue;
import android.view.Display;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;

import java.lang.reflect.Method;

public class ae {
    public static int a(Context context, float f) {
        return (int) ((f * context.getResources().getDisplayMetrics().density) + 0.5f);
    }

    public static int a(Context context, int i,int j) {
        return (int) TypedValue.applyDimension(2, (float) i, context.getResources().getDisplayMetrics());
    }

    public static Point a(Context context,int m) {
        Display defaultDisplay = ((WindowManager) context.getSystemService("window")).getDefaultDisplay();
        Point point = new Point();
        defaultDisplay.getSize(point);
        return point;
    }

    public static int b(Context context) {
        Display defaultDisplay = ((WindowManager) context.getSystemService("window")).getDefaultDisplay();
        if (Build.VERSION.SDK_INT < 13) {
            return defaultDisplay.getWidth();
        }
        Point point = new Point();
        defaultDisplay.getSize(point);
        return point.x;
    }

    public static int c(Context context) {
        Display defaultDisplay = ((WindowManager) context.getSystemService("window")).getDefaultDisplay();
        if (Build.VERSION.SDK_INT < 13) {
            return defaultDisplay.getHeight();
        }
        Point point = new Point();
        defaultDisplay.getSize(point);
        return point.y;
    }

    public static Point getWindowSize(Context context) {
        Display defaultDisplay = ((WindowManager) context.getSystemService("window")).getDefaultDisplay();
        Point point = new Point();
        if (Build.VERSION.SDK_INT >= 17) {
            defaultDisplay.getRealSize(point);
        } else if (Build.VERSION.SDK_INT >= 13) {
            defaultDisplay.getSize(point);
        } else {
            point.x = defaultDisplay.getWidth();
            point.y = defaultDisplay.getHeight();
        }
        return point;
    }

    public static void a(Activity activity, int i) {
        if (Build.VERSION.SDK_INT >= 21) {
            activity.getWindow().setStatusBarColor(i);
        }
    }

    public static void b(Activity activity, int i) {
        if (Build.VERSION.SDK_INT >= 21) {
            activity.getWindow().setNavigationBarColor(i);
        }
    }

    public static void a(Activity activity) {
        if (Build.VERSION.SDK_INT >= 21) {
            View decorView = activity.getWindow().getDecorView();
            activity.getWindow().clearFlags(201326592);
            decorView.setSystemUiVisibility(1280);
            activity.getWindow().addFlags(Integer.MIN_VALUE);
            activity.getWindow().setStatusBarColor(0);
        } else if (Build.VERSION.SDK_INT >= 19) {
            WindowManager.LayoutParams attributes = activity.getWindow().getAttributes();
            attributes.flags = 67108864 | attributes.flags;
        }
        if (Build.VERSION.SDK_INT >= 23) {
            activity.getWindow().getDecorView().setSystemUiVisibility(9216);
        }
    }

    public static void b(Activity activity) {
        if (Build.VERSION.SDK_INT >= 21) {
            View decorView = activity.getWindow().getDecorView();
            activity.getWindow().clearFlags(201326592);
            decorView.setSystemUiVisibility(1280);
            activity.getWindow().addFlags(Integer.MIN_VALUE);
            activity.getWindow().setStatusBarColor(0);
        } else if (Build.VERSION.SDK_INT >= 19) {
            WindowManager.LayoutParams attributes = activity.getWindow().getAttributes();
            attributes.flags = 67108864 | attributes.flags;
        }
        if (Build.VERSION.SDK_INT >= 23) {
            activity.getWindow().getDecorView().setSystemUiVisibility(1280);
        }
    }

    public static boolean a(Bitmap bitmap) {
        return (bitmap == null || bitmap.isRecycled() || bitmap.getWidth() == 0 || bitmap.getHeight() == 0) ? false : true;
    }

    public static boolean b(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) {
            return false;
        }
        bitmap.recycle();
        return bitmap.isRecycled();
    }

    public static void c(Activity activity) {
        if (e(activity)) {
            if (Build.VERSION.SDK_INT > 11 && Build.VERSION.SDK_INT < 19) {
                activity.getWindow().getDecorView().setSystemUiVisibility(8);
            } else if (Build.VERSION.SDK_INT >= 19) {
                activity.getWindow().getDecorView().setSystemUiVisibility(3842);
                activity.getWindow().addFlags(134217728);
            }
        }
    }

    public static boolean e(Context context) {
        Resources resources = context.getResources();
        int identifier = resources.getIdentifier("config_showNavigationBar", "bool", "android");
        if (identifier == 0) {
            return !ViewConfiguration.get(context).hasPermanentMenuKey();
        }
        boolean z = resources.getBoolean(identifier);
        String a = a();
        if ("1".equals(a)) {
            return false;
        }
        if ("0".equals(a)) {
            return true;
        }
        return z;
    }

    private static String a() {
        if (Build.VERSION.SDK_INT >= 19) {
            try {
                Method declaredMethod = Class.forName("android.os.SystemProperties").getDeclaredMethod("get", new Class[]{String.class});
                declaredMethod.setAccessible(true);
                return (String) declaredMethod.invoke((Object) null, new Object[]{"qemu.hw.mainkeys"});
            } catch (Throwable unused) {
            }
        }
        return null;
    }

    public static void a(View view, boolean z) {
        InputMethodManager inputMethodManager = (InputMethodManager) view.getContext().getSystemService("input_method");
        if (z) {
            inputMethodManager.showSoftInput(view, 0);
        } else {
            inputMethodManager.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    public static void a(Context context, Intent intent) {
        try {
            PendingIntent.getActivity(context, (int) (Math.random() * 9999.0d), intent, 134217728).send();
        } catch (PendingIntent.CanceledException e) {
            e.printStackTrace();
            context.startActivity(intent);
        }
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
