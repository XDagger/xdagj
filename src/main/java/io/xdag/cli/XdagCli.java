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

package io.xdag.cli;

import static io.xdag.wallet.WalletUtils.WALLET_PASSWORD_PROMPT;

import io.xdag.Kernel;
import io.xdag.Launcher;
import io.xdag.config.Config;
import io.xdag.config.Constants;
import io.xdag.config.MainnetConfig;
import io.xdag.crypto.Keys;
import io.xdag.crypto.MnemonicUtils;
import io.xdag.crypto.SecureRandomUtils;
import io.xdag.crypto.Sign;
import io.xdag.db.DatabaseFactory;
import io.xdag.db.DatabaseName;
import io.xdag.db.SnapshotChainStore;
import io.xdag.db.SnapshotChainStoreImpl;
import io.xdag.db.SnapshotJ;
import io.xdag.db.rocksdb.RocksdbFactory;
import io.xdag.db.rocksdb.RocksdbKVSource;
import io.xdag.utils.BytesUtils;
import io.xdag.utils.XdagTime;
import io.xdag.wallet.Wallet;
import java.io.Console;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Scanner;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.StringUtils;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.crypto.SECPPrivateKey;

public class XdagCli extends Launcher {

    private static final Scanner scanner = new Scanner(new InputStreamReader(System.in, StandardCharsets.UTF_8));

    /**
     * Creates a new Xdag CLI instance.
     */
    public XdagCli() {
        Option helpOption = Option.builder()
                .longOpt(XdagOption.HELP.toString())
                .desc("print help")
                .build();
        addOption(helpOption);

        Option versionOption = Option.builder()
                .longOpt(XdagOption.VERSION.toString())
                .desc("show version")
                .build();
        addOption(versionOption);

        Option accountOption = Option.builder()
                .longOpt(XdagOption.ACCOUNT.toString())
                .desc("init|create|list")
                .hasArg(true).numberOfArgs(1).optionalArg(false).argName("action").type(String.class)
                .build();
        addOption(accountOption);

        Option changePasswordOption = Option.builder()
                .longOpt(XdagOption.CHANGE_PASSWORD.toString()).desc("change wallet password").build();
        addOption(changePasswordOption);

        Option dumpPrivateKeyOption = Option.builder()
                .longOpt(XdagOption.DUMP_PRIVATE_KEY.toString())
                .desc("print hex key")
                .hasArg(true).optionalArg(false).argName("address").type(String.class)
                .build();
        addOption(dumpPrivateKeyOption);

        Option importPrivateKeyOption = Option.builder()
                .longOpt(XdagOption.IMPORT_PRIVATE_KEY.toString())
                .desc("import hex key")
                .hasArg(true).optionalArg(false).argName("key").type(String.class)
                .build();
        addOption(importPrivateKeyOption);

        Option importMnemonicOption = Option.builder()
                .longOpt(XdagOption.IMPORT_MNEMONIC.toString())
                .desc("import HDWallet mnemonic")
                .hasArg(true).optionalArg(false).argName("mnemonic").type(String.class)
                .build();
        addOption(importMnemonicOption);

        Option convertOldWalletOption = Option.builder()
                .longOpt(XdagOption.CONVERT_OLD_WALLET.toString())
                .desc("convert xdag old wallet.dat to private key hex")
                .hasArg(true).optionalArg(false).argName("filename").type(String.class)
                .build();
        addOption(convertOldWalletOption);

        Option loadSnapshotOption = Option.builder()
                .longOpt(XdagOption.LOAD_SNAPSHOT.toString()).desc("load snapshot")
                .hasArg(true).optionalArg(false).argName("filename").type(String.class)
                .build();
        addOption(loadSnapshotOption);

        Option bootSnapshotOption = Option.builder()
                .longOpt(XdagOption.ENABLE_SNAPSHOT.toString()).desc("enable snapshot")
                .hasArg(true).numberOfArgs(3).optionalArg(false)
                .argName("isSnapshotJ").type(Boolean.class)
                .argName("snapshotheight").type(Integer.class)
                .argName("snapshottime").type(Integer.class)
                .desc("the parameter snapshottime uses hexadecimal")
                .build();
        addOption(bootSnapshotOption);

        Option makeSnapshotOption = Option.builder()
                .longOpt(XdagOption.MAKE_SNAPSHOT.toString()).desc("make snapshot")
                .hasArg(true).optionalArg(true).argName("covertuint").type(String.class)
                .build();
        addOption(makeSnapshotOption);
    }

    public static void main(String[] args, XdagCli cli) throws Exception {
        try {
            cli.start(args);
        } catch (IOException exception) {
            System.err.println(exception.getMessage());
        }
    }

    public static void main(String[] args) throws Exception {
        main(args, new XdagCli());
    }

    public void start(String[] args) throws Exception {
        Config config = buildConfig(args);
        setConfig(config);
        // move old args
        List<String> argsList = new ArrayList<>();
        for (String arg : args) {
            if (StringUtils.equalsAny(arg, "-d", "-t")) {
                // only devnet or testnet
            } else {
                argsList.add(arg);
            }
        }
        String[] newArgs = argsList.toArray(new String[0]);
        // parse common options
        CommandLine cmd = null;
        try {
            cmd = parseOptions(newArgs);
        } catch (ParseException exception) {
            System.err.println("Parsing Failed:" + exception.getMessage());
        }

        assert cmd != null;
        if (cmd.hasOption(XdagOption.HELP.toString())) {
            printHelp();
        } else if (cmd.hasOption(XdagOption.VERSION.toString())) {
            printVersion();
        } else if (cmd.hasOption(XdagOption.ACCOUNT.toString())) {
            String action = cmd.getOptionValue(XdagOption.ACCOUNT.toString()).trim();
            switch (action) {
                case "init" -> initHDAccount();
                case "create" -> createAccount();
                case "list" -> listAccounts();
                default -> System.out.println("No Action!");
            }
        } else if (cmd.hasOption(XdagOption.CHANGE_PASSWORD.toString())) {
            changePassword();
        } else if (cmd.hasOption(XdagOption.DUMP_PRIVATE_KEY.toString())) {
            dumpPrivateKey(cmd.getOptionValue(XdagOption.DUMP_PRIVATE_KEY.toString()).trim());
        } else if (cmd.hasOption(XdagOption.IMPORT_PRIVATE_KEY.toString())) {
            importPrivateKey(cmd.getOptionValue(XdagOption.IMPORT_PRIVATE_KEY.toString()).trim());
        } else if (cmd.hasOption(XdagOption.IMPORT_MNEMONIC.toString())) {
            importMnemonic(cmd.getOptionValue(XdagOption.IMPORT_MNEMONIC.toString()).trim());
        } else if (cmd.hasOption(XdagOption.LOAD_SNAPSHOT.toString())) {
            File file = new File(cmd.getOptionValue(XdagOption.LOAD_SNAPSHOT.toString()).trim());
            loadSnapshot(file);
        } else if (cmd.hasOption(XdagOption.MAKE_SNAPSHOT.toString())) {
            boolean convertUInt = false;
            String action = cmd.getOptionValue(XdagOption.MAKE_SNAPSHOT.toString());
            if (action != null && action.trim().equals("convertuint")) {
                convertUInt = true;
            }
            makeSnapshot(convertUInt);
        } else {
            if (cmd.hasOption(XdagOption.ENABLE_SNAPSHOT.toString())) {
                String[] values = cmd.getOptionValues(XdagOption.ENABLE_SNAPSHOT.toString().trim());
                try {
                    boolean isSnapshotJ = Boolean.parseBoolean(values[0]);
                    long height = Long.parseLong(values[1]);
                    long time = Long.parseLong(values[2], 16);
                    config.getSnapshotSpec().setSnapshotJ(isSnapshotJ);
                    config.getSnapshotSpec().setSnapshotHeight(height);
                    config.getSnapshotSpec().setSnapshotTime(time);
                    config.getSnapshotSpec().snapshotEnable();
                    System.out.println("enable snapshot:" + config.getSnapshotSpec().isSnapshotEnabled());
                } catch (NumberFormatException e) {
                    System.out.println("params error");
                }
            }
            start();
        }
    }

    protected void printHelp() {
        HelpFormatter formatter = new HelpFormatter();
        formatter.setWidth(200);
        formatter.printHelp("./xdag.sh [options]", getOptions());
    }

    protected void printVersion() {
        System.out.println(Constants.CLIENT_VERSION);
    }

    protected void start() throws IOException {
        // create/unlock wallet
        Wallet wallet = loadWallet().exists() ? loadAndUnlockWallet() : createNewWallet();
        if (wallet == null) {
            return;
        }

        if (!wallet.isHdWalletInitialized()) {
            initializedHdSeed(wallet, System.out);
        }

        // create a new account if the wallet is empty
        List<KeyPair> accounts = wallet.getAccounts();
        if (accounts.isEmpty()) {
            KeyPair key = wallet.addAccountWithNextHdKey();
            wallet.flush();
            System.out.println("New Address:" + BytesUtils.toHexString(Keys.toBytesAddress(key)));
        }

        // start kernel
        try {
            startKernel(getConfig(), wallet);
        } catch (Exception e) {
            System.err.println("Uncaught exception during kernel startup:" + e.getMessage());
            exit(-1);
        }
    }

    /**
     * Starts the kernel.
     */
    protected Kernel startKernel(Config config, Wallet wallet) throws Exception {
        Kernel kernel = new Kernel(config, wallet);
        kernel.testStart();
        return kernel;
    }

    protected void initHDAccount() {
        // create/unlock wallet
        Wallet wallet;
        if (loadWallet().exists()) {
            wallet = loadAndUnlockWallet();
        } else {
            wallet = createNewWallet();
        }

        if (wallet == null) {
            return;
        }
        if (!wallet.isHdWalletInitialized()) {
            initializedHdSeed(wallet, System.out);
        } else {
            System.out.println("HD Wallet Account already init.");
        }
    }

    protected void createAccount() {
        Wallet wallet = loadAndUnlockWallet();
        if (Objects.nonNull(wallet) && !wallet.isHdWalletInitialized()) {
            System.out.println("Please init HD Wallet account first!");
            return;
        }
        KeyPair key = wallet.addAccountWithNextHdKey();
        if (wallet.flush()) {
            System.out.println("New Address:" + BytesUtils.toHexString(Keys.toBytesAddress(key)));
            System.out.println("PublicKey:" + key.getPublicKey().getEncodedBytes().toHexString());
        }
    }

    protected void listAccounts() {
        Wallet wallet = loadAndUnlockWallet();
        List<KeyPair> accounts = wallet.getAccounts();

        if (accounts.isEmpty()) {
            System.out.println("Account Missing");
        } else {
            for (int i = 0; i < accounts.size(); i++) {
                System.out.println("Address:" + i + " " + BytesUtils.toHexString(Keys.toBytesAddress(accounts.get(i))));
            }
        }
    }

    protected void changePassword() {
        Wallet wallet = loadAndUnlockWallet();
        if (wallet.isUnlocked()) {
            String newPassword = readNewPassword("EnterNewPassword:", "ReEnterNewPassword:");
            if (newPassword == null) {
                return;
            }
            wallet.changePassword(newPassword);
            boolean isFlushed = wallet.flush();
            if (!isFlushed) {
                System.out.println("Wallet File Cannot Be Updated");
                return;
            }
            System.out.println("Password Changed Successfully!");
        }
    }

    protected void exit(int code) {
        System.exit(code);
    }

    protected void dumpPrivateKey(String address) {
        Wallet wallet = loadAndUnlockWallet();
        byte[] addressBytes = BytesUtils.hexStringToBytes(address);
        KeyPair account = wallet.getAccount(addressBytes);
        if (account == null) {
            System.out.println("Address Not In Wallet");
        } else {
            System.out.println("Private:" + BytesUtils.toHexString(account.getPrivateKey().getEncoded()));
        }
        System.out.println("Private Dump Successfully!");
    }

    protected boolean importPrivateKey(String key) {
        Wallet wallet = loadWallet().exists() ? loadAndUnlockWallet() : createNewWallet();
        KeyPair account = KeyPair.create(SECPPrivateKey.create(Bytes32.fromHexString(key), Sign.CURVE_NAME), Sign.CURVE, Sign.CURVE_NAME);

        boolean accountAdded = wallet.addAccount(account);
        if (!accountAdded) {
            System.out.println("Private Key Already In Wallet");
            return false;
        }

        boolean walletFlushed = wallet.flush();
        if (!walletFlushed) {
            System.out.println("Wallet File Cannot Be Updated");
            return false;
        }

        System.out.println("Address:" + BytesUtils.toHexString(Keys.toBytesAddress(account)));
        System.out.println("PublicKey:" + BytesUtils.toHexString(account.getPublicKey().getEncoded()));
        System.out.println("Private Key Imported Successfully!");
        return true;
    }

    protected boolean importMnemonic(String mnemonic) {
        Wallet wallet = loadWallet().exists() ? loadAndUnlockWallet() : createNewWallet();

        if (wallet.isHdWalletInitialized()) {
            System.out.println("HDWallet Mnemonic Already In Wallet");
            return false;
        }

        if (!MnemonicUtils.validateMnemonic(mnemonic)) {
            System.out.println("Wrong Mnemonic");
            return false;
        }

        wallet.initializeHdWallet(mnemonic);
        if (!wallet.flush()) {
            System.out.println("HDWallet File Cannot Be Updated");
            return false;
        }

        // default add one hd key
        createAccount();

        System.out.println("HDWallet Mnemonic Imported Successfully!");
        return true;
    }

    // snapshot load
    protected void loadSnapshot(File file) {
        Config config = getConfig();
        DatabaseFactory dbFactory = new RocksdbFactory(config);
        SnapshotChainStore snapshotChainStore = new SnapshotChainStoreImpl(dbFactory.getDB(DatabaseName.SNAPSHOT));
        snapshotChainStore.reset();
        boolean mainLag = config instanceof MainnetConfig;
        Wallet wallet = loadAndUnlockWallet();
        long start = System.currentTimeMillis();
        boolean res = snapshotChainStore.loadFromSnapshotData(file.getAbsolutePath(), mainLag, wallet.getAccounts());
        long end = System.currentTimeMillis();
        System.out.println("load res: " + res);
        System.out.println("time: " + (end - start) / 1000 + "s");
    }

    public Wallet loadWallet() {
        return new Wallet(getConfig());
    }

    public Wallet loadAndUnlockWallet() {
        Wallet wallet = loadWallet();
        if (getPassword() == null) {
            if (wallet.unlock("")) {
                setPassword("");
            } else {
                setPassword(readPassword(WALLET_PASSWORD_PROMPT));
            }
        }

        if (!wallet.unlock(getPassword())) {
            System.err.println("Invalid password");
        }

        return wallet;
    }

    /**
     * Create a new wallet with a new password
     */
    public Wallet createNewWallet() {
        System.out.println("Create New Wallet...");
        String newPassword = readNewPassword("EnterNewPassword:", "ReEnterNewPassword:");
        if (newPassword == null) {
            return null;
        }

        setPassword(newPassword);
        Wallet wallet = loadWallet();

        if (!wallet.unlock(newPassword) || !wallet.flush()) {
            System.err.println("Create New WalletError");
            System.exit(-1);
            return null;
        }

        return wallet;
    }

    /**
     * Read a new password from input and require confirmation
     */
    public String readNewPassword(String newPasswordMessageKey, String reEnterNewPasswordMessageKey) {
        String newPassword = readPassword(newPasswordMessageKey);
        String newPasswordRe = readPassword(reEnterNewPasswordMessageKey);

        if (!newPassword.equals(newPasswordRe)) {
            System.err.println("ReEnter NewPassword Incorrect");
            System.exit(-1);
            return null;
        }

        return newPassword;
    }

    /**
     * Reads a line from the console.
     */
    public String readLine(String prompt) {
        if (prompt != null) {
            System.out.print(prompt);
            System.out.flush();
        }

        return scanner.nextLine();
    }

    public boolean initializedHdSeed(Wallet wallet, PrintStream printer) {
        if (wallet.isUnlocked() && !wallet.isHdWalletInitialized()) {
            // HD Mnemonic
            printer.println("HdWallet Initializing...");
            byte[] initialEntropy = new byte[16];
            SecureRandomUtils.secureRandom().nextBytes(initialEntropy);
            String phrase = MnemonicUtils.generateMnemonic(initialEntropy);
            printer.println("HdWallet Mnemonic:" + phrase);

            String repeat = readLine("HdWallet Mnemonic Repeat:");
            repeat = String.join(" ", repeat.trim().split("\\s+"));

            if (!repeat.equals(phrase)) {
                printer.println("HdWallet Initialized Failure");
                return false;
            }

            wallet.initializeHdWallet(phrase);
            wallet.flush();
            printer.println("HdWallet Initialized Successfully!");
            return true;
        }
        return false;
    }

    public String readPassword(String prompt) {
        Console console = System.console();
        if (console == null) {
            if (prompt != null) {
                System.out.print(prompt);
                System.out.flush();
            }
            return scanner.nextLine();
        }
        return new String(console.readPassword(prompt));
    }

    public void makeSnapshot(boolean b) {
        System.out.println("make snapshot start");
        System.out.println("convertuint = " + b);
        long start = System.currentTimeMillis();
        this.getConfig().getSnapshotSpec().setSnapshotJ(true);
        RocksdbKVSource blockSource = new RocksdbKVSource(DatabaseName.TIME.toString());
        blockSource.setConfig(getConfig());
        blockSource.init();
        RocksdbKVSource snapshotSource = new RocksdbKVSource("SNAPSHOTJ");
        snapshotSource.setConfig(getConfig());
        snapshotSource.init();
        SnapshotJ index = new SnapshotJ(DatabaseName.INDEX.toString());
        index.setConfig(getConfig());
        index.init();
        index.makeSnapshot(blockSource, snapshotSource, b);

        long end = System.currentTimeMillis();
        System.out.println("make snapshot done");
        System.out.println("time：" + (end - start) + "ms");
        System.out.println("snapshot height: " + index.getHeight());
        System.out.println("next start frame: " + Long.toHexString(XdagTime.getEndOfEpoch(index.getNextTime()) + 1));
    }
}