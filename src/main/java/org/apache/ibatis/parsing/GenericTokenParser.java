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
package org.apache.ibatis.parsing;

/**
 * 通用标记解析器
 */
public class GenericTokenParser {

    // 开始标记
    private final String openToken;
    // 结束标记
    private final String closeToken;
    // 标记处理器
    private final TokenHandler handler;

  public GenericTokenParser(String openToken, String closeToken, TokenHandler handler) {
    this.openToken = openToken;
    this.closeToken = closeToken;
    this.handler = handler;
  }

  public String parse(String text) {
    if (text == null || text.isEmpty()) {
      return "";
    }
    // 从第0位开始， 查找开始标记的下标
    int start = text.indexOf(openToken, 0);
    if (start == -1) { // 找不到则返回原参数
      return text;
    }
    char[] src = text.toCharArray();
    // offset用来记录builder变量读取到了哪
    int offset = 0;
    // builder 是最终返回的字符串
    final StringBuilder builder = new StringBuilder();
    // expression 是每一次找到的表达式， 要传入处理器中进行处理
    StringBuilder expression = null;
    while (start > -1) {
      if (start > 0 && src[start - 1] == '\\') {
        // 开始标记是转义的， 则去除转义字符'\'
        builder.append(src, offset, start - offset - 1).append(openToken);
        offset = start + openToken.length();
      } else {
        // 此分支是找到了结束标记， 要找到结束标记
        if (expression == null) {
          expression = new StringBuilder();
        } else {
          expression.setLength(0);
        }
        // 将开始标记前的字符串都添加到 builder 中
        builder.append(src, offset, start - offset);
        // 计算新的 offset
        offset = start + openToken.length();

        // 从此处开始查找结束的标记
        int end = text.indexOf(closeToken, offset);
        while (end > -1) {
          if (end > offset && src[end - 1] == '\\') {
            // 此结束标记是转义的
            expression.append(src, offset, end - offset - 1).append(closeToken);
            offset = end + closeToken.length();
            end = text.indexOf(closeToken, offset);
          } else {
            expression.append(src, offset, end - offset);
            offset = end + closeToken.length();
            break;
          }
        }
        if (end == -1) {
          // 找不到结束标记了
          builder.append(src, start, src.length - start);
          offset = src.length;
        } else {
          // 找到了结束的标记， 则放入处理器进行处理
          builder.append(handler.handleToken(expression.toString()));
          offset = end + closeToken.length();
        }
      }
      // 因为字符串中可能有很多表达式需要解析， 因此开始下一个表达式的查找
      start = text.indexOf(openToken, offset);
    }
    // 最后一次未找到开始标记， 则将 offset 后的字符串添加到 builder 中
    if (offset < src.length) {
      builder.append(src, offset, src.length - offset);
    }
    return builder.toString();
  }
}
