import { Layout, Typography } from "antd";
import { useTranslation } from "react-i18next";
import { Outlet, useLocation } from "react-router-dom";
import { WORKBENCH_PAGE_SHELL } from "../../layout/workbenchLayout";
import { SiteHeader } from "../siteHeader/SiteHeader";
import { WorkbenchSubNav } from "../topNav/WorkbenchSubNav";

export function SiteShell() {
  const { t } = useTranslation();
  const { pathname } = useLocation();
  const isHome = pathname === "/";
  const isDocView = pathname === "/documentation/view";

  return (
    <Layout
      className={
        isDocView
          ? "flex h-screen flex-col overflow-hidden bg-white dark:bg-black"
          : "flex min-h-screen flex-col bg-white dark:bg-black"
      }
    >
      <SiteHeader />
      <WorkbenchSubNav />
      {isDocView ? (
        <div className="flex min-h-0 flex-1 items-stretch">
          <Outlet />
        </div>
      ) : (
        <Layout.Content
          className={
            isHome ? "w-full flex-1 p-0" : WORKBENCH_PAGE_SHELL
          }
        >
          <Outlet />
        </Layout.Content>
      )}
      {isDocView ? null : (
        <Layout.Footer className="shrink-0 border-t border-neutral-200 bg-white px-6 py-10 text-center text-xs leading-relaxed text-neutral-500 dark:border-white/10 dark:bg-black dark:text-neutral-400">
          <Typography.Text className="text-[inherit]">
            {t("footer.line1", { year: new Date().getFullYear() })}
          </Typography.Text>
          <br />
          <Typography.Text className="text-[inherit]">
            {t("footer.line2")}
          </Typography.Text>
        </Layout.Footer>
      )}
    </Layout>
  );
}
