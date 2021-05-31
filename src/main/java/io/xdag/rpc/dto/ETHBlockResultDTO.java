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
package io.xdag.rpc.dto;

import lombok.Data;

import java.util.Collections;
import java.util.List;

import static io.xdag.rpc.utils.TypeConverter.toQuantityJsonHex;

@Data
public class ETHBlockResultDTO {
    private final String number; // QUANTITY - the block number. null when its pending block.
    private final String hash; // DATA, 32 Bytes - hash of the block. null when its pending block.
    private final String parentHash; // DATA, 32 Bytes - hash of the parent block.
    private final String sha3Uncles; // DATA, 32 Bytes - SHA3 of the uncles data in the block.
    private final String logsBloom; // DATA, 256 Bytes - the bloom filter for the logs of the block. null when its pending block.
    private final String transactionsRoot; // DATA, 32 Bytes - the root of the transaction trie of the block.
    private final String stateRoot; // DATA, 32 Bytes - the root of the final state trie of the block.
    private final String receiptsRoot; // DATA, 32 Bytes - the root of the receipts trie of the block.
    private final String miner; // DATA, 20 Bytes - the address of the beneficiary to whom the mining rewards were given.
    private final String difficulty; // QUANTITY - integer of the difficulty for this block.
    private final String totalDifficulty; // QUANTITY - integer of the total difficulty of the chain until this block.
    private final String extraData; // DATA - the "extra data" field of this block
    private final String size;//QUANTITY - integer the size of this block in bytes.
    private final String gasLimit;//: QUANTITY - the maximum gas allowed in this block.
    private final String gasUsed; // QUANTITY - the total used gas by all transactions in this block.
    private final String timestamp; //: QUANTITY - the unix timestamp for when the block was collated.
    private final List<Object> transactions; //: Collection - Collection of transaction objects, or 32 Bytes transaction hashes depending on the last given parameter.
    private final List<String> uncles; //: Collection - Collection of uncle hashes.
    private final String cumulativeDifficulty;


    public ETHBlockResultDTO(
            long timestamp,
            List<Object> transactions,
            List<String> uncles
            ) {
        this.number = toQuantityJsonHex(12) ;
        this.hash = "0x88b221a282a64df608a820bae740425e5f439d1c";
        this.parentHash = "0x88b221a282a64df608a820bae740425e5f439d1c";
        this.sha3Uncles = "0x88b221a282a64df608a820bae740425e5f439d1c";
        this.logsBloom =  null;
        this.transactionsRoot = "0x88b221a282a64df608a820bae740425e5f439d1c";
        this.stateRoot = "0x88b221a282a64df608a820bae740425e5f439d1c";
        this.receiptsRoot = "0x88b221a282a64df608a820bae740425e5f439d1c";
        this.miner =  null;

        this.difficulty = "0x0";
        this.totalDifficulty = "0x0";
        this.cumulativeDifficulty = "0x0";

        this.extraData = "0x";
        this.size = toQuantityJsonHex(234);
        this.gasLimit = toQuantityJsonHex(1233);
        this.gasUsed = toQuantityJsonHex(12);
        this.timestamp = toQuantityJsonHex(timestamp);

        this.transactions = Collections.unmodifiableList(transactions);
        this.uncles = Collections.unmodifiableList(uncles);

    }
}
