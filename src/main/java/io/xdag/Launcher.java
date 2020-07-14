package io.xdag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;

import io.xdag.event.PubSubFactory;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Launcher {

    /**
     * Here we make sure that all shutdown hooks will be executed in the order of
     * registration. This is necessary to be manually maintained because
     * ${@link Runtime#addShutdownHook(Thread)} starts shutdown hooks concurrently
     * in unspecified order.
     */
    private static final List<Pair<String, Runnable>> shutdownHooks = Collections.synchronizedList(new ArrayList<>());

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(Launcher::shutdownHook, "shutdown-hook"));
    }

    /**
     * Registers a shutdown hook which will be executed in the order of
     * registration.
     *
     * @param name
     * @param runnable
     */
    public static synchronized void registerShutdownHook(String name, Runnable runnable) {
        shutdownHooks.add(Pair.of(name, runnable));
    }

    /** Call registered shutdown hooks in the order of registration. */
    private static synchronized void shutdownHook() {
        // shutdown hooks
        for (Pair<String, Runnable> r : shutdownHooks) {
            try {
                log.debug("Shutting down {}", r.getLeft());
                r.getRight().run();
            } catch (Exception e) {
                log.debug("Failed to shutdown {}", r.getLeft(), e);
            }
        }
        // flush log4j async loggers
        LogManager.shutdown();
    }

    /** Set up pubsub service. */
    protected void setupPubSub() {
        PubSubFactory.getDefault().start();
        registerShutdownHook("pubsub-default", () -> PubSubFactory.getDefault().stop());
    }
}
