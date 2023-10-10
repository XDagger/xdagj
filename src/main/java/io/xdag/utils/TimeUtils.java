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

import java.io.IOException;
import java.net.InetAddress;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.net.ntp.NTPUDPClient;
import org.apache.commons.net.ntp.TimeInfo;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class TimeUtils {
    private static final ThreadFactory factory = new ThreadFactory() {
        private final AtomicInteger cnt = new AtomicInteger(0);

        @Override
        public Thread newThread(Runnable r) {
            return new Thread(r, "ntpUpdate-" + cnt.getAndIncrement());
        }
    };

    public static final String DEFAULT_DURATION_FORMAT = "%02d:%02d:%02d";
    public static final String DEFAULT_TIMESTAMP_FORMAT = "yyyy-MM-dd HH:mm:ss";
    public static final String NTP_POOL = "pool.ntp.org";
    private static final ScheduledExecutorService ntpUpdateTimer = Executors.newSingleThreadScheduledExecutor(factory);
    private static final int TIME_RETRIES = 5;

    private static long timeOffsetFromNtp = 0;

    public static void startNtpProcess() {
        // inline run at start
        updateNetworkTimeOffset();
        // update time every hour
        ntpUpdateTimer.scheduleAtFixedRate(TimeUtils::updateNetworkTimeOffset, 60 * 60 * 1000, 60, TimeUnit.MINUTES);
    }

    /**
     * Returns a human-readable duration
     *
     * @param duration
     *            duration object to be formatted
     * @return formatted duration in 00:00:00
     */
    public static String formatDuration(Duration duration) {
        long seconds = duration.getSeconds();
        return String.format(DEFAULT_DURATION_FORMAT, seconds / 3600, (seconds % 3600) / 60, (seconds % 60));
    }

    /**
     * @param timestamp
     *            timestamp in milliseconds to be formatter
     * @return formatted timestamp in yyyy-MM-dd HH:mm:ss
     */
    public static String formatTimestamp(Long timestamp) {
        return new SimpleDateFormat(DEFAULT_TIMESTAMP_FORMAT).format(new Date(timestamp));
    }

    public static long currentTimeMillis() {
        return System.currentTimeMillis() + timeOffsetFromNtp;
    }

    /**
     * Update time offset from NTP
     */
    private static void updateNetworkTimeOffset() {
        NTPUDPClient client = new NTPUDPClient();
        client.setDefaultTimeout(10000);
        for (int i = 0; i < TIME_RETRIES; i++) {
            try {
                client.open();
                InetAddress hostAddr = InetAddress.getByName(NTP_POOL);
                TimeInfo info = client.getTime(hostAddr);
                info.computeDetails();

                // update our current internal state
                timeOffsetFromNtp = info.getOffset();
                // break from retry loop
                return;
            } catch (IOException e) {
                log.warn("Unable to retrieve NTP time");
            } finally {
                client.close();
            }
        }
    }

    public static long getTimeOffsetFromNtp() {
        return timeOffsetFromNtp;
    }

    public static void shutdownNtpUpdater() {
        ntpUpdateTimer.shutdownNow();
    }

}
