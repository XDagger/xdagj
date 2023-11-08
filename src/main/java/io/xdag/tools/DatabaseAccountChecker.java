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
package io.xdag.tools;


import static io.xdag.core.XUnit.XDAG;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.LongStream;

import io.xdag.core.XAmount;
import io.xdag.core.state.Account;
import io.xdag.db.Database;
import io.xdag.db.DatabaseFactory;
import io.xdag.db.DatabaseName;
import io.xdag.db.LeveldbDatabase;
import io.xdag.utils.BytesUtils;
import io.xdag.utils.ClosableIterator;

public class DatabaseAccountChecker {

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.out.println("Usage: java DatabaseAccountChecker [DATABASE_DIR]");
            return;
        }

        DatabaseFactory dbFactory = new LeveldbDatabase.LeveldbFactory(new File(args[0]));
        PrintStream out = System.out;
        if (args.length >= 2) {
            out = new PrintStream(new FileOutputStream(args[1]), true, StandardCharsets.UTF_8);
        }

        Database indexDB = dbFactory.getDB(DatabaseName.INDEX);

        long blockNumber =  BytesUtils.toLong(indexDB.get(new byte[] { 0x00 }));

        List<Account> accounts = new ArrayList<>();
        Database accountDB = dbFactory.getDB(DatabaseName.ACCOUNT);
        ClosableIterator<Map.Entry<byte[], byte[]>> iterator = accountDB.iterator();
        while (iterator.hasNext()) {
            Map.Entry<byte[], byte[]> entry = iterator.next();
            byte[] key = entry.getKey();
            byte[] value = entry.getValue();

            if (key[0] == 0x00) {
                byte[] address = Arrays.copyOfRange(key, 1, key.length);
                Account account = Account.fromBytes(address, value);
                out.println(account);
                accounts.add(account);
            }
        }
        iterator.close();

        long totalBlockRewards = LongStream.range(1, blockNumber + 1).map(number -> {
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

        long totalAvailable = accounts.stream().mapToLong(acc -> acc.getAvailable().toLong()).sum();
        long totalLocked = accounts.stream().mapToLong(acc -> acc.getLocked().toLong()).sum();

        out.println("Latest block number: " + blockNumber);
        out.println("Total block rewards: " + XAmount.of(totalBlockRewards).toDecimal(9, XDAG).toPlainString());
        out.println("Total available    : " + XAmount.of(totalAvailable).toDecimal(9, XDAG).toPlainString());
        out.println("Total locked       : " + XAmount.of(totalLocked).toDecimal(9, XDAG).toPlainString());
        out.println("Diff               : " + XAmount.of((totalLocked + totalAvailable - totalBlockRewards)).toDecimal(8, XDAG).toPlainString());

        dbFactory.close();
    }
}
