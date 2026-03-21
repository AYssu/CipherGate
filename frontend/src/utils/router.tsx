import Home from '../pages/Home';
import Dashboard from '../pages/Dashboard';
import SystemManagement from '../pages/SystemManagement';

export const getPageComponent = (path: string) => {
  switch (path) {
    case '/':
      return <Home />;
    case '/login':
      // 登录功能已集成到Home页面的弹窗中，重定向到首页
      return <Home />;
    case '/dashboard':
      return <Dashboard />;
    case '/users':
    case '/roles':
    case '/menus':
    case '/permissions':
    case '/config':
    case '/system':
      return <SystemManagement />;
    default:
      return <Home />;
  }
};

export const navigateTo = (path: string) => {
  window.history.pushState({}, '', path);
  window.dispatchEvent(new PopStateEvent('popstate'));
};