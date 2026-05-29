import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { VirtualTerminalPanel } from "../../components/virtualTerminal/VirtualTerminalPanel";
import type { useProductDeployPage } from "./useProductDeployPage";

type ProductDeployPageViewProps = ReturnType<typeof useProductDeployPage>;

export function ProductDeployPageView(props: ProductDeployPageViewProps) {
  const { t } = useTranslation();
  const {
    repo,
    projectId,
    setProjectId,
    branch,
    setBranch,
    artifactPath,
    setArtifactPath,
    buildEnabled,
    setBuildEnabled,
    deployHost,
    setDeployHost,
    deployPort,
    setDeployPort,
    deployUser,
    setDeployUser,
    deployTargetDir,
    setDeployTargetDir,
    sshPassword,
    setSshPassword,
    sshKeySettingUp,
    deploying,
    status,
    activeTaskId,
    activeTask,
    bootLines,
    sections,
    terminalSessionId,
    terminalOpen,
    terminalBusy,
    showTerminalUnavailable,
    handleTriggerDeploy,
    handleSetupSshKey,
    handleCancel,
    onSessionReady,
    setTerminalConnectionState,
    setTerminalOpen,
  } = props;

  return (
    <div className="w-full pb-20 pt-2 text-neutral-950 dark:text-neutral-50">
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
          <label className="flex flex-col gap-1 text-sm text-neutral-600 dark:text-neutral-300">
            {t("pages.deploy.actions.deployHost")}
            <input
              value={deployHost}
              onChange={(event) => setDeployHost(event.target.value)}
              placeholder={t("pages.deploy.actions.deployHostPlaceholder")}
              className="rounded-lg border border-neutral-300 bg-white px-3 py-2 text-sm text-neutral-900 outline-none ring-neutral-400 transition focus:ring-2 dark:border-white/15 dark:bg-black/30 dark:text-neutral-100"
            />
          </label>
          <label className="flex flex-col gap-1 text-sm text-neutral-600 dark:text-neutral-300">
            {t("pages.deploy.actions.deployPort")}
            <input
              type="number"
              min={1}
              max={65535}
              value={deployPort}
              onChange={(event) => setDeployPort(event.target.value)}
              className="rounded-lg border border-neutral-300 bg-white px-3 py-2 text-sm text-neutral-900 outline-none ring-neutral-400 transition focus:ring-2 dark:border-white/15 dark:bg-black/30 dark:text-neutral-100"
            />
          </label>
          <label className="flex flex-col gap-1 text-sm text-neutral-600 dark:text-neutral-300">
            {t("pages.deploy.actions.deployUser")}
            <input
              value={deployUser}
              onChange={(event) => setDeployUser(event.target.value)}
              className="rounded-lg border border-neutral-300 bg-white px-3 py-2 text-sm text-neutral-900 outline-none ring-neutral-400 transition focus:ring-2 dark:border-white/15 dark:bg-black/30 dark:text-neutral-100"
            />
          </label>
          <label className="flex flex-col gap-1 text-sm text-neutral-600 dark:text-neutral-300">
            {t("pages.deploy.actions.deployTargetDir")}
            <input
              value={deployTargetDir}
              onChange={(event) => setDeployTargetDir(event.target.value)}
              className="rounded-lg border border-neutral-300 bg-white px-3 py-2 text-sm text-neutral-900 outline-none ring-neutral-400 transition focus:ring-2 dark:border-white/15 dark:bg-black/30 dark:text-neutral-100"
            />
          </label>
          {deployHost.trim() ? (
            <label className="flex flex-col gap-1 text-sm text-neutral-600 dark:text-neutral-300 md:col-span-2">
              {t("pages.deploy.actions.sshPassword")}
              <input
                type="password"
                value={sshPassword}
                onChange={(event) => setSshPassword(event.target.value)}
                className="rounded-lg border border-neutral-300 bg-white px-3 py-2 text-sm text-neutral-900 outline-none ring-neutral-400 transition focus:ring-2 dark:border-white/15 dark:bg-black/30 dark:text-neutral-100"
              />
            </label>
          ) : null}
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
          {deployHost.trim() ? (
            <button
              type="button"
              onClick={handleSetupSshKey}
              disabled={sshKeySettingUp || deploying}
              className="rounded-lg border border-emerald-300 px-3 py-2 text-sm font-medium text-emerald-700 transition hover:bg-emerald-50 disabled:cursor-not-allowed disabled:opacity-60 dark:border-emerald-700 dark:text-emerald-400 dark:hover:bg-emerald-900/20"
            >
              {sshKeySettingUp
                ? t("pages.deploy.actions.settingUpSshKey")
                : t("pages.deploy.actions.setupSshKey")}
            </button>
          ) : null}
        </div>

        {status ? (
          <p
            className={`mt-3 text-sm ${
              status.type === "success"
                ? "text-emerald-600 dark:text-emerald-400"
                : status.type === "warning"
                  ? "text-amber-600 dark:text-amber-400"
                  : "text-rose-600 dark:text-rose-400"
            }`}
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
