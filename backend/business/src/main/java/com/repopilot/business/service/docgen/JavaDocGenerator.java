package com.repopilot.business.service.docgen;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.repopilot.business.dto.DocStructuredContent;
import com.repopilot.common.exception.BusinessException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class JavaDocGenerator implements DocGenerator {

    private static final Duration JAVADOC_TIMEOUT = Duration.ofSeconds(60);
    private static final String STRUCTURED_DOC_FILE = "doc.json";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final JavaDocHtmlParser htmlParser = new JavaDocHtmlParser();

    @Override
    public String toolName() {
        return "javadoc";
    }

    @Override
    public Set<String> supportedExtensions() {
        return Set.of(".java");
    }

    @Override
    public DocGenerationResult generate(DocGenerationContext context) {
        Path structuredDocPath = runJavadoc(context);
        return DocGenerationResult.builder()
                .docFilePath(structuredDocPath.toString())
                .build();
    }

    private Path runJavadoc(DocGenerationContext context) {
        Path workDir = null;
        try {
            workDir = Files.createTempDirectory("repopilot-javadoc-");
            Path outputDir = prepareOutputDir(context);
            Path commandOutputFile = workDir.resolve("javadoc.log");

            Path tempSourceRoot = writeSourceFile(context, workDir);
            Path sourceFile = safeResolve(tempSourceRoot, context.getFilePath());

            ProcessBuilder processBuilder = new ProcessBuilder(
                    resolveJavadocCommand(),
                    "-quiet",
                    "-encoding", "UTF-8",
                    "-charset", "UTF-8",
                    "-docencoding", "UTF-8",
                    "-Xdoclint:none",
                    "--ignore-source-errors",
                    "-private",
                    "-sourcepath", buildSourcePath(tempSourceRoot, context.getSourceRoot()),
                    "-d", outputDir.toString(),
                    sourceFile.toString()
            );
            processBuilder.redirectErrorStream(true);
            processBuilder.redirectOutput(commandOutputFile.toFile());

            Process process = processBuilder.start();
            boolean completed = process.waitFor(JAVADOC_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            if (!completed) {
                process.destroyForcibly();
                throw new BusinessException(500, "javadoc timed out for file: " + context.getFilePath());
            }

            String commandOutput = Files.exists(commandOutputFile)
                    ? Files.readString(commandOutputFile, StandardCharsets.UTF_8)
                    : "";
            if (process.exitValue() != 0) {
                throw new BusinessException(500, "javadoc failed for file: "
                        + context.getFilePath() + ". " + summarize(commandOutput));
            }

            JavaDocOutput output = readJavaDocOutput(outputDir);
            DocStructuredContent structuredContent = htmlParser.parse(context, output.outputDir(), output.htmlFiles());
            return writeStructuredDoc(workDir, output.outputDir(), structuredContent);
        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            throw new BusinessException(500, "Failed to run javadoc for file: " + context.getFilePath());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(500, "javadoc interrupted for file: " + context.getFilePath());
        } finally {
            deleteQuietly(workDir);
        }
    }

    private Path prepareOutputDir(DocGenerationContext context) throws IOException {
        Path outputRoot = context.getOutputRoot();
        if (outputRoot == null) {
            outputRoot = Path.of("workspace", "docs");
        }

        Path outputDir = outputRoot.toAbsolutePath().normalize()
                .resolve(sanitizePathSegment(context.getProject()))
                .resolve(sanitizePathSegment(context.getBranch()))
                .resolve(sanitizePathSegment(context.getCommitId()))
                .resolve(hashPath(context.getFilePath()))
                .normalize();
        deleteQuietly(outputDir);
        Files.createDirectories(outputDir);
        return outputDir;
    }

    private Path writeSourceFile(DocGenerationContext context, Path workDir) throws IOException {
        Path tempSourceRoot = workDir.resolve("source");
        Path sourceFile = safeResolve(tempSourceRoot, context.getFilePath());
        Files.createDirectories(sourceFile.getParent());
        Files.writeString(sourceFile, context.getSourceContent() == null ? "" : context.getSourceContent(),
                StandardCharsets.UTF_8);
        return tempSourceRoot;
    }

    private String buildSourcePath(Path tempSourceRoot, Path localSourceRoot) {
        if (localSourceRoot == null || !Files.isDirectory(localSourceRoot)) {
            return tempSourceRoot.toString();
        }
        return tempSourceRoot + System.getProperty("path.separator") + localSourceRoot.toAbsolutePath().normalize();
    }

    private Path safeResolve(Path root, String filePath) {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path resolved = normalizedRoot.resolve(filePath).normalize();
        if (!resolved.startsWith(normalizedRoot)) {
            throw new BusinessException(400, "Invalid source file path for javadoc");
        }
        return resolved;
    }

    private JavaDocOutput readJavaDocOutput(Path outputDir) throws IOException {
        List<Path> htmlFiles;
        try (Stream<Path> stream = Files.walk(outputDir)) {
            htmlFiles = stream.filter(path -> Files.isRegularFile(path)
                            && path.getFileName().toString().endsWith(".html"))
                    .sorted()
                    .collect(Collectors.toList());
        }

        if (htmlFiles.isEmpty()) {
            throw new BusinessException(500, "javadoc produced no HTML output");
        }

        return new JavaDocOutput(outputDir, htmlFiles);
    }

    private Path writeStructuredDoc(Path workDir, Path outputDir, DocStructuredContent structuredContent) {
        Path tempJson = workDir.resolve(STRUCTURED_DOC_FILE);
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(tempJson.toFile(), structuredContent);
            return replaceJavadocOutputWithJson(outputDir, tempJson);
        } catch (IOException ex) {
            throw new BusinessException(500, "Failed to write structured javadoc JSON");
        }
    }

    private Path replaceJavadocOutputWithJson(Path outputDir, Path tempJson) throws IOException {
        Path normalizedOutputDir = outputDir.toAbsolutePath().normalize();
        Path backupRoot = Files.createTempDirectory(normalizedOutputDir.getParent(),
                normalizedOutputDir.getFileName() + "-javadoc-");
        Path backupOutputDir = backupRoot.resolve("output");
        Path jsonPath = normalizedOutputDir.resolve(STRUCTURED_DOC_FILE);
        boolean movedOriginal = false;

        try {
            Files.move(normalizedOutputDir, backupOutputDir);
            movedOriginal = true;
            Files.createDirectories(normalizedOutputDir);
            Files.move(tempJson, jsonPath, StandardCopyOption.REPLACE_EXISTING);
            deleteRecursively(backupRoot);
            return jsonPath;
        } catch (IOException ex) {
            restoreJavadocOutput(normalizedOutputDir, backupOutputDir, movedOriginal);
            deleteQuietly(backupRoot);
            throw ex;
        }
    }

    private void restoreJavadocOutput(Path outputDir, Path backupOutputDir, boolean movedOriginal) {
        if (!movedOriginal) {
            return;
        }
        deleteQuietly(outputDir);
        try {
            if (Files.exists(backupOutputDir) && !Files.exists(outputDir)) {
                Files.move(backupOutputDir, outputDir);
            }
        } catch (IOException ignored) {
            // The parse failure path should keep the original javadoc files when the filesystem allows it.
        }
    }

    private void deleteRecursively(Path root) throws IOException {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(root)) {
            List<Path> paths = stream.sorted(Comparator.reverseOrder()).collect(Collectors.toList());
            for (Path path : paths) {
                Files.deleteIfExists(path);
            }
        }
    }

    private String resolveJavadocCommand() {
        String executable = System.getProperty("os.name", "").toLowerCase().contains("win")
                ? "javadoc.exe"
                : "javadoc";
        Path javaHomeJavadoc = Path.of(System.getProperty("java.home"), "bin", executable);
        if (Files.isRegularFile(javaHomeJavadoc)) {
            return javaHomeJavadoc.toString();
        }
        return executable;
    }

    private String summarize(String output) {
        if (!StringUtils.hasText(output)) {
            return "No javadoc output.";
        }
        String normalized = output.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 500 ? normalized : normalized.substring(0, 500);
    }

    private String sanitizePathSegment(String value) {
        if (!StringUtils.hasText(value)) {
            return "_";
        }
        return value.trim().replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private String hashPath(String filePath) {
        return Integer.toHexString(filePath == null ? 0 : filePath.hashCode());
    }

    private void deleteQuietly(Path root) {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(root)) {
            stream.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // Temporary javadoc files can be cleaned by the OS if deletion fails.
                }
            });
        } catch (IOException ignored) {
            // Temporary javadoc files can be cleaned by the OS if deletion fails.
        }
    }

    private record JavaDocOutput(Path outputDir, List<Path> htmlFiles) {
    }
}
