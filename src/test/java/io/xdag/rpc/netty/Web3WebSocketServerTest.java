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
package io.xdag.rpc.netty;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.xdag.rpc.Web3;
import io.xdag.rpc.modules.ModuleDescription;
import io.xdag.rpc.serialize.JacksonBasedRpcSerializer;
import lombok.SneakyThrows;
import okhttp3.*;
import okio.Buffer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static io.xdag.rpc.netty.Web3HttpServerTest.APPLICATION_JSON;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.refEq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class Web3WebSocketServerTest {
    private static JsonNodeFactory JSON_NODE_FACTORY = JsonNodeFactory.instance;
    private static ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private ExecutorService wsExecutor;

    @Before
    public void setup() {
        wsExecutor = Executors.newSingleThreadExecutor();
    }

    @Test
    public void smokeTest() throws Exception {
        Web3 web3Mock = mock(Web3.class);
        String mockResult = "output";
        when(web3Mock.web3_sha3(anyString())).thenReturn(mockResult);

        int randomPort = 9998;//new ServerSocket(0).getLocalPort();

        List<ModuleDescription> filteredModules = Collections.singletonList(new ModuleDescription("web3", "1.0", true, Collections.emptyList(), Collections.emptyList()));
        XdagJsonRpcHandler handler = new XdagJsonRpcHandler( new JacksonBasedRpcSerializer());
        JsonRpcWeb3ServerHandler serverHandler = new JsonRpcWeb3ServerHandler(web3Mock, filteredModules);

        Web3WebSocketServer websocketServer = new Web3WebSocketServer(InetAddress.getLoopbackAddress(), randomPort, handler, serverHandler);
        websocketServer.start();

        OkHttpClient wsClient = new OkHttpClient();
        Request wsRequest = new Request.Builder().url("ws://localhost:"+randomPort+"/websocket").build();

        CountDownLatch wsAsyncResultLatch = new CountDownLatch(1);
        CountDownLatch wsAsyncCloseLatch = new CountDownLatch(1);
        AtomicReference<Throwable> failureReference = new AtomicReference<>();

        wsClient.newWebSocket(wsRequest,new WebSocketListener() {

            private WebSocket webSocket;

            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                wsExecutor.submit(() -> {
                    Buffer buffer = new Buffer();
                    try {
                        RequestBody.create( getJsonRpcDummyMessage(),MediaType.get(APPLICATION_JSON)).writeTo(buffer);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    String req =  buffer.readUtf8();
                    try {
                        this.webSocket = webSocket;
                        this.webSocket.send(req);
                        this.webSocket.close(1000, null);
                    } catch (Throwable e) {
                        failureReference.set(e);
                    }
                });
            }

            @Override
            public void onFailure(@NotNull WebSocket webSocket, @NotNull Throwable t, @Nullable Response response) {
                failureReference.set(t);
            }

            @SneakyThrows
            @Override
            public void onMessage(@NotNull WebSocket webSocket, @NotNull String bytes) {
                JsonNode jsonRpcResponse = OBJECT_MAPPER.readTree(bytes);
                assertEquals(jsonRpcResponse.at("/result").asText(),mockResult);
                wsAsyncResultLatch.countDown();
            }

//            @Override
//            public void onClosed(@NotNull WebSocket webSocket, int code, @NotNull String reason) {
//                wsAsyncCloseLatch.countDown();
//            }

            @Override
            public void onClosing(@NotNull WebSocket webSocket, int code, @NotNull String reason) {
                wsAsyncCloseLatch.countDown();
            }
        });


        if (!wsAsyncResultLatch.await(10, TimeUnit.SECONDS)) {
            fail("Result timed out");
        }

        if (!wsAsyncCloseLatch.await(10, TimeUnit.SECONDS)) {
            fail("Close timed out");
        }

        websocketServer.stop();

        Throwable failure = failureReference.get();
        if (failure != null) {
            failure.printStackTrace();
            fail(failure.getMessage());
        }
    }

    @After
    public void tearDown() {
        wsExecutor.shutdown();
    }

    private byte[] getJsonRpcDummyMessage() {
        Map<String, JsonNode> jsonRpcRequestProperties = new HashMap<>();
        jsonRpcRequestProperties.put("jsonrpc", JSON_NODE_FACTORY.textNode("2.0"));
        jsonRpcRequestProperties.put("id", JSON_NODE_FACTORY.numberNode(13));
        jsonRpcRequestProperties.put("method", JSON_NODE_FACTORY.textNode("web3_sha3"));
        jsonRpcRequestProperties.put("params", JSON_NODE_FACTORY.arrayNode().add("value"));

        byte[] request = new byte[0];
        try {
            request = OBJECT_MAPPER.writeValueAsBytes(OBJECT_MAPPER.treeToValue(
                    JSON_NODE_FACTORY.objectNode().setAll(jsonRpcRequestProperties), Object.class));
        } catch (JsonProcessingException e) {
            fail(e.getMessage());
        }
        return request;

    }
}
