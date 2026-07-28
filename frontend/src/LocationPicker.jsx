import { useEffect, useRef, useState } from 'react'
import L from 'leaflet'
import { api } from './api.js'
import { useI18n } from './i18n/index.jsx'
import { createMap, TILE_URL, TILE_OPTS, DEFAULT_CENTER } from './utils/mapMarkers.js'

/** 드래그 가능한 선택 핀. 노선도 핀과 같은 톤(외부 이미지 없이 CSS 로만). */
const PIN_ICON = L.divIcon({
  className: 'route-pin',
  html: '<div class="rp-dot pick-dot">📍</div><div class="rp-tip"></div>',
  iconSize: [30, 38], iconAnchor: [15, 38],
})

/**
 * 노선도 정거장의 위치를 직접 지정/수정하는 지도 모달.
 * 자동 지오코딩이 못 찾았거나 엉뚱한 곳에 찍혔을 때 사용자가 바로잡는 용도.
 *
 * 세 가지 방법을 제공한다.
 *  1) 지도를 눌러 핀 찍기(핀 드래그로 미세 조정)
 *  2) 장소명 검색 → 후보 선택
 *  3) '자동으로 다시 찾기' — 수동 지정을 풀고 서버가 다시 지오코딩하게 함
 *
 * @param {object}   stop     { itemId, title, latitude?, longitude? }
 * @param {array}    center   좌표 없을 때 지도 초기 중심 [lat, lng]
 * @param {function} onSaved  저장/자동복귀 후 호출(노선도 재조회)
 * @param {function} onClose
 * @param {function} onError
 */
export default function LocationPicker({ stop, center, onSaved, onClose, onError }) {
  const { t } = useI18n()
  const mapEl = useRef(null)
  const mapRef = useRef(null)
  const markerRef = useRef(null)

  const initial = stop.latitude != null ? [Number(stop.latitude), Number(stop.longitude)] : null
  const [pos, setPos] = useState(initial)          // 선택된 좌표 [lat, lng]
  const [q, setQ] = useState(stop.title || '')
  const [results, setResults] = useState(null)     // 검색 후보 (null = 검색 안 함)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    const onKey = (e) => { if (e.key === 'Escape') onClose() }
    window.addEventListener('keydown', onKey)
    const prev = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => { window.removeEventListener('keydown', onKey); document.body.style.overflow = prev }
  }, [onClose])

  // 지도 생성 + 클릭으로 핀 찍기
  useEffect(() => {
    const map = createMap(mapEl.current)
    L.tileLayer(TILE_URL, TILE_OPTS).addTo(map)
    mapRef.current = map
    map.setView(initial || center || DEFAULT_CENTER, initial ? 15 : 12)
    map.on('click', (e) => setPos([e.latlng.lat, e.latlng.lng]))
    // 모달이 열리며 크기가 잡힌 뒤 타일이 깨지지 않도록 한 번 갱신
    setTimeout(() => map.invalidateSize(), 120)
    return () => { map.remove(); mapRef.current = null }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  // 선택 좌표가 바뀌면 마커를 옮긴다(드래그로도 조정 가능).
  // Leaflet 기본 마커는 번들러에서 이미지 경로가 깨져 404 가 나므로 divIcon 을 쓴다.
  useEffect(() => {
    const map = mapRef.current
    if (!map || !pos) return
    if (!markerRef.current) {
      markerRef.current = L.marker(pos, { draggable: true, icon: PIN_ICON }).addTo(map)
      markerRef.current.on('dragend', (e) => {
        const p = e.target.getLatLng()
        setPos([p.lat, p.lng])
      })
    } else {
      markerRef.current.setLatLng(pos)
    }
  }, [pos])

  async function search(e) {
    e?.preventDefault()
    if (!q.trim()) return
    setBusy(true)
    try {
      const r = await api.searchPlaces(q.trim())
      setResults(r)
      if (r.length > 0) {
        const first = [Number(r[0].latitude), Number(r[0].longitude)]
        setPos(first)
        mapRef.current?.setView(first, 15)
      }
    } catch (e2) { onError?.(e2.message) } finally { setBusy(false) }
  }

  function choose(c) {
    const p = [Number(c.latitude), Number(c.longitude)]
    setPos(p)
    mapRef.current?.setView(p, 16)
    setResults(null)
  }

  async function save() {
    if (!pos) return
    setBusy(true)
    try {
      await api.setItemPlace(stop.itemId, { name: q.trim() || stop.title, latitude: pos[0], longitude: pos[1] })
      onSaved()
      onClose()
    } catch (e) { onError?.(e.message) } finally { setBusy(false) }
  }

  async function backToAuto() {
    setBusy(true)
    try { await api.autoLocateItem(stop.itemId); onSaved(); onClose() }
    catch (e) { onError?.(e.message) } finally { setBusy(false) }
  }

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal picker" onClick={(e) => e.stopPropagation()} role="dialog" aria-modal="true">
        <div className="tr-head">
          <h3>📍 {t('위치 지정')}</h3>
          <button className="x" onClick={onClose} aria-label={t('닫기')}>✕</button>
        </div>
        <p className="muted small-text" style={{ marginTop: -4 }}>
          {t('“{title}”의 위치를 지도에서 눌러 지정하거나, 이름으로 검색해 고르세요.', { title: stop.title })}
        </p>

        <form className="pick-search" onSubmit={search}>
          <input value={q} onChange={(e) => setQ(e.target.value)} placeholder={t('장소 이름으로 검색')} />
          <button type="submit" disabled={busy || !q.trim()}>{t('검색')}</button>
        </form>

        {results && (
          results.length === 0
            ? <p className="muted small-text">{t('검색 결과가 없어요. 지도를 직접 눌러 지정해 주세요.')}</p>
            : (
              <ul className="pick-results">
                {results.map((c, i) => (
                  <li key={i}><button type="button" onClick={() => choose(c)}>{c.displayName}</button></li>
                ))}
              </ul>
            )
        )}

        <div ref={mapEl} className="map pick-map" />

        <p className="muted small-text pick-coord">
          {pos
            ? `📍 ${pos[0].toFixed(5)}, ${pos[1].toFixed(5)} — ${t('핀을 끌어 미세 조정할 수 있어요.')}`
            : t('지도를 눌러 위치를 지정하세요.')}
        </p>

        <div className="row">
          <button type="button" className="small ghost" style={{ flex: 1 }} onClick={backToAuto} disabled={busy}>
            {t('자동으로 다시 찾기')}
          </button>
          <button type="button" style={{ flex: 2 }} onClick={save} disabled={busy || !pos}>
            {busy ? t('저장 중...') : t('이 위치로 저장')}
          </button>
        </div>
      </div>
    </div>
  )
}
