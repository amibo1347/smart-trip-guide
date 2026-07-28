import { useEffect, useRef, useState } from 'react'
import { api } from './api.js'
import PlaceSearchInput from './PlaceSearchInput.jsx'
import PlacePhoto from './PlacePhoto.jsx'
import { useI18n } from './i18n/index.jsx'
import { fmt } from './utils/format.js'
import { DISCOVERY_TO_PLAN_TYPE, PLAN_TYPE, ITEM_CATEGORY_ORDER } from './constants/labels.js'

// 탐색 카테고리 3분류(관광/식당·카페/쇼핑). 값은 백엔드 PlaceCategory enum.
const CATS = [
  { key: 'SIGHT', emoji: '🏞', label: '관광' },
  { key: 'FOOD', emoji: '🍽', label: '식당·카페' },
  { key: 'SHOPPING', emoji: '🛍', label: '쇼핑' },
]

/**
 * 장소 둘러보고 일정에 담기. 목적지의 인기 장소 → 장소 선택 시 주변(카테고리별) → 일정에 추가.
 * 담긴 장소는 좌표가 함께 저장돼 노선도에 바로 뜬다.
 *
 * @param {object}   trip     목적지 좌표(destinationLat/Lng) 사용
 * @param {object}   plan     일자 목록(days) — 담을 날짜 선택용
 * @param {function} onAdded  담은 뒤 호출(일정 재조회)
 * @param {function} onClose
 * @param {function} onError
 */
export default function PlaceExplorer({ trip, plan, onAdded, onClose, onError }) {
  const { t, lang } = useI18n()
  const hasDest = trip.destinationLat != null && trip.destinationLng != null
  const center = hasDest ? { lat: Number(trip.destinationLat), lng: Number(trip.destinationLng) } : null

  const [focus, setFocus] = useState(null)   // 선택한 기준 장소 { name, latitude, longitude }
  const [cat, setCat] = useState('SIGHT')
  const [list, setList] = useState(null)
  const [busy, setBusy] = useState(false)
  const [addingId, setAddingId] = useState(null) // 담기 폼을 연 장소 providerId
  const listRef = useRef(null)

  useEffect(() => {
    const onKey = (e) => { if (e.key === 'Escape') onClose() }
    window.addEventListener('keydown', onKey)
    const prev = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => { window.removeEventListener('keydown', onKey); document.body.style.overflow = prev }
  }, [onClose])

  // 기준: focus 가 있으면 그 좌표 주변, 없으면 목적지 인기 장소
  async function loadPopular() {
    if (!center) { setList([]); return }
    setBusy(true)
    try { setList(await api.popularPlaces(center.lat, center.lng, { lang, limit: 12, radiusKm: 20 })) }
    catch (e) { onError?.(e.message); setList([]) } finally { setBusy(false) }
  }
  async function loadNearby(place, category) {
    setBusy(true)
    try { setList(await api.nearbyPlaces(Number(place.latitude), Number(place.longitude), category, { lang, limit: 15 })) }
    catch (e) { onError?.(e.message); setList([]) } finally { setBusy(false) }
  }

  useEffect(() => { loadPopular() /* eslint-disable-next-line */ }, [])

  function pickFocus(p) {
    setFocus(p); setCat('SIGHT'); setAddingId(null)
    loadNearby(p, 'SIGHT')
    listRef.current?.scrollTo({ top: 0 })
  }
  function changeCat(c) {
    setCat(c); setAddingId(null)
    if (focus) loadNearby(focus, c)
  }
  function backToPopular() {
    setFocus(null); setAddingId(null); loadPopular()
  }

  async function addPlace(place, { dayId, type, plannedStart }) {
    setBusy(true)
    try {
      await api.addPlanItem(dayId, {
        type,
        title: place.name.slice(0, 100),
        plannedStart: plannedStart || null,
        place: {
          name: place.name.slice(0, 150),
          address: place.address || null,
          latitude: place.latitude,
          longitude: place.longitude,
        },
      })
      setAddingId(null)
      onAdded?.(place.name)
    } catch (e) { onError?.(e.message) } finally { setBusy(false) }
  }

  const defaultType = focus ? (DISCOVERY_TO_PLAN_TYPE[cat] || 'SPOT') : 'SPOT'

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal explorer" onClick={(e) => e.stopPropagation()} role="dialog" aria-modal="true">
        <div className="tr-head">
          <h3>🔍 {t('장소 담기')}</h3>
          <button className="x" onClick={onClose} aria-label={t('닫기')}>✕</button>
        </div>

        <PlaceSearchInput placeholder={t('가고 싶은 장소 검색')} onPick={pickFocus} onError={onError} />

        {/* 기준 안내: 목적지 인기 장소 or 선택 장소 주변 */}
        {focus ? (
          <div className="exp-focus">
            <button className="link" onClick={backToPopular}>← {t('인기 장소로')}</button>
            <span className="exp-focus-name">📍 {focus.name} {t('주변')}</span>
          </div>
        ) : (
          <p className="muted small-text exp-lead">
            {hasDest
              ? t('{dest} 인기 장소예요. 장소를 검색하면 그 주변도 볼 수 있어요.', { dest: trip.destinationName || t('목적지') })
              : t('목적지가 없어요. 위에서 장소를 검색해 주변을 둘러보세요.')}
          </p>
        )}

        {/* 카테고리 탭(주변 볼 때만) */}
        {focus && (
          <div className="exp-cats">
            {CATS.map((c) => (
              <button key={c.key} className={cat === c.key ? 'exp-cat on' : 'exp-cat'} onClick={() => changeCat(c.key)}>
                {c.emoji} {t(c.label)}
              </button>
            ))}
          </div>
        )}

        <div className="exp-list" ref={listRef}>
          {busy && <p className="muted small-text ta-center">{t('불러오는 중...')}</p>}
          {!busy && list?.length === 0 && (
            <p className="muted small-text ta-center">{t('표시할 장소가 없어요. 검색해 보세요.')}</p>
          )}
          {!busy && list?.map((p, i) => {
            const id = p.providerId || p.name + i
            const open = addingId === id
            return (
              <div className={open ? 'exp-card open' : 'exp-card'} key={id}>
                <div className="exp-row">
                  <PlacePhoto className="exp-photo" src={p.photoUrl}
                              fallback={focus ? CATS.find((c) => c.key === cat)?.emoji : '🏞'} alt={p.name} />
                  <div className="exp-info">
                    <div className="exp-name">
                      {!focus && <span className="exp-rank">{i + 1}</span>}
                      <b>{p.name}</b>
                    </div>
                    <div className="exp-meta">
                      {p.rating ? <span className="exp-rating">⭐ {p.rating} <span className="muted">({p.reviewCount})</span></span> : null}
                      {p.distanceKm != null && <span>📏 {p.distanceKm}km</span>}
                    </div>
                    {p.address && <div className="exp-addr">{p.address}</div>}
                  </div>
                </div>
                <div className="exp-foot">
                  <button type="button" className={open ? 'exp-add on' : 'exp-add'}
                          onClick={() => setAddingId(open ? null : id)}>
                    {open ? t('닫기') : `＋ ${t('담기')}`}
                  </button>
                </div>
                {open && (
                  <AddForm place={p} days={plan?.days || []} defaultType={defaultType}
                           busy={busy} onSubmit={(opts) => addPlace(p, opts)} t={t} lang={lang} />
                )}
              </div>
            )
          })}
        </div>
      </div>
    </div>
  )
}

/** 장소를 어느 날/유형/시간에 담을지 고르는 미니 폼. */
function AddForm({ place, days, defaultType, busy, onSubmit, t, lang }) {
  const [dayId, setDayId] = useState(days[0]?.id ?? '')
  const [type, setType] = useState(defaultType)
  const [time, setTime] = useState('')

  return (
    <form className="exp-form" onSubmit={(e) => { e.preventDefault(); if (dayId) onSubmit({ dayId, type, plannedStart: time }) }}>
      <div className="row">
        <label>{t('날짜')}
          <select value={dayId} onChange={(e) => setDayId(Number(e.target.value))}>
            {days.map((d) => (
              <option key={d.id} value={d.id}>{t('{n}일차', { n: d.dayNo })} · {d.date}</option>
            ))}
          </select>
        </label>
        <label>{t('시작 시간')}
          <input type="time" lang={lang} value={time} onChange={(e) => setTime(e.target.value)} />
        </label>
      </div>
      <label className="field-label">{t('유형')}</label>
      <div className="exp-types">
        {ITEM_CATEGORY_ORDER.map((code) => (
          <button type="button" key={code} className={type === code ? 'chip on' : 'chip'} onClick={() => setType(code)}>
            {PLAN_TYPE[code][0]} {t(PLAN_TYPE[code][1])}
          </button>
        ))}
      </div>
      <button disabled={busy || !dayId}>{busy ? t('담는 중...') : t('이 일정에 담기')}</button>
    </form>
  )
}
