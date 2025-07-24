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
import static io.xdag.crypto.keys.AddressUtils.toBytesAddress;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.attribute.PosixFilePermission.OWNER_READ;
import static java.nio.file.attribute.PosixFilePermission.OWNER_WRITE;

import com.google.common.collect.Lists;
import io.xdag.config.Config;
import io.xdag.core.*;
import io.xdag.crypto.bip.Bip32Key;
import io.xdag.crypto.bip.Bip39Mnemonic;
import io.xdag.crypto.bip.Bip44Wallet;
import io.xdag.crypto.core.CryptoProvider;
import io.xdag.crypto.encryption.Aes;
import io.xdag.crypto.exception.CryptoException;
import io.xdag.crypto.keys.ECKeyPair;
import io.xdag.crypto.keys.PrivateKey;
import io.xdag.utils.SimpleEncoder;
import io.xdag.utils.Numeric;
import io.xdag.utils.SimpleDecoder;
import io.xdag.utils.XdagTime;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
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

    private final Map<Bytes, ECKeyPair> accounts = Collections.synchronizedMap(new LinkedHashMap<>());
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

    public ECKeyPair getDefKey() {
        List<ECKeyPair> accountList = getAccounts();
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

                Set<ECKeyPair> newAccounts;
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
                    for (ECKeyPair account : newAccounts) {
                        Bytes b = Bytes.wrap(toBytesAddress(account));
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
    protected LinkedHashSet<ECKeyPair> readAccounts(byte[] key, SimpleDecoder dec, boolean vlq, int version)
        throws CryptoException {
        LinkedHashSet<ECKeyPair> keys = new LinkedHashSet<>();
        int total = dec.readInt(); // size

        for (int i = 0; i < total; i++) {
            byte[] iv = dec.readBytes(vlq);
            byte[] privateKey = Aes.decrypt(Bytes.wrap(dec.readBytes(vlq)), Bytes.wrap(key), Bytes.wrap(iv)).toArray();
            ECKeyPair keyPair = ECKeyPair.fromPrivateKey(PrivateKey.fromBigInteger(Numeric.toBigInt(privateKey)));
            keys.add(keyPair);
        }
        return keys;
    }

    /**
     * Writes the account keys.
     */
    protected void writeAccounts(byte[] key, SimpleEncoder enc) throws CryptoException {
        synchronized (accounts) {
            enc.writeInt(accounts.size());
            for (ECKeyPair keyPair : accounts.values()) {
                byte[] iv = CryptoProvider.nextBytes(16);

                enc.writeBytes(iv);
                enc.writeBytes(Aes.encrypt(Bytes.wrap(keyPair.getPrivateKey().toBytes().toArray()), Bytes.wrap(key), Bytes.wrap(iv)).toArray());
            }
        }
    }

    /**
     * Reads the mnemonic phase and next account index.
     */
    protected void readHdSeed(byte[] key, SimpleDecoder dec) throws CryptoException {
        byte[] iv = dec.readBytes();
        byte[] hdSeedEncrypted = dec.readBytes();
        byte[] hdSeedRaw = Aes.decrypt(Bytes.wrap(hdSeedEncrypted), Bytes.wrap(key), Bytes.wrap(iv)).toArray();

        SimpleDecoder d = new SimpleDecoder(hdSeedRaw);
        mnemonicPhrase = d.readString();
        nextAccountIndex = d.readInt();
    }

    /**
     * Writes the mnemonic phase and next account index.
     */
    protected void writeHdSeed(byte[] key, SimpleEncoder enc) throws CryptoException {
        SimpleEncoder e = new SimpleEncoder();
        e.writeString(mnemonicPhrase);
        e.writeInt(nextAccountIndex);

        byte[] iv = CryptoProvider.nextBytes(16);
        byte[] hdSeedRaw = e.toBytes();
        byte[] hdSeedEncrypted = Aes.encrypt(Bytes.wrap(hdSeedRaw), Bytes.wrap(key), Bytes.wrap(iv)).toArray();

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
    public List<ECKeyPair> getAccounts() {
        requireUnlocked();
        synchronized (accounts) {
            return Lists.newArrayList(accounts.values());
        }
    }

    /**
     * Sets the accounts inside this wallet.
     */
    public void setAccounts(List<ECKeyPair> list) {
        requireUnlocked();
        accounts.clear();
        for (ECKeyPair key : list) {
            addAccount(key);
        }
    }

    /**
     * Returns account by index.
     */
    public ECKeyPair getAccount(int idx) {
        requireUnlocked();
        synchronized (accounts) {
            return getAccounts().get(idx);
        }
    }

    /**
     * Returns account by address.
     */
    public ECKeyPair getAccount(byte[] address) {
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

            byte[] salt = CryptoProvider.nextBytes(SALT_LENGTH);
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
        } catch (CryptoException e) {
          throw new RuntimeException(e);
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
    public boolean addAccount(ECKeyPair newKey) {
        requireUnlocked();

        synchronized (accounts) {
            Bytes b = toBytesAddress(newKey);
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
    public ECKeyPair addAccountRandom() {
        ECKeyPair key = ECKeyPair.generate();
        addAccount(key);
        return key;
    }

    /**
     * Adds a list of accounts to the wallet.
     */
    public int addAccounts(List<ECKeyPair> accounts) {
        requireUnlocked();

        int n = 0;
        for (ECKeyPair acc : accounts) {
            n += addAccount(acc) ? 1 : 0;
        }
        return n;
    }

    /**
     * Deletes an account in the wallet.
     */
    public boolean removeAccount(ECKeyPair key) {
        return removeAccount(toBytesAddress(key).toArrayUnsafe());
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
        try {
            return Bip39Mnemonic.toSeed(this.mnemonicPhrase, MNEMONIC_PASS_PHRASE).toArrayUnsafe();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate seed from mnemonic", e);
        }
    }

    /**
     * Derives a key based on the current HD account index, and put it into the
     * wallet.
     */
    public ECKeyPair addAccountWithNextHdKey() {
        requireUnlocked();
        requireHdWalletInitialized();

        synchronized (accounts) {
            byte[] seed = getSeed();
            Bip32Key masterKeyPair = Bip44Wallet.createMasterKey(seed);
//            Bip32Key masterKeypair = Bip32Key.generateKeyPair(seed);
            Bip32Key bip44Keypair = Bip44Wallet.deriveXdagKey(masterKeyPair,0, nextAccountIndex++);
//            Bip32Key bip44Keypair = WalletUtils.generateBip44KeyPair(masterKeyPair, nextAccountIndex++);
            Bytes b = Bytes.wrap(toBytesAddress(bip44Keypair.keyPair()));
            accounts.put(b, bip44Keypair.keyPair());
            return bip44Keypair.keyPair();
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
    public List<BlockWrapper> createTransactionBlock(Map<Address, ECKeyPair> ourKeys, Bytes32 to, String remark, UInt64 txNonce) {
        // Check if remark exists
        int hasRemark = remark == null ? 0 : 1;

        List<BlockWrapper> res = Lists.newArrayList();

        // Process ourKeys to calculate max entries per block
        LinkedList<Entry<Address, ECKeyPair>> stack = new LinkedList<>(ourKeys.entrySet());

        // Keys used for current block
        Map<Address, ECKeyPair> keys = new HashMap<>();
        // Ensure key uniqueness
        Set<ECKeyPair> keysPerBlock = new HashSet<>();
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
            Map.Entry<Address, ECKeyPair> key = stack.peek();
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
                res.add(createTransaction(to, amount, keys, remark, txNonce));
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
            res.add(createTransaction(to, amount, keys, remark, txNonce));
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
    private BlockWrapper createTransaction(Bytes32 to, XAmount amount, Map<Address, ECKeyPair> keys, String remark, UInt64 txNonce) {

        List<Address> tos = Lists.newArrayList(new Address(to, XDAG_FIELD_OUTPUT, amount,true));

        Block block = createNewBlock(new HashMap<>(keys), tos, remark, txNonce);

        if (block == null) {
            return null;
        }

        ECKeyPair defaultKey = getDefKey();

        boolean isdefaultKey = false;
        // Sign with all keys
        for (ECKeyPair ecKey : Set.copyOf(new HashMap<>(keys).values())) {
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
    private Block createNewBlock(Map<Address, ECKeyPair> pairs, List<Address> to,
            String remark, UInt64 txNonce) {
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
        List<ECKeyPair> keys = new ArrayList<>(Set.copyOf(pairs.values()));
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
                defKeyIndex, XAmount.of(100, XUnit.MILLI_XDAG), txNonce);
    }

}
