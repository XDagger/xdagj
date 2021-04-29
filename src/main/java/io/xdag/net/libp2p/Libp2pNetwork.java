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

import identify.pb.IdentifyOuterClass;
import io.libp2p.core.Host;
import io.libp2p.core.PeerId;
import io.libp2p.core.crypto.KEY_TYPE;
import io.libp2p.core.crypto.KeyKt;
import io.libp2p.core.crypto.PrivKey;
import io.libp2p.core.dsl.Builder;
import io.libp2p.core.dsl.BuilderJKt;
import io.libp2p.core.multiformats.Multiaddr;
import io.libp2p.core.multistream.ProtocolBinding;
import io.libp2p.etc.types.ByteArrayExtKt;
import io.libp2p.mux.mplex.MplexStreamMuxer;
import io.libp2p.protocol.Identify;
import io.libp2p.protocol.Ping;
import io.libp2p.security.noise.NoiseXXSecureChannel;
import io.libp2p.transport.tcp.TcpTransport;
import io.netty.handler.logging.LogLevel;
import io.xdag.Kernel;
import io.xdag.net.libp2p.libp2phandler.Firewall;
import io.xdag.net.libp2p.libp2phandler.NonHandler;
import io.xdag.net.libp2p.libp2phandler.Libp2pChannelHandler;
import io.xdag.net.libp2p.peer.Libp2pNodeId;
import io.xdag.net.libp2p.peer.NodeId;
import io.xdag.net.manager.PeerManager;
import io.xdag.utils.MultiaddrUtil;
import io.xdag.utils.SafeFuture;
import lombok.extern.slf4j.Slf4j;
import org.apache.tuweni.bytes.Bytes;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static io.xdag.utils.SafeFuture.failedFuture;


/**
 * @author wawa
 */
@Slf4j
public class Libp2pNetwork implements P2pNetwork {
    private ProtocolBinding<?> rpcHandler;
    public Host host;
    private final PrivKey privKey;
    private NodeId nodeId;
    private final AtomicReference<State> state = new AtomicReference<>(State.IDLE);
    private final Multiaddr listenAddr;
    public PeerManager peerManager = new PeerManager();
    public Libp2pNetwork(Kernel kernel){
        int port = kernel.getConfig().getLibp2pPort();
        rpcHandler = new Libp2pChannelHandler(kernel,peerManager);
        //种子节点 Privkey从配置文件读取 非种子节点随机生成一个
        //PrivKey privKey= KeyKt.generateKeyPair(KEY_TYPE.SECP256K1,0).getFirst();
        if(kernel.getConfig().isbootnode){
            String Privy = kernel.getConfig().getPrivkey();
            Bytes privately = Bytes.fromHexString(Privy);
            privKey = KeyKt.unmarshalPrivateKey(privately.toArrayUnsafe());
        }else{
            privKey = KeyKt.generateKeyPair(KEY_TYPE.SECP256K1).component1();
        }

        PeerId peerId = PeerId.fromPubKey(privKey.publicKey());
        this.nodeId = new Libp2pNodeId(peerId);
        this.listenAddr =
                MultiaddrUtil.fromInetSocketAddress(
                        new InetSocketAddress(kernel.getConfig().getNodeIp(), port),nodeId);
    }

    public Libp2pNetwork(PrivKey privKey, Multiaddr listenAddr) {
        this.rpcHandler = new NonHandler(peerManager);
        this.privKey = privKey;
        this.listenAddr = listenAddr;
    }

    private void crate(){
        host = BuilderJKt.hostJ(Builder.Defaults.None,
                b->{
                    b.getIdentity().setFactory(() -> privKey);
                    b.getTransports().add(TcpTransport::new);
                    b.getSecureChannels().add(NoiseXXSecureChannel::new);
                    b.getMuxers().add(MplexStreamMuxer::new);

                    b.getNetwork().listen(listenAddr.toString());

                    b.getProtocols().addAll(getDefaultProtocols());
                    b.getProtocols().add(rpcHandler);

                    b.getDebug().getBeforeSecureHandler().setLogger(LogLevel.DEBUG, "wire.ciphered");
                    Firewall firewall = new Firewall(Duration.ofSeconds(100));
                    b.getDebug().getBeforeSecureHandler().setHandler(firewall);
                    b.getDebug().getMuxFramesHandler().setLogger(LogLevel.DEBUG, "wire.mux");

                    b.getConnectionHandlers().add(peerManager);
                });
    }



    private List<ProtocolBinding<?>> getDefaultProtocols() {
        final Ping ping = new Ping();
        IdentifyOuterClass.Identify identifyMsg =
                IdentifyOuterClass.Identify.newBuilder()
                        .setProtocolVersion("ipfs/0.1.0")
                        .setAgentVersion("XDAGJ" + "/" + "develop")
                        .setPublicKey(ByteArrayExtKt.toProtobuf(privKey.publicKey().bytes()))
                        .addListenAddrs(ByteArrayExtKt.toProtobuf(listenAddr.getBytes()))
                        .setObservedAddr(ByteArrayExtKt.toProtobuf(listenAddr.getBytes()))
                        .addAllProtocols(ping.getProtocolDescriptor().getAnnounceProtocols())
                        .build();
        return List.of(ping, new Identify(identifyMsg));
    }

    @Override
    public SafeFuture<?> start() {
        if (!state.compareAndSet(State.IDLE, State.RUNNING)) {
            return failedFuture(new IllegalStateException("Network already started"));
        }
        crate();
        //16Uiu2HAm3NZUwzzNHfnnB8ADfnuP5MTDuqjRb3nTRBxPTQ4g7Wjj
        log.info("id ={}",host.getPeerId().toString());
        log.info("Starting libp2p network...");
        return SafeFuture.of(host.start())
                .thenApply(
                        i -> {
                            log.info(getNodeAddress());
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
        return listenAddr.toString();
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

}
