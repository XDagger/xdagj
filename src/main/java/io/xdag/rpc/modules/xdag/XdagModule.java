package io.xdag.rpc.modules.xdag;

import io.xdag.rpc.Web3;
import io.xdag.rpc.utils.TypeConverter;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Slf4j
public class XdagModule implements XdagModuleTransaction,XdagModuleWallet{

    private static final Logger logger = LoggerFactory.getLogger(XdagModule.class);

    private final XdagModuleWallet xdagModuleWallet;
    private final XdagModuleTransaction xdagModuleTransaction;
    private final byte chainId;


    public XdagModule(byte chainId, XdagModuleWallet xdagModuleWallet, XdagModuleTransaction xdagModuleTransaction) {
        this.chainId = chainId;
        this.xdagModuleWallet = xdagModuleWallet;
        this.xdagModuleTransaction = xdagModuleTransaction;
    }


    public String chainId() {
        return TypeConverter.toJsonHex(new byte[] { chainId });
    }

    @Override
    public String sendTransaction(Web3.CallArguments args) {
        return xdagModuleTransaction.sendTransaction(args);
    }

    @Override
    public String sendRawTransaction(String rawData) {
        return xdagModuleTransaction.sendRawTransaction(rawData);
    }

    @Override
    public String[] accounts() {
        return xdagModuleWallet.accounts();
    }

    @Override
    public String sign(String addr, String data) {
        return xdagModuleWallet.sign(addr,data);
    }
}
