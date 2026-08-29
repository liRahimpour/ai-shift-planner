import { fileURLToPath, URL } from 'node:url'
import react from '@vitejs/plugin-react'
// vitest/config re-exports Vite's defineConfig with the `test` block typed, so the dev
// server config and the test config stay in one file instead of drifting apart in two.
import { defineConfig } from 'vitest/config'

// The backend origin is read at BUILD time from VITE_API_BASE_URL when the frontend is
// deployed as static files behind its own host. In development we instead proxy /api and
// /actuator to the backend, so the browser sees one origin and there is no CORS setup to
// keep in sync between environments.
export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: process.env.VITE_DEV_BACKEND ?? 'http://localhost:8080',
        changeOrigin: true,
      },
      '/actuator': {
        target: process.env.VITE_DEV_BACKEND ?? 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  build: {
    outDir: 'dist',
    sourcemap: true,
  },
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['./src/test-setup.ts'],
    css: false,
    include: ['src/**/*.test.{ts,tsx}'],
  },
})
