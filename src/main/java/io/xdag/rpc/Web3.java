package io.xdag.rpc;

import io.xdag.rpc.modules.web3.Web3XdagModule;

import java.util.Arrays;
import java.util.Map;

public interface Web3 extends Web3XdagModule {
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

//    void sco_banAddress(String address);
//    void sco_unbanAddress(String address);
//    PeerScoringInformation[] sco_peerList();
//    String[] sco_bannedAddresses();
//    PeerScoringReputationSummary sco_reputationSummary();
}
