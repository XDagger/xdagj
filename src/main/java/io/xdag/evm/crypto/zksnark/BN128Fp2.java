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
 * Definition of {@link BN128} over F_p2, where "p" equals {@link Params#P}
 * <br/>
 *
 * Curve equation: <br/>
 * Y^2 = X^3 + b, where "b" equals {@link Params#B_Fp2} <br/>
 */
public class BN128Fp2 extends BN128<Fp2> {

    // the point at infinity
    static final BN128<Fp2> ZERO = new BN128Fp2(Fp2.ZERO, Fp2.ZERO, Fp2.ZERO);

    protected BN128Fp2(Fp2 x, Fp2 y, Fp2 z) {
        super(x, y, z);
    }

    @Override
    protected BN128<Fp2> zero() {
        return ZERO;
    }

    @Override
    protected BN128<Fp2> instance(Fp2 x, Fp2 y, Fp2 z) {
        return new BN128Fp2(x, y, z);
    }

    @Override
    protected Fp2 b() {
        return Params.B_Fp2;
    }

    @Override
    protected Fp2 one() {
        return Fp2._1;
    }

    protected BN128Fp2(BigInteger a, BigInteger b, BigInteger c, BigInteger d) {
        super(Fp2.create(a, b), Fp2.create(c, d), Fp2._1);
    }

    /**
     * Checks whether provided data are coordinates of a point on the curve, then
     * checks if this point is a member of subgroup of order "r" and if checks have
     * been passed it returns a point, otherwise returns null
     */
    public static BN128<Fp2> create(byte[] aa, byte[] bb, byte[] cc, byte[] dd) {

        Fp2 x = Fp2.create(aa, bb);
        Fp2 y = Fp2.create(cc, dd);

        // check for point at infinity
        if (x.isZero() && y.isZero()) {
            return ZERO;
        }

        BN128<Fp2> p = new BN128Fp2(x, y, Fp2._1);

        // check whether point is a valid one
        if (p.isValid()) {
            return p;
        } else {
            return null;
        }
    }
}
