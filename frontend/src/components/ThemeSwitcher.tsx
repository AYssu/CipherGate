import React from 'react';
import { Button, Dropdown } from 'antd';
import { BgColorsOutlined } from '@ant-design/icons';
import { useTheme, THEME_PRESETS } from '../theme/context';

const ThemeSwitcher: React.FC = () => {
  const { themeKey, setTheme } = useTheme();

  const items = THEME_PRESETS.map((p) => ({
    key: p.key,
    label: (
      <span style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
        <span
          style={{
            width: 14,
            height: 14,
            borderRadius: '50%',
            background: p.color,
            display: 'inline-block',
            border: themeKey === p.key ? '2px solid var(--ant-color-text, #333)' : '2px solid transparent',
            flexShrink: 0,
          }}
        />
        {p.label}
        {themeKey === p.key && (
          <span style={{ color: 'var(--ant-color-text-secondary, #999)', fontSize: 12, marginLeft: 4 }}>✓</span>
        )}
      </span>
    ),
    onClick: () => setTheme(p.key),
  }));

  return (
    <Dropdown menu={{ items }} placement="bottomRight" trigger={['click']}>
      <Button type="text" aria-label="切换主题" icon={<BgColorsOutlined />} />
    </Dropdown>
  );
};

export default ThemeSwitcher;
