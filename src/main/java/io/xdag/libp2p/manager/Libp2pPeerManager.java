//package io.xdag.libp2p.manager;
//
//import io.libp2p.core.Connection;
//import io.libp2p.core.ConnectionHandler;
//import io.libp2p.core.PeerId;
//import lombok.extern.slf4j.Slf4j;
//import org.jetbrains.annotations.NotNull;
//
//import java.time.Duration;
//import java.util.concurrent.ScheduledExecutorService;
//
////啥也没干啊
//@Slf4j
//public class Libp2pPeerManager implements ConnectionHandler {
//
//    private final ScheduledExecutorService scheduler;
//    private static final Duration RECONNECT_TIMEOUT = Duration.ofSeconds(5);
//    public Libp2pPeerManager(ScheduledExecutorService scheduler) {
//        this.scheduler = scheduler;
//    }
//
//
//    @Override
//    public void handleConnection(@NotNull final Connection connection) {
//        final PeerId remoteId = connection.secureSession().getRemoteId();
//        log.debug( "Got new connection from " + remoteId);
//        connection.closeFuture().thenRun(() -> log.debug( "Peer disconnected: " + remoteId));
//
//    }
//
//}
