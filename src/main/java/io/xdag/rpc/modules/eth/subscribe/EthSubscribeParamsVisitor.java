//package io.xdag.rpc.modules.eth.subscribe;
//
//import java.nio.channels.Channel;
//
//public interface EthSubscribeParamsVisitor {
//    /**
//     * @param params new heads subscription request parameters.
//     * @param channel a Netty channel to subscribe notifications to.
//     * @return a subscription id which should be used as an unsubscribe parameter.
//     */
//    SubscriptionId visit(EthSubscribeNewHeadsParams params, Channel channel);
//
//    /**
//     * @param params logs subscription request parameters.
//     * @param channel a Netty channel to subscribe notifications to.
//     * @return a subscription id which should be used as an unsubscribe parameter.
//     */
//    SubscriptionId visit(EthSubscribeLogsParams params, Channel channel);
//}
