import { Space } from "antd";
import { useTranslation } from "react-i18next";
import { NavLink, useLocation } from "react-router-dom";

interface NavItem {
  path: string;
  labelKey: string;
}

const NAV_ITEMS: NavItem[] = [
  { path: "/", labelKey: "header.home" },
  { path: "/dashboard", labelKey: "header.dashboard" },
];

export function TopNav() {
  const { t } = useTranslation();
  const { pathname } = useLocation();

  const isDashboardRoute =
    pathname === "/dashboard" ||
    pathname === "/documentation" ||
    pathname === "/deploy";

  return (
    <Space
      size="large"
      className="min-w-0 flex-1 overflow-x-auto whitespace-nowrap md:overflow-visible"
      style={{ fontSize: 14 }}
    >
      {NAV_ITEMS.map((item) => (
        <NavLink
          key={item.path}
          to={item.path}
          end={item.path === "/"}
          className={({ isActive }: { isActive: boolean }) =>
            {
              const active =
                item.path === "/dashboard" ? isActive || isDashboardRoute : isActive;

              return [
                "text-sm transition-colors",
                active
                  ? "font-semibold text-neutral-950 dark:text-white"
                  : "font-normal text-neutral-600 hover:text-neutral-950 dark:text-neutral-400 dark:hover:text-white",
              ].join(" ");
            }
          }
        >
          {t(item.labelKey)}
        </NavLink>
      ))}
    </Space>
  );
}
