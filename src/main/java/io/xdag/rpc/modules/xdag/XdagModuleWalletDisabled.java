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

package io.xdag.rpc.modules.xdag;

import static io.xdag.rpc.exception.XdagJsonRpcRequestException.invalidParamError;

import java.util.Arrays;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class XdagModuleWalletDisabled implements XdagModuleWallet {

    @Override
    public String[] accounts() {
        String[] accounts = {};
        log.debug("xdag_accounts(): {}", Arrays.toString(accounts));
        return accounts;
    }

    @Override
    public String sign(String addr, String data) {
        log.debug("eth_sign({}, {}): {}", addr, data, null);
        throw invalidParamError("Local wallet is disabled in this node");
    }
}
