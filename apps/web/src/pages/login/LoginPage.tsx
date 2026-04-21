import { Button } from "antd";
import { useTranslation } from "react-i18next";
import { Link } from "react-router-dom";
import { getGitLabOAuthLoginUrl } from "../../config/auth";
import { GitLabMark } from "./GitLabMark";

const glassCard =
  "rounded-[2.5rem] border border-neutral-200/60 bg-white/50 p-10 shadow-sm backdrop-blur-md dark:border-white/5 dark:bg-white/[0.02]";

export function LoginPage() {
  const { t } = useTranslation();
  const oauthHref = getGitLabOAuthLoginUrl();

  return (
    <div className="mx-auto max-w-lg pb-24 pt-4 text-neutral-950 dark:text-neutral-50 md:pt-10">
      <p className="mb-4 text-xs font-bold uppercase tracking-[0.25em] text-neutral-400">
        {t("login.kicker")}
      </p>
      <h1 className="text-balance text-3xl font-bold tracking-tight md:text-4xl">
        {t("login.title")}
      </h1>
      <p className="mt-4 text-base leading-relaxed text-neutral-600 dark:text-neutral-400">
        {t("login.subtitle")}
      </p>

      <div className={`${glassCard} mt-12`}>
        <p className="text-sm leading-relaxed text-neutral-600 dark:text-neutral-400">
          {t("login.gitlabHint")}
        </p>
        <Button
          type="primary"
          size="large"
          href={oauthHref}
          className="mt-8 !flex !h-14 !w-full !items-center !justify-center !gap-3 !rounded-2xl !border-0 !bg-[#FC6D26] !px-6 !text-base !font-semibold !text-white hover:!bg-[#e24329] dark:!bg-[#FC6D26] dark:hover:!bg-[#e24329]"
          icon={<GitLabMark className="h-6 w-6 text-white" />}
        >
          {t("login.gitlabCta")}
        </Button>
        <p className="mt-6 text-center text-xs leading-relaxed text-neutral-500 dark:text-neutral-500">
          {t("login.redirectNote")}
        </p>
      </div>

      <p className="mt-10 text-center text-sm text-neutral-500 dark:text-neutral-400">
        <Link
          to="/"
          className="font-semibold text-neutral-950 underline decoration-neutral-300 decoration-2 underline-offset-4 transition hover:decoration-neutral-950 dark:text-white dark:decoration-neutral-600 dark:hover:decoration-white"
        >
          {t("login.backHome")}
        </Link>
      </p>
    </div>
  );
}
