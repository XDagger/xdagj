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

import static io.xdag.core.XdagField.FieldType.XDAG_FIELD_IN;
import static io.xdag.core.XdagField.FieldType.XDAG_FIELD_OUT;

import com.google.common.collect.Lists;
import io.xdag.config.Config;
import io.xdag.core.Address;
import io.xdag.core.Block;
import io.xdag.crypto.Hash;
import java.util.ArrayList;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.crypto.SECP256K1;
import org.bouncycastle.util.encoders.Hex;

public class BlockBuilder {

    public static Block generateAddressBlock(Config config, SECP256K1.KeyPair key, long xdagTime) {
        Block b = new Block(config, xdagTime, null, null, false, null, null, -1);
        b.signOut(key);
        return b;
    }

    // TODO:set nonce means this block is a mining block, the mining param need to set true
    public static Block generateExtraBlock(Config config, SECP256K1.KeyPair key, long xdagTime, List<Address> pendings) {
        Block b = new Block(config, xdagTime, null, pendings, true, null, null, -1);
        b.signOut(key);
        Bytes32 random = Hash.sha256(Bytes.wrap(Hex.decode("1234")));
        b.setNonce(random);
        return b;
    }

    // TODO:set nonce means this block is a mining block, the mining param need to set true
    public static Block generateExtraBlockGivenRandom(Config config, SECP256K1.KeyPair key, long xdagTime,
            List<Address> pendings, String randomS) {
        Block b = new Block(config, xdagTime, null, pendings, true, null, null, -1);
        b.signOut(key);
        Bytes32 random = Hash.sha256(Bytes.wrap(Hex.decode(randomS)));
        b.setNonce(random);
        return b;
    }

    public static Block generateTransactionBlock(Config config, SECP256K1.KeyPair key, long xdagTime, Address from, Address to,
            long amount) {
        List<Address> refs = Lists.newArrayList();
        refs.add(new Address(from.getHashLow(), XDAG_FIELD_IN, amount)); // key1
        refs.add(new Address(to.getHashLow(), XDAG_FIELD_OUT, amount));
        List<SECP256K1.KeyPair> keys = new ArrayList<>();
        keys.add(key);
        Block b = new Block(config, xdagTime, refs, null, false, keys, null, 0); // orphan
        b.signOut(key);
        return b;
    }

}
