package io.xdag.core;

import org.apache.tuweni.units.bigints.UInt64;
import org.junit.Test;

import java.math.BigDecimal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import static io.xdag.core.XUnit.NANO_XDAG;
import static io.xdag.core.XUnit.MICRO_XDAG;
import static io.xdag.core.XUnit.MILLI_XDAG;
import static io.xdag.core.XUnit.XDAG;
import static io.xdag.core.XAmount.ZERO;

public class XAmountTest {

    @Test
    public void testUnits() {
        assertEquals(ZERO, ZERO);
        assertEquals(XAmount.of(1), XAmount.of(1));
        assertEquals(XAmount.of(1000), XAmount.of(1, MICRO_XDAG));
        assertEquals(XAmount.of(1000, MICRO_XDAG), XAmount.of(1, MILLI_XDAG));
        assertEquals(XAmount.of(1000, MILLI_XDAG), XAmount.of(1, XDAG));
    }

    @Test
    public void testFromDecimal() {
        assertEquals(ZERO, XAmount.of(BigDecimal.ZERO, XDAG));
        assertEquals(XAmount.of(10, XDAG), XAmount.of(new BigDecimal("10.000"), XDAG));
    }

    @Test
    public void testFromSymbol() {
        assertEquals(NANO_XDAG, XUnit.of("nXDAG"));
        assertEquals(MICRO_XDAG, XUnit.of("μXDAG"));
        assertEquals(MILLI_XDAG, XUnit.of("mXDAG"));
        assertEquals(XDAG, XUnit.of("XDAG"));
    }

    @Test
    public void testFromSymbolUnknown() {
        assertNull(XUnit.of("???"));
    }

    @Test
    public void testToDecimal() {
        assertEquals(new BigDecimal("0"), ZERO.toDecimal(0, XDAG));
        assertEquals(new BigDecimal("0.000"), ZERO.toDecimal(3, XDAG));

        XAmount oneXdag = XAmount.of(1, XDAG);
        assertEquals(new BigDecimal("1.000"), oneXdag.toDecimal(3, XDAG));
        assertEquals(new BigDecimal("1000.000"), oneXdag.toDecimal(3, MILLI_XDAG));
    }

    @Test
    public void testCompareTo() {
        assertEquals(ZERO.compareTo(ZERO), 0);
        assertEquals(XAmount.of(1000).compareTo(XAmount.of(1, MICRO_XDAG)), 0);

        assertEquals(XAmount.of(10).compareTo(XAmount.of(10)), 0);
        assertEquals(XAmount.of(5).compareTo(XAmount.of(10)), -1);
        assertEquals(XAmount.of(10).compareTo(XAmount.of(5)), 1);
    }

    @Test
    public void testHashCode() {
        assertEquals(ZERO.hashCode(), XAmount.of(0, XDAG).hashCode());
        assertEquals(XAmount.of(999, XDAG).hashCode(), XAmount.of(999, XDAG).hashCode());
    }

    @Test
    public void testGtLtEtc() {
        assertTrue(XAmount.of(19, XDAG).isPositive());
        assertTrue(XAmount.of(-9, XDAG).isNegative());
        assertFalse(ZERO.isPositive());
        assertFalse(ZERO.isNegative());

        assertTrue(ZERO.isNotNegative());
        assertTrue(ZERO.isNotPositive());
        assertFalse(XAmount.of(-9, XDAG).isNotNegative());
        assertFalse(XAmount.of(99, XDAG).isNotPositive());

        assertTrue(XAmount.of(999, XDAG).greaterThan(XAmount.of(999, MILLI_XDAG)));
        assertTrue(XAmount.of(999, XDAG).greaterThanOrEqual(XAmount.of(999, MILLI_XDAG)));
        assertFalse(XAmount.of(999, XDAG).lessThan(XAmount.of(999, MILLI_XDAG)));
        assertFalse(XAmount.of(999, XDAG).lessThanOrEqual(XAmount.of(999, MILLI_XDAG)));
    }

    @Test
    public void TestAmount2xdag() {
        assertEquals(972.80, XAmount.ofXAmount(4178144185548L).toDecimal(2, XDAG).doubleValue(), 0.0);

        // 3333333334
        assertEquals(51.20, XAmount.ofXAmount(219902325556L).toDecimal(2, XDAG).doubleValue(), 0.0);

        // 400 0000 0000?
        assertEquals(1.49, XAmount.ofXAmount(6400000000L).toDecimal(2, XDAG).doubleValue(), 0.0);

        // Xfer:transferred   44796588980   10.430000000 XDAG to the address 0000002f28322e9d817fd94a1357e51a. 10.43
        assertEquals(10.43, XAmount.ofXAmount(44796588980L).toDecimal(2, XDAG).doubleValue(), 0.0);

        // Xfer:transferred   42949672960   10.000000000 XDAG to the address 0000002f28322e9d817fd94a1357e51a. 10.00
        assertEquals(10.00, XAmount.ofXAmount(42949672960L).toDecimal(2, XDAG).doubleValue(), 0.0);

        // Xfer:transferred 4398046511104 1024.000000000 XDAG to the address 0000002f28322e9d817fd94a1357e51a. 1024.00
        assertEquals(1024.00, XAmount.ofXAmount(4398046511104L).toDecimal(2, XDAG).doubleValue(), 0.0);
    }

    @Test
    public void testXdag2amount() {
        BigDecimal a = BigDecimal.valueOf(972.80);
        assertEquals(UInt64.valueOf(4178144185549L), XAmount.of(a, XDAG).toXAmount());

        BigDecimal b = BigDecimal.valueOf(51.20);
        assertEquals(UInt64.valueOf(219902325556L), XAmount.of(b, XDAG).toXAmount());

        BigDecimal c = BigDecimal.valueOf(100.00);
        assertEquals(UInt64.valueOf(429496729600L), XAmount.of(c, XDAG).toXAmount());
    }

    @Test
    public void testXAmountStringFormat() {
        XAmount amount = XAmount.of(0, XDAG);
        assertEquals("0.000000000", amount.toDecimal(9, XDAG).toPlainString());
        assertEquals("0E-9", amount.toDecimal(9, XDAG).toString());
    }

}
