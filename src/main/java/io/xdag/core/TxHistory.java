package io.xdag.core;

import lombok.Data;

@Data
public class TxHistory {

    Address address;
    long timeStamp;

    public TxHistory(Address address, long timeStamp) {
        this.address = address;
        this.timeStamp = timeStamp;
    }
}
