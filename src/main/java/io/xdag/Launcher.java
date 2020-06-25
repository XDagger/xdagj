package io.xdag;

import io.xdag.event.PubSubFactory;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Launcher {
  private static final Logger logger = LoggerFactory.getLogger(Launcher.class);

  /**
   * Here we make sure that all shutdown hooks will be executed in the order of registration. This
   * is necessary to be manually maintained because ${@link Runtime#addShutdownHook(Thread)} starts
   * shutdown hooks concurrently in unspecified order.
   */
  private static final List<Pair<String, Runnable>> shutdownHooks =
      Collections.synchronizedList(new ArrayList<>());

  static {
    Runtime.getRuntime().addShutdownHook(new Thread(Launcher::shutdownHook, "shutdown-hook"));
  }

  /** Set up pubsub service. */
  protected void setupPubSub() {
    PubSubFactory.getDefault().start();
    registerShutdownHook("pubsub-default", () -> PubSubFactory.getDefault().stop());
  }

  /**
   * Registers a shutdown hook which will be executed in the order of registration.
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
        logger.debug("Shutting down {}", r.getLeft());
        r.getRight().run();
      } catch (Exception e) {
        logger.debug("Failed to shutdown {}", r.getLeft(), e);
      }
    }
    // flush log4j async loggers
    LogManager.shutdown();
  }
}
