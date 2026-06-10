package com.repopilot.business.service.docgen;

import com.repopilot.business.dto.DocStructuredContent;
import com.repopilot.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * JavaDocHtmlParser 单元测试
 *
 * 测试覆盖范围：
 * - parse() 入口方法
 * - parseTypeDoc() 类型文档解析
 * - parseMembers() 成员列表解析
 * - kindFromSignature() 类型种类识别
 * - parseTypeHeader() 类型头信息提取
 */
class JavaDocHtmlParserTest {

    private JavaDocHtmlParser parser;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        parser = new JavaDocHtmlParser();
    }

    @Nested
    @DisplayName("parse() 入口方法测试")
    class ParseTests {

        @Test
        @DisplayName("正常解析包含类的HTML文件")
        void shouldParseValidHtmlFiles() throws IOException {
            // Given
            String html = createFullClassHtml("UserService", "用户服务类",
                    createMethodSection("getUserName", "public String getUserName(Long id)",
                            "根据ID查询用户", "Long id", "用户ID", "String", "用户名称", "IllegalArgumentException", "ID为空时抛出"));
            Path htmlFile = writeHtmlFile("UserService.html", html);
            DocGenerationContext context = createContext("test-project", "main", "abc123", "src/UserService.java");

            // When
            DocStructuredContent result = parser.parse(context, tempDir, List.of(htmlFile));

            // Then
            assertThat(result.getProject()).isEqualTo("test-project");
            assertThat(result.getBranch()).isEqualTo("main");
            assertThat(result.getCommitId()).isEqualTo("abc123");
            assertThat(result.getSourceFilePath()).isEqualTo("src/UserService.java");
            assertThat(result.getTypes()).hasSize(1);

            DocStructuredContent.TypeDoc typeDoc = result.getTypes().get(0);
            assertThat(typeDoc.getName()).isEqualTo("UserService");
            assertThat(typeDoc.getKind()).isEqualTo("CLASS");
            assertThat(typeDoc.getDescription()).isEqualTo("用户服务类");
            assertThat(typeDoc.getMethods()).hasSize(1);
        }

        @Test
        @DisplayName("解析多个HTML文件")
        void shouldParseMultipleHtmlFiles() throws IOException {
            // Given
            String html1 = createFullClassHtml("UserService", "用户服务", createEmptyMethodSection());
            String html2 = createFullClassHtml("OrderService", "订单服务", createEmptyMethodSection());
            Path file1 = writeHtmlFile("UserService.html", html1);
            Path file2 = writeHtmlFile("OrderService.html", html2);
            DocGenerationContext context = createContext("project", "main", "commit", "src/Services.java");

            // When
            DocStructuredContent result = parser.parse(context, tempDir, List.of(file1, file2));

            // Then
            assertThat(result.getTypes()).hasSize(2);
            assertThat(result.getTypes()).extracting(DocStructuredContent.TypeDoc::getName)
                    .containsExactly("UserService", "OrderService");
        }

        @Test
        @DisplayName("无有效类型时抛出异常")
        void shouldThrowWhenNoTypesFound() throws IOException {
            // Given - 创建一个没有类型信息的HTML
            String html = "<html><body><div>No class here</div></body></html>";
            Path htmlFile = writeHtmlFile("Empty.html", html);
            DocGenerationContext context = createContext("project", "main", "commit", "src/Empty.java");

            // When & Then
            assertThatThrownBy(() -> parser.parse(context, tempDir, List.of(htmlFile)))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("No class HTML pages found");
        }
    }

    @Nested
    @DisplayName("类型种类识别测试")
    class KindFromSignatureTests {

        @Test
        @DisplayName("识别CLASS类型")
        void shouldIdentifyClass() throws IOException {
            assertKind("public class UserService", "CLASS");
        }

        @Test
        @DisplayName("识别INTERFACE类型")
        void shouldIdentifyInterface() throws IOException {
            assertKind("public interface UserRepository", "INTERFACE");
        }

        @Test
        @DisplayName("识别ENUM类型")
        void shouldIdentifyEnum() throws IOException {
            assertKind("public enum OrderStatus", "ENUM");
        }

        @Test
        @DisplayName("识别ANNOTATION类型")
        void shouldIdentifyAnnotation() throws IOException {
            assertKind("public @interface CustomAnnotation", "ANNOTATION");
        }

        @Test
        @DisplayName("识别RECORD类型")
        void shouldIdentifyRecord() throws IOException {
            assertKind("public record Point(int x, int y)", "RECORD");
        }

        private void assertKind(String signature, String expectedKind) throws IOException {
            String typeName = signature.substring(signature.lastIndexOf(' ') + 1);
            // 处理 record 的情况，提取括号前的名字
            if (typeName.contains("(")) {
                typeName = typeName.substring(0, typeName.indexOf('('));
            }
            String html = createTypeSignatureHtml(typeName, signature);
            Path htmlFile = writeHtmlFile("Test.html", html);
            DocGenerationContext context = createContext("project", "main", "commit", "src/Test.java");
            DocStructuredContent result = parser.parse(context, tempDir, List.of(htmlFile));
            assertThat(result.getTypes().get(0).getKind()).isEqualTo(expectedKind);
        }
    }

    @Nested
    @DisplayName("成员解析测试")
    class MemberParsingTests {

        @Test
        @DisplayName("解析方法基本签名")
        void shouldParseMethodSignature() throws IOException {
            // Given
            String html = createFullClassHtml("UserService", "用户服务",
                    createMethodSection("save", "public void save()", "保存用户", null, null, null, null, null, null));
            Path htmlFile = writeHtmlFile("UserService.html", html);
            DocGenerationContext context = createContext("project", "main", "commit", "src/UserService.java");

            // When
            DocStructuredContent result = parser.parse(context, tempDir, List.of(htmlFile));

            // Then
            DocStructuredContent.MemberDoc method = result.getTypes().get(0).getMethods().get(0);
            assertThat(method.getName()).isEqualTo("save");
            assertThat(method.getSignature()).contains("public void save()");
            assertThat(method.getDescription()).isEqualTo("保存用户");
        }

        @Test
        @DisplayName("解析带参数、返回值和异常的方法")
        void shouldParseMethodWithParamsAndReturn() throws IOException {
            // Given
            String html = createFullClassHtml("UserService", "用户服务",
                    createMethodSection("getUserName", "public String getUserName(Long id)",
                            "根据ID查询用户", "Long id", "用户ID", "String", "用户名称", "IllegalArgumentException", "ID为空时抛出"));
            Path htmlFile = writeHtmlFile("UserService.html", html);
            DocGenerationContext context = createContext("project", "main", "commit", "src/UserService.java");

            // When
            DocStructuredContent result = parser.parse(context, tempDir, List.of(htmlFile));

            // Then
            DocStructuredContent.MemberDoc method = result.getTypes().get(0).getMethods().get(0);
            assertThat(method.getName()).isEqualTo("getUserName");

            // 验证参数
            assertThat(method.getParameters()).hasSize(1);
            assertThat(method.getParameters().get(0).getName()).isEqualTo("id");
            assertThat(method.getParameters().get(0).getDescription()).isEqualTo("用户ID");

            // 验证返回值
            assertThat(method.getReturns()).isNotNull();
            assertThat(method.getReturns().getDescription()).isEqualTo("用户名称");

            // 验证异常
            assertThat(method.getThrowsItems()).hasSize(1);
            assertThat(method.getThrowsItems().get(0).getType()).isEqualTo("IllegalArgumentException");
            assertThat(method.getThrowsItems().get(0).getDescription()).isEqualTo("ID为空时抛出");
        }

        @Test
        @DisplayName("解析void返回类型方法")
        void shouldHandleVoidReturnType() throws IOException {
            // Given
            String html = createFullClassHtml("VoidClass", "Void类",
                    createMethodSection("doNothing", "public void doNothing()", "无操作", null, null, null, null, null, null));
            Path htmlFile = writeHtmlFile("VoidClass.html", html);
            DocGenerationContext context = createContext("project", "main", "commit", "src/VoidClass.java");

            // When
            DocStructuredContent result = parser.parse(context, tempDir, List.of(htmlFile));

            // Then
            DocStructuredContent.MemberDoc method = result.getTypes().get(0).getMethods().get(0);
            assertThat(method.getReturns()).isNull();
        }
    }

    @Nested
    @DisplayName("构造函数和字段测试")
    class ConstructorAndFieldTests {

        @Test
        @DisplayName("解析构造函数")
        void shouldParseConstructor() throws IOException {
            // Given
            String html = createFullClassHtml("MyClass", "测试类",
                    createConstructorSection("MyClass", "public MyClass(String name)", "构造函数", "String name", "名称"));
            Path htmlFile = writeHtmlFile("MyClass.html", html);
            DocGenerationContext context = createContext("project", "main", "commit", "src/MyClass.java");

            // When
            DocStructuredContent result = parser.parse(context, tempDir, List.of(htmlFile));

            // Then
            assertThat(result.getTypes().get(0).getConstructors()).hasSize(1);
            assertThat(result.getTypes().get(0).getConstructors().get(0).getName()).isEqualTo("MyClass");
        }

        @Test
        @DisplayName("解析字段")
        void shouldParseFields() throws IOException {
            // Given
            String html = createFullClassHtml("MyClass", "测试类",
                    createFieldSection("name", "private String name", "用户名"));
            Path htmlFile = writeHtmlFile("MyClass.html", html);
            DocGenerationContext context = createContext("project", "main", "commit", "src/MyClass.java");

            // When
            DocStructuredContent result = parser.parse(context, tempDir, List.of(htmlFile));

            // Then
            assertThat(result.getTypes().get(0).getFields()).hasSize(1);
            assertThat(result.getTypes().get(0).getFields().get(0).getName()).isEqualTo("name");
            assertThat(result.getTypes().get(0).getFields().get(0).getDescription()).isEqualTo("用户名");
        }
    }

    @Nested
    @DisplayName("边界条件测试")
    class EdgeCaseTests {

        @Test
        @DisplayName("处理空方法列表")
        void shouldHandleEmptyMethodList() throws IOException {
            String html = createFullClassHtml("EmptyClass", "空类", createEmptyMethodSection());
            Path htmlFile = writeHtmlFile("EmptyClass.html", html);
            DocGenerationContext context = createContext("project", "main", "commit", "src/EmptyClass.java");

            DocStructuredContent result = parser.parse(context, tempDir, List.of(htmlFile));

            assertThat(result.getTypes().get(0).getMethods()).isEmpty();
        }

        @Test
        @DisplayName("解析interface类型")
        void shouldParseInterface() throws IOException {
            // Given
            String html = """
                    <html><body>
                    <section class="class-description">
                        <div class="type-name-label">UserService</div>
                        <pre class="type-signature">public interface UserService</pre>
                        <div class="block">用户服务接口</div>
                    </section>
                    <section class="method-details">
                        <section class="detail" id="getUser()">
                            <h3>getUser</h3>
                            <pre class="member-signature">public String getUser()</pre>
                            <div class="block">获取用户</div>
                        </section>
                    </section>
                    </body></html>
                    """;
            Path htmlFile = writeHtmlFile("UserService.html", html);
            DocGenerationContext context = createContext("project", "main", "commit", "src/UserService.java");

            DocStructuredContent result = parser.parse(context, tempDir, List.of(htmlFile));

            assertThat(result.getTypes().get(0).getKind()).isEqualTo("INTERFACE");
            assertThat(result.getTypes().get(0).getMethods()).hasSize(1);
        }
    }

    // ========== 辅助方法 ==========

    private DocGenerationContext createContext(String project, String branch, String commitId, String filePath) {
        return DocGenerationContext.builder()
                .project(project)
                .branch(branch)
                .commitId(commitId)
                .filePath(filePath)
                .build();
    }

    private Path writeHtmlFile(String name, String content) throws IOException {
        Path file = tempDir.resolve(name);
        Files.writeString(file, content);
        return file;
    }

    /**
     * 创建完整的类HTML，模拟真实javadoc输出结构
     */
    private String createFullClassHtml(String className, String description, String detailsHtml) {
        return """
                <html><body>
                <section class="class-description">
                    <div class="type-name-label">%s</div>
                    <pre class="type-signature">public class %s</pre>
                    <div class="block">%s</div>
                </section>
                %s
                </body></html>
                """.formatted(className, className, description, detailsHtml);
    }

    /**
     * 创建带有类型签名的HTML（用于测试kind识别）
     */
    private String createTypeSignatureHtml(String typeName, String signature) {
        return """
                <html><body>
                <section class="class-description">
                    <div class="type-name-label">%s</div>
                    <pre class="type-signature">%s</pre>
                    <div class="block">测试类型</div>
                </section>
                </body></html>
                """.formatted(typeName, signature);
    }

    /**
     * 创建方法详情section
     */
    private String createMethodSection(String methodName, String signature, String description,
                                        String paramNames, String paramDesc,
                                        String returnType, String returnDesc,
                                        String throwsType, String throwsDesc) {
        StringBuilder notesHtml = new StringBuilder();
        if (paramNames != null) {
            notesHtml.append("<dt>Parameters:</dt>\n");
            for (String param : paramNames.split(",")) {
                String[] parts = param.trim().split(" ");
                if (parts.length >= 2) {
                    notesHtml.append("<dd><code>").append(parts[1]).append("</code> - ").append(paramDesc).append("</dd>\n");
                }
            }
        }
        if (returnDesc != null) {
            notesHtml.append("<dt>Returns:</dt>\n");
            notesHtml.append("<dd>").append(returnDesc).append("</dd>\n");
        }
        if (throwsType != null) {
            notesHtml.append("<dt>Throws:</dt>\n");
            notesHtml.append("<dd><code>").append(throwsType).append("</code> - ").append(throwsDesc).append("</dd>\n");
        }

        return """
                <section class="method-details">
                    <section class="detail" id="%s()">
                        <h3>%s</h3>
                        <pre class="member-signature">%s</pre>
                        <div class="block">%s</div>
                        <dl class="notes">
                            %s
                        </dl>
                    </section>
                </section>
                """.formatted(methodName, methodName, signature, description, notesHtml);
    }

    /**
     * 创建空的方法section
     */
    private String createEmptyMethodSection() {
        return """
                <section class="method-details">
                </section>
                """;
    }

    /**
     * 创建构造函数section
     */
    private String createConstructorSection(String name, String signature, String description,
                                             String paramNames, String paramDesc) {
        StringBuilder notesHtml = new StringBuilder();
        if (paramNames != null) {
            notesHtml.append("<dt>Parameters:</dt>\n");
            for (String param : paramNames.split(",")) {
                String[] parts = param.trim().split(" ");
                if (parts.length >= 2) {
                    notesHtml.append("<dd><code>").append(parts[1]).append("</code> - ").append(paramDesc).append("</dd>\n");
                }
            }
        }

        return """
                <section class="constructor-details">
                    <section class="detail" id="%s()">
                        <h3>%s</h3>
                        <pre class="member-signature">%s</pre>
                        <div class="block">%s</div>
                        <dl class="notes">
                            %s
                        </dl>
                    </section>
                </section>
                """.formatted(name, name, signature, description, notesHtml);
    }

    /**
     * 创建字段section
     */
    private String createFieldSection(String fieldName, String signature, String description) {
        return """
                <section class="field-details">
                    <section class="detail" id="%s">
                        <h3>%s</h3>
                        <pre class="member-signature">%s</pre>
                        <div class="block">%s</div>
                    </section>
                </section>
                """.formatted(fieldName, fieldName, signature, description);    
    }
}
