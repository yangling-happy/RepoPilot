//package-info文件：给某个 Java package 写说明文档，或者给整个 package 添加注解(这样package下的所有东西默认都带上这个注解)
//下面这段注释会被 Javadoc 识别，用来生成包级文档
/**
 * business 模块 API 使用的请求和响应 DTO。
 *
 * DTO 只负责承载接口入参/出参，不直接操作数据库，也不放复杂业务逻辑。
 * Controller 接收到 DTO 后，会把它校验并转换为 Entity 或 Service 方法参数。
 */
package com.repopilot.business.dto;
