//package io.xdag.libp2p;
//
//import com.google.common.util.concurrent.ThreadFactoryBuilder;
//import io.libp2p.core.Host;
//import io.libp2p.core.PeerId;
//import io.libp2p.core.dsl.HostBuilder;
//import io.libp2p.core.multiformats.Multiaddr;
//import io.libp2p.discovery.MDnsDiscovery;
//import io.xdag.Kernel;
//import io.xdag.libp2p.Handler.Handler;
//import io.xdag.libp2p.Utils.IpUtil;
//import io.xdag.libp2p.manager.Libp2pPeerManager;
//import kotlin.Unit;
//import lombok.extern.slf4j.Slf4j;
//
//import java.net.InetAddress;
//import java.util.*;
//import java.util.concurrent.Executors;
//import java.util.concurrent.ScheduledExecutorService;
//
//@Slf4j
//public class jvmLibp2pNetwork {
//    public Host host;
//    private final InetAddress privateAddress = IpUtil.getLocalAddress();
//    private final Set<Libp2pNode> peers = new HashSet<>();
//    private final Map<PeerId, Libp2pNode> knownNodes = Collections.synchronizedMap(new HashMap<>());
//    Libp2pNode libp2pNode = new Libp2pNode();
//    private final ScheduledExecutorService scheduler;
//    Libp2pPeerManager peerManager;
//    Handler handler;
//    //todo:随机数端口改成kernel定义
//    Random random=new Random();
//    int port ;
//    public jvmLibp2pNetwork(){
//        port = 10012;
//        InetAddress privateAddress = IpUtil.getLocalAddress();
//        scheduler = Executors.newSingleThreadScheduledExecutor(
//                new ThreadFactoryBuilder().setDaemon(true).setNameFormat("libp2p-%d").build());
//        peerManager = new Libp2pPeerManager(scheduler);
//
//        host = new HostBuilder().listen("/ip4/" + privateAddress.getHostAddress() + "/tcp/" + port).build();
//        host.addConnectionHandler(peerManager);
//    }
//    public jvmLibp2pNetwork(Kernel kernel) {
//        port = kernel.getConfig().getLibp2pport();
//        InetAddress privateAddress = IpUtil.getLocalAddress();
//        scheduler = Executors.newSingleThreadScheduledExecutor(
//                new ThreadFactoryBuilder().setDaemon(true).setNameFormat("libp2p-%d").build());
//        peerManager = new Libp2pPeerManager(scheduler);
//        handler = new Handler(kernel);
//
//        host = new HostBuilder().protocol(handler).listen("/ip4/" + privateAddress.getHostAddress() + "/tcp/" + port).build();
//        host.addConnectionHandler(peerManager);
//
//    }
//    //把发现网络抽离
//    //todo :修改
//    public void start(){
//        try {
//            host.start().get();
//            log.info("Node started and listening on ");
//            log.info(host.listenAddresses().toString());
//            String serviceTag = "_ipfs-discovery._udp";
//            String serviceTagLocal = serviceTag + ".local.";
//            int queryInterval = 6000;
//            MDnsDiscovery peerFinder = new MDnsDiscovery(host, serviceTagLocal, queryInterval, privateAddress);
//            peerFinder.getNewPeerFoundListeners().add(peerInfo -> {
//                System.out.println("find peer : " + peerInfo.getPeerId().toString());
//                Unit u = Unit.INSTANCE;
//
//                if (!peerInfo.getAddresses().toString().contains(this.getAddress()) && !knownNodes.containsKey(peerInfo.getPeerId())) {
//
//
//                    libp2pNode.setPeerInfo(peerInfo);
//
//                    knownNodes.put(peerInfo.getPeerId(), libp2pNode);
//                    peers.add(libp2pNode);
//
//
//                    String ip = peerInfo.getAddresses().toString() + "/ipfs/" +
//                            peerInfo.getPeerId().toString();
//                    ip = ip.replace("[", "").replace("]", "");
//                    System.out.println(ip);
//                    Multiaddr address = Multiaddr.fromString(ip);
//                    handler.dial(this.host, address);
//
//
//                }
//                return u;
//            });
//            peerFinder.start();
//            log.info("Peer finder started ");
//        }catch(Exception e){
//            e.printStackTrace();
//        }
//    }
//    public void stop(){
//        host.stop();
//        scheduler.shutdownNow();
//    }
//
//    public Handler getHandler(){
//        return handler;
//    }
//
//    public void connect(Multiaddr address){
//        handler.dial(this.host, address);
//    }
//
//    public Host getNode(){
//        return host;
//    }
//
//    public String getAddress(){
//        return "/ip4/"+privateAddress.getHostAddress()+
//                "/tcp/"+port;
//    }
//
//}
//
