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
package io.xdag.snapshot.config;

import java.nio.charset.StandardCharsets;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.MutableBytes;

public class SnapShotKeys {

    public final static String SNAPTSHOT_KEY_TIME = "g_snapshot_time";
    public final static String SNAPTSHOT_KEY_STATE = "g_xdag_state";
    public final static String SNAPTSHOT_KEY_STATS = "g_xdag_stats";
    public final static String SNAPTSHOT_KEY_EXTSTATS = "g_xdag_extstats";

    public final static String SNAPSHOT_KEY_STATS_MAIN = "g_snapshot_main";
    public final static String SNAPSHOT_PRE_SEED = "pre_seed";


    public static MutableBytes getMutableBytesByKey(String key) {
        byte[] bytes = key.getBytes(StandardCharsets.UTF_8);
        MutableBytes resKey = MutableBytes.create(bytes.length + 1);
        resKey.set(0, Bytes.wrap(bytes));
        return resKey;
    }

    public static MutableBytes getMutableBytesByKey_(String key) {
        byte[] bytes = key.getBytes(StandardCharsets.UTF_8);
        MutableBytes resKey = MutableBytes.create(bytes.length);
        resKey.set(0, Bytes.wrap(bytes));
        return resKey;
    }
}
