// 文档生成服务内部使用的数据对象，把一次文档生成需要的信息封装起来，然后其他文档生成器就可以拿这个对象来生成文档
package com.repopilot.business.service.docgen;

import lombok.Builder;
import lombok.Value;

import java.nio.file.Path;

//@Value是一个Lombok 注解，它表示这个类是一个不可变对象，
//所有字段默认变成 private final，自动生成getter，构造方法toString()，equals()，hashCode()
//与@Data不同的是，@Data是普通可变对象，可以 set
@Value
/* 
@Builder也是 Lombok 注解，可以让你像下面这样创建对象
DocGenerationContext context = DocGenerationContext.builder()
        .project("RepoPilot")
        .branch("main")
        .commitId("abc123")
        .filePath("src/main/java/com/repopilot/Demo.java")
        .sourceContent(sourceContent)
        .sourceRoot(sourceRoot)
        .outputRoot(outputRoot)
        .build(); 
如果没有 @Builder，可能要这样创建：
        new DocGenerationContext(
            project,
            branch,
            commitId,
            filePath,
            sourceContent,
            sourceRoot,
            outputRoot
        );
*/
@Builder
public class DocGenerationContext {

    String project;
    String branch;
    String commitId;
    String filePath;
    String sourceContent;
    Path sourceRoot;
    Path outputRoot;
}
