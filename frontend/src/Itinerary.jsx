import { useEffect, useState } from 'react'
import { api } from './api.js'
import Bookings from './Bookings.jsx'
import PlaceExplorer from './PlaceExplorer.jsx'
import { useI18n } from './i18n/index.jsx'
import { fmt, foreign, pct, won } from './utils/format.js'
import { PLAN_TYPE, typeLabel } from './constants/labels.js'
import { weatherOf } from './utils/weather.js'
import Row from './components/Row.jsx'

/** 하루 날씨 칩. byDate[date] 가 있으면 이모지 + 최고/최저 + (강수확률). */
function WeatherChip({ w }) {
  const { t } = useI18n()
  if (!w) return null
  const { emoji, key } = weatherOf(w.code)
  return (
    <span className="wx" title={t(key)}>
      {emoji} {w.tempMax != null && <b>{Math.round(w.tempMax)}°</b>}
      {w.tempMin != null && <span className="wx-min">/{Math.round(w.tempMin)}°</span>}
      {w.precipProb != null && w.precipProb >= 30 && <span className="wx-pop">💧{w.precipProb}%</span>}
    </span>
  )
}

const GMAPS_SEARCH = 'https://www.google.com/maps/search/?api=1&query='

export default function Itinerary({ trip, defaultOrigin, onError }) {
  const { t } = useI18n()
  const [plan, setPlan] = useState(null)
  const [openDay, setOpenDay] = useState(null)
  const [collapsed, setCollapsed] = useState({}) // dayId → true 면 그 일차 접힘
  const [editId, setEditId] = useState(null) // 인라인 편집 중인 항목 id
  const toggleCollapse = (id) => setCollapsed((c) => ({ ...c, [id]: !c[id] }))
  const [view, setView] = useState('list') // 'list'(편집 가능) | 'timeline'(하루 흐름 훑어보기)
  const [explore, setExplore] = useState(false) // 장소 둘러보기 모달
  const [suggestDay, setSuggestDay] = useState(null) // 'AI 추천' 연 일자 id
  const [tick, setTick] = useState(0) // 활동비/예약 변경 시 예산 점검 재조회 트리거
  const [bookings, setBookings] = useState([]) // 확정 예약 — 일정 항목에 가격 연동 표시용
  const [fx, setFx] = useState(null)           // 목적지 통화·환율(원화+외화 병기)
  const [weather, setWeather] = useState({})   // date → DayWeather (목적지 좌표 있을 때만)
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

  // 날씨는 목적지 좌표에만 의존 → 한 번만 로드(예보 범위 밖 날짜는 응답에서 빠짐).
  useEffect(() => {
    let alive = true
    api.getWeather(trip.id)
      .then((list) => { if (alive) setWeather(Object.fromEntries((list || []).map((w) => [w.date, w]))) })
      .catch(() => { /* 날씨 실패는 무시 */ })
    return () => { alive = false }
  }, [trip.id])

  const bookingById = Object.fromEntries(bookings.map((b) => [b.id, b]))

  async function removeItem(itemId) {
    try { await api.deletePlanItem(itemId); await loadPlan(); bump() } catch (e) { onError(e.message) }
  }
  async function move(itemId, direction) {
    try { setPlan(await api.movePlanItem(itemId, direction)) } catch (e) { onError(e.message) }
  }

  return (
    <div>
      {/* 주 흐름: 장소를 직접 골라 담기. AI 는 아래에서 '부가 기능'으로 접어 둔다. */}
      <section className="card explore-cta">
        <h2>🧭 {t('장소 담아 일정 만들기')}</h2>
        <p className="muted small-text">
          {trip.destinationName
            ? t('{dest} 인기 장소와 주변을 둘러보고 마음에 드는 곳을 일정에 담으세요.', { dest: trip.destinationName })
            : t('가고 싶은 장소를 검색해 일정에 담으세요.')}
        </p>
        <button className="btn-explore" onClick={() => setExplore(true)} disabled={!plan}>
          🔍 {t('장소 둘러보고 담기')}
        </button>
      </section>

      <AiGenerate trip={trip} plan={plan} defaultOrigin={defaultOrigin} onGenerated={(p) => { setPlan(p); bump() }} onError={onError} />

      <section className="card">
        <BookingHelper trip={trip} plan={plan} onError={onError} />
        <hr className="card-div" />
        <Bookings trip={trip} onChanged={bump} onError={onError} />
      </section>
      <BudgetPanel trip={trip} tick={tick} fx={fx} onError={onError} />

      {explore && plan && (
        <PlaceExplorer trip={trip} plan={plan}
                       onAdded={() => { loadPlan(); bump() }}
                       onClose={() => setExplore(false)} onError={onError} />
      )}

      {!plan && <p className="muted">{t('일정 불러오는 중...')}</p>}

      {plan && plan.days.length > 0 && (
        <div className="day-head" style={{ marginBottom: 10 }}>
          <span className="muted small-text">{t('{n}일 일정', { n: plan.days.length })}</span>
          <div className="view-toggle">
            <button className={view === 'list' ? 'on' : ''} onClick={() => setView('list')}>☰ {t('목록')}</button>
            <button className={view === 'timeline' ? 'on' : ''} onClick={() => setView('timeline')}>🕒 {t('타임라인')}</button>
          </div>
        </div>
      )}

      {plan && view === 'timeline' && plan.days.map((day) => (
        <DayTimeline key={day.id} day={day} bookingById={bookingById} fx={fx} w={weather[day.date]} />
      ))}

      {plan && view === 'list' && plan.days.map((day) => (
        <section className="card" key={day.id}>
          <div className="day-head">
            <h3 className="day-toggle" onClick={() => toggleCollapse(day.id)} title={collapsed[day.id] ? t('펼치기') : t('접기')}>
              <span className="chev">{collapsed[day.id] ? '▸' : '▾'}</span> {t('{n}일차', { n: day.dayNo })}
              <span className="muted"> · {day.date}</span>
              <WeatherChip w={weather[day.date]} />
              <span className="muted small-text"> · {t('{n}개', { n: day.items.length })}</span>
            </h3>
            <div className="day-actions">
              <button className="small ghost" onClick={() => setSuggestDay(suggestDay === day.id ? null : day.id)}>
                ✨ {t('추천')}
              </button>
              <button className="small" onClick={() => setOpenDay(openDay === day.id ? null : day.id)}>
                {openDay === day.id ? t('닫기') : t('+ 일정')}
              </button>
            </div>
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
                        <span className="confirm-tag">{t('예약')}</span>{' '}
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
                      <a className="maplink" href={mapUrl(it, plan)} target="_blank" rel="noreferrer">🗺 {t('지도')}</a>
                    )}
                  </div>
                )}
              </li>
            ))}
          </ul>

          {suggestDay === day.id && (
            <DaySuggest trip={trip} day={day}
                        onAdded={() => { loadPlan(); bump() }}
                        onClose={() => setSuggestDay(null)} onError={onError} />
          )}

          {openDay === day.id && (
            <AddItemForm dayId={day.id} onAdded={(p) => { setPlan(p); setOpenDay(null); bump() }} onError={onError} />
          )}
          </>)}
        </section>
      ))}
    </div>
  )
}

/**
 * '이 날 AI 추천' — 하루치 장소를 AI 가 제안하면, 각 카드를 눌러 일정에 담는다.
 * 담을 때 장소를 실제 검색해 좌표·주소를 붙여 노선도에 바로 뜨게 한다.
 */
function DaySuggest({ trip, day, onAdded, onClose, onError }) {
  const { t, lang } = useI18n()
  const [items, setItems] = useState(null)   // null=로딩, []=없음
  const [busy, setBusy] = useState(false)
  const [addingIdx, setAddingIdx] = useState(null)
  const [addedNames, setAddedNames] = useState([])

  useEffect(() => {
    let alive = true
    api.suggestDay(trip.id, day.id)
      .then((r) => { if (alive) setItems(r) })
      .catch((e) => { if (alive) { setItems([]); onError(e.message) } })
    return () => { alive = false }
  }, [trip.id, day.id]) // eslint-disable-line react-hooks/exhaustive-deps

  // 추천을 담을 때: 이름으로 장소를 검색해 좌표·주소를 붙인다(없으면 이름만으로 담김 → 노선도가 지오코딩).
  async function add(s, idx) {
    setAddingIdx(idx); setBusy(true)
    try {
      let place = { name: s.name.slice(0, 150) }
      try {
        const hits = await api.searchPlacesRich(s.name, lang)
        if (hits?.[0]) place = { name: hits[0].name.slice(0, 150), address: hits[0].address || null,
                                 latitude: hits[0].latitude, longitude: hits[0].longitude }
      } catch { /* 검색 실패해도 이름만으로 담는다 */ }
      await api.addPlanItem(day.id, { type: s.type, title: s.name.slice(0, 100), place })
      setAddedNames((n) => [...n, s.name])
      onAdded?.()
    } catch (e) { onError(e.message) } finally { setBusy(false); setAddingIdx(null) }
  }

  return (
    <div className="day-suggest">
      <div className="ds-head">
        <b>✨ {t('{n}일차 추천', { n: day.dayNo })}</b>
        <button className="x" onClick={onClose} aria-label={t('닫기')}>✕</button>
      </div>
      {items === null && <p className="muted small-text ta-center">{t('AI가 추천을 고르는 중...')}</p>}
      {items?.length === 0 && <p className="muted small-text ta-center">{t('추천을 가져오지 못했어요.')}</p>}
      {items?.map((s, idx) => {
        const added = addedNames.includes(s.name)
        return (
          <div className={added ? 'ds-item added' : 'ds-item'} key={idx}>
            <span className="type-badge">{typeLabel(t, PLAN_TYPE, s.type)}</span>
            <div className="ds-body">
              <b>{s.name}</b>
              {s.reason && <span className="ds-reason">{s.reason}</span>}
            </div>
            <button className="ds-add" disabled={busy || added} onClick={() => add(s, idx)}>
              {added ? `✓ ${t('담김')}` : (busy && addingIdx === idx) ? '...' : `＋ ${t('담기')}`}
            </button>
          </div>
        )
      })}
    </div>
  )
}

/**
 * 하루 일정을 시간축으로 훑어보는 뷰(읽기 전용).
 * 시간이 지정된 항목이 먼저, 그 안에서는 시작시각 순 — 편집은 목록 뷰에서.
 */
function DayTimeline({ day, bookingById, fx, w }) {
  const { t } = useI18n()

  const items = [...day.items].sort((a, b) => {
    if (!a.plannedStart && !b.plannedStart) return 0
    if (!a.plannedStart) return 1 // 시간 미정은 뒤로
    if (!b.plannedStart) return -1
    return String(a.plannedStart).localeCompare(String(b.plannedStart))
  })

  return (
    <section className="card">
      <div className="day-head">
        <h3>{t('{n}일차', { n: day.dayNo })} <span className="muted">· {day.date}</span> <WeatherChip w={w} /></h3>
        <span className="muted small-text">{t('{n}개', { n: day.items.length })}</span>
      </div>

      {items.length === 0 ? (
        <p className="muted small-text">{t('아직 일정이 없습니다.')}</p>
      ) : (
        <ul className="timeline">
          {items.map((it) => {
            const bk = it.bookingId ? bookingById[it.bookingId] : null
            const cost = bk?.price ?? it.estCost
            return (
              <li className={bk ? 'tl-item confirmed' : 'tl-item'} key={it.id}>
                <span className="tl-dot" />
                <div className="tl-body">
                  <div className={it.plannedStart ? 'tl-time' : 'tl-time none'}>
                    {it.plannedStart
                      ? `${fmt(it.plannedStart)}${it.plannedEnd ? ` – ${fmt(it.plannedEnd)}` : ''}`
                      : t('시간 미정')}
                  </div>
                  <p className="tl-title">{it.title}</p>
                  <div className="tl-meta">
                    <span className="type-badge">{typeLabel(t, PLAN_TYPE, it.type)}</span>
                    {cost != null && Number(cost) > 0 && (
                      <span>💰 {Number(cost).toLocaleString()}{t('원')}{foreign(cost, fx)}</span>
                    )}
                    {bk && <span className="confirm-tag">{t('예약')}</span>}
                    {it.place?.address && <span>📍 {it.place.address}</span>}
                  </div>
                </div>
              </li>
            )
          })}
        </ul>
      )}
    </section>
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
        <Row k={t('예약')} v={money(b.bookingTotal)} />
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

      {/* 정산(1/N) — 여럿이 가는 여행에서 각자 얼마인지. 혼자면 나눌 게 없으니 감춘다. */}
      {b.headcount > 1 && b.perPersonPlanned != null && (
        <div className="split">
          <div>
            <div className="split-l">🧮 {t('1인당 부담액')}</div>
            <div className="split-sub">{t('합계 ÷ {n}명', { n: b.headcount })}</div>
          </div>
          <div className="split-v">{money(b.perPersonPlanned)}</div>
        </div>
      )}
    </section>
  )
}


function AiGenerate({ trip, plan, defaultOrigin, onGenerated, onError }) {
  const { t } = useI18n()
  const [form, setForm] = useState({ note: '', origin: defaultOrigin || '서울', flightTime: 'ANY', lowCost: false })
  const [busy, setBusy] = useState(false)
  const [open, setOpen] = useState(false) // AI 는 부가 기능 — 기본은 접어 둔다

  const hasItems = plan?.days?.some((d) => d.items.length > 0)

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
      <div className="ai-head ai-toggle" onClick={() => setOpen((v) => !v)}>
        <h3>✨ {t('AI로 한 번에 짜기')} <span className="ai-sub">{t('(선택)')}</span></h3>
        <span className="chev">{open ? '▾' : '▸'}</span>
      </div>
      {!open && (
        <p className="muted small-text" style={{ margin: 0 }}>
          {hasItems
            ? t('직접 담은 일정이 있어요. AI로 새로 짜면 기존 일정은 이전 버전으로 보관돼요.')
            : t('막막하면 AI가 전체 일정을 한 번에 만들어 줘요. 펼쳐서 사용하세요.')}
        </p>
      )}
      {open && (<>
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
      </>)}
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

function mapUrl(item, plan) {
  const name = item.place?.name || item.title
  const lat = plan?.destinationLat
  const lng = plan?.destinationLng
  // 목적지 좌표가 있으면 '그 지역 중심'으로 지도를 띄운 채 검색한다(@위도,경도,줌z).
  // 텍스트로 도시명을 덧붙이는 게 아니라 실제 위치에서 검색하므로,
  // "홍콩반점(한국)" 같은 이름에 지역명만 들어간 엉뚱한 곳이 걸러진다.
  if (lat != null && lng != null) {
    return `https://www.google.com/maps/search/${encodeURIComponent(name)}/@${lat},${lng},12z`
  }
  // 좌표가 없는(구버전) 일정은 폴백: 주소/도시명을 검색어에 덧붙여 지역을 보정한다.
  const region = item.place?.address || plan?.destinationCity || ''
  const q = region && !name.includes(region) ? `${name} ${region}` : name
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
