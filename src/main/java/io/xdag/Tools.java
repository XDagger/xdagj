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

import static io.xdag.core.XUnit.XDAG;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.LongStream;

import org.apache.commons.lang3.StringUtils;
import org.apache.tuweni.bytes.Bytes32;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import io.xdag.config.Constants;
import io.xdag.core.Genesis;
import io.xdag.core.XAmount;
import io.xdag.core.state.Account;
import io.xdag.core.state.AccountState;
import io.xdag.core.state.BlockState;
import io.xdag.db.Database;
import io.xdag.db.DatabaseFactory;
import io.xdag.db.DatabaseName;
import io.xdag.utils.BytesUtils;
import io.xdag.utils.ClosableIterator;
import io.xdag.utils.TimeUtils;
import io.xdag.utils.WalletUtils;

public class Tools {

    public static long getTotalBlockRewards(long latestBlockNumber) {
        long totalBlockRewards = LongStream.range(1, latestBlockNumber + 1).map(number -> {
            if (number < 1_017_323L) { // before apollo fork
                return XAmount.of(1024, XDAG).toLong();
            } else if (number < 2_097_152L) { // ~4.25 years
                return XAmount.of(128, XDAG).toLong();
            } else if (number < 4_194_304L) { // ~8.51 years
                return XAmount.of(64, XDAG).toLong();
            } else if (number < 6_291_456L) { // ~12.77 years
                return XAmount.of(32, XDAG).toLong();
            } else if (number < 8_388_608L) { // ~17.02 years
                return XAmount.of(16, XDAG).toLong();
            } else if (number < 10_485_760L) { // ~21.28 years
                return XAmount.of(8, XDAG).toLong();
            } else if (number < 12_582_912L) {// ~25.53 years
                return XAmount.of(4, XDAG).toLong();
            } else if (number < 14_680_064L) {// ~29.79 years
                return XAmount.of(2, XDAG).toLong();
            } else if (number < 16_777_216L) {// ~34.04 years
                return XAmount.of(1, XDAG).toLong();
            } else {
                return XAmount.ZERO.toLong();
            }
        }).sum();
        return totalBlockRewards;
    }

    public static void checkDbAccount(DatabaseFactory databaseFactory) {
        DatabaseFactory dbFactory = databaseFactory;
        PrintStream out = System.out;
        Database indexDB = dbFactory.getDB(DatabaseName.INDEX);

        long latestBlockNumber =  BytesUtils.toLong(indexDB.get(new byte[] { AccountState.TYPE_ACCOUNT }));

        List<Account> accounts = Lists.newArrayList();
        Database accountDB = dbFactory.getDB(DatabaseName.ACCOUNT);
        ClosableIterator<Map.Entry<byte[], byte[]>> iterator = accountDB.iterator();
        while (iterator.hasNext()) {
            Map.Entry<byte[], byte[]> entry = iterator.next();
            byte[] key = entry.getKey();
            byte[] value = entry.getValue();

            if (key[0] == AccountState.TYPE_ACCOUNT) {
                byte[] address = Arrays.copyOfRange(key, 1, key.length);
                Account account = Account.fromBytes(address, value);
                out.println(account);
                accounts.add(account);
            }
        }
        iterator.close();

        long totalBlockRewards = getTotalBlockRewards(latestBlockNumber);
        long totalAvailable = accounts.stream().mapToLong(acc -> acc.getAvailable().toLong()).sum();
        long totalLocked = accounts.stream().mapToLong(acc -> acc.getLocked().toLong()).sum();

        out.println("Latest block number: " + latestBlockNumber);
        out.println("Total block rewards: " + XAmount.of(totalBlockRewards).toDecimal(9, XDAG).toPlainString());
        out.println("Total available    : " + XAmount.of(totalAvailable).toDecimal(9, XDAG).toPlainString());
        out.println("Total locked       : " + XAmount.of(totalLocked).toDecimal(9, XDAG).toPlainString());
        out.println("Diff               : " + XAmount.of((totalLocked + totalAvailable - totalBlockRewards)).toDecimal(8, XDAG).toPlainString());

        dbFactory.close();
    }

    public static File exportSnapshot(DatabaseFactory databaseFactory) throws IOException {
        DatabaseFactory dbFactory = databaseFactory;
        PrintStream out = System.out;

        Database indexDB = dbFactory.getDB(DatabaseName.INDEX);

        long latestBlockNumber =  BytesUtils.toLong(indexDB.get(new byte[] { BlockState.TYPE_LATEST_BLOCK_NUMBER }));

        List<Account> accounts = Lists.newArrayList();
        List<Genesis.XSnapshot> snapshots = Lists.newArrayList();
        Database accountDB = dbFactory.getDB(DatabaseName.ACCOUNT);
        ClosableIterator<Map.Entry<byte[], byte[]>> iterator = accountDB.iterator();
        while (iterator.hasNext()) {
            Map.Entry<byte[], byte[]> entry = iterator.next();
            byte[] key = entry.getKey();
            byte[] value = entry.getValue();

            if (key[0] == AccountState.TYPE_ACCOUNT) {
                byte[] address = Arrays.copyOfRange(key, 1, key.length);
                Account account = Account.fromBytes(address, value);
                out.println(account);
                accounts.add(account);
                Genesis.XSnapshot xs = new Genesis.XSnapshot(account.getAddress(), account.getAvailable(), StringUtils.EMPTY);
                snapshots.add(xs);
            }
        }
        iterator.close();
        dbFactory.close();

        long number = latestBlockNumber;
        String coinbase = WalletUtils.toBase58(Constants.COINBASE_ADDRESS);
        String parentHash = Bytes32.ZERO.toHexString();
        long timestamp = TimeUtils.currentTimeMillis();
        String data = StringUtils.EMPTY;

        Genesis genesis = Genesis.jsonCreator(number, coinbase, parentHash, timestamp, data, Maps.newHashMap(), snapshots);

        File file = new File("snapshot-" + latestBlockNumber +".json");

        ObjectMapper objectMapper = new ObjectMapper();

        SimpleModule simpleModule = new SimpleModule();
        simpleModule.addSerializer(XAmount.class, new XAmount.XAmountJsonSerializer());
        simpleModule.addDeserializer(XAmount.class, new XAmount.XAmountJsonDeserializer());
        objectMapper.registerModule(simpleModule);

        objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, genesis);


        long totalBlockRewards = getTotalBlockRewards(latestBlockNumber);
        long totalAvailable = accounts.stream().mapToLong(acc -> acc.getAvailable().toLong()).sum();
        long totalLocked = accounts.stream().mapToLong(acc -> acc.getLocked().toLong()).sum();

        out.println("Snapshot Info: ");
        out.println("File               : " + file.getAbsolutePath());
        out.println("Total account      : " + accounts.size());
        out.println("Latest block number: " + latestBlockNumber);
        out.println("Total block rewards: " + XAmount.of(totalBlockRewards).toDecimal(9, XDAG).toPlainString());
        out.println("Total available    : " + XAmount.of(totalAvailable).toDecimal(9, XDAG).toPlainString());
        out.println("Total locked       : " + XAmount.of(totalLocked).toDecimal(9, XDAG).toPlainString());
        out.println("Diff               : " + XAmount.of((totalLocked + totalAvailable - totalBlockRewards)).toDecimal(8, XDAG).toPlainString());

        return file;
    }
}