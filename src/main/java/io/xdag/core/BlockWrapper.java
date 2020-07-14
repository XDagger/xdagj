package io.xdag.core;

import io.xdag.net.node.Node;

public class BlockWrapper {

    private Block block;
    private int ttl;
    /** 记录区块接收节点 */
    private Node remoteNode;
    // private boolean isTransaction = false;

    public BlockWrapper(Block block, int ttl, Node remoteNode) {
        this.block = block;
        this.ttl = ttl;
        this.remoteNode = remoteNode;
    }

    public Block getBlock() {
        return block;
    }

    public void setBlock(Block block) {
        this.block = block;
    }

    public int getTtl() {
        return ttl;
    }

    public void setTtl(int ttl) {
        this.ttl = ttl;
    }

    public Node getRemoteNode() {
        return remoteNode;
    }

    public void setRemoteNode(Node remoteNode) {
        this.remoteNode = remoteNode;
    }
}
