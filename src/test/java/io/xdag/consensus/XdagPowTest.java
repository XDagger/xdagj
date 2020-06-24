package io.xdag.consensus;

import static io.xdag.utils.FastByteComparisons.compareTo;

import java.io.IOException;

import org.junit.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.util.Arrays;
import org.spongycastle.util.encoders.Hex;

import io.xdag.core.Block;
import io.xdag.core.XdagBlock;
import io.xdag.core.XdagField;
import io.xdag.utils.XdagSha256Digest;

public class XdagPowTest {

	private static final Logger logger = LoggerFactory.getLogger(XdagPowTest.class);

	XdagSha256Digest currentTaskDigest;

	@Before
	public void init() {
		currentTaskDigest = new XdagSha256Digest();
	}

	public void onNewShareTest(XdagField[] shareInfo) {
		Block generateBlock = new Block(new XdagBlock());
		XdagField share = shareInfo[0];
		byte[] minHash = new byte[32];
		Arrays.fill(minHash, (byte) 0);
		byte[] hash = null;
		try {
			XdagSha256Digest digest = new XdagSha256Digest(currentTaskDigest);
			byte[] data = share.getData();
			data = Arrays.reverse(data);
			hash = digest.sha256Final(data);

			if (compareTo(hash, 0, 32, minHash, 0, 32) < 0) {
				minHash = hash;
				// minShare = share.getData();
				byte[] hashlow = new byte[32];
				System.arraycopy(minHash, 8, hashlow, 8, 24);
				// generateBlock.setNonce(minShare);
				generateBlock.setHash(minHash);
				generateBlock.setHashLow(hashlow);

				logger.debug("New MinHash :" + Hex.toHexString(minHash));
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}