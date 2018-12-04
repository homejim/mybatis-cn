/**
 *    Copyright 2009-2016 the original author or authors.
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
package org.apache.ibatis.executor;

import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.transaction.Transaction;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collections;
import java.util.List;

/**
 * 简单Executor实现类
 * @author Clinton Begin
 */
public class SimpleExecutor extends BaseExecutor {

  public SimpleExecutor(Configuration configuration, Transaction transaction) {
    super(configuration, transaction);
  }


  /**
   * 更新（insert | udpdate | delete）实现
   * @param ms
   * @param parameter
   * @return
   * @throws SQLException
   */
  @Override
  public int doUpdate(MappedStatement ms, Object parameter) throws SQLException {
    Statement stmt = null;
    try {
      // 获取配置
      Configuration configuration = ms.getConfiguration();
      // 获取 StatementHandler
      StatementHandler handler = configuration.newStatementHandler(this, ms, parameter, RowBounds.DEFAULT, null, null);
      // 准备 Statement
      stmt = prepareStatement(handler, ms.getStatementLog());
      // 执行
      return handler.update(stmt);
    } finally {
      // 关闭
      closeStatement(stmt);
    }
  }

  /**
   * 查询
   */
  @Override
  public <E> List<E> doQuery(MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql) throws SQLException {
    // 使用 Statement
    Statement stmt = null;
    try {
      // 获取配置对象
      Configuration configuration = ms.getConfiguration();
      // 获取对应的 StatementHandler
      StatementHandler handler = configuration.newStatementHandler(wrapper, ms, parameter, rowBounds, resultHandler, boundSql);
      // Statement 初始化
      stmt = prepareStatement(handler, ms.getStatementLog());
      // 查询并完成结果映射
      return handler.<E>query(stmt, resultHandler);
    } finally {
      // 关闭
      closeStatement(stmt);
    }
  }

  /**
   * 查询， 游标
   */
  @Override
  protected <E> Cursor<E> doQueryCursor(MappedStatement ms, Object parameter, RowBounds rowBounds, BoundSql boundSql) throws SQLException {
    Configuration configuration = ms.getConfiguration();
    StatementHandler handler = configuration.newStatementHandler(wrapper, ms, parameter, rowBounds, null, boundSql);
    Statement stmt = prepareStatement(handler, ms.getStatementLog());
    return handler.<E>queryCursor(stmt);
  }

  /**
   * 返回空集合， 即不处理批处理
   */
  @Override
  public List<BatchResult> doFlushStatements(boolean isRollback) throws SQLException {
    return Collections.emptyList();
  }

  /**
   * 准备 Statement
   * @param handler
   * @param statementLog
   * @return
   * @throws SQLException
   */
  private Statement prepareStatement(StatementHandler handler, Log statementLog) throws SQLException {
    Statement stmt;
    // 获取连接
    Connection connection = getConnection(statementLog);
    // 调用 handler.prepare() 创建 Statement 对象
    stmt = handler.prepare(connection, transaction.getTimeout());
    // 处理占位符
    handler.parameterize(stmt);
    return stmt;
  }

}
