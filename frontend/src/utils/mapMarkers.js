// Leaflet 지도 공통: 타일 설정 + 사진 핀/팝업 (이전엔 Tracking·Review 에 중복).
import L from 'leaflet'

export const TILE_URL = 'https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png'
export const TILE_OPTS = { maxZoom: 19 }
export const DEFAULT_CENTER = [37.5665, 126.978] // 서울
export const DEFAULT_ZOOM = 11
export const ROUTE_COLOR = '#2563eb'

/** 새 지도 생성(attribution 숨김 + OSM 타일). */
export function createMap(el) {
  const map = L.map(el, { attributionControl: false })
  L.tileLayer(TILE_URL, TILE_OPTS).addTo(map)
  return map
}

const ESC = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }
function esc(s) { return String(s).replace(/[&<>"']/g, (c) => ESC[c]) }

/** 사진 URL 검증: 같은 출처 /uploads/... 또는 http(s) 만 허용(스킴 인젝션 방지). 아니면 ''. */
function safeUrl(u) {
  if (!u) return ''
  const s = String(u)
  if (s.startsWith('/uploads/') || s.startsWith('https://') || s.startsWith('http://')) {
    return encodeURI(s).replace(/'/g, '%27').replace(/"/g, '%22')
  }
  return ''
}

/** 사진이 있으면 썸네일 핀, 없으면 기분 이모지/📍 점. */
export function pinIcon(m) {
  const photo = safeUrl(m.photoUrl)
  if (photo) {
    return L.divIcon({
      className: 'photo-pin',
      html: `<div class="pp-img" style="background-image:url('${photo}')"></div><div class="pp-tip"></div>`,
      iconSize: [50, 58], iconAnchor: [25, 58], popupAnchor: [0, -56],
    })
  }
  return L.divIcon({
    className: 'photo-pin',
    html: `<div class="pp-dot">${esc(m.mood || '📍')}</div><div class="pp-tip"></div>`,
    iconSize: [34, 42], iconAnchor: [17, 42], popupAnchor: [0, -40],
  })
}

/** 사진/시간/지역/기분/메모/금액 팝업. 모든 사용자 입력은 이스케이프. t 로 단위 번역. */
export function popupHtml(m, t, fmtDt) {
  const photo = safeUrl(m.photoUrl)
  const parts = []
  if (photo) parts.push(`<img src="${photo}" style="width:180px;border-radius:8px;display:block;margin-bottom:6px"/>`)
  parts.push(`<div style="font-size:12px;color:#64748b">🕐 ${esc(fmtDt(m.recordedAt))}</div>`)
  if (m.place) parts.push(`<div style="font-size:12px;color:#64748b">📍 ${esc(m.place)}</div>`)
  if (m.mood) parts.push(`<div style="font-size:18px">${esc(m.mood)}</div>`)
  if (m.memo) parts.push(`<div style="margin-top:2px">${esc(m.memo)}</div>`)
  if (m.amount != null) {
    parts.push(`<div style="margin-top:2px">💰 ${Number(m.amount).toLocaleString()}${esc(t('원'))}${m.category ? ` (${esc(t(m.category))})` : ''}</div>`)
  }
  return `<div style="min-width:140px">${parts.join('')}</div>`
}
