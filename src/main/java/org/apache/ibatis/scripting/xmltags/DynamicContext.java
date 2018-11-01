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
package org.apache.ibatis.scripting.xmltags;

import java.util.HashMap;
import java.util.Map;

import ognl.OgnlContext;
import ognl.OgnlException;
import ognl.OgnlRuntime;
import ognl.PropertyAccessor;

import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.Configuration;

/**
 * @author Clinton Begin
 */
public class DynamicContext {

  public static final String PARAMETER_OBJECT_KEY = "_parameter";
  public static final String DATABASE_ID_KEY = "_databaseId";

  static {
    OgnlRuntime.setPropertyAccessor(ContextMap.class, new ContextAccessor());
  }

  // 参数的上下文
  private final ContextMap bindings;
  // 在 SqlNode 解析动态 SQL 时， 会将解析后的 SQL 语句片段添加到该属性中保存， 最终拼出完整的 SQL 语句
  private final StringBuilder sqlBuilder = new StringBuilder();
  private int uniqueNumber = 0;

  /**
   * 构造函数
   * @param configuration 核心的Configuration对象
   * @param parameterObject SQL 对应的实参
   */
  public DynamicContext(Configuration configuration, Object parameterObject) {
    if (parameterObject != null && !(parameterObject instanceof Map)) {
      // 不是 Map 对象， 则创对应的 MetaObject 对象
      MetaObject metaObject = configuration.newMetaObject(parameterObject);
      // 将 MetaObject 对象封装成 ContextMap 对象
      bindings = new ContextMap(metaObject);
    } else {
      bindings = new ContextMap(null);
    }
    // 将 parameterObject 放入 bindings 中， key 为 PARAMETER_OBJECT_KEY（_parameter）
    bindings.put(PARAMETER_OBJECT_KEY, parameterObject);
    // 将对应的 databaseId 放入， 键为 PARAMETER_OBJECT_KEY（_databaseId）
    bindings.put(DATABASE_ID_KEY, configuration.getDatabaseId());
  }

  /**
   * 获取整个参数的上下文
   * @return
   */
  public Map<String, Object> getBindings() {
    return bindings;
  }

  /**
   * 绑定
   * @param name key
   * @param value value
   */
  public void bind(String name, Object value) {
    bindings.put(name, value);
  }

  /**
   * 追加 SQL 片段
   * @param sql
   */
  public void appendSql(String sql) {
    sqlBuilder.append(sql);
    sqlBuilder.append(" ");
  }

  /**
   * 获取 SQL （解析后的）
   * @return
   */
  public String getSql() {
    return sqlBuilder.toString().trim();
  }

  public int getUniqueNumber() {
    return uniqueNumber++;
  }

  /**
   * 内部类， 集成 HashMap， 并重写了 get 方法
   */
  static class ContextMap extends HashMap<String, Object> {
    private static final long serialVersionUID = 2977601501966151582L;

    // 将用户传入的参数封装成了 MetaObject 对象
    private MetaObject parameterMetaObject;
    /**
     *  将对应的 MetaObject 对象封装成 ContextMap 对象
     */
    public ContextMap(MetaObject parameterMetaObject) {
      this.parameterMetaObject = parameterMetaObject;
    }

    /**
     * 重写了 get 方法
     * @param key
     * @return
     */
    @Override
    public Object get(Object key) {
      String strKey = (String) key;
      if (super.containsKey(strKey)) {// 如果对象中含有该键， 直接返回对应的值即可
        return super.get(strKey);
      }

      // 从运行时的参数中查找对应的属性
      if (parameterMetaObject != null) {
        // issue #61 do not modify the context when reading
        return parameterMetaObject.getValue(strKey);
      }

      return null;
    }
  }

  /**
   *
   */
  static class ContextAccessor implements PropertyAccessor {

    @Override
    public Object getProperty(Map context, Object target, Object name)
        throws OgnlException {
      Map map = (Map) target;

      Object result = map.get(name);
      if (map.containsKey(name) || result != null) {
        return result;
      }

      Object parameterObject = map.get(PARAMETER_OBJECT_KEY);
      if (parameterObject instanceof Map) {
        return ((Map)parameterObject).get(name);
      }

      return null;
    }

    @Override
    public void setProperty(Map context, Object target, Object name, Object value)
        throws OgnlException {
      Map<Object, Object> map = (Map<Object, Object>) target;
      map.put(name, value);
    }

    @Override
    public String getSourceAccessor(OgnlContext arg0, Object arg1, Object arg2) {
      return null;
    }

    @Override
    public String getSourceSetter(OgnlContext arg0, Object arg1, Object arg2) {
      return null;
    }
  }
}