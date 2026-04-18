import { Layout, Typography } from 'antd'
import { useTranslation } from 'react-i18next'
import { Outlet } from 'react-router-dom'
import { RouteBreadcrumb } from '../routeBreadcrumb/RouteBreadcrumb'
import { SiteHeader } from '../siteHeader/SiteHeader'

export function SiteShell() {
  const { t } = useTranslation()

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <SiteHeader />
      <Layout.Content style={{ padding: '24px 48px', maxWidth: 1120, margin: '0 auto', width: '100%' }}>
        <RouteBreadcrumb />
        <Outlet />
      </Layout.Content>
      <Layout.Footer style={{ textAlign: 'center' }}>
        <Typography.Text>{t('footer.line1', { year: new Date().getFullYear() })}</Typography.Text>
        <br />
        <Typography.Text>{t('footer.line2')}</Typography.Text>
      </Layout.Footer>
    </Layout>
  )
}
