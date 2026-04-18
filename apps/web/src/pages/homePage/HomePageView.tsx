import { FileTextOutlined, RocketOutlined } from "@ant-design/icons";
import { Link } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { HeroScene } from "./HeroScene";
import { DEMO_REPOS } from "./repoMock";

const PIPELINE_STEPS = [1, 2, 3, 4, 5] as const;

export function HomePageView() {
  const { t } = useTranslation();

  return (
    <div className="min-h-[calc(100vh-64px)] bg-white text-neutral-950 dark:bg-black dark:text-neutral-50">
      {/* Hero */}
      <section className="border-b border-neutral-200 dark:border-white/10">
        <div className="mx-auto max-w-[1200px] px-6 py-14 md:px-8 md:py-20">
          <div className="grid items-center gap-12 lg:grid-cols-2 lg:gap-16">
            <div className="max-w-xl">
              <p className="text-[11px] font-semibold uppercase tracking-[0.22em] text-neutral-500 dark:text-neutral-400">
                {t("home.hero.eyebrow")}
              </p>
              <h1 className="mt-5 text-balance text-4xl font-semibold tracking-tight md:text-5xl md:leading-[1.08]">
                {t("home.hero.titleLine1")}
                <span className="block">{t("home.hero.titleLine2")}</span>
              </h1>
              <p className="mt-5 text-pretty text-base leading-relaxed text-neutral-600 dark:text-neutral-400 md:text-lg">
                {t("home.hero.subtitle")}
              </p>
              <div className="mt-8 flex flex-wrap gap-3">
                <Link
                  to="/documentation"
                  className="inline-flex h-11 items-center justify-center rounded-full bg-neutral-950 px-6 text-sm font-medium text-white shadow-sm transition hover:bg-neutral-800 dark:bg-white dark:text-black dark:hover:bg-neutral-200"
                >
                  {t("home.cta.documentation")}
                </Link>
                <Link
                  to="/deploy"
                  className="inline-flex h-11 items-center justify-center rounded-full border border-neutral-300 bg-transparent px-6 text-sm font-medium text-neutral-900 transition hover:border-neutral-950 hover:bg-neutral-50 dark:border-white/25 dark:text-white dark:hover:border-white dark:hover:bg-white/5"
                >
                  {t("home.cta.deploy")}
                </Link>
              </div>
            </div>

            <HeroScene />
          </div>
        </div>
      </section>

      {/* Repo grid */}
      <section className="border-b border-neutral-200 py-14 dark:border-white/10 md:py-16">
        <div className="mx-auto max-w-[1200px] px-6 md:px-8">
          <div className="flex flex-col gap-2 md:flex-row md:items-end md:justify-between">
            <div>
              <h2 className="text-2xl font-semibold tracking-tight md:text-3xl">
                {t("home.repos.title")}
              </h2>
              <p className="mt-2 max-w-2xl text-sm text-neutral-600 dark:text-neutral-400 md:text-base">
                {t("home.repos.subtitle")}
              </p>
            </div>
          </div>

          <div className="mt-10 grid gap-4 sm:grid-cols-2 xl:grid-cols-2">
            {DEMO_REPOS.map((repo) => (
              <article
                key={repo.id}
                className="group relative overflow-hidden rounded-2xl border border-neutral-200 bg-white p-5 shadow-[0_1px_0_rgba(0,0,0,0.04)] transition hover:border-neutral-950 hover:shadow-[0_24px_80px_-48px_rgba(0,0,0,0.35)] dark:border-white/12 dark:bg-black dark:shadow-none dark:hover:border-white dark:hover:shadow-[0_24px_80px_-48px_rgba(255,255,255,0.12)]"
              >
                <div className="flex items-start justify-between gap-4">
                  <div className="min-w-0">
                    <div className="flex flex-wrap items-center gap-2">
                      <span className="truncate font-mono text-[15px] font-medium tracking-tight">
                        {repo.name}
                      </span>
                      <span className="rounded-full border border-neutral-200 px-2 py-0.5 text-[10px] font-semibold uppercase tracking-wide text-neutral-500 dark:border-white/20 dark:text-neutral-400">
                        {t(`home.repos.visibility.${repo.visibility}`)}
                      </span>
                    </div>
                    <p className="mt-2 text-sm text-neutral-600 dark:text-neutral-400">
                      {repo.stack}
                    </p>
                    <p className="mt-3 text-xs text-neutral-400 dark:text-neutral-500">
                      {t("home.repos.lastUpdated", {
                        time: t(`home.repos.times.${repo.timeKey}`),
                      })}
                    </p>
                  </div>
                </div>

                <div className="mt-6 grid grid-cols-1 gap-2 sm:grid-cols-2">
                  <Link
                    to={`/documentation?repo=${encodeURIComponent(repo.id)}`}
                    className="inline-flex h-10 items-center justify-center gap-2 rounded-lg border border-neutral-200 bg-white text-sm font-medium text-neutral-900 transition hover:border-neutral-950 hover:bg-neutral-50 dark:border-white/20 dark:bg-black dark:text-white dark:hover:border-white dark:hover:bg-white/5"
                  >
                    <FileTextOutlined />
                    {t("home.repos.generateDocs")}
                  </Link>
                  <Link
                    to={`/deploy?repo=${encodeURIComponent(repo.id)}`}
                    className="inline-flex h-10 items-center justify-center gap-2 rounded-lg bg-neutral-950 text-sm font-medium text-white transition hover:bg-neutral-800 dark:bg-white dark:text-black dark:hover:bg-neutral-200"
                  >
                    <RocketOutlined />
                    {t("home.repos.deploy")}
                  </Link>
                </div>
              </article>
            ))}
          </div>
        </div>
      </section>

      {/* Pipeline strip */}
      <section className="border-b border-neutral-200 bg-neutral-50 py-12 dark:border-white/10 dark:bg-neutral-950">
        <div className="mx-auto max-w-[1200px] px-6 md:px-8">
          <p className="text-[11px] font-semibold uppercase tracking-[0.22em] text-neutral-500 dark:text-neutral-400">
            {t("home.pipelineStrip.kicker")}
          </p>
          <h3 className="mt-3 text-xl font-semibold tracking-tight md:text-2xl">
            {t("home.pipelineStrip.title")}
          </h3>
          <div className="mt-8 flex flex-wrap gap-2">
            {PIPELINE_STEPS.map((step) => (
              <div
                key={step}
                className="rounded-full border border-neutral-200 bg-white px-4 py-2 text-xs font-medium text-neutral-800 shadow-sm dark:border-white/15 dark:bg-black dark:text-neutral-100"
              >
                {t(`home.pipeline.step${step}`)}
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* Closing CTA */}
      <section className="py-16 md:py-20">
        <div className="mx-auto max-w-[720px] px-6 text-center md:px-8">
          <h3 className="text-2xl font-semibold tracking-tight md:text-3xl">
            {t("home.closing.title")}
          </h3>
          <p className="mt-3 text-neutral-600 dark:text-neutral-400">
            {t("home.closing.subtitle")}
          </p>
          <div className="mt-8 flex flex-wrap justify-center gap-3">
            <Link
              to="/documentation"
              className="inline-flex h-11 items-center justify-center rounded-full bg-neutral-950 px-6 text-sm font-medium text-white transition hover:bg-neutral-800 dark:bg-white dark:text-black dark:hover:bg-neutral-200"
            >
              {t("home.cta.documentation")}
            </Link>
            <Link
              to="/deploy"
              className="inline-flex h-11 items-center justify-center rounded-full border border-neutral-300 px-6 text-sm font-medium text-neutral-900 transition hover:border-neutral-950 hover:bg-neutral-50 dark:border-white/25 dark:text-white dark:hover:border-white dark:hover:bg-white/5"
            >
              {t("home.cta.deploy")}
            </Link>
          </div>
        </div>
      </section>
    </div>
  );
}
