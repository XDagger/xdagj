package io.xdag.rpc.modules;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.netty.channel.ChannelHandlerContext;
import io.xdag.rpc.jsonrpc.JsonRpcRequest;
import io.xdag.rpc.jsonrpc.JsonRpcResultOrError;
import io.xdag.rpc.jsonrpc.JsonRpcVersion;


@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "method", visible = true)
@JsonSubTypes({
//        @JsonSubTypes.Type(value = EthSubscribeRequest.class, name = "eth_subscribe"),
//        @JsonSubTypes.Type(value = EthUnsubscribeRequest.class, name = "eth_unsubscribe"),
})
public abstract class XdagJsonRpcRequest extends JsonRpcRequest<XdagJsonRpcMethod> {
    public XdagJsonRpcRequest(
            JsonRpcVersion version,
            XdagJsonRpcMethod method,
            int id) {
        super(version, method, id);
    }

    /**
     * Inheritors should implement this method by delegating to the corresponding visitor method.
     */
    public abstract JsonRpcResultOrError accept(XdagJsonRpcRequestVisitor visitor, ChannelHandlerContext ctx);
}
