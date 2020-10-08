package io.xdag.config;

import com.google.common.primitives.UnsignedLong;
import io.xdag.utils.BasicUtils;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.junit.Assert.assertEquals;

public class ConstantsTest {
    @Test
    public void testMainStartAmount() {
        UnsignedLong startAmount = UnsignedLong.fromLongBits(1L << 42);
        UnsignedLong apolloStartAmount = UnsignedLong.fromLongBits(1L << 39);
        assertEquals(Constants.MAIN_START_AMOUNT, startAmount.longValue());
        assertEquals(Constants.MAIN_APOLLO_AMOUNT, apolloStartAmount.longValue());
        assertEquals(Constants.MAIN_START_AMOUNT, startAmount.longValue());
        assertEquals(String.valueOf(BasicUtils.amount2xdag(Constants.MAIN_START_AMOUNT)), "1024.0");
        assertEquals(String.valueOf(BasicUtils.amount2xdag(Constants.MAIN_APOLLO_AMOUNT)), "128.0");
    }

}
