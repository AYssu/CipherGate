import React, { useState, useEffect } from 'react';
import { ConfigProvider } from 'antd';
import { getPageComponent } from './utils/router';
import './App.css';

const App: React.FC = () => {
  const [currentPath, setCurrentPath] = useState(window.location.pathname);

  useEffect(() => {
    const handlePopState = () => {
      setCurrentPath(window.location.pathname);
    };

    window.addEventListener('popstate', handlePopState);
    return () => window.removeEventListener('popstate', handlePopState);
  }, []);

  return (
    <ConfigProvider>
      {getPageComponent(currentPath)}
    </ConfigProvider>
  );
};

export default App;