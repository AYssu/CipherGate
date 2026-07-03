import React, { useState } from 'react';
import { Dropdown } from 'antd';
import { BgColorsOutlined } from '@ant-design/icons';
import { useTheme, THEME_PRESETS } from '../theme';

const ThemeSwitcher: React.FC = () => {
  const { themeKey, setTheme } = useTheme();
  const [hovered, setHovered] = useState(false);

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
      <span
        style={{
          cursor: 'pointer',
          fontSize: 16,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          width: 32,
          height: 32,
          borderRadius: 6,
          transition: 'background 0.2s',
          background: hovered ? 'var(--color-bg-text-hover, #f5f5f5)' : 'transparent',
        }}
        onMouseEnter={() => setHovered(true)}
        onMouseLeave={() => setHovered(false)}
      >
        <BgColorsOutlined />
      </span>
    </Dropdown>
  );
};

export default ThemeSwitcher;
