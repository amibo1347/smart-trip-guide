import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { VitePWA } from 'vite-plugin-pwa'

// 백엔드 주소 (dev 프록시 대상). 환경변수로 오버라이드 가능.
const BACKEND = process.env.VITE_BACKEND ?? 'http://localhost:8082'

export default defineConfig({
  plugins: [
    react(),
    VitePWA({
      registerType: 'autoUpdate',
      includeAssets: ['favicon.svg', 'icons/icon-192.png', 'icons/icon-512.png'],
      manifest: {
        name: '스마트 여행 플래너',
        short_name: '여행플래너',
        description: '여행 계획·기록·복기를 잇는 스마트 여행 플래너',
        theme_color: '#2563eb',
        background_color: '#ffffff',
        display: 'standalone',
        start_url: '/',
        icons: [
          { src: 'icons/icon-192.png', sizes: '192x192', type: 'image/png' },
          { src: 'icons/icon-512.png', sizes: '512x512', type: 'image/png' },
          { src: 'icons/icon-512.png', sizes: '512x512', type: 'image/png', purpose: 'maskable' }
        ]
      },
      devOptions: { enabled: true }
    })
  ],
  server: {
    port: 5173,
    // dev 중 /api 호출을 백엔드(8082)로 프록시 → CORS 없이 동작
    proxy: {
      '/api': { target: BACKEND, changeOrigin: true }
    }
  }
})
