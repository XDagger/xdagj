package io.xdag;

import com.google.common.collect.Lists;
import io.xdag.core.Address;
import io.xdag.core.Block;
import io.xdag.core.XdagField;
import io.xdag.crypto.ECKey;

import java.util.ArrayList;
import java.util.List;

import static io.xdag.core.XdagField.FieldType.XDAG_FIELD_OUT;

public class BlockGenerater {

    public static Block generateAddressBlock(ECKey key, long xdagTime) {
        Block b = new Block(xdagTime, null, null, false, null, null, -1);
        b.signOut(key);
        return b;
    }

    public static Block generateExtraBlock(ECKey key, long xdagTime, List<Address> pendings) {
        Block b = new Block(xdagTime, null, pendings, false, null, null, -1);
        b.signOut(key);
        return b;
    }

    public static Block generateTransactionBlock(ECKey key, long xdagTime, Address from, Address to, long amount) {
        List refs = Lists.newArrayList();
        refs.add(new Address(from.getHashLow(), XdagField.FieldType.XDAG_FIELD_IN, amount)); // key1
        refs.add(new Address(to.getHashLow(), XDAG_FIELD_OUT, amount));
        List<ECKey> keys = new ArrayList<>();
        keys.add(key);
        Block b = new Block(xdagTime, refs, null, false, keys, null, 0); // orphan
        b.signOut(key);
        return b;
    }

}
