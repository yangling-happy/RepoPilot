package com.repopilot.business.service.gitignore;

import org.eclipse.jgit.ignore.IgnoreNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class GitIgnoreMatcher {

    private final Path repoRoot;
    private final IgnoreNode rootIgnoreNode;

    private GitIgnoreMatcher(Path repoRoot, IgnoreNode rootIgnoreNode) {
        this.repoRoot = repoRoot.toAbsolutePath().normalize();
        this.rootIgnoreNode = rootIgnoreNode;
    }

    public static GitIgnoreMatcher load(Path repoRoot) throws IOException {
        Path normalizedRoot = repoRoot.toAbsolutePath().normalize();
        IgnoreNode ignoreNode = new IgnoreNode();
        Path gitignore = normalizedRoot.resolve(".gitignore");
        if (Files.isRegularFile(gitignore)) {
            try (InputStream input = Files.newInputStream(gitignore)) {
                ignoreNode.parse(input);
            }
        }
        return new GitIgnoreMatcher(normalizedRoot, ignoreNode);
    }

    public boolean isIgnored(Path path, boolean directory) {
        Path normalized = path.toAbsolutePath().normalize();
        if (!normalized.startsWith(repoRoot) || normalized.equals(repoRoot)) {
            return false;
        }

        String relativePath = repoRoot.relativize(normalized).toString().replace('\\', '/');
        Boolean ignored = rootIgnoreNode.checkIgnored(relativePath, directory);
        return Boolean.TRUE.equals(ignored);
    }
}
