import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'node:path'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const proxyTarget = env.VITE_API_PROXY_TARGET || 'http://localhost:8080'
  const surface = env.VITE_APP_SURFACE || 'all'
  const defaultPortMap: Record<string, number> = {
    admin: 5173,
    workstation: 5174,
    screen: 5175,
    all: 5173
  }
  const configuredPort = Number.parseInt(env.VITE_PORT || '', 10)
  const port = Number.isNaN(configuredPort) ? (defaultPortMap[surface] || 5173) : configuredPort

  return {
    plugins: [vue()],
    resolve: {
      alias: {
        '@': path.resolve(__dirname, 'src')
      }
    },
    server: {
      host: '0.0.0.0',
      port,
      strictPort: true,
      proxy: {
        '/api': {
          target: proxyTarget,
          changeOrigin: true
        }
      }
    },
    preview: {
      host: '0.0.0.0',
      port,
      strictPort: true
    }
  }
})
