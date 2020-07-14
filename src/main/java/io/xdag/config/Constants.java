package io.xdag.config;

import static io.xdag.config.Config.MainNet;

public class Constants {
    public static final long XDAG_TEST_ERA = 0x16900000000L;
    public static final long XDAG_MAIN_ERA = 0x16940000000L;

    public static final long XDAG_ERA = MainNet ? XDAG_MAIN_ERA : XDAG_TEST_ERA;

    public static final long XDAG_MAIN_CHAIN_PERIOD = (64 << 10);

    /** setmain设置区块为主块时标志该位 */
    public static final byte BI_MAIN = 0x01;
    /** 跟BI_MAIN差不多 不过BI_MAIN是确定的 BI_MAIN_CHAIN是还未确定的 */
    public static final byte BI_MAIN_CHAIN = 0x02;
    /** 区块被应用apply后可能会标志该标识位（因为有可能区块存在问题不过还是被指向了 但是会标示为拒绝状态） */
    public static final byte BI_APPLIED = 0x04;
    /** 区块应用apply过后会置该标识位 */
    public static final byte BI_MAIN_REF = 0x08;
    /** 从孤块链中移除 即有区块链接孤块的时候 将孤块置为BI_REF */
    public static final byte BI_REF = 0x10;
    /** 添加区块时如果该区块的签名可以用自身的公钥解 则说明该区块是自己的区块 */
    public static final byte BI_OURS = 0x20;
    /** 候补主块未持久化 */
    public static final byte BI_EXTRA = 0x40;

    public static final byte BI_REMARK = (byte) 0x80;

    public static final Long SEND_PERIOD = 10L;

    public static final int DNET_PKT_XDAG = 0x8B;
    public static final int BLOCK_HEAD_WORD = 0x3fca9e2b;

    public static final long REQUEST_BLOCKS_MAX_TIME = 1L << 20;
    public static final long REQUEST_WAIT = 64;

    public static final long MAX_ALLOWED_EXTRA = 65536;

    public static final String FUND_ADDRESS = "FQglVQtb60vQv2DOWEUL7yh3smtj7g1s";

    /** 每一轮的确认数是16 */
    public static final int CONFIRMATIONS_COUNT = 16;

    /** Apollo */
    public static final int MAIN_BIG_PERIOD_LOG = 21;

    public static final long MAIN_APOLLO_HEIGHT = 1017323;
    public static final long MAIN_APOLLO_TESTNET_HEIGHT = 196250;

    public static final long MAIN_START_AMOUNT = 1L << 42;
    public static final long MAIN_APOLLO_AMOUNT = 1L << 39;
}
