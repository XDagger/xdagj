package io.xdag.mine.manager;

import java.net.InetSocketAddress;
import java.util.Map;

import io.xdag.consensus.PoW;
import io.xdag.consensus.Task;
import io.xdag.mine.MinerChannel;
import io.xdag.mine.miner.Miner;
import io.xdag.net.message.Message;
import io.xdag.utils.ByteArrayWrapper;

public interface MinerManager {
	void updateNewTaskandBroadcast(Task task);

	Map<ByteArrayWrapper, Miner> getActivateMiners();

	/** 接收到share */
	void onNewShare(MinerChannel channel, Message msg);

	void setPoW(PoW pow);

	void start();

	void addActivateChannel(MinerChannel channel);

	void close();

	MinerChannel getChannelByHost(InetSocketAddress host);

	Map<InetSocketAddress, MinerChannel> getActivateMinerChannels();

	void removeUnactivateChannel(MinerChannel channel);
}
