import { Link } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { motion } from "framer-motion";
import { HeroScene } from "./HeroScene";
import { SpotlightCard } from "./SpotlightCard";

const PIPELINE_STEPS = [1, 2, 3, 4, 5] as const;

const revealVariants = {
  hidden: { opacity: 0, y: 36 },
  visible: { opacity: 1, y: 0 },
} as const;

const revealViewport = {
  once: true,
  margin: "0px 0px -12% 0px",
} as const;

const revealTransition = {
  duration: 0.8,
  ease: [0.21, 0.47, 0.32, 0.98] as const,
};

const pipelineItemVariants = {
  hidden: { opacity: 0, y: 24, scale: 0.96 },
  visible: { opacity: 1, y: 0, scale: 1 },
} as const;

// 更加柔和的卡片：加大圆角，优化深色模式下的边框对比
const glassCard =
  "rounded-[2.5rem] border border-neutral-200/60 bg-white/50 p-10 shadow-sm backdrop-blur-md dark:border-white/5 dark:bg-white/[0.02]";

export function HomePageView() {
  const { t } = useTranslation();

  return (
    <div className="min-h-screen bg-white text-neutral-950 selection:bg-neutral-900 selection:text-white dark:bg-black dark:text-neutral-50">
      <div className="mx-auto max-w-[1200px] px-8 py-16 md:px-12 md:py-24">
        {/* --- Hero Section --- */}
        <section className="mb-32 flex flex-col items-center text-center lg:flex-row lg:text-left">
          <div className="flex-1 opacity-0 animate-home-rise [animation-fill-mode:forwards]">
            {/* 这里的 Eyebrow 提升到 14px，增加间距提升高级感 */}
            <p className="mb-6 text-sm font-bold uppercase tracking-[0.25em] text-neutral-400">
              {t("home.hero.eyebrow")}
            </p>
            <h1 className="text-balance text-5xl font-bold tracking-tight sm:text-6xl xl:text-7xl">
              {t("home.hero.titleLine1")}
              <span className="block text-neutral-400">
                {t("home.hero.titleLine2")}
              </span>
            </h1>
            <p className="mx-auto mt-8 max-w-xl text-lg leading-relaxed text-neutral-500 dark:text-neutral-400 lg:mx-0 lg:text-xl">
              {t("home.hero.subtitle")}
            </p>
            <div className="mt-12 flex flex-wrap justify-center gap-4 lg:justify-start">
              <Link
                to="/documentation"
                className="inline-flex h-14 items-center justify-center rounded-full bg-neutral-950 px-10 text-base font-semibold text-white transition-all hover:bg-neutral-800 active:scale-95 dark:bg-white dark:text-black dark:hover:bg-neutral-200"
              >
                {t("home.cta.documentation")}
              </Link>
              <Link
                to="/deploy"
                className="inline-flex h-14 items-center justify-center rounded-full border border-neutral-200 bg-white px-10 text-base font-semibold transition-all hover:bg-neutral-50 dark:border-white/10 dark:bg-transparent dark:hover:bg-white/5"
              >
                {t("home.cta.deploy")}
              </Link>
            </div>
          </div>

          <div className="mt-20 flex-1 opacity-0 animate-home-rise [animation-delay:200ms] [animation-fill-mode:forwards] lg:mt-0">
            <HeroScene />
          </div>
        </section>

        {/* --- Content Grid --- */}
        <div className="grid gap-8 md:grid-cols-12">
          {/* Dashboard Entry */}
          <motion.section
            className="col-span-12"
            initial="hidden"
            whileInView="visible"
            viewport={revealViewport}
            variants={revealVariants}
            transition={{ ...revealTransition, delay: 0.1 }}
          >
            <SpotlightCard
              className={`${glassCard} [--spotlight-color:rgba(0,0,0,0.06)] dark:[--spotlight-color:rgba(255,255,255,0.06)] flex flex-col justify-between gap-8 lg:flex-row lg:items-center`}
            >
              <div className="max-w-2xl">
                <h2 className="text-3xl font-semibold tracking-tight md:text-4xl">
                  {t("home.dashboardEntry.title")}
                </h2>
                <p className="mt-4 text-base leading-relaxed text-neutral-500 dark:text-neutral-400 md:text-lg">
                  {t("home.dashboardEntry.subtitle")}
                </p>
              </div>
              <Link
                to="/dashboard"
                className="inline-flex h-14 shrink-0 items-center justify-center rounded-2xl bg-neutral-950 px-8 text-base font-bold text-white transition-all hover:scale-[1.02] active:scale-95 dark:bg-white dark:text-black lg:rounded-full"
              >
                {t("home.dashboardEntry.cta")}
              </Link>
            </SpotlightCard>
          </motion.section>

          {/* Pipeline */}
          <motion.section
            className={`${glassCard} col-span-12`}
            initial="hidden"
            whileInView="visible"
            viewport={revealViewport}
            variants={revealVariants}
            transition={{ ...revealTransition, delay: 0.2 }}
          >
            <div className="mb-12">
              <p className="text-xs font-bold uppercase tracking-[0.2em] text-neutral-400">
                {t("home.pipelineStrip.kicker")}
              </p>
              <h3 className="mt-3 text-2xl font-semibold md:text-3xl">
                {t("home.pipelineStrip.title")}
              </h3>
            </div>

            <div className="grid grid-cols-1 gap-4 sm:grid-cols-3 md:grid-cols-5">
              {PIPELINE_STEPS.map((step, index) => (
                <motion.div
                  key={step}
                  initial="hidden"
                  whileInView="visible"
                  viewport={revealViewport}
                  variants={pipelineItemVariants}
                  transition={{
                    duration: 0.6,
                    ease: [0.22, 1, 0.36, 1],
                    delay: 0.15 + index * 0.08,
                  }}
                  className="group relative flex flex-col items-center justify-center rounded-[2rem] border border-neutral-100 bg-neutral-50/50 p-8 transition-all duration-500 hover:-translate-y-2 hover:border-neutral-300 hover:bg-white hover:shadow-[0_20px_40px_-15px_rgba(0,0,0,0.1)] dark:border-white/5 dark:bg-white/[0.01] dark:hover:border-white/20 dark:hover:bg-white/[0.05] dark:hover:shadow-[0_20px_40px_-15px_rgba(255,255,255,0.05)]"
                >
                  <span className="mb-3 text-sm font-black text-neutral-300 transition-colors group-hover:text-neutral-950 dark:text-neutral-700 dark:group-hover:text-white">
                    {t("home.pipeline.stepBadge", {
                      code: String(step).padStart(2, "0"),
                    })}
                  </span>
                  <div className="text-center text-sm font-bold leading-snug tracking-tight text-neutral-700 transition-colors group-hover:text-neutral-950 dark:text-neutral-200 dark:group-hover:text-white md:text-base">
                    {t(`home.pipeline.step${step}`)}
                  </div>
                </motion.div>
              ))}
            </div>
          </motion.section>

          {/* Closing Footer */}
          <motion.section
            className="col-span-12 py-32 text-center"
            initial="hidden"
            whileInView="visible"
            viewport={revealViewport}
            variants={revealVariants}
            transition={{ ...revealTransition, delay: 0.15 }}
          >
            <h3 className="text-4xl font-bold tracking-tight md:text-5xl">
              {t("home.closing.title")}
            </h3>
            <p className="mx-auto mt-6 max-w-lg text-lg text-neutral-500 dark:text-neutral-400">
              {t("home.closing.subtitle")}
            </p>
            <div className="mt-12 flex items-center justify-center gap-8">
              <Link
                to="/documentation"
                className="text-base font-bold text-neutral-950 underline decoration-neutral-300 decoration-2 underline-offset-8 transition-all hover:decoration-neutral-950 dark:text-white dark:decoration-neutral-700 dark:hover:decoration-white"
              >
                {t("home.cta.documentation")}
              </Link>
              <Link
                to="/deploy"
                className="text-base font-bold text-neutral-500 transition-colors hover:text-neutral-950 dark:hover:text-white"
              >
                {t("home.cta.deploy")}
              </Link>
            </div>
          </motion.section>
        </div>
      </div>
    </div>
  );
}
