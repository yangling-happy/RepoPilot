import { useTranslation } from "react-i18next";
import { NavLink, useLocation, useSearchParams } from "react-router-dom";

interface SecondaryNavItem {
  path: string;
  labelKey: string;
}

const WORKBENCH_PATHS = new Set(["/dashboard", "/documentation", "/deploy"]);

const SECONDARY_NAV_ITEMS: SecondaryNavItem[] = [
  { path: "/documentation", labelKey: "header.documentation" },
  { path: "/deploy", labelKey: "header.deploy" },
];

export function WorkbenchSubNav() {
  const { t } = useTranslation();
  const { pathname } = useLocation();
  const [searchParams] = useSearchParams();
  const repo = searchParams.get("repo");

  if (!WORKBENCH_PATHS.has(pathname) || !repo) {
    return null;
  }

  const encodedRepo = encodeURIComponent(repo);

  return (
    <div className="border-b border-neutral-200/80 bg-white/80 px-6 py-3 backdrop-blur-sm dark:border-white/10 dark:bg-black/60 md:px-8">
      <div className="mx-auto flex w-full max-w-6xl items-center gap-4">
        <span className="hidden shrink-0 rounded-full border border-neutral-200 bg-neutral-50 px-3 py-1 text-xs font-mono text-neutral-700 dark:border-white/15 dark:bg-white/5 dark:text-neutral-200 md:inline-flex">
          {t("header.activeRepo", { repo })}
        </span>

        <div className="flex min-w-0 flex-1 items-center gap-5 overflow-x-auto whitespace-nowrap">
          {SECONDARY_NAV_ITEMS.map((item) => (
            <NavLink
              key={item.path}
              to={`${item.path}?repo=${encodedRepo}`}
              end
              className={({ isActive }: { isActive: boolean }) =>
                [
                  "text-sm transition-colors",
                  isActive
                    ? "font-semibold text-neutral-950 dark:text-white"
                    : "font-normal text-neutral-600 hover:text-neutral-950 dark:text-neutral-400 dark:hover:text-white",
                ].join(" ")
              }
            >
              {t(item.labelKey)}
            </NavLink>
          ))}
        </div>
      </div>
    </div>
  );
}
