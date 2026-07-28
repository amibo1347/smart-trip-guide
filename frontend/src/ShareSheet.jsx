import { useEffect, useState } from 'react'
import { useI18n } from './i18n/index.jsx'
import { copyToClipboard } from './utils/clipboard.js'
import { availableChannels, canNativeShare } from './utils/share.js'

/**
 * 일정 초대 링크 공유 시트.
 * 링크 복사 + SNS 채널(카톡/인스타/X/페북/라인/밴드/텔레그램/왓츠앱/문자/메일)로 한 번에 공유한다.
 *
 * @param {string}   url        공유할 절대 URL (/share/{token})
 * @param {string}   title      여행 제목 — 공유 카드 제목
 * @param {string}   subtitle   기간·인원 등 한 줄 요약 — 공유 카드 설명
 * @param {function} onClose    닫기
 * @param {function} [onDisable] 소유자만 전달. 공유 중단(링크 무효화)
 */
export default function ShareSheet({ url, title, subtitle, onClose, onDisable }) {
  const { t } = useI18n()
  // 같은 문구를 연속으로 띄워도 다시 보이도록 매번 새 id 를 붙인다
  // (문자열만 넣으면 두 번째 클릭에서 상태가 안 바뀌어 토스트가 뜨지 않는다).
  const [toast, setToast] = useState(null) // { id, msg }
  const notify = (msg) => setToast((prev) => ({ id: (prev?.id ?? 0) + 1, msg }))

  // 모달이 열려 있는 동안 뒤 배경 스크롤 잠금 + ESC 로 닫기
  useEffect(() => {
    const prev = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    const onKey = (e) => { if (e.key === 'Escape') onClose() }
    window.addEventListener('keydown', onKey)
    return () => { document.body.style.overflow = prev; window.removeEventListener('keydown', onKey) }
  }, [onClose])

  // 토스트는 잠깐 띄우고 사라진다(알럿과 달리 공유 흐름을 끊지 않음).
  useEffect(() => {
    if (!toast) return
    const timer = setTimeout(() => setToast(null), 3200)
    return () => clearTimeout(timer)
  }, [toast])

  const shareText = t('{title} 여행 일정에 초대합니다 ✈️', { title })
  const payload = { title, text: subtitle ? `${shareText}\n${subtitle}` : shareText, url, promptMsg: t('아래 링크를 복사하세요') }

  async function runChannel(ch) {
    let result
    try { result = await ch.run(payload) }
    catch { notify(t('공유를 열지 못했어요. 링크를 복사해 사용하세요.')); return }

    // 'shared' = 그 앱/공유창이 실제로 열림. sms·mailto 처럼 '열렸는지 확인할 방법이 없는'
    // 채널만 afterHint 로 무슨 일이 일어났는지 알려준다.
    if (result === 'shared') { if (ch.afterHint) notify(t(ch.afterHint)) }
    else if (result === 'copied-and-opened') notify(t('링크를 복사했어요. 열린 창에 붙여넣어 공유하세요!'))
    else if (result === 'opened') notify(t('복사가 막혀 있어요. 위 링크를 직접 복사해 붙여넣어 주세요.'))
    else if (result === 'copied') notify(t(ch.copyHint ?? '링크를 복사했어요. 붙여넣어 공유하세요!'))
    else if (result === 'prompted') notify(t('링크를 직접 복사해 주세요.'))
  }

  async function copy() {
    notify(await copyToClipboard(url, payload.promptMsg)
      ? t('링크를 복사했어요. 붙여넣어 공유하세요!')
      : t('링크를 직접 복사해 주세요.'))
  }

  async function nativeShare() {
    try { await navigator.share({ title, text: payload.text, url }) } catch { /* 취소 무시 */ }
  }

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal sheet" onClick={(e) => e.stopPropagation()} role="dialog" aria-modal="true"
           aria-label={t('일정 공유')}>
        <div className="sheet-grip" />

        <div className="tr-head">
          <h3>🔗 {t('일정 공유')}</h3>
          <button className="x" onClick={onClose} aria-label={t('닫기')}>✕</button>
        </div>

        <p className="muted small-text sheet-lead">
          {t('링크를 받은 사람은 로그인 없이 일정을 볼 수 있어요. 여러 명에게 함께 보내보세요.')}
        </p>

        <div className="link-copy">
          <input readOnly value={url} onFocus={(e) => e.target.select()} aria-label={t('공유 링크')} />
          <button type="button" onClick={copy}>{t('복사')}</button>
        </div>

        {canNativeShare() && (
          <button className="share-native" onClick={nativeShare}>📤 {t('기기 공유 시트 열기')}</button>
        )}

        <div className="ch-grid">
          {availableChannels().map((ch) => (
            <button key={ch.id} className={`ch ch-${ch.brand}`} onClick={() => runChannel(ch)}>
              <span className="ch-ico" aria-hidden="true">{ch.emoji}</span>
              <span className="ch-label">{t(ch.label)}</span>
            </button>
          ))}
        </div>

        <div className="sheet-foot">
          <a className="sheet-link" href={url} target="_blank" rel="noreferrer">🖨 {t('공유 화면 미리보기 ↗')}</a>
          {onDisable && (
            <button className="sheet-link danger" onClick={onDisable}>{t('공유 끄기')}</button>
          )}
        </div>

        {toast && <div className="toast" role="status" key={toast.id}>{toast.msg}</div>}
      </div>
    </div>
  )
}
