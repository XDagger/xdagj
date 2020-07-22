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

import static io.xdag.utils.FastByteComparisons.equalBytes;

import io.xdag.core.Address;
import io.xdag.core.Block;
import io.xdag.core.XdagField;
import io.xdag.db.KVSource;
import io.xdag.utils.BytesUtils;
import java.util.ArrayList;
import java.util.List;
import org.spongycastle.util.encoders.Hex;

public class OrphanPool2 {
    public static final byte ORPHAN_PREV_PREFEX = 0x00;
    public static final byte ORPHAN_NEXT_PREFEX = 0x01;
    /** size key */
    private static final byte[] ORPHAN_SIZE = Hex.decode("FFFFFFFFFFFFFFFF");
    /** size key */
    private static final byte[] ORPHAN_FIRST = Hex.decode("EEEEEEEEEEEEEEEE");
    /** size key */
    private static final byte[] ORPHAN_LAST = Hex.decode("DDDDDDDDDDDDDDDD");
    private KVSource<byte[], byte[]> orphanSource;

    public OrphanPool2(KVSource<byte[], byte[]> orphan) {
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

    public List<Address> getOrphan(int num, long time) {
        List<Address> res = new ArrayList<>();
        if (orphanSource.get(ORPHAN_SIZE) == null
                || BytesUtils.bytesToLong(orphanSource.get(ORPHAN_SIZE), 0, false) == 0) {
            return null;
        } else {
            int orphanSize = BytesUtils.bytesToInt(orphanSource.get(ORPHAN_SIZE), 0, false);
            int addNum = Math.min(orphanSize, num);
            byte[] key = orphanSource.get(ORPHAN_FIRST);

            while (addNum > 0) {
                // TODO:判断时间
                key = orphanSource.get(BytesUtils.merge(ORPHAN_NEXT_PREFEX, key));
                res.add(new Address(key, XdagField.FieldType.XDAG_FIELD_OUT));
                addNum--;
            }
            return res;
        }
    }

    public void deleteByHash(byte[] hashlow) {
        long currentsize = BytesUtils.bytesToLong(orphanSource.get(ORPHAN_SIZE), 0, false);
        orphanSource.put(ORPHAN_SIZE, BytesUtils.longToBytes(currentsize - 1, false));
        if (equalBytes(orphanSource.get(ORPHAN_FIRST), hashlow)
                && equalBytes(orphanSource.get(ORPHAN_LAST), hashlow)) {
            orphanSource.delete(ORPHAN_LAST);
            orphanSource.delete(ORPHAN_FIRST);
            return;
        }

        if (equalBytes(orphanSource.get(ORPHAN_FIRST), hashlow)) {
            byte[] next = orphanSource.get(BytesUtils.merge(ORPHAN_NEXT_PREFEX, hashlow));
            orphanSource.put(ORPHAN_FIRST, next);
            orphanSource.delete(BytesUtils.merge(ORPHAN_NEXT_PREFEX, hashlow));
            return;
        }

        if (equalBytes(orphanSource.get(ORPHAN_LAST), hashlow)) {
            byte[] prev = orphanSource.get(BytesUtils.merge(ORPHAN_PREV_PREFEX, hashlow));
            orphanSource.put(ORPHAN_LAST, prev);
            orphanSource.delete(BytesUtils.merge(ORPHAN_NEXT_PREFEX, hashlow));
            return;
        }

        byte[] prev = orphanSource.get(BytesUtils.merge(ORPHAN_PREV_PREFEX, hashlow));
        byte[] next = orphanSource.get(BytesUtils.merge(ORPHAN_NEXT_PREFEX, hashlow));

        orphanSource.delete(BytesUtils.merge(ORPHAN_PREV_PREFEX, hashlow));
        orphanSource.delete(BytesUtils.merge(ORPHAN_NEXT_PREFEX, hashlow));

        orphanSource.put(BytesUtils.merge(ORPHAN_NEXT_PREFEX, prev), next);
        orphanSource.put(BytesUtils.merge(ORPHAN_PREV_PREFEX, next), prev);
    }

    public synchronized void addOrphan(Block block) {

        if (orphanSource.get(ORPHAN_FIRST) == null || orphanSource.get(ORPHAN_LAST) == null) {
            orphanSource.put(ORPHAN_FIRST, block.getHashLow());
            orphanSource.put(ORPHAN_LAST, block.getHashLow());
            return;
        }

        byte[] last = orphanSource.get(ORPHAN_LAST);
        orphanSource.put(BytesUtils.merge(ORPHAN_NEXT_PREFEX, last), block.getHashLow());
        orphanSource.put(BytesUtils.merge(ORPHAN_PREV_PREFEX, block.getHashLow()), last);
        orphanSource.put(ORPHAN_LAST, block.getHashLow());

        long currentsize = BytesUtils.bytesToLong(orphanSource.get(ORPHAN_SIZE), 0, false);
        orphanSource.put(ORPHAN_SIZE, BytesUtils.longToBytes(currentsize + 1, false));
    }

    public long getOrphanSize() {
        long currentsize = BytesUtils.bytesToLong(orphanSource.get(ORPHAN_SIZE), 0, false);
        return currentsize;
    }
}
// private static final byte[] ORPHAN_SIZE =
// Hex.decode("FFFFFFFFFFFFFFFF");//size key
// private static final byte[] ORPHAN_FIRST =
// Hex.decode("EEEEEEEEEEEEEEEE");//size key
// private static final byte[] ORPHAN_LAST =
// Hex.decode("DDDDDDDDDDDDDDDD");//size key
// public static final byte ORPHAN_PREV_PREFEX = 0x00;
// public static final byte ORPHAN_NEXT_PREFEX = 0x01;
//
// private KVSource<byte[],byte[]> orphanSource;
//
// public OrphanPool(KVSource<byte[],byte[]> orphan){
// this.orphanSource = orphan;
// }
//
// public void init(){
// this.orphanSource.init();
// if(orphanSource.get(ORPHAN_SIZE)==null){
// this.orphanSource.put(ORPHAN_SIZE, BytesUtils.longToBytes(0,false));
// }
// }
//
// public void reset(){
// this.orphanSource.reset();
// this.orphanSource.put(ORPHAN_SIZE,BytesUtils.longToBytes(0,false));
// }
// /*
// * @Author punk
// * @Description 获取orphan块hash进行引用
// * @Date 2020/4/21
// * @Param [hash]
// * @return java.util.List<io.xdag.core.Address>
// **/
// public List<Address> getOrphan(int num){
// List<Address> res = new ArrayList<>();
//
// if(orphanSource.get(ORPHAN_SIZE)==null||BytesUtils.bytesToLong(orphanSource.get(ORPHAN_SIZE),0,false)==0){
// return null;
// }else {
// int orphanSize =
// BytesUtils.bytesToInt(orphanSource.get(ORPHAN_SIZE),0,false);
// int addNum = Math.min(orphanSize, num);
// byte[] key = orphanSource.get(ORPHAN_FIRST);
//
// while (addNum>0){
// //TODO:判断时间
// key = orphanSource.get(BytesUtils.merge(ORPHAN_NEXT_PREFEX,key));
// res.add(new Address(key, XdagField.FieldType.XDAG_FIELD_OUT));
// addNum--;
// }
// return res;
// }
// }
//
// public void deleteByHash(byte[] hashlow){
// long currentsize = BytesUtils.bytesToLong(orphanSource.get(ORPHAN_SIZE), 0,
// false);
// orphanSource.put(ORPHAN_SIZE,BytesUtils.longToBytes(currentsize-1,false));
// if(equalBytes(orphanSource.get(ORPHAN_FIRST),hashlow) &&
// equalBytes(orphanSource.get(ORPHAN_LAST),hashlow)){
// orphanSource.delete(ORPHAN_LAST);
// orphanSource.delete(ORPHAN_FIRST);
// return;
// }
//
// if(equalBytes(orphanSource.get(ORPHAN_FIRST),hashlow)){
// byte[] next = orphanSource.get(BytesUtils.merge(ORPHAN_NEXT_PREFEX,hashlow));
// orphanSource.put(ORPHAN_FIRST,next);
// orphanSource.delete(BytesUtils.merge(ORPHAN_NEXT_PREFEX,hashlow));
// return;
// }
//
// if(equalBytes(orphanSource.get(ORPHAN_LAST),hashlow)){
// byte[] prev = orphanSource.get(BytesUtils.merge(ORPHAN_PREV_PREFEX,hashlow));
// orphanSource.put(ORPHAN_LAST,prev);
// orphanSource.delete(BytesUtils.merge(ORPHAN_NEXT_PREFEX,hashlow));
// return;
// }
//
// byte[] prev = orphanSource.get(BytesUtils.merge(ORPHAN_PREV_PREFEX,hashlow));
// byte[] next = orphanSource.get(BytesUtils.merge(ORPHAN_NEXT_PREFEX,hashlow));
//
// orphanSource.delete(BytesUtils.merge(ORPHAN_PREV_PREFEX,hashlow));
// orphanSource.delete(BytesUtils.merge(ORPHAN_NEXT_PREFEX,hashlow));
//
// orphanSource.put(BytesUtils.merge(ORPHAN_NEXT_PREFEX,prev),next);
// orphanSource.put(BytesUtils.merge(ORPHAN_PREV_PREFEX,next),prev);
// }
//
// public synchronized void addOrphan(Block block){
//
// if(orphanSource.get(ORPHAN_FIRST) == null && orphanSource.get(ORPHAN_LAST) ==
// null){
// orphanSource.put(ORPHAN_FIRST,block.getHashLow());
// orphanSource.put(ORPHAN_LAST,block.getHashLow());
// return;
// }
//
// byte[] last = orphanSource.get(ORPHAN_LAST);
// orphanSource.put(BytesUtils.merge(ORPHAN_NEXT_PREFEX,last),block.getHashLow());
// orphanSource.put(BytesUtils.merge(ORPHAN_PREV_PREFEX,block.getHashLow()),last);
// orphanSource.put(ORPHAN_LAST,block.getHashLow());
//
// long currentsize = BytesUtils.bytesToLong(orphanSource.get(ORPHAN_SIZE), 0,
// false);
// orphanSource.put(ORPHAN_SIZE,BytesUtils.longToBytes(currentsize+1,false));
//
// }
//
// public long getOrphanSize(){
// long currentsize = BytesUtils.bytesToLong(orphanSource.get(ORPHAN_SIZE), 0,
// false);
// return currentsize;
// }
//
// public boolean containsKey(byte[] hashlow){
// if(orphanSource.get(BytesUtils.merge(ORPHAN_PREV_PREFEX,hashlow))!=null){
// return true;
// }
// return false;
// }
