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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.xdag.rpc.Web3;
import io.xdag.rpc.cors.CorsConfiguration;
import io.xdag.rpc.modules.ModuleDescription;
import okhttp3.*;
import org.junit.Test;
import org.mockito.Mockito;

import javax.net.ssl.*;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URL;
import java.security.cert.X509Certificate;
import java.util.*;

import static org.junit.Assert.assertEquals;

public class Web3HttpServerTest {
    public static final String APPLICATION_JSON = "application/json";
    private static final JsonNodeFactory JSON_NODE_FACTORY = JsonNodeFactory.instance;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    public void smokeTestUsingJsonContentType() throws Exception {
        smokeTest(APPLICATION_JSON);
    }

    @Test
    public void smokeTestUsingJsonRpcContentType() throws Exception {
        smokeTest("application/json-rpc");
    }

    @Test(expected = IOException.class)
    public void smokeTestUsingInvalidContentType() throws Exception {
        smokeTest("text/plain");
    }

    @Test
    public void smokeTestUsingValidHost() throws Exception {
        smokeTest(APPLICATION_JSON, "localhost");
    }

    @Test(expected = IOException.class)
    public void smokeTestUsingInvalidHost() throws Exception {
        smokeTest(APPLICATION_JSON, "evil.com");
    }

    @Test
    public void smokeTestUsingValidHostAndHostName() throws Exception {
        String domain = "www.google.com";
        List<String> rpcHost = new ArrayList<>();
        rpcHost.add(domain);
        smokeTest(APPLICATION_JSON, domain, InetAddress.getByName(domain), rpcHost);
    }

    @Test
    public void smokeTestUsingInvalidHostAndHostName() throws Exception {
        InetAddress google = InetAddress.getByName("www.google.com");
        smokeTest(APPLICATION_JSON, google.getHostAddress(), google, new ArrayList<>());
    }


    private void smokeTest(String contentType, String host) throws Exception {
        smokeTest(contentType, host, InetAddress.getLoopbackAddress(), new ArrayList<>());
    }

    private void smokeTest(String contentType, String host, InetAddress rpcAddress, List<String> rpcHost) throws Exception {
        Web3 web3Mock = Mockito.mock(Web3.class);
        String mockResult = "output";
        Mockito.when(web3Mock.web3_sha3(Mockito.anyString())).thenReturn(mockResult);
        CorsConfiguration mockCorsConfiguration = Mockito.mock(CorsConfiguration.class);
        Mockito.when(mockCorsConfiguration.hasHeader()).thenReturn(true);
        Mockito.when(mockCorsConfiguration.getHeader()).thenReturn("*");

        int randomPort = 9999;//new ServerSocket(0).getLocalPort();

        List<ModuleDescription> filteredModules = Collections.singletonList(new ModuleDescription("web3", "1.0", true, Collections.emptyList(), Collections.emptyList()));
        JsonRpcWeb3FilterHandler filterHandler = new JsonRpcWeb3FilterHandler("*", rpcAddress, rpcHost);
        JsonRpcWeb3ServerHandler serverHandler = new JsonRpcWeb3ServerHandler(web3Mock, filteredModules);
        Web3HttpServer server = new Web3HttpServer(InetAddress.getLoopbackAddress(), randomPort, 0, Boolean.TRUE, mockCorsConfiguration, filterHandler, serverHandler);
        server.start();
        try {
            Response response = sendJsonRpcMessage(randomPort, contentType, host);
            JsonNode jsonRpcResponse = OBJECT_MAPPER.readTree(Objects.requireNonNull(response.body()).string());

            assertEquals(response.code(), HttpResponseStatus.OK.code());
            assertEquals(jsonRpcResponse.at("/result").asText(), mockResult);
        } finally {
            server.stop();
        }
    }

    private void smokeTest(String contentType) throws Exception {
        smokeTest(contentType, "127.0.0.1");
    }

    private Response sendJsonRpcMessage(int port, String contentType, String host) throws IOException {
        Map<String, JsonNode> jsonRpcRequestProperties = new HashMap<>();
        jsonRpcRequestProperties.put("jsonrpc", JSON_NODE_FACTORY.textNode("2.0"));
        jsonRpcRequestProperties.put("id", JSON_NODE_FACTORY.numberNode(13));
        jsonRpcRequestProperties.put("method", JSON_NODE_FACTORY.textNode("web3_sha3"));
        jsonRpcRequestProperties.put("params", JSON_NODE_FACTORY.arrayNode().add("value"));

        RequestBody requestBody = RequestBody.Companion.create(JSON_NODE_FACTORY.objectNode()
                .setAll(jsonRpcRequestProperties).toString(),MediaType.parse(contentType));
        URL url = new URL("http", "localhost", port, "/");
        Request request = new Request.Builder().url(url)
                .addHeader("Host", host)
//                .addHeader("Accept-Encoding", "identity")
                .post(requestBody).build();
        return getUnsafeOkHttpClient().newCall(request).execute();
    }

    private static OkHttpClient getUnsafeOkHttpClient() {
        try {
            // Create a trust manager that does not validate certificate chains
            final TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        @Override
                        public void checkClientTrusted(java.security.cert.X509Certificate[] chain,
                                                       String authType) {
                        }

                        @Override
                        public void checkServerTrusted(java.security.cert.X509Certificate[] chain,
                                                       String authType) {
                        }

                        @Override
                        public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[0];
                        }
                    }
            };
            // Install the all-trusting trust manager
            final SSLContext sslContext = SSLContext.getInstance("SSL");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
            // Create an ssl socket factory with our all-trusting manager
            final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();


            return new OkHttpClient.Builder()
                    .sslSocketFactory(sslSocketFactory,(X509TrustManager)trustAllCerts[0])
                    .hostnameVerifier((hostname, session) -> true)
                    .build();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
