///*
// * The MIT License (MIT)
// *
// * Copyright (c) 2020-2030 The XdagJ Developers
// *
// * Permission is hereby granted, free of charge, to any person obtaining a copy
// * of this software and associated documentation files (the "Software"), to deal
// * in the Software without restriction, including without limitation the rights
// * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// * copies of the Software, and to permit persons to whom the Software is
// * furnished to do so, subject to the following conditions:
// *
// * The above copyright notice and this permission notice shall be included in
// * all copies or substantial portions of the Software.
// *
// * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
// * THE SOFTWARE.
// */
//package io.xdag.rpc.modules.debug;
//
//import com.fasterxml.jackson.databind.JsonNode;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//
//import java.util.Map;
//
//
//public class DebugModuleImpl implements DebugModule {
//    private static final Logger logger = LoggerFactory.getLogger("web3");
//
//    private final BlockStore blockStore;
//    private final ReceiptStore receiptStore;
//
//    private final MessageHandler messageHandler;
//    private final BlockExecutor blockExecutor;
//
//    public DebugModuleImpl(
//            BlockStore blockStore,
//            ReceiptStore receiptStore,
//            MessageHandler messageHandler,
//            BlockExecutor blockExecutor) {
//        this.blockStore = blockStore;
//        this.receiptStore = receiptStore;
//        this.messageHandler = messageHandler;
//        this.blockExecutor = blockExecutor;
//    }
//
//    @Override
//    public String wireProtocolQueueSize() {
//        long n = messageHandler.getMessageQueueSize();
//        return TypeConverter.toQuantityJsonHex(n);
//    }
//
//    @Override
//    public JsonNode traceTransaction(String transactionHash, Map<String, String> traceOptions) throws Exception {
//        logger.trace("debug_traceTransaction({}, {})", transactionHash, traceOptions);
//
//        if (traceOptions != null && !traceOptions.isEmpty()) {
//            // TODO: implement the logic that takes into account trace options.
//            logger.warn("Received {} trace options. For now trace options are being ignored", traceOptions);
//        }
//
//        byte[] hash = stringHexToByteArray(transactionHash);
//        TransactionInfo txInfo = receiptStore.getInMainChain(hash, blockStore);
//
//        if (txInfo == null) {
//            logger.trace("No transaction info for {}", transactionHash);
//            return null;
//        }
//
//        Block block = blockStore.getBlockByHash(txInfo.getBlockHash());
//        Block parent = blockStore.getBlockByHash(block.getParentHash().getBytes());
//        Transaction tx = block.getTransactionsList().get(txInfo.getIndex());
//        txInfo.setTransaction(tx);
//
//        ProgramTraceProcessor programTraceProcessor = new ProgramTraceProcessor();
//        blockExecutor.traceBlock(programTraceProcessor, 0, block, parent.getHeader(), false, false);
//
//        return programTraceProcessor.getProgramTraceAsJsonNode(tx.getHash());
//    }
//}
