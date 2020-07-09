package io.xdag.utils;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class BigDecimalUtils {

  /** 默认的除法运算的精度 */
  private static final int DEF_DIV_SCALE = 6;

  public static double add(double v1, double v2) {
    BigDecimal b1 = BigDecimal.valueOf(v1);
    BigDecimal b2 = BigDecimal.valueOf(v2);
    return b1.add(b2).doubleValue();
  }

  public static double sub(double v1, double v2) {
    BigDecimal b1 = BigDecimal.valueOf(v1);
    BigDecimal b2 = BigDecimal.valueOf(v2);
    return b1.subtract(b2).doubleValue();
  }

  public static double mul(double v1, double v2) {
    BigDecimal b1 = BigDecimal.valueOf(v1);
    BigDecimal b2 = BigDecimal.valueOf(v2);
    return b1.multiply(b2).doubleValue();
  }

  public static double div(double v1, double v2) {
    return div(v1, v2, DEF_DIV_SCALE);
  }

  /**
   * @param v1 被除数
   * @param v2 除数
   * @param scale 精确到小数点后几位
   * @return 四舍五入后的结果
   */
  public static double div(double v1, double v2, int scale) {

    if (scale < 0) {
      throw new IllegalArgumentException("The scale must be a positive integer or zero");
    }

    BigDecimal b1 = BigDecimal.valueOf(v1);
    BigDecimal b2 = BigDecimal.valueOf(v2);
    ;
    return b1.divide(b2, scale, RoundingMode.HALF_UP).doubleValue();
  }

  /**
   * 对一个double 提供精确到某位小数点的精确结果
   *
   * @param value 数字
   * @param scale 要保留的小数点的后几位
   * @return 四舍五入后的结果
   */
  public static double round(double value, int scale) {
    if (scale < 0) {
      throw new IllegalArgumentException("The scale must be a positive integer or zero");
    }

    BigDecimal b = BigDecimal.valueOf(value);
    BigDecimal one = BigDecimal.ONE;
    return b.divide(one, scale, RoundingMode.HALF_UP).doubleValue();
  }

  public static long mul(long v1, double v2) {
    BigDecimal b1 = BigDecimal.valueOf(v1);
    BigDecimal b2 = BigDecimal.valueOf(v2);
    return b1.multiply(b2).longValue();
  }
}
