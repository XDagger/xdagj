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
//import com.fasterxml.jackson.annotation.JsonCreator;
//import com.fasterxml.jackson.annotation.JsonValue;
//import io.xdag.rpc.jsonrpc.JsonRpcResult;
//import io.xdag.rpc.utils.TypeConverter;
//
//import java.security.SecureRandom;
//import java.util.Arrays;
//
//public class SubscriptionId extends JsonRpcResult {
//    private final byte[] id;
//
//    @JsonCreator
//    public SubscriptionId(String hexId) {
//        this.id = TypeConverter.stringHexToByteArray(hexId);
//    }
//
//    public SubscriptionId() {
//        this.id = new byte[16];
//        new SecureRandom().nextBytes(id);
//    }
//
//    public byte[] getId() {
//        return Arrays.copyOf(id, id.length);
//    }
//
//    @Override
//    public int hashCode() {
//        return Arrays.hashCode(id);
//    }
//
//    @Override
//    public boolean equals(Object o) {
//        if (o == this) {
//            return true;
//        }
//
//        if (!(o instanceof SubscriptionId)) {
//            return false;
//        }
//
//        SubscriptionId other = (SubscriptionId) o;
//        return Arrays.equals(this.id, other.id);
//    }
//
//    @JsonValue
//    @SuppressWarnings("unused")
//    private String serialize() {
//        return TypeConverter.toJsonHex(id);
//    }
//}
