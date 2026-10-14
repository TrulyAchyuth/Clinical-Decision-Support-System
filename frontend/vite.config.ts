/// <reference types="vitest/config" />
import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// During `npm run dev`, requests to /api are forwarded to the Spring Boot
// backend, so the browser talks to one origin and no CORS setup is needed.
const BACKEND_URL = 'http://localhost:8080'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    port: 5173,
    proxy: {
      '/api': BACKEND_URL,
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
  },
})
