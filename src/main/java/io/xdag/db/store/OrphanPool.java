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
package io.xdag.db.store;

import java.util.ArrayList;
import java.util.List;

import org.spongycastle.util.encoders.Hex;

import io.xdag.core.Address;
import io.xdag.core.Block;
import io.xdag.core.XdagField;
import io.xdag.db.KVSource;
import io.xdag.utils.BytesUtils;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OrphanPool {
    public static final byte ORPHAN_PREFEX = 0x00;
    /** size key */
    private static final byte[] ORPHAN_SIZE = Hex.decode("FFFFFFFFFFFFFFFF");
    // <hash,nexthash>
    private KVSource<byte[], byte[]> orphanSource;

    public OrphanPool(KVSource<byte[], byte[]> orphan) {
        this.orphanSource = orphan;
    }

    public void init() {
        this.orphanSource.init();
        if (orphanSource.get(ORPHAN_SIZE) == null) {
            this.orphanSource.put(ORPHAN_SIZE, BytesUtils.longToBytes(0, false));
        }
    }

    public void reset() {
        this.orphanSource.reset();
        this.orphanSource.put(ORPHAN_SIZE, BytesUtils.longToBytes(0, false));
    }

    public List<Address> getOrphan(long num) {
        List<Address> res = new ArrayList<>();
        if (orphanSource.get(ORPHAN_SIZE) == null || getOrphanSize() == 0) {
            return null;
        } else {
            long orphanSize = getOrphanSize();
            long addNum = Math.min(orphanSize, num);
            byte[] key = BytesUtils.of(ORPHAN_PREFEX);
            List<byte[]> ans = orphanSource.prefixKeyLookup(key);
            for (byte[] an : ans) {
                if (addNum == 0) {
                    break;
                }
                // TODO:判断时间
                addNum--;
                res.add(new Address(BytesUtils.subArray(an, 1, 32), XdagField.FieldType.XDAG_FIELD_OUT));
            }
            return res;
        }
    }

    public void deleteByHash(byte[] hashlow) {
        log.debug("deleteByhash");
        orphanSource.delete(BytesUtils.merge(ORPHAN_PREFEX, hashlow));
        long currentsize = BytesUtils.bytesToLong(orphanSource.get(ORPHAN_SIZE), 0, false);
        orphanSource.put(ORPHAN_SIZE, BytesUtils.longToBytes(currentsize - 1, false));
    }

    public void addOrphan(Block block) {
        orphanSource.put(BytesUtils.merge(ORPHAN_PREFEX, block.getHashLow()), new byte[0]);
        long currentsize = BytesUtils.bytesToLong(orphanSource.get(ORPHAN_SIZE), 0, false);
        log.debug("orphan current size:" + currentsize);
        log.debug(":" + Hex.toHexString(orphanSource.get(ORPHAN_SIZE)));
        orphanSource.put(ORPHAN_SIZE, BytesUtils.longToBytes(currentsize + 1, false));
    }

    public long getOrphanSize() {
        long currentsize = BytesUtils.bytesToLong(orphanSource.get(ORPHAN_SIZE), 0, false);
        log.debug("current orphan size:" + currentsize);
        log.debug("Hex:" + Hex.toHexString(orphanSource.get(ORPHAN_SIZE)));
        return currentsize;
    }

    public boolean containsKey(byte[] hashlow) {
        return orphanSource.get(BytesUtils.merge(ORPHAN_PREFEX, hashlow)) != null;
    }
}
