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
