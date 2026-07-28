// SNS 공유 채널. 각 채널은 공유 URL 을 여는 방식이 달라 세 갈래로 나뉜다.
//  1) web intent  — 브라우저에서 바로 열리는 공유 URL 이 있음 (X/페북/라인/밴드/텔레그램/왓츠앱/문자/메일)
//  2) SDK         — 카카오톡. JS 키가 있으면 공식 SDK 로 피드 메시지 전송
//  3) 시트/복사    — 인스타그램처럼 웹 공유 URL 이 없는 곳은 OS 공유 시트 → 실패 시 링크 복사
import { copyToClipboard } from './clipboard.js'

const KAKAO_SDK = 'https://t1.kakaocdn.net/kakao_js_sdk/2.7.4/kakao.min.js'
const KAKAO_JS_KEY = import.meta.env.VITE_KAKAO_JS_KEY ?? ''

const enc = encodeURIComponent

/** 새 탭으로 공유 창 열기. 팝업 차단 시 같은 탭 이동으로 폴백. */
function openShare(url) {
  const w = window.open(url, '_blank', 'noopener,noreferrer,width=600,height=640')
  if (!w) window.location.href = url
}

/** 카카오 SDK 를 한 번만 로드하고 init 까지 마친 Kakao 객체를 돌려준다. 키가 없으면 null. */
let kakaoPromise = null
function loadKakao() {
  if (!KAKAO_JS_KEY) return Promise.resolve(null)
  if (kakaoPromise) return kakaoPromise
  kakaoPromise = new Promise((resolve) => {
    if (window.Kakao?.isInitialized?.()) return resolve(window.Kakao)
    const s = document.createElement('script')
    s.src = KAKAO_SDK
    s.async = true
    s.onload = () => {
      try {
        if (!window.Kakao.isInitialized()) window.Kakao.init(KAKAO_JS_KEY)
        resolve(window.Kakao)
      } catch { resolve(null) }
    }
    s.onerror = () => resolve(null)
    document.head.appendChild(s)
  })
  return kakaoPromise
}

/**
 * 웹 공유 URL 이 없는 채널(카톡·인스타)용 폴백. 세 단계로 내려간다.
 *   1) OS 공유 시트 — 모바일이면 목록에 그 앱이 뜬다(가장 좋은 경로)
 *   2) 링크를 복사한 뒤 대상 앱을 새 탭으로 열어준다 — 붙여넣기만 하면 되게
 *   3) 클립보드도 막히면 prompt 로 링크를 노출
 *
 * @param openUrl 2단계에서 열 대상 앱 주소(없으면 복사만)
 * @returns {Promise<'shared'|'copied-and-opened'|'copied'|'prompted'>}
 */
async function sheetOrCopy({ title, text, url, promptMsg }, openUrl) {
  if (navigator.share) {
    try { await navigator.share({ title, text, url }) } catch { /* 사용자가 취소해도 시트는 떴으므로 성공 취급 */ }
    return 'shared'
  }
  // 열어줄 대상이 있으면 복사 성공 여부와 무관하게 연다 — 복사만 실패했다고
  // 앱까지 안 열어주면 사용자는 아무 일도 안 일어난 것처럼 느낀다.
  const copied = await copyToClipboard(url, openUrl ? undefined : promptMsg)
  if (openUrl) { openShare(openUrl); return copied ? 'copied-and-opened' : 'opened' }
  return copied ? 'copied' : 'prompted'
}

/**
 * 공유 채널 목록.
 * - id/label/emoji/brand: 버튼 렌더용 (brand 는 CSS 클래스 .ch-{brand} 로 색 지정)
 * - touchOnly: 터치 기기에서만 노출(sms 처럼 PC 에 핸들러가 없는 채널)
 * - copyHint/afterHint: 복사 폴백/열기 직후에 띄울 안내 문구(한국어 키 — t() 로 번역)
 * - run({title,text,url,promptMsg}): 실제 공유 실행. 결과로 호출측 토스트 문구가 정해진다.
 *   'shared'            → 그 앱/공유창이 실제로 떴음
 *   'copied-and-opened' → 링크 복사 + 대상 앱 열기까지 됨
 *   'opened'            → 대상 앱은 열었지만 복사는 실패
 *   'copied'            → 링크만 복사함 (붙여넣기 안내 필요)
 *   'prompted'          → 클립보드가 막혀 prompt 로 노출함
 */
export const SHARE_CHANNELS = [
  {
    id: 'kakao', label: '카카오톡', emoji: '💬', brand: 'kakao',
    // 복사로 폴백했을 때 띄울 안내(카톡은 열어줄 웹 주소가 없어 붙여넣기가 유일한 경로).
    copyHint: '링크를 복사했어요. 카카오톡 대화창에 붙여넣어 보내세요.',
    async run(p) {
      const { title, text, url } = p
      const Kakao = await loadKakao()
      if (Kakao?.Share) {
        try {
          Kakao.Share.sendDefault({
            objectType: 'feed',
            content: {
              title,
              description: text,
              imageUrl: `${window.location.origin}/icons/icon-512.png`,
              link: { mobileWebUrl: url, webUrl: url },
            },
            buttons: [{ title: '일정 보기', link: { mobileWebUrl: url, webUrl: url } }],
          })
          return 'shared'
        } catch { /* SDK 실패 → 아래 폴백 */ }
      }
      // JS 키 미설정 or SDK 실패. 카카오톡은 외부 링크를 받는 웹 주소가 없어서
      // PC 에서는 열어줄 대상이 없다 → 복사만 하고 붙여넣기를 안내한다.
      return sheetOrCopy(p)
    },
  },
  {
    // 인스타그램은 외부 링크를 받는 웹 공유 URL 이 없다(스토리 공유는 네이티브 앱 전용).
    // → 모바일은 공유 시트, PC 는 링크를 복사한 뒤 인스타그램을 열어 붙여넣게 한다.
    id: 'instagram', label: '인스타그램', emoji: '📸', brand: 'instagram',
    run: (p) => sheetOrCopy(p, 'https://www.instagram.com/'),
  },
  {
    id: 'x', label: 'X (트위터)', emoji: '𝕏', brand: 'x',
    run: ({ text, url }) => { openShare(`https://twitter.com/intent/tweet?text=${enc(text)}&url=${enc(url)}`); return 'shared' },
  },
  {
    // 워드마크가 뚜렷한 채널(f/L/B/W)은 이모지 대신 글자를 써야 작은 원 안에서 알아보기 쉽다.
    id: 'facebook', label: '페이스북', emoji: 'f', brand: 'facebook',
    run: ({ url }) => { openShare(`https://www.facebook.com/sharer/sharer.php?u=${enc(url)}`); return 'shared' },
  },
  {
    id: 'line', label: '라인', emoji: 'L', brand: 'line',
    run: ({ text, url }) => { openShare(`https://social-plugins.line.me/lineit/share?url=${enc(url)}&text=${enc(text)}`); return 'shared' },
  },
  {
    id: 'band', label: '밴드', emoji: 'B', brand: 'band',
    run: ({ text, url }) => { openShare(`https://band.us/plugin/share?body=${enc(`${text}\n${url}`)}&route=${enc(url)}`); return 'shared' },
  },
  {
    id: 'telegram', label: '텔레그램', emoji: '✈️', brand: 'telegram',
    run: ({ text, url }) => { openShare(`https://t.me/share/url?url=${enc(url)}&text=${enc(text)}`); return 'shared' },
  },
  {
    id: 'whatsapp', label: 'WhatsApp', emoji: 'W', brand: 'whatsapp',
    run: ({ text, url }) => { openShare(`https://wa.me/?text=${enc(`${text}\n${url}`)}`); return 'shared' },
  },
  {
    // sms: 는 PC 에 핸들러가 없어 눌러도 아무 일이 없다 → 터치 기기에서만 노출한다.
    // sms:?&body= 형태가 iOS/Android 양쪽에서 가장 널리 동작한다.
    id: 'sms', label: '문자', emoji: '📩', brand: 'sms', touchOnly: true,
    afterHint: '문자 앱을 열었어요.',
    run: ({ text, url }) => { window.location.href = `sms:?&body=${enc(`${text}\n${url}`)}`; return 'shared' },
  },
  {
    // mailto 는 본문에 링크가 이미 채워져 나가지만, 메일 클라이언트가 없으면 아무 일도 없다.
    // 눌렀는데 조용한 상황을 막으려고 안내 문구를 함께 띄운다.
    id: 'email', label: '메일', emoji: '📧', brand: 'email',
    afterHint: '메일 앱을 열었어요. 열리지 않으면 위 링크를 복사해 보내주세요.',
    run: ({ title, text, url }) => { window.location.href = `mailto:?subject=${enc(title)}&body=${enc(`${text}\n${url}`)}`; return 'shared' },
  },
]

/** OS 공유 시트를 쓸 수 있는 환경인지(모바일). 버튼 노출 여부 판단용. */
export const canNativeShare = () => typeof navigator !== 'undefined' && !!navigator.share

/** 터치 기기인지 — sms 처럼 모바일에서만 의미 있는 채널을 거르는 데 쓴다. */
export const isTouchDevice = () =>
  typeof window !== 'undefined' &&
  (navigator.maxTouchPoints > 0 || window.matchMedia?.('(pointer: coarse)').matches)

/** 현재 기기에서 실제로 동작하는 채널만. */
export const availableChannels = () =>
  SHARE_CHANNELS.filter((c) => !c.touchOnly || isTouchDevice())
