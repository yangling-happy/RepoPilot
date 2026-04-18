import { Breadcrumb } from "antd";
import { Link, useLocation } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { BREADCRUMB_BY_PATH } from "../../constants/breadcrumbRoutes";

export function RouteBreadcrumb() {
  const { pathname } = useLocation();
  const { t } = useTranslation();
  const segments = BREADCRUMB_BY_PATH[pathname] ?? BREADCRUMB_BY_PATH["/"];

  const items = segments
    .map((segment, index) => {
      const isLast = index === segments.length - 1;
      if (isLast) return null;
      const title = segment.path ? (
        <Link to={segment.path}>{t(segment.translationKey)}</Link>
      ) : (
        t(segment.translationKey)
      );
      return { title };
    })
    .filter(
      (item): item is { title: string | React.ReactElement } => item !== null,
    );

  return items.length > 0 ? (
    <Breadcrumb items={items} style={{ marginBottom: 24 }} />
  ) : null;
}
