package io.xdag.utils;

import java.math.BigDecimal;

import org.junit.Test;
import org.spongycastle.util.encoders.Hex;

public class BasicUtilsTest {

    @Test
    public void xdag2amount() {

        BigDecimal a = BigDecimal.valueOf(972.8);
        System.out.println(BasicUtils.xdag2amount(a.doubleValue()));

        BigDecimal b = BigDecimal.valueOf(51.2);
        System.out.println(BasicUtils.xdag2amount(b.doubleValue()));

        BigDecimal c = BigDecimal.valueOf(100);
        System.out.println(BasicUtils.xdag2amount(c.doubleValue()));
    }

    @Test
    public void amount2xdag() {

        long a = 4178144185548L;
        // 3CC CCCC CCCD?
        System.out.println(BasicUtils.amount2xdag(a));

        long b = 219902325556L;
        // 3333333334
        System.out.println(BasicUtils.amount2xdag(b));

//        long c = 4398046511104L;
        // 400 0000 0000?
        System.out.println(BasicUtils.amount2xdag(6400000000L));

//        long d = 4398046511106L;
        // 400 0000 0001
        System.out.println(BasicUtils.amount2xdag(6400000000L));
    }

    @Test
    public void xdag_diff2logTest() {
        double res = BasicUtils.xdag_diff2log(
                BasicUtils.getDiffByHash(
                        Hex.decode("00000021c468294605ebcf8ce9462026caf42941ca82373e6ca5802d1fe339c8")));

        System.out.println(res);
    }

    @Test
    public void xdag_hashrate() {
//        BigInteger diff = BasicUtils.getDiffByHash(
//                Hex.decode("00000021c468294605ebcf8ce9462026caf42941ca82373e6ca5802d1fe339c8"));
    }

    @Test
    public void xdag_log_difficulty2hashrateTest() {
        double res = BasicUtils.xdag_log_difficulty2hashrate(100);
        System.out.println("this is res :" + res);
    }
}
