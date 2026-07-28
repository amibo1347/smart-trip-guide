import { useState } from 'react'

/**
 * 장소 사진. 로드 실패(할당량 초과/사진 없음)면 깨진 이미지 대신 아이콘 자리표시자를 보여준다.
 * 사진 소스가 흔들려도 목록 자체는 항상 깔끔하게 유지된다.
 *
 * @param {string} src        사진 URL(없으면 바로 자리표시자)
 * @param {string} className  <img>/자리표시자 공용 클래스
 * @param {string} fallback   자리표시자 이모지
 */
export default function PlacePhoto({ src, className, fallback = '🏞', alt = '' }) {
  const [failed, setFailed] = useState(false)
  if (!src || failed) {
    return <span className={`${className} ph`} aria-hidden="true">{fallback}</span>
  }
  return <img className={className} src={src} alt={alt} loading="lazy" onError={() => setFailed(true)} />
}
