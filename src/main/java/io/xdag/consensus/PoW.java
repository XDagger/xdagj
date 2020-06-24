package io.xdag.consensus;

import io.xdag.mine.MinerChannel;
import io.xdag.net.message.Message;

public interface PoW {

    void start();

    void stop();

    boolean isRunning();

    void receiveNewShare(MinerChannel channel, Message msg);

    void receiveNewPretop(byte[] pretop);
}
