import type { TerminalClient } from "../../../../terminal/src";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { useSearchParams } from "react-router-dom";
import type { TerminalConnectionState } from "../../components/virtualTerminal/VirtualTerminalPanel";
import {
  isMockRepo,
  MOCK_DOC_ITEMS,
  simulateMockScan,
} from "../../mocks/docMockData";
import { queryDocs, scanLocalDoc, type DocQueryItem } from "../../services/backendApi";
import { createSessionId } from "../../utils/terminalSession";
import { getDocKey } from "./DocViewComponents";
import {
  buildGroups,
  filterGroups,
  getAnchorId,
  scrollToAnchor,
} from "./docViewUtils";

export function useDocViewPage() {
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
      if (isMockRepo(repo)) {
        await new Promise((resolve) => setTimeout(resolve, 300));
        setDocs(MOCK_DOC_ITEMS);
        return;
      }
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
      t("pages.docView.terminal.ready"),
      t("pages.docView.terminal.hint"),
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

  const formatTimingMs = useCallback(
    (value: number | undefined | null, fallback: number | string) => {
      const ms = value ?? fallback;
      return typeof ms === "number" ? String(ms) : ms;
    },
    [],
  );

  const handleScan = useCallback(async () => {
    if (!repo || scanning) return;
    setScanning(true);
    setTerminalOpen(true);
    setTerminalBusy(true);
    terminalClientRef.current?.clear();
    appendTerminal(
      t("pages.docView.terminal.scanStarted", {
        project: repo,
        branch: branchParam,
      }),
    );

    const startTime = Date.now();
    try {
      const result = isMockRepo(repo)
        ? await simulateMockScan(branchParam, appendTerminal)
        : await scanLocalDoc({
            project: repo,
            branch: branchParam,
            terminalSessionId: terminalSessionIdRef.current,
          });

      const elapsed = Date.now() - startTime;
      appendTerminal("");
      appendTerminal(t("pages.docView.terminal.separator"));
      appendTerminal(
        t("pages.docView.terminal.scanCompleted", {
          scanned: result.scannedFileCount,
          generated: result.generatedFileCount,
          skipped: result.skippedFileCount,
          failed: result.failedFileCount,
          retried: result.retryFileCount,
          recovered: result.retryGeneratedCount,
        }),
      );
      appendTerminal(
        t("pages.docView.terminal.scanTiming.total", {
          ms: formatTimingMs(result.totalDurationMs, elapsed),
        }),
      );
      appendTerminal(
        t("pages.docView.terminal.scanTiming.fileListing", {
          ms: formatTimingMs(result.fileListingDurationMs, "-"),
        }),
      );
      appendTerminal(
        t("pages.docView.terminal.scanTiming.docGeneration", {
          ms: formatTimingMs(result.docGenerationDurationMs, "-"),
        }),
      );
      appendTerminal(
        t("pages.docView.terminal.scanTiming.dbOps", {
          ms: formatTimingMs(result.dbOperationDurationMs, "-"),
        }),
      );
      appendTerminal(t("pages.docView.terminal.separator"));

      await loadDocs();
    } catch (err) {
      appendTerminal("");
      appendTerminal(
        t("pages.docView.terminal.scanError", {
          message: err instanceof Error ? err.message : String(err),
        }),
      );
    } finally {
      setScanning(false);
      setTerminalBusy(false);
    }
  }, [
    repo,
    branchParam,
    scanning,
    loadDocs,
    t,
    appendTerminal,
    formatTimingMs,
  ]);

  const groups = useMemo(() => buildGroups(docs), [docs]);
  const filteredGroups = useMemo(
    () => filterGroups(groups, searchQuery),
    [groups, searchQuery],
  );

  useEffect(() => {
    if (searchQuery.trim()) {
      setExpandedGroups(
        new Set(filteredGroups.map((group) => group.packagePath)),
      );
    }
  }, [searchQuery, filteredGroups]);

  useEffect(() => {
    if (groups.length > 0 && expandedGroups.size === 0) {
      setExpandedGroups(new Set(groups.map((group) => group.packagePath)));
    }
  }, [groups, expandedGroups.size]);

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
    anchors.forEach((el) => observer.observe(el));

    return () => observer.disconnect();
  }, [docs]);

  const handleToggleGroup = useCallback((path: string) => {
    setExpandedGroups((prev) => {
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

  return {
    mockMode: isMockRepo(repo),
    repo,
    branchParam,
    docs,
    loading,
    error,
    expandedGroups,
    activeDocKey,
    searchQuery,
    setSearchQuery,
    scanning,
    terminalOpen,
    terminalBusy,
    terminalConnectionState,
    bootLines,
    terminalSessionId: terminalSessionIdRef.current,
    contentRef,
    filteredGroups,
    loadDocs,
    handleScan,
    handleToggleGroup,
    handleSelectDoc,
    onSessionReady,
    setTerminalConnectionState,
    setTerminalOpen,
  };
}
