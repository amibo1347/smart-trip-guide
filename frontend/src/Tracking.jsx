import { useEffect, useRef, useState } from 'react'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import { api } from './api.js'
import { enqueue, pendingCount, flush } from './offlineQueue.js'
import { useI18n } from './i18n/index.jsx'
import { fmtDt, nowLocal, uuid, won } from './utils/format.js'
import { createMap, pinIcon, popupHtml, DEFAULT_CENTER, DEFAULT_ZOOM, ROUTE_COLOR } from './utils/mapMarkers.js'

const MOODS = ['😀', '😍', '😌', '😐', '😫', '🤩', '🥵', '🌧']
const CATEGORIES = ['식비', '교통', '숙박', '관광', '기타']

export default function Tracking({ trip, onError }) {
  const { t } = useI18n()
  const [moments, setMoments] = useState([])
  const [summary, setSummary] = useState(null)
  const [pending, setPending] = useState(pendingCount())
  const [online, setOnline] = useState(navigator.onLine)
  const [tick, setTick] = useState(0) // 기록 변경 시 예산 경고 재조회 트리거

  async function loadAll() {
    try {
      const [m, s] = await Promise.all([api.listMoments(trip.id), api.momentSummary(trip.id)])
      setMoments(m); setSummary(s); setTick((t) => t + 1)
    } catch (err) { onError(err.message) }
  }

  async function syncNow() {
    const n = await flush(api.post)
    setPending(pendingCount())
    if (n > 0) loadAll()
  }

  async function remove(id) {
    if (!window.confirm(t('이 기록을 완전히 삭제할까요? (사진도 함께 삭제됩니다)'))) return
    try { await api.deleteMoment(trip.id, id); loadAll() } catch (err) { onError(err.message) }
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

  return (
    <div>
      {(!online || pending > 0) && (
        <div className={`netbar ${online ? 'on' : 'off'}`}>
          {!online && `🔴 ${t('오프라인 — 사진 없는 기록은 저장 후 복귀 시 동기화')}`}
          {pending > 0 && (
            <span className="pending">{t('동기화 대기 {n}건', { n: pending })}
              {online && <button className="rb" onClick={syncNow}>{t('지금 동기화')}</button>}
            </span>
          )}
        </div>
      )}

      <BudgetAlert trip={trip} tick={tick} onError={onError} />

      <MomentMap moments={moments} />

      <RecordCard trip={trip} online={online}
                  onSaved={loadAll}
                  onQueued={() => setPending(pendingCount())}
                  onError={onError} />

      <section className="card">
        <h3>🧾 {t('기록')} ({moments.length})
          {summary && summary.total > 0 && (
            <span className="muted" style={{ fontWeight: 400, fontSize: '0.85rem' }}>
              {' '}· {t('지출')} {Number(summary.total).toLocaleString()}{t('원')}</span>
          )}
        </h3>
        <ul className="items">
          {moments.slice(0, 20).map((m) => (
            <li key={m.id} className="moment">
              {m.photoUrl && <img className="m-thumb" src={m.photoUrl} alt="" />}
              <div className="m-body">
                <div className="item-main">
                  {m.mood && <span className="m-mood">{m.mood}</span>}
                  <b>{m.memo || (m.amount != null ? `${Number(m.amount).toLocaleString()}${t('원')}` : t('기록'))}</b>
                  <button className="del" onClick={() => remove(m.id)} title={t('기록 삭제')}>✕</button>
                </div>
                <div className="item-meta">
                  🕐 {fmtDt(m.recordedAt)}
                  {m.amount != null && <> · 💰 {Number(m.amount).toLocaleString()}{t('원')}{m.category ? ` (${t(m.category)})` : ''}</>}
                  {m.place
                    ? <> · 📍 {m.place}</>
                    : m.latitude != null && <> · 📍 {t('위치 기록됨')}</>}
                </div>
              </div>
            </li>
          ))}
          {moments.length === 0 && <p className="muted small-text">{t('아직 기록이 없습니다. 위에서 첫 기록을 남겨보세요.')}</p>}
        </ul>
      </section>
    </div>
  )
}

// 여행 중 실시간 예산 경고: 확정 예약 + 실제 지출(moments)이 한도에 근접/초과하면 배너로 알림.
function BudgetAlert({ trip, tick, onError }) {
  const { t } = useI18n()
  const [b, setB] = useState(null)

  useEffect(() => {
    let alive = true
    api.getBudget(trip.id).then((d) => { if (alive) setB(d) }).catch((e) => onError(e.message))
    return () => { alive = false }
  }, [trip.id, tick]) // eslint-disable-line react-hooks/exhaustive-deps

  if (!b || b.budgetLimit == null) return null // 예산 미설정이면 표시 안 함

  const ratio = b.usedRatio ?? 0
  const over = b.liveOverBudget
  const near = !over && ratio >= 80
  const level = over ? 'over' : near ? 'near' : 'ok'
  const fill = Math.min(100, ratio)
  const w = (v) => won(v, t('원'))

  return (
    <section className={`card budget-alert ${level}`}>
      <div className="ba-head">
        <b>
          {over ? `🚨 ${t('예산 초과')}` : near ? `⚠️ ${t('예산 임박')}` : `💰 ${t('예산 현황')}`}
        </b>
        <span className="ba-ratio">{ratio.toFixed(0)}%</span>
      </div>
      <div className="bar">
        <div className="bar-fill" style={{ width: `${fill}%` }} />
      </div>
      <div className="ba-nums">
        <span>{t('쓴 금액')} <b>{w(b.liveTotal)}</b></span>
        <span>{t('한도')} {w(b.budgetLimit)}</span>
      </div>
      <p className="ba-msg">
        {over
          ? t('한도보다 {amount} 더 썼어요.', { amount: w(b.liveOverAmount) })
          : `${t('남은 예산')} ${w(b.liveRemaining)}`}
        {b.actualSpent > 0 && b.bookingTotal > 0 && (
          <span className="muted small-text"> ({t('예약')} {w(b.bookingTotal)} + {t('지출')} {w(b.actualSpent)})</span>
        )}
      </p>
    </section>
  )
}

// 저장 시 현재 위치를 1회 측정. GPS가 꺼졌거나 거부/실패하면 null(위치 없이 저장).
function getPosition() {
  return new Promise((resolve) => {
    if (!navigator.geolocation) return resolve(null)
    navigator.geolocation.getCurrentPosition(
      (p) => resolve({ lat: p.coords.latitude, lng: p.coords.longitude, accuracy: p.coords.accuracy }),
      () => resolve(null),
      { enableHighAccuracy: true, timeout: 8000 },
    )
  })
}

function RecordCard({ trip, online, onSaved, onQueued, onError }) {
  const { t } = useI18n()
  const empty = { mood: '', amount: '', category: '식비', memo: '' }
  const [form, setForm] = useState(empty)
  const [photo, setPhoto] = useState(null)   // File
  const [preview, setPreview] = useState(null)
  const [busy, setBusy] = useState(false)

  // 미리보기 objectURL 누수 방지: 새 사진/언마운트 시 이전 URL 해제
  useEffect(() => () => { if (preview) URL.revokeObjectURL(preview) }, [preview])

  function pickPhoto(e) {
    const f = e.target.files?.[0] || null
    setPhoto(f)
    setPreview(f ? URL.createObjectURL(f) : null)
  }

  function reset() {
    setForm(empty); setPhoto(null); setPreview(null)
  }

  async function save(e) {
    e.preventDefault()
    setBusy(true)
    const loc = await getPosition() // 기록 누르는 순간 현재 위치 자동 측정(실패 시 위치 없이 저장)
    const data = {
      clientUuid: uuid(),
      recordedAt: nowLocal(),
      latitude: loc?.lat ?? null,
      longitude: loc?.lng ?? null,
      accuracyM: loc?.accuracy ?? null,
      mood: form.mood || null,
      amount: form.amount === '' ? null : Number(form.amount),
      category: form.amount === '' ? null : form.category,
      memo: form.memo || null,
    }
    try {
      if (!online) {
        // 오프라인: 사진은 큐에 담을 수 없어 텍스트 기록만 적재(복귀 시 동기화)
        if (photo) onError(t('오프라인에서는 사진을 저장할 수 없어 텍스트만 기록됩니다.'))
        enqueue({ path: `/api/trips/${trip.id}/moments`, body: data })
        onQueued()
        reset()
      } else {
        await api.recordMoment(trip.id, data, photo)
        reset()
        onSaved()
      }
    } catch (err) { onError(err.message) } finally { setBusy(false) }
  }

  return (
    <section className="card">
      <h3>✍️ {t('기록하기')}</h3>
      <form onSubmit={save}>
        {/* 기분 */}
        <div className="moodbar">
          {MOODS.map((e) => (
            <button type="button" key={e}
                    className={`mood ${form.mood === e ? 'sel' : ''}`}
                    onClick={() => setForm({ ...form, mood: form.mood === e ? '' : e })}>{e}</button>
          ))}
        </div>

        {/* 지출(선택) */}
        <div className="row">
          <label style={{ flex: 1 }}>{t('지출(선택)')}
            <input type="number" min="0" placeholder={t('원')} value={form.amount}
                   onChange={(e) => setForm({ ...form, amount: e.target.value })} />
          </label>
          <label>{t('분류')}
            <select value={form.category} disabled={form.amount === ''}
                    onChange={(e) => setForm({ ...form, category: e.target.value })}>
              {CATEGORIES.map((c) => <option key={c} value={c}>{t(c)}</option>)}
            </select>
          </label>
        </div>

        {/* 메모 */}
        <input placeholder={t('메모(선택) — 무엇을 했는지 한 줄')} value={form.memo}
               onChange={(e) => setForm({ ...form, memo: e.target.value })} />

        {/* 사진(선택) */}
        <div className="photo-row">
          <label className="photo-pick">
            📷 {t('사진 첨부(선택)')}
            <input type="file" accept="image/*" capture="environment" onChange={pickPhoto} hidden disabled={!online} />
          </label>
          {preview && <img className="m-thumb" src={preview} alt="" />}
          {!online && <span className="muted small-text">{t('오프라인에선 사진 첨부 불가')}</span>}
        </div>

        <button disabled={busy}>{busy ? t('기록 중...') : t('이 순간 기록')}</button>
        <p className="muted small-text" style={{ textAlign: 'center', margin: 0 }}>
          📍 {t('위치 권한이 켜져 있으면 누른 순간의 위치가 함께 기록돼요.')}
        </p>
      </form>
    </section>
  )
}

function MomentMap({ moments }) {
  const { t } = useI18n()
  const ref = useRef(null)
  const mapRef = useRef(null)

  useEffect(() => {
    if (mapRef.current) return
    const map = createMap(ref.current)
    mapRef.current = map
    map.setView(DEFAULT_CENTER, DEFAULT_ZOOM)
    return () => { map.remove(); mapRef.current = null }
  }, [])

  // moments 변경 시 마커 다시 그림
  useEffect(() => {
    const map = mapRef.current
    if (!map) return
    const layer = L.layerGroup().addTo(map)
    const pts = []
    // 오래된→최신 순으로 경로/마커
    const withLoc = [...moments].filter((m) => m.latitude != null && m.longitude != null).reverse()
    withLoc.forEach((m) => {
      const p = [Number(m.latitude), Number(m.longitude)]
      pts.push(p)
      L.marker(p, { icon: pinIcon(m) }).bindPopup(popupHtml(m, t, fmtDt)).addTo(layer)
    })
    if (pts.length > 1) L.polyline(pts, { color: ROUTE_COLOR, weight: 3, opacity: 0.4 }).addTo(layer)
    if (pts.length === 1) map.setView(pts[0], 14)
    else if (pts.length > 1) map.fitBounds(pts, { padding: [40, 40] })
    return () => { layer.remove() }
  }, [moments, t])

  return <div ref={ref} className="map" />
}
