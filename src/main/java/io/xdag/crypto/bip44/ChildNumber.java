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
package io.xdag.crypto.bip44;

import io.xdag.crypto.bip32.DeterministicHierarchy;

import java.util.List;
import java.util.Locale;

/**
 * <p>This is just a wrapper for the i (child number) as per BIP 32 with a boolean getter for the most significant bit
 * and a getter for the actual 0-based child number. A {@link List} of these forms a <i>path</i> through a
 * {@link DeterministicHierarchy}. This class is immutable.
 */
public class ChildNumber implements Comparable<ChildNumber> {

    /**
     * The bit that's set in the child number to indicate whether this key is "hardened". Given a hardened key, it is
     * not possible to derive a child public key if you know only the hardened public key. With a non-hardened key this
     * is possible, so you can derive trees of public keys given only a public parent, but the downside is that it's
     * possible to leak private keys if you disclose a parent public key and a child private key (elliptic curve maths
     * allows you to work upwards).
     */
    public static final int HARDENED_BIT = 0x80000000;

    public static final ChildNumber ZERO = new ChildNumber(0);
    public static final ChildNumber ZERO_HARDENED = new ChildNumber(0, true);
    public static final ChildNumber ONE = new ChildNumber(1);
    public static final ChildNumber ONE_HARDENED = new ChildNumber(1, true);

    /** Integer i as per BIP 32 spec, including the MSB denoting derivation type (0 = public, 1 = private) **/
    private final int i;

    public ChildNumber(int childNumber, boolean isHardened) {
        if (hasHardenedBit(childNumber))
            throw new IllegalArgumentException("Most significant bit is reserved and shouldn't be set: " + childNumber);
        i = isHardened ? (childNumber | HARDENED_BIT) : childNumber;
    }

    public ChildNumber(int i) {
        this.i = i;
    }

    /** Returns the uint32 encoded form of the path element, including the most significant bit. */
    public int getI() {
        return i;
    }

    /** Returns the uint32 encoded form of the path element, including the most significant bit. */
    public int i() { return i; }

    public boolean isHardened() {
        return hasHardenedBit(i);
    }

    private static boolean hasHardenedBit(int a) {
        return (a & HARDENED_BIT) != 0;
    }

    /** Returns the child number without the hardening bit set (i.e. index in that part of the tree). */
    public int num() {
        return i & (~HARDENED_BIT);
    }

    @Override
    public String toString() {
        return String.format(Locale.US, "%d%s", num(), isHardened() ? "H" : "");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return i == ((ChildNumber)o).i;
    }

    @Override
    public int hashCode() {
        return i;
    }

    @Override
    public int compareTo(ChildNumber other) {
        // note that in this implementation compareTo() is not consistent with equals()
        return Integer.compare(this.num(), other.num());
    }
}
