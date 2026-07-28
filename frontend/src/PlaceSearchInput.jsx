import { useEffect, useRef, useState } from 'react'
import { api } from './api.js'
import PlacePhoto from './PlacePhoto.jsx'
import { useI18n } from './i18n/index.jsx'

/**
 * 장소 검색 입력 + 결과 드롭다운. 목적지 고르기·장소 담기에서 공용으로 쓴다.
 * 고르면 onPick({ name, address, latitude, longitude, photoUrl }) 을 넘긴다.
 *
 * @param {string}   placeholder
 * @param {function} onPick
 * @param {string}   [initial]  입력창 초기값
 */
export default function PlaceSearchInput({ placeholder, onPick, initial = '', onError }) {
  const { t, lang } = useI18n()
  const [q, setQ] = useState(initial)
  const [results, setResults] = useState(null) // null=검색 전, []=결과없음
  const [busy, setBusy] = useState(false)
  const [open, setOpen] = useState(false)
  const boxRef = useRef(null)
  const timerRef = useRef(null)

  // 바깥 클릭 시 드롭다운 닫기
  useEffect(() => {
    const onDoc = (e) => { if (boxRef.current && !boxRef.current.contains(e.target)) setOpen(false) }
    document.addEventListener('mousedown', onDoc)
    return () => document.removeEventListener('mousedown', onDoc)
  }, [])

  // 입력 후 400ms 멈추면 검색(디바운스) — 타이핑마다 호출하지 않아 API 절약
  function onChange(v) {
    setQ(v)
    clearTimeout(timerRef.current)
    if (v.trim().length < 2) { setResults(null); setOpen(false); return }
    timerRef.current = setTimeout(() => run(v.trim()), 400)
  }

  async function run(query) {
    setBusy(true); setOpen(true)
    try { setResults(await api.searchPlacesRich(query, lang)) }
    catch (e) { onError?.(e.message); setResults([]) } finally { setBusy(false) }
  }

  function pick(p) {
    setQ(p.name)
    setOpen(false)
    onPick(p)
  }

  return (
    <div className="place-search" ref={boxRef}>
      <input value={q} placeholder={placeholder}
             onChange={(e) => onChange(e.target.value)}
             onFocus={() => { if (results) setOpen(true) }} />
      {open && (
        <div className="ps-drop">
          {busy && <div className="ps-empty">{t('검색 중...')}</div>}
          {!busy && results?.length === 0 && <div className="ps-empty">{t('검색 결과가 없어요.')}</div>}
          {!busy && results?.map((p) => (
            <button type="button" className="ps-item" key={p.providerId || p.name + p.address}
                    onClick={() => pick(p)}>
              <PlacePhoto className="ps-thumb" src={p.photoUrl} fallback="📍" alt={p.name} />
              <span className="ps-info">
                <b>{p.name}</b>
                {p.address && <span className="ps-addr">{p.address}</span>}
              </span>
            </button>
          ))}
        </div>
      )}
    </div>
  )
}
