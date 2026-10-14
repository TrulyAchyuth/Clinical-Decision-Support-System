/// <reference types="vitest/config" />
import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// During `npm run dev`, requests to /api are forwarded to the Spring Boot
// backend, so the browser talks to one origin and no CORS setup is needed.
// The backend's dev profile listens on 127.0.0.1 only; naming that address
// avoids "localhost" resolving to the IPv6 address ::1, where nothing listens.
const BACKEND_URL = 'http://127.0.0.1:8080'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    // Vite's default, written out: only this computer can open the dev server.
    host: 'localhost',
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
