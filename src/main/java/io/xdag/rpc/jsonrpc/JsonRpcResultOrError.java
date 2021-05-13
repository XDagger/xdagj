package io.xdag.rpc.jsonrpc;

import com.fasterxml.jackson.annotation.JsonIgnore;

public interface JsonRpcResultOrError {
    /**
     * @return a response according to the result or error state of this object.
     * @param messageId the message ID
     */
    @JsonIgnore
    JsonRpcIdentifiableMessage responseFor(int messageId);
}
