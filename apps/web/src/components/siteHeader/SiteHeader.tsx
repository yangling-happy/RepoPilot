import { GithubOutlined, MoonOutlined, SunOutlined } from "@ant-design/icons";
import { Button, Layout, Select, Space, Typography } from "antd";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { Link } from "react-router-dom";
import { useTheme } from "next-themes";
import { TopNav } from "../topNav/TopNav";
import { getGithubUrl } from "../../constants/externalLinks";

const { Header } = Layout;

export function SiteHeader() {
  const { t, i18n } = useTranslation();
  const { resolvedTheme, setTheme } = useTheme();
  const [mounted, setMounted] = useState(false);

  useEffect(() => setMounted(true), []);

  const isDark = mounted ? resolvedTheme === "dark" : false;
  const githubUrl = getGithubUrl();

  const languageValue = i18n.language.startsWith("zh") ? "zh" : "en";

  return (
    <Header className="sticky top-0 z-[1000] flex h-14 items-center gap-6 border-b border-neutral-200/90 bg-white/85 px-4 backdrop-blur-md dark:border-white/10 dark:bg-black/70 md:h-16 md:px-8">
      <Typography.Title
        level={4}
        className="!mb-0 shrink-0 !font-semibold !tracking-tight"
      >
        <Link
          to="/"
          className="text-neutral-950 no-underline transition hover:opacity-80 dark:text-white"
        >
          {t("brand.name")}
        </Link>
      </Typography.Title>

      <TopNav />

      <Space wrap size="middle" className="ml-auto shrink-0">
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
        />

        <Button
          type="text"
          icon={<GithubOutlined />}
          href={githubUrl}
          target="_blank"
          rel="noreferrer noopener"
          aria-label={t("header.github")}
        />
      </Space>
    </Header>
  );
}
