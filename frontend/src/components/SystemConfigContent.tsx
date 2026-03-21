import React, { useState, useEffect } from 'react';
import { 
  Card, 
  Typography, 
  Row, 
  Col, 
  Statistic,
  Space,
  Spin,
  message
} from 'antd';
import { 
  InfoCircleOutlined,
  ClockCircleOutlined,
  DatabaseOutlined,
  CloudServerOutlined
} from '@ant-design/icons';
import { systemApi } from '../services/systemService';
import type { SystemInfo, SystemStatus } from '../services/systemService';

const { Text } = Typography;

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

  return (
    <div>
      <Row gutter={[16, 16]}>
        {/* 系统状态概览 */}
        <Col span={24}>
          <Card title={<Space><CloudServerOutlined />系统状态</Space>}>
            <Row gutter={[16, 16]}>
              <Col span={6}>
                <Card size="small">
                  <Statistic
                    title="系统版本"
                    value={systemInfo?.application.name + ' ' + systemInfo?.application.version}
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
                  />
                </Card>
              </Col>
              <Col span={6}>
                <Card size="small">
                  <Statistic
                    title="系统负载"
                    value={systemStatus?.systemLoad || 0}
                    suffix="%"
                    valueStyle={{ 
                      fontSize: 16, 
                      color: (systemStatus?.systemLoad || 0) > 80 ? '#ff4d4f' : '#13c2c2' 
                    }}
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
        </Col>
        
        {/* 系统详细信息 */}
        <Col span={24}>
          <Card title={<Space><InfoCircleOutlined />系统信息</Space>}>
            <Row gutter={[16, 16]}>
              <Col span={12}>
                <Card size="small" title="技术栈">
                  <Space direction="vertical" style={{ width: '100%' }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                      <Text>后端框架</Text>
                      <Text strong>{systemInfo?.techStack.backend}</Text>
                    </div>
                    <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                      <Text>前端框架</Text>
                      <Text strong>{systemInfo?.techStack.frontend}</Text>
                    </div>
                    <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                      <Text>数据库</Text>
                      <Text strong>{systemInfo?.techStack.database}</Text>
                    </div>
                    <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                      <Text>认证方式</Text>
                      <Text strong>{systemInfo?.techStack.authentication}</Text>
                    </div>
                  </Space>
                </Card>
              </Col>
              
              <Col span={12}>
                <Card size="small" title="系统资源">
                  <Space direction="vertical" style={{ width: '100%' }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                      <Text>CPU核心数</Text>
                      <Text strong>{systemInfo?.operatingSystem.processors} 核</Text>
                    </div>
                    <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                      <Text>内存使用率</Text>
                      <Text strong style={{ 
                        color: (systemInfo?.memory.usagePercent || 0) > 80 ? '#ff4d4f' : '#1890ff' 
                      }}>
                        {systemInfo?.memory.usagePercent}%
                      </Text>
                    </div>
                    <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                      <Text>已用内存</Text>
                      <Text strong>{systemInfo?.memory.used ? formatMemorySize(systemInfo.memory.used) : '-'}</Text>
                    </div>
                    <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                      <Text>最大内存</Text>
                      <Text strong>{systemInfo?.memory.max ? formatMemorySize(systemInfo.memory.max) : '-'}</Text>
                    </div>
                  </Space>
                </Card>
              </Col>
            </Row>
          </Card>
        </Col>

        {/* 环境信息 */}
        <Col span={24}>
          <Card title={<Space><DatabaseOutlined />环境信息</Space>}>
            <Row gutter={[16, 16]}>
              <Col span={12}>
                <Card size="small" title="操作系统">
                  <Space direction="vertical" style={{ width: '100%' }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                      <Text>系统名称</Text>
                      <Text strong>{systemInfo?.operatingSystem.name}</Text>
                    </div>
                    <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                      <Text>系统版本</Text>
                      <Text strong>{systemInfo?.operatingSystem.version}</Text>
                    </div>
                    <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                      <Text>系统架构</Text>
                      <Text strong>{systemInfo?.operatingSystem.arch}</Text>
                    </div>
                  </Space>
                </Card>
              </Col>
              
              <Col span={12}>
                <Card size="small" title="Java环境">
                  <Space direction="vertical" style={{ width: '100%' }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                      <Text>Java版本</Text>
                      <Text strong>{systemInfo?.java.version}</Text>
                    </div>
                    <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                      <Text>Java厂商</Text>
                      <Text strong>{systemInfo?.java.vendor}</Text>
                    </div>
                    <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                      <Text>Java路径</Text>
                      <Text strong style={{ fontSize: '12px' }}>{systemInfo?.java.home}</Text>
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