package io.xdag.wallet;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.Pair;

import io.xdag.config.Config;
import io.xdag.crypto.ECKey;
import io.xdag.crypto.jni.Native;
import io.xdag.utils.FileUtils;

public class WalletImpl implements Wallet {
    /** 保存得密钥文件 */
    public static final String DNET_KEY_FILE = Config.MainNet ? Config.root + "/dnet_key.dat"
            : Config.root + "/dnet_key.dat";
    /** 钱包文件 */
    public static final String WALLET_KEY_FILE = Config.MainNet ? Config.root + "/wallet.dat"
            : Config.root + "/wallet-testnet.dat";
    private List<key_internal_item> key_internal = new ArrayList<>();

    // TODO：File 路径 修改至resources
    private key_internal_item defKey;
    private int keysNum = 0;

    @Override
    public int init(Config config) throws Exception {
        File dnetDatFile = new File(DNET_KEY_FILE);
        Native.dfslib_random_init();
        Native.crc_init();
        boolean fileExist = !dnetDatFile.exists() || dnetDatFile.length() == 0;
        Pair<String, String> pair = getPassword(fileExist);
        if (pair == null) {
            System.out.println("wallet init fail");
            System.exit(1);
        }
        if (fileExist) {
            // 文件不存在 创建
            byte[] dnetKeyBytes = Native.general_dnet_key(pair.getLeft(), pair.getRight());
            config.setDnetKeyBytes(dnetKeyBytes);
            FileOutputStream fileOutputStream = new FileOutputStream(dnetDatFile);
            IOUtils.write(dnetKeyBytes, fileOutputStream);
            fileOutputStream.close();
        } else {
            // 文件存在 进行校验
            byte[] dnetKeyBytes = FileUtils.readDnetDat(dnetDatFile);
            int res = Native.verify_dnet_key(pair.getLeft(), dnetKeyBytes);

            if (res < 0) {
                return res;
            }
            config.setDnetKeyBytes(dnetKeyBytes);
        }
        pasreWalletDat();
        return 0;
    }

    @Override
    public key_internal_item getDefKey() {
        return defKey;
    }

    @Override
    public void createNewKey() {
        addKey(null);
    }

    @Override
    public ECKey getKeyByIndex(int index) {
        return key_internal.get(index).ecKey;
    }

    @Override
    public List<key_internal_item> getKey_internal() {
        return key_internal;
    }

    private void addKey(BigInteger priv) {
        if (priv == null) {
            File walletDatFile = new File(WALLET_KEY_FILE);
            ECKey ecKey = new ECKey();
            byte lastByte = ecKey.getPubKey()[ecKey.getPubKey().length - 1];
            // 奇偶
            boolean pubKeyParity = (lastByte & 1) == 0;
            key_internal_item newKey = new key_internal_item();
            newKey.ecKey = ecKey;
            newKey.pubKeyParity = pubKeyParity;
            defKey = newKey;
            key_internal.add(newKey);
            FileOutputStream fileOutputStream = null;
            try {
                fileOutputStream = new FileOutputStream(walletDatFile, true);
                if (!walletDatFile.exists()) {
                    if (!walletDatFile.createNewFile()) {
                        System.out.println("create new file wallet.dat failed");
                        throw new Exception();
                    }
                }
                // encrypted the priv byte with user's password
                byte[] priv32 = ecKey.getPrivKeyBytes();
                byte[] priv32Encrypted = Native.encrypt_wallet_key(priv32, keysNum++);
                IOUtils.write(priv32Encrypted, fileOutputStream);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (fileOutputStream != null) {
                    try {
                        fileOutputStream.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    private Pair<String, String> getPassword(boolean fileExist) {
        if (fileExist) {
            System.out.println("Pleasr set Your Password :");
        } else {
            System.out.println("Pleasr Input Your Password :");
        }
        Scanner scanner = new Scanner(System.in);
        String password = scanner.nextLine();
        String random = null;
        // 文件存在 仅需要输入一次密码 不存在 则需要重复输入一次
        if (fileExist) {
            System.out.println("Please replace your password :");
            String replacePassword = scanner.nextLine();
            if (!replacePassword.equals(password)) {
                System.out.println("passwords are inconsistent, please check");
                scanner.close();
                return null;
            }
            System.out.println("Please Input random:");
            random = scanner.nextLine();
        }
        scanner.close();
        return Pair.of(password, random);
    }

    private void pasreWalletDat() throws Exception {
        File walletDatFile = new File(WALLET_KEY_FILE);
        if (!walletDatFile.exists() || walletDatFile.length() == 0) {
            // if wallet.dat not exist create it
            ECKey ecKey = new ECKey();
            byte lastByte = ecKey.getPubKey()[ecKey.getPubKey().length - 1];
            // 奇偶
            boolean pubKeyParity = (lastByte & 1) == 0;
            key_internal_item newKey = new key_internal_item();
            newKey.ecKey = ecKey;
            newKey.pubKeyParity = pubKeyParity;
            defKey = newKey;
            key_internal.add(newKey);
            if (!walletDatFile.exists()) {
                if (!walletDatFile.createNewFile()) {
                    System.out.println("create new file wallet.dat failed");
                    throw new Exception();
                }
            }
            // encrypted the priv byte with user's password
            FileOutputStream fileOutputStream = new FileOutputStream(walletDatFile);
            byte[] priv32 = ecKey.getPrivKeyBytes();
            byte[] priv32Encrypted = Native.encrypt_wallet_key(priv32, keysNum++);
            IOUtils.write(priv32Encrypted, fileOutputStream);
            fileOutputStream.close();
        } else {
            // read wallet.dat
            FileInputStream fileInputStream = new FileInputStream(walletDatFile);
            byte[] priv32Encrypted = new byte[32];
            while (fileInputStream.read(priv32Encrypted) != -1) {
                byte[] priv32 = Native.uncrypt_wallet_key(priv32Encrypted, keysNum++);
                ECKey ecKey = ECKey.fromPrivate(priv32);
                byte lastByte = ecKey.getPubKey()[ecKey.getPubKey().length - 1];
                // 奇偶
                boolean pubKeyParity = (lastByte & 1) == 0;
                key_internal_item newKey = new key_internal_item();
                newKey.ecKey = ecKey;
                newKey.pubKeyParity = pubKeyParity;
                key_internal.add(newKey);
            }
            // 最后一个
            defKey = key_internal.get(key_internal.size() - 1);
            fileInputStream.close();
        }
    }
}
