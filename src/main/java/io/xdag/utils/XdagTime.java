package io.xdag.utils;

public class XdagTime {

  /** 获取当前的xdag时间戳 */
  public static long getCurrentTimestamp() {
    long time_ms = System.currentTimeMillis();
    double ms_tmp = (double) (time_ms << 10);
    long time_xdag = (long) Math.ceil(ms_tmp / 1000 + 0.5);
    return time_xdag;
  }

  /** 把毫秒转为xdag时间戳 */
  public static long msToXdagtimestamp(long ms) {
    double ms_tmp = (double) (ms << 10);
    long xdag_timestamp = (long) Math.ceil(ms_tmp / 1000 + 0.5);
    return xdag_timestamp;
  }

  public static long xdagtimestampToMs(long timestamp) {
    long ms = timestamp * 1000;
    return (ms >> 10) - 1;
  }

  /** 获取该时间戳所属的epoch */
  public static long getEpoch(long time) {
    long epoch = time >> 16;
    return epoch;
  }

  /** 获取时间戳所属epoch的最后一个时间戳 主要用于mainblock */
  public static long getEndOfEpoch(long time) {
    long result = time | 0xffff;
    return result;
  }

  /** 获取当前时间所属epoch的最后一个时间戳 */
  public static long getMainTime() {
    return getEndOfEpoch(getCurrentTimestamp());
  }

  public static long MainTime() {
    long time = getCurrentTimestamp();
    return time >> 16;
  }

  public static boolean isEndOfEpoch(long time) {
    return (time & 0xffff) == 0xffff;
  }

  public static long getCurrentEpoch() {
    return getEpoch(getCurrentTimestamp());
  }

  public static int compareEpoch(long time1, long time2) {
    if (getEpoch(time1) > getEpoch(time2)) {
      return 1;
    } else if (getEpoch(time1) == getEpoch(time2)) {
      return 0;
    } else {
      return -1;
    }
  }

  public static boolean isStartOfEpoch(long timestamp) {
    return (timestamp & 0xff) == 0x00;
  }
}
