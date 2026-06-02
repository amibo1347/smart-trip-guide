import { useEffect, useState } from 'react'
import { api } from './api.js'
import { enqueue, pendingCount, flush } from './offlineQueue.js'

const MOODS = ['😀', '😍', '😌', '😐', '😫', '🤩', '🥵', '🌧']
const CATEGORIES = ['식비', '교통', '숙박', '관광', '기타']

// 로컬 wall-clock "YYYY-MM-DDTHH:mm:ss" (백엔드 LocalDateTime 파싱용, Z 미포함)
function nowLocal() {
  const d = new Date()
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}
function uuid() {
  return (crypto.randomUUID && crypto.randomUUID()) || `${Date.now()}-${Math.random().toString(16).slice(2)}`
}

export default function Tracking({ trip, onError }) {
  const [locations, setLocations] = useState([])
  const [moods, setMoods] = useState([])
  const [expenses, setExpenses] = useState([])
  const [summary, setSummary] = useState(null)
  const [pending, setPending] = useState(pendingCount())
  const [online, setOnline] = useState(navigator.onLine)
  const [gpsBusy, setGpsBusy] = useState(false)

  async function loadAll() {
    try {
      const [l, m, e, s] = await Promise.all([
        api.listLocations(trip.id), api.listMoods(trip.id),
        api.listExpenses(trip.id), api.expenseSummary(trip.id),
      ])
      setLocations(l); setMoods(m); setExpenses(e); setSummary(s)
    } catch (err) { onError(err.message) }
  }

  async function syncNow() {
    const n = await flush(api.post)
    setPending(pendingCount())
    if (n > 0) loadAll()
  }

  useEffect(() => {
    loadAll()
    if (navigator.onLine) syncNow()
    const goOnline = () => { setOnline(true); syncNow() }
    const goOffline = () => setOnline(false)
    window.addEventListener('online', goOnline)
    window.addEventListener('offline', goOffline)
    return () => {
      window.removeEventListener('online', goOnline)
      window.removeEventListener('offline', goOffline)
    }
    // eslint-disable-next-line
  }, [trip.id])

  // 온라인이면 전송, 아니면(또는 실패 시) 큐에 적재 — client_uuid 멱등키로 중복 방지
  async function recordOrQueue(path, body) {
    if (!navigator.onLine) {
      setPending(enqueue({ path, body }))
      return false
    }
    try {
      await api.post(path, body)
      return true
    } catch {
      setPending(enqueue({ path, body }))
      return false
    }
  }

  function captureLocation() {
    if (!navigator.geolocation) { onError('이 기기는 위치를 지원하지 않습니다.'); return }
    setGpsBusy(true)
    navigator.geolocation.getCurrentPosition(
      async (pos) => {
        const body = {
          clientUuid: uuid(),
          recordedAt: nowLocal(),
          latitude: pos.coords.latitude,
          longitude: pos.coords.longitude,
          accuracyM: pos.coords.accuracy,
        }
        const ok = await recordOrQueue(`/api/trips/${trip.id}/locations`, body)
        if (ok) loadAll()
        setGpsBusy(false)
      },
      (err) => { onError('위치 가져오기 실패: ' + err.message); setGpsBusy(false) },
      { enableHighAccuracy: true, timeout: 10000 },
    )
  }

  async function recordMood(emoji) {
    const ok = await recordOrQueue(`/api/trips/${trip.id}/moods`, { clientUuid: uuid(), emoji, recordedAt: nowLocal() })
    if (ok) loadAll()
  }

  return (
    <div>
      <div className={`netbar ${online ? 'on' : 'off'}`}>
        {online ? '🟢 온라인' : '🔴 오프라인 — 기록은 저장 후 복귀 시 동기화'}
        {pending > 0 && (
          <span className="pending">동기화 대기 {pending}건
            {online && <button className="rb" onClick={syncNow}>지금 동기화</button>}
          </span>
        )}
      </div>

      {/* 위치 */}
      <section className="card">
        <h3>📍 위치 기록</h3>
        <button onClick={captureLocation} disabled={gpsBusy}>{gpsBusy ? 'GPS 측정 중...' : '현재 위치 기록'}</button>
        <ul className="items">
          {locations.slice(0, 5).map((l) => (
            <li key={l.id}>
              <div className="item-meta">🕐 {fmtDt(l.recordedAt)} · {Number(l.latitude).toFixed(5)}, {Number(l.longitude).toFixed(5)}
                {l.accuracyM != null && <> · ±{Math.round(l.accuracyM)}m</>}</div>
            </li>
          ))}
          {locations.length === 0 && <p className="muted small-text">기록 없음</p>}
        </ul>
      </section>

      {/* 기분 */}
      <section className="card">
        <h3>🙂 기분</h3>
        <div className="moodbar">
          {MOODS.map((e) => <button key={e} className="mood" onClick={() => recordMood(e)}>{e}</button>)}
        </div>
        <div className="moodlog">
          {moods.slice(0, 12).map((m) => <span key={m.id} className="moodchip" title={fmtDt(m.recordedAt)}>{m.emoji}</span>)}
          {moods.length === 0 && <p className="muted small-text">기록 없음</p>}
        </div>
      </section>

      {/* 지출 */}
      <section className="card">
        <h3>💰 지출</h3>
        {summary && (
          <div className="summary">
            <b>합계 {Number(summary.total).toLocaleString()}원</b>
            {trip.budgetLimit != null && <span className="muted"> / 예산 {Number(trip.budgetLimit).toLocaleString()}원</span>}
            <div className="bycat">
              {Object.entries(summary.byCategory).map(([c, v]) => (
                <span key={c} className="catchip">{c} {Number(v).toLocaleString()}</span>
              ))}
            </div>
          </div>
        )}
        <ExpenseForm trip={trip} onRecord={recordOrQueue} onDone={loadAll} />
        <ul className="items">
          {expenses.slice(0, 8).map((x) => (
            <li key={x.id}>
              <div className="item-main"><b>{Number(x.amount).toLocaleString()}원</b>
                <span className="type-badge">{x.category ?? '기타'}</span></div>
              <div className="item-meta">{x.memo} · {fmtDt(x.spentAt)}</div>
            </li>
          ))}
          {expenses.length === 0 && <p className="muted small-text">기록 없음</p>}
        </ul>
      </section>
    </div>
  )
}

function fmtDt(s) { return s ? s.replace('T', ' ').slice(5, 16) : '' }

function ExpenseForm({ trip, onRecord, onDone }) {
  const empty = { amount: '', category: '식비', memo: '' }
  const [form, setForm] = useState(empty)
  const [busy, setBusy] = useState(false)

  async function submit(e) {
    e.preventDefault()
    setBusy(true)
    const ok = await onRecord(`/api/trips/${trip.id}/expenses`, {
      clientUuid: uuid(),
      amount: Number(form.amount),
      category: form.category,
      memo: form.memo || null,
      spentAt: nowLocal(),
    })
    setForm(empty)
    setBusy(false)
    if (ok) onDone()
  }

  return (
    <form className="add-form" onSubmit={submit}>
      <div className="row">
        <label>금액 <input type="number" min="1" value={form.amount} onChange={(e) => setForm({ ...form, amount: e.target.value })} required /></label>
        <label>분류
          <select value={form.category} onChange={(e) => setForm({ ...form, category: e.target.value })}>
            {CATEGORIES.map((c) => <option key={c} value={c}>{c}</option>)}
          </select>
        </label>
      </div>
      <input placeholder="메모(선택)" value={form.memo} onChange={(e) => setForm({ ...form, memo: e.target.value })} />
      <button disabled={busy}>{busy ? '기록 중...' : '지출 기록'}</button>
    </form>
  )
}
