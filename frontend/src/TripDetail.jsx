import { useState } from 'react'
import Itinerary from './Itinerary.jsx'
import Tracking from './Tracking.jsx'
import Review from './Review.jsx'

/**
 * 여행 상세 화면. 탭으로 [일정(여행 전)] / [기록(여행 중)] 전환.
 */
export default function TripDetail({ trip, onBack, onError }) {
  const [tab, setTab] = useState('plan')

  return (
    <div>
      <button className="link" onClick={onBack}>← 내 여행으로</button>

      <section className="card">
        <h2>🗺 {trip.title}</h2>
        <p className="muted">
          {trip.startDate} ~ {trip.endDate} · 👥 {trip.headcount}명
          {trip.concept && <> · 🏷 {trip.concept}</>}
        </p>
        <div className="tabs">
          <button className={tab === 'plan' ? 'tab active' : 'tab'} onClick={() => setTab('plan')}>📋 일정</button>
          <button className={tab === 'track' ? 'tab active' : 'tab'} onClick={() => setTab('track')}>📍 기록</button>
          <button className={tab === 'review' ? 'tab active' : 'tab'} onClick={() => setTab('review')}>📊 복기</button>
        </div>
      </section>

      {tab === 'plan' && <Itinerary trip={trip} onError={onError} />}
      {tab === 'track' && <Tracking trip={trip} onError={onError} />}
      {tab === 'review' && <Review trip={trip} onError={onError} />}
    </div>
  )
}
