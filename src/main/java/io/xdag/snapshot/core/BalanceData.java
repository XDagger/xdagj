package io.xdag.snapshot.core;

import com.google.common.primitives.UnsignedLong;
import io.xdag.net.handler.Xdag;
import io.xdag.utils.BytesUtils;
import io.xdag.utils.XdagTime;
import lombok.Data;
import org.apache.commons.lang3.time.FastDateFormat;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

import java.nio.ByteOrder;

import static io.xdag.utils.BasicUtils.*;

@Data
public class BalanceData {
    long amount;
    long time;
    // we dont need storage_pos
    long storage_pos;
    byte[] hash;

    public BalanceData(){

    }

    public BalanceData(UnsignedLong amount, UnsignedLong time, UnsignedLong storage_pos, Bytes32 hash) {
        this.amount = amount.longValue();
        this.time = time.longValue();
        this.storage_pos = storage_pos.longValue();
        this.hash = hash.toArray();
    }

    public static BalanceData parse(Bytes key, Bytes value) {
        if (key.size() == 32) {
            UnsignedLong amount = UnsignedLong.valueOf(value.getLong(0, ByteOrder.LITTLE_ENDIAN));
            UnsignedLong time = UnsignedLong.valueOf(value.getLong(8, ByteOrder.LITTLE_ENDIAN));
            UnsignedLong storage_pos = UnsignedLong.valueOf(value.getLong(16, ByteOrder.LITTLE_ENDIAN));
            Bytes32 hash = Bytes32.wrap(key.reverse());
            return new BalanceData(amount, time, storage_pos, hash);
        }
        return null;
    }

    @Override
    public String toString() {
        return "BalanceData{" +
                "amount=" + amount2xdag(amount) +
                ", time=" + FastDateFormat.getInstance("yyyy-MM-dd HH:mm:ss.SSS").format(XdagTime.xdagTimestampToMs(time)) +
                ", storage_pos=" + storage_pos +
                ", hash=" + (hash!=null?hash2Address(hash):"") +
                '}';
    }
}
