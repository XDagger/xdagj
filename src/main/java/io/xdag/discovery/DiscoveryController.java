package io.xdag.discovery;

import com.google.common.annotations.VisibleForTesting;
import io.libp2p.discovery.mdns.impl.DNSRecord;
import io.vertx.core.Vertx;
import io.vertx.core.datagram.DatagramPacket;
import io.vertx.core.datagram.DatagramSocket;
import io.xdag.config.DiscoveryConfiguration;
import io.xdag.discovery.Utils.*;
import io.xdag.discovery.Utils.bytes.BytesValue;
import io.xdag.discovery.cryto.SECP256K1;
import io.xdag.discovery.newMessage.FindNeighborsPacketData;
import io.xdag.discovery.newMessage.NeighborsPacketData;
import io.xdag.discovery.newMessage.PingPacketData;
import io.xdag.discovery.newMessage.PongPacketData;
import io.xdag.discovery.peer.DiscoveryPeer;
import io.xdag.discovery.peer.Endpoint;
import io.xdag.discovery.peer.Peer;
import io.xdag.discovery.peer.PeerTable;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.DecoderException;
import org.bouncycastle.util.IPAddress;

import java.io.IOException;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static io.xdag.discovery.Utils.Preconditions.checkGuard;
import static io.xdag.discovery.Utils.bytes.MutableBytesValue.wrapBuffer;
import static java.util.Collections.emptyList;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

@Slf4j
public class DiscoveryController {
    private static final int MAX_PACKET_SIZE_BYTES = 1600;
    PeerTable peerTable;
    DatagramSocket socket;
    private long lastRefreshTime = -1;
    DiscoveryPeer myself;
    private Vertx vertx;
    Endpoint mynode;
    List<DiscoveryPeer> bootnodes;
    DiscoveryConfiguration discoveryconfig = new DiscoveryConfiguration();
    private static SECP256K1.KeyPair keyPair ;
    private static final long REFRESH_CHECK_INTERVAL_MILLIS = MILLISECONDS.convert(30, SECONDS);
    private final long tableRefreshIntervalMs = MILLISECONDS.convert(30, TimeUnit.MINUTES);
    private final RetryDelayFunction retryDelayFunction = RetryDelayFunction.linear(1.5, 2000, 60000);
    private final Map<BytesValue, PeerInteractionState> inflightInteractions =
            new ConcurrentHashMap<>();
    private final Subscribers<Consumer<PeerDiscoveryEvent.PeerBondedEvent>> peerBondedObservers = new Subscribers<>();

    public void start(boolean isbootnode) throws DecoderException, UnknownHostException {
        //todo:
        final String host = InetAddress.getLocalHost().toString();
        final int port ;
        final BytesValue myid ;
        if(isbootnode){
            final SECP256K1.PrivateKey privateKey =
                    SECP256K1.PrivateKey.create(
                            new BigInteger("c85ef7d79691fe79573b1a7064c19c1a9819ebdbd1faaab1a8ec92344438aaf4", 16));
            keyPair = SECP256K1.KeyPair.create(privateKey);
            myself = DiscoveryConfiguration.getBootnode().get(0);
            myid = myself.getId();
            mynode = myself.getEndpoint();
        }else {
            port = 10004;
            OptionalInt tcp = OptionalInt.of(10005);
            keyPair= SECP256K1.KeyPair.generate();
            myid = keyPair.getPublicKey().getEncodedBytes();
            mynode = new Endpoint(host, port, tcp);
            myself = new DiscoveryPeer(myid, mynode);

        }
        peerTable = new PeerTable(myid, 10);

        bootnodes = DiscoveryConfiguration.getBootnode();

        this.vertx = Vertx.vertx();
        vertx.createDatagramSocket().listen(mynode.getUdpPort(),mynode.getHost(), ar -> {
            if (!ar.succeeded()) {
                log.debug("初始化失败");
            }
            else log.debug("初始化成功");

            socket = ar.result();
            socket.exceptionHandler(this::handleException);
            socket.handler(this::handlePacket);
        });
        //todo:写在配置文件里 或者创建密码的时候生成

        // 定时执行的线程
        timeRefreshTable();


        if(!isbootnode){
            log.info("非种子节点");
            bootnodes.stream().filter(node -> peerTable.tryAdd(node).getOutcome() == PeerTable.AddResult.Outcome.ADDED).
                    forEach(node -> bond(node, true));

        }


    }

    @VisibleForTesting
    void bond(final DiscoveryPeer peer, final boolean bootstrap) {
        peer.setFirstDiscovered(System.currentTimeMillis());
        peer.setStatus(PeerDiscoveryStatus.BONDING);

        final Consumer<PeerInteractionState> action =
                interaction -> {
                    final PingPacketData data =
                            PingPacketData.create(myself.getEndpoint(), peer.getEndpoint());
                    final Packet sentPacket = sendPacket(peer, PacketType.PING, data);

                    final BytesValue pingHash = sentPacket.getHash();
                    // Update the matching filter to only accept the PONG if it echoes the hash of our PING.
                    final Predicate<Packet> newFilter =
                            packet ->
                                    packet
                                            .getPacketData(PongPacketData.class)
                                            .map(pong -> pong.getPingHash().equals(pingHash))
                                            .orElse(false);
                    interaction.updateFilter(newFilter);
                };

        // The filter condition will be updated as soon as the action is performed.
        final PeerInteractionState ping =
                new PeerInteractionState(action, PacketType.PONG, (packet) -> false, true, bootstrap);
        dispatchInteraction(peer, ping);
    }

    public Packet sendPacket(final DiscoveryPeer peer, final PacketType type, final PacketData data) {
        final Packet packet = Packet.create(type, data, keyPair);
//        log.info("Packet = {}",packet.toString());
//        log.info(
//                ">>> Sending {} discovery packet to {} ({}): {}",
//                type,
//                peer.getEndpoint(),
//                peer.getId().slice(0, 16),
//                packet);

        // Update the lastContacted timestamp on the peer if the dispatch succeeds.
        socket.send(
                packet.encode(),
                peer.getEndpoint().getUdpPort(),
                peer.getEndpoint().getHost(),
                ar -> {
                    if (ar.failed()) {
                        log.warn(
                                "Sending to peer {} failed, packet: {}",
                                peer,
                                wrapBuffer(packet.encode()),
                                ar.cause());
                        return;
                    }
                    if (ar.succeeded()) {
                        peer.setLastContacted(System.currentTimeMillis());
                        log.info("Packet发送成功");
                    }
                });

        return packet;
    }

    private void dispatchInteraction(final Peer peer, final PeerInteractionState state) {
        final PeerInteractionState previous = inflightInteractions.put(peer.getId(), state);
        log.info("inflightInteractions.put {} " ,peer.getId());
        if (previous != null) {
            previous.cancelTimers();
        }
        state.execute(0);
    }
    private void handleException(final Throwable exception) {
        if (exception instanceof IOException) {
            log.debug("Packet handler exception", exception);
        } else {
            log.error("Packet handler exception", exception);
        }
    }

    public void timeRefreshTable() {
        log.info("start RefreshTable");
        vertx.setPeriodic(
                Math.min(REFRESH_CHECK_INTERVAL_MILLIS, tableRefreshIntervalMs),
                (l) -> refreshTableIfRequired());
    }

    private void refreshTableIfRequired() {
        final long now = System.currentTimeMillis();
        if (lastRefreshTime + tableRefreshIntervalMs < now) {
            log.info("Peer table refresh triggered by timer expiry");
            refreshTable();
        }
    }

    private void refreshTable() {
        final BytesValue target = Peer.randomId();
        peerTable.nearestPeers(Peer.randomId(), 16).forEach((peer) -> findNodes(peer, target));
        lastRefreshTime = System.currentTimeMillis();
    }
    private void findNodes(final DiscoveryPeer peer, final BytesValue target) {
        final Consumer<PeerInteractionState> action =
                (interaction) -> {
                    final FindNeighborsPacketData data = FindNeighborsPacketData.create(target);
                    log.info("findNodes");
                    sendPacket(peer, PacketType.FIND_NEIGHBORS, data);
                };
        final PeerInteractionState interaction =
                new PeerInteractionState(action, PacketType.NEIGHBORS, packet -> true, true, false);
        dispatchInteraction(peer, interaction);
    }

    private void handlePacket(final DatagramPacket datagram) {
        try {
            final int length = datagram.data().length();
            checkGuard(
                    length <= MAX_PACKET_SIZE_BYTES,
                    PeerDiscoveryPacketDecodingException::new,
                    "Packet too large. Actual size (bytes): %s",
                    length);

            // We allow exceptions to bubble up, as they'll be picked up by the exception handler.
            final Packet packet = Packet.decode(datagram.data());

            OptionalInt fromPort = OptionalInt.empty();
            if (packet.getPacketData(PingPacketData.class).isPresent()) {
                final PingPacketData ping = packet.getPacketData(PingPacketData.class).orElseGet(null);
                if (ping != null && ping.getFrom() != null && ping.getFrom().getTcpPort().isPresent()) {
                    fromPort = ping.getFrom().getTcpPort();
                }
            }

            // Acquire the senders coordinates to build a Peer representation from them.
            final String addr = datagram.sender().host();
            final int port = datagram.sender().port();

            // Notify the peer controller.
            final DiscoveryPeer peer = new DiscoveryPeer(packet.getNodeId(), addr, port, fromPort);
            onMessage(packet, peer);
        } catch (final PeerDiscoveryPacketDecodingException e) {
            log.debug("Discarding invalid peer discovery packet", e);
        } catch (final Throwable t) {
            log.error("Encountered error while handling packet", t);
        }
    }

    public void onMessage(final Packet packet, final DiscoveryPeer sender) {
        log.info("Received Packet Type {}",packet.getType());
        // Message from self. This should not happen.
        if (sender.getId().equals(myself.getId())) {
            return;
        }



        // Load the peer from the table, or use the instance that comes in.
        final Optional<DiscoveryPeer> maybeKnownPeer = peerTable.get(sender);
        final DiscoveryPeer peer = maybeKnownPeer.orElse(sender);
        final boolean peerKnown = maybeKnownPeer.isPresent();

        switch (packet.getType()) {
            case PING:
                if (addToPeerTable(peer)) {
                    log.info("Table成功添加peer");
                    final PingPacketData ping = packet.getPacketData(PingPacketData.class).get();
                    respondToPing(ping, packet.getHash(), peer);
                }

                break;
            case PONG:
            {
                matchInteraction(packet)
                        .ifPresent(
                                interaction -> {
                                    log.info("Table成功添加peer");
                                    addToPeerTable(peer);

                                    // If this was a bootstrap peer, let's ask it for nodes near to us.
                                    // 如果是种子节点就询问对方离我最近的节点
                                    if (interaction.isBootstrap()) {
                                        log.info("interaction.isBootstrap() = {}" ,interaction.isBootstrap());
                                        findNodes(peer, myself.getId());
                                    }
                                });
                break;
            }
            case NEIGHBORS:

                matchInteraction(packet)
                        .ifPresent(
                                interaction -> {
                                    // Extract the peers from the incoming packet.
                                    log.info("enter NEIGHBORS");
                                    final List<DiscoveryPeer> neighbors =
                                            packet
                                                    .getPacketData(NeighborsPacketData.class)
                                                    .map(NeighborsPacketData::getNodes)
                                                    .orElse(emptyList());
                                    //向peerTable没有的peer发送ping消息
                                    for (final DiscoveryPeer neighbor : neighbors) {
                                        if (peerTable.get(neighbor).isPresent()) {
                                            log.info("peerTable had this peer");
                                            continue;
                                        }
                                        log.info("bond new peer");
                                        bond(neighbor, false);
                                    }
                                });
                break;

            case FIND_NEIGHBORS:
                //这里是fasle
//                log.info("peerKnown {}",peerKnown);
//                if (!peerKnown) {
//                    break;
//                }
                final FindNeighborsPacketData fn =
                        packet.getPacketData(FindNeighborsPacketData.class).get();
                respondToFindNeighbors(fn, peer);
                break;
        }
    }

    private void respondToPing(
            final PingPacketData packetData, final BytesValue pingHash, final DiscoveryPeer sender) {
        final PongPacketData data = PongPacketData.create(packetData.getFrom(), pingHash);
        sendPacket(sender, PacketType.PONG, data);
    }
    private void respondToFindNeighbors(
            final FindNeighborsPacketData packetData, final DiscoveryPeer sender) {
        // TODO: for now return 16 peers. Other implementations calculate how many
        // peers they can fit in a 1280-byte payload.
        final List<DiscoveryPeer> peers = peerTable.nearestPeers(packetData.getTarget(), 16);
        log.info("DiscoveryPeer number {}",peers.size());
        final PacketData data = NeighborsPacketData.create(peers);
        sendPacket(sender, PacketType.NEIGHBORS, data);
    }

    private boolean addToPeerTable(final DiscoveryPeer peer) {
        final PeerTable.AddResult result = peerTable.tryAdd(peer);
        if (result.getOutcome() == PeerTable.AddResult.Outcome.SELF) {
            return false;
        }

        // Reset the last seen timestamp.
        final long now = System.currentTimeMillis();
        if (peer.getFirstDiscovered() == 0) {
            peer.setFirstDiscovered(now);
        }
        peer.setLastSeen(now);

        if (peer.getStatus() != PeerDiscoveryStatus.BONDED) {
            peer.setStatus(PeerDiscoveryStatus.BONDED);
            notifyPeerBonded(peer, now);
        }

        if (result.getOutcome() == PeerTable.AddResult.Outcome.ALREADY_EXISTED) {
            // Bump peer.
            peerTable.evict(peer);
            peerTable.tryAdd(peer);
        } else if (result.getOutcome() == PeerTable.AddResult.Outcome.BUCKET_FULL) {
            peerTable.evict(result.getEvictionCandidate());
            peerTable.tryAdd(peer);
        }

        return true;
    }

    private void notifyPeerBonded(final DiscoveryPeer peer, final long now) {
        final PeerDiscoveryEvent.PeerBondedEvent event = new PeerDiscoveryEvent.PeerBondedEvent(peer, now);
        dispatchEvent(peerBondedObservers, event);
    }

    private class PeerInteractionState implements Predicate<Packet> {
        /**
         * The action that led to the peer being in this state (e.g. sending a PING or NEIGHBORS
         * message), in case it needs to be retried.
         */
        private final Consumer<PeerInteractionState> action;
        /**
         * The expected type of the message that will transition the peer out of this state.
         */
        private final PacketType expectedType;
        /**
         * A custom filter to accept transitions out of this state.
         */
        private Predicate<Packet> filter;
        /**
         * Whether the action associated to this state is retryable or not.
         */
        private final boolean retryable;
        /**
         * Whether this is an entry for a bootstrap peer.
         */
        private final boolean bootstrap;
        /**
         * Timers associated with this entry.
         */
        private OptionalLong timerId = OptionalLong.empty();

        PeerInteractionState(
                final Consumer<PeerInteractionState> action,
                final PacketType expectedType,
                final Predicate<Packet> filter,
                final boolean retryable,
                final boolean bootstrap) {
            this.action = action;
            this.expectedType = expectedType;
            this.filter = filter;
            this.retryable = retryable;
            this.bootstrap = bootstrap;
        }

        @Override
        public boolean test(final Packet packet) {
            return expectedType == packet.getType() && (filter == null || filter.test(packet));
        }

        void updateFilter(final Predicate<Packet> filter) {
            this.filter = filter;
        }

        boolean isBootstrap() {
            return bootstrap;
        }

        /**
         * Executes the action associated with this state. Sets a "boomerang" timer to itself in case
         * the action is retryable.
         *
         * @param lastTimeout the previous timeout, or 0 if this is the first time the action is being
         *                    executed.
         */
        void execute(final long lastTimeout) {
            action.accept(this);
            if (retryable) {
                final long newTimeout = retryDelayFunction.apply(lastTimeout);
                timerId = OptionalLong.of(vertx.setTimer(newTimeout, id -> execute(newTimeout)));
            }
        }

        /**
         * Cancels any timers associated with this entry.
         */
        void cancelTimers() {
            timerId.ifPresent(vertx::cancelTimer);
        }
    }
    private <T extends PeerDiscoveryEvent> void dispatchEvent(
            final Subscribers<Consumer<T>> observers, final T event) {
        observers.forEach(
                observer ->
                        vertx.executeBlocking(
                                future -> {
                                    observer.accept(event);
                                    future.complete();
                                },
                                x -> {}));
    }
    private Optional<PeerInteractionState> matchInteraction(final Packet packet) {
        final PeerInteractionState interaction = inflightInteractions.get(packet.getNodeId());
        log.info("packet.getNodeId() = {}",packet.getNodeId());
        if (interaction == null || !interaction.test(packet)) {
            log.info("interaction == null = {}",interaction == null);
            log.info("return Optional.empty()");
            return Optional.empty();
        }

        interaction.cancelTimers();
        inflightInteractions.remove(packet.getNodeId());
        return Optional.of(interaction);
    }
}

