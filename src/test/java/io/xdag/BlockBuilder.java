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
package io.xdag;

import com.google.common.collect.Lists;
import io.xdag.core.Address;
import io.xdag.core.Block;
import io.xdag.core.XdagField;

import java.util.ArrayList;
import java.util.List;

import static io.xdag.core.XdagField.FieldType.XDAG_FIELD_OUT;

import io.xdag.crypto.ECKeyPair;
import io.xdag.crypto.Hash;
import org.bouncycastle.util.encoders.Hex;

public class BlockBuilder {

    public static Block generateAddressBlock(ECKeyPair key, long xdagTime) {
        Block b = new Block(xdagTime, null, null, false, null, null, -1);
        b.signOut(key);
        return b;
    }

    public static Block generateExtraBlock(ECKeyPair key, long xdagTime, List<Address> pendings) {
        Block b = new Block(xdagTime, null, pendings, false, null, null, -1);
        b.signOut(key);
        byte[] random = Hash.sha256(Hex.decode("1234"));
        b.setNonce(random);
        return b;
    }

    public static Block generateExtraBlockGivenRandom(ECKeyPair key, long xdagTime, List<Address> pendings, String randomS) {
        Block b = new Block(xdagTime, null, pendings, false, null, null, -1);
        b.signOut(key);
        byte[] random = Hash.sha256(Hex.decode(randomS));
        b.setNonce(random);
        return b;
    }

    public static Block generateTransactionBlock(ECKeyPair key, long xdagTime, Address from, Address to, long amount) {
        List refs = Lists.newArrayList();
        refs.add(new Address(from.getHashLow(), XdagField.FieldType.XDAG_FIELD_IN, amount)); // key1
        refs.add(new Address(to.getHashLow(), XDAG_FIELD_OUT, amount));
        List<ECKeyPair> keys = new ArrayList<>();
        keys.add(key);
        Block b = new Block(xdagTime, refs, null, false, keys, null, 0); // orphan
        b.signOut(key);
        return b;
    }

}
