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

import org.hyperledger.besu.crypto.SECPPrivateKey;
import org.junit.BeforeClass;
import org.junit.Test;

import io.xdag.core.Address;
import io.xdag.core.TxHistory;
import io.xdag.core.XAmount;
import io.xdag.core.XdagField;
import io.xdag.crypto.Sign;
import io.xdag.db.TransactionHistoryStore;
import io.xdag.utils.DruidUtils;

public class TransactionHistoryStoreImplTest {

    public static final String SQL_CTEATE_TABLE = """
               DROP TABLE IF EXISTS `t_transaction_history`;
               CREATE TABLE `t_transaction_history` (
                `fid` int NOT NULL AUTO_INCREMENT,
                `faddress` varchar(64) NOT NULL,
                `famount` decimal(10,9) NOT NULL,
                `ftype` tinyint NOT NULL,
                `fremark` varchar(64) DEFAULT NULL,
                `ftime` datetime NOT NULL,
                PRIMARY KEY (`fid`),
                UNIQUE KEY `id_UNIQUE` (`fid`),
                KEY `faddress_index` (`faddress`)
                )
            """;

    private TransactionHistoryStore txHistoryStore = new TransactionHistoryStoreImpl();

    BigInteger private_1 = new BigInteger("c85ef7d79691fe79573b1a7064c19c1a9819ebdbd1faaab1a8ec92344438aaf4", 16);

    SECPPrivateKey secretkey_1 = SECPPrivateKey.create(private_1, Sign.CURVE_NAME);

    @BeforeClass
    public static void setUp() throws Exception {
        Connection conn = DruidUtils.getConnection();
        Statement stmt = conn.createStatement();
        stmt.execute(SQL_CTEATE_TABLE);
    }

    @Test
    public void testTxTxHistorySaveAndListAndCount() {
        TxHistory txHistory = new TxHistory();
        Address input = new Address(secretkey_1.getEncodedBytes(), XdagField.FieldType.XDAG_FIELD_INPUT, XAmount.ZERO,true);
        txHistory.setAddress(input);
        txHistory.setRemark("xdagj_test");
        txHistory.setTimeStamp(System.currentTimeMillis());
        txHistoryStore.saveTxHistory(txHistory);

        String addr = input.getIsAddress() ? toBase58(hash2byte(input.getAddress())) : hash2Address(input.getAddress());
        List<TxHistory> txHistoryList = txHistoryStore.listTxHistoryByAddress(addr, 1);
        assertNotNull(txHistoryList);
        assertEquals(1, txHistoryList.size());

        int count = txHistoryStore.getTxHistoryCount(addr);
        assertEquals(1, count);
    }

}
