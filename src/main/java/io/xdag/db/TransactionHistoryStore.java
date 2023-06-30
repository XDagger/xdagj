package io.xdag.db;

import java.util.List;

import io.xdag.core.TxHistory;

public interface TransactionHistoryStore {

    void saveTxHistory(TxHistory txHistory);

    List<TxHistory> listTxHistoryByAddress(String address, int page);

    int getTxHistoryCount(String address);

}