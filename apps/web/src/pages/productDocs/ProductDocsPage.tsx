import { List, Space, Typography } from 'antd'
import { useTranslation } from 'react-i18next'

export function ProductDocsPage() {
  const { t } = useTranslation()

  const items = [
    <>
      <Typography.Text strong>{t('pages.documentation.items.webhook')}</Typography.Text>{' '}
      <Typography.Text code>POST /api/doc/webhook/gitlab</Typography.Text>
      {'、'}
      <Typography.Text code>POST /api/doc/rebuild</Typography.Text>
    </>,
    <>
      <Typography.Text strong>{t('pages.documentation.items.query')}</Typography.Text>{' '}
      <Typography.Text code>GET /api/doc/query</Typography.Text>
    </>,
    <>
      <Typography.Text strong>{t('pages.documentation.items.session')}</Typography.Text>{' '}
      <Typography.Text code>POST /api/session/setGitlabToken</Typography.Text>
    </>,
  ]

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <Typography.Title level={2} style={{ marginTop: 0 }}>
        {t('pages.documentation.title')}
      </Typography.Title>
      <Typography.Paragraph>{t('pages.documentation.lede')}</Typography.Paragraph>
      <List
        split={false}
        dataSource={items}
        renderItem={(item) => (
          <List.Item style={{ paddingInline: 0 }}>
            <Typography.Paragraph style={{ marginBottom: 0 }}>{item}</Typography.Paragraph>
          </List.Item>
        )}
      />
    </Space>
  )
}
