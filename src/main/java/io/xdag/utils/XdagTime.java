/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2020-2030 The XdagJ Developers
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
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

    public static long xdagTimestampToMs(long timestamp) {
        return (timestamp * 1000) >> 10;
    }

    /** 获取该时间戳所属的epoch */
    public static long getEpoch(long time) {
        return time >> 16;
    }

    /** 获取时间戳所属epoch的最后一个时间戳 主要用于mainblock */
    public static long getEndOfEpoch(long time) {
        return time | 0xffff;
    }

    /** 获取当前时间所属epoch的最后一个时间戳 */
    public static long getMainTime() {
        return getEndOfEpoch(getCurrentTimestamp());
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
