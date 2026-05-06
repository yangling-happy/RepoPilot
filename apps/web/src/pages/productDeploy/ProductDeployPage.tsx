import type { TerminalClient } from "../../../../terminal/src";
import {
  type ReactNode,
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react";
import { useTranslation } from "react-i18next";
import { useSearchParams } from "react-router-dom";
import {
  type TerminalConnectionState,
  VirtualTerminalPanel,
} from "../../components/virtualTerminal/VirtualTerminalPanel";
import {
  ApiError,
  cancelDeploy,
  getDeployTask,
  triggerDeploy,
  type DeployTask,
} from "../../services/backendApi";
import { getTerminalUnavailableMessage } from "../productDocs/productDocsMessages";

const TERMINAL_SESSION_STORAGE_KEY = "repopilot.deploy.terminalSessionId";

type StatusMessage = {
  type: "success" | "error" | "warning";
  text: string;
} | null;

export function ProductDeployPage() {
  const { i18n, t } = useTranslation();
  const [params] = useSearchParams();
  const repo = params.get("repo");
  const terminalClientRef = useRef<TerminalClient | null>(null);
  const language = i18n.resolvedLanguage ?? i18n.language;

  const [projectId, setProjectId] = useState(() => {
    if (!repo) {
      return "";
    }
    return /^\d+$/.test(repo) ? repo : "";
  });
  const [branch, setBranch] = useState("main");
  const [environment, setEnvironment] = useState("staging");
  const [artifactPath, setArtifactPath] = useState("");
  const [buildEnabled, setBuildEnabled] = useState(true);
  const [deploying, setDeploying] = useState(false);
  const [status, setStatus] = useState<StatusMessage>(null);
  const [activeTaskId, setActiveTaskId] = useState<string | null>(null);
  const [activeTask, setActiveTask] = useState<DeployTask | null>(null);
  const [terminalSessionId] = useState(() => getOrCreateTerminalSessionId());
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

  const sections = [
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
    client.writeln(line);
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
    const effectiveEnvironment = environment.trim();
    if (!effectiveEnvironment) {
      setStatus({
        type: "error",
        text: t("pages.deploy.actions.errors.environmentRequired"),
      });
      return;
    }

    setTerminalOpen(true);
    terminalClientRef.current?.clear();
    setTerminalBusy(true);
    setDeploying(true);
    setStatus(null);
    appendTerminal(
      t("pages.deploy.actions.terminal.triggered", {
        project: trimmedProject,
        branch: effectiveBranch,
        environment: effectiveEnvironment,
      }),
    );

    try {
      const response = await triggerDeploy({
        project: trimmedProject,
        branch: effectiveBranch,
        environment: effectiveEnvironment,
        terminalSessionId,
        build: buildEnabled,
        artifactPath: artifactPath.trim() || undefined,
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
    environment,
    projectId,
    t,
    terminalSessionId,
  ]);

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
      setStatus({ type: "success", text: t("pages.deploy.actions.success.cancelled") });
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

  return (
    <div className="mx-auto max-w-[1200px] pb-20 pt-2 text-neutral-950 dark:text-neutral-50">
      <h1 className="mt-0 text-3xl font-semibold tracking-tight md:text-4xl">
        {t("pages.deploy.title")}
      </h1>

      {repo ? (
        <div className="mt-5 inline-flex max-w-full rounded-full border border-neutral-200 bg-neutral-50 px-4 py-1.5 font-mono text-xs text-neutral-700 dark:border-white/15 dark:bg-white/[0.06] dark:text-neutral-200">
          {t("pages.deploy.contextRepo", { repo })}
        </div>
      ) : null}

      <p
        className={`text-pretty text-base leading-relaxed text-neutral-600 dark:text-neutral-400 ${repo ? "mt-8" : "mt-6"}`}
      >
        {t("pages.deploy.lede")}
      </p>

      <div className="mt-8 rounded-2xl border border-neutral-200 bg-neutral-50/70 p-5 dark:border-white/10 dark:bg-white/[0.04]">
        <h2 className="text-base font-semibold text-neutral-900 dark:text-neutral-100">
          {t("pages.deploy.actions.title")}
        </h2>
        <div className="mt-4 grid gap-3 md:grid-cols-2">
          <label className="flex flex-col gap-1 text-sm text-neutral-600 dark:text-neutral-300">
            {t("pages.deploy.actions.projectId")}
            <input
              type="number"
              min={1}
              value={projectId}
              onChange={(event) => setProjectId(event.target.value)}
              className="rounded-lg border border-neutral-300 bg-white px-3 py-2 text-sm text-neutral-900 outline-none ring-neutral-400 transition focus:ring-2 dark:border-white/15 dark:bg-black/30 dark:text-neutral-100"
            />
          </label>
          <label className="flex flex-col gap-1 text-sm text-neutral-600 dark:text-neutral-300">
            {t("pages.deploy.actions.environment")}
            <select
              value={environment}
              onChange={(event) => setEnvironment(event.target.value)}
              className="rounded-lg border border-neutral-300 bg-white px-3 py-2 text-sm text-neutral-900 outline-none ring-neutral-400 transition focus:ring-2 dark:border-white/15 dark:bg-black/30 dark:text-neutral-100"
            >
              <option value="staging">
                {t("pages.deploy.actions.envOptions.staging")}
              </option>
              <option value="production">
                {t("pages.deploy.actions.envOptions.production")}
              </option>
            </select>
          </label>
          <label className="flex flex-col gap-1 text-sm text-neutral-600 dark:text-neutral-300 md:col-span-2">
            {t("pages.deploy.actions.branch")}
            <input
              value={branch}
              onChange={(event) => setBranch(event.target.value)}
              className="rounded-lg border border-neutral-300 bg-white px-3 py-2 text-sm text-neutral-900 outline-none ring-neutral-400 transition focus:ring-2 dark:border-white/15 dark:bg-black/30 dark:text-neutral-100"
            />
          </label>
          <label className="flex flex-col gap-1 text-sm text-neutral-600 dark:text-neutral-300 md:col-span-2">
            {t("pages.deploy.actions.artifactPath")}
            <input
              value={artifactPath}
              onChange={(event) => setArtifactPath(event.target.value)}
              placeholder={t("pages.deploy.actions.artifactPlaceholder")}
              className="rounded-lg border border-neutral-300 bg-white px-3 py-2 text-sm text-neutral-900 outline-none ring-neutral-400 transition focus:ring-2 dark:border-white/15 dark:bg-black/30 dark:text-neutral-100"
            />
          </label>
          <label className="flex items-center gap-2 text-sm text-neutral-600 dark:text-neutral-300">
            <input
              type="checkbox"
              checked={buildEnabled}
              onChange={(event) => setBuildEnabled(event.target.checked)}
              className="h-4 w-4"
            />
            {t("pages.deploy.actions.build")}
          </label>
        </div>

        <div className="mt-4 flex flex-wrap gap-2">
          <button
            type="button"
            onClick={handleTriggerDeploy}
            disabled={deploying}
            className="rounded-lg bg-neutral-900 px-3 py-2 text-sm font-medium text-white transition hover:bg-neutral-700 disabled:cursor-not-allowed disabled:opacity-60 dark:bg-white dark:text-black dark:hover:bg-neutral-200"
          >
            {deploying
              ? t("pages.deploy.actions.triggering")
              : t("pages.deploy.actions.trigger")}
          </button>
          <button
            type="button"
            onClick={handleCancel}
            disabled={!deploying}
            className="rounded-lg border border-neutral-300 px-3 py-2 text-sm font-medium transition hover:bg-neutral-100 disabled:cursor-not-allowed disabled:opacity-60 dark:border-white/20 dark:hover:bg-white/10"
          >
            {t("pages.deploy.actions.cancel")}
          </button>
        </div>

        {status ? (
          <p
            className={`mt-3 text-sm ${status.type === "success"
                ? "text-emerald-600 dark:text-emerald-400"
                : status.type === "warning"
                  ? "text-amber-600 dark:text-amber-400"
                  : "text-rose-600 dark:text-rose-400"
              }`}
          >
            {status.text}
          </p>
        ) : null}
        {terminalOpen &&
          (terminalConnectionState === "error" ||
            terminalConnectionState === "closed") ? (
          <p className="mt-2 text-sm text-amber-600 dark:text-amber-400">
            {getTerminalUnavailableMessage(language)}
          </p>
        ) : null}
      </div>

      <div className="mt-10 grid gap-4 lg:grid-cols-[340px_minmax(0,1fr)]">
        <section className="rounded-2xl border border-neutral-200 bg-white p-4 shadow-sm dark:border-white/10 dark:bg-white/[0.03]">
          <div className="flex items-center justify-between gap-3">
            <h2 className="text-sm font-semibold text-neutral-900 dark:text-neutral-100">
              {t("pages.deploy.summary.title")}
            </h2>
          </div>

          <div className="mt-4 space-y-3 text-sm text-neutral-600 dark:text-neutral-300">
            <SummaryRow label={t("pages.deploy.summary.taskId")}>
              {activeTask?.deployTaskId || activeTaskId || "-"}
            </SummaryRow>
            <SummaryRow label={t("pages.deploy.summary.status")}>
              {activeTask?.runStatus || "-"}
            </SummaryRow>
            <SummaryRow label={t("pages.deploy.summary.commitId")}>
              {activeTask?.commitId || "-"}
            </SummaryRow>
          </div>
        </section>

        <section className="rounded-2xl border border-neutral-200 bg-white p-4 shadow-sm dark:border-white/10 dark:bg-white/[0.03]">
          <div className="text-xs font-semibold uppercase tracking-wide text-neutral-500 dark:text-neutral-400">
            {t("pages.deploy.terminal.title")}
          </div>
          <p className="mt-2 text-sm text-neutral-600 dark:text-neutral-400">
            {t("pages.deploy.terminal.subtitle")}
          </p>
        </section>
      </div>

      <VirtualTerminalPanel
        title={t("pages.deploy.terminal.title")}
        subtitle={t("pages.deploy.terminal.subtitle")}
        bootLines={bootLines}
        sessionId={terminalSessionId}
        variant="floating"
        open={terminalOpen}
        dismissible={!terminalBusy}
        onRequestClose={() => setTerminalOpen(false)}
        onConnectionStatusChange={setTerminalConnectionState}
        onSessionReady={onSessionReady}
      />

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

function SummaryRow({
  label,
  children,
}: {
  label: string;
  children: ReactNode;
}) {
  return (
    <div className="flex items-center justify-between gap-3">
      <span className="text-xs font-semibold uppercase tracking-wide text-neutral-500 dark:text-neutral-400">
        {label}
      </span>
      <span className="font-mono text-xs text-neutral-900 dark:text-neutral-100">
        {children}
      </span>
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
