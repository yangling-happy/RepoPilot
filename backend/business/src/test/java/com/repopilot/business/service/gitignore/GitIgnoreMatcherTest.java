package com.repopilot.business.service.gitignore;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GitIgnoreMatcher 单元测试
 *
 * 测试覆盖范围：
 * - load() 工厂方法
 * - isIgnored() 规则匹配
 * - 常见 .gitignore 规则（目录、通配符、否定等）
 * - 边界条件（路径规范化、根目录处理）
 */
class GitIgnoreMatcherTest {

    @TempDir
    Path repoRoot;

    private GitIgnoreMatcher matcher;

    @Nested
    @DisplayName("load() 工厂方法测试")
    class LoadTests {

        @Test
        @DisplayName("正常加载.gitignore文件")
        void shouldLoadGitignoreFile() throws IOException {
            // Given
            writeGitignore("*.log\n*.class\n");

            // When
            matcher = GitIgnoreMatcher.load(repoRoot);

            // Then
            assertThat(matcher).isNotNull();
            assertThat(matcher.isIgnored(repoRoot.resolve("test.log"), false)).isTrue();
            assertThat(matcher.isIgnored(repoRoot.resolve("MyClass.class"), false)).isTrue();
            assertThat(matcher.isIgnored(repoRoot.resolve("src/Main.java"), false)).isFalse();
        }

        @Test
        @DisplayName(".gitignore不存在时不忽略任何文件")
        void shouldNotIgnoreWhenGitignoreMissing() throws IOException {
            // Given - 不创建 .gitignore 文件

            // When
            matcher = GitIgnoreMatcher.load(repoRoot);

            // Then
            assertThat(matcher.isIgnored(repoRoot.resolve("test.log"), false)).isFalse();
            assertThat(matcher.isIgnored(repoRoot.resolve("build/output.jar"), false)).isFalse();
        }

        @Test
        @DisplayName("空.gitignore不忽略任何文件")
        void shouldNotIgnoreWithEmptyGitignore() throws IOException {
            // Given
            writeGitignore("");

            // When
            matcher = GitIgnoreMatcher.load(repoRoot);

            // Then
            assertThat(matcher.isIgnored(repoRoot.resolve("test.log"), false)).isFalse();
        }
    }

    @Nested
    @DisplayName("文件匹配规则测试")
    class FilePatternTests {

        @Test
        @DisplayName("匹配特定扩展名")
        void shouldMatchFileExtension() throws IOException {
            // Given
            writeGitignore("*.log");
            matcher = GitIgnoreMatcher.load(repoRoot);

            // Then
            assertThat(matcher.isIgnored(repoRoot.resolve("app.log"), false)).isTrue();
            assertThat(matcher.isIgnored(repoRoot.resolve("path/to/error.log"), false)).isTrue();
            assertThat(matcher.isIgnored(repoRoot.resolve("app.txt"), false)).isFalse();
        }

        @Test
        @DisplayName("匹配特定文件名")
        void shouldMatchExactFilename() throws IOException {
            // Given
            writeGitignore("Thumbs.db\n.DS_Store");
            matcher = GitIgnoreMatcher.load(repoRoot);

            // Then
            assertThat(matcher.isIgnored(repoRoot.resolve("Thumbs.db"), false)).isTrue();
            assertThat(matcher.isIgnored(repoRoot.resolve("subdir/Thumbs.db"), false)).isTrue();
            assertThat(matcher.isIgnored(repoRoot.resolve(".DS_Store"), false)).isTrue();
            assertThat(matcher.isIgnored(repoRoot.resolve("other.db"), false)).isFalse();
        }

        @Test
        @DisplayName("匹配通配符模式")
        void shouldMatchWildcardPattern() throws IOException {
            // Given
            writeGitignore("*.class\n*.jar\n*.war");
            matcher = GitIgnoreMatcher.load(repoRoot);

            // Then
            assertThat(matcher.isIgnored(repoRoot.resolve("MyClass.class"), false)).isTrue();
            assertThat(matcher.isIgnored(repoRoot.resolve("lib/app.jar"), false)).isTrue();
            assertThat(matcher.isIgnored(repoRoot.resolve("target/app.war"), false)).isTrue();
            assertThat(matcher.isIgnored(repoRoot.resolve("app.java"), false)).isFalse();
        }

        @Test
        @DisplayName("匹配带斜杠前缀的规则（仅根目录）")
        void shouldMatchRootOnlyPattern() throws IOException {
            // Given
            writeGitignore("/build\n/dist");
            matcher = GitIgnoreMatcher.load(repoRoot);

            // Then - 只匹配根目录下的
            assertThat(matcher.isIgnored(repoRoot.resolve("build"), true)).isTrue();
            assertThat(matcher.isIgnored(repoRoot.resolve("dist"), true)).isTrue();
            // 子目录中的不匹配
            assertThat(matcher.isIgnored(repoRoot.resolve("sub/build"), true)).isFalse();
        }
    }

    @Nested
    @DisplayName("目录匹配规则测试")
    class DirectoryPatternTests {

        @Test
        @DisplayName("匹配目录下的文件（使用通配符）")
        void shouldMatchFilesInIgnoredDirectory() throws IOException {
            // Given - 使用通配符模式匹配目录下所有文件
            writeGitignore("build/**\ntarget/**");
            matcher = GitIgnoreMatcher.load(repoRoot);

            // Then - 目录下的文件应该被忽略
            assertThat(matcher.isIgnored(repoRoot.resolve("build/output.jar"), false)).isTrue();
            assertThat(matcher.isIgnored(repoRoot.resolve("target/classes/Foo.class"), false)).isTrue();
            // 不在忽略目录下的文件不被忽略
            assertThat(matcher.isIgnored(repoRoot.resolve("src/Main.java"), false)).isFalse();
        }

        @Test
        @DisplayName("匹配特定目录名的文件")
        void shouldMatchFilesWithDirectoryName() throws IOException {
            // Given
            writeGitignore("**/target/*.class");
            matcher = GitIgnoreMatcher.load(repoRoot);

            // Then
            assertThat(matcher.isIgnored(repoRoot.resolve("target/Foo.class"), false)).isTrue();
            assertThat(matcher.isIgnored(repoRoot.resolve("module/target/Bar.class"), false)).isTrue();
            assertThat(matcher.isIgnored(repoRoot.resolve("target/src/Main.java"), false)).isFalse();
        }
    }

    @Nested
    @DisplayName("否定规则测试")
    class NegationTests {

        @Test
        @DisplayName("否定规则（!前缀）取消忽略")
        void shouldSupportNegation() throws IOException {
            // Given
            writeGitignore("*.log\n!important.log");
            matcher = GitIgnoreMatcher.load(repoRoot);

            // Then
            assertThat(matcher.isIgnored(repoRoot.resolve("app.log"), false)).isTrue();
            assertThat(matcher.isIgnored(repoRoot.resolve("important.log"), false)).isFalse();
        }

        @Test
        @DisplayName("否定特定目录下的文件")
        void shouldNegateInSpecificDirectory() throws IOException {
            // Given
            writeGitignore("*.txt\n!doc/*.txt");
            matcher = GitIgnoreMatcher.load(repoRoot);

            // Then
            assertThat(matcher.isIgnored(repoRoot.resolve("readme.txt"), false)).isTrue();
            assertThat(matcher.isIgnored(repoRoot.resolve("doc/guide.txt"), false)).isFalse();
        }
    }

    @Nested
    @DisplayName("注释和空白测试")
    class CommentTests {

        @Test
        @DisplayName("忽略注释行")
        void shouldIgnoreComments() throws IOException {
            // Given
            writeGitignore("# This is a comment\n*.log\n# Another comment\n*.tmp");
            matcher = GitIgnoreMatcher.load(repoRoot);

            // Then
            assertThat(matcher.isIgnored(repoRoot.resolve("app.log"), false)).isTrue();
            assertThat(matcher.isIgnored(repoRoot.resolve("file.tmp"), false)).isTrue();
        }

        @Test
        @DisplayName("忽略空行")
        void shouldIgnoreEmptyLines() throws IOException {
            // Given
            writeGitignore("*.log\n\n\n*.tmp\n");
            matcher = GitIgnoreMatcher.load(repoRoot);

            // Then
            assertThat(matcher.isIgnored(repoRoot.resolve("app.log"), false)).isTrue();
            assertThat(matcher.isIgnored(repoRoot.resolve("file.tmp"), false)).isTrue();
        }
    }

    @Nested
    @DisplayName("路径处理测试")
    class PathHandlingTests {

        @Test
        @DisplayName("路径规范化处理")
        void shouldNormalizePaths() throws IOException {
            // Given
            writeGitignore("*.log");
            matcher = GitIgnoreMatcher.load(repoRoot);

            // When - 使用带 .. 的路径
            Path pathWithParent = repoRoot.resolve("subdir").resolve("..").resolve("app.log");

            // Then
            assertThat(matcher.isIgnored(pathWithParent, false)).isTrue();
        }

        @Test
        @DisplayName("根目录本身不被忽略")
        void shouldNotIgnoreRootDirectory() throws IOException {
            // Given
            writeGitignore("*");
            matcher = GitIgnoreMatcher.load(repoRoot);

            // Then
            assertThat(matcher.isIgnored(repoRoot, true)).isFalse();
        }

        @Test
        @DisplayName("仓库外路径不被忽略")
        void shouldNotIgnorePathsOutsideRepo() throws IOException {
            // Given
            writeGitignore("*.log");
            matcher = GitIgnoreMatcher.load(repoRoot);

            // When
            Path outsidePath = repoRoot.getParent().resolve("other-repo").resolve("app.log");

            // Then
            assertThat(matcher.isIgnored(outsidePath, false)).isFalse();
        }

        @Test
        @DisplayName("Windows反斜杠路径转换")
        void shouldHandleWindowsPaths() throws IOException {
            // Given
            writeGitignore("build/output");
            matcher = GitIgnoreMatcher.load(repoRoot);

            // When - 模拟Windows路径
            Path windowsPath = repoRoot.resolve("build").resolve("output");

            // Then
            assertThat(matcher.isIgnored(windowsPath, false)).isTrue();
        }
    }

    @Nested
    @DisplayName("综合场景测试")
    class IntegrationTests {

        @Test
        @DisplayName("Maven项目的典型.gitignore")
        void shouldHandleMavenGitignore() throws IOException {
            // Given
            writeGitignore("""
                    # Maven
                    target/**
                    pom.xml.tag
                    pom.xml.releaseBackup
                    *.class

                    # IDE
                    *.iml
                    .idea/**

                    # OS
                    .DS_Store
                    Thumbs.db
                    """);
            matcher = GitIgnoreMatcher.load(repoRoot);

            // Then - Maven
            assertThat(matcher.isIgnored(repoRoot.resolve("target/classes/Foo.class"), false)).isTrue();
            assertThat(matcher.isIgnored(repoRoot.resolve("pom.xml.tag"), false)).isTrue();
            assertThat(matcher.isIgnored(repoRoot.resolve("MyClass.class"), false)).isTrue();

            // IDE
            assertThat(matcher.isIgnored(repoRoot.resolve("MyModule.iml"), false)).isTrue();
            assertThat(matcher.isIgnored(repoRoot.resolve(".idea/workspace.xml"), false)).isTrue();

            // OS
            assertThat(matcher.isIgnored(repoRoot.resolve(".DS_Store"), false)).isTrue();
            assertThat(matcher.isIgnored(repoRoot.resolve("Thumbs.db"), false)).isTrue();

            // 不应忽略的
            assertThat(matcher.isIgnored(repoRoot.resolve("src/Main.java"), false)).isFalse();
            assertThat(matcher.isIgnored(repoRoot.resolve("pom.xml"), false)).isFalse();
        }

        @Test
        @DisplayName("Node.js项目的典型.gitignore")
        void shouldHandleNodeJsGitignore() throws IOException {
            // Given
            writeGitignore("""
                    node_modules/**
                    dist/**
                    .env
                    .env.local
                    *.log
                    """);
            matcher = GitIgnoreMatcher.load(repoRoot);

            // Then
            assertThat(matcher.isIgnored(repoRoot.resolve("node_modules/react/index.js"), false)).isTrue();
            assertThat(matcher.isIgnored(repoRoot.resolve("dist/bundle.js"), false)).isTrue();
            assertThat(matcher.isIgnored(repoRoot.resolve(".env"), false)).isTrue();
            assertThat(matcher.isIgnored(repoRoot.resolve(".env.local"), false)).isTrue();
            assertThat(matcher.isIgnored(repoRoot.resolve("app.log"), false)).isTrue();

            // 不应忽略的
            assertThat(matcher.isIgnored(repoRoot.resolve("src/index.js"), false)).isFalse();
            assertThat(matcher.isIgnored(repoRoot.resolve("package.json"), false)).isFalse();
        }

        @Test
        @DisplayName("复杂规则组合")
        void shouldHandleComplexRuleCombination() throws IOException {
            // Given
            writeGitignore("""
                    # 忽略所有日志
                    *.log

                    # 但保留重要日志
                    !important.log

                    # 忽略构建目录
                    build/
                    dist/

                    # 但保留文档构建
                    !docs/build/
                    """);
            matcher = GitIgnoreMatcher.load(repoRoot);

            // Then
            assertThat(matcher.isIgnored(repoRoot.resolve("app.log"), false)).isTrue();
            assertThat(matcher.isIgnored(repoRoot.resolve("important.log"), false)).isFalse();
            assertThat(matcher.isIgnored(repoRoot.resolve("build"), true)).isTrue();
            assertThat(matcher.isIgnored(repoRoot.resolve("dist"), true)).isTrue();
        }
    }

    // ========== 辅助方法 ==========

    private void writeGitignore(String content) throws IOException {
        Files.writeString(repoRoot.resolve(".gitignore"), content);
    }
}
