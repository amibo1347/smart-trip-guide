// API 호출 래퍼. dev에서는 vite 프록시(/api → 8082), prod에서는 동일 출처 또는 VITE_API_BASE 사용.
const BASE = import.meta.env.VITE_API_BASE ?? ''

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

export const api = {
  health: () => request('GET', '/api/health'),
  createUser: (payload) => request('POST', '/api/users', payload),
  createTrip: (payload) => request('POST', '/api/trips', payload),
  listTrips: () => request('GET', '/api/trips'), // 세션 사용자의 여행
  // Planning
  getPlan: (tripId) => request('GET', `/api/trips/${tripId}/plan`),
  generatePlan: (tripId, payload) => request('POST', `/api/trips/${tripId}/plan/generate`, payload),
  bookingLinks: (tripId, origin) => request('GET', `/api/trips/${tripId}/booking-links${origin ? `?origin=${encodeURIComponent(origin)}` : ''}`),
  addPlanItem: (dayId, payload) => request('POST', `/api/plan-days/${dayId}/items`, payload),
  deletePlanItem: (itemId) => request('DELETE', `/api/plan-items/${itemId}`),
  movePlanItem: (itemId, direction) => request('PATCH', `/api/plan-items/${itemId}/move?direction=${direction}`),
  listAccommodations: (tripId) => request('GET', `/api/trips/${tripId}/accommodations`),
  addAccommodation: (tripId, payload) => request('POST', `/api/trips/${tripId}/accommodations`, payload),
  deleteAccommodation: (id) => request('DELETE', `/api/accommodations/${id}`),
  // Auth
  me: () => request('GET', '/api/auth/me'),
  login: (payload) => request('POST', '/api/auth/login', payload),
  logout: () => request('POST', '/logout'),
  // Tracking (여행 중 기록)
  recordLocation: (tripId, payload) => request('POST', `/api/trips/${tripId}/locations`, payload),
  listLocations: (tripId) => request('GET', `/api/trips/${tripId}/locations`),
  recordMood: (tripId, payload) => request('POST', `/api/trips/${tripId}/moods`, payload),
  listMoods: (tripId) => request('GET', `/api/trips/${tripId}/moods`),
  recordExpense: (tripId, payload) => request('POST', `/api/trips/${tripId}/expenses`, payload),
  listExpenses: (tripId) => request('GET', `/api/trips/${tripId}/expenses`),
  expenseSummary: (tripId) => request('GET', `/api/trips/${tripId}/expenses/summary`),
  // Review (여행 후 복기)
  getReview: (tripId) => request('GET', `/api/trips/${tripId}/review`),
  upsertActual: (itemId, payload) => request('PUT', `/api/plan-items/${itemId}/actual`, payload),
  saveFeedback: (tripId, payload) => request('PUT', `/api/trips/${tripId}/feedback`, payload),
  // 오프라인 큐 재전송용 일반 POST
  post: (path, body) => request('POST', path, body),
}

// 소셜 로그인 시작 (백엔드 OAuth2 인가 엔드포인트로 전체 페이지 이동)
export function socialLoginUrl(provider) {
  return (BASE || '') + `/oauth2/authorization/${provider}`
}
