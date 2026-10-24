import { createContext, useContext } from 'react';
import type { ThemeConfig } from 'antd';

// ─── Theme presets ───────────────────────────────────────────
export interface ThemePreset {
  key: string;
  label: string;
  color: string;
  config: ThemeConfig;
}

const THEME_PRESETS: ThemePreset[] = [
  {
    key: 'emerald',
    label: '翡翠绿',
    color: '#00C9A7',
    config: {
      token: {
        colorPrimary: '#00C9A7',
        colorLink: '#00C9A7',
        colorSuccess: '#52c41a',
        borderRadius: 8,
        fontFamily: "-apple-system, BlinkMacSystemFont, 'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', sans-serif",
      },
    },
  },
  {
    key: 'blue',
    label: '经典蓝',
    color: '#1890ff',
    config: {
      token: {
        colorPrimary: '#1890ff',
        colorLink: '#1890ff',
        colorSuccess: '#52c41a',
        borderRadius: 8,
        fontFamily: "-apple-system, BlinkMacSystemFont, 'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', sans-serif",
      },
    },
  },
  {
    key: 'purple',
    label: '优雅紫',
    color: '#722ed1',
    config: {
      token: {
        colorPrimary: '#722ed1',
        colorLink: '#722ed1',
        colorSuccess: '#52c41a',
        borderRadius: 8,
        fontFamily: "-apple-system, BlinkMacSystemFont, 'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', sans-serif",
      },
    },
  },
  {
    key: 'volcano',
    label: '火山橙',
    color: '#fa541c',
    config: {
      token: {
        colorPrimary: '#fa541c',
        colorLink: '#fa541c',
        colorSuccess: '#52c41a',
        borderRadius: 8,
        fontFamily: "-apple-system, BlinkMacSystemFont, 'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', sans-serif",
      },
    },
  },
];

export { THEME_PRESETS };

// ─── Context ─────────────────────────────────────────────────
interface ThemeContextValue {
  themeKey: string;
  themePreset: ThemePreset;
  themeConfig: ThemeConfig;
  setTheme: (key: string) => void;
}

export const ThemeContext = createContext<ThemeContextValue>({
  themeKey: 'emerald',
  themePreset: THEME_PRESETS[0],
  themeConfig: THEME_PRESETS[0].config,
  setTheme: () => {},
});

export const useTheme = () => useContext(ThemeContext);
