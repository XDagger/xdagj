package io.xdag.net.handler;

import java.math.BigInteger;

import com.google.common.util.concurrent.ListenableFuture;

import io.xdag.core.Block;
import io.xdag.net.XdagVersion;
import io.xdag.net.message.Message;
import io.xdag.net.message.impl.SumReplyMessage;

public class XdagAdapter implements Xdag {
	@Override
	public void sendNewBlock(Block newBlock, int TTl) {
		// TODO Auto-generated method stub

	}

	@Override
	public void sendGetblocks(long starttime, long endtime) {
		// TODO Auto-generated method stub
	}

	@Override
	public void sendGetblock(byte[] hash) {
		// TODO Auto-generated method stub

	}

	@Override
	public ListenableFuture<SumReplyMessage> sendGetsums(long starttime, long endtime) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void dropConnection() {
		// TODO Auto-generated method stub

	}

	@Override
	public boolean isIdle() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public BigInteger getTotalDifficulty() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void activate() {
		// TODO Auto-generated method stub

	}

	@Override
	public XdagVersion getVersion() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void disableBlocks() {
		// TODO Auto-generated method stub

	}

	@Override
	public void enableBlocks() {
		// TODO Auto-generated method stub

	}

	@Override
	public void onSyncDone(boolean done) {
		// TODO Auto-generated method stub

	}

	@Override
	public void sendMessage(Message message) {

	}

}
