package io.xdag.net.manager;

import static io.xdag.config.Config.MainNet;
import static io.xdag.config.Config.WHITELIST_URL;
import static io.xdag.config.Config.WHITELIST_URL_TESTNET;

import io.xdag.config.Config;
import io.xdag.net.message.NetDB;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.URL;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;

@Slf4j
public class NetDBManager {

    private @Getter String database;
    private @Getter String databaseWhite;
    private @Getter String whiteUrl;

    private @Getter NetDB whiteDB;
    private @Getter NetDB netDB;

    public NetDBManager(Config config) {
        database = MainNet ? config.getNetDBDir() : config.getNetDBDirTest();
        databaseWhite = MainNet ? config.getWhiteListDir() : config.getWhiteListDirTest();
        whiteUrl = MainNet ? WHITELIST_URL : WHITELIST_URL_TESTNET;
        whiteDB = new NetDB();
        netDB = new NetDB();
    }

    public void init() {
        // List<NetDB.IP> res = new ArrayList<>();
        File file = new File(databaseWhite);
        FileReader fr = null;
        BufferedReader br = null;
        try {
            if (!file.exists()) {
                if (!file.createNewFile()) {
                    log.debug("Create File failed");
                }
                // 白名单的地址 并且读取
                URL url = new URL(WHITELIST_URL_TESTNET);
                FileUtils.copyURLToFile(url, file);
                if (file.exists() && file.isFile()) {
                    fr = new FileReader(file);
                    br = new BufferedReader(fr);
                    String temp = null;
                    String ip = null;
                    int port;
                    while ((temp = br.readLine()) != null) {
                        ip = temp.split(":")[0];
                        port = Integer.parseInt(temp.split(":")[1]);
                        whiteDB.addNewIP(ip, port);
                    }
                }
            } else {
                fr = new FileReader(file);
                br = new BufferedReader(fr);
                String temp = null;
                String ip = null;
                int port;
                while ((temp = br.readLine()) != null) {
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
                if (br != null) {
                    br.close();
                }
                if (fr != null) {
                    fr.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void updateNetDB(NetDB netDB) {
        log.debug("Update Netdb");
        if (netDB != null) {
            this.netDB.appendNetDB(netDB);
            log.debug("ip list:" + this.netDB.getSize());
        }
    }

    public boolean canAccept(InetSocketAddress address) {
        boolean res = whiteDB.contains(address);
        return res;
    }

    public void refresh() {
        try {
            File file = new File(databaseWhite);
            FileReader fr = null;
            BufferedReader br = null;
            // 白名单的地址 并且读取
            URL url = null;
            url = new URL(WHITELIST_URL_TESTNET);

            FileUtils.copyURLToFile(url, file);
            if (file.exists() && file.isFile()) {
                fr = new FileReader(file);
                br = new BufferedReader(fr);
                String temp = null;
                String ip = null;
                int port;
                while ((temp = br.readLine()) != null) {
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
        HOST_OUR(1),
        // host connected
        HOST_CONNECTED(2),
        // host from init command
        HOST_SET(4),
        // host in netdb.txt
        HOST_INDB(8),
        // host not added
        HOST_NOT_ADD(0x10),
        // host in whitelist
        HOST_WHITE(0x20);

        private final int cmd;

        HostFlags(int cmd) {
            this.cmd = cmd;
        }
    }
}
