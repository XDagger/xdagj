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
import io.xdag.config.Config;
import io.xdag.core.Address;
import io.xdag.core.Block;
import io.xdag.crypto.Hash;
import java.util.ArrayList;
import java.util.List;

import io.xdag.utils.BasicUtils;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt64;
import org.hyperledger.besu.crypto.KeyPair;
import org.bouncycastle.util.encoders.Hex;

import static io.xdag.core.XdagField.FieldType.*;

public class BlockBuilder {

    public static Block generateAddressBlock(Config config, KeyPair key, long xdagTime) {
        Block b = new Block(config, xdagTime, null, null, false, null, null, -1);
        b.signOut(key);
        b.getInfo().setAmount(BasicUtils.xdag2amount(1000));
        return b;
    }

    // TODO:set nonce means this block is a mining block, the mining param need to set true
    public static Block generateExtraBlock(Config config, KeyPair key, long xdagTime, List<Address> pendings) {
        Block b = new Block(config, xdagTime, null, pendings, true, null, null, -1);
        b.signOut(key);
        Bytes32 random = Hash.sha256(Bytes.wrap(Hex.decode("1234")));
        b.setNonce(random);
        return b;
    }

    // TODO:set nonce means this block is a mining block, the mining param need to set true
    public static Block generateExtraBlockGivenRandom(Config config, KeyPair key, long xdagTime,
            List<Address> pendings, String randomS) {
        Block b = new Block(config, xdagTime, null, pendings, true, null, null, -1);
        b.signOut(key);
        Bytes32 random = Hash.sha256(Bytes.wrap(Hex.decode(randomS)));
        b.setNonce(random);
        return b;
    }

    public static Block generateOldTransactionBlock(Config config, KeyPair key, long xdagTime, Address from, Address to,
                                                 UInt64 amount) {
        List<Address> refs = Lists.newArrayList();
        refs.add(new Address(from.getAddress(), XDAG_FIELD_IN, amount,false)); // key1
        refs.add(new Address(to.getAddress(), XDAG_FIELD_OUTPUT, amount,true));
        List<KeyPair> keys = new ArrayList<>();
        keys.add(key);
        Block b = new Block(config, xdagTime, refs, null, false, keys, null, 0); // orphan
        b.signOut(key);
        return b;
    }
    public static Block generateNewTransactionBlock(Config config, KeyPair key, long xdagTime, Address from, Address to,
                                                    UInt64 amount) {
        List<Address> refs = Lists.newArrayList();
        refs.add(new Address(from.getAddress(), XDAG_FIELD_INPUT, amount,true)); // key1
        refs.add(new Address(to.getAddress(), XDAG_FIELD_OUTPUT, amount,true));
        List<KeyPair> keys = new ArrayList<>();
        keys.add(key);
        Block b = new Block(config, xdagTime, refs, null, false, keys, null, 0); // orphan
        b.signOut(key);
        return b;
    }


}
