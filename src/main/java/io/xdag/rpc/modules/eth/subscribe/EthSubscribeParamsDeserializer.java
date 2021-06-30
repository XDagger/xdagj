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
//package io.xdag.rpc.modules.eth.subscribe;
//
//import com.fasterxml.jackson.core.JsonParser;
//import com.fasterxml.jackson.core.JsonToken;
//import com.fasterxml.jackson.databind.DeserializationContext;
//import com.fasterxml.jackson.databind.JsonDeserializer;
//
//import java.io.IOException;
//import java.util.HashMap;
//
//public class EthSubscribeParamsDeserializer extends JsonDeserializer {
//
//    private final HashMap<String, Class<? extends EthSubscribeParams>> subscriptionTypes;
//
//    public EthSubscribeParamsDeserializer() {
//        this.subscriptionTypes = new HashMap<>();
//        this.subscriptionTypes.put("newHeads", EthSubscribeNewHeadsParams.class);
//        this.subscriptionTypes.put("logs", EthSubscribeLogsParams.class);
//    }
//
//    @Override
//    public Object deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
//        if (!p.isExpectedStartArrayToken()) {
//            return ctxt.handleUnexpectedToken(
//                    EthSubscribeParams.class,
//                    p.currentToken(),
//                    p,
//                    "eth_subscribe parameters are expected to be arrays"
//            );
//        }
//        p.nextToken(); // skip '['
//        String subscriptionType = p.getText();
//        Class<? extends EthSubscribeParams> subscriptionTypeClass = subscriptionTypes.get(subscriptionType);
//        p.nextToken();
//        EthSubscribeParams params;
//        if (p.isExpectedStartObjectToken()) {
//            params = p.readValueAs(subscriptionTypeClass);
//            p.nextToken();
//        } else {
//            try {
//                params = subscriptionTypeClass.newInstance();
//            } catch (InstantiationException | IllegalAccessException e) {
//                return ctxt.handleInstantiationProblem(
//                        subscriptionTypeClass,
//                        null,
//                        e
//                );
//            }
//        }
//        if (p.currentToken() != JsonToken.END_ARRAY) {
//            return ctxt.handleUnexpectedToken(
//                    EthSubscribeParams.class,
//                    p.currentToken(),
//                    p,
//                    "eth_subscribe can only have one object to configure subscription"
//            );
//        }
//        return params;
//    }
//}
