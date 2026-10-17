import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// Public frontend path: http://<host>/front/
// Host nginx strips /front/ and forwards to the frontend container.
const rawBase = process.env.VITE_APP_BASE || '/front/'
const appBase = rawBase.endsWith('/') ? rawBase : `${rawBase}/`

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
      // The backend already runs with context-path /backend, so no rewrite.
      '/backend/api': {
        target: backend,
        changeOrigin: true
      },
      '/backend/ws': {
        target: wsBackend,
        ws: true,
        changeOrigin: true
      }
    }
  },
  preview: {
    host: '0.0.0.0',
    port: 7998,
    strictPort: true
  }
})
