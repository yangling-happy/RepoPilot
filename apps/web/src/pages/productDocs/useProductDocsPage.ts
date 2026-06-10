import type { TerminalClient } from "../../../../terminal/src";
import { useCallback, useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { useSearchParams } from "react-router-dom";
import type { TerminalConnectionState } from "../../components/virtualTerminal/VirtualTerminalPanel";
import { mapCloneErrorMessage } from "../../i18n/backendErrors";
import {
  isMockMode,
  isMockRepo,
  MOCK_PROJECT_ID,
  simulateMockClone,
} from "../../mocks/docMockData";
import { useAuth } from "../../hooks/useAuth";
import {
  cloneRepo,
  type CloneRepoResponse,
} from "../../services/backendApi";
import { toErrorMessage } from "../../utils/errorMessage";
import { getOrCreateTerminalSessionId } from "../../utils/terminalSession";
import { saveClonedRepo, loadClonedRepos, loadRemoteRepos } from "../workbench/repoLocalStore";

const TERMINAL_SESSION_STORAGE_KEY = "repopilot.docs.terminalSessionId";

export type ProductDocsStatus = {
  type: "success" | "error";
  text: string;
} | null;

export function useProductDocsPage() {
  const { t } = useTranslation();
  const { user } = useAuth();
  const username = user?.username;
  const [params] = useSearchParams();
  const repo = params.get("repo");
  const terminalClientRef = useRef<TerminalClient | null>(null);

  const [projectId, setProjectId] = useState(() => {
    if (isMockRepo(repo)) {
      return String(MOCK_PROJECT_ID);
    }
    if (!repo) {
      return "";
    }
    return /^\d+$/.test(repo) ? repo : "";
  });
  const [branch, setBranch] = useState("main");
  const [cloning, setCloning] = useState(false);
  const [status, setStatus] = useState<ProductDocsStatus>(null);
  const [lastClone, setLastClone] = useState<CloneRepoResponse | null>(null);
  const [terminalSessionId] = useState(() =>
    getOrCreateTerminalSessionId(TERMINAL_SESSION_STORAGE_KEY),
  );
  const [terminalConnectionState, setTerminalConnectionState] =
    useState<TerminalConnectionState>("connecting");
  const [terminalOpen, setTerminalOpen] = useState(false);
  const [terminalBusy, setTerminalBusy] = useState(false);

  const bootLines = useMemo(
    () => [
      t("pages.documentation.terminal.line1"),
      t("pages.documentation.terminal.line2"),
    ],
    [t],
  );

  const sections = useMemo(
    () => [
      {
        label: t("pages.documentation.items.webhook"),
        codes: ["POST /api/repo/clone", "POST /api/doc/scan-local"],
      },
      {
        label: t("pages.documentation.items.query"),
        codes: ["GET /api/doc/query"],
      },
      {
        label: t("pages.documentation.items.session"),
        codes: ["POST /api/session/setGitlabToken"],
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

  const handleClone = useCallback(async () => {
    const projectIdNumber = Number(projectId);
    if (!Number.isInteger(projectIdNumber) || projectIdNumber <= 0) {
      setStatus({
        type: "error",
        text: t("pages.documentation.actions.errors.projectIdInvalid"),
      });
      return;
    }

    const effectiveBranch = branch.trim() || "main";

    setTerminalOpen(true);
    terminalClientRef.current?.clear();
    setTerminalBusy(true);
    setCloning(true);
    appendTerminal(
      t("pages.documentation.actions.terminal.cloneStarted", {
        projectId: projectIdNumber,
        branch: effectiveBranch,
      }),
    );

    try {
      const response = isMockMode(repo, projectId)
        ? await simulateMockClone(appendTerminal)
        : await cloneRepo({
            projectId: projectIdNumber,
            branch: effectiveBranch,
            terminalSessionId,
          });

      saveClonedRepo(response, username);
      setLastClone(response);
      appendTerminal(
        t("pages.documentation.actions.terminal.cloneCompleted", {
          localPath: response.localPath,
        }),
      );
      setStatus({
        type: "success",
        text: t("pages.documentation.actions.success.cloneCompleted", {
          projectPath: response.projectPath,
        }),
      });
    } catch (error) {
      const cloneErrorMessage = mapCloneErrorMessage(
        toErrorMessage(
          error,
          t("pages.documentation.actions.errors.unexpected"),
        ),
        t,
      );
      appendTerminal(
        t("pages.documentation.actions.terminal.cloneFailed", {
          message: cloneErrorMessage,
        }),
      );
      setStatus({
        type: "error",
        text: cloneErrorMessage,
      });
    } finally {
      setCloning(false);
      setTerminalBusy(false);
    }
  }, [appendTerminal, branch, projectId, repo, t, terminalSessionId, username]);

  const viewDocsUrl = useMemo(() => {
    const effectiveBranch = branch.trim() || "main";
    const clonedRepos = loadClonedRepos(username);
    const remoteRepos = loadRemoteRepos(username);
    const allRepos = [...clonedRepos, ...remoteRepos];

    if (isMockMode(repo, projectId) || isMockRepo(repo)) {
      return `/documentation/view?repo=mock&name=MockProject&branch=${encodeURIComponent(effectiveBranch)}`;
    }

    const project =
      projectId.trim() || (lastClone ? String(lastClone.projectId) : "");
    if (!project) return null;

    const foundRepo = allRepos.find((r) => r.id === project);
    const name = foundRepo ? foundRepo.name : project;
    return `/documentation/view?repo=${encodeURIComponent(project)}&name=${encodeURIComponent(name)}&branch=${encodeURIComponent(effectiveBranch)}`;
  }, [branch, lastClone, projectId, repo, username]);

  const showTerminalUnavailable =
    terminalOpen &&
    (terminalConnectionState === "error" ||
      terminalConnectionState === "closed");

  const mockMode = isMockMode(repo, projectId);

  return {
    mockMode,
    repo,
    projectId,
    setProjectId,
    branch,
    setBranch,
    cloning,
    status,
    bootLines,
    sections,
    terminalSessionId,
    terminalOpen,
    terminalBusy,
    showTerminalUnavailable,
    viewDocsUrl,
    handleClone,
    onSessionReady,
    setTerminalConnectionState,
    setTerminalOpen,
  };
}
