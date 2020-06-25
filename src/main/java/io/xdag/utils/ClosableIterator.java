package io.xdag.utils;

import java.util.Iterator;

public interface ClosableIterator<T> extends Iterator<T> {

  /** Closes the underlying resources for this iterator. */
  void close();
}
