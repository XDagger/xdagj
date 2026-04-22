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

package io.xdag.rpc.error;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

@Getter
public class JsonRpcError {
    // Standard JSON-RPC 2.0 errors (-32768 to -32000)
    public static final int ERR_PARSE = -32700;              // Parse error: Invalid JSON
    public static final int ERR_INVALID_REQUEST = -32600;    // Invalid Request: The JSON sent is not a valid Request object
    public static final int ERR_METHOD_NOT_FOUND = -32601;   // Method not found: The method does not exist / is not available
    public static final int ERR_INVALID_PARAMS = -32602;     // Invalid params: Invalid method parameter(s)
    public static final int ERR_INTERNAL = -32603;           // Internal error: Internal JSON-RPC error
    public static final int ERR_SERVER = -32000;             // Server error: Generic server-side error

    // Success code
    public static final int SUCCESS = 0;                     // Operation completed successfully

    // XDAG Specific Errors (-10000 to -10999)
    // Address related errors (-10000 to -10099)
    public static final int ERR_XDAG_ADDRESS = -10000;       // Invalid XDAG address format
    public static final int ERR_XDAG_DEST = -10001;         // Invalid destination address

    // Block related errors (-10100 to -10199)
    public static final int ERR_XDAG_BLOCK = -10100;        // Block not found or invalid
    public static final int ERR_XDAG_TX = -10101;           // Transaction processing error

    // Balance related errors (-10200 to -10299)
    public static final int ERR_XDAG_FUNDS = -10200;        // Insufficient funds for transaction
    public static final int ERR_XDAG_BALANCE = -10201;      // Insufficient balance
    public static final int ERR_XDAG_AMOUNT = -10202;       // Invalid transaction amount

    // Wallet related errors (-10300 to -10399)
    public static final int ERR_XDAG_WALLET = -10300;       // Wallet operation error
    public static final int ERR_XDAG_WALLET_LOCKED = -10301; // Wallet is locked or incorrect password

    // Pool related errors (-10400 to -10499)
    public static final int ERR_XDAG_POOL = -10400;         // Mining pool operation error

    // Parameter related errors (-10500 to -10599)
    public static final int ERR_XDAG_PARAM = -10500;        // Invalid parameter value

    @JsonProperty("code")
    private final int code;

    @JsonProperty("message")
    private final String message;

    public JsonRpcError(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
