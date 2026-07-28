import { useEffect, useRef } from 'react'
import maplibregl from 'maplibre-gl'
import 'maplibre-gl/dist/maplibre-gl.css'

// 무료·키불필요 벡터 타일(OpenFreeMap). 라스터(OSM)와 달리 라벨을 우리가 원하는 언어로 바꿀 수 있다.
const STYLE = 'https://tiles.openfreemap.org/styles/liberty'

/**
 * 누적 방문 지도 — 여러 여행의 방문 장소를 한 지도에 찍는다.
 * 지명 라벨을 '한국어(name:ko) 우선'으로 바꿔 해외 지역도 한글로 보이게 한다.
 */
export default function VisitedMap({ places }) {
  const ref = useRef(null)

  useEffect(() => {
    if (!places?.length) return
    const map = new maplibregl.Map({
      container: ref.current,
      style: STYLE,
      attributionControl: false,
    })
    map.addControl(new maplibregl.NavigationControl({ showCompass: false }), 'top-left')

    map.on('style.load', () => {
      forceKoreanLabels(map, i18n())
      addMarkers(map, places)
      fit(map, places)
    })
    // 컨테이너 크기 확정 후 리사이즈
    const t = setTimeout(() => map.resize(), 150)
    return () => { clearTimeout(t); map.remove() }
  }, [places])

  return <div ref={ref} className="map mp-map" />
}

/** 현재 UI 언어(로컬스토리지) → OSM name:{lang}. 없으면 한국어. */
function i18n() {
  const l = (typeof localStorage !== 'undefined' && localStorage.getItem('ui-lang')) || 'ko'
  return ['ko', 'en', 'ja', 'zh'].includes(l) ? l : 'ko'
}

/**
 * 심볼 레이어의 text-field 를 '구글 지도식 혼합'으로 바꾼다.
 *  - 1줄: 사용자 언어(name:{lang}) → 라틴표기 → 원문 순
 *  - 2줄: 현지 원문(name:nonlatin)이 1줄과 다르면 함께 표기 (예: 오사카 / 大阪)
 * 그래서 한글·영어·일본어·한자가 자연스럽게 섞여 보인다.
 */
function forceKoreanLabels(map, lang) {
  const primary = ['coalesce', ['get', `name:${lang}`], ['get', 'name:latin'], ['get', 'name']]
  const expr = [
    'let', 'p', primary,
    ['case',
      ['all', ['has', 'name:nonlatin'], ['!=', ['var', 'p'], ['get', 'name:nonlatin']]],
      ['concat', ['var', 'p'], '\n', ['get', 'name:nonlatin']],
      ['var', 'p']],
  ]
  for (const layer of map.getStyle().layers) {
    if (layer.type === 'symbol' && layer.layout && 'text-field' in layer.layout) {
      try { map.setLayoutProperty(layer.id, 'text-field', expr) } catch { /* 일부 레이어는 무시 */ }
    }
  }
}

function addMarkers(map, places) {
  for (const p of places) {
    const el = document.createElement('div')
    el.className = 'vm-pin'
    el.textContent = '📍'
    const popup = new maplibregl.Popup({ offset: 24, closeButton: false })
      .setHTML(`<b>${esc(p.name)}</b><br><span style="font-size:12px;color:#5f7787">${esc(p.tripTitle)}</span>`)
    new maplibregl.Marker({ element: el, anchor: 'bottom' })
      .setLngLat([Number(p.longitude), Number(p.latitude)])
      .setPopup(popup)
      .addTo(map)
  }
}

function fit(map, places) {
  if (places.length === 1) {
    map.jumpTo({ center: [Number(places[0].longitude), Number(places[0].latitude)], zoom: 10 })
    return
  }
  const b = new maplibregl.LngLatBounds()
  places.forEach((p) => b.extend([Number(p.longitude), Number(p.latitude)]))
  map.fitBounds(b, { padding: 40, maxZoom: 12, duration: 0 })
}

const esc = (s) => String(s ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]))
