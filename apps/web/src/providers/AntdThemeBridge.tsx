import { ConfigProvider, theme as antTheme } from 'antd'
import enUS from 'antd/locale/en_US'
import zhCN from 'antd/locale/zh_CN'
import { useMemo, useEffect, useState, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { useTheme } from 'next-themes'

type Props = {
  children: ReactNode
}

export function AntdThemeBridge({ children }: Props) {
  const { i18n } = useTranslation()
  const { resolvedTheme } = useTheme()
  const [mounted, setMounted] = useState(false)

  useEffect(() => setMounted(true), [])

  const isDark = mounted ? resolvedTheme === 'dark' : false
  const antdLocale = i18n.language.startsWith('zh') ? zhCN : enUS

  const themeConfig = useMemo(
    () => ({
      algorithm: isDark ? antTheme.darkAlgorithm : antTheme.defaultAlgorithm,
      token: {
        colorPrimary: isDark ? '#ffffff' : '#000000',
        colorBgBase: isDark ? '#000000' : '#ffffff',
        colorText: isDark ? '#ffffff' : '#000000',
        colorTextBase: isDark ? '#ffffff' : '#000000',
        colorBorder: isDark ? '#ffffff' : '#000000',
        colorTextLightSolid: isDark ? '#000000' : '#ffffff',
        colorSplit: isDark ? 'rgba(255, 255, 255, 0.14)' : 'rgba(0, 0, 0, 0.14)',
        colorBgContainer: isDark ? '#000000' : '#ffffff',
        colorBgLayout: isDark ? '#000000' : '#ffffff',
        borderRadius: 6,
      },
      components: {
        Layout: {
          headerBg: isDark ? '#000000' : '#ffffff',
          bodyBg: isDark ? '#000000' : '#ffffff',
          footerBg: isDark ? '#000000' : '#ffffff',
        },
      },
    }),
    [isDark],
  )

  return (
    <ConfigProvider locale={antdLocale} theme={themeConfig}>
      {children}
    </ConfigProvider>
  )
}
