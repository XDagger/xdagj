package io.xdag.utils;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public class MapUtils {

  public static <K, V> Map.Entry<K, V> getHead(LinkedHashMap<K, V> map) {
    return map.entrySet().iterator().next();
  }

  public static <K, V> Map.Entry<K, V> getTail(LinkedHashMap<K, V> map) {
    Iterator<Map.Entry<K, V>> iterator = map.entrySet().iterator();
    Map.Entry<K, V> tail = null;
    while (iterator.hasNext()) {
      tail = iterator.next();
    }
    return tail;
  }
}
