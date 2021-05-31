package io.xdag.rpc.modules.xdag;

import io.xdag.core.Blockchain;

public class XdagModuleTransactionEnabled extends XdagModuleTransactionBase{

    public XdagModuleTransactionEnabled(Blockchain blockchain) {
        super(blockchain);
    }

    @Override
    public String sendRawTransaction(String rawData) {
        String result = super.sendRawTransaction(rawData);
        return result;
    }
}
