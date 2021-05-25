//package io.xdag.rpc.modules.xdag.subscribe;
//
//import com.fasterxml.jackson.annotation.JsonCreator;
//import com.fasterxml.jackson.annotation.JsonValue;
//import io.xdag.rpc.jsonrpc.JsonRpcResult;
//import io.xdag.rpc.utils.TypeConverter;
//
//import java.security.SecureRandom;
//import java.util.Arrays;
//
//public class SubscriptionId extends JsonRpcResult {
//    private final byte[] id;
//
//    @JsonCreator
//    public SubscriptionId(String hexId) {
//        this.id = TypeConverter.stringHexToByteArray(hexId);
//    }
//
//    public SubscriptionId() {
//        this.id = new byte[16];
//        new SecureRandom().nextBytes(id);
//    }
//
//    public byte[] getId() {
//        return Arrays.copyOf(id, id.length);
//    }
//
//    @Override
//    public int hashCode() {
//        return Arrays.hashCode(id);
//    }
//
//    @Override
//    public boolean equals(Object o) {
//        if (o == this) {
//            return true;
//        }
//
//        if (!(o instanceof SubscriptionId)) {
//            return false;
//        }
//
//        SubscriptionId other = (SubscriptionId) o;
//        return Arrays.equals(this.id, other.id);
//    }
//
//    @JsonValue
//    @SuppressWarnings("unused")
//    private String serialize() {
//        return TypeConverter.toJsonHex(id);
//    }
//}
