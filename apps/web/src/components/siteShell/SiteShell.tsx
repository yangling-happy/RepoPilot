import { Layout, Typography } from "antd";
import { useTranslation } from "react-i18next";
import { Outlet, useLocation } from "react-router-dom";
import { DOC_VIEW_SHELL, WORKBENCH_CONTENT_SHELL } from "../../layout/workbenchLayout";
import { SiteHeader } from "../siteHeader/SiteHeader";
import { WorkbenchSubNav } from "../topNav/WorkbenchSubNav";

export function SiteShell() {
  const { t } = useTranslation();
  const { pathname } = useLocation();
  const isHome = pathname === "/";
  const isDocView = pathname === "/documentation/view";

  return (
    <Layout className="min-h-screen bg-white dark:bg-black">
      <SiteHeader />
      <WorkbenchSubNav />
      <Layout.Content
        className={
          isHome
            ? "w-full flex-1 p-0"
            : isDocView
              ? DOC_VIEW_SHELL
              : WORKBENCH_CONTENT_SHELL
        }
      >
        <Outlet />
      </Layout.Content>
      <Layout.Footer className="border-t border-neutral-200 bg-white px-0 py-10 text-center text-xs leading-relaxed text-neutral-500 dark:border-white/10 dark:bg-black dark:text-neutral-400">
        <Typography.Text className="text-[inherit]">
          {t("footer.line1", { year: new Date().getFullYear() })}
        </Typography.Text>
        <br />
        <Typography.Text className="text-[inherit]">
          {t("footer.line2")}
        </Typography.Text>
      </Layout.Footer>
    </Layout>
  );
}
