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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.xdag.config.Config;
import io.xdag.crypto.ECKey;
import io.xdag.crypto.bip32.DeterministicKey;
import io.xdag.crypto.bip32.DeterministicSeed;
import io.xdag.crypto.bip39.MnemonicCode;
import io.xdag.crypto.bip39.MnemonicException;
import io.xdag.crypto.bip44.HDKeyDerivation;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

public class WalletUtils {

    private static final String HEX_PREFIX = "0x";
    private static final char[] HEX_CHAR_MAP = "0123456789abcdef".toCharArray();

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static byte[] toBytesPadded(BigInteger value, int length) {
        byte[] result = new byte[length];
        byte[] bytes = value.toByteArray();

        int bytesLength;
        int srcOffset;
        if (bytes[0] == 0) {
            bytesLength = bytes.length - 1;
            srcOffset = 1;
        } else {
            bytesLength = bytes.length;
            srcOffset = 0;
        }

        if (bytesLength > length) {
            throw new RuntimeException("Input is too large to put in byte array of size " + length);
        }

        int destOffset = length - bytesLength;
        System.arraycopy(bytes, srcOffset, result, destOffset, bytesLength);
        return result;
    }

    public static String toHexStringNoPrefix(byte[] input) {
        return toHexString(input, 0, input.length, false);
    }

    public static String toHexString(byte[] input, int offset, int length, boolean withPrefix) {
        final String output = new String(toHexCharArray(input, offset, length));
        return withPrefix ? HEX_PREFIX + output : output;
    }

    private static char[] toHexCharArray(byte[] input, int offset, int length) {
        final char[] output = new char[length << 1];
        for (int i = offset, j = 0; i < length; i++, j++) {
            final int v = input[i] & 0xFF;
            output[j++] = HEX_CHAR_MAP[v >>> 4];
            output[j] = HEX_CHAR_MAP[v & 0x0F];
        }
        return output;
    }

    public static byte[] hexStringToByteArray(String input) {
        String cleanInput = cleanHexPrefix(input);
        int len = cleanInput.length();
        if (len == 0) {
            return new byte[] {};
        }
        byte[] data;
        int startIdx;
        if (len % 2 != 0) {
            data = new byte[(len / 2) + 1];
            data[0] = (byte) Character.digit(cleanInput.charAt(0), 16);
            startIdx = 1;
        } else {
            data = new byte[len / 2];
            startIdx = 0;
        }

        for (int i = startIdx; i < len; i += 2) {
            data[(i + 1) / 2] =
                    (byte)
                            ((Character.digit(cleanInput.charAt(i), 16) << 4)
                                    + Character.digit(cleanInput.charAt(i + 1), 16));
        }
        return data;
    }

    public static String cleanHexPrefix(String input) {
        if (containsHexPrefix(input)) {
            return input.substring(2);
        } else {
            return input;
        }
    }

    public static boolean containsHexPrefix(String input) {
        return !StringUtils.isEmpty(input)
                && input.length() > 1
                && input.charAt(0) == '0'
                && input.charAt(1) == 'x';
    }

    private static String getWalletFileName(WalletFile walletFile) {
        DateTimeFormatter format = DateTimeFormatter.ofPattern("'UTC--'yyyy-MM-dd'T'HH-mm-ss.nVV'--'");
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        return now.format(format) + walletFile.getAddress() + ".json";
    }

    public static String getTestnetKeyDirectory() {
        return String.format("%s%stestnet%skeystore", getDefaultKeyDirectory(), File.separator, File.separator);
    }

    public static String getMainnetKeyDirectory() {
        return String.format("%s%skeystore", getDefaultKeyDirectory(), File.separator);
    }

    public static String getDefaultKeyDirectory() {
        return getDefaultKeyDirectory(System.getProperty("os.name"));
    }

    static String getDefaultKeyDirectory(String osName1) {
        String osName = osName1.toLowerCase();

        if (osName.startsWith("mac")) {
            return String.format("%s%sLibrary%sxdagj", System.getProperty("user.home"), File.separator, File.separator);
        } else if (osName.startsWith("win")) {
            return String.format("%s%sxdagj", System.getenv("APPDATA"), File.separator);
        } else {
            return String.format("%s%s.xdagj", System.getProperty("user.home"), File.separator);
        }
    }

    public static String generateWalletFile(
            Config config, String password, ECKey key, File destinationDirectory, boolean useFullScrypt)
            throws CipherException, IOException {
        WalletFile walletFile;
        if (useFullScrypt) {
            walletFile = Wallet.createStandard(config, password, key);
        } else {
            walletFile = Wallet.createLight(config, password, key);
        }
        String fileName = getWalletFileName(walletFile);
        File destination = new File(destinationDirectory, fileName);
        objectMapper.writeValue(destination, walletFile);
        return fileName;
    }

    public static String generateFullNewWalletFile(Config config, String password, File destinationDirectory)
            throws CipherException, IOException {
        return generateNewWalletFile(config, password, destinationDirectory, true);
    }

    /**
     * Generates a BIP-39 compatible Xdag wallet. The private key for the wallet can be
     * calculated using following algorithm:
     *
     * <pre>
     *     Key = SHA-256(BIP_39_SEED(mnemonic, password))
     * </pre>
     *
     * @param config  testnetConfig or mainnetConfig
     * @param password Will be used for both wallet encryption and passphrase for BIP-39 seed
     * @param destinationDirectory The directory containing the wallet
     * @return A BIP-39 compatible Xdag wallet
     * @throws CipherException if the underlying cipher is not available
     * @throws IOException if the destination cannot be written to
     * @throws MnemonicException.MnemonicLengthException if the mnemonic error
     */
    public static Bip39Wallet generateBip39Wallet(Config config, String password, File destinationDirectory)
            throws CipherException, IOException, MnemonicException.MnemonicLengthException {
        byte[] initialEntropy = new byte[16];
        long creationtime = System.currentTimeMillis();
        SecureRandom secureRandom = new SecureRandom();
        secureRandom.nextBytes(initialEntropy);
        List<String> mnemonicCode = MnemonicCode.INSTANCE.toMnemonic(initialEntropy);
        MnemonicCode.SPACE_JOINER.join(mnemonicCode);
        String mnemonic = MnemonicCode.SPACE_JOINER.join(mnemonicCode);
        DeterministicSeed seed = new DeterministicSeed(mnemonic, null, password, creationtime);
        DeterministicKey masterPrivateKey = HDKeyDerivation.createMasterPrivateKey(Objects.requireNonNull(seed.getSeedBytes()));
        String walletFile = generateWalletFile(config, password, masterPrivateKey, destinationDirectory, false);
        return new Bip39Wallet(walletFile, mnemonic);
    }

    /**
     * Generates a BIP-39 compatible Xdag wallet using a mnemonic passed as argument.
     *
     * @param config  testnetConfig or mainnetConfig
     * @param password Will be used for both wallet encryption and passphrase for BIP-39 seed
     * @param mnemonic The mnemonic that will be used to generate the seed
     * @param destinationDirectory The directory containing the wallet
     * @return A BIP-39 compatible Xdag wallet
     * @throws CipherException if the underlying cipher is not available
     * @throws IOException if the destination cannot be written to
     */
    public static Bip39Wallet generateBip39WalletFromMnemonic(
            Config config, String password, String mnemonic, File destinationDirectory)
            throws CipherException, IOException {
        long creationtime = System.currentTimeMillis();
        DeterministicSeed seed = new DeterministicSeed(mnemonic, null, password, creationtime);
        DeterministicKey masterPrivateKey = HDKeyDerivation.createMasterPrivateKey(Objects.requireNonNull(seed.getSeedBytes()));
        String walletFile = generateWalletFile(config, password, masterPrivateKey, destinationDirectory, false);
        return new Bip39Wallet(walletFile, mnemonic);
    }

    public static String generateNewWalletFile(Config config, String password, File destinationDirectory)
            throws CipherException,
            IOException {
        return generateFullNewWalletFile(config, password, destinationDirectory);
    }

    public static String generateNewWalletFile(
            Config config, String password, File destinationDirectory, boolean useFullScrypt)
            throws CipherException, IOException{
        return generateWalletFile(config, password, new ECKey(), destinationDirectory, useFullScrypt);
    }

    public static ECKey loadECKey(String password, File source)
            throws IOException, CipherException {
        WalletFile walletFile = objectMapper.readValue(source, WalletFile.class);
        return Wallet.decrypt(password, walletFile);
    }

}
