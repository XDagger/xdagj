package io.xdag.nat;

public enum NatServiceType {
    XDAG_DISCOVERY("xdag_discovery"),
    XDAG_P2P("xdag_p2p");
    private final String value;

    NatServiceType(final String value) {
        this.value = value;
    }

    public static NatServiceType fromString(final String natServiceTypeName) {
        for (final NatServiceType mode : NatServiceType.values()) {
            if (mode.getValue().equalsIgnoreCase(natServiceTypeName)) {
                return mode;
            }
        }
        throw new IllegalStateException(
                String.format("Invalid NAT service type provided: %s", natServiceTypeName));
    }

    public String getValue() {
        return value;
    }
}