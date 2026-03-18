import React, { useState, useEffect } from 'react';
import { Card, Typography, Space, Button, Divider, Row, Col, Tag, Avatar } from 'antd';
import { UserOutlined, GithubOutlined, MailOutlined, GlobalOutlined } from '@ant-design/icons';

const { Title, Text, Paragraph } = Typography;

const UserInfo: React.FC = () => {
  const [userInfo, setUserInfo] = useState<any>(null);
  const [basicInfo, setBasicInfo] = useState<any>(null);
  const [emails, setEmails] = useState<any>(null);
  const [repos, setRepos] = useState<any>(null);
  const [loading, setLoading] = useState(false);

  const fetchUserInfo = async () => {
    setLoading(true);
    try {
      // 获取完整用户信息
      const userResponse = await fetch('http://localhost:8080/user', {
        credentials: 'include'
      });
      const userData = await userResponse.json();
      setUserInfo(userData);

      // 获取基本信息
      const basicResponse = await fetch('http://localhost:8080/api/github/user/basic', {
        credentials: 'include'
      });
      const basicData = await basicResponse.json();
      setBasicInfo(basicData);

      // 获取邮箱信息
      const emailResponse = await fetch('http://localhost:8080/api/github/user/emails', {
        credentials: 'include'
      });
      const emailData = await emailResponse.json();
      setEmails(emailData);

      // 获取仓库信息
      const repoResponse = await fetch('http://localhost:8080/api/github/user/repos', {
        credentials: 'include'
      });
      const repoData = await repoResponse.json();
      setRepos(repoData);

    } catch (error) {
      console.error('获取用户信息失败:', error);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchUserInfo();
  }, []);

  const renderJsonData = (data: any, title: string) => (
    <Card title={title} style={{ marginBottom: 16 }}>
      <pre style={{ 
        background: '#f5f5f5', 
        padding: 16, 
        borderRadius: 4, 
        overflow: 'auto',
        fontSize: 12,
        lineHeight: 1.4
      }}>
        {JSON.stringify(data, null, 2)}
      </pre>
    </Card>
  );

  return (
    <div style={{ padding: 24, background: '#f5f5f5', minHeight: '100vh' }}>
      <div style={{ maxWidth: 1200, margin: '0 auto' }}>
        <Title level={2}>
          <GithubOutlined /> GitHub 用户信息详情
        </Title>
        
        <Button 
          type="primary" 
          onClick={fetchUserInfo} 
          loading={loading}
          style={{ marginBottom: 24 }}
        >
          刷新数据
        </Button>

        {userInfo && (
          <Row gutter={[16, 16]}>
            {/* 用户卡片 */}
            <Col span={24}>
              <Card>
                <Row align="middle" gutter={16}>
                  <Col>
                    <Avatar 
                      src={userInfo.avatar_url} 
                      size={80}
                      icon={<UserOutlined />}
                    />
                  </Col>
                  <Col flex={1}>
                    <Title level={3} style={{ margin: 0 }}>
                      {userInfo.name || userInfo.login}
                    </Title>
                    <Space direction="vertical" size="small">
                      <Text type="secondary">
                        <GithubOutlined /> @{userInfo.login}
                      </Text>
                      {userInfo.email && (
                        <Text type="secondary">
                          <MailOutlined /> {userInfo.email}
                        </Text>
                      )}
                      {userInfo.blog && (
                        <Text type="secondary">
                          <GlobalOutlined /> {userInfo.blog}
                        </Text>
                      )}
                      {userInfo.location && (
                        <Tag>{userInfo.location}</Tag>
                      )}
                      {userInfo.company && (
                        <Tag color="blue">{userInfo.company}</Tag>
                      )}
                    </Space>
                  </Col>
                  <Col>
                    <Space direction="vertical" style={{ textAlign: 'center' }}>
                      <div>
                        <Text strong>{userInfo.public_repos || 0}</Text>
                        <br />
                        <Text type="secondary">仓库</Text>
                      </div>
                      <div>
                        <Text strong>{userInfo.followers || 0}</Text>
                        <br />
                        <Text type="secondary">关注者</Text>
                      </div>
                      <div>
                        <Text strong>{userInfo.following || 0}</Text>
                        <br />
                        <Text type="secondary">关注</Text>
                      </div>
                    </Space>
                  </Col>
                </Row>
                
                {userInfo.bio && (
                  <>
                    <Divider />
                    <Paragraph>{userInfo.bio}</Paragraph>
                  </>
                )}
              </Card>
            </Col>

            {/* 原始数据 */}
            <Col span={12}>
              {renderJsonData(userInfo, '完整用户信息 (/user)')}
            </Col>
            
            <Col span={12}>
              {renderJsonData(basicInfo, '基本信息 (/api/github/user/basic)')}
            </Col>
            
            <Col span={12}>
              {renderJsonData(emails, '邮箱信息 (/api/github/user/emails)')}
            </Col>
            
            <Col span={12}>
              {renderJsonData(repos, '仓库信息 (/api/github/user/repos)')}
            </Col>
          </Row>
        )}
      </div>
    </div>
  );
};

export default UserInfo;