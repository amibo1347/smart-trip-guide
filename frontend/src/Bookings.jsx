import { useEffect, useState } from 'react'
import { api } from './api.js'

const TYPE_LABEL = { FLIGHT: '✈️ 항공', HOTEL: '🏨 숙소' }

export default function Bookings({ trip, onError }) {
  const [list, setList] = useState([])
  const [open, setOpen] = useState(false)

  async function load() {
    try { setList(await api.listBookings(trip.id)) } catch (e) { onError(e.message) }
  }
  useEffect(() => { load() /* eslint-disable-next-line */ }, [trip.id])

  async function remove(id) {
    try { await api.deleteBooking(id); load() } catch (e) { onError(e.message) }
  }

  const total = list.reduce((s, b) => s + (b.price ? Number(b.price) : 0), 0)

  return (
    <section className="card">
      <div className="day-head">
        <h3>🧾 확정 예약 ({list.length})</h3>
        <button className="small" onClick={() => setOpen(!open)}>{open ? '닫기' : '+ 링크로 추가'}</button>
      </div>

      {list.length === 0 && <p className="muted small-text">예약 도우미에서 고른 항공/숙소의 링크를 붙여넣어 저장하세요.</p>}
      <ul className="items">
        {list.map((b) => (
          <li key={b.id} className="bk">
            {b.imageUrl && <img className="bk-img" src={b.imageUrl} alt="" referrerPolicy="no-referrer" />}
            <div className="bk-body">
              <div className="item-main">
                <span className="type-badge">{TYPE_LABEL[b.type] ?? b.type}</span>
                <b>{b.title}</b>
                <button className="del" onClick={() => remove(b.id)} title="삭제">✕</button>
              </div>
              <div className="item-meta">
                {b.price != null && <>💰 {Number(b.price).toLocaleString()}원 </>}
                {b.startDate && <>📅 {b.startDate}{b.endDate ? `~${b.endDate}` : ''} </>}
                {b.bookingUrl && <a className="maplink" href={b.bookingUrl} target="_blank" rel="noreferrer">예약 페이지 열기 ↗</a>}
              </div>
            </div>
          </li>
        ))}
      </ul>
      {list.length > 0 && <p className="muted small-text">예약 합계: <b>{total.toLocaleString()}원</b>
        {trip.budgetLimit != null && <> / 예산 {Number(trip.budgetLimit).toLocaleString()}원</>}</p>}

      {open && <ImportForm trip={trip} onSaved={() => { setOpen(false); load() }} onError={onError} />}
    </section>
  )
}

function ImportForm({ trip, onSaved, onError }) {
  const empty = { url: '', type: 'HOTEL', title: '', price: '', startDate: '', endDate: '', memo: '', imageUrl: null }
  const [form, setForm] = useState(empty)
  const [preview, setPreview] = useState(null)
  const [busy, setBusy] = useState(false)

  async function fetchPreview() {
    if (!form.url) return
    setBusy(true)
    try {
      const p = await api.linkPreview(form.url)
      setPreview(p)
      setForm((f) => ({ ...f, title: f.title || p.title || '', imageUrl: p.imageUrl || null }))
    } catch (e) {
      onError(e.message) // 실패해도 수동 입력으로 저장 가능
    } finally {
      setBusy(false)
    }
  }

  async function save(e) {
    e.preventDefault()
    setBusy(true)
    try {
      await api.addBooking(trip.id, {
        type: form.type,
        title: form.title,
        price: form.price === '' ? null : Number(form.price),
        bookingUrl: form.url || null,
        imageUrl: form.imageUrl,
        startDate: form.startDate || null,
        endDate: form.endDate || null,
        memo: form.memo || null,
      })
      setForm(empty); setPreview(null)
      onSaved()
    } catch (err) { onError(err.message) } finally { setBusy(false) }
  }

  return (
    <form className="add-form" onSubmit={save}>
      <div className="row">
        <input style={{ flex: 1 }} placeholder="예약 페이지 링크 붙여넣기 (야놀자/아고다/항공권 등)" value={form.url}
               onChange={(e) => setForm({ ...form, url: e.target.value })} disabled={busy} />
        <button type="button" className="small" onClick={fetchPreview} disabled={busy || !form.url}>
          {busy ? '...' : '가져오기'}
        </button>
      </div>

      {preview && (
        <div className="preview">
          {preview.imageUrl && <img className="bk-img" src={preview.imageUrl} alt="" referrerPolicy="no-referrer" />}
          <div className="muted small-text">{preview.siteName || '미리보기'} · {preview.title}</div>
        </div>
      )}

      <div className="row">
        <label>유형
          <select value={form.type} onChange={(e) => setForm({ ...form, type: e.target.value })}>
            <option value="HOTEL">🏨 숙소</option>
            <option value="FLIGHT">✈️ 항공</option>
          </select>
        </label>
        <label style={{ flex: 2 }}>이름
          <input value={form.title} placeholder="가져오기 후 자동 채움 (수정 가능)"
                 onChange={(e) => setForm({ ...form, title: e.target.value })} required />
        </label>
      </div>
      <div className="row">
        <label style={{ flex: 1 }}>가격(본 값)
          <input type="number" min="0" placeholder="원" value={form.price}
                 onChange={(e) => setForm({ ...form, price: e.target.value })} />
        </label>
        <label>시작 <input type="date" value={form.startDate} onChange={(e) => setForm({ ...form, startDate: e.target.value })} /></label>
        <label>종료 <input type="date" value={form.endDate} onChange={(e) => setForm({ ...form, endDate: e.target.value })} /></label>
      </div>
      <button disabled={busy}>{busy ? '저장 중...' : '예약 저장'}</button>
    </form>
  )
}
