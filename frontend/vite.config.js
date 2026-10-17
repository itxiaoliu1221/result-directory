import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// Production path: http://<host>/pick/
// The frontend container serves static files at /, while the host nginx
// strips the /pick prefix before forwarding to the container.
const rawBase = process.env.VITE_APP_BASE || '/pick/'
const appBase = rawBase.endsWith('/') ? rawBase : `${rawBase}/`
const appPrefix = appBase.replace(/\/$/, '')

const backend = process.env.VITE_DEV_BACKEND || 'http://localhost:7999'
const wsBackend = backend.replace(/^http/, 'ws')

export default defineConfig({
  base: appBase,
  plugins: [vue()],
  server: {
    host: '0.0.0.0',
    port: 7998,
    strictPort: true,
    proxy: {
      [`${appPrefix}/api`]: {
        target: backend,
        changeOrigin: true,
        rewrite: (path) => path.replace(`${appPrefix}/api`, '/api')
      },
      [`${appPrefix}/ws`]: {
        target: wsBackend,
        ws: true,
        changeOrigin: true,
        rewrite: (path) => path.replace(`${appPrefix}/ws`, '/ws')
      }
    }
  },
  preview: {
    host: '0.0.0.0',
    port: 7998,
    strictPort: true
  }
})
