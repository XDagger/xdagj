package io.xdag.utils.discoveryutils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class Subscribers<T> {

    private final Map<Long, T> subscribers = new ConcurrentHashMap<>();

    public void forEach(final Consumer<T> action) {
        subscribers.values().forEach(action);
    }

}

