import { Link } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { HeroScene } from "./HeroScene";

const PIPELINE_STEPS = [1, 2, 3, 4, 5] as const;

const sectionCard =
  "rounded-2xl border border-neutral-200 bg-white p-6 shadow-[0_1px_0_rgba(0,0,0,0.04)] dark:border-white/12 dark:bg-black";

export function HomePageView() {
  const { t } = useTranslation();

  return (
    <div className="min-h-[calc(100vh-64px)] bg-neutral-50 text-neutral-950 dark:bg-neutral-950 dark:text-neutral-50">
      <div className="mx-auto max-w-[1200px] space-y-4 px-6 py-8 md:space-y-5 md:px-8 md:py-10">
        {/* Hero */}
        <section
          className={`${sectionCard} opacity-0 animate-home-rise [animation-delay:0ms]`}
        >
          <div className="grid items-center gap-8 lg:grid-cols-2 lg:gap-12">
            <div className="max-w-xl">
              <p className="text-[11px] font-semibold uppercase tracking-[0.22em] text-neutral-500 dark:text-neutral-400">
                {t("home.hero.eyebrow")}
              </p>
              <h1 className="mt-4 text-balance text-3xl font-semibold tracking-tight sm:text-4xl md:text-5xl md:leading-[1.08]">
                {t("home.hero.titleLine1")}
                <span className="block">{t("home.hero.titleLine2")}</span>
              </h1>
              <p className="mt-4 text-pretty text-base leading-relaxed text-neutral-600 dark:text-neutral-400 md:text-lg">
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
                  className="inline-flex h-11 items-center justify-center rounded-full border border-neutral-300 bg-neutral-50 px-6 text-sm font-medium text-neutral-900 transition hover:border-neutral-950 hover:bg-white dark:border-white/25 dark:bg-transparent dark:text-white dark:hover:border-white dark:hover:bg-white/5"
                >
                  {t("home.cta.deploy")}
                </Link>
              </div>
            </div>

            <div className="opacity-0 animate-home-rise [animation-delay:120ms]">
              <HeroScene />
            </div>
          </div>
        </section>

        {/* Dashboard */}
        <section
          className={`${sectionCard} opacity-0 animate-home-rise [animation-delay:90ms]`}
        >
          <div className="flex flex-col gap-6 md:flex-row md:items-center md:justify-between">
            <div className="max-w-xl">
              <h2 className="text-xl font-semibold tracking-tight md:text-2xl">
                {t("home.dashboardEntry.title")}
              </h2>
              <p className="mt-2 text-sm text-neutral-600 dark:text-neutral-400 md:text-base">
                {t("home.dashboardEntry.subtitle")}
              </p>
            </div>
            <Link
              to="/dashboard"
              className="inline-flex h-11 shrink-0 items-center justify-center rounded-xl border border-neutral-200 bg-neutral-950 px-6 text-sm font-medium text-white transition hover:bg-neutral-800 dark:border-white/15 dark:bg-white dark:text-black dark:hover:bg-neutral-200 md:rounded-full"
            >
              {t("home.dashboardEntry.cta")}
            </Link>
          </div>
        </section>

        {/* Pipeline */}
        <section
          className={`${sectionCard} opacity-0 animate-home-rise [animation-delay:180ms]`}
        >
          <p className="text-[11px] font-semibold uppercase tracking-[0.22em] text-neutral-500 dark:text-neutral-400">
            {t("home.pipelineStrip.kicker")}
          </p>
          <h3 className="mt-2 text-lg font-semibold tracking-tight md:text-xl">
            {t("home.pipelineStrip.title")}
          </h3>
          <div className="mt-6 grid grid-cols-2 gap-2 sm:grid-cols-3 md:grid-cols-5">
            {PIPELINE_STEPS.map((step, index) => (
              <div
                key={step}
                style={{ animationDelay: `${260 + index * 55}ms` }}
                className="flex aspect-square max-h-[100px] items-center justify-center rounded-xl border border-neutral-200 bg-neutral-50 px-2 text-center text-[11px] font-medium leading-snug text-neutral-800 opacity-0 shadow-sm animate-home-rise dark:border-white/12 dark:bg-white/[0.04] dark:text-neutral-100 md:text-xs"
              >
                {t(`home.pipeline.step${step}`)}
              </div>
            ))}
          </div>
        </section>

        {/* Closing */}
        <section
          className={`${sectionCard} opacity-0 animate-home-rise [animation-delay:260ms] text-center`}
        >
          <h3 className="text-xl font-semibold tracking-tight md:text-2xl">
            {t("home.closing.title")}
          </h3>
          <p className="mx-auto mt-3 max-w-lg text-sm text-neutral-600 dark:text-neutral-400 md:text-base">
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
              className="inline-flex h-11 items-center justify-center rounded-full border border-neutral-300 bg-neutral-50 px-6 text-sm font-medium text-neutral-900 transition hover:border-neutral-950 hover:bg-white dark:border-white/25 dark:bg-transparent dark:text-white dark:hover:border-white dark:hover:bg-white/5"
            >
              {t("home.cta.deploy")}
            </Link>
          </div>
        </section>
      </div>
    </div>
  );
}
