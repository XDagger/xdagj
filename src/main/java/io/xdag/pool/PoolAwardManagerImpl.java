package io.xdag.pool;

import io.xdag.Kernel;
import io.xdag.Wallet;
import io.xdag.config.Config;
import io.xdag.core.*;
import io.xdag.net.websocket.ChannelSupervise;
import io.xdag.utils.BasicUtils;
import io.xdag.utils.WalletUtils;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.MutableBytes32;
import org.hyperledger.besu.crypto.KeyPair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import static io.xdag.config.Constants.MIN_GAS;
import static io.xdag.core.XdagField.FieldType.XDAG_FIELD_IN;
import static io.xdag.core.XdagField.FieldType.XDAG_FIELD_OUTPUT;
import static io.xdag.pool.PoolAwardManagerImpl.BlockRewardHistorySender.awardMessageHistoryQueue;
import static io.xdag.utils.BasicUtils.*;
import static io.xdag.utils.BytesUtils.compareTo;

@Slf4j
public class PoolAwardManagerImpl implements PoolAwardManager, Runnable {
    private static final String TX_REMARK = "Block Reward Distribution";
    private final Kernel kernel;
    protected Config config;
    private final Blockchain blockchain;
    private final Wallet wallet;
    private final String fundAddress;
    private final double fundRation;
    private final double nodeRation;
    /**
     * The hash of the past sixteen blocks
     */
    protected List<Bytes32> blockPreHashs = new CopyOnWriteArrayList<>(new ArrayList<>(16));
    protected List<Bytes32> blockHashs = new CopyOnWriteArrayList<>(new ArrayList<>(16));
    protected List<Bytes32> minShares = new CopyOnWriteArrayList<>(new ArrayList<>(16));
    private static final BlockingQueue<AwardBlock> awardBlockBlockingQueue = new LinkedBlockingQueue<>();

    private final ExecutorService workExecutor = Executors.newSingleThreadExecutor(new BasicThreadFactory.Builder()
            .namingPattern("PoolAwardManager-work-thread")
            .daemon(true)
            .build());
    private volatile boolean isRunning = false;

    public PoolAwardManagerImpl(Kernel kernel) {
        this.kernel = kernel;
        this.config = kernel.getConfig();
        this.wallet = kernel.getWallet();
        this.fundAddress = config.getFundSpec().getFundAddress();
        this.fundRation = Math.max(0, Math.min(config.getFundSpec().getFundRation(), 100));
        this.nodeRation = Math.max(0, Math.min(config.getNodeSpec().getNodeRation(), 100));
        this.blockchain = kernel.getBlockchain();
        init();
    }

    public void addAwardBlock(Bytes32 share, Bytes32 preHash, Bytes32 hash, long generateTime) {
        AwardBlock awardBlock = new AwardBlock();
        awardBlock.share = share;
        awardBlock.preHash = preHash;
        awardBlock.hash = hash;
        awardBlock.generateTime = generateTime;
        if (!awardBlockBlockingQueue.offer(awardBlock)) {
            log.error("Failed to add a awardBlock to the block queue!");
        }
    }

    @Override
    public void start() {
        isRunning = true;
        workExecutor.execute(this);
        log.debug("PoolAwardManager started.");
    }

    @Override
    public void stop() {
        isRunning = false;
        workExecutor.shutdown();
    }

    @Override
    public void run() {
        while (isRunning) {
            try {
                AwardBlock awardBlock = awardBlockBlockingQueue.poll(1, TimeUnit.SECONDS);
                if (awardBlock != null) {
                    log.debug("Start award this block:{}", awardBlock.hash.toHexString());
                    payAndAddNewAwardBlock(awardBlock);
                }
            } catch (InterruptedException e) {
                log.error(" Can not take the awardBlock from awardBlockQueue" + e.getMessage(), e);
            }
        }
    }

    public void init() {
        log.debug("Pool award manager init.");
        // Container initialization
        for (int i = 0; i < 16; i++) {
            blockHashs.add(null);
            minShares.add(null);
            blockPreHashs.add(null);
        }
    }

    public void payAndAddNewAwardBlock(AwardBlock awardBlock) {
        int awardBlockIndex = (int) ((awardBlock.generateTime >> 16) & config.getNodeSpec().getAwardEpoch());
        log.debug("Add reward block to index: " + awardBlockIndex);
        if (payPools(awardBlock.generateTime) == 0) {
            log.debug("Start distributing block rewards...");
        }
        blockPreHashs.set(awardBlockIndex, awardBlock.preHash);
        blockHashs.set(awardBlockIndex, awardBlock.hash);
        minShares.set(awardBlockIndex, awardBlock.share);
    }

    public int payPools(long time) {
        // Obtain the corresponding +1 position of the current task and delay it for 16 rounds
        int paidBlockIndex = (int) (((time >> 16) + 1) & config.getNodeSpec().getAwardEpoch());
        log.info("Index of the block paid to the pool:{} ", paidBlockIndex);
        int keyPos;

        // Obtain the block hash and corresponding share to be paid
        Bytes32 preHash = blockPreHashs.get(paidBlockIndex) == null ? null : blockPreHashs.get(paidBlockIndex);
        Bytes32 hash = blockHashs.get(paidBlockIndex) == null ? null : blockHashs.get(paidBlockIndex);
        Bytes32 share = minShares.get(paidBlockIndex) == null ? null : minShares.get(paidBlockIndex);
        if (hash == null || share == null || preHash == null) {
            log.debug("Can not find the hash or nonce or preHash ,hash is null ?[{}],nonce is null ?[{}],preHash is " +
                            "null ?[{}]",
                    hash == null,
                    share == null, preHash == null);
            return -1;
        }
        // Obtain the hashlow of this block for query
        MutableBytes32 hashlow = MutableBytes32.create();
        hashlow.set(8, Bytes.wrap(hash).slice(8, 24));
        Block block = blockchain.getBlockByHash(hashlow, true);
        log.debug("Hash low [{}]", hashlow.toHexString());
        if (block == null) {
            log.debug("Can't find the block");
            return -2;
        }
        if (compareTo(block.getNonce().slice(0, 20).toArray(), 0,
                20, block.getCoinBase().getAddress().slice(8, 20).toArray(), 0, 20) == 0) {
            log.debug("This block is not produced by mining and belongs to the node");
            return -3;
        }
        if (kernel.getBlockchain().getMemOurBlocks().get(hashlow) == null) {
            keyPos = kernel.getBlockStore().getKeyIndexByHash(hashlow);
        } else {
            keyPos = kernel.getBlockchain().getMemOurBlocks().get(hashlow);
        }
        if (keyPos < 0) {
            log.debug("keyPos < 0,keyPos = {}", keyPos);
            return -4;
        }
        XAmount sendAmount = block.getInfo().getAmount();
        if (compareAmountTo(sendAmount, XAmount.ZERO) <= 0) {
            log.debug("no main block,can't pay");
            return -5;
        }
        Bytes32 poolWalletAddress = BasicUtils.hexPubAddress2Hashlow(String.valueOf(block.getNonce().slice(0, 20)));
        log.debug("=========== At this time {} starts to distribute rewards to pools===========", time);
        TransactionInfoSender transactionInfoSender = new TransactionInfoSender();
        transactionInfoSender.setPreHash(preHash);
        transactionInfoSender.setShare(share);
        doPayments(hashlow, sendAmount, poolWalletAddress, keyPos, transactionInfoSender);
        return 0;
    }

    public void doPayments(Bytes32 hashLow, XAmount sendAmount, Bytes32 poolWalletAddress, int keyPos,
                           TransactionInfoSender transactionInfoSender) {
        // Foundation rewards, default reward ratio is 5%
        XAmount fundAmount = sendAmount.multiply(div(fundRation, 100, 6));
        // Node rewards, default reward ratio is 5%
        XAmount nodeAmount = sendAmount.multiply(div(nodeRation, 100, 6));
        // Pool rewards
        XAmount poolAmount = sendAmount.subtract(fundAmount).subtract(nodeAmount);
        if (fundRation + nodeRation >= 100 || poolAmount.lessThan(MIN_GAS)) {
            log.error("Block reward distribution failed.The fundRation and nodeRation parameter settings are " +
                    "unreasonable.Your fundRation:{} ," +
                    "nodeRation:{}", fundRation, nodeRation);
            return;
        }
        // Amount output: community and pool, the remaining part is node reward
        ArrayList<Address> receipt = new ArrayList<>(2);
        if (sendAmount.compareTo(MIN_GAS.multiply(2)) >= 0) {
            receipt.add(new Address(pubAddress2Hash(fundAddress), XDAG_FIELD_OUTPUT, fundAmount, true));
            receipt.add(new Address(poolWalletAddress, XDAG_FIELD_OUTPUT, poolAmount, true));
            transactionInfoSender.setAmount(poolAmount.subtract(MIN_GAS).toDecimal(9,
                    XUnit.XDAG).toPlainString());
            transactionInfoSender.setFee(MIN_GAS.toDecimal(9, XUnit.XDAG).toPlainString());
            transactionInfoSender.setDonate(fundAmount.toDecimal(9, XUnit.XDAG).toPlainString());
            log.debug("Start payment...");
            transaction(hashLow, receipt, sendAmount, keyPos, transactionInfoSender);
        } else {
            log.debug("The balance of block {} is insufficient and rewards will not be distributed. Maybe this block " +
                            "has been rollback",
                    hashLow.toHexString());
        }
        receipt.clear();
    }

    public void transaction(Bytes32 hashLow, ArrayList<Address> receipt, XAmount sendAmount, int keypos,
                            TransactionInfoSender transactionInfoSender) {
        log.debug("All balance in this block: {}", sendAmount);
        log.debug("unlock keypos =[{}]", keypos);
        Map<Address, KeyPair> inputMap = new HashMap<>();
        Address input = new Address(hashLow, XDAG_FIELD_IN, sendAmount, false);
        KeyPair inputKey = wallet.getAccount(keypos);
        inputMap.put(input, inputKey);
        Block block = blockchain.createNewBlock(inputMap, receipt, false, TX_REMARK, MIN_GAS);
        if (inputKey.equals(wallet.getDefKey())) {
            block.signOut(inputKey);
        } else {
            block.signIn(inputKey);
            block.signOut(wallet.getDefKey());
        }
        log.debug("tx block hash [{}]", block.getHash().toHexString());
        kernel.getSyncMgr().validateAndAddNewBlock(new BlockWrapper(block, 5));
        // Rewards to the foundation and pool rewards are in the same transaction block
        transactionInfoSender.setTxBlock(block.getHash());
        transactionInfoSender.setDonateBlock(block.getHash());
        /*
        * Send the award distribute transaction information to pools for pools to validate and then distribute award
        to miners
        * */
        if (awardMessageHistoryQueue.remainingCapacity() == 0) {
            awardMessageHistoryQueue.poll();
        }
        // Send the last 16 reward distribution transaction history to the pool
        if (awardMessageHistoryQueue.offer(transactionInfoSender.toJsonString())) {
            ChannelSupervise.send2Pools(BlockRewardHistorySender.toJsonString());
        } else {
            log.error("Failed to add transaction history");
        }
        log.debug("The reward for block {} has been distributed to pool address {}", hashLow,
                WalletUtils.toBase58(receipt.get(1).getAddress().slice(8, 20).toArray()));
    }


    /**
     * Used to record information about the reward main block
     */
    public static class AwardBlock {
        Bytes32 share;
        Bytes32 preHash;
        Bytes32 hash;
        long generateTime;
    }

    @Setter
    public static class TransactionInfoSender {
        // Single transaction history
        Bytes32 txBlock;
        Bytes32 donateBlock;
        Bytes32 preHash;
        Bytes32 share;
        String amount;
        String fee;
        String donate;

        public String toJsonString() {
            return "{\n" +
                    "  \"txBlock\":\"" + txBlock.toUnprefixedHexString() + "\",\n" +
                    "  \"preHash\":\"" + preHash.toUnprefixedHexString() + "\",\n" +
                    "  \"share\":\"" + share.toUnprefixedHexString() + "\",\n" +
                    "  \"amount\":" + amount + ",\n" +
                    "  \"fee\":" + fee + ",\n" +
                    "  \"donateBlock\":\"" + txBlock.toUnprefixedHexString() + "\",\n" +
                    "  \"donate\":" + donate +
                    "\n}";
        }
    }

    public static class BlockRewardHistorySender {
        // Cache the last 16 blocks reward transaction history
        public static final BlockingQueue<String> awardMessageHistoryQueue = new LinkedBlockingQueue<>(16);
        private static final int REWARD_HISTORIES_FLAG = 3;

        public static String toJsonString() {
            return "{\n" +
                    "  \"msgType\": " + REWARD_HISTORIES_FLAG + ",\n" +
                    "  \"msgContent\": \n" + awardMessageHistoryQueue + "\n" +
                    "}";
        }

    }

}
