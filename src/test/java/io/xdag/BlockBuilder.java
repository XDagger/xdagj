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
import static io.xdag.core.XdagField.FieldType.XDAG_FIELD_INPUT;
import static io.xdag.core.XdagField.FieldType.XDAG_FIELD_OUTPUT;

import com.google.common.collect.Lists;
import io.xdag.config.Config;
import io.xdag.core.Address;
import io.xdag.core.Block;
import io.xdag.core.XAmount;
import io.xdag.core.XUnit;
import io.xdag.crypto.Hash;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.bouncycastle.util.encoders.Hex;
import org.hyperledger.besu.crypto.KeyPair;

public class BlockBuilder {

    public static Block generateAddressBlock(Config config, KeyPair key, long xdagTime) {
        return generateAddressBlockWithAmount(config, key, xdagTime, XAmount.of(1000, XUnit.XDAG) );
    }

    public static Block generateAddressBlockWithAmount(Config config, KeyPair key, long xdagTime, XAmount balance) {
        Block b = new Block(config, xdagTime, null, null, false, null, null, -1, XAmount.ZERO);
        b.signOut(key);
        b.getInfo().setAmount(balance);
        return b;
    }

    // TODO:set nonce means this block is a mining block, the mining param need to set true
    public static Block generateExtraBlock(Config config, KeyPair key, long xdagTime, List<Address> pendings) {
        return generateExtraBlockGivenRandom(config, key, xdagTime, pendings, "1234");
    }

    public static Block generateExtraBlock(Config config, KeyPair key, long xdagTime, String remark, List<Address> pendings) {
        Block b = new Block(config, xdagTime, null, pendings, true, null, remark, -1,XAmount.ZERO);
        Bytes32 random = Hash.sha256(Bytes.wrap(Hex.decode("1234")));
        b.signOut(key);
        b.setNonce(random);
        return b;
    }

    // TODO:set nonce means this block is a mining block, the mining param need to set true
    public static Block generateExtraBlockGivenRandom(Config config, KeyPair key, long xdagTime,
            List<Address> pendings, String randomS) {
        Block b = new Block(config, xdagTime, null, pendings, true, null, null, -1, XAmount.ZERO);
        Bytes32 random = Hash.sha256(Bytes.wrap(Hex.decode(randomS)));
        b.signOut(key);
        b.setNonce(random);
        return b;
    }

    public static Block generateOldTransactionBlock(Config config, KeyPair key, long xdagTime, Address from, Address to,
                                                 XAmount amount) {
        List<Address> refs = Lists.newArrayList();
        List<KeyPair> keys = Lists.newArrayList();
        refs.add(new Address(from.getAddress(), XDAG_FIELD_IN, amount,false)); // key1
        refs.add(new Address(to.getAddress(), XDAG_FIELD_OUTPUT, amount,true));
        keys.add(key);
        Block b = new Block(config, xdagTime, refs, null, false, keys, null, 0,XAmount.of(100,XUnit.MILLI_XDAG)); // orphan
        b.signOut(key);
        return b;
    }

    public static Block generateOldTransactionBlock(Config config, KeyPair key, long xdagTime, Address from, XAmount amount,Address to,
                                                    XAmount amount1, Address to1, XAmount amount2) {
        List<Address> refs = Lists.newArrayList();
        List<KeyPair> keys = Lists.newArrayList();
        refs.add(new Address(from.getAddress(), XDAG_FIELD_IN, amount,false)); // key1
        refs.add(new Address(to.getAddress(), XDAG_FIELD_OUTPUT, amount1,true));
        refs.add(new Address(to1.getAddress(), XDAG_FIELD_OUTPUT, amount2,true));
        keys.add(key);
        Block b = new Block(config, xdagTime, refs, null, false, keys, null, 0,XAmount.of(100,XUnit.MILLI_XDAG)); // orphan
        b.signOut(key);
        return b;
    }

    public static Block generateNewTransactionBlock(Config config, KeyPair key, long xdagTime, Address from, Address to,
                                                    XAmount amount) {
        List<Address> refs = Lists.newArrayList();
        List<KeyPair> keys = Lists.newArrayList();
        refs.add(new Address(from.getAddress(), XDAG_FIELD_INPUT, amount,true)); // key1
        refs.add(new Address(to.getAddress(), XDAG_FIELD_OUTPUT, amount,true));
        keys.add(key);
        Block b = new Block(config, xdagTime, refs, null, false, keys, null, 0, XAmount.of(100, XUnit.MILLI_XDAG)); // orphan
        b.signOut(key);
        return b;
    }

    public static Block generateNewTransactionBlock(Config config, KeyPair key, long xdagTime, Address from, Address to,
                                                    XAmount amount, XAmount VariableFee) {
        List<Address> refs = Lists.newArrayList();
        List<KeyPair> keys = Lists.newArrayList();
        refs.add(new Address(from.getAddress(), XDAG_FIELD_INPUT, amount,true)); // key1
        refs.add(new Address(to.getAddress(), XDAG_FIELD_OUTPUT, amount,true));
        keys.add(key);
        Block b = new Block(config, xdagTime, refs, null, false, keys, null, 0, VariableFee); // orphan
        b.signOut(key);
        return b;
    }

    public static Block generateWalletTransactionBlock(Config config, KeyPair key, long xdagTime, Address from, Address to,
                                                    XAmount amount) {
        List<Address> refs = Lists.newArrayList();
        List<KeyPair> keys = Lists.newArrayList();
        refs.add(new Address(from.getAddress(), XDAG_FIELD_INPUT, amount,true)); // key1
        refs.add(new Address(to.getAddress(), XDAG_FIELD_OUTPUT, amount,true));
        keys.add(key);
        Block b = new Block(config, xdagTime, refs, null, false, keys, null, 0, XAmount.ZERO); // orphan
        b.signOut(key);
        return b;
    }

    public static Block generateMinerRewardTxBlock(Config config, KeyPair key, long xdagTime, Address from, Address to1,Address to2,
                                                       XAmount amount, XAmount amount1, XAmount amount2) {
        List<Address> refs = Lists.newArrayList();
        List<KeyPair> keys = Lists.newArrayList();
        refs.add(new Address(from.getAddress(), XDAG_FIELD_INPUT, amount,true)); // key1
        refs.add(new Address(to1.getAddress(), XDAG_FIELD_OUTPUT, amount1,true));
        refs.add(new Address(to2.getAddress(), XDAG_FIELD_OUTPUT, amount2,true));
        keys.add(key);
        Block b = new Block(config, xdagTime, refs, null, false, keys, null, 0, XAmount.ZERO); // orphan
        b.signOut(key);
        return b;
    }

}
