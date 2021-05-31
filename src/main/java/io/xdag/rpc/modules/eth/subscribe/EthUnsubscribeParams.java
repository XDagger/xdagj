//package io.xdag.rpc.modules.eth.subscribe;
//
//import com.fasterxml.jackson.annotation.JsonCreator;
//import com.fasterxml.jackson.annotation.JsonFormat;
//import com.fasterxml.jackson.annotation.JsonProperty;
//import com.fasterxml.jackson.annotation.JsonPropertyOrder;
//
//import java.util.Objects;
//
//@JsonFormat(shape=JsonFormat.Shape.ARRAY)
//@JsonPropertyOrder({"subscriptionId"})
//public class EthUnsubscribeParams {
//
//    private final SubscriptionId subscriptionId;
//
//    @JsonCreator
//    public EthUnsubscribeParams(
//            @JsonProperty("subscriptionId") SubscriptionId subscriptionId) {
//        this.subscriptionId = Objects.requireNonNull(subscriptionId);
//    }
//
//    public SubscriptionId getSubscriptionId() {
//        return subscriptionId;
//    }
//}
