import { useEffect, useState } from 'react'
import { api } from './api.js'
import { useI18n } from './i18n/index.jsx'
import { fmt, foreign } from './utils/format.js'
import { PLAN_TYPE, BOOKING_TYPE, CHECKLIST_CATEGORY, categoryOrder, typeLabel } from './constants/labels.js'
import ShareSheet from './ShareSheet.jsx'

const fx2 = (krw, fx) => foreign(krw, fx, true) // 공유 뷰는 ≈ 표기

/**
 * 로그인 없이 보는 읽기 전용 일정(공유 링크 /share/{token}).
 * PDF 저장(인쇄)·링크 공유 버튼 제공.
 */
export default function SharedView({ token }) {
  const { t } = useI18n()
  const [data, setData] = useState(null)
  const [err, setErr] = useState('')
  const [shareOpen, setShareOpen] = useState(false) // 받은 사람이 다시 공유(단톡방 전달 등)

  useEffect(() => {
    api.getShared(token).then(setData).catch((e) => setErr(e.message))
  }, [token])

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
      <header><h1>🧳 {t('스마트 여행 플래너')}</h1></header>

      <section className="card hero">
        <h2 className="hero-title">{data.title}</h2>
        <p className="hero-meta">
          <span>📅 {data.startDate} ~ {data.endDate}</span>
          <span>👥 {data.headcount}{t('명')}</span>
          {data.concept && <span>🏷 {data.concept}</span>}
        </p>
        <div className="hero-actions no-print">
          <button className="btn-share" onClick={() => setShareOpen(true)}>🔗 {t('친구에게 공유')}</button>
          <button className="small ghost" onClick={() => window.print()}>🖨 {t('PDF로 저장')}</button>
        </div>
      </section>

      <section className="card">
        {fx && fx.code !== 'KRW' && fx.perKrw && (
          <p className="muted small-text">💱 {fx.code} {t('환율')}: ₩1,000 ≈ {fx.symbol}{Math.round(1000 * Number(fx.perKrw)).toLocaleString()}
            {' '}({fx.live ? t('실시간') : t('근사치')})</p>
        )}
        {/* 1인당 부담액 — 여럿이 가는 여행이면 링크만 받고도 각자 얼마인지 바로 안다 */}
        {data.headcount > 1 && data.perPerson != null && Number(data.plannedTotal) > 0 && (
          <div className="split" style={{ marginTop: 0 }}>
            <div>
              <div className="split-l">🧮 {t('1인당 부담액')}</div>
              <div className="split-sub">
                {t('합계')} {Number(data.plannedTotal).toLocaleString()}{t('원')} ÷ {data.headcount}{t('명')}
              </div>
            </div>
            <div className="split-v">{Number(data.perPerson).toLocaleString()}{t('원')}</div>
          </div>
        )}
        <p className="muted small-text no-print" style={{ marginBottom: 0 }}>{t('읽기 전용 공유 일정입니다.')}</p>
      </section>

      {shareOpen && (
        <ShareSheet
          url={window.location.href}
          title={data.title}
          subtitle={`${data.startDate} ~ ${data.endDate} · ${data.headcount}${t('명')}`}
          onClose={() => setShareOpen(false)}
        />
      )}

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
                    <span className="type-badge">{typeLabel(t, PLAN_TYPE, it.type)}</span>
                    <b>{it.title}</b>
                  </div>
                  <div className="item-meta">
                    {(it.plannedStart || it.plannedEnd) && (
                      <>⏰ {fmt(it.plannedStart)}{it.plannedEnd ? `~${fmt(it.plannedEnd)}` : ''} </>
                    )}
                    {bk ? (
                      <>
                        {bk.price != null && <>💰 {Number(bk.price).toLocaleString()}{t('원')}{fx2(bk.price, fx)} </>}
                        <span className="confirm-tag">{t('예약')}</span>{' '}
                      </>
                    ) : (
                      it.estCost != null && Number(it.estCost) > 0 && (
                        <>💰 {Number(it.estCost).toLocaleString()}{t('원')}{fx2(it.estCost, fx)} </>
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
          <h3>🧾 {t('예약')} ({data.bookings.length})</h3>
          <ul className="items">
            {data.bookings.map((b) => (
              <li key={b.id}>
                <div className="item-main">
                  <span className="type-badge">{typeLabel(t, BOOKING_TYPE, b.type)}</span>
                  <b>{b.title}</b>
                </div>
                <div className="item-meta">
                  {b.price != null && <>💰 {Number(b.price).toLocaleString()}{t('원')}{fx2(b.price, fx)} </>}
                  {b.startDate && <>📅 {b.startDate}{b.endDate ? `~${b.endDate}` : ''} </>}
                </div>
              </li>
            ))}
          </ul>
        </section>
      )}

      {data.checklist && data.checklist.length > 0 && (
        <section className="card">
          <h3>🎒 {t('준비물')} ({data.checklist.filter((i) => i.checked).length}/{data.checklist.length})</h3>
          {categoryOrder(data.checklist).map(([category, list]) => (
            <div className="chk-cat" key={category}>
              <div className="chk-cat-head">
                <span>{CHECKLIST_CATEGORY[category]?.emoji ?? '📦'}</span>
                <span>{t(CHECKLIST_CATEGORY[category]?.label ?? '기타')}</span>
              </div>
              <ul className="chk-list">
                {list.map((item) => (
                  <li className={item.checked ? 'chk-item done' : 'chk-item'} key={item.id}>
                    {/* 공유 화면은 읽기 전용 — 체크 상태만 보여주고 토글하지 않는다 */}
                    <span className="chk-box" aria-hidden="true">{item.checked ? '✓' : ''}</span>
                    <span className="chk-title">{item.title}</span>
                    {item.assignee && <span className="chk-who">{item.assignee}</span>}
                  </li>
                ))}
              </ul>
            </div>
          ))}
        </section>
      )}

      <p className="muted small-text" style={{ textAlign: 'center', margin: '16px 0' }}>{t('스마트 여행 플래너로 공유된 일정')}</p>
    </div>
  )
}
