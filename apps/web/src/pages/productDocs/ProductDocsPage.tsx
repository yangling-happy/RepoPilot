import type { TerminalClient } from "../../../../terminal/src";
import { type ReactNode, useCallback, useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { useSearchParams } from "react-router-dom";
import {
  type TerminalConnectionState,
  VirtualTerminalPanel,
} from "../../components/virtualTerminal/VirtualTerminalPanel";
import {
  ApiError,
  cloneRepo,
  queryDocs,
  refreshDoc,
  scanLocalDoc,
  setGitlabToken,
  type CloneRepoResponse,
  type DocMemberDoc,
  type DocQueryItem,
  type DocTypeDoc,
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
  const [docs, setDocs] = useState<DocQueryItem[]>([]);
  const [loadingDocs, setLoadingDocs] = useState(false);
  const [selectedDocKey, setSelectedDocKey] = useState<string | null>(null);
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

  const loadDocs = useCallback(
    async (project: string, docBranch: string) => {
      setLoadingDocs(true);
      try {
        const loadedDocs = await queryDocs({
          project,
          branch: docBranch,
        });
        setDocs(loadedDocs);
        setSelectedDocKey((current) => {
          if (current && loadedDocs.some((doc) => getDocKey(doc) === current)) {
            return current;
          }
          const firstStructuredDoc =
            loadedDocs.find((doc) => doc.structuredDoc) ?? loadedDocs[0];
          return firstStructuredDoc ? getDocKey(firstStructuredDoc) : null;
        });
        return loadedDocs;
      } catch (error) {
        setStatus({
          type: "error",
          text: toErrorMessage(
            error,
            t("pages.documentation.actions.errors.unexpected"),
          ),
        });
        return [];
      } finally {
        setLoadingDocs(false);
      }
    },
    [t],
  );

  const handleRefreshDocs = useCallback(async () => {
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
    setLoadingDocs(true);
    appendTerminal(
      t("pages.documentation.actions.terminal.refreshStarted", {
        project,
        branch: effectiveBranch,
      }),
    );

    try {
      await refreshDoc({ project, branch: effectiveBranch });
      appendTerminal(
        t("pages.documentation.actions.terminal.refreshCompleted"),
      );
      await loadDocs(project, effectiveBranch);
      setStatus({
        type: "success",
        text: t("pages.documentation.actions.success.docRefreshed"),
      });
    } catch (error) {
      appendTerminal(
        t("pages.documentation.actions.terminal.refreshFailed", {
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
      setLoadingDocs(false);
    }
  }, [appendTerminal, branch, lastClone, loadDocs, projectId, t]);

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
      await loadDocs(project, effectiveBranch);
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
  }, [appendTerminal, branch, lastClone, loadDocs, projectId, t]);

  const selectedDoc = useMemo(() => {
    const fallback = docs.find((doc) => doc.structuredDoc) ?? docs[0] ?? null;
    if (!selectedDocKey) {
      return fallback;
    }
    return docs.find((doc) => getDocKey(doc) === selectedDocKey) ?? fallback;
  }, [docs, selectedDocKey]);

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

      <div className="mt-10 grid gap-4 lg:grid-cols-[300px_minmax(0,1fr)]">
        <section className="rounded-2xl border border-neutral-200 bg-white p-4 shadow-sm dark:border-white/10 dark:bg-white/[0.03]">
          <div className="flex items-center justify-between gap-3">
            <h2 className="text-sm font-semibold text-neutral-900 dark:text-neutral-100">
              {t("pages.documentation.structured.title")}
            </h2>
            <button
              type="button"
              onClick={handleRefreshDocs}
              disabled={loadingDocs}
              className="rounded-lg border border-neutral-300 px-2.5 py-1.5 text-xs font-medium text-neutral-700 transition hover:bg-neutral-100 disabled:cursor-not-allowed disabled:opacity-60 dark:border-white/20 dark:text-neutral-200 dark:hover:bg-white/10"
            >
              {loadingDocs
                ? t("pages.documentation.structured.refreshing")
                : t("pages.documentation.structured.refresh")}
            </button>
          </div>

          <div className="mt-4 max-h-[520px] space-y-2 overflow-auto pr-1">
            {docs.length === 0 ? (
              <div className="rounded-xl border border-dashed border-neutral-300 px-3 py-8 text-center text-sm text-neutral-500 dark:border-white/15 dark:text-neutral-400">
                {t("pages.documentation.structured.empty")}
              </div>
            ) : (
              docs.map((doc) => {
                const active = selectedDoc
                  ? getDocKey(doc) === getDocKey(selectedDoc)
                  : false;
                return (
                  <button
                    key={getDocKey(doc)}
                    type="button"
                    onClick={() => setSelectedDocKey(getDocKey(doc))}
                    className={`w-full rounded-xl border px-3 py-3 text-left transition ${
                      active
                        ? "border-neutral-900 bg-neutral-900 text-white dark:border-white dark:bg-white dark:text-black"
                        : "border-neutral-200 bg-neutral-50 text-neutral-800 hover:border-neutral-400 dark:border-white/10 dark:bg-black/20 dark:text-neutral-200 dark:hover:border-white/25"
                    }`}
                  >
                    <span className="block truncate font-mono text-xs">
                      {doc.filePath}
                    </span>
                    <span
                      className={`mt-2 inline-flex rounded-full px-2 py-0.5 text-[11px] font-semibold ${
                        active
                          ? "bg-white/20 text-inherit dark:bg-black/10"
                          : doc.parseStatus === "SUCCESS"
                            ? "bg-emerald-50 text-emerald-700 dark:bg-emerald-400/10 dark:text-emerald-300"
                            : "bg-rose-50 text-rose-700 dark:bg-rose-400/10 dark:text-rose-300"
                      }`}
                    >
                      {doc.parseStatus}
                    </span>
                  </button>
                );
              })
            )}
          </div>
        </section>

        <StructuredDocDetail doc={selectedDoc} />
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

function StructuredDocDetail({ doc }: { doc: DocQueryItem | null }) {
  const { t } = useTranslation();

  if (!doc) {
    return (
      <section className="rounded-2xl border border-neutral-200 bg-white p-6 shadow-sm dark:border-white/10 dark:bg-white/[0.03]">
        <div className="flex min-h-[360px] items-center justify-center rounded-xl border border-dashed border-neutral-300 text-sm text-neutral-500 dark:border-white/15 dark:text-neutral-400">
          {t("pages.documentation.structured.empty")}
        </div>
      </section>
    );
  }

  if (!doc.structuredDoc) {
    return (
      <section className="rounded-2xl border border-neutral-200 bg-white p-6 shadow-sm dark:border-white/10 dark:bg-white/[0.03]">
        <div className="font-mono text-xs text-neutral-500 dark:text-neutral-400">
          {doc.filePath}
        </div>
        <div className="mt-4 rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800 dark:border-amber-400/20 dark:bg-amber-400/10 dark:text-amber-200">
          {doc.parseErrorMsg ||
            t("pages.documentation.structured.parseMissing")}
        </div>
      </section>
    );
  }

  return (
    <section className="rounded-2xl border border-neutral-200 bg-white p-6 shadow-sm dark:border-white/10 dark:bg-white/[0.03]">
      <div className="flex flex-col gap-3 border-b border-neutral-200 pb-4 dark:border-white/10 md:flex-row md:items-start md:justify-between">
        <div className="min-w-0">
          <div className="truncate font-mono text-xs text-neutral-500 dark:text-neutral-400">
            {doc.structuredDoc.sourceFilePath}
          </div>
          <h2 className="mt-2 text-xl font-semibold tracking-tight text-neutral-950 dark:text-neutral-50">
            {doc.filePath}
          </h2>
        </div>
        <div className="shrink-0 rounded-xl border border-neutral-200 px-3 py-2 text-right dark:border-white/10">
          <div className="text-2xl font-semibold text-neutral-950 dark:text-neutral-50">
            {doc.structuredDoc.types.length}
          </div>
          <div className="text-xs text-neutral-500 dark:text-neutral-400">
            {t("pages.documentation.structured.types")}
          </div>
        </div>
      </div>

      <div className="mt-5 space-y-5">
        {doc.structuredDoc.types.map((typeDoc) => (
          <TypeDocView key={`${typeDoc.htmlFile}-${typeDoc.name}`} typeDoc={typeDoc} />
        ))}
      </div>
    </section>
  );
}

function TypeDocView({ typeDoc }: { typeDoc: DocTypeDoc }) {
  const { t } = useTranslation();

  return (
    <article className="rounded-xl border border-neutral-200 bg-neutral-50/70 p-4 dark:border-white/10 dark:bg-black/20">
      <div className="flex flex-col gap-3 md:flex-row md:items-start md:justify-between">
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-2">
            <span className="rounded-full bg-neutral-900 px-2 py-0.5 text-[11px] font-semibold text-white dark:bg-white dark:text-black">
              {typeDoc.kind}
            </span>
            <h3 className="text-lg font-semibold text-neutral-950 dark:text-neutral-50">
              {typeDoc.name}
            </h3>
          </div>
          <div className="mt-1 break-all font-mono text-xs text-neutral-500 dark:text-neutral-400">
            {typeDoc.qualifiedName}
          </div>
        </div>
      </div>

      <p className="mt-4 text-sm leading-6 text-neutral-700 dark:text-neutral-300">
        {typeDoc.description || t("pages.documentation.structured.noDescription")}
      </p>

      {typeDoc.signature ? (
        <pre className="mt-4 overflow-x-auto rounded-lg bg-neutral-950 px-3 py-2 font-mono text-xs text-neutral-50 dark:bg-black">
          {typeDoc.signature}
        </pre>
      ) : null}

      <div className="mt-4 space-y-4">
        <MemberGroup
          title={t("pages.documentation.structured.fields")}
          members={typeDoc.fields}
        />
        <MemberGroup
          title={t("pages.documentation.structured.constructors")}
          members={typeDoc.constructors}
        />
        <MemberGroup
          title={t("pages.documentation.structured.methods")}
          members={typeDoc.methods}
        />
      </div>
    </article>
  );
}

function MemberGroup({
  title,
  members,
}: {
  title: string;
  members: DocMemberDoc[];
}) {
  if (members.length === 0) {
    return null;
  }

  return (
    <section>
      <h4 className="text-xs font-semibold uppercase tracking-normal text-neutral-500 dark:text-neutral-400">
        {title}
      </h4>
      <div className="mt-2 space-y-2">
        {members.map((member) => (
          <MemberDocView key={`${member.kind}-${member.id || member.name}`} member={member} />
        ))}
      </div>
    </section>
  );
}

function MemberDocView({ member }: { member: DocMemberDoc }) {
  const { t } = useTranslation();
  const throwsItems = member.throws ?? [];

  return (
    <div className="rounded-lg border border-neutral-200 bg-white p-3 dark:border-white/10 dark:bg-white/[0.03]">
      <div className="flex flex-wrap items-center gap-2">
        <span className="font-mono text-sm font-semibold text-neutral-950 dark:text-neutral-50">
          {member.name}
        </span>
        <span className="rounded-full bg-neutral-100 px-2 py-0.5 text-[11px] text-neutral-500 dark:bg-white/10 dark:text-neutral-300">
          {member.kind}
        </span>
      </div>

      {member.signature ? (
        <pre className="mt-3 overflow-x-auto rounded-md bg-neutral-950 px-3 py-2 font-mono text-xs text-neutral-50 dark:bg-black">
          {member.signature}
        </pre>
      ) : null}

      <p className="mt-3 text-sm leading-6 text-neutral-700 dark:text-neutral-300">
        {member.description || t("pages.documentation.structured.noDescription")}
      </p>

      {member.parameters.length > 0 ? (
        <DocMetaBlock title={t("pages.documentation.structured.parameters")}>
          {member.parameters.map((parameter) => (
            <div
              key={parameter.name}
              className="grid gap-1 border-t border-neutral-100 py-2 first:border-t-0 dark:border-white/10 md:grid-cols-[160px_minmax(0,1fr)]"
            >
              <div className="font-mono text-xs text-neutral-900 dark:text-neutral-100">
                {parameter.type ? `${parameter.type} ` : ""}
                {parameter.name}
              </div>
              <div className="text-sm text-neutral-600 dark:text-neutral-300">
                {parameter.description}
              </div>
            </div>
          ))}
        </DocMetaBlock>
      ) : null}

      {member.returns ? (
        <DocMetaBlock title={t("pages.documentation.structured.returns")}>
          <div className="grid gap-1 py-2 md:grid-cols-[160px_minmax(0,1fr)]">
            <div className="font-mono text-xs text-neutral-900 dark:text-neutral-100">
              {member.returns.type}
            </div>
            <div className="text-sm text-neutral-600 dark:text-neutral-300">
              {member.returns.description}
            </div>
          </div>
        </DocMetaBlock>
      ) : null}

      {throwsItems.length > 0 ? (
        <DocMetaBlock title={t("pages.documentation.structured.throws")}>
          {throwsItems.map((throwsItem) => (
            <div
              key={`${throwsItem.type}-${throwsItem.description}`}
              className="grid gap-1 border-t border-neutral-100 py-2 first:border-t-0 dark:border-white/10 md:grid-cols-[160px_minmax(0,1fr)]"
            >
              <div className="font-mono text-xs text-neutral-900 dark:text-neutral-100">
                {throwsItem.type}
              </div>
              <div className="text-sm text-neutral-600 dark:text-neutral-300">
                {throwsItem.description}
              </div>
            </div>
          ))}
        </DocMetaBlock>
      ) : null}
    </div>
  );
}

function DocMetaBlock({
  title,
  children,
}: {
  title: string;
  children: ReactNode;
}) {
  return (
    <div className="mt-3 rounded-md border border-neutral-200 bg-neutral-50 px-3 py-2 dark:border-white/10 dark:bg-black/20">
      <div className="text-[11px] font-semibold uppercase tracking-normal text-neutral-500 dark:text-neutral-400">
        {title}
      </div>
      <div className="mt-1">{children}</div>
    </div>
  );
}

function getDocKey(doc: DocQueryItem) {
  return `${doc.filePath}::${doc.commitId}`;
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
