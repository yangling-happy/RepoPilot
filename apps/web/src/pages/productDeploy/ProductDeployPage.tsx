import { List, Space, Typography } from 'antd'
import { useTranslation } from 'react-i18next'

export function ProductDeployPage() {
  const { t } = useTranslation()

  const items = [
    <>
      <Typography.Text strong>{t('pages.deploy.items.trigger')}</Typography.Text>{' '}
      <Typography.Text code>POST /api/deploy/trigger</Typography.Text>
    </>,
    <>
      <Typography.Text strong>{t('pages.deploy.items.queryLog')}</Typography.Text>{' '}
      <Typography.Text code>GET /api/deploy/task</Typography.Text>
      {'、'}
      <Typography.Text code>GET /api/deploy/log</Typography.Text>
    </>,
    <>
      <Typography.Text strong>{t('pages.deploy.items.cancel')}</Typography.Text>{' '}
      <Typography.Text code>POST /api/deploy/cancel</Typography.Text>
    </>,
  ]

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <Typography.Title level={2} style={{ marginTop: 0 }}>
        {t('pages.deploy.title')}
      </Typography.Title>
      <Typography.Paragraph>{t('pages.deploy.lede')}</Typography.Paragraph>
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
