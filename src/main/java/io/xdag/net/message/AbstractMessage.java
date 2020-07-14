package io.xdag.net.message;

import static io.xdag.config.Constants.DNET_PKT_XDAG;
import static io.xdag.core.XdagBlock.XDAG_BLOCK_SIZE;
import static io.xdag.core.XdagField.FieldType.XDAG_FIELD_NONCE;
import static io.xdag.net.message.XdagMessageCodes.SUMS_REPLY;

import io.xdag.utils.BytesUtils;
import java.math.BigInteger;
import java.util.zip.CRC32;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.util.encoders.Hex;

@EqualsAndHashCode(callSuper = false)
@Data
public abstract class AbstractMessage extends Message {
    protected long starttime;
    protected long endtime;
    protected long random;
    protected byte[] hash;
    /** 获取对方节点的netstatus */
    protected NetStatus netStatus;
    /** 获取对方节点的netdb */
    protected NetDB netDB;
    protected XdagMessageCodes codes;
    Logger logger = LoggerFactory.getLogger(AbstractMessage.class);

    public AbstractMessage(
            XdagMessageCodes type, long starttime, long endtime, long random, NetStatus netStatus) {
        parsed = true;
        this.starttime = starttime;
        this.endtime = endtime;
        this.random = random;
        this.netStatus = netStatus;
        this.codes = type;
        encode();
    }

    public AbstractMessage(
            XdagMessageCodes type, long starttime, long endtime, byte[] hash, NetStatus netStatus) {
        parsed = true;
        this.starttime = starttime;
        this.endtime = endtime;
        this.hash = hash;
        this.netStatus = netStatus;
        this.codes = type;
        encode();
        updateCrc();
    }

    public AbstractMessage(byte[] data) {
        super(data);
        parse();
    }

    public NetStatus getNetStatus() {
        return netStatus;
    }

    public NetDB getNetDB() {
        return netDB;
    }

    public void parse() {
        if (parsed) {
            return;
        }
        starttime = BytesUtils.bytesToLong(encoded, 16, true);
        endtime = BytesUtils.bytesToLong(encoded, 24, true);
        random = BytesUtils.bytesToLong(encoded, 32, true);
        BigInteger maxdifficulty = BytesUtils.bytesToBigInteger(encoded, 80, true);
        long totalnblocks = BytesUtils.bytesToLong(encoded, 104, true);
        long totalnmains = BytesUtils.bytesToLong(encoded, 120, true);
        int totalnhosts = BytesUtils.bytesToInt(encoded, 132, true);
        long maintime = BytesUtils.bytesToLong(encoded, 136, true);
        netStatus = new NetStatus(maxdifficulty, totalnblocks, totalnmains, totalnhosts, maintime);

        // test netdb
        int length = getCommand() == SUMS_REPLY ? 6 : 14;
        // 80 是sizeof(xdag_stats)
        byte[] netdb = new byte[length * 32 - 80];
        System.arraycopy(encoded, 144, netdb, 0, length * 32 - 80);
        netDB = new NetDB(netdb);

        parsed = true;
    }

    public void encode() {
        parsed = true;
        encoded = new byte[512];
        int ttl = 1;
        long transportheader = (ttl << 8) | DNET_PKT_XDAG | (XDAG_BLOCK_SIZE << 16);
        long type = (codes.asByte() << 4) | XDAG_FIELD_NONCE.asByte();

        BigInteger diff = netStatus.difficulty;
        BigInteger maxDiff = netStatus.maxdifficulty;
        long nmain = netStatus.nmain;
        long totalMainNumber = netStatus.totalnmain;
        long nblocks = netStatus.nblocks;
        long totalBlockNumber = netStatus.totalnblocks;

        // TODO：后续根据ip替换
        String tmp = "04000000040000003ef4780100000000" + "7f000001611e7f000001b8227f0000015f767f000001d49d";
        // net 相关
        byte[] tmpbyte = Hex.decode(tmp);

        // add netdb
        // byte[] iplist = netDB.encode(netDB.getActiveIP());

        // field 0 and field1
        byte[] first = BytesUtils.merge(
                BytesUtils.longToBytes(transportheader, true),
                BytesUtils.longToBytes(type, true),
                BytesUtils.longToBytes(starttime, true),
                BytesUtils.longToBytes(endtime, true));
        System.arraycopy(first, 0, encoded, 0, 32);
        System.arraycopy(BytesUtils.longToBytes(random, true), 0, encoded, 32, 8);

        // field2 diff and maxdiff
        System.arraycopy(BytesUtils.bigIntegerToBytes(diff, 16, true), 0, encoded, 64, 16);
        System.arraycopy(BytesUtils.bigIntegerToBytes(maxDiff, 16, true), 0, encoded, 80, 16);

        // field3 nblock totalblock main totalmain
        System.arraycopy(BytesUtils.longToBytes(nblocks, true), 0, encoded, 96, 8);
        System.arraycopy(BytesUtils.longToBytes(totalBlockNumber, true), 0, encoded, 104, 8);
        System.arraycopy(BytesUtils.longToBytes(nmain, true), 0, encoded, 112, 8);
        System.arraycopy(BytesUtils.longToBytes(totalMainNumber, true), 0, encoded, 120, 8);

        System.arraycopy(tmpbyte, 0, encoded, 128, tmpbyte.length);
    }

    public void updateCrc() {
        CRC32 crc32 = new CRC32();
        crc32.update(encoded, 0, 512);
        System.arraycopy(BytesUtils.intToBytes((int) crc32.getValue(), true), 0, encoded, 4, 4);
    }
}
