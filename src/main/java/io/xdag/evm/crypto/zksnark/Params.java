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
package io.xdag.evm.crypto.zksnark;

import java.math.BigInteger;

/**
 * Common params for BN curves, its derivatives and pairing
 */
class Params {

    /**
     * "p" field parameter of F_p, F_p2, F_p6 and F_p12
     */
    static final BigInteger P = new BigInteger("21888242871839275222246405745257275088696311157297823662689037894645226208583");

    /**
     * "r" order of {@link BN128G2} cyclic subgroup
     */
    static final BigInteger R = new BigInteger("21888242871839275222246405745257275088548364400416034343698204186575808495617");

    /**
     * "b" curve parameter for {@link BN128Fp}
     */
    static final Fp B_Fp = Fp.create(BigInteger.valueOf(3));

    /**
     * Twist parameter for the curves
     */
    static final Fp2 TWIST = Fp2.create(BigInteger.valueOf(9), BigInteger.valueOf(1));

    /**
     * "b" curve parameter for {@link BN128Fp2}
     */
    static final Fp2 B_Fp2 = B_Fp.mul(TWIST.inverse());

    static final Fp2 TWIST_MUL_BY_P_X = Fp2.create(
            new BigInteger("21575463638280843010398324269430826099269044274347216827212613867836435027261"),
            new BigInteger("10307601595873709700152284273816112264069230130616436755625194854815875713954"));

    static final Fp2 TWIST_MUL_BY_P_Y = Fp2.create(
            new BigInteger("2821565182194536844548159561693502659359617185244120367078079554186484126554"),
            new BigInteger("3505843767911556378687030309984248845540243509899259641013678093033130930403"));

    static final BigInteger PAIRING_FINAL_EXPONENT_Z = new BigInteger("4965661367192848881");
}

