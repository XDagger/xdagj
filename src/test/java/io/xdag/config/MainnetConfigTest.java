package io.xdag.config;

import org.junit.Before;
import org.junit.Test;

import static io.xdag.core.XdagField.FieldType.XDAG_FIELD_HEAD;
import static io.xdag.utils.BasicUtils.amount2xdag;
import static org.junit.Assert.assertEquals;

public class MainnetConfigTest {

    private Config config;
    private String whitelistUrl;

    @Before
    public void setUp() {
        config = new MainnetConfig();
        whitelistUrl = "https://raw.githubusercontent.com/XDagger/xdag/master/client/netdb-white.txt";
    }

    @Test
    public void testParams() {
        assertEquals("mainnet", config.getRootDir());
        assertEquals("xdag-mainnet.config", config.getConfigName());
        assertEquals(whitelistUrl, config.getNodeSpec().getWhitelistUrl());
        assertEquals(0x16940000000L, config.getXdagEra());
        assertEquals(XDAG_FIELD_HEAD, config.getXdagFieldHeader());
        assertEquals(1017323, config.getApolloForkHeight());
        assertEquals("1024.0", String.valueOf(amount2xdag(config.getMainStartAmount())));
        assertEquals("128.0", String.valueOf(amount2xdag(config.getApolloForkAmount())));
    }

}
