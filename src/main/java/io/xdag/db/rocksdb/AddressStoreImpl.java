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

import io.xdag.core.XAmount;
import io.xdag.db.AddressStore;
import io.xdag.utils.BytesUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt64;


@Slf4j
public class AddressStoreImpl implements AddressStore {
    private static final int AddressSize = 20;
    private final KVSource<byte[], byte[]> AddressSource;


    //<addressHash,balance>
    public AddressStoreImpl(KVSource<byte[], byte[]> addressSource) {
        AddressSource = addressSource;
    }

    public void init() {
        this.AddressSource.init();
        if(AddressSource.get(new byte[]{ADDRESS_SIZE}) == null){
            AddressSource.put(new byte[]{ADDRESS_SIZE}, BytesUtils.longToBytes(0,false));
        }
        if(AddressSource.get(new byte[]{AMOUNT_SUM}) == null){
            AddressSource.put(new byte[]{AMOUNT_SUM},BytesUtils.longToBytes(0,false));
        }
    }

    public void reset() {
        this.AddressSource.reset();
        AddressSource.put(new byte[]{ADDRESS_SIZE}, BytesUtils.longToBytes(0,false));
        AddressSource.put(new byte[]{AMOUNT_SUM},BytesUtils.longToBytes(0,false));
    }

    public XAmount getBalanceByAddress(byte[] address){
        byte[] data = AddressSource.get(BytesUtils.merge(ADDRESS,address));
        if(data == null){
            log.debug("This pubkey don't exist");
            return XAmount.ZERO;
        } else {
            return XAmount.ofXAmount(UInt64.fromBytes(Bytes.wrap(data)).toLong());
        }
    }

    public boolean addressIsExist(byte[] address){
        return AddressSource.get(BytesUtils.merge(ADDRESS,address)) != null;
    }

    public void addAddress(byte[] address){
        AddressSource.put(BytesUtils.merge(ADDRESS,address), UInt64.ZERO.toBytes().toArray());
        long currentSize = BytesUtils.bytesToLong(AddressSource.get(new byte[]{ADDRESS_SIZE}),0,false);
        AddressSource.put(new byte[] {ADDRESS_SIZE},BytesUtils.longToBytes(currentSize + 1,false));
    }

    public XAmount getAllBalance(){
        UInt64 u64v = UInt64.fromBytes(Bytes.wrap(AddressSource.get(new byte[]{AMOUNT_SUM})));
        return XAmount.ofXAmount(u64v.toLong());
    }

    @Override
    public void saveAddressSize(byte[] addressSize) {
        AddressSource.put(new byte[]{ADDRESS_SIZE},addressSize);
    }

    @Override
    public void savaAmountSum(XAmount balanceSum) {
        UInt64 u64v = balanceSum.toXAmount();
        AddressSource.put(new byte[]{AMOUNT_SUM}, u64v.toBytes().toArray());
    }

    public UInt64 getAddressSize(){
        return UInt64.fromBytes(Bytes.wrap(AddressSource.get(new byte[]{ADDRESS_SIZE})));
    }

    public void updateAllBalance(XAmount balance){
        UInt64 u64V = balance.toXAmount();
        AddressSource.put(new byte[]{AMOUNT_SUM}, u64V.toBytes().toArray());
    }


    //TODO：计算上移到应用层
    public void updateBalance(byte[] address, XAmount balance){
        if(address.length != AddressSize){
            log.debug("The Address type is wrong");
            return;
        }
        if(AddressSource.get(BytesUtils.merge(ADDRESS,address)) == null){
            log.debug("This address don't exist");
            addAddress(address);
        }
        UInt64 u64V = balance.toXAmount();
        AddressSource.put((BytesUtils.merge(ADDRESS,address)), u64V.toBytes().toArray());
    }

    @Override
    public void snapshotAddress(byte[] address, XAmount balance) {
        UInt64 u64V = balance.toXAmount();
        AddressSource.put(BytesUtils.merge(ADDRESS,address), u64V.toBytes().toArray());
    }

}
