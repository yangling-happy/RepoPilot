package com.repopilot.business.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.repopilot.business.dto.DocLocalScanResult;
import com.repopilot.business.service.DocPipelineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class LocalScanCliRunner implements ApplicationRunner {

    private static final String MODE_SCAN_LOCAL = "scan-local";

    private final DocPipelineService docPipelineService;
    private final ObjectMapper objectMapper;
    private final ConfigurableApplicationContext applicationContext;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!MODE_SCAN_LOCAL.equals(readOption(args, "repopilot.cli.mode", ""))) {
            return;
        }

        int exitCode = 0;
        Path resultFile = null;
        try {
            String gitlabUsername = readRequired(args, "repopilot.cli.gitlab-username");
            String project = readRequired(args, "repopilot.cli.project");
            String branch = readOption(args, "repopilot.cli.branch", "main");
            String terminalSessionId = readOption(args, "repopilot.cli.terminal-session-id", null);
            resultFile = Path.of(readRequired(args, "repopilot.cli.result-file")).toAbsolutePath().normalize();

            DocLocalScanResult result = docPipelineService.scanLocal(
                    gitlabUsername,
                    project,
                    branch,
                    terminalSessionId);
            writeResult(resultFile, "SUCCESS", result.getMessage(), result);
        } catch (Exception e) {
            exitCode = 1;
            log.error("Local scan CLI failed", e);
            if (resultFile != null) {
                writeResult(resultFile, "FAILED", e.getMessage(), null);
            }
        } finally {
            int finalExitCode = exitCode;
            SpringApplication.exit(applicationContext, () -> finalExitCode);
            System.exit(finalExitCode);
        }
    }

    private void writeResult(Path resultFile, String status, String message, Object data) throws Exception {
        Path parent = resultFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", status);
        payload.put("message", message);
        payload.put("data", data);
        objectMapper.writeValue(resultFile.toFile(), payload);
    }

    private String readRequired(ApplicationArguments args, String name) {
        String value = readOption(args, name, null);
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }

    private String readOption(ApplicationArguments args, String name, String fallback) {
        List<String> values = args.getOptionValues(name);
        if (values == null || values.isEmpty()) {
            return fallback;
        }
        String value = values.get(0);
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }
}
