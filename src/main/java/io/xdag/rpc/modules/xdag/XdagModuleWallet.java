package io.xdag.rpc.modules.xdag;

public interface XdagModuleWallet {

    String[] accounts();

    String sign(String addr, String data);
}
