import type { ChangeEvent } from "react";
import { useTranslation } from "react-i18next";
import { Link } from "react-router-dom";
import { VirtualTerminalPanel } from "../../components/virtualTerminal/VirtualTerminalPanel";
import { WORKBENCH_FORM_PAGE_INNER } from "../../layout/workbenchLayout";
import type { useProductDocsPage } from "./useProductDocsPage";

type ProductDocsPageViewProps = ReturnType<typeof useProductDocsPage>;

export function ProductDocsPageView(props: ProductDocsPageViewProps) {
  const { t } = useTranslation();
  const {
    mockMode,
    repo,
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
  } = props;

  return (
    <div className={WORKBENCH_FORM_PAGE_INNER}>
      <h1 className="mt-0 text-3xl font-semibold tracking-tight md:text-4xl">
        {t("pages.documentation.title")}
      </h1>

      {repo ? (
        <div className="mt-5 inline-flex max-w-full rounded-full border border-neutral-200 bg-neutral-50 px-4 py-1.5 font-mono text-xs text-neutral-700 dark:border-white/15 dark:bg-white/[0.06] dark:text-neutral-200">
          {t("pages.documentation.contextRepo", { repo })}
        </div>
      ) : null}

      {mockMode ? (
        <div className="mt-5 rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-900 dark:border-amber-400/20 dark:bg-amber-400/10 dark:text-amber-100">
          {t("pages.documentation.mockMode.hint")}
        </div>
      ) : null}

      <p
        className={`text-pretty text-base leading-relaxed text-neutral-600 dark:text-neutral-400 ${repo || mockMode ? "mt-8" : "mt-6"}`}
      >
        {t("pages.documentation.lede")}
      </p>

      <div className="mt-8 rounded-2xl border border-neutral-200 bg-neutral-50/70 p-5 dark:border-white/10 dark:bg-white/[0.04]">
        <h2 className="text-base font-semibold text-neutral-900 dark:text-neutral-100">
          {t("pages.documentation.actions.title")}
        </h2>
        <label className="mt-4 flex flex-col gap-1 text-sm text-neutral-600 dark:text-neutral-300">
          {t("pages.documentation.actions.branch")}
          <input
            value={branch}
            onChange={(event: ChangeEvent<HTMLInputElement>) =>
              setBranch(event.target.value)
            }
            className="rounded-lg border border-neutral-300 bg-white px-3 py-2 text-sm text-neutral-900 outline-none ring-neutral-400 transition focus:ring-2 dark:border-white/15 dark:bg-black/30 dark:text-neutral-100"
          />
        </label>

        <div className="mt-4 flex flex-wrap gap-2">
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
          {viewDocsUrl ? (
            <Link
              to={viewDocsUrl}
              className="rounded-lg bg-neutral-900 px-3 py-2 text-sm font-medium text-white transition hover:bg-neutral-700 dark:bg-white dark:text-black dark:hover:bg-neutral-200"
            >
              {t("pages.documentation.actions.enterDocPage")}
            </Link>
          ) : null}
        </div>

        {status ? (
          <p
            className={`mt-3 text-sm ${status.type === "success" ? "text-emerald-600 dark:text-emerald-400" : "text-rose-600 dark:text-rose-400"}`}
          >
            {status.text}
          </p>
        ) : null}
        {showTerminalUnavailable ? (
          <p className="mt-2 text-sm text-amber-600 dark:text-amber-400">
            {t("common.terminal.unavailable")}
          </p>
        ) : null}
      </div>

      <VirtualTerminalPanel
        title={t("pages.documentation.terminal.title")}
        subtitle={t("pages.documentation.terminal.subtitle")}
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
