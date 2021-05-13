package io.xdag.rpc.modules.web3;

import com.sun.jdi.LongValue;
import io.xdag.Kernel;
import io.xdag.core.Block;
import io.xdag.core.Blockchain;
import io.xdag.rpc.dto.BlockResultDTO;
import io.xdag.rpc.modules.xdag.XdagModule;
import io.xdag.utils.StringUtils;
import org.bouncycastle.util.encoders.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.Objects;

import static io.xdag.rpc.utils.TypeConverter.toQuantityJsonHex;
import static io.xdag.utils.BasicUtils.address2Hash;
import static io.xdag.utils.BasicUtils.amount2xdag;

public class Web3XdagModuleImpl implements Web3XdagModule{

    private static final Logger logger = LoggerFactory.getLogger(Web3XdagModuleImpl.class);

    class SyncingResult {
        public String currentBlock;
        public String highestBlock;
    }

    private final Blockchain blockchain;
    private final XdagModule xdagModule;
    private final Kernel kernel;

    public Web3XdagModuleImpl(Blockchain blockchain, XdagModule xdagModule, Kernel kernel) {
        this.blockchain = blockchain;
        this.xdagModule = xdagModule;
        this.kernel = kernel;
    }


    @Override
    public XdagModule getXdagModule() {
        return xdagModule;
    }

    @Override
    public String xdag_protocolVersion() {
        return null;
    }

    @Override
    public Object xdag_syncing() {
        long currentBlock = this.blockchain.getXdagStats().nmain;
        long highestBlock = this.blockchain.getXdagStats().totalnmain;

        if (highestBlock < currentBlock){
            return false;
        }

        SyncingResult s = new SyncingResult();
        try {
            s.currentBlock = toQuantityJsonHex(currentBlock);
            s.highestBlock = toQuantityJsonHex(highestBlock);

            return s;
        } finally {
            logger.debug("xdag_syncing():current {}, highest {} ", s.currentBlock, s.highestBlock);
        }
    }

    @Override
    public String xdag_coinbase() {
        return Hex.toHexString(kernel.getPoolMiner().getAddressHash());
    }

    @Override
    public String xdag_blockNumber() {
        long b = blockchain.getXdagStats().nmain;
        logger.debug("xdag_blockNumber(): {}", b);

        return toQuantityJsonHex(b);
    }

    @Override
    public String xdag_getBalance(String address) throws Exception {
        byte[] hash;
        if (org.apache.commons.lang3.StringUtils.length(address) == 32) {
            hash = address2Hash(address);
        } else {
            hash = StringUtils.getHash(address);
        }
        byte[] key = new byte[32];
        System.arraycopy(Objects.requireNonNull(hash), 8, key, 8, 24);
        Block block = kernel.getBlockStore().getBlockInfoByHash(key);
        double balance = amount2xdag(block.getInfo().getAmount());
        return toQuantityJsonHex(balance);
    }

    @Override
    public BlockResultDTO xdag_getBlockByNumber(String bnOrId, Boolean full) throws Exception {
        return null;
    }

    @Override
    public BlockResultDTO xdag_getBlockByHash(String blockHash, Boolean full) throws Exception {
        return null;
    }
}
