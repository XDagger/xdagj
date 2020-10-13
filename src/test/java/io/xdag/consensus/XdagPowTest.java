/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2020-2030 The XdagJ Developers
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package io.xdag.consensus;

import io.xdag.core.Block;
import io.xdag.core.XdagBlock;
import io.xdag.core.XdagField;
import io.xdag.utils.XdagSha256Digest;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.spongycastle.util.Arrays;
import org.spongycastle.util.encoders.Hex;

import java.io.IOException;

import static io.xdag.utils.FastByteComparisons.compareTo;

@Slf4j
public class XdagPowTest {
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
                generateBlock.getInfo().setHash(minHash);
                generateBlock.getInfo().setHashlow(hashlow);
                log.debug("New MinHash :" + Hex.toHexString(minHash));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
