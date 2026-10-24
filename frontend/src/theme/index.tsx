import React, { useState, useEffect, useCallback } from 'react';
import { ThemeContext, THEME_PRESETS } from './context';

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
