// 클립보드 복사 / 공유 (이전엔 TripDetail·SharedView·Translate 에 중복).

/**
 * 텍스트를 클립보드에 복사. 실패 시 promptMsg 가 있으면 window.prompt 폴백.
 * @returns {Promise<boolean>} 클립보드 API 성공 여부.
 */
export async function copyToClipboard(text, promptMsg) {
  try {
    await navigator.clipboard.writeText(text)
    return true
  } catch {
    if (promptMsg) window.prompt(promptMsg, text)
    return false
  }
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
