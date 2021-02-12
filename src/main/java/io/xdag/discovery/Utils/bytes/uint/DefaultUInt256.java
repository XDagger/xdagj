package io.xdag.discovery.Utils.bytes.uint;


import io.xdag.discovery.Utils.bytes.Bytes32;

class DefaultUInt256 extends AbstractUInt256Value<UInt256> implements UInt256 {

    DefaultUInt256(final Bytes32 bytes) {
        super(bytes, UInt256Counter::new);
    }

    static Counter<UInt256> newVar() {
        return new UInt256Counter();
    }

    @Override
    public UInt256 asUInt256() {
        return this;
    }

    private static class UInt256Counter extends Counter<UInt256> {
        private UInt256Counter() {
            super(DefaultUInt256::new);
        }
    }
}
