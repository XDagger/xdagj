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

package io.xdag.utils;

import static io.xdag.crypto.Bip32ECKeyPair.HARDENED_BIT;

import io.xdag.Wallet;
import io.xdag.crypto.Bip32ECKeyPair;
import io.xdag.crypto.MnemonicUtils;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class WalletUtils {

    // https://github.com/satoshilabs/slips/blob/master/slip-0044.md
    public static final int XDAG_BIP44_CION_TYPE = 586;

    public static final String WALLET_PASSWORD_PROMPT = "Please Enter Wallet Password: ";

    public static Bip32ECKeyPair generateBip44KeyPair(Bip32ECKeyPair master, int index) {
        // m/44'/586'/0'/0/0
        // xdag coin type 586 at https://github.com/satoshilabs/slips/blob/master/slip-0044.md
        final int[] path = {44 | HARDENED_BIT, XDAG_BIP44_CION_TYPE | HARDENED_BIT, 0 | HARDENED_BIT, 0, index};
        return Bip32ECKeyPair.deriveKeyPair(master, path);
    }

    public static Bip32ECKeyPair importMnemonic(Wallet wallet, String password, String mnemonic, int index) {
        wallet.unlock(password);
        byte[] seed = MnemonicUtils.generateSeed(mnemonic, password);
        Bip32ECKeyPair masterKeypair = Bip32ECKeyPair.generateKeyPair(seed);
        return generateBip44KeyPair(masterKeypair, index);
    }

}