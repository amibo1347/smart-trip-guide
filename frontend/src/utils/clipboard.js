// 클립보드 복사 / 공유 (이전엔 TripDetail·SharedView·Translate 에 중복).

/**
 * 텍스트를 클립보드에 복사. 세 단계로 내려간다.
 *   1) navigator.clipboard — https/localhost(보안 컨텍스트)에서만 존재
 *   2) 화면 밖 textarea + execCommand('copy') — 사내망 http 배포 등 비보안 컨텍스트 대비
 *   3) promptMsg 가 있으면 window.prompt 로 직접 복사하도록 노출
 *
 * @returns {Promise<boolean>} 클립보드에 실제로 들어갔는지.
 */
export async function copyToClipboard(text, promptMsg) {
  try {
    await navigator.clipboard.writeText(text)
    return true
  } catch { /* 비보안 컨텍스트/권한 거부 → 아래 폴백 */ }

  try {
    const ta = document.createElement('textarea')
    ta.value = text
    ta.setAttribute('readonly', '')
    ta.style.cssText = 'position:fixed;top:-9999px;opacity:0'
    document.body.appendChild(ta)
    ta.select()
    const ok = document.execCommand('copy')
    document.body.removeChild(ta)
    if (ok) return true
  } catch { /* 폴백도 실패 → prompt */ }

  if (promptMsg) window.prompt(promptMsg, text)
  return false
}

/**
 * 네이티브 공유 시트가 있으면 공유, 없으면 클립보드 복사로 폴백.
 * @returns {Promise<'shared'|'copied'|'prompted'>}
 */
export async function shareOrCopy({ title, url, promptMsg }) {
  if (navigator.share) {
    try { await navigator.share({ title, url }); return 'shared' } catch { /* 취소 무시 */ return 'shared' }
  }
  return (await copyToClipboard(url, promptMsg)) ? 'copied' : 'prompted'
}
