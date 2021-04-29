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

import io.xdag.crypto.Bip32ECKeyPair;
import io.xdag.crypto.Credentials;
import io.xdag.crypto.MnemonicUtils;
import io.xdag.crypto.SecureRandomUtils;

import java.io.File;
import java.io.IOException;

import static io.xdag.crypto.Bip32ECKeyPair.HARDENED_BIT;

public class Bip44WalletUtils extends WalletUtils {
    // https://github.com/satoshilabs/slips/blob/master/slip-0044.md
    public static final int XDAG_BIP44_CION_TYPE = 586;

    /**
     * Generates a BIP-44 compatible Ethereum wallet on top of BIP-39 generated seed.
     *
     * @param password Will be used for both wallet encryption and passphrase for BIP-39 seed
     * @param destinationDirectory The directory containing the wallet
     * @return A BIP-39 compatible Ethereum wallet
     * @throws CipherException if the underlying cipher is not available
     * @throws IOException if the destination cannot be written to
     */
    public static Bip39Wallet generateBip44Wallet(String password, File destinationDirectory)
            throws CipherException, IOException {
        byte[] initialEntropy = new byte[16];
        SecureRandomUtils.secureRandom().nextBytes(initialEntropy);

        String mnemonic = MnemonicUtils.generateMnemonic(initialEntropy);
        byte[] seed = MnemonicUtils.generateSeed(mnemonic, null);

        Bip32ECKeyPair masterKeypair = Bip32ECKeyPair.generateKeyPair(seed);
        Bip32ECKeyPair bip44Keypair = generateBip44KeyPair(masterKeypair);

        String walletFile = generateWalletFile(password, bip44Keypair, destinationDirectory, false);

        return new Bip39Wallet(walletFile, mnemonic);
    }

    public static Bip32ECKeyPair generateBip44KeyPair(Bip32ECKeyPair master) {
        // m/44'/586'/0'/0/0
        // xdag coin type 586 at https://github.com/satoshilabs/slips/blob/master/slip-0044.md
        final int[] path = {44 | HARDENED_BIT, XDAG_BIP44_CION_TYPE | HARDENED_BIT, 0 | HARDENED_BIT, 0, 0};
        return Bip32ECKeyPair.deriveKeyPair(master, path);

    }

    public static Credentials loadBip44Credentials(String password, String mnemonic) {
        byte[] seed = MnemonicUtils.generateSeed(mnemonic, password);
        Bip32ECKeyPair masterKeypair = Bip32ECKeyPair.generateKeyPair(seed);
        Bip32ECKeyPair bip44Keypair = generateBip44KeyPair(masterKeypair);
        return Credentials.create(bip44Keypair);
    }

}
