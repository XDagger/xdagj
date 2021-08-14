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
