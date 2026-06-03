package com.repopilot.business.service.gitlab;

//Java record 语法糖，自动生成：
//  - 两个 private final 字段（token、username）
//  - 全参构造函数
//  - getter 方法（token()、username()，注意不是 getToken()）
//  - equals()、hashCode()、toString()
//record 适合做不可变的数据载体，比写一个完整的 POJO 简洁得多
//这里用来封装当前请求的 GitLab 认证信息
public record GitLabUserContext(String token, String username) {
}
