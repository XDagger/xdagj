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

import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.*;
import io.xdag.config.spec.RPCSpec;
import io.xdag.rpc.api.XdagApi;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static org.junit.Assert.*;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doReturn;

public class JsonRpcHandlerTest {

    @Mock
    private XdagApi xdagApi;

    @Mock
    private RPCSpec rpcSpec;

    private JsonRpcHandler handler;
    private EmbeddedChannel channel;
    private JsonRequestHandler jsonRequestHandler;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        
        // Mock RPCSpec
        doReturn(1024).when(rpcSpec).getRpcHttpMaxContentLength();
        
        // Create a JsonRequestHandler with mocked XdagApi
        jsonRequestHandler = new JsonRequestHandler(xdagApi);
        
        handler = new JsonRpcHandler(rpcSpec, Collections.singletonList(jsonRequestHandler));
        channel = new EmbeddedChannel(new CorsHandler("http://localhost:3000"), handler);
    }

    @Test
    public void testRequestSizeTooLarge() {
        // Create a large request
        StringBuilder largeContent = new StringBuilder();
        for (int i = 0; i < 1025; i++) {
            largeContent.append('x');
        }

        FullHttpRequest request = new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1,
                HttpMethod.POST,
                "/",
                Unpooled.copiedBuffer(largeContent.toString(), StandardCharsets.UTF_8));
        request.headers()
                .set(HttpHeaderNames.CONTENT_TYPE, "application/json")
                .set(HttpHeaderNames.ORIGIN, "http://localhost:3000");

        // Send request
        channel.writeInbound(request);
        FullHttpResponse response = channel.readOutbound();

        // Verify response
        assertNotNull(response);
        assertEquals(HttpResponseStatus.INTERNAL_SERVER_ERROR, response.status());
        assertEquals("http://localhost:3000", response.headers().get(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN));
        String content = response.content().toString(StandardCharsets.UTF_8);
//        assertTrue(content.contains("Request too large");
    }

    @Test
    public void testInvalidHttpMethod() {
        FullHttpRequest request = new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1,
                HttpMethod.GET,
                "/");
        request.headers()
                .set(HttpHeaderNames.CONTENT_TYPE, "application/json")
                .set(HttpHeaderNames.ORIGIN, "http://localhost:3000");

        // Send request
        channel.writeInbound(request);
        FullHttpResponse response = channel.readOutbound();

        // Verify response
        assertNotNull(response);
        assertEquals(HttpResponseStatus.INTERNAL_SERVER_ERROR, response.status());
        assertEquals("http://localhost:3000", response.headers().get(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN));
        String content = response.content().toString(StandardCharsets.UTF_8);
//        assertTrue(content.contains("Only POST method is allowed");
    }

    @Test
    public void testInvalidContentType() {
        FullHttpRequest request = new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1,
                HttpMethod.POST,
                "/");
        request.headers()
                .set(HttpHeaderNames.CONTENT_TYPE, "text/plain")
                .set(HttpHeaderNames.ORIGIN, "http://localhost:3000");

        // Send request
        channel.writeInbound(request);
        FullHttpResponse response = channel.readOutbound();

        // Verify response
        assertNotNull(response);
        assertEquals(HttpResponseStatus.INTERNAL_SERVER_ERROR, response.status());
        assertEquals("http://localhost:3000", response.headers().get(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN));
        String content = response.content().toString(StandardCharsets.UTF_8);
//        assertTrue(content.contains("Content-Type must be application/json");
    }

    @Test
    public void testInvalidJson() {
        String invalidJson = "{ invalid json }";
        FullHttpRequest request = new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1,
                HttpMethod.POST,
                "/",
                Unpooled.copiedBuffer(invalidJson, StandardCharsets.UTF_8));
        request.headers()
                .set(HttpHeaderNames.CONTENT_TYPE, "application/json")
                .set(HttpHeaderNames.ORIGIN, "http://localhost:3000");

        // Send request
        channel.writeInbound(request);
        FullHttpResponse response = channel.readOutbound();

        // Verify response
        assertNotNull(response);
        assertEquals(HttpResponseStatus.INTERNAL_SERVER_ERROR, response.status());
        assertEquals("http://localhost:3000", response.headers().get(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN));
        String content = response.content().toString(StandardCharsets.UTF_8);
//        assertTrue(content.contains("Invalid JSON request");
    }

    @Test
    public void testValidRequestWithCors() {
        String validJson = "{\"jsonrpc\":\"2.0\",\"method\":\"xdag_blockNumber\",\"id\":\"1\"}";
        FullHttpRequest request = new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1,
                HttpMethod.POST,
                "/",
                Unpooled.copiedBuffer(validJson, StandardCharsets.UTF_8));
        request.headers()
                .set(HttpHeaderNames.CONTENT_TYPE, "application/json")
                .set(HttpHeaderNames.ORIGIN, "http://localhost:3000");

        // Mock API response
        when(xdagApi.xdag_blockNumber()).thenReturn("12345");

        // Send request
        channel.writeInbound(request);
        FullHttpResponse response = channel.readOutbound();

        // Verify response
        assertNotNull(response);
        assertEquals(HttpResponseStatus.OK, response.status());
        assertEquals("http://localhost:3000", response.headers().get(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN));
        assertEquals("true", response.headers().get(HttpHeaderNames.ACCESS_CONTROL_ALLOW_CREDENTIALS));
        assertEquals("Origin", response.headers().get(HttpHeaderNames.VARY));

        String content = response.content().toString(StandardCharsets.UTF_8);

        // Verify the complete success response structure
        assertTrue("Response should contain jsonrpc version", content.contains("\"jsonrpc\":\"2.0\""));
        assertTrue("Response should contain result", content.contains("\"result\":\"12345\""));
//        assertTrue("Response should contain id", content.contains("\"id\":\"1\"");
        assertFalse("Response should not contain error", content.contains("\"error\""));
    }

    @Test
    public void testInternalError() {
        String validJson = "{\"jsonrpc\":\"2.0\",\"method\":\"xdag_blockNumber\",\"id\":\"1\"}";
        FullHttpRequest request = new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1,
                HttpMethod.POST,
                "/",
                Unpooled.copiedBuffer(validJson, StandardCharsets.UTF_8));
        request.headers()
                .set(HttpHeaderNames.CONTENT_TYPE, "application/json")
                .set(HttpHeaderNames.ORIGIN, "http://localhost:3000");

        // Mock API to throw exception
        when(xdagApi.xdag_blockNumber()).thenThrow(new RuntimeException("Internal error"));

        // Send request
        channel.writeInbound(request);
        FullHttpResponse response = channel.readOutbound();

        // Verify response
        assertNotNull(response);
        assertEquals(HttpResponseStatus.OK, response.status());
        assertEquals("http://localhost:3000", response.headers().get(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN));
        String content = response.content().toString(StandardCharsets.UTF_8);
        
        // Verify the complete error response structure
        assertTrue("Response should contain jsonrpc version", content.contains("\"jsonrpc\":\"2.0\""));
        assertTrue("Response should contain error code", content.contains("\"code\":-32603"));
        assertTrue("Response should contain error message", content.contains("\"message\":\"Internal error: Internal error\""));
//        assertTrue("Response should contain id", content.contains("\"id\":\"1\"");
        assertFalse("Response should not contain result", content.contains("\"result\""));
    }
} 