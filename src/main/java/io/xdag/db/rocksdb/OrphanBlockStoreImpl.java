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

package io.xdag.db.rocksdb;

import io.xdag.core.Address;
import io.xdag.core.Block;
import io.xdag.core.Filter;
import io.xdag.core.XdagField;
import io.xdag.db.OrphanBlockStore;
import io.xdag.utils.BytesUtils;
import java.util.Comparator;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes32;
import org.bouncycastle.util.encoders.Hex;

import com.google.common.collect.Lists;

@Slf4j
public class OrphanBlockStoreImpl implements OrphanBlockStore {


    // <hash,nexthash>
    private final KVSource<byte[], byte[]> orphanSource;

    public OrphanBlockStoreImpl(KVSource<byte[], byte[]> orphan) {
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

    public List<Address> getOrphan(long num, long[] sendtime, Filter filter) {
        List<Address> res = Lists.newArrayList();
        if (orphanSource.get(ORPHAN_SIZE) == null || getOrphanSize() == 0) {
            return null;
        } else {
            long orphanSize = getOrphanSize();
            long addNum = Math.min(orphanSize, num);
            byte[] key = BytesUtils.of(ORPHAN_PREFEX);
            List<Pair<byte[],byte[]>> ans = orphanSource.prefixKeyAndValueLookup(key);
            ans.sort(Comparator.comparingLong(a -> BytesUtils.bytesToLong(a.getValue(), 0, true)));
            for (Pair<byte[],byte[]> an : ans) {
                if (addNum == 0) {
                    break;
                }
                // TODO:判断时间，这里出现过orphanSource获取key时为空的情况
                if (an.getValue() == null) {
                    continue;
                }
                long time =  BytesUtils.bytesToLong(an.getValue(), 0, true);
                if (time <= sendtime[0]) {
                    Bytes32 blockHashLow = Bytes32.wrap(an.getKey(),1);
                    if(filter.filterOurLinkBlock(blockHashLow)){
                        addNum--;
                        //TODO:通过address 获取区块 遍历连接块是否都是output如果是 则为链接块 判断是否是自己的是才链接
                        res.add(new Address(blockHashLow, XdagField.FieldType.XDAG_FIELD_OUT,false));
                        sendtime[1] = Math.max(sendtime[1],time);
                    }
                }
            }
            sendtime[1] = Math.min(sendtime[1]+1,sendtime[0]);
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
        orphanSource.put(BytesUtils.merge(ORPHAN_PREFEX, block.getHashLow().toArray()),
                BytesUtils.longToBytes(block.getTimestamp(), true));
        long currentsize = BytesUtils.bytesToLong(orphanSource.get(ORPHAN_SIZE), 0, false);
        log.debug("orphan current size:" + currentsize);
//        log.debug(":" + Hex.toHexString(orphanSource.get(ORPHAN_SIZE)));
        orphanSource.put(ORPHAN_SIZE, BytesUtils.longToBytes(currentsize + 1, false));
    }

    public long getOrphanSize() {
        long currentsize = BytesUtils.bytesToLong(orphanSource.get(ORPHAN_SIZE), 0, false);
        log.debug("current orphan size:" + currentsize);
        log.debug("Hex:" + Hex.toHexString(orphanSource.get(ORPHAN_SIZE)));
        return currentsize;
    }

}
