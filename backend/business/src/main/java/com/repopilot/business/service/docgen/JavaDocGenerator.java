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

//Spring 注解，将这个类注册为 Spring Bean，Spring 会自动创建它的实例并管理
//由于实现了 DocGenerator 接口，Spring 会自动将它注入到 DocGeneratorRegistry 中
@Component
public class JavaDocGenerator implements DocGenerator {

    //javadoc 命令执行超时时间（60秒），防止 javadoc 卡死导致线程永久阻塞
    private static final Duration JAVADOC_TIMEOUT = Duration.ofSeconds(60);
    //最终输出的结构化文档文件名
    private static final String STRUCTURED_DOC_FILE = "doc.json";

    //Jackson 的 JSON 序列化/反序列化工具，用于将 Java 对象转成 JSON
    private final ObjectMapper objectMapper = new ObjectMapper();
    //HTML 解析器，负责将 javadoc 生成的 HTML 页面解析成结构化的 DocStructuredContent
    private final JavaDocHtmlParser htmlParser = new JavaDocHtmlParser();

    //返回工具名称，用于日志标识
    @Override
    public String toolName() {
        return "javadoc";
    }

    //这个生成器只处理 .java 文件
    @Override
    public Set<String> supportedExtensions() {
        return Set.of(".java");
    }

    //文档生成的入口方法：调用 javadoc 命令 -> 解析 HTML -> 输出结构化 JSON
    @Override
    public DocGenerationResult generate(DocGenerationContext context) {
        Path structuredDocPath = runJavadoc(context);
        return DocGenerationResult.builder()
                .docFilePath(structuredDocPath.toString())
                .build();
    }

    //核心方法：调用系统的 javadoc 命令生成 HTML 文档，然后解析成结构化 JSON
    //整体流程：创建临时目录 -> 写入源文件 -> 执行 javadoc -> 读取 HTML 输出 -> 解析为结构化对象 -> 写入 JSON
    private Path runJavadoc(DocGenerationContext context) {
        Path workDir = null;
        try {
            //创建临时工作目录，用于存放中间产物（源文件、javadoc 日志等）
            workDir = Files.createTempDirectory("repopilot-javadoc-");
            //准备最终的输出目录（按 项目/分支/commit/文件哈希 组织）
            Path outputDir = prepareOutputDir(context);
            //javadoc 命令的标准输出日志文件
            Path commandOutputFile = workDir.resolve("javadoc.log");

            //将源代码内容写入临时文件（javadoc 需要读取文件，不能直接从内存处理）
            Path tempSourceRoot = writeSourceFile(context, workDir);
            //安全解析源文件路径，防止路径穿越
            Path sourceFile = safeResolve(tempSourceRoot, context.getFilePath());

            //构建 javadoc 命令行
            //ProcessBuilder 用于启动外部进程（相当于在命令行执行 javadoc 命令）
            ProcessBuilder processBuilder = new ProcessBuilder(
                    resolveJavadocCommand(),  //javadoc 可执行文件路径
                    "-quiet",                 //安静模式，减少输出
                    "-encoding", "UTF-8",     //源文件编码
                    "-charset", "UTF-8",      //HTML charset
                    "-docencoding", "UTF-8",  //文档编码
                    "-Xdoclint:none",         //关闭文档检查（避免因为 Javadoc 不规范而报错）
                    "--ignore-source-errors", //忽略源码编译错误（只提取已有的 Javadoc 注释）
                    "-private",               //包含 private 成员的文档
                    "-sourcepath", buildSourcePath(tempSourceRoot, context.getSourceRoot()),
                    "-d", outputDir.toString(), //输出目录
                    sourceFile.toString()     //要处理的源文件
            );
            //将错误流合并到标准输出（javac/javadoc 的错误信息混在标准输出中）
            processBuilder.redirectErrorStream(true);
            //将所有输出重定向到日志文件
            processBuilder.redirectOutput(commandOutputFile.toFile());

            //启动 javadoc 进程
            Process process = processBuilder.start();
            //等待进程完成，设置超时时间（防止 javadoc 卡死）
            boolean completed = process.waitFor(JAVADOC_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            if (!completed) {
                //超时了，强制杀掉进程
                process.destroyForcibly();
                throw new BusinessException(500, "javadoc timed out for file: " + context.getFilePath());
            }

            //读取 javadoc 的输出日志
            String commandOutput = Files.exists(commandOutputFile)
                    ? Files.readString(commandOutputFile, StandardCharsets.UTF_8)
                    : "";
            //退出码非 0 表示 javadoc 执行失败
            if (process.exitValue() != 0) {
                throw new BusinessException(500, "javadoc failed for file: "
                        + context.getFilePath() + ". " + summarize(commandOutput));
            }

            //读取 javadoc 生成的 HTML 文件列表
            JavaDocOutput output = readJavaDocOutput(outputDir);
            //用 Jsoup 解析 HTML，提取结构化的文档信息（类名、方法、参数、注释等）
            DocStructuredContent structuredContent = htmlParser.parse(context, output.outputDir(), output.htmlFiles());
            //将结构化对象序列化为 JSON 文件并返回路径
            return writeStructuredDoc(workDir, output.outputDir(), structuredContent);
        } catch (BusinessException e) {
            //业务异常直接抛出
            throw e;
        } catch (IOException e) {
            //IO 异常包装成业务异常
            throw new BusinessException(500, "Failed to run javadoc for file: " + context.getFilePath());
        } catch (InterruptedException e) {
            //Java 里通常不会强制杀死一个线程，而是通过 interrupt() 温和地通知线程
            //当阻塞方法抛出 InterruptedException 时，线程的中断标志会被清除
            //以下代码是将中断包装成一个业务异常，同时恢复中断标志
            Thread.currentThread().interrupt();
            throw new BusinessException(500, "javadoc interrupted for file: " + context.getFilePath());
        } finally {
            //无论成功还是失败，都清理临时工作目录
            deleteQuietly(workDir);
        }
    }

    //准备文档输出目录，路径结构：{outputRoot}/{project}/{branch}/{commitId}/{fileHash}/
    //这样每个文件的文档产物都隔离在独立目录中，不会互相覆盖
    private Path prepareOutputDir(DocGenerationContext context) throws IOException {
        Path outputRoot = context.getOutputRoot();
        if (outputRoot == null) {
            outputRoot = Path.of("workspace", "docs");
        }

        //sanitizePathSegment: 将特殊字符替换为下划线，防止路径注入
        //hashPath: 对文件路径取哈希，避免文件名中的特殊字符导致路径问题
        Path outputDir = outputRoot.toAbsolutePath().normalize()
                .resolve(sanitizePathSegment(context.getProject()))
                .resolve(sanitizePathSegment(context.getBranch()))
                .resolve(sanitizePathSegment(context.getCommitId()))
                .resolve(hashPath(context.getFilePath()))
                .normalize();
        //先删除旧目录（重建场景下需要覆盖）
        deleteQuietly(outputDir);
        Files.createDirectories(outputDir);
        return outputDir;
    }

    //将源代码内容写入临时目录，保持原始的目录结构（javadoc 需要通过 -sourcepath 定位源文件）
    private Path writeSourceFile(DocGenerationContext context, Path workDir) throws IOException {
        Path tempSourceRoot = workDir.resolve("source");
        //safeResolve 防止 filePath 中的 ../ 导致路径穿越
        Path sourceFile = safeResolve(tempSourceRoot, context.getFilePath());
        //创建父目录（如 source/src/main/java/com/example/）
        Files.createDirectories(sourceFile.getParent());
        //将源代码写入临时文件
        Files.writeString(sourceFile, context.getSourceContent() == null ? "" : context.getSourceContent(),
                StandardCharsets.UTF_8);
        return tempSourceRoot;
    }

    //构建 javadoc 的 -sourcepath 参数
    //如果有本地仓库的源码根目录，也加入 sourcepath，这样 javadoc 可以解析 import 的类
    private String buildSourcePath(Path tempSourceRoot, Path localSourceRoot) {
        if (localSourceRoot == null || !Files.isDirectory(localSourceRoot)) {
            return tempSourceRoot.toString();
        }
        //用系统路径分隔符（Windows 是 ;，Linux 是 :）拼接多个源码路径
        return tempSourceRoot + System.getProperty("path.separator") + localSourceRoot.toAbsolutePath().normalize();
    }

    //安全的路径解析：防止路径穿越攻击
    //如果 filePath 包含 ../ 等内容，解析后可能跳出 root 目录，这里做了检查
    private Path safeResolve(Path root, String filePath) {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path resolved = normalizedRoot.resolve(filePath).normalize();
        if (!resolved.startsWith(normalizedRoot)) {
            throw new BusinessException(400, "Invalid source file path for javadoc");
        }
        return resolved;
    }

    //读取 javadoc 输出目录中的所有 HTML 文件（javadoc 为每个类生成一个 HTML 页面）
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

    //将结构化的文档对象序列化为 JSON 文件，替换掉 javadoc 原始的 HTML 输出
    private Path writeStructuredDoc(Path workDir, Path outputDir, DocStructuredContent structuredContent) {
        Path tempJson = workDir.resolve(STRUCTURED_DOC_FILE);
        try {
            //writerWithDefaultPrettyPrinter: 格式化输出（带缩进），方便调试查看
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(tempJson.toFile(), structuredContent);
            //将 JSON 文件放到输出目录，替换掉 javadoc 生成的 HTML 文件
            return replaceJavadocOutputWithJson(outputDir, tempJson);
        } catch (IOException ex) {
            throw new BusinessException(500, "Failed to write structured javadoc JSON");
        }
    }

    //用 JSON 文件替换 javadoc 原始输出目录的内容
    //先备份原目录 -> 创建新目录 -> 放入 JSON -> 删除备份
    //如果中间出错，尝试恢复原目录
    private Path replaceJavadocOutputWithJson(Path outputDir, Path tempJson) throws IOException {
        Path normalizedOutputDir = outputDir.toAbsolutePath().normalize();
        Path backupRoot = Files.createTempDirectory(normalizedOutputDir.getParent(),
                normalizedOutputDir.getFileName() + "-javadoc-");
        Path backupOutputDir = backupRoot.resolve("output");
        Path jsonPath = normalizedOutputDir.resolve(STRUCTURED_DOC_FILE);
        boolean movedOriginal = false;

        try {
            //先将原输出目录移到备份位置
            Files.move(normalizedOutputDir, backupOutputDir);
            movedOriginal = true;
            //重建原目录，只放入 JSON 文件
            Files.createDirectories(normalizedOutputDir);
            Files.move(tempJson, jsonPath, StandardCopyOption.REPLACE_EXISTING);
            //成功后删除备份目录（原始 HTML 不再需要）
            deleteRecursively(backupRoot);
            return jsonPath;
        } catch (IOException ex) {
            //出错时尝试恢复原目录
            restoreJavadocOutput(normalizedOutputDir, backupOutputDir, movedOriginal);
            deleteQuietly(backupRoot);
            throw ex;
        }
    }

    //恢复原 javadoc 输出目录（替换操作失败时的回滚逻辑）
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
            //恢复失败也没办法了，原始 javadoc 文件可能已经丢失
        }
    }

    //递归删除目录及其所有内容（会抛 IOException）
    //先删除子文件/子目录，再删除父目录（Comparator.reverseOrder 确保深度优先）
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

    //根据操作系统选择 javadoc 可执行文件名
    //优先使用 JAVA_HOME/bin 下的 javadoc，找不到就用系统 PATH 中的
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

    //截取 javadoc 输出的前 500 个字符作为错误摘要（避免日志过长）
    private String summarize(String output) {
        if (!StringUtils.hasText(output)) {
            return "No javadoc output.";
        }
        String normalized = output.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 500 ? normalized : normalized.substring(0, 500);
    }

    //清理路径段中的特殊字符，只保留字母、数字、点、下划线、横线
    //防止文件名中的特殊字符（如 /、\、:）导致路径问题
    private String sanitizePathSegment(String value) {
        if (!StringUtils.hasText(value)) {
            return "_";
        }
        return value.trim().replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    //对文件路径取哈希值，转为十六进制字符串，作为输出目录名
    //这样即使文件名很长或包含特殊字符，也能生成合法的目录名
    private String hashPath(String filePath) {
        return Integer.toHexString(filePath == null ? 0 : filePath.hashCode());
    }

    //安静地删除目录（不抛异常），用于清理临时文件
    //与 deleteRecursively 不同的是，这里吞掉所有 IOException
    private void deleteQuietly(Path root) {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(root)) {
            stream.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    //临时文件删不掉也没关系，操作系统会在合适的时候清理
                }
            });
        } catch (IOException ignored) {
            //同上
        }
    }

    //内部 record，封装 javadoc 输出目录和其中的 HTML 文件列表
    private record JavaDocOutput(Path outputDir, List<Path> htmlFiles) {
    }
}
