import { useEffect, useState } from 'react'
import { api } from './api.js'

const TYPE_LABEL = {
  SPOT: '🏞 명소',
  MEAL: '🍽 식사',
  MOVE: '🚌 이동',
  STAY: '🏨 숙박',
  ACTIVITY: '🎯 액티비티',
}

export default function Itinerary({ trip, onError }) {
  const [plan, setPlan] = useState(null)
  const [accs, setAccs] = useState([])
  const [openDay, setOpenDay] = useState(null)
  const [openAcc, setOpenAcc] = useState(false)

  async function loadPlan() {
    try { setPlan(await api.getPlan(trip.id)) } catch (e) { onError(e.message) }
  }
  async function loadAccs() {
    try { setAccs(await api.listAccommodations(trip.id)) } catch (e) { onError(e.message) }
  }
  useEffect(() => { loadPlan(); loadAccs() /* eslint-disable-next-line */ }, [trip.id])

  async function removeItem(itemId) {
    try { await api.deletePlanItem(itemId); loadPlan() } catch (e) { onError(e.message) }
  }
  async function move(itemId, direction) {
    try { setPlan(await api.movePlanItem(itemId, direction)) } catch (e) { onError(e.message) }
  }
  async function removeAcc(id) {
    try { await api.deleteAccommodation(id); loadAccs() } catch (e) { onError(e.message) }
  }

  return (
    <div>
      <AiGenerate trip={trip} plan={plan} onGenerated={setPlan} onError={onError} />

      {!plan && <p className="muted">일정 불러오는 중...</p>}

      {plan && plan.days.map((day) => (
        <section className="card" key={day.id}>
          <div className="day-head">
            <h3>{day.dayNo}일차 <span className="muted">· {day.date}</span></h3>
            <button className="small" onClick={() => setOpenDay(openDay === day.id ? null : day.id)}>
              {openDay === day.id ? '닫기' : '+ 일정'}
            </button>
          </div>

          {day.items.length === 0 && <p className="muted small-text">아직 일정이 없습니다.</p>}
          <ul className="items">
            {day.items.map((it, idx) => (
              <li key={it.id}>
                <div className="item-main">
                  <span className="reorder">
                    <button className="rb" disabled={idx === 0} onClick={() => move(it.id, 'UP')} title="위로">▲</button>
                    <button className="rb" disabled={idx === day.items.length - 1} onClick={() => move(it.id, 'DOWN')} title="아래로">▼</button>
                  </span>
                  <span className="type-badge">{TYPE_LABEL[it.type] ?? it.type}</span>
                  <b>{it.title}</b>
                  <button className="del" onClick={() => removeItem(it.id)} title="삭제">✕</button>
                </div>
                <div className="item-meta">
                  {(it.plannedStart || it.plannedEnd) && (
                    <>⏰ {fmt(it.plannedStart)}{it.plannedEnd ? `~${fmt(it.plannedEnd)}` : ''} </>
                  )}
                  {it.estCost != null && <>💰 {Number(it.estCost).toLocaleString()}원 </>}
                  {it.place?.address && <>📍 {it.place.address}</>}
                </div>
              </li>
            ))}
          </ul>

          {openDay === day.id && (
            <AddItemForm dayId={day.id} onAdded={(p) => { setPlan(p); setOpenDay(null) }} onError={onError} />
          )}
        </section>
      ))}

      {/* 숙박 */}
      <section className="card">
        <div className="day-head">
          <h3>🏨 숙박 ({accs.length})</h3>
          <button className="small" onClick={() => setOpenAcc(!openAcc)}>{openAcc ? '닫기' : '+ 숙박'}</button>
        </div>
        {accs.length === 0 && <p className="muted small-text">등록된 숙소가 없습니다.</p>}
        <ul className="items">
          {accs.map((a) => (
            <li key={a.id}>
              <div className="item-main">
                <b>{a.name}</b>
                <button className="del" onClick={() => removeAcc(a.id)} title="삭제">✕</button>
              </div>
              <div className="item-meta">
                🛏 {a.checkIn} ~ {a.checkOut}
                {a.cost != null && <> · 💰 {Number(a.cost).toLocaleString()}원</>}
                {a.place?.address && <> · 📍 {a.place.address}</>}
              </div>
            </li>
          ))}
        </ul>
        {openAcc && (
          <AddAccForm tripId={trip.id} onAdded={() => { setOpenAcc(false); loadAccs() }} onError={onError} />
        )}
      </section>
    </div>
  )
}

function AiGenerate({ trip, plan, onGenerated, onError }) {
  const [note, setNote] = useState('')
  const [busy, setBusy] = useState(false)

  async function generate() {
    if (!window.confirm('AI가 새 일정 버전을 생성합니다. 기존 일정은 이전 버전으로 보관됩니다. 진행할까요?')) return
    setBusy(true)
    try {
      onGenerated(await api.generatePlan(trip.id, { note: note || null }))
    } catch (e) {
      onError(e.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <section className="card ai-card">
      <div className="ai-head">
        <h3>✨ AI 일정 생성</h3>
        {plan && <span className="ver">v{plan.version} · {plan.generatedBy === 'AI' ? 'AI 생성' : '직접 작성'}</span>}
      </div>
      <input placeholder="지역·요청사항 (예: 제주 동부 위주, 맛집 많이)" value={note}
             onChange={(e) => setNote(e.target.value)} disabled={busy} />
      <button onClick={generate} disabled={busy}>
        {busy ? '생성 중... (수 초 소요)' : '✨ AI로 일정 만들기'}
      </button>
    </section>
  )
}

function fmt(t) { return t ? t.slice(0, 5) : '' }

function AddItemForm({ dayId, onAdded, onError }) {
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
        <label>유형
          <select value={form.type} onChange={(e) => setForm({ ...form, type: e.target.value })}>
            {Object.entries(TYPE_LABEL).map(([v, l]) => <option key={v} value={v}>{l}</option>)}
          </select>
        </label>
        <label style={{ flex: 2 }}>제목
          <input value={form.title} placeholder="예: 성산일출봉"
                 onChange={(e) => setForm({ ...form, title: e.target.value })} required />
        </label>
      </div>
      <div className="row">
        <label>시작 <input type="time" value={form.plannedStart} onChange={(e) => setForm({ ...form, plannedStart: e.target.value })} /></label>
        <label>종료 <input type="time" value={form.plannedEnd} onChange={(e) => setForm({ ...form, plannedEnd: e.target.value })} /></label>
        <label>예상비용 <input type="number" min="0" placeholder="원" value={form.estCost} onChange={(e) => setForm({ ...form, estCost: e.target.value })} /></label>
      </div>
      <div className="row">
        <label style={{ flex: 1 }}>장소명(선택) <input value={form.placeName} onChange={(e) => setForm({ ...form, placeName: e.target.value })} /></label>
        <label style={{ flex: 1 }}>주소(선택) <input value={form.address} onChange={(e) => setForm({ ...form, address: e.target.value })} /></label>
      </div>
      <button disabled={busy}>{busy ? '추가 중...' : '이 일자에 추가'}</button>
    </form>
  )
}

function AddAccForm({ tripId, onAdded, onError }) {
  const empty = { name: '', checkIn: '', checkOut: '', cost: '', address: '' }
  const [form, setForm] = useState(empty)
  const [busy, setBusy] = useState(false)

  async function submit(e) {
    e.preventDefault()
    setBusy(true)
    try {
      await api.addAccommodation(tripId, {
        name: form.name,
        checkIn: form.checkIn,
        checkOut: form.checkOut,
        cost: form.cost === '' ? null : Number(form.cost),
        address: form.address || null,
      })
      setForm(empty)
      onAdded()
    } catch (err) { onError(err.message) } finally { setBusy(false) }
  }

  return (
    <form className="add-form" onSubmit={submit}>
      <input placeholder="숙소명" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} required />
      <div className="row">
        <label>체크인 <input type="date" value={form.checkIn} onChange={(e) => setForm({ ...form, checkIn: e.target.value })} required /></label>
        <label>체크아웃 <input type="date" value={form.checkOut} onChange={(e) => setForm({ ...form, checkOut: e.target.value })} required /></label>
      </div>
      <div className="row">
        <label style={{ flex: 1 }}>비용 <input type="number" min="0" placeholder="원" value={form.cost} onChange={(e) => setForm({ ...form, cost: e.target.value })} /></label>
        <label style={{ flex: 2 }}>주소(선택) <input value={form.address} onChange={(e) => setForm({ ...form, address: e.target.value })} /></label>
      </div>
      <button disabled={busy}>{busy ? '추가 중...' : '숙박 추가'}</button>
    </form>
  )
}
