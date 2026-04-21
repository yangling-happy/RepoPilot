import { UserOutlined } from "@ant-design/icons";
import { Button } from "antd";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router-dom";

export function LoginPlaceholder() {
  const { t } = useTranslation();
  const navigate = useNavigate();

  return (
    <Button
      type="default"
      icon={<UserOutlined />}
      className="rounded-full"
      aria-label={t("header.loginPlaceholder")}
      title={t("header.loginPlaceholder")}
      onClick={() => navigate("/login")}
    >
      {t("header.loginPlaceholder")}
    </Button>
  );
}
