package io.xdag.discovery.Utils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public class Subscribers<T> {

    private final AtomicLong subscriberId = new AtomicLong();
    private final Map<Long, T> subscribers = new ConcurrentHashMap<>();

    /**
     * Add a subscriber to the list.
     *
     * @param subscriber the subscriber to add
     * @return the ID assigned to this subscriber
     */
    public long subscribe(final T subscriber) {
        final long id = subscriberId.getAndIncrement();
        subscribers.put(id, subscriber);
        return id;
    }

    /**
     * Remove a subscriber from the list.
     *
     * @param subscriberId the ID of the subscriber to remove
     * @return <code>true</code> if a subscriber with that ID was found and removed, otherwise <code>
     *     false</code>
     */
    public boolean unsubscribe(final long subscriberId) {
        return subscribers.remove(subscriberId) != null;
    }

    /**
     * Iterate through the current list of subscribers. This is typically used to deliver events e.g.:
     *
     * <pre>
     * <code>subscribers.forEach(subscriber -&gt; subscriber.onEvent());</code>
     * </pre>
     *
     * @param action the action to perform for each subscriber
     */
    public void forEach(final Consumer<T> action) {
        subscribers.values().forEach(action);
    }

    /**
     * Get the current subscriber count.
     *
     * @return the current number of subscribers.
     */
    public int getSubscriberCount() {
        return subscribers.size();
    }
}

