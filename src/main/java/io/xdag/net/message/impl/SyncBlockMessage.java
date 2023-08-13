package io.xdag.net.message.impl;

import io.xdag.core.Block;
import io.xdag.core.XdagBlock;
import static io.xdag.config.Constants.DNET_PKT_XDAG;
import io.xdag.net.message.Message;
import io.xdag.net.message.XdagMessageCodes;
import io.xdag.utils.BytesUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.MutableBytes;

import java.util.zip.CRC32;

@Slf4j
public class SyncBlockMessage extends Message {
    private XdagBlock xdagBlock;
    private Block block;
    private int ttl;


    /**
     * 不处理crc
     */
    public SyncBlockMessage(MutableBytes bytes) {
        super(bytes);
    }

    /**
     * 处理crc 创建新的用于发送Block的message
     */
    public SyncBlockMessage(Block block, int ttl) {
        this.block = block;
        this.ttl = ttl;
        this.parsed = true;
        encode();
    }

    /**
     * 不处理crc
     */
    public SyncBlockMessage(XdagBlock xdagBlock, int ttl) {
        super(xdagBlock.getData().mutableCopy());
        this.xdagBlock = xdagBlock;
        this.ttl = ttl;
    }

    public Block getBlock() {
        parse();
        return block;
    }

    private void parse() {
        if (parsed) {
            return;
        }
        block = new Block(xdagBlock);
        parsed = true;
    }

    private void encode() {
        this.encoded = this.block.getXdagBlock().getData().mutableCopy();
        // (1L << 31):Used to distinguish between newBlockMessage and syncBlockMessage.
        long transportheader = ((long) ttl << 8) | DNET_PKT_XDAG | (512 << 16) | (1L << 31);
        this.encoded.set(0, Bytes.wrap(BytesUtils.longToBytes(transportheader, true)));

        updateCrc();
    }

    public void updateCrc() {
        CRC32 crc32 = new CRC32();
        crc32.update(encoded.toArray(), 0, 512);
        this.encoded.set(4, Bytes.wrap(BytesUtils.intToBytes((int) crc32.getValue(), true)));
    }

    public int getTtl() {
        return ttl;
    }

    public void setTtl(int ttl) {
        this.ttl = ttl;
    }

    @Override
    public Bytes getEncoded() {
        return encoded;
    }

    @Override
    public Class<?> getAnswerMessage() {
        return null;
    }

    @Override
    public XdagMessageCodes getCommand() {
        return XdagMessageCodes.SYNC_BLOCK;
    }

    @Override
    public String toString() {
        return "NewBlock Message:" + encoded.toHexString();
    }
}
