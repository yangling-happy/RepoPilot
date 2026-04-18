import {
  ArrowLeftOutlined,
  FileTextOutlined,
  RocketOutlined,
} from "@ant-design/icons";
import { Button, Tag } from "antd";
import type { MouseEvent } from "react";
import { useEffect, useMemo } from "react";
import { useTranslation } from "react-i18next";
import { Link, useSearchParams } from "react-router-dom";
import { WORKBENCH_REPOS } from "./repoMock";

export function WorkbenchPageView() {
  const { t } = useTranslation();
  const [searchParams, setSearchParams] = useSearchParams();

  const selectedRepoId = searchParams.get("repo");

  const selectedRepo = useMemo(
    () => WORKBENCH_REPOS.find((repo) => repo.id === selectedRepoId),
    [selectedRepoId],
  );

  const openDetails = (repoId: string) => {
    setSearchParams({ repo: repoId });
  };

  const closeDetails = () => {
    setSearchParams({});
  };

  const stopCardNavigation = (e: MouseEvent) => {
    e.stopPropagation();
  };

  useEffect(() => {
    if (!selectedRepoId) return;
    const prev = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") setSearchParams({});
    };
    window.addEventListener("keydown", onKey);
    return () => {
      document.body.style.overflow = prev;
      window.removeEventListener("keydown", onKey);
    };
  }, [selectedRepoId, setSearchParams]);

  const detailRows = selectedRepo
    ? [
        { label: t("pages.dashboard.detail.id"), value: selectedRepo.id },
        { label: t("pages.dashboard.detail.branch"), value: selectedRepo.branch },
        { label: t("pages.dashboard.detail.owner"), value: selectedRepo.owner },
        {
          label: t("pages.dashboard.detail.updatedAt"),
          value: selectedRepo.lastUpdatedAt,
        },
        {
          label: t("pages.dashboard.detail.lastDeploy"),
          value: selectedRepo.lastDeployAt,
        },
        {
          label: t("pages.dashboard.detail.docsEndpoint"),
          value: (
            <a
              href={selectedRepo.docsEndpoint}
              target="_blank"
              rel="noreferrer"
              className="text-neutral-950 underline decoration-neutral-300 underline-offset-4 transition hover:decoration-neutral-950 dark:text-white dark:decoration-white/30 dark:hover:decoration-white"
            >
              {selectedRepo.docsEndpoint}
            </a>
          ),
        },
        {
          label: t("pages.dashboard.detail.summary"),
          value: t(
            `pages.dashboard.repoDescriptions.${selectedRepo.descriptionKey}`,
          ),
        },
      ]
    : [];

  return (
    <div className="w-full">
      <header className="max-w-6xl">

        <h1 className="mt-3 text-3xl font-semibold tracking-tight md:text-4xl">
          {t("pages.dashboard.title")}
        </h1>
        <p className="mt-3 max-w-2xl text-sm leading-relaxed text-neutral-600 dark:text-neutral-400 md:text-base">
          {t("pages.dashboard.lede")}
        </p>
      </header>

      <div className="mt-12 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {WORKBENCH_REPOS.map((repo) => (
          <article
            key={repo.id}
            role="button"
            tabIndex={0}
            aria-label={t("pages.dashboard.openRepoDetails", {
              name: repo.name,
            })}
            className="group cursor-pointer rounded-2xl border border-neutral-200 bg-white p-5 shadow-[0_1px_0_rgba(0,0,0,0.04)] outline-none transition hover:border-neutral-950 hover:shadow-[0_24px_80px_-48px_rgba(0,0,0,0.35)] focus-visible:ring-2 focus-visible:ring-neutral-950 focus-visible:ring-offset-2 dark:border-white/12 dark:bg-black dark:shadow-none dark:hover:border-white dark:hover:shadow-[0_24px_80px_-48px_rgba(255,255,255,0.12)] dark:focus-visible:ring-white dark:focus-visible:ring-offset-black"
            onClick={() => openDetails(repo.id)}
            onKeyDown={(e) => {
              if (e.key === "Enter" || e.key === " ") {
                e.preventDefault();
                openDetails(repo.id);
              }
            }}
          >
            <div className="flex flex-col gap-3">
              <div className="flex items-start justify-between gap-2">
                <span className="truncate font-mono text-[15px] font-medium">
                  {repo.name}
                </span>
                <Tag className="m-0 shrink-0 border-neutral-200 bg-neutral-50 text-neutral-700 dark:border-white/20 dark:bg-white/10 dark:text-neutral-200">
                  {t("pages.dashboard.repoBadge.production")}
                </Tag>
              </div>

              <p className="text-sm leading-snug text-neutral-600 dark:text-neutral-400">
                {t(`pages.dashboard.repoDescriptions.${repo.descriptionKey}`)}
              </p>
              <p className="text-xs text-neutral-500 dark:text-neutral-500">
                {repo.stack}
              </p>

              <div className="flex flex-wrap gap-2">
                <Tag>{t(`home.repos.visibility.${repo.visibility}`)}</Tag>
                <Tag>{repo.branch}</Tag>
              </div>

              <div
                className="flex flex-wrap gap-2 border-t border-neutral-100 pt-4 dark:border-white/10"
                onClick={stopCardNavigation}
              >
                <Link
                  to={`/documentation?repo=${encodeURIComponent(repo.id)}`}
                  className="inline-flex"
                  onClick={stopCardNavigation}
                >
                  <Button
                    size="small"
                    type="default"
                    icon={<FileTextOutlined />}
                    className="border-neutral-300 dark:border-white/25"
                  >
                    {t("pages.dashboard.actions.generateDocs")}
                  </Button>
                </Link>
                <Link
                  to={`/deploy?repo=${encodeURIComponent(repo.id)}`}
                  className="inline-flex"
                  onClick={stopCardNavigation}
                >
                  <Button
                    type="primary"
                    size="small"
                    icon={<RocketOutlined />}
                    className="!bg-neutral-950 !text-white hover:!bg-neutral-800 dark:!bg-white dark:!text-black dark:hover:!bg-neutral-200"
                  >
                    {t("pages.dashboard.actions.deploy")}
                  </Button>
                </Link>
              </div>

              <p className="text-[11px] text-neutral-400 dark:text-neutral-500">
                {t("pages.dashboard.cardHint")}
              </p>
            </div>
          </article>
        ))}
      </div>

      {/* Fullscreen repository detail */}
      {selectedRepo ? (
        <div
          className="fixed inset-0 z-[1100] flex flex-col bg-white dark:bg-black"
          role="dialog"
          aria-modal="true"
          aria-labelledby="dashboard-detail-title"
        >
          <div className="flex h-14 shrink-0 items-center gap-3 border-b border-neutral-200 px-4 dark:border-white/10 md:h-16 md:px-8">
            <button
              type="button"
              onClick={closeDetails}
              className="inline-flex h-9 items-center gap-2 rounded-lg border border-neutral-200 px-3 text-sm font-medium text-neutral-800 transition hover:bg-neutral-50 dark:border-white/15 dark:text-neutral-100 dark:hover:bg-white/5"
            >
              <ArrowLeftOutlined className="text-xs" />
              {t("pages.dashboard.back")}
            </button>
            <span
              id="dashboard-detail-title"
              className="min-w-0 flex-1 truncate font-mono text-sm font-medium md:text-base"
            >
              {selectedRepo.name}
            </span>
          </div>

          <div className="min-h-0 flex-1 overflow-y-auto">
            <div className="mx-auto max-w-3xl px-6 py-10 md:px-8 md:py-14">
              <dl className="divide-y divide-neutral-200 dark:divide-white/10">
                {detailRows.map((row, index) => (
                  <div
                    key={`${row.label}-${index}`}
                    className="grid gap-1 py-5 sm:grid-cols-[minmax(0,200px)_1fr] sm:gap-8 sm:py-6"
                  >
                    <dt className="text-xs font-medium uppercase tracking-wide text-neutral-500 dark:text-neutral-400">
                      {row.label}
                    </dt>
                    <dd className="text-sm text-neutral-900 dark:text-neutral-100">
                      {row.value}
                    </dd>
                  </div>
                ))}
              </dl>

              <div className="mt-12 flex flex-wrap gap-3 border-t border-neutral-200 pt-10 dark:border-white/10">
                <Link
                  to={`/documentation?repo=${encodeURIComponent(selectedRepo.id)}`}
                  onClick={stopCardNavigation}
                >
                  <Button
                    icon={<FileTextOutlined />}
                    className="h-10 border-neutral-300 dark:border-white/25"
                  >
                    {t("pages.dashboard.actions.generateDocs")}
                  </Button>
                </Link>
                <Link
                  to={`/deploy?repo=${encodeURIComponent(selectedRepo.id)}`}
                  onClick={stopCardNavigation}
                >
                  <Button
                    type="primary"
                    icon={<RocketOutlined />}
                    className="h-10 !bg-neutral-950 !text-white hover:!bg-neutral-800 dark:!bg-white dark:!text-black dark:hover:!bg-neutral-200"
                  >
                    {t("pages.dashboard.actions.deploy")}
                  </Button>
                </Link>
              </div>
            </div>
          </div>
        </div>
      ) : null}
    </div>
  );
}
