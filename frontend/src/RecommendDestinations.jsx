import { useState } from 'react'
import { useI18n } from './i18n/index.jsx'

// 추천 여행지 — 국내/해외로 나눈 인기 도시 목록. 고르면 그 이름으로 장소 검색해 목적지로 채운다.
const DOMESTIC = ['서울', '부산', '제주', '강릉', '여수', '경주', '전주', '속초', '인천', '대구', '통영', '포항']
const OVERSEAS = ['오사카', '도쿄', '후쿠오카', '방콕', '다낭', '나트랑', '세부', '타이베이', '홍콩', '싱가포르', '파리', '로마', '바르셀로나', '런던', '뉴욕', '발리']

/**
 * 추천 여행지 드롭다운. '추천 여행지' 를 펼치면 국내/해외 탭 + 도시 칩이 나온다.
 * 칩을 누르면 onSelect(도시명) — 상위에서 검색해 목적지로 채운다.
 */
export default function RecommendDestinations({ onSelect, busy }) {
  const { t } = useI18n()
  const [open, setOpen] = useState(false)
  const [tab, setTab] = useState('domestic')
  const list = tab === 'domestic' ? DOMESTIC : OVERSEAS

  return (
    <div className="recos">
      <button type="button" className="reco-toggle" onClick={() => setOpen((v) => !v)}>
        🔥 {t('추천 여행지')} <span className="chev">{open ? '▾' : '▸'}</span>
      </button>
      {open && (
        <div className="reco-panel">
          <div className="reco-tabs">
            <button type="button" className={tab === 'domestic' ? 'on' : ''} onClick={() => setTab('domestic')}>🇰🇷 {t('국내')}</button>
            <button type="button" className={tab === 'overseas' ? 'on' : ''} onClick={() => setTab('overseas')}>🌏 {t('해외')}</button>
          </div>
          <div className="reco-chips">
            {list.map((name) => (
              <button type="button" key={name} className="reco-chip" disabled={busy} onClick={() => onSelect(name)}>{name}</button>
            ))}
          </div>
        </div>
      )}
    </div>
  )
}
