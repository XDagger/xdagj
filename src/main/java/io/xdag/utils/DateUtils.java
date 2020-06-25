package io.xdag.utils;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

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
