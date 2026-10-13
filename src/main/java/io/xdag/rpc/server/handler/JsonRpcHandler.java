/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2020-2030 The XdagJ Developers
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package io.xdag.rpc.server.handler;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import io.xdag.config.spec.RPCSpec;
import io.xdag.rpc.error.JsonRpcError;
import io.xdag.rpc.error.JsonRpcException;
import io.xdag.rpc.server.protocol.JsonRpcErrorResponse;
import io.xdag.rpc.server.protocol.JsonRpcRequest;
import io.xdag.rpc.server.protocol.JsonRpcResponse;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@ChannelHandler.Sharable
public class JsonRpcHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    public static final ObjectMapper MAPPER;
    private final RPCSpec rpcSpec;
    private final List<JsonRpcRequestHandler> handlers;

    static {
        MAPPER = new ObjectMapper()
                .configure(JsonParser.Feature.INCLUDE_SOURCE_IN_LOCATION, true)
                .configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true)
                .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true)
                .configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true)
                .setSerializationInclusion(JsonInclude.Include.ALWAYS);
    }

    public JsonRpcHandler(RPCSpec rpcSpec, List<JsonRpcRequestHandler> handlers) {
        this.rpcSpec = rpcSpec;
        this.handlers = handlers;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) {
        // Check request size
        if (request.content().readableBytes() > rpcSpec.getRpcHttpMaxContentLength()) {
            sendError(ctx, new JsonRpcError(JsonRpcError.ERR_INVALID_REQUEST, "Request too large, max size: " + rpcSpec.getRpcHttpMaxContentLength() + " bytes"));
            return;
        }

        // Check HTTP method
        if (!request.method().equals(HttpMethod.POST)) {
            sendError(ctx, new JsonRpcError(JsonRpcError.ERR_INVALID_REQUEST, "Only POST method is allowed"));
            return;
        }

        // Check content type
        String contentType = request.headers().get(HttpHeaderNames.CONTENT_TYPE);
        if (contentType == null || !contentType.contains("application/json")) {
            sendError(ctx, new JsonRpcError(JsonRpcError.ERR_INVALID_REQUEST, "Content-Type must be application/json"));
            return;
        }

        String content = request.content().toString(StandardCharsets.UTF_8);
        JsonRpcRequest rpcRequest;
        try {
            rpcRequest = MAPPER.readValue(content, JsonRpcRequest.class);
        } catch (JsonRpcException e) {
            sendError(ctx, new JsonRpcError(e.getCode(), e.getMessage()), null);
            return;
        } catch (Exception e) {
            log.debug("Failed to parse JSON-RPC request", e);
            sendError(ctx, new JsonRpcError(JsonRpcError.ERR_PARSE, "Invalid JSON request: " + e.getMessage()));
            return;
        }

        try {
            Object result = dispatch(rpcRequest);
            sendResponse(ctx, new JsonRpcResponse(rpcRequest.getId(), result));
        } catch (JsonRpcException e) {
            log.debug("RPC error: {}", e.getMessage());
            sendError(ctx, new JsonRpcError(e.getCode(), e.getMessage()), rpcRequest);
        } catch (Exception e) {
            log.error("Error processing request", e);
            sendError(ctx, new JsonRpcError(JsonRpcError.ERR_INTERNAL, "Internal error: " + e.getMessage()), rpcRequest);
        }
    }

    private Object dispatch(JsonRpcRequest request) throws JsonRpcException {
        if (request.getMethod() == null) {
            throw JsonRpcException.invalidRequest("Method cannot be null");
        }

        for (JsonRpcRequestHandler handler : handlers) {
            if (handler.supportsMethod(request.getMethod())) {
                return handler.handle(request);
            }
        }

        throw JsonRpcException.methodNotFound(request.getMethod());
    }

    private void sendResponse(ChannelHandlerContext ctx, JsonRpcResponse response) {
        try {
            ByteBuf content = Unpooled.copiedBuffer(
                    MAPPER.writeValueAsString(response),
                    StandardCharsets.UTF_8
            );
            sendHttpResponse(ctx, content, HttpResponseStatus.OK);
        } catch (Exception e) {
            log.error("Error sending response", e);
            sendError(ctx, new JsonRpcError(JsonRpcError.ERR_INTERNAL, "Error serializing response: " + e.getMessage()));
        }
    }

    private void sendError(ChannelHandlerContext ctx, JsonRpcError error, JsonRpcRequest request) {
        try {
            ByteBuf content = Unpooled.copiedBuffer(
                    MAPPER.writeValueAsString(new JsonRpcErrorResponse(request != null ? request.getId() : null, error)),
                    StandardCharsets.UTF_8
            );
            sendHttpResponse(ctx, content, HttpResponseStatus.OK);
        } catch (Exception e) {
            log.error("Error sending error response", e);
            sendError(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private void sendError(ChannelHandlerContext ctx, JsonRpcError error) {
        sendError(ctx, error, null);
    }

    private void sendError(ChannelHandlerContext ctx, HttpResponseStatus status) {
        ByteBuf content = Unpooled.copiedBuffer(status.toString(), StandardCharsets.UTF_8);
        sendHttpResponse(ctx, content, status);
    }

    private void sendHttpResponse(ChannelHandlerContext ctx, ByteBuf content, HttpResponseStatus status) {
        FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                status,
                content
        );
        response.headers()
                .set(HttpHeaderNames.CONTENT_TYPE, "application/json")
                .set(HttpHeaderNames.CONTENT_LENGTH, content.readableBytes());

        // Get CORS Origin and set corresponding headers
        String origin = ctx.channel().attr(CorsHandler.CORS_ORIGIN).get();
        if (origin != null) {
            response.headers()
                    .set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, origin)
                    .set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true")
                    .set(HttpHeaderNames.VARY, "Origin");
        }

//        ctx.writeAndFlush(response);
        // Set connection to close after the response
        response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);

        // Send the response and close the connection
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("Channel exception", cause);
        ctx.close();
    }
}
