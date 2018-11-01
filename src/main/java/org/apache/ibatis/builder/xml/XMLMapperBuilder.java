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
package org.apache.ibatis.builder.xml;

import java.io.InputStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.ibatis.builder.BaseBuilder;
import org.apache.ibatis.builder.BuilderException;
import org.apache.ibatis.builder.CacheRefResolver;
import org.apache.ibatis.builder.IncompleteElementException;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.builder.ResultMapResolver;
import org.apache.ibatis.cache.Cache;
import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.Discriminator;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.ParameterMode;
import org.apache.ibatis.mapping.ResultFlag;
import org.apache.ibatis.mapping.ResultMap;
import org.apache.ibatis.mapping.ResultMapping;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.parsing.XPathParser;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeHandler;

/**
 * @author Clinton Begin
 */
public class XMLMapperBuilder extends BaseBuilder {

  private final XPathParser parser;
  private final MapperBuilderAssistant builderAssistant;
  private final Map<String, XNode> sqlFragments;
  private final String resource;

  @Deprecated
  public XMLMapperBuilder(Reader reader, Configuration configuration, String resource, Map<String, XNode> sqlFragments, String namespace) {
    this(reader, configuration, resource, sqlFragments);
    this.builderAssistant.setCurrentNamespace(namespace);
  }

  @Deprecated
  public XMLMapperBuilder(Reader reader, Configuration configuration, String resource, Map<String, XNode> sqlFragments) {
    this(new XPathParser(reader, true, configuration.getVariables(), new XMLMapperEntityResolver()),
        configuration, resource, sqlFragments);
  }

  public XMLMapperBuilder(InputStream inputStream, Configuration configuration, String resource, Map<String, XNode> sqlFragments, String namespace) {
    this(inputStream, configuration, resource, sqlFragments);
    this.builderAssistant.setCurrentNamespace(namespace);
  }

  public XMLMapperBuilder(InputStream inputStream, Configuration configuration, String resource, Map<String, XNode> sqlFragments) {
    this(new XPathParser(inputStream, true, configuration.getVariables(), new XMLMapperEntityResolver()),
        configuration, resource, sqlFragments);
  }

  private XMLMapperBuilder(XPathParser parser, Configuration configuration, String resource, Map<String, XNode> sqlFragments) {
    super(configuration);
    this.builderAssistant = new MapperBuilderAssistant(configuration, resource);
    this.parser = parser;
    this.sqlFragments = sqlFragments;
    this.resource = resource;
  }

  /**
   * 解析某个 xxx.xml
   */
  public void parse() {
    // 判断是否已经加载过了
    if (!configuration.isResourceLoaded(resource)) {
      // 具体的解析过程
      configurationElement(parser.evalNode("/mapper"));
      // 加入loadedResources， 表示已经解析
      configuration.addLoadedResource(resource);
      // 映射文件与对应 Mapper 接口的绑定
      bindMapperForNamespace();
    }
    // 解析在configurationElement函数中处理resultMap时其extends属性指向的父对象还没被处理的<resultMap>节点
    parsePendingResultMaps();
    // 解析在configurationElement函数中处理cache-ref时其指向的对象不存在的<cache>节点(如果cache-ref先于其指向的cache节点加载就会出现这种情况)
    parsePendingCacheRefs();
    // 如果cache没加载的话处理statement时也会抛出异常
    parsePendingStatements();
  }

  public XNode getSqlFragment(String refid) {
    return sqlFragments.get(refid);
  }

  /**
   * 解析对应的 Mapper.xml 文件
   * @param context
   */
  private void configurationElement(XNode context) {
    try {
      // 获取namespace属性， 其代表者这个文档的标识
      String namespace = context.getStringAttribute("namespace");
      if (namespace == null || namespace.equals("")) {
        throw new BuilderException("Mapper's namespace cannot be empty");
      }
      builderAssistant.setCurrentNamespace(namespace);
      // 解析 <cache-ref> 节点
      cacheRefElement(context.evalNode("cache-ref"));
      // 解析 <cache> 节点
      cacheElement(context.evalNode("cache"));
      // 解析 </mapper/parameterMap> 节点
      parameterMapElement(context.evalNodes("/mapper/parameterMap"));
      // 解析 </mapper/resultMap> 节点
      resultMapElements(context.evalNodes("/mapper/resultMap"));
      // 解析 </mapper/sql> 节点
      sqlElement(context.evalNodes("/mapper/sql"));
      // 解析 select|insert|update|delet 节点
      buildStatementFromContext(context.evalNodes("select|insert|update|delete"));
    } catch (Exception e) {
      throw new BuilderException("Error parsing Mapper XML. Cause: " + e, e);
    }
  }

  private void buildStatementFromContext(List<XNode> list) {
    if (configuration.getDatabaseId() != null) {
      buildStatementFromContext(list, configuration.getDatabaseId());
    }
    buildStatementFromContext(list, null);
  }

  private void buildStatementFromContext(List<XNode> list, String requiredDatabaseId) {
    // 遍历 XNode 节点
    for (XNode context : list) {
      // 建造者模式
      final XMLStatementBuilder statementParser = new XMLStatementBuilder(configuration, builderAssistant, context, requiredDatabaseId);
      try {
        // 解析
        statementParser.parseStatementNode();
      } catch (IncompleteElementException e) {
        // 无法解析的添加到 Configuration 对象
        configuration.addIncompleteStatement(statementParser);
      }
    }
  }

  /**
   * 解析未解析成功的 ResultMap
   */
  private void parsePendingResultMaps() {
    // 获取 Configuration.incompleteResultMaps 集合
    Collection<ResultMapResolver> incompleteResultMaps = configuration.getIncompleteResultMaps();
    synchronized (incompleteResultMaps) {
      // 迭代器遍历
      Iterator<ResultMapResolver> iter = incompleteResultMaps.iterator();
      while (iter.hasNext()) {
        try {
          // 重新解析
          iter.next().resolve();
          iter.remove();
        } catch (IncompleteElementException e) {
          // ResultMap is still missing a resource...
        }
      }
    }
  }

  /**
   * 处理解析未成功的 CacheRef
   */
  private void parsePendingCacheRefs() {
    // 获取 Configuration.mappedStatements 集合
    Collection<CacheRefResolver> incompleteCacheRefs = configuration.getIncompleteCacheRefs();
    synchronized (incompleteCacheRefs) {
      // 迭代器遍历
      Iterator<CacheRefResolver> iter = incompleteCacheRefs.iterator();
      while (iter.hasNext()) {
        try {
          // 重新解析
          iter.next().resolveCacheRef();
          iter.remove();
        } catch (IncompleteElementException e) {
          // 还是无法解析就不管了
        }
      }
    }
  }

  /**
   * 处理未解析成功的 statements
   */
  private void parsePendingStatements() {
    // 获取 Configuration.incompleteStatements 集合
    Collection<XMLStatementBuilder> incompleteStatements = configuration.getIncompleteStatements();
    synchronized (incompleteStatements) {//加锁
      // 迭代器进行遍历
      Iterator<XMLStatementBuilder> iter = incompleteStatements.iterator();
      while (iter.hasNext()) {
        try {
          iter.next().parseStatementNode();//重新解析
          iter.remove();// 移除
        } catch (IncompleteElementException e) {
          // 还是无法解析就不管了
        }
      }
    }
  }

  private void cacheRefElement(XNode context) {
    if (context != null) {
      configuration.addCacheRef(builderAssistant.getCurrentNamespace(), context.getStringAttribute("namespace"));
      CacheRefResolver cacheRefResolver = new CacheRefResolver(builderAssistant, context.getStringAttribute("namespace"));
      try {
        cacheRefResolver.resolveCacheRef();
      } catch (IncompleteElementException e) {
        configuration.addIncompleteCacheRef(cacheRefResolver);
      }
    }
  }

  /**
   * 解析 xxxMapper.xml 的 cache 节点
   * @param context
   * @throws Exception
   */
  private void cacheElement(XNode context) throws Exception {
    if (context != null) {
      // 获取 type 节点的属性， 默认是 PERPETUAL
      String type = context.getStringAttribute("type", "PERPETUAL");
      // 通过 type 值， 查找对应 Cache 接口的实现
      Class<? extends Cache> typeClass = typeAliasRegistry.resolveAlias(type);
      // eviction 属性， eviction 对应的是回收策略， 默认为 LRU。
      String eviction = context.getStringAttribute("eviction", "LRU");
      // 解析 eviction 属性指定的 Cache 装饰器类型
      Class<? extends Cache> evictionClass = typeAliasRegistry.resolveAlias(eviction);
      // flushInterval 对应刷新间隔， 单位毫秒， 默认值不设置， 即没有刷新间隔， 缓存仅仅在刷新语句时刷新。
      Long flushInterval = context.getLongAttribute("flushInterval");
      // size 对应为引用的数量，即最多的缓存对象数据。
      Integer size = context.getIntAttribute("size");
      // readOnly 为只读属性， 默认为 false, 即可读写
      boolean readWrite = !context.getBooleanAttribute("readOnly", false);
      // blocking 为阻塞， 默认值为 false。 当指定为 true 时将采用 BlockingCache 进行封装
      boolean blocking = context.getBooleanAttribute("blocking", false);
      // 获取 <cache> 属性节点下的子节点， 用于初始化二级缓存
      Properties props = context.getChildrenAsProperties();
      // 通过 MapperBuilderAssistant 创建 Cache 对象， 并将其添加到 COnfiguration 中
      builderAssistant.useNewCache(typeClass, evictionClass, flushInterval, size, readWrite, blocking, props);
    }
  }

  private void parameterMapElement(List<XNode> list) throws Exception {
    for (XNode parameterMapNode : list) {
      String id = parameterMapNode.getStringAttribute("id");
      String type = parameterMapNode.getStringAttribute("type");
      Class<?> parameterClass = resolveClass(type);
      List<XNode> parameterNodes = parameterMapNode.evalNodes("parameter");
      List<ParameterMapping> parameterMappings = new ArrayList<>();
      for (XNode parameterNode : parameterNodes) {
        String property = parameterNode.getStringAttribute("property");
        String javaType = parameterNode.getStringAttribute("javaType");
        String jdbcType = parameterNode.getStringAttribute("jdbcType");
        String resultMap = parameterNode.getStringAttribute("resultMap");
        String mode = parameterNode.getStringAttribute("mode");
        String typeHandler = parameterNode.getStringAttribute("typeHandler");
        Integer numericScale = parameterNode.getIntAttribute("numericScale");
        ParameterMode modeEnum = resolveParameterMode(mode);
        Class<?> javaTypeClass = resolveClass(javaType);
        JdbcType jdbcTypeEnum = resolveJdbcType(jdbcType);
        @SuppressWarnings("unchecked")
        Class<? extends TypeHandler<?>> typeHandlerClass = (Class<? extends TypeHandler<?>>) resolveClass(typeHandler);
        ParameterMapping parameterMapping = builderAssistant.buildParameterMapping(parameterClass, property, javaTypeClass, jdbcTypeEnum, resultMap, modeEnum, typeHandlerClass, numericScale);
        parameterMappings.add(parameterMapping);
      }
      builderAssistant.addParameterMap(id, parameterClass, parameterMappings);
    }
  }

  private void resultMapElements(List<XNode> list) throws Exception {
    for (XNode resultMapNode : list) {
      try {
        resultMapElement(resultMapNode);
      } catch (IncompleteElementException e) {
        // ignore, it will be retried
      }
    }
  }

  private ResultMap resultMapElement(XNode resultMapNode) throws Exception {
    return resultMapElement(resultMapNode, Collections.<ResultMapping> emptyList());
  }

  /**
   * 处理 <resultMap> 节点， 将节点解析成 ResultMap 对象， 下面包含有 ResultMapping 对象组成的列表
   * @param resultMapNode resultMap 节点
   * @param additionalResultMappings 另外的 ResultMapping 列
   * @return ResultMap 对象
   * @throws Exception
   */
  private ResultMap resultMapElement(XNode resultMapNode, List<ResultMapping> additionalResultMappings) throws Exception {
    ErrorContext.instance().activity("processing " + resultMapNode.getValueBasedIdentifier());
    // 获取 ID , 默认值会拼装所有父节点的 id 或 value 或 property
    String id = resultMapNode.getStringAttribute("id",
        resultMapNode.getValueBasedIdentifier());
    // 获取 type 属性， 表示结果集将被映射为 type 指定类型的对象
    String type = resultMapNode.getStringAttribute("type",
        resultMapNode.getStringAttribute("ofType",
            resultMapNode.getStringAttribute("resultType",
                resultMapNode.getStringAttribute("javaType"))));
    // 获取 extends 属性， 其表示结果集的继承
    String extend = resultMapNode.getStringAttribute("extends");
    // 自动映射属性。 将列名自动映射为属性
    Boolean autoMapping = resultMapNode.getBooleanAttribute("autoMapping");
    // 解析 type， 获取其类型
    Class<?> typeClass = resolveClass(type);
    Discriminator discriminator = null;
    // 记录解析的结果
    List<ResultMapping> resultMappings = new ArrayList<>();
    resultMappings.addAll(additionalResultMappings);
    // 处理子节点
    List<XNode> resultChildren = resultMapNode.getChildren();
    for (XNode resultChild : resultChildren) {
      // 处理 constructor 节点
      if ("constructor".equals(resultChild.getName())) {
        // 解析构造函数元素，其下的没每一个子节点都会生产一个 ResultMapping 对象
        processConstructorElement(resultChild, typeClass, resultMappings);
        // 处理 discriminator 节点
      } else if ("discriminator".equals(resultChild.getName())) {
        discriminator = processDiscriminatorElement(resultChild, typeClass, resultMappings);
        // 处理其余节点， 如 id， result, assosation d等
      } else {
        List<ResultFlag> flags = new ArrayList<>();
        if ("id".equals(resultChild.getName())) {
          flags.add(ResultFlag.ID);
        }
        // 创建 resultMapping 对象， 并添加到 resultMappings 中
        resultMappings.add(buildResultMappingFromContext(resultChild, typeClass, flags));
      }
    }
    // 创建 ResultMapResolver 对象， 该对象可以生成 ResultMap 对象
    ResultMapResolver resultMapResolver = new ResultMapResolver(builderAssistant, id, typeClass, extend, discriminator, resultMappings, autoMapping);
    try {
      return resultMapResolver.resolve();
    } catch (IncompleteElementException  e) {
      // 如果无法创建 ResultMap 对象， 则将该结果添加到 incompleteResultMaps 集合中
      configuration.addIncompleteResultMap(resultMapResolver);
      throw e;
    }
  }


  /**
   * 解析构造函数元素，其下的没每一个子节点都会生产一个 ResultMapping 对象
   * @param resultChild constructor 节点
   * @param resultType 结果的类型， 对应的是resultMap中的type
   * @param resultMappings resultMappings 结果集
   * @throws Exception  buildResultMappingFromContext 返回的异常不处理， 直接抛出去
   */
  private void processConstructorElement(XNode resultChild, Class<?> resultType, List<ResultMapping> resultMappings) throws Exception {
    List<XNode> argChildren = resultChild.getChildren();
    // 遍历子节点， 每个子节点都是一个 ResultMapping 对象
    for (XNode argChild : argChildren) {
      List<ResultFlag> flags = new ArrayList<>();
      flags.add(ResultFlag.CONSTRUCTOR);
      // idArg 相当于 id, 只不过为了区别开
      if ("idArg".equals(argChild.getName())) {
        flags.add(ResultFlag.ID);
      }
      resultMappings.add(buildResultMappingFromContext(argChild, resultType, flags));
    }
  }

  /**
   * 处理鉴别器
   * @param context 节点
   * @param resultType 结果类型
   * @param resultMappings 列结果集合
   * @return 鉴别器
   * @throws Exception
   */
  private Discriminator processDiscriminatorElement(XNode context, Class<?> resultType, List<ResultMapping> resultMappings) throws Exception {
    String column = context.getStringAttribute("column");
    String javaType = context.getStringAttribute("javaType");
    String jdbcType = context.getStringAttribute("jdbcType");
    String typeHandler = context.getStringAttribute("typeHandler");
    // 先获取各个属性
    // 取得 javaType 对应的类型
    Class<?> javaTypeClass = resolveClass(javaType);
    // 取得 typeHandler 对应的类型
    @SuppressWarnings("unchecked")
    Class<? extends TypeHandler<?>> typeHandlerClass = (Class<? extends TypeHandler<?>>) resolveClass(typeHandler);
    // 取得 jdbcType 对应的类型
    JdbcType jdbcTypeEnum = resolveJdbcType(jdbcType);
    // 创建 discriminatorMap， 并遍历子节点， 以 value->resultMap 的方式放入discriminatorMap中
    Map<String, String> discriminatorMap = new HashMap<>();
    for (XNode caseChild : context.getChildren()) {
      String value = caseChild.getStringAttribute("value");
      String resultMap = caseChild.getStringAttribute("resultMap", processNestedResultMappings(caseChild, resultMappings));
      discriminatorMap.put(value, resultMap);
    }
    // 创建鉴别器
    return builderAssistant.buildDiscriminator(resultType, column, javaTypeClass, jdbcTypeEnum, typeHandlerClass, discriminatorMap);
  }

    /**
     * 解析 <sql> 节点
     * @param list
     * @throws Exception
     */
  private void sqlElement(List<XNode> list) throws Exception {
    if (configuration.getDatabaseId() != null) {
      sqlElement(list, configuration.getDatabaseId());
    }
    sqlElement(list, null);
  }

    /**
     * 解析 <sql> 节点
     *
     * @param list
     * @param requiredDatabaseId
     * @throws Exception
     */
    private void sqlElement(List<XNode> list, String requiredDatabaseId) throws Exception {
        // 遍历 <sql> 节点
        for (XNode context : list) {
            // 获取 databaseId 属性
            String databaseId = context.getStringAttribute("databaseId");
            // 获取 id 属性
            String id = context.getStringAttribute("id");
            // 为 id 添加命名空间
            id = builderAssistant.applyCurrentNamespace(id, false);
            // 检查 sql 节点的 databaseId 与当前 Configuration 中的是否一致
            if (databaseIdMatchesCurrent(id, databaseId, requiredDatabaseId)) {
                // 记录到 XMLMapperBuider.sqlFragments(Map<String, XNode>)中保存
                // 其最终是指向了 Configuration.sqlFragments(configuration.getSqlFragments) 集合
                sqlFragments.put(id, context);
            }
        }
    }
  
  private boolean databaseIdMatchesCurrent(String id, String databaseId, String requiredDatabaseId) {
    if (requiredDatabaseId != null) {
      if (!requiredDatabaseId.equals(databaseId)) {
        return false;
      }
    } else {
      if (databaseId != null) {
        return false;
      }
      // skip this fragment if there is a previous one with a not null databaseId
      if (this.sqlFragments.containsKey(id)) {
        XNode context = this.sqlFragments.get(id);
        if (context.getStringAttribute("databaseId") != null) {
          return false;
        }
      }
    }
    return true;
  }

  /**
   * 获取一行， 如result等， 取得他们所有的属性， 通过这些属性建立 ResultMapping 对象
   * @param context 对于节点本身
   * @param resultType resultMap 的结果类型
   * @param flags flag 属性， 对应 ResultFlag 枚举中的属性。 一般情况下为空
   * @return 返回 ResultMapping
   * @throws Exception
   */
  private ResultMapping buildResultMappingFromContext(XNode context, Class<?> resultType, List<ResultFlag> flags) throws Exception {
    String property;
    // 获取节点的属性， 如果节点是构造函数（只有name属性， 没有property），
    // 则获取的是 name, 否则获取 property
    if (flags.contains(ResultFlag.CONSTRUCTOR)) {
      property = context.getStringAttribute("name");
    } else {
      property = context.getStringAttribute("property");
    }
    String column = context.getStringAttribute("column");
    String javaType = context.getStringAttribute("javaType");
    String jdbcType = context.getStringAttribute("jdbcType");
    String nestedSelect = context.getStringAttribute("select");
    String nestedResultMap = context.getStringAttribute("resultMap",
        //    处理嵌套结果集， 当节点为 association， collection， case ， 且含有 select 时会用到
        processNestedResultMappings(context, Collections.<ResultMapping> emptyList()));
    String notNullColumn = context.getStringAttribute("notNullColumn");
    String columnPrefix = context.getStringAttribute("columnPrefix");
    String typeHandler = context.getStringAttribute("typeHandler");
    String resultSet = context.getStringAttribute("resultSet");
    String foreignColumn = context.getStringAttribute("foreignColumn");
    boolean lazy = "lazy".equals(context.getStringAttribute("fetchType", configuration.isLazyLoadingEnabled() ? "lazy" : "eager"));

    // 以上获取各个属性节点
    // 解析 javaType， typeHandler， jdbcType
    Class<?> javaTypeClass = resolveClass(javaType);
    @SuppressWarnings("unchecked")
    Class<? extends TypeHandler<?>> typeHandlerClass = (Class<? extends TypeHandler<?>>) resolveClass(typeHandler);
    JdbcType jdbcTypeEnum = resolveJdbcType(jdbcType);
    // 创建resultMapping对象
    return builderAssistant.buildResultMapping(resultType, property, column, javaTypeClass, jdbcTypeEnum, nestedSelect, nestedResultMap, notNullColumn, columnPrefix, typeHandlerClass, flags, resultSet, foreignColumn, lazy);
  }

  /**
   * 处理嵌套结果集， 当节点为 association， collection， case ， 且含有 select 时会用到
   * @param context 节点本身
   * @param resultMappings resultMappings结果集
   * @return
   * @throws Exception
   */
  private String processNestedResultMappings(XNode context, List<ResultMapping> resultMappings) throws Exception {
    if ("association".equals(context.getName())
        || "collection".equals(context.getName())
        || "case".equals(context.getName())) {
      if (context.getStringAttribute("select") == null) {
        // 会先将嵌套的结果集进行处理， 添加到 Configuration 中
        ResultMap resultMap = resultMapElement(context, resultMappings);
        return resultMap.getId();
      }
    }
    return null;
  }

  /**
   * 映射文件与对应 Mapper 的绑定
   */
  private void bindMapperForNamespace() {
    // 获取映射文件的 namespace
    String namespace = builderAssistant.getCurrentNamespace();
    if (namespace != null) {
      Class<?> boundType = null;
      try {
        // 解析 namespace 对应的类型
        boundType = Resources.classForName(namespace);
      } catch (ClassNotFoundException e) {
        //不做处理， 对应的类型不存在也没关系
      }
      // 存在对应的类型， 才做处理
      if (boundType != null) {
        if (!configuration.hasMapper(boundType)) { // 是否已经加载了 boundType 接口
          // Spring may not know the real resource name so we set a flag
          // to prevent loading again this resource from the mapper interface
          // look at MapperAnnotationBuilder#loadXmlResource
          // 追加namespace 作为前缀， 并添加到 Configuration 对象的 loadedResources 中
          configuration.addLoadedResource("namespace:" + namespace);
          // 注册Mapper到mapperRegistry中
          configuration.addMapper(boundType);
        }
      }
    }
  }

}
