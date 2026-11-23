import { defineConfig } from '@umijs/max';
import { resolve } from 'node:path';
import routes from './routes';
import defaultSettings from './defaultSettings';
import { siteTitle, siteMetas } from './siteMetadata';

export default defineConfig({
  title: siteTitle,
  plugins: ['./config/html.ts'],
  metas: siteMetas,
  hash: true,
  esbuildMinifyIIFE: true,
  alias: { 'decode-uri-component$': resolve(__dirname, '../src/compat/decode-uri-component.cjs') },
  publicPath: '/',
  routes,
  model: {},
  initialState: {},
  access: {},
  layout: { locale: false, ...defaultSettings },
  antd: {},
  npmClient: 'npm',
  mfsu: false,
  mock: false,
  devtool: false,
  proxy: {
    '/api': { target: process.env.CIPHERGATE_API_TARGET || 'http://localhost:8080', changeOrigin: true },
    '/docs': { target: 'http://localhost:5174', changeOrigin: true },
  },
  define: {
    'process.env.CIPHERGATE_DOCS_URL': process.env.CIPHERGATE_DOCS_URL || process.env.VITE_DOCS_URL || '/docs/',
  },
  links: [{ rel: 'icon', href: '/favicon.svg', type: 'image/svg+xml' }],
});
