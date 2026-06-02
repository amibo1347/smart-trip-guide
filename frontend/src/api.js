// API 호출 래퍼. dev에서는 vite 프록시(/api → 8082), prod에서는 동일 출처 또는 VITE_API_BASE 사용.
const BASE = import.meta.env.VITE_API_BASE ?? ''

async function request(method, path, body) {
  const res = await fetch(BASE + path, {
    method,
    headers: body ? { 'Content-Type': 'application/json' } : undefined,
    body: body ? JSON.stringify(body) : undefined,
  })
  const text = await res.text()
  const data = text ? JSON.parse(text) : null
  if (!res.ok) {
    // 백엔드 표준 에러 응답(ErrorResponse) 형태를 메시지로 변환
    const msg = data?.message ?? `요청 실패 (HTTP ${res.status})`
    const fields = data?.fieldErrors
      ? ' — ' + Object.entries(data.fieldErrors).map(([k, v]) => `${k}: ${v}`).join(', ')
      : ''
    throw new Error(msg + fields)
  }
  return data
}

export const api = {
  health: () => request('GET', '/api/health'),
  createUser: (payload) => request('POST', '/api/users', payload),
  createTrip: (payload) => request('POST', '/api/trips', payload),
  listTrips: (userId) => request('GET', `/api/trips?userId=${userId}`),
}
