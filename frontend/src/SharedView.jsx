import { useEffect, useState } from 'react'
import { api } from './api.js'
import { useI18n } from './i18n/index.jsx'

// [이모지, 이름키] — 이름키는 t()로 현재 언어 표시.
const TYPE_LABEL = {
  SPOT: ['🏞', '명소'], MEAL: ['🍽', '식사'], MOVE: ['🚌', '이동'], STAY: ['🏨', '숙박'], ACTIVITY: ['🎯', '액티비티'],
}
const BK_LABEL = { FLIGHT: ['✈️', '항공'], HOTEL: ['🏨', '숙소'] }
function typeLabel(t, map, code) { return map[code] ? `${map[code][0]} ${t(map[code][1])}` : code }

function fmt(t) { return t ? t.slice(0, 5) : '' }

/** 원화 → 목적지 통화 환산 " (≈ ¥16,200)". 환율 없거나 KRW면 빈 문자열. */
function foreign(krw, fx) {
  if (!fx || fx.code === 'KRW' || !fx.perKrw || krw == null) return ''
  const v = Number(krw) * Number(fx.perKrw)
  if (!isFinite(v) || v <= 0) return ''
  return ` (≈ ${fx.symbol}${Math.round(v).toLocaleString()})`
}

/**
 * 로그인 없이 보는 읽기 전용 일정(공유 링크 /share/{token}).
 * PDF 저장(인쇄)·링크 공유 버튼 제공.
 */
export default function SharedView({ token }) {
  const { t } = useI18n()
  const [data, setData] = useState(null)
  const [err, setErr] = useState('')

  useEffect(() => {
    api.getShared(token).then(setData).catch((e) => setErr(e.message))
  }, [token])

  async function share() {
    const url = window.location.href
    if (navigator.share) {
      try { await navigator.share({ title: data?.title || t('여행 일정'), url }) } catch { /* 취소 무시 */ }
    } else {
      try { await navigator.clipboard.writeText(url); alert(t('링크가 복사되었습니다.')) }
      catch { window.prompt(t('아래 링크를 복사하세요'), url) }
    }
  }

  if (err) {
    return (
      <div className="app">
        <header><h1>🧳 {t('스마트 여행 플래너')}</h1></header>
        <section className="card"><p className="muted">{err}</p></section>
      </div>
    )
  }
  if (!data) {
    return (
      <div className="app">
        <header><h1>🧳 {t('스마트 여행 플래너')}</h1></header>
        <section className="card"><p className="muted">{t('불러오는 중...')}</p></section>
      </div>
    )
  }

  const fx = data.currency
  const bookingById = Object.fromEntries((data.bookings || []).map((b) => [b.id, b]))
  const days = data.plan?.days || []

  return (
    <div className="app shared">
      <header><h1>🧳 {data.title}</h1></header>

      <section className="card">
        <p className="muted">
          📅 {data.startDate} ~ {data.endDate} · 👥 {data.headcount}{t('명')}
          {data.concept && <> · 🏷 {data.concept}</>}
        </p>
        {fx && fx.code !== 'KRW' && fx.perKrw && (
          <p className="muted small-text">💱 {fx.code} {t('환율')}: ₩1,000 ≈ {fx.symbol}{Math.round(1000 * Number(fx.perKrw)).toLocaleString()}
            {' '}({fx.live ? t('실시간') : t('근사치')})</p>
        )}
        <div className="share-actions no-print">
          <button className="small" onClick={() => window.print()}>🖨 {t('PDF로 저장')}</button>
          <button className="small" onClick={share}>🔗 {t('공유 / 링크 복사')}</button>
        </div>
        <p className="muted small-text no-print">{t('읽기 전용 공유 일정입니다.')}</p>
      </section>

      {days.length === 0 && <section className="card"><p className="muted">{t('아직 일정이 없습니다.')}</p></section>}

      {days.map((day) => (
        <section className="card" key={day.id}>
          <div className="day-head"><h3>{t('{n}일차', { n: day.dayNo })} <span className="muted">· {day.date}</span></h3></div>
          {day.items.length === 0 && <p className="muted small-text">{t('일정 없음')}</p>}
          <ul className="items">
            {day.items.map((it) => {
              const bk = it.bookingId ? bookingById[it.bookingId] : null
              return (
                <li key={it.id}>
                  <div className="item-main">
                    <span className="type-badge">{typeLabel(t, TYPE_LABEL, it.type)}</span>
                    <b>{it.title}</b>
                  </div>
                  <div className="item-meta">
                    {(it.plannedStart || it.plannedEnd) && (
                      <>⏰ {fmt(it.plannedStart)}{it.plannedEnd ? `~${fmt(it.plannedEnd)}` : ''} </>
                    )}
                    {bk ? (
                      <>
                        {bk.price != null && <>💰 {Number(bk.price).toLocaleString()}{t('원')}{foreign(bk.price, fx)} </>}
                        <span className="confirm-tag">{t('확정 예약')}</span>{' '}
                      </>
                    ) : (
                      it.estCost != null && Number(it.estCost) > 0 && (
                        <>💰 {Number(it.estCost).toLocaleString()}{t('원')}{foreign(it.estCost, fx)} </>
                      )
                    )}
                    {it.place?.address && <>📍 {it.place.address} </>}
                  </div>
                </li>
              )
            })}
          </ul>
        </section>
      ))}

      {data.bookings && data.bookings.length > 0 && (
        <section className="card">
          <h3>🧾 {t('확정 예약')} ({data.bookings.length})</h3>
          <ul className="items">
            {data.bookings.map((b) => (
              <li key={b.id}>
                <div className="item-main">
                  <span className="type-badge">{typeLabel(t, BK_LABEL, b.type)}</span>
                  <b>{b.title}</b>
                </div>
                <div className="item-meta">
                  {b.price != null && <>💰 {Number(b.price).toLocaleString()}{t('원')}{foreign(b.price, fx)} </>}
                  {b.startDate && <>📅 {b.startDate}{b.endDate ? `~${b.endDate}` : ''} </>}
                </div>
              </li>
            ))}
          </ul>
        </section>
      )}

      <p className="muted small-text" style={{ textAlign: 'center', margin: '16px 0' }}>{t('스마트 여행 플래너로 공유된 일정')}</p>
    </div>
  )
}
