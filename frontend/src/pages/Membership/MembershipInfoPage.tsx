import React from 'react';
import { Card, Typography, Tag, Statistic, Row, Col, Progress, Spin, Space, Grid, Modal, Tooltip } from 'antd';
import {
  CrownOutlined,
  AppstoreOutlined,
  KeyOutlined,
  TeamOutlined,
  CloudOutlined,
  FireOutlined,
  ArrowUpOutlined,
} from '@ant-design/icons';

const { Text } = Typography;

const MembershipInfoPage: React.FC = () => {
  const screens = Grid.useBreakpoint();
  const isMobile = !screens.md;
  const [loading, setLoading] = React.useState(true);
  const [membership, setMembership] = React.useState<any>(null);
  const [level, setLevel] = React.useState<any>(null);
  const [allLevels, setAllLevels] = React.useState<any[]>([]);
  const [showLevelCompare, setShowLevelCompare] = React.useState(false);
  const [inviteEnabled, setInviteEnabled] = React.useState(true);
  const [inviteConfig, setInviteConfig] = React.useState({ rewardAmount: 300, maxInviteCount: 20 });

  React.useEffect(() => {
    Promise.all([
      fetch('/api/user/membership/info', { credentials: 'include' }).then(r => r.json()),
      fetch('/api/membership/levels/public', { credentials: 'include' }).then(r => r.json()),
      fetch('/api/user/invite/stats', { credentials: 'include' }).then(r => r.json()).catch(() => ({ data: {} }))
    ]).then(([memRes, lvlRes, inviteRes]) => {
      const mem = memRes.data;
      setMembership(mem);
      const inviteData = inviteRes?.data || {};
      setInviteEnabled(inviteData.enabled !== false);
      setInviteConfig({
        rewardAmount: inviteData.rewardAmount || 300,
        maxInviteCount: inviteData.maxInviteCount || 20
      });
      if (lvlRes.data) {
        setAllLevels(lvlRes.data.sort((a: any, b: any) => a.level - b.level));
        if (mem) {
          const lvl = lvlRes.data.find((l: any) => l.id === mem.levelId);
          setLevel(lvl);
        }
      }
      setLoading(false);
    }).catch(() => setLoading(false));
  }, []);

  if (loading) return <div style={{ textAlign: 'center', padding: 100 }}><Spin size="large" /></div>;
  if (!membership || !level) return <Card><Text>加载失败</Text></Card>;

  const quotaItems = [
    { icon: <AppstoreOutlined />, name: '应用创建', used: membership.appUsed || 0, total: level.appQuota === -1 ? -1 : (level.appQuota || 0) + (membership.extraAppQuota || 0), color: '#1890ff' },
    { icon: <KeyOutlined />, name: '卡密额度', used: membership.licenseUsed || 0, total: level.licenseQuota === -1 ? -1 : (level.licenseQuota || 0) + (membership.extraLicenseQuota || 0), color: '#52c41a' },
    { icon: <TeamOutlined />, name: '终端用户', used: membership.userRegisterUsed || 0, total: level.userRegisterQuota === -1 ? -1 : (level.userRegisterQuota || 0) + (membership.extraUserRegisterQuota || 0), color: '#722ed1' },
    { icon: <CloudOutlined />, name: '流量额度', used: membership.trafficUsed || 0, total: level.trafficQuota === -1 ? -1 : (level.trafficQuota || 0) + (membership.extraTrafficQuota || 0), color: '#fa8c16', isBytes: true },
  ];

  const formatBytes = (bytes: number) => {
    if (!bytes || bytes === 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
  };

  const formatValue = (v: number | null | undefined, isBytes?: boolean) => {
    const val = v || 0;
    return isBytes ? formatBytes(val) : val.toLocaleString();
  };

  return (
    <div style={{ padding: isMobile ? 12 : 24 }}>
      {/* 会员等级 + 余额 */}
      <Card bordered={false} style={{ marginBottom: 16 }}>
        <Row gutter={[24, 16]} align="middle">
          <Col xs={24} sm={16}>
            <Space size={16} align="center">
              <div style={{
                width: 56,
                height: 56,
                borderRadius: 12,
                background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
              }}>
                <CrownOutlined style={{ fontSize: 28, color: '#fff' }} />
              </div>
              <div>
                <Space size={8} align="center">
                  <Text style={{ fontSize: 20, fontWeight: 600 }}>{level.levelName}</Text>
                  <Tooltip title="点击查看等级对比">
                    <Tag
                      color="purple"
                      style={{ margin: 0, cursor: 'pointer' }}
                      onClick={() => setShowLevelCompare(true)}
                    >
                      Lv.{level.level}
                    </Tag>
                  </Tooltip>
                </Space>
                <div style={{ marginTop: 4 }}>
                  <Text type="secondary" style={{ fontSize: 13 }}>
                    {level.description}
                  </Text>
                </div>
              </div>
            </Space>
          </Col>
          <Col xs={24} sm={8} style={{ textAlign: isMobile ? 'left' : 'right' }}>
            <div>
              <Text type="secondary" style={{ fontSize: 13 }}>账户余额</Text>
              <div style={{ fontSize: 28, fontWeight: 600, color: '#1890ff', lineHeight: 1.2 }}>
                <span style={{ fontSize: 16, fontWeight: 400 }}>¥</span>
                {((membership.balance || 0) / 100).toFixed(2)}
              </div>
            </div>
          </Col>
        </Row>
      </Card>

      {/* 统计卡片 */}
      <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
        {inviteEnabled && (
          <Col xs={12} sm={6}>
            <Card bordered={false} size="small">
              <Statistic title="已邀请人数" value={membership.inviteCount || 0} valueStyle={{ fontSize: 20 }} />
            </Card>
          </Col>
        )}
        <Col xs={12} sm={inviteEnabled ? 6 : 8}>
          <Card bordered={false} size="small">
            <Statistic title="累计签到" value={membership.totalCheckinDays || 0} suffix="天" valueStyle={{ fontSize: 20 }} />
          </Card>
        </Col>
        <Col xs={12} sm={inviteEnabled ? 6 : 8}>
          <Card bordered={false} size="small">
            <Statistic
              title="连续签到"
              value={membership.consecutiveCheckinDays || 0}
              suffix="天"
              valueStyle={{ fontSize: 20, color: membership.consecutiveCheckinDays > 0 ? '#faad14' : undefined }}
              prefix={membership.consecutiveCheckinDays > 0 ? <FireOutlined /> : undefined}
            />
          </Card>
        </Col>
        <Col xs={12} sm={inviteEnabled ? 6 : 8}>
          <Card bordered={false} size="small" hoverable onClick={() => setShowLevelCompare(true)} style={{ cursor: 'pointer' }}>
            <Statistic title="会员等级" value={`Lv.${level.level}`} valueStyle={{ fontSize: 20, color: '#722ed1' }} prefix={<CrownOutlined />} />
          </Card>
        </Col>
      </Row>

      {/* 额度使用详情 */}
      <Card bordered={false} title="额度使用详情" style={{ marginBottom: 16 }}>
        <Row gutter={[24, 24]}>
          {quotaItems.map((item) => {
            const isUnlimited = item.total === -1;
            const percent = isUnlimited ? 0 : Math.min(100, Math.round((item.used / item.total) * 100));
            const remaining = isUnlimited ? '不限' : formatValue(item.total - item.used, item.isBytes);

            return (
              <Col xs={24} sm={12} key={item.name}>
                <div style={{ padding: '12px 0', borderBottom: '1px solid #f0f0f0' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
                    <Space size={8}>
                      <span style={{ color: item.color }}>{item.icon}</span>
                      <Text strong>{item.name}</Text>
                    </Space>
                    <Text type="secondary" style={{ fontSize: 13 }}>
                      {formatValue(item.used, item.isBytes)} / {isUnlimited ? '不限' : formatValue(item.total, item.isBytes)}
                    </Text>
                  </div>
                  <Progress
                    percent={isUnlimited ? 0 : percent}
                    strokeColor={item.color}
                    showInfo={false}
                    size="small"
                  />
                  <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: 4 }}>
                    <Text type="secondary" style={{ fontSize: 12 }}>
                      剩余 <Text style={{ fontSize: 12, fontWeight: 500 }}>{remaining}</Text>
                    </Text>
                    {!isUnlimited && percent >= 80 && (
                      <Text type="danger" style={{ fontSize: 12 }}>
                        <ArrowUpOutlined /> 额度不足
                      </Text>
                    )}
                  </div>
                </div>
              </Col>
            );
          })}
        </Row>
      </Card>

      {/* 等级对比弹窗 */}
      <Modal
        title={
          <Space>
            <CrownOutlined style={{ color: '#722ed1' }} />
            <span>会员等级对比</span>
          </Space>
        }
        open={showLevelCompare}
        onCancel={() => setShowLevelCompare(false)}
        footer={null}
        width={isMobile ? '100%' : 800}
        styles={{ body: { padding: isMobile ? 12 : 24 } }}
        className={isMobile ? 'mobile-modal' : undefined}
      >
        <div style={{ marginBottom: 16, padding: '12px 16px', background: '#f6f0ff', borderRadius: 8, border: '1px solid #d3adf7' }}>
          <Text style={{ color: '#531dab' }}>
            <CrownOutlined style={{ marginRight: 8 }} />
            升级会员等级，解锁更多额度和专属权益
          </Text>
        </div>
        <div style={{ overflowX: 'auto' }}>
          <table style={{ width: '100%', borderCollapse: 'collapse', minWidth: isMobile ? 500 : '100%' }}>
            <thead>
              <tr style={{ borderBottom: '2px solid #f0f0f0' }}>
                <th style={{ padding: '12px 8px', textAlign: 'left', fontWeight: 600, minWidth: 100 }}>等级</th>
                <th style={{ padding: '12px 8px', textAlign: 'center', fontWeight: 600 }}>价格</th>
                <th style={{ padding: '12px 8px', textAlign: 'center', fontWeight: 600 }}>
                  <Space size={4}><AppstoreOutlined />应用</Space>
                </th>
                <th style={{ padding: '12px 8px', textAlign: 'center', fontWeight: 600 }}>
                  <Space size={4}><KeyOutlined />卡密</Space>
                </th>
                <th style={{ padding: '12px 8px', textAlign: 'center', fontWeight: 600 }}>
                  <Space size={4}><TeamOutlined />用户</Space>
                </th>
                <th style={{ padding: '12px 8px', textAlign: 'center', fontWeight: 600 }}>
                  <Space size={4}><CloudOutlined />流量</Space>
                </th>
                <th style={{ padding: '12px 8px', textAlign: 'center', fontWeight: 600 }}>时长</th>
              </tr>
            </thead>
            <tbody>
              {allLevels.map((l: any) => {
                const isCurrent = l.id === level?.id;
                const isHigher = l.level > (level?.level || 0);
                const rowBg = isCurrent ? '#f6f0ff' : isHigher ? '#f6ffed' : '#fafafa';
                const rowBorder = isCurrent ? '2px solid #722ed1' : '1px solid #f0f0f0';

                const formatQuota = (v: number) => v === -1 ? '不限' : v?.toLocaleString() || '0';
                const formatBytes = (bytes: number) => {
                  if (!bytes || bytes === 0) return '0 B';
                  if (bytes === -1) return '不限';
                  const k = 1024;
                  const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
                  const i = Math.floor(Math.log(bytes) / Math.log(k));
                  return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
                };

                return (
                  <tr key={l.id} style={{ background: rowBg, borderBottom: rowBorder }}>
                    <td style={{ padding: '12px 8px' }}>
                      <Space size={8}>
                        <Tag color={isCurrent ? 'purple' : isHigher ? 'green' : 'default'} style={{ margin: 0 }}>
                          Lv.{l.level}
                        </Tag>
                        <Text strong={isCurrent}>{l.levelName}</Text>
                        {isCurrent && <Tag color="purple" style={{ margin: 0, fontSize: 11 }}>当前</Tag>}
                      </Space>
                    </td>
                    <td style={{ padding: '12px 8px', textAlign: 'center' }}>
                      <div>
                        {l.price > 0 ? (
                          <>
                            <Text strong style={{ color: '#52c41a', fontSize: 16 }}>
                              ¥{Math.max(0, l.price - (inviteConfig.rewardAmount / 100 * inviteConfig.maxInviteCount))}
                            </Text>
                            <Text type="secondary" style={{ textDecoration: 'line-through', marginLeft: 4, fontSize: 12 }}>
                              ¥{l.price}
                            </Text>
                          </>
                        ) : (
                          <Text strong style={{ color: '#52c41a', fontSize: 16 }}>免费</Text>
                        )}
                      </div>
                    </td>
                    <td style={{ padding: '12px 8px', textAlign: 'center' }}>
                      {l.appQuota === -1 ? (
                        <Text style={{ color: '#52c41a', fontWeight: 600 }}>不限</Text>
                      ) : (
                        <Text>{l.appQuota || 0}</Text>
                      )}
                    </td>
                    <td style={{ padding: '12px 8px', textAlign: 'center' }}>
                      {l.licenseQuota === -1 ? (
                        <Text style={{ color: '#52c41a', fontWeight: 600 }}>不限</Text>
                      ) : (
                        <Text>{formatQuota(l.licenseQuota)}</Text>
                      )}
                    </td>
                    <td style={{ padding: '12px 8px', textAlign: 'center' }}>
                      {l.userRegisterQuota === -1 ? (
                        <Text style={{ color: '#52c41a', fontWeight: 600 }}>不限</Text>
                      ) : (
                        <Text>{formatQuota(l.userRegisterQuota)}</Text>
                      )}
                    </td>
                    <td style={{ padding: '12px 8px', textAlign: 'center' }}>
                      {l.trafficQuota === -1 ? (
                        <Text style={{ color: '#52c41a', fontWeight: 600 }}>不限</Text>
                      ) : (
                        <Text>{formatBytes(l.trafficQuota)}</Text>
                      )}
                    </td>
                    <td style={{ padding: '12px 8px', textAlign: 'center' }}>
                      <Text>{l.durationDays > 0 ? `${l.durationDays}天` : '永久'}</Text>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
        {level && (
          <div style={{ marginTop: 16, padding: '12px 16px', background: '#fffbe6', borderRadius: 8, border: '1px solid #ffe58f' }}>
            <Text style={{ color: '#ad6800' }}>
              <FireOutlined style={{ marginRight: 8 }} />
              邀请好友注册即可获得 ¥{(inviteConfig.rewardAmount / 100).toFixed(2)}/人余额奖励，邀请满{inviteConfig.maxInviteCount}人可获 ¥{(inviteConfig.rewardAmount / 100 * inviteConfig.maxInviteCount).toFixed(2)}
            </Text>
          </div>
        )}
      </Modal>
    </div>
  );
};

export default MembershipInfoPage;
