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
package io.xdag.wallet;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.xdag.crypto.*;

import java.io.File;
import java.io.IOException;
import java.util.Optional;

/**
 * Wallet Manager
 */
public class WalletManager {

    /**
     * Create BIP44 Xdag Wallet
     *
     * @param password User Password
     * @param destinationDirectory Keystore Directory
     */
    public static Optional<Credentials> createBip44Wallet(String password, File destinationDirectory) throws CipherException, IOException {
        Bip39Wallet wallet = Bip44WalletUtils.generateBip44Wallet(password, destinationDirectory);
        Credentials credentials = Bip44WalletUtils.loadBip44Credentials(password, wallet.getMnemonic());
        return Optional.of(credentials);
    }

    /**
     * Import BIP44 Wallet From Mnemonic
     *
     * @param password User Password
     * @param mnemonic Mnemonic of 12 Worlds
     */
    public static Optional<Credentials> importBip44WalletFromMnemonic(String password, String mnemonic)  {
        return Optional.of(Bip44WalletUtils.loadBip44Credentials(password, mnemonic));
    }

    /**
     * Import BIP44 Wallet From Keystore
     *
     * @param password User Password
     * @param keystore Keystore String
     */
    public static Optional<Credentials> importBip44WalletFromKeystore(String password, String keystore) throws JsonProcessingException, CipherException {
        ObjectMapper objectMapper = new ObjectMapper();
        WalletFile walletFile = objectMapper.readValue(keystore, WalletFile.class);
        ECKeyPair ecKeyPair = Wallet.decrypt(password, walletFile);
        return Optional.of(Credentials.create(ecKeyPair));
    }
}
