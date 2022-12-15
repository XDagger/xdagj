package io.xdag.rpc.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;


@Data
@Builder
public class AccountResultDTO {
    private String balance;
    private List<BlockResultDTO.TxLink> transactions; // means transaction a wallet have

    @Data
    @Builder
    public static class TxLink {

        private int direction; // 0 input 1 output 2 coinbase
        private String hashlow;
        private String address;
        private String amount;
        private long time;
        private String remark;
    }
}
