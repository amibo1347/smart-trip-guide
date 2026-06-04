import { useEffect, useRef, useState } from 'react'
import { api } from './api.js'
import { useI18n } from './i18n/index.jsx'

const TYPE_LABEL = { FLIGHT: ['✈️', '항공'], HOTEL: ['🏨', '숙소'] }
function typeLabel(t, code) { return TYPE_LABEL[code] ? `${TYPE_LABEL[code][0]} ${t(TYPE_LABEL[code][1])}` : code }

/** 원화 금액의 목적지 통화 환산 문자열 " ( ¥16,200)". 환율 없으면 빈 문자열. */
function foreign(krw, fx) {
  if (!fx || fx.code === 'KRW' || !fx.perKrw || krw == null) return ''
  const v = Number(krw) * Number(fx.perKrw)
  if (!isFinite(v) || v <= 0) return ''
  return ` (${fx.symbol}${Math.round(v).toLocaleString()})`
}

export default function Bookings({ trip, onChanged, onError }) {
  const { t } = useI18n()
  const [list, setList] = useState([])
  const [open, setOpen] = useState(false)
  const [fx, setFx] = useState(null)

  async function load() {
    try { setList(await api.listBookings(trip.id)) } catch (e) { onError(e.message) }
  }
  useEffect(() => { load() /* eslint-disable-next-line */ }, [trip.id])
  useEffect(() => {
    let alive = true
    api.getCurrency(trip.id).then((c) => { if (alive) setFx(c) }).catch(() => {})
    return () => { alive = false }
  }, [trip.id])

  async function remove(id) {
    try { await api.deleteBooking(id); await load(); onChanged?.() } catch (e) { onError(e.message) }
  }

  const total = list.reduce((s, b) => s + (b.price ? Number(b.price) : 0), 0)

  return (
    <div className="booking-section">
      <div className="day-head">
        <h3>🧾 {t('확정 예약')} ({list.length})</h3>
        <button className="small" onClick={() => setOpen(!open)}>{open ? t('닫기') : t('+ 추가')}</button>
      </div>

      {list.length === 0 && <p className="muted small-text">{t('예약 도우미에서 고른 항공/숙소의 링크를 붙여넣어 저장하세요.')}</p>}
      <ul className="items">
        {list.map((b) => (
          <li key={b.id} className="bk">
            {b.imageUrl && <img className="bk-img" src={b.imageUrl} alt="" referrerPolicy="no-referrer" />}
            <div className="bk-body">
              <div className="item-main">
                <span className="type-badge">{typeLabel(t, b.type)}</span>
                <b>{b.title}</b>
                <button className="del" onClick={() => remove(b.id)} title={t('삭제')}>✕</button>
              </div>
              <div className="item-meta">
                {b.price != null && <>💰 {Number(b.price).toLocaleString()}{t('원')}{foreign(b.price, fx)} </>}
                {b.startDate && <>📅 {b.startDate}{b.endDate ? `~${b.endDate}` : ''} </>}
                {b.bookingUrl && <a className="maplink" href={b.bookingUrl} target="_blank" rel="noreferrer">{t('예약 페이지 열기 ↗')}</a>}
              </div>
            </div>
          </li>
        ))}
      </ul>
      {list.length > 0 && <p className="muted small-text">{t('예약 합계')}: <b>{total.toLocaleString()}{t('원')}{foreign(total, fx)}</b>
        {trip.budgetLimit != null && <> / {t('예산')} {Number(trip.budgetLimit).toLocaleString()}{t('원')}</>}</p>}

      {open && <ImportForm trip={trip} onSaved={() => { setOpen(false); load(); onChanged?.() }} onError={onError} />}
    </div>
  )
}

function ImportForm({ trip, onSaved, onError }) {
  const { t, lang } = useI18n()
  // 날짜는 여행 기간으로 미리 채움. 사용자가 수정 가능.
  const empty = {
    url: '', type: 'HOTEL', title: '', price: '',
    startDate: trip.startDate || '', endDate: trip.endDate || '', memo: '',
  }
  const [form, setForm] = useState(empty)
  const [photo, setPhoto] = useState(null)          // File (직접 올린 사진)
  const [photoPreview, setPhotoPreview] = useState(null) // 로컬 미리보기 objectURL
  const [dragOver, setDragOver] = useState(false)
  const [busy, setBusy] = useState(false)
  const fileRef = useRef(null)

  function pickFile(file) {
    if (!file) return
    if (!file.type.startsWith('image/')) { onError(t('이미지 파일만 올릴 수 있어요.')); return }
    if (photoPreview) URL.revokeObjectURL(photoPreview)
    setPhoto(file)
    setPhotoPreview(URL.createObjectURL(file))
  }
  function clearPhoto() {
    if (photoPreview) URL.revokeObjectURL(photoPreview)
    setPhoto(null); setPhotoPreview(null)
    if (fileRef.current) fileRef.current.value = ''
  }
  function onDrop(e) {
    e.preventDefault(); setDragOver(false)
    pickFile(e.dataTransfer.files?.[0])
  }

  // 예약 링크에서 '이름'만 자동으로 가져오기(사진/가격 미리보기 없음)
  async function fetchTitle() {
    if (!form.url) return
    setBusy(true)
    try {
      const r = await api.linkTitle(form.url)
      if (r?.title) setForm((f) => ({ ...f, title: r.title }))
    } catch (e) { onError(e.message) } finally { setBusy(false) }
  }

  const isFlight = form.type === 'FLIGHT'

  async function save(e) {
    e.preventDefault()
    setBusy(true)
    try {
      await api.addBooking(trip.id, {
        type: form.type,
        title: form.title,
        price: form.price === '' ? null : Number(form.price),
        bookingUrl: form.url || null,
        imageUrl: null,
        startDate: form.startDate || null,
        endDate: form.endDate || null,
        memo: form.memo || null,
      }, isFlight ? null : photo)
      clearPhoto(); setForm(empty)
      onSaved()
    } catch (err) { onError(err.message) } finally { setBusy(false) }
  }

  return (
    <form className="add-form" onSubmit={save}>
      <div className="row">
        <input style={{ flex: 1 }} placeholder={t('예약 페이지 링크 (선택) — 붙여넣고 ‘이름 가져오기’')}
               value={form.url} onChange={(e) => setForm({ ...form, url: e.target.value })} disabled={busy} />
        <button type="button" className="small" onClick={fetchTitle} disabled={busy || !form.url}>
          {busy ? '...' : t('이름 가져오기')}
        </button>
      </div>

      {/* 사진: 숙소에서만(항공은 사진 불필요). 드래그&드롭 또는 클릭해서 파일 선택 */}
      {!isFlight && (
        <div className={`dropzone${dragOver ? ' over' : ''}`}
             onClick={() => fileRef.current?.click()}
             onDragOver={(e) => { e.preventDefault(); setDragOver(true) }}
             onDragLeave={() => setDragOver(false)}
             onDrop={onDrop}>
          {photoPreview ? (
            <div className="dz-has">
              <img className="bk-img" src={photoPreview} alt="" />
              <button type="button" className="small" onClick={(e) => { e.stopPropagation(); clearPhoto() }}>{t('사진 제거')}</button>
            </div>
          ) : (
            <span className="muted small-text">📷 {t('사진을 여기로 드래그하거나 클릭해서 올리기 (선택)')}</span>
          )}
          <input ref={fileRef} type="file" accept="image/*" hidden disabled={busy}
                 onChange={(e) => pickFile(e.target.files?.[0])} />
        </div>
      )}

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
        <label style={{ flex: 1 }}>{t('가격(본 값)')}
          <input type="number" min="0" placeholder={t('원')} value={form.price}
                 onChange={(e) => setForm({ ...form, price: e.target.value })} />
        </label>
        <label>{isFlight ? t('출발') : t('체크인')} <input type="date" lang={lang} value={form.startDate} onChange={(e) => setForm({ ...form, startDate: e.target.value })} /></label>
        <label>{isFlight ? t('귀국') : t('체크아웃')} <input type="date" lang={lang} value={form.endDate} onChange={(e) => setForm({ ...form, endDate: e.target.value })} /></label>
      </div>
      <button disabled={busy}>{busy ? t('저장 중...') : t('예약 저장')}</button>
    </form>
  )
}
