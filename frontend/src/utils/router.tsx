import React from 'react';
import Home from '../pages/Home';
import Login from '../pages/Login';
import Dashboard from '../pages/Dashboard';
import UserInfo from '../pages/UserInfo';

export const getPageComponent = (path: string) => {
  switch (path) {
    case '/':
      return <Home />;
    case '/login':
      return <Login />;
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