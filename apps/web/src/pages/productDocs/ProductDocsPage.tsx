import type { TerminalClient } from "../../../../terminal/src";
import { useCallback, useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { useSearchParams } from "react-router-dom";
import {
  type TerminalConnectionState,
  VirtualTerminalPanel,
} from "../../components/virtualTerminal/VirtualTerminalPanel";
import {
  ApiError,
  cloneRepo,
  scanLocalDoc,
  setGitlabToken,
  type CloneRepoResponse,
} from "../../services/backendApi";
import {
  getCloneErrorMessage,
  getTerminalUnavailableMessage,
} from "./productDocsMessages";
import { saveClonedRepo } from "../workbench/repoLocalStore";

const TOKEN_STORAGE_KEY = "repopilot.gitlabToken";
const TERMINAL_SESSION_STORAGE_KEY = "repopilot.docs.terminalSessionId";

export function ProductDocsPage() {
  const { i18n, t } = useTranslation();
  const [params] = useSearchParams();
  const repo = params.get("repo");
  const terminalClientRef = useRef<TerminalClient | null>(null);

  const [token, setToken] = useState(() => {
    if (typeof window === "undefined") {
      return "";
    }
    return window.localStorage.getItem(TOKEN_STORAGE_KEY) || "";
  });
  const [projectId, setProjectId] = useState(() => {
    if (!repo) {
      return "";
    }
    return /^\d+$/.test(repo) ? repo : "";
  });
  const [branch, setBranch] = useState("main");
  const [savingToken, setSavingToken] = useState(false);
  const [cloning, setCloning] = useState(false);
  const [scanning, setScanning] = useState(false);
  const [status, setStatus] = useState<{
    type: "success" | "error";
    text: string;
  } | null>(null);
  const [lastClone, setLastClone] = useState<CloneRepoResponse | null>(null);
  const [terminalSessionId] = useState(() => getOrCreateTerminalSessionId());
  const [terminalConnectionState, setTerminalConnectionState] =
    useState<TerminalConnectionState>("connecting");

  const language = i18n.resolvedLanguage ?? i18n.language;

  const bootLines = useMemo(
    () => [
      t("pages.documentation.terminal.line1"),
      t("pages.documentation.terminal.line2"),
    ],
    [t],
  );

  const sections = [
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
  ];

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
    const client = terminalClientRef.current;
    if (!client) {
      return;
    }
    const escaped = line.replace(/'/g, `'"'"'`);
    client.sendStdin(`printf '%s\\r\\n' '${escaped}'\n`);
  }, []);

  const handleSaveToken = useCallback(async () => {
    const trimmedToken = token.trim();
    if (!trimmedToken) {
      setStatus({
        type: "error",
        text: t("pages.documentation.actions.errors.tokenRequired"),
      });
      return;
    }

    setSavingToken(true);
    try {
      await setGitlabToken(trimmedToken);
      if (typeof window !== "undefined") {
        window.localStorage.setItem(TOKEN_STORAGE_KEY, trimmedToken);
      }
      appendTerminal(t("pages.documentation.actions.terminal.tokenSaved"));
      setStatus({
        type: "success",
        text: t("pages.documentation.actions.success.tokenSaved"),
      });
    } catch (error) {
      setStatus({
        type: "error",
        text: toErrorMessage(
          error,
          t("pages.documentation.actions.errors.unexpected"),
        ),
      });
    } finally {
      setSavingToken(false);
    }
  }, [appendTerminal, t, token]);

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

    setCloning(true);
    appendTerminal(
      t("pages.documentation.actions.terminal.cloneStarted", {
        projectId: projectIdNumber,
        branch: effectiveBranch,
      }),
    );

    try {
      if (token.trim()) {
        await setGitlabToken(token.trim());
      }

      const response = await cloneRepo({
        projectId: projectIdNumber,
        branch: effectiveBranch,
        terminalSessionId,
      });

      saveClonedRepo(response);
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
      const cloneErrorMessage = getCloneErrorMessage(
        toErrorMessage(
          error,
          t("pages.documentation.actions.errors.unexpected"),
        ),
        language,
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
    }
  }, [appendTerminal, branch, language, projectId, t, terminalSessionId, token]);

  const handleScanLocal = useCallback(async () => {
    const project =
      projectId.trim() || (lastClone ? String(lastClone.projectId) : "");
    if (!project) {
      setStatus({
        type: "error",
        text: t("pages.documentation.actions.errors.projectIdRequiredForDoc"),
      });
      return;
    }

    const effectiveBranch = branch.trim() || "main";
    setScanning(true);
    appendTerminal(
      t("pages.documentation.actions.terminal.scanStarted", {
        project,
        branch: effectiveBranch,
      }),
    );

    try {
      const result = await scanLocalDoc({ project, branch: effectiveBranch });
      appendTerminal(
        t("pages.documentation.actions.terminal.scanCompleted", {
          scanned: result.scannedFileCount,
          generated: result.generatedFileCount,
          skipped: result.skippedFileCount,
        }),
      );
      setStatus({
        type: "success",
        text: t("pages.documentation.actions.success.scanCompleted", {
          generated: result.generatedFileCount,
        }),
      });
    } catch (error) {
      appendTerminal(
        t("pages.documentation.actions.terminal.scanFailed", {
          message: toErrorMessage(
            error,
            t("pages.documentation.actions.errors.unexpected"),
          ),
        }),
      );
      setStatus({
        type: "error",
        text: toErrorMessage(
          error,
          t("pages.documentation.actions.errors.unexpected"),
        ),
      });
    } finally {
      setScanning(false);
    }
  }, [appendTerminal, branch, lastClone, projectId, t]);

  return (
    <div className="mx-auto max-w-[1200px] pb-20 pt-2 text-neutral-950 dark:text-neutral-50">
      <h1 className="mt-0 text-3xl font-semibold tracking-tight md:text-4xl">
        {t("pages.documentation.title")}
      </h1>

      {repo ? (
        <div className="mt-5 inline-flex max-w-full rounded-full border border-neutral-200 bg-neutral-50 px-4 py-1.5 font-mono text-xs text-neutral-700 dark:border-white/15 dark:bg-white/[0.06] dark:text-neutral-200">
          {t("pages.documentation.contextRepo", { repo })}
        </div>
      ) : null}

      <p
        className={`text-pretty text-base leading-relaxed text-neutral-600 dark:text-neutral-400 ${repo ? "mt-8" : "mt-6"}`}
      >
        {t("pages.documentation.lede")}
      </p>

      <div className="mt-8 rounded-2xl border border-neutral-200 bg-neutral-50/70 p-5 dark:border-white/10 dark:bg-white/[0.04]">
        <h2 className="text-base font-semibold text-neutral-900 dark:text-neutral-100">
          {t("pages.documentation.actions.title")}
        </h2>
        <div className="mt-4 grid gap-3 md:grid-cols-2">
          <label className="flex flex-col gap-1 text-sm text-neutral-600 dark:text-neutral-300">
            {t("pages.documentation.actions.token")}
            <input
              type="password"
              value={token}
              onChange={(event) => setToken(event.target.value)}
              className="rounded-lg border border-neutral-300 bg-white px-3 py-2 text-sm text-neutral-900 outline-none ring-neutral-400 transition focus:ring-2 dark:border-white/15 dark:bg-black/30 dark:text-neutral-100"
            />
          </label>
          <label className="flex flex-col gap-1 text-sm text-neutral-600 dark:text-neutral-300">
            {t("pages.documentation.actions.projectId")}
            <input
              type="number"
              min={1}
              value={projectId}
              onChange={(event) => setProjectId(event.target.value)}
              className="rounded-lg border border-neutral-300 bg-white px-3 py-2 text-sm text-neutral-900 outline-none ring-neutral-400 transition focus:ring-2 dark:border-white/15 dark:bg-black/30 dark:text-neutral-100"
            />
          </label>
          <label className="flex flex-col gap-1 text-sm text-neutral-600 dark:text-neutral-300 md:col-span-2">
            {t("pages.documentation.actions.branch")}
            <input
              value={branch}
              onChange={(event) => setBranch(event.target.value)}
              className="rounded-lg border border-neutral-300 bg-white px-3 py-2 text-sm text-neutral-900 outline-none ring-neutral-400 transition focus:ring-2 dark:border-white/15 dark:bg-black/30 dark:text-neutral-100"
            />
          </label>
        </div>

        <div className="mt-4 flex flex-wrap gap-2">
          <button
            type="button"
            onClick={handleSaveToken}
            disabled={savingToken}
            className="rounded-lg border border-neutral-300 px-3 py-2 text-sm font-medium transition hover:bg-neutral-100 disabled:cursor-not-allowed disabled:opacity-60 dark:border-white/20 dark:hover:bg-white/10"
          >
            {savingToken
              ? t("pages.documentation.actions.savingToken")
              : t("pages.documentation.actions.saveToken")}
          </button>
          <button
            type="button"
            onClick={handleClone}
            disabled={cloning}
            className="rounded-lg bg-neutral-900 px-3 py-2 text-sm font-medium text-white transition hover:bg-neutral-700 disabled:cursor-not-allowed disabled:opacity-60 dark:bg-white dark:text-black dark:hover:bg-neutral-200"
          >
            {cloning
              ? t("pages.documentation.actions.cloning")
              : t("pages.documentation.actions.clone")}
          </button>
          <button
            type="button"
            onClick={handleScanLocal}
            disabled={scanning}
            className="rounded-lg border border-neutral-300 px-3 py-2 text-sm font-medium transition hover:bg-neutral-100 disabled:cursor-not-allowed disabled:opacity-60 dark:border-white/20 dark:hover:bg-white/10"
          >
            {scanning
              ? t("pages.documentation.actions.scanning")
              : t("pages.documentation.actions.scanLocal")}
          </button>
        </div>

        {status ? (
          <p
            className={`mt-3 text-sm ${status.type === "success" ? "text-emerald-600 dark:text-emerald-400" : "text-rose-600 dark:text-rose-400"}`}
          >
            {status.text}
          </p>
        ) : null}
        {terminalConnectionState === "error" ||
        terminalConnectionState === "closed" ? (
          <p className="mt-2 text-sm text-amber-600 dark:text-amber-400">
            {getTerminalUnavailableMessage(language)}
          </p>
        ) : null}
      </div>

      <div className="mt-14">
        <VirtualTerminalPanel
          title={t("pages.documentation.terminal.title")}
          subtitle={t("pages.documentation.terminal.subtitle")}
          bootLines={bootLines}
          sessionId={terminalSessionId}
          onConnectionStatusChange={setTerminalConnectionState}
          onSessionReady={onSessionReady}
        />
      </div>

      <div className="mt-14 space-y-3">
        {sections.map((section) => (
          <div
            key={section.label}
            className="rounded-2xl border border-neutral-200 bg-neutral-50/60 px-5 py-4 dark:border-white/10 dark:bg-white/[0.03]"
          >
            <div className="text-xs font-medium text-neutral-500 dark:text-neutral-400">
              {section.label}
            </div>
            <div className="mt-2 flex flex-col gap-2">
              {section.codes.map((code) => (
                <code
                  key={code}
                  className="block rounded-lg bg-white px-3 py-2 font-mono text-[13px] text-neutral-900 shadow-[inset_0_0_0_1px_rgba(0,0,0,0.06)] dark:bg-black dark:text-neutral-100 dark:shadow-[inset_0_0_0_1px_rgba(255,255,255,0.08)]"
                >
                  {code}
                </code>
              ))}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

function getOrCreateTerminalSessionId() {
  if (typeof window === "undefined") {
    return createSessionIdFallback();
  }

  const existing = window.localStorage.getItem(TERMINAL_SESSION_STORAGE_KEY);
  if (existing) {
    return existing;
  }

  const created = createSessionIdFallback();
  window.localStorage.setItem(TERMINAL_SESSION_STORAGE_KEY, created);
  return created;
}

function createSessionIdFallback() {
  if (
    typeof crypto !== "undefined" &&
    typeof crypto.randomUUID === "function"
  ) {
    return crypto.randomUUID();
  }
  return `${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

function toErrorMessage(error: unknown, fallback: string): string {
  if (error instanceof ApiError && error.message) {
    return error.message;
  }
  if (error instanceof Error && error.message) {
    return error.message;
  }
  return fallback;
}
