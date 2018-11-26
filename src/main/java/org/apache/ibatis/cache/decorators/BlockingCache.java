/**
 *    Copyright 2009-2018 the original author or authors.
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

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.ibatis.cache.Cache;
import org.apache.ibatis.cache.CacheException;

/**
 * Simple blocking decorator 
 * 
 * Simple and inefficient version of EhCache's BlockingCache decorator.
 * It sets a lock over a cache key when the element is not found in cache.
 * This way, other threads will wait until this element is filled instead of hitting the database.
 *
 * 阻塞版本的缓存装饰器
 *
 * @author Eduardo Macarron
 *
 */
public class BlockingCache implements Cache {

  // 阻塞超时时间
  private long timeout;
  // 缓存
  private final Cache delegate;
  // 使用 ConcurrentHashMap 来存储每个 Key 的 ReentrantLock 对象
  private final ConcurrentHashMap<Object, ReentrantLock> locks;

  public BlockingCache(Cache delegate) {
    this.delegate = delegate;
    this.locks = new ConcurrentHashMap<>();
  }

  @Override
  public String getId() {
    return delegate.getId();
  }

  @Override
  public int getSize() {
    return delegate.getSize();
  }

  @Override
  public void putObject(Object key, Object value) {
    try {
      // 将缓存数据放入
      delegate.putObject(key, value);
    } finally {
      // 释放锁
      releaseLock(key);
    }
  }

  @Override
  public Object getObject(Object key) {
    // 获取锁
    acquireLock(key);
    // 获取值
    Object value = delegate.getObject(key);
    if (value != null) {
      // 缓存对象存在则释放锁， 否则继续持有锁对象
      releaseLock(key);
    }
    return value;
  }

  @Override
  public Object removeObject(Object key) {
    // despite of its name, this method is called only to release locks
    releaseLock(key);
    return null;
  }

  @Override
  public void clear() {
    delegate.clear();
  }

  @Override
  public ReadWriteLock getReadWriteLock() {
    return null;
  }

  // 如果key没有对应的锁对象， 则为其创建新的 ReentrantLock 对象
  private ReentrantLock getLockForKey(Object key) {
    ReentrantLock lock = new ReentrantLock();
    // 添加到 locks 集合中， 如果 locks 有对应的 ReentrantLock 对象， 则使用该对象
    ReentrantLock previous = locks.putIfAbsent(key, lock);
    return previous == null ? lock : previous;
  }

  /**
   * 获取锁
   * @param key
   */
  private void acquireLock(Object key) {
    // 获取 ReentrantLock 对象
    Lock lock = getLockForKey(key);
    if (timeout > 0) {
      try {
        // 获取锁， 超时时长 timeout
        boolean acquired = lock.tryLock(timeout, TimeUnit.MILLISECONDS);
        if (!acquired) {
          throw new CacheException("Couldn't get a lock in " + timeout + " for the key " +  key + " at the cache " + delegate.getId());  
        }
      } catch (InterruptedException e) {
        throw new CacheException("Got interrupted while trying to acquire lock for key " + key, e);
      }
    } else {
      // 获取锁， 没有超时时长
      lock.lock();
    }
  }

  /**
   * 释放对应 key 的锁
   * @param key
   */
  private void releaseLock(Object key) {
    ReentrantLock lock = locks.get(key);
    if (lock.isHeldByCurrentThread()) {// 判断锁是否被当前的对象持有
      lock.unlock();
    }
  }

  public long getTimeout() {
    return timeout;
  }

  public void setTimeout(long timeout) {
    this.timeout = timeout;
  }  
}