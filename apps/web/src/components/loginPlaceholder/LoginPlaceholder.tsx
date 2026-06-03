import { UserOutlined } from "@ant-design/icons";
import { Avatar, Button, Dropdown } from "antd";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../../hooks/useAuth";

export function LoginPlaceholder() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { user, logout } = useAuth();

  if (user) {
    return (
      <Dropdown
        menu={{
          items: [
            {
              key: "username",
              label: user.name || user.username,
              disabled: true,
            },
            { type: "divider" as const },
            {
              key: "logout",
              label: t("header.logout"),
              onClick: () => logout(),
            },
          ],
        }}
        trigger={["click"]}
      >
        <Button type="text" className="flex items-center gap-2 rounded-full">
          <Avatar
            size="small"
            src={user.avatarUrl}
            icon={<UserOutlined />}
          />
          <span className="hidden sm:inline">{user.name || user.username}</span>
        </Button>
      </Dropdown>
    );
  }

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
