import { useEffect, useState } from 'react'
import { api } from './api.js'
import Voucher from './Voucher.jsx'
import { useI18n } from './i18n/index.jsx'
import { foreign } from './utils/format.js'
import { BOOKING_TYPE, typeLabel } from './constants/labels.js'

/**
 * 예약 확인 — 미리 예약해 둔 항공/숙소를 등록하고, 확인증(e-티켓·바우처)을 붙인다.
 * 예전의 '링크 붙여넣기 → 이름/이미지 가져오기'는 없앴다. 지금은 기본 정보 + 확인증 파일만.
 */
export default function Bookings({ trip, onChanged, onError }) {
  const { t } = useI18n()
  const [list, setList] = useState([])
  const [open, setOpen] = useState(false)
  const [fx, setFx] = useState(null)

  async function load() {
    try { setList(await api.listBookings(trip.id)) } catch (e) { onError(e.message) }
  }
  useEffect(() => {
    let alive = true
    api.listBookings(trip.id).then((l) => { if (alive) setList(l) }).catch((e) => onError(e.message))
    api.getCurrency(trip.id).then((c) => { if (alive) setFx(c) }).catch(() => {})
    return () => { alive = false }
  }, [trip.id]) // eslint-disable-line react-hooks/exhaustive-deps

  async function remove(id) {
    if (!window.confirm(t('이 예약을 삭제할까요?'))) return
    try { await api.deleteBooking(id); await load(); onChanged?.() } catch (e) { onError(e.message) }
  }

  const total = list.reduce((s, b) => s + (b.price ? Number(b.price) : 0), 0)

  return (
    <div className="booking-section">
      <div className="day-head">
        <h3>🎫 {t('예약 확인')} ({list.length})</h3>
        <button className="small" onClick={() => setOpen(!open)}>{open ? t('닫기') : t('+ 추가')}</button>
      </div>

      {list.length === 0 && <p className="muted small-text">{t('예약한 항공/숙소를 등록하고 확인증을 올려두세요.')}</p>}

      <ul className="tickets">
        {list.map((b) => (
          <li key={b.id} className="ticket">
            <div className="ticket-head">
              <span className="type-badge">{typeLabel(t, BOOKING_TYPE, b.type)}</span>
              <div className="ticket-info">
                <b>{b.title}</b>
                <span className="ticket-meta">
                  {b.startDate && <>📅 {b.startDate}{b.endDate && b.endDate !== b.startDate ? `~${b.endDate}` : ''} </>}
                  {b.price != null && <> · 💰 {Number(b.price).toLocaleString()}{t('원')}{foreign(b.price, fx)}</>}
                </span>
              </div>
              <button className="del" onClick={() => remove(b.id)} title={t('삭제')}>✕</button>
            </div>
            <Voucher booking={b} onChanged={() => { load(); onChanged?.() }} onError={onError} />
          </li>
        ))}
      </ul>

      {list.length > 0 && (
        <p className="muted small-text">{t('예약 합계')}: <b>{total.toLocaleString()}{t('원')}{foreign(total, fx)}</b>
          {trip.budgetLimit != null && <> / {t('예산')} {Number(trip.budgetLimit).toLocaleString()}{t('원')}</>}</p>
      )}

      {open && <AddForm trip={trip} onSaved={() => { setOpen(false); load(); onChanged?.() }} onError={onError} />}
    </div>
  )
}

/** 예약 등록 폼 — 유형·이름·가격·날짜 + 확인증 파일(선택). 링크 가져오기 없음. */
function AddForm({ trip, onSaved, onError }) {
  const { t, lang } = useI18n()
  const empty = { type: 'HOTEL', title: '', price: '', startDate: trip.startDate || '', endDate: trip.endDate || '' }
  const [form, setForm] = useState(empty)
  const [file, setFile] = useState(null)      // 확인증 파일(선택)
  const [busy, setBusy] = useState(false)
  const isFlight = form.type === 'FLIGHT'

  async function save(e) {
    e.preventDefault()
    setBusy(true)
    try {
      // 1) 예약 등록(기본 정보만) → 2) 확인증 파일이 있으면 첨부
      const created = await api.addBooking(trip.id, {
        type: form.type,
        title: form.title,
        price: form.price === '' ? null : Number(form.price),
        bookingUrl: null,
        imageUrl: null,
        startDate: form.startDate || null,
        endDate: form.endDate || null,
        memo: null,
      }, null)
      if (file) await api.attachTicket(created.id, file)
      setForm(empty); setFile(null)
      onSaved()
    } catch (err) { onError(err.message) } finally { setBusy(false) }
  }

  return (
    <form className="add-form" onSubmit={save}>
      <div className="row">
        <label>{t('유형')}
          <select value={form.type} onChange={(e) => setForm({ ...form, type: e.target.value })}>
            <option value="HOTEL">🏨 {t('숙소')}</option>
            <option value="FLIGHT">✈️ {t('항공')}</option>
          </select>
        </label>
        <label style={{ flex: 2 }}>{t('이름')}
          <input value={form.title}
                 placeholder={isFlight ? t('예: KE001 인천-오사카 왕복') : t('예: 웰리나 호텔 우메다')}
                 onChange={(e) => setForm({ ...form, title: e.target.value })} required />
        </label>
      </div>
      <div className="row">
        <label style={{ flex: 1 }}>{t('가격')}
          <input type="number" min="0" placeholder={t('원')} value={form.price}
                 onChange={(e) => setForm({ ...form, price: e.target.value })} />
        </label>
        <label>{isFlight ? t('출발') : t('체크인')} <input type="date" lang={lang} value={form.startDate} onChange={(e) => setForm({ ...form, startDate: e.target.value })} /></label>
        <label>{isFlight ? t('귀국') : t('체크아웃')} <input type="date" lang={lang} value={form.endDate} onChange={(e) => setForm({ ...form, endDate: e.target.value })} /></label>
      </div>

      {/* 확인증 파일(선택) — 이미지/PDF */}
      <label className="ticket-upload as-field">
        {file ? `📎 ${file.name}` : `📎 ${t('확인증 파일 (선택, 이미지/PDF)')}`}
        <input type="file" accept="image/*,application/pdf" hidden
               onChange={(e) => setFile(e.target.files?.[0] || null)} disabled={busy} />
      </label>

      <button disabled={busy}>{busy ? t('저장 중...') : t('예약 등록')}</button>
    </form>
  )
}
