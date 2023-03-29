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

import io.xdag.db.AddressStore;
import io.xdag.utils.BytesUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt64;

/**
 * @author Dcj_Cory
 * @date 2022/10/31 1:43 PM
 */
@Slf4j
public class AddressStoreImpl implements AddressStore {
    private static final int AddressSize = 20;
    private final KVSource<byte[], byte[]> AddressSource;
    public static final byte ADDRESS_SIZE = 0x10;
    public static final byte AMOUNT_SUM = 0x20;

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

    public UInt64 getBalanceByAddress(byte[] Address){
        byte[] data = AddressSource.get(Address);
        if(data == null){
            log.debug("This pubkey don't exist");
            return UInt64.ZERO;
        }else {
            return UInt64.fromBytes(Bytes.wrap(data));
        }
    }

    public boolean addressIsExist(byte[] Address){
        return AddressSource.get(Address) != null;
    }

    public void addAddress(byte[] Address){
        AddressSource.put(Address,UInt64.ZERO.toBytes().toArray());
        long currentSize = BytesUtils.bytesToLong(AddressSource.get(new byte[]{ADDRESS_SIZE}),0,false);
        AddressSource.put(new byte[] {ADDRESS_SIZE},BytesUtils.longToBytes(currentSize + 1,false));
    }

    public UInt64 getAllBalance(){
        return UInt64.fromBytes(Bytes.wrap(AddressSource.get(new byte[]{AMOUNT_SUM})));
    }

    @Override
    public void saveAddressSize(byte[] addressSize) {
        AddressSource.put(new byte[]{ADDRESS_SIZE},addressSize);
    }

    @Override
    public void savaAmountSum(byte[] amountSum) {
        AddressSource.put(new byte[]{AMOUNT_SUM},amountSum);
    }

    public UInt64 getAddressSize(){
        return UInt64.fromBytes(Bytes.wrap(AddressSource.get(new byte[]{ADDRESS_SIZE})));
    }

    public void updateAllBalance(UInt64 value){
        AddressSource.put(new byte[]{AMOUNT_SUM},value.toBytes().toArray());
    }


    //TODO：计算上移到应用层
    public void updateBalance(byte[] address,UInt64 value){
        if(address.length != AddressSize){
            log.debug("The Address type is wrong");
            return;
        }
        if(AddressSource.get(address) == null){
            log.debug("This address don't exist");
            addAddress(address);
        }
        AddressSource.put(address,value.toBytes().toArray());
    }

    @Override
    public void saveAddress(byte[] address, byte[] balance) {
        AddressSource.put(address,balance);
    }

}
