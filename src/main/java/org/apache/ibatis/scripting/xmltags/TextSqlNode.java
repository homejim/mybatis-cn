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

import java.util.regex.Pattern;

import org.apache.ibatis.parsing.GenericTokenParser;
import org.apache.ibatis.parsing.TokenHandler;
import org.apache.ibatis.scripting.ScriptingException;
import org.apache.ibatis.type.SimpleTypeRegistry;

/**
 * 文本节点
 */
public class TextSqlNode implements SqlNode {
  private final String text;
  private final Pattern injectionFilter;

  public TextSqlNode(String text) {
    this(text, null);
  }
  
  public TextSqlNode(String text, Pattern injectionFilter) {
    this.text = text;
    this.injectionFilter = injectionFilter;
  }

  /**
   * 判断是否为动态节点
   * @return
   */
  public boolean isDynamic() {

    // 该 DynamicCheckerTokenParser 的 handleToken 方法不做任何的操作， 只是节点标记为动态 SQL 节点
    DynamicCheckerTokenParser checker = new DynamicCheckerTokenParser();
    // 使用通过解析器进行解析
    GenericTokenParser parser = createParser(checker);
    parser.parse(text);
    // 返回， 有$ 就是动态节点
    return checker.isDynamic();
  }

  /**
   * 实际参数替换
   *
   * @param context
   * @return
   */
  @Override
  public boolean apply(DynamicContext context) {
    // GenericTokenParser 解析器是通用标记解析器， 具体可以参考我的博客
    GenericTokenParser parser = createParser(new BindingTokenParser(context, injectionFilter));
    // 解析 text， 并添加到 DynamicContext 对象中
    context.appendSql(parser.parse(text));
    return true;
  }

  /**
   * 创建通用标记解析器
   * @param handler
   * @return
   */
  private GenericTokenParser createParser(TokenHandler handler) {
  // 解析的是 ${} 的内容
    return new GenericTokenParser("${", "}", handler);
  }

  /**
   *  绑定记号解析器
   */
  private static class BindingTokenParser implements TokenHandler {

    private DynamicContext context;
    private Pattern injectionFilter;

    public BindingTokenParser(DynamicContext context, Pattern injectionFilter) {
      this.context = context;
      this.injectionFilter = injectionFilter;
    }

    /**
     * 符号处理器
     *
     * @param content
     * @return
     */
    @Override
    public String handleToken(String content) {
      // 获取用户传入的实际参数， 不过用的竟然不是 DynamicContext.PARAMETER_OBJECT_KEY 变量， 有点奇怪
      Object parameter = context.getBindings().get("_parameter");
      if (parameter == null) {
        context.getBindings().put("value", null);
      } else if (SimpleTypeRegistry.isSimpleType(parameter.getClass())) {
        context.getBindings().put("value", parameter);
      }
      // 通过 ognl 解析 content 的值
      Object value = OgnlCache.getValue(content, context.getBindings());
      String srtValue = (value == null ? "" : String.valueOf(value)); // issue #274 return "" instead of "null"
      // 检查合法性
      checkInjection(srtValue);
      return srtValue;
    }

    private void checkInjection(String value) {
      if (injectionFilter != null && !injectionFilter.matcher(value).matches()) {
        throw new ScriptingException("Invalid input. Please conform to regex" + injectionFilter.pattern());
      }
    }
  }

  /**
   * 内部类， 实现了 TokenHandler
   */
  private static class DynamicCheckerTokenParser implements TokenHandler {

    private boolean isDynamic;

    public DynamicCheckerTokenParser() {
      // Prevent Synthetic Access
    }

    public boolean isDynamic() {
      return isDynamic;
    }

    /**
     * 参数都没用到， 就是设定为是动态SQL
     * @return
     */
    @Override
    public String handleToken(String content) {
      this.isDynamic = true;
      return null;
    }
  }
  
}