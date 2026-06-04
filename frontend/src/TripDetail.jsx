import { useState } from 'react'
import { api } from './api.js'
import Itinerary from './Itinerary.jsx'
import Tracking from './Tracking.jsx'
import Review from './Review.jsx'
import { useI18n } from './i18n/index.jsx'

/**
 * 여행 상세 화면. 탭으로 [일정(여행 전)] / [기록(여행 중)] / [복기] 전환.
 */
export default function TripDetail({ trip, onBack, onError }) {
  const { t } = useI18n()
  const [tab, setTab] = useState('plan')

  return (
    <div>
      <button className="link" onClick={onBack}>← {t('내 여행으로')}</button>

      <section className="card">
        <h2>🗺 {trip.title}</h2>
        <p className="muted">
          {trip.startDate} ~ {trip.endDate} · 👥 {trip.headcount}{t('명')}
          {trip.concept && <> · 🏷 {trip.concept}</>}
        </p>
        <SharePanel trip={trip} onError={onError} />
        <div className="tabs">
          <button className={tab === 'plan' ? 'tab active' : 'tab'} onClick={() => setTab('plan')}>📋 {t('일정')}</button>
          <button className={tab === 'track' ? 'tab active' : 'tab'} onClick={() => setTab('track')}>📍 {t('기록')}</button>
          <button className={tab === 'review' ? 'tab active' : 'tab'} onClick={() => setTab('review')}>📊 {t('복기')}</button>
        </div>
      </section>

      {tab === 'plan' && <Itinerary trip={trip} onError={onError} />}
      {tab === 'track' && <Tracking trip={trip} onError={onError} />}
      {tab === 'review' && <Review trip={trip} onError={onError} />}
    </div>
  )
}

/** 일정 읽기 전용 공유: 링크 발급 → 복사/공유하기/PDF용 열기/공유 끄기. */
function SharePanel({ trip, onError }) {
  const { t } = useI18n()
  const [url, setUrl] = useState(null)
  const [busy, setBusy] = useState(false)

  async function enable() {
    setBusy(true)
    try {
      const r = await api.enableShare(trip.id)
      setUrl(window.location.origin + r.path)
    } catch (e) { onError(e.message) } finally { setBusy(false) }
  }
  async function disable() {
    if (!window.confirm(t('공유를 끄면 기존 링크가 즉시 무효화됩니다. 진행할까요?'))) return
    setBusy(true)
    try { await api.disableShare(trip.id); setUrl(null) }
    catch (e) { onError(e.message) } finally { setBusy(false) }
  }
  async function share() {
    if (!url) return
    if (navigator.share) {
      try { await navigator.share({ title: trip.title, url }) } catch { /* 취소 무시 */ }
    } else {
      try { await navigator.clipboard.writeText(url); alert(t('링크가 복사되었습니다.')) }
      catch { window.prompt(t('아래 링크를 복사하세요'), url) }
    }
  }
  async function copy() {
    try { await navigator.clipboard.writeText(url); alert(t('링크가 복사되었습니다.')) }
    catch { window.prompt(t('아래 링크를 복사하세요'), url) }
  }

  if (!url) {
    return (
      <div className="share-actions">
        <button className="small" onClick={enable} disabled={busy}>{busy ? '...' : `🔗 ${t('일정 공유')}`}</button>
      </div>
    )
  }
  return (
    <div className="share-box">
      <div className="row">
        <input style={{ flex: 1 }} readOnly value={url} onFocus={(e) => e.target.select()} />
        <button type="button" className="small" onClick={copy}>{t('복사')}</button>
      </div>
      <div className="share-actions">
        <button className="small" onClick={share}>📤 {t('공유하기')}</button>
        <a className="small btn-like" href={url} target="_blank" rel="noreferrer">🖨 {t('PDF용 열기 ↗')}</a>
        <button className="small ghost" onClick={disable} disabled={busy}>{t('공유 끄기')}</button>
      </div>
      <p className="muted small-text">{t('이 링크로 누구나 읽기 전용 일정을 볼 수 있어요. 카톡·문자에 붙여넣어 공유하세요.')}</p>
    </div>
  )
}
