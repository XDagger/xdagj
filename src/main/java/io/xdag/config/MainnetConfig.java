package io.xdag.config;

import com.google.common.primitives.UnsignedLong;
import static io.xdag.core.XdagField.FieldType.XDAG_FIELD_HEAD;

public class MainnetConfig extends AbstractConfig {

    public MainnetConfig() {
        super("mainnet", "xdag-mainnet.config");
        this.whitelistUrl = "https://raw.githubusercontent.com/XDagger/xdag/master/client/netdb-white.txt";

        this.dnetKeyFile = "dnet_keys.bin";
        this.xdagEra = 0x16940000000L;
        this.mainStartAmount = UnsignedLong.fromLongBits(1L << 42).longValue();

        this.apolloForkHeight = 1017323;
        this.apolloForkAmount = UnsignedLong.fromLongBits(1L << 39).longValue();
        this.xdagFieldHeader = XDAG_FIELD_HEAD;

        this.dnetKeyFile = "dnet_keys.bin";
        this.walletKeyFile = "wallet.dat";
    }

}
