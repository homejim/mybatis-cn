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
package org.apache.ibatis.scripting.xmltags;

import java.util.List;

/**
 * @author Clinton Begin
 * 含有多种 SqlNode。 其是树枝节点， 因此有多个叶子节点
 */
public class MixedSqlNode implements SqlNode {
  // 含有的叶子节点
  private final List<SqlNode> contents;

  public MixedSqlNode(List<SqlNode> contents) {
    this.contents = contents;
  }

  /**
   * 遍历调用各个类型的 apply 方法
   */
  @Override
  public boolean apply(DynamicContext context) {
    for (SqlNode sqlNode : contents) {
      // 调用叶子节点的 SqlNode的apply方法
      sqlNode.apply(context);
    }
    return true;
  }
}
