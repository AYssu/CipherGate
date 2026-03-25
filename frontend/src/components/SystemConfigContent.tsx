import React, { useState, useEffect } from 'react';
import { 
  Card, 
  Typography, 
  Row, 
  Col, 
  Statistic,
  Space,
  Spin,
  message,
  Descriptions,
  Progress,
  Alert
} from 'antd';
import { 
  InfoCircleOutlined,
  ClockCircleOutlined,
  DatabaseOutlined,
  CloudServerOutlined,
  CheckCircleOutlined,
  WarningOutlined
} from '@ant-design/icons';
import { systemApi } from '../services/systemService';
import type { SystemInfo, SystemStatus } from '../services/systemService';

const { Title, Text } = Typography;

const SystemConfigContent: React.FC = () => {
  const [systemInfo, setSystemInfo] = useState<SystemInfo | null>(null);
  const [systemStatus, setSystemStatus] = useState<SystemStatus | null>(null);
  const [loading, setLoading] = useState(true);

  // 获取系统信息
  const fetchSystemInfo = async () => {
    try {
      const response = await systemApi.getSystemInfo();
      setSystemInfo(response.data);
    } catch (error) {
      console.error('获取系统信息失败:', error);
      message.error('获取系统信息失败');
    }
  };

  // 获取系统状态
  const fetchSystemStatus = async () => {
    try {
      const response = await systemApi.getSystemStatus();
      setSystemStatus(response.data);
    } catch (error) {
      console.error('获取系统状态失败:', error);
      message.error('获取系统状态失败');
    }
  };

  // 格式化运行时间
  const formatUptime = (uptime: number) => {
    const days = Math.floor(uptime / (1000 * 60 * 60 * 24));
    const hours = Math.floor((uptime % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60));
    const minutes = Math.floor((uptime % (1000 * 60 * 60)) / (1000 * 60));
    
    if (days > 0) {
      return `${days}天 ${hours}小时 ${minutes}分钟`;
    } else if (hours > 0) {
      return `${hours}小时 ${minutes}分钟`;
    } else {
      return `${minutes}分钟`;
    }
  };

  // 格式化内存大小
  const formatMemorySize = (bytes: number) => {
    const gb = bytes / (1024 * 1024 * 1024);
    return `${gb.toFixed(2)} GB`;
  };

  useEffect(() => {
    const loadData = async () => {
      setLoading(true);
      await Promise.all([fetchSystemInfo(), fetchSystemStatus()]);
      setLoading(false);
    };
    
    loadData();
    
    // 每30秒刷新一次状态数据
    const interval = setInterval(fetchSystemStatus, 30000);
    return () => clearInterval(interval);
  }, []);

  if (loading) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '400px' }}>
        <Spin size="large" />
      </div>
    );
  }

  const memoryUsage = systemInfo?.memory.usagePercent || 0;
  const systemLoad = systemStatus?.systemLoad || 0;

  return (
    <div style={{ padding: 0 }}>
      {/* 系统状态概览 */}
      <Card style={{ marginBottom: 24 }}>
        <Title level={4} style={{ margin: '0 0 16px 0' }}>
          <CloudServerOutlined style={{ marginRight: 8, color: '#1890ff' }} />
          系统状态概览
        </Title>
        
        <Row gutter={[16, 16]}>
          <Col span={6}>
            <Card size="small">
              <Statistic
                title="系统版本"
                value={systemInfo?.application.name}
                suffix={systemInfo?.application.version}
                valueStyle={{ fontSize: 16, color: '#1890ff' }}
              />
            </Card>
          </Col>
          <Col span={6}>
            <Card size="small">
              <Statistic
                title="运行状态"
                value={systemStatus?.status || '正常运行'}
                valueStyle={{ fontSize: 16, color: '#52c41a' }}
                prefix={<CheckCircleOutlined />}
              />
            </Card>
          </Col>
          <Col span={6}>
            <Card size="small">
              <Statistic
                title="系统负载"
                value={systemLoad}
                suffix="%"
                valueStyle={{ 
                  fontSize: 16, 
                  color: systemLoad > 80 ? '#ff4d4f' : systemLoad > 60 ? '#faad14' : '#52c41a'
                }}
                prefix={systemLoad > 80 ? <WarningOutlined /> : <CheckCircleOutlined />}
              />
            </Card>
          </Col>
          <Col span={6}>
            <Card size="small">
              <Statistic
                title="运行时间"
                value={systemInfo?.application.uptime ? formatUptime(systemInfo.application.uptime) : '-'}
                valueStyle={{ fontSize: 14, color: '#722ed1' }}
                prefix={<ClockCircleOutlined />}
              />
            </Card>
          </Col>
        </Row>
      </Card>

      {/* 系统资源监控 */}
      <Card style={{ marginBottom: 24 }}>
        <Title level={4} style={{ margin: '0 0 16px 0' }}>
          <DatabaseOutlined style={{ marginRight: 8, color: '#1890ff' }} />
          系统资源监控
        </Title>
        
        <Row gutter={[24, 24]}>
          <Col span={12}>
            <div style={{ marginBottom: 16 }}>
              <Text strong>内存使用情况</Text>
              <div style={{ marginTop: 8 }}>
                <Progress 
                  percent={memoryUsage} 
                  strokeColor={memoryUsage > 80 ? '#ff4d4f' : memoryUsage > 60 ? '#faad14' : '#52c41a'}
                  format={() => `${memoryUsage}%`}
                />
                <div style={{ marginTop: 8, fontSize: 13, color: '#666' }}>
                  已用: {systemInfo?.memory.used ? formatMemorySize(systemInfo.memory.used) : '-'} / 
                  总计: {systemInfo?.memory.max ? formatMemorySize(systemInfo.memory.max) : '-'}
                </div>
              </div>
            </div>
            
            <div>
              <Text strong>CPU 信息</Text>
              <div style={{ marginTop: 8 }}>
                <Descriptions size="small" column={1}>
                  <Descriptions.Item label="处理器核心">{systemInfo?.operatingSystem.processors} 核</Descriptions.Item>
                  <Descriptions.Item label="系统负载">{systemLoad}%</Descriptions.Item>
                </Descriptions>
              </div>
            </div>
          </Col>
          
          <Col span={12}>
            <div style={{ marginBottom: 16 }}>
              <Text strong>技术栈信息</Text>
              <div style={{ marginTop: 8 }}>
                <Descriptions size="small" column={1}>
                  <Descriptions.Item label="后端框架">{systemInfo?.techStack.backend}</Descriptions.Item>
                  <Descriptions.Item label="前端框架">{systemInfo?.techStack.frontend}</Descriptions.Item>
                  <Descriptions.Item label="数据库">{systemInfo?.techStack.database}</Descriptions.Item>
                  <Descriptions.Item label="认证方式">{systemInfo?.techStack.authentication}</Descriptions.Item>
                </Descriptions>
              </div>
            </div>
          </Col>
        </Row>
      </Card>

      {/* 环境信息 */}
      <Row gutter={[16, 16]}>
        <Col span={12}>
          <Card>
            <Title level={5} style={{ margin: '0 0 16px 0' }}>
              <InfoCircleOutlined style={{ marginRight: 8, color: '#1890ff' }} />
              操作系统
            </Title>
            <Descriptions size="small" column={1}>
              <Descriptions.Item label="系统名称">{systemInfo?.operatingSystem.name}</Descriptions.Item>
              <Descriptions.Item label="系统版本">{systemInfo?.operatingSystem.version}</Descriptions.Item>
              <Descriptions.Item label="系统架构">{systemInfo?.operatingSystem.arch}</Descriptions.Item>
            </Descriptions>
          </Card>
        </Col>
        
        <Col span={12}>
          <Card>
            <Title level={5} style={{ margin: '0 0 16px 0' }}>
              <InfoCircleOutlined style={{ marginRight: 8, color: '#1890ff' }} />
              Java 环境
            </Title>
            <Descriptions size="small" column={1}>
              <Descriptions.Item label="Java 版本">{systemInfo?.java.version}</Descriptions.Item>
              <Descriptions.Item label="Java 厂商">{systemInfo?.java.vendor}</Descriptions.Item>
              <Descriptions.Item label="安装路径" span={2}>
                <Text style={{ fontSize: 12, wordBreak: 'break-all' }}>
                  {systemInfo?.java.home}
                </Text>
              </Descriptions.Item>
            </Descriptions>
          </Card>
        </Col>
      </Row>
    </div>
  );
};

export default SystemConfigContent;