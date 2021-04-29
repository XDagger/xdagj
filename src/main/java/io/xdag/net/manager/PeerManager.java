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
package io.xdag.net.manager;

import io.libp2p.core.Connection;
import io.libp2p.core.ConnectionHandler;
import io.libp2p.core.PeerId;
import io.xdag.net.libp2p.peer.Libp2pNodeId;
import io.xdag.net.libp2p.peer.NodeId;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class PeerManager implements ConnectionHandler {


    private final ConcurrentHashMap<NodeId, Integer> connectedPeerMap = new ConcurrentHashMap<>();

    Integer num = Integer.signum(0);
    public PeerManager() {

    }

    @Override
    public void handleConnection(@NotNull final Connection connection) {
        final PeerId remoteId = connection.secureSession().getRemoteId();
        NodeId nodeId = new Libp2pNodeId(connection.secureSession().getRemoteId());
        connectedPeerMap.putIfAbsent(nodeId,num);
        log.debug( "Got new connection from " + remoteId);
        connection.closeFuture().thenRun(() ->
                log.debug( "Peer disconnected: " + remoteId)
        );
        if(connection.closeFuture().isDone()){
            connectedPeerMap.remove(nodeId);
        }
    }

    public int getPeerCount() {
        return connectedPeerMap.size();
    }

    public List<NodeId> getConnectNodeId(){
        List<NodeId> nodeIdList = new ArrayList<>();
        connectedPeerMap.forEach((a,b)->nodeIdList.add(a));
        return nodeIdList;
    }
}
