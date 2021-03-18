/*
 * Copyright 2021 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package io.xdag.nat;

import okhttp3.*;
import org.jupnp.model.message.*;
import org.jupnp.transport.impl.jetty.StreamClientConfigurationImpl;
import org.jupnp.transport.spi.AbstractStreamClient;

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
      streamResponseMessage.setBodyCharacters(httpResponse.body().bytes());
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
