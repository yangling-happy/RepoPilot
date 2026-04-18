import { UserOutlined } from "@ant-design/icons";
import { Button } from "antd";
import { useTranslation } from "react-i18next";

export function LoginPlaceholder() {
  const { t } = useTranslation();

  return (
    <Button
      type="default"
      icon={<UserOutlined />}
      className="rounded-full"
      aria-label={t("header.loginPlaceholder")}
      title={t("header.loginPlaceholder")}
    >
      {t("header.loginPlaceholder")}
    </Button>
  );
}
