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

type PackageNode = {
  name: string;
  packagePath: string;
  children?: PackageNode[];
  docs?: DocQueryItem[];
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

function buildPackageTree(docs: DocQueryItem[]): PackageNode[] {
  const root: PackageNode[] = [];
  const nodeMap = new Map<string, PackageNode>();

  for (const doc of docs) {
    const { segments } = extractPackageSegments(doc.filePath);
    let currentLevel = root;
    let currentPath = "";

    for (const seg of segments) {
      currentPath = currentPath ? `${currentPath}/${seg}` : seg;

      if (!nodeMap.has(currentPath)) {
        const node: PackageNode = {
          name: seg,
          packagePath: currentPath.replace(/\//g, "."),
          children: [],
        };
        nodeMap.set(currentPath, node);
        currentLevel.push(node);
      }
      currentLevel = nodeMap.get(currentPath)!.children!;
    }

    currentLevel.push({
      name: getClassName(doc),
      packagePath: currentPath.replace(/\//g, "."),
      docs: [doc],
    });
  }

  return root;
}

function filterTree(nodes: PackageNode[], query: string): PackageNode[] {
  const lower = query.toLowerCase();
  const result: PackageNode[] = [];

  for (const node of nodes) {
    if (node.docs) {
      const matches = node.docs.some(
        (doc) =>
          node.name.toLowerCase().includes(lower) ||
          node.packagePath.toLowerCase().includes(lower) ||
          doc.filePath.toLowerCase().includes(lower),
      );
      if (matches) {
        result.push(node);
      }
    } else if (node.children) {
      const filteredChildren = filterTree(node.children, query);
      if (
        filteredChildren.length > 0 ||
        node.name.toLowerCase().includes(lower) ||
        node.packagePath.toLowerCase().includes(lower)
      ) {
        result.push({
          ...node,
          children:
            filteredChildren.length > 0
              ? filteredChildren
              : node.children,
        });
      }
    }
  }

  return result;
}

function collectExpandedPaths(nodes: PackageNode[]): Set<string> {
  const paths = new Set<string>();
  function walk(list: PackageNode[]) {
    for (const node of list) {
      if (node.children) {
        paths.add(node.packagePath);
        walk(node.children);
      }
    }
  }
  walk(nodes);
  return paths;
}

function PackageTree({
  nodes,
  selectedDocKey,
  expandedPackages,
  onSelectDoc,
  onTogglePackage,
  depth = 0,
}: {
  nodes: PackageNode[];
  selectedDocKey: string | null;
  expandedPackages: Set<string>;
  onSelectDoc: (doc: DocQueryItem) => void;
  onTogglePackage: (path: string) => void;
  depth?: number;
}) {
  return (
    <div className={depth > 0 ? "ml-2 border-l border-neutral-200 dark:border-white/10" : ""}>
      {nodes.map((node) => {
        const isPackage = !!node.children;
        const isExpanded = expandedPackages.has(node.packagePath);

        if (isPackage) {
          return (
            <div key={node.packagePath}>
              <button
                type="button"
                onClick={() => onTogglePackage(node.packagePath)}
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
                <PackageTree
                  nodes={node.children}
                  selectedDocKey={selectedDocKey}
                  expandedPackages={expandedPackages}
                  onSelectDoc={onSelectDoc}
                  onTogglePackage={onTogglePackage}
                  depth={depth + 1}
                />
              ) : null}
            </div>
          );
        }

        const doc = node.docs?.[0];
        const isActive = doc && getDocKey(doc) === selectedDocKey;

        return (
          <button
            key={node.packagePath}
            type="button"
            onClick={() => doc && onSelectDoc(doc)}
            className={`flex w-full items-center gap-1.5 rounded-md px-2 py-1.5 text-left text-xs transition ${
              isActive
                ? "bg-neutral-900 text-white dark:bg-white dark:text-black"
                : "text-neutral-700 hover:bg-neutral-100 dark:text-neutral-300 dark:hover:bg-white/10"
            }`}
            style={{ paddingLeft: `${depth * 12 + 8}px` }}
          >
            <span className="shrink-0 text-[10px] opacity-40">C</span>
            <span className="truncate font-mono">{node.name}</span>
            {doc ? (
              <span
                className={`ml-auto shrink-0 rounded-full px-1.5 py-0.5 text-[10px] font-semibold ${
                  isActive
                    ? "bg-white/20 dark:bg-black/10"
                    : doc.parseStatus === "SUCCESS"
                      ? "bg-emerald-50 text-emerald-700 dark:bg-emerald-400/10 dark:text-emerald-300"
                      : "bg-rose-50 text-rose-700 dark:bg-rose-400/10 dark:text-rose-300"
                }`}
              >
                {doc.parseStatus}
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
  const [searchQuery, setSearchQuery] = useState("");
  const [expandedPackages, setExpandedPackages] = useState<Set<string>>(
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

  const packageTree = useMemo(() => buildPackageTree(docs), [docs]);

  const filteredTree = useMemo(() => {
    if (!searchQuery.trim()) return packageTree;
    return filterTree(packageTree, searchQuery.trim());
  }, [packageTree, searchQuery]);

  useEffect(() => {
    if (searchQuery.trim()) {
      setExpandedPackages(collectExpandedPaths(filteredTree));
    }
  }, [searchQuery, filteredTree]);

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

  const handleTogglePackage = useCallback((path: string) => {
    setExpandedPackages((prev: Set<string>) => {
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
                {t("pages.docView.classIndex", "Class Index")}
              </h2>
              <span className="shrink-0 rounded-full bg-neutral-100 px-2 py-0.5 text-[11px] font-mono text-neutral-600 dark:bg-white/10 dark:text-neutral-300">
                {docs.length}
              </span>
            </div>
            <div className="px-1 pb-2">
              <input
                type="text"
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                placeholder={t("pages.docView.searchPlaceholder", "Search classes...")}
                className="w-full rounded-lg border border-neutral-200 bg-neutral-50 px-3 py-1.5 text-xs text-neutral-900 placeholder-neutral-400 outline-none transition focus:border-neutral-400 focus:bg-white dark:border-white/15 dark:bg-white/[0.06] dark:text-neutral-100 dark:placeholder-neutral-500 dark:focus:border-white/30 dark:focus:bg-white/[0.1]"
              />
            </div>
            <PackageTree
              nodes={filteredTree}
              selectedDocKey={selectedDoc ? getDocKey(selectedDoc) : null}
              expandedPackages={expandedPackages}
              onSelectDoc={handleSelectDoc}
              onTogglePackage={handleTogglePackage}
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
