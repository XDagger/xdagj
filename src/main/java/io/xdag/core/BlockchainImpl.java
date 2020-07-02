package io.xdag.core;

import static io.xdag.config.Constants.BI_APPLIED;
import static io.xdag.config.Constants.BI_EXTRA;
import static io.xdag.config.Constants.BI_MAIN;
import static io.xdag.config.Constants.BI_MAIN_CHAIN;
import static io.xdag.config.Constants.BI_MAIN_REF;
import static io.xdag.config.Constants.BI_OURS;
import static io.xdag.config.Constants.BI_REF;
import static io.xdag.config.Constants.MAX_ALLOWED_EXTRA;
import static io.xdag.utils.BasicUtils.amount2xdag;
import static io.xdag.utils.BasicUtils.getDiffByHash;
import static io.xdag.utils.BasicUtils.xdag2amount;
import static io.xdag.utils.FastByteComparisons.equalBytes;
import static io.xdag.utils.MapUtils.getHead;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.util.encoders.Hex;

import io.xdag.Kernel;
import io.xdag.crypto.ECKey;
import io.xdag.crypto.Sha256Hash;
import io.xdag.db.DatabaseFactory;
import io.xdag.db.store.AccountStore;
import io.xdag.db.store.BlockStore;
import io.xdag.db.store.OrphanPool;
import io.xdag.net.message.NetStatus;
import io.xdag.utils.ByteArrayWrapper;
import io.xdag.utils.BytesUtils;
import io.xdag.utils.XdagTime;
import io.xdag.wallet.Wallet;
import io.xdag.wallet.key_internal_item;

public class BlockchainImpl implements Blockchain {

  enum OrphanRemoveActions {
    ORPHAN_REMOVE_NORMAL,
    ORPHAN_REMOVE_REUSE,
    ORPHAN_REMOVE_EXTRA
  };

  private static final Logger logger = LoggerFactory.getLogger(BlockchainImpl.class);

  private Wallet wallet;

  private BlockStore blockStore;
  private AccountStore accountStore;
  /** 非Extra orphan存放 */
  private OrphanPool orphanPool;

  private LinkedHashMap<ByteArrayWrapper, Block> MemOrphanPool = new LinkedHashMap<>();

  private Map<ByteArrayWrapper, Integer> MemAccount = new ConcurrentHashMap<>();

  private final ReentrantReadWriteLock stateLock = new ReentrantReadWriteLock();

  private BigInteger topDiff;
  /** 存放的是一个可能是最大难度的block hash */
  private byte[] top_main_chain;

  private byte[] pretop;
  private BigInteger pretopDiff;

  private NetStatus netStatus;

  public BlockchainImpl(Kernel kernel, DatabaseFactory dbFactory) {
    this.wallet = kernel.getWallet();
    this.accountStore = kernel.getAccountStore();
    this.blockStore = kernel.getBlockStore();
    this.orphanPool = kernel.getOrphanPool();
    this.pretop = this.top_main_chain = blockStore.getOriginpretop();
    this.pretopDiff = this.topDiff = blockStore.getOriginpretopDiff();
    //        this.blockNumber = blockStore.getBlockNumber();
    this.netStatus = kernel.getNetStatus();
    this.netStatus.init(pretopDiff, blockStore.getMainNumber(), blockStore.getBlockNumber());
  }

  /** 尝试去连接这个块 */
  @Override
  public ImportResult tryToConnect(Block block) {
    logger.debug("======Connect New Block:" + Hex.toHexString(block.getHashLow()) + "======");
    ReentrantReadWriteLock.WriteLock writeLock = this.stateLock.writeLock();
    writeLock.lock();
    try {

      ImportResult result = ImportResult.IMPORTED_NOT_BEST;
      // 如果区块已经存在不处理
      if (isExist(block.getHashLow())) {
        return ImportResult.EXIST;
      }

      if (isExtraBlock(block)) {
        updateBlockFlag(block, BI_EXTRA, true);
      }

      List<Address> all = block.getLinks();
      // 检查区块的引用区块是否都存在,对所有input和output放入block（可能在pending或db中取出
      for (Address ref : all) {
        if (ref != null) {
          if (!isExist(ref.getHashLow())) {
            logger.debug("No Parent " + Hex.toHexString(ref.getHashLow()));
            result = ImportResult.NO_PARENT;
            result.setHashLow(ref.getHashLow());
            return result;
          } else {
            if (!ref.getAmount().equals(BigInteger.ZERO)) {
              updateBlockFlag(block, BI_EXTRA, false);
            }
          }
        }
      }

      // 检查区块合法性 检查input是否能使用
      if (!canUseInput(block)) {
        logger.debug("this is a invalid block");
        return ImportResult.INVALID_BLOCK;
      }

      // 如果是自己的区块
      if (checkMineAndAdd(block)) {
        logger.debug("A block hash:" + Hex.toHexString(block.getHashLow()) + " become mine");
        updateBlockFlag(block, BI_OURS, true);
      }

      // 更新区块难度和maxdifflink
      calculateBlockDiff(block);

      // 检查当前主链
      checkNewMain();

      // 更新pretop
      setPretop(block);
      if (top_main_chain != null) {
        logger.debug(
            "top main chain,这个时候的top main chain为【{}】", Hex.toHexString(top_main_chain));
        setPretop(getBlockByHash(top_main_chain, false));
      }

      // TODO:extra 处理
      if (MemOrphanPool.size() > MAX_ALLOWED_EXTRA) {
        Block reuse = getHead(MemOrphanPool).getValue();
        logger.debug("remove when extra too big");
        removeOrphan(reuse, OrphanRemoveActions.ORPHAN_REMOVE_REUSE);
        netStatus.decBlock();
        if ((reuse.getFlags() & BI_OURS) != 0) {
          removeAccount(reuse);
        }
      }

      // 根据难度更新主链
      // 判断难度是否是比当前最大，并以此更新topmainchain
      if (block.getDifficulty().compareTo(getTopDiff()) > 0) {
        // 切换主链 fork
        Block blockRef = null;
        Block blockRef0 = null;
        // 把当前区块根据最大难度链接块递归查询到不是主链块为止 将这段的区块更新为主链块
        for (blockRef = block;
            blockRef != null && ((blockRef.flags & BI_MAIN_CHAIN) == 0);
            blockRef = getMaxDiffLink(blockRef, false)) {
          Block tmpRef = getMaxDiffLink(blockRef, false);
          //if (tmpRef != null) {}
          if ((tmpRef == null || blockRef.getDifficulty().compareTo(calculateBlockDiff(tmpRef)) > 0)
              && (blockRef0 == null
                  || XdagTime.getEpoch(blockRef0.getTimestamp())
                      > XdagTime.getEpoch(blockRef.getTimestamp()))) {
            updateBlockFlag(blockRef, BI_MAIN_CHAIN, true);
            blockRef0 = blockRef;
          }
        }
        // 分叉点
        if (blockRef != null
            && blockRef0 != null
            && !blockRef.equals(blockRef0)
            && XdagTime.getEpoch(blockRef.getTimestamp())
                == XdagTime.getEpoch(blockRef0.getTimestamp())) {
          blockRef = getMaxDiffLink(blockRef, false);
        }
        // 将主链回退到blockref
        unWindMain(blockRef);

        setTopDiff(block.getDifficulty());

        setTopMainchain(block);

        result = ImportResult.IMPORTED_BEST;
      }

      // remove links
      for (Address address : all) {
        logger.debug("remove links");
        removeOrphan(
            getBlockByHash(address.getHashLow(), false),
            (block.flags & BI_EXTRA) != 0
                ? OrphanRemoveActions.ORPHAN_REMOVE_EXTRA
                : OrphanRemoveActions.ORPHAN_REMOVE_NORMAL);
        // TODO:add backref
        //            if(!all.get(i).getAmount().equals(BigInteger.ZERO)){
        //                Block blockRef = getBlockByHash(all.get(i).getHashLow(),false);
        //            }
      }

      // 新增区块
      netStatus.incBlock();
      if (netStatus.getTotalnblocks() < netStatus.getNblocks()) {
        netStatus.setTotalnblocks(netStatus.getNblocks());
      }

      logger.debug("======New block waiting to link======");
      if ((block.flags & BI_EXTRA) != 0) {
        logger.debug(Hex.toHexString(block.getHashLow()) + " into extra");
        MemOrphanPool.put(new ByteArrayWrapper(block.getHashLow()), block);
      } else {
        logger.debug(Hex.toHexString(block.getHashLow()) + " into orphan");
        saveBlock(block);
        orphanPool.addOrphan(block);
      }
      logger.debug("Current diff:" + getTopDiff().toString(16));

      return result;
    } finally {
      writeLock.unlock();
    }
  }

  /** 检查更新主链 * */
  @Override
  public synchronized void checkNewMain() {
    logger.debug("Check New Main...");
    Block p = null;
    int i = 0;
    if (top_main_chain != null) {
      for (Block block = getBlockByHash(top_main_chain, true);
          block != null && ((block.flags & BI_MAIN) == 0);
          block = getMaxDiffLink(block, true)) {

        if ((block.flags & BI_MAIN_CHAIN) != 0) {
          p = block;
          ++i;
        }
      }
    }
    if (p != null
        && ((p.flags & BI_REF) != 0)
        && i > 1
        && XdagTime.getCurrentTimestamp() >= p.getTimestamp() + 2 * 1024) {
      setMain(p);
    }
  }

  /** 回退到区块block * */
  public synchronized void unWindMain(Block block) {
    if (block == null) {
      return;
    }
    if (top_main_chain != null) {
      for (Block tmp = getBlockByHash(top_main_chain, true);
          tmp != null && !tmp.equals(block);
          tmp = getMaxDiffLink(tmp, true)) {
        updateBlockFlag(tmp, BI_MAIN_CHAIN, false);
        // 更新对应的flag信息
        if ((tmp.flags & BI_MAIN) != 0) {
          unSetMain(tmp);
        }
      }
    }
  }

  /** 执行区块并返回手续费 * */
  private long applyBlock(Block block) {
    long sumIn = 0;
    long sumOut = 0; // sumOut是用来支付其他区块link自己的手续费 现在先用0
    // 处理过
    if ((block.flags & BI_MAIN_REF) != 0) {
      return -1;
    }
    // 设置为已处理
    updateBlockFlag(block, BI_MAIN_REF, true);

    List<Address> links = block.getLinks();
    if (links == null || links.size() == 0) {
      updateBlockFlag(block, BI_APPLIED, true);
      return 0;
    }

    for (Address link : links) {
      Block ref = getBlockByHash(link.getHashLow(), true);
      long ret = applyBlock(ref);
      if (ret == -1) {
        continue;
      }
      updateBlockRef(ref, new Address(block));
      if (amount2xdag(block.getAmount() + link.getAmount().longValue())
          >= amount2xdag(block.getAmount())) {
        acceptAmount(block, link.getAmount().longValue());
      }
    }
    for (Address link : links) {
      if (link.getType() == XdagField.FieldType.XDAG_FIELD_IN) {
        Block ref = getBlockByHash(link.getHashLow(), false);

        if (amount2xdag(ref.getAmount()) < amount2xdag(link.getAmount().longValue())) {
          return 0;
        }
        if (amount2xdag(sumIn + link.getAmount().longValue()) < amount2xdag(sumIn)) {
          return 0;
        }
        sumIn += link.getAmount().longValue();
      } else {
        if (amount2xdag(sumOut + link.getAmount().longValue()) < amount2xdag(sumOut)) {
          return 0;
        }
        sumOut += link.getAmount().longValue();
      }
    }

    if (amount2xdag(sumIn + block.getAmount()) < amount2xdag(sumOut)
        || amount2xdag(sumIn + block.getAmount()) < amount2xdag(sumIn)) {
      logger.debug("执行块失败");
      return 0;
    }
    for (Address link : links) {
      if (link.getType() == XdagField.FieldType.XDAG_FIELD_IN) {
        Block ref = getBlockByHash(link.getHashLow(), false);
        acceptAmount(ref, -link.getAmount().longValue());
      } else {
        Block ref = getBlockByHash(link.getHashLow(), false);
        acceptAmount(ref, link.getAmount().longValue());
      }
    }

    // 不一定大于0 因为可能部分金额扣除
    long remain = sumIn - sumOut;
    acceptAmount(block, remain);
    updateBlockFlag(block, BI_APPLIED, true);
    //        logger.debug("====Apply block["+Hex.toHexString(block.getHashLow())+"] done====");

    return 0;
  }

  public long unApplyBlock(Block block) {
    List<Address> links = block.getLinks();
    if ((block.flags & BI_APPLIED) != 0) {
      long sum = 0;
      for (Address link : links) {
        if (link.getType() == XdagField.FieldType.XDAG_FIELD_IN) {
          Block ref = getBlockByHash(link.getHashLow(), false);
          acceptAmount(ref, link.getAmount().longValue());
          sum -= link.getAmount().longValue();
        } else {
          Block ref = getBlockByHash(link.getHashLow(), false);
          acceptAmount(ref, -link.getAmount().longValue());
          sum += link.getAmount().longValue();
        }
      }
      acceptAmount(block, sum);
      updateBlockFlag(block, BI_APPLIED, false);
    }
    updateBlockFlag(block, BI_MAIN_REF, false);
    updateBlockRef(block, null);
    for (Address link : links) {
      Block ref = getBlockByHash(link.getHashLow(), true);
      if (ref.getRef() != null
          && equalBytes(ref.getRef().getHashLow(), block.getHashLow())
          && ((ref.getFlags() & BI_MAIN_REF) != 0)) {
        acceptAmount(block, unApplyBlock(ref));
      }
    }
    return 0;
  }

  /** 设置以block为主块的主链 要么分叉 要么延长 * */
  public synchronized void setMain(Block block) {

    blockStore.mainNumberInc();

    netStatus.incMain();
    // 设置奖励
    long reward = getCurrentReward();

    updateBlockFlag(block, BI_MAIN, true);

    // 接收奖励
    acceptAmount(block, reward);

    // 递归执行主块引用的区块 并获取手续费
    acceptAmount(block, applyBlock(block));

    // 主块REF指向自身
    updateBlockRef(block, new Address(block));
    // logger.info("set mainblock [{}]", Hex.toHexString(block.getHash()));

  }

  /** 取消Block主块身份 * */
  public synchronized void unSetMain(Block block) {
    blockStore.mainNumberDec();
    netStatus.decMain();

    long amount = getCurrentReward();
    updateBlockFlag(block, BI_MAIN, false);

    // 去掉奖励和引用块的手续费
    acceptAmount(block, -amount);
    acceptAmount(block, unApplyBlock(block));
  }

  @Override
  public Block createNewBlock(Map<Address, ECKey> pairs, List<Address> to, boolean mining) {
    if (pairs == null && to == null) {
      if (mining) {
        return createMainBlock();
      }
    }
    int defkeyIndex = -1;
    // 遍历所有key 判断是否有defkey
    List<ECKey> keys = new ArrayList<ECKey>(pairs.values());
    for (int i = 0; i < keys.size(); i++) {
      if (keys.get(i).equals(wallet.getDefKey().ecKey)) {
        defkeyIndex = i;
      }
    }

    List<Address> all = new ArrayList<>();
    all.addAll(pairs.keySet());
    all.addAll(to);
    int res = 1 + pairs.size() + to.size() + 3 * pairs.size() + defkeyIndex == -1 ? 2 : 0;
    long sendtime = XdagTime.getCurrentTimestamp();
    if (mining) {
      res += 1;
      sendtime = XdagTime.getMainTime();
    }

    Address pretop = null;
    if (getPreTopMainBlockForLink(sendtime) != null) {

      pretop = new Address(getPreTopMainBlockForLink(sendtime), XdagField.FieldType.XDAG_FIELD_OUT);
      res++;
    }
    List<Address> refs = getBlockFromOrphanPool(16 - res);
    return new Block(sendtime, pretop, all, refs, mining, keys, defkeyIndex);
  }

  public Block createMainBlock() {
    int res = 4;
    long sendtime = XdagTime.getMainTime();
    Address pretop = null;
    if (getPreTopMainBlockForLink(sendtime) != null) {
      pretop = new Address(getPreTopMainBlockForLink(sendtime), XdagField.FieldType.XDAG_FIELD_OUT);
      res++;
    }
    List<Address> refs = getBlockFromOrphanPool(16 - res);
    return new Block(sendtime, pretop, null, refs, true, null, -1);
  }

  /*
   * @Author punk
   *
   * @Description 从orphan中获取一定数量的orpahn块用来link
   *
   * @Date 2020/4/21
   *
   * @Param [num]
   *
   * @return java.util.List<io.xdag.core.Address>
   **/
  public List<Address> getBlockFromOrphanPool(int num) {
    return orphanPool.getOrphan(num);
  }

  public byte[] getPreTopMainBlockForLink(long sendTime) {
    long maintime = XdagTime.getEpoch(sendTime);
    Block topInfo = null;
    if (top_main_chain == null) {
      return null;
    }

    topInfo = getBlockByHash(top_main_chain, false);
    if (XdagTime.getEpoch(topInfo.getTimestamp()) == maintime) {
      return pretop;
    } else {

      return top_main_chain;
    }
  }

  @Override
  public byte[] getTop_main_chain() {
    return top_main_chain;
  }

  public synchronized void setPretop(Block block) {
    if (block == null) {
      return;
    }
    if (XdagTime.getEpoch(block.getTimestamp()) > XdagTime.getCurrentEpoch()) {
      return;
    }
    BigInteger blockDiff = calculateBlockDiff(block);
    if (pretop == null) {
      blockStore.setPretop(block);
      blockStore.setPretopDiff(blockDiff);
      //            if ((block.flags & BI_EXTRA) == 0) { //存进来
      //                blockStore.setOriginpretop(block);
      //                blockStore.setOriginpretopdiff(blockDiff);
      //            }
      return;
    }
    if (blockDiff.compareTo(getPretopDiff()) > 0) {
      blockStore.setPretop(block);
      blockStore.setPretopDiff(blockDiff);
      //            if ((block.flags & BI_EXTRA) == 0) { //存进来
      //                blockStore.setOriginpretop(block);
      //                blockStore.setOriginpretopdiff(blockDiff);
      //            }
    }
  }

  /** 计算区块在链上的难度 同时设置难度 和最大难度连接 并返回区块难度 * */
  public BigInteger calculateBlockDiff(Block block) {

    if (block.getDifficulty() != null) {
      logger.debug("block 的难度不为空，hash[{}]", Hex.toHexString(block.getHash()));
      return block.getDifficulty();
    }

    // 初始区块自身难度设置
    BigInteger diff0 = getDiffByHash(block.getHash());
    block.setDifficulty(diff0);

    BigInteger maxDiff = diff0;
    Address maxdifflink = null;

    // 临时区块
    Block tmpBlock;
    if (block.getLinks().size() == 0) {
      return diff0;
    }

    // 遍历所有link 找maxlink
    List<Address> links = block.getLinks();
    for (Address ref : links) {
      Block refBlock = getBlockByHash(ref.getHashLow(), false);

      // 如果引用的那个快的epoch 小于当前这个块的回合
      if (XdagTime.getEpoch(refBlock.getTimestamp()) < XdagTime.getEpoch(block.getTimestamp())) {
        // 如果难度大于当前最大难度
        BigInteger curDiff = refBlock.getDifficulty().add(diff0);
        if (curDiff.compareTo(maxDiff) > 0) {
          maxDiff = curDiff;
          maxdifflink = ref;
        }
      } else {
        // 计算出来的diff
        // 1. 不在同一epoch的maxdiff+diff0
        // 2. 同一epoch的maxdiff
        tmpBlock = refBlock; // tmpBlock是link中的
        BigInteger curDiff = refBlock.getDifficulty();
        while ((tmpBlock != null)
            && XdagTime.getEpoch(tmpBlock.getTimestamp())
                == XdagTime.getEpoch(block.getTimestamp())) {
          tmpBlock = getMaxDiffLink(tmpBlock, false);
        }
        if (tmpBlock != null
            && (XdagTime.getEpoch(tmpBlock.getTimestamp())
                < XdagTime.getEpoch(block.getTimestamp()))) {
          curDiff = tmpBlock.getDifficulty().add(diff0);
        }
        if (curDiff.compareTo(maxDiff) > 0) {
          maxDiff = curDiff;
          maxdifflink = ref;
        }
      }
    }
    block.setDifficulty(maxDiff);
    block.setMaxDifflink(maxdifflink);
    return maxDiff;
  }

  @Override
  public Block getBlockByHash(byte[] hashlow, boolean isRaw) {
    if (hashlow == null) {
      return null;
    }
    ByteArrayWrapper key = new ByteArrayWrapper(hashlow);
    if (MemOrphanPool.containsKey(key)) {
      return MemOrphanPool.get(key);
    }
    return blockStore.getBlockByHash(hashlow, isRaw);
  }

  public Block getMaxDiffLink(Block block, boolean isRaw) {
    if (block.getMaxDifflink() != null) {
      return getBlockByHash(block.getMaxDifflink().getHashLow(), isRaw);
    }
    return null;
  }

  @Override
  public BigInteger getTopDiff() {
    return topDiff;
  }

  @Override
  public BigInteger getPretopDiff() {
    return blockStore.getPretopDiff();
  }

  @Override
  public boolean hasBlock(byte[] hash) {
    return blockStore.hasBlock(hash);
  }

  public void removeOrphan(Block removeBlockInfo, OrphanRemoveActions action) {
    if (((removeBlockInfo.getFlags() & BI_REF) == 0)
        && (action != OrphanRemoveActions.ORPHAN_REMOVE_EXTRA
            || (removeBlockInfo.getFlags() & BI_EXTRA) != 0)) {
      // 如果removeBlock是BI_EXTRA
      if ((removeBlockInfo.getFlags() & BI_EXTRA) != 0) {
        logger.debug("移除Extra");
        // 那removeBlockInfo就是完整的
        // 从MemOrphanPool中去除
        ByteArrayWrapper key = new ByteArrayWrapper(removeBlockInfo.getHashLow());
        // 如果不存在
        if (!MemOrphanPool.containsKey(key)) {
          return;
        }
        Block removeBlockRaw = MemOrphanPool.get(key);
        MemOrphanPool.remove(key);
        if (action != OrphanRemoveActions.ORPHAN_REMOVE_REUSE) {
          // 将区块保存
          saveBlock(removeBlockRaw);
          // 移除所有EXTRA块链接的块
          List<Address> all = removeBlockRaw.getLinks();
          for (Address address : all) {
            removeOrphan(
                getBlockByHash(address.getHashLow(), false),
                OrphanRemoveActions.ORPHAN_REMOVE_NORMAL);
          }
        }
        // 更新removeBlockRaw的flag
        // nextra减1
        updateBlockFlag(removeBlockRaw, BI_EXTRA, false);
      } else {
        // 从orphanPool中移除
        // noref减1 因为非BI_EXTRA的块已经存储了所以不用存储操作
        logger.debug("移除orphan");
        if (!orphanPool.containsKey(removeBlockInfo.getHashLow())) {
          return;
        }
        orphanPool.deleteByHash(removeBlockInfo.getHashLow());
      }
      // 更新这个块的flag
      updateBlockFlag(removeBlockInfo, BI_REF, true);
    }
  }

  public void updateBlockFlag(Block block, byte flag, boolean direction) {
    // logger.debug("update flag");
    if (direction) {
      block.flags |= flag;
    } else {
      block.flags &= ~flag;
    }
    if (block.isSaved) {

      blockStore.updateBlockInfo(BlockStore.BLOCK_FLAG, block);
    }
  }

  public void updateBlockRef(Block block, Address ref) {
    block.setRef(ref);
    if (block.isSaved) {
      blockStore.updateBlockInfo(BlockStore.BLOCK_REF, block);
    }
  }

  public void saveBlock(Block block) {
    block.isSaved = true;
    logger.debug("save a block block hash [{}]", Hex.toHexString(block.getHashLow()));
    blockStore.saveBlock(block);
    // 如果是自己的账户
    if (MemAccount.containsKey(new ByteArrayWrapper(block.getHash()))) {
      logger.debug("new account");
      addNewAccount(block, MemAccount.get(new ByteArrayWrapper(block.getHash())));
      MemAccount.remove(new ByteArrayWrapper(block.getHash()));
    }
  }

  public boolean isExtraBlock(Block block) {
    if ((block.getTimestamp() & 0xffff) != 0xffff || block.getNonce() == null || block.isSaved()) {
      return false;
    }
    return true;
  }

  @Override
  public long getOrphanSize() {
    return orphanPool.getOrphanSize();
  }

  @Override
  public long getExtraSize() {
    return MemOrphanPool.size();
  }

  @Override
  public List<Block> getBlockByTime(long starttime, long endtime) {
    return blockStore.getBlocksUsedTime(starttime, endtime);
  }

  @Override
  public long getMainBlockSize() {
    return blockStore.getMainNumber();
  }

  @Override
  public long getBlockSize() {
    return blockStore.getBlockNumber();
  }

  private boolean canUseInput(Block block) {
    logger.debug("verifiedKeys【{}】 ", Hex.toHexString(block.getHash()));
    List<ECKey> ecKeys = block.verifiedKeys();
    List<Address> input = block.getInputs();
    if (input == null || input.size() == 0) {
      return true;
    }
    for (Address in : input) {
      boolean canUse = false;
      // 获取签名与hash
      Block inBlock = blockStore.getBlockByHash(in.getHashLow(), true);
      byte[] subdata = inBlock.getSubRawData(inBlock.getOutsigIndex() - 2);

      ECKey.ECDSASignature sig = inBlock.getOutsig();

      for (ECKey ecKey : ecKeys) {
        byte[] hash = Sha256Hash.hashTwice(BytesUtils.merge(subdata, ecKey.getPubKeybyCompress()));
        if (ecKey.verify(hash, sig)) {
          canUse = true;
        }
      }
      if (!canUse) {
        return false;
      }
    }
    return true;
  }

  public boolean checkMineAndAdd(Block block) {
    List<key_internal_item> ourkeys = wallet.getKey_internal();
    // 输出签名只有一个
    ECKey.ECDSASignature signature = block.getOutsig();
    // 遍历所有key
    for (int i = 0; i < ourkeys.size(); i++) {
      ECKey ecKey = ourkeys.get(i).ecKey;
      byte[] digest =
          BytesUtils.merge(block.getSubRawData(block.getOutsigIndex() - 2), ecKey.getPubKeybyCompress());
      byte[] hash = Sha256Hash.hashTwice(digest);
      if (ecKey.verify(hash, signature)) {
        logger.debug("Validate Success");
        addNewAccount(block, i);
        return true;
      }
    }
    return false;
  }

  public void addNewAccount(Block block, int keyIndex) {
    if (!block.isSaved()) {
      logger.debug("Add into Mem,size:" + MemAccount.size());
      //            MemAccount.put(new ByteArrayWrapper(block.getHashLow()),keyIndex);
      MemAccount.put(new ByteArrayWrapper(block.getHash()), keyIndex);
    } else {
      logger.debug("Add into storage");
      accountStore.addNewAccount(block, keyIndex);
    }
  }

  public void removeAccount(Block block) {
    if (!block.isSaved) {
      //            MemAccount.remove(new ByteArrayWrapper(block.getHashLow()));
      MemAccount.remove(new ByteArrayWrapper(block.getHash()));
    } else {
      accountStore.removeAccount(block);
    }
  }

  public void setTopDiff(BigInteger diff) {
    topDiff = diff;
  }

  public void setTopMainchain(Block block) {
    this.top_main_chain = block.getHashLow();
  }

  /** 根据当前区块数量计算奖励金额 cheato * */
  public long getCurrentReward() {
    return xdag2amount(1024);
  }

  /** 为区块block添加amount金额 * */
  private void acceptAmount(Block block, long amount) {
    block.setAmount(block.getAmount() + amount);
    blockStore.updateBlockInfo(BlockStore.BLOCK_AMOUNT, block);
    if ((block.flags & BI_OURS) != 0) {
      logger.debug(
          "====Our balance add new amount:" + amount + "====,获取到amount的hash【{}】",
          Hex.toHexString(block.getHashLow()));
      accountStore.updateGBanlance(amount);
    }
  }

  /** 判断是否已经接收过区块 * */
  public boolean isExist(byte[] hashlow) {
    if (MemOrphanPool.containsKey(new ByteArrayWrapper(hashlow)) || blockStore.hasBlock(hashlow)) {
      return true;
    }
    return false;
  }

  public AccountStore getAccountStore() {
    return accountStore;
  }

  @Override
  public List<Block> listMainBlocks(int count) {
    Block temp = getBlockByHash(top_main_chain, false);
    List<Block> res = new ArrayList<>();
    while (count > 0) {
      if (temp == null) {
        break;
      }
      if ((temp.getFlags() & BI_MAIN) != 0) {
        count--;
        res.add((Block) temp.clone());
        if (temp.getMaxDifflink() == null) {
          break;
        }
        temp = getBlockByHash(temp.getMaxDifflink().getHashLow(), false);
      } else {
        if (temp.getMaxDifflink() == null) {
          break;
        }
        temp = getBlockByHash(temp.getMaxDifflink().getHashLow(), false);
      }
    }
    return res;
  }

  @Override
  public List<Block> listMinedBlocks(int count) {
    Block temp = getBlockByHash(top_main_chain, false);
    List<Block> res = new ArrayList<>();
    while (count > 0) {
      if (temp == null) {
        break;
      }
      if ((temp.getFlags() & BI_MAIN) != 0 && (temp.getFlags() & BI_OURS) != 0) {
        count--;
        res.add((Block) temp.clone());
        if (temp.getMaxDifflink() == null) {
          break;
        }
        temp = getBlockByHash(temp.getMaxDifflink().getHashLow(), false);
      } else {
        if (temp.getMaxDifflink() == null) {
          break;
        }
        temp = getBlockByHash(temp.getMaxDifflink().getHashLow(), false);
      }
    }
    return res;
  }

  @Override
  public List<byte[]> getAllAccount() {
    List<byte[]> res = new ArrayList<>();
    //        List<byte[]> memAccount = getMemAcccount();
    List<byte[]> storeAccount = accountStore.getAllAccount();
    //        res.addAll(memAccount);
    res.addAll(storeAccount);
    return res;
  }

  @Override
  public Map<ByteArrayWrapper, Integer> getMemAccount() {
    return MemAccount;
  }

  @Override
  public ReentrantReadWriteLock getStateLock() {
    return stateLock;
  }

  @Override
  public Block getExtraBlock(byte[] hashlow) {
    return MemOrphanPool.getOrDefault(new ByteArrayWrapper(hashlow), null);
  }
}
