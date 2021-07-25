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

package io.xdag.rpc;

import io.xdag.rpc.modules.web3.Web3XdagModule;
import java.util.Arrays;
import java.util.Map;

public interface Web3 extends Web3XdagModule {

    String web3_clientVersion();

    String web3_sha3(String data) throws Exception;

    String net_version();

    String net_peerCount();

    boolean net_listening();

    String[] net_peerList();

    // methods required by dev environments
    Map<String, String> rpc_modules();

    void db_putString();

    void db_getString();

    void db_putHex();

    void db_getHex();

    String personal_newAccount(String passphrase);

    String[] personal_listAccounts();

    String personal_importRawKey(String key, String passphrase);

    String personal_sendTransaction(CallArguments transactionArgs, String passphrase) throws Exception;

    boolean personal_unlockAccount(String key, String passphrase, String duration);

    boolean personal_lockAccount(String key);

    String personal_dumpRawKey(String address) throws Exception;

    class CallArguments {

        public String from;
        public String to;
        //        public String gas;
//        public String gasPrice;
        public String value;
        //        public String data; // compiledCode
        public String remark;
        public String netType;
        //        public String nonce;
        public String chainId; //NOSONAR

        @Override
        public String toString() {
            return "CallArguments{" +
                    "from='" + from + '\'' +
                    ", to='" + to + '\'' +
//                    ", gasLimit='" + gas + '\'' +
//                    ", gasPrice='" + gasPrice + '\'' +
                    ", value='" + value + '\'' +
                    ", remark='" + remark + '\'' +
                    ", netType='" + netType + '\'' +
//                    ", data='" + data + '\'' +
//                    ", nonce='" + nonce + '\'' +
                    ", chainId='" + chainId + '\'' +
                    '}';
        }
    }

    class BlockInformationResult {

        public String hash;
        public String totalDifficulty;
        public boolean inMainChain;
    }

    class FilterRequest {

        public String fromBlock;
        public String toBlock;
        public Object address;
        public Object[] topics;

        @Override
        public String toString() {
            return "FilterRequest{" +
                    "fromBlock='" + fromBlock + '\'' +
                    ", toBlock='" + toBlock + '\'' +
                    ", address=" + address +
                    ", topics=" + Arrays.toString(topics) +
                    '}';
        }
    }

//    void sco_banAddress(String address);
//    void sco_unbanAddress(String address);
//    PeerScoringInformation[] sco_peerList();
//    String[] sco_bannedAddresses();
//    PeerScoringReputationSummary sco_reputationSummary();
}
