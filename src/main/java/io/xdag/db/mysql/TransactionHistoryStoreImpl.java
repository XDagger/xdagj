package io.xdag.db.mysql;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;

import com.google.common.collect.Lists;

import io.xdag.core.TxHistory;
import io.xdag.db.TransactionHistoryStore;
import io.xdag.utils.DruidUtils;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TransactionHistoryStoreImpl implements TransactionHistoryStore {

    private static final String SQL_INSERT = "insert into t_transaction_history(faddress, fremark, ftime) values(?,?,?)";

    private static final String SQL_QUERY_TXHISTORY_BY_ADDRESS = "select faddress,fremark,ftime from t_transaction_history order by id desc limit ?,?";

    private static final String SQL_QUERY_TXHISTORY_COUNT = "select count(*) from t_transaction_history where faddress=?";

    private static final int PAGE_SIZE = 100;

    @Override
    public void saveTxHistory(TxHistory txHistory) {
        Connection conn = null;
        PreparedStatement pstmt = null;

        try {
            conn = DruidUtils.getConnection();
            pstmt = conn.prepareStatement(SQL_INSERT);
            //pstmt.setString(1, txHistory.getAddress());
            pstmt.setString(2, txHistory.getRemark());
            pstmt.setTimestamp(3, new java.sql.Timestamp(txHistory.getTimeStamp()));
            pstmt.execute();
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
            pstmt = conn.prepareStatement(SQL_QUERY_TXHISTORY_BY_ADDRESS);
            pstmt.setInt(1, (page - 1) * PAGE_SIZE);
            pstmt.setInt(2, PAGE_SIZE);
            rs = pstmt.executeQuery();
            while (rs.next()) {
                TxHistory txHistory = new TxHistory();
                //txHistory.setAddress(rs.getString(1));
                txHistory.setRemark(rs.getString(2));
                txHistory.setTimeStamp(rs.getTimestamp(3).getTime());
                txHistoryList.add(txHistory);
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        } finally {
            DruidUtils.close(conn, pstmt, rs);
        }
        return txHistoryList;
    }

    public int getTxHistoryCount(String address) {
        int count = 0;
        Connection conn = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;

        try {
            conn = DruidUtils.getConnection();
            pstmt = conn.prepareStatement(SQL_QUERY_TXHISTORY_COUNT);
            pstmt.setString(1, address);
            rs = pstmt.executeQuery();
            if (rs.next()) {
                count = rs.getInt(1);
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        } finally {
            DruidUtils.close( conn, pstmt, rs);
        }
        return count;
    }

}
