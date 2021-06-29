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

import static io.xdag.evm.crypto.zksnark.Params.B_Fp;

/**
 * Definition of {@link BN128} over F_p, where "p" equals {@link Params#P} <br/>
 *
 * Curve equation: <br/>
 * Y^2 = X^3 + b, where "b" equals {@link Params#B_Fp} <br/>
 */
public class BN128Fp extends BN128<Fp> {

    // the point at infinity
    static final BN128<Fp> ZERO = new BN128Fp(Fp.ZERO, Fp.ZERO, Fp.ZERO);

    protected BN128Fp(Fp x, Fp y, Fp z) {
        super(x, y, z);
    }

    @Override
    protected BN128<Fp> zero() {
        return ZERO;
    }

    @Override
    protected BN128<Fp> instance(Fp x, Fp y, Fp z) {
        return new BN128Fp(x, y, z);
    }

    @Override
    protected Fp b() {
        return B_Fp;
    }

    @Override
    protected Fp one() {
        return Fp._1;
    }

    /**
     * Checks whether x and y belong to Fp, then checks whether point with (x; y)
     * coordinates lays on the curve.
     *
     * Returns new point if all checks have been passed, otherwise returns null
     */
    public static BN128<Fp> create(byte[] xx, byte[] yy) {

        Fp x = Fp.create(xx);
        Fp y = Fp.create(yy);

        // check for point at infinity
        if (x.isZero() && y.isZero()) {
            return ZERO;
        }

        BN128<Fp> p = new BN128Fp(x, y, Fp._1);

        // check whether point is a valid one
        if (p.isValid()) {
            return p;
        } else {
            return null;
        }
    }
}

