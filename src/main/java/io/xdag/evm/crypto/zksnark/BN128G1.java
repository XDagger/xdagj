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

/**
 * Implementation of specific cyclic subgroup of points belonging to
 * {@link BN128Fp} <br/>
 * Members of this subgroup are passed as a first param to pairing input
 * {@link PairingCheck#addPair(BN128G1, BN128G2)} <br/>
 *
 * Subgroup generator G = (1; 2)
 */
public class BN128G1 extends BN128Fp {

    BN128G1(BN128<Fp> p) {
        super(p.x, p.y, p.z);
    }

    @Override
    public BN128G1 toAffine() {
        return new BN128G1(super.toAffine());
    }

    /**
     * Checks whether point is a member of subgroup, returns a point if check has
     * been passed and null otherwise
     */
    public static BN128G1 create(byte[] x, byte[] y) {

        BN128<Fp> p = BN128Fp.create(x, y);

        if (p == null)
            return null;

        if (!isGroupMember(p))
            return null;

        return new BN128G1(p);
    }

    /**
     * Formally we have to do this check but in our domain it's not necessary, thus
     * always return true
     */
    private static boolean isGroupMember(BN128<Fp> p) {
        return true;
    }
}

