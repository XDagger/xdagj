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
package io.xdag.db;

import io.xdag.core.XdagLifecycle;
import org.apache.tuweni.units.bigints.UInt64;

import io.xdag.core.XAmount;

public interface AddressStore extends XdagLifecycle {

    byte ADDRESS_SIZE = (byte) 0x10;
    byte AMOUNT_SUM = (byte) 0x20;
    byte ADDRESS = (byte) 0x30;
    byte CURRENT_TRANSACTION_QUANTITY = (byte) 0x40;
    byte EXECUTED_NONCE_NUM = (byte) 0x50;

    void reset();

    XAmount getBalanceByAddress(byte[] Address);

    boolean addressIsExist(byte[] Address);

    void addAddress(byte[] Address);

    XAmount getAllBalance();

    void saveAddressSize(byte[] addressSize);

    void saveAmountSum(XAmount balanceSum);

    void updateAllBalance(XAmount balance);

    UInt64 getAddressSize();

    void updateBalance(byte[] address, XAmount balance);

    void snapshotAddress(byte[] address, XAmount balance);

    void snapshotTxQuantity(byte[] address, UInt64 txQuantity);

    void snapshotExeTxNonceNum(byte[] address, UInt64 exeTxNonceNum);

    UInt64 getTxQuantity(byte[] address);

    void updateTxQuantity(byte[] address, UInt64 newTxQuantity);

    void updateTxQuantity(byte[] address, UInt64 currentTxNonce, UInt64 currentExeNonce);

    UInt64 getExecutedNonceNum(byte[] address);

    void updateExcutedNonceNum(byte[] address,boolean addOrSubstract);
}
