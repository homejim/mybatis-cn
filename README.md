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
- 未完待续...

## MyBatis 源码解析

你还不知道 MyBatis 的源码怎么阅读， 跟我的文章走就对了！
- [mybatis源码-解析配置文件（一）之XML的DOM解析方式](https://www.cnblogs.com/homejim/p/9652273.html)：MyBatis的使用 XML 配置， 源码阅读前， 务必看看这个。
- [mybatis源码-解析配置文件（二）之解析的流程](https://www.cnblogs.com/homejim/p/9654992.html)： 配置文件的解析流程， 先知道大的流程， 再去研究细节
- [mybatis源码-解析配置文件（三）之配置文件Configuration解析](https://www.cnblogs.com/homejim/p/9672224.html)： 这是 mybatis 解析配置文件的核心
- [mybatis源码-解析配置文件（四）之配置文件Mapper解析](https://www.cnblogs.com/homejim/p/9741404.html)： mapper.xml中的文件最后都解析成什么了？
- [mybatis源码-解析配置文件（四-1）之配置文件Mapper解析(cache)](https://www.cnblogs.com/homejim/p/9743921.html)： 缓存中的配置都有什么用
- [mybatis百科-列映射类ResultMapping](https://www.cnblogs.com/homejim/p/9833863.html)： resultMap 节点解析相关的类
- [mybatis百科-结果集映射类ResultMap](https://www.cnblogs.com/homejim/p/9840373.html)
- 未完待续
