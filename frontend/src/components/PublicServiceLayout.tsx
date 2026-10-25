import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { ConfigProvider } from 'antd';
import './PublicServiceLayout.css';

type Props = { title: string; description: string; children: ReactNode; help?: ReactNode };

export default function PublicServiceLayout({ title, description, children, help }: Props) {
  return (
    <ConfigProvider theme={{ token: { fontFamily: "'PingFang SC', 'Microsoft YaHei', system-ui, sans-serif", colorPrimary: '#2459b8', colorText: '#20324a', borderRadius: 8, controlHeight: 44, fontSize: 15 } }}>
      <div className="public-service">
        <header className="public-service-header">
          <Link to="/" className="public-service-brand"><img src="/favicon.svg" alt="" />CipherGate</Link>
          <Link to="/">返回首页</Link>
        </header>
        <main className="public-service-main">
          <aside className="public-service-intro">
            <h1>{title}</h1>
            <p>{description}</p>
            {help && <div className="public-service-help">{help}</div>}
          </aside>
          <section className="public-service-panel" aria-label={title}>{children}</section>
        </main>
        <footer className="public-service-footer">CipherGate 应用用户服务</footer>
      </div>
    </ConfigProvider>
  );
}
