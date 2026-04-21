import type { ReactNode } from "react";
import { createElement } from "react";
import { I18nextProvider } from "react-i18next";
import { ThemeProvider } from "next-themes";
import i18n from "../i18n/i18n";
import { AntdThemeBridge } from "./AntdThemeBridge";

type Props = {
  children: ReactNode;
};

export function AppProviders({ children }: Props) {
  return (
    <I18nextProvider i18n={i18n}>
      {createElement(
        ThemeProvider,
        {
          attribute: "class",
          defaultTheme: "system",
          enableSystem: true,
          disableTransitionOnChange: true,
        },
        <AntdThemeBridge>{children}</AntdThemeBridge>,
      )}
    </I18nextProvider>
  );
}
