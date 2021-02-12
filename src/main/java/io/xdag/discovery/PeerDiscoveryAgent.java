package io.xdag.discovery;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.DecoderException;

import java.net.UnknownHostException;

@Slf4j
public class PeerDiscoveryAgent {

    public PeerDiscoveryAgent() {

    }

    public void start(boolean isbootnodes) throws DecoderException, UnknownHostException {
        if (isbootnodes){
            log.info("启动种子节点的发现功能");
        }else {
            log.info("启动非种子节点的发现功能");
        }
        DiscoveryController discoveryController = new DiscoveryController();
        discoveryController.start(isbootnodes);
    }
}
