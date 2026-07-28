import { useEffect, useRef, useState } from 'react'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import { api } from './api.js'
import LocationPicker from './LocationPicker.jsx'
import { useI18n } from './i18n/index.jsx'
import { fmt, uuid, nowLocal } from './utils/format.js'
import { PLAN_TYPE, typeLabel } from './constants/labels.js'
import { createMap, TILE_URL, TILE_OPTS, DEFAULT_CENTER, ROUTE_COLOR } from './utils/mapMarkers.js'

const MOODS = ['😀', '😍', '😌', '😐', '😫', '🤩', '🥵', '🌧']
const CATEGORIES = ['식비', '교통', '숙박', '관광', '기타']

/**
 * 일정 기반 노선도. 일정 항목(장소)을 좌표로 변환해 방문 순서대로 잇고,
 * 각 장소에 '기록'(사진 + 지출 + 메모 + 기분)을 하나로 남긴다. 기록은 통합 기록(moment)에
 * planItemId 로 연결되므로 그 지출은 예산 집계에도 그대로 반영된다.
 *
 * @param {object}   trip
 * @param {boolean}  readOnly    true 면 기록 추가/삭제 UI 를 숨긴다(복기·공유용)
 * @param {function} onRecorded  기록 추가/삭제 시 호출(부모의 예산·지출목록 갱신용)
 * @param {function} onError
 */
export default function RouteMap({ trip, readOnly = false, onRecorded, onError }) {
  const { t } = useI18n()
  const [route, setRoute] = useState(null)
  const [view, setView] = useState('map')
  const [zoom, setZoom] = useState(null)     // 라이트박스로 크게 볼 사진 { url, caption }
  const [picking, setPicking] = useState(null) // 위치를 수동 지정할 정거장
  const pollRef = useRef(null)

  async function load({ keepPolling = true } = {}) {
    try {
      const r = await api.getRoute(trip.id)
      setRoute(r)
      clearTimeout(pollRef.current)
      if (keepPolling && r.pending > 0) pollRef.current = setTimeout(() => load(), 1500)
    } catch (e) { onError?.(e.message) }
  }

  useEffect(() => {
    load()
    return () => clearTimeout(pollRef.current)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [trip.id])

  useEffect(() => {
    if (!zoom) return
    const onKey = (e) => { if (e.key === 'Escape') setZoom(null) }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [zoom])

  // 장소에 기록 저장(사진+지출+메모+기분을 하나의 moment 로). planItemId 로 그 장소에 연결.
  async function saveRecord(itemId, { mood, amount, category, memo }, photoFile) {
    const data = {
      clientUuid: uuid(),
      planItemId: itemId,
      recordedAt: nowLocal(),
      latitude: null, longitude: null, accuracyM: null,
      mood: mood || null,
      amount: amount === '' || amount == null ? null : Number(amount),
      category: amount === '' || amount == null ? null : category,
      memo: memo || null,
    }
    await api.recordMoment(trip.id, data, photoFile || null)
    await load({ keepPolling: false })
    onRecorded?.()
  }
  async function removeRecord(momentId) {
    if (!window.confirm(t('이 기록을 삭제할까요? (사진도 함께 삭제됩니다)'))) return
    try { await api.deleteMoment(trip.id, momentId); await load({ keepPolling: false }); onRecorded?.() }
    catch (e) { onError?.(e.message) }
  }

  if (!route) return <section className="card"><p className="muted">{t('노선도 불러오는 중...')}</p></section>

  if (!route.hasPlan) {
    return (
      <section className="card">
        <h3>🗺 {t('여행 노선도')}</h3>
        <p className="muted small-text">{t('일정을 먼저 만들면 방문 장소가 지도에 노선으로 그려져요. ‘일정’ 탭에서 일정을 추가해 보세요.')}</p>
      </section>
    )
  }

  const allStops = [...route.stops, ...route.unlocated]
  const photoCount = allStops.reduce((n, s) => n + (s.records?.filter((r) => r.photoUrl).length || 0), 0)

  return (
    <section className="card">
      <div className="day-head">
        <h3 style={{ margin: 0 }}>🗺 {t('여행 노선도')}</h3>
        <div className="view-toggle">
          <button className={view === 'map' ? 'on' : ''} onClick={() => setView('map')}>🗺 {t('지도')}</button>
          <button className={view === 'album' ? 'on' : ''} onClick={() => setView('album')}>🖼 {t('사진첩')} {photoCount || ''}</button>
        </div>
      </div>

      {route.pending > 0 && (
        <p className="muted small-text">⏳ {t('장소 위치를 지도에 표시하는 중... ({n}곳 남음)', { n: route.pending })}</p>
      )}

      {view === 'map' && (
        <>
          {route.stops.length === 0 ? (
            <p className="muted small-text">
              {route.pending > 0
                ? t('위치를 변환하는 중이에요. 잠시만 기다려 주세요.')
                : t('지도에 표시할 수 있는 장소가 아직 없어요. 일정 항목에 장소명을 넣으면 노선이 그려져요.')}
            </p>
          ) : (
            <LeafletRoute stops={route.stops} center={route.centerLat && [Number(route.centerLat), Number(route.centerLng)]}
                          onOpenPhoto={setZoom} t={t} />
          )}
          <StopList stops={route.stops} unlocated={route.unlocated}
                    readOnly={readOnly} onSave={saveRecord} onRemove={removeRecord}
                    onOpen={setZoom} onPick={setPicking} t={t} />
        </>
      )}

      {view === 'album' && <PhotoAlbum stops={allStops} onOpen={setZoom} t={t} />}

      {zoom && (
        <div className="lightbox" onClick={() => setZoom(null)} role="dialog" aria-modal="true">
          <img src={zoom.url} alt={zoom.caption || t('여행 사진')} />
          {zoom.caption && <p>{zoom.caption}</p>}
        </div>
      )}

      {picking && (
        <LocationPicker
          stop={picking}
          center={route.centerLat && [Number(route.centerLat), Number(route.centerLng)]}
          onSaved={() => load({ keepPolling: false })}
          onClose={() => setPicking(null)}
          onError={onError}
        />
      )}
    </section>
  )
}

/** 순번 마커 + 사진 마커 + 노선(polyline) 을 그리는 Leaflet 지도. */
function LeafletRoute({ stops, center, onOpenPhoto, t }) {
  const ref = useRef(null)
  const mapRef = useRef(null)

  useEffect(() => {
    const map = createMap(ref.current)
    L.tileLayer(TILE_URL, TILE_OPTS).addTo(map)
    mapRef.current = map
    map.setView(center || DEFAULT_CENTER, 12)
    return () => { map.remove(); mapRef.current = null }
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => {
    const map = mapRef.current
    if (!map) return
    const layer = L.layerGroup().addTo(map)
    const pts = stops.map((s) => [Number(s.latitude), Number(s.longitude)])
    if (pts.length > 1) {
      L.polyline(pts, { color: ROUTE_COLOR, weight: 3, opacity: 0.55, dashArray: '1 8', lineCap: 'round' }).addTo(layer)
    }
    stops.forEach((s, i) => {
      L.marker(pts[i], { icon: markerIcon(s) }).bindPopup(popupHtml(s, t), { minWidth: 150 }).addTo(layer)
    })
    if (pts.length === 1) map.setView(pts[0], 14)
    else map.fitBounds(pts, { padding: [36, 36], maxZoom: 15 })

    map.on('popupopen', (e) => {
      e.popup.getElement()?.querySelectorAll('[data-photo]')?.forEach((el) => {
        el.addEventListener('click', () => onOpenPhoto({ url: el.getAttribute('data-photo'), caption: el.getAttribute('data-caption') || '' }))
      })
    })
    return () => { map.off('popupopen'); layer.remove() }
  }, [stops, onOpenPhoto, t])

  return <div ref={ref} className="map" />
}

const ESC = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }
const esc = (s) => String(s ?? '').replace(/[&<>"']/g, (c) => ESC[c])
function safePhoto(u) {
  const s = String(u || '')
  return (s.startsWith('/uploads/') || s.startsWith('http://') || s.startsWith('https://'))
    ? encodeURI(s).replace(/'/g, '%27').replace(/"/g, '%22') : ''
}
/** 그 장소 기록 중 첫 사진. */
const firstPhoto = (stop) => stop.records?.find((r) => r.photoUrl)?.photoUrl

function markerIcon(stop) {
  const photo = safePhoto(firstPhoto(stop))
  if (photo) {
    return L.divIcon({
      className: 'route-pin',
      html: `<div class="rp-img" style="background-image:url('${photo}')"></div><div class="rp-num">${stop.order}</div><div class="rp-tip"></div>`,
      iconSize: [46, 54], iconAnchor: [23, 54], popupAnchor: [0, -52],
    })
  }
  return L.divIcon({
    className: 'route-pin',
    html: `<div class="rp-dot">${stop.order}</div><div class="rp-tip"></div>`,
    iconSize: [30, 38], iconAnchor: [15, 38], popupAnchor: [0, -36],
  })
}

function popupHtml(s, t) {
  const parts = [`<div style="font-weight:700;margin-bottom:2px">${s.order}. ${esc(s.title)}</div>`]
  parts.push(`<div style="font-size:12px;color:#5f7787">${t('{n}일차', { n: s.dayNo })}${s.plannedStart ? ` · ${esc(fmt(s.plannedStart))}` : ''}</div>`)
  if (s.address) parts.push(`<div style="font-size:12px;color:#5f7787">📍 ${esc(s.address)}</div>`)
  const p = safePhoto(firstPhoto(s))
  if (p) parts.push(`<img data-photo="${p}" src="${p}" style="width:170px;border-radius:8px;display:block;margin-top:6px;cursor:zoom-in"/>`)
  const spent = (s.records || []).reduce((n, r) => n + (Number(r.amount) || 0), 0)
  if (spent > 0) parts.push(`<div style="font-size:12px;color:#5f7787;margin-top:4px">💰 ${spent.toLocaleString()}${esc(t('원'))}</div>`)
  return `<div style="min-width:150px">${parts.join('')}</div>`
}

/** 장소별 기록 목록. 위치가 있는 정거장(번호) → 위치 없는 항목 순. */
function StopList({ stops, unlocated, readOnly, onSave, onRemove, onOpen, onPick, t }) {
  return (
    <div className="stops">
      {stops.map((s) => (
        <StopRow key={s.itemId} stop={s} badge={s.order} readOnly={readOnly}
                 onSave={onSave} onRemove={onRemove} onOpen={onOpen} onPick={onPick} t={t} />
      ))}
      {unlocated.length > 0 && (
        <>
          <p className="muted small-text" style={{ margin: '10px 0 4px' }}>
            📍 {t('아래 항목은 지도에서 위치를 찾지 못했어요. 📍를 눌러 직접 지정할 수 있어요.')}
          </p>
          {unlocated.map((s) => (
            <StopRow key={s.itemId} stop={s} badge="·" readOnly={readOnly}
                     onSave={onSave} onRemove={onRemove} onOpen={onOpen} onPick={onPick} t={t} />
          ))}
        </>
      )}
    </div>
  )
}

function StopRow({ stop, badge, readOnly, onSave, onRemove, onOpen, onPick, t }) {
  const [open, setOpen] = useState(false)
  const records = stop.records || []
  const spent = records.reduce((n, r) => n + (Number(r.amount) || 0), 0)

  return (
    <div className="stop">
      <div className="stop-head">
        <span className="stop-no">{badge}</span>
        <div className="stop-info">
          <b>{stop.title}</b>
          <span className="stop-meta">
            {t('{n}일차', { n: stop.dayNo })}
            {stop.plannedStart && <> · ⏰ {fmt(stop.plannedStart)}</>}
            {' · '}{typeLabel(t, PLAN_TYPE, stop.type)}
            {spent > 0 && <> · 💰 {spent.toLocaleString()}{t('원')}</>}
          </span>
        </div>
        {!readOnly && (
          <>
            {/* 자동 지오코딩 결과를 직접 바로잡거나, 못 찾은 장소를 지정 */}
            <button type="button" className={stop.latitude != null ? 'stop-pin' : 'stop-pin need'}
                    onClick={() => onPick(stop)}
                    title={stop.latitude != null ? t('위치 수정') : t('위치 지정')}>📍</button>
            <button type="button" className="stop-add" onClick={() => setOpen((v) => !v)} title={t('기록 추가')}>
              {open ? t('닫기') : `＋ ${t('기록')}`}
            </button>
          </>
        )}
      </div>

      {records.length > 0 && (
        <div className="stop-records">
          {records.map((r) => (
            <RecordChip key={r.id} r={r} stopTitle={stop.title} readOnly={readOnly} onOpen={onOpen} onRemove={onRemove} t={t} />
          ))}
        </div>
      )}

      {open && !readOnly && (
        <RecordForm onCancel={() => setOpen(false)}
                    onSubmit={async (payload, file) => { await onSave(stop.itemId, payload, file); setOpen(false) }}
                    t={t} />
      )}
    </div>
  )
}

/** 저장된 기록 한 건: 사진 썸네일이 있으면 썸네일, 없으면 기분/메모/금액 칩. */
function RecordChip({ r, stopTitle, readOnly, onOpen, onRemove, t }) {
  const caption = r.memo || stopTitle
  if (r.photoUrl) {
    return (
      <div className="stop-thumb">
        <img src={r.photoUrl} alt={caption} loading="lazy" onClick={() => onOpen({ url: r.photoUrl, caption })} />
        {(r.amount != null || r.mood) && (
          <span className="thumb-tag">{r.mood || ''}{r.amount != null ? ` ₩${Number(r.amount).toLocaleString()}` : ''}</span>
        )}
        {!readOnly && <button className="thumb-del" onClick={() => onRemove(r.id)} title={t('삭제')}>✕</button>}
      </div>
    )
  }
  return (
    <div className="rec-note">
      {r.mood && <span className="m-mood">{r.mood}</span>}
      <span className="rec-text">
        {r.memo || (r.amount != null ? `${Number(r.amount).toLocaleString()}${t('원')}` : t('기록'))}
        {r.memo && r.amount != null && <> · 💰 {Number(r.amount).toLocaleString()}{t('원')}</>}
        {r.category && r.amount != null && <span className="muted"> ({t(r.category)})</span>}
      </span>
      {!readOnly && <button className="del" onClick={() => onRemove(r.id)} title={t('삭제')}>✕</button>}
    </div>
  )
}

/** 장소에 남길 기록 입력: 사진 + 지출(금액/분류) + 메모 + 기분. 하나 이상 있으면 저장. */
function RecordForm({ onSubmit, onCancel, t }) {
  const [mood, setMood] = useState('')
  const [amount, setAmount] = useState('')
  const [category, setCategory] = useState('식비')
  const [memo, setMemo] = useState('')
  const [file, setFile] = useState(null)
  const [preview, setPreview] = useState(null)
  const [busy, setBusy] = useState(false)

  useEffect(() => () => { if (preview) URL.revokeObjectURL(preview) }, [preview])

  function pick(e) {
    const f = e.target.files?.[0] || null
    setFile(f); setPreview(f ? URL.createObjectURL(f) : null)
  }
  async function submit(e) {
    e.preventDefault()
    if (!file && !memo && amount === '' && !mood) return // 아무것도 없으면 저장 안 함
    setBusy(true)
    try { await onSubmit({ mood, amount, category, memo }, file) }
    finally { setBusy(false) }
  }

  return (
    <form className="rec-form" onSubmit={submit}>
      <div className="moodbar">
        {MOODS.map((m) => (
          <button type="button" key={m} className={`mood ${mood === m ? 'sel' : ''}`}
                  onClick={() => setMood(mood === m ? '' : m)}>{m}</button>
        ))}
      </div>
      <div className="row">
        <label style={{ flex: 1 }}>{t('지출(선택)')}
          <input type="number" min="0" placeholder={t('원')} value={amount} onChange={(e) => setAmount(e.target.value)} />
        </label>
        <label>{t('분류')}
          <select value={category} disabled={amount === ''} onChange={(e) => setCategory(e.target.value)}>
            {CATEGORIES.map((c) => <option key={c} value={c}>{t(c)}</option>)}
          </select>
        </label>
      </div>
      <input placeholder={t('메모(선택) — 무엇을 했는지 한 줄')} value={memo} onChange={(e) => setMemo(e.target.value)} />
      <div className="photo-row">
        <label className="photo-pick">📷 {t('사진 첨부(선택)')}
          <input type="file" accept="image/*" capture="environment" onChange={pick} hidden />
        </label>
        {preview && <img className="m-thumb" src={preview} alt="" />}
      </div>
      <div className="row">
        <button type="button" className="small ghost" style={{ flex: 1 }} onClick={onCancel}>{t('취소')}</button>
        <button style={{ flex: 2 }} disabled={busy}>{busy ? t('기록 중...') : t('이 장소에 기록')}</button>
      </div>
    </form>
  )
}

/** 첨부된 모든 사진을 장소별로 묶어 그리드로 보여준다. */
function PhotoAlbum({ stops, onOpen, t }) {
  const withPhotos = stops
    .map((s) => ({ ...s, photos: (s.records || []).filter((r) => r.photoUrl) }))
    .filter((s) => s.photos.length > 0)
  if (withPhotos.length === 0) {
    return <p className="muted small-text">{t('아직 사진이 없어요. 지도의 장소에 기록을 남기며 사진을 붙여보세요.')}</p>
  }
  return (
    <>
      {withPhotos.map((s) => (
        <div key={s.itemId}>
          <div className="album-day">📍 {s.title} <span className="muted">· {t('{n}일차', { n: s.dayNo })}</span></div>
          <div className="album-grid">
            {s.photos.map((r) => (
              <button className="album-cell" key={r.id} onClick={() => onOpen({ url: r.photoUrl, caption: r.memo || s.title })}>
                <img src={r.photoUrl} alt={r.memo || s.title} loading="lazy" />
                {(r.memo || r.amount != null) && (
                  <span className="ac-place">{r.memo || `₩${Number(r.amount).toLocaleString()}`}</span>
                )}
              </button>
            ))}
          </div>
        </div>
      ))}
    </>
  )
}
