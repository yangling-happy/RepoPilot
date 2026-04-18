import { ArrowRightOutlined } from "@ant-design/icons";
import { Button, Col, Flex, Row, Space, Typography, theme } from "antd";
import { useEffect, useState, type CSSProperties } from "react";
import { useTranslation } from "react-i18next";
import { Link } from "react-router-dom";
import styles from "./HomePage.module.css";

export function HomePageView() {
  const { t } = useTranslation();
  const { token } = theme.useToken();
  const [scrollY, setScrollY] = useState(0);
  const [hoveredCard, setHoveredCard] = useState<string | null>(null);

  useEffect(() => {
    const handleScroll = () => setScrollY(window.scrollY);
    window.addEventListener("scroll", handleScroll);
    return () => window.removeEventListener("scroll", handleScroll);
  }, []);

  const containerStyle: CSSProperties = {
    width: "100%",
    maxWidth: 1200,
    margin: "0 auto",
    padding: "0 24px",
  };

  const scrawlDecorStyle: CSSProperties = {
    position: "absolute",
    opacity: 0.1,
    pointerEvents: "none",
    userSelect: "none",
  };

  const cardStyle = (isHovered: boolean): CSSProperties => ({
    border: `2px solid ${token.colorTextBase}`,
    borderRadius: 12,
    padding: 32,
    height: "100%",
    transition: "all 0.3s cubic-bezier(0.34, 1.56, 0.64, 1)",
    transform: isHovered ? "translateY(-8px) scale(1.02)" : "translateY(0)",
    backgroundColor: isHovered ? token.colorBgBase : "transparent",
    boxShadow: isHovered ? `0 12px 24px rgba(0, 0, 0, 0.15)` : "none",
    cursor: "pointer",
  });

  const buttonStyle: CSSProperties = {
    height: 48,
    fontSize: 16,
    borderRadius: 8,
    fontWeight: 600,
  };

  return (
    <div
      className={styles.homePage}
      style={{ 
        backgroundColor: token.colorBgBase,
        minHeight: "100vh", // 铺满视口高度
        display: "flex",
        flexDirection: "column",
      }}
    >
      {/* Hero Section - 添加 flex 占据剩余空间 */}
      <section
        style={{
          position: "relative",
          paddingTop: 80,
          paddingBottom: 60,
          overflow: "hidden",
          flex: 1, // 让 Hero 区域撑满剩余空间
          display: "flex",
          alignItems: "center", // 垂直居中内容
        }}
      >
        <svg
          style={{
            ...scrawlDecorStyle,
            top: -50,
            right: -100,
            width: 300,
            height: 300,
            transform: `translateY(${scrollY * 0.3}px)`,
          }}
          xmlns="http://www.w3.org/2000/svg"
          viewBox="0 0 64 64"
          fill="none"
          stroke={token.colorTextBase}
          strokeWidth="4"
          strokeLinecap="round"
          strokeLinejoin="round"
        >
          <path d="M32 8 C32 8 16 24 16 40 C16 52 24 56 32 56 C40 56 48 52 48 40 C48 24 32 8 32 8 Z" />
          <circle cx="32" cy="36" r="6" />
          <path d="M16 40 L8 52 L16 48 Z" />
          <path d="M48 40 L56 52 L48 48 Z" />
          <path d="M28 56 L32 62 L36 56" />
        </svg>

        <div style={containerStyle}>
          <Space direction="vertical" size={32} style={{ width: "100%" }}>
            <Space direction="vertical" size={16}>
              <Typography.Text
                style={{
                  fontSize: 14,
                  fontWeight: 600,
                  letterSpacing: "0.08em",
                  textTransform: "uppercase",
                  color: token.colorTextSecondary,
                }}
              >
                {t("home.hero.eyebrow")}
              </Typography.Text>
            </Space>

            <Typography.Title
              level={1}
              style={{
                margin: 0,
                letterSpacing: "-0.03em",
                fontSize: 48,
                lineHeight: 1.2,
                fontWeight: 800,
              }}
            >
              {t("home.hero.titleLine1")}
              <br />
              {t("home.hero.titleLine2")}
            </Typography.Title>

            <Typography.Paragraph
              style={{
                marginBottom: 0,
                fontSize: 18,
                maxWidth: 640,
                lineHeight: 1.6,
                color: token.colorTextSecondary,
              }}
            >
              {t("home.hero.subtitle")}
            </Typography.Paragraph>

            <Space wrap size={16} style={{ marginTop: 16 }}>
              <Link to="/documentation">
                <Button
                  type="primary"
                  size="large"
                  style={buttonStyle}
                  icon={<ArrowRightOutlined />}
                  iconPosition="end"
                  className={styles.ctaButton}
                >
                  {t("home.cta.documentation")}
                </Button>
              </Link>
              <Link to="/deploy">
                <Button
                  size="large"
                  style={{
                    ...buttonStyle,
                    borderColor: token.colorTextBase,
                    color: token.colorTextBase,
                  }}
                  className={styles.secondaryButton}
                >
                  {t("home.cta.deploy")}
                </Button>
              </Link>
            </Space>
          </Space>
        </div>
      </section>

      {/* Pipeline Section */}
      <section
        style={{
          paddingTop: 60,
          paddingBottom: 60,
          backgroundColor: token.colorBgContainer,
          borderTop: `1px solid ${token.colorBorder}`,
          borderBottom: `1px solid ${token.colorBorder}`,
        }}
      >
        <div style={containerStyle}>
          <Space direction="vertical" size={32} style={{ width: "100%" }}>
            <div>
              <Typography.Title
                level={3}
                style={{ marginTop: 0, marginBottom: 12 }}
              >
                {t("home.pipeline.title")}
              </Typography.Title>
              <Typography.Text type="secondary">
                {t("home.capabilities.lede")}
              </Typography.Text>
            </div>

            <div
              style={{
                display: "grid",
                gridTemplateColumns: "repeat(auto-fit, minmax(150px, 1fr))",
                gap: 12,
              }}
            >
              {[1, 2, 3, 4, 5].map((step) => (
                <div
                  key={`step-${step}`}
                  className={styles.pipelineStep}
                  style={{
                    padding: 16,
                    border: `1px dashed ${token.colorBorder}`,
                    borderRadius: 8,
                    textAlign: "center",
                    transition: "all 0.3s ease",
                  }}
                >
                  <Typography.Text strong>
                    {t(`home.pipeline.step${step}`)}
                  </Typography.Text>
                </div>
              ))}
            </div>
          </Space>
        </div>
      </section>

      {/* Capabilities Section */}
      <section
        style={{ paddingTop: 80, paddingBottom: 80, position: "relative" }}
      >
        <svg
          style={{
            ...scrawlDecorStyle,
            bottom: -100,
            left: -150,
            width: 400,
            height: 400,
            transform: `translateY(${(scrollY - 800) * 0.2}px)`,
          }}
          xmlns="http://www.w3.org/2000/svg"
          viewBox="0 0 64 64"
          fill="none"
          stroke={token.colorTextBase}
          strokeWidth="4"
          strokeLinecap="round"
          strokeLinejoin="round"
        >
          <path d="M32 8 C32 8 16 24 16 40 C16 52 24 56 32 56 C40 56 48 52 48 40 C48 24 32 8 32 8 Z" />
          <circle cx="32" cy="36" r="6" />
          <path d="M16 40 L8 52 L16 48 Z" />
          <path d="M48 40 L56 52 L48 48 Z" />
          <path d="M28 56 L32 62 L36 56" />
        </svg>

        <div style={containerStyle}>
          <Space direction="vertical" size={48} style={{ width: "100%" }}>
            <div>
              <Typography.Title
                level={2}
                style={{ marginTop: 0, marginBottom: 16 }}
              >
                {t("home.capabilities.title")}
              </Typography.Title>
            </div>

            <Row gutter={[32, 32]}>
              {(["doc", "deploy", "platform"] as const).map((cardType) => (
                <Col key={cardType} xs={24} md={8}>
                  <div
                    className={styles.featureCard}
                    style={cardStyle(hoveredCard === cardType)}
                    onMouseEnter={() => setHoveredCard(cardType)}
                    onMouseLeave={() => setHoveredCard(null)}
                  >
                    <Flex vertical gap={16} style={{ height: "100%" }}>
                      <Typography.Text
                        strong
                        style={{
                          letterSpacing: "0.06em",
                          textTransform: "uppercase",
                          fontSize: 11,
                          color: token.colorTextSecondary,
                        }}
                      >
                        {t(`home.cards.${cardType}.label`)}
                      </Typography.Text>

                      <div style={{ flex: 1 }}>
                        <Typography.Title
                          level={4}
                          style={{ marginTop: 0, marginBottom: 12 }}
                        >
                          {t(`home.cards.${cardType}.title`)}
                        </Typography.Title>
                        <Typography.Paragraph
                          style={{ marginBottom: 0, fontSize: 14 }}
                        >
                          {t(`home.cards.${cardType}.desc`)}
                        </Typography.Paragraph>
                      </div>

                      <Button
                        type="text"
                        size="small"
                        icon={<ArrowRightOutlined />}
                        iconPosition="end"
                        style={{
                          alignSelf: "flex-start",
                          padding: 0,
                          fontWeight: 600,
                          marginTop: 16,
                        }}
                        className={styles.cardLink}
                      >
                        Learn more
                      </Button>
                    </Flex>
                  </div>
                </Col>
              ))}
            </Row>
          </Space>
        </div>
      </section>

      {/* CTA Section */}
      <section
        style={{
          paddingTop: 60,
          paddingBottom: 60,
          backgroundColor: token.colorBgContainer,
          borderTop: `1px solid ${token.colorBorder}`,
          textAlign: "center",
        }}
      >
        <div style={containerStyle}>
          <Space direction="vertical" size={24}>
            <Typography.Title
              level={3}
              style={{ marginTop: 0, marginBottom: 0 }}
            >
              Ready to get started?
            </Typography.Title>
            <Typography.Text type="secondary" style={{ fontSize: 16 }}>
              Transform your documentation and deployment workflow
            </Typography.Text>
            <Space wrap style={{ justifyContent: "center" }}>
              <Link to="/documentation">
                <Button
                  type="primary"
                  size="large"
                  style={buttonStyle}
                  className={styles.ctaButton}
                >
                  {t("home.cta.documentation")}
                </Button>
              </Link>
              <Link to="/deploy">
                <Button
                  size="large"
                  style={{
                    ...buttonStyle,
                    borderColor: token.colorTextBase,
                    color: token.colorTextBase,
                  }}
                  className={styles.secondaryButton}
                >
                  {t("home.cta.deploy")}
                </Button>
              </Link>
            </Space>
          </Space>
        </div>
      </section>
    </div>
  );
}