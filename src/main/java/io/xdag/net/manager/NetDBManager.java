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

import io.xdag.config.Config;
import io.xdag.config.DevnetConfig;
import io.xdag.net.message.NetDB;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;

@Slf4j
public class NetDBManager {

    @Getter
    private final String database;
    @Getter
    private final String databaseWhite;
    @Getter
    private final String whiteUrl;
    @Getter
    private final NetDB netDB;
    @Getter
    private NetDB whiteDB;

    private Config config;

    public NetDBManager(Config config) {
        this.config = config;
        database = config.getNodeSpec().getNetDBDir();
        databaseWhite = config.getNodeSpec().getWhiteListDir();
        whiteUrl = config.getNodeSpec().getWhitelistUrl();
        whiteDB = new NetDB();
        netDB = new NetDB();
    }

    public void loadFromConfig() {
        for (InetSocketAddress address:config.getNodeSpec().getWhiteIPList()){
            whiteDB.addNewIP(address);
        }
    }

    public void loadFromUrl() {
        // 2. 从官网读取白名单并写入到netdb.txt文件上
        File file = new File(databaseWhite);
        BufferedReader reader = null;
        try {
            if (!file.exists()) {
                if (!file.createNewFile()) {
                    log.debug("Create File failed");
                }
                // 白名单的地址 并且读取
                URL url = new URL(whiteUrl);
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

    public void init() {
        loadFromConfig();
        if(config instanceof DevnetConfig) {
            // devnet only read from config
            return;
        }
        loadFromUrl();
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
            url = new URL(whiteUrl);

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
