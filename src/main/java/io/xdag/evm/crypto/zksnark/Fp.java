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

import static io.xdag.evm.crypto.zksnark.Params.P;

import java.math.BigInteger;
import java.util.Objects;

/**
 * Arithmetic in F_p, p =
 * 21888242871839275222246405745257275088696311157297823662689037894645226208583
 */
public class Fp implements Field<Fp> {

    static final Fp ZERO = new Fp(BigInteger.ZERO);
    static final Fp _1 = new Fp(BigInteger.ONE);
    static final Fp NON_RESIDUE = new Fp(
            new BigInteger("21888242871839275222246405745257275088696311157297823662689037894645226208582"));

    static final Fp _2_INV = new Fp(BigInteger.valueOf(2).modInverse(P));

    BigInteger v;

    Fp(BigInteger v) {
        this.v = v;
    }

    @Override
    public Fp add(Fp o) {
        return new Fp(this.v.add(o.v).mod(P));
    }

    @Override
    public Fp mul(Fp o) {
        return new Fp(this.v.multiply(o.v).mod(P));
    }

    @Override
    public Fp sub(Fp o) {
        return new Fp(this.v.subtract(o.v).mod(P));
    }

    @Override
    public Fp squared() {
        return new Fp(v.multiply(v).mod(P));
    }

    @Override
    public Fp dbl() {
        return new Fp(v.add(v).mod(P));
    }

    @Override
    public Fp inverse() {
        return new Fp(v.modInverse(P));
    }

    @Override
    public Fp negate() {
        return new Fp(v.negate().mod(P));
    }

    @Override
    public boolean isZero() {
        return v.compareTo(BigInteger.ZERO) == 0;
    }

    /**
     * Checks if provided value is a valid Fp member
     */
    @Override
    public boolean isValid() {
        return v.compareTo(P) < 0;
    }

    Fp2 mul(Fp2 o) {
        return new Fp2(o.a.mul(this), o.b.mul(this));
    }

    static Fp create(byte[] v) {
        return new Fp(new BigInteger(1, v));
    }

    static Fp create(BigInteger v) {
        return new Fp(v);
    }

    public byte[] bytes() {
        return v.toByteArray();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        Fp fp = (Fp) o;

        return !(v != null ? v.compareTo(fp.v) != 0 : fp.v != null);
    }

    @Override
    public int hashCode() {
        return Objects.hash(v);
    }

    @Override
    public String toString() {
        return v.toString();
    }
}

