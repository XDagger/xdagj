package io.xdag.libp2p;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.libp2p.core.Host;
import io.libp2p.core.PeerId;
import io.libp2p.core.crypto.KeyKt;
import io.libp2p.core.crypto.PrivKey;
import io.libp2p.core.dsl.Builder;
import io.libp2p.core.dsl.BuilderJKt;
import io.libp2p.core.multiformats.Multiaddr;
import io.libp2p.core.mux.StreamMuxerProtocol;
import io.libp2p.security.noise.NoiseXXSecureChannel;
import io.libp2p.transport.tcp.TcpTransport;
import io.netty.handler.logging.LogLevel;
import io.xdag.Kernel;
import io.xdag.libp2p.Manager.PeerManager;
import io.xdag.libp2p.RPCHandler.Firewall;
import io.xdag.libp2p.RPCHandler.RPCHandler;
import io.xdag.libp2p.peer.LibP2PNodeId;
import io.xdag.libp2p.peer.NodeId;
import io.xdag.libp2p.peer.Peer;
import io.xdag.libp2p.peer.PeerAddress;
import io.xdag.libp2p.utils.IpUtil;
import io.xdag.libp2p.utils.MultiaddrPeerAddress;
import io.xdag.libp2p.utils.MultiaddrUtil;
import io.xdag.libp2p.utils.SafeFuture;
import lombok.extern.slf4j.Slf4j;
import org.apache.tuweni.bytes.Bytes;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;


@Slf4j
public class Libp2pNetwork implements P2PNetwork<Peer> {
    private RPCHandler rpcHandler;
    private int port;
    private final Host host;
    private final PrivKey privKeyBytes;
    private final NodeId nodeId;
    private final InetAddress privateAddress;
    private PeerManager peerManager;
    private final AtomicReference<State> state = new AtomicReference<>(State.IDLE);
    private final Multiaddr advertisedAddr;
    public Libp2pNetwork(Kernel kernel){
        this.port = kernel.getConfig().getLibp2pPort();
        rpcHandler = new RPCHandler(kernel);
        privateAddress = IpUtil.getLocalAddress();
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
                new ThreadFactoryBuilder().setDaemon(true).setNameFormat("libp2p-%d").build());
        peerManager = new PeerManager(scheduler);
        String prikey0 = "0x0802122074ca7d1380b2c407be6878669ebb5c7a2ee751bb18198f1a0f214bcb93b894b5";
        String prikey1 = "0x0802122074ca7d1380b2c407be6878669ebb5c7a2ee751bb18198f1a0f214bcb93b89411";
        Bytes pub = Bytes.fromHexString(port == 10000 ? prikey0 : prikey1);
        this.privKeyBytes = KeyKt.unmarshalPrivateKey(pub.toArrayUnsafe());
        this.nodeId = new LibP2PNodeId(PeerId.fromPubKey(privKeyBytes.publicKey()));
        this.advertisedAddr =
                MultiaddrUtil.fromInetSocketAddress(
                        new InetSocketAddress("127.0.0.1", port),nodeId);
        System.out.println("nodeid = "+nodeId);
//        host = new HostBuilder().protocol(rpcHandler).listen("/ip4/" + privateAddress.getHostAddress() + "/tcp/" + listenPort).build();
        host = BuilderJKt.hostJ(Builder.Defaults.None,
                b->{
                    b.getIdentity().setFactory(()-> privKeyBytes);
                    b.getTransports().add(TcpTransport::new);
                    b.getSecureChannels().add(NoiseXXSecureChannel::new);
                    b.getMuxers().add(StreamMuxerProtocol.getMplex());
                    b.getNetwork().listen(advertisedAddr.toString());
                    b.getProtocols().add(rpcHandler);
                    b.getDebug().getBeforeSecureHandler().addLogger(LogLevel.DEBUG, "wire.ciphered");
                    Firewall firewall = new Firewall(Duration.ofSeconds(100));
                    b.getDebug().getBeforeSecureHandler().addNettyHandler(firewall);
                    b.getDebug().getMuxFramesHandler().addLogger(LogLevel.DEBUG, "wire.mux");
                    b.getConnectionHandlers().add(peerManager);
                });
        System.out.println(advertisedAddr.toString());
        System.out.println("host = "+host.getPeerId().toString());
    }
    @Override
    public SafeFuture<?> start() {
        if (!state.compareAndSet(State.IDLE, State.RUNNING)) {
            return SafeFuture.failedFuture(new IllegalStateException("Network already started"));
        }
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
    public PeerAddress createPeerAddress(final String peerAddress) {
        return MultiaddrPeerAddress.fromAddress(peerAddress);
    }

    @Override
    public boolean isConnected(final PeerAddress peerAddress) {
        return peerManager.getPeer(peerAddress.getId()).isPresent();
    }

    @Override
    public Bytes getPrivateKey() {
        return Bytes.wrap(privKeyBytes.raw());
    }

    @Override
    public Optional<Peer> getPeer(final NodeId id) {
        return peerManager.getPeer(id);
    }

    @Override
    public Stream<Peer> streamPeers() {
        return peerManager.streamPeers();
    }

    @Override
    public NodeId parseNodeId(final String nodeId) {
        return new LibP2PNodeId(PeerId.fromBase58(nodeId));
    }

    @Override
    public int getPeerCount() {
        return peerManager.getPeerCount();
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
