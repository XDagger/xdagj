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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import io.xdag.DagKernel;
import io.xdag.core.state.AccountState;
import io.xdag.core.state.ByteArray;
import io.xdag.net.Channel;
import io.xdag.net.message.p2p.TransactionMessage;
import io.xdag.utils.BytesUtils;
import io.xdag.utils.TimeUtils;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PendingManager implements Runnable, DagchainListener {

    private static final ThreadFactory factory = new ThreadFactory() {

        private final AtomicInteger cnt = new AtomicInteger(0);

        @Override
        public Thread newThread(Runnable r) {
            return new Thread(r, "pending-" + cnt.getAndIncrement());
        }
    };

    public static final long ALLOWED_TIME_DRIFT = TimeUnit.HOURS.toMillis(2);

    private static final int QUEUE_SIZE_LIMIT = 128 * 1024;
    private static final int VALID_TXS_LIMIT = 16 * 1024;
    private static final int LARGE_NONCE_TXS_LIMIT = 32 * 1024;
    private static final int PROCESSED_TXS_LIMIT = 128 * 1024;

    private final DagKernel kernel;

    private AccountState pendingAS;

    // Transactions that haven't been processed
    private final LinkedHashMap<ByteArray, Transaction> queue = new LinkedHashMap<>();

    // Transactions that have been processed and are valid for block production
    private final ArrayList<PendingTransaction> validTxs = new ArrayList<>();

    // Transactions whose nonce is too large, compared to the sender's nonce
    private final Cache<ByteArray, Transaction> largeNonceTxs = Caffeine.newBuilder().maximumSize(LARGE_NONCE_TXS_LIMIT)
            .build();

    // Transactions that have been processed, including both valid and invalid ones
    private final Cache<ByteArray, Long> processedTxs = Caffeine.newBuilder().maximumSize(PROCESSED_TXS_LIMIT).build();

    private final ScheduledExecutorService exec;

    private ScheduledFuture<?> validateFuture;

    private volatile boolean isRunning;

    /**
     * Creates a pending manager.
     */
    public PendingManager(DagKernel kernel) {
        this.kernel = kernel;

        this.pendingAS = kernel.getDagchain().getAccountState().track();

        this.exec = Executors.newSingleThreadScheduledExecutor(factory);
    }

    /**
     * Starts this pending manager.
     */
    public synchronized void start() {
        if (!isRunning) {
            /*
             * NOTE: a rate smaller than the message queue sending rate should be used to
             * prevent message queues from hitting the NET_MAX_QUEUE_SIZE, especially when
             * the network load is heavy.
             */
            this.validateFuture = exec.scheduleAtFixedRate(this, 2, 2, TimeUnit.MILLISECONDS);

            kernel.getDagchain().addListener(this);

            log.debug("Pending manager started");
            this.isRunning = true;
        }
    }

    /**
     * Shuts down this pending manager.
     */
    public synchronized void stop() {
        if (isRunning) {
            validateFuture.cancel(true);

            log.debug("Pending manager stopped");
            isRunning = false;
        }
    }

    /**
     * Returns whether the pending manager is running or not.
     */
    public synchronized boolean isRunning() {
        return isRunning;
    }

    /**
     * Returns a copy of the queue, for test purpose only.
     */
    public synchronized List<Transaction> getQueue() {
        return new ArrayList<>(queue.values());
    }

    /**
     * Adds a transaction to the queue, which will be validated later by the
     * background worker. Transaction may get rejected if the queue is full.
     */
    public synchronized void addTransaction(Transaction tx) {
        ByteArray hash = ByteArray.of(tx.getHash());

        if (queue.size() < QUEUE_SIZE_LIMIT
                && processedTxs.getIfPresent(hash) == null
                && tx.validate(kernel.getConfig().getNodeSpec().getNetwork())) {
            // NOTE: re-insertion doesn't affect item order
            queue.put(ByteArray.of(tx.getHash()), tx);
        }
    }

    /**
     * Adds a transaction to the pool and waits until it's done.
     *
     * @param tx
     *            The transaction
     * @return The processing result
     */
    public synchronized ProcessingResult addTransactionSync(Transaction tx) {
        // nonce check for transactions from this client
        if (tx.getNonce() != getNonce(tx.getFrom())) {
            return new ProcessingResult(0, TransactionResult.Code.INVALID_NONCE);
        }

        if (tx.validate(kernel.getConfig().getNodeSpec().getNetwork())) {
            // proceed with the tx, ignoring transaction queue size limit
            return processTransaction(tx, false, true);
        } else {
            return new ProcessingResult(0, TransactionResult.Code.INVALID_FORMAT);
        }
    }

    /**
     * Returns the nonce of an account based on the pending state.
     */
    public synchronized long getNonce(byte[] address) {
        return pendingAS.getAccount(address).getNonce();
    }

    /**
     * Returns pending transactions, limited by the given total size in bytes.
     */
    public synchronized List<PendingTransaction> getPendingTransactions(XAmount blockFeeLimit) {
        List<PendingTransaction> txs = new ArrayList<>();
        Iterator<PendingTransaction> it = validTxs.iterator();

        XAmount feeLimit = XAmount.of(blockFeeLimit.toLong());
        while (it.hasNext() && blockFeeLimit.greaterThan(XAmount.ZERO)) {
            PendingTransaction tx = it.next();

            XAmount feeUsage = kernel.getConfig().getDagSpec().getMinTransactionFee();

            if (feeLimit.greaterThan(feeUsage)) {
                txs.add(tx);
                feeLimit = feeLimit.subtract(feeUsage);
            }
        }

        return txs;
    }

    /**
     * Returns all pending transactions.
     */
    public synchronized List<PendingTransaction> getPendingTransactions() {
        return getPendingTransactions(XAmount.of(512, XUnit.XDAG));
    }

    /**
     * Resets the pending state and returns all pending transactions.
     */
    public synchronized List<PendingTransaction> reset() {
        // reset state
        pendingAS = kernel.getDagchain().getAccountState().track();

        // clear transaction pool
        List<PendingTransaction> txs = new ArrayList<>(validTxs);
        validTxs.clear();

        return txs;
    }

    @Override
    public synchronized void onMainBlockAdded(MainBlock block) {
        if (isRunning) {
            long t1 = TimeUtils.currentTimeMillis();

            // clear transaction pool
            List<PendingTransaction> txs = reset();

            // update pending state
            long accepted = 0;
            for (PendingTransaction tx : txs) {
                accepted += processTransaction(tx.transaction, true, false).accepted;
            }

            long t2 = TimeUtils.currentTimeMillis();
            log.debug("Execute pending transactions: # txs = {} / {},  time = {} ms", accepted, txs.size(), t2 - t1);
        }
    }

    @Override
    public synchronized void run() {
        Iterator<Map.Entry<ByteArray, Transaction>> iterator = queue.entrySet().iterator();

        while (validTxs.size() < VALID_TXS_LIMIT && iterator.hasNext()) {
            // the eldest entry
            Map.Entry<ByteArray, Transaction> entry = iterator.next();
            iterator.remove();

            // reject already executed transactions
            if (processedTxs.getIfPresent(entry.getKey()) != null) {
                continue;
            }

            // process the transaction
            int accepted = processTransaction(entry.getValue(), false, false).accepted;
            processedTxs.put(entry.getKey(), TimeUtils.currentTimeMillis());

            // include one tx per call
            if (accepted > 0) {
                break;
            }
        }
    }

    /**
     * Validates the given transaction and add to pool if success.
     *
     * @param tx
     *            a transaction
     * @param isIncludedBefore
     *            whether the transaction is included before
     * @param isFromThisNode
     *            whether the transaction is from this node
     * @return the number of transactions that have been included
     */
    protected ProcessingResult processTransaction(Transaction tx, boolean isIncludedBefore, boolean isFromThisNode) {

        int cnt = 0;
        long now = TimeUtils.currentTimeMillis();

        // reject transactions with a duplicated tx hash
        if (kernel.getDagchain().hasTransaction(tx.getHash())) {
            return new ProcessingResult(0, TransactionResult.Code.INVALID);
        }

        // check transaction timestamp if this is a fresh transaction:
        // a time drift of 2 hours is allowed by default
        if (tx.getTimestamp() < now - kernel.getConfig().getDagSpec().getMaxTxPoolTimeDrift()
                || tx.getTimestamp() > now + kernel.getConfig().getDagSpec().getMaxTxPoolTimeDrift()) {
            return new ProcessingResult(0, TransactionResult.Code.INVALID_TIMESTAMP);
        }

        // report INVALID_NONCE error to prevent the transaction from being
        // silently ignored due to a low nonce
        if (tx.getNonce() < getNonce(tx.getFrom())) {
            return new ProcessingResult(0, TransactionResult.Code.INVALID_NONCE);
        }

        // Check transaction nonce: pending transactions must be executed sequentially
        // by nonce in ascending order. In case of a nonce jump, the transaction is
        // delayed for the next event loop of PendingManager.
        while (tx != null && tx.getNonce() == getNonce(tx.getFrom())) {

            // execute transactions
            AccountState as = pendingAS.track();
            TransactionResult result = new TransactionExecutor(kernel.getConfig()).execute(tx, as);

            if (result.getCode().isAcceptable()) {
                // commit state updates
                as.commit();

                // Add the successfully processed transaction into the pool of transactions
                // which are ready to be proposed to the network.
                PendingTransaction pendingTransaction = new PendingTransaction(tx, result);
                validTxs.add(pendingTransaction);
                cnt++;

                // If a transaction is not included before, send it to the network now
                if (!isIncludedBefore) {
                    // if it is from myself, broadcast it to everyone
                    broadcastTransaction(tx, isFromThisNode);
                }
            } else {
                // exit immediately if invalid
                return new ProcessingResult(cnt, result.getCode());
            }

            tx = largeNonceTxs.getIfPresent(createKey(tx.getFrom(), getNonce(tx.getFrom())));
            isIncludedBefore = false; // A large-nonce transaction is not included before
        }

        // Delay the transaction for the next event loop of PendingManager. The delayed
        // transaction is expected to be processed once PendingManager has received
        // all of its preceding transactions from the same address.
        if (tx != null && tx.getNonce() > getNonce(tx.getFrom())) {
            largeNonceTxs.put(createKey(tx), tx);
        }

        return new ProcessingResult(cnt);
    }

    private void broadcastTransaction(Transaction tx, boolean toAllPeers) {
        List<Channel> channels = kernel.getChannelManager().getActiveChannels();

        // If not to all peers, randomly pick n channels
        int n = kernel.getConfig().getNodeSpec().getNetRelayRedundancy();
        if (!toAllPeers && channels.size() > n) {
            Collections.shuffle(channels);
            channels = channels.subList(0, n);
        }

        // Send the message
        TransactionMessage msg = new TransactionMessage(tx);
        for (Channel c : channels) {
            if (c.isActive()) {
                c.getMessageQueue().sendMessage(msg);
            }
        }
    }

    private ByteArray createKey(Transaction tx) {
        return ByteArray.of(BytesUtils.merge(tx.getFrom(), BytesUtils.of(tx.getNonce())));
    }

    private ByteArray createKey(byte[] acc, long nonce) {
        return ByteArray.of(BytesUtils.merge(acc, BytesUtils.of(nonce)));
    }

    /**
     * This object represents a transaction and its execution result against a
     * snapshot of local state that is not yet confirmed by the network.
     */
    @Getter
    public static class PendingTransaction {

        public final Transaction transaction;

        public final TransactionResult result;

        public PendingTransaction(Transaction transaction, TransactionResult result) {
            this.transaction = transaction;
            this.result = result;
        }
    }

    /**
     * This object represents the number of accepted transactions and the cause of
     * rejection by ${@link PendingManager}.
     */
    public static class ProcessingResult {

        public final int accepted;

        public final TransactionResult.Code error;

        public ProcessingResult(int accepted, TransactionResult.Code error) {
            this.accepted = accepted;
            this.error = error;
        }

        public ProcessingResult(int accepted) {
            this.accepted = accepted;
            this.error = null;
        }
    }
}
