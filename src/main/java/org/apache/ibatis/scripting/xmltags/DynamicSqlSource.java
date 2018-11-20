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

import java.util.Map;

import org.apache.ibatis.builder.SqlSourceBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.session.Configuration;

/**
 * @author Clinton Begin
 * 负责动态SQL的解析
 */
public class DynamicSqlSource implements SqlSource {

  private final Configuration configuration;
  private final SqlNode rootSqlNode;

  public DynamicSqlSource(Configuration configuration, SqlNode rootSqlNode) {
    this.configuration = configuration;
    this.rootSqlNode = rootSqlNode;
  }

  @Override
  public BoundSql getBoundSql(Object parameterObject) {
    // 获取 DynamicContext 对象, parameterObject为用户传入的参数
    DynamicContext context = new DynamicContext(configuration, parameterObject);
    // 解析 context
    rootSqlNode.apply(context);
    // 创建 SqlSourceBuilder 对象
    SqlSourceBuilder sqlSourceParser = new SqlSourceBuilder(configuration);
    // 获取参数的类型
    Class<?> parameterType = parameterObject == null ? Object.class : parameterObject.getClass();
    // 解析， 将 ${xxx}替换成 ？
    SqlSource sqlSource = sqlSourceParser.parse(context.getSql(), parameterType, context.getBindings());
    // 获取 BoundSql 对象
    BoundSql boundSql = sqlSource.getBoundSql(parameterObject);
    // 将 DynamicContext.bindings 中的参数复制到 metaParameters 集合中
    for (Map.Entry<String, Object> entry : context.getBindings().entrySet()) {
      boundSql.setAdditionalParameter(entry.getKey(), entry.getValue());
    }
    return boundSql;
  }

}
