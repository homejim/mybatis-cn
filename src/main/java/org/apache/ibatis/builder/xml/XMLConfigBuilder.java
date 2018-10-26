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
package org.apache.ibatis.builder.xml;

import java.io.InputStream;
import java.io.Reader;
import java.util.Properties;
import javax.sql.DataSource;

import org.apache.ibatis.builder.BaseBuilder;
import org.apache.ibatis.builder.BuilderException;
import org.apache.ibatis.datasource.DataSourceFactory;
import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.executor.loader.ProxyFactory;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.io.VFS;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.mapping.DatabaseIdProvider;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.parsing.XPathParser;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.reflection.DefaultReflectorFactory;
import org.apache.ibatis.reflection.MetaClass;
import org.apache.ibatis.reflection.ReflectorFactory;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.reflection.wrapper.ObjectWrapperFactory;
import org.apache.ibatis.session.AutoMappingBehavior;
import org.apache.ibatis.session.AutoMappingUnknownColumnBehavior;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.LocalCacheScope;
import org.apache.ibatis.transaction.TransactionFactory;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeHandler;

/**
 * @author Clinton Begin
 * @author Kazuki Shimizu
 */
public class XMLConfigBuilder extends BaseBuilder {

  private boolean parsed;
  private final XPathParser parser;
  private String environment;
  private final ReflectorFactory localReflectorFactory = new DefaultReflectorFactory();

  public XMLConfigBuilder(Reader reader) {
    this(reader, null, null);
  }

  public XMLConfigBuilder(Reader reader, String environment) {
    this(reader, environment, null);
  }

  public XMLConfigBuilder(Reader reader, String environment, Properties props) {
    this(new XPathParser(reader, true, props, new XMLMapperEntityResolver()), environment, props);
  }

  public XMLConfigBuilder(InputStream inputStream) {
    this(inputStream, null, null);
  }

  public XMLConfigBuilder(InputStream inputStream, String environment) {
    this(inputStream, environment, null);
  }

  public XMLConfigBuilder(InputStream inputStream, String environment, Properties props) {
    this(new XPathParser(inputStream, true, props, new XMLMapperEntityResolver()), environment, props);
  }

  private XMLConfigBuilder(XPathParser parser, String environment, Properties props) {
    super(new Configuration());
    ErrorContext.instance().resource("SQL Mapper Configuration");
    this.configuration.setVariables(props);
    this.parsed = false;
    this.environment = environment;
    this.parser = parser;
  }

  /**
   * 解析 mybatis-config.xml
   * @return
   */
  public Configuration parse() {
    // 只能解析一次
    if (parsed) {
      throw new BuilderException("Each XMLConfigBuilder can only be used once.");
    }
    parsed = true;
    parseConfiguration(parser.evalNode("/configuration"));
    return configuration;
  }

  private void parseConfiguration(XNode root) {
    try {
      //issue #117 read properties first
      // 解析 properties 节点
      propertiesElement(root.evalNode("properties"));
      // 解析 settings 节点
      Properties settings = settingsAsProperties(root.evalNode("settings"));
      loadCustomVfs(settings);
      // 解析 typeAliases 节点
      typeAliasesElement(root.evalNode("typeAliases"));
      // 解析 plugins 节点
      pluginElement(root.evalNode("plugins"));
      // 解析 objectFactory 节点
      objectFactoryElement(root.evalNode("objectFactory"));
      // 解析 objectWrapperFactory 节点
      objectWrapperFactoryElement(root.evalNode("objectWrapperFactory"));
      // 解析 reflectorFactory 节点
      reflectorFactoryElement(root.evalNode("reflectorFactory"));
      settingsElement(settings);
      // 解析 environments 节点， 需要在 objectFactory 和 objectWrapperFactory才能读取
      environmentsElement(root.evalNode("environments"));
      // 解析 databaseIdProvider 节点
      databaseIdProviderElement(root.evalNode("databaseIdProvider"));
      // 解析 typeHandlers 节点
      typeHandlerElement(root.evalNode("typeHandlers"));
      // 解析 mappers 节点
      mapperElement(root.evalNode("mappers"));
    } catch (Exception e) {
      throw new BuilderException("Error parsing SQL Mapper Configuration. Cause: " + e, e);
    }
  }

  /**
   * 解析 <settings> 节点
   * @param context
   * @return
   */
  private Properties settingsAsProperties(XNode context) {
    if (context == null) {
      return new Properties();
    }
    // 解析所有的子节点， 并存到 Properties 对象中。
    Properties props = context.getChildrenAsProperties();
    // Check that all settings are known to the configuration class
    // 创建对应的 MetaClass 对象
    MetaClass metaConfig = MetaClass.forClass(Configuration.class, localReflectorFactory);
    // 检测 Configuraion 对象中是否定义了相应的 setter 方法, 不定义代表不存在该属性， 直接抛异常
    for (Object key : props.keySet()) {
      if (!metaConfig.hasSetter(String.valueOf(key))) {
        throw new BuilderException("The setting " + key + " is not known.  Make sure you spelled it correctly (case sensitive).");
      }
    }
    return props;
  }

  private void loadCustomVfs(Properties props) throws ClassNotFoundException {
    String value = props.getProperty("vfsImpl");
    if (value != null) {
      String[] clazzes = value.split(",");
      for (String clazz : clazzes) {
        if (!clazz.isEmpty()) {
          @SuppressWarnings("unchecked")
          Class<? extends VFS> vfsImpl = (Class<? extends VFS>)Resources.classForName(clazz);
          configuration.setVfsImpl(vfsImpl);
        }
      }
    }
  }

  /**
   * 解析 <typeAliases>
   * @param parent
   */
  private void typeAliasesElement(XNode parent) {
    if (parent != null) {
      // 处理全部的子类
      for (XNode child : parent.getChildren()) {
        // 处理 <package> 子节点
        if ("package".equals(child.getName())) {
          // 获取配置处的属性 name , 其对应着包名
          String typeAliasPackage = child.getStringAttribute("name");
          configuration.getTypeAliasRegistry().registerAliases(typeAliasPackage);
        } else {
          // 处理 <typeAlias>节点
          String alias = child.getStringAttribute("alias");
          String type = child.getStringAttribute("type");
          try {
            Class<?> clazz = Resources.classForName(type);
            if (alias == null) {
              // 如果alias没有配置，则按照约定的方式注册类
              typeAliasRegistry.registerAlias(clazz);
            } else {
              // alias 配置了， 则将类注册到alias
              typeAliasRegistry.registerAlias(alias, clazz);
            }
          } catch (ClassNotFoundException e) {
            throw new BuilderException("Error registering typeAlias for '" + alias + "'. Cause: " + e, e);
          }
        }
      }
    }
  }

  /**
   * 解析 plugins 节点
   * @param parent
   * @throws Exception
   */
  private void pluginElement(XNode parent) throws Exception {
    if (parent != null) {
      // 遍历 <pluagins> 下的所有 <plugin> 节点
      for (XNode child : parent.getChildren()) {
        // 获取对应的 <plugin> 中的 interceptor 属性
        String interceptor = child.getStringAttribute("interceptor");
        // 获取 <plugin> 下的所有 <property> 节点， 并以 name->value 的形式存入 Properties 对象中
        Properties properties = child.getChildrenAsProperties();
        // 通过反射生成对象
        Interceptor interceptorInstance = (Interceptor) resolveClass(interceptor).newInstance();
        // 设置拦截器的属性
        interceptorInstance.setProperties(properties);
        // 将拦截器添加到 Configuration 对象中
        configuration.addInterceptor(interceptorInstance);
      }
    }
  }

  /**
   * 解析 <objectFactory>
   * @param context
   * @throws Exception
   */
  private void objectFactoryElement(XNode context) throws Exception {
    if (context != null) {
      // 获取 <objectFactory> 的type属性
      String type = context.getStringAttribute("type");
      // 获取 <plugin> 下的所有 <property> 节点， 并以 name->value 的形式存入 Properties 对象中
      Properties properties = context.getChildrenAsProperties();
      // 通过反射生成对象
      ObjectFactory factory = (ObjectFactory) resolveClass(type).newInstance();
      // 设置属性
      factory.setProperties(properties);
      // 将 ObjectFactory 对象设置到 Configuration 对象中
      configuration.setObjectFactory(factory);
    }
  }

  /**
   * 解析 <objectWrapperFactory>
   * @param context
   * @throws Exception
   */
  private void objectWrapperFactoryElement(XNode context) throws Exception {
    if (context != null) {
      // 获取 <objectWrapperFactory> 的type属性
      String type = context.getStringAttribute("type");
      // 通过反射生成 ObjectWrapperFactory 对象
      ObjectWrapperFactory factory = (ObjectWrapperFactory) resolveClass(type).newInstance();
      // 将 ObjectWrapperFactory 对象设置到 Configuration 对象中
      configuration.setObjectWrapperFactory(factory);
    }
  }

  /**
   *  解析 <reflectorFactory>
   * @param context
   * @throws Exception
   */
  private void reflectorFactoryElement(XNode context) throws Exception {
    if (context != null) {
      // 获取 <objectWrapperFactory> 的type属性
      String type = context.getStringAttribute("type");
      // 通过反射生成 ReflectorFactory 对象
      ReflectorFactory factory = (ReflectorFactory) resolveClass(type).newInstance();
      // 将 ReflectorFactory 对象设置到 Configuration 对象中
      configuration.setReflectorFactory(factory);
    }
  }

  /**
   * 解析 <properties> 节点
   * @param context 对应的节点
   * @throws Exception
   */
  private void propertiesElement(XNode context) throws Exception {
    if (context != null) {
      // 先解析<properties>下的各个子节点， 以name->value的形式记录到 Properties 对象中
      Properties defaults = context.getChildrenAsProperties();
      // 解析 resource 和 url 属性
      String resource = context.getStringAttribute("resource");
      String url = context.getStringAttribute("url");
      // resource 和 url 属性不能同时不为空， 也就是说只能存在一个
      if (resource != null && url != null) {
        throw new BuilderException("The properties element cannot specify both a URL and a resource based property file reference.  Please specify one or the other.");
      }
      // 根据 resource 或 url， 将对应的 .properties 文件加载进来， 并将内容全部存到 Properties 对象中
      if (resource != null) {
        defaults.putAll(Resources.getResourceAsProperties(resource));
      } else if (url != null) {
        defaults.putAll(Resources.getUrlAsProperties(url));
      }
      // 将Configuration 对象中原先的属性合并进来。
      Properties vars = configuration.getVariables();
      if (vars != null) {
        defaults.putAll(vars);
      }
      // 两个含有该成员变量的类对象更新
      parser.setVariables(defaults);
      configuration.setVariables(defaults);
    }
  }

  private void settingsElement(Properties props) throws Exception {
    configuration.setAutoMappingBehavior(AutoMappingBehavior.valueOf(props.getProperty("autoMappingBehavior", "PARTIAL")));
    configuration.setAutoMappingUnknownColumnBehavior(AutoMappingUnknownColumnBehavior.valueOf(props.getProperty("autoMappingUnknownColumnBehavior", "NONE")));
    configuration.setCacheEnabled(booleanValueOf(props.getProperty("cacheEnabled"), true));
    configuration.setProxyFactory((ProxyFactory) createInstance(props.getProperty("proxyFactory")));
    configuration.setLazyLoadingEnabled(booleanValueOf(props.getProperty("lazyLoadingEnabled"), false));
    configuration.setAggressiveLazyLoading(booleanValueOf(props.getProperty("aggressiveLazyLoading"), false));
    configuration.setMultipleResultSetsEnabled(booleanValueOf(props.getProperty("multipleResultSetsEnabled"), true));
    configuration.setUseColumnLabel(booleanValueOf(props.getProperty("useColumnLabel"), true));
    configuration.setUseGeneratedKeys(booleanValueOf(props.getProperty("useGeneratedKeys"), false));
    configuration.setDefaultExecutorType(ExecutorType.valueOf(props.getProperty("defaultExecutorType", "SIMPLE")));
    configuration.setDefaultStatementTimeout(integerValueOf(props.getProperty("defaultStatementTimeout"), null));
    configuration.setDefaultFetchSize(integerValueOf(props.getProperty("defaultFetchSize"), null));
    configuration.setMapUnderscoreToCamelCase(booleanValueOf(props.getProperty("mapUnderscoreToCamelCase"), false));
    configuration.setSafeRowBoundsEnabled(booleanValueOf(props.getProperty("safeRowBoundsEnabled"), false));
    configuration.setLocalCacheScope(LocalCacheScope.valueOf(props.getProperty("localCacheScope", "SESSION")));
    configuration.setJdbcTypeForNull(JdbcType.valueOf(props.getProperty("jdbcTypeForNull", "OTHER")));
    configuration.setLazyLoadTriggerMethods(stringSetValueOf(props.getProperty("lazyLoadTriggerMethods"), "equals,clone,hashCode,toString"));
    configuration.setSafeResultHandlerEnabled(booleanValueOf(props.getProperty("safeResultHandlerEnabled"), true));
    configuration.setDefaultScriptingLanguage(resolveClass(props.getProperty("defaultScriptingLanguage")));
    @SuppressWarnings("unchecked")
    Class<? extends TypeHandler> typeHandler = (Class<? extends TypeHandler>)resolveClass(props.getProperty("defaultEnumTypeHandler"));
    configuration.setDefaultEnumTypeHandler(typeHandler);
    configuration.setCallSettersOnNulls(booleanValueOf(props.getProperty("callSettersOnNulls"), false));
    configuration.setUseActualParamName(booleanValueOf(props.getProperty("useActualParamName"), true));
    configuration.setReturnInstanceForEmptyRow(booleanValueOf(props.getProperty("returnInstanceForEmptyRow"), false));
    configuration.setLogPrefix(props.getProperty("logPrefix"));
    @SuppressWarnings("unchecked")
    Class<? extends Log> logImpl = (Class<? extends Log>)resolveClass(props.getProperty("logImpl"));
    configuration.setLogImpl(logImpl);
    configuration.setConfigurationFactory(resolveClass(props.getProperty("configurationFactory")));
  }

  /**
   * 解析 <environments>
   * @param context
   * @throws Exception
   */
  private void environmentsElement(XNode context) throws Exception {
    if (context != null) {
      // 未指定， 则使用 default 属性指定的 <environment>
      if (environment == null) {
        environment = context.getStringAttribute("default");
      }
      // 遍历所有的 <environment> 子节点
      for (XNode child : context.getChildren()) {
        String id = child.getStringAttribute("id");
        // 只有 id 与 XMLConfigBuilder 的 environment 匹配上才会进行解析里面的内容
        if (isSpecifiedEnvironment(id)) {
          // 创建 TransactionFactory 对象
          TransactionFactory txFactory = transactionManagerElement(child.evalNode("transactionManager"));
          // 创建 DataSourceFactory 对象， 并以之创建 DataSource 对象
          DataSourceFactory dsFactory = dataSourceElement(child.evalNode("dataSource"));
          DataSource dataSource = dsFactory.getDataSource();
          // 创建 Environment.Builder 对象， 将以上产生的对象对应设置到该对象中
          Environment.Builder environmentBuilder = new Environment.Builder(id)
                  .transactionFactory(txFactory)
                  .dataSource(dataSource);
          // environmentBuilder.build()创建 Environment 对象， 并设置到 Configuration 对象对应的成员变量中
          configuration.setEnvironment(environmentBuilder.build());
        }
      }
    }
  }

  /**
   * 解析 <databaseIdProvider>
   * @param context
   * @throws Exception
   */
  private void databaseIdProviderElement(XNode context) throws Exception {
    DatabaseIdProvider databaseIdProvider = null;
    if (context != null) {
      String type = context.getStringAttribute("type");
      // awful patch to keep backward compatibility
      // 为了兼容， 更改 type 为 VENDOR 至 DB_VENDOR
      if ("VENDOR".equals(type)) {
        type = "DB_VENDOR";
      }
      // 解析对应的配置信息
      Properties properties = context.getChildrenAsProperties();
      // 通过反射生成对象
      databaseIdProvider = (DatabaseIdProvider) resolveClass(type).newInstance();
      // 设置属性
      databaseIdProvider.setProperties(properties);
    }
    Environment environment = configuration.getEnvironment();

    if (environment != null && databaseIdProvider != null) {
      String databaseId = databaseIdProvider.getDatabaseId(environment.getDataSource());
      configuration.setDatabaseId(databaseId);
    }
  }

  private TransactionFactory transactionManagerElement(XNode context) throws Exception {
    if (context != null) {
      String type = context.getStringAttribute("type");
      Properties props = context.getChildrenAsProperties();
      TransactionFactory factory = (TransactionFactory) resolveClass(type).newInstance();
      factory.setProperties(props);
      return factory;
    }
    throw new BuilderException("Environment declaration requires a TransactionFactory.");
  }

  private DataSourceFactory dataSourceElement(XNode context) throws Exception {
    if (context != null) {
      String type = context.getStringAttribute("type");
      Properties props = context.getChildrenAsProperties();
      DataSourceFactory factory = (DataSourceFactory) resolveClass(type).newInstance();
      factory.setProperties(props);
      return factory;
    }
    throw new BuilderException("Environment declaration requires a DataSourceFactory.");
  }

  /**
   * 解析 <typeHandlers>
   * @param parent
   * @throws Exception
   */
  private void typeHandlerElement(XNode parent) throws Exception {
    if (parent != null) {
      // 遍历所有的子节点
      for (XNode child : parent.getChildren()) {
        // 包（package）子节点
        if ("package".equals(child.getName())) {
          String typeHandlerPackage = child.getStringAttribute("name");
          typeHandlerRegistry.register(typeHandlerPackage);
        } else { // 非 package 子节点
          // 获取相应的属性
          String javaTypeName = child.getStringAttribute("javaType");
          String jdbcTypeName = child.getStringAttribute("jdbcType");
          String handlerTypeName = child.getStringAttribute("handler");
          // 根据属性， 通过反射创建对象
          Class<?> javaTypeClass = resolveClass(javaTypeName);
          JdbcType jdbcType = resolveJdbcType(jdbcTypeName);
          Class<?> typeHandlerClass = resolveClass(handlerTypeName);
          // 将对象注册到 typeHandlerRegistry 对象中
          if (javaTypeClass != null) {
            if (jdbcType == null) {
              typeHandlerRegistry.register(javaTypeClass, typeHandlerClass);
            } else {
              typeHandlerRegistry.register(javaTypeClass, jdbcType, typeHandlerClass);
            }
          } else {
            typeHandlerRegistry.register(typeHandlerClass);
          }
        }
      }
    }
  }

  /**
   * 解析 <mappers>
   * @param parent
   * @throws Exception
   */
  private void mapperElement(XNode parent) throws Exception {
    if (parent != null) {
      for (XNode child : parent.getChildren()) {
        // <package> 子节点， 就是包下面的类
        if ("package".equals(child.getName())) {
          String mapperPackage = child.getStringAttribute("name");
          configuration.addMappers(mapperPackage);
        } else { // 对应 <mapper> 子节点
          // resource / url / class 中 3选1
          String resource = child.getStringAttribute("resource");
          String url = child.getStringAttribute("url");
          String mapperClass = child.getStringAttribute("class");
          // 如果节点指定了 resource 或 url， 则创建 XMLMapperBuilder 对象，
          // 通过该对象的 parse() 函数将 mapper 添加到 configuration 中
          if (resource != null && url == null && mapperClass == null) {
            ErrorContext.instance().resource(resource);
            InputStream inputStream = Resources.getResourceAsStream(resource);
            XMLMapperBuilder mapperParser = new XMLMapperBuilder(inputStream, configuration, resource, configuration.getSqlFragments());
            mapperParser.parse();
          } else if (resource == null && url != null && mapperClass == null) {
            ErrorContext.instance().resource(url);
            InputStream inputStream = Resources.getUrlAsStream(url);
            XMLMapperBuilder mapperParser = new XMLMapperBuilder(inputStream, configuration, url, configuration.getSqlFragments());
            mapperParser.parse();
          } else if (resource == null && url == null && mapperClass != null) {
            Class<?> mapperInterface = Resources.classForName(mapperClass);
            configuration.addMapper(mapperInterface);
          } else {
            throw new BuilderException("A mapper element may only specify a url, resource or class, but not more than one.");
          }
        }
      }
    }
  }

  private boolean isSpecifiedEnvironment(String id) {
    if (environment == null) {
      throw new BuilderException("No environment specified.");
    } else if (id == null) {
      throw new BuilderException("Environment requires an id attribute.");
    } else if (environment.equals(id)) {
      return true;
    }
    return false;
  }

}
