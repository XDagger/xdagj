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

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.MutableBytes32;

/**
 * @author Dcj_Cory
 * @date 2022/11/7 4:47 PM
 */
public class ByteArrayToByte32 {
    public static MutableBytes32 arrayToByte32(byte[] value){
        MutableBytes32 mutableBytes32 = MutableBytes32.wrap(new byte[32]);
        mutableBytes32.set(8, Bytes.wrap(value));
        return mutableBytes32;
    }
    public static byte[] byte32ToArray(MutableBytes32 value){
        return value.mutableCopy().slice(8,20).toArray();
    }

}
