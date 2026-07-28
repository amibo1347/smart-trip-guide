// 프로필 아바타 — 외부 이미지 없이 SVG(data URI)로만 만든다.
// 기본값은 카카오톡 기본 프로필 느낌의 회색 캐릭터. 프리셋은 색·표정이 다른 귀여운 캐릭터들.

const uri = (svg) => `data:image/svg+xml,${encodeURIComponent(svg)}`

// 카카오톡 기본 프로필 스타일 — 옅은 회색 배경 + 하얀 사람 실루엣(머리+어깨).
const DEFAULT_SVG = `<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 100 100'>
  <rect width='100' height='100' fill='#b3bac3'/>
  <circle cx='50' cy='40' r='17' fill='#f4f5f7'/>
  <path d='M20 88 a30 26 0 0 1 60 0 z' fill='#f4f5f7'/>
</svg>`

/** 캐릭터 아바타 생성기: 둥근 배경 + 크림색 얼굴 + 표정. */
function charSvg(bg, face) {
  return `<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 100 100'>
    <rect width='100' height='100' rx='26' fill='${bg}'/>
    <circle cx='50' cy='53' r='27' fill='#FFF7EC'/>
    ${face}
  </svg>`
}

const E = '#3d322c' // 눈·입 색
const eyes = (l = 41, r = 59, y = 49, rad = 3.4) =>
  `<circle cx='${l}' cy='${y}' r='${rad}' fill='${E}'/><circle cx='${r}' cy='${y}' r='${rad}' fill='${E}'/>`
const cheeks = (c = '#FFC7B0') =>
  `<circle cx='36' cy='58' r='4' fill='${c}' opacity='0.8'/><circle cx='64' cy='58' r='4' fill='${c}' opacity='0.8'/>`
const smile = `<path d='M42 61 q8 8 16 0' stroke='${E}' stroke-width='3' fill='none' stroke-linecap='round'/>`
const grin = `<path d='M40 60 q10 11 20 0 z' fill='${E}'/>`
const wink = `<circle cx='59' cy='49' r='3.4' fill='${E}'/><path d='M37 49 q4 -4 8 0' stroke='${E}' stroke-width='3' fill='none' stroke-linecap='round'/>`
const oh = `<ellipse cx='50' cy='62' rx='5' ry='6' fill='${E}'/>`
const flat = `<path d='M43 62 h14' stroke='${E}' stroke-width='3' fill='none' stroke-linecap='round'/>`

const PRESETS = [
  { id: 'p1', svg: charSvg('#6EC6FF', eyes() + cheeks() + smile) },
  { id: 'p2', svg: charSvg('#FFB74D', eyes() + grin) },
  { id: 'p3', svg: charSvg('#81C784', wink + cheeks('#FFB39C') + smile) },
  { id: 'p4', svg: charSvg('#BA68C8', eyes(41, 59, 49, 4) + oh) },
  { id: 'p5', svg: charSvg('#4DD0E1', eyes() + cheeks() + smile) },
  { id: 'p6', svg: charSvg('#F06292', eyes(41, 59, 48, 3) + grin) },
  { id: 'p7', svg: charSvg('#FFD54F', eyes() + flat) },
  { id: 'p8', svg: charSvg('#A1887F', wink + smile) },
]

export const DEFAULT_AVATAR = uri(DEFAULT_SVG)
export const AVATAR_PRESETS = PRESETS.map((p) => ({ id: `preset:${p.id}`, src: uri(p.svg) }))

/** 저장된 avatar 값 → 실제 이미지 src. null=기본, "preset:xx"=프리셋, 그 외=업로드 URL. */
export function avatarSrc(avatar) {
  if (!avatar) return DEFAULT_AVATAR
  if (avatar.startsWith('preset:')) {
    return AVATAR_PRESETS.find((a) => a.id === avatar)?.src || DEFAULT_AVATAR
  }
  return avatar
}
