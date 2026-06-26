import React, { useState, useEffect } from 'react';
import {
  Card,
  Typography,
  Tag,
  Space,
  Button,
  List,
  Row,
  Col,
  Grid,
  Spin,
  Empty,
  Divider,
  message,
} from 'antd';
import {
  DownloadOutlined,
  EyeOutlined,
  GithubOutlined,
  QqOutlined,
  PlayCircleOutlined,
  CalendarOutlined,
  PaperClipOutlined,
} from '@ant-design/icons';
import { docApi } from '../services/docService';
import type { DocDetail, DocAttachment } from '../services/docService';

const { Title, Text } = Typography;
const { useBreakpoint } = Grid;

interface DocViewContentProps {
  docId: number;
}

const DocViewContent: React.FC<DocViewContentProps> = ({ docId }) => {
  const screens = useBreakpoint();
  const isMobile = !screens.md;

  const [doc, setDoc] = useState<DocDetail | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetchDoc = async () => {
      setLoading(true);
      try {
        const result = await docApi.getDocDetail(docId);
        setDoc((result as any).data || null);
      } catch (error) {
        console.error('获取文档详情失败:', error);
      } finally {
        setLoading(false);
      }
    };
    if (docId) {
      fetchDoc();
    }
  }, [docId]);

  const handleDownload = async (attachment: DocAttachment) => {
    try {
      await docApi.recordDownload(attachment.id);
      window.open(attachment.fileUrl, '_blank');
    } catch (error) {
      console.error('下载失败:', error);
      message.error('下载失败');
    }
  };

  const formatFileSize = (bytes?: number) => {
    if (!bytes) return '-';
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  };

  const formatDate = (dateString?: string) => {
    if (!dateString) return '-';
    return new Date(dateString).toLocaleDateString('zh-CN', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
    });
  };

  if (loading) {
    return (
      <div style={{ textAlign: 'center', padding: '80px 0' }}>
        <Spin size="large" />
      </div>
    );
  }

  if (!doc) {
    return <Empty description="文档不存在或已被删除" />;
  }

  return (
    <Row gutter={isMobile ? 0 : 24}>
      <Col xs={24} sm={24} md={16} lg={17} style={{ marginBottom: isMobile ? 16 : 0 }}>
        <Card>
          <div style={{ marginBottom: 16 }}>
            <Space direction="vertical" size={8} style={{ width: '100%' }}>
              <Space wrap>
                {doc.categoryName && <Tag color="blue">{doc.categoryName}</Tag>}
                <Space size={4}>
                  <EyeOutlined style={{ color: '#999' }} />
                  <Text type="secondary">{doc.viewCount || 0} 次浏览</Text>
                </Space>
              </Space>
              <Title level={isMobile ? 4 : 3} style={{ margin: 0 }}>{doc.title}</Title>
              <Space wrap size={16}>
                {doc.authorName && (
                  <Space size={4}>
                    <Text type="secondary">作者: {doc.authorName}</Text>
                  </Space>
                )}
                <Space size={4}>
                  <CalendarOutlined style={{ color: '#999' }} />
                  <Text type="secondary">{formatDate(doc.createdAt)}</Text>
                </Space>
              </Space>
            </Space>
          </div>

          <Divider style={{ margin: '16px 0' }} />

          <div
            className="doc-content"
            style={{
              lineHeight: 1.8,
              fontSize: 15,
              color: '#333',
            }}
          >
            <pre style={{
              whiteSpace: 'pre-wrap',
              wordBreak: 'break-word',
              fontFamily: 'inherit',
              margin: 0,
              background: 'transparent',
              border: 'none',
              padding: 0,
            }}>
              {doc.content}
            </pre>
          </div>

          {(doc.authorGithub || doc.authorQq || doc.authorBilibili) && (
            <>
              <Divider style={{ margin: '24px 0 16px' }} />
              <Space wrap size={16}>
                {doc.authorGithub && (
                  <Space size={4}>
                    <GithubOutlined style={{ fontSize: 16 }} />
                    <a href={`https://github.com/${doc.authorGithub}`} target="_blank" rel="noopener noreferrer">
                      {doc.authorGithub}
                    </a>
                  </Space>
                )}
                {doc.authorQq && (
                  <Space size={4}>
                    <QqOutlined style={{ fontSize: 16 }} />
                    <Text>{doc.authorQq}</Text>
                  </Space>
                )}
                {doc.authorBilibili && (
                  <Space size={4}>
                    <PlayCircleOutlined style={{ fontSize: 16 }} />
                    <a href={`https://space.bilibili.com/${doc.authorBilibili}`} target="_blank" rel="noopener noreferrer">
                      {doc.authorBilibili}
                    </a>
                  </Space>
                )}
              </Space>
            </>
          )}
        </Card>
      </Col>

      <Col xs={24} sm={24} md={8} lg={7}>
        <Card
          title={
            <Space>
              <PaperClipOutlined />
              <span>附件 ({doc.attachments?.length || 0})</span>
            </Space>
          }
          bodyStyle={{ padding: isMobile ? 12 : 16 }}
        >
          {doc.attachments && doc.attachments.length > 0 ? (
            <List
              dataSource={doc.attachments}
              renderItem={(attachment) => (
                <List.Item
                  actions={[
                    <Button
                      type="link"
                      icon={<DownloadOutlined />}
                      onClick={() => handleDownload(attachment)}
                    >
                      下载
                    </Button>,
                  ]}
                >
                  <List.Item.Meta
                    title={<Text style={{ fontSize: 13 }}>{attachment.fileName}</Text>}
                    description={
                      <Space size={12}>
                        <Text type="secondary" style={{ fontSize: 12 }}>
                          {formatFileSize(attachment.fileSize)}
                        </Text>
                        <Text type="secondary" style={{ fontSize: 12 }}>
                          {attachment.downloadCount || 0} 次下载
                        </Text>
                      </Space>
                    }
                  />
                </List.Item>
              )}
            />
          ) : (
            <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无附件" />
          )}
        </Card>
      </Col>
    </Row>
  );
};

export default DocViewContent;
