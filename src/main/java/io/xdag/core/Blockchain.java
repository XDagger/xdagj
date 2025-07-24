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

package io.xdag.core;

import io.xdag.crypto.keys.ECKeyPair;
import io.xdag.listener.Listener;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt64;
import java.util.List;
import java.util.Map;

public interface Blockchain {

    // Get pre-seed for snapshot initialization
    byte[] getPreSeed();

    // Try to connect a new block to the blockchain
    ImportResult tryToConnect(Block block);

    // Create a new block with given parameters
    Block createNewBlock(
            Map<Address, ECKeyPair> pairs,
            List<Address> to,
            boolean mining,
            String remark,
            XAmount fee,
            UInt64 txNonce);

    // Get block by its hash
    Block getBlockByHash(Bytes32 hash, boolean isRaw);

    // Get block by its height
    Block getBlockByHeight(long height);

    // Check and update main chain
    void checkNewMain();

    // Get the latest main block number
    long getLatestMainBlockNumber();

    // Get list of main blocks with specified count
    List<Block> listMainBlocks(int count);

    // Get list of mined blocks with specified count
    List<Block> listMinedBlocks(int count);

    // Get memory blocks created by current node
    Map<Bytes, Integer> getMemOurBlocks();

    // Get XDAG network statistics
    XdagStats getXdagStats();

    // Get XDAG top status
    XdagTopStatus getXdagTopStatus();

    // Calculate reward for given main block number
    XAmount getReward(long nmain);

    // Calculate total supply at given main block number
    XAmount getSupply(long nmain);

    // Get blocks within specified time range
    List<Block> getBlocksByTime(long starttime, long endtime);

    // Start main chain check thread with given period
    void startCheckMain(long period);

    // Stop main chain check thread
    void stopCheckMain();

    // Register blockchain event listener
    void registerListener(Listener listener);

    // Get transaction history for given address
    List<TxHistory> getBlockTxHistoryByAddress(Bytes32 addressHashlow, int page, Object... parameters);

    // Get extended XDAG network statistics
    XdagExtStats getXdagExtStats();
}
