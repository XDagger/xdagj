package io.xdag.utils;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;


/**
 * @author Mrx-1
 * @Classname TimeUtils
 * @Description TODO
 * @Date 2020/5/30 16:32
 * @Created by Myron
 */
public class DateUtils {


    public static Date getCurrentTime() {
        Calendar calendar = Calendar.getInstance();
        return calendar.getTime();
    }

    public static String dateToString(Date date) {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");
        return simpleDateFormat.format(date);

    }


}
