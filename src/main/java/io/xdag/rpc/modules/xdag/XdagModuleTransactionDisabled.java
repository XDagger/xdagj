package io.xdag.rpc.modules.xdag;

import io.xdag.core.Blockchain;
import io.xdag.rpc.Web3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.xdag.rpc.exception.XdagJsonRpcRequestException.invalidParamError;

public class XdagModuleTransactionDisabled extends XdagModuleTransactionBase{

    private static final Logger logger = LoggerFactory.getLogger(XdagModuleTransactionDisabled.class);

    public XdagModuleTransactionDisabled(Blockchain blockchain) {
        super(blockchain);
    }

    @Override
    public String sendTransaction(Web3.CallArguments args) {
        logger.debug("xdag_sendTransaction({}): {}", args, null);
        throw invalidParamError("Local wallet is disabled in this node");
    }

    @Override
    public String sendRawTransaction(String rawData) {
        logger.debug("xdag_sendRawTransaction({}): {}", rawData, null);
        throw invalidParamError("Local wallet is disabled in this node");
    }
}
