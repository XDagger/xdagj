package io.xdag.config;

import org.apache.commons.lang3.StringUtils;
import org.junit.Before;
import org.junit.Test;

import static io.xdag.core.XdagField.FieldType.XDAG_FIELD_HEAD_TEST;
import static io.xdag.utils.BasicUtils.amount2xdag;
import static org.junit.Assert.*;

public class DevnetConfigTest {

    private Config config;

    @Before
    public void setUp() {
        config = new DevnetConfig();
    }

    @Test
    public void testParams() {
        assertEquals("devnet", config.getRootDir());
        assertEquals("xdag-devnet.config", config.getConfigName());
        assertEquals(StringUtils.EMPTY, config.getNodeSpec().getWhitelistUrl());
        assertEquals(0x16900000000L, config.getXdagEra());
        assertEquals(XDAG_FIELD_HEAD_TEST, config.getXdagFieldHeader());
        assertEquals(1000, config.getApolloForkHeight());
        assertEquals("1024.0", String.valueOf(amount2xdag(config.getMainStartAmount())));
        assertEquals("128.0", String.valueOf(amount2xdag(config.getApolloForkAmount())));
    }
}
