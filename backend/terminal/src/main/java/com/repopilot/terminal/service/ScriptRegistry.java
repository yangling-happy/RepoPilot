package com.repopilot.terminal.service;

import com.repopilot.terminal.dto.TerminalTaskType;
import com.repopilot.terminal.exception.TerminalTaskException;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

//脚本注册表
//职责：管理所有终端任务的 shell 脚本，包括：
//  1. 定义每种任务类型对应的脚本文件和参数映射
//  2. 应用启动时将 classpath 中的脚本文件复制到临时目录
//  3. 根据任务类型和参数创建脚本启动计划（ScriptLaunchPlan）
@Component
public class ScriptRegistry {

    //脚本文件在 classpath 中的根路径
    private static final String RESOURCE_ROOT = "scripts/";
    //所有需要部署的脚本文件名
    private static final Set<String> SCRIPT_FILES = Set.of(
            "common.sh",          //公共函数库
            "clone-repo.sh",      //克隆仓库脚本
            "refresh-doc.sh",     //刷新文档脚本
            "scan-local-doc.sh",  //本地扫描文档脚本
            "build-project.sh",   //构建项目脚本
            "deploy-project.sh", //部署项目脚本
            "custom-deploy.sh",  //自定义部署脚本（执行仓库内 deploy.sh）
            "setup-ssh-key.sh"); //SSH 密钥配置脚本

    //任务类型 -> 脚本定义的映射（EnumMap 比 HashMap 更高效）
    private final Map<TerminalTaskType, ScriptDefinition> definitions = new EnumMap<>(TerminalTaskType.class);

    //执行脚本的 shell 程序（默认 bash，可通过配置文件修改）
    @Value("${terminal.tasks.shell:bash}")
    private String shell;

    //自定义脚本目录（可选，为空则使用系统临时目录）
    @Value("${terminal.tasks.script-dir:}")
    private String configuredScriptDir;

    //脚本文件实际存放的目录
    private Path scriptDirectory;

    public ScriptRegistry() {
        definitions.put(TerminalTaskType.CLONE_REPO, new ScriptDefinition(
                "clone-repo.sh",
                List.of(required("projectId", "--project-id"),
                        required("branch", "--branch"),
                        required("username", "--username"),
                        optional("repoUrl", "--repo-url"),
                        optional("targetDir", "--target-dir"),
                        optional("workspaceRoot", "--workspace-root")),
                Map.of("gitlabToken", "GITLAB_TOKEN")));
        definitions.put(TerminalTaskType.REFRESH_DOC, new ScriptDefinition(
                "refresh-doc.sh",
                List.of(required("project", "--project"),
                        required("branch", "--branch"),
                        required("username", "--username"),
                        optional("repoDir", "--repo-dir"),
                        optional("workspaceRoot", "--workspace-root")),
                Map.of("gitlabToken", "GITLAB_TOKEN")));
        definitions.put(TerminalTaskType.SCAN_LOCAL_DOC, new ScriptDefinition(
                "scan-local-doc.sh",
                List.of(required("project", "--project"),
                        required("branch", "--branch"),
                        required("username", "--username"),
                        optional("repoDir", "--repo-dir"),
                        optional("workspaceRoot", "--workspace-root")),
                Map.of()));
        definitions.put(TerminalTaskType.BUILD_PROJECT, new ScriptDefinition(
                "build-project.sh",
                List.of(required("project", "--project"),
                        required("branch", "--branch"),
                        required("username", "--username"),
                        optional("repoDir", "--repo-dir"),
                        optional("workspaceRoot", "--workspace-root")),
                Map.of()));
        definitions.put(TerminalTaskType.DEPLOY_PROJECT, new ScriptDefinition(
                "deploy-project.sh",
                List.of(required("project", "--project"),
                        required("branch", "--branch"),
                        required("username", "--username"),
                        optional("artifactPath", "--artifact-path"),
                        optional("repoDir", "--repo-dir"),
                        optional("workspaceRoot", "--workspace-root")),
                Map.of("deployTargetDir", "DEPLOY_TARGET_DIR",
                       "deployHost", "DEPLOY_HOST",
                       "deployPort", "DEPLOY_PORT",
                       "deployUser", "DEPLOY_USER")));
        definitions.put(TerminalTaskType.CUSTOM_DEPLOY, new ScriptDefinition(
                "custom-deploy.sh",
                List.of(required("repoDir", "--repo-dir")),
                Map.of()));
        definitions.put(TerminalTaskType.SETUP_SSH_KEY, new ScriptDefinition(
                "setup-ssh-key.sh",
                List.of(required("host", "--host"),
                        optional("port", "--port"),
                        required("user", "--user")),
                Map.of("sshPassword", "SSH_PASSWORD")));
    }

    //@PostConstruct 注解：Spring Bean 初始化完成后自动调用此方法
    //在这里将 classpath 中的脚本文件复制到临时目录，并设置可执行权限
    @PostConstruct
    public void initialize() {
        scriptDirectory = resolveScriptDirectory();
        try {
            Files.createDirectories(scriptDirectory);
            for (String scriptFile : SCRIPT_FILES) {
                copyScript(scriptFile);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to prepare terminal task scripts", e);
        }
    }

    public ScriptLaunchPlan createLaunchPlan(TerminalTaskType taskType, Map<String, Object> args) {
        ScriptDefinition definition = definitions.get(taskType);
        if (definition == null) {
            throw new TerminalTaskException(400, "unsupported taskType: " + taskType);
        }

        Path scriptPath = scriptDirectory.resolve(definition.scriptFile()).normalize();
        if (!scriptPath.startsWith(scriptDirectory) || !Files.isRegularFile(scriptPath)) {
            throw new TerminalTaskException(500, "registered script is not available: " + definition.scriptFile());
        }

        Map<String, Object> safeArgs = args == null ? Map.of() : args;
        List<String> command = new ArrayList<>();
        command.add(resolveShell());
        command.add(scriptPath.toString());
        command.addAll(definition.toCommandArguments(safeArgs));

        Map<String, String> environment = new LinkedHashMap<>();
        definition.addEnvironment(safeArgs, environment);
        augmentPath(environment);

        return new ScriptLaunchPlan(taskType, List.copyOf(command), Map.copyOf(environment), scriptDirectory);
    }

    public TerminalTaskType parseTaskType(String rawTaskType) {
        if (!StringUtils.hasText(rawTaskType)) {
            throw new TerminalTaskException(400, "taskType is required");
        }
        try {
            return TerminalTaskType.valueOf(rawTaskType.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new TerminalTaskException(400, "unsupported taskType: " + rawTaskType);
        }
    }

    private Path resolveScriptDirectory() {
        if (StringUtils.hasText(configuredScriptDir)) {
            return Path.of(configuredScriptDir).toAbsolutePath().normalize();
        }
        return Path.of(System.getProperty("java.io.tmpdir"), "repopilot-terminal-scripts")
                .toAbsolutePath()
                .normalize();
    }

    private String resolveShell() {
        if (!StringUtils.hasText(shell)) {
            return "bash";
        }
        return shell.trim();
    }

    private void copyScript(String scriptFile) throws IOException {
        Path target = scriptDirectory.resolve(scriptFile).normalize();
        if (!target.startsWith(scriptDirectory)) {
            throw new IOException("Unsafe script target: " + scriptFile);
        }

        ClassPathResource resource = new ClassPathResource(RESOURCE_ROOT + scriptFile);
        String content;
        try (InputStream inputStream = resource.getInputStream()) {
            content = new String(inputStream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        content = content.replace("\r\n", "\n").replace("\r", "\n");
        Files.write(target, content.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.TRUNCATE_EXISTING,
                java.nio.file.StandardOpenOption.WRITE);
        makeExecutable(target);
    }

    private void makeExecutable(Path target) {
        try {
            Files.setPosixFilePermissions(target, Set.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE,
                    PosixFilePermission.GROUP_READ,
                    PosixFilePermission.GROUP_EXECUTE));
        } catch (UnsupportedOperationException | IOException ignored) {
            // Windows and some filesystems do not support POSIX permissions; bash can still read the script.
        }
    }

    //将脚本目录加入 PATH 环境变量，使脚本可以调用同目录下的其他脚本
    private void augmentPath(Map<String, String> environment) {
        String currentPath = System.getenv("PATH");
        String scriptDir = scriptDirectory.toAbsolutePath().toString();
        if (currentPath != null && !currentPath.isEmpty()) {
            environment.put("PATH", scriptDir + File.pathSeparator + currentPath);
        } else {
            environment.put("PATH", scriptDir);
        }
    }

    private static ArgSpec required(String key, String flag) {
        return new ArgSpec(key, flag, true);
    }

    private static ArgSpec optional(String key, String flag) {
        return new ArgSpec(key, flag, false);
    }

    private record ScriptDefinition(
            String scriptFile,
            List<ArgSpec> arguments,
            Map<String, String> environmentArguments) {

        private List<String> toCommandArguments(Map<String, Object> args) {
            List<String> commandArgs = new ArrayList<>();
            for (ArgSpec argument : arguments) {
                String value = readText(args, argument.key(), argument.required());
                if (value == null) {
                    continue;
                }
                commandArgs.add(argument.flag());
                commandArgs.add(value);
            }
            return commandArgs;
        }

        private void addEnvironment(Map<String, Object> args, Map<String, String> environment) {
            for (Map.Entry<String, String> entry : environmentArguments.entrySet()) {
                String value = readText(args, entry.getKey(), false);
                if (value != null) {
                    environment.put(entry.getValue(), value);
                }
            }
        }

        private static String readText(Map<String, Object> args, String key, boolean required) {
            Object value = args.get(key);
            if (value == null) {
                if (required) {
                    throw new TerminalTaskException(400, key + " is required");
                }
                return null;
            }

            if (value instanceof Map<?, ?> || value instanceof Iterable<?>) {
                throw new TerminalTaskException(400, key + " must be a scalar value");
            }

            String text = String.valueOf(value).trim();
            if (!StringUtils.hasText(text)) {
                if (required) {
                    throw new TerminalTaskException(400, key + " is required");
                }
                return null;
            }
            if (text.indexOf('\0') >= 0) {
                throw new TerminalTaskException(400, key + " contains an invalid character");
            }
            return text;
        }
    }

    private record ArgSpec(String key, String flag, boolean required) {
    }
}
