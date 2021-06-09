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
package io.xdag.net.libp2p;

import io.libp2p.core.Host;
import io.libp2p.core.PeerId;
import io.libp2p.core.crypto.PrivKey;
import io.libp2p.core.dsl.Builder;
import io.libp2p.core.dsl.BuilderJKt;
import io.libp2p.core.multiformats.Multiaddr;
import io.libp2p.core.multistream.ProtocolBinding;
import io.libp2p.core.mux.StreamMuxerProtocol;
import io.libp2p.crypto.keys.Secp256k1Kt;
import io.libp2p.security.noise.NoiseXXSecureChannel;
import io.libp2p.transport.tcp.TcpTransport;
import io.xdag.Kernel;
import io.xdag.crypto.ECKeyPair;
import io.xdag.net.libp2p.discovery.DiscV5Service;
import io.xdag.net.libp2p.peer.LibP2PNodeId;
import io.xdag.net.libp2p.peer.NodeId;
import io.xdag.utils.SafeFuture;
import lombok.extern.slf4j.Slf4j;
import org.apache.tuweni.bytes.Bytes;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;


@Slf4j
public class Libp2pNetwork {
    enum State {
        IDLE,
        RUNNING,
        STOPPED
    }
    public ProtocolBinding<?> protocol;
    private int port;
    private Host host;
    private final PrivKey privKey;
    private NodeId nodeId;
    protected String ip;

    private final AtomicReference<State> state = new AtomicReference<>(State.IDLE);
    private final Multiaddr advertisedAddr;
    protected DiscV5Service discV5Service;
    protected List<String> bootnodes ;

    public Libp2pNetwork(PrivKey privKey, Multiaddr listenAddr) {
        protocol = new NonProtocol();
        this.privKey = privKey;
        this.advertisedAddr = listenAddr;
        this.bootnodes = new ArrayList<>();
    }
    public Libp2pNetwork(Kernel kernel){
        port = kernel.getConfig().getNodeSpec().getLibp2pPort();
        protocol = new Libp2pXdagProtocol(kernel);
        // libp2p use wallet default key
        ECKeyPair key = kernel.getWallet().getDefKey();
        privKey = Secp256k1Kt.unmarshalSecp256k1PrivateKey(key.getPrivateKey().toByteArray());
        PeerId peerId = PeerId.fromPubKey(privKey.publicKey());
        ip = kernel.getConfig().getNodeSpec().getNodeIp();
        nodeId = new LibP2PNodeId(peerId);
        advertisedAddr = Libp2pUtils.fromInetSocketAddress(
                        new InetSocketAddress(kernel.getConfig().getNodeSpec().getNodeIp(), port),nodeId);
        bootnodes = kernel.getConfig().getNodeSpec().getBootnodes();

    }

    private void crate(){
        host = BuilderJKt.hostJ(Builder.Defaults.None,
                b->{
                    b.getIdentity().setFactory(()-> privKey);
                    b.getTransports().add(TcpTransport::new);
                    b.getSecureChannels().add(NoiseXXSecureChannel::new);
                    b.getMuxers().add(StreamMuxerProtocol.getMplex());
                    b.getNetwork().listen(advertisedAddr.toString());
                    b.getProtocols().add(protocol);
//                    b.getDebug().getBeforeSecureHandler().setLogger(LogLevel.DEBUG, "wire.ciphered");
//                    Firewall firewall = new Firewall(Duration.ofSeconds(100));
//                    b.getDebug().getBeforeSecureHandler().addNettyHandler(firewall);
//                    b.getDebug().getMuxFramesHandler().setLogger(LogLevel.DEBUG, "wire.mux");
                });
        discV5Service = DiscV5Service.create(Bytes.wrap(privKey.raw()),ip,port,bootnodes);
    }

    public SafeFuture<?> start() {
        if (!state.compareAndSet(State.IDLE, State.RUNNING)) {
            return SafeFuture.failedFuture(new IllegalStateException("Network already started"));
        }
        crate();
        //16Uiu2HAm3NZUwzzNHfnnB8ADfnuP5MTDuqjRb3nTRBxPTQ4g7Wjj
        log.info("id ={}", host.getPeerId());
        log.info("Starting libp2p network...");
        if(discV5Service!=null){
            discV5Service.start();
            discV5Service.searchForPeers();
        }
        return SafeFuture.of(host.start())
                .thenApply(
                        i -> {
                            log.debug(getNodeAddress());
                            return null;
                        });
    }


    public void dail(String peer) {
        Multiaddr address = Multiaddr.fromString(peer);
        protocol.dial(host,address);
    }

    public String getNodeAddress() {
        return advertisedAddr.toString();
    }

    public NodeId getNodeId() {
        return nodeId;
    }

    public DiscV5Service getDiscV5Service() {
        return discV5Service;
    }

    public SafeFuture<?> stop() {
        if (!state.compareAndSet(State.RUNNING, State.STOPPED)) {
            return SafeFuture.COMPLETE;
        }
        log.debug("LibP2PNetwork.stop()");
        SafeFuture.of(discV5Service.stop());
        return SafeFuture.of(host.stop());
    }

}
