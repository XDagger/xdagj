package io.xdag.rpc.modules.xdag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

import static io.xdag.rpc.exception.XdagJsonRpcRequestException.invalidParamError;

public class XdagModuleWalletDisabled implements XdagModuleWallet{

    private static final Logger logger = LoggerFactory.getLogger(XdagModuleWalletDisabled.class);



    @Override
    public String[] accounts() {
        String[] accounts = {};
        logger.debug("xdag_accounts(): {}", Arrays.toString(accounts));
        return accounts;
    }

    @Override
    public String sign(String addr, String data) {
        logger.debug("eth_sign({}, {}): {}", addr, data, null);
        throw invalidParamError("Local wallet is disabled in this node");
    }
}
