package io.xdag.config;

import com.google.common.primitives.UnsignedLong;
import static io.xdag.core.XdagField.FieldType.XDAG_FIELD_HEAD_TEST;

public class DevnetConfig extends AbstractConfig {

    public DevnetConfig() {
        super("testnet", "xdag-devnet.config");
        this.whitelistUrl = "https://raw.githubusercontent.com/XDagger/xdag/master/client/netdb-white-testnet.txt";

        this.xdagEra = 0x16900000000L;
        this.mainStartAmount = UnsignedLong.fromLongBits(1L << 42).longValue();

        this.apolloForkHeight = 196250;
        this.apolloForkAmount = UnsignedLong.fromLongBits(1L << 39).longValue();
        this.xdagFieldHeader = XDAG_FIELD_HEAD_TEST;

        this.dnetKeyFile = "dnet_keys.bin";
        this.walletKeyFile = "wallet.dat";
    }

}
