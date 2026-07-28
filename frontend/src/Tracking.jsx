import { useEffect, useState } from 'react'
import { api } from './api.js'
import RouteMap from './RouteMap.jsx'
import { enqueue, pendingCount, flush } from './offlineQueue.js'
import { useI18n } from './i18n/index.jsx'
import { fmtDt, nowLocal, uuid, won } from './utils/format.js'

const MOODS = ['😀', '😍', '😌', '😐', '😫', '🤩', '🥵', '🌧']
const CATEGORIES = ['식비', '교통', '숙박', '관광', '기타']

export default function Tracking({ trip, onError }) {
  const { t } = useI18n()
  const [moments, setMoments] = useState([])
  const [summary, setSummary] = useState(null)
  const [pending, setPending] = useState(pendingCount())
  const [online, setOnline] = useState(navigator.onLine)
  const [tick, setTick] = useState(0) // 기록 변경 시 예산 경고 재조회 트리거
  // 노선도(장소별)에 붙지 않은 일반 기록만 아래 목록에 — 장소 기록은 노선도에서 관리.
  const generalMoments = moments.filter((m) => m.planItemId == null)

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

      {/* 일정 노선도 + 장소별 기록(사진·지출·메모를 한 장소에 함께). 기록 시 예산/지출목록도 갱신. */}
      <RouteMap trip={trip} onRecorded={loadAll} onError={onError} />

      {/* 특정 장소에 매이지 않은 일반 지출·메모(예: 도시 간 이동비). 장소별 기록은 위 노선도에서. */}
      <RecordCard trip={trip} online={online}
                  onSaved={loadAll}
                  onQueued={() => setPending(pendingCount())}
                  onError={onError} />

      <section className="card">
        <h3>🧾 {t('기타 지출·메모')} ({generalMoments.length})
          {summary && summary.total > 0 && (
            <span className="muted" style={{ fontWeight: 400, fontSize: '0.85rem' }}>
              {' '}· {t('전체 지출')} {Number(summary.total).toLocaleString()}{t('원')}</span>
          )}
        </h3>
        <p className="muted small-text" style={{ marginTop: 0 }}>
          {t('장소에 남긴 기록은 위 노선도에 있어요. 여기는 특정 장소와 무관한 기록만 모입니다.')}
        </p>

        <ul className="items">
          {generalMoments.slice(0, 20).map((m) => (
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
                </div>
              </div>
            </li>
          ))}
          {generalMoments.length === 0 && <p className="muted small-text">{t('아직 일반 기록이 없어요.')}</p>}
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
      {/* 여럿이 가는 여행이면 지금까지 쓴 돈의 1인당 몫도 함께 — 정산 감각을 여행 중에 유지 */}
      {b.headcount > 1 && b.perPersonLive != null && (
        <div className="split">
          <div>
            <div className="split-l">🧮 {t('지금까지 1인당')}</div>
            <div className="split-sub">{t('쓴 금액 ÷ {n}명', { n: b.headcount })}</div>
          </div>
          <div className="split-v">{w(b.perPersonLive)}</div>
        </div>
      )}
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

function RecordCard({ trip, online, onSaved, onQueued, onError }) {
  const { t } = useI18n()
  const empty = { mood: '', amount: '', category: '식비', memo: '' }
  const [form, setForm] = useState(empty)
  const [busy, setBusy] = useState(false)

  function reset() { setForm(empty) }

  async function save(e) {
    e.preventDefault()
    setBusy(true)
    // 지출·기분·메모만 기록한다(위치는 사진과 함께 '일정 노선도'에서 관리하므로 GPS 측정 안 함).
    const data = {
      clientUuid: uuid(),
      recordedAt: nowLocal(),
      latitude: null,
      longitude: null,
      accuracyM: null,
      mood: form.mood || null,
      amount: form.amount === '' ? null : Number(form.amount),
      category: form.amount === '' ? null : form.category,
      memo: form.memo || null,
    }
    try {
      if (!online) {
        enqueue({ path: `/api/trips/${trip.id}/moments`, body: data })
        onQueued()
        reset()
      } else {
        await api.recordMoment(trip.id, data, null)
        reset()
        onSaved()
      }
    } catch (err) { onError(err.message) } finally { setBusy(false) }
  }

  return (
    <section className="card">
      <h3>✍️ {t('지출·메모 기록')}</h3>
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

        <button disabled={busy}>{busy ? t('기록 중...') : t('기록하기')}</button>
        <p className="muted small-text" style={{ textAlign: 'center', margin: 0 }}>
          📷 {t('사진은 위 노선도에서 방문한 장소에 붙일 수 있어요.')}
        </p>
      </form>
    </section>
  )
}
