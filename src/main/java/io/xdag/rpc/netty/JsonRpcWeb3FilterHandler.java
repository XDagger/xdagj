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

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.xdag.rpc.cors.OriginValidator;
import io.xdag.rpc.utils.HttpUtils;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ChannelHandler.Sharable
public class JsonRpcWeb3FilterHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private static final Logger logger = LoggerFactory.getLogger("jsonrpc");
    private final List<String> rpcHost;
    private final InetAddress rpcAddress;
    private final List<String> acceptedHosts;

    private final OriginValidator originValidator;

    public JsonRpcWeb3FilterHandler(String corsDomains, InetAddress rpcAddress, List<String> rpcHost) {
        this.originValidator = new OriginValidator(corsDomains);
        this.rpcHost = rpcHost;
        this.rpcAddress = rpcAddress;
        this.acceptedHosts = getAcceptedHosts();
    }

    private List<String> getAcceptedHosts() {
        List<String> hosts = new ArrayList<>();
        if (isAcceptedAddress(rpcAddress)) {
            hosts.add(rpcAddress.getHostName());
            hosts.add(rpcAddress.getHostAddress());
        } else {
            for (String host : rpcHost) {
                try {
                    InetAddress hostAddress = InetAddress.getByName(host);
                    if (!hostAddress.isAnyLocalAddress()) {
                        hosts.add(hostAddress.getHostAddress());
                        hosts.add(hostAddress.getHostName());
                    } else {
                        logger.warn("Wildcard address is not allowed on rpc host property {}", hostAddress);
                    }
                } catch (UnknownHostException e) {
                    logger.warn("Invalid Host defined on rpc.host", e);
                }
            }
        }
        return hosts;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
        HttpResponse response;
        HttpMethod httpMethod = request.method();
        HttpHeaders headers = request.headers();

        // when a request has multiple host fields declared it would be equivalent to a comma separated list
        // the request will be inmediately rejected since it won't be parsed as a valid URI
        // and won't work to match an item on rpc.host
//        String hostHeader = headers.get(HttpHeaderNames.HOST);
//        String parsedHeader = parseHostHeader(hostHeader);

        // TODO:暂时让所有人都可以调用
//        if (!acceptedHosts.contains(parsedHeader)) {
//            logger.debug("Invalid header HOST {}", hostHeader);
//            response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_REQUEST);
//            ctx.write(response).addListener(ChannelFutureListener.CLOSE);
//            return;
//        }

        if (HttpMethod.POST.equals(httpMethod)) {

            String mimeType = HttpUtils.getMimeType(headers.get(HttpHeaderNames.CONTENT_TYPE));
            String origin = headers.get(HttpHeaderNames.ORIGIN);
            String referer = headers.get(HttpHeaderNames.REFERER);

            if (!"application/json".equals(mimeType) && !"application/json-rpc".equals(mimeType)) {
                logger.debug("Unsupported content type");
                response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.UNSUPPORTED_MEDIA_TYPE);
            } else if (origin != null && !this.originValidator.isValidOrigin(origin)) {
                logger.debug("Invalid origin");
                response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_REQUEST);
            } else if (referer != null && !this.originValidator.isValidReferer(referer)) {
                logger.debug("Invalid referer");
                response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_REQUEST);
            } else {
                ctx.fireChannelRead(request);
                return;
            }
        } else {
            response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_IMPLEMENTED);
        }

        ctx.write(response).addListener(ChannelFutureListener.CLOSE);
    }

    private String parseHostHeader(String hostHeader) {
        try {
            // WORKAROUND: add any scheme to make the resulting URI valid.
            URI uri = new URI("my://" + hostHeader); // may throw URISyntaxException
            return uri.getHost();
        } catch (URISyntaxException e) {
            return hostHeader;
        }
    }

    private boolean isAcceptedAddress(final InetAddress address) {
        // Check if the address is a valid special local or loop back
        if (address.isLoopbackAddress()) {
            return true;
        }
        // Check if the address is defined on any interface
        try {
            return !address.isAnyLocalAddress() && NetworkInterface.getByInetAddress(address) != null;
        } catch (SocketException se) {
            return false;
        }
    }

}
