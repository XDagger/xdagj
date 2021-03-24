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

import org.bouncycastle.util.encoders.Hex;
import org.junit.Test;

import java.math.BigDecimal;

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
