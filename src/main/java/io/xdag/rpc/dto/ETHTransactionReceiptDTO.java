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

import io.xdag.core.Block;

import static io.xdag.rpc.utils.TypeConverter.toQuantityJsonHex;
import static io.xdag.rpc.utils.TypeConverter.toUnformattedJsonHex;
import static io.xdag.utils.BytesUtils.EMPTY_BYTE_ARRAY;

public class ETHTransactionReceiptDTO {

    public static final int BLOOM_BYTES = 256;
    private byte[] data = new byte[BLOOM_BYTES];
    protected static final byte[] FAILED_STATUS = EMPTY_BYTE_ARRAY;
    protected static final byte[] PENDING_STATUS = new byte[]{0x02};
    protected static final byte[] SUCCESS_STATUS = new byte[]{0x01};


    private String transactionHash;      // hash of the transaction.
    private String transactionIndex;     // integer of the transactions index position in the block.
    private String blockHash;            // hash of the block where this transaction was in.
    private String blockNumber;          // block number where this transaction was in.
    private String cumulativeGasUsed;    // The total amount of gas used when this transaction was executed in the block.
    private String gasUsed;              // The amount of gas used by this specific transaction alone.
    private String contractAddress;      // The contract address created, if the transaction was a contract creation, otherwise  null .
    private String from;                 // address of the sender.
    private String to;                   // address of the receiver. null when it's a contract creation transaction.
    private String status;               // either 1 (success) or 0 (failure)

    // TODO : ignore
    private String[] logs;     // Array of log objects, which this transaction generated.
    private String logsBloom;            // Bloom filter for light clients to quickly retrieve related logs.

    public ETHTransactionReceiptDTO(Block block) {

        status = toQuantityJsonHex(SUCCESS_STATUS);

        blockHash = toUnformattedJsonHex(block.getHash());
        blockNumber = toQuantityJsonHex(19);


        cumulativeGasUsed = toQuantityJsonHex(123);
        from = "0x88b221a282a64df608a820bae740425e5f439d1c";
        gasUsed = toQuantityJsonHex(123);

        logs = new String[0];

        to = "0x88b221a282a64df608a820bae740425e5f439d1c";
        transactionHash = "0x647edff965606eb655103f6dee0c3b2805a56c40b3592eb7a8d011552d1e1839";
        transactionIndex = toQuantityJsonHex(1);
        logsBloom = toUnformattedJsonHex(data);
    }

}
