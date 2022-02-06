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
package io.xdag.evm;

import static org.junit.Assert.*;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.MutableBytes32;
import org.junit.Test;

import java.math.BigInteger;

public class DataWordTest {

    @Test
    public void testAdd() {
        MutableBytes32 three = MutableBytes32.create();
        for (int i = 0; i < three.size(); i++) {
            three.set(i, (byte) 0xff);
        }

        DataWord x = DataWord.of(three);
        x.add(DataWord.of(three));
        assertEquals(32, x.getData().size());
    }

    @Test
    public void testMod() {
        String expected = "000000000000000000000000000000000000000000000000000000000000001a";
        MutableBytes32 one = MutableBytes32.create();
        // 0x000000000000000000000000000000000000000000000000000000000000001e
        one.set(31, (byte)0x1e);

        MutableBytes32 two = MutableBytes32.create();
        for (int i = 0; i < two.size(); i++) {
            two.set(i, (byte) 0xff);
        }
        // 0xffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff56
        two.set(31, (byte)0x56);

        DataWord x = DataWord.of(one);// System.out.println(x.value());
        DataWord y = DataWord.of(two);// System.out.println(y.value());
        DataWord z = y.mod(x);
        assertEquals(32, z.getData().size());
        assertEquals(expected, z.getData().toUnprefixedHexString());
    }

    @Test
    public void testMul() {
        MutableBytes32 one = MutableBytes32.create();
        // 0x0000000000000000000000000000000000000000000000000000000000000001
        one.set(31, (byte)0x1);

        MutableBytes32 two = MutableBytes32.create();
        // 0x0000000000000000000000010000000000000000000000000000000000000000
        two.set(11, (byte)0x1);

        DataWord x = DataWord.of(one);// System.out.println(x.value());
        DataWord y = DataWord.of(two);// System.out.println(y.value());
        DataWord z = x.mul(y);
        assertEquals(32, z.getData().size());
        assertEquals("0000000000000000000000010000000000000000000000000000000000000000",
                z.getData().toUnprefixedHexString());
    }

    @Test
    public void testMulOverflow() {
        MutableBytes32 one = MutableBytes32.create();
        one.set(30, (byte)0x1);

        MutableBytes32 two = MutableBytes32.create();
        two.set(0, (byte)0x1);

        DataWord x = DataWord.of(one);
        System.out.println(x);
        DataWord y = DataWord.of(two);
        System.out.println(y);
        DataWord z = x.mul(y);
        System.out.println(z);

        assertEquals(32, z.getData().size());
        assertEquals("0000000000000000000000000000000000000000000000000000000000000000",
                z.getData().toUnprefixedHexString());
    }

    @Test
    public void testDiv() {
        MutableBytes32 one = MutableBytes32.create();
        // 0x000000000000000000000000000000000000000000000000000000000000012c
        one.set(30, (byte)0x01);
        one.set(31, (byte)0x2c);

        MutableBytes32 two = MutableBytes32.create();
        // 0x000000000000000000000000000000000000000000000000000000000000000f
        two.set(31, (byte)0x0f);

        DataWord x = DataWord.of(one);
        DataWord y = DataWord.of(two);
        DataWord z = x.div(y);

        assertEquals(32, z.getData().size());
        assertEquals("0000000000000000000000000000000000000000000000000000000000000014",
                z.getData().toUnprefixedHexString());
    }

    @Test
    public void testDivZero() {
//        byte[] one = new byte[32];
        MutableBytes32 one = MutableBytes32.create();
//        one[30] = 0x05; // 0x0000000000000000000000000000000000000000000000000000000000000500
        one.set(30,(byte) 0x05);

//        byte[] two = new byte[32];
        MutableBytes32 two = MutableBytes32.create();

        DataWord x = DataWord.of(one);
        DataWord y = DataWord.of(two);
        DataWord z = x.div(y);

        assertEquals(32, z.getData().size());
        assertTrue(z.isZero());
    }

    @Test
    public void testSDivNegative() {

        // one is -300 as 256-bit signed integer:
        Bytes one = Bytes.fromHexString("fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffed4");

        MutableBytes32 two = MutableBytes32.create();
        two.set(31, (byte)0x0f);

        DataWord x = DataWord.of(one);
        DataWord y = DataWord.of(two);
        DataWord z = x.sDiv(y);

        assertEquals(32, z.getData().size());
        assertEquals("ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffec", z.toString());
    }

    @Test
    public void testSignExtend1() {

//        DataWord x = DataWord.of(BytesUtils.fromHexString("f2"));
        DataWord x = DataWord.of(Bytes.fromHexString("f2"));
        byte k = 0;
        String expected = "fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff2";

        DataWord z = x.signExtend(k);
        System.out.println(z.toString());
        assertEquals(expected, z.toString());
    }

    @Test
    public void testSignExtend2() {
//        DataWord x = DataWord.of(BytesUtils.fromHexString("f2"));
        DataWord x = DataWord.of(Bytes.fromHexString("f2"));
        byte k = 1;
        String expected = "00000000000000000000000000000000000000000000000000000000000000f2";

        DataWord z = x.signExtend(k);
        System.out.println(z.toString());
        assertEquals(expected, z.toString());
    }

    @Test
    public void testSignExtend3() {

        byte k = 1;
//        DataWord x = DataWord.of(BytesUtils.fromHexString("0f00ab"));
        DataWord x = DataWord.of(Bytes.fromHexString("0f00ab"));
        String expected = "00000000000000000000000000000000000000000000000000000000000000ab";

        DataWord z = x.signExtend(k);
        System.out.println(z.toString());
        assertEquals(expected, z.toString());
    }

    @Test
    public void testSignExtend4() {

        byte k = 1;
//        DataWord x = DataWord.of(BytesUtils.fromHexString("ffff"));
        DataWord x = DataWord.of(Bytes.fromHexString("ffff"));
        String expected = "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff";

        DataWord z = x.signExtend(k);
        System.out.println(z.toString());
        assertEquals(expected, z.toString());
    }

    @Test
    public void testSignExtend5() {

        byte k = 3;
//        DataWord x = DataWord.of(BytesUtils.fromHexString("ffffffff"));
        DataWord x = DataWord.of(Bytes.fromHexString("ffffffff"));
        String expected = "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff";

        DataWord z = x.signExtend(k);
        System.out.println(z.toString());
        assertEquals(expected, z.toString());
    }

    @Test
    public void testSignExtend6() {

        byte k = 3;
//        DataWord x = DataWord.of(BytesUtils.fromHexString("ab02345678"));
        DataWord x = DataWord.of(Bytes.fromHexString("ab02345678"));
        String expected = "0000000000000000000000000000000000000000000000000000000002345678";

        DataWord z = x.signExtend(k);
        System.out.println(z.toString());
        assertEquals(expected, z.toString());
    }

    @Test
    public void testSignExtend7() {

        byte k = 3;
//        DataWord x = DataWord.of(BytesUtils.fromHexString("ab82345678"));
        DataWord x = DataWord.of(Bytes.fromHexString("ab82345678"));
        String expected = "ffffffffffffffffffffffffffffffffffffffffffffffffffffffff82345678";

        DataWord z = x.signExtend(k);
        System.out.println(z.toString());
        assertEquals(expected, z.toString());
    }

    @Test
    public void testSignExtend8() {

        byte k = 30;
        DataWord x = DataWord.of(Bytes.fromHexString("ff34567882345678823456788234567882345678823456788234567882345678"));
        String expected = "0034567882345678823456788234567882345678823456788234567882345678";

        DataWord z = x.signExtend(k);
        System.out.println(z.toString());
        assertEquals(expected, z.toString());
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void testSignExtendException1() {

        byte k = -1;
        DataWord x = DataWord.of(0);

        x.signExtend(k); // should throw an exception
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void testSignExtendException2() {

        byte k = 32;
        DataWord x = DataWord.of(0);

        x.signExtend(k); // should throw an exception
    }

    @Test
    public void testAddModOverflow() {
        testAddMod("9999999999999999999999999999999999999999999999999999999999999999",
                "8888888888888888888888888888888888888888888888888888888888888888",
                "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff");
        testAddMod("ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff",
                "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff",
                "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff");
    }

    void testAddMod(String v1, String v2, String v3) {
        DataWord dv1 = DataWord.of(Bytes.fromHexString(v1));
        DataWord dv2 = DataWord.of(Bytes.fromHexString(v2));
        DataWord dv3 = DataWord.of(Bytes.fromHexString(v3));
        BigInteger bv1 = new BigInteger(v1, 16);
        BigInteger bv2 = new BigInteger(v2, 16);
        BigInteger bv3 = new BigInteger(v3, 16);

        DataWord z = dv1.addmod(dv2, dv3);
        BigInteger br = bv1.add(bv2).mod(bv3);
        assertEquals(z.value(), br);
    }

    @Test
    public void testMulMod1() {
        DataWord wr = DataWord.of(
                Bytes.fromHexString("9999999999999999999999999999999999999999999999999999999999999999"));
        DataWord w1 = DataWord.of(Bytes.fromHexString("01"));
        DataWord w2 = DataWord.of(
                Bytes.fromHexString("9999999999999999999999999999999999999999999999999999999999999998"));

        DataWord z = wr.mulmod(w1, w2);

        assertEquals(32, z.getData().size());
        assertEquals("0000000000000000000000000000000000000000000000000000000000000001",
                z.getData().toUnprefixedHexString());
    }

    @Test
    public void testMulMod2() {
        DataWord wr = DataWord.of(
                Bytes.fromHexString("9999999999999999999999999999999999999999999999999999999999999999"));
//        DataWord w1 = DataWord.of(BytesUtils.fromHexString("01"));
        DataWord w1 = DataWord.of(Bytes.fromHexString("01"));
        DataWord w2 = DataWord.of(
                Bytes.fromHexString("9999999999999999999999999999999999999999999999999999999999999999"));

        DataWord z = wr.mulmod(w1, w2);

        assertEquals(32, z.getData().size());
        assertTrue(z.isZero());
    }

    @Test
    public void testMulModZero() {
//        DataWord wr = DataWord.of(BytesUtils.fromHexString("00"));
        DataWord wr = DataWord.of(Bytes.fromHexString("00"));
        DataWord w1 = DataWord.of(
                Bytes.fromHexString("9999999999999999999999999999999999999999999999999999999999999999"));
        DataWord w2 = DataWord.of(
                Bytes.fromHexString("ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"));

        DataWord z = wr.mulmod(w1, w2);

        assertEquals(32, z.getData().size());
        assertTrue(z.isZero());
    }

    @Test
    public void testMulModZeroWord1() {
        DataWord wr = DataWord.of(
                Bytes.fromHexString("9999999999999999999999999999999999999999999999999999999999999999"));
        DataWord w1 = DataWord.of(Bytes.fromHexString("00"));
        DataWord w2 = DataWord.of(
                Bytes.fromHexString("ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"));

        DataWord z = wr.mulmod(w1, w2);

        assertEquals(32, z.getData().size());
        assertTrue(z.isZero());
    }

    @Test
    public void testMulModZeroWord2() {
        DataWord wr = DataWord.of(
                Bytes.fromHexString("9999999999999999999999999999999999999999999999999999999999999999"));
        DataWord w1 = DataWord.of(
                Bytes.fromHexString("ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"));
//        DataWord w2 = DataWord.of(BytesUtils.fromHexString("00"));
        DataWord w2 = DataWord.of(Bytes.fromHexString("00"));

        DataWord z = wr.mulmod(w1, w2);

        assertEquals(32, z.getData().size());
        assertTrue(z.isZero());
    }

    @Test
    public void testMulModOverflow() {
        DataWord wr = DataWord.of(
                Bytes.fromHexString("ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"));
        DataWord w1 = DataWord.of(
                Bytes.fromHexString("ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"));
        DataWord w2 = DataWord.of(
                Bytes.fromHexString("ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"));

        DataWord z = wr.mulmod(w1, w2);

        assertEquals(32, z.getData().size());
        assertTrue(z.isZero());
    }

    @Test
    public void testSHL() {
        assertEquals(DataWord.of("0x0000000000000000000000000000000000000000000000000000000000000001"),
                DataWord.of("0x0000000000000000000000000000000000000000000000000000000000000001")
                        .shiftLeft(DataWord.of("0x00")));
        assertEquals(DataWord.of("0x0000000000000000000000000000000000000000000000000000000000000002"),
                DataWord.of("0x0000000000000000000000000000000000000000000000000000000000000001")
                        .shiftLeft(DataWord.of("0x01")));
        assertEquals(DataWord.of("0x8000000000000000000000000000000000000000000000000000000000000000"),
                DataWord.of("0x0000000000000000000000000000000000000000000000000000000000000001")
                        .shiftLeft(DataWord.of("0xff")));
        assertEquals(DataWord.of("0x0000000000000000000000000000000000000000000000000000000000000000"),
                DataWord.of("0x0000000000000000000000000000000000000000000000000000000000000001")
                        .shiftLeft(DataWord.of("0x0100")));
        assertEquals(DataWord.of("0x0000000000000000000000000000000000000000000000000000000000000000"),
                DataWord.of("0x0000000000000000000000000000000000000000000000000000000000000001")
                        .shiftLeft(DataWord.of("0x0101")));
        assertEquals(DataWord.of("0xffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"),
                DataWord.of("0xffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff")
                        .shiftLeft(DataWord.of("0x00")));
        assertEquals(DataWord.of("0xfffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffe"),
                DataWord.of("0xffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff")
                        .shiftLeft(DataWord.of("0x01")));
        assertEquals(DataWord.of("0x8000000000000000000000000000000000000000000000000000000000000000"),
                DataWord.of("0xffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff")
                        .shiftLeft(DataWord.of("0xff")));
        assertEquals(DataWord.of("0x0000000000000000000000000000000000000000000000000000000000000000"),
                DataWord.of("0xffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff")
                        .shiftLeft(DataWord.of("0x0100")));
        assertEquals(DataWord.of("0x0000000000000000000000000000000000000000000000000000000000000000"),
                DataWord.of("0x0000000000000000000000000000000000000000000000000000000000000000")
                        .shiftLeft(DataWord.of("0x01")));
        assertEquals(DataWord.of("0xfffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffe"),
                DataWord.of("0x7fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff")
                        .shiftLeft(DataWord.of("0x01")));
        assertEquals(DataWord.of(""), DataWord.of("").shiftLeft(DataWord.of("")));
    }

    @Test
    public void testSHR() {
        assertEquals(DataWord.of("0x0000000000000000000000000000000000000000000000000000000000000001"),
                DataWord.of("0x0000000000000000000000000000000000000000000000000000000000000001")
                        .shiftRight(DataWord.of("0x00")));
        assertEquals(DataWord.of("0x0000000000000000000000000000000000000000000000000000000000000000"),
                DataWord.of("0x0000000000000000000000000000000000000000000000000000000000000001")
                        .shiftRight(DataWord.of("0x01")));
        assertEquals(DataWord.of("0x4000000000000000000000000000000000000000000000000000000000000000"),
                DataWord.of("0x8000000000000000000000000000000000000000000000000000000000000000")
                        .shiftRight(DataWord.of("0x01")));
        assertEquals(DataWord.of("0x0000000000000000000000000000000000000000000000000000000000000001"),
                DataWord.of("0x8000000000000000000000000000000000000000000000000000000000000000")
                        .shiftRight(DataWord.of("0xff")));
        assertEquals(DataWord.of("0x0000000000000000000000000000000000000000000000000000000000000000"),
                DataWord.of("0x8000000000000000000000000000000000000000000000000000000000000000")
                        .shiftRight(DataWord.of("0x0100")));
        assertEquals(DataWord.of("0x0000000000000000000000000000000000000000000000000000000000000000"),
                DataWord.of("0x8000000000000000000000000000000000000000000000000000000000000000")
                        .shiftRight(DataWord.of("0x0101")));
        assertEquals(DataWord.of("0xffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"),
                DataWord.of("0xffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff")
                        .shiftRight(DataWord.of("0x00")));
        assertEquals(DataWord.of("0x7fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"),
                DataWord.of("0xffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff")
                        .shiftRight(DataWord.of("0x01")));
        assertEquals(DataWord.of("0x0000000000000000000000000000000000000000000000000000000000000001"),
                DataWord.of("0xffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff")
                        .shiftRight(DataWord.of("0xff")));
        assertEquals(DataWord.of("0x0000000000000000000000000000000000000000000000000000000000000000"),
                DataWord.of("0xffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff")
                        .shiftRight(DataWord.of("0x0100")));
        assertEquals(DataWord.of("0x0000000000000000000000000000000000000000000000000000000000000000"),
                DataWord.of("0x0000000000000000000000000000000000000000000000000000000000000000")
                        .shiftRight(DataWord.of("0x01")));
    }

    @Test
    public void testSAR() {
        assertEquals(DataWord.of("0x0000000000000000000000000000000000000000000000000000000000000001"),
                DataWord.of("0x0000000000000000000000000000000000000000000000000000000000000001")
                        .shiftRightSigned(DataWord.of("0x00")));
        assertEquals(DataWord.of("0x0000000000000000000000000000000000000000000000000000000000000000"),
                DataWord.of("0x0000000000000000000000000000000000000000000000000000000000000001")
                        .shiftRightSigned(DataWord.of("0x01")));
        assertEquals(DataWord.of("0xc000000000000000000000000000000000000000000000000000000000000000"),
                DataWord.of("0x8000000000000000000000000000000000000000000000000000000000000000")
                        .shiftRightSigned(DataWord.of("0x01")));
        assertEquals(DataWord.of("0xffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"),
                DataWord.of("0x8000000000000000000000000000000000000000000000000000000000000000")
                        .shiftRightSigned(DataWord.of("0xff")));
        assertEquals(DataWord.of("0xffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"),
                DataWord.of("0x8000000000000000000000000000000000000000000000000000000000000000")
                        .shiftRightSigned(DataWord.of("0x0100")));
        assertEquals(DataWord.of("0xffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"),
                DataWord.of("0x8000000000000000000000000000000000000000000000000000000000000000")
                        .shiftRightSigned(DataWord.of("0x0101")));
        assertEquals(DataWord.of("0xffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"),
                DataWord.of("0xffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff")
                        .shiftRightSigned(DataWord.of("0x00")));
        assertEquals(DataWord.of("0xffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"),
                DataWord.of("0xffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff")
                        .shiftRightSigned(DataWord.of("0x01")));
        assertEquals(DataWord.of("0xffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"),
                DataWord.of("0xffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff")
                        .shiftRightSigned(DataWord.of("0xff")));
        assertEquals(DataWord.of("0xffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"),
                DataWord.of("0xffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff")
                        .shiftRightSigned(DataWord.of("0x0100")));
        assertEquals(DataWord.of("0x0000000000000000000000000000000000000000000000000000000000000000"),
                DataWord.of("0x0000000000000000000000000000000000000000000000000000000000000000")
                        .shiftRightSigned(DataWord.of("0x01")));
        assertEquals(DataWord.of("0x0000000000000000000000000000000000000000000000000000000000000001"),
                DataWord.of("0x4000000000000000000000000000000000000000000000000000000000000000")
                        .shiftRightSigned(DataWord.of("0xfe")));
        assertEquals(DataWord.of("0x000000000000000000000000000000000000000000000000000000000000007f"),
                DataWord.of("0x7fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff")
                        .shiftRightSigned(DataWord.of("0xf8")));
        assertEquals(DataWord.of("0x0000000000000000000000000000000000000000000000000000000000000001"),
                DataWord.of("0x7fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff")
                        .shiftRightSigned(DataWord.of("0xfe")));
        assertEquals(DataWord.of("0x0000000000000000000000000000000000000000000000000000000000000000"),
                DataWord.of("0x7fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff")
                        .shiftRightSigned(DataWord.of("0xff")));
        assertEquals(DataWord.of("0x0000000000000000000000000000000000000000000000000000000000000000"),
                DataWord.of("0x7fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff")
                        .shiftRightSigned(DataWord.of("0x0100")));
    }

    public static BigInteger pow(BigInteger x, BigInteger y) {
        if (y.compareTo(BigInteger.ZERO) < 0)
            throw new IllegalArgumentException();
        BigInteger z = x; // z will successively become x^2, x^4, x^8, x^16, x^32...
        BigInteger result = BigInteger.ONE;
        byte[] bytes = y.toByteArray();
        for (int i = bytes.length - 1; i >= 0; i--) {
            byte bits = bytes[i];
            for (int j = 0; j < 8; j++) {
                if ((bits & 1) != 0)
                    result = result.multiply(z);
                // short cut out if there are no more bits to handle:
                if ((bits >>= 1) == 0 && i == 0)
                    return result;
                z = z.multiply(z);
            }
        }
        return result;
    }
}

