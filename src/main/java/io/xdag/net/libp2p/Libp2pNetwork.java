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
import io.libp2p.core.crypto.KeyKt;
import io.libp2p.core.crypto.PrivKey;
import io.libp2p.core.dsl.Builder;
import io.libp2p.core.dsl.BuilderJKt;
import io.libp2p.core.multiformats.Multiaddr;
import io.libp2p.core.multistream.ProtocolBinding;
import io.libp2p.mux.mplex.MplexStreamMuxer;
import io.libp2p.security.noise.NoiseXXSecureChannel;
import io.libp2p.transport.tcp.TcpTransport;
import io.netty.handler.logging.LogLevel;
import io.xdag.Kernel;
import io.xdag.net.libp2p.RPCHandler.Firewall;
import io.xdag.net.libp2p.RPCHandler.NonHandler;
import io.xdag.net.libp2p.RPCHandler.RPCHandler;
import io.xdag.net.libp2p.manager.PeerManager;
import io.xdag.net.libp2p.peer.LibP2PNodeId;
import io.xdag.net.libp2p.peer.NodeId;
import io.xdag.utils.IpUtil;
import io.xdag.utils.MultiaddrUtil;
import io.xdag.utils.SafeFuture;
import lombok.extern.slf4j.Slf4j;
import org.apache.tuweni.bytes.Bytes;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;



@Slf4j
public class Libp2pNetwork implements P2PNetwork {
    private ProtocolBinding<?> rpcHandler;
    private int port;
    private Host host;
    private final PrivKey privKey;
    private NodeId nodeId;
    private InetAddress privateAddress;
    protected PeerManager peerManager = new PeerManager();
    private final AtomicReference<State> state = new AtomicReference<>(State.IDLE);
    private final Multiaddr advertisedAddr;
    public Libp2pNetwork(PrivKey privKey, Multiaddr listenAddr) {
        rpcHandler = new NonHandler(peerManager);
        this.privKey = privKey;
        this.advertisedAddr = listenAddr;
    }
    public Libp2pNetwork(Kernel kernel){
        port = kernel.getConfig().getNodeSpec().getLibp2pPort();
        rpcHandler = new RPCHandler(kernel,peerManager);
        privateAddress = IpUtil.getLocalAddress();
        peerManager = new PeerManager();
        //种子节点 Privkey从配置文件读取 非种子节点随机生成一个
        //PrivKey privKey= KeyKt.generateKeyPair(KEY_TYPE.SECP256K1,0).getFirst();
        if(kernel.getConfig().getNodeSpec().isBootnode()){
            String Privkey = kernel.getConfig().getNodeSpec().getLibp2pPrivkey();
            Bytes privkeybytes = Bytes.fromHexString(Privkey);
            this.privKey = KeyKt.unmarshalPrivateKey(privkeybytes.toArrayUnsafe());
        }else{
            this.privKey = kernel.getPrivKey();
        }
        PeerId peerId = PeerId.fromPubKey(privKey.publicKey());
        this.nodeId = new LibP2PNodeId(peerId);
        this.advertisedAddr =
                MultiaddrUtil.fromInetSocketAddress(
                        new InetSocketAddress(kernel.getConfig().getNodeSpec().getNodeIp(), port),nodeId);

    }

    private void crate(){
        host = BuilderJKt.hostJ(Builder.Defaults.None,
                b->{
                    b.getIdentity().setFactory(()-> privKey);
                    b.getTransports().add(TcpTransport::new);
                    b.getSecureChannels().add(NoiseXXSecureChannel::new);
//                    b.getMuxers().add(StreamMuxerProtocol.getMplex());
                    b.getMuxers().add(MplexStreamMuxer::new);
                    b.getNetwork().listen(advertisedAddr.toString());
                    b.getProtocols().add(rpcHandler);
                    b.getDebug().getBeforeSecureHandler().setLogger(LogLevel.DEBUG, "wire.ciphered");
                    Firewall firewall = new Firewall(Duration.ofSeconds(100));
                    b.getDebug().getBeforeSecureHandler().setHandler(firewall);
                    b.getDebug().getMuxFramesHandler().setLogger(LogLevel.DEBUG, "wire.mux");
                    b.getConnectionHandlers().add(peerManager);
                });
    }

    @Override
    public SafeFuture<?> start() {
        if (!state.compareAndSet(State.IDLE, State.RUNNING)) {
            return SafeFuture.failedFuture(new IllegalStateException("Network already started"));
        }
        crate();
        //16Uiu2HAm3NZUwzzNHfnnB8ADfnuP5MTDuqjRb3nTRBxPTQ4g7Wjj
        log.info("id ={}", host.getPeerId());
        log.info("Starting libp2p network...");
        return SafeFuture.of(host.start())
                .thenApply(
                        i -> {
                            log.debug(getNodeAddress());
                            return null;
                        });
    }


    @Override
    public void dail(String peer) {
        Multiaddr address = Multiaddr.fromString(peer);
        rpcHandler.dial(host,address);
    }


    @Override
    public String getNodeAddress() {
        return advertisedAddr.toString();
    }

    @Override
    public NodeId getNodeId() {
        return nodeId;
    }


    @Override
    public SafeFuture<?> stop() {
        if (!state.compareAndSet(State.RUNNING, State.STOPPED)) {
            return SafeFuture.COMPLETE;
        }
        log.debug("LibP2PNetwork.stop()");
        return SafeFuture.of(host.stop());
    }


    public String getAddress(){
        return "/ip4/"+privateAddress.getHostAddress()+
                "/tcp/"+port;
    }
}
