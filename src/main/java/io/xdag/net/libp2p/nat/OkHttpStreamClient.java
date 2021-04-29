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
package io.xdag.net.libp2p.nat;

import okhttp3.*;
import org.jupnp.model.message.*;
import org.jupnp.transport.impl.jetty.StreamClientConfigurationImpl;
import org.jupnp.transport.spi.AbstractStreamClient;

import java.util.Objects;
import java.util.concurrent.Callable;

public class OkHttpStreamClient extends AbstractStreamClient<StreamClientConfigurationImpl, Call> {

  private final StreamClientConfigurationImpl config;
  private final OkHttpClient client;

  OkHttpStreamClient(final StreamClientConfigurationImpl config) {
    this.config = config;
    client = new OkHttpClient();
  }

  @Override
  protected Call createRequest(final StreamRequestMessage requestMessage) {

    final UpnpRequest.Method method = requestMessage.getOperation().getMethod();
    final RequestBody body;
    if (method == UpnpRequest.Method.POST || method == UpnpRequest.Method.NOTIFY) {
      final MediaType mediaType = MediaType.get(requestMessage.getContentTypeHeader().getString());
      if (requestMessage.getBodyType() == UpnpMessage.BodyType.STRING) {
        body = RequestBody.create(requestMessage.getBodyString(), mediaType);
      } else {
        body = RequestBody.create(requestMessage.getBodyBytes(), mediaType);
      }
    } else {
      body = null;
    }

    final Headers.Builder headersBuilder = new Headers.Builder();
    requestMessage.getHeaders().forEach((k, v) -> v.forEach(s -> headersBuilder.add(k, s)));

    final Request request =
        new Request.Builder()
            .url(requestMessage.getUri().toString())
            .method(requestMessage.getOperation().getHttpMethodName(), body)
            .headers(headersBuilder.build())
            .build();
    return client.newCall(request);
  }

  @Override
  protected Callable<StreamResponseMessage> createCallable(
      final StreamRequestMessage requestMessage, final Call call) {
    return () -> {
      final Response httpResponse = call.execute();
      final UpnpResponse upnpResponse =
          new UpnpResponse(httpResponse.code(), httpResponse.message());
      final StreamResponseMessage streamResponseMessage = new StreamResponseMessage(upnpResponse);
      streamResponseMessage.setHeaders(new UpnpHeaders(httpResponse.headers().toMultimap()));
      streamResponseMessage.setBodyCharacters(Objects.requireNonNull(httpResponse.body()).bytes());
      return streamResponseMessage;
    };
  }

  @Override
  protected void abort(final Call call) {
    call.cancel();
  }

  @Override
  protected boolean logExecutionException(final Throwable t) {
    return false;
  }

  @Override
  public void stop() {}

  @Override
  public StreamClientConfigurationImpl getConfiguration() {
    return config;
  }
}
