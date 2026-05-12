package com.repopilot.terminal.service;

import com.repopilot.terminal.dto.TerminalTaskType;
import com.repopilot.terminal.exception.TerminalTaskException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScriptRegistryTest {

    @TempDir
    Path tempDir;

    @Test
    void cloneRepoLaunchPlanRequiresRepoUrl() {
        ScriptRegistry registry = initializedRegistry();

        assertThatThrownBy(() -> registry.createLaunchPlan(
                TerminalTaskType.CLONE_REPO,
                Map.of(
                        "projectId", 42,
                        "branch", "main",
                        "username", "alice",
                        "gitlabToken", "secret")))
                .isInstanceOf(TerminalTaskException.class)
                .hasMessage("repoUrl is required");
    }

    @Test
    void cloneRepoLaunchPlanPutsTokenInEnvironmentOnly() {
        ScriptRegistry registry = initializedRegistry();

        ScriptLaunchPlan plan = registry.createLaunchPlan(
                TerminalTaskType.CLONE_REPO,
                Map.of(
                        "projectId", 42,
                        "branch", "main",
                        "username", "alice",
                        "repoUrl", "https://gitlab.example.com/team/app.git",
                        "projectPath", "team/app",
                        "gitlabToken", "secret-token"));

        assertThat(plan.taskType()).isEqualTo(TerminalTaskType.CLONE_REPO);
        assertThat(plan.environment()).containsEntry("GITLAB_TOKEN", "secret-token");
        assertThat(plan.command()).containsSubsequence(
                "--project-id", "42",
                "--branch", "main",
                "--username", "alice",
                "--repo-url", "https://gitlab.example.com/team/app.git",
                "--project-path", "team/app");
        assertThat(String.join(" ", plan.command())).doesNotContain("secret-token");
    }

    @Test
    void scalarArgumentsRejectStructuredValues() {
        ScriptRegistry registry = initializedRegistry();

        assertThatThrownBy(() -> registry.createLaunchPlan(
                TerminalTaskType.SCAN_LOCAL_DOC,
                Map.of(
                        "project", List.of("42"),
                        "branch", "main",
                        "username", "alice")))
                .isInstanceOf(TerminalTaskException.class)
                .hasMessage("project must be a scalar value");
    }

    private ScriptRegistry initializedRegistry() {
        ScriptRegistry registry = new ScriptRegistry();
        ReflectionTestUtils.setField(registry, "shell", "bash");
        ReflectionTestUtils.setField(registry, "configuredScriptDir", tempDir.toString());
        registry.initialize();
        return registry;
    }
}
