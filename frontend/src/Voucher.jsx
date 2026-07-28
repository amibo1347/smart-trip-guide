import { useState } from 'react'
import { api } from './api.js'
import { useI18n } from './i18n/index.jsx'

/**
 * 예약 확인증/바우처(이미지 또는 PDF) 보기·올리기·삭제. 예약 확인(일정)과 지갑에서 공용.
 * 예약 한 건(booking)에 파일 하나가 붙는다(= booking.ticketUrl).
 */
export default function Voucher({ booking, onChanged, onError }) {
  const { t } = useI18n()
  const [busy, setBusy] = useState(false)
  const isPdf = booking.ticketUrl?.toLowerCase().endsWith('.pdf')

  async function upload(e) {
    const file = e.target.files?.[0]
    e.target.value = ''
    if (!file) return
    setBusy(true)
    try { await api.attachTicket(booking.id, file); onChanged?.() }
    catch (err) { onError?.(err.message) } finally { setBusy(false) }
  }
  async function remove() {
    if (!window.confirm(t('확인증을 삭제할까요?'))) return
    setBusy(true)
    try { await api.removeTicket(booking.id); onChanged?.() }
    catch (err) { onError?.(err.message) } finally { setBusy(false) }
  }

  if (!booking.ticketUrl) {
    return (
      <label className="ticket-upload">
        {busy ? t('올리는 중...') : `📎 ${t('확인증 올리기')}`}
        <input type="file" accept="image/*,application/pdf" hidden onChange={upload} disabled={busy} />
      </label>
    )
  }
  return (
    <div className="ticket-file">
      {isPdf ? (
        <a className="ticket-view pdf" href={booking.ticketUrl} target="_blank" rel="noreferrer">📄 {t('확인증 PDF 열기')}</a>
      ) : (
        <a className="ticket-view" href={booking.ticketUrl} target="_blank" rel="noreferrer">
          <img src={booking.ticketUrl} alt={t('예약 확인증')} />
          <span>🔍 {t('크게 보기')}</span>
        </a>
      )}
      <button type="button" className="small ghost" onClick={remove} disabled={busy}>{t('삭제')}</button>
    </div>
  )
}
