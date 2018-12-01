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
package org.apache.ibatis.executor.statement;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.keygen.Jdbc3KeyGenerator;
import org.apache.ibatis.executor.keygen.KeyGenerator;
import org.apache.ibatis.executor.keygen.SelectKeyGenerator;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ResultSetType;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

/**
 * 使用 java.sql.Statement 对象对数据库进行操作
 * 所处理的 SQL 语句中没有占位符
 * @author Clinton Begin
 */
public class SimpleStatementHandler extends BaseStatementHandler {

  public SimpleStatementHandler(Executor executor, MappedStatement mappedStatement, Object parameter, RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql) {
    super(executor, mappedStatement, parameter, rowBounds, resultHandler, boundSql);
  }

  /**
   * insert | delete | update
   */
  @Override
  public int update(Statement statement) throws SQLException {
    // 获取 sql 语句
    String sql = boundSql.getSql();
    // 获取用户传入的参数
    Object parameterObject = boundSql.getParameterObject();
    // 获取对应的主键生成器
    KeyGenerator keyGenerator = mappedStatement.getKeyGenerator();
    int rows;
    if (keyGenerator instanceof Jdbc3KeyGenerator) {
      // 执行 SQL 语句
      statement.execute(sql, Statement.RETURN_GENERATED_KEYS);
      // 获取收影响的行
      rows = statement.getUpdateCount();
      // 将数据库生成的主键添加到 parameterObject 对象中
      keyGenerator.processAfter(executor, mappedStatement, statement, parameterObject);
    } else if (keyGenerator instanceof SelectKeyGenerator) {
      statement.execute(sql);
      rows = statement.getUpdateCount();
      // 执行 <selectKey> 节点配置的 SQL 语句来获取数据库生产的主键
      keyGenerator.processAfter(executor, mappedStatement, statement, parameterObject);
    } else {
      // 对应不需要进行主键处理的
      statement.execute(sql);
      rows = statement.getUpdateCount();
    }
    return rows;
  }

  /**
   * 批量数据操作
   * @param statement
   * @throws SQLException
   */
  @Override
  public void batch(Statement statement) throws SQLException {
    // 获取 SQL 语句， 该语句中没有占位符
    String sql = boundSql.getSql();
    statement.addBatch(sql);
  }

  /**
   * 查询
   */
  @Override
  public <E> List<E> query(Statement statement, ResultHandler resultHandler) throws SQLException {
    // 获取 SQL 语句， 该语句中没有占位符
    String sql = boundSql.getSql();
    // 执行 SQL 语句
    statement.execute(sql);
    // 处理结果集
    return resultSetHandler.<E>handleResultSets(statement);
  }

  /**
   * 查询游标， 与上面的方法类似
   */
  @Override
  public <E> Cursor<E> queryCursor(Statement statement) throws SQLException {
    String sql = boundSql.getSql();
    statement.execute(sql);
    return resultSetHandler.<E>handleCursorResultSets(statement);
  }

  /**
   * 初始化 Statement 对象， 通过 Connection 进行创建
   */
  @Override
  protected Statement instantiateStatement(Connection connection) throws SQLException {
    if (mappedStatement.getResultSetType() == ResultSetType.DEFAULT) {
      return connection.createStatement();
    } else {
      return connection.createStatement(mappedStatement.getResultSetType().getValue(), ResultSet.CONCUR_READ_ONLY);
    }
  }

  @Override
  public void parameterize(Statement statement) throws SQLException {
    // N/A
  }

}
