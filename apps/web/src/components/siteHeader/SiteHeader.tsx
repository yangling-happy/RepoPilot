import { GithubOutlined, MoonOutlined, SunOutlined } from "@ant-design/icons";
import { Button, Layout, Select, Space, Typography, theme } from "antd";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { Link, useLocation } from "react-router-dom";
import { useTheme } from "next-themes";

const { Header } = Layout;

export function SiteHeader() {
  const { t, i18n } = useTranslation();
  const { resolvedTheme, setTheme } = useTheme();
  const { pathname } = useLocation();
  const [mounted, setMounted] = useState(false);
  const { token } = theme.useToken();

  useEffect(() => setMounted(true), []);

  const isDark = mounted ? resolvedTheme === "dark" : false;


  const languageValue = i18n.language.startsWith("zh") ? "zh" : "en";

  const isActive = (path: string) => pathname === path;

  return (
    <Header
      style={{
        display: "flex",
        alignItems: "center",
        gap: 24,
        paddingInline: 24,
        borderBottom: `1px solid ${token.colorBorder}`,
      }}
    >
      {/* 品牌名称：将 level 从 4 改为 3，字号变大 */}
      <Typography.Title level={3} style={{ margin: 0 }}>
        <Link to="/" style={{ color: "inherit", textDecoration: "none" }}>
          {t("brand.name")}
        </Link>
      </Typography.Title>

      {/* 导航菜单：添加 fontSize: '16px' 使字体变大 */}
      <Space size="large" style={{ flex: 1, fontSize: '16px' }}>
        <Link
          to="/"
          style={{
            color: isActive("/") ? token.colorPrimary : token.colorText,
            textDecoration: "none",
            fontWeight: isActive("/") ? 600 : 400,
            fontSize: '16px', // 字体变大
          }}
        >
          {t("header.home")}
        </Link>
        <Link
          to="/documentation"
          style={{
            color: isActive("/documentation")
              ? token.colorPrimary
              : token.colorText,
            textDecoration: "none",
            fontWeight: isActive("/documentation") ? 600 : 400,
            fontSize: '16px', // 字体变大
          }}
        >
          {t("header.documentation")}
        </Link>
        <Link
          to="/deploy"
          style={{
            color: isActive("/deploy") ? token.colorPrimary : token.colorText,
            textDecoration: "none",
            fontWeight: isActive("/deploy") ? 600 : 400,
            fontSize: '16px', // 字体变大
          }}
        >
          {t("header.deploy")}
        </Link>
      </Space>

      {/* 右侧控制区：图标字体改为 20px，比之前更大 */}
      <Space wrap size="middle">
        <Select
          aria-label={t("header.language")}
          value={languageValue}
          variant="borderless"
          popupMatchSelectWidth={false}
          options={[
            { value: "zh", label: "中文" },
            { value: "en", label: "English" },
          ]}
          onChange={(lng: string) => void i18n.changeLanguage(lng)}
        />

        <Button
          type="text"
          icon={isDark ? <SunOutlined /> : <MoonOutlined />}
          disabled={!mounted}
          onClick={() => setTheme(isDark ? "light" : "dark")}
          aria-label={t("header.theme")}
          title={isDark ? "Light Mode" : "Dark Mode"}
          style={{ fontSize: '20px' }} // 图标变大
        />

        <Button
          type="text"
          icon={<GithubOutlined />}
          href="https://github.com/yangling-happy/RepoPilot"
          target="_blank"
          rel="noreferrer noopener"
          aria-label={t("header.github")}
          style={{ fontSize: '20px' }} // 图标变大
        />
      </Space>
    </Header>
  );
}