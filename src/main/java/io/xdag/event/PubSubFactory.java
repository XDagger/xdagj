package io.xdag.event;

import java.util.concurrent.ConcurrentHashMap;

public class PubSubFactory {

    private static final PubSub defaultInstance = new PubSub("default");

    private static final ConcurrentHashMap<String, PubSub> instances = new ConcurrentHashMap<>();

    private PubSubFactory() {
    }

    public static PubSub getDefault() {
        return defaultInstance;
    }

    public static PubSub get(String name) {
        return instances.computeIfAbsent(name, PubSub::new);
    }
}
