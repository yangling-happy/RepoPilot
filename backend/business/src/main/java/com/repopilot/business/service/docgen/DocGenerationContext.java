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

    //项目标识，用于组织输出目录，也用于写入结构化文档元信息
    String project;
    //分支名，用于区分同一项目不同分支生成的文档
    String branch;
    //commit hash，表示这次生成文档所基于的源码版本
    String commitId;
    //源文件在仓库中的相对路径，如 src/main/java/com/example/Demo.java
    //生成器会用这个路径恢复临时源码目录结构，javadoc 也依赖这个结构定位源码
    String filePath;
    //源文件内容；远程增量场景来自 GitLab API，本地扫描场景来自本地文件读取
    String sourceContent;
    //本地仓库根目录；传给 javadoc 的 sourcepath，帮助它解析 import 的其他源码
    Path sourceRoot;
    //文档输出根目录；最终结构化 JSON 会写入这个目录下面
    Path outputRoot;
}
