package io.xdag.rpc.exception;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.googlecode.jsonrpc4j.ErrorResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.List;

public class XdagErrorResolver implements ErrorResolver{
    private static final Logger logger = LoggerFactory.getLogger("web3");

    @Override
    public ErrorResolver.JsonError resolveError(Throwable t, Method method, List<JsonNode> arguments) {
        ErrorResolver.JsonError error = null;
        if(t instanceof  XdagJsonRpcRequestException) {
            error =  new ErrorResolver.JsonError(((XdagJsonRpcRequestException) t).getCode(), t.getMessage(), null);
        } else if (t instanceof InvalidFormatException) {
            error = new ErrorResolver.JsonError(-32603, "Internal server error, probably due to invalid parameter type", null);
        } else {
            logger.error("JsonRPC error when for method {} with arguments {}", method, arguments, t);
            error = new ErrorResolver.JsonError(-32603, "Internal server error", null);
        }
        return error;
    }
}
