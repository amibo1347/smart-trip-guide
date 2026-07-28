import { useState } from 'react'
import { api } from './api.js'
import Itinerary from './Itinerary.jsx'
import Checklist from './Checklist.jsx'
import Tracking from './Tracking.jsx'
import Review from './Review.jsx'
import TripWallet from './TripWallet.jsx'
import ShareSheet from './ShareSheet.jsx'
import { useI18n } from './i18n/index.jsx'

/**
 * 여행 상세 화면. 탭으로 [일정(여행 전)] / [기록(여행 중)] / [복기] 전환.
 */
export default function TripDetail({ trip, defaultOrigin, onBack, onError }) {
  const { t } = useI18n()
  const [tab, setTab] = useState('plan')

  return (
    <div>
      <button className="link back" onClick={onBack}>← {t('내 여행으로')}</button>

      <section className="card hero">
        <h2 className="hero-title">{trip.title}</h2>
        <p className="hero-meta">
          <span>📅 {trip.startDate} ~ {trip.endDate}</span>
          <span>👥 {trip.headcount}{t('명')}</span>
          {trip.concept && <span>🏷 {trip.concept}</span>}
        </p>
        <SharePanel trip={trip} onError={onError} />
      </section>

      <div className="tabs">
        <button className={tab === 'plan' ? 'tab active' : 'tab'} onClick={() => setTab('plan')}>📋 {t('일정')}</button>
        <button className={tab === 'wallet' ? 'tab active' : 'tab'} onClick={() => setTab('wallet')}>💼 {t('지갑')}</button>
        <button className={tab === 'pack' ? 'tab active' : 'tab'} onClick={() => setTab('pack')}>🎒 {t('준비물')}</button>
        <button className={tab === 'track' ? 'tab active' : 'tab'} onClick={() => setTab('track')}>📍 {t('기록')}</button>
        <button className={tab === 'review' ? 'tab active' : 'tab'} onClick={() => setTab('review')}>📊 {t('복기')}</button>
      </div>

      {tab === 'plan' && <Itinerary trip={trip} defaultOrigin={defaultOrigin} onError={onError} />}
      {tab === 'wallet' && <TripWallet trip={trip} onError={onError} />}
      {tab === 'pack' && <Checklist trip={trip} onError={onError} />}
      {tab === 'track' && <Tracking trip={trip} onError={onError} />}
      {tab === 'review' && <Review trip={trip} onError={onError} />}
    </div>
  )
}

/**
 * 읽기 전용 공유 링크 발급 → SNS 공유 시트 열기.
 * 이미 공유 중인 여행(trip.shareToken)은 발급 없이 바로 시트를 연다.
 */
function SharePanel({ trip, onError }) {
  const { t } = useI18n()
  const [token, setToken] = useState(trip.shareToken ?? null)
  const [open, setOpen] = useState(false)
  const [busy, setBusy] = useState(false)

  const url = token ? `${window.location.origin}/share/${token}` : null
  const subtitle = `${trip.startDate} ~ ${trip.endDate} · ${trip.headcount}${t('명')}`

  async function openSheet() {
    if (token) { setOpen(true); return }
    setBusy(true)
    try {
      const r = await api.enableShare(trip.id)
      setToken(r.token)
      trip.shareToken = r.token // 목록으로 돌아갔다 다시 들어와도 공유 상태 유지
      setOpen(true)
    } catch (e) { onError(e.message) } finally { setBusy(false) }
  }

  async function disable() {
    if (!window.confirm(t('공유를 끄면 기존 링크가 즉시 무효화됩니다. 진행할까요?'))) return
    setBusy(true)
    try {
      await api.disableShare(trip.id)
      setToken(null)
      trip.shareToken = null
      setOpen(false)
    } catch (e) { onError(e.message) } finally { setBusy(false) }
  }

  return (
    <>
      <div className="hero-actions">
        <button className="btn-share" onClick={openSheet} disabled={busy}>
          {busy ? t('링크 만드는 중...') : <>🔗 {t('일정 공유하기')}</>}
        </button>
        {token && <span className="live-dot">{t('공유 중')}</span>}
      </div>

      {open && url && (
        <ShareSheet
          url={url}
          title={trip.title}
          subtitle={subtitle}
          onClose={() => setOpen(false)}
          onDisable={disable}
        />
      )}
    </>
  )
}
