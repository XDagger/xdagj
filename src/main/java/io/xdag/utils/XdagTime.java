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

import java.util.Date;
import org.apache.commons.lang3.time.FastDateFormat;

public class XdagTime {

    /**
     * Get current XDAG timestamp
     */
    public static long getCurrentTimestamp() {
        long time_ms = System.currentTimeMillis();
        return msToXdagtimestamp(time_ms);
    }

    /**
     * Convert milliseconds to XDAG timestamp
     */
    public static long msToXdagtimestamp(long ms) {
        double ms_tmp = (double) (ms << 10);
        return (long) Math.ceil(ms_tmp / 1000 + 0.5);
    }

    /**
     * Convert XDAG timestamp to milliseconds
     */
    public static long xdagTimestampToMs(long timestamp) {
        return (timestamp * 1000) >> 10;
    }

    /**
     * Get the epoch number for a given timestamp
     */
    public static long getEpoch(long time) {
        return time >> 16;
    }

    /**
     * Get the last timestamp of the epoch for a given timestamp
     * Mainly used for mainblock
     */
    public static long getEndOfEpoch(long time) {
        return time | 0xffff;
    }

    /**
     * Get the last timestamp of current epoch
     */
    public static long getMainTime() {
        return getEndOfEpoch(getCurrentTimestamp());
    }

    /**
     * Check if the timestamp is at the end of an epoch
     */
    public static boolean isEndOfEpoch(long time) {
        return (time & 0xffff) == 0xffff;
    }

    /**
     * Get current epoch number
     */
    public static long getCurrentEpoch() {
        return getEpoch(getCurrentTimestamp());
    }

    /**
     * Format date to string in "yyyy-MM-dd HH:mm:ss" pattern
     */
    public static String format(Date date) {
        return FastDateFormat.getInstance("yyyy-MM-dd HH:mm:ss").format(date);
    }

}
