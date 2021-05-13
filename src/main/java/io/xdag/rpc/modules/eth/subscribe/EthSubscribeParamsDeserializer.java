//package io.xdag.rpc.modules.eth.subscribe;
//
//import com.fasterxml.jackson.core.JsonParser;
//import com.fasterxml.jackson.core.JsonToken;
//import com.fasterxml.jackson.databind.DeserializationContext;
//import com.fasterxml.jackson.databind.JsonDeserializer;
//
//import java.io.IOException;
//import java.util.HashMap;
//
//public class EthSubscribeParamsDeserializer extends JsonDeserializer {
//
//    private final HashMap<String, Class<? extends EthSubscribeParams>> subscriptionTypes;
//
//    public EthSubscribeParamsDeserializer() {
//        this.subscriptionTypes = new HashMap<>();
//        this.subscriptionTypes.put("newHeads", EthSubscribeNewHeadsParams.class);
//        this.subscriptionTypes.put("logs", EthSubscribeLogsParams.class);
//    }
//
//    @Override
//    public Object deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
//        if (!p.isExpectedStartArrayToken()) {
//            return ctxt.handleUnexpectedToken(
//                    EthSubscribeParams.class,
//                    p.currentToken(),
//                    p,
//                    "eth_subscribe parameters are expected to be arrays"
//            );
//        }
//        p.nextToken(); // skip '['
//        String subscriptionType = p.getText();
//        Class<? extends EthSubscribeParams> subscriptionTypeClass = subscriptionTypes.get(subscriptionType);
//        p.nextToken();
//        EthSubscribeParams params;
//        if (p.isExpectedStartObjectToken()) {
//            params = p.readValueAs(subscriptionTypeClass);
//            p.nextToken();
//        } else {
//            try {
//                params = subscriptionTypeClass.newInstance();
//            } catch (InstantiationException | IllegalAccessException e) {
//                return ctxt.handleInstantiationProblem(
//                        subscriptionTypeClass,
//                        null,
//                        e
//                );
//            }
//        }
//        if (p.currentToken() != JsonToken.END_ARRAY) {
//            return ctxt.handleUnexpectedToken(
//                    EthSubscribeParams.class,
//                    p.currentToken(),
//                    p,
//                    "eth_subscribe can only have one object to configure subscription"
//            );
//        }
//        return params;
//    }
//}
