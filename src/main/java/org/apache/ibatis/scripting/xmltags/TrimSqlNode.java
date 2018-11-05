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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringTokenizer;

import org.apache.ibatis.session.Configuration;


/**
 * <trim> 节点
 */
public class TrimSqlNode implements SqlNode {

  // <trim> 节点对应的子节点
  private final SqlNode contents;
  // 前缀
  private final String prefix;
  // 后缀
  private final String suffix;
  // 如果 trim 中的 SQL 是空语句， 删除指定的前缀
  private final List<String> prefixesToOverride;
  // 如果 trim 中的 SQL 是空语句， 删除指定的后缀
  private final List<String> suffixesToOverride;
  // Mybatis 解析后的 Configuration 对象， 其包含mybatis中的内容
  private final Configuration configuration;

  /**
   * 构造函数
   * 会调用 parseOverrides， 对 prefixesToOverride和suffixesToOverride 进行解析， 返回相应的 list
   */
  public TrimSqlNode(Configuration configuration, SqlNode contents, String prefix, String prefixesToOverride, String suffix, String suffixesToOverride) {
    this(configuration, contents, prefix, parseOverrides(prefixesToOverride), suffix, parseOverrides(suffixesToOverride));
  }

  protected TrimSqlNode(Configuration configuration, SqlNode contents, String prefix, List<String> prefixesToOverride, String suffix, List<String> suffixesToOverride) {
    this.contents = contents;
    this.prefix = prefix;
    this.prefixesToOverride = prefixesToOverride;
    this.suffix = suffix;
    this.suffixesToOverride = suffixesToOverride;
    this.configuration = configuration;
  }

  @Override
  public boolean apply(DynamicContext context) {
    // FilteredDynamicContext 对象， 内部有 DynamicContext
    FilteredDynamicContext filteredDynamicContext = new FilteredDynamicContext(context);
    // 调用子方法的 apply 方法
    boolean result = contents.apply(filteredDynamicContext);
    // 处理前缀和后缀
    filteredDynamicContext.applyAll();
    return result;
  }

  /**
   *
   * @param overrides
   * @return
   */
  private static List<String> parseOverrides(String overrides) {
    if (overrides != null) {
      // 按照 | 对overrides进行拆分
      final StringTokenizer parser = new StringTokenizer(overrides, "|", false);
      final List<String> list = new ArrayList<>(parser.countTokens());
      while (parser.hasMoreTokens()) {
        list.add(parser.nextToken().toUpperCase(Locale.ENGLISH));
      }
      return list;
    }
    return Collections.emptyList();
  }

  /**
   * 内部类， 继承于 DynamicContext, 同时增加和前缀和后缀的处理
   */
  private class FilteredDynamicContext extends DynamicContext {
    // DynamicContext 对象
    private DynamicContext delegate;
    // 前缀是否已经处理过
    private boolean prefixApplied;
    // 后缀是否已经处理过
    private boolean suffixApplied;
    // 用于记录子节点的解析结果
    private StringBuilder sqlBuffer;

    public FilteredDynamicContext(DynamicContext delegate) {
      super(configuration, null);
      this.delegate = delegate;
      this.prefixApplied = false;
      this.suffixApplied = false;
      this.sqlBuffer = new StringBuilder();
    }

    /**
     *
     */
    public void applyAll() {
      // 获取子节点解析的结果
      sqlBuffer = new StringBuilder(sqlBuffer.toString().trim());
      // 转化为大写
      String trimmedUppercaseSql = sqlBuffer.toString().toUpperCase(Locale.ENGLISH);
      if (trimmedUppercaseSql.length() > 0) {
        applyPrefix(sqlBuffer, trimmedUppercaseSql);// 处理前缀
        applySuffix(sqlBuffer, trimmedUppercaseSql);// 处理后缀
      }
      delegate.appendSql(sqlBuffer.toString());//结果添加到DynamicContext对象中
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
    public int getUniqueNumber() {
      return delegate.getUniqueNumber();
    }

    @Override
    public void appendSql(String sql) {
      sqlBuffer.append(sql);
    }

    @Override
    public String getSql() {
      return delegate.getSql();
    }

    /**
     * prefix: 当 trim 元素包含有内容时， 增加 prefix 所指定的前缀
     * 因为只是增加一次， 因此需要使用 prefixApplied 来进行标记， 表示是否已经增加过了
     * @param sql sql 语句
     * @param trimmedUppercaseSql
     */
    private void applyPrefix(StringBuilder sql, String trimmedUppercaseSql) {
      // 是否已经处理过
      if (!prefixApplied) {
        // 标记已经处理
        prefixApplied = true;
        if (prefixesToOverride != null) {
          // 遍历
          for (String toRemove : prefixesToOverride) {
            //
            if (trimmedUppercaseSql.startsWith(toRemove)) {
              sql.delete(0, toRemove.trim().length());
              break;
            }
          }
        }
        if (prefix != null) {
          // 添加前缀
          sql.insert(0, " ");
          sql.insert(0, prefix);
        }
      }
    }

    /**
     * 处理后缀
     *
     * @param sql
     * @param trimmedUppercaseSql
     */
    private void applySuffix(StringBuilder sql, String trimmedUppercaseSql) {
      // 是否已经处理过
      if (!suffixApplied) {
        // 已处理标记
        suffixApplied = true;
        if (suffixesToOverride != null) {
          // 遍历
          for (String toRemove : suffixesToOverride) {
            // 如果以 suffixesToOverride 的某一项结尾， 则将该项从 SQL 语句尾部删除
            if (trimmedUppercaseSql.endsWith(toRemove) || trimmedUppercaseSql.endsWith(toRemove.trim())) {
              int start = sql.length() - toRemove.trim().length();
              int end = sql.length();
              sql.delete(start, end);
              break;
            }
          }
        }
        // 后缀非空， 则添加
        if (suffix != null) {
          sql.append(" ");
          sql.append(suffix);
        }
      }
    }

  }

}
