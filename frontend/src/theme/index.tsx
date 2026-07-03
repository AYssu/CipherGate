import React, { createContext, useContext, useState, useEffect, useCallback } from 'react';
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

const ThemeContext = createContext<ThemeContextValue>({
  themeKey: 'emerald',
  themePreset: THEME_PRESETS[0],
  themeConfig: THEME_PRESETS[0].config,
  setTheme: () => {},
});

export const useTheme = () => useContext(ThemeContext);

// ─── Storage key ─────────────────────────────────────────────
const STORAGE_KEY = 'ciphergate_theme';

function getStoredTheme(): string {
  try {
    return localStorage.getItem(STORAGE_KEY) || 'emerald';
  } catch {
    return 'emerald';
  }
}

function storeTheme(key: string) {
  try {
    localStorage.setItem(STORAGE_KEY, key);
  } catch {
    // ignore
  }
}

// ─── Provider ────────────────────────────────────────────────
export const ThemeProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [themeKey, setThemeKey] = useState<string>(getStoredTheme);

  const preset = THEME_PRESETS.find((p) => p.key === themeKey) || THEME_PRESETS[0];

  const setTheme = useCallback((key: string) => {
    setThemeKey(key);
    storeTheme(key);
  }, []);

  // Sync across tabs
  useEffect(() => {
    const handler = (e: StorageEvent) => {
      if (e.key === STORAGE_KEY && e.newValue) {
        setThemeKey(e.newValue);
      }
    };
    window.addEventListener('storage', handler);
    return () => window.removeEventListener('storage', handler);
  }, []);

  return (
    <ThemeContext.Provider value={{ themeKey, themePreset: preset, themeConfig: preset.config, setTheme }}>
      {children}
    </ThemeContext.Provider>
  );
};

export default ThemeProvider;
