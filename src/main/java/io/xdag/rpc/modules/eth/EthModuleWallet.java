package io.xdag.rpc.modules.eth;

public interface EthModuleWallet {

    String[] accounts();

    String sign(String addr, String data);
}
