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
      <ReportCard trip={trip} onError={onError} />

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

/** 기록 데이터를 바탕으로 AI가 여행을 요약. 현재 UI 언어로 생성. */
function ReportCard({ trip, onError }) {
  const { t, lang } = useI18n()
  const [report, setReport] = useState(null)
  const [busy, setBusy] = useState(false)

  async function generate() {
    setBusy(true)
    try { setReport(await api.reviewReport(trip.id, lang)) }
    catch (e) { onError(e.message) }
    finally { setBusy(false) }
  }

  return (
    <section className="card report-card">
      <div className="day-head">
        <h3>🤖 {t('AI 회고 리포트')}</h3>
        {report && <button className="small" onClick={generate} disabled={busy}>{busy ? t('생성 중...') : t('다시 생성')}</button>}
      </div>
      {!report && !busy && (
        <>
          <p className="muted small-text">{t('기록한 일정·지출·기분을 바탕으로 AI가 이번 여행을 정리해 드려요.')}</p>
          <button onClick={generate} disabled={busy}>✨ {t('리포트 만들기')}</button>
        </>
      )}
      {busy && <p className="muted small-text">{t('AI가 회고를 작성하는 중이에요... (수 초 소요)')}</p>}
      {report && (
        <div className="report">
          <p className="report-title">“{report.title}”</p>
          {report.highlights?.length > 0 && (
            <div className="report-sec">
              <h4>✨ {t('하이라이트')}</h4>
              <ul>{report.highlights.map((h, i) => <li key={i}>{h}</li>)}</ul>
            </div>
          )}
          {report.spending && <div className="report-sec"><h4>💰 {t('지출 이야기')}</h4><p>{report.spending}</p></div>}
          {report.mood && <div className="report-sec"><h4>🎭 {t('기분과 만족도')}</h4><p>{report.mood}</p></div>}
          {report.tips?.length > 0 && (
            <div className="report-sec">
              <h4>🧭 {t('다음 여행 팁')}</h4>
              <ul>{report.tips.map((tip, i) => <li key={i}>{tip}</li>)}</ul>
            </div>
          )}
          <p className="muted small-text">{t('※ 기록된 데이터를 바탕으로 AI가 생성했어요.')}</p>
        </div>
      )}
    </section>
  )
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
