package io.xdag.net.discovery;

import io.libp2p.core.crypto.KeyKt;
import io.libp2p.core.crypto.PrivKey;
import io.xdag.Kernel;
import io.xdag.net.discovery.discv5.DiscV5ServiceImpl;
import lombok.Getter;
import org.apache.tuweni.bytes.Bytes;

import java.util.List;

@Getter
public class DiscoveryController {
    protected DiscoveryService discV5Service1;
    Kernel kernel;
    String ip;
    int port;
    boolean isbootnode;
    PrivKey privKey;
    public DiscoveryController(Kernel kernel) {
        this.kernel = kernel;
        ip = kernel.getConfig().getNodeSpec().getNodeIp();
        port = kernel.getConfig().getNodeSpec().getLibp2pPort();
        isbootnode = kernel.getConfig().getNodeSpec().isBootnode();
        privKey = kernel.getPrivKey();
    }

    public void start(){
        if(kernel.getConfig().getNodeSpec().isBootnode()){
            String Privkey = kernel.getConfig().getNodeSpec().getLibp2pPrivkey();
            Bytes privkeybytes = Bytes.fromHexString(Privkey);
            this.privKey = KeyKt.unmarshalPrivateKey(privkeybytes.toArrayUnsafe());
        }else{
            this.privKey = kernel.getPrivKey();
        }
        List<String> bootnodes = kernel.getConfig().getNodeSpec().getBootnodes();
        discV5Service1 = DiscV5ServiceImpl.create(Bytes.wrap(privKey.raw()),ip,port,bootnodes);
        discV5Service1.start();
        discV5Service1.searchForPeers();

    }

    public void stop() {
        discV5Service1.stop();
    }
}
