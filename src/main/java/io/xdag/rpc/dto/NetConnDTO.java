package io.xdag.rpc.dto;

import java.net.InetSocketAddress;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class NetConnDTO {

    InetSocketAddress nodeAddress;
    long connectTime;
    long inBound;
    long outBound;

}
