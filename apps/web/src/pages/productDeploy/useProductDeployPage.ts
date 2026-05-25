import type { TerminalClient } from "../../../../terminal/src";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { useSearchParams } from "react-router-dom";
import type { TerminalConnectionState } from "../../components/virtualTerminal/VirtualTerminalPanel";
import { useSessionState } from "../../hooks/useSessionState";
import {
  cancelDeploy,
  getDeployTask,
  setupSshKey,
  triggerDeploy,
  type DeployTask,
} from "../../services/backendApi";
import { toErrorMessage } from "../../utils/errorMessage";
import { getOrCreateTerminalSessionId } from "../../utils/terminalSession";

const TERMINAL_SESSION_STORAGE_KEY = "repopilot.deploy.terminalSessionId";

export type DeployStatusMessage = {
  type: "success" | "error" | "warning";
  text: string;
} | null;

export function useProductDeployPage() {
  const { t } = useTranslation();
  const [params] = useSearchParams();
  const repo = params.get("repo");
  const terminalClientRef = useRef<TerminalClient | null>(null);

  const repoKey = repo || "default";
  const [projectId, setProjectId] = useSessionState<string>(
    `deploy.${repoKey}.projectId`,
    () => {
      if (!repo) {
        return "";
      }
      return /^\d+$/.test(repo) ? repo : "";
    },
  );
  const [branch, setBranch] = useSessionState(`deploy.${repoKey}.branch`, "main");
  const [artifactPath, setArtifactPath] = useSessionState(
    `deploy.${repoKey}.artifactPath`,
    "",
  );
  const [buildEnabled, setBuildEnabled] = useSessionState(
    `deploy.${repoKey}.buildEnabled`,
    true,
  );
  const [deployHost, setDeployHost] = useSessionState(
    `deploy.${repoKey}.deployHost`,
    "",
  );
  const [deployPort, setDeployPort] = useSessionState(
    `deploy.${repoKey}.deployPort`,
    "22",
  );
  const [deployUser, setDeployUser] = useSessionState(
    `deploy.${repoKey}.deployUser`,
    "",
  );
  const [deployTargetDir, setDeployTargetDir] = useSessionState(
    `deploy.${repoKey}.deployTargetDir`,
    "",
  );
  const [sshPassword, setSshPassword] = useState("");
  const [sshKeySettingUp, setSshKeySettingUp] = useState(false);
  const [deploying, setDeploying] = useState(false);
  const [status, setStatus] = useState<DeployStatusMessage>(null);
  const [activeTaskId, setActiveTaskId] = useState<string | null>(null);
  const [activeTask, setActiveTask] = useState<DeployTask | null>(null);
  const [terminalSessionId] = useState(() =>
    getOrCreateTerminalSessionId(TERMINAL_SESSION_STORAGE_KEY),
  );
  const [terminalConnectionState, setTerminalConnectionState] =
    useState<TerminalConnectionState>("connecting");
  const [terminalOpen, setTerminalOpen] = useState(false);
  const [terminalBusy, setTerminalBusy] = useState(false);

  const bootLines = useMemo(
    () => [
      t("pages.deploy.terminal.line1"),
      t("pages.deploy.terminal.line2"),
    ],
    [t],
  );

  const sections = useMemo(
    () => [
      {
        label: t("pages.deploy.items.trigger"),
        codes: ["POST /api/deploy/trigger"],
      },
      {
        label: t("pages.deploy.items.queryLog"),
        codes: ["GET /api/deploy/task", "GET /api/deploy/log"],
      },
      {
        label: t("pages.deploy.items.cancel"),
        codes: ["POST /api/deploy/cancel"],
      },
    ],
    [t],
  );

  const onSessionReady = useCallback(
    ({ sessionId, client }: { sessionId: string; client: TerminalClient }) => {
      terminalClientRef.current = client;
      if (typeof window !== "undefined") {
        window.localStorage.setItem(TERMINAL_SESSION_STORAGE_KEY, sessionId);
      }
    },
    [],
  );

  const appendTerminal = useCallback((line: string) => {
    terminalClientRef.current?.writeln(line);
  }, []);

  const handleTriggerDeploy = useCallback(async () => {
    const trimmedProject = projectId.trim();
    if (!/^\d+$/.test(trimmedProject)) {
      setStatus({
        type: "error",
        text: t("pages.deploy.actions.errors.projectIdInvalid"),
      });
      return;
    }

    const effectiveBranch = branch.trim() || "main";

    setTerminalOpen(true);
    terminalClientRef.current?.clear();
    setTerminalBusy(true);
    setDeploying(true);
    setStatus(null);
    appendTerminal(
      t("pages.deploy.actions.terminal.triggered", {
        project: trimmedProject,
        branch: effectiveBranch,
      }),
    );

    try {
      const response = await triggerDeploy({
        project: trimmedProject,
        branch: effectiveBranch,
        terminalSessionId,
        build: buildEnabled,
        artifactPath: artifactPath.trim() || undefined,
        deployHost: deployHost.trim() || undefined,
        deployPort: deployPort.trim() ? Number(deployPort.trim()) : undefined,
        deployUser: deployUser.trim() || undefined,
        deployTargetDir: deployTargetDir.trim() || undefined,
      });
      setActiveTaskId(response.deployTaskId);
      setStatus({
        type: "success",
        text: t("pages.deploy.actions.success.triggered", {
          taskId: response.deployTaskId,
        }),
      });
      appendTerminal(
        t("pages.deploy.actions.terminal.accepted", {
          taskId: response.deployTaskId,
        }),
      );
    } catch (error) {
      const message = toErrorMessage(
        error,
        t("pages.deploy.actions.errors.unexpected"),
      );
      setStatus({ type: "error", text: message });
      appendTerminal(
        t("pages.deploy.actions.terminal.failed", {
          message,
        }),
      );
      setTerminalBusy(false);
      setDeploying(false);
    }
  }, [
    appendTerminal,
    artifactPath,
    branch,
    buildEnabled,
    deployHost,
    deployPort,
    deployUser,
    deployTargetDir,
    projectId,
    t,
    terminalSessionId,
  ]);

  const handleSetupSshKey = useCallback(async () => {
    const trimmedHost = deployHost.trim();
    const trimmedUser = deployUser.trim();
    const trimmedPassword = sshPassword.trim();

    if (!trimmedHost || !trimmedUser || !trimmedPassword) {
      setStatus({
        type: "error",
        text: t("pages.deploy.actions.errors.sshPasswordRequired"),
      });
      return;
    }

    setTerminalOpen(true);
    terminalClientRef.current?.clear();
    setSshKeySettingUp(true);
    setStatus(null);
    appendTerminal(
      t("pages.deploy.actions.terminal.triggered", {
        project: "ssh-key-setup",
        branch: trimmedHost,
      }),
    );
    appendTerminal(
      t("pages.deploy.actions.terminal.sshSetupStarted", {
        user: trimmedUser,
        host: trimmedHost,
      }),
    );

    try {
      await setupSshKey({
        host: trimmedHost,
        port: deployPort.trim() ? Number(deployPort.trim()) : undefined,
        user: trimmedUser,
        password: trimmedPassword,
      });
      setStatus({
        type: "success",
        text: t("pages.deploy.actions.success.sshKeySetup"),
      });
      appendTerminal(t("pages.deploy.actions.terminal.sshSetupSucceeded"));
      setSshPassword("");
    } catch (error) {
      const message = toErrorMessage(
        error,
        t("pages.deploy.actions.errors.unexpected"),
      );
      setStatus({
        type: "error",
        text: t("pages.deploy.actions.errors.sshKeySetupFailed", { message }),
      });
      appendTerminal(
        t("pages.deploy.actions.terminal.sshSetupFailed", { message }),
      );
    } finally {
      setSshKeySettingUp(false);
    }
  }, [appendTerminal, deployHost, deployPort, deployUser, sshPassword, t]);

  const handleCancel = useCallback(async () => {
    if (!activeTaskId) {
      setStatus({
        type: "warning",
        text: t("pages.deploy.actions.errors.noActiveTask"),
      });
      return;
    }
    try {
      const task = await cancelDeploy(activeTaskId, terminalSessionId);
      setActiveTask(task);
      setStatus({
        type: "success",
        text: t("pages.deploy.actions.success.cancelled"),
      });
      setDeploying(false);
      setTerminalBusy(false);
    } catch (error) {
      setStatus({
        type: "error",
        text: toErrorMessage(
          error,
          t("pages.deploy.actions.errors.unexpected"),
        ),
      });
    }
  }, [activeTaskId, terminalSessionId, t]);

  useEffect(() => {
    if (!activeTaskId) {
      return undefined;
    }

    let cancelled = false;

    const poll = async () => {
      try {
        const task = await getDeployTask(activeTaskId);
        if (cancelled) {
          return;
        }
        setActiveTask(task);
        if (task.runStatus !== "RUNNING") {
          const messageType =
            task.runStatus === "SUCCESS"
              ? "success"
              : task.runStatus === "CANCELLED"
                ? "warning"
                : "error";
          setDeploying(false);
          setTerminalBusy(false);
          setStatus({
            type: messageType,
            text: t("pages.deploy.actions.status", {
              status: task.runStatus,
            }),
          });
        }
      } catch (error) {
        if (cancelled) {
          return;
        }
        setStatus({
          type: "error",
          text: toErrorMessage(
            error,
            t("pages.deploy.actions.errors.unexpected"),
          ),
        });
      }
    };

    poll();
    const timer = window.setInterval(poll, 3000);
    return () => {
      cancelled = true;
      window.clearInterval(timer);
    };
  }, [activeTaskId, t]);

  const showTerminalUnavailable =
    terminalOpen &&
    (terminalConnectionState === "error" ||
      terminalConnectionState === "closed");

  return {
    repo,
    projectId,
    setProjectId,
    branch,
    setBranch,
    artifactPath,
    setArtifactPath,
    buildEnabled,
    setBuildEnabled,
    deployHost,
    setDeployHost,
    deployPort,
    setDeployPort,
    deployUser,
    setDeployUser,
    deployTargetDir,
    setDeployTargetDir,
    sshPassword,
    setSshPassword,
    sshKeySettingUp,
    deploying,
    status,
    activeTaskId,
    activeTask,
    bootLines,
    sections,
    terminalSessionId,
    terminalOpen,
    terminalBusy,
    showTerminalUnavailable,
    handleTriggerDeploy,
    handleSetupSshKey,
    handleCancel,
    onSessionReady,
    setTerminalConnectionState,
    setTerminalOpen,
  };
}
