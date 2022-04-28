package io.xdag.core;

import lombok.Getter;
import lombok.Setter;

import java.math.BigInteger;
import java.util.Arrays;

import static io.xdag.config.Constants.HASH_RATE_LAST_MAX_TIME;

@Getter
@Setter
public class XdagExtStats {
    private BigInteger[] hashRateTotal = new BigInteger[HASH_RATE_LAST_MAX_TIME];
    private BigInteger[] hashRateOurs = new BigInteger[HASH_RATE_LAST_MAX_TIME];
    private long hashrate_last_time;

    public XdagExtStats() {
        Arrays.fill(hashRateTotal,BigInteger.ZERO);
        Arrays.fill(hashRateOurs,BigInteger.ZERO);
        hashrate_last_time = 0L;
    }
}
