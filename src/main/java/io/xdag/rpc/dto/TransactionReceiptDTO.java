//package io.xdag.rpc.dto;
//
//import io.xdag.core.Block;
//
//import static io.xdag.rpc.utils.TypeConverter.toUnformattedJsonHex;
//
//public class TransactionReceiptDTO {
//    private String transactionHash;      // hash of the transaction.
//    private String transactionIndex;     // integer of the transactions index position in the block.
//    private String blockHash;            // hash of the block where this transaction was in.
//    private String blockNumber;          // block number where this transaction was in.
//    private String cumulativeGasUsed;    // The total amount of gas used when this transaction was executed in the block.
//    private String gasUsed;              // The amount of gas used by this specific transaction alone.
//    private String contractAddress;      // The contract address created, if the transaction was a contract creation, otherwise  null .
//    private String from;                 // address of the sender.
//    private String to;                   // address of the receiver. null when it's a contract creation transaction.
//    private String status;               // either 1 (success) or 0 (failure)
//    private String logsBloom;            // Bloom filter for light clients to quickly retrieve related logs.
//
//    public TransactionReceiptDTO(Block block) {
//        TransactionReceipt receipt = txInfo.getReceipt();
//
//        status = toQuantityJsonHex(txInfo.getReceipt().getStatus());
//        blockHash = toUnformattedJsonHex(block.getHash());
//        blockNumber = toQuantityJsonHex(block.getNumber());
//
//        RskAddress contractAddress = receipt.getTransaction().getContractAddress();
//        if (contractAddress != null) {
//            this.contractAddress = contractAddress.toJsonString();
//        }
//
//        cumulativeGasUsed = toQuantityJsonHex(receipt.getCumulativeGas());
//        from = receipt.getTransaction().getSender().toJsonString();
//        gasUsed = toQuantityJsonHex(receipt.getGasUsed());
//
//        logs = new LogFilterElement[receipt.getLogInfoList().size()];
//        for (int i = 0; i < logs.length; i++) {
//            LogInfo logInfo = receipt.getLogInfoList().get(i);
//            logs[i] = new LogFilterElement(logInfo, block, txInfo.getIndex(),
//                    txInfo.getReceipt().getTransaction(), i);
//        }
//
//        to = receipt.getTransaction().getReceiveAddress().toJsonString();
//        transactionHash = receipt.getTransaction().getHash().toJsonString();
//        transactionIndex = toQuantityJsonHex(txInfo.getIndex());
//        logsBloom = toUnformattedJsonHex(txInfo.getReceipt().getBloomFilter().getData());
//    }
//
//    public String getTransactionHash() {
//        return transactionHash;
//    }
//
//    public String getTransactionIndex() {
//        return transactionIndex;
//    }
//
//    public String getBlockHash() {
//        return blockHash;
//    }
//
//    public String getBlockNumber() {
//        return blockNumber;
//    }
//
//    public String getCumulativeGasUsed() {
//        return cumulativeGasUsed;
//    }
//
//    public String getGasUsed() {
//        return gasUsed;
//    }
//
//    public String getContractAddress() {
//        return contractAddress;
//    }
//
//    public String getFrom() {
//        return from;
//    }
//
//    public String getTo() {
//        return to;
//    }
//
//    public String getStatus() {
//        return status;
//    }
//
//    public String getLogsBloom() {
//        return logsBloom;
//    }
//}
