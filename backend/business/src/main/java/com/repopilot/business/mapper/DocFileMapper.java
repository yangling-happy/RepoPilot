package com.repopilot.business.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.repopilot.business.entity.DocFile;
import org.apache.ibatis.annotations.Mapper;

//Mapper层:把 Java 实体类和数据库表连接起来，让后端可以通过 Java 方法操作数据库

//Mapper注解:这是一个 MyBatis 的 Mapper 接口，需要被 Spring 扫描并注册成 Bean
//BaseMapper 是 MyBatis-Plus 提供的通用 Mapper 接口,它已经内置了很多常用数据库CRUD操作
//所以即使这个 Mapper 里面没有写任何方法，也已经可以做基础 CRUD
@Mapper
public interface DocFileMapper extends BaseMapper<DocFile> {
}
