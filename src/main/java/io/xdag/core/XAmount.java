package io.xdag.core;

import io.xdag.utils.BasicUtils;

import java.math.BigDecimal;

import static java.math.RoundingMode.FLOOR;
import static java.math.RoundingMode.HALF_UP;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt64;

public class XAmount implements Comparable<XAmount> {

    public static final XAmount ZERO = new XAmount(0);
    public static final XAmount ONE = new XAmount(1);
    public static final XAmount TEN = new XAmount(10);

    private final long nano;

    private XAmount(long nano) {
        this.nano = nano;
    }

    public static XAmount of(long n) {
        return new XAmount(n);
    }

    public static XAmount of(String n) {
        return new XAmount(Long.parseLong(n));
    }

    public static XAmount of(Bytes bytes) {
        return new XAmount(bytes.toLong());
    }

    public static XAmount of(long n, XUnit unit) throws ArithmeticException {
        return new XAmount(Math.multiplyExact(n, unit.factor));
    }

    public static XAmount of(BigDecimal d, XUnit unit) {
        return new XAmount(d.movePointRight(unit.exp).setScale(0, FLOOR).longValueExact());
    }

    public BigDecimal toDecimal(int scale, XUnit unit) {
        BigDecimal nano = BigDecimal.valueOf(this.nano);
        return nano.movePointLeft(unit.exp).setScale(scale, FLOOR);
    }

    /**
     * Of Xdag Amount from C
     */
    public static XAmount ofXAmount(long n) {
        BigDecimal d = BasicUtils.amount2xdagNew(n) ;
        return new XAmount(d.movePointRight(9).setScale(0, HALF_UP).longValueExact());
    }

    /**
     * To Xdag Amount from C
     */
    public UInt64 toXAmount() {
        return BasicUtils.xdag2amount(toDecimal(9, XUnit.XDAG).doubleValue());
    }

    @Override
    public int compareTo(XAmount other) {
        return this.lessThan(other) ? -1 : (this.greaterThan(other) ? 1 : 0);
    }

    @Override
    public int hashCode() {
        return Long.hashCode(nano);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof XAmount && ((XAmount) other).nano == nano;
    }

    public boolean isZero() {
        return nano == 0;
    }

    @Override
    public String toString() {
        return String.valueOf(nano);
    }

    public boolean greaterThan(XAmount other) {
        return nano > other.nano;
    }

    public boolean greaterThanOrEqual(XAmount other) {
        return nano >= other.nano;
    }

    public boolean isPositive() {
        return greaterThan(ZERO);
    }

    public boolean isNotNegative() {
        return greaterThanOrEqual(ZERO);
    }

    public boolean lessThan(XAmount other) {
        return nano < other.nano;
    }

    public boolean lessThanOrEqual(XAmount other) {
        return nano <= other.nano;
    }

    public boolean isNegative() {
        return lessThan(ZERO);
    }

    public boolean isNotPositive() {
        return lessThanOrEqual(ZERO);
    }

    public XAmount negate() throws ArithmeticException {
        return new XAmount(Math.negateExact(this.nano));
    }

    public XAmount add(XAmount a) throws ArithmeticException {
        return new XAmount(Math.addExact(this.nano, a.nano));
    }

    public XAmount subtract(XAmount a) throws ArithmeticException {
        return new XAmount(Math.subtractExact(this.nano, a.nano));
    }

    public XAmount multiply(long a) throws ArithmeticException {
        return new XAmount(Math.multiplyExact(this.nano, a));
    }

    public XAmount multiply(double a) throws ArithmeticException {
        BigDecimal b1 = BigDecimal.valueOf(this.nano);
        BigDecimal b2 = BigDecimal.valueOf(a);
        return XAmount.of(b1.multiply(b2).longValue());
    }

    public static XAmount sum(XAmount a, XAmount b) throws ArithmeticException {
        return new XAmount(Math.addExact(a.nano, b.nano));
    }

}