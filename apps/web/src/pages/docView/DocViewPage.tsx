import { useCallback, useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { Link, useSearchParams } from "react-router-dom";
import { queryDocs, type DocQueryItem } from "../../services/backendApi";
import {
  type StructuredDocSection,
  StructuredDocDetail,
  STRUCTURED_DOC_SECTIONS,
  STRUCTURED_DOC_SECTION_LABEL_KEYS,
  getDocKey,
  getDefaultDocSection,
  getSectionCount,
} from "./DocViewComponents";

type StructuredDocSelection = {
  docKey: string;
  section: StructuredDocSection;
};

type TreeNode = {
  name: string;
  path: string;
  children?: TreeNode[];
  doc?: DocQueryItem;
};

function buildTree(docs: DocQueryItem[]): TreeNode[] {
  const root: TreeNode[] = [];
  const folderMap = new Map<string, TreeNode>();

  for (const doc of docs) {
    const segments = doc.filePath.split("/");
    let currentLevel = root;
    let currentPath = "";

    for (let i = 0; i < segments.length - 1; i++) {
      const folderName = segments[i];
      currentPath = currentPath ? `${currentPath}/${folderName}` : folderName;

      if (!folderMap.has(currentPath)) {
        const folderNode: TreeNode = {
          name: folderName,
          path: currentPath,
          children: [],
        };
        folderMap.set(currentPath, folderNode);
        currentLevel.push(folderNode);
      }
      currentLevel = folderMap.get(currentPath)!.children!;
    }

    const fileName = segments[segments.length - 1];
    currentLevel.push({
      name: fileName,
      path: doc.filePath,
      doc,
    });
  }

  return root;
}

function FileTree({
  nodes,
  selectedDocKey,
  expandedFolders,
  onSelectDoc,
  onToggleFolder,
  depth = 0,
}: {
  nodes: TreeNode[];
  selectedDocKey: string | null;
  expandedFolders: Set<string>;
  onSelectDoc: (doc: DocQueryItem) => void;
  onToggleFolder: (path: string) => void;
  depth?: number;
}) {
  return (
    <div className={depth > 0 ? "ml-3 border-l border-neutral-200 dark:border-white/10" : ""}>
      {nodes.map((node) => {
        const isFolder = !!node.children;
        const isExpanded = expandedFolders.has(node.path);
        const isActive = node.doc && getDocKey(node.doc) === selectedDocKey;

        if (isFolder) {
          return (
            <div key={node.path}>
              <button
                type="button"
                onClick={() => onToggleFolder(node.path)}
                className="flex w-full items-center gap-1.5 rounded-md px-2 py-1.5 text-left text-xs text-neutral-700 transition hover:bg-neutral-100 dark:text-neutral-300 dark:hover:bg-white/10"
              >
                <span
                  className={`shrink-0 text-[10px] transition-transform ${isExpanded ? "rotate-90" : ""}`}
                >
                  ▶
                </span>
                <span className="truncate font-medium">{node.name}</span>
              </button>
              {isExpanded && node.children ? (
                <FileTree
                  nodes={node.children}
                  selectedDocKey={selectedDocKey}
                  expandedFolders={expandedFolders}
                  onSelectDoc={onSelectDoc}
                  onToggleFolder={onToggleFolder}
                  depth={depth + 1}
                />
              ) : null}
            </div>
          );
        }

        return (
          <button
            key={node.path}
            type="button"
            onClick={() => node.doc && onSelectDoc(node.doc)}
            className={`flex w-full items-center gap-1.5 rounded-md px-2 py-1.5 text-left text-xs transition ${
              isActive
                ? "bg-neutral-900 text-white dark:bg-white dark:text-black"
                : "text-neutral-700 hover:bg-neutral-100 dark:text-neutral-300 dark:hover:bg-white/10"
            }`}
            style={{ paddingLeft: `${depth * 12 + 8}px` }}
          >
            <span className="shrink-0 text-[10px] opacity-40">📄</span>
            <span className="truncate">{node.name}</span>
            {node.doc ? (
              <span
                className={`ml-auto shrink-0 rounded-full px-1.5 py-0.5 text-[10px] font-semibold ${
                  isActive
                    ? "bg-white/20 dark:bg-black/10"
                    : node.doc.parseStatus === "SUCCESS"
                      ? "bg-emerald-50 text-emerald-700 dark:bg-emerald-400/10 dark:text-emerald-300"
                      : "bg-rose-50 text-rose-700 dark:bg-rose-400/10 dark:text-rose-300"
                }`}
              >
                {node.doc.parseStatus}
              </span>
            ) : null}
          </button>
        );
      })}
    </div>
  );
}

export function DocViewPage() {
  const { t } = useTranslation();
  const [params] = useSearchParams();
  const repo = params.get("repo");
  const branchParam = params.get("branch") || "main";

  const [docs, setDocs] = useState<DocQueryItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [selectedDocSelection, setSelectedDocSelection] =
    useState<StructuredDocSelection | null>(null);
  const [expandedFolders, setExpandedFolders] = useState<Set<string>>(
    new Set(),
  );

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

  const tree = useMemo(() => buildTree(docs), [docs]);

  const selectedDoc = useMemo(() => {
    const fallback =
      docs.find((doc: DocQueryItem) => doc.structuredDoc) ?? docs[0] ?? null;
    if (!selectedDocSelection) {
      return fallback;
    }
    return (
      docs.find(
        (doc: DocQueryItem) => getDocKey(doc) === selectedDocSelection.docKey,
      ) ?? fallback
    );
  }, [docs, selectedDocSelection]);
  const selectedSection = selectedDocSelection?.section ?? "types";

  const handleSelectDoc = useCallback((doc: DocQueryItem) => {
    setSelectedDocSelection({
      docKey: getDocKey(doc),
      section: getDefaultDocSection(doc),
    });
  }, []);

  const handleToggleFolder = useCallback((path: string) => {
    setExpandedFolders((prev: Set<string>) => {
      const next = new Set(prev);
      if (next.has(path)) {
        next.delete(path);
      } else {
        next.add(path);
      }
      return next;
    });
  }, []);

  return (
    <div className="mx-auto max-w-[1200px] pb-20 pt-2 text-neutral-950 dark:text-neutral-50">
      <div className="flex items-center justify-between gap-4">
        <h1 className="mt-0 text-3xl font-semibold tracking-tight md:text-4xl">
          {t("pages.docView.title")}
        </h1>
        {repo ? (
          <Link
            to={`/documentation?repo=${encodeURIComponent(repo)}`}
            className="shrink-0 rounded-lg border border-neutral-300 px-3 py-2 text-sm font-medium text-neutral-700 transition hover:bg-neutral-100 dark:border-white/20 dark:text-neutral-200 dark:hover:bg-white/10"
          >
            {t("pages.docView.backToGenerate")}
          </Link>
        ) : null}
      </div>

      {repo ? (
        <div className="mt-5 inline-flex max-w-full rounded-full border border-neutral-200 bg-neutral-50 px-4 py-1.5 font-mono text-xs text-neutral-700 dark:border-white/15 dark:bg-white/[0.06] dark:text-neutral-200">
          {t("pages.documentation.contextRepo", { repo })}
          {branchParam !== "main" ? ` · ${branchParam}` : ""}
        </div>
      ) : null}

      {!repo ? (
        <div className="mt-10 rounded-2xl border border-dashed border-neutral-300 px-6 py-12 text-center text-sm text-neutral-500 dark:border-white/15 dark:text-neutral-400">
          {t("pages.docView.noRepo")}
        </div>
      ) : loading ? (
        <div className="mt-10 flex min-h-[300px] items-center justify-center">
          <div className="text-sm text-neutral-500 dark:text-neutral-400">
            {t("pages.docView.loading")}
          </div>
        </div>
      ) : error ? (
        <div className="mt-10 rounded-2xl border border-rose-200 bg-rose-50 px-6 py-4 text-sm text-rose-800 dark:border-rose-400/20 dark:bg-rose-400/10 dark:text-rose-200">
          {error}
        </div>
      ) : docs.length === 0 ? (
        <div className="mt-10 rounded-2xl border border-dashed border-neutral-300 px-6 py-12 text-center text-sm text-neutral-500 dark:border-white/15 dark:text-neutral-400">
          {t("pages.docView.empty")}
        </div>
      ) : (
        <div className="mt-8 grid gap-4 lg:grid-cols-[300px_minmax(0,1fr)]">
          <section className="max-h-[720px] overflow-auto rounded-2xl border border-neutral-200 bg-white p-3 shadow-sm dark:border-white/10 dark:bg-white/[0.03]">
            <div className="flex items-center justify-between gap-3 px-2 pb-2">
              <h2 className="text-sm font-semibold text-neutral-900 dark:text-neutral-100">
                {t("pages.docView.fileTree")}
              </h2>
              <span className="shrink-0 rounded-full bg-neutral-100 px-2 py-0.5 text-[11px] font-mono text-neutral-600 dark:bg-white/10 dark:text-neutral-300">
                {docs.length}
              </span>
            </div>
            <FileTree
              nodes={tree}
              selectedDocKey={selectedDoc ? getDocKey(selectedDoc) : null}
              expandedFolders={expandedFolders}
              onSelectDoc={handleSelectDoc}
              onToggleFolder={handleToggleFolder}
            />
          </section>

          <div>
            {/* Section tabs */}
            {selectedDoc?.structuredDoc ? (
              <div className="mb-3 flex flex-wrap gap-1.5">
                {STRUCTURED_DOC_SECTIONS.map((section) => {
                  const sectionActive = selectedSection === section;
                  const count = getSectionCount(selectedDoc, section);
                  return (
                    <button
                      key={section}
                      type="button"
                      onClick={() =>
                        setSelectedDocSelection({
                          docKey: getDocKey(selectedDoc),
                          section,
                        })
                      }
                      className={`flex items-center gap-1.5 rounded-lg px-3 py-1.5 text-xs font-medium transition ${
                        sectionActive
                          ? "bg-neutral-900 text-white dark:bg-white dark:text-black"
                          : "border border-neutral-200 text-neutral-600 hover:border-neutral-400 dark:border-white/15 dark:text-neutral-300 dark:hover:border-white/30"
                      }`}
                    >
                      <span>
                        {t(STRUCTURED_DOC_SECTION_LABEL_KEYS[section])}
                      </span>
                      <span
                        className={`rounded-full px-1.5 py-0.5 text-[10px] font-mono ${
                          sectionActive
                            ? "bg-white/20 dark:bg-black/10"
                            : "bg-neutral-100 text-neutral-500 dark:bg-white/10 dark:text-neutral-400"
                        }`}
                      >
                        {count}
                      </span>
                    </button>
                  );
                })}
              </div>
            ) : null}

            <StructuredDocDetail doc={selectedDoc} section={selectedSection} />
          </div>
        </div>
      )}
    </div>
  );
}
