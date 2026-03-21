import React, { useState, useEffect } from 'react';
import { 
  Card, 
  Typography, 
  Row, 
  Col, 
  Switch, 
  Divider, 
  Statistic,
  Space
} from 'antd';
import { 
  SafetyOutlined,
  BellOutlined,
  InfoCircleOutlined
} from '@ant-design/icons';
import { userApi } from '../services/userService';

const { Text } = Typography;

const SystemConfigContent: React.FC = () => {
  const [userCount, setUserCount] = useState(0);

  // 获取用户数量
  const fetchUserCount = async () => {
    try {
      const result = await userApi.getUsers();
      const users = (result as any).data || [];
      setUserCount(users.filter((u: any) => u.status === 1).length);
    } catch (error) {
      console.error('获取用户数量失败:', error);
    }
  };

  useEffect(() => {
    fetchUserCount();
  }, []);

  return (
    <div>
      <Row gutter={[16, 16]}>
        <Col span={24}>
          <Card title={<Space><SafetyOutlined />安全设置</Space>}>
            <Row gutter={[16, 16]}>
              <Col span={12}>
                <Card size="small" title="认证设置">
                  <Space direction="vertical" style={{ width: '100%' }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                      <div>
                        <div>启用双因素认证</div>
                        <Text type="secondary">增强账户安全性</Text>
                      </div>
                      <Switch defaultChecked={false} />
                    </div>
                    
                    <Divider />
                    
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                      <div>
                        <div>强制密码复杂度</div>
                        <Text type="secondary">要求复杂密码策略</Text>
                      </div>
                      <Switch defaultChecked={true} />
                    </div>
                    
                    <Divider />
                    
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                      <div>
                        <div>会话超时</div>
                        <Text type="secondary">自动登出闲置用户</Text>
                      </div>
                      <Switch defaultChecked={true} />
                    </div>
                  </Space>
                </Card>
              </Col>
              
              <Col span={12}>
                <Card size="small" title="审计设置">
                  <Space direction="vertical" style={{ width: '100%' }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                      <div>
                        <div>登录日志记录</div>
                        <Text type="secondary">记录所有登录活动</Text>
                      </div>
                      <Switch defaultChecked={true} />
                    </div>
                    
                    <Divider />
                    
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                      <div>
                        <div>操作日志记录</div>
                        <Text type="secondary">记录用户操作行为</Text>
                      </div>
                      <Switch defaultChecked={true} />
                    </div>
                    
                    <Divider />
                    
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                      <div>
                        <div>API访问日志</div>
                        <Text type="secondary">记录API调用详情</Text>
                      </div>
                      <Switch defaultChecked={false} />
                    </div>
                  </Space>
                </Card>
              </Col>
            </Row>
          </Card>
        </Col>
        
        <Col span={24}>
          <Card title={<Space><BellOutlined />通知设置</Space>}>
            <Row gutter={[16, 16]}>
              <Col span={12}>
                <Card size="small" title="邮件通知">
                  <Space direction="vertical" style={{ width: '100%' }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                      <div>
                        <div>系统通知</div>
                        <Text type="secondary">发送系统状态通知</Text>
                      </div>
                      <Switch defaultChecked={true} />
                    </div>
                    
                    <Divider />
                    
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                      <div>
                        <div>安全警报</div>
                        <Text type="secondary">发送安全事件警报</Text>
                      </div>
                      <Switch defaultChecked={true} />
                    </div>
                    
                    <Divider />
                    
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                      <div>
                        <div>用户活动通知</div>
                        <Text type="secondary">通知用户登录等活动</Text>
                      </div>
                      <Switch defaultChecked={false} />
                    </div>
                  </Space>
                </Card>
              </Col>
              
              <Col span={12}>
                <Card size="small" title="系统通知">
                  <Space direction="vertical" style={{ width: '100%' }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                      <div>
                        <div>维护通知</div>
                        <Text type="secondary">系统维护时通知用户</Text>
                      </div>
                      <Switch defaultChecked={true} />
                    </div>
                    
                    <Divider />
                    
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                      <div>
                        <div>更新通知</div>
                        <Text type="secondary">系统更新时通知管理员</Text>
                      </div>
                      <Switch defaultChecked={true} />
                    </div>
                    
                    <Divider />
                    
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                      <div>
                        <div>错误报告</div>
                        <Text type="secondary">自动发送错误报告</Text>
                      </div>
                      <Switch defaultChecked={false} />
                    </div>
                  </Space>
                </Card>
              </Col>
            </Row>
          </Card>
        </Col>
        
        <Col span={24}>
          <Card title={<Space><InfoCircleOutlined />系统信息</Space>}>
            <Row gutter={[16, 16]}>
              <Col span={6}>
                <Card size="small">
                  <Statistic
                    title="系统版本"
                    value="CipherGate v1.0.0"
                    valueStyle={{ fontSize: 16, color: '#1890ff' }}
                  />
                </Card>
              </Col>
              <Col span={6}>
                <Card size="small">
                  <Statistic
                    title="运行状态"
                    value="正常运行"
                    valueStyle={{ fontSize: 16, color: '#52c41a' }}
                  />
                </Card>
              </Col>
              <Col span={6}>
                <Card size="small">
                  <Statistic
                    title="在线用户"
                    value={userCount}
                    suffix="人"
                    valueStyle={{ fontSize: 16, color: '#722ed1' }}
                  />
                </Card>
              </Col>
              <Col span={6}>
                <Card size="small">
                  <Statistic
                    title="系统负载"
                    value="12%"
                    valueStyle={{ fontSize: 16, color: '#13c2c2' }}
                  />
                </Card>
              </Col>
            </Row>
            
            <Divider />
            
            <Row gutter={[16, 16]}>
              <Col span={12}>
                <Card size="small" title="技术栈">
                  <Space direction="vertical" style={{ width: '100%' }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                      <Text>后端框架</Text>
                      <Text strong>Spring Boot 4.0.3</Text>
                    </div>
                    <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                      <Text>前端框架</Text>
                      <Text strong>React + TypeScript</Text>
                    </div>
                    <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                      <Text>数据库</Text>
                      <Text strong>MySQL 8.0</Text>
                    </div>
                    <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                      <Text>认证方式</Text>
                      <Text strong>OAuth2 + GitHub</Text>
                    </div>
                  </Space>
                </Card>
              </Col>
              
              <Col span={12}>
                <Card size="small" title="系统资源">
                  <Space direction="vertical" style={{ width: '100%' }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                      <Text>CPU使用率</Text>
                      <Text strong style={{ color: '#52c41a' }}>12%</Text>
                    </div>
                    <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                      <Text>内存使用率</Text>
                      <Text strong style={{ color: '#1890ff' }}>45%</Text>
                    </div>
                    <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                      <Text>磁盘使用率</Text>
                      <Text strong style={{ color: '#722ed1' }}>23%</Text>
                    </div>
                    <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                      <Text>网络状态</Text>
                      <Text strong style={{ color: '#52c41a' }}>正常</Text>
                    </div>
                  </Space>
                </Card>
              </Col>
            </Row>
          </Card>
        </Col>
      </Row>
    </div>
  );
};

export default SystemConfigContent;