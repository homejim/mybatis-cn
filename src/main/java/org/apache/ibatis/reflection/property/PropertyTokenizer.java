/**
 *    Copyright 2009-2017 the original author or authors.
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
package org.apache.ibatis.reflection.property;

import java.util.Iterator;

/**
 * 实现了迭代器接口
 */
public class PropertyTokenizer implements Iterator<PropertyTokenizer> {
  // 当前表达式的名称
  private String name;
  // 当前表达式的索引名
  private final String indexedName;
  // 索引下标
  private String index;
  // 子表达式
  private final String children;

  /**
   * 构造函数
   * @param fullname 需要解析的表达式
   */
  public PropertyTokenizer(String fullname) {
    // 查找 "." 的位置
    int delim = fullname.indexOf('.');
    if (delim > -1) {
      // 初始化 name
      name = fullname.substring(0, delim);
      // 初始化 children
      children = fullname.substring(delim + 1);
    } else {
      name = fullname;
      children = null;
    }
    // 初始化 indexedName
    indexedName = name;
    delim = name.indexOf('[');
    if (delim > -1) {
      // 初始化 index
      index = name.substring(delim + 1, name.length() - 1);
      name = name.substring(0, delim);
    }
  }

  public String getName() {
    return name;
  }

  public String getIndex() {
    return index;
  }

  public String getIndexedName() {
    return indexedName;
  }

  public String getChildren() {
    return children;
  }

  @Override
  public boolean hasNext() {
    return children != null;
  }

  /**
   * 通过该方法进行迭代
   * @return
   */
  @Override
  public PropertyTokenizer next() {
    return new PropertyTokenizer(children);
  }

  @Override
  public void remove() {
    throw new UnsupportedOperationException("Remove is not supported, as it has no meaning in the context of properties.");
  }
}
