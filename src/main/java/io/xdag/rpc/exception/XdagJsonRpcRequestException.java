package io.xdag.rpc.exception;

public class XdagJsonRpcRequestException extends RuntimeException {

    private final Integer code;

    protected XdagJsonRpcRequestException(Integer code, String message, Exception e) {
        super(message, e);
        this.code = code;
    }

    public XdagJsonRpcRequestException(Integer code, String message) {
        super(message);
        this.code = code;
    }

    public Integer getCode() {
        return code;
    }

    public static XdagJsonRpcRequestException transactionRevertedExecutionError() {
        return executionError("transaction reverted");
    }

    public static XdagJsonRpcRequestException unknownError(String message) {
        return new XdagJsonRpcRequestException(-32009, message);
    }

    private static XdagJsonRpcRequestException executionError(String message) {
        return new XdagJsonRpcRequestException(-32015, String.format("VM execution error: %s", message));
    }

    public static XdagJsonRpcRequestException transactionError(String message) {
        return new XdagJsonRpcRequestException(-32010, message);
    }

    public static XdagJsonRpcRequestException invalidParamError(String message) {
        return new XdagJsonRpcRequestException(-32602, message);
    }

    public static XdagJsonRpcRequestException invalidParamError(String message, Exception e) {
        return new XdagJsonRpcRequestException(-32602, message, e);
    }

    public static XdagJsonRpcRequestException unimplemented(String message) {
        return new XdagJsonRpcRequestException(-32201, message);
    }

    public static XdagJsonRpcRequestException blockNotFound(String message) {
        return new XdagJsonRpcRequestException(-32600, message);
    }

    public static XdagJsonRpcRequestException stateNotFound(String message) {
        return new XdagJsonRpcRequestException(-32600, message);
    }
}
