import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { useSearchParams } from "react-router-dom";
import type { TerminalClient } from "../../../../terminal/src";
import {
  type TerminalConnectionState,
  VirtualTerminalPanel,
} from "../../components/virtualTerminal/VirtualTerminalPanel";
import { queryDocs, scanLocalDoc, type DocQueryItem } from "../../services/backendApi";
import {
  StructuredDocDetail,
  getDocKey,
  getDefaultDocSection,
} from "./DocViewComponents";

type PackageGroup = {
  name: string;
  packagePath: string;
  docs: DocQueryItem[];
};

const SOURCE_PREFIX_RE = /^src\/main\/(?:java|kotlin)\//;

function extractPackageSegments(filePath: string): { segments: string[]; className: string } {
  const withoutPrefix = filePath.replace(SOURCE_PREFIX_RE, "");
  const parts = withoutPrefix.split("/");
  const fileName = parts.pop() ?? withoutPrefix;
  const className = fileName.replace(/\.\w+$/, "");
  return { segments: parts, className };
}

function getClassName(doc: DocQueryItem): string {
  if (doc.structuredDoc?.types?.length) {
    return doc.structuredDoc.types[0].name;
  }
  const { className } = extractPackageSegments(doc.filePath);
  return className;
}

function getPackageGroup(doc: DocQueryItem): string {
  const { segments } = extractPackageSegments(doc.filePath);
  // Use last 2 segments as group, e.g. "service.docgen" or "dto"
  if (segments.length >= 2) {
    return segments.slice(-2).join(".");
  }
  return segments.join(".") || "(root)";
}

function buildGroups(docs: DocQueryItem[]): PackageGroup[] {
  const groupMap = new Map<string, DocQueryItem[]>();

  for (const doc of docs) {
    const group = getPackageGroup(doc);
    if (!groupMap.has(group)) {
      groupMap.set(group, []);
    }
    groupMap.get(group)!.push(doc);
  }

  return Array.from(groupMap.entries())
    .map(([name, docs]: [string, DocQueryItem[]]) => ({
      name,
      packagePath: name,
      docs,
    }))
    .sort((a: PackageGroup, b: PackageGroup) => a.name.localeCompare(b.name));
}

function scrollToAnchor(anchorId: string) {
  const el = document.getElementById(anchorId);
  if (el) {
    el.scrollIntoView({ behavior: "smooth", block: "start" });
  }
}

function getAnchorId(doc: DocQueryItem): string {
  return `doc-${getDocKey(doc).replace(/[^a-zA-Z0-9]/g, "-")}`;
}

export function DocViewPage() {
  const { t } = useTranslation();
  const [params] = useSearchParams();
  const repo = params.get("repo");
  const branchParam = params.get("branch") || "main";

  const [docs, setDocs] = useState<DocQueryItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [expandedGroups, setExpandedGroups] = useState<Set<string>>(new Set());
  const [activeDocKey, setActiveDocKey] = useState<string | null>(null);
  const [searchQuery, setSearchQuery] = useState("");
  const [scanning, setScanning] = useState(false);
  const [terminalOpen, setTerminalOpen] = useState(false);
  const [terminalBusy, setTerminalBusy] = useState(false);
  const [terminalConnectionState, setTerminalConnectionState] =
    useState<TerminalConnectionState>("connecting");
  const terminalClientRef = useRef<TerminalClient | null>(null);
  const terminalSessionIdRef = useRef<string>(createSessionId());
  const contentRef = useRef<HTMLDivElement>(null);

  const loadDocs = useCallback(async () => {
    if (!repo) return;
    setLoading(true);
    setError(null);
    try {
      const result = await queryDocs({ project: repo, branch: branchParam });
      setDocs(result);
    } catch (err) {
      setError(
        err instanceof Error ? err.message : t("pages.docView.loadError"),
      );
    } finally {
      setLoading(false);
    }
  }, [repo, branchParam, t]);

  useEffect(() => {
    loadDocs();
  }, [loadDocs]);

  const bootLines = useMemo(
    () => [
      t("pages.docView.terminal.ready", "Terminal ready."),
      t("pages.docView.terminal.hint", 'Click "Scan & Generate Docs" to start.'),
    ],
    [t],
  );

  const onSessionReady = useCallback(
    ({ client }: { sessionId: string; client: TerminalClient }) => {
      terminalClientRef.current = client;
    },
    [],
  );

  const appendTerminal = useCallback((line: string) => {
    terminalClientRef.current?.writeln(line);
  }, []);

  const handleScan = useCallback(async () => {
    if (!repo || scanning) return;
    setScanning(true);
    setTerminalOpen(true);
    setTerminalBusy(true);
    terminalClientRef.current?.clear();
    appendTerminal(
      t("pages.docView.terminal.scanStarted", { project: repo, branch: branchParam }),
    );

    const startTime = Date.now();
    try {
      const result = await scanLocalDoc({
        project: repo,
        branch: branchParam,
        terminalSessionId: terminalSessionIdRef.current,
      });

      const elapsed = Date.now() - startTime;
      appendTerminal("");
      appendTerminal("────────────────────────────────────────");
      appendTerminal(
        t("pages.docView.terminal.scanCompleted", {
          scanned: result.scannedFileCount,
          generated: result.generatedFileCount,
          skipped: result.skippedFileCount,
          failed: result.failedFileCount,
        }),
      );
      appendTerminal(
        `  total:          ${result.totalDurationMs ?? elapsed} ms`,
      );
      appendTerminal(
        `  fileListing:    ${result.fileListingDurationMs ?? "-"} ms`,
      );
      appendTerminal(
        `  docGeneration:  ${result.docGenerationDurationMs ?? "-"} ms`,
      );
      appendTerminal(
        `  dbOps:          ${result.dbOperationDurationMs ?? "-"} ms`,
      );
      appendTerminal("────────────────────────────────────────");

      await loadDocs();
    } catch (err) {
      appendTerminal("");
      appendTerminal(
        `[ERROR] ${err instanceof Error ? err.message : String(err)}`,
      );
    } finally {
      setScanning(false);
      setTerminalBusy(false);
    }
  }, [repo, branchParam, scanning, loadDocs, t, appendTerminal]);

  const groups = useMemo(() => buildGroups(docs), [docs]);

  const filteredGroups = useMemo(() => {
    if (!searchQuery.trim()) return groups;
    const lower = searchQuery.toLowerCase();
    return groups
      .map((group: PackageGroup) => ({
        ...group,
        docs: group.docs.filter(
          (doc: DocQueryItem) =>
            getClassName(doc).toLowerCase().includes(lower) ||
            group.name.toLowerCase().includes(lower) ||
            doc.filePath.toLowerCase().includes(lower),
        ),
      }))
      .filter((group: PackageGroup) => group.docs.length > 0);
  }, [groups, searchQuery]);

  // Expand groups that have matching results when searching
  useEffect(() => {
    if (searchQuery.trim()) {
      setExpandedGroups(new Set(filteredGroups.map((g: PackageGroup) => g.packagePath)));
    }
  }, [searchQuery, filteredGroups]);

  // Default: expand all groups on first load
  useEffect(() => {
    if (groups.length > 0 && expandedGroups.size === 0) {
      setExpandedGroups(new Set(groups.map((g: PackageGroup) => g.packagePath)));
    }
  }, [groups, expandedGroups.size]);

  // Intersection observer to highlight active doc in sidebar
  useEffect(() => {
    const container = contentRef.current;
    if (!container) return;

    const observer = new IntersectionObserver(
      (entries) => {
        for (const entry of entries) {
          if (entry.isIntersecting) {
            const anchorId = entry.target.id;
            const docKey = anchorId.replace("doc-", "").replace(/-/g, "/");
            setActiveDocKey(docKey);
            break;
          }
        }
      },
      { rootMargin: "-80px 0px -60% 0px", threshold: 0.1 },
    );

    const anchors = container.querySelectorAll("[id^='doc-']");
    anchors.forEach((el: Element) => observer.observe(el));

    return () => observer.disconnect();
  }, [docs]);

  const handleToggleGroup = useCallback((path: string) => {
    setExpandedGroups((prev: Set<string>) => {
      const next = new Set(prev);
      if (next.has(path)) {
        next.delete(path);
      } else {
        next.add(path);
      }
      return next;
    });
  }, []);

  const handleSelectDoc = useCallback((doc: DocQueryItem) => {
    setActiveDocKey(getDocKey(doc));
    scrollToAnchor(getAnchorId(doc));
  }, []);

  return (
    <div className="flex h-[calc(100vh-64px)] overflow-hidden text-neutral-900 dark:text-neutral-100">
      {/* Left sidebar */}
      <aside className="w-64 shrink-0 overflow-y-auto border-r border-neutral-200 bg-neutral-50 dark:border-white/10 dark:bg-neutral-900">
        <div className="sticky top-0 z-10 border-b border-neutral-200 bg-neutral-50 p-3 dark:border-white/10 dark:bg-neutral-900">
          <div className="flex items-center justify-between gap-2">
            <h2 className="text-xs font-semibold uppercase tracking-wider text-neutral-500 dark:text-neutral-400">
              {t("pages.docView.classIndex", "Class Index")}
            </h2>
            {repo ? (
              <div className="flex items-center gap-1.5">
                <button
                  type="button"
                  onClick={loadDocs}
                  disabled={loading}
                  className="rounded p-1 text-neutral-400 transition hover:bg-neutral-200 hover:text-neutral-600 disabled:opacity-50 dark:hover:bg-white/10 dark:hover:text-neutral-300"
                  title={t("pages.docView.refresh", "Refresh")}
                >
                  <svg
                    className={`h-3.5 w-3.5 ${loading ? "animate-spin" : ""}`}
                    viewBox="0 0 16 16"
                    fill="none"
                    stroke="currentColor"
                    strokeWidth="1.5"
                  >
                    <path d="M13.5 8a5.5 5.5 0 1 1-1.6-3.9" />
                    <path d="M13.5 2.5v3h-3" />
                  </svg>
                </button>
                <button
                  type="button"
                  onClick={handleScan}
                  disabled={scanning || loading}
                  className="rounded p-1 text-neutral-400 transition hover:bg-neutral-200 hover:text-neutral-600 disabled:opacity-50 dark:hover:bg-white/10 dark:hover:text-neutral-300"
                  title={t("pages.docView.scanLocal", "Scan & Generate Docs")}
                >
                  <svg
                    className={`h-3.5 w-3.5 ${scanning ? "animate-pulse" : ""}`}
                    viewBox="0 0 16 16"
                    fill="none"
                    stroke="currentColor"
                    strokeWidth="1.5"
                  >
                    <path d="M2 4h12M2 8h12M2 12h8" />
                  </svg>
                </button>
                <span className="rounded bg-neutral-200 px-1.5 py-0.5 text-[10px] font-mono text-neutral-500 dark:bg-white/10 dark:text-neutral-400">
                  {docs.length}
                </span>
              </div>
            ) : null}
          </div>
          <input
            type="text"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder={t("pages.docView.searchPlaceholder", "Search classes...")}
            className="mt-2 w-full rounded border border-neutral-200 bg-white px-2.5 py-1.5 text-xs text-neutral-900 placeholder-neutral-400 outline-none transition focus:border-neutral-400 dark:border-white/15 dark:bg-white/[0.06] dark:text-neutral-100 dark:placeholder-neutral-500 dark:focus:border-white/30"
          />
        </div>

        <nav className="p-2">
          {filteredGroups.map((group) => {
            const isExpanded = expandedGroups.has(group.packagePath);
            return (
              <div key={group.packagePath} className="mb-0.5">
                <button
                  type="button"
                  onClick={() => handleToggleGroup(group.packagePath)}
                  className="flex w-full items-center gap-1.5 rounded px-2 py-1.5 text-left text-xs transition hover:bg-neutral-200/60 dark:hover:bg-white/[0.06]"
                >
                  <svg
                    className={`h-3 w-3 shrink-0 text-neutral-400 transition-transform ${isExpanded ? "rotate-90" : ""}`}
                    viewBox="0 0 16 16"
                    fill="currentColor"
                  >
                    <path d="M6 4l4 4-4 4V4z" />
                  </svg>
                  <span className="truncate font-medium text-neutral-500 dark:text-neutral-400">
                    {group.name}
                  </span>
                  <span className="ml-auto text-[10px] text-neutral-400 dark:text-neutral-500">
                    {group.docs.length}
                  </span>
                </button>
                {isExpanded ? (
                  <div className="ml-3 border-l border-neutral-200 dark:border-white/10">
                    {group.docs.map((doc) => {
                      const docKey = getDocKey(doc);
                      const isActive = activeDocKey === docKey;
                      return (
                        <button
                          key={docKey}
                          type="button"
                          onClick={() => handleSelectDoc(doc)}
                          className={`flex w-full items-center gap-1.5 truncate rounded-r px-3 py-1 text-left text-xs transition ${
                            isActive
                              ? "bg-blue-50 font-medium text-blue-600 dark:bg-blue-500/10 dark:text-blue-400"
                              : "text-neutral-400 hover:bg-neutral-200/40 hover:text-neutral-700 dark:text-neutral-500 dark:hover:bg-white/[0.04] dark:hover:text-neutral-300"
                          }`}
                        >
                          <span className="truncate font-mono text-[11px]">
                            {getClassName(doc)}
                          </span>
                        </button>
                      );
                    })}
                  </div>
                ) : null}
              </div>
            );
          })}
        </nav>
      </aside>

      {/* Right content */}
      <main ref={contentRef} className="flex-1 overflow-y-auto">
        <div className="mx-auto max-w-4xl px-8 py-6">
          {/* Header */}
          <div className="mb-6">
            <h1 className="text-2xl font-semibold tracking-tight">
              {t("pages.docView.title")}
            </h1>
            {repo ? (
              <p className="mt-1 font-mono text-xs text-neutral-500 dark:text-neutral-400">
                {repo}
                {branchParam !== "main" ? ` · ${branchParam}` : ""}
              </p>
            ) : null}
          </div>

          {/* Content */}
          {!repo ? (
            <div className="rounded-2xl border border-dashed border-neutral-300 px-6 py-12 text-center text-sm text-neutral-500 dark:border-white/15 dark:text-neutral-400">
              {t("pages.docView.noRepo")}
            </div>
          ) : loading ? (
            <div className="flex min-h-[300px] items-center justify-center">
              <div className="text-sm text-neutral-500 dark:text-neutral-400">
                {t("pages.docView.loading")}
              </div>
            </div>
          ) : error ? (
            <div className="rounded-2xl border border-rose-200 bg-rose-50 px-6 py-4 text-sm text-rose-800 dark:border-rose-400/20 dark:bg-rose-400/10 dark:text-rose-200">
              {error}
            </div>
          ) : docs.length === 0 ? (
            <div className="rounded-2xl border border-dashed border-neutral-300 px-6 py-12 text-center text-sm text-neutral-500 dark:border-white/15 dark:text-neutral-400">
              {t("pages.docView.empty")}
            </div>
          ) : (
            <div className="space-y-6">
              {docs.map((doc) => (
                <DocSection key={getDocKey(doc)} doc={doc} />
              ))}
            </div>
          )}
        </div>
      </main>

      <VirtualTerminalPanel
        title={t("pages.docView.terminal.title", "Document Scan")}
        subtitle={t(
          "pages.docView.terminal.subtitle",
          "Real-time output from the document generation pipeline.",
        )}
        bootLines={bootLines}
        sessionId={terminalSessionIdRef.current}
        variant="floating"
        open={terminalOpen}
        dismissible={!terminalBusy}
        onRequestClose={() => setTerminalOpen(false)}
        onConnectionStatusChange={setTerminalConnectionState}
        onSessionReady={onSessionReady}
      />
    </div>
  );
}

function DocSection({ doc }: { doc: DocQueryItem }) {
  const anchorId = getAnchorId(doc);
  const className = getClassName(doc);

  if (!doc.structuredDoc) {
    return (
      <section
        id={anchorId}
        className="scroll-mt-4 rounded-xl border border-neutral-200 bg-white p-5 dark:border-white/10 dark:bg-white/[0.03]"
      >
        <div className="flex items-center gap-2">
          <span className="font-mono text-sm font-semibold text-neutral-900 dark:text-neutral-100">
            {className}
          </span>
          <span
            className={`rounded-full px-2 py-0.5 text-[10px] font-semibold ${
              doc.parseStatus === "SUCCESS"
                ? "bg-emerald-50 text-emerald-700 dark:bg-emerald-400/10 dark:text-emerald-300"
                : "bg-rose-50 text-rose-700 dark:bg-rose-400/10 dark:text-rose-300"
            }`}
          >
            {doc.parseStatus}
          </span>
        </div>
        <p className="mt-1 font-mono text-[11px] text-neutral-400 dark:text-neutral-500">
          {doc.filePath}
        </p>
        {doc.parseErrorMsg ? (
          <div className="mt-3 rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-xs text-amber-800 dark:border-amber-400/20 dark:bg-amber-400/10 dark:text-amber-200">
            {doc.parseErrorMsg}
          </div>
        ) : null}
      </section>
    );
  }

  const sd = doc.structuredDoc;
  const firstType = sd.types[0];

  return (
    <section
      id={anchorId}
      className="scroll-mt-4 rounded-xl border border-neutral-200 bg-white dark:border-white/10 dark:bg-white/[0.03]"
    >
      {/* Section header */}
      <div className="border-b border-neutral-100 px-5 py-4 dark:border-white/10">
        <div className="flex items-center gap-2">
          {firstType ? (
            <span className="rounded bg-neutral-900 px-1.5 py-0.5 text-[10px] font-semibold text-white dark:bg-white dark:text-black">
              {firstType.kind}
            </span>
          ) : null}
          <h3 className="text-base font-semibold text-neutral-950 dark:text-neutral-50">
            {className}
          </h3>
          <span
            className={`ml-auto rounded-full px-2 py-0.5 text-[10px] font-semibold ${
              doc.parseStatus === "SUCCESS"
                ? "bg-emerald-50 text-emerald-700 dark:bg-emerald-400/10 dark:text-emerald-300"
                : "bg-rose-50 text-rose-700 dark:bg-rose-400/10 dark:text-rose-300"
            }`}
          >
            {doc.parseStatus}
          </span>
        </div>
        {firstType ? (
          <p className="mt-1 font-mono text-[11px] text-neutral-400 dark:text-neutral-500">
            {firstType.qualifiedName}
          </p>
        ) : null}
      </div>

      {/* Section body */}
      <div className="px-5 py-4">
        <StructuredDocDetail doc={doc} section={getDefaultDocSection(doc)} hideHeader />
      </div>
    </section>
  );
}

function createSessionId() {
  if (
    typeof crypto !== "undefined" &&
    typeof crypto.randomUUID === "function"
  ) {
    return crypto.randomUUID();
  }
  return `${Date.now()}-${Math.random().toString(16).slice(2)}`;
}
