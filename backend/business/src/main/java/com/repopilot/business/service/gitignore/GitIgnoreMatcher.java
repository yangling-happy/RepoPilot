package com.repopilot.business.service.gitignore;

import org.eclipse.jgit.ignore.IgnoreNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

//GitIgnore 规则匹配器
//作用：判断某个文件是否应该被 .gitignore 规则忽略
//内部使用 JGit（Git 的 Java 实现）的 IgnoreNode 来解析和匹配 .gitignore 规则
public class GitIgnoreMatcher {

    //仓库根目录的绝对路径
    private final Path repoRoot;
    //JGit 的 .gitignore 规则解析器
    private final IgnoreNode rootIgnoreNode;

    private GitIgnoreMatcher(Path repoRoot, IgnoreNode rootIgnoreNode) {
        this.repoRoot = repoRoot.toAbsolutePath().normalize();
        this.rootIgnoreNode = rootIgnoreNode;
    }

    //工厂方法：加载仓库根目录下的 .gitignore 文件并创建匹配器
    //如果 .gitignore 文件不存在，创建一个空规则的匹配器（即不忽略任何文件）
    public static GitIgnoreMatcher load(Path repoRoot) throws IOException {
        Path normalizedRoot = repoRoot.toAbsolutePath().normalize();
        IgnoreNode ignoreNode = new IgnoreNode();
        Path gitignore = normalizedRoot.resolve(".gitignore");
        if (Files.isRegularFile(gitignore)) {
            //解析 .gitignore 文件中的规则
            try (InputStream input = Files.newInputStream(gitignore)) {
                ignoreNode.parse(input);
            }
        }
        return new GitIgnoreMatcher(normalizedRoot, ignoreNode);
    }

    //判断指定路径的文件/目录是否被 .gitignore 忽略
    //path: 要检查的文件/目录路径
    //directory: 是否是目录（.gitignore 对文件和目录的匹配规则可能不同）
    public boolean isIgnored(Path path, boolean directory) {
        Path normalized = path.toAbsolutePath().normalize();
        //如果路径不在仓库根目录下，或者就是根目录本身，不认为是被忽略的
        if (!normalized.startsWith(repoRoot) || normalized.equals(repoRoot)) {
            return false;
        }

        //将绝对路径转为相对于仓库根目录的路径（.gitignore 规则都是相对于仓库根的）
        //同时将 Windows 的反斜杠转为正斜杠，保持与 .gitignore 规则一致
        String relativePath = repoRoot.relativize(normalized).toString().replace('\\', '/');
        //调用 JGit 的规则匹配
        Boolean ignored = rootIgnoreNode.checkIgnored(relativePath, directory);
        return Boolean.TRUE.equals(ignored);
    }
}
