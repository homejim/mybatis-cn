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
package org.apache.ibatis.mapping;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.builder.BuilderException;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;
import org.apache.ibatis.reflection.ParamNameUtil;
import org.apache.ibatis.session.Configuration;

/**
 * @author Clinton Begin
 */
public class ResultMap {
  // 这个是mybatis存储所有信息的对象
  private Configuration configuration;

  // resultMap 节点的 id
  private String id;
  // resultMap 节点的 type
  private Class<?> type;
  // 用于记录 <discriminayor> 节点之外的其他映射关系
  private List<ResultMapping> resultMappings;
  // 记录映射关系中带有 ID 标记的映射关系。 如 id， constructor 等节点
  private List<ResultMapping> idResultMappings;
  // 记录映射关系中有 Constructor 标记的映射关系
  private List<ResultMapping> constructorResultMappings;
  // 记录映射关系中没有 Constructor 标记的映射关系
  private List<ResultMapping> propertyResultMappings;
  // 记录所有映射关系中涉及 column 属性的集合
  private Set<String> mappedColumns;
  private Set<String> mappedProperties;
  // 鉴别器， 对应 <discriminayor> 节点
  private Discriminator discriminator;
  // 是否含有嵌套的结果映射， 如果某个映射关系中有 resultMap， 没有 resultSet ， 则为true
  private boolean hasNestedResultMaps;
  // 是否存在嵌套查询
  private boolean hasNestedQueries;
  // 是否开启自动映射
  private Boolean autoMapping;

  private ResultMap() {
  }

  // 建造者模式
  public static class Builder {
    private static final Log log = LogFactory.getLog(Builder.class);

    private ResultMap resultMap = new ResultMap();

    public Builder(Configuration configuration, String id, Class<?> type, List<ResultMapping> resultMappings) {
      this(configuration, id, type, resultMappings, null);
    }

    public Builder(Configuration configuration, String id, Class<?> type, List<ResultMapping> resultMappings, Boolean autoMapping) {
      resultMap.configuration = configuration;
      resultMap.id = id;
      resultMap.type = type;
      resultMap.resultMappings = resultMappings;
      resultMap.autoMapping = autoMapping;
    }

    public Builder discriminator(Discriminator discriminator) {
      resultMap.discriminator = discriminator;
      return this;
    }

    public Class<?> type() {
      return resultMap.type;
    }

    public ResultMap build() {
      if (resultMap.id == null) {
        throw new IllegalArgumentException("ResultMaps must have an id");
      }
      resultMap.mappedColumns = new HashSet<>();
      resultMap.mappedProperties = new HashSet<>();
      resultMap.idResultMappings = new ArrayList<>();
      resultMap.constructorResultMappings = new ArrayList<>();
      resultMap.propertyResultMappings = new ArrayList<>();
      final List<String> constructorArgNames = new ArrayList<>();
      // 遍历 resultMappings
      for (ResultMapping resultMapping : resultMap.resultMappings) {
        // 是否存在嵌套查询
        resultMap.hasNestedQueries = resultMap.hasNestedQueries || resultMapping.getNestedQueryId() != null;
        // 是否存在嵌套的结果
        resultMap.hasNestedResultMaps = resultMap.hasNestedResultMaps || (resultMapping.getNestedResultMapId() != null && resultMapping.getResultSet() == null);
        // 获取列名
        final String column = resultMapping.getColumn();
        if (column != null) {
          // 列名转大写添加到 mappedColumns 结果集中
          resultMap.mappedColumns.add(column.toUpperCase(Locale.ENGLISH));
        } else if (resultMapping.isCompositeResult()) {
          for (ResultMapping compositeResultMapping : resultMapping.getComposites()) {
            final String compositeColumn = compositeResultMapping.getColumn();
            if (compositeColumn != null) {
              resultMap.mappedColumns.add(compositeColumn.toUpperCase(Locale.ENGLISH));
            }
          }
        }
        // 获取列映射对应的属性
        final String property = resultMapping.getProperty();
        if(property != null) {
          resultMap.mappedProperties.add(property);
        }
        if (resultMapping.getFlags().contains(ResultFlag.CONSTRUCTOR)) {
          resultMap.constructorResultMappings.add(resultMapping);
          if (resultMapping.getProperty() != null) {
            constructorArgNames.add(resultMapping.getProperty());
          }
        } else {
          resultMap.propertyResultMappings.add(resultMapping);
        }
        if (resultMapping.getFlags().contains(ResultFlag.ID)) {
          resultMap.idResultMappings.add(resultMapping);
        }
      }
      if (resultMap.idResultMappings.isEmpty()) {
        resultMap.idResultMappings.addAll(resultMap.resultMappings);
      }
      if (!constructorArgNames.isEmpty()) {
        final List<String> actualArgNames = argNamesOfMatchingConstructor(constructorArgNames);
        if (actualArgNames == null) {
          throw new BuilderException("Error in result map '" + resultMap.id
              + "'. Failed to find a constructor in '"
              + resultMap.getType().getName() + "' by arg names " + constructorArgNames
              + ". There might be more info in debug log.");
        }
        Collections.sort(resultMap.constructorResultMappings, (o1, o2) -> {
          int paramIdx1 = actualArgNames.indexOf(o1.getProperty());
          int paramIdx2 = actualArgNames.indexOf(o2.getProperty());
          return paramIdx1 - paramIdx2;
        });
      }
      // lock down collections
      resultMap.resultMappings = Collections.unmodifiableList(resultMap.resultMappings);
      resultMap.idResultMappings = Collections.unmodifiableList(resultMap.idResultMappings);
      resultMap.constructorResultMappings = Collections.unmodifiableList(resultMap.constructorResultMappings);
      resultMap.propertyResultMappings = Collections.unmodifiableList(resultMap.propertyResultMappings);
      resultMap.mappedColumns = Collections.unmodifiableSet(resultMap.mappedColumns);
      return resultMap;
    }

    /**
     * 校验
     * @param constructorArgNames
     * @return
     */
    private List<String> argNamesOfMatchingConstructor(List<String> constructorArgNames) {
      // 获取声明的构造方法
      Constructor<?>[] constructors = resultMap.type.getDeclaredConstructors();
      // 遍历每个构造方法
      for (Constructor<?> constructor : constructors) {
        // 获取构造方法的参数类型
        Class<?>[] paramTypes = constructor.getParameterTypes();
        // 参数长度和获取到参数类型数量一致
        if (constructorArgNames.size() == paramTypes.length) {
          // 获取构造函数的参数名称
          List<String> paramNames = getArgNames(constructor);
          if (constructorArgNames.containsAll(paramNames)
                  && argTypesMatch(constructorArgNames, paramTypes, paramNames)) {
            return paramNames;
          }
        }
      }
      return null;
    }

    /**
     * 用来检查构造方法参数类型是否匹配
     * @param constructorArgNames 参数名称列表
     * @param paramTypes  参数类型
     * @param paramNames 参数名
     * @return 
     */
    private boolean argTypesMatch(final List<String> constructorArgNames,
        Class<?>[] paramTypes, List<String> paramNames) {
      for (int i = 0; i < constructorArgNames.size(); i++) {
        Class<?> actualType = paramTypes[paramNames.indexOf(constructorArgNames.get(i))];
        Class<?> specifiedType = resultMap.constructorResultMappings.get(i).getJavaType();
        if (!actualType.equals(specifiedType)) {
          if (log.isDebugEnabled()) {
            log.debug("While building result map '" + resultMap.id
                + "', found a constructor with arg names " + constructorArgNames
                + ", but the type of '" + constructorArgNames.get(i)
                + "' did not match. Specified: [" + specifiedType.getName() + "] Declared: ["
                + actualType.getName() + "]");
          }
          return false;
        }
      }
      return true;
    }

    // 获取构造函数的参数名
    private List<String> getArgNames(Constructor<?> constructor) {
      List<String> paramNames = new ArrayList<>();
      List<String> actualParamNames = null;
      // 获取参数的注解
      final Annotation[][] paramAnnotations = constructor.getParameterAnnotations();
      int paramCount = paramAnnotations.length;
      for (int paramIndex = 0; paramIndex < paramCount; paramIndex++) {
        String name = null;
        for (Annotation annotation : paramAnnotations[paramIndex]) {
          if (annotation instanceof Param) {
            name = ((Param) annotation).value();
            break;
          }
        }
        if (name == null && resultMap.configuration.isUseActualParamName()) {
          if (actualParamNames == null) {
            actualParamNames = ParamNameUtil.getParamNames(constructor);
          }
          if (actualParamNames.size() > paramIndex) {
            name = actualParamNames.get(paramIndex);
          }
        }
        paramNames.add(name != null ? name : "arg" + paramIndex);
      }
      return paramNames;
    }
  }

  public String getId() {
    return id;
  }

  public boolean hasNestedResultMaps() {
    return hasNestedResultMaps;
  }

  public boolean hasNestedQueries() {
    return hasNestedQueries;
  }

  public Class<?> getType() {
    return type;
  }

  public List<ResultMapping> getResultMappings() {
    return resultMappings;
  }

  public List<ResultMapping> getConstructorResultMappings() {
    return constructorResultMappings;
  }

  public List<ResultMapping> getPropertyResultMappings() {
    return propertyResultMappings;
  }

  public List<ResultMapping> getIdResultMappings() {
    return idResultMappings;
  }

  public Set<String> getMappedColumns() {
    return mappedColumns;
  }

  public Set<String> getMappedProperties() {
    return mappedProperties;
  }

  public Discriminator getDiscriminator() {
    return discriminator;
  }

  public void forceNestedResultMaps() {
    hasNestedResultMaps = true;
  }
  
  public Boolean getAutoMapping() {
    return autoMapping;
  }

}
