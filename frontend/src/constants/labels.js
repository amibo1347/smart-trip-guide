// 항목/예약 유형 라벨 (이전엔 Itinerary·Review·SharedView·Bookings 에 중복).
// 값은 [이모지, i18n키]. 이름키는 t()로 현재 언어 표시.

export const PLAN_TYPE = {
  SPOT: ['🏞', '관광'],
  MEAL: ['🍽', '식사'],
  MOVE: ['🚌', '교통'],
  STAY: ['🏨', '숙박'],
  SHOPPING: ['🛍', '쇼핑'],
  ACTIVITY: ['🎯', '액티비티'],
  ETC: ['📌', '기타'],
}

/** 일정 항목 추가 시 고르는 카테고리(표시 순서). 액티비티는 기존 데이터 호환용이라 목록에서 뺀다. */
export const ITEM_CATEGORY_ORDER = ['SPOT', 'MEAL', 'STAY', 'MOVE', 'SHOPPING', 'ETC']

/** 장소 탐색 카테고리(관광/식당·카페/쇼핑) → 일정 항목 유형 매핑. */
export const DISCOVERY_TO_PLAN_TYPE = {
  SIGHT: 'SPOT',
  FOOD: 'MEAL',
  SHOPPING: 'SHOPPING',
}

export const BOOKING_TYPE = {
  FLIGHT: ['✈️', '항공'],
  HOTEL: ['🏨', '숙소'],
}

/** 준비물 분류. 키 = 백엔드 ChecklistCategory. 표시 순서도 이 선언 순서를 따른다. */
export const CHECKLIST_CATEGORY = {
  DOCUMENT: { emoji: '🛂', label: '서류·증명' },
  CLOTHES: { emoji: '👕', label: '의류' },
  ELECTRONICS: { emoji: '🔌', label: '전자기기' },
  TOILETRIES: { emoji: '🧴', label: '세면·화장' },
  MEDICINE: { emoji: '💊', label: '상비약' },
  ETC: { emoji: '📦', label: '기타' },
}

/**
 * 체크리스트 항목을 분류별로 묶어 [분류코드, 항목배열] 배열로 돌려준다.
 * 비어 있는 분류는 빼고, 순서는 CHECKLIST_CATEGORY 선언 순서를 따른다.
 */
export function categoryOrder(items) {
  return Object.keys(CHECKLIST_CATEGORY)
    .map((code) => [code, items.filter((i) => i.category === code)])
    .filter(([, list]) => list.length > 0)
}

/** "이모지 이름" (현재 언어). 미지정 코드는 코드 그대로. */
export function typeLabel(t, map, code) {
  return map[code] ? `${map[code][0]} ${t(map[code][1])}` : code
}

/** 이모지 없이 이름만(현재 언어). */
export function typeName(t, map, code) {
  return map[code] ? t(map[code][1]) : code
}
