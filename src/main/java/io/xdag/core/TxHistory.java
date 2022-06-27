package io.xdag.core;

import lombok.Data;

@Data
public class TxHistory {

    Address address;
    long timeStamp;
    String remark;

    public TxHistory(Address address, long timeStamp, String remark) {
        this.address = address;
        this.timeStamp = timeStamp;
        this.remark = remark;
    }
}
