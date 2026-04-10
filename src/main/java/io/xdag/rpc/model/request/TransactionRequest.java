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
package io.xdag.rpc.model.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor  // Add this annotation for default constructor
public class TransactionRequest {
    @JsonProperty("from")
    private String from;  // Change to private for better encapsulation

    @JsonProperty("to")
    private String to;

    @JsonProperty("value")
    private String value;

    @JsonProperty("remark")
    private String remark;

    @JsonProperty("nonce")
    private String nonce;

    @JsonProperty("fee")
    private String fee;

    // Add all-args constructor with JsonCreator
    @JsonCreator
    public TransactionRequest(
            @JsonProperty("from") String from,
            @JsonProperty("to") String to,
            @JsonProperty("value") String value,
            @JsonProperty("remark") String remark,
            @JsonProperty("nonce") String nonce,
            @JsonProperty("fee") String fee) {
        this.from = from;
        this.to = to;
        this.value = value;
        this.remark = remark;
        this.nonce = nonce;
        this.fee = fee;
    }
}
