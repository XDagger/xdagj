package io.xdag.db.mysql;

import static io.xdag.utils.BasicUtils.hash2Address;
import static io.xdag.utils.BasicUtils.hash2byte;
import static io.xdag.utils.WalletUtils.toBase58;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;

import org.apache.tuweni.bytes.Bytes32;

import com.google.common.collect.Lists;

import io.xdag.core.Address;
import io.xdag.core.TxHistory;
import io.xdag.core.XAmount;
import io.xdag.core.XUnit;
import io.xdag.core.XdagField;
import io.xdag.db.TransactionHistoryStore;
import io.xdag.utils.BasicUtils;
import io.xdag.utils.DruidUtils;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TransactionHistoryStoreImpl implements TransactionHistoryStore {

    private static final String SQL_INSERT = "insert into t_transaction_history(faddress,famount,ftype,fremark,ftime) values(?,?,?,?,?)";

    private static final String SQL_QUERY_TXHISTORY_BY_ADDRESS = "select faddress,famount,ftype,fremark,ftime from t_transaction_history order by fid desc limit ?,?";

    private static final String SQL_QUERY_TXHISTORY_COUNT = "select count(*) from t_transaction_history where faddress=?";

    private static final int PAGE_SIZE = 100;

    @Override
    public void saveTxHistory(TxHistory txHistory) {
        Connection conn;
        PreparedStatement pstmt;

        try {
            conn = DruidUtils.getConnection();
            if (conn != null) {
                pstmt = conn.prepareStatement(SQL_INSERT);

                Address address = txHistory.getAddress();
                String addr = address.getIsAddress() ? toBase58(hash2byte(address.getAddress())) : hash2Address(address.getAddress());
                pstmt.setString(1, addr);
                pstmt.setBigDecimal(2, txHistory.getAddress().getAmount().toDecimal(9, XUnit.XDAG));
                pstmt.setInt(3, txHistory.getAddress().getType().asByte());
                pstmt.setString(4, txHistory.getRemark());
                pstmt.setTimestamp(5, new java.sql.Timestamp(txHistory.getTimeStamp()));
                pstmt.execute();
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    @Override
    public List<TxHistory> listTxHistoryByAddress(String address, int page) {
        Connection conn = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        List<TxHistory> txHistoryList = Lists.newArrayList();

        try {
            conn = DruidUtils.getConnection();
            if (conn != null) {
                pstmt = conn.prepareStatement(SQL_QUERY_TXHISTORY_BY_ADDRESS);
                pstmt.setInt(1, (page - 1) * PAGE_SIZE);
                pstmt.setInt(2, PAGE_SIZE);
                rs = pstmt.executeQuery();
                while (rs.next()) {
                    TxHistory txHistory = new TxHistory();
                    Bytes32 addr = BasicUtils.pubAddress2Hash(rs.getString(1));
                    XAmount amount = XAmount.of(rs.getBigDecimal(2), XUnit.XDAG);
                    int fType = rs.getInt(3);
                    Address addrObj = new Address(addr, XdagField.FieldType.fromByte((byte)fType), amount, true);
                    txHistory.setAddress(addrObj);
                    txHistory.setRemark(rs.getString(4));
                    txHistory.setTimeStamp(rs.getTimestamp(5).getTime());
                    txHistoryList.add(txHistory);
                }
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        } finally {
            DruidUtils.close(conn, pstmt, rs);
        }
        return txHistoryList;
    }

    @Override
    public int getTxHistoryCount(String address) {
        int count = 0;
        Connection conn = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;

        try {
            conn = DruidUtils.getConnection();
            if (conn != null) {
                pstmt = conn.prepareStatement(SQL_QUERY_TXHISTORY_COUNT);
                pstmt.setString(1, address);
                rs = pstmt.executeQuery();
                if (rs.next()) {
                    count = rs.getInt(1);
                }
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        } finally {
            DruidUtils.close( conn, pstmt, rs);
        }
        return count;
    }

}
