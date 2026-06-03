import { useTranslation } from "react-i18next";
import { Link, NavLink, useLocation, useSearchParams } from "react-router-dom";
import {
  DOC_VIEW_SUBNAV_INNER,
  DOC_VIEW_SUBNAV_SHELL,
  WORKBENCH_SUBNAV_INNER,
  WORKBENCH_SUBNAV_SHELL,
} from "../../layout/workbenchLayout";

interface SecondaryNavItem {
  path: string;
  labelKey: string;
}

const WORKBENCH_PATHS = new Set([
  "/dashboard",
  "/documentation",
  "/documentation/view",
  "/deploy",
]);

const SECONDARY_NAV_ITEMS: SecondaryNavItem[] = [
  { path: "/documentation/view", labelKey: "header.documentation" },
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
  const isDocView = pathname === "/documentation/view";

  if (isDocView) {
    return (
      <div className={DOC_VIEW_SUBNAV_SHELL}>
        <div className={DOC_VIEW_SUBNAV_INNER}>
          <nav className="flex min-w-0 flex-1 items-center gap-1.5 text-sm">
            <Link
              to="/dashboard"
              className="text-neutral-500 transition hover:text-neutral-800 dark:text-neutral-400 dark:hover:text-neutral-200"
            >
              {t("header.dashboard")}
            </Link>
            <span className="text-neutral-300 dark:text-neutral-600">/</span>
            <span className="truncate font-mono text-xs text-neutral-700 dark:text-neutral-300">
              {repo}
            </span>
            <span className="text-neutral-300 dark:text-neutral-600">/</span>
            <span className="font-semibold text-neutral-950 dark:text-white">
              {t("header.documentation")}
            </span>
          </nav>
        </div>
      </div>
    );
  }

  return (
    <div className={WORKBENCH_SUBNAV_SHELL}>
      <div className={WORKBENCH_SUBNAV_INNER}>
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
