import { useTranslation } from "react-i18next";
import { VirtualTerminalPanel } from "../../components/virtualTerminal/VirtualTerminalPanel";
import {
  DOC_VIEW_CONTENT_PADDING,
  DOC_VIEW_HEADER_OFFSET,
  DOC_VIEW_SIDEBAR,
} from "../../layout/workbenchLayout";
import {
  MemberGroup,
  getDocKey,
  getSectionMembers,
} from "./DocViewComponents";
import { getClassName } from "./docViewUtils";
import type { useDocViewPage } from "./useDocViewPage";
import type { DocQueryItem } from "../../services/backendApi";

type DocViewPageViewProps = ReturnType<typeof useDocViewPage>;

export function DocViewPageView(props: DocViewPageViewProps) {
  const { t } = useTranslation();
  const {
    mockMode,
    repo,
    branchParam,
    docs,
    loading,
    error,
    expandedGroups,
    activeDocKey,
    selectedDoc,
    searchQuery,
    setSearchQuery,
    scanning,
    cloning,
    cloneStatus,
    terminalOpen,
    terminalBusy,
    bootLines,
    terminalSessionId,
    filteredGroups,
    loadDocs,
    handleScan,
    handleToggleGroup,
    handleSelectDoc,
    handleShowAll,
    handleClone,
    onSessionReady,
    setTerminalConnectionState,
    setTerminalOpen,
  } = props;

  return (
    <div className="flex w-full items-stretch text-neutral-900 dark:text-neutral-100">
      <aside
        className={DOC_VIEW_SIDEBAR}
        style={{
          top: DOC_VIEW_HEADER_OFFSET,
          height: `calc(100vh - ${DOC_VIEW_HEADER_OFFSET})`,
        }}
      >
        <div className="shrink-0 border-b border-neutral-200 bg-neutral-50 p-3 dark:border-white/10 dark:bg-neutral-900">
          <div className="flex items-center justify-between gap-2">
            <h2 className="text-xs font-semibold uppercase tracking-wider text-neutral-500 dark:text-neutral-400">
              {t("pages.docView.classIndex")}
            </h2>
            {repo ? (
              <div className="flex items-center gap-1.5">
                <button
                  type="button"
                  onClick={loadDocs}
                  disabled={loading}
                  className="rounded p-1 text-neutral-400 transition hover:bg-neutral-200 hover:text-neutral-600 disabled:opacity-50 dark:hover:bg-white/10 dark:hover:text-neutral-300"
                  title={t("pages.docView.refresh")}
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
                  title={t("pages.docView.scanLocal")}
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
                <button
                  type="button"
                  onClick={handleClone}
                  disabled={cloning || loading}
                  className="rounded p-1 text-neutral-400 transition hover:bg-neutral-200 hover:text-neutral-600 disabled:opacity-50 dark:hover:bg-white/10 dark:hover:text-neutral-300"
                  title={t("pages.docView.cloneRepo")}
                >
                  <svg
                    className={`h-3.5 w-3.5 ${cloning ? "animate-pulse" : ""}`}
                    viewBox="0 0 16 16"
                    fill="none"
                    stroke="currentColor"
                    strokeWidth="1.5"
                  >
                    <path d="M8 2v8M4 7l4 4 4-4" />
                    <path d="M2 12v2h12v-2" />
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
            placeholder={t("pages.docView.searchPlaceholder")}
            className="mt-2 w-full rounded border border-neutral-200 bg-white px-2.5 py-1.5 text-xs text-neutral-900 placeholder-neutral-400 outline-none transition focus:border-neutral-400 dark:border-white/15 dark:bg-white/[0.06] dark:text-neutral-100 dark:placeholder-neutral-500 dark:focus:border-white/30"
          />
        </div>

        <nav className="flex-1 p-3">
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

      <main
        className={`min-w-0 flex-1 self-stretch ${DOC_VIEW_CONTENT_PADDING}`}
      >
        <div className="w-full">
          <div className="mb-6">
            <h1 className="text-2xl font-semibold tracking-tight">
              {t("pages.docView.title")}
            </h1>
            {mockMode ? (
              <div className="mt-3 rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-900 dark:border-amber-400/20 dark:bg-amber-400/10 dark:text-amber-100">
                {t("pages.documentation.mockMode.hint")}
              </div>
            ) : null}
            {repo ? (
              <p className="mt-1 font-mono text-xs text-neutral-500 dark:text-neutral-400">
                {repo}
                {branchParam !== "main" ? ` · ${branchParam}` : ""}
              </p>
            ) : null}
          </div>

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
            <div className="space-y-4">
              <div className="rounded-2xl border border-rose-200 bg-rose-50 px-6 py-4 text-sm text-rose-800 dark:border-rose-400/20 dark:bg-rose-400/10 dark:text-rose-200">
                {error}
              </div>
              <button
                type="button"
                onClick={handleClone}
                disabled={cloning}
                className="rounded-lg bg-neutral-900 px-4 py-2 text-sm font-medium text-white transition hover:bg-neutral-700 disabled:cursor-not-allowed disabled:opacity-60 dark:bg-white dark:text-black dark:hover:bg-neutral-200"
              >
                {cloning
                  ? t("pages.documentation.actions.cloning")
                  : t("pages.docView.cloneRepo")}
              </button>
              {cloneStatus ? (
                <p
                  className={`text-sm ${cloneStatus.type === "success" ? "text-emerald-600 dark:text-emerald-400" : "text-rose-600 dark:text-rose-400"}`}
                >
                  {cloneStatus.text}
                </p>
              ) : null}
            </div>
          ) : docs.length === 0 ? (
            <div className="rounded-2xl border border-dashed border-neutral-300 px-6 py-12 text-center text-sm text-neutral-500 dark:border-white/15 dark:text-neutral-400">
              {t("pages.docView.empty")}
            </div>
          ) : selectedDoc ? (
            <div>
              <button
                type="button"
                onClick={handleShowAll}
                className="mb-4 text-xs text-neutral-500 transition hover:text-neutral-800 dark:text-neutral-400 dark:hover:text-neutral-200"
              >
                &larr; {t("pages.docView.showAll")}
              </button>
              <DocSection doc={selectedDoc} />
            </div>
          ) : (
            <div className="space-y-6">
              {docs.map((doc) => (
                <DocSection key={getDocKey(doc)} doc={doc} />
              ))}
            </div>
          )}

          {cloneStatus && !error ? (
            <p
              className={`mt-3 text-sm ${cloneStatus.type === "success" ? "text-emerald-600 dark:text-emerald-400" : "text-rose-600 dark:text-rose-400"}`}
            >
              {cloneStatus.text}
            </p>
          ) : null}
        </div>
      </main>

      <VirtualTerminalPanel
        title={t("pages.docView.terminal.title")}
        subtitle={t("pages.docView.terminal.subtitle")}
        bootLines={bootLines}
        sessionId={terminalSessionId}
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
  const { t } = useTranslation();
  const className = getClassName(doc);

  if (!doc.structuredDoc) {
    return (
      <section className="rounded-xl border border-neutral-200 bg-white p-5 dark:border-white/10 dark:bg-white/[0.03]">
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

  const sectionEntries: { key: "fields" | "constructors" | "methods"; label: string }[] = [
    { key: "fields", label: t("pages.documentation.structured.fields") },
    { key: "constructors", label: t("pages.documentation.structured.constructors") },
    { key: "methods", label: t("pages.documentation.structured.methods") },
  ];

  return (
    <section className="rounded-xl border border-neutral-200 bg-white dark:border-white/10 dark:bg-white/[0.03]">
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
        {firstType?.description ? (
          <p className="mt-3 text-sm leading-6 text-neutral-700 dark:text-neutral-300">
            {firstType.description}
          </p>
        ) : null}
        {firstType?.signature ? (
          <pre className="mt-3 overflow-x-auto rounded-lg bg-neutral-950 px-3 py-2 font-mono text-xs text-neutral-50 dark:bg-black">
            {firstType.signature}
          </pre>
        ) : null}
      </div>

      <div className="space-y-6 px-5 py-4">
        {sectionEntries.map(({ key, label }) => {
          const groups = sd.types
            .map((typeDoc) => ({
              typeDoc,
              members: getSectionMembers(typeDoc, key),
            }))
            .filter((group) => group.members.length > 0);

          if (groups.length === 0) return null;

          return (
            <div key={key}>
              <h4 className="text-xs font-semibold uppercase tracking-normal text-neutral-500 dark:text-neutral-400">
                {label}
              </h4>
              <div className="mt-2 space-y-2">
                {groups.map(({ typeDoc, members }) => (
                  <MemberGroup
                    key={`${typeDoc.htmlFile}-${typeDoc.name}-${key}`}
                    title={typeDoc.name}
                    members={members}
                  />
                ))}
              </div>
            </div>
          );
        })}
      </div>
    </section>
  );
}
