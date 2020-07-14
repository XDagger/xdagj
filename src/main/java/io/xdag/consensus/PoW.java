package io.xdag.consensus;

import io.xdag.mine.MinerChannel;
import io.xdag.net.message.Message;

public interface PoW {

    /** Start thread */
    void start();

    /** Stop thread */
    void stop();

    /**
     * is running
     *
     * @return .
     */
    boolean isRunning();

    /**
     * Receive and process shares sent by miners
     *
     * @param channel
     *            minerchannel
     * @param msg
     *            share
     */
    void receiveNewShare(MinerChannel channel, Message msg);

    void receiveNewPretop(byte[] pretop);
}
