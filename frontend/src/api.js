// API 호출 래퍼. dev에서는 vite 프록시(/api → 8082), prod에서는 동일 출처 또는 VITE_API_BASE 사용.
const BASE = import.meta.env.VITE_API_BASE ?? ''

// 401(미인증) 발생 시 호출될 전역 핸들러 — App이 로그인 화면으로 전환하도록 등록.
let unauthorizedHandler = null
export function setUnauthorizedHandler(fn) { unauthorizedHandler = fn }

async function request(method, path, body) {
  const res = await fetch(BASE + path, {
    method,
    headers: body ? { 'Content-Type': 'application/json' } : undefined,
    body: body ? JSON.stringify(body) : undefined,
    credentials: 'include', // 세션 쿠키 포함 (소셜 로그인 상태 유지)
  })
  const text = await res.text()
  const data = text ? JSON.parse(text) : null
  if (!res.ok) {
    // 미인증이면 전역 핸들러로 로그인 화면 전환 (단, 로그인 시도 자체는 제외)
    if (res.status === 401 && path !== '/api/auth/login' && unauthorizedHandler) {
      unauthorizedHandler()
    }
    // 백엔드 표준 에러 응답(ErrorResponse) 형태를 메시지로 변환
    const msg = data?.message ?? `요청 실패 (HTTP ${res.status})`
    const fields = data?.fieldErrors
      ? ' — ' + Object.entries(data.fieldErrors).map(([k, v]) => `${k}: ${v}`).join(', ')
      : ''
    const err = new Error(msg + fields)
    err.status = res.status // 401/403 등 호출측에서 분기용
    throw err
  }
  return data
}

// multipart 업로드 — Content-Type 은 브라우저가 boundary 와 함께 자동 설정(직접 지정 금지).
async function upload(method, path, formData) {
  const res = await fetch(BASE + path, { method, body: formData, credentials: 'include' })
  const text = await res.text()
  const data = text ? JSON.parse(text) : null
  if (!res.ok) {
    if (res.status === 401 && unauthorizedHandler) unauthorizedHandler()
    const err = new Error(data?.message ?? `요청 실패 (HTTP ${res.status})`)
    err.status = res.status
    throw err
  }
  return data
}

export const api = {
  health: () => request('GET', '/api/health'),
  createUser: (payload) => request('POST', '/api/users', payload),
  createTrip: (payload) => request('POST', '/api/trips', payload),
  listTrips: () => request('GET', '/api/trips'), // 세션 사용자의 여행
  deleteTrip: (id) => request('DELETE', `/api/trips/${id}`),
  // Planning
  getPlan: (tripId) => request('GET', `/api/trips/${tripId}/plan`),
  generatePlan: (tripId, payload) => request('POST', `/api/trips/${tripId}/plan/generate`, payload),
  bookingLinks: (tripId, origin) => request('GET', `/api/trips/${tripId}/booking-links${origin ? `?origin=${encodeURIComponent(origin)}` : ''}`),
  linkTitle: (url) => request('POST', '/api/link-title', { url }), // 예약 링크에서 이름만 가져오기
  listBookings: (tripId) => request('GET', `/api/trips/${tripId}/bookings`),
  // 사진 포함 저장 — multipart (data 파트 JSON + photo 파트 파일, 사진은 선택)
  addBooking: (tripId, data, photoFile) => {
    const fd = new FormData()
    fd.append('data', new Blob([JSON.stringify(data)], { type: 'application/json' }))
    if (photoFile) fd.append('photo', photoFile)
    return upload('POST', `/api/trips/${tripId}/bookings`, fd)
  },
  deleteBooking: (id) => request('DELETE', `/api/bookings/${id}`),
  addPlanItem: (dayId, payload) => request('POST', `/api/plan-days/${dayId}/items`, payload),
  updatePlanItem: (itemId, payload) => request('PATCH', `/api/plan-items/${itemId}`, payload), // 제목/시간/예상비용 편집
  deletePlanItem: (itemId) => request('DELETE', `/api/plan-items/${itemId}`),
  movePlanItem: (itemId, direction) => request('PATCH', `/api/plan-items/${itemId}/move?direction=${direction}`),
  getBudget: (tripId) => request('GET', `/api/trips/${tripId}/budget`), // 계획 단계 예산 점검
  getCurrency: (tripId) => request('GET', `/api/trips/${tripId}/currency`), // 목적지 통화·환율(원화+외화 병기)
  // 읽기 전용 공유
  enableShare: (tripId) => request('POST', `/api/trips/${tripId}/share`),
  disableShare: (tripId) => request('DELETE', `/api/trips/${tripId}/share`),
  getShared: (token) => request('GET', `/api/shared/${token}`),
  // Auth
  me: () => request('GET', '/api/auth/me'),
  login: (payload) => request('POST', '/api/auth/login', payload),
  logout: () => request('POST', '/logout'),
  // Tracking (여행 중 통합 기록: 위치+기분+지출+메모+사진)
  listMoments: (tripId) => request('GET', `/api/trips/${tripId}/moments`),
  momentSummary: (tripId) => request('GET', `/api/trips/${tripId}/moments/summary`),
  deleteMoment: (tripId, id) => request('DELETE', `/api/trips/${tripId}/moments/${id}`),
  // 사진 포함 기록 — multipart (data 파트 JSON + photo 파트 파일)
  recordMoment: (tripId, data, photoFile) => {
    const fd = new FormData()
    fd.append('data', new Blob([JSON.stringify(data)], { type: 'application/json' }))
    if (photoFile) fd.append('photo', photoFile)
    return upload('POST', `/api/trips/${tripId}/moments`, fd)
  },
  // Review (여행 후 복기)
  getReview: (tripId) => request('GET', `/api/trips/${tripId}/review`),
  reviewReport: (tripId, lang) => request('POST', `/api/trips/${tripId}/review/report?lang=${lang}`), // AI 회고 리포트

  upsertActual: (itemId, payload) => request('PUT', `/api/plan-items/${itemId}/actual`, payload),
  saveFeedback: (tripId, payload) => request('PUT', `/api/trips/${tripId}/feedback`, payload),
  // 오프라인 큐 재전송용 일반 POST
  post: (path, body) => request('POST', path, body),
  // 번역 도우미 (공개 — 로그인 불필요)
  translateText: (text, target) => request('POST', '/api/translate/text', { text, target }),
  translateImage: (file, target) => {
    const fd = new FormData()
    fd.append('image', file)
    fd.append('target', target)
    return upload('POST', '/api/translate/image', fd)
  },
}

// 소셜 로그인 시작 (백엔드 OAuth2 인가 엔드포인트로 전체 페이지 이동)
export function socialLoginUrl(provider) {
  return (BASE || '') + `/oauth2/authorization/${provider}`
}
