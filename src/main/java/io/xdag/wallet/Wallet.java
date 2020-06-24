package io.xdag.wallet;

import java.util.List;

import io.xdag.config.Config;
import io.xdag.crypto.ECKey;

public interface Wallet {

    /**init wallet*/
    public int init(Config config) throws Exception;

    /**获取到一个新的key*/
    public key_internal_item getDefKey();

    /**创建一个新的key*/
    public void createNewKey();

    /**通过编号获取密钥对*/
    public ECKey getKeyByIndex(int index);

    public List<key_internal_item> getKey_internal();
}
