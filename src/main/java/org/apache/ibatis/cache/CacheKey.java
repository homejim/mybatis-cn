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
package org.apache.ibatis.cache;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.apache.ibatis.reflection.ArrayUtil;

/**
 * @author Clinton Begin
 */
public class CacheKey implements Cloneable, Serializable {

  private static final long serialVersionUID = 1146682552656046210L;

  public static final CacheKey NULL_CACHE_KEY = new NullCacheKey();

  private static final int DEFAULT_MULTIPLYER = 37;
  private static final int DEFAULT_HASHCODE = 17;

  // 参与 hashcode 的计算， 类似于 String hashcode 函数中的 31
  private final int multiplier;
  // 哈希值
  private int hashcode;
  // 校验和
  private long checksum;
  // 集合的数量
  private int count;
  // 8/21/2017 - Sonarlint flags this as needing to be marked transient.  While true if content is not serializable, this is not always true and thus should not be marked transient.
  private List<Object> updateList;

  public CacheKey() {
    this.hashcode = DEFAULT_HASHCODE;
    this.multiplier = DEFAULT_MULTIPLYER;
    this.count = 0;
    this.updateList = new ArrayList<>();
  }

  public CacheKey(Object[] objects) {
    this();
    updateAll(objects);
  }

  public int getUpdateCount() {
    return updateList.size();
  }

  // 添加元素
  public void update(Object object) {
    // null 的 hashcode 为 1
    int baseHashCode = object == null ? 1 : ArrayUtil.hashCode(object);
    // 数量增加
    count++;
    checksum += baseHashCode;
    baseHashCode *= count;
    // 计算哈希值
    hashcode = multiplier * hashcode + baseHashCode;
    // 添加元素
    updateList.add(object);
  }

  // 将数组中的元素全部调用 update 方法
  public void updateAll(Object[] objects) {
    for (Object o : objects) {
      update(o);
    }
  }

  // 重新了 equals 方法
  @Override
  public boolean equals(Object object) {
    // 同意个对象返回 true
    if (this == object) {
      return true;
    }
    // 不是 CacheKey 类型 返回 false
    if (!(object instanceof CacheKey)) {
      return false;
    }

    final CacheKey cacheKey = (CacheKey) object;

    // // hashcode/checksum/count 其中之一不同就返回 false
    if (hashcode != cacheKey.hashcode) {
      return false;
    }
    if (checksum != cacheKey.checksum) {
      return false;
    }
    if (count != cacheKey.count) {
      return false;
    }

    // 如果上面都相同， 比较每一个位置的对象是否相同， 一个不同就为 false
    for (int i = 0; i < updateList.size(); i++) {
      Object thisObject = updateList.get(i);
      Object thatObject = cacheKey.updateList.get(i);
      if (!ArrayUtil.equals(thisObject, thatObject)) {
        return false;
      }
    }
    return true;
  }

  /**
   * hashcode 重写： 返回成员变量 hashcode
   * @return
   */
  @Override
  public int hashCode() {
    return hashcode;
  }

  /**
   * 调用 ArrayUtil.toString 重写 toString() 方法
   * @return
   */
  @Override
  public String toString() {
    StringBuilder returnValue = new StringBuilder().append(hashcode).append(':').append(checksum);
    for (Object object : updateList) {
      returnValue.append(':').append(ArrayUtil.toString(object));
    }
    return returnValue.toString();
  }

  /**
   * 克隆方法
   *
   * @return
   * @throws CloneNotSupportedException
   */
  @Override
  public CacheKey clone() throws CloneNotSupportedException {
    CacheKey clonedCacheKey = (CacheKey) super.clone();
    // 由于 Object.clone 中对数组类是浅拷贝， 因此需要创建一个新的数组类
    clonedCacheKey.updateList = new ArrayList<>(updateList);
    return clonedCacheKey;
  }

}
