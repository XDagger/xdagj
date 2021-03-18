package io.xdag.nat;

public class NatConfiguration {
    private final NatMethod natMethod;

    private NatConfiguration(final NatMethod natMethod) {
        this.natMethod = natMethod;
    }

    public NatMethod getNatMethod() {
        return natMethod;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        public Builder() {};

        private NatMethod natMethod;

        public Builder natMethod(final NatMethod natMethod) {
            this.natMethod = natMethod;
            return this;
        }

        public NatConfiguration build() {
            return new NatConfiguration(natMethod);
        }
    }
}

