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

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt64;
import org.hyperledger.besu.crypto.KeyPair;

import com.google.common.io.BaseEncoding;
import com.google.common.primitives.UnsignedLong;

import io.xdag.config.Config;
import io.xdag.core.BlockHeader;
import io.xdag.core.MainBlock;
import io.xdag.core.Transaction;
import io.xdag.core.TransactionResult;
import io.xdag.core.TransactionType;
import io.xdag.core.XAmount;
import io.xdag.crypto.Keys;
import io.xdag.crypto.SampleKeys;
import io.xdag.utils.BlockUtils;
import io.xdag.utils.BytesUtils;
import io.xdag.utils.MerkleUtils;
import io.xdag.utils.TimeUtils;

public class TestUtils {

    public static MainBlock createMainBlock(long number, List<Transaction> txs, List<TransactionResult> res) {
        return createBlock(BytesUtils.EMPTY_HASH, SampleKeys.KEY_PAIR, number, txs, res);
    }

    public static MainBlock createMainBlock(long timestamp, byte[] prevHash, KeyPair coinbase, long number, List<Transaction> txs,
            List<TransactionResult> res) {
        byte[] transactionsRoot = MerkleUtils.computeTransactionsRoot(txs);
        byte[] resultsRoot = MerkleUtils.computeResultsRoot(res);
        byte[] data = {};

        BlockHeader header = BlockUtils.createProofOfWorkHeader(prevHash, number, Keys.toBytesAddress(coinbase), timestamp, transactionsRoot, resultsRoot, 0L, data);
        List<Bytes32> txHashs = new ArrayList<>();
        txs.forEach(t-> txHashs.add(Bytes32.wrap(t.getHash())));
        return new MainBlock(header, txs, txHashs, res);
    }

    public static MainBlock createBlock(byte[] prevHash, KeyPair coinbase, long number, List<Transaction> txs,
            List<TransactionResult> res) {
        return createMainBlock(TimeUtils.currentTimeMillis(), prevHash, coinbase, number, txs, res);
    }

    public static MainBlock createEmptyBlock(long number) {
        return createMainBlock(number, Collections.emptyList(), Collections.emptyList());
    }

    public static Transaction createTransaction(Config config) {
        return createTransaction(config, SampleKeys.KEY1, SampleKeys.KEY2, XAmount.ZERO);
    }

    public static Transaction createTransaction(Config config, KeyPair from, KeyPair to, XAmount value) {
        return createTransaction(config, from, to, value, 0);
    }

    public static Transaction createTransaction(Config config, KeyPair from, KeyPair to, XAmount value, long nonce) {
        return createTransaction(config, TransactionType.TRANSFER, from, to, value, nonce);
    }

    public static Transaction createTransaction(Config config, TransactionType type, KeyPair from, KeyPair to, XAmount value,
            long nonce) {
        Network network = config.getNodeSpec().getNetwork();
        XAmount fee = config.getDagSpec().getMinTransactionFee();
        long timestamp = TimeUtils.currentTimeMillis();
        byte[] data = {};

        return new Transaction(network, type, Keys.toBytesAddress(to), value, fee, nonce, timestamp, data).sign(from);
    }

    // Source:
    // https://github.com/noushadali/powermock/blob/master/reflect/src/main/java/org/powermock/reflect/internal/WhiteboxImpl.java

    /**
     * Get the value of a field using reflection. Use this method when you need to
     * specify in which class the field is declared. This might be useful when you
     * have mocked the instance you are trying to access. Use this method to avoid
     * casting.
     *
     * @param <T>
     *            the expected type of the field
     * @param object
     *            the object to modify
     * @param fieldName
     *            the name of the field
     * @param where
     *            which class the field is defined
     * @return the internal state
     */
    @SuppressWarnings("unchecked")
    public static <T> T getInternalState(Object object, String fieldName, Class<?> where) {
        if (object == null || fieldName == null || fieldName.isEmpty() || fieldName.startsWith(" ")) {
            throw new IllegalArgumentException("object, field name, and \"where\" must not be empty or null.");
        }

        Field field;
        try {
            field = where.getDeclaredField(fieldName);
            field.setAccessible(true);
            return (T) field.get(object);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException("Field '" + fieldName + "' was not found in class " + where.getName() + ".");
        } catch (Exception e) {
            throw new RuntimeException("Internal error: Failed to get field in method getInternalState.", e);
        }
    }

    /**
     * Set the value of a field using reflection. Use this method when you need to
     * specify in which class the field is declared. This is useful if you have two
     * fields in a class hierarchy that has the same name, but you like to modify the
     * latter.
     *
     * @param object
     *            the object to modify
     * @param fieldName
     *            the name of the field
     * @param value
     *            the new value of the field
     * @param where
     *            which class the field is defined
     */
    public static void setInternalState(Object object, String fieldName, Object value, Class<?> where) {
        if (object == null || fieldName == null || fieldName.isEmpty() || fieldName.startsWith(" ")) {
            throw new IllegalArgumentException("object, field name, and \"where\" must not be empty or null.");
        }

        final Field field = getField(fieldName, where);
        try {
            field.set(object, value);
        } catch (Exception e) {
            throw new RuntimeException("Internal Error: Failed to set field in method setInternalState.", e);
        }
    }

    /**
     * Gets the field.
     *
     * @param fieldName
     *            the field name
     * @param where
     *            the where
     * @return the field
     */
    private static Field getField(String fieldName, Class<?> where) {
        if (where == null) {
            throw new IllegalArgumentException("where cannot be null");
        }

        Field field;
        try {
            field = where.getDeclaredField(fieldName);
            field.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException("Field '" + fieldName + "' was not found in class " + where.getName() + ".");
        }
        return field;
    }

    static int MAIN_BIG_PERIOD_LOG = 21;

    public static String toHexString(byte[] data) {
        return data == null ? "" : BaseEncoding.base16().lowerCase().encode(data);
    }
    public static UnsignedLong long2UnsignedLong(long number) {
        return UnsignedLong.valueOf(toHexString((ByteBuffer.allocate(8).putLong(number).array())),16);
    }

    static XAmount mainStartAmount = XAmount.ofXAmount(UInt64.valueOf(1L << 42).toLong());
    static long apolloForkHeight = 1017323;
    static XAmount apolloForkAmount = XAmount.ofXAmount(UInt64.valueOf(1L << 39).toLong());

    public static XAmount getOldStartAmount(long nmain) {
        XAmount startAmount;
        long forkHeight = apolloForkHeight;
        if (nmain >= forkHeight) {
            startAmount = apolloForkAmount;
        } else {
            startAmount = mainStartAmount;
        }

        return startAmount;
    }

    public static XAmount getOldReward(long nmain) {
        XAmount start = getOldStartAmount(nmain);
        long nanoAmount = start.toXAmount().toLong();
        return XAmount.ofXAmount(nanoAmount >> (nmain >> MAIN_BIG_PERIOD_LOG));
    }

    public static XAmount getOldSupply(long nmain) {
        UnsignedLong res = UnsignedLong.ZERO;
        XAmount amount = getOldStartAmount(nmain);
        long nanoAmount = amount.toXAmount().toLong();
        long current_nmain = nmain;
        while ((current_nmain >> MAIN_BIG_PERIOD_LOG) > 0) {
            res = res.plus(UnsignedLong.fromLongBits(1L << MAIN_BIG_PERIOD_LOG).times(long2UnsignedLong(nanoAmount)));
            current_nmain -= 1L << MAIN_BIG_PERIOD_LOG;
            nanoAmount >>= 1;
        }
        res = res.plus(long2UnsignedLong(current_nmain).times(long2UnsignedLong(nanoAmount)));
        long fork_height = 1017323;
        if (nmain >= fork_height) {
            // add before apollo amount
            XAmount diff = mainStartAmount.subtract(apolloForkAmount);
            long nanoDiffAmount = diff.toXAmount().toLong();
            res = res.plus(long2UnsignedLong(fork_height - 1).times(long2UnsignedLong(nanoDiffAmount)));
        }
        return XAmount.ofXAmount(res.longValue());
    }
}
