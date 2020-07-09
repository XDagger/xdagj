package io.xdag.wallet;

import io.xdag.config.Config;
import io.xdag.crypto.ECKey;
import java.util.List;

public interface Wallet {

  /** init wallet */
  int init(Config config) throws Exception;

  /** 获取到一个新的key */
  key_internal_item getDefKey();

  /** 创建一个新的key */
  void createNewKey();

  /** 通过编号获取密钥对 */
  ECKey getKeyByIndex(int index);

  List<key_internal_item> getKey_internal();
}
