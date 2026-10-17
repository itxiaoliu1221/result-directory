import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

const backend = process.env.VITE_BACKEND || 'http://localhost:8080'
const wsBackend = backend.replace(/^http/, 'ws')

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: backend,
        changeOrigin: true
      },
      '/ws': {
        target: wsBackend,
        ws: true,
        changeOrigin: true
      }
    }
  }
})
