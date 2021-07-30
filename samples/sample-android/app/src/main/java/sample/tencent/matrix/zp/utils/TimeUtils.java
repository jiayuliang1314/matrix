package sample.tencent.matrix.zp.utils;

import java.text.SimpleDateFormat;
import java.util.Date;

public class TimeUtils {
    public static String formatTime(long time) {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("MM/dd HH:mm:ss");
        if (TimeUtils.isToday(time)) {
            simpleDateFormat = new SimpleDateFormat("HH:mm:ss");
        }
        Date date = new Date(time);
        return simpleDateFormat.format(date);
    }

    public static boolean isToday(long time) {
        return Math.abs(System.currentTimeMillis() - time) < 60L * 60 * 1000 * 24;
    }

    public static boolean isThisWeek(long time) {
        return Math.abs(System.currentTimeMillis() - time) < 60L * 60 * 1000 * 24 * 7;
    }

    public static boolean isThisMonth(long time) {
        return Math.abs(System.currentTimeMillis() - time) < 60L * 60 * 1000 * 24 * 31;
    }
}
