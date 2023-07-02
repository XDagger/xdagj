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
package io.xdag.db.mysql;

import static io.xdag.utils.BasicUtils.hash2Address;
import static io.xdag.utils.BasicUtils.hash2byte;
import static io.xdag.utils.WalletUtils.toBase58;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.math.BigInteger;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;

import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.crypto.SECPPrivateKey;
import org.junit.BeforeClass;
import org.junit.Test;

import io.xdag.core.Address;
import io.xdag.core.TxHistory;
import io.xdag.core.XAmount;
import io.xdag.core.XdagField;
import io.xdag.crypto.Sign;
import io.xdag.db.TransactionHistoryStore;
import io.xdag.utils.BasicUtils;
import io.xdag.utils.DruidUtils;

public class TransactionHistoryStoreImplTest {

    public static final String SQL_CTEATE_TABLE = """
               DROP TABLE IF EXISTS `t_transaction_history`;
               CREATE TABLE `t_transaction_history` (
                `fid` int NOT NULL AUTO_INCREMENT,
                `faddress` varchar(64) NOT NULL,
                `fhash` varchar(64) NOT NULL,
                `famount` decimal(10,9) NOT NULL,
                `ftype` tinyint NOT NULL,
                `fremark` varchar(64) DEFAULT NULL,
                `ftime` datetime NOT NULL,
                PRIMARY KEY (`fid`),
                UNIQUE KEY `id_UNIQUE` (`fid`),
                KEY `faddress_index` (`faddress`)
                )
            """;

    private final TransactionHistoryStore txHistoryStore = new TransactionHistoryStoreImpl();

    BigInteger private_1 = new BigInteger("c85ef7d79691fe79573b1a7064c19c1a9819ebdbd1faaab1a8ec92344438aaf4", 16);

    SECPPrivateKey secretkey_1 = SECPPrivateKey.create(private_1, Sign.CURVE_NAME);

    @BeforeClass
    public static void setUp() throws Exception {
        Statement stmt;
        Connection conn = DruidUtils.getConnection();
        if (conn != null) {
            stmt = conn.createStatement();
            stmt.execute(SQL_CTEATE_TABLE);
        }
    }

    @Test
    public void testTxHistorySaveAndListAndCount() {
        long timestamp = System.currentTimeMillis();
        String remark = "xdagj_test";
        String hash = BasicUtils.hash2Address(Bytes32.ZERO);
        TxHistory txHistory = new TxHistory();
        Address input = new Address(secretkey_1.getEncodedBytes(), XdagField.FieldType.XDAG_FIELD_INPUT, XAmount.ZERO,true);
        txHistory.setAddress(input);
        txHistory.setHash(hash);
        txHistory.setRemark(remark);
        txHistory.setTimestamp(timestamp);
        txHistoryStore.saveTxHistory(txHistory);

        String addr = input.getIsAddress() ? toBase58(hash2byte(input.getAddress())) : hash2Address(input.getAddress());
        List<TxHistory> txHistoryList = txHistoryStore.listTxHistoryByAddress(addr, 1);
        assertNotNull(txHistoryList);
        assertEquals(1, txHistoryList.size());

        TxHistory resTxHistory = txHistoryList.get(0);
        int count = txHistoryStore.getTxHistoryCount(addr);
        assertEquals(1, count);
        assertEquals(remark, resTxHistory.getRemark());
        assertEquals(hash, resTxHistory.getHash());
        assertEquals(timestamp, resTxHistory.getTimestamp());
    }

}
