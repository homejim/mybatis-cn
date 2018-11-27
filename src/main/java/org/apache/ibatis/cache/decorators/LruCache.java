/**
 *    Copyright 2009-2015 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.cache.decorators;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;

import org.apache.ibatis.cache.Cache;

/**
 * Lru (least recently used) cache decorator
 *
 * 最近最少使用的缓存装饰类
 *
 * @author Clinton Begin
 */
public class LruCache implements Cache {

  // 被装饰的 Cache
  private final Cache delegate;
  // LinkedHashMap 来记录换成对象
  private Map<Object, Object> keyMap;
  // 最近最少使用的 key
  private Object eldestKey;

  /**
   * 默认大小为 1024
   *
   * @param delegate
   */
  public LruCache(Cache delegate) {
    this.delegate = delegate;
    setSize(1024);
  }

  @Override
  public String getId() {
    return delegate.getId();
  }

  @Override
  public int getSize() {
    return delegate.getSize();
  }

  /**
   * 使用 LinkedHashMap 来实现 Lru
   * @param size
   */
  public void setSize(final int size) {
    // 第三个参数 accessOrder 为 true, 访问的时候会改变其顺序
    keyMap = new LinkedHashMap<Object, Object>(size, .75F, true) {
      private static final long serialVersionUID = 4267176411845948333L;
      // 重写 removeEldestEntry
      @Override
      protected boolean removeEldestEntry(Map.Entry<Object, Object> eldest) {
        boolean tooBig = size() > size;
        if (tooBig) {
          eldestKey = eldest.getKey();
        }
        return tooBig;
      }
    };
  }

  @Override
  public void putObject(Object key, Object value) {
    delegate.putObject(key, value);
    cycleKeyList(key);// 检查并清理缓存
  }

  @Override
  public Object getObject(Object key) {
    keyMap.get(key); //touch， 改变顺序
    return delegate.getObject(key);
  }

  @Override
  public Object removeObject(Object key) {
    return delegate.removeObject(key);
  }

  @Override
  public void clear() {
    delegate.clear();
    keyMap.clear();
  }

  @Override
  public ReadWriteLock getReadWriteLock() {
    return null;
  }

  private void cycleKeyList(Object key) {
    keyMap.put(key, key);
    if (eldestKey != null) {// eldestKey 非空则进行移除
      delegate.removeObject(eldestKey);
      eldestKey = null;
    }
  }

}
