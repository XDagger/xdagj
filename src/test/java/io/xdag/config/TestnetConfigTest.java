package io.xdag.config;

import org.junit.Before;
import org.junit.Test;

import static io.xdag.core.XdagField.FieldType.XDAG_FIELD_HEAD_TEST;
import static io.xdag.utils.BasicUtils.amount2xdag;
import static org.junit.Assert.assertEquals;

public class TestnetConfigTest {

    private Config config;
    private String whitelistUrl;

    @Before
    public void setUp() {
        config = new TestnetConfig();
        whitelistUrl = "https://raw.githubusercontent.com/XDagger/xdag/master/client/netdb-white-testnet.txt";
    }

    @Test
    public void testParams() {
        assertEquals("testnet", config.getRootDir());
        assertEquals("xdag-testnet.config", config.getConfigName());
        assertEquals(whitelistUrl, config.getNodeSpec().getWhitelistUrl());
        assertEquals(0x16900000000L, config.getXdagEra());
        assertEquals(XDAG_FIELD_HEAD_TEST, config.getXdagFieldHeader());
        assertEquals(196250, config.getApolloForkHeight());
        assertEquals("1024.0", String.valueOf(amount2xdag(config.getMainStartAmount())));
        assertEquals("128.0", String.valueOf(amount2xdag(config.getApolloForkAmount())));
    }

}
