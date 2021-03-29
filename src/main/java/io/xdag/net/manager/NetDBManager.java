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
    private final String database;

    @Getter
    private final String databaseWhite;

    @Getter
    private final String whiteUrl;

    @Getter
    private NetDB whiteDB;

    @Getter
    private final NetDB netDB;

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
//                URL url = new URL(WHITELIST_URL_TESTNET);
//                FileUtils.copyURLToFile(url, file);
                if (file.exists() && file.isFile()) {
                    reader = new BufferedReader(
                            new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8));
                    String temp;
                    String ip;
                    int port;
                    while ((temp = reader.readLine()) != null) {
                        ip = temp.split(":")[0];
                        port = Integer.parseInt(temp.split(":")[1]);
                        whiteDB.addNewIP(ip, port);
                    }
                }
            } else {
                reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8));
                String temp;
                String ip;
                int port;
                while ((temp = reader.readLine()) != null) {
                    ip = temp.split(":")[0];
                    port = Integer.parseInt(temp.split(":")[1]);
                    whiteDB.addNewIP(ip, port);
                }
                log.debug("File have exist..");
            }
        } catch (MalformedURLException e) {
            log.error(e.getMessage(), e);
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        } finally {
            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (IOException e) {
                log.error(e.getMessage(), e);
            }
        }
    }

    public void updateNetDB(NetDB netDB) {
        if (netDB != null) {
            this.netDB.appendNetDB(netDB);
        }
    }

    public boolean canAccept(InetSocketAddress address) {
        return whiteDB.contains(address);
    }

    public void refresh() {
        try {
            File file = new File(databaseWhite);
            BufferedReader reader;
            // 白名单的地址 并且读取
            URL url;
            url = new URL(WHITELIST_URL_TESTNET);

            FileUtils.copyURLToFile(url, file);
            if (file.exists() && file.isFile()) {
                reader = new BufferedReader(
                        new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8));
                String temp;
                String ip;
                int port;
                while ((temp = reader.readLine()) != null) {
                    ip = temp.split(":")[0];
                    port = Integer.parseInt(temp.split(":")[1]);
                    whiteDB = new NetDB();
                    whiteDB.addNewIP(ip, port);
                }
            }
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
    }

}
