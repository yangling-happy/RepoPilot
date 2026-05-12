package com.repopilot.business.service.docgen;

import java.util.Set;

//文档生成器接口
//不同的文件类型（.java、.py 等）需要不同的文档生成器
//通过这个接口抽象，新增文件类型支持时只需实现这个接口，无需修改已有代码
public interface DocGenerator {

    //返回生成器的名称（如 "javadoc"），用于日志和识别
    String toolName();

    //返回支持的文件扩展名集合（如 {".java"}）
    //DocGeneratorRegistry 根据这个信息来路由文件到正确的生成器
    Set<String> supportedExtensions();

    //执行文档生成，传入上下文信息，返回生成结果
    DocGenerationResult generate(DocGenerationContext context);
}
