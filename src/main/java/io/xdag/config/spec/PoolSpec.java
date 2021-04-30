package io.xdag.config.spec;

/**
 * The Mining Pool Specifications
 */
public interface PoolSpec {

    String getPoolIp();
    int getPoolPort();
    String getPoolTag();
    int getGlobalMinerLimit();
    int getGlobalMinerChannelLimit();
    int getMaxConnectPerIp();

    /** 拥有相同地址块的矿工最多允许同时在线的数量 g_connections_per_miner_limit */
    int getMaxMinerPerAccount();
    int getMaxShareCountPerChannel();

    int getConnectionTimeout();


    /** 矿池自己的收益占比 */
    double getPoolRation();

    /** 出块矿工收益占比 */
    double getRewardRation();

    /** 基金会收益占比 */
    double getFundRation();

    /** 参与奖励的占比 */
    double getDirectRation();

    /** 奖励支付的周期 */
    int getAwardEpoch();

    /** 等待超过10个epoch默认启动挖矿 */
    int getWaitEpoch();

}
