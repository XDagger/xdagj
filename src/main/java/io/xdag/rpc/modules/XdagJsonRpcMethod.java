package io.xdag.rpc.modules;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum  XdagJsonRpcMethod {
    @JsonProperty("eth_subscribe")
    ETH_SUBSCRIBE,
    @JsonProperty("eth_unsubscribe")
    ETH_UNSUBSCRIBE
}
