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

import com.google.common.collect.Lists;
import io.xdag.core.*;
import io.xdag.db.TransactionHistoryStore;
import io.xdag.utils.BasicUtils;
import io.xdag.utils.DruidUtils;
import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import static io.xdag.utils.BasicUtils.hash2Address;
import static io.xdag.utils.BasicUtils.hash2byte;
import static io.xdag.utils.WalletUtils.toBase58;

@Slf4j
public class TransactionHistoryStoreImpl implements TransactionHistoryStore {

    private static final String SQL_INSERT = "insert into t_transaction_history(faddress,fhash,famount,ftype,fremark,ftime) values(?,?,?,?,?,?)";

    private static final String SQL_QUERY_TXHISTORY_BY_ADDRESS = "select faddress,fhash,famount,ftype,fremark,ftime from t_transaction_history where faddress= ? order by fid desc limit ?,?";

    private static final String SQL_QUERY_TXHISTORY_COUNT = "select count(*) from t_transaction_history where faddress=?";

    private static final int PAGE_SIZE = 100;
    private Connection conn = null;
    private PreparedStatement pstmt = null;

    @Override
    public boolean saveTxHistory(TxHistory txHistory) {
        Connection conn = null;
        PreparedStatement pstmt = null;

        boolean result = false;
        try {
            conn = DruidUtils.getConnection();
            if (conn != null) {
                pstmt = conn.prepareStatement(SQL_INSERT);

                Address address = txHistory.getAddress();
                String addr = address.getIsAddress() ? toBase58(hash2byte(address.getAddress())) : hash2Address(address.getAddress());
                pstmt.setString(1, addr);
                pstmt.setString(2, txHistory.getHash());
                pstmt.setBigDecimal(3, txHistory.getAddress().getAmount().toDecimal(9, XUnit.XDAG));
                pstmt.setInt(4, txHistory.getAddress().getType().asByte());
                pstmt.setString(5, txHistory.getRemark());
                pstmt.setTimestamp(6, new java.sql.Timestamp(txHistory.getTimestamp()));
                result = pstmt.executeUpdate() == 1;
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        } finally {
            DruidUtils.close(conn, pstmt);
        }
        return result;
    }

    @Override
    public boolean batchSaveTxHistory(TxHistory txHistory, int count, boolean isEnd) {
        boolean result = false;
        try {
            if (conn == null) {
                conn = DruidUtils.getConnection();
                conn.setAutoCommit(false);
            }
            if (pstmt == null) {
                pstmt = conn.prepareStatement(SQL_INSERT);
            }
            Address address = txHistory.getAddress();
            String addr = address.getIsAddress() ? toBase58(hash2byte(address.getAddress())) : hash2Address(address.getAddress());
            pstmt.setString(1, addr);
            pstmt.setString(2, txHistory.getHash());
            pstmt.setBigDecimal(3, txHistory.getAddress().getAmount().toDecimal(9, XUnit.XDAG));
            pstmt.setInt(4, txHistory.getAddress().getType().asByte());
            pstmt.setString(5, txHistory.getRemark());
            pstmt.setTimestamp(6, new java.sql.Timestamp(txHistory.getTimestamp()));
            pstmt.addBatch();
            if (count == 50000 || isEnd) {
                pstmt.executeBatch();
                conn.commit();
                result = true;
            }

        } catch (Exception e) {
            log.error(e.getMessage(), e);
        } finally {
            if (isEnd && conn != null) {
                try {
                    conn.close();
                    pstmt.close();
                    log.info("close mysql");
                } catch (SQLException e) {
                    log.error(e.getMessage(), e);
                }
                conn = null;
                pstmt=null;
            }
        }
        return result;
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
                pstmt.setString(1, address);
                pstmt.setInt(2, (page - 1) * PAGE_SIZE);
                pstmt.setInt(3, PAGE_SIZE);
                rs = pstmt.executeQuery();
                while (rs.next()) {
                    TxHistory txHistory = new TxHistory();
                    // Bytes32 addr = BasicUtils.address2Hash(rs.getString(1));
                    String hashlow = rs.getString(2);
                    txHistory.setHash(hashlow);
                    XAmount amount = XAmount.of(rs.getBigDecimal(3), XUnit.XDAG);
                    int fType = rs.getInt(4);
                    Address addrObj = new Address(BasicUtils.address2Hash(hashlow), XdagField.FieldType.fromByte((byte) fType), amount, false);
                    txHistory.setAddress(addrObj);
                    txHistory.setRemark(rs.getString(5));
                    txHistory.setTimestamp(rs.getTimestamp(6).getTime());
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
            DruidUtils.close(conn, pstmt, rs);
        }
        return count;
    }

}
