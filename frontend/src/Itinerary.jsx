import { useEffect, useState } from 'react'
import { api } from './api.js'
import Bookings from './Bookings.jsx'
import { useI18n } from './i18n/index.jsx'
import { fmt, foreign, pct, won } from './utils/format.js'
import { PLAN_TYPE, typeLabel } from './constants/labels.js'
import Row from './components/Row.jsx'

const GMAPS_SEARCH = 'https://www.google.com/maps/search/?api=1&query='

export default function Itinerary({ trip, onError }) {
  const { t } = useI18n()
  const [plan, setPlan] = useState(null)
  const [openDay, setOpenDay] = useState(null)
  const [collapsed, setCollapsed] = useState({}) // dayId → true 면 그 일차 접힘
  const [editId, setEditId] = useState(null) // 인라인 편집 중인 항목 id
  const toggleCollapse = (id) => setCollapsed((c) => ({ ...c, [id]: !c[id] }))
  const [tick, setTick] = useState(0) // 활동비/예약 변경 시 예산 점검 재조회 트리거
  const [bookings, setBookings] = useState([]) // 확정 예약 — 일정 항목에 가격 연동 표시용
  const [fx, setFx] = useState(null)           // 목적지 통화·환율(원화+외화 병기)
  const bump = () => setTick((t) => t + 1)

  async function loadPlan() {
    try { setPlan(await api.getPlan(trip.id)) } catch (e) { onError(e.message) }
  }

  // 일정 + 확정 예약 + 환율 로드. tick 으로 갱신 — 예약 추가/삭제가 일정 항목(자동 생성)에 반영되므로 함께 재조회.
  useEffect(() => {
    let alive = true
    api.getPlan(trip.id).then((p) => { if (alive) setPlan(p) }).catch((e) => onError(e.message))
    api.listBookings(trip.id).then((b) => { if (alive) setBookings(b) }).catch((e) => onError(e.message))
    api.getCurrency(trip.id).then((c) => { if (alive) setFx(c) }).catch(() => { /* 환율 실패는 무시(원화만 표시) */ })
    return () => { alive = false }
  }, [trip.id, tick]) // eslint-disable-line react-hooks/exhaustive-deps

  const bookingById = Object.fromEntries(bookings.map((b) => [b.id, b]))

  async function removeItem(itemId) {
    try { await api.deletePlanItem(itemId); await loadPlan(); bump() } catch (e) { onError(e.message) }
  }
  async function move(itemId, direction) {
    try { setPlan(await api.movePlanItem(itemId, direction)) } catch (e) { onError(e.message) }
  }

  return (
    <div>
      <AiGenerate trip={trip} plan={plan} onGenerated={(p) => { setPlan(p); bump() }} onError={onError} />
      <section className="card">
        <BookingHelper trip={trip} plan={plan} onError={onError} />
        <hr className="card-div" />
        <Bookings trip={trip} onChanged={bump} onError={onError} />
      </section>
      <BudgetPanel trip={trip} tick={tick} fx={fx} onError={onError} />

      {!plan && <p className="muted">{t('일정 불러오는 중...')}</p>}

      {plan && plan.days.map((day) => (
        <section className="card" key={day.id}>
          <div className="day-head">
            <h3 className="day-toggle" onClick={() => toggleCollapse(day.id)} title={collapsed[day.id] ? t('펼치기') : t('접기')}>
              <span className="chev">{collapsed[day.id] ? '▸' : '▾'}</span> {t('{n}일차', { n: day.dayNo })}
              <span className="muted"> · {day.date}</span>
              <span className="muted small-text"> · {t('{n}개', { n: day.items.length })}</span>
            </h3>
            <button className="small" onClick={() => setOpenDay(openDay === day.id ? null : day.id)}>
              {openDay === day.id ? t('닫기') : t('+ 일정')}
            </button>
          </div>

          {!collapsed[day.id] && (<>
          {day.items.length === 0 && <p className="muted small-text">{t('아직 일정이 없습니다.')}</p>}
          <ul className="items">
            {day.items.map((it, idx) => (
              <li key={it.id}>
                <div className="item-main">
                  <span className="reorder">
                    <button className="rb" disabled={idx === 0} onClick={() => move(it.id, 'UP')} title={t('위로')}>▲</button>
                    <button className="rb" disabled={idx === day.items.length - 1} onClick={() => move(it.id, 'DOWN')} title={t('아래로')}>▼</button>
                  </span>
                  <span className="type-badge">{typeLabel(t, PLAN_TYPE, it.type)}</span>
                  <b>{it.title}</b>
                  <button className="edit" onClick={() => setEditId(editId === it.id ? null : it.id)} title={t('편집')}>✎</button>
                  <button className="del" onClick={() => removeItem(it.id)} title={t('삭제')}>✕</button>
                </div>
                {editId === it.id ? (
                  <EditItemForm item={it}
                                onSaved={(p) => { setPlan(p); setEditId(null); bump() }}
                                onCancel={() => setEditId(null)} onError={onError} />
                ) : (
                  <div className="item-meta">
                    {(it.plannedStart || it.plannedEnd) && (
                      <>⏰ {fmt(it.plannedStart)}{it.plannedEnd ? `~${fmt(it.plannedEnd)}` : ''} </>
                    )}
                    {it.bookingId && bookingById[it.bookingId] ? (
                      // 확정 예약에서 생성된 항목 — 비용은 예약 가격으로 표시(예산엔 예약 합계로만 반영)
                      <>
                        {bookingById[it.bookingId].price != null && (
                          <>💰 {Number(bookingById[it.bookingId].price).toLocaleString()}{t('원')}{foreign(bookingById[it.bookingId].price, fx)} </>
                        )}
                        <span className="confirm-tag">{t('확정 예약')}</span>{' '}
                        {bookingById[it.bookingId].bookingUrl && (
                          <a className="maplink" href={bookingById[it.bookingId].bookingUrl} target="_blank" rel="noreferrer">{t('예약 ↗')}</a>
                        )}{' '}
                      </>
                    ) : (
                      it.estCost != null && Number(it.estCost) > 0 && (
                        <>💰 {Number(it.estCost).toLocaleString()}{t('원')}{foreign(it.estCost, fx)} </>
                      )
                    )}
                    {it.place?.address && <>📍 {it.place.address} </>}
                    {(it.type === 'SPOT' || it.type === 'MEAL' || it.type === 'ACTIVITY') && (
                      <a className="maplink" href={mapUrl(it)} target="_blank" rel="noreferrer">🗺 {t('지도')}</a>
                    )}
                  </div>
                )}
              </li>
            ))}
          </ul>

          {openDay === day.id && (
            <AddItemForm dayId={day.id} onAdded={(p) => { setPlan(p); setOpenDay(null); bump() }} onError={onError} />
          )}
          </>)}
        </section>
      ))}
    </div>
  )
}

function BudgetPanel({ trip, tick, fx, onError }) {
  const { t } = useI18n()
  const [b, setB] = useState(null)
  const money = (n) => won(n, t('원')) + foreign(n, fx)

  useEffect(() => {
    let alive = true
    api.getBudget(trip.id).then((d) => { if (alive) setB(d) }).catch((e) => onError(e.message))
    return () => { alive = false }
  }, [trip.id, tick]) // eslint-disable-line react-hooks/exhaustive-deps

  if (!b) return null
  const has = b.budgetLimit != null
  return (
    <section className="card">
      <h3>💰 {t('예산 점검')}</h3>
      <div className="budget">
        <Row k={t('예산 한도')} v={has ? money(b.budgetLimit) : t('미설정')} />
        <Row k={t('활동비(예상)')} v={money(b.activityTotal)} />
        <Row k={t('확정 예약')} v={money(b.bookingTotal)} />
        <Row k={t('합계')} v={money(b.plannedTotal)} strong />
        {has && (
          <Row k={b.overBudget ? t('예산 초과') : t('남은 예산')}
               v={b.overBudget ? `-${money(b.overAmount)}` : `+${money(b.remaining)}`}
               cls={b.overBudget ? 'bad' : 'good'} />
        )}
      </div>
      {has && (
        <div className="bar">
          <div className="bar-fill" style={{ width: pct(b.plannedTotal, b.budgetLimit),
                 background: b.overBudget ? '#ef4444' : undefined }} />
        </div>
      )}
      {has && b.overBudget && <p className="warn-text">⚠️ {t('계획이 예산을 {amount} 초과했습니다.', { amount: won(b.overAmount, t('원')) })}</p>}
    </section>
  )
}


function AiGenerate({ trip, plan, onGenerated, onError }) {
  const { t } = useI18n()
  const [form, setForm] = useState({ note: '', origin: '서울', flightTime: 'ANY', lowCost: false })
  const [busy, setBusy] = useState(false)

  async function generate() {
    if (!window.confirm(t('AI가 새 일정 버전을 생성합니다. 기존 일정은 이전 버전으로 보관됩니다. 진행할까요?'))) return
    setBusy(true)
    try {
      onGenerated(await api.generatePlan(trip.id, {
        note: form.note || null,
        origin: form.origin || null,
        flightTime: form.flightTime,
        lowCost: form.lowCost,
      }))
    } catch (e) {
      onError(e.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <section className="card ai-card">
      <div className="ai-head">
        <h3>✨ {t('AI 일정 생성')}</h3>
      </div>
      <input placeholder={t('지역·요청사항 (예: 도톤보리 맛집 위주)')} value={form.note}
             onChange={(e) => setForm({ ...form, note: e.target.value })} disabled={busy} />
      <div className="row">
        <label style={{ flex: 1 }}>{t('출발지')}
          <input value={form.origin} placeholder={t('서울')}
                 onChange={(e) => setForm({ ...form, origin: e.target.value })} disabled={busy} />
        </label>
        <label style={{ flex: 1 }}>{t('항공 시간대')}
          <select value={form.flightTime} onChange={(e) => setForm({ ...form, flightTime: e.target.value })} disabled={busy}>
            <option value="ANY">{t('상관없음')}</option>
            <option value="MORNING">{t('오전')}</option>
            <option value="AFTERNOON">{t('낮')}</option>
            <option value="EVENING">{t('저녁')}</option>
          </select>
        </label>
      </div>
      <label className="chk">
        <input type="checkbox" checked={form.lowCost}
               onChange={(e) => setForm({ ...form, lowCost: e.target.checked })} disabled={busy} /> {t('저가항공(LCC) 선호')}
      </label>
      <button onClick={generate} disabled={busy}>
        {busy ? t('생성 중... (수 초 소요)') : t('✨ AI로 일정 만들기')}
      </button>
    </section>
  )
}

function BookingHelper({ trip, plan, onError }) {
  const { t } = useI18n()
  const [data, setData] = useState(null)
  const [open, setOpen] = useState(false)

  async function load() {
    try { setData(await api.bookingLinks(trip.id)) } catch (e) { onError(e.message) }
  }

  function toggle() {
    const next = !open
    setOpen(next)
    if (next && !data) load()
  }

  return (
    <div className="booking-section">
      <div className="day-head">
        <h3>🧳 {t('예약 도우미 (항공·숙소)')}</h3>
        <button className="small" onClick={toggle}>{open ? t('닫기') : t('열기')}</button>
      </div>
      {open && (
        <>
          {!data && <p className="muted small-text">{t('불러오는 중...')}</p>}
          {data && (
            <>
              <div className="link-group">
                <span className="lg-title">✈️ {t('항공권')}</span>
                {data.flights.map((l) => (
                  <a key={l.label} className="lk" href={l.url} target="_blank" rel="noreferrer">
                    {l.label}{l.prefilled ? ' 🔎' : ' ↗'}
                  </a>
                ))}
              </div>
              <div className="link-group">
                <span className="lg-title">🏨 {t('숙소')} ({t('{n}인', { n: trip.headcount })})</span>
                {data.hotels.map((l) => (
                  <a key={l.label} className="lk" href={l.url} target="_blank" rel="noreferrer">
                    {l.label}{l.prefilled ? ' 🔎' : ' ↗'}
                  </a>
                ))}
              </div>
            </>
          )}
        </>
      )}
    </div>
  )
}

function mapUrl(item) {
  const q = item.place?.name || item.title
  return GMAPS_SEARCH + encodeURIComponent(q)
}

function AddItemForm({ dayId, onAdded, onError }) {
  const { t } = useI18n()
  const empty = { type: 'SPOT', title: '', plannedStart: '', plannedEnd: '', estCost: '', placeName: '', address: '' }
  const [form, setForm] = useState(empty)
  const [busy, setBusy] = useState(false)

  async function submit(e) {
    e.preventDefault()
    setBusy(true)
    try {
      const payload = {
        type: form.type,
        title: form.title,
        plannedStart: form.plannedStart || null,
        plannedEnd: form.plannedEnd || null,
        estCost: form.estCost === '' ? null : Number(form.estCost),
        place: form.placeName ? { name: form.placeName, address: form.address || null } : null,
      }
      const updated = await api.addPlanItem(dayId, payload)
      setForm(empty)
      onAdded(updated)
    } catch (err) { onError(err.message) } finally { setBusy(false) }
  }

  return (
    <form className="add-form" onSubmit={submit}>
      <div className="row">
        <label>{t('유형')}
          <select value={form.type} onChange={(e) => setForm({ ...form, type: e.target.value })}>
            {Object.keys(PLAN_TYPE).map((v) => <option key={v} value={v}>{typeLabel(t, PLAN_TYPE, v)}</option>)}
          </select>
        </label>
        <label style={{ flex: 2 }}>{t('제목')}
          <input value={form.title} placeholder={t('예: 성산일출봉')}
                 onChange={(e) => setForm({ ...form, title: e.target.value })} required />
        </label>
      </div>
      <div className="row">
        <label>{t('시작')} <input type="time" value={form.plannedStart} onChange={(e) => setForm({ ...form, plannedStart: e.target.value })} /></label>
        <label>{t('종료')} <input type="time" value={form.plannedEnd} onChange={(e) => setForm({ ...form, plannedEnd: e.target.value })} /></label>
        <label>{t('예상비용')} <input type="number" min="0" placeholder={t('원')} value={form.estCost} onChange={(e) => setForm({ ...form, estCost: e.target.value })} /></label>
      </div>
      <div className="row">
        <label style={{ flex: 1 }}>{t('장소명(선택)')} <input value={form.placeName} onChange={(e) => setForm({ ...form, placeName: e.target.value })} /></label>
        <label style={{ flex: 1 }}>{t('주소(선택)')} <input value={form.address} onChange={(e) => setForm({ ...form, address: e.target.value })} /></label>
      </div>
      <button disabled={busy}>{busy ? t('추가 중...') : t('이 일자에 추가')}</button>
    </form>
  )
}

function EditItemForm({ item, onSaved, onCancel, onError }) {
  const { t } = useI18n()
  const [form, setForm] = useState({
    title: item.title ?? '',
    plannedStart: fmt(item.plannedStart),
    plannedEnd: fmt(item.plannedEnd),
    estCost: item.estCost != null ? String(item.estCost) : '',
  })
  const [busy, setBusy] = useState(false)

  async function submit(e) {
    e.preventDefault()
    setBusy(true)
    try {
      const updated = await api.updatePlanItem(item.id, {
        title: form.title,
        plannedStart: form.plannedStart || null,
        plannedEnd: form.plannedEnd || null,
        estCost: form.estCost === '' ? null : Number(form.estCost),
      })
      onSaved(updated)
    } catch (err) { onError(err.message) } finally { setBusy(false) }
  }

  return (
    <form className="add-form" onSubmit={submit}>
      <div className="row">
        <label style={{ flex: 2 }}>{t('제목')}
          <input value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} required />
        </label>
      </div>
      <div className="row">
        <label>{t('시작')} <input type="time" value={form.plannedStart} onChange={(e) => setForm({ ...form, plannedStart: e.target.value })} /></label>
        <label>{t('종료')} <input type="time" value={form.plannedEnd} onChange={(e) => setForm({ ...form, plannedEnd: e.target.value })} /></label>
        <label>{t('예상비용(1인)')} <input type="number" min="0" placeholder={t('원')} value={form.estCost}
               onChange={(e) => setForm({ ...form, estCost: e.target.value })} /></label>
      </div>
      <div className="row">
        <button disabled={busy}>{busy ? t('저장 중...') : t('저장')}</button>
        <button type="button" className="small" onClick={onCancel} disabled={busy}>{t('취소')}</button>
      </div>
    </form>
  )
}
