import { useEffect, useRef, useState } from 'react'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import { api } from './api.js'

const TYPE_LABEL = { SPOT: '명소', MEAL: '식사', MOVE: '이동', STAY: '숙박', ACTIVITY: '액티비티' }

export default function Review({ trip, onError }) {
  const [review, setReview] = useState(null)

  async function load() {
    try { setReview(await api.getReview(trip.id)) } catch (e) { onError(e.message) }
  }
  useEffect(() => { load() /* eslint-disable-next-line */ }, [trip.id])

  if (!review) return <p className="muted">복기 정보를 불러오는 중...</p>

  const { budgetLimit, plannedCostTotal, actualSpent, budgetDiff,
          expenseByCategory, locationCount, moodCount, visitedCount, totalItems,
          items, locations } = review

  return (
    <div>
      {/* 요약 */}
      <section className="card">
        <h3>📊 한눈에 보기</h3>
        <div className="stat-grid">
          <Stat label="방문" value={`${visitedCount}/${totalItems}`} />
          <Stat label="기록 위치" value={locationCount} />
          <Stat label="기분 기록" value={moodCount} />
        </div>
      </section>

      {/* 예산 vs 실제 */}
      <section className="card">
        <h3>💰 예산 vs 실제</h3>
        <div className="budget">
          <Row k="예산 한도" v={budgetLimit != null ? won(budgetLimit) : '미설정'} />
          <Row k="계획 예상비용" v={won(plannedCostTotal)} />
          <Row k="실제 지출" v={won(actualSpent)} strong />
          {budgetDiff != null && (
            <Row k="예산 대비" v={`${budgetDiff >= 0 ? '+' : ''}${won(budgetDiff)} (${budgetDiff >= 0 ? '절약' : '초과'})`}
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
            <span key={c} className="catchip">{c} {won(v)}</span>
          ))}
        </div>
      </section>

      {/* 지도 */}
      <section className="card">
        <h3>🗺 이동 경로 ({locationCount})</h3>
        {locations.length === 0
          ? <p className="muted small-text">기록된 위치가 없습니다. ‘기록’ 탭에서 위치를 남겨보세요.</p>
          : <MapView locations={locations} />}
      </section>

      {/* 계획 vs 실제 */}
      <section className="card">
        <h3>✅ 계획 vs 실제</h3>
        {items.length === 0 && <p className="muted small-text">일정이 없습니다.</p>}
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

function won(v) { return `${Number(v).toLocaleString()}원` }
function pct(a, b) {
  const p = Number(b) > 0 ? Math.min(100, (Number(a) / Number(b)) * 100) : 0
  return `${p}%`
}

function Stat({ label, value }) {
  return <div className="stat"><div className="stat-v">{value}</div><div className="stat-l">{label}</div></div>
}
function Row({ k, v, strong, cls }) {
  return <div className="brow"><span>{k}</span><b className={cls}>{strong ? <b>{v}</b> : v}</b></div>
}

function MapView({ locations }) {
  const ref = useRef(null)
  const mapRef = useRef(null)

  useEffect(() => {
    if (mapRef.current) return
    const map = L.map(ref.current)
    mapRef.current = map
    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      attribution: '© OpenStreetMap', maxZoom: 19,
    }).addTo(map)

    // 시간순(오래된→최신)으로 마커 + 경로
    const pts = [...locations].reverse().map((l) => [Number(l.latitude), Number(l.longitude)])
    pts.forEach((p, i) => {
      L.circleMarker(p, { radius: 6, color: '#2563eb', fillColor: '#2563eb', fillOpacity: 0.8 })
        .bindPopup(`${i + 1}번째 기록`).addTo(map)
    })
    if (pts.length > 1) {
      L.polyline(pts, { color: '#2563eb', weight: 3, opacity: 0.5 }).addTo(map)
    }
    if (pts.length === 1) map.setView(pts[0], 14)
    else map.fitBounds(pts, { padding: [30, 30] })

    return () => { map.remove(); mapRef.current = null }
  }, [locations])

  return <div ref={ref} className="map" />
}

function ActualRow({ item, onSaved, onError }) {
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
          방문
        </label>
        <span className="type-badge">{TYPE_LABEL[item.type] ?? item.type}</span>
        <b className={visited ? '' : 'muted'}>{item.dayNo}일차 · {item.title}</b>
      </div>
      <div className="item-meta actual-row">
        <span>만족도
          <span className="stars">
            {[1, 2, 3, 4, 5].map((n) => (
              <button key={n} className={'star' + (n <= satisfaction ? ' on' : '')}
                      onClick={() => { setSatisfaction(n); save({ satisfaction: n }) }}>★</button>
            ))}
          </span>
        </span>
        <span>실제비용
          <input type="number" min="0" className="cost-in" value={actualCost} placeholder={item.estCost ?? '0'}
                 onChange={(e) => setActualCost(e.target.value)}
                 onBlur={(e) => save({ actualCost: e.target.value })} />원
        </span>
      </div>
    </li>
  )
}

function FeedbackCard({ trip, feedback, onSaved, onError }) {
  const [score, setScore] = useState(feedback?.overallScore ?? 0)
  const [comment, setComment] = useState(feedback?.comment ?? '')
  const [busy, setBusy] = useState(false)
  const [savedMsg, setSavedMsg] = useState('')

  async function save() {
    setBusy(true); setSavedMsg('')
    try {
      await api.saveFeedback(trip.id, { overallScore: score || null, comment: comment || null })
      setSavedMsg('저장됨 ✓')
      onSaved()
    } catch (e) { onError(e.message) } finally { setBusy(false) }
  }

  return (
    <section className="card">
      <h3>📝 여행 회고</h3>
      <div className="brow"><span>전체 만족도</span>
        <span className="stars">
          {[1, 2, 3, 4, 5].map((n) => (
            <button key={n} className={'star' + (n <= score ? ' on' : '')} onClick={() => setScore(n)}>★</button>
          ))}
        </span>
      </div>
      <textarea className="ta" rows="3" placeholder="이번 여행은 어땠나요?" value={comment}
                onChange={(e) => setComment(e.target.value)} />
      <button disabled={busy} onClick={save}>{busy ? '저장 중...' : '회고 저장'}</button>
      {savedMsg && <span className="saved">{savedMsg}</span>}
      {feedback?.budgetDiff != null && (
        <p className="muted small-text">예산 대비: {Number(feedback.budgetDiff).toLocaleString()}원</p>
      )}
    </section>
  )
}
