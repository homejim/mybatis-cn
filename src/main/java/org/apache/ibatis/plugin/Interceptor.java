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
package org.apache.ibatis.plugin;

import java.util.Properties;

/**
 * 拦截器接口
 *
 * @author Clinton Begin
 */
public interface Interceptor {

  /**
   * 执行拦截逻辑的方法
   *
   * @param invocation 调用信息
   * @return 调用结果
   * @throws Throwable 异常
   */
  Object intercept(Invocation invocation) throws Throwable;

  /**
   * 代理
   *
   * @param target
   * @return
   */
  Object plugin(Object target);

  /**
   * 根据配置来初始化 Interceptor 方法
   * @param properties
   */
  void setProperties(Properties properties);

}
