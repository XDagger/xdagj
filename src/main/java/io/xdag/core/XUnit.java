package io.xdag.core;

import static java.util.Arrays.stream;
import java.math.BigInteger;

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
