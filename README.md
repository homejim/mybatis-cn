MyBatis 是一款优秀的支持自定义 SQL 查询、存储过程和高级映射的持久层框架。

本文中的代码是从最新的 MyBatis-3 中拷贝过来的

## Mybatis使用

该系列文章的目的， 是从入门到精通

- [mybatis 初步使用](https://www.cnblogs.com/homejim/p/9613205.html)： 基于 Maven 的入门示例， 超级详细的教程
- [mybatis 代码生成器（IDEA, Maven)及配置详解](https://www.cnblogs.com/homejim/p/9782403.html)：还在手写 XML 和 JavaBean 吗？快来试一下代码生成器吧
- [mybatis 多个接口参数的注解使用方式（@Param)](https://www.cnblogs.com/homejim/p/9758930.html)： 接口中需要传入多个参数， 试一下注解模式吧
- [mybatis-高级结果映射之一对一](https://www.cnblogs.com/homejim/p/9785802.html)： 来试试高级映射功能
- [mybatis-高级结果映射之一对多](https://www.cnblogs.com/homejim/p/9808847.html)： 一对多的高级映射
- [mybatis 缓存的使用， 看这篇就够了](https://www.cnblogs.com/homejim/p/9729191.html)： 一级缓存， 二级缓存， 提升性能的同时， 也注意它的限制
- [MyBatis动态SQL](https://www.cnblogs.com/homejim/p/9909657.html)：我再也不要在代码中拼接语句了！！ 来用 mybatis 动态 SQL 爽一爽吧
- [MyBatis-你所不了解的sql和include](https://www.cnblogs.com/homejim/p/9961102.html)：sql语句重用
- 未完待续...
## 相关设计模式
- [Java设计模式-建造者(Builder)模式](https://www.cnblogs.com/homejim/p/9644182.html)： mybatis 使用频率很高的设计模式。 尤其是解析相关
- [Java设计模式-静态代理和动态代理](https://www.cnblogs.com/homejim/p/9581294.html)： 日志模块等会用到
- 未完待续...
## MyBatis 源码解析

你还不知道 MyBatis 的源码怎么阅读， 跟我的文章走就对了！
- [mybatis源码-解析配置文件（一）之XML的DOM解析方式](https://www.cnblogs.com/homejim/p/9652273.html)：MyBatis使用的时候 XML 配置， 源码阅读前， 务必看看这个。
- [mybatis源码-解析配置文件（二）之解析的流程](https://www.cnblogs.com/homejim/p/9654992.html)： 配置文件的解析流程， 先知道大的流程， 再去研究细节
- [mybatis源码-解析配置文件（三）之配置文件Configuration解析](https://www.cnblogs.com/homejim/p/9672224.html)： 这是 mybatis 解析配置文件的核心
- [mybatis源码-解析配置文件（四）之配置文件Mapper解析](https://www.cnblogs.com/homejim/p/9741404.html)： mapper.xml中的文件最后都解析成什么了？
- [mybatis源码-解析配置文件（四-1）之配置文件Mapper解析(cache)](https://www.cnblogs.com/homejim/p/9743921.html)： 缓存中的配置都有什么用
- [mybatis百科-列映射类ResultMapping](https://www.cnblogs.com/homejim/p/9833863.html)： resultMap 节点解析相关的类
- [mybatis百科-结果集映射类ResultMap](https://www.cnblogs.com/homejim/p/9840373.html)： resultMap 节点解析相关的类
- [ mybatis源码-解析配置文件（四-二）之配置文件Mapper解析(resultMap)](https://www.cnblogs.com/homejim/p/9853703.html)： resultMap 完整解析流程。 看完知道原来 resultMap 在内存中长这样
- 未完待续
## MyBatis 提取出的工具
- [mybatis抽取出的工具-（一）通用标记解析器（即拿即用）](https://www.cnblogs.com/homejim/p/9739632.html)： 通用的标记解析器， 用于处理字符串中的占位符


## 找不到中文注释？
好吧， 因为源码的阅读和注释是一个长期的过程， 因此， 如果在我这里找不到中文注释， 那就是我还没有阅读到那个地方。

对此， 你可以参考以前[别人的注释](https://github.com/homejim/mybatis-cn-2014)。

或者， 在我的博客中给我留言
