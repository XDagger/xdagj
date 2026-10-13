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

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.*;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class CorsHandlerTest {

    private EmbeddedChannel channel;
    private static final String ALLOWED_ORIGINS = "http://localhost:3000";

    @Before
    public void setUp() {
        CorsHandler handler = new CorsHandler(ALLOWED_ORIGINS);
        channel = new EmbeddedChannel(handler);
    }

    @Test
    public void testSimpleRequestWithDisallowedOrigin() {
        FullHttpRequest request = new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1,
                HttpMethod.POST,
                "/");
        request.headers()
                .set(HttpHeaderNames.ORIGIN, "http://disallowed.com")
                .set(HttpHeaderNames.CONTENT_TYPE, "application/json");

        channel.writeInbound(request);
        FullHttpResponse response = channel.readOutbound();

        assertNotNull(response);
        assertEquals(HttpResponseStatus.FORBIDDEN, response.status());
        assertNull(response.headers().get(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    public void testPreflightRequestWithDisallowedOrigin() {
        FullHttpRequest request = new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1,
                HttpMethod.OPTIONS,
                "/");
        request.headers()
                .set(HttpHeaderNames.ORIGIN, "http://disallowed.com")
                .set(HttpHeaderNames.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .set(HttpHeaderNames.ACCESS_CONTROL_REQUEST_HEADERS, "content-type");

        channel.writeInbound(request);
        FullHttpResponse response = channel.readOutbound();

        assertNotNull(response);
        assertEquals(HttpResponseStatus.FORBIDDEN, response.status());
        assertNull(response.headers().get(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    public void testPreflightRequest() {
        FullHttpRequest request = new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1,
                HttpMethod.OPTIONS,
                "/");
        request.headers()
                .set(HttpHeaderNames.ORIGIN, "http://localhost:3000")
                .set(HttpHeaderNames.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .set(HttpHeaderNames.ACCESS_CONTROL_REQUEST_HEADERS, "content-type");

        channel.writeInbound(request);
        FullHttpResponse response = channel.readOutbound();

        assertNotNull(response);
        assertEquals(HttpResponseStatus.OK, response.status());
        assertEquals("http://localhost:3000", response.headers().get(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN));
        assertEquals("true", response.headers().get(HttpHeaderNames.ACCESS_CONTROL_ALLOW_CREDENTIALS));
        assertEquals("GET, POST, OPTIONS", response.headers().get(HttpHeaderNames.ACCESS_CONTROL_ALLOW_METHODS));
        assertEquals("content-type, authorization", response.headers().get(HttpHeaderNames.ACCESS_CONTROL_ALLOW_HEADERS));
        assertEquals("Origin", response.headers().get(HttpHeaderNames.VARY));
    }
}