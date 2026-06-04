import { useEffect, useRef, useState } from 'react'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import { api } from './api.js'
import { useI18n } from './i18n/index.jsx'
import { fmtDt, pct, won } from './utils/format.js'
import { createMap, pinIcon, popupHtml, ROUTE_COLOR } from './utils/mapMarkers.js'
import { PLAN_TYPE, typeName } from './constants/labels.js'
import Row from './components/Row.jsx'

export default function Review({ trip, onError }) {
  const { t } = useI18n()
  const [review, setReview] = useState(null)

  async function load() {
    try { setReview(await api.getReview(trip.id)) } catch (e) { onError(e.message) }
  }
  useEffect(() => { load() /* eslint-disable-next-line */ }, [trip.id])

  if (!review) return <p className="muted">{t('복기 정보를 불러오는 중...')}</p>

  const { budgetLimit, plannedCostTotal, actualSpent, budgetDiff,
          expenseByCategory, locationCount, moodCount, visitedCount, totalItems,
          items, locations } = review

  return (
    <div>
      {/* 요약 */}
      <section className="card">
        <h3>📊 {t('한눈에 보기')}</h3>
        <div className="stat-grid">
          <Stat label={t('방문')} value={`${visitedCount}/${totalItems}`} />
          <Stat label={t('기록 위치')} value={locationCount} />
          <Stat label={t('기분 기록')} value={moodCount} />
        </div>
      </section>

      {/* 예산 vs 실제 */}
      <section className="card">
        <h3>💰 {t('예산 vs 실제')}</h3>
        <div className="budget">
          <Row k={t('예산 한도')} v={budgetLimit != null ? won(budgetLimit, t('원')) : t('미설정')} />
          <Row k={t('계획 예상비용')} v={won(plannedCostTotal, t('원'))} />
          <Row k={t('실제 지출')} v={won(actualSpent, t('원'))} strong />
          {budgetDiff != null && (
            <Row k={t('예산 대비')} v={`${budgetDiff >= 0 ? '+' : ''}${won(budgetDiff, t('원'))} (${budgetDiff >= 0 ? t('절약') : t('초과')})`}
                 cls={budgetDiff >= 0 ? 'good' : 'bad'} />
          )}
        </div>
        {budgetLimit != null && (
          <div className="bar">
            <div className="bar-fill" style={{ width: pct(actualSpent, budgetLimit) }} />
          </div>
        )}
        <div className="bycat">
          {Object.entries(expenseByCategory).map(([c, v]) => (
            <span key={c} className="catchip">{c} {won(v, t('원'))}</span>
          ))}
        </div>
      </section>

      {/* 지도 */}
      <section className="card">
        <h3>🗺 {t('이동 경로')} ({locationCount})</h3>
        {locations.length === 0
          ? <p className="muted small-text">{t('기록된 위치가 없습니다. ‘기록’ 탭에서 위치를 남겨보세요.')}</p>
          : <MapView locations={locations} />}
      </section>

      {/* 계획 vs 실제 */}
      <section className="card">
        <h3>✅ {t('계획 vs 실제')}</h3>
        {items.length === 0 && <p className="muted small-text">{t('일정이 없습니다.')}</p>}
        <ul className="items">
          {items.map((it) => (
            <ActualRow key={it.planItemId} item={it} onSaved={load} onError={onError} />
          ))}
        </ul>
      </section>

      {/* 회고 */}
      <FeedbackCard trip={trip} feedback={review.feedback} onSaved={load} onError={onError} />
    </div>
  )
}

function Stat({ label, value }) {
  return <div className="stat"><div className="stat-v">{value}</div><div className="stat-l">{label}</div></div>
}

function MapView({ locations }) {
  const { t } = useI18n()
  const ref = useRef(null)
  const mapRef = useRef(null)

  useEffect(() => {
    if (mapRef.current) return
    const map = createMap(ref.current)
    mapRef.current = map

    // 시간순(오래된→최신)으로 사진 핀 + 경로
    const ordered = [...locations].reverse()
    const pts = ordered.map((l) => [Number(l.latitude), Number(l.longitude)])
    ordered.forEach((l, i) => {
      L.marker(pts[i], { icon: pinIcon(l) }).bindPopup(popupHtml(l, t, fmtDt)).addTo(map)
    })
    if (pts.length > 1) {
      L.polyline(pts, { color: ROUTE_COLOR, weight: 3, opacity: 0.5 }).addTo(map)
    }
    if (pts.length === 1) map.setView(pts[0], 14)
    else if (pts.length > 1) map.fitBounds(pts, { padding: [30, 30] })

    return () => { map.remove(); mapRef.current = null }
  }, [locations, t])

  return <div ref={ref} className="map" />
}

function ActualRow({ item, onSaved, onError }) {
  const { t } = useI18n()
  const [visited, setVisited] = useState(item.visited)
  const [satisfaction, setSatisfaction] = useState(item.satisfaction ?? 0)
  const [actualCost, setActualCost] = useState(item.actualCost ?? '')
  const [busy, setBusy] = useState(false)

  async function save(next) {
    setBusy(true)
    try {
      const payload = {
        visited: next.visited ?? visited,
        actualCost: (next.actualCost ?? actualCost) === '' ? null : Number(next.actualCost ?? actualCost),
        satisfaction: (next.satisfaction ?? satisfaction) || null,
      }
      await api.upsertActual(item.planItemId, payload)
      onSaved()
    } catch (e) { onError(e.message) } finally { setBusy(false) }
  }

  return (
    <li>
      <div className="item-main">
        <label className="chk">
          <input type="checkbox" checked={visited} disabled={busy}
                 onChange={(e) => { setVisited(e.target.checked); save({ visited: e.target.checked }) }} />
          {t('방문')}
        </label>
        <span className="type-badge">{typeName(t, PLAN_TYPE, item.type)}</span>
        <b className={visited ? '' : 'muted'}>{t('{n}일차', { n: item.dayNo })} · {item.title}</b>
      </div>
      <div className="item-meta actual-row">
        <span>{t('만족도')}
          <span className="stars">
            {[1, 2, 3, 4, 5].map((n) => (
              <button key={n} className={'star' + (n <= satisfaction ? ' on' : '')}
                      onClick={() => { setSatisfaction(n); save({ satisfaction: n }) }}>★</button>
            ))}
          </span>
        </span>
        <span>{t('실제비용')}
          <input type="number" min="0" className="cost-in" value={actualCost} placeholder={item.estCost ?? '0'}
                 onChange={(e) => setActualCost(e.target.value)}
                 onBlur={(e) => save({ actualCost: e.target.value })} />{t('원')}
        </span>
      </div>
    </li>
  )
}

function FeedbackCard({ trip, feedback, onSaved, onError }) {
  const { t } = useI18n()
  const [score, setScore] = useState(feedback?.overallScore ?? 0)
  const [comment, setComment] = useState(feedback?.comment ?? '')
  const [busy, setBusy] = useState(false)
  const [savedMsg, setSavedMsg] = useState('')

  async function save() {
    setBusy(true); setSavedMsg('')
    try {
      await api.saveFeedback(trip.id, { overallScore: score || null, comment: comment || null })
      setSavedMsg(t('저장됨 ✓'))
      onSaved()
    } catch (e) { onError(e.message) } finally { setBusy(false) }
  }

  return (
    <section className="card">
      <h3>📝 {t('여행 일기')}</h3>
      <div className="brow"><span>{t('전체 만족도')}</span>
        <span className="stars">
          {[1, 2, 3, 4, 5].map((n) => (
            <button key={n} className={'star' + (n <= score ? ' on' : '')} onClick={() => setScore(n)}>★</button>
          ))}
        </span>
      </div>
      <textarea className="ta" rows="3" placeholder={t('이번 여행은 어땠나요?')} value={comment}
                onChange={(e) => setComment(e.target.value)} />
      <button disabled={busy} onClick={save}>{busy ? t('저장 중...') : t(' 일기 기록')}</button>
      {savedMsg && <span className="saved">{savedMsg}</span>}
      {feedback?.budgetDiff != null && (
        <p className="muted small-text">{t('예산 대비')}: {Number(feedback.budgetDiff).toLocaleString()}{t('원')}</p>
      )}
    </section>
  )
}
