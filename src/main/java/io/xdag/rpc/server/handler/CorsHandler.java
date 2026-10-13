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

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.*;
import io.netty.util.AttributeKey;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@Slf4j
public class CorsHandler extends ChannelInboundHandlerAdapter {
    public static final AttributeKey<String> CORS_ORIGIN = AttributeKey.valueOf("CorsOrigin");
    private final Set<String> allowedOrigins;
    private static final String ALLOWED_HEADERS = "content-type, authorization";
    private static final String ALLOWED_METHODS = "GET, POST, OPTIONS";

    public CorsHandler(String corsDomainsString) {
        if (corsDomainsString != null && !corsDomainsString.isEmpty()) {
            allowedOrigins = new HashSet<>(Arrays.asList(corsDomainsString.split(",")));
        } else {
            allowedOrigins = new HashSet<>();
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (!(msg instanceof HttpRequest request)) {
            ctx.fireChannelRead(msg);
            return;
        }

      String origin = request.headers().get(HttpHeaderNames.ORIGIN);

        if (request.method() == HttpMethod.OPTIONS) {
            handlePreflightRequest(ctx, request);
            if (request instanceof FullHttpRequest) {
                ((FullHttpRequest) request).release();
            }
            return;
        }

        if (origin != null) {
            if (isOriginAllowed(origin)) {
                setCorsHeaders(ctx, origin);
                ctx.fireChannelRead(msg);
            } else {
                log.warn("Blocked request from unauthorized origin: {}", origin);
                sendError(ctx, origin);
                if (request instanceof FullHttpRequest) {
                    ((FullHttpRequest) request).release();
                }
            }
        } else {
            ctx.fireChannelRead(msg);
        }
    }

    private void handlePreflightRequest(ChannelHandlerContext ctx, HttpRequest request) {
        String origin = request.headers().get(HttpHeaderNames.ORIGIN);
        if (origin == null || !isOriginAllowed(origin)) {
            sendError(ctx, origin);
            return;
        }

        FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, 
                HttpResponseStatus.OK);

        response.headers()
                .set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, origin)
                .set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_METHODS, ALLOWED_METHODS)
                .set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_HEADERS, ALLOWED_HEADERS)
                .set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true")
                .set(HttpHeaderNames.ACCESS_CONTROL_MAX_AGE, "3600")
                .set(HttpHeaderNames.VARY, "Origin")
                .set(HttpHeaderNames.CONTENT_LENGTH, 0);

        ctx.writeAndFlush(response);
    }

    private boolean isOriginAllowed(String origin) {
        return allowedOrigins.isEmpty() || allowedOrigins.contains("*") || allowedOrigins.contains(origin);
    }

    private void setCorsHeaders(ChannelHandlerContext ctx, String origin) {
        ctx.channel().attr(CORS_ORIGIN).set(origin);
    }

    private void sendError(ChannelHandlerContext ctx, String origin) {
        FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                HttpResponseStatus.FORBIDDEN);
        
        if (origin != null && isOriginAllowed(origin)) {
            response.headers()
                    .set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, origin)
                    .set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true")
                    .set(HttpHeaderNames.VARY, "Origin");
        }
        
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, 0);
        ctx.writeAndFlush(response);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("Error in CORS handler", cause);
        ctx.close();
    }
}
