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
package org.apache.ibatis.datasource.unpooled;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.util.Enumeration;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import javax.sql.DataSource;

import org.apache.ibatis.io.Resources;

/**
 * @author Clinton Begin
 * @author Eduardo Macarron
 *
 * 没使用连接池的DataSource, 每次使用都会创建一个新连接
 *
 */
public class UnpooledDataSource implements DataSource {

  // 类加载器
  private ClassLoader driverClassLoader;
  // 数据库连接的相关设置属性
  private Properties driverProperties;
  // 缓存所有已经注册的数据库连接驱动
  private static Map<String, Driver> registeredDrivers = new ConcurrentHashMap<>();

  // 很熟悉的数据库连接的必备
  private String driver;
  private String url;
  private String username;
  private String password;

  // 是否自动提交
  private Boolean autoCommit;
  // 事物的隔离级别
  private Integer defaultTransactionIsolationLevel;

  static {
    // 获取 DriverManager 的 driver
    Enumeration<Driver> drivers = DriverManager.getDrivers();
    while (drivers.hasMoreElements()) {
      Driver driver = drivers.nextElement();
      // 缓存
      registeredDrivers.put(driver.getClass().getName(), driver);
    }
  }

  public UnpooledDataSource() {
  }

  public UnpooledDataSource(String driver, String url, String username, String password) {
    this.driver = driver;
    this.url = url;
    this.username = username;
    this.password = password;
  }

  public UnpooledDataSource(String driver, String url, Properties driverProperties) {
    this.driver = driver;
    this.url = url;
    this.driverProperties = driverProperties;
  }

  public UnpooledDataSource(ClassLoader driverClassLoader, String driver, String url, String username, String password) {
    this.driverClassLoader = driverClassLoader;
    this.driver = driver;
    this.url = url;
    this.username = username;
    this.password = password;
  }

  public UnpooledDataSource(ClassLoader driverClassLoader, String driver, String url, Properties driverProperties) {
    this.driverClassLoader = driverClassLoader;
    this.driver = driver;
    this.url = url;
    this.driverProperties = driverProperties;
  }

  @Override
  public Connection getConnection() throws SQLException {
    return doGetConnection(username, password);
  }

  @Override
  public Connection getConnection(String username, String password) throws SQLException {
    return doGetConnection(username, password);
  }

  @Override
  public void setLoginTimeout(int loginTimeout) throws SQLException {
    DriverManager.setLoginTimeout(loginTimeout);
  }

  @Override
  public int getLoginTimeout() throws SQLException {
    return DriverManager.getLoginTimeout();
  }

  @Override
  public void setLogWriter(PrintWriter logWriter) throws SQLException {
    DriverManager.setLogWriter(logWriter);
  }

  @Override
  public PrintWriter getLogWriter() throws SQLException {
    return DriverManager.getLogWriter();
  }

  public ClassLoader getDriverClassLoader() {
    return driverClassLoader;
  }

  public void setDriverClassLoader(ClassLoader driverClassLoader) {
    this.driverClassLoader = driverClassLoader;
  }

  public Properties getDriverProperties() {
    return driverProperties;
  }

  public void setDriverProperties(Properties driverProperties) {
    this.driverProperties = driverProperties;
  }

  public String getDriver() {
    return driver;
  }

  public synchronized void setDriver(String driver) {
    this.driver = driver;
  }

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  public Boolean isAutoCommit() {
    return autoCommit;
  }

  public void setAutoCommit(Boolean autoCommit) {
    this.autoCommit = autoCommit;
  }

  public Integer getDefaultTransactionIsolationLevel() {
    return defaultTransactionIsolationLevel;
  }

  public void setDefaultTransactionIsolationLevel(Integer defaultTransactionIsolationLevel) {
    this.defaultTransactionIsolationLevel = defaultTransactionIsolationLevel;
  }

  /**
   * 获取 Connection
   * @param username 数据库用户名
   * @param password 数据库密码
   * @return 数据库连接
   * @throws SQLException
   */
  private Connection doGetConnection(String username, String password) throws SQLException {
    Properties props = new Properties();
    if (driverProperties != null) {
      props.putAll(driverProperties);
    }
    if (username != null) {
      props.setProperty("user", username);
    }
    if (password != null) {
      props.setProperty("password", password);
    }
    return doGetConnection(props);
  }

  /**
   * 获取连接
   */
  private Connection doGetConnection(Properties properties) throws SQLException {
    // 初始化数据库驱动
    initializeDriver();
    // 获取连接， 与JDBC一致
    Connection connection = DriverManager.getConnection(url, properties);
    // 设置 autoCommit和事物的隔离级别
    configureConnection(connection);
    return connection;
  }

  /**
   * 初始化数据库驱动
   */
  private synchronized void initializeDriver() throws SQLException {
    // 如果驱动没有缓存
    if (!registeredDrivers.containsKey(driver)) {
      Class<?> driverType;
      try {
        if (driverClassLoader != null) {
          // 注册驱动
          driverType = Class.forName(driver, true, driverClassLoader);
        } else {
          // 从资源类中加载驱动
          driverType = Resources.classForName(driver);
        }
        // DriverManager requires the driver to be loaded via the system ClassLoader.
        // http://www.kfu.com/~nsayer/Java/dyn-jdbc.html
        // 创建一个驱动类的实例， 并使用 DriverManager 进行注册。 最后放入 registeredDrivers 中缓存
        Driver driverInstance = (Driver) driverType.newInstance();
        DriverManager.registerDriver(new DriverProxy(driverInstance));
        registeredDrivers.put(driver, driverInstance);
      } catch (Exception e) {
        throw new SQLException("Error setting driver on UnpooledDataSource. Cause: " + e);
      }
    }
  }

  /**
   * 设置 autoCommit和事物的隔离级别
   * @param conn 数据库连接
   * @throws SQLException
   */
  private void configureConnection(Connection conn) throws SQLException {
    // 设置是否自动提交
    if (autoCommit != null && autoCommit != conn.getAutoCommit()) {
      conn.setAutoCommit(autoCommit);
    }
    // 设置事物的隔离级别
    if (defaultTransactionIsolationLevel != null) {
      conn.setTransactionIsolation(defaultTransactionIsolationLevel);
    }
  }

  /**
   * 驱动静态内部类
   */
  private static class DriverProxy implements Driver {
    private Driver driver;

    DriverProxy(Driver d) {
      this.driver = d;
    }

    @Override
    public boolean acceptsURL(String u) throws SQLException {
      return this.driver.acceptsURL(u);
    }

    @Override
    public Connection connect(String u, Properties p) throws SQLException {
      return this.driver.connect(u, p);
    }

    @Override
    public int getMajorVersion() {
      return this.driver.getMajorVersion();
    }

    @Override
    public int getMinorVersion() {
      return this.driver.getMinorVersion();
    }

    @Override
    public DriverPropertyInfo[] getPropertyInfo(String u, Properties p) throws SQLException {
      return this.driver.getPropertyInfo(u, p);
    }

    @Override
    public boolean jdbcCompliant() {
      return this.driver.jdbcCompliant();
    }

    // @Override only valid jdk7+
    public Logger getParentLogger() {
      return Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
    }
  }

  @Override
  public <T> T unwrap(Class<T> iface) throws SQLException {
    throw new SQLException(getClass().getName() + " is not a wrapper.");
  }

  @Override
  public boolean isWrapperFor(Class<?> iface) throws SQLException {
    return false;
  }

  // @Override only valid jdk7+
  public Logger getParentLogger() {
    // requires JDK version 1.6
    return Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
  }

}
