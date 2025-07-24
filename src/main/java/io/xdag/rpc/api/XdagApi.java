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

package io.xdag.rpc.api;

import io.xdag.core.XdagLifecycle;
import io.xdag.crypto.exception.AddressFormatException;
import io.xdag.rpc.model.response.*;
import io.xdag.rpc.model.request.TransactionRequest;

import java.util.List;

/**
 * XdagApi interface provides RPC methods for interacting with the XDAG blockchain.
 * It extends XdagLifecycle to manage the lifecycle of the API service.
 */
public interface XdagApi extends XdagLifecycle {

    BlockResponse xdag_getTransactionByHash(String hash, int page);

    String xdag_getBalanceByNumber(String bnOrId);

    ConfigResponse xdag_poolConfig();


    String xdag_protocolVersion();

    /**
     * Get block information by its hash.
     *
     * @param hash Block hash
     * @param page Page number for pagination
     * @return Block information response
     */
    BlockResponse xdag_getBlockByHash(String hash, int page);

    /**
     * Get block information by its hash with custom page size.
     *
     * @param hash Block hash
     * @param page Page number for pagination
     * @param pageSize Number of items per page
     * @return Block information response
     */
    BlockResponse xdag_getBlockByHash(String hash, int page, int pageSize);

    /**
     * Get block information by its hash within a time range.
     *
     * @param hash Block hash
     * @param page Page number for pagination
     * @param startTime Start time for filtering
     * @param endTime End time for filtering
     * @return Block information response
     */
    BlockResponse xdag_getBlockByHash(String hash, int page, String startTime, String endTime);

    /**
     * Get block information by its hash within a time range with custom page size.
     *
     * @param hash Block hash
     * @param page Page number for pagination
     * @param startTime Start time for filtering
     * @param endTime End time for filtering
     * @param pageSize Number of items per page
     * @return Block information response
     */
    BlockResponse xdag_getBlockByHash(String hash, int page, String startTime, String endTime, int pageSize);

    /**
     * Get block information by its number or ID.
     *
     * @param bnOrId Block number or ID
     * @param page Page number for pagination
     * @return Block information response
     */
    BlockResponse xdag_getBlockByNumber(String bnOrId, int page);

    /**
     * Get block information by its number or ID with custom page size.
     *
     * @param bnOrId Block number or ID
     * @param page Page number for pagination
     * @param pageSize Number of items per page
     * @return Block information response
     */
    BlockResponse xdag_getBlockByNumber(String bnOrId, int page, int pageSize);

    List<BlockResponse> xdag_getBlocksByNumber(String bnOrId);

    /**
     * Get the current block number of the XDAG blockchain.
     *
     * @return Current block number as string
     */
    String xdag_blockNumber();

    /**
     * Get the coinbase address of the node.
     *
     * @return Coinbase address as string
     */
    String xdag_coinbase();

    /**
     * Get the balance of a specific address.
     *
     * @param address XDAG address
     * @return Balance as string
     */
    String xdag_getBalance(String address) throws AddressFormatException;

    /**
     * Get the transaction nonce of a specific address.
     *
     * @param address XDAG address
     * @return Transaction nonce as string
     */
    String xdag_getTransactionNonce(String address) throws AddressFormatException;

    /**
     * Get the total balance of the node.
     *
     * @return Total balance as string
     */
    String xdag_getTotalBalance();

    /**
     * Get the current status of the XDAG node.
     *
     * @return Node status information
     */
    XdagStatusResponse xdag_getStatus();

    /**
     * Send a transaction using the personal account.
     *
     * @param request Transaction request details
     * @param passphrase Passphrase for account unlocking
     * @return Transaction process response
     */
    ProcessResponse xdag_personal_sendTransaction(TransactionRequest request, String passphrase);

    /**
     * Send a transaction with transaction nonce using the personal account.
     *
     * @param request Transaction request details
     * @param passphrase Passphrase for account unlocking
     * @return Transaction process response
     */
    ProcessResponse xdag_personal_sendSafeTransaction(TransactionRequest request, String passphrase);

    /**
     * Get the reward amount for a specific block.
     *
     * @param bnOrId Block number or ID
     * @return Reward amount as string
     */
    String xdag_getRewardByNumber(String bnOrId);

    /**
     * Send a raw transaction to the network.
     *
     * @param rawData Raw transaction data
     * @return Transaction hash as string
     */
    String xdag_sendRawTransaction(String rawData);

    /**
     * Get the list of network connections.
     *
     * @return List of network connection information
     * @throws Exception if there's an error retrieving the connections
     */
    List<NetConnResponse> xdag_netConnectionList() throws Exception;

    /**
     * Get the network type (mainnet, testnet, or devnet).
     *
     * @return Network type as string
     */
    String xdag_netType();

    Object xdag_syncing();
}
