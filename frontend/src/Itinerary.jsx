import { useEffect, useState } from 'react'
import { api } from './api.js'

const TYPE_LABEL = {
  SPOT: '🏞 명소',
  MEAL: '🍽 식사',
  MOVE: '🚌 이동',
  STAY: '🏨 숙박',
  ACTIVITY: '🎯 액티비티',
}

export default function Itinerary({ trip, onBack, onError }) {
  const [plan, setPlan] = useState(null)
  const [openDay, setOpenDay] = useState(null) // 일정 추가 폼이 열린 dayId

  async function load() {
    try {
      setPlan(await api.getPlan(trip.id))
    } catch (e) {
      onError(e.message)
    }
  }

  useEffect(() => { load() /* eslint-disable-next-line */ }, [trip.id])

  async function removeItem(itemId) {
    try {
      await api.deletePlanItem(itemId)
      load()
    } catch (e) {
      onError(e.message)
    }
  }

  return (
    <div>
      <button className="link" onClick={onBack}>← 내 여행으로</button>

      <section className="card">
        <h2>🗺 {trip.title}</h2>
        <p className="muted">
          {trip.startDate} ~ {trip.endDate} · 👥 {trip.headcount}명
          {trip.concept && <> · 🏷 {trip.concept}</>}
        </p>
      </section>

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
            {day.items.map((it) => (
              <li key={it.id}>
                <div className="item-main">
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
            <AddItemForm
              dayId={day.id}
              onAdded={(p) => { setPlan(p); setOpenDay(null) }}
              onError={onError}
            />
          )}
        </section>
      ))}
    </div>
  )
}

function fmt(t) {
  return t ? t.slice(0, 5) : '' // "HH:mm:ss" → "HH:mm"
}

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
        place: form.placeName
          ? { name: form.placeName, address: form.address || null }
          : null,
      }
      const updated = await api.addPlanItem(dayId, payload)
      setForm(empty)
      onAdded(updated)
    } catch (err) {
      onError(err.message)
    } finally {
      setBusy(false)
    }
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
        <label>시작 <input type="time" value={form.plannedStart}
               onChange={(e) => setForm({ ...form, plannedStart: e.target.value })} /></label>
        <label>종료 <input type="time" value={form.plannedEnd}
               onChange={(e) => setForm({ ...form, plannedEnd: e.target.value })} /></label>
        <label>예상비용 <input type="number" min="0" placeholder="원" value={form.estCost}
               onChange={(e) => setForm({ ...form, estCost: e.target.value })} /></label>
      </div>
      <div className="row">
        <label style={{ flex: 1 }}>장소명(선택) <input value={form.placeName} placeholder="수동 입력"
               onChange={(e) => setForm({ ...form, placeName: e.target.value })} /></label>
        <label style={{ flex: 1 }}>주소(선택) <input value={form.address}
               onChange={(e) => setForm({ ...form, address: e.target.value })} /></label>
      </div>
      <button disabled={busy}>{busy ? '추가 중...' : '이 일자에 추가'}</button>
    </form>
  )
}
