package io.xdag.utils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Function;

public class ExecutorPipeline<In, Out> {

  private static AtomicInteger pipeNumber = new AtomicInteger(1);
  private BlockingQueue<Runnable> queue;
  private ThreadPoolExecutor exec;
  private boolean preserveOrder = false;
  private Function<In, Out> processor;
  private Consumer<Throwable> exceptionHandler;
  private ExecutorPipeline<Out, ?> next;
  private AtomicLong orderCounter = new AtomicLong();
  private long nextOutTaskNumber = 0;
  private Map<Long, Out> orderMap = new HashMap<>();
  private ReentrantLock lock = new ReentrantLock();
  private String threadPoolName;
  private AtomicInteger threadNumber = new AtomicInteger(1);

  public ExecutorPipeline(
      int threads,
      int queueSize,
      boolean preserveOrder,
      Function<In, Out> processor,
      Consumer<Throwable> exceptionHandler) {
    queue = new LimitedQueue<>(queueSize);
    exec =
        new ThreadPoolExecutor(
            threads,
            threads,
            0L,
            TimeUnit.MILLISECONDS,
            queue,
            r -> new Thread(r, threadPoolName + "-" + threadNumber.getAndIncrement()));
    this.preserveOrder = preserveOrder;
    this.processor = processor;
    this.exceptionHandler = exceptionHandler;
    this.threadPoolName = "pipe-" + pipeNumber.getAndIncrement();
  }

  public ExecutorPipeline<Out, Void> add(int threads, int queueSize, final Consumer<Out> consumer) {
    return add(
        threads,
        queueSize,
        false,
        out -> {
          consumer.accept(out);
          return null;
        });
  }

  public <NextOut> ExecutorPipeline<Out, NextOut> add(
      int threads, int queueSize, boolean preserveOrder, Function<Out, NextOut> processor) {
    ExecutorPipeline<Out, NextOut> ret =
        new ExecutorPipeline<>(threads, queueSize, preserveOrder, processor, exceptionHandler);
    next = ret;
    return ret;
  }

  private void pushNext(long order, Out res) {
    if (next != null) {
      if (!preserveOrder) {
        next.push(res);
      } else {
        lock.lock();
        try {
          if (order == nextOutTaskNumber) {
            next.push(res);
            while (true) {
              nextOutTaskNumber++;
              Out out = orderMap.remove(nextOutTaskNumber);
              if (out == null) {
                break;
              }
              next.push(out);
            }
          } else {
            orderMap.put(order, res);
          }
        } finally {
          lock.unlock();
        }
      }
    }
  }

  public void push(final In in) {
    final long order = orderCounter.getAndIncrement();
    exec.execute(
        () -> {
          try {
            pushNext(order, processor.apply(in));
          } catch (Throwable e) {
            exceptionHandler.accept(e);
          }
        });
  }

  public void pushAll(final List<In> list) {
    for (In in : list) {
      push(in);
    }
  }

  public ExecutorPipeline<In, Out> setThreadPoolName(String threadPoolName) {
    this.threadPoolName = threadPoolName;
    return this;
  }

  public BlockingQueue<Runnable> getQueue() {
    return queue;
  }

  public Map<Long, Out> getOrderMap() {
    return orderMap;
  }

  public void shutdown() {
    try {
      exec.shutdown();
    } catch (Exception e) {
    }
    if (next != null) {
      exec.shutdown();
    }
  }

  public boolean isShutdown() {
    return exec.isShutdown();
  }

  /**
   * Shutdowns executors and waits until all pipeline submitted tasks complete
   *
   * @throws InterruptedException
   */
  public void join() throws InterruptedException {
    exec.shutdown();
    exec.awaitTermination(10, TimeUnit.MINUTES);
    if (next != null) {
      next.join();
    }
  }

  private static class LimitedQueue<E> extends LinkedBlockingQueue<E> {

    private static final long serialVersionUID = -6518133204112839721L;

    public LimitedQueue(int maxSize) {
      super(maxSize);
    }

    @Override
    public boolean offer(E e) {
      // turn offer() and add() into a blocking calls (unless interrupted)
      try {
        put(e);
        return true;
      } catch (InterruptedException ie) {
        Thread.currentThread().interrupt();
      }
      return false;
    }
  }
}
