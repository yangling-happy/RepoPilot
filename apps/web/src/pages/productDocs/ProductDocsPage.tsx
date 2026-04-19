import { useMemo } from "react";
import { useTranslation } from "react-i18next";
import { useSearchParams } from "react-router-dom";
import { VirtualTerminalPanel } from "../../components/virtualTerminal/VirtualTerminalPanel";

export function ProductDocsPage() {
  const { t } = useTranslation();
  const [params] = useSearchParams();
  const repo = params.get("repo");

  const bootLines = useMemo(
    () => [t("pages.documentation.terminal.line1"), t("pages.documentation.terminal.line2")],
    [t],
  );

  const sections = [
    {
      label: t("pages.documentation.items.webhook"),
      codes: ["POST /api/doc/webhook/gitlab", "POST /api/doc/rebuild"],
    },
    {
      label: t("pages.documentation.items.query"),
      codes: ["GET /api/doc/query"],
    },
    {
      label: t("pages.documentation.items.session"),
      codes: ["POST /api/session/setGitlabToken"],
    },
  ];

  return (
    <div className="mx-auto max-w-[1200px] pb-20 pt-2 text-neutral-950 dark:text-neutral-50">
      <h1 className="mt-0 text-3xl font-semibold tracking-tight md:text-4xl">
        {t("pages.documentation.title")}
      </h1>

      {repo ? (
        <div className="mt-5 inline-flex max-w-full rounded-full border border-neutral-200 bg-neutral-50 px-4 py-1.5 font-mono text-xs text-neutral-700 dark:border-white/15 dark:bg-white/[0.06] dark:text-neutral-200">
          {t("pages.documentation.contextRepo", { repo })}
        </div>
      ) : null}

      <p
        className={`text-pretty text-base leading-relaxed text-neutral-600 dark:text-neutral-400 ${repo ? "mt-8" : "mt-6"}`}
      >
        {t("pages.documentation.lede")}
      </p>

      <div className="mt-14">
        <VirtualTerminalPanel
          title={t("pages.documentation.terminal.title")}
          subtitle={t("pages.documentation.terminal.subtitle")}
          bootLines={bootLines}
        />
      </div>

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
