package io.xdag.rpc.modules.eth;

import io.xdag.rpc.Web3;
import io.xdag.rpc.utils.TypeConverter;

public class EthModule implements EthModuleTransaction, EthModuleWallet{



    private final byte chainId;

    public EthModule(byte chainId) {
        this.chainId = chainId;
    }


    @Override
    public String sendTransaction(Web3.CallArguments args) {
        return null;
    }

    @Override
    public String sendRawTransaction(String rawData) {
        return null;
    }

    @Override
    public String[] accounts() {
        return new String[0];
    }

    @Override
    public String sign(String addr, String data) {
        return null;
    }


    public String chainId() {
        return TypeConverter.toJsonHex(new byte[] { chainId });
    }

    public String getCode(String address, String blockId) {
        return "0x";
    }
}
