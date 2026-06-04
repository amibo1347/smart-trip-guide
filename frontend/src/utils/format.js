// 화면 전반에서 쓰는 포맷 헬퍼 모음(이전엔 각 컴포넌트에 중복 정의됨).

/** 금액 + 통화 단위. null/undefined 는 0 으로(NaN 방지). unit 은 i18n t('원') 등을 주입. */
export function won(v, unit = '원') {
  return `${Number(v ?? 0).toLocaleString()}${unit}`
}

/** 0~100 사이 백분율 문자열("33.3%"). b<=0 이면 0%. (반올림으로 바 너비 안정화) */
export function pct(a, b) {
  const p = Number(b) > 0 ? Math.min(100, (Number(a) / Number(b)) * 100) : 0
  return `${Math.round(p * 10) / 10}%`
}

/** 원화 → 목적지 통화 환산 문자열 " (¥16,200)". 환율 없거나 KRW면 빈 문자열. approx=true면 ≈ 표기. */
export function foreign(krw, fx, approx = false) {
  if (!fx || fx.code === 'KRW' || !fx.perKrw || krw == null) return ''
  const v = Number(krw) * Number(fx.perKrw)
  if (!isFinite(v) || v <= 0) return ''
  return ` (${approx ? '≈ ' : ''}${fx.symbol}${Math.round(v).toLocaleString()})`
}

/** "HH:mm:ss" / "HH:mm" → "HH:mm". 비면 빈 문자열. */
export function fmt(t) {
  return t ? t.slice(0, 5) : ''
}

/** "YYYY-MM-DDTHH:mm:ss" → "MM-DD HH:mm". 비면 빈 문자열. */
export function fmtDt(s) {
  return s ? s.replace('T', ' ').slice(5, 16) : ''
}

/** 로컬 wall-clock "YYYY-MM-DDTHH:mm:ss" (백엔드 LocalDateTime 파싱용, Z 미포함). */
export function nowLocal() {
  const d = new Date()
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}

/** 멱등키용 UUID. crypto.randomUUID 우선, 미지원 시 폴백. */
export function uuid() {
  return (crypto.randomUUID && crypto.randomUUID()) || `${Date.now()}-${Math.random().toString(16).slice(2)}`
}
