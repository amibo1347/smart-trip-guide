import { useEffect, useState } from 'react'
import { api } from './api.js'
import Voucher from './Voucher.jsx'
import { useI18n } from './i18n/index.jsx'
import { won } from './utils/format.js'
import { BOOKING_TYPE, typeLabel } from './constants/labels.js'

// 가계부 카테고리(고정 목록). 값은 그대로 저장·집계된다.
const CATS = ['식비', '교통', '숙박', '관광', '쇼핑', '기타']

/**
 * '여행 지갑' — 예약 확인증 + 가계부(직접 입력 지출) + N빵 정산.
 * 지출을 세세하게 적고, 인원수로 나눈 1인당·결제자별 정산까지 한 화면에서 본다.
 */
export default function TripWallet({ trip, onError }) {
  const { t } = useI18n()
  const [bookings, setBookings] = useState(null)
  const [ledger, setLedger] = useState(null)
  const [budget, setBudget] = useState(null)

  async function load() {
    try {
      const [b, lg, bg] = await Promise.all([api.listBookings(trip.id), api.listExpenses(trip.id), api.getBudget(trip.id)])
      setBookings(b); setLedger(lg); setBudget(bg)
    } catch (e) { onError?.(e.message) }
  }
  useEffect(() => { load() /* eslint-disable-next-line */ }, [trip.id])

  const money = (n) => won(n, t('원'))

  return (
    <div>
      {/* 예산 한도 — 가계부 지출에 따라 남은 예산이 줄어든다 */}
      <BudgetLimitCard trip={trip} budget={budget} onChanged={load} onError={onError} money={money} t={t} />

      {/* 예약 확인증 */}
      <section className="card">
        <h2>🎫 {t('예약 확인증')}</h2>
        <p className="muted small-text" style={{ marginTop: -2 }}>
          {t('항공권·숙소 바우처를 올려두면 여행 중 바로 꺼내볼 수 있어요. (이미지 또는 PDF)')}
        </p>
        {bookings === null && <p className="muted small-text">{t('불러오는 중...')}</p>}
        {bookings?.length === 0 && (
          <p className="muted small-text">{t("'일정' 탭의 예약 도우미에서 항공/숙소를 먼저 등록하세요.")}</p>
        )}
        <div className="tickets">
          {bookings?.map((b) => (
            <TicketRow key={b.id} booking={b} onChanged={load} onError={onError} t={t} money={money} />
          ))}
        </div>
      </section>

      {/* 가계부 */}
      <ExpenseLedger trip={trip} ledger={ledger} onChanged={load} onError={onError} money={money} t={t} />

      {/* 정산(N빵) */}
      {ledger && ledger.items.length > 0 && (
        <Settlement ledger={ledger} money={money} t={t} />
      )}
    </div>
  )
}

/* ── 예산 한도: 지출이 쌓일수록 남은 예산이 줄어드는 게이지 ── */
function BudgetLimitCard({ trip, budget, onChanged, onError, money, t }) {
  const [editing, setEditing] = useState(false)
  const [value, setValue] = useState('')
  const [busy, setBusy] = useState(false)

  const limit = budget?.budgetLimit
  const spent = budget?.liveTotal ?? 0
  const remaining = budget?.liveRemaining
  const over = budget?.liveOverBudget
  const ratio = budget?.usedRatio ?? 0
  const level = over ? 'over' : ratio >= 80 ? 'near' : 'ok'

  async function save(next) {
    setBusy(true)
    try {
      const v = next !== undefined ? next : (value === '' ? null : Number(value))
      await api.setBudgetLimit(trip.id, v)
      setEditing(false); onChanged()
    } catch (e) { onError(e.message) } finally { setBusy(false) }
  }

  function openEdit() {
    setValue(limit != null ? String(limit) : '')
    setEditing(true)
  }

  if (budget === null) return null

  return (
    <section className={`card budget-limit ${level}`}>
      <div className="day-head">
        <h2>💰 {t('예산 한도')}</h2>
        {limit != null && !editing && (
          <button className="small ghost" onClick={openEdit}>✎ {t('수정')}</button>
        )}
      </div>

      {editing ? (
        <div className="bl-edit">
          <label className="field-label">{t('예산 한도 (원)')}</label>
          <input type="number" min="0" inputMode="numeric" value={value} placeholder={t('예: 900000')}
                 autoFocus onChange={(e) => setValue(e.target.value)} />
          <div className="row">
            <button className="small ghost" style={{ flex: 1 }} onClick={() => setEditing(false)}>{t('취소')}</button>
            <button style={{ flex: 2 }} disabled={busy} onClick={() => save()}>{busy ? t('저장 중...') : t('저장')}</button>
          </div>
          {limit != null && (
            <button className="link" disabled={busy} onClick={() => save(null)}>{t('한도 지우기')}</button>
          )}
        </div>
      ) : limit == null ? (
        <>
          <p className="muted small-text" style={{ marginTop: -2 }}>
            {t('예산 한도를 정해두면 가계부에 지출을 적을 때마다 남은 예산이 줄어드는 걸 볼 수 있어요.')}
          </p>
          <button onClick={openEdit}>＋ {t('예산 한도 설정')}</button>
        </>
      ) : (
        <>
          <div className="bl-top">
            <div>
              <div className="bl-remaining-label">{over ? t('예산 초과') : t('남은 예산')}</div>
              <div className={`bl-remaining ${over ? 'bad' : 'good'}`}>
                {over ? `-${money(budget.liveOverAmount)}` : money(remaining)}
              </div>
            </div>
            <div className="bl-ratio">{Math.round(ratio)}%</div>
          </div>
          <div className="bar"><div className="bar-fill" style={{ width: `${Math.min(ratio, 100)}%` }} /></div>
          <div className="budget" style={{ marginTop: 8 }}>
            <Row k={t('예산 한도')} v={money(limit)} />
            <Row k={t('지금까지 지출')} v={money(spent)} strong />
          </div>
        </>
      )}
    </section>
  )
}

/* ── 가계부: 지출 추가 + 목록(수정/삭제) ── */
function ExpenseLedger({ trip, ledger, onChanged, onError, money, t }) {
  const headcount = ledger?.headcount || trip.headcount || 1
  const [editing, setEditing] = useState(null) // 수정 중인 expense id
  const [adding, setAdding] = useState(false)

  return (
    <section className="card">
      <div className="day-head">
        <h2>📒 {t('가계부')}</h2>
        {!adding && <button className="small" onClick={() => { setAdding(true); setEditing(null) }}>＋ {t('지출 추가')}</button>}
      </div>
      <p className="muted small-text" style={{ marginTop: -2 }}>
        {t('쓴 돈을 적어두면 아래에서 항목별·1인당으로 자동 정산돼요.')}
      </p>

      {adding && (
        <ExpenseForm headcount={headcount} onCancel={() => setAdding(false)}
                     onSubmit={async (payload) => {
                       try { await api.addExpense(trip.id, payload); setAdding(false); onChanged() }
                       catch (e) { onError(e.message) }
                     }} t={t} />
      )}

      {ledger === null && <p className="muted small-text">{t('불러오는 중...')}</p>}
      {ledger?.items.length === 0 && !adding && (
        <p className="muted small-text">{t('아직 기록한 지출이 없어요. ‘지출 추가’로 시작해요.')}</p>
      )}

      <div className="exp-ledger">
        {ledger?.items.map((it) => (
          editing === it.id ? (
            <ExpenseForm key={it.id} headcount={headcount} initial={it} onCancel={() => setEditing(null)}
                         onSubmit={async (payload) => {
                           try { await api.updateExpense(it.id, payload); setEditing(null); onChanged() }
                           catch (e) { onError(e.message) }
                         }}
                         onDelete={async () => {
                           if (!window.confirm(t('이 지출을 삭제할까요?'))) return
                           try { await api.deleteExpense(it.id); setEditing(null); onChanged() }
                           catch (e) { onError(e.message) }
                         }} t={t} />
          ) : (
            <button key={it.id} className="exp-item" onClick={() => { setEditing(it.id); setAdding(false) }}>
              <span className="exp-item-cat">{it.category || t('기타')}</span>
              <span className="exp-item-main">
                <b>{it.title || t('(무제)')}</b>
                <span className="exp-item-sub">
                  {it.spentOn || ''}
                  {it.payer ? ` · 💳 ${it.payer}` : ''}
                  {it.splitCount > 1 ? ` · ${t('{n}명 나눔', { n: it.splitCount })}` : ''}
                </span>
              </span>
              <span className="exp-item-amt">
                {money(it.amount)}
                {it.splitCount > 1 && <span className="exp-item-per">1인 {money(it.perHead)}</span>}
              </span>
            </button>
          )
        ))}
      </div>
    </section>
  )
}

function ExpenseForm({ headcount, initial, onSubmit, onCancel, onDelete, t }) {
  const today = new Date().toISOString().slice(0, 10)
  const [spentOn, setSpentOn] = useState(initial?.spentOn || today)
  const [title, setTitle] = useState(initial?.title || '')
  const [category, setCategory] = useState(initial?.category || '식비')
  const [amount, setAmount] = useState(initial?.amount ?? '')
  const [splitCount, setSplitCount] = useState(initial?.splitCount || headcount || 1)
  const [payer, setPayer] = useState(initial?.payer || '')
  const [busy, setBusy] = useState(false)

  async function submit(e) {
    e.preventDefault()
    if (amount === '' || Number(amount) <= 0) return
    setBusy(true)
    await onSubmit({
      spentOn: spentOn || null,
      title: title.trim() || null,
      category,
      amount: Number(amount),
      splitCount: Number(splitCount) || 1,
      payer: payer.trim() || null,
    })
    setBusy(false)
  }

  return (
    <form className="exp-form-ledger" onSubmit={submit}>
      <input className="exp-title-in" placeholder={t('어디에 썼나요? (예: 도톤보리 저녁)')} value={title}
             maxLength={120} onChange={(e) => setTitle(e.target.value)} autoFocus />
      <div className="chip-row">
        {CATS.map((c) => (
          <button type="button" key={c} className={category === c ? 'chip on' : 'chip'} onClick={() => setCategory(c)}>{t(c)}</button>
        ))}
      </div>
      <div className="row">
        <label style={{ flex: 2 }}>{t('금액')}
          <input type="number" min="0" inputMode="numeric" value={amount} placeholder="0"
                 onChange={(e) => setAmount(e.target.value)} />
        </label>
        <label style={{ flex: 1.4 }}>{t('날짜')}
          <input type="date" value={spentOn} onChange={(e) => setSpentOn(e.target.value)} />
        </label>
      </div>
      <div className="row">
        <label style={{ flex: 1 }}>{t('나눠 낼 인원')}
          <input type="number" min="1" value={splitCount} onChange={(e) => setSplitCount(e.target.value)} />
        </label>
        <label style={{ flex: 1.4 }}>{t('결제자 (선택)')}
          <input placeholder={t('예: 나, 철수')} value={payer} maxLength={30} onChange={(e) => setPayer(e.target.value)} />
        </label>
      </div>
      <div className="row">
        {onDelete && <button type="button" className="mp-delete" style={{ flex: 1 }} onClick={onDelete}>{t('삭제')}</button>}
        <button type="button" className="small ghost" style={{ flex: 1 }} onClick={onCancel}>{t('취소')}</button>
        <button style={{ flex: 2 }} disabled={busy}>{busy ? t('저장 중...') : t('저장')}</button>
      </div>
    </form>
  )
}

/* ── 정산: 총액·1인당·항목별·결제자별·송금 추천 ── */
function Settlement({ ledger, money, t }) {
  const { total, headcount, perPerson, myShare, byCategory, byPayer, transfers } = ledger
  return (
    <section className="card">
      <h2>🧮 {t('정산')}</h2>
      <div className="budget">
        <Row k={t('총 지출')} v={money(total)} strong />
        <Row k={t('인원')} v={t('{n}명', { n: headcount })} />
        {headcount > 1 && <Row k={t('1인당 (균등 N빵)')} v={money(perPerson)} cls="good" />}
        {myShare != null && myShare !== total && (
          <Row k={t('내 몫 (항목별 나눔 합)')} v={money(myShare)} />
        )}
      </div>

      {Object.keys(byCategory).length > 0 && (
        <div className="bycat" style={{ marginTop: 10 }}>
          {Object.entries(byCategory).map(([c, v]) => (
            <span key={c} className="catchip">{t(c)} {money(v)}</span>
          ))}
        </div>
      )}

      {byPayer.length > 0 && (
        <div className="settle-block">
          <h4>💳 {t('결제자별')}</h4>
          {byPayer.map((p) => (
            <div key={p.payer} className="brow">
              <span>{p.payer} <span className="muted small-text">({t('냄')} {money(p.paid)})</span></span>
              <b className={p.net >= 0 ? 'good' : 'bad'}>
                {p.net >= 0 ? `+${money(p.net)} ${t('받을 돈')}` : `-${money(-p.net)} ${t('낼 돈')}`}
              </b>
            </div>
          ))}
        </div>
      )}

      {transfers.length > 0 && (
        <div className="settle-block">
          <h4>🔁 {t('이렇게 보내면 정산 끝')}</h4>
          {transfers.map((tr, i) => (
            <div key={i} className="settle-transfer">
              <b>{tr.from}</b> → <b>{tr.to}</b>
              <span className="settle-amt">{money(tr.amount)}</span>
            </div>
          ))}
        </div>
      )}
    </section>
  )
}

function Row({ k, v, strong, cls }) {
  return (
    <div className="brow">
      <span>{k}</span>
      <b className={cls}>{strong ? <b>{v}</b> : v}</b>
    </div>
  )
}

/** 예약 한 건 + 확인증(공용 Voucher 컴포넌트). */
function TicketRow({ booking, onChanged, onError, t, money }) {
  return (
    <div className="ticket">
      <div className="ticket-head">
        <span className="type-badge">{typeLabel(t, BOOKING_TYPE, booking.type)}</span>
        <div className="ticket-info">
          <b>{booking.title}</b>
          <span className="ticket-meta">
            {booking.startDate && <>📅 {booking.startDate}{booking.endDate && booking.endDate !== booking.startDate ? `~${booking.endDate}` : ''}</>}
            {booking.price != null && <> · 💰 {money(booking.price)}</>}
          </span>
        </div>
      </div>
      <Voucher booking={booking} onChanged={onChanged} onError={onError} />
    </div>
  )
}
