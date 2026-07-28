import { useEffect, useState } from 'react'
import { api } from './api.js'
import RouteMap from './RouteMap.jsx'
import { useI18n } from './i18n/index.jsx'
import { pct, won } from './utils/format.js'
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
          expenseByCategory, items } = review

  return (
    <div>
      <ReportCard trip={trip} onError={onError} />

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

      {/* 여행 노선도 (일정 기반) — 읽기 전용. 복기에서는 다녀온 경로와 장소별 사진을 함께 본다. */}
      <RouteMap trip={trip} readOnly onError={onError} />

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
  const [liked, setLiked] = useState(feedback?.liked ?? '')
  const [regret, setRegret] = useState(feedback?.regret ?? '')
  const [nextTime, setNextTime] = useState(feedback?.nextTime ?? '')
  const [busy, setBusy] = useState(false)
  const [savedMsg, setSavedMsg] = useState('')

  async function save() {
    setBusy(true); setSavedMsg('')
    try {
      await api.saveFeedback(trip.id, {
        overallScore: score || null,
        comment: comment.trim() || null,
        liked: liked.trim() || null,
        regret: regret.trim() || null,
        nextTime: nextTime.trim() || null,
      })
      setSavedMsg(t('저장됨 ✓'))
      onSaved()
    } catch (e) { onError(e.message) } finally { setBusy(false) }
  }

  return (
    <section className="card">
      <h3>📝 {t('여행 일기')}</h3>
      <p className="muted small-text" style={{ marginTop: -2 }}>
        {t('이번 여행을 찬찬히 되돌아보며 남겨보세요. 나중에 다시 꺼내보는 기록이 돼요.')}
      </p>

      <div className="brow" style={{ marginTop: 4 }}><span>{t('전체 만족도')}</span>
        <span className="stars">
          {[1, 2, 3, 4, 5].map((n) => (
            <button key={n} className={'star' + (n <= score ? ' on' : '')} onClick={() => setScore(n)}>★</button>
          ))}
        </span>
      </div>

      <label className="diary-label">📔 {t('오늘의 여행 일기')}</label>
      <textarea className="ta" rows="4" placeholder={t('무엇을 하고, 무엇을 느꼈나요? 기억하고 싶은 순간을 자유롭게 적어보세요.')}
                value={comment} onChange={(e) => setComment(e.target.value)} />

      <label className="diary-label">😊 {t('좋았던 점')}</label>
      <textarea className="ta" rows="2" placeholder={t('가장 만족스러웠던 장소·음식·순간은?')}
                value={liked} onChange={(e) => setLiked(e.target.value)} />

      <label className="diary-label">😅 {t('아쉬웠던 점')}</label>
      <textarea className="ta" rows="2" placeholder={t('아쉬웠거나 다음엔 피하고 싶은 건?')}
                value={regret} onChange={(e) => setRegret(e.target.value)} />

      <label className="diary-label">🧭 {t('다음엔 이렇게')}</label>
      <textarea className="ta" rows="2" placeholder={t('다음 여행에서 꼭 해보고 싶은 것, 메모해둘 팁')}
                value={nextTime} onChange={(e) => setNextTime(e.target.value)} />

      <button disabled={busy} onClick={save}>{busy ? t('저장 중...') : t('일기 저장')}</button>
      {savedMsg && <span className="saved">{savedMsg}</span>}
      {feedback?.budgetDiff != null && (
        <p className="muted small-text">{t('예산 대비')}: {Number(feedback.budgetDiff).toLocaleString()}{t('원')}</p>
      )}
    </section>
  )
}
