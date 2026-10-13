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
    private static final int ADDRESS_SIZE = 20; // Corrected constant name to uppercase
    private final KVSource<byte[], byte[]> addressSource; // Renamed for clarity

    // Constructor to initialize address source
    public AddressStoreImpl(KVSource<byte[], byte[]> addressSource) {
        this.addressSource = addressSource;
    }

    public void start() {
        this.addressSource.init();
        if (addressSource.get(new byte[]{ADDRESS_SIZE}) == null) {
            addressSource.put(new byte[]{ADDRESS_SIZE}, BytesUtils.longToBytes(0, false));
        }
        if (addressSource.get(new byte[]{AMOUNT_SUM}) == null) {
            addressSource.put(new byte[]{AMOUNT_SUM}, BytesUtils.longToBytes(0, false));
        }
    }

    @Override
    public void stop() {
        addressSource.close();
    }

    @Override
    public boolean isRunning() {
        return false;
    }

    public void reset() {
        this.addressSource.reset();
        addressSource.put(new byte[]{ADDRESS_SIZE}, BytesUtils.longToBytes(0, false));
        addressSource.put(new byte[]{AMOUNT_SUM}, BytesUtils.longToBytes(0, false));
    }

    public XAmount getBalanceByAddress(byte[] address) {
        byte[] data = addressSource.get(BytesUtils.merge(ADDRESS, address));
        if (data == null) {
            log.debug("This public key doesn't exist");
            return XAmount.ZERO;
        } else {
            return XAmount.ofXAmount(UInt64.fromBytes(Bytes.wrap(data)).toLong());
        }
    }

    public boolean addressIsExist(byte[] address) {
        return addressSource.get(BytesUtils.merge(ADDRESS, address)) != null;
    }

    public void addAddress(byte[] address) {
        addressSource.put(BytesUtils.merge(ADDRESS, address), UInt64.ZERO.toBytes().toArray());
        long currentSize = BytesUtils.bytesToLong(addressSource.get(new byte[]{ADDRESS_SIZE}), 0, false);
        addressSource.put(new byte[]{ADDRESS_SIZE}, BytesUtils.longToBytes(currentSize + 1, false));
    }

    public XAmount getAllBalance() {
        UInt64 u64v = UInt64.fromBytes(Bytes.wrap(addressSource.get(new byte[]{AMOUNT_SUM})));
        return XAmount.ofXAmount(u64v.toLong());
    }

    @Override
    public void saveAddressSize(byte[] addressSize) {
        addressSource.put(new byte[]{ADDRESS_SIZE}, addressSize);
    }

    @Override
    public void saveAmountSum(XAmount balanceSum) { // Fixed typo in method name
        UInt64 u64v = balanceSum.toXAmount();
        addressSource.put(new byte[]{AMOUNT_SUM}, u64v.toBytes().toArray());
    }

    public UInt64 getAddressSize() {
        return UInt64.fromBytes(Bytes.wrap(addressSource.get(new byte[]{ADDRESS_SIZE})));
    }

    public void updateAllBalance(XAmount balance) {
        UInt64 u64V = balance.toXAmount();
        addressSource.put(new byte[]{AMOUNT_SUM}, u64V.toBytes().toArray());
    }

    // TODO: Move calculation to application layer
    public void updateBalance(byte[] address, XAmount balance) {
        if (address.length != ADDRESS_SIZE) {
            log.debug("The address type is wrong");
            return;
        }
        if (addressSource.get(BytesUtils.merge(ADDRESS, address)) == null) {
            log.debug("This address doesn't exist");
            addAddress(address);
        }
        UInt64 u64V = balance.toXAmount();
        addressSource.put(BytesUtils.merge(ADDRESS, address), u64V.toBytes().toArray());
    }

    @Override
    public void snapshotAddress(byte[] address, XAmount balance) {
        UInt64 u64V = balance.toXAmount();
        addressSource.put(address, u64V.toBytes().toArray());
    }

    @Override
    public void snapshotTxQuantity(byte[] address, UInt64 txQuantity) {
        addressSource.put(address, txQuantity.toBytes().toArray());
    }

    @Override
    public void snapshotExeTxNonceNum(byte[] address, UInt64 exeTxNonceNum) {
        addressSource.put(address, exeTxNonceNum.toBytes().toArray());
    }

    @Override
    public UInt64 getTxQuantity(byte[] address) {
        byte[] key = BytesUtils.merge(CURRENT_TRANSACTION_QUANTITY, address);
        byte[] txQuantity = addressSource.get(key);

        if (txQuantity == null) {
            return UInt64.ZERO;
        } else {
            return UInt64.fromBytes(Bytes.wrap(txQuantity));
        }
    }

    @Override
    public void updateTxQuantity(byte[] address, UInt64 newTxQuantity) {
        byte[] key = BytesUtils.merge(CURRENT_TRANSACTION_QUANTITY, address);
        addressSource.put(key,newTxQuantity.toBytes().toArray());
    }

    @Override
    public void updateTxQuantity(byte[] address, UInt64 currentTxNonce, UInt64 currentExeNonce) {
        UInt64 txNonce = currentTxNonce.toLong() >= currentExeNonce.toLong() ? currentTxNonce : currentExeNonce;
        byte[] key = BytesUtils.merge(CURRENT_TRANSACTION_QUANTITY, address);
        addressSource.put(key,txNonce.toBytes().toArray());
    }

    @Override
    public UInt64 getExecutedNonceNum(byte[] address) {
        byte[] key = BytesUtils.merge(EXECUTED_NONCE_NUM, address);
        byte[] processedTxNonce = addressSource.get(key);
        if(processedTxNonce == null){
            addressSource.put(key,UInt64.ZERO.toBytes().toArray());
            return UInt64.ZERO;
        } else {
            return UInt64.fromBytes(Bytes.wrap(processedTxNonce));
        }
    }

    @Override
    public void updateExcutedNonceNum(byte[] address, boolean addOrSubstract) {
        byte[] key = BytesUtils.merge(EXECUTED_NONCE_NUM, address);
        UInt64 before = getExecutedNonceNum(address);
        UInt64 now;
        if (addOrSubstract) {
            now = before.add(UInt64.ONE);
        }else {
            if (before.compareTo(UInt64.ZERO) == 0) {
                now = UInt64.ZERO;
            }else {
                now = before.subtract(UInt64.ONE);
            }
        }
        addressSource.put(key,now.toBytes().toArray());
    }
}
