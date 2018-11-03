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
package org.apache.ibatis.scripting.xmltags;

import java.util.Map;

import org.apache.ibatis.parsing.GenericTokenParser;
import org.apache.ibatis.session.Configuration;

/**
 * @author Clinton Begin
 */
public class ForEachSqlNode implements SqlNode {
  public static final String ITEM_PREFIX = "__frch_";

  // 用于判断循环的终止条件
  private final ExpressionEvaluator evaluator;
  // 迭代的集合表达式
  private final String collectionExpression;
  // 子节点
  private final SqlNode contents;
  // 循环开始前添加的字符串
  private final String open;
  // 循环机结束后添加的字符串
  private final String close;
  // 循环每项之间的分隔符
  private final String separator;
  // item 是本次迭代的元素
  private final String item;
  // 当前的迭代次数
  private final String index;
  private final Configuration configuration;

  public ForEachSqlNode(Configuration configuration, SqlNode contents, String collectionExpression, String index, String item, String open, String close, String separator) {
    this.evaluator = new ExpressionEvaluator();
    this.collectionExpression = collectionExpression;
    this.contents = contents;
    this.open = open;
    this.close = close;
    this.separator = separator;
    this.index = index;
    this.item = item;
    this.configuration = configuration;
  }

  @Override
  public boolean apply(DynamicContext context) {
    // 获取参数信息
    Map<String, Object> bindings = context.getBindings();

    final Iterable<?> iterable = evaluator.evaluateIterable(collectionExpression, bindings);
    if (!iterable.iterator().hasNext()) {
      return true;
    }
    boolean first = true;
    // 添加字段
    applyOpen(context);
    int i = 0;
    // 遍历
    for (Object o : iterable) {
      DynamicContext oldContext = context;
      // 如果是第一次， 则使用 PrefixedContext 对象对 context 进行封装
      if (first || separator == null) {
        context = new PrefixedContext(context, "");
      } else {
        context = new PrefixedContext(context, separator);
      }
      // 从 0 开始， 每次自增1
      int uniqueNumber = context.getUniqueNumber();
      // Issue #709 
      if (o instanceof Map.Entry) {
        @SuppressWarnings("unchecked") 
        Map.Entry<Object, Object> mapEntry = (Map.Entry<Object, Object>) o;
        applyIndex(context, mapEntry.getKey(), uniqueNumber);
        applyItem(context, mapEntry.getValue(), uniqueNumber);
      } else {
        applyIndex(context, i, uniqueNumber);
        applyItem(context, o, uniqueNumber);
      }
      // 调用子节点的 apply() 方法处理
      contents.apply(new FilteredDynamicContext(configuration, context, index, item, uniqueNumber));
      if (first) {
        first = !((PrefixedContext) context).isPrefixApplied();
      }
      context = oldContext;
      i++;
    }
    applyClose(context);
    context.getBindings().remove(item);
    context.getBindings().remove(index);
    return true;
  }

  private void applyIndex(DynamicContext context, Object o, int i) {
      if (index != null) {
          // index->o
          context.bind(index, o);
          // __frch_index_i->o
          context.bind(itemizeItem(index, i), o);
      }
  }

  private void applyItem(DynamicContext context, Object o, int i) {
      if (item != null) {
          // item->o
          context.bind(item, o);
          // __frch_item_i->o
          context.bind(itemizeItem(item, i), o);
      }
  }

  /**
   * 添加 open 到 context 中
   * @param context
   */
  private void applyOpen(DynamicContext context) {
    if (open != null) {
      context.appendSql(open);
    }
  }

  private void applyClose(DynamicContext context) {
    if (close != null) {
      context.appendSql(close);
    }
  }

  /**
   * item 替换成 __frch_item_i
   * @return
   */
  private static String itemizeItem(String item, int i) {
    return ITEM_PREFIX + item + "_" + i;
  }

  /**
   * 处理 #{} 占位符的类
   */
  private static class FilteredDynamicContext extends DynamicContext {
    // 代理的 DynamicContext 对象
    private final DynamicContext delegate;
    // 对应的集合项在集合中的位置
    private final int index;
    // 对应集合项的 index
    private final String itemIndex;
    // 对应集合项的 item
    private final String item;

    public FilteredDynamicContext(Configuration configuration, DynamicContext delegate, String itemIndex, String item, int i) {
      super(configuration, null);
      this.delegate = delegate;
      this.index = i;
      this.itemIndex = itemIndex;
      this.item = item;
    }

    @Override
    public Map<String, Object> getBindings() {
      return delegate.getBindings();
    }

    @Override
    public void bind(String name, Object value) {
      delegate.bind(name, value);
    }

    @Override
    public String getSql() {
      return delegate.getSql();
    }

    @Override
    public void appendSql(String sql) {
      // 处理的是 #{xxx} 的内容， TokenHandler 是匿名内部类
      GenericTokenParser parser = new GenericTokenParser("#{", "}", content -> {
        // 对传入的content进行处理， 正则表达式
        String newContent = content.replaceFirst("^\\s*" + item + "(?![^.,:\\s])", itemizeItem(item, index));
        if (itemIndex != null && newContent.equals(content)) {
          newContent = content.replaceFirst("^\\s*" + itemIndex + "(?![^.,:\\s])", itemizeItem(itemIndex, index));
        }
        return "#{" + newContent + "}";
      });

      delegate.appendSql(parser.parse(sql));
    }

    @Override
    public int getUniqueNumber() {
      return delegate.getUniqueNumber();
    }

  }


  /**
   *
   */
  private class PrefixedContext extends DynamicContext {
    // 代理 DynamicContext 对象
    private final DynamicContext delegate;
    // 指定的前缀
    private final String prefix;
    // 指定的后缀
    private boolean prefixApplied;

    public PrefixedContext(DynamicContext delegate, String prefix) {
      super(configuration, null);
      this.delegate = delegate;
      this.prefix = prefix;
      this.prefixApplied = false;
    }

    public boolean isPrefixApplied() {
      return prefixApplied;
    }

    @Override
    public Map<String, Object> getBindings() {
      return delegate.getBindings();
    }

    @Override
    public void bind(String name, Object value) {
      delegate.bind(name, value);
    }

    /**
     * 追加指定的前缀 prefix 到 delegate 中， 再追加 sql 语句
     * @param sql
     */
    @Override
    public void appendSql(String sql) {
      // 判断是否需要追加前缀
      if (!prefixApplied && sql != null && sql.trim().length() > 0) {
        // 追加前缀
        delegate.appendSql(prefix);
        // 表示已经追加过
        prefixApplied = true;
      }
      // 追加 sql 语句
      delegate.appendSql(sql);
    }

    @Override
    public String getSql() {
      return delegate.getSql();
    }

    @Override
    public int getUniqueNumber() {
      return delegate.getUniqueNumber();
    }
  }

}
