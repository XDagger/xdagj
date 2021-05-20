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

import io.xdag.Kernel;
import io.xdag.Launcher;
import io.xdag.config.Config;
import io.xdag.config.Constants;
import io.xdag.config.TestnetConfig;
import io.xdag.crypto.ECKeyPair;
import io.xdag.crypto.Keys;
import io.xdag.utils.BytesUtils;
import io.xdag.wallet.Wallet;
import io.xdag.wallet.WalletUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.ArrayUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static io.xdag.wallet.WalletUtils.*;

@Slf4j
public class XdagCli extends Launcher {

    public static void main(String[] args, XdagCli cli) throws Exception {
        try {
            cli.start(args);
        } catch (IOException exception) {
            log.error(exception.getMessage());
        }
    }

    public static void main(String[] args) throws Exception {
        main(args, new XdagCli());
    }

    /**
     * Creates a new Xdag CLI instance.
     */
    public XdagCli() {
        Option helpOption = Option.builder()
                .longOpt(XdagOption.HELP.toString())
                .desc("PrintHelp")
                .build();
        addOption(helpOption);

        Option versionOption = Option.builder()
                .longOpt(XdagOption.VERSION.toString())
                .desc("ShowVersion")
                .build();
        addOption(versionOption);

        Option accountOption = Option.builder()
                .longOpt(XdagOption.ACCOUNT.toString())
                .desc("ChooseAction")
                .hasArg(true).numberOfArgs(1).optionalArg(false).argName("action").type(String.class)
                .build();
        addOption(accountOption);

        Option changePasswordOption = Option.builder()
                .longOpt(XdagOption.CHANGE_PASSWORD.toString()).desc("ChangeWalletPassword").build();
        addOption(changePasswordOption);

        Option dumpPrivateKeyOption = Option.builder()
                .longOpt(XdagOption.DUMP_PRIVATE_KEY.toString())
                .desc("PrintHexKey")
                .hasArg(true).optionalArg(false).argName("address").type(String.class)
                .build();
        addOption(dumpPrivateKeyOption);

        Option importPrivateKeyOption = Option.builder()
                .longOpt(XdagOption.IMPORT_PRIVATE_KEY.toString())
                .desc("ImportHexKey")
                .hasArg(true).optionalArg(false).argName("key").type(String.class)
                .build();
        addOption(importPrivateKeyOption);

    }

    public void start(String[] args) throws Exception {
        Config config = getConfig(args);
        // move old args
        List<String> argsList = new ArrayList<>();
        for (String arg : args) {
            switch (arg) {
                case "-t" -> config = new TestnetConfig();
                default -> argsList.add(arg);
            }
        }
        String[] newArgs = argsList.toArray(new String[0]);
        // parse common options
        CommandLine cmd = null;
        try {
            cmd = parseOptions(newArgs);
        } catch (ParseException exception) {
            log.error("ParsingFailed:" + exception.getMessage());
        }

        if(cmd == null) {
            start(config);
        } else if (cmd.hasOption(XdagOption.HELP.toString())) {
            printHelp();
        } else if (cmd.hasOption(XdagOption.VERSION.toString())) {
            printVersion();
        } else if (cmd.hasOption(XdagOption.ACCOUNT.toString())) {
            String action = cmd.getOptionValue(XdagOption.ACCOUNT.toString()).trim();
            if ("create".equals(action)) {
                createAccount(config);
            } else if ("list".equals(action)) {
                listAccounts(config);
            }
        } else if (cmd.hasOption(XdagOption.CHANGE_PASSWORD.toString())) {
            changePassword(config);
        } else if (cmd.hasOption(XdagOption.DUMP_PRIVATE_KEY.toString())) {
            dumpPrivateKey(config, cmd.getOptionValue(XdagOption.DUMP_PRIVATE_KEY.toString()).trim());

        } else if (cmd.hasOption(XdagOption.IMPORT_PRIVATE_KEY.toString())) {
            importPrivateKey(config, cmd.getOptionValue(XdagOption.IMPORT_PRIVATE_KEY.toString()).trim());
        }
    }

    protected void printHelp() {
        HelpFormatter formatter = new HelpFormatter();
        formatter.setWidth(200);
        formatter.printHelp("./xdag-cli.sh [options]", getOptions());
        formatter.printHelp("./xdag-cli.sh [options]", getOptions());
    }

    protected void printVersion() {
        System.out.println(Constants.CLIENT_VERSION);
    }

    protected void start(Config config) throws IOException {
        // create/unlock wallet
        Wallet wallet = loadWallet(config).exists() ? WalletUtils.loadAndUnlockWallet(config) : createNewWallet(config);
        if (wallet == null) {
            return;
        }

        if (!wallet.isHdWalletInitialized()) {
            WalletUtils.initializedHdSeed(wallet);
        }

        // create a new account if the wallet is empty
        List<ECKeyPair> accounts = wallet.getAccounts();
        if (accounts.isEmpty()) {
            ECKeyPair key = wallet.addAccountWithNextHdKey();
            wallet.flush();
            log.info("NewAccountCreatedForAddress:" + BytesUtils.toHexString(Keys.toAddress(key)));
        }

        // start kernel
        try {
            startKernel(config, wallet);
        } catch (Exception e) {
            log.error("Uncaught exception during kernel startup.", e);
            exit(-1);
        }
    }

    /**
     * Starts the kernel.
     */
    protected void startKernel(Config config, Wallet wallet) throws Exception {
        Kernel kernel = new Kernel(config, wallet);
        kernel.testStart();
    }

    protected void createAccount(Config config) {
        Wallet wallet = loadAndUnlockWallet(config);
        ECKeyPair key = wallet.addAccountWithNextHdKey();
        if (wallet.flush()) {
            log.info("NewAccountCreatedForAddress:" + BytesUtils.toHexString(Keys.toAddress(key)));
            log.info("PublicKey:" + BytesUtils.toHexString(key.getPublicKey().toByteArray()));
        }
    }

    protected void listAccounts(Config config) {
        Wallet wallet = loadAndUnlockWallet(config);
        List<ECKeyPair> accounts = wallet.getAccounts();

        if (accounts.isEmpty()) {
            log.info("AccountMissing");
        } else {
            for (int i = 0; i < accounts.size(); i++) {
                log.info("ListAccountItem:" + i + " " + BytesUtils.toHexString(Keys.toAddress(accounts.get(i))));
            }
        }
    }

    protected void changePassword(Config config) {
        Wallet wallet = loadAndUnlockWallet(config);

            String newPassword = readNewPassword();
            if (newPassword == null) {
                return;
            }

            wallet.changePassword(newPassword);
            boolean isFlushed = wallet.flush();
            if (!isFlushed) {
                log.error("WalletFileCannotBeUpdated");
                exit(-1);
                return;
            }

            log.info("PasswordChangedSuccessfully");

    }

    protected void exit(int code) {
        System.exit(code);
    }

    /**
     * Read a new password from input and require confirmation
     *
     * @return new password, or null if the confirmation failed
     */
    protected String readNewPassword() {
        String newPassword = readPassword("EnterNewPassword:");
        String newPasswordRe = readPassword("ReEnterNewPassword:");

        if (!newPassword.equals(newPasswordRe)) {
            log.error("ReEnterNewPasswordIncorrect");
            System.exit(-1);
            return null;
        }

        return newPassword;
    }

    protected void dumpPrivateKey(Config config, String address) {
        Wallet wallet = loadAndUnlockWallet(config);
        byte[] addressBytes = BytesUtils.hexStringToBytes(address);
        ECKeyPair account = wallet.getAccount(addressBytes);
        if (account == null) {
            log.error("AddressNotInWallet");
            System.exit(-1);
        } else {
            System.out.println("PrivateKeyIs:" + BytesUtils.toHexString(account.getPrivateKey().toByteArray()));
        }
    }

    protected void importPrivateKey(Config config, String key) {
        Wallet wallet = loadAndUnlockWallet(config);
        byte[] keyBytes = BytesUtils.hexStringToBytes(key);
        ECKeyPair account = ECKeyPair.create(keyBytes);

        boolean accountAdded = wallet.addAccount(account);
        if (!accountAdded) {
            log.error("PrivateKeyAlreadyInWallet");
            System.exit(-1);
            return;
        }

        boolean walletFlushed = wallet.flush();
        if (!walletFlushed) {
            log.error("WalletFileCannotBeUpdated");
            System.exit(-1);
            return;
        }

        log.info("PrivateKeyImportedSuccessfully");
        log.info("Address:" + BytesUtils.toHexString(Keys.toAddress(account)));
        log.info("PublicKey:" + BytesUtils.toHexString(account.getPublicKey().toByteArray()));
    }
}