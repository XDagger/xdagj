package io.xdag.config.spec;

import io.xdag.crypto.DnetKeys;
import io.xdag.discovery.peers.DiscoveryPeer;

import java.util.List;

/**
 * The Node Specifications
 */
public interface NodeSpec {
    // dnet
    String getNodeIp();
    int getNodePort();
    int getMaxConnections();
    int getConnectionReadTimeout();
    byte[] getDnetKeyBytes();
    DnetKeys getXKeys();
    void setDnetKeyBytes(byte[] dnetKeyBytes);
    int getTTL();
    List<String> getWhiteIPList();
    void setWhiteIPList(List<String> list);

    // libp2p
    boolean isBootnode() ;
    List<DiscoveryPeer> getBootnodes();
    int getDiscoveryPort();
    int getLibp2pPort();
    String getLibp2pPrivkey();
    String getDnetKeyFile();

    // Store
    String getRootDir();
    void setStoreDir(String dir);
    String getStoreDir();
    String getStoreBackupDir();
    void setStoreBackupDir(String dir);
    String getWhiteListDir();
    String getNetDBDir();

    int getStoreMaxOpenFiles();
    int getStoreMaxThreads ();
    boolean isStoreFromBackup();
    /** 用于测试加载已有区块数据 从C版本生成的数据 请将所需要的数据放在该目录下 */
    String getOriginStoreDir();

    // White List
    String getWhitelistUrl();
    boolean enableRefresh();

}
