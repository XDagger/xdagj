package io.xdag.rpc.modules.xdag;

import io.xdag.config.Constants;
import io.xdag.rpc.Web3;
import io.xdag.rpc.utils.TypeConverter;
import io.xdag.wallet.Wallet;
import org.bouncycastle.util.encoders.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;

public class XdagModuleTransactionBase implements XdagModuleTransaction{
    protected static final Logger logger = LoggerFactory.getLogger(XdagModuleTransactionBase.class);



    @Override
    public synchronized String sendTransaction(Web3.CallArguments args) {

        // 1. process args
        byte[] from = Hex.decode(args.from);
        byte[] to = Hex.decode(args.to);
        BigInteger value = args.value != null ? TypeConverter.stringNumberAsBigInt(args.value) : BigInteger.ZERO;

        // 2. create a transaction

        // 3. try to add blockchain


        return null;
    }

    @Override
    public String sendRawTransaction(String rawData) {

        // 1. build transaction

        // 2. try to add blockchain

        return null;
    }
}
