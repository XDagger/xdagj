package io.xdag.event;

import io.xdag.utils.exception.UnreachableException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PubSub {

    private static final Logger logger = LoggerFactory.getLogger(PubSub.class);

    private final String name;

    private final LinkedBlockingQueue<PubSubEvent> queue;

    /** [event] => [list of subscribers] */
    private final ConcurrentHashMap<Class<? extends PubSubEvent>, ConcurrentLinkedQueue<PubSubSubscriber>> subscribers;
    private final AtomicBoolean isRunning;
    private Thread eventProcessingThread;

    protected PubSub(String name) {
        this.name = name;
        queue = new LinkedBlockingQueue<>();
        subscribers = new ConcurrentHashMap<>();
        isRunning = new AtomicBoolean(false);
    }

    /**
     * Add an event to {@link this#queue}.
     *
     * @param event
     *            the event to be subscribed.
     * @return whether the event is successfully added.
     */
    public boolean publish(PubSubEvent event) {
        // Do not accept any event if this pubsub instance hasn't been started in order
        // to avoid memory garbage.
        if (!isRunning.get()) {
            return false;
        }

        return queue.add(event);
    }

    /**
     * Subscribe to an event.
     *
     * @param subscriber
     *            the subscriber.
     * @param eventClss
     *            the event classes to be subscribed.
     */
    @SafeVarargs
    public final void subscribe(
            PubSubSubscriber subscriber, Class<? extends PubSubEvent>... eventClss) {
        for (Class<? extends PubSubEvent> eventCls : eventClss) {
            subscribers.computeIfAbsent(eventCls, k -> new ConcurrentLinkedQueue<>()).add(subscriber);
        }
    }

    /**
     * Unsubscribe an event.
     *
     * @param subscriber
     *            the subscriber.
     * @param event
     *            the event to be unsubscribed.
     * @return whether the event is successfully unsubscribed.
     */
    public boolean unsubscribe(PubSubSubscriber subscriber, Class<? extends PubSubEvent> event) {
        ConcurrentLinkedQueue<?> q = subscribers.get(event);
        if (q != null) {
            return q.remove(subscriber);
        } else {
            return false;
        }
    }

    /**
     * Unsubscribe from all events.
     *
     * @param subscriber
     *            the subscriber.
     */
    public void unsubscribeAll(final PubSubSubscriber subscriber) {
        subscribers.values().forEach(q -> q.remove(subscriber));
    }

    /**
     * Start the {@link this#eventProcessingThread}.
     *
     * @throws UnreachableException
     *             this method should only be called for once, otherwise an
     *             exception will be thrown.
     */
    public synchronized void start() {
        if (!isRunning.compareAndSet(false, true)) {
            throw new UnreachableException("PubSub service can be started for only once");
        }

        eventProcessingThread = new Thread(new EventProcessor(), "event-processor-" + name);
        eventProcessingThread.start();
        logger.info("PubSub service started");
    }

    /** Stop the {@link this#eventProcessingThread}. */
    public synchronized void stop() {
        eventProcessingThread.interrupt();
        try {
            eventProcessingThread.join(10_000L);
        } catch (InterruptedException e) {
            logger.error("Interrupted while joining the PubSub processing thread");
        }

        isRunning.set(false);
        logger.info("PubSub service stopped");
    }

    /**
     * This thread will be continuously polling for new events until PubSub is
     * stopped.
     */
    private class EventProcessor implements Runnable {
        @Override
        public void run() {
            while (!Thread.currentThread().isInterrupted()) {
                PubSubEvent event;
                try {
                    event = queue.take();
                } catch (InterruptedException e) {
                    return;
                }

                ConcurrentLinkedQueue<PubSubSubscriber> q = subscribers.get(event.getClass());
                if (q != null) {
                    for (PubSubSubscriber subscriber : q) {
                        try {
                            subscriber.onPubSubEvent(event);
                        } catch (Exception e) {
                            logger.error("Event processing error", e);
                        }
                    }
                }
            }
        }
    }
}
