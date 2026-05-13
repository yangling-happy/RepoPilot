package com.repopilot.business.service.docgen;

import lombok.Builder;
import lombok.Value;

//一次文档生成的返回结果
//目前只返回结构化文档路径，后续如果要扩展生成器耗时、告警信息，也可以从这里加字段
//
//@Value 是 Lombok 注解，生成不可变对象（所有字段 private final，只有 getter 没有 setter）
//@Builder 让你可以用链式调用的方式创建对象：DocGenerationResult.builder().docFilePath("...").build()
@Value
@Builder
public class DocGenerationResult {

    //生成的文档文件路径（通常是 JSON 格式的结构化文档）
    String docFilePath;
}
