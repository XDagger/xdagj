package io.xdag.net.node;

import java.math.BigInteger;
import java.util.concurrent.atomic.AtomicLong;

public class NodeStat {

	private BigInteger xdagTotalDifficulty;

	private boolean isPredefined = false;

	public class StatHandler {
		AtomicLong count = new AtomicLong(0);

		public void add() {
			count.incrementAndGet();
		}

		public void add(long delta) {
			count.addAndGet(delta);
		}

		public long get() {
			return count.get();
		}

		@Override
		public String toString() {
			return count.toString();
		}
	}

	public final StatHandler Inbound = new StatHandler();
	public final StatHandler Outbound = new StatHandler();

	public void disconnected() {
		System.currentTimeMillis();
	}

	public boolean isPredefined() {
		return isPredefined;
	}

	public BigInteger getXdagTotalDifficulty() {
		return xdagTotalDifficulty;
	}
}
