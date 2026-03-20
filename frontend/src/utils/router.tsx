import Home from '../pages/Home';
import Dashboard from '../pages/Dashboard';
import UserInfo from '../pages/UserInfo';

export const getPageComponent = (path: string) => {
  switch (path) {
    case '/':
      return <Home />;
    case '/login':
      // 登录功能已集成到Home页面的弹窗中，重定向到首页
      return <Home />;
    case '/dashboard':
      return <Dashboard />;
    case '/userinfo':
      return <UserInfo />;
    default:
      return <Home />;
  }
};

export const navigateTo = (path: string) => {
  window.history.pushState({}, '', path);
  window.dispatchEvent(new PopStateEvent('popstate'));
};