import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { VitePWA } from 'vite-plugin-pwa'

// 백엔드 주소 (dev 프록시 대상). 환경변수로 오버라이드 가능.
const BACKEND = process.env.VITE_BACKEND ?? 'http://localhost:8082'

export default defineConfig({
  // .env 는 저장소 루트에 하나만 둔다(백엔드와 공용). 이걸 지정하지 않으면 Vite 가
  // frontend/.env 만 찾아 VITE_* 변수가 undefined 로 들어온다.
  envDir: '..',
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
      // 개발 중에는 서비스워커 비활성(캐시 때문에 코드 변경이 화면에 안 보이는 문제 방지).
      // 운영 빌드(npm run build)에서는 PWA/SW가 정상 동작한다.
      devOptions: { enabled: false }
    })
  ],
  server: {
    port: 5173,
    // dev 중 /api·OAuth 경로를 백엔드(8082)로 프록시 → CORS 없이 동작
    proxy: {
      '/api': { target: BACKEND, changeOrigin: true },
      '/uploads': { target: BACKEND, changeOrigin: true }, // 기록 사진(서버 로컬 파일) 정적 서빙
      '/oauth2': { target: BACKEND, changeOrigin: true },
      '/login': { target: BACKEND, changeOrigin: true },
      '/logout': { target: BACKEND, changeOrigin: true }
    }
  }
})
