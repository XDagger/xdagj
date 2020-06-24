package io.xdag.mine.manager;

import io.xdag.consensus.Task;
import io.xdag.mine.miner.Miner;

/**
 * @ClassName AwardManager
 * @Description
 * @Author punk
 * @Date 2020/5/31 14:56
 * @Version V1.0
 **/
public interface AwardManager {

    /**
     * 根据地址块的hash 设置矿池自身对象
     * @param hash 地址块hash
     */
    void setPoolMiner(byte[] hash);

    /** 获取poolminer */
    Miner getPoolMiner();

    /** 接受到一个新的任务*/
    void onNewTask(Task task);

    /** 挖矿收益支付及沈城交易块*/
    void payAndaddNewAwardBlock(byte[] share, byte[] hash,long generateTime);

}
