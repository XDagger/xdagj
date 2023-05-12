package io.xdag.core;

import static java.util.Arrays.stream;
import java.math.BigInteger;

/**
 *  XUnit
 *
 * NANO_XDAG	   1 NANO_XDAG	1	          10^-9 XDAG
 * MICRO_XDAG	10^3 NANO_XDAG	1,000	      10^-6 XDAG
 * MILLI_XDAG	10^6 NANO_XDAG	1,000,000	  10^-3 XDAG
 * XDAG	        10^9 NANO_XDAG	1,000,000,000	  1 XDAG
 */
public enum XUnit {

    NANO_XDAG(0, "nXDAG"),

    MICRO_XDAG(3, "μXDAG"),

    MILLI_XDAG(6, "mXDAG"),

    XDAG(9, "XDAG");

    public final int exp;
    public final long factor;
    public final String symbol;

    XUnit(int exp, String symbol) {
        this.exp = exp;
        this.factor = BigInteger.TEN.pow(exp).longValueExact();
        this.symbol = symbol;
    }

    /**
     * Decode the unit from symbol.
     *
     * @param symbol
     *            the symbol text
     * @return a Unit object if valid; otherwise false
     */
    public static XUnit of(String symbol) {
        return stream(values()).filter(v -> v.symbol.equals(symbol)).findAny().orElse(null);
    }

}