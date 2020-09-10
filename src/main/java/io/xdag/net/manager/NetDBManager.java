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
package io.xdag.net.manager;

import static io.xdag.config.Config.MAINNET;
import static io.xdag.config.Config.WHITELIST_URL;
import static io.xdag.config.Config.WHITELIST_URL_TESTNET;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.FileUtils;

import io.xdag.config.Config;
import io.xdag.net.message.NetDB;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class NetDBManager {
    @Getter
    private String database;

    @Getter
    private String databaseWhite;

    @Getter
    private String whiteUrl;

    @Getter
    private NetDB whiteDB;

    @Getter
    private NetDB netDB;

    public NetDBManager(Config config) {
        database = MAINNET ? config.getNetDBDir() : config.getNetDBDirTest();
        databaseWhite = MAINNET ? config.getWhiteListDir() : config.getWhiteListDirTest();
        whiteUrl = MAINNET ? WHITELIST_URL : WHITELIST_URL_TESTNET;
        whiteDB = new NetDB();
        netDB = new NetDB();
    }

    public void init() {
        // List<NetDB.IP> res = new ArrayList<>();
        File file = new File(databaseWhite);
        BufferedReader reader = null;
        try {
            if (!file.exists()) {
                if (!file.createNewFile()) {
                    log.debug("Create File failed");
                }
                // 白名单的地址 并且读取
                URL url = new URL(WHITELIST_URL_TESTNET);
                FileUtils.copyURLToFile(url, file);
                if (file.exists() && file.isFile()) {
                    reader = new BufferedReader(
                            new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8));
                    String temp = null;
                    String ip = null;
                    int port;
                    while ((temp = reader.readLine()) != null) {
                        ip = temp.split(":")[0];
                        port = Integer.parseInt(temp.split(":")[1]);
                        whiteDB.addNewIP(ip, port);
                    }
                }
            } else {
                reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8));
                String temp = null;
                String ip = null;
                int port;
                while ((temp = reader.readLine()) != null) {
                    ip = temp.split(":")[0];
                    port = Integer.parseInt(temp.split(":")[1]);
                    whiteDB.addNewIP(ip, port);
                }
                log.debug("File have exist..");
            }
        } catch (MalformedURLException e) {
            e.printStackTrace();
            System.out.println(false);
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println(true);
        } finally {
            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void updateNetDB(NetDB netDB) {
        if (netDB != null) {
            this.netDB.appendNetDB(netDB);
//            log.debug("ip list:" + this.netDB.getSize());
        }
    }

    public boolean canAccept(InetSocketAddress address) {
        boolean res = whiteDB.contains(address);
        return res;
    }

    public void refresh() {
        try {
            File file = new File(databaseWhite);
            BufferedReader reader = null;
            // 白名单的地址 并且读取
            URL url = null;
            url = new URL(WHITELIST_URL_TESTNET);

            FileUtils.copyURLToFile(url, file);
            if (file.exists() && file.isFile()) {
                reader = new BufferedReader(
                        new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8));
                String temp = null;
                String ip = null;
                int port;
                while ((temp = reader.readLine()) != null) {
                    ip = temp.split(":")[0];
                    port = Integer.parseInt(temp.split(":")[1]);
                    whiteDB = new NetDB();
                    whiteDB.addNewIP(ip, port);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    enum HostFlags {
        // our host
        HOST_OUR(0x01),
        // host connected
        HOST_CONNECTED(0x02),
        // host from init command
        HOST_SET(0x04),
        // host in netdb.txt
        HOST_INDB(0x08),
        // host not added
        HOST_NOT_ADD(0x10),
        // host in whitelist
        HOST_WHITE(0x20);

        private int cmd;

        private HostFlags(int cmd) {
            this.setCmd(cmd);
        }

        public int getCmd() {
            return cmd;
        }

        public void setCmd(int cmd) {
            this.cmd = cmd;
        }
    }
}
