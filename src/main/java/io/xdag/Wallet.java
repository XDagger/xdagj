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

package io.xdag;

import static io.xdag.core.XdagField.FieldType.XDAG_FIELD_OUTPUT;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.attribute.PosixFilePermission.OWNER_READ;
import static java.nio.file.attribute.PosixFilePermission.OWNER_WRITE;

import com.google.common.collect.Lists;
import io.xdag.config.Config;
import io.xdag.core.*;
import io.xdag.utils.SimpleEncoder;
import io.xdag.crypto.Aes;
import io.xdag.crypto.Bip32ECKeyPair;
import io.xdag.crypto.Keys;
import io.xdag.utils.MnemonicUtils;
import io.xdag.crypto.Sign;
import io.xdag.utils.Numeric;
import io.xdag.utils.SimpleDecoder;
import io.xdag.utils.WalletUtils;
import io.xdag.utils.XdagTime;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Security;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt64;
import org.bouncycastle.crypto.generators.BCrypt;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.crypto.SECPPrivateKey;
import org.hyperledger.besu.crypto.SecureRandomProvider;

@Slf4j
@Getter
@Setter
public class Wallet {

    public static final Set<PosixFilePermission> POSIX_SECURED_PERMISSIONS = Set.of(OWNER_READ, OWNER_WRITE);
    private static final int VERSION = 4;
    private static final int SALT_LENGTH = 16;
    private static final int BCRYPT_COST = 12;
    private static final String MNEMONIC_PASS_PHRASE = "";
    /**
     * Returns the file where the wallet is persisted.
     */
    private final File file;
    private final Config config;

    private final Map<Bytes, KeyPair> accounts = Collections.synchronizedMap(new LinkedHashMap<>());
    private String password;

    // HD wallet key
    private String mnemonicPhrase = "";
    private int nextAccountIndex = 0;

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    /**
     * Creates a new wallet instance.
     */
    public Wallet(Config config) {
        this.file = FileUtils.getFile(config.getWalletSpec().getWalletFilePath());
        this.config = config;
    }

    /**
     * Returns whether the wallet file exists and non-empty.
     */
    public boolean exists() {
        return file.length() > 0;
    }

    /**
     * Deletes the wallet file.
     */
    public void delete() throws IOException {
        Files.delete(file.toPath());
    }

    /**
     * Locks the wallet.
     */
    public void lock() {
        password = null;
        accounts.clear();
    }

    public KeyPair getDefKey() {
        List<KeyPair> accountList = getAccounts();
        if (CollectionUtils.isNotEmpty(accountList)) {
            return accountList.get(0);
        }
        return null;
    }

    /**
     * Unlocks this wallet
     */
    public boolean unlock(String password) {
        if (password == null) {
            throw new IllegalArgumentException("Password can not be null");
        }

        try {
            byte[] key;
            byte[] salt;

            if (exists()) {

                SimpleDecoder dec = new SimpleDecoder(FileUtils.readFileToByteArray(file));
                int version = dec.readInt(); // version

                Set<KeyPair> newAccounts;
                switch (version) {
                    // only version 4
                    case 4 -> {
                        salt = dec.readBytes();
                        key = BCrypt.generate(password.getBytes(UTF_8), salt, BCRYPT_COST);
                        try {
                            newAccounts = readAccounts(key, dec, true, version);
                            readHdSeed(key, dec);
                        } catch (Exception e) {
                            log.warn("Failed to read HD mnemonic phrase");
                            return false;
                        }
                    }
                    default -> throw new RuntimeException("Unknown wallet version.");
                }

                synchronized (accounts) {
                    accounts.clear();
                    for (KeyPair account : newAccounts) {
                        Bytes b = Bytes.wrap(Keys.toBytesAddress(account));
                        accounts.put(b, account);
                    }
                }
            }
            this.password = password;
            return true;
        } catch (Exception e) {
            log.error("Failed to open wallet", e);
        }
        return false;
    }

    /**
     * Reads the account keys.
     */
    protected LinkedHashSet<KeyPair> readAccounts(byte[] key, SimpleDecoder dec, boolean vlq, int version) {
        LinkedHashSet<KeyPair> keys = new LinkedHashSet<>();
        int total = dec.readInt(); // size

        for (int i = 0; i < total; i++) {
            byte[] iv = dec.readBytes(vlq);
            byte[] privateKey = Aes.decrypt(dec.readBytes(vlq), key, iv);
            KeyPair keyPair = KeyPair.create(SECPPrivateKey.create(Numeric.toBigInt(privateKey), Sign.CURVE_NAME), Sign.CURVE, Sign.CURVE_NAME);
            keys.add(keyPair);
        }
        return keys;
    }

    /**
     * Writes the account keys.
     */
    protected void writeAccounts(byte[] key, SimpleEncoder enc) {
        synchronized (accounts) {
            enc.writeInt(accounts.size());
            for (KeyPair keyPair : accounts.values()) {
                byte[] iv = SecureRandomProvider.publicSecureRandom().generateSeed(16);

                enc.writeBytes(iv);
                enc.writeBytes(Aes.encrypt(keyPair.getPrivateKey().getEncoded(), key, iv));
            }
        }
    }

    /**
     * Reads the mnemonic phase and next account index.
     */
    protected void readHdSeed(byte[] key, SimpleDecoder dec) {
        byte[] iv = dec.readBytes();
        byte[] hdSeedEncrypted = dec.readBytes();
        byte[] hdSeedRaw = Aes.decrypt(hdSeedEncrypted, key, iv);

        SimpleDecoder d = new SimpleDecoder(hdSeedRaw);
        mnemonicPhrase = d.readString();
        nextAccountIndex = d.readInt();
    }

    /**
     * Writes the mnemonic phase and next account index.
     */
    protected void writeHdSeed(byte[] key, SimpleEncoder enc) {
        SimpleEncoder e = new SimpleEncoder();
        e.writeString(mnemonicPhrase);
        e.writeInt(nextAccountIndex);

        byte[] iv = SecureRandomProvider.publicSecureRandom().generateSeed(16);
        byte[] hdSeedRaw = e.toBytes();
        byte[] hdSeedEncrypted = Aes.encrypt(hdSeedRaw, key, iv);

        enc.writeBytes(iv);
        enc.writeBytes(hdSeedEncrypted);
    }

    /**
     * Returns if this wallet is unlocked.
     */
    public boolean isUnlocked() {
        return !isLocked();
    }

    /**
     * Returns whether the wallet is locked.
     */
    public boolean isLocked() {
        return password == null;
    }

    /**
     * Returns a copy of the accounts inside this wallet.
     */
    public List<KeyPair> getAccounts() {
        requireUnlocked();
        synchronized (accounts) {
            return Lists.newArrayList(accounts.values());
        }
    }

    /**
     * Sets the accounts inside this wallet.
     */
    public void setAccounts(List<KeyPair> list) {
        requireUnlocked();
        accounts.clear();
        for (KeyPair key : list) {
            addAccount(key);
        }
    }

    /**
     * Returns account by index.
     */
    public KeyPair getAccount(int idx) {
        requireUnlocked();
        synchronized (accounts) {
            return getAccounts().get(idx);
        }
    }

    /**
     * Returns account by address.
     */
    public KeyPair getAccount(byte[] address) {
        requireUnlocked();

        synchronized (accounts) {
            return accounts.get(Bytes.of(address));
        }
    }

    /**
     * Flushes this wallet into the disk.
     */
    public boolean flush() {
        requireUnlocked();

        try {
            SimpleEncoder enc = new SimpleEncoder();
            enc.writeInt(VERSION);

            byte[] salt = SecureRandomProvider.publicSecureRandom().generateSeed(SALT_LENGTH);
            enc.writeBytes(salt);

            byte[] key = BCrypt.generate(password.getBytes(UTF_8), salt, BCRYPT_COST);

            writeAccounts(key, enc);
            writeHdSeed(key, enc);

            if (!file.getParentFile().exists() && !file.getParentFile().mkdirs()) {
                log.error("Failed to create the directory for wallet");
                return false;
            }

            // set posix permissions
            if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix") && !file.exists()) {
                Files.createFile(file.toPath());
                Files.setPosixFilePermissions(file.toPath(), POSIX_SECURED_PERMISSIONS);
            }

            FileUtils.writeByteArrayToFile(file, enc.toBytes());
            return true;
        } catch (IOException e) {
            log.error("Failed to write wallet to disk", e);
        }
        return false;
    }


    private void requireUnlocked() {
        if (!isUnlocked()) {
            throw new RuntimeException("Wallet is Locked!");
        }
    }

    /**
     * Adds a new account to the wallet.
     */
    public boolean addAccount(KeyPair newKey) {
        requireUnlocked();

        synchronized (accounts) {
            Bytes b = Bytes.wrap(Keys.toBytesAddress(newKey));
            if (accounts.containsKey(b)) {
                return false;
            }

            accounts.put(b, newKey);
            return true;
        }
    }

    /**
     * Add an account with randomly generated key.
     */
    public KeyPair addAccountRandom()
            throws InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchProviderException {
        KeyPair key = Keys.createEcKeyPair();
        addAccount(key);
        return key;
    }

    /**
     * Adds a list of accounts to the wallet.
     */
    public int addAccounts(List<KeyPair> accounts) {
        requireUnlocked();

        int n = 0;
        for (KeyPair acc : accounts) {
            n += addAccount(acc) ? 1 : 0;
        }
        return n;
    }

    /**
     * Deletes an account in the wallet.
     */
    public boolean removeAccount(KeyPair key) {
        return removeAccount(Keys.toBytesAddress(key));
    }

    /**
     * Deletes an account in the wallet.
     */
    public boolean removeAccount(byte[] address) {
        requireUnlocked();
        synchronized (accounts) {
            return accounts.remove(Bytes.of(address)) != null;
        }
    }

    /**
     * Changes the password of the wallet.
     */
    public void changePassword(String newPassword) {
        requireUnlocked();

        if (newPassword == null) {
            throw new IllegalArgumentException("Password can not be null");
        }

        this.password = newPassword;
    }

    // ================
    // HD wallet
    // ================

    /**
     * Returns whether the HD seed is initialized.
     *
     * @return true if set, otherwise false
     */
    public boolean isHdWalletInitialized() {
        requireUnlocked();
        return mnemonicPhrase != null && !mnemonicPhrase.isEmpty();
    }

    /**
     * Initialize the HD wallet.
     *
     * @param mnemonicPhrase the mnemonic word list
     */
    public void initializeHdWallet(String mnemonicPhrase) {
        this.mnemonicPhrase = mnemonicPhrase;
        this.nextAccountIndex = 0;
    }

    /**
     * Returns the HD seed.
     */
    public byte[] getSeed() {
        return MnemonicUtils.generateSeed(this.mnemonicPhrase, MNEMONIC_PASS_PHRASE);
    }

    /**
     * Derives a key based on the current HD account index, and put it into the
     * wallet.
     */
    public KeyPair addAccountWithNextHdKey() {
        requireUnlocked();
        requireHdWalletInitialized();

        synchronized (accounts) {
            byte[] seed = getSeed();
            Bip32ECKeyPair masterKeypair = Bip32ECKeyPair.generateKeyPair(seed);
            Bip32ECKeyPair bip44Keypair = WalletUtils.generateBip44KeyPair(masterKeypair, nextAccountIndex++);
            Bytes b = Bytes.wrap(Keys.toBytesAddress(bip44Keypair.getKeyPair()));
            accounts.put(b, bip44Keypair.getKeyPair());
            return bip44Keypair.getKeyPair();
        }
    }

    private void requireHdWalletInitialized() {
        if (!isHdWalletInitialized()) {
            throw new IllegalArgumentException("HD Seed is not initialized");
        }
    }

    /**
     * Creates transaction blocks from a map of our keys and addresses to a destination address
     *
     * @param ourKeys Map of addresses and their corresponding keypairs that we own
     * @param to Destination address
     * @param remark Optional remark to include in transaction
     * @return List of transaction block wrappers
     */
    public List<BlockWrapper> createTransactionBlock(Map<Address, KeyPair> ourKeys, Bytes32 to, String remark, UInt64 txNonce, XAmount txFee) {
        // Check if remark exists
        int hasRemark = remark == null ? 0 : 1;

        List<BlockWrapper> res = Lists.newArrayList();

        // Process ourKeys to calculate max entries per block
        LinkedList<Entry<Address, KeyPair>> stack = new LinkedList<>(ourKeys.entrySet());

        // Keys used for current block
        Map<Address, KeyPair> keys = new HashMap<>();
        // Ensure key uniqueness
        Set<KeyPair> keysPerBlock = new HashSet<>();
        // Add default key
        keysPerBlock.add(getDefKey());

        int base;
        if (txNonce != null) {
            // base count a block <header + transaction nonce + send address + defKey signature>
            base = 1 + 1 + 1 + 2 + hasRemark;
        } else {
            // base count a block <header + send address + defKey signature>
            base = 1 + 1 + 2 + hasRemark;
        }
        XAmount amount = XAmount.ZERO;

        while (!stack.isEmpty()) {
            Map.Entry<Address, KeyPair> key = stack.peek();
            base += 1;
            int originSize = keysPerBlock.size();
            keysPerBlock.add(key.getValue());
            // New unique key added
            if (keysPerBlock.size() > originSize) {
                // Add fields for public key and signatures
                base += 3;
            }
            // Can fit in current block
            if (base < 16) {
                amount = amount.add(key.getKey().getAmount());
                keys.put(key.getKey(), key.getValue());
                stack.poll();
            } else {
                // Create block with current keys
                res.add(createTransaction(to, amount, keys, remark, txNonce, txFee));
                // Reset for next block
                keys = new HashMap<>();
                keysPerBlock = new HashSet<>();
                keysPerBlock.add(getDefKey());
                if (txNonce != null) {
                    base = 1 + 1 + 1 + 2 + hasRemark;
                } else {
                    base = 1 + 1 + 2 + hasRemark;
                }
                amount = XAmount.ZERO;
            }
        }
        if (!keys.isEmpty()) {
            res.add(createTransaction(to, amount, keys, remark, txNonce, txFee));
        }

        return res;
    }

    /**
     * Creates a single transaction block
     *
     * @param to Destination address
     * @param amount Transaction amount
     * @param keys Map of addresses and keypairs to use as inputs
     * @param remark Optional remark
     * @return Transaction block wrapper
     */
    private BlockWrapper createTransaction(Bytes32 to, XAmount amount, Map<Address, KeyPair> keys, String remark, UInt64 txNonce, XAmount txFee) {

        List<Address> tos = Lists.newArrayList(new Address(to, XDAG_FIELD_OUTPUT, amount,true));

        Block block = createNewBlock(new HashMap<>(keys), tos, remark, txNonce, txFee);

        if (block == null) {
            return null;
        }

        KeyPair defaultKey = getDefKey();

        boolean isdefaultKey = false;
        // Sign with all keys
        for (KeyPair ecKey : Set.copyOf(new HashMap<>(keys).values())) {
            if (ecKey.equals(defaultKey)) {
                isdefaultKey = true;
                block.signOut(ecKey);
            } else {
                block.signIn(ecKey);
            }
        }
        // Re-sign with default key if changed
        if (!isdefaultKey) {
            block.signOut(getDefKey());
        }

        return new BlockWrapper(block, getConfig().getNodeSpec().getTTL());
    }

    /**
     * Creates a new transaction block
     *
     * @param pairs Map of input addresses and keypairs
     * @param to List of output addresses
     * @param remark Optional remark
     * @return New transaction block
     */
    private Block createNewBlock(Map<Address, KeyPair> pairs, List<Address> to,
                                 String remark, UInt64 txNonce, XAmount fee) {
        int hasRemark = remark == null ? 0 : 1;

        int defKeyIndex = -1;

        // Validate inputs
        if (pairs == null || pairs.isEmpty()) {
            return null;
        }

        // Validate outputs
        if (to == null || to.isEmpty()) {
            return null;
        }

        // Find default key index
        List<KeyPair> keys = new ArrayList<>(Set.copyOf(pairs.values()));
        for (int i = 0; i < keys.size(); i++) {
            if (keys.get(i).equals(getDefKey())) {
                defKeyIndex = i;
            }
        }

        List<Address> all = Lists.newArrayList();
        all.addAll(pairs.keySet());
        all.addAll(to);

        // Calculate total fields needed
        int res;
        if (txNonce != null) {
            res = 1 + 1 + pairs.size() + to.size() + 3 * keys.size() + (defKeyIndex == -1 ? 2 : 0) + hasRemark;
        } else {
            res = 1 + pairs.size() + to.size() + 3 * keys.size() + (defKeyIndex == -1 ? 2 : 0) + hasRemark;
        }

        // Validate block size
        if (res > 16) {
            return null;
        }

        long sendTime = XdagTime.getCurrentTimestamp();

        return new Block(getConfig(), sendTime, all, null, false, keys, remark,
                defKeyIndex, fee, txNonce);
    }

}
