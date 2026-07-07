import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    host: '0.0.0.0',
    proxy: {
      '/api': {
        target: 'http://localhost:8080', // 后端服务地址
        changeOrigin: true,
        // 后端接口路径包含 /api 前缀，所以不需要 rewrite
        // rewrite: (path) => path.replace(/^\/api/, '')
      },
      // 文档服务代理（开发环境）
      '/docs': {
        target: 'http://localhost:5174',
        changeOrigin: true,
        // 不重写路径，保持 /docs/xxx
      }
    }
  },
})
